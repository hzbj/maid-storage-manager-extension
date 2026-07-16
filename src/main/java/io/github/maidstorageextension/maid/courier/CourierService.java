package io.github.maidstorageextension.maid.courier;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import io.github.maidstorageextension.compat.EnderPocketCompat;
import io.github.maidstorageextension.data.CourierData;
import io.github.maidstorageextension.data.WarehouseCourierData;
import io.github.maidstorageextension.maid.ExtensionMemoryUtil;
import io.github.maidstorageextension.maid.task.CourierTask;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.wrapper.InvWrapper;
import studio.fantasyit.maid_storage_manager.Config;
import studio.fantasyit.maid_storage_manager.api.event.RequestListStatusChangeEvent;
import studio.fantasyit.maid_storage_manager.items.RequestListItem;
import studio.fantasyit.maid_storage_manager.maid.task.StorageManageTask;
import studio.fantasyit.maid_storage_manager.registry.ItemRegistry;
import studio.fantasyit.maid_storage_manager.util.MemoryUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Server authority for physical courier routes and lossless item hand-offs. */
public final class CourierService {
    public static final String META = "MaidStorageExtensionCourier";
    private static final String META_COURIER = "courier";
    private static final String META_ORIGINAL_STORAGE = "originalStorage";
    private static final String META_ORIGINAL_ENTITY = "originalEntity";
    private static final String META_FAILURE_RETURN = "failureReturn";
    private static final double BIND_RANGE = 32.0;
    private static final double OWNER_ANCHOR_RANGE = 8.0;
    private static final double HANDOFF_DISTANCE = 2.75;
    private static final double POSITION_ARRIVAL_DISTANCE = 1.75;
    private static final long HANDOFF_TICKS = 20L;
    private static final long DEPOSIT_STALL_TICKS = 20L * 20L;

    private CourierService() {
    }

    public static void requestNearestWarehouse(ServerPlayer owner, EntityMaid courier) {
        if (!isCourierOwnedBy(courier, owner)) return;
        CourierData.Data courierData = CourierData.get(courier);
        if (hasActiveTransaction(courier)) {
            owner.sendSystemMessage(Component.translatable(
                    "message.maid_storage_manager_extension.courier.transaction_active"));
            return;
        }
        CourierData.WarehouseBinding binding = CourierWarehouseStationService.findNearest(
                owner.serverLevel(), courier.blockPosition());
        if (binding == null) {
            EntityMaid nearby = findNearestWarehouse(owner.serverLevel(), courier,
                    courierData.broomFlightDistance());
            if (nearby != null) {
                binding = new CourierData.WarehouseBinding(nearby.getUUID(),
                        warehouseAnchor(nearby), dimension(owner.serverLevel()),
                        null, null, null, null, nearby.getName().getString());
            }
        }
        if (binding == null) {
            owner.sendSystemMessage(Component.translatable(
                    "message.maid_storage_manager_extension.courier.no_station"));
            return;
        }
        Entity entity = owner.serverLevel().getEntity(binding.warehouse());
        if (!(entity instanceof EntityMaid warehouse)) {
            owner.sendSystemMessage(Component.translatable(
                    "message.maid_storage_manager_extension.courier.warehouse_not_loaded"));
            return;
        }
        if (courierData.binding(binding.warehouse()) == null
                && courierData.warehouses().size() >= CourierData.MAX_WAREHOUSES) {
            owner.sendSystemMessage(Component.translatable(
                    "message.maid_storage_manager_extension.courier.station_limit",
                    CourierData.MAX_WAREHOUSES));
            return;
        }

        WarehouseCourierData.Data warehouseData = WarehouseCourierData.get(warehouse);
        if (warehouseData.isAuthorized(courier.getUUID()) || warehouse.isOwnedBy(owner)) {
            warehouseData.authorized().add(courier.getUUID());
            bind(courierData, warehouse, binding);
            sync(courier, courierData);
            sync(warehouse, warehouseData);
            owner.sendSystemMessage(Component.translatable(
                    "message.maid_storage_manager_extension.courier.bound_station",
                    warehouse.getName(), courierData.warehouses().size(), CourierData.MAX_WAREHOUSES));
        } else {
            warehouseData.request(courier.getUUID());
            courierData.requestApproval(binding);
            sync(courier, courierData);
            sync(warehouse, warehouseData);
            owner.sendSystemMessage(Component.translatable(
                    "message.maid_storage_manager_extension.courier.approval_requested", warehouse.getName()));
            notifyOwner(warehouse, "message.maid_storage_manager_extension.courier.approval_pending",
                    courier.getName());
        }
    }

    public static void setBroomFlightDistance(ServerPlayer owner, EntityMaid courier, int value) {
        if (!isCourierOwnedBy(courier, owner)) return;
        CourierData.Data data = CourierData.get(courier);
        data.broomFlightDistance(value);
        sync(courier, data);
    }

    public static void setPostDeliveryHomeMode(ServerPlayer owner, EntityMaid courier,
                                               boolean stayHome) {
        if (!isCourierOwnedBy(courier, owner)) return;
        CourierData.Data data = CourierData.get(courier);
        data.stayHomeAfterDelivery(stayHome);
        sync(courier, data);
    }

    public static boolean setDeliveryChest(ServerPlayer owner, UUID courierId,
                                           ServerLevel level, BlockPos position) {
        EntityMaid courier = findMaid(level, courierId);
        if (courier == null || !isCourierOwnedBy(courier, owner)
                || deliveryHandler(level, position) == null) return false;
        CourierData.Data data = CourierData.get(courier);
        data.deliveryTarget(position, dimension(level));
        redirectOwnerDeliveryToChest(courier, data);
        sync(courier, data);
        return true;
    }

    public static boolean clearDeliveryChest(ServerPlayer owner, UUID courierId) {
        EntityMaid courier = findMaid(owner.serverLevel(), courierId);
        if (courier == null || !isCourierOwnedBy(courier, owner)) return false;
        clearDeliveryTarget(courier, CourierData.get(courier));
        return true;
    }

    public static void approve(ServerPlayer warehouseOwner, EntityMaid warehouse, UUID courierId) {
        if (!warehouse.isOwnedBy(warehouseOwner)
                || !warehouse.getTask().getUid().equals(StorageManageTask.TASK_ID)) return;
        WarehouseCourierData.Data data = WarehouseCourierData.get(warehouse);
        if (!data.approve(courierId)) return;
        EntityMaid courier = findMaid(warehouseOwner.serverLevel(), courierId);
        if (courier != null) {
            CourierData.Data courierData = CourierData.get(courier);
            if (warehouse.getUUID().equals(courierData.pendingWarehouse())) {
                CourierData.WarehouseBinding pending = courierData.pendingBinding();
                if (pending == null) {
                    courierData.requestApproval((UUID) null);
                    sync(courier, courierData);
                    notifyOwner(courier,
                            "message.maid_storage_manager_extension.courier.station_unavailable");
                    sync(warehouse, data);
                    return;
                }
                bind(courierData, warehouse, pending);
                sync(courier, courierData);
                notifyOwner(courier, "message.maid_storage_manager_extension.courier.approved",
                        warehouse.getName());
            }
        }
        sync(warehouse, data);
        warehouseOwner.sendSystemMessage(Component.translatable(
                "message.maid_storage_manager_extension.courier.approval_accepted"));
    }

    public static void reject(ServerPlayer warehouseOwner, EntityMaid warehouse, UUID courierId) {
        if (!warehouse.isOwnedBy(warehouseOwner)) return;
        WarehouseCourierData.Data data = WarehouseCourierData.get(warehouse);
        data.reject(courierId);
        EntityMaid courier = findMaid(warehouseOwner.serverLevel(), courierId);
        if (courier != null) {
            CourierData.Data courierData = CourierData.get(courier);
            if (warehouse.getUUID().equals(courierData.pendingWarehouse())) {
                courierData.requestApproval((UUID) null);
                sync(courier, courierData);
                notifyOwner(courier, "message.maid_storage_manager_extension.courier.rejected",
                        warehouse.getName());
            }
        }
        sync(warehouse, data);
    }

    public static void unbind(ServerPlayer owner, EntityMaid courier) {
        if (!isCourierOwnedBy(courier, owner)) return;
        CourierData.Data data = CourierData.get(courier);
        if (hasActiveTransaction(courier)) {
            owner.sendSystemMessage(Component.translatable(
                    "message.maid_storage_manager_extension.courier.transaction_active"));
            return;
        }
        UUID warehouseId = data.warehouse();
        data.removeWarehouse(warehouseId);
        sync(courier, data);
        if (warehouseId != null && owner.serverLevel().getEntity(warehouseId) instanceof EntityMaid warehouse) {
            WarehouseCourierData.Data warehouseData = WarehouseCourierData.get(warehouse);
            warehouseData.revoke(courier.getUUID());
            sync(warehouse, warehouseData);
        }
    }

    public static void selectWarehouse(ServerPlayer owner, EntityMaid courier, UUID warehouseId) {
        if (!isCourierOwnedBy(courier, owner) || warehouseId == null) return;
        if (hasActiveTransaction(courier)) {
            owner.sendSystemMessage(Component.translatable(
                    "message.maid_storage_manager_extension.courier.transaction_active"));
            return;
        }
        CourierData.Data data = CourierData.get(courier);
        if (data.selectWarehouse(warehouseId)) {
            sync(courier, data);
            owner.sendSystemMessage(Component.translatable(
                    "message.maid_storage_manager_extension.courier.default_station_set",
                    data.warehouseName()));
        }
    }

    public static void recall(ServerPlayer owner, UUID courierId) {
        EntityMaid courier = findMaid(owner.serverLevel(), courierId);
        if (courier == null || !isCourierOwnedBy(courier, owner)) {
            owner.sendSystemMessage(Component.translatable(
                    "message.maid_storage_manager_extension.courier.recall_unavailable"));
            return;
        }
        CourierData.Data data = CourierData.get(courier);
        if (!(courier.level() instanceof ServerLevel level)
                || !CourierRecallPolicy.canRecall(data.transportMode(), data.phase(),
                CourierBroomFlightService.isAirborne(level, courier, data))) {
            owner.sendSystemMessage(Component.translatable(
                    "message.maid_storage_manager_extension.courier.recall_not_flying"));
            return;
        }
        CourierData.WarehouseBinding binding = data.binding(data.warehouse());
        if (binding == null || !binding.hasStation()
                || !binding.stationDimension().equals(dimension(level))) {
            owner.sendSystemMessage(Component.translatable(
                    "message.maid_storage_manager_extension.courier.recall_station_unavailable"));
            return;
        }
        level.getChunkAt(binding.mailboxPos());
        level.getChunkAt(binding.stationPos());
        if (!CourierWarehouseStationService.isValid(level, binding)) {
            owner.sendSystemMessage(Component.translatable(
                    "message.maid_storage_manager_extension.courier.recall_station_unavailable"));
            return;
        }
        CourierData.Phase recalledLeg = data.phase();
        CourierBroomFlightService.forceLand(courier, data, binding.stationPos());
        data.phase(CourierRecallPolicy.afterStationRecall(recalledLeg));
        data.targetWarningSent(false);
        sync(courier, data);
        owner.sendSystemMessage(Component.translatable(
                "message.maid_storage_manager_extension.courier.recalled", data.warehouseName()));
    }

