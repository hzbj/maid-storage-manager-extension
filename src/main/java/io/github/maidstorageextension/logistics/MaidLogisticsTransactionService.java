package io.github.maidstorageextension.logistics;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import io.github.maidstorageextension.block.CourierWarehouseStationBlockEntity;
import io.github.maidstorageextension.compat.EnderPocketCompat;
import io.github.maidstorageextension.data.BusinessLicenseData;
import io.github.maidstorageextension.data.CourierData;
import io.github.maidstorageextension.data.DriverData;
import io.github.maidstorageextension.data.MaidLogisticsCourierData;
import io.github.maidstorageextension.data.WarehouseCourierData;
import io.github.maidstorageextension.maid.ExtensionMemoryUtil;
import io.github.maidstorageextension.maid.courier.CourierBroomFlightService;
import io.github.maidstorageextension.maid.courier.CourierFlightPolicy;
import io.github.maidstorageextension.maid.courier.CourierService;
import io.github.maidstorageextension.maid.task.CourierTask;
import io.github.maidstorageextension.network.NetworkWarehouseActionPacket;
import io.github.maidstorageextension.terminal.MailboxWarehouseData;
import io.github.maidstorageextension.terminal.TerminalAccountData;
import io.github.maidstorageextension.terminal.TerminalAccountService;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.registries.ForgeRegistries;
import studio.fantasyit.maid_storage_manager.Config;
import studio.fantasyit.maid_storage_manager.items.RequestListItem;
import studio.fantasyit.maid_storage_manager.registry.ItemRegistry;
import studio.fantasyit.maid_storage_manager.util.MemoryUtil;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Executes one permanent schedule opportunity without changing persistent warehouse bindings. */
public final class MaidLogisticsTransactionService {
    private static final String REQUEST_MARKER = "MaidLogisticsTransaction";
    private static final double ARRIVAL_DISTANCE_SQR = 3.0D * 3.0D;
    private static final int START_INTERVAL = 20;

    private MaidLogisticsTransactionService() {
    }

    public static void tick(ServerLevel level, EntityMaid courier) {
        MaidLogisticsCourierData.Data journal = MaidLogisticsCourierData.get(courier);
        if (journal.active()) {
            tickActive(level, courier, journal);
            return;
        }
        if (courier.tickCount % START_INTERVAL != 0
                || !courier.getTask().getUid().equals(CourierTask.TASK_ID)
                || CourierService.hasActiveTransaction(courier)
                || DriverData.get(courier).activeTrip()
                || ExtensionMemoryUtil.getMiscSort(courier).hasInFlight()) return;

        HeldList held = heldEditableList(courier);
        if (held == null) return;
        MaidLogisticsData routes = MaidLogisticsData.get(level.getServer());
        MaidLogisticsData.Route route = routes.advance(courier.getUUID());
        if (route == null) return;
        TerminalAccountData accounts = TerminalAccountData.get(level.getServer());
        TerminalAccountData.Account account = accounts.byId(route.account());
        if (account == null || !accounts.belongsTo(account, courier.getUUID())) {
            routes.status(route.id(), MaidLogisticsData.RouteStatus.BLOCKED, "courier_not_authorized");
            return;
        }
        String blocker = runtimeBlocker(level, route, courier);
        if (blocker != null) {
            routes.status(route.id(), MaidLogisticsData.RouteStatus.BLOCKED, blocker);
            return;
        }

        UUID requestToken = UUID.randomUUID();
        journal.begin(route.account(), route.id(), requestToken, held.stack().copy(),
                new CourierData().writeSaveData(CourierData.get(courier)), level.getGameTime());
        rewriteRequest(held.stack(), requestToken, courier, route);
        sync(courier, journal);
        if (route.source().kind() == MaidLogisticsData.NodeKind.WAREHOUSE) {
            if (!startWarehouse(level, courier, journal, route, true)) {
                routes.status(route.id(), MaidLogisticsData.RouteStatus.BLOCKED,
                        "warehouse_unavailable");
                journal.phase(MaidLogisticsCourierData.Phase.RESTORING_REQUEST_LIST,
                        level.getGameTime());
                sync(courier, journal);
            }
        }
    }

