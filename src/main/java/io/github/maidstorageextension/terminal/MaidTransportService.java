package io.github.maidstorageextension.terminal;

import com.github.tartaricacid.touhoulittlemaid.entity.item.EntityBroom;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import io.github.maidstorageextension.compat.EnderPocketCompat;
import io.github.maidstorageextension.data.CourierData;
import io.github.maidstorageextension.logistics.LogisticsDisplayName;
import io.github.maidstorageextension.maid.ExtensionMemoryUtil;
import io.github.maidstorageextension.maid.courier.CourierBroomFlightService;
import io.github.maidstorageextension.maid.courier.CourierService;
import io.github.maidstorageextension.maid.courier.CourierSortMutex;
import io.github.maidstorageextension.maid.task.CourierTask;
import io.github.maidstorageextension.network.ExtensionNetwork;
import io.github.maidstorageextension.network.MaidTransportActionPacket;
import io.github.maidstorageextension.network.MaidTransportSnapshotPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

import java.util.UUID;

/** Server-authoritative passenger service driven by a registered courier maid. */
public final class MaidTransportService {
    private MaidTransportService() {
    }

    public static void handle(ServerPlayer player, MaidTransportActionPacket packet) {
        UUID driverId = TerminalAccountService.selectedDriver(player, packet.terminal());
        if (driverId == null || !TerminalAccountService.authorizes(
                player, packet.terminal(), driverId)) {
            update(player, packet.terminal());
            return;
        }
        switch (packet.action()) {
            case REFRESH -> update(player, packet.terminal());
            case START -> start(player, packet.terminal(), driverId,
                    packet.pickup(), packet.destination());
            case END -> end(player, packet.terminal(), driverId);
        }
    }

    private static void start(ServerPlayer rider, UUID terminal, UUID driverId,
                              BlockPos requestedPickup, BlockPos requestedDestination) {
        EntityMaid driver = TerminalAccountService.findMaid(rider.getServer(), driverId);
        String failure = startFailure(rider, driver, requestedPickup, requestedDestination);
        if (failure != null) {
            rider.sendSystemMessage(Component.translatable(failure));
            update(rider, terminal);
            return;
        }
        ServerLevel level = (ServerLevel) rider.level();
        BlockPos pickup = requestedPickup == null
                ? rider.blockPosition() : surfaceAnchor(level, requestedPickup, rider.getBlockY());
        BlockPos destination = surfaceAnchor(level, requestedDestination, rider.getBlockY());
        CourierData.Data data = CourierData.get(driver);
        data.beginPassengerTransport(rider.getUUID(), pickup, destination,
                level.dimension().location());
        sync(driver, data);
        rider.sendSystemMessage(Component.translatable(
                "message.maid_storage_manager_extension.transport.started"));
        update(rider, terminal);
    }

    private static String startFailure(ServerPlayer rider, EntityMaid driver,
                                       BlockPos requestedPickup,
                                       BlockPos destination) {
        if (destination == null) {
            return "message.maid_storage_manager_extension.transport.destination_required";
        }
        if (!(rider.level() instanceof ServerLevel level)
                || !level.dimension().equals(Level.OVERWORLD)) {
            return "message.maid_storage_manager_extension.transport.overworld_only";
        }
        if (!level.getWorldBorder().isWithinBounds(
                new BlockPos(destination.getX(), level.getSeaLevel(), destination.getZ()))) {
            return "message.maid_storage_manager_extension.transport.outside_world_border";
        }
        if (requestedPickup != null && !level.getWorldBorder().isWithinBounds(
                new BlockPos(requestedPickup.getX(), level.getSeaLevel(), requestedPickup.getZ()))) {
            return "message.maid_storage_manager_extension.transport.outside_world_border";
        }
        if (driver == null) return "message.maid_storage_manager_extension.transport.driver_offline";
        if (!driver.level().dimension().equals(Level.OVERWORLD)) {
            return "message.maid_storage_manager_extension.transport.driver_overworld";
        }
        if (!driver.getTask().getUid().equals(CourierTask.TASK_ID)) {
            return "message.maid_storage_manager_extension.transport.driver_wrong_task";
        }
        CourierData.Data data = CourierData.get(driver);
        if (CourierService.hasActiveTransaction(driver)
                || ExtensionMemoryUtil.getMiscSort(driver).hasInFlight()
                || data.phase() != CourierData.Phase.IDLE
                && data.phase() != CourierData.Phase.UNBOUND) {
            return "message.maid_storage_manager_extension.transport.driver_busy";
        }
        if (!EnderPocketCompat.hasBroom(driver)) {
            return "message.maid_storage_manager_extension.transport.no_broom";
        }
        if (requestedPickup == null && !level.canSeeSky(rider.blockPosition())) {
            return "message.maid_storage_manager_extension.transport.pickup_blocked";
        }
        return null;
    }