    public static void locateOwnerTarget(ServerPlayer owner, UUID courierId) {
        EntityMaid courier = findMaid(owner.serverLevel(), courierId);
        if (courier == null || !isCourierOwnedBy(courier, owner)) {
            owner.sendSystemMessage(Component.translatable(
                    "message.maid_storage_manager_extension.courier.recall_unavailable"));
            return;
        }
        CourierData.Data data = CourierData.get(courier);
        if (!hasActiveTransaction(courier)) {
            owner.sendSystemMessage(Component.translatable(
                    "message.maid_storage_manager_extension.courier.locate_unavailable"));
            return;
        }
        data.ownerTarget(owner.blockPosition(), dimension(owner.serverLevel()));
        if (data.phase() == CourierData.Phase.WAITING_OWNER_PICKUP
                || data.phase() == CourierData.Phase.OWNER_HANDOFF
                || data.phase() == CourierData.Phase.OWNER_WAITING_SPACE) {
            data.phase(CourierData.Phase.TRAVEL_TO_OWNER);
        }
        if (data.flightLeg() == data.phase()) data.retargetFlight(data.phase());
        sync(courier, data);
        owner.sendSystemMessage(Component.translatable(
                "message.maid_storage_manager_extension.courier.owner_target_updated"));
    }

    public static void clearWork(ServerPlayer owner, UUID courierId) {
        EntityMaid courier = findMaid(owner.serverLevel(), courierId);
        if (courier == null || !isCourierOwnedBy(courier, owner)
                || !(courier.level() instanceof ServerLevel level)) {
            owner.sendSystemMessage(Component.translatable(
                    "message.maid_storage_manager_extension.courier.clear_work_unavailable"));
            return;
        }
        CourierData.Data data = CourierData.get(courier);
        if (CourierBroomFlightService.isAirborne(level, courier, data)) {
            BlockPos landing = CourierFlightStandLocator.findLanding(level,
                    courier.blockPosition(), 0, data.broomFlightDistance(), 0);
            if (landing == null) {
                owner.sendSystemMessage(Component.translatable(
                        "message.maid_storage_manager_extension.courier.clear_work_no_safe_landing"));
                return;
            }
            CourierBroomFlightService.forceLand(courier, data, landing);
        }
        finishWorkClear(courier, data);
        owner.sendSystemMessage(Component.translatable(
                "message.maid_storage_manager_extension.courier.clear_work_complete"));
    }

    public static void confirmDeposit(ServerPlayer owner, EntityMaid courier) {
        if (!isCourierOwnedBy(courier, owner)) return;
        CourierData.Data data = CourierData.get(courier);
        if (data.phase() != CourierData.Phase.IDLE
                && data.phase() != CourierData.Phase.WAITING_AT_DELIVERY_CHEST) {
            owner.sendSystemMessage(Component.translatable(
                    "message.maid_storage_manager_extension.courier.transaction_active"));
            return;
        }
        if (data.phase() == CourierData.Phase.WAITING_AT_DELIVERY_CHEST) {
            data.phase(CourierData.Phase.IDLE);
        }
        data.depositRequested(true);
        sync(courier, data);
        owner.sendSystemMessage(Component.translatable(
                "message.maid_storage_manager_extension.courier.deposit_confirmed"));
    }

    public static void tick(ServerLevel level, EntityMaid courier) {
        CourierData.Data data = CourierData.get(courier);
        validateDeliveryTarget(level, courier, data);
        if (!CourierSortMutex.mayCourierUseOwnInventory(
                ExtensionMemoryUtil.getMiscSort(courier).hasInFlight())) {
            return;
        }
        enforceCourierNavigationOwnership(courier, data);
        if (tickWaitingState(level, courier, data)) return;
        if (!hasActiveTransaction(courier) && data.followOverrideActive()) {
            restoreFollowIfBesideOwner(courier, data);
        }
        if (hasActiveTransaction(courier) && data.transportMode() == CourierData.TransportMode.NONE) {
            recoverTransportMode(level, courier, data);
            if (data.transportMode() == CourierData.TransportMode.NONE) {
                pauseForMissingTransport(courier, data);
                return;
            }
        }
        if (!transportAvailable(courier, data.transportMode())) {
            pauseForMissingTransport(courier, data);
            return;
        } else if (data.accessoryWarningSent()) {
            data.accessoryWarningSent(false);
            sync(courier, data);
        }

        if (!data.pendingList().isEmpty()) tryReturnPendingList(courier, data);
        drainPendingCargo(courier, data);
        reconcileApproval(level, courier, data);

        if (data.warehouse() == null) return;
        EntityMaid warehouse = resolveWarehouse(level, data);
        if (warehouse != null) {
            if (!validWarehouseLink(courier, warehouse)) {
                pauseForUnavailableTarget(courier, data,
                        "message.maid_storage_manager_extension.courier.authorization_lost");
                return;
            }
            BlockPos current = warehouseAnchor(warehouse);
            ResourceLocation currentDimension = dimension(level);
            if (data.phase() == CourierData.Phase.IDLE
                    && (!current.equals(data.warehousePos())
                    || !currentDimension.equals(data.warehouseDimension()))) {
                data.rememberWarehouse(current, currentDimension);
                sync(courier, data);
            }
        }
        if (hasActiveTransaction(courier) && data.originPos() == null) {
            captureOrigin(level, courier, data); // Migrates an in-flight 1.1.0 journal safely.
            sync(courier, data);
        }

        switch (data.phase()) {
            case IDLE -> {
                if (!beginRequest(level, courier, data, warehouse) && data.depositRequested()) {
                    beginDeposit(level, courier, data, warehouse);
                }
            }
            case TRAVEL_TO_WAREHOUSE_REQUEST -> {
                EntityMaid meeting = approachWarehouse(level, courier, data, warehouse);
                if (meeting != null) enterHandoff(courier, data,
                        CourierData.Phase.REQUEST_HANDOFF, level.getGameTime());
            }
            case REQUEST_HANDOFF -> {
                EntityMaid meeting = approachWarehouse(level, courier, data, warehouse);
                if (meeting != null && handoffReady(level, courier, meeting, data)) {
                    dispatchRequest(courier, meeting, data);
                }
            }
            // The courier waits at the counter while the storage maid is free to visit chests.
            // MSM's return behavior brings the storage maid back to this courier for final hand-off.
            case REQUEST_RUNNING, REQUEST_WAITING_SPACE -> pauseMovement(courier);
            case TRAVEL_TO_OWNER -> approachOwner(level, courier, data);
            case TRAVEL_TO_DELIVERY_CHEST, DELIVERY_CHEST_WAITING_SPACE ->
                    approachDeliveryChest(level, courier, data);
            case OWNER_HANDOFF, OWNER_WAITING_SPACE -> handoffToOwner(level, courier, data);
            case RETURNING_TO_ORIGIN -> returnToOrigin(level, courier, data);
            case RETURNING_AFTER_LANDING_FAILURE ->
                    waitAfterLandingFailure(courier, data);
            case TRAVEL_TO_WAREHOUSE_DEPOSIT -> {
                EntityMaid meeting = approachWarehouse(level, courier, data, warehouse);
                if (meeting != null) enterHandoff(courier, data,
                        CourierData.Phase.DEPOSIT_HANDOFF, level.getGameTime());
            }
            case DEPOSIT_HANDOFF -> {
                EntityMaid meeting = approachWarehouse(level, courier, data, warehouse);
                if (meeting != null && handoffReady(level, courier, meeting, data)) {
                    dispatchDeposit(level, courier, meeting, data);
                }
            }
            // Do not stop or chase the storage maid here: she must be able to walk to target chests.
            case DEPOSIT_RUNNING -> {
                pauseMovement(courier);
                if (warehouse != null) monitorDeposit(level, courier, warehouse, data);
            }
            case DEPOSIT_RETURNING, DEPOSIT_WAITING_SPACE -> {
                EntityMaid meeting = approachWarehouse(level, courier, data, warehouse);
                if (meeting != null) returnDeposit(courier, meeting, data);
            }
            case LINK_UNAVAILABLE -> {
                if (warehouse != null && validWarehouseLink(courier, warehouse)) {
                    data.phase(CourierData.Phase.IDLE);
                    data.targetWarningSent(false);
                    sync(courier, data);
                }
            }
            default -> { }
        }
    }

    /**
     * Moves a broom courier every server tick. The main state machine only runs every ten ticks,
     * so flight must not be tied to behavior cadence or it becomes visibly jerky.
     */
    public static void tickBroomFlight(ServerLevel level, EntityMaid courier) {
        CourierData.Data data = CourierData.get(courier);
        enforceCourierNavigationOwnership(courier, data);
        boolean courierTaskSelected = courier.getTask().getUid().equals(CourierTask.TASK_ID);
        if (CourierRuntimePolicy.shouldKeepCourierChunkLoaded(courierTaskSelected,
                data.transportMode(), data.phase())) {
            // Couriers are autonomous workers: keep the courier, warehouse and mailbox context
            // ticking even when the owner and every ordinary player leave the area.
            CourierBroomFlightService.keepCourierContextLoaded(level, courier, data);
        }
        if (!CourierSortMutex.mayCourierUseOwnInventory(
                ExtensionMemoryUtil.getMiscSort(courier).hasInFlight())) {
            CourierBroomFlightService.cleanup(courier, data);
            return;
        }
        if (!data.transportMode().usesBroom()
                || !hasActiveTransaction(courier)) {
            CourierBroomFlightService.cleanup(courier, data);
            return;
        }
        if (!CourierFlightPolicy.supportsBroomDimension(dimension(level))) {
            pauseBroomForDimension(courier, data,
                    "message.maid_storage_manager_extension.courier.broom_overworld_only");
            return;
        }
        // Keep the protected vehicle hovering when the broom item is temporarily removed. The
        // ten-tick state machine reports the missing tool and resumes without dropping the maid.
        if (!EnderPocketCompat.hasBroom(courier)) return;

        if (data.phase() == CourierData.Phase.WAITING_FOR_SAFE_LANDING) return;

        if (isOwnerFlightLeg(data) && !ownerAvailableForBroom(level, courier)) {
            pauseBroomForDimension(courier, data,
                    "message.maid_storage_manager_extension.courier.owner_must_be_overworld");
            return;
        }

        Vec3 target = broomTarget(level, courier, data);
        if (target == null) {
            CourierBroomFlightService.holdPosition(courier);
            return;
        }
        CourierData.WarehouseBinding station = data.binding(data.warehouse());
        boolean stationReady = station != null && validStation(level, station);
        boolean broomAlreadyActive = CourierBroomFlightService.isCourierBroom(courier.getVehicle())
                || data.courierBroom() != null;
        BlockPos fixedLanding = switch (data.phase()) {
            case TRAVEL_TO_WAREHOUSE_REQUEST, TRAVEL_TO_WAREHOUSE_DEPOSIT ->
                    stationReady ? station.stationPos() : null;
            default -> null;
        };
        BlockPos fixedTakeoff = switch (data.phase()) {
            case TRAVEL_TO_OWNER, TRAVEL_TO_DELIVERY_CHEST, RETURNING_TO_ORIGIN ->
                    broomAlreadyActive ? data.flightTakeoffPos()
                            : data.flightLeg() != data.phase() && stationReady
                            ? station.stationPos() : null;
            default -> null;
        };
        boolean warehouseLeg = data.phase() == CourierData.Phase.TRAVEL_TO_WAREHOUSE_REQUEST
                || data.phase() == CourierData.Phase.TRAVEL_TO_WAREHOUSE_DEPOSIT;
        boolean stationTakeoffLeg = data.flightLeg() != data.phase()
                && (data.phase() == CourierData.Phase.TRAVEL_TO_OWNER
                || data.phase() == CourierData.Phase.TRAVEL_TO_DELIVERY_CHEST
                || data.phase() == CourierData.Phase.RETURNING_TO_ORIGIN);
        if (warehouseLeg && fixedLanding == null
                || stationTakeoffLeg && !broomAlreadyActive && fixedTakeoff == null) {
            pauseMovement(courier);
            pauseForUnavailableTarget(courier, data,
                    "message.maid_storage_manager_extension.courier.station_unavailable");
            return;
        }
        CourierBroomFlightService.TickResult result = CourierBroomFlightService.tick(
                level, courier, data, data.phase(), target, fixedTakeoff, fixedLanding);
        if (result == CourierBroomFlightService.TickResult.LANDING_SEARCH_EXHAUSTED) {
            handleLandingSearchExhausted(level, courier, data);
        } else if (result == CourierBroomFlightService.TickResult.STATION_UNAVAILABLE) {
            pauseForUnavailableTarget(courier, data,
                    "message.maid_storage_manager_extension.courier.station_unavailable");
        }
    }