    public static boolean active(EntityMaid courier) {
        return MaidLogisticsCourierData.get(courier).active();
    }

    public static UUID activeRoute(EntityMaid courier) {
        MaidLogisticsCourierData.Data data = MaidLogisticsCourierData.get(courier);
        return data.active() ? data.route() : null;
    }

    public static List<MaidLogisticsData.CargoLine> actualCargo(EntityMaid courier) {
        MaidLogisticsCourierData.Data journal = MaidLogisticsCourierData.get(courier);
        if (!journal.active()) return List.of();
        IItemHandler inventory = courier.getAvailableInv(false);
        List<MaidLogisticsData.CargoLine> result = new ArrayList<>();
        for (MaidLogisticsCourierData.CargoEntry entry : journal.cargo()) {
            int amount = Math.min(entry.amount(), Math.max(0,
                    count(inventory, entry.prototype()) - entry.baseline()));
            if (amount > 0) result.add(new MaidLogisticsData.CargoLine(entry.prototype(), amount));
        }
        return List.copyOf(result);
    }

    private static void tickActive(ServerLevel level, EntityMaid courier,
                                   MaidLogisticsCourierData.Data journal) {
        MaidLogisticsData routeData = MaidLogisticsData.get(level.getServer());
        MaidLogisticsData.Route route = routeData.route(journal.route());
        if (route == null || !route.courier().equals(courier.getUUID())
                || !courier.getTask().getUid().equals(CourierTask.TASK_ID)) {
            journal.phase(MaidLogisticsCourierData.Phase.SAFE_RETURN, level.getGameTime());
        }
        switch (journal.phase()) {
            case TO_LICENSE_SOURCE -> tickToLicenseSource(level, courier, journal, route);
            case TO_LICENSE_DESTINATION -> tickToLicenseDestination(level, courier, journal, route);
            case WAREHOUSE_RUNNING -> tickWarehouse(level, courier, journal, route);
            case SAFE_RETURN -> tickSafeReturn(level, courier, journal, route);
            case RESTORING_REQUEST_LIST, BLOCKED -> restoreWhenAvailable(level, courier, journal);
            case IDLE -> { }
        }
    }

    private static void tickToLicenseSource(ServerLevel level, EntityMaid courier,
                                            MaidLogisticsCourierData.Data journal,
                                            MaidLogisticsData.Route route) {
        if (route == null || route.source().kind() != MaidLogisticsData.NodeKind.LICENSE) {
            journal.phase(MaidLogisticsCourierData.Phase.SAFE_RETURN, level.getGameTime());
            sync(courier, journal);
            return;
        }
        BusinessLicenseData.Snapshot source = license(level, route.source());
        if (source == null || !moveTo(level, courier, journal, source.position(), null,
                source.landingPos())) return;
        int extracted = extractLicenseCargo(level, courier, journal, source, route.lines());
        if (extracted <= 0) {
            MaidLogisticsData.get(level.getServer()).status(route.id(),
                    MaidLogisticsData.RouteStatus.SOURCE_EMPTY, "source_empty");
            journal.phase(MaidLogisticsCourierData.Phase.RESTORING_REQUEST_LIST,
                    level.getGameTime());
            sync(courier, journal);
            return;
        }
        if (route.destination().kind() == MaidLogisticsData.NodeKind.WAREHOUSE) {
            if (!startWarehouse(level, courier, journal, route, false)) {
                journal.phase(MaidLogisticsCourierData.Phase.SAFE_RETURN, level.getGameTime());
                sync(courier, journal);
            }
            return;
        }
        journal.flight().retargetFlight(CourierData.Phase.TRANSPORT_TO_DESTINATION);
        journal.phase(MaidLogisticsCourierData.Phase.TO_LICENSE_DESTINATION, level.getGameTime());
        MaidLogisticsData.get(level.getServer()).status(route.id(),
                MaidLogisticsData.RouteStatus.ACTIVE, "");
        sync(courier, journal);
    }