    private static BlockPos surfaceAnchor(ServerLevel level, BlockPos requested, int fallbackY) {
        int x = requested.getX();
        int z = requested.getZ();
        int y = level.hasChunk(x >> 4, z >> 4)
                ? level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z)
                : Mth.clamp(fallbackY, level.getMinBuildHeight() + 1,
                level.getMaxBuildHeight() - 2);
        return new BlockPos(x, y, z);
    }

    private static void end(ServerPlayer rider, UUID terminal, UUID driverId) {
        EntityMaid driver = TerminalAccountService.findMaid(rider.getServer(), driverId);
        if (driver == null) {
            update(rider, terminal);
            return;
        }
        CourierData.Data data = CourierData.get(driver);
        if (!rider.getUUID().equals(data.transportRider())) return;
        if (data.phase() == CourierData.Phase.TRANSPORT_TO_DESTINATION
                && driver.level() instanceof ServerLevel level
                && handControlToRider(level, driver, data, rider)) {
            rider.sendSystemMessage(Component.translatable(
                    "message.maid_storage_manager_extension.transport.control_transferred"));
        } else if (data.phase() == CourierData.Phase.TRANSPORT_TO_PICKUP
                || data.phase() == CourierData.Phase.TRANSPORT_WAITING_RIDER) {
            cancelBeforeBoarding(driver, data);
            rider.sendSystemMessage(Component.translatable(
                    "message.maid_storage_manager_extension.transport.cancelled"));
        }
        update(rider, terminal);
    }

    /** Called every maid tick before the ordinary courier flight state machine. */
    public static void tick(ServerLevel level, EntityMaid driver, CourierData.Data data) {
        if (!CourierSortMutex.isPassengerTransport(data.phase())) return;
        ServerPlayer rider = data.transportRider() == null ? null
                : level.getServer().getPlayerList().getPlayer(data.transportRider());
        if (rider == null || !rider.isAlive()) {
            if (data.phase() == CourierData.Phase.TRANSPORT_TO_PICKUP
                    || data.phase() == CourierData.Phase.TRANSPORT_WAITING_RIDER) {
                cancelBeforeBoarding(driver, data);
            } else {
                beginEmergencyLanding(level, driver, data);
            }
            return;
        }
        if (!level.dimension().equals(Level.OVERWORLD)
                || !rider.level().dimension().equals(Level.OVERWORLD)) {
            beginEmergencyLanding(level, driver, data);
            return;
        }
        switch (data.phase()) {
            case TRANSPORT_TO_PICKUP -> tickToPickup(level, driver, data, rider);
            case TRANSPORT_WAITING_RIDER -> tickWaiting(level, driver, data, rider);
            case TRANSPORT_TO_DESTINATION -> tickToDestination(level, driver, data, rider);
            case TRANSPORT_PLAYER_CONTROLLED -> tickPlayerControlled(level, driver, data, rider);
            case TRANSPORT_EMERGENCY_LANDING -> tickEmergency(level, driver, data);
            default -> { }
        }
    }

    private static void tickToPickup(ServerLevel level, EntityMaid driver,
                                     CourierData.Data data, ServerPlayer rider) {
        BlockPos target = data.transportPickupAnchor();
        if (target == null) {
            cancelBeforeBoarding(driver, data);
            return;
        }
        CourierBroomFlightService.TickResult result = CourierBroomFlightService.tickPassenger(
                level, driver, data, CourierData.Phase.TRANSPORT_TO_PICKUP, target.getCenter());
        if (result == CourierBroomFlightService.TickResult.LANDING_SEARCH_EXHAUSTED) {
            cancelBeforeBoarding(driver, data);
            rider.sendSystemMessage(Component.translatable(
                    "message.maid_storage_manager_extension.transport.pickup_no_landing"));
            return;
        }
        if (result != CourierBroomFlightService.TickResult.LANDED) return;
        data.transportPickup(data.flightLandingPos());
        data.phase(CourierData.Phase.TRANSPORT_WAITING_RIDER);
        EntityBroom broom = CourierBroomFlightService.createWaitingTransportBroom(
                level, driver, data, rider.getUUID());
        if (broom == null) {
            cancelBeforeBoarding(driver, data);
            return;
        }
        sync(driver, data);
        rider.sendSystemMessage(Component.translatable(
                "message.maid_storage_manager_extension.transport.waiting"));
    }

    private static void tickWaiting(ServerLevel level, EntityMaid driver,
                                    CourierData.Data data, ServerPlayer rider) {
        EntityBroom broom = CourierBroomFlightService.transportBroom(level, driver, data);
        if (broom == null) {
            broom = CourierBroomFlightService.createWaitingTransportBroom(
                    level, driver, data, rider.getUUID());
            if (broom == null) return;
        }
        broom.setDeltaMovement(Vec3.ZERO);
        if (!broom.getPassengers().contains(rider)) return;
        data.phase(CourierData.Phase.TRANSPORT_TO_DESTINATION);
        data.retargetFlight(CourierData.Phase.TRANSPORT_TO_DESTINATION);
        sync(driver, data);
        rider.sendSystemMessage(Component.translatable(
                "message.maid_storage_manager_extension.transport.departed"));
    }

    private static void tickToDestination(ServerLevel level, EntityMaid driver,
                                          CourierData.Data data, ServerPlayer rider) {
        EntityBroom broom = CourierBroomFlightService.transportBroom(level, driver, data);
        if (broom == null || !broom.getPassengers().contains(rider)) {
            beginEmergencyLanding(level, driver, data);
            return;
        }
        BlockPos target = data.transportDestinationAnchor();
        if (target == null) {
            beginEmergencyLanding(level, driver, data);
            return;
        }
        CourierBroomFlightService.TickResult result = CourierBroomFlightService.tickPassenger(
                level, driver, data, CourierData.Phase.TRANSPORT_TO_DESTINATION,
                target.getCenter());
        if (result == CourierBroomFlightService.TickResult.LANDED) {
            BlockPos landing = data.flightLandingPos();
            data.transportDestination(landing);
            finishTrip(driver, data);
            rider.sendSystemMessage(Component.translatable(
                    "message.maid_storage_manager_extension.transport.arrived"));
        } else if (result == CourierBroomFlightService.TickResult.LANDING_SEARCH_EXHAUSTED) {
            if (handControlToRider(level, driver, data, rider)) {
                rider.sendSystemMessage(Component.translatable(
                        "message.maid_storage_manager_extension.transport.no_landing_control"));
            }
        }
    }

    private static void tickPlayerControlled(ServerLevel level, EntityMaid driver,
                                             CourierData.Data data, ServerPlayer rider) {
        EntityBroom broom = CourierBroomFlightService.transportBroom(level, driver, data);
        if (broom != null && broom.getPassengers().contains(rider)) return;
        beginEmergencyLanding(level, driver, data);
    }

    private static void beginEmergencyLanding(ServerLevel level, EntityMaid driver,
                                              CourierData.Data data) {
        EntityBroom broom = CourierBroomFlightService.transportBroom(level, driver, data);
        if (broom == null) {
            finishTrip(driver, data);
            return;
        }
        CourierBroomFlightService.resumeTransportAutopilot(broom, driver, data);
        BlockPos current = BlockPos.containing(broom.position());
        data.transportDestinationAnchor(current);
        data.phase(CourierData.Phase.TRANSPORT_EMERGENCY_LANDING);
        data.retargetFlight(CourierData.Phase.TRANSPORT_EMERGENCY_LANDING);
        sync(driver, data);
    }

    private static void tickEmergency(ServerLevel level, EntityMaid driver,
                                      CourierData.Data data) {
        BlockPos target = data.transportDestinationAnchor();
        if (target == null) target = driver.blockPosition();
        CourierBroomFlightService.TickResult result = CourierBroomFlightService.tickPassenger(
                level, driver, data, CourierData.Phase.TRANSPORT_EMERGENCY_LANDING,
                target.getCenter());
        if (result == CourierBroomFlightService.TickResult.LANDED) finishTrip(driver, data);
    }

    private static boolean handControlToRider(ServerLevel level, EntityMaid driver,
                                              CourierData.Data data, ServerPlayer rider) {
        EntityBroom broom = CourierBroomFlightService.transportBroom(level, driver, data);
        if (broom == null || !broom.getPassengers().contains(rider)) return false;
        if (!CourierBroomFlightService.handControlToRider(broom, driver, rider, data)) return false;
        data.phase(CourierData.Phase.TRANSPORT_PLAYER_CONTROLLED);
        sync(driver, data);
        return true;
    }

    private static void cancelBeforeBoarding(EntityMaid driver, CourierData.Data data) {
        CourierBroomFlightService.discardTransportBroom(driver, data);
        finishTrip(driver, data);
    }

    private static void finishTrip(EntityMaid driver, CourierData.Data data) {
        data.clearPassengerTransport();
        sync(driver, data);
    }

    public static void update(ServerPlayer viewer, UUID terminal) {
        UUID driverId = TerminalAccountService.selectedDriver(viewer, terminal);
        MaidTransportSnapshot.Snapshot snapshot = snapshot(viewer, driverId);
        ExtensionNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> viewer),
                new MaidTransportSnapshotPacket(terminal, snapshot));
    }

    private static MaidTransportSnapshot.Snapshot snapshot(ServerPlayer viewer, UUID driverId) {
        if (driverId == null) return MaidTransportSnapshot.Snapshot.empty();
        EntityMaid driver = TerminalAccountService.findMaid(viewer.getServer(), driverId);
        if (driver == null) return new MaidTransportSnapshot.Snapshot(driverId, "",
                MaidTransportSnapshot.State.OFFLINE, null, null,
                null, null, null,
                "gui.maid_storage_manager_extension.transport.driver_offline");
        CourierData.Data data = CourierData.get(driver);
        MaidTransportSnapshot.State state = state(driver, data);
        String reason = reason(state);
        return new MaidTransportSnapshot.Snapshot(driverId,
                LogisticsDisplayName.encode(driver.getName()), state,
                driver.level().dimension().location(), driver.blockPosition(),
                data.transportPickup() == null ? data.transportPickupAnchor() : data.transportPickup(),
                data.transportDestination() == null
                        ? data.transportDestinationAnchor() : data.transportDestination(),
                data.transportRider(), reason);
    }

    private static MaidTransportSnapshot.State state(EntityMaid driver, CourierData.Data data) {
        return switch (data.phase()) {
            case TRANSPORT_TO_PICKUP -> MaidTransportSnapshot.State.TO_PICKUP;
            case TRANSPORT_WAITING_RIDER -> MaidTransportSnapshot.State.WAITING_RIDER;
            case TRANSPORT_TO_DESTINATION -> MaidTransportSnapshot.State.TO_DESTINATION;
            case TRANSPORT_PLAYER_CONTROLLED -> MaidTransportSnapshot.State.PLAYER_CONTROLLED;
            case TRANSPORT_EMERGENCY_LANDING -> MaidTransportSnapshot.State.EMERGENCY_LANDING;
            default -> !driver.getTask().getUid().equals(CourierTask.TASK_ID)
                    ? MaidTransportSnapshot.State.WRONG_TASK
                    : !EnderPocketCompat.hasBroom(driver)
                    ? MaidTransportSnapshot.State.NO_BROOM
                    : CourierService.hasActiveTransaction(driver)
                    || ExtensionMemoryUtil.getMiscSort(driver).hasInFlight()
                    ? MaidTransportSnapshot.State.BUSY : MaidTransportSnapshot.State.READY;
        };
    }

    private static String reason(MaidTransportSnapshot.State state) {
        return switch (state) {
            case OFFLINE -> "gui.maid_storage_manager_extension.transport.driver_offline";
            case WRONG_TASK -> "gui.maid_storage_manager_extension.transport.driver_wrong_task";
            case NO_BROOM -> "gui.maid_storage_manager_extension.transport.no_broom";
            case BUSY -> "gui.maid_storage_manager_extension.transport.driver_busy";
            default -> "";
        };
    }

    private static void sync(EntityMaid maid, CourierData.Data data) {
        maid.setAndSyncData(CourierData.KEY, data);
    }
}