    private static Vec3 broomTarget(ServerLevel level, EntityMaid courier, CourierData.Data data) {
        return switch (data.phase()) {
            case TRAVEL_TO_WAREHOUSE_REQUEST, TRAVEL_TO_WAREHOUSE_DEPOSIT -> {
                CourierData.WarehouseBinding binding = data.binding(data.warehouse());
                yield binding == null || binding.stationPos() == null
                        ? null : binding.stationPos().getCenter();
            }
            case TRAVEL_TO_OWNER -> {
                yield ownerTarget(level, courier, data);
            }
            case RETURNING_TO_ORIGIN -> {
                if (data.originOwner()) {
                    yield ownerTarget(level, courier, data);
                }
                yield data.originDimension() != null
                        && data.originDimension().equals(dimension(level))
                        && data.originPos() != null ? data.originPos().getCenter() : null;
            }
            case TRAVEL_TO_DELIVERY_CHEST -> data.deliveryDimension() != null
                    && data.deliveryDimension().equals(dimension(level))
                    && data.deliveryPos() != null ? data.deliveryPos().getCenter() : null;
            case RETURNING_AFTER_LANDING_FAILURE -> data.originDimension() != null
                    && data.originDimension().equals(dimension(level))
                    && data.originPos() != null ? data.originPos().getCenter() : null;
            default -> null;
        };
    }

    private static Vec3 ownerTarget(ServerLevel level, EntityMaid courier,
                                    CourierData.Data data) {
        ServerPlayer owner = ownerPlayer(level, courier);
        BlockPos target = ensureOwnerTarget(level, courier, data, owner);
        return target == null ? null : target.getCenter();
    }

    private static BlockPos ensureOwnerTarget(ServerLevel level, EntityMaid courier,
                                              CourierData.Data data, ServerPlayer owner) {
        if (data.ownerTargetPos() != null && data.ownerTargetDimension() != null) {
            return data.ownerTargetDimension().equals(dimension(level))
                    ? data.ownerTargetPos() : null;
        }
        if (owner == null || owner.level() != level) return null;
        data.ownerTarget(owner.blockPosition(), dimension(level));
        sync(courier, data);
        return data.ownerTargetPos();
    }

    private static boolean tickWaitingState(ServerLevel level, EntityMaid courier,
                                            CourierData.Data data) {
        return switch (data.phase()) {
            case WAITING_AT_DELIVERY_CHEST -> {
                pauseMovement(courier);
                if (CourierDeliveryPolicy.shouldResumeFromChestWait(
                        hasDispatchableRequest(courier), data.depositRequested())) {
                    data.phase(CourierData.Phase.IDLE);
                    sync(courier, data);
                    yield false;
                }
                yield true;
            }
            case WAITING_OWNER_PICKUP -> {
                pauseMovement(courier);
                if (redirectOwnerDeliveryToChest(courier, data)) yield false;
                if (courier.getOwner() instanceof ServerPlayer owner
                        && owner.level() == level
                        && CourierGroundNavigationPolicy.shouldResumeOwnerPickup(
                        true, courier.distanceToSqr(owner), HANDOFF_DISTANCE)) {
                    data.phase(CourierData.Phase.TRAVEL_TO_OWNER);
                    data.targetWarningSent(false);
                    sync(courier, data);
                    yield false;
                }
                yield true;
            }
            case WAITING_WITH_CARGO_AT_DELIVERY_CHEST -> {
                pauseMovement(courier);
                yield true;
            }
            case WAITING_AT_STATION_AFTER_RECALL -> {
                pauseMovement(courier);
                if (courier.getOwner() instanceof ServerPlayer owner
                        && owner.level() == level
                        && courier.distanceToSqr(owner)
                        <= OWNER_ANCHOR_RANGE * OWNER_ANCHOR_RANGE) {
                    data.phase(CourierData.Phase.TRAVEL_TO_OWNER);
                    data.targetWarningSent(false);
                    sync(courier, data);
                    yield false;
                }
                yield true;
            }
            case WAITING_FOR_SAFE_LANDING -> true;
            default -> false;
        };
    }

    private static void handleLandingSearchExhausted(ServerLevel level, EntityMaid courier,
                                                      CourierData.Data data) {
        if (!CourierBroomFlightService.isAirborne(level, courier, data)) {
            pauseMovement(courier);
            data.phase(CourierData.Phase.WAITING_FOR_SAFE_LANDING);
            sync(courier, data);
            return;
        }
        if (data.phase() != CourierData.Phase.RETURNING_AFTER_LANDING_FAILURE
                && data.originPos() != null && data.originDimension() != null
                && data.originDimension().equals(dimension(level))) {
            data.phase(CourierData.Phase.RETURNING_AFTER_LANDING_FAILURE);
            // Keep the already-mounted broom and turn it around. Dismounting in mid-air would
            // turn the recovery path itself into another unsafe landing.
            data.retargetFlight(CourierData.Phase.RETURNING_AFTER_LANDING_FAILURE);
            sync(courier, data);
            notifyOwner(courier,
                    "message.maid_storage_manager_extension.courier.landing_failed_returning");
            return;
        }
        data.phase(CourierData.Phase.WAITING_FOR_SAFE_LANDING);
        sync(courier, data);
        notifyOwner(courier,
                "message.maid_storage_manager_extension.courier.landing_search_exhausted");
    }

    private static void waitAfterLandingFailure(EntityMaid courier, CourierData.Data data) {
        if (!CourierBroomFlightService.landedFor(
                data, CourierData.Phase.RETURNING_AFTER_LANDING_FAILURE)) return;
        pauseMovement(courier);
        CourierBroomFlightService.cleanup(courier, data);
        data.phase(CourierData.Phase.WAITING_OWNER_PICKUP);
        sync(courier, data);
        notifyOwner(courier,
                "message.maid_storage_manager_extension.courier.returned_after_landing_failure");
    }

    private static void reconcileApproval(ServerLevel level, EntityMaid courier, CourierData.Data data) {
        if (data.pendingWarehouse() == null) return;
        if (level.getEntity(data.pendingWarehouse()) instanceof EntityMaid pendingWarehouse) {
            WarehouseCourierData.Data pendingData = WarehouseCourierData.get(pendingWarehouse);
            if (pendingData.isAuthorized(courier.getUUID())) {
                CourierData.WarehouseBinding pending = data.pendingBinding();
                if (pending == null) {
                    data.requestApproval((UUID) null);
                    sync(courier, data);
                    notifyOwner(courier,
                            "message.maid_storage_manager_extension.courier.station_unavailable");
                    return;
                }
                bind(data, pendingWarehouse, pending);
                sync(courier, data);
                notifyOwner(courier, "message.maid_storage_manager_extension.courier.approved",
                        pendingWarehouse.getName());
            } else if (!pendingData.pending().contains(courier.getUUID())) {
                data.requestApproval((UUID) null);
                sync(courier, data);
                notifyOwner(courier, "message.maid_storage_manager_extension.courier.rejected",
                        pendingWarehouse.getName());
            }
        }
    }

    private static boolean beginRequest(ServerLevel level, EntityMaid courier, CourierData.Data data,
                                        EntityMaid warehouse) {
        IItemHandlerModifiable source = courier.getAvailableInv(false);
        for (int slot = 0; slot < source.getSlots(); slot++) {
            ItemStack list = source.getStackInSlot(slot);
            if (!list.is(ItemRegistry.REQUEST_LIST_ITEM.get())
                    || RequestListItem.isIgnored(list) || isActiveCourierList(list)) continue;
            CourierData.TransportMode mode = selectStartMode(level, courier, data, warehouse);
            if (mode == CourierData.TransportMode.NONE) {
                warnTransportNotReady(courier, data, warehouse);
                return false;
            }
            if (mode.usesBroom() && !validStation(level, data.binding(data.warehouse()))) {
                warnStationUnavailable(courier, data);
                return false;
            }
            activateMode(courier, data, mode);
            captureOrigin(level, courier, data);
            rememberRequestOwnerTarget(level, courier, data, list);
            data.clearRequest();
            data.phase(CourierData.Phase.TRAVEL_TO_WAREHOUSE_REQUEST);
            sync(courier, data);
            return true;
        }
        return false;
    }

    private static void beginDeposit(ServerLevel level, EntityMaid courier, CourierData.Data data,
                                     EntityMaid warehouse) {
        CourierData.TransportMode mode = selectStartMode(level, courier, data, warehouse);
        if (mode == CourierData.TransportMode.NONE) {
            warnTransportNotReady(courier, data, warehouse);
            return;
        }
        if (mode.usesBroom() && !validStation(level, data.binding(data.warehouse()))) {
            warnStationUnavailable(courier, data);
            return;
        }
        activateMode(courier, data, mode);
        captureOrigin(level, courier, data);
        data.clearDeposit();
        data.depositRequested(true);
        data.requestManifest().clear();
        data.phase(CourierData.Phase.TRAVEL_TO_WAREHOUSE_DEPOSIT);
        sync(courier, data);
    }

    private static void captureOrigin(ServerLevel level, EntityMaid courier, CourierData.Data data) {
        boolean besideOwner = isNearOwnerForTransport(level, courier, data.broomFlightDistance());
        data.beginRoute(courier.blockPosition(), dimension(level), besideOwner);
    }

    private static CourierData.TransportMode selectStartMode(ServerLevel level, EntityMaid courier,
                                                             CourierData.Data data,
                                                             EntityMaid warehouse) {
        CourierData.TransportMode required = requiredStartMode(level, courier, data, warehouse);
        CourierData.TransportMode selected = CourierTransportPolicy.hasRequiredItems(required,
                EnderPocketCompat.isEquipped(courier), EnderPocketCompat.hasBroom(courier))
                ? required : CourierData.TransportMode.NONE;
        return selected.usesBroom() && !broomRouteAvailable(level, courier, data)
                ? CourierData.TransportMode.NONE : selected;
    }