    private static void tickToLicenseDestination(ServerLevel level, EntityMaid courier,
                                                 MaidLogisticsCourierData.Data journal,
                                                 MaidLogisticsData.Route route) {
        if (route == null) {
            journal.phase(MaidLogisticsCourierData.Phase.SAFE_RETURN, level.getGameTime());
            sync(courier, journal);
            return;
        }
        BusinessLicenseData.Snapshot source = license(level, route.source());
        BusinessLicenseData.Snapshot destination = license(level, route.destination());
        if (source == null || destination == null) {
            journal.phase(MaidLogisticsCourierData.Phase.SAFE_RETURN, level.getGameTime());
            sync(courier, journal);
            return;
        }
        if (!moveTo(level, courier, journal, destination.position(), source.landingPos(),
                destination.landingPos())) return;
        int before = cargoRemaining(courier, journal);
        int delivered = depositCargo(level, courier, journal, destination);
        int remaining = cargoRemaining(courier, journal);
        if (remaining > 0) depositCargo(level, courier, journal, source);
        if (delivered <= 0 && before > 0) {
            MaidLogisticsData.get(level.getServer()).status(route.id(),
                    MaidLogisticsData.RouteStatus.DESTINATION_FULL, "destination_full");
        } else {
            MaidLogisticsData.get(level.getServer()).status(route.id(),
                    MaidLogisticsData.RouteStatus.READY, "");
        }
        if (cargoRemaining(courier, journal) > 0) {
            journal.phase(MaidLogisticsCourierData.Phase.SAFE_RETURN, level.getGameTime());
        } else {
            journal.phase(MaidLogisticsCourierData.Phase.RESTORING_REQUEST_LIST,
                    level.getGameTime());
        }
        sync(courier, journal);
    }

    private static void tickSafeReturn(ServerLevel level, EntityMaid courier,
                                       MaidLogisticsCourierData.Data journal,
                                       MaidLogisticsData.Route route) {
        if (route == null || route.source().kind() != MaidLogisticsData.NodeKind.LICENSE) {
            journal.phase(MaidLogisticsCourierData.Phase.RESTORING_REQUEST_LIST,
                    level.getGameTime());
            sync(courier, journal);
            return;
        }
        BusinessLicenseData.Snapshot source = license(level, route.source());
        if (source == null || !moveTo(level, courier, journal, source.position(), null,
                source.landingPos())) return;
        depositCargo(level, courier, journal, source);
        if (cargoRemaining(courier, journal) <= 0) {
            journal.phase(MaidLogisticsCourierData.Phase.RESTORING_REQUEST_LIST,
                    level.getGameTime());
            sync(courier, journal);
        }
    }

    private static boolean startWarehouse(ServerLevel level, EntityMaid courier,
                                          MaidLogisticsCourierData.Data journal,
                                          MaidLogisticsData.Route route, boolean sourceWarehouse) {
        MaidLogisticsData.NodeRef node = sourceWarehouse ? route.source() : route.destination();
        if (!(level.getBlockEntity(node.position())
                instanceof CourierWarehouseStationBlockEntity station)) return false;
        CourierData.WarehouseBinding binding = station.binding(level);
        if (binding == null) return false;
        EntityMaid warehouse = TerminalAccountService.findMaid(level.getServer(), binding.warehouse());
        if (warehouse == null || CourierService.hasActiveWarehouseTransaction(warehouse)) return false;

        WarehouseCourierData.Data authority = WarehouseCourierData.get(warehouse);
        boolean added = authority.authorized().add(courier.getUUID());
        if (added) warehouse.setAndSyncData(WarehouseCourierData.KEY, authority);
        journal.temporaryWarehouse(warehouse.getUUID(), added);

        CourierData.Data transientData = new CourierData.Data();
        transientData.broomFlightDistance(CourierData.get(courier).broomFlightDistance());
        if (!transientData.addWarehouse(binding)) return false;
        transientData.dispatchSource(CourierData.DispatchSource.TERMINAL);
        if (sourceWarehouse) {
            BusinessLicenseData.Snapshot destination = license(level, route.destination());
            BlockPos delivery = firstDirectContainer(level, destination);
            if (delivery == null) return false;
            transientData.deliveryTarget(delivery, level.dimension().location());
        } else {
            BusinessLicenseData.Snapshot source = license(level, route.source());
            BlockPos returnTarget = firstDirectContainer(level, source);
            if (returnTarget == null) return false;
            transientData.deliveryTarget(returnTarget, level.dimension().location());
            for (MaidLogisticsCourierData.CargoEntry entry : journal.cargo()) {
                transientData.logisticsDepositFilter().add(new CourierData.ManifestEntry(
                        entry.prototype(), entry.amount(), entry.baseline()));
            }
            transientData.depositRequested(true);
        }
        transientData.phase(CourierData.Phase.IDLE);
        courier.setAndSyncData(CourierData.KEY, transientData);
        journal.phase(MaidLogisticsCourierData.Phase.WAREHOUSE_RUNNING, level.getGameTime());
        MaidLogisticsData.get(level.getServer()).status(route.id(),
                MaidLogisticsData.RouteStatus.ACTIVE, "");
        sync(courier, journal);
        return true;
    }

