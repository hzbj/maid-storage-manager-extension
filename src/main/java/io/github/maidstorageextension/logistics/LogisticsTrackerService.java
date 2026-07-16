package io.github.maidstorageextension.logistics;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import io.github.maidstorageextension.compat.EnderPocketCompat;
import io.github.maidstorageextension.data.CourierData;
import io.github.maidstorageextension.maid.courier.CourierWarehouseStationService;
import io.github.maidstorageextension.maid.courier.CourierBroomFlightService;
import io.github.maidstorageextension.maid.courier.CourierRecallPolicy;
import io.github.maidstorageextension.maid.courier.CourierTransportPolicy;
import io.github.maidstorageextension.network.ExtensionNetwork;
import io.github.maidstorageextension.network.LogisticsTrackerPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Builds and sends realtime tracker data without changing the tracker ItemStack. */
public final class LogisticsTrackerService {
    private static final Map<UUID, LogisticsSnapshot.Snapshot> LAST_SNAPSHOT = new HashMap<>();

    private LogisticsTrackerService() {
    }

    public static void update(ServerPlayer viewer, UUID courierId) {
        MinecraftServer server = viewer.getServer();
        EntityMaid courier = findMaid(server, courierId);
        LogisticsSnapshot.Snapshot snapshot;
        if (courier == null) {
            snapshot = LAST_SNAPSHOT.getOrDefault(courierId,
                    LogisticsSnapshot.Snapshot.empty()).offline();
        } else if (!courier.isOwnedBy(viewer)) {
            CourierData.Data data = CourierData.get(courier);
            snapshot = unauthorized(courier, data, viewer.serverLevel().getGameTime());
        } else {
            snapshot = build(server, courier);
            LAST_SNAPSHOT.put(courierId, snapshot);
        }
        ExtensionNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> viewer),
                new LogisticsTrackerPacket(courierId, snapshot));
    }

    /** Broadcasts one frame-safe snapshot; the frame itself is never replaced or re-synced. */
    public static void updateMounted(ItemFrame frame, MinecraftServer server,
                                     UUID courierId, UUID boundOwnerId) {
        EntityMaid courier = findMaid(server, courierId);
        LogisticsSnapshot.Snapshot snapshot;
        if (courier == null) {
            snapshot = LAST_SNAPSHOT.getOrDefault(courierId,
                    LogisticsSnapshot.Snapshot.empty()).offline();
        } else if (boundOwnerId == null || !boundOwnerId.equals(courier.getOwnerUUID())) {
            snapshot = unauthorized(courier, CourierData.get(courier),
                    server.overworld().getGameTime());
        } else {
            snapshot = build(server, courier);
            LAST_SNAPSHOT.put(courierId, snapshot);
        }
        ExtensionNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY.with(() -> frame),
                new LogisticsTrackerPacket(courierId, snapshot));
    }

    private static LogisticsSnapshot.Snapshot build(MinecraftServer server, EntityMaid courier) {
        CourierData.Data data = CourierData.get(courier);
        EntityMaid warehouse = findMaid(server, data.warehouse());
        ServerPlayer owner = courier.getOwnerUUID() == null ? null
                : server.getPlayerList().getPlayer(courier.getOwnerUUID());
        CourierData.TransportMode mode = displayMode(courier, data, warehouse, owner);
        TargetInfo target = target(courier, data, warehouse, owner);
        List<LogisticsSnapshot.Station> stations = collectStations(server, courier, data);
        boolean recallAvailable = courier.level() instanceof ServerLevel level
                && CourierRecallPolicy.canRecall(data.transportMode(), data.phase(),
                CourierBroomFlightService.isAirborne(level, courier, data));
        return new LogisticsSnapshot.Snapshot(
                true, true, LogisticsDisplayName.encode(courier.getName()), data.phase().name(), mode,
                target.kind(), target.name(), target.distance(), target.loaded(),
                recallAvailable, courier.level().getGameTime(), stations,
                CourierData.MAX_WAREHOUSES);
    }

    private static LogisticsSnapshot.Snapshot unauthorized(EntityMaid courier,
                                                            CourierData.Data data,
                                                            long gameTime) {
        return new LogisticsSnapshot.Snapshot(true, false, LogisticsDisplayName.encode(courier.getName()),
                data.phase().name(), CourierData.TransportMode.NONE,
                LogisticsSnapshot.TargetKind.NONE, "", -1, false,
                false, gameTime, List.of(), CourierData.MAX_WAREHOUSES);
    }

    private static CourierData.TransportMode displayMode(EntityMaid courier, CourierData.Data data,
                                                          EntityMaid warehouse, ServerPlayer owner) {
        if (data.transportMode() != CourierData.TransportMode.NONE) return data.transportMode();
        boolean nearOwner = owner != null && owner.level() == courier.level()
                && withinHorizontal(courier.blockPosition(), owner.blockPosition(),
                data.broomFlightDistance());
        BlockPos warehousePos = warehouse == null ? data.warehousePos() : warehouse.blockPosition();
        boolean nearWarehouse = data.warehouseDimension() != null
                && data.warehouseDimension().equals(courier.level().dimension().location())
                && warehousePos != null && withinHorizontal(courier.blockPosition(), warehousePos,
                data.broomFlightDistance());
        return CourierTransportPolicy.select(EnderPocketCompat.isEquipped(courier),
                EnderPocketCompat.hasBroom(courier), nearWarehouse, nearOwner);
    }

    private static TargetInfo target(EntityMaid courier, CourierData.Data data,
                                     EntityMaid warehouse, ServerPlayer owner) {
        return switch (data.phase()) {
            case TRAVEL_TO_WAREHOUSE_REQUEST, REQUEST_HANDOFF, REQUEST_RUNNING,
                    REQUEST_WAITING_SPACE, TRAVEL_TO_WAREHOUSE_DEPOSIT, DEPOSIT_HANDOFF,
                    DEPOSIT_RUNNING, DEPOSIT_RETURNING, DEPOSIT_WAITING_SPACE,
                    WAITING_APPROVAL, WAITING_AT_STATION_AFTER_RECALL,
                    LINK_UNAVAILABLE -> warehouseTarget(courier, data, warehouse);
            case TRAVEL_TO_OWNER, OWNER_HANDOFF, OWNER_WAITING_SPACE,
                    WAITING_OWNER_PICKUP ->
                    ownerPositionTarget(courier, data, owner);
            case TRAVEL_TO_DELIVERY_CHEST, DELIVERY_CHEST_WAITING_SPACE,
                    WAITING_WITH_CARGO_AT_DELIVERY_CHEST, WAITING_AT_DELIVERY_CHEST ->
                    positionTarget(courier, data.deliveryPos(), data.deliveryDimension(),
                            LogisticsSnapshot.TargetKind.DELIVERY_CHEST, "");
            case RETURNING_TO_ORIGIN -> data.originOwner()
                    ? ownerPositionTarget(courier, data, owner)
                    : positionTarget(courier, data.originPos(), data.originDimension(),
                    LogisticsSnapshot.TargetKind.ORIGIN, "");
            case RETURNING_AFTER_LANDING_FAILURE, WAITING_FOR_SAFE_LANDING ->
                    positionTarget(courier, data.originPos(), data.originDimension(),
                            LogisticsSnapshot.TargetKind.ORIGIN, "");
            default -> new TargetInfo(LogisticsSnapshot.TargetKind.NONE, "", -1, false);
        };
    }

    private static TargetInfo warehouseTarget(EntityMaid courier, CourierData.Data data,
                                               EntityMaid warehouse) {
        if (warehouse != null) {
            return entityTarget(courier, warehouse, LogisticsSnapshot.TargetKind.WAREHOUSE);
        }
        return positionTarget(courier, data.warehousePos(), data.warehouseDimension(),
                LogisticsSnapshot.TargetKind.WAREHOUSE, "");
    }

    private static TargetInfo ownerPositionTarget(EntityMaid courier, CourierData.Data data,
                                                  ServerPlayer owner) {
        String name = owner == null ? "" : LogisticsDisplayName.encode(owner.getName());
        if (data.ownerTargetPos() != null && data.ownerTargetDimension() != null) {
            return positionTarget(courier, data.ownerTargetPos(), data.ownerTargetDimension(),
                    LogisticsSnapshot.TargetKind.OWNER, name);
        }
        return entityTarget(courier, owner, LogisticsSnapshot.TargetKind.OWNER);
    }

    private static TargetInfo entityTarget(EntityMaid courier, Entity target,
                                           LogisticsSnapshot.TargetKind kind) {
        if (target == null) return new TargetInfo(kind, "", -1, false);
        boolean sameDimension = target.level().dimension().equals(courier.level().dimension());
        return new TargetInfo(kind, LogisticsDisplayName.encode(target.getName()),
                sameDimension ? distance(courier.position(), target.position()) : -1, true);
    }

    private static TargetInfo positionTarget(EntityMaid courier, BlockPos position,
                                             ResourceLocation dimension,
                                             LogisticsSnapshot.TargetKind kind, String name) {
        if (position == null || dimension == null) return new TargetInfo(kind, name, -1, false);
        boolean sameDimension = courier.level().dimension().location().equals(dimension);
        boolean loaded = sameDimension && courier.level().hasChunkAt(position);
        return new TargetInfo(kind, name,
                sameDimension ? distance(courier.position(), position.getCenter()) : -1, loaded);
    }

    private static int distance(Vec3 first, Vec3 second) {
        return (int) Math.round(Math.sqrt(first.distanceToSqr(second)));
    }

    private static boolean withinHorizontal(BlockPos first, BlockPos second, int range) {
        long dx = (long) first.getX() - second.getX();
        long dz = (long) first.getZ() - second.getZ();
        return dx * dx + dz * dz <= (long) range * range;
    }

    private static EntityMaid findMaid(MinecraftServer server, UUID id) {
        if (server == null || id == null) return null;
        for (ServerLevel level : server.getAllLevels()) {
            if (level.getEntity(id) instanceof EntityMaid maid) return maid;
        }
        return null;
    }

    private static List<LogisticsSnapshot.Station> collectStations(
            MinecraftServer server, EntityMaid courier, CourierData.Data data) {
        List<LogisticsSnapshot.Station> stations = new ArrayList<>();
        for (CourierData.WarehouseBinding binding : data.warehouses()) {
            EntityMaid warehouse = findMaid(server, binding.warehouse());
            String name = warehouse == null ? binding.warehouseName()
                    : LogisticsDisplayName.encode(warehouse.getName());
            if (warehouse == null && !name.isBlank()) {
                name = LogisticsDisplayName.encode(net.minecraft.network.chat.Component.literal(name));
            }
            ServerLevel stationLevel = findLevel(server, binding.mailboxDimension());
            boolean locationValid = binding.hasStation()
                    ? stationLevel != null && (!stationLevel.hasChunkAt(binding.mailboxPos())
                    || CourierWarehouseStationService.isValid(stationLevel, binding))
                    : !EnderPocketCompat.hasBroom(courier) && warehouse != null;
            boolean valid = locationValid && (warehouse == null
                    || io.github.maidstorageextension.data.WarehouseCourierData.get(warehouse)
                    .isAuthorized(courier.getUUID()));
            stations.add(new LogisticsSnapshot.Station(binding.warehouse(), name,
                    binding.warehouse().equals(data.warehouse()), valid));
        }
        return List.copyOf(stations);
    }

    private static ServerLevel findLevel(MinecraftServer server, ResourceLocation dimension) {
        if (server == null || dimension == null) return null;
        for (ServerLevel level : server.getAllLevels()) {
            if (level.dimension().location().equals(dimension)) return level;
        }
        return null;
    }

    private record TargetInfo(LogisticsSnapshot.TargetKind kind, String name,
                              int distance, boolean loaded) {
    }

}