    private static CourierData.TransportMode requiredStartMode(ServerLevel level, EntityMaid courier,
                                                               CourierData.Data data,
                                                               EntityMaid warehouse) {
        boolean sameWarehouseDimension = data.warehouseDimension() != null
                && data.warehouseDimension().equals(dimension(level));
        boolean insideWarehouse = sameWarehouseDimension
                && nearWarehouseForTransport(courier, warehouse, data.warehousePos(),
                data.broomFlightDistance());
        boolean besideOwner = isNearOwnerForTransport(level, courier, data.broomFlightDistance());
        return CourierTransportPolicy.requiredMode(insideWarehouse, besideOwner);
    }

    private static boolean isBesideOwner(ServerLevel level, EntityMaid courier) {
        return courier.getOwner() instanceof ServerPlayer owner
                && owner.level() == level
                && courier.distanceToSqr(owner) <= OWNER_ANCHOR_RANGE * OWNER_ANCHOR_RANGE;
    }

    private static boolean isNearOwnerForTransport(ServerLevel level, EntityMaid courier,
                                                   int range) {
        return courier.getOwner() instanceof ServerPlayer owner
                && owner.level() == level && withinHorizontal(courier.blockPosition(),
                owner.blockPosition(), range);
    }

    private static boolean nearWarehouseForTransport(EntityMaid courier, EntityMaid warehouse,
                                                       BlockPos rememberedPos, int range) {
        BlockPos target = warehouse == null ? rememberedPos : warehouse.blockPosition();
        return target != null && withinHorizontal(courier.blockPosition(), target, range);
    }

    private static boolean withinHorizontal(BlockPos first, BlockPos second, int range) {
        long dx = (long) first.getX() - second.getX();
        long dz = (long) first.getZ() - second.getZ();
        return dx * dx + dz * dz <= (long) range * range;
    }

    private static void activateMode(EntityMaid courier, CourierData.Data data,
                                     CourierData.TransportMode mode) {
        data.transportMode(mode);
        data.targetWarningSent(false);
        if (CourierRuntimePolicy.shouldSuspendOwnerFollow(mode)) {
            data.beginFollowOverride(courier.isHomeModeEnable());
            courier.setHomeModeEnable(false);
        }
    }

    private static void enforceCourierNavigationOwnership(EntityMaid courier, CourierData.Data data) {
        if (CourierRuntimePolicy.shouldDisableHomeRestriction(
                data.followOverrideActive(), data.transportMode())
                && courier.isHomeModeEnable()) {
            courier.setHomeModeEnable(false);
        }
    }

    private static void rememberRequestOwnerTarget(ServerLevel level, EntityMaid courier,
                                                   CourierData.Data data, ItemStack list) {
        CourierRequestTarget.Target marked = CourierRequestTarget.read(list);
        if (marked != null) {
            data.ownerTarget(marked.position(), marked.dimension());
            return;
        }
        ServerPlayer owner = ownerPlayer(level, courier);
        if (owner != null) data.ownerTarget(owner.blockPosition(), dimension(owner.serverLevel()));
    }

    private static void recoverTransportMode(ServerLevel level, EntityMaid courier,
                                             CourierData.Data data) {
        EntityMaid warehouse = resolveWarehouse(level, data);
        CourierData.TransportMode recovered = selectStartMode(level, courier, data, warehouse);
        if (recovered == CourierData.TransportMode.NONE
                && CourierTransportPolicy.hasRequiredItems(
                CourierData.TransportMode.BROOM_ENDER_POCKET,
                EnderPocketCompat.isEquipped(courier), EnderPocketCompat.hasBroom(courier))) {
            recovered = CourierData.TransportMode.BROOM_ENDER_POCKET;
        }
        if (recovered.usesBroom() && !broomRouteAvailable(level, courier, data)) {
            recovered = CourierData.TransportMode.NONE;
        }
        if (recovered != CourierData.TransportMode.NONE) {
            activateMode(courier, data, recovered);
            sync(courier, data);
        }
    }

    private static boolean transportAvailable(EntityMaid courier, CourierData.TransportMode mode) {
        return CourierTransportPolicy.hasRequiredItems(mode,
                EnderPocketCompat.isEquipped(courier), EnderPocketCompat.hasBroom(courier));
    }

    private static void pauseForMissingTransport(EntityMaid courier, CourierData.Data data) {
        pauseMovement(courier);
        if (data.accessoryWarningSent()) return;
        data.accessoryWarningSent(true);
        sync(courier, data);
        notifyOwner(courier, "message.maid_storage_manager_extension.courier.transport_removed");
    }

    private static void warnTransportNotReady(EntityMaid courier, CourierData.Data data,
                                              EntityMaid warehouse) {
        if (data.targetWarningSent()) return;
        data.targetWarningSent(true);
        sync(courier, data);
        if (!(courier.level() instanceof ServerLevel level)) return;
        CourierData.TransportMode required = requiredStartMode(level, courier, data, warehouse);
        boolean hasEnderPocket = EnderPocketCompat.isEquipped(courier);
        boolean hasBroom = EnderPocketCompat.hasBroom(courier);
        if (required.usesBroom() && !hasBroom && required.usesEnderPocket() && !hasEnderPocket) {
            notifyOwner(courier,
                    "message.maid_storage_manager_extension.courier.missing_broom_and_ender_pocket");
        } else if (required.usesBroom() && !hasBroom) {
            notifyOwner(courier, "message.maid_storage_manager_extension.courier.missing_broom");
        } else if (required.usesEnderPocket() && !hasEnderPocket) {
            notifyOwner(courier, "message.maid_storage_manager_extension.courier.missing_ender_pocket");
        } else if (required.usesBroom() && !broomRouteAvailable(level, courier, data)) {
            notifyOwner(courier,
                    "message.maid_storage_manager_extension.courier.broom_overworld_only");
        }
    }

    private static EntityMaid approachWarehouse(ServerLevel level, EntityMaid courier,
                                                CourierData.Data data, EntityMaid loadedWarehouse) {
        if (data.warehouseDimension() == null || !dimension(level).equals(data.warehouseDimension())) {
            pauseForUnavailableTarget(courier, data,
                    "message.maid_storage_manager_extension.courier.warehouse_other_dimension");
            return null;
        }
        if (loadedWarehouse != null) {
            if (courier.distanceToSqr(loadedWarehouse) <= HANDOFF_DISTANCE * HANDOFF_DISTANCE) {
                CourierBroomFlightService.cleanup(courier, data);
                data.targetWarningSent(false);
                faceEachOther(courier, loadedWarehouse);
                return loadedWarehouse;
            }
            resetHandoff(data);
            data.targetWarningSent(false);
            if (data.transportMode().usesBroom()
                    && !CourierBroomFlightService.landedFor(data, data.phase())) {
                return null;
            }
            GroundNavigationResult result = navigateGround(
                    level, courier, data, loadedWarehouse, 1);
            if (result == GroundNavigationResult.EXHAUSTED
                    && data.transportMode().usesBroom()) {
                data.clearGroundApproach();
                sync(courier, data);
                notifyOwner(courier,
                        "message.maid_storage_manager_extension.courier.station_path_retry");
            }
            return null;
        }
        BlockPos target = data.warehousePos();
        if (target == null) {
            pauseForUnavailableTarget(courier, data,
                    "message.maid_storage_manager_extension.courier.link_unavailable");
            return null;
        }
        if (courier.distanceToSqr(target.getCenter()) > POSITION_ARRIVAL_DISTANCE * POSITION_ARRIVAL_DISTANCE) {
            resetHandoff(data);
            if (data.transportMode().usesBroom()
                    && !CourierBroomFlightService.landedFor(data, data.phase())) {
                return null;
            } else {
                MemoryUtil.setTarget(courier, target, (float) Config.collectSpeed);
                MemoryUtil.setLookAt(courier, target);
            }
        } else {
            pauseForUnavailableTarget(courier, data,
                    "message.maid_storage_manager_extension.courier.warehouse_not_loaded");
        }
        return null;
    }

    private static void approachOwner(ServerLevel level, EntityMaid courier, CourierData.Data data) {
        if (redirectOwnerDeliveryToChest(courier, data)) return;
        ServerPlayer owner = ownerPlayer(level, courier);
        if (data.transportMode().usesBroom()
                && (owner == null || owner.level() != level
                || !CourierFlightPolicy.supportsBroomDimension(owner.level().dimension().location()))) {
            pauseBroomForDimension(courier, data,
                    "message.maid_storage_manager_extension.courier.owner_must_be_overworld");
            return;
        }
        if (owner == null || owner.level() != level) {
            pauseForUnavailableTarget(courier, data,
                    "message.maid_storage_manager_extension.courier.owner_unavailable");
            return;
        }
        data.targetWarningSent(false);
        BlockPos target = ensureOwnerTarget(level, courier, data, owner);
        if (target == null) return;
        boolean reachedTarget = courier.distanceToSqr(target.getCenter())
                <= POSITION_ARRIVAL_DISTANCE * POSITION_ARRIVAL_DISTANCE;
        if (courier.distanceToSqr(owner) <= HANDOFF_DISTANCE * HANDOFF_DISTANCE) {
            CourierBroomFlightService.cleanup(courier, data);
            faceEachOther(courier, owner);
            enterHandoff(courier, data, CourierData.Phase.OWNER_HANDOFF, level.getGameTime());
        } else if (reachedTarget) {
            pauseMovement(courier);
            data.phase(CourierData.Phase.WAITING_OWNER_PICKUP);
            sync(courier, data);
            notifyOwner(courier,
                    "message.maid_storage_manager_extension.courier.waiting_owner_pickup");
        } else {
            resetHandoff(data);
            if (data.transportMode().usesBroom()
                    && !CourierBroomFlightService.landedFor(data, data.phase())) {
                return;
            } else {
                if (navigateGround(level, courier, data, target, 1)
                        == GroundNavigationResult.EXHAUSTED) {
                    pauseMovement(courier);
                    data.phase(CourierData.Phase.WAITING_OWNER_PICKUP);
                    sync(courier, data);
                    notifyOwner(courier,
                            "message.maid_storage_manager_extension.courier.waiting_owner_pickup");
                }
            }
        }
    }