    private static void tickWarehouse(ServerLevel level, EntityMaid courier,
                                      MaidLogisticsCourierData.Data journal,
                                      MaidLogisticsData.Route route) {
        CourierData.Data transaction = CourierData.get(courier);
        if (CourierService.hasActiveTransaction(courier)) {
            journal.warehouseStarted(true);
            if (route != null && route.source().kind() == MaidLogisticsData.NodeKind.WAREHOUSE
                    && journal.cargo().isEmpty()) {
                for (CourierData.ManifestEntry entry : transaction.requestManifest()) {
                    journal.cargo().add(new MaidLogisticsCourierData.CargoEntry(
                            entry.prototype(), entry.amount(), entry.baseline()));
                }
            }
            if (route != null && (transaction.phase() == CourierData.Phase.DELIVERY_CHEST_WAITING_SPACE
                    || transaction.phase() == CourierData.Phase.WAITING_WITH_CARGO_AT_DELIVERY_CHEST)) {
                MaidLogisticsData.get(level.getServer()).status(route.id(),
                        MaidLogisticsData.RouteStatus.DESTINATION_FULL, "destination_full");
            }
            sync(courier, journal);
            return;
        }
        if (!journal.warehouseStarted()) return;
        if (route != null) {
            MaidLogisticsData.get(level.getServer()).status(route.id(),
                    journal.cargo().isEmpty() && route.source().kind() == MaidLogisticsData.NodeKind.WAREHOUSE
                            ? MaidLogisticsData.RouteStatus.SOURCE_EMPTY
                            : MaidLogisticsData.RouteStatus.READY,
                    journal.cargo().isEmpty() ? "source_empty" : "");
        }
        journal.phase(MaidLogisticsCourierData.Phase.RESTORING_REQUEST_LIST,
                level.getGameTime());
        sync(courier, journal);
    }

    private static void restoreWhenAvailable(ServerLevel level, EntityMaid courier,
                                             MaidLogisticsCourierData.Data journal) {
        ItemSlot marked = findMarkedList(courier, journal.requestToken());
        if (marked == null) return; // The physical list is still inside another transaction participant.
        marked.replace(journal.originalRequest().copy());
        CourierBroomFlightService.cleanup(courier, journal.flight());
        courier.setAndSyncData(CourierData.KEY,
                new CourierData().readSaveData(journal.originalCourier()));
        revokeTemporaryAuthorization(level, courier, journal);
        journal.clear();
        sync(courier, journal);
    }

    private static void revokeTemporaryAuthorization(ServerLevel level, EntityMaid courier,
                                                     MaidLogisticsCourierData.Data journal) {
        if (!journal.temporaryAuthorizationAdded() || journal.temporaryWarehouse() == null) return;
        EntityMaid warehouse = TerminalAccountService.findMaid(
                level.getServer(), journal.temporaryWarehouse());
        if (warehouse == null) return;
        WarehouseCourierData.Data authority = WarehouseCourierData.get(warehouse);
        authority.revoke(courier.getUUID());
        warehouse.setAndSyncData(WarehouseCourierData.KEY, authority);
    }