    private static void approachDeliveryChest(ServerLevel level, EntityMaid courier,
                                              CourierData.Data data) {
        BlockPos target = data.deliveryPos();
        if (target == null || data.deliveryDimension() == null
                || !data.deliveryDimension().equals(dimension(level))) {
            data.deliveryTarget(null, null);
            data.phase(CourierData.Phase.TRAVEL_TO_OWNER);
            sync(courier, data);
            notifyOwner(courier,
                    "message.maid_storage_manager_extension.courier.delivery_chest_unavailable");
            return;
        }
        if (courier.distanceToSqr(target.getCenter())
                > HANDOFF_DISTANCE * HANDOFF_DISTANCE) {
            if (data.phase() == CourierData.Phase.TRAVEL_TO_DELIVERY_CHEST
                    && data.transportMode().usesBroom()
                    && !CourierBroomFlightService.landedFor(data, data.phase())) return;
            if (navigateGround(level, courier, data, target, 1)
                    == GroundNavigationResult.EXHAUSTED) {
                pauseMovement(courier);
                data.phase(CourierData.Phase.WAITING_WITH_CARGO_AT_DELIVERY_CHEST);
                sync(courier, data);
                notifyOwner(courier,
                        "message.maid_storage_manager_extension.courier.delivery_chest_unreachable");
            }
            return;
        }

        pauseMovement(courier);
        IItemHandler destination = deliveryHandler(level, target);
        if (destination == null) {
            data.deliveryTarget(null, null);
            data.phase(CourierData.Phase.TRAVEL_TO_OWNER);
            sync(courier, data);
            notifyOwner(courier,
                    "message.maid_storage_manager_extension.courier.delivery_chest_unavailable");
            return;
        }
        if (transferDeliveryCargo(courier, destination, data)) {
            courier.swing(InteractionHand.MAIN_HAND, true);
            completeChestDelivery(courier, data);
        } else {
            data.phase(CourierData.Phase.DELIVERY_CHEST_WAITING_SPACE);
            if (!data.spaceWarningSent()) {
                data.spaceWarningSent(true);
                notifyOwner(courier,
                        "message.maid_storage_manager_extension.courier.delivery_chest_full");
            }
            sync(courier, data);
        }
    }

    private static void completeChestDelivery(EntityMaid courier, CourierData.Data data) {
        pauseMovement(courier);
        CourierBroomFlightService.cleanup(courier, data);
        data.clearRequest();
        data.clearDeposit();
        data.clearRoute();
        setHomeAtCurrentPosition(courier);
        data.clearFollowOverride();
        data.phase(CourierData.Phase.WAITING_AT_DELIVERY_CHEST);
        notifyOwner(courier,
                "message.maid_storage_manager_extension.courier.delivery_chest_complete");
        sync(courier, data);
    }

    private static void handoffToOwner(ServerLevel level, EntityMaid courier, CourierData.Data data) {
        if (redirectOwnerDeliveryToChest(courier, data)) return;
        if (!(courier.getOwner() instanceof ServerPlayer owner) || owner.level() != level) {
            pauseForUnavailableTarget(courier, data,
                    "message.maid_storage_manager_extension.courier.owner_unavailable");
            return;
        }
        if (courier.distanceToSqr(owner) > HANDOFF_DISTANCE * HANDOFF_DISTANCE) {
            data.phase(CourierData.Phase.TRAVEL_TO_OWNER);
            resetHandoff(data);
            sync(courier, data);
            return;
        }
        faceEachOther(courier, owner);
        if (data.phase() == CourierData.Phase.OWNER_HANDOFF
                && !handoffReady(level, courier, owner, data)) return;

        if (transferDeliveryCargo(courier, owner, data)) {
            courier.swing(InteractionHand.MAIN_HAND, true);
            owner.swing(InteractionHand.MAIN_HAND, true);
            data.clearRequest();
            data.spaceWarningSent(false);
            notifyOwner(courier, "message.maid_storage_manager_extension.courier.owner_handoff_complete");
            beginReturn(courier, data);
        } else {
            data.phase(CourierData.Phase.OWNER_WAITING_SPACE);
            if (!data.spaceWarningSent()) {
                data.spaceWarningSent(true);
                notifyOwner(courier, "message.maid_storage_manager_extension.courier.owner_inventory_full");
            }
            sync(courier, data);
        }
    }

    private static boolean transferDeliveryCargo(EntityMaid courier, ServerPlayer owner,
                                                  CourierData.Data data) {
        return transferDeliveryCargo(courier, new InvWrapper(owner.getInventory()), data);
    }

    private static boolean transferDeliveryCargo(EntityMaid courier, IItemHandler destination,
                                                  CourierData.Data data) {
        IItemHandlerModifiable courierInventory = courier.getAvailableInv(false);
        List<CourierData.ManifestEntry> remainingManifest = new ArrayList<>();
        for (CourierData.ManifestEntry entry : data.requestManifest()) {
            int available = Math.min(entry.amount(), Math.max(0,
                    CourierInventory.count(courierInventory, entry.prototype()) - entry.baseline()));
            if (available <= 0) continue; // Remotely removed cargo already reached the owner.
            ItemStack extracted = CourierInventory.extractAboveBaseline(
                    courierInventory, entry.prototype(), entry.baseline(), available);
            ItemStack remainder = CourierInventory.insert(destination, extracted);
            if (!remainder.isEmpty()) {
                ItemStack impossible = CourierInventory.insert(courierInventory, remainder);
                if (!impossible.isEmpty()) data.pendingCargo().add(impossible.copy());
                remainingManifest.add(new CourierData.ManifestEntry(
                        entry.prototype(), remainder.getCount() - impossible.getCount(), entry.baseline()));
            }
        }
        data.requestManifest().clear();
        data.requestManifest().addAll(remainingManifest);

        List<ItemStack> pending = new ArrayList<>();
        for (ItemStack stack : data.pendingCargo()) {
            ItemStack remainder = CourierInventory.insert(destination, stack);
            if (!remainder.isEmpty()) pending.add(remainder.copy());
        }
        data.pendingCargo().clear();
        data.pendingCargo().addAll(pending);
        return data.requestManifest().isEmpty() && data.pendingCargo().isEmpty();
    }

    private static void beginReturn(EntityMaid courier, CourierData.Data data) {
        resetHandoff(data);
        if (data.transportMode().usesEnderPocket()) {
            completeRemoteTransaction(courier, data);
            return;
        }
        if (data.originOwner() && courier.getOwner() instanceof ServerPlayer owner
                && owner.level() == courier.level()
                && courier.distanceToSqr(owner) <= HANDOFF_DISTANCE * HANDOFF_DISTANCE) {
            completeReturn(courier, data);
        } else {
            data.phase(CourierData.Phase.RETURNING_TO_ORIGIN);
            sync(courier, data);
        }
    }

    private static void returnToOrigin(ServerLevel level, EntityMaid courier, CourierData.Data data) {
        ServerPlayer owner = ownerPlayer(level, courier);
        if (data.originOwner() && data.transportMode().usesBroom()
                && (owner == null || owner.level() != level
                || !CourierFlightPolicy.supportsBroomDimension(owner.level().dimension().location()))) {
            pauseBroomForDimension(courier, data,
                    "message.maid_storage_manager_extension.courier.owner_must_be_overworld");
            return;
        }
        if (data.originOwner() && owner != null && owner.level() == level) {
            BlockPos target = ensureOwnerTarget(level, courier, data, owner);
            if (target == null) {
                pauseForUnavailableTarget(courier, data,
                        "message.maid_storage_manager_extension.courier.return_target_unavailable");
                return;
            }
            boolean reachedTarget = courier.distanceToSqr(target.getCenter())
                    <= POSITION_ARRIVAL_DISTANCE * POSITION_ARRIVAL_DISTANCE;
            if (courier.distanceToSqr(owner) <= HANDOFF_DISTANCE * HANDOFF_DISTANCE) {
                CourierBroomFlightService.cleanup(courier, data);
                faceEachOther(courier, owner);
                completeReturn(courier, data);
            } else if (reachedTarget) {
                pauseMovement(courier);
                data.phase(CourierData.Phase.WAITING_OWNER_PICKUP);
                sync(courier, data);
                notifyOwner(courier,
                        "message.maid_storage_manager_extension.courier.waiting_owner_pickup");
            } else {
                if (data.transportMode().usesBroom()
                        && !CourierBroomFlightService.landedFor(data, data.phase())) {
                    return;
                } else {
                    if (navigateGround(level, courier, data, target, 1)
                            == GroundNavigationResult.EXHAUSTED) {
                        pauseMovement(courier);
                        data.phase(CourierData.Phase.WAITING_OWNER_PICKUP);
                        sync(courier, data);
                        notifyOwner(courier,
                                "message.maid_storage_manager_extension.courier.waiting_owner_pickup");
                    }
                }
            }
            return;
        }
        if (data.originDimension() == null || !dimension(level).equals(data.originDimension())
                || data.originPos() == null) {
            pauseForUnavailableTarget(courier, data,
                    "message.maid_storage_manager_extension.courier.return_target_unavailable");
            return;
        }
        if (courier.distanceToSqr(data.originPos().getCenter())
                <= POSITION_ARRIVAL_DISTANCE * POSITION_ARRIVAL_DISTANCE) {
            pauseMovement(courier);
            completeReturn(courier, data);
        } else {
            MemoryUtil.setTarget(courier, data.originPos(), (float) Config.collectSpeed);
            MemoryUtil.setLookAt(courier, data.originPos());
        }
    }

    private static void completeReturn(EntityMaid courier, CourierData.Data data) {
        pauseMovement(courier);
        CourierBroomFlightService.cleanup(courier, data);
        restoreFollowIfBesideOwner(courier, data);
        data.clearRequest();
        data.clearDeposit();
        data.clearRoute();
        data.phase(data.warehouse() == null ? CourierData.Phase.UNBOUND : CourierData.Phase.IDLE);
        sync(courier, data);
        notifyOwner(courier, "message.maid_storage_manager_extension.courier.return_complete");
    }

    private enum GroundNavigationResult {
        MOVING,
        RECOVERED,
        EXHAUSTED
    }

    private static GroundNavigationResult navigateGround(ServerLevel level, EntityMaid courier,
                                                         CourierData.Data data, LivingEntity target,
                                                         int closeEnoughDistance) {
        boolean targetChanged = MemoryUtil.getTargetPos(courier) == null
                || data.groundApproachPhase() != data.phase();
        MemoryUtil.setTarget(courier, target, (float) Config.collectSpeed, closeEnoughDistance);
        MemoryUtil.setLookAt(courier, target);
        if (CourierGroundNavigationPolicy.shouldDispatchRoute(targetChanged,
                courier.getNavigation().isDone(), level.getGameTime())) {
            courier.getNavigation().moveTo(target, Config.collectSpeed);
        }

        CourierGroundRecoveryPolicy.Update update = updateGroundProgress(level, courier, data);
        if (!update.shouldTeleport()) return GroundNavigationResult.MOVING;

        // This is Touhou Little Maid's own safe teleport: it samples ten walkable positions
        // around the target, rejects avoided blocks/collisions, and clears stale path memories.
        if (CourierGroundNavigationPolicy.mayUseSafeTeleport(data.transportMode(),
                target instanceof ServerPlayer)
                && courier.teleportToOwner(target)) {
            courier.getNavigationManager().resetNavigation();
            MemoryUtil.clearTarget(courier);
            data.clearGroundApproach();
            return GroundNavigationResult.RECOVERED;
        }
        return recordGroundRecoveryFailure(data);
    }

    private static GroundNavigationResult navigateGround(ServerLevel level, EntityMaid courier,
                                                         CourierData.Data data, BlockPos target,
                                                         int closeEnoughDistance) {
        BlockPos previous = MemoryUtil.getTargetPos(courier);
        boolean targetChanged = previous == null || !previous.equals(target)
                || data.groundApproachPhase() != data.phase();
        MemoryUtil.setTarget(courier, target, (float) Config.collectSpeed);
        MemoryUtil.setLookAt(courier, target);
        if (CourierGroundNavigationPolicy.shouldDispatchRoute(targetChanged,
                courier.getNavigation().isDone(), level.getGameTime())) {
            courier.getNavigation().moveTo(target.getX() + 0.5, target.getY(),
                    target.getZ() + 0.5, Config.collectSpeed);
        }

        CourierGroundRecoveryPolicy.Update update = updateGroundProgress(level, courier, data);
        if (!update.shouldTeleport()) return GroundNavigationResult.MOVING;
        // There is no living target for Touhou Little Maid's teleport helper. Three complete
        // stall windows therefore hand control to the explicit chest/landing fallback.
        return recordGroundRecoveryFailure(data);
    }

    private static CourierGroundRecoveryPolicy.Update updateGroundProgress(
            ServerLevel level, EntityMaid courier, CourierData.Data data) {
        CourierGroundRecoveryPolicy.Update update = CourierGroundRecoveryPolicy.update(
                data.groundApproachPhase(), data.groundApproachPos(),
                data.groundApproachProgressGameTime(), data.phase(),
                courier.blockPosition(), level.getGameTime());
        if (update.progressed()) data.groundTeleportFailures(0);
        if (update.changed()) {
            data.groundApproach(update.phase(), update.position(), update.progressGameTime());
        }
        return update;
    }

    private static GroundNavigationResult recordGroundRecoveryFailure(CourierData.Data data) {
        CourierLandingSearchPolicy.Failure failure = CourierLandingSearchPolicy.fail(
                data.groundTeleportFailures());
        data.groundTeleportFailures(failure.failures());
        if (failure.reselect()) {
            data.clearGroundApproach();
            return GroundNavigationResult.EXHAUSTED;
        }
        return GroundNavigationResult.MOVING;
    }

    private static void completeRemoteTransaction(EntityMaid courier, CourierData.Data data) {
        pauseMovement(courier);
        CourierBroomFlightService.cleanup(courier, data);
        if (CourierRuntimePolicy.shouldAnchorAfterRemoteTransaction(data.transportMode())) {
            setHomeAtCurrentPosition(courier);
            data.clearFollowOverride();
        }
        data.clearRequest();
        data.clearDeposit();
        data.clearRoute();
        data.phase(data.warehouse() == null ? CourierData.Phase.UNBOUND : CourierData.Phase.IDLE);
        sync(courier, data);
        notifyOwner(courier, "message.maid_storage_manager_extension.courier.remote_ready");
    }

    private static boolean restoreFollowIfBesideOwner(EntityMaid courier, CourierData.Data data) {
        if (!data.followOverrideActive()) return true;
        if (!(courier.getOwner() instanceof ServerPlayer owner)
                || owner.level() != courier.level()
                || courier.distanceToSqr(owner) > OWNER_ANCHOR_RANGE * OWNER_ANCHOR_RANGE) {
            return false;
        }
        if (data.stayHomeAfterDelivery()) {
            setHomeAtCurrentPosition(courier);
        } else {
            courier.setHomeModeEnable(false);
        }
        data.clearFollowOverride();
        sync(courier, data);
        return true;
    }

    private static void setHomeAtCurrentPosition(EntityMaid courier) {
        BlockPos home = courier.blockPosition();
        var schedule = courier.getSchedulePos();
        schedule.setWorkPos(home);
        schedule.setIdlePos(home);
        schedule.setSleepPos(home);
        schedule.setDimension(courier.level().dimension().location());
        schedule.setConfigured(true);
        courier.setHomeModeEnable(true);
        schedule.restrictTo(courier);
    }

    private static boolean redirectOwnerDeliveryToChest(EntityMaid courier,
                                                         CourierData.Data data) {
        boolean hasTarget = data.deliveryPos() != null && data.deliveryDimension() != null;
        boolean hasCargo = hasDeliveryCargo(data);
        if (!CourierDeliveryPolicy.shouldRedirectToChest(
                data.phase(), hasTarget, hasCargo)) return false;
        pauseMovement(courier);
        resetHandoff(data);
        data.targetWarningSent(false);
        data.phase(CourierData.Phase.TRAVEL_TO_DELIVERY_CHEST);
        sync(courier, data);
        return true;
    }

    private static void validateDeliveryTarget(ServerLevel courierLevel, EntityMaid courier,
                                               CourierData.Data data) {
        BlockPos target = data.deliveryPos();
        ResourceLocation targetDimension = data.deliveryDimension();
        if (target == null || targetDimension == null) return;
        ServerLevel targetLevel = null;
        for (ServerLevel candidate : courierLevel.getServer().getAllLevels()) {
            if (targetDimension.equals(dimension(candidate))) {
                targetLevel = candidate;
                break;
            }
        }
        if (targetLevel == null) {
            clearDeliveryTarget(courier, data);
            notifyOwner(courier,
                    "message.maid_storage_manager_extension.courier.delivery_chest_unavailable");
            return;
        }
        boolean loaded = targetLevel.hasChunkAt(target);
        boolean hasHandler = loaded && deliveryHandler(targetLevel, target) != null;
        if (!CourierDeliveryPolicy.shouldInvalidateTarget(loaded, hasHandler)) return;
        clearDeliveryTarget(courier, data);
        notifyOwner(courier,
                "message.maid_storage_manager_extension.courier.delivery_chest_unavailable");
    }

    private static void clearDeliveryTarget(EntityMaid courier, CourierData.Data data) {
        CourierData.Phase previous = data.phase();
        CourierData.Phase next = CourierDeliveryPolicy.afterTargetCleared(
                previous, hasDeliveryCargo(data));
        data.deliveryTarget(null, null);
        data.targetWarningSent(false);
        if (next != previous) {
            pauseMovement(courier);
            resetHandoff(data);
            data.phase(next);
            if (data.flightLeg() == previous && next == CourierData.Phase.TRAVEL_TO_OWNER) {
                data.retargetFlight(next);
            }
        }
        sync(courier, data);
    }

    private static boolean hasDeliveryCargo(CourierData.Data data) {
        return !data.requestManifest().isEmpty() || !data.pendingCargo().isEmpty();
    }

    private static void finishWorkClear(EntityMaid courier, CourierData.Data data) {
        pauseMovement(courier);
        CourierBroomFlightService.cleanup(courier, data);
        if (!data.pendingList().isEmpty()) tryReturnPendingList(courier, data);
        data.deliveryTarget(null, null);
        data.clearRequest();
        data.clearDeposit();
        data.clearRoute();
        setHomeAtCurrentPosition(courier);
        data.clearFollowOverride();
        data.phase(data.warehouse() == null ? CourierData.Phase.UNBOUND : CourierData.Phase.IDLE);
        sync(courier, data);
    }