    private static String runtimeBlocker(ServerLevel level, MaidLogisticsData.Route route,
                                         EntityMaid courier) {
        if (!level.dimension().equals(LevelKey.OVERWORLD)) return "overworld_only";
        if (license(level, route.source()) == null && route.source().kind() == MaidLogisticsData.NodeKind.LICENSE
                || license(level, route.destination()) == null
                && route.destination().kind() == MaidLogisticsData.NodeKind.LICENSE) return "license_invalid";
        if (route.source().kind() == MaidLogisticsData.NodeKind.WAREHOUSE
                && warehouseBinding(level, route.source()) == null
                || route.destination().kind() == MaidLogisticsData.NodeKind.WAREHOUSE
                && warehouseBinding(level, route.destination()) == null) return "warehouse_invalid";
        double dx = route.destination().position().getX() - route.source().position().getX();
        double dz = route.destination().position().getZ() - route.source().position().getZ();
        if (CourierFlightPolicy.shouldUseBroom(dx, dz, CourierData.get(courier).broomFlightDistance())
                && !EnderPocketCompat.hasBroom(courier)) return "broom_required";
        return null;
    }

    private static CourierData.WarehouseBinding warehouseBinding(
            ServerLevel level, MaidLogisticsData.NodeRef node) {
        return level.getBlockEntity(node.position()) instanceof CourierWarehouseStationBlockEntity station
                ? station.binding(level) : null;
    }

    private static BusinessLicenseData.Snapshot license(
            ServerLevel level, MaidLogisticsData.NodeRef node) {
        if (node == null || node.kind() != MaidLogisticsData.NodeKind.LICENSE) return null;
        BusinessLicenseData.Snapshot value = BusinessLicenseData.get(level.getServer()).get(node.license());
        return value != null && value.dimension().equals(node.dimension())
                && value.position().equals(node.position()) && !value.containers().isEmpty()
                ? value : null;
    }

    private static boolean moveTo(ServerLevel level, EntityMaid courier,
                                  MaidLogisticsCourierData.Data journal, BlockPos target,
                                  BlockPos requiredTakeoff, BlockPos requiredLanding) {
        if (target == null) return false;
        if (courier.distanceToSqr(target.getCenter()) <= ARRIVAL_DISTANCE_SQR
                && !CourierBroomFlightService.isAirborne(level, courier, journal.flight())) {
            CourierBroomFlightService.cleanup(courier, journal.flight());
            courier.getNavigation().stop();
            MemoryUtil.clearTarget(courier);
            return true;
        }
        double dx = target.getX() - courier.getX();
        double dz = target.getZ() - courier.getZ();
        if (!CourierFlightPolicy.shouldUseBroom(dx, dz, journal.flight().broomFlightDistance())) {
            MemoryUtil.setTarget(courier, target, (float) Config.collectSpeed);
            courier.getNavigation().moveTo(target.getX() + 0.5, target.getY(),
                    target.getZ() + 0.5, Config.collectSpeed);
            return false;
        }
        if (!EnderPocketCompat.hasBroom(courier)) return false;
        CourierBroomFlightService.TickResult result = CourierBroomFlightService.tick(
                level, courier, journal.flight(), CourierData.Phase.TRANSPORT_TO_DESTINATION,
                target.getCenter(), requiredTakeoff, requiredLanding);
        return result == CourierBroomFlightService.TickResult.LANDED;
    }

    private static int extractLicenseCargo(ServerLevel level, EntityMaid courier,
                                           MaidLogisticsCourierData.Data journal,
                                           BusinessLicenseData.Snapshot license,
                                           List<MaidLogisticsData.CargoLine> lines) {
        IItemHandlerModifiable destination = courier.getAvailableInv(false);
        int total = 0;
        for (MaidLogisticsData.CargoLine line : lines) {
            int needed = line.amount();
            ResourceLocation expected = ForgeRegistries.ITEMS.getKey(line.prototype().getItem());
            for (BusinessLicenseData.ContainerRef ref : license.containers()) {
                IItemHandler source = handler(level, ref);
                if (source == null) continue;
                for (int slot = 0; slot < source.getSlots() && needed > 0; slot++) {
                    ItemStack current = source.getStackInSlot(slot);
                    if (current.isEmpty() || !expected.equals(
                            ForgeRegistries.ITEMS.getKey(current.getItem()))) continue;
                    int baseline = count(destination, current);
                    ItemStack simulated = ItemHandlerHelper.insertItem(
                            destination, current.copyWithCount(Math.min(needed, current.getCount())), true);
                    int movable = Math.min(needed, current.getCount()) - simulated.getCount();
                    if (movable <= 0) continue;
                    ItemStack extracted = source.extractItem(slot, movable, false);
                    ItemStack remainder = ItemHandlerHelper.insertItem(destination, extracted, false);
                    int moved = extracted.getCount() - remainder.getCount();
                    if (!remainder.isEmpty()) ItemHandlerHelper.insertItem(source, remainder, false);
                    if (moved > 0) {
                        mergeCargo(journal.cargo(), extracted, moved, baseline);
                        needed -= moved;
                        total += moved;
                    }
                }
            }
        }
        sync(courier, journal);
        return total;
    }

    private static int depositCargo(ServerLevel level, EntityMaid courier,
                                    MaidLogisticsCourierData.Data journal,
                                    BusinessLicenseData.Snapshot license) {
        if (license == null) return 0;
        IItemHandlerModifiable source = courier.getAvailableInv(false);
        int total = 0;
        for (MaidLogisticsCourierData.CargoEntry entry : journal.cargo()) {
            int remaining = Math.min(entry.amount(), Math.max(0,
                    count(source, entry.prototype()) - entry.baseline()));
            for (BusinessLicenseData.ContainerRef ref : license.containers()) {
                IItemHandler destination = handler(level, ref);
                if (destination == null || remaining <= 0) continue;
                int movable = insertCapacity(destination, entry.prototype(), remaining);
                if (movable <= 0) continue;
                ItemStack extracted = extractAboveBaseline(source, entry, movable);
                ItemStack rest = ItemHandlerHelper.insertItem(destination, extracted, false);
                int moved = extracted.getCount() - rest.getCount();
                total += moved;
                remaining -= moved;
                if (!rest.isEmpty()) ItemHandlerHelper.insertItem(source, rest, false);
            }
        }
        return total;
    }

    private static int cargoRemaining(EntityMaid courier, MaidLogisticsCourierData.Data journal) {
        IItemHandler inventory = courier.getAvailableInv(false);
        int total = 0;
        for (MaidLogisticsCourierData.CargoEntry entry : journal.cargo()) {
            total += Math.min(entry.amount(), Math.max(0,
                    count(inventory, entry.prototype()) - entry.baseline()));
        }
        return total;
    }

    private static ItemStack extractAboveBaseline(IItemHandler source,
                                                  MaidLogisticsCourierData.CargoEntry entry,
                                                  int amount) {
        int available = Math.min(amount, Math.max(0,
                count(source, entry.prototype()) - entry.baseline()));
        ItemStack result = entry.prototype().copyWithCount(0);
        for (int slot = 0; slot < source.getSlots() && available > 0; slot++) {
            ItemStack current = source.getStackInSlot(slot);
            if (!ItemStack.isSameItemSameTags(current, entry.prototype())) continue;
            ItemStack extracted = source.extractItem(slot, Math.min(available, current.getCount()), false);
            if (extracted.isEmpty()) continue;
            if (result.isEmpty()) result = extracted.copy();
            else result.grow(extracted.getCount());
            available -= extracted.getCount();
        }
        return result;
    }

    private static int insertCapacity(IItemHandler target, ItemStack prototype, int amount) {
        ItemStack offered = prototype.copyWithCount(amount);
        return amount - ItemHandlerHelper.insertItem(target, offered, true).getCount();
    }