    private static boolean hasDispatchableRequest(EntityMaid courier) {
        IItemHandlerModifiable inventory = courier.getAvailableInv(false);
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (stack.is(ItemRegistry.REQUEST_LIST_ITEM.get())
                    && !RequestListItem.isIgnored(stack) && !isActiveCourierList(stack)) {
                return true;
            }
        }
        return false;
    }

    private static void dispatchRequest(EntityMaid courier, EntityMaid warehouse,
                                        CourierData.Data data) {
        if (!mayMutateWarehouseForCourier(warehouse)) return;
        IItemHandlerModifiable source = courier.getAvailableInv(false);
        for (int slot = 0; slot < source.getSlots(); slot++) {
            ItemStack list = source.getStackInSlot(slot);
            if (!list.is(ItemRegistry.REQUEST_LIST_ITEM.get())
                    || RequestListItem.isIgnored(list) || isActiveCourierList(list)) continue;

            ItemStack outgoing = list.copyWithCount(1);
            prepareRequestTag(outgoing, courier);
            ItemStack remainder = CourierInventory.insert(warehouse.getAvailableInv(false), outgoing);
            if (!remainder.isEmpty()) {
                if (!data.spaceWarningSent()) {
                    data.spaceWarningSent(true);
                    notifyOwner(courier,
                            "message.maid_storage_manager_extension.courier.warehouse_inventory_full");
                }
                resetHandoff(data);
                sync(courier, data);
                return;
            }
            if (!CourierInventory.removeExactSlot(source, slot, list.copyWithCount(1))) {
                CourierInventory.extract(warehouse.getAvailableInv(false), outgoing, 1);
                resetHandoff(data);
                sync(courier, data);
                return;
            }
            data.requestFinished(false);
            data.phase(CourierData.Phase.REQUEST_RUNNING);
            data.spaceWarningSent(false);
            resetHandoff(data);
            courier.swing(InteractionHand.MAIN_HAND, true);
            warehouse.swing(InteractionHand.MAIN_HAND, true);
            sync(courier, data);
            notifyOwner(courier, "message.maid_storage_manager_extension.courier.request_dispatched",
                    warehouse.getName());
            return;
        }
        notifyOwner(courier, "message.maid_storage_manager_extension.courier.request_list_missing");
        beginReturn(courier, data);
    }

    private static boolean isActiveCourierList(ItemStack list) {
        CompoundTag root = list.getTag();
        return root != null && root.contains(META, Tag.TAG_COMPOUND)
                && root.getCompound(META).hasUUID(META_COURIER);
    }

    private static void prepareRequestTag(ItemStack list, EntityMaid courier) {
        CompoundTag root = list.getOrCreateTag();
        CompoundTag meta = new CompoundTag();
        meta.putUUID(META_COURIER, courier.getUUID());
        if (root.contains(RequestListItem.TAG_STORAGE)) {
            Tag original = root.get(RequestListItem.TAG_STORAGE);
            if (original != null) meta.put(META_ORIGINAL_STORAGE, original.copy());
        }
        if (root.hasUUID(RequestListItem.TAG_STORAGE_ENTITY)) {
            meta.putUUID(META_ORIGINAL_ENTITY, root.getUUID(RequestListItem.TAG_STORAGE_ENTITY));
        }
        root.put(META, meta);
        root.remove(RequestListItem.TAG_STORAGE);
        root.putUUID(RequestListItem.TAG_STORAGE_ENTITY, courier.getUUID());
    }

    public static boolean finishRequest(EntityMaid warehouse, ItemStack list) {
        CompoundTag root = list.getTag();
        if (root == null || !root.contains(META, Tag.TAG_COMPOUND)) return false;
        CompoundTag meta = root.getCompound(META);
        if (!meta.hasUUID(META_COURIER)) return false;
        Entity entity = ((ServerLevel) warehouse.level()).getEntity(meta.getUUID(META_COURIER));
        if (!(entity instanceof EntityMaid courier)) return false;

        CourierData.Data data = CourierData.get(courier);
        if (!warehouse.getUUID().equals(data.warehouse())
                || (data.phase() != CourierData.Phase.REQUEST_RUNNING
                && data.phase() != CourierData.Phase.REQUEST_WAITING_SPACE)) return false;

        boolean success = RequestListItem.isAllSuccess(list);
        MinecraftForge.EVENT_BUS.post(new RequestListStatusChangeEvent(
                RequestListStatusChangeEvent.Status.END, warehouse,
                RequestListItem.getUUID(list), list));
        restoreRequestTag(list, meta);
        list.getOrCreateTag().putBoolean(RequestListItem.TAG_IGNORE_TASK, true);
        list.getOrCreateTag().getCompound(META).putBoolean(META_FAILURE_RETURN, !success);

        if (!returnList(courier, list, !success)) data.pendingList(list);
        data.requestFinished(true);
        data.phase(data.transportMode().usesEnderPocket()
                ? CourierData.Phase.IDLE : physicalDeliveryPhase(data));
        data.spaceWarningSent(false);
        resetHandoff(data);
        sync(courier, data);
        notifyOwner(courier, success
                ? "message.maid_storage_manager_extension.courier.request_complete"
                : "message.maid_storage_manager_extension.courier.request_incomplete");

        MemoryUtil.getRequestProgress(warehouse).clearTarget();
        MemoryUtil.getRequestProgress(warehouse).stopWork();
        warehouse.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        if (data.transportMode().usesEnderPocket()) {
            completeRemoteTransaction(courier, data);
        }
        return true;
    }

    private static void restoreRequestTag(ItemStack list, CompoundTag meta) {
        CompoundTag root = list.getOrCreateTag();
        root.remove(RequestListItem.TAG_STORAGE);
        root.remove(RequestListItem.TAG_STORAGE_ENTITY);
        if (meta.contains(META_ORIGINAL_STORAGE)) {
            Tag original = meta.get(META_ORIGINAL_STORAGE);
            if (original != null) root.put(RequestListItem.TAG_STORAGE, original.copy());
        }
        if (meta.hasUUID(META_ORIGINAL_ENTITY)) {
            root.putUUID(RequestListItem.TAG_STORAGE_ENTITY, meta.getUUID(META_ORIGINAL_ENTITY));
        }
        CompoundTag returned = new CompoundTag();
        returned.putBoolean(META_FAILURE_RETURN, false);
        root.put(META, returned);
    }

    private static boolean returnList(EntityMaid courier, ItemStack list, boolean failure) {
        if (!failure) {
            return CourierInventory.insert(courier.getAvailableBackpackInv(), list).isEmpty();
        }
        ItemStack offhand = courier.getOffhandItem();
        if (!offhand.isEmpty()) {
            ItemStack remainder = CourierInventory.insert(courier.getAvailableBackpackInv(), offhand);
            if (!remainder.isEmpty()) return false;
        }
        courier.setItemInHand(InteractionHand.OFF_HAND, list.copy());
        return true;
    }

    private static void tryReturnPendingList(EntityMaid courier, CourierData.Data data) {
        ItemStack list = data.pendingList();
        CompoundTag root = list.getTag();
        boolean failure = root != null && root.contains(META, Tag.TAG_COMPOUND)
                && root.getCompound(META).getBoolean(META_FAILURE_RETURN);
        if (returnList(courier, list, failure)) {
            data.pendingList(ItemStack.EMPTY);
            sync(courier, data);
        }
    }

    /** Called from the request-return mixin at the physical warehouse/courier meeting point. */
    public static boolean acceptRequestPayload(EntityMaid warehouse, EntityMaid courier,
                                               ItemStack payload) {
        CourierData.Data data = CourierData.get(courier);
        if (data.warehouse() == null || !data.warehouse().equals(warehouse.getUUID())
                || (data.phase() != CourierData.Phase.REQUEST_RUNNING
                && data.phase() != CourierData.Phase.REQUEST_WAITING_SPACE)
                || courier.distanceToSqr(warehouse) > HANDOFF_DISTANCE * HANDOFF_DISTANCE) {
            return false;
        }
        // A task switch can leave this courier recovering an older exact-NBT misc-sort batch.
        // Keep the warehouse delivery in the courier journal until that unique-source return
        // finishes instead of exposing either transaction to the other's inventory accounting.
        if (!CourierSortMutex.mayCourierUseOwnInventory(
                ExtensionMemoryUtil.getMiscSort(courier).hasInFlight())) {
            data.pendingCargo().add(payload.copy());
            data.phase(CourierData.Phase.REQUEST_WAITING_SPACE);
            if (!data.spaceWarningSent()) {
                data.spaceWarningSent(true);
                notifyOwner(courier, "message.maid_storage_manager_extension.courier.waiting_for_space");
            }
            sync(courier, data);
            return true;
        }
        faceEachOther(courier, warehouse);
        courier.swing(InteractionHand.MAIN_HAND, true);
        warehouse.swing(InteractionHand.MAIN_HAND, true);

        int baseline = CourierInventory.count(courier.getAvailableInv(false), payload);
        ItemStack remainder = CourierInventory.insert(courier.getAvailableInv(false), payload);
        int moved = payload.getCount() - remainder.getCount();
        if (moved > 0) mergeManifest(data.requestManifest(), payload, moved, baseline);
        if (!remainder.isEmpty()) {
            data.pendingCargo().add(remainder.copy());
            data.phase(CourierData.Phase.REQUEST_WAITING_SPACE);
            if (!data.spaceWarningSent()) {
                data.spaceWarningSent(true);
                notifyOwner(courier, "message.maid_storage_manager_extension.courier.waiting_for_space");
            }
        }
        sync(courier, data);
        return true;
    }

    /** Final face-to-face recovery after upstream request return pathing has stalled. */
    public static boolean tryRecoverRequestHandoff(EntityMaid warehouse, EntityMaid courier,
                                                   ItemStack payload) {
        if (acceptRequestPayload(warehouse, courier, payload)) return true;
        if (warehouse.level() != courier.level() || !warehouse.isAlive() || !courier.isAlive()) {
            return false;
        }
        CourierData.Data data = CourierData.get(courier);
        if (!warehouse.getUUID().equals(data.warehouse())
                || (data.phase() != CourierData.Phase.REQUEST_RUNNING
                && data.phase() != CourierData.Phase.REQUEST_WAITING_SPACE)) return false;
        if (!warehouse.teleportToOwner(courier)) return false;
        warehouse.getNavigationManager().resetNavigation();
        MemoryUtil.clearTarget(warehouse);
        return acceptRequestPayload(warehouse, courier, payload);
    }

    private static void drainPendingCargo(EntityMaid courier, CourierData.Data data) {
        if (data.pendingCargo().isEmpty()
                || data.phase() == CourierData.Phase.TRAVEL_TO_OWNER
                || data.phase() == CourierData.Phase.TRAVEL_TO_DELIVERY_CHEST
                || data.phase() == CourierData.Phase.DELIVERY_CHEST_WAITING_SPACE
                || data.phase() == CourierData.Phase.WAITING_WITH_CARGO_AT_DELIVERY_CHEST
                || data.phase() == CourierData.Phase.WAITING_OWNER_PICKUP
                || data.phase() == CourierData.Phase.OWNER_HANDOFF
                || data.phase() == CourierData.Phase.OWNER_WAITING_SPACE) return;
        List<ItemStack> remaining = new ArrayList<>();
        boolean changed = false;
        for (ItemStack stack : data.pendingCargo()) {
            int baseline = CourierInventory.count(courier.getAvailableInv(false), stack);
            ItemStack remainder = CourierInventory.insert(courier.getAvailableInv(false), stack);
            int moved = stack.getCount() - remainder.getCount();
            if (moved > 0 && CourierSortMutex.isActiveTransaction(data.phase())) {
                mergeManifest(data.requestManifest(), stack, moved, baseline);
                changed = true;
            }
            if (!remainder.isEmpty()) remaining.add(remainder.copy());
        }
        if (changed || remaining.size() != data.pendingCargo().size()) {
            data.pendingCargo().clear();
            data.pendingCargo().addAll(remaining);
            sync(courier, data);
        }
    }

    private static void dispatchDeposit(ServerLevel level, EntityMaid courier, EntityMaid warehouse,
                                        CourierData.Data data) {
        if (!mayMutateWarehouseForCourier(warehouse)) return;
        IItemHandlerModifiable source = courier.getAvailableInv(false);
        var destination = warehouse.getAvailableInv(false);
        List<CourierData.ManifestEntry> manifest = new ArrayList<>();
        for (int slot = 0; slot < source.getSlots(); slot++) {
            ItemStack stack = source.getStackInSlot(slot);
            if (stack.isEmpty() || stack.is(ItemRegistry.REQUEST_LIST_ITEM.get())
                    || EnderPocketCompat.isBroom(stack)) continue;
            ItemStack offered = stack.copy();
            int baseline = CourierInventory.count(destination, offered);
            ItemStack remainder = CourierInventory.insert(destination, offered);
            int moved = offered.getCount() - remainder.getCount();
            if (moved <= 0) continue;
            source.extractItem(slot, moved, false);
            mergeManifest(manifest, offered, moved, baseline);
        }
        data.depositRequested(false);
        resetHandoff(data);
        courier.swing(InteractionHand.MAIN_HAND, true);
        warehouse.swing(InteractionHand.MAIN_HAND, true);
        if (manifest.isEmpty()) {
            data.clearDeposit();
            sync(courier, data);
            notifyOwner(courier, "message.maid_storage_manager_extension.courier.nothing_to_deposit");
            beginReturn(courier, data);
            return;
        }
        data.depositManifest().clear();
        data.depositManifest().addAll(manifest);
        int remaining = depositRemaining(warehouse, data);
        data.lastDepositRemaining(remaining);
        data.lastDepositProgressGameTime(level.getGameTime());
        data.phase(CourierData.Phase.DEPOSIT_RUNNING);
        sync(courier, data);
        notifyOwner(courier, "message.maid_storage_manager_extension.courier.deposit_dispatched",
                warehouse.getName());
    }

    private static void mergeManifest(List<CourierData.ManifestEntry> manifest,
                                      ItemStack prototype, int moved, int baseline) {
        for (int i = 0; i < manifest.size(); i++) {
            CourierData.ManifestEntry entry = manifest.get(i);
            if (ItemStack.isSameItemSameTags(entry.prototype(), prototype)) {
                manifest.set(i, new CourierData.ManifestEntry(prototype,
                        entry.amount() + moved, Math.min(entry.baseline(), baseline)));
                return;
            }
        }
        manifest.add(new CourierData.ManifestEntry(prototype, moved, baseline));
    }

    private static int depositRemaining(EntityMaid warehouse, CourierData.Data data) {
        int total = 0;
        for (CourierData.ManifestEntry entry : data.depositManifest()) {
            total += Math.min(entry.amount(), Math.max(0,
                    CourierInventory.count(warehouse.getAvailableInv(false), entry.prototype())
                            - entry.baseline()));
        }
        return total;
    }

    private static void monitorDeposit(ServerLevel level, EntityMaid courier, EntityMaid warehouse,
                                       CourierData.Data data) {
        int remaining = depositRemaining(warehouse, data);
        if (remaining <= 0) {
            data.clearDeposit();
            sync(courier, data);
            notifyOwner(courier, "message.maid_storage_manager_extension.courier.deposit_complete");
            beginReturn(courier, data);
            return;
        }
        if (remaining < data.lastDepositRemaining()) {
            data.lastDepositRemaining(remaining);
            data.lastDepositProgressGameTime(level.getGameTime());
            sync(courier, data);
        } else if (level.getGameTime() - data.lastDepositProgressGameTime() >= DEPOSIT_STALL_TICKS) {
            data.phase(CourierData.Phase.DEPOSIT_RETURNING);
            sync(courier, data);
            notifyOwner(courier, "message.maid_storage_manager_extension.courier.storage_full_returning");
        }
    }

    private static void returnDeposit(EntityMaid courier, EntityMaid warehouse,
                                      CourierData.Data data) {
        if (!mayMutateWarehouseForCourier(warehouse)) return;
        faceEachOther(courier, warehouse);
        for (CourierData.ManifestEntry entry : data.depositManifest()) {
            int available = Math.min(entry.amount(), Math.max(0,
                    CourierInventory.count(warehouse.getAvailableInv(false), entry.prototype())
                            - entry.baseline()));
            if (available <= 0) continue;
            ItemStack extracted = CourierInventory.extractAboveBaseline(
                    warehouse.getAvailableInv(false), entry.prototype(), entry.baseline(), available);
            int courierBaseline = CourierInventory.count(courier.getAvailableInv(false), extracted);
            ItemStack remainder = CourierInventory.insert(courier.getAvailableInv(false), extracted);
            int moved = extracted.getCount() - remainder.getCount();
            if (moved > 0) mergeManifest(data.requestManifest(), extracted, moved, courierBaseline);
            if (!remainder.isEmpty()) data.pendingCargo().add(remainder.copy());
        }
        if (depositRemaining(warehouse, data) <= 0) {
            courier.swing(InteractionHand.MAIN_HAND, true);
            warehouse.swing(InteractionHand.MAIN_HAND, true);
            data.clearDeposit();
            data.phase(data.transportMode().usesEnderPocket()
                    ? CourierData.Phase.IDLE
                    : data.requestManifest().isEmpty() && data.pendingCargo().isEmpty()
                    ? CourierData.Phase.RETURNING_TO_ORIGIN : physicalDeliveryPhase(data));
            sync(courier, data);
            notifyOwner(courier, "message.maid_storage_manager_extension.courier.deposit_returned");
            if (data.transportMode().usesEnderPocket()) {
                completeRemoteTransaction(courier, data);
            } else if (data.phase() == CourierData.Phase.RETURNING_TO_ORIGIN) {
                beginReturn(courier, data);
            }
        }
    }

    private static void enterHandoff(EntityMaid courier, CourierData.Data data,
                                     CourierData.Phase phase, long gameTime) {
        if (data.phase() == phase) return;
        data.phase(phase);
        data.handoffStartedGameTime(gameTime);
        sync(courier, data);
    }

    private static CourierData.Phase physicalDeliveryPhase(CourierData.Data data) {
        return data.deliveryPos() != null && data.deliveryDimension() != null
                ? CourierData.Phase.TRAVEL_TO_DELIVERY_CHEST
                : CourierData.Phase.TRAVEL_TO_OWNER;
    }

    private static boolean handoffReady(ServerLevel level, EntityMaid courier, Entity target,
                                        CourierData.Data data) {
        faceEachOther(courier, target);
        if (data.handoffStartedGameTime() < 0L) {
            data.handoffStartedGameTime(level.getGameTime());
            sync(courier, data);
            return false;
        }
        return level.getGameTime() - data.handoffStartedGameTime() >= HANDOFF_TICKS;
    }

    private static void resetHandoff(CourierData.Data data) {
        data.handoffStartedGameTime(-1L);
    }

    private static void faceEachOther(EntityMaid courier, Entity target) {
        pauseMovement(courier);
        MemoryUtil.setLookAt(courier, target);
        if (target instanceof EntityMaid other) {
            other.getNavigation().stop();
            MemoryUtil.setLookAt(other, courier);
        }
    }

    private static void pauseMovement(EntityMaid courier) {
        courier.getNavigation().stop();
        MemoryUtil.clearTarget(courier);
    }

    private static void pauseForUnavailableTarget(EntityMaid courier, CourierData.Data data,
                                                   String message) {
        pauseMovement(courier);
        if (!data.targetWarningSent()) {
            data.targetWarningSent(true);
            sync(courier, data);
            notifyOwner(courier, message);
        }
    }

    private static IItemHandler deliveryHandler(ServerLevel level, BlockPos position) {
        if (position == null || !level.hasChunkAt(position)) return null;
        var blockEntity = level.getBlockEntity(position);
        if (blockEntity == null) return null;
        return blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER, null).orElse(null);
    }

    private static EntityMaid resolveWarehouse(ServerLevel level, CourierData.Data data) {
        if (data.warehouse() == null) return null;
        Entity entity = level.getEntity(data.warehouse());
        return entity instanceof EntityMaid maid ? maid : null;
    }

    private static boolean validWarehouseLink(EntityMaid courier, EntityMaid warehouse) {
        return warehouse.isAlive()
                && warehouse.getTask().getUid().equals(StorageManageTask.TASK_ID)
                && WarehouseCourierData.get(warehouse).isAuthorized(courier.getUUID());
    }

    private static void bind(CourierData.Data data, EntityMaid warehouse,
                             CourierData.WarehouseBinding stationBinding) {
        CourierData.WarehouseBinding binding = new CourierData.WarehouseBinding(
                warehouse.getUUID(), warehouseAnchor(warehouse),
                dimension((ServerLevel) warehouse.level()), stationBinding.mailboxPos(),
                stationBinding.mailboxDimension(), stationBinding.stationPos(),
                stationBinding.stationDimension(), warehouse.getName().getString());
        data.addWarehouse(binding);
    }

    private static boolean validStation(ServerLevel level, CourierData.WarehouseBinding binding) {
        if (!configuredStation(level, binding)) return false;
        level.getChunkAt(binding.mailboxPos());
        level.getChunkAt(binding.stationPos());
        return CourierWarehouseStationService.isValid(level, binding);
    }

    private static boolean configuredStation(ServerLevel level,
                                             CourierData.WarehouseBinding binding) {
        return binding != null && binding.hasStation()
                && binding.mailboxDimension().equals(dimension(level))
                && binding.stationDimension().equals(dimension(level));
    }

    private static void warnStationUnavailable(EntityMaid courier, CourierData.Data data) {
        pauseMovement(courier);
        if (data.targetWarningSent()) return;
        data.targetWarningSent(true);
        sync(courier, data);
        notifyOwner(courier,
                "message.maid_storage_manager_extension.courier.station_unavailable");
    }

    private static BlockPos warehouseAnchor(EntityMaid warehouse) {
        return warehouse.hasRestriction() ? warehouse.getRestrictCenter() : warehouse.blockPosition();
    }

    private static ResourceLocation dimension(ServerLevel level) {
        return level.dimension().location();
    }

    private static boolean isCourierOwnedBy(EntityMaid courier, ServerPlayer owner) {
        return courier.isAlive() && courier.isOwnedBy(owner)
                && courier.getTask().getUid().equals(CourierTask.TASK_ID);
    }

    private static EntityMaid findNearestWarehouse(ServerLevel level, EntityMaid courier, int range) {
        return level.getEntitiesOfClass(EntityMaid.class,
                        courier.getBoundingBox().inflate(range), maid -> maid.isAlive()
                                && maid.getTask().getUid().equals(StorageManageTask.TASK_ID)
                                && withinHorizontal(courier.blockPosition(), maid.blockPosition(), range))
                .stream().min(java.util.Comparator.comparingDouble(courier::distanceToSqr))
                .orElse(null);
    }

    public static boolean hasActiveTransaction(EntityMaid courier) {
        return CourierSortMutex.isActiveTransaction(CourierData.get(courier).phase());
    }

    /** Returns true when an authorized courier linked to this warehouse owns an active journal. */
    public static boolean hasActiveWarehouseTransaction(EntityMaid warehouse) {
        if (!warehouse.getTask().getUid().equals(StorageManageTask.TASK_ID)
                || !(warehouse.level() instanceof ServerLevel warehouseLevel)) return false;
        UUID warehouseId = warehouse.getUUID();
        WarehouseCourierData.Data warehouseData = WarehouseCourierData.get(warehouse);
        for (UUID courierId : warehouseData.authorized()) {
            EntityMaid courier = findMaid(warehouseLevel, courierId);
            if (courier == null) continue;
            CourierData.Data courierData = CourierData.get(courier);
            if (CourierSortMutex.isActiveForWarehouse(warehouseId, courierData.warehouse(),
                    warehouseData.isAuthorized(courierId), courierData.phase())) {
                return true;
            }
        }
        return false;
    }

    private static EntityMaid findMaid(ServerLevel warehouseLevel, UUID entityId) {
        for (ServerLevel level : warehouseLevel.getServer().getAllLevels()) {
            Entity entity = level.getEntity(entityId);
            if (entity instanceof EntityMaid maid && maid.isAlive()) return maid;
        }
        return null;
    }

    private static boolean mayMutateWarehouseForCourier(EntityMaid warehouse) {
        return CourierSortMutex.mayCourierMutateWarehouse(
                ExtensionMemoryUtil.getMiscSort(warehouse).hasInFlight());
    }

    private static void notifyOwner(EntityMaid maid, String key, Object... args) {
        if (!(maid.level() instanceof ServerLevel level)) return;
        ServerPlayer owner = ownerPlayer(level, maid);
        if (owner != null) owner.sendSystemMessage(Component.translatable(key, args));
    }

    private static ServerPlayer ownerPlayer(ServerLevel level, EntityMaid courier) {
        UUID ownerId = courier.getOwnerUUID();
        return ownerId == null ? null : level.getServer().getPlayerList().getPlayer(ownerId);
    }

    private static boolean isOwnerFlightLeg(CourierData.Data data) {
        return data.phase() == CourierData.Phase.TRAVEL_TO_OWNER
                || data.phase() == CourierData.Phase.RETURNING_TO_ORIGIN && data.originOwner();
    }

    private static boolean ownerAvailableForBroom(ServerLevel level, EntityMaid courier) {
        ServerPlayer owner = ownerPlayer(level, courier);
        return owner != null && owner.level() == level
                && CourierFlightPolicy.supportsBroomDimension(owner.level().dimension().location());
    }

    private static boolean broomRouteAvailable(ServerLevel level, EntityMaid courier,
                                               CourierData.Data data) {
        if (!CourierFlightPolicy.supportsBroomDimension(dimension(level))
                || !CourierFlightPolicy.supportsBroomDimension(data.warehouseDimension())) {
            return false;
        }
        CourierData.WarehouseBinding binding = data.binding(data.warehouse());
        if (binding == null
                || !CourierFlightPolicy.supportsBroomDimension(binding.stationDimension())) {
            return false;
        }
        ServerPlayer owner = ownerPlayer(level, courier);
        return owner != null && CourierFlightPolicy.supportsBroomDimension(
                owner.level().dimension().location());
    }

    private static void pauseBroomForDimension(EntityMaid courier, CourierData.Data data,
                                               String messageKey) {
        CourierBroomFlightService.holdPosition(courier);
        pauseMovement(courier);
        if (data.targetWarningSent()) return;
        data.targetWarningSent(true);
        sync(courier, data);
        notifyOwner(courier, messageKey);
    }

    private static void sync(EntityMaid maid, CourierData.Data data) {
        maid.setAndSyncData(CourierData.KEY, data);
    }

    private static void sync(EntityMaid maid, WarehouseCourierData.Data data) {
        maid.setAndSyncData(WarehouseCourierData.KEY, data);
    }
}