    private static int count(IItemHandler inventory, ItemStack prototype) {
        int total = 0;
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            ItemStack current = inventory.getStackInSlot(slot);
            if (ItemStack.isSameItemSameTags(current, prototype)) total += current.getCount();
        }
        return total;
    }

    private static void mergeCargo(List<MaidLogisticsCourierData.CargoEntry> cargo,
                                   ItemStack prototype, int moved, int baseline) {
        for (int i = 0; i < cargo.size(); i++) {
            MaidLogisticsCourierData.CargoEntry current = cargo.get(i);
            if (ItemStack.isSameItemSameTags(current.prototype(), prototype)) {
                cargo.set(i, new MaidLogisticsCourierData.CargoEntry(prototype,
                        current.amount() + moved, Math.min(current.baseline(), baseline)));
                return;
            }
        }
        cargo.add(new MaidLogisticsCourierData.CargoEntry(prototype, moved, baseline));
    }

    private static BlockPos firstDirectContainer(ServerLevel level,
                                                 BusinessLicenseData.Snapshot license) {
        if (license == null) return null;
        return license.containers().stream().map(BusinessLicenseData.ContainerRef::position)
                .filter(level::hasChunkAt)
                .filter(pos -> level.getBlockEntity(pos) != null
                        && level.getBlockEntity(pos).getCapability(
                        ForgeCapabilities.ITEM_HANDLER, null).isPresent())
                .findFirst().orElse(null);
    }

    private static IItemHandler handler(ServerLevel level, BusinessLicenseData.ContainerRef ref) {
        if (ref == null || !level.hasChunkAt(ref.position())) return null;
        var block = level.getBlockEntity(ref.position());
        return block == null ? null : block.getCapability(ForgeCapabilities.ITEM_HANDLER, ref.side())
                .resolve().orElse(null);
    }

    private static HeldList heldEditableList(EntityMaid courier) {
        if (CourierService.canEditHeldRequestList(courier.getMainHandItem())) {
            return new HeldList(InteractionHand.MAIN_HAND, courier.getMainHandItem());
        }
        if (CourierService.canEditHeldRequestList(courier.getOffhandItem())) {
            return new HeldList(InteractionHand.OFF_HAND, courier.getOffhandItem());
        }
        return null;
    }

    private static void rewriteRequest(ItemStack request, UUID token, EntityMaid courier,
                                       MaidLogisticsData.Route route) {
        List<NetworkWarehouseActionPacket.RequestedItem> lines = route.lines().stream()
                .map(line -> new NetworkWarehouseActionPacket.RequestedItem(
                        line.prototype(), line.amount())).toList();
        NetworkWarehouseRequestFactory.update(request,
                courier.getOwnerUUID() == null ? route.account() : courier.getOwnerUUID(),
                courier.getUUID(), route.id(), courier.level().getGameTime(),
                NetworkWarehouseActionPacket.DeliveryTarget.FIXED_CHEST, lines);
        request.getOrCreateTag().putUUID(REQUEST_MARKER, token);
        request.getOrCreateTag().putBoolean(RequestListItem.TAG_IGNORE_TASK, false);
    }

    private record HeldList(InteractionHand hand, ItemStack stack) { }

    private interface ItemSlot { void replace(ItemStack stack); }

    private static ItemSlot findMarkedList(EntityMaid courier, UUID token) {
        if (token == null) return null;
        if (marked(courier.getMainHandItem(), token)) {
            return stack -> courier.setItemInHand(InteractionHand.MAIN_HAND, stack);
        }
        if (marked(courier.getOffhandItem(), token)) {
            return stack -> courier.setItemInHand(InteractionHand.OFF_HAND, stack);
        }
        IItemHandlerModifiable inventory = courier.getAvailableInv(true);
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            if (marked(inventory.getStackInSlot(slot), token)) {
                int index = slot;
                return stack -> inventory.setStackInSlot(index, stack);
            }
        }
        return null;
    }

    private static boolean marked(ItemStack stack, UUID token) {
        CompoundTag tag = stack == null ? null : stack.getTag();
        return tag != null && tag.hasUUID(REQUEST_MARKER)
                && token.equals(tag.getUUID(REQUEST_MARKER));
    }

    private static void sync(EntityMaid courier, MaidLogisticsCourierData.Data data) {
        courier.setAndSyncData(MaidLogisticsCourierData.KEY, data);
    }

    /** Avoid importing a client Level constant into persistence-facing validation. */
    private static final class LevelKey {
        private static final net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> OVERWORLD =
                net.minecraft.world.level.Level.OVERWORLD;
    }
}
