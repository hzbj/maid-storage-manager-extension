package io.github.maidstorageextension.terminal;

import com.github.tartaricacid.touhoulittlemaid.entity.item.EntityBroom;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import io.github.maidstorageextension.block.CourierWarehouseStationBlockEntity;
import io.github.maidstorageextension.compat.EnderPocketCompat;
import io.github.maidstorageextension.data.CourierData;
import io.github.maidstorageextension.data.DriverData;
import io.github.maidstorageextension.logistics.LogisticsDisplayName;
import io.github.maidstorageextension.maid.MaidRoleService;
import io.github.maidstorageextension.maid.courier.CourierBroomFlightService;
import io.github.maidstorageextension.maid.courier.CourierFlightStandLocator;
import io.github.maidstorageextension.maid.task.DriverTask;
import io.github.maidstorageextension.network.ExtensionNetwork;
import io.github.maidstorageextension.network.MaidTransportActionPacket;
import io.github.maidstorageextension.network.MaidTransportSnapshotPacket;
import io.github.maidstorageextension.network.MaidPickupGuidancePacket;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;
import studio.fantasyit.maid_storage_manager.Config;
import studio.fantasyit.maid_storage_manager.util.MemoryUtil;

import java.util.Comparator;
import java.util.UUID;

/** Server-authoritative passenger service owned by the independent driver profession. */
public final class MaidTransportService {
    private static final double NEAR_OWNER_DISTANCE_SQR = 8.0D * 8.0D;
    private static final double ARRIVAL_DISTANCE_SQR = 3.0D * 3.0D;
    private static final double NEAR_DESTINATION_DISTANCE_SQR = 16.0D * 16.0D;
    private static final double PASSENGER_TERRAIN_CLEARANCE = 24.0D;

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
            case RETURN_TO_WAREHOUSE -> returnToWarehouse(
                    player, packet.terminal(), driverId, packet.mailbox());
            case CLEAR_STATUS -> clearStatus(player, packet.terminal(), driverId);
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
        ServerLevel level = rider.serverLevel();
        MaidRoleService.Result role = MaidRoleService.ensureDriver(level, driver);
        if (role != MaidRoleService.Result.READY) {
            rider.sendSystemMessage(Component.translatable(role == MaidRoleService.Result.WAREHOUSE_MANAGER
                    ? "message.maid_storage_manager_extension.transport.driver_is_manager"
                    : "message.maid_storage_manager_extension.transport.driver_busy"));
            update(rider, terminal);
            return;
        }

        BlockPos requested = requestedPickup == null
                ? rider.blockPosition() : surfaceAnchor(level, requestedPickup, rider.getBlockY());
        ensureSearchAreaLoaded(level, requested, 12);
        BlockPos actualPickup = CourierFlightStandLocator.findLanding(level, requested, 12);
        if (actualPickup == null) {
            rider.sendSystemMessage(Component.translatable(
                    "message.maid_storage_manager_extension.transport.pickup_no_landing"));
            update(rider, terminal);
            return;
        }

        CourierData.Data courierSettings = CourierData.get(driver);
        int broomDistance = courierSettings.broomFlightDistance();
        BlockPos takeoff = CourierFlightStandLocator.findTakeoff(level, driver, broomDistance);
        if (takeoff == null && driver.distanceToSqr(rider) <= NEAR_OWNER_DISTANCE_SQR) {
            rider.sendSystemMessage(Component.translatable(
                    "message.maid_storage_manager_extension.transport.pickup_blocked"));
            update(rider, terminal);
            return;
        }
        if (takeoff == null) takeoff = nearestMailboxPad(rider, terminal, driver);
        if (takeoff == null) {
            rider.sendSystemMessage(Component.translatable(
                    "message.maid_storage_manager_extension.courier.no_safe_takeoff"));
            update(rider, terminal);
            return;
        }

        BlockPos destination = surfaceAnchor(
                level, requestedDestination, rider.getBlockY());
        DriverData.Data data = DriverData.get(driver);
        data.begin(rider.getUUID(), level.dimension().location(), requested,
                actualPickup, destination, takeoff, broomDistance);
        driver.setHomeModeEnable(false);
        sync(driver, data);
        rider.sendSystemMessage(Component.translatable(
                "message.maid_storage_manager_extension.transport.started"));
        update(rider, terminal);
    }

    private static String startFailure(ServerPlayer rider, EntityMaid driver,
                                       BlockPos requestedPickup, BlockPos destination) {
        if (destination == null) {
            return "message.maid_storage_manager_extension.transport.destination_required";
        }
        ServerLevel level = rider.serverLevel();
        if (!level.getWorldBorder().isWithinBounds(
                new BlockPos(destination.getX(), level.getSeaLevel(), destination.getZ()))) {
            return "message.maid_storage_manager_extension.transport.outside_world_border";
        }
        if (requestedPickup != null && !level.getWorldBorder().isWithinBounds(
                new BlockPos(requestedPickup.getX(), level.getSeaLevel(), requestedPickup.getZ()))) {
            return "message.maid_storage_manager_extension.transport.outside_world_border";
        }
        if (driver == null) return "message.maid_storage_manager_extension.transport.driver_offline";
        if (driver.level() != rider.level()) {
            return "message.maid_storage_manager_extension.transport.driver_same_dimension";
        }
        if (DriverData.get(driver).activeTrip()) {
            return "message.maid_storage_manager_extension.transport.driver_busy";
        }
        if (!EnderPocketCompat.hasBroom(driver)) {
            return "message.maid_storage_manager_extension.transport.no_broom";
        }
        return null;
    }

    private static void end(ServerPlayer rider, UUID terminal, UUID driverId) {
        EntityMaid driver = TerminalAccountService.findMaid(rider.getServer(), driverId);
        if (driver == null) {
            update(rider, terminal);
            return;
        }
        DriverData.Data data = DriverData.get(driver);
        if (!rider.getUUID().equals(data.rider())) return;
        if (data.phase() == DriverData.Phase.TO_DESTINATION
                && driver.level() instanceof ServerLevel level
                && handControlToRider(level, driver, data, rider)) {
            rider.sendSystemMessage(Component.translatable(
                    "message.maid_storage_manager_extension.transport.control_transferred"));
        } else if (data.phase() == DriverData.Phase.TO_PICKUP
                || data.phase() == DriverData.Phase.WAITING_RIDER) {
            clearGuidance(rider);
            cancelBeforeBoarding(driver, data);
            rider.sendSystemMessage(Component.translatable(
                    "message.maid_storage_manager_extension.transport.cancelled"));
        }
        update(rider, terminal);
    }

    private static void returnToWarehouse(ServerPlayer owner, UUID terminal, UUID driverId,
                                          MailboxKey mailbox) {
        TerminalAccountService.Session session = TerminalAccountService.authenticate(owner, terminal);
        EntityMaid driver = TerminalAccountService.findMaid(owner.getServer(), driverId);
        if (session == null || driver == null || mailbox == null
                || session.account().mailboxes().stream().noneMatch(value ->
                value.sameLocation(mailbox.dimension(), mailbox.position()))
                || !driver.level().dimension().location().equals(mailbox.dimension())) {
            owner.sendSystemMessage(Component.translatable(
                    "message.maid_storage_manager_extension.transport.return_invalid_mailbox"));
            update(owner, terminal);
            return;
        }
        DriverData.Data data = DriverData.get(driver);
        if (data.activeTrip()) {
            owner.sendSystemMessage(Component.translatable(
                    "message.maid_storage_manager_extension.transport.driver_busy"));
            update(owner, terminal);
            return;
        }
        ServerLevel level = (ServerLevel) driver.level();
        if (!(level.getBlockEntity(mailbox.position())
                instanceof CourierWarehouseStationBlockEntity station)
                || station.landingPos() == null) {
            owner.sendSystemMessage(Component.translatable(
                    "message.maid_storage_manager_extension.transport.return_invalid_mailbox"));
            update(owner, terminal);
            return;
        }
        data.flight().transportMode(CourierData.TransportMode.BROOM);
        data.beginReturn(mailbox, station.landingPos());
        driver.setHomeModeEnable(false);
        sync(driver, data);
        owner.sendSystemMessage(Component.translatable(
                "message.maid_storage_manager_extension.transport.returning_to_warehouse"));
        update(owner, terminal);
    }

    /** Called every maid tick; driver flight is independent from courier cargo processing. */
    public static void tick(ServerLevel level, EntityMaid driver) {
        DriverData.Data data = DriverData.get(driver);
        if (!data.activeTrip()) return;
        CourierBroomFlightService.keepActiveCourierLoaded(level, driver);
        ServerPlayer rider = data.rider() == null ? null
                : level.getServer().getPlayerList().getPlayer(data.rider());
        if (data.phase() != DriverData.Phase.RETURNING_TO_WAREHOUSE
                && (rider == null || !rider.isAlive())) {
            if (data.phase() == DriverData.Phase.TO_PICKUP
                    || data.phase() == DriverData.Phase.WAITING_RIDER) {
                cancelBeforeBoarding(driver, data);
            } else {
                beginEmergencyLanding(level, driver, data);
            }
            return;
        }
        if (rider != null && rider.level() != level) {
            beginEmergencyLanding(level, driver, data);
            return;
        }
        switch (data.phase()) {
            case TO_PICKUP -> tickToPickup(level, driver, data, rider);
            case WAITING_RIDER -> tickWaiting(level, driver, data, rider);
            case TO_DESTINATION -> tickToDestination(level, driver, data, rider);
            case RETURNING_TO_WAREHOUSE -> tickReturnToWarehouse(level, driver, data);
            case PLAYER_CONTROLLED -> tickPlayerControlled(level, driver, data, rider);
            case EMERGENCY_LANDING -> tickEmergency(level, driver, data);
            default -> { }
        }
    }

    private static void tickToPickup(ServerLevel level, EntityMaid driver,
                                     DriverData.Data data, ServerPlayer rider) {
        BlockPos target = data.pickup();
        if (target == null) {
            cancelBeforeBoarding(driver, data);
            return;
        }
        if (!CourierBroomFlightService.isAirborne(level, driver, data.flight())
                && MaidTransportBoardingPolicy.withinRange(
                driver.distanceToSqr(target.getCenter()))) {
            beginWaiting(level, driver, data, rider, target);
            return;
        }
        if (!CourierBroomFlightService.isAirborne(level, driver, data.flight())
                && horizontalDistanceSqr(driver.blockPosition(), target)
                <= (double) data.flight().broomFlightDistance()
                * data.flight().broomFlightDistance()) {
            MemoryUtil.setTarget(driver, target, (float) Config.viewChangeSpeed);
            driver.getNavigation().moveTo(target.getX() + 0.5, target.getY(),
                    target.getZ() + 0.5, Config.viewChangeSpeed);
            return;
        }
        CourierBroomFlightService.TickResult result = CourierBroomFlightService.tickPassenger(
                level, driver, data.flight(), CourierData.Phase.TRANSPORT_TO_PICKUP,
                target.getCenter(), data.takeoff(), target);
        if (result == CourierBroomFlightService.TickResult.LANDING_SEARCH_EXHAUSTED) {
            cancelBeforeBoarding(driver, data);
            rider.sendSystemMessage(Component.translatable(
                    "message.maid_storage_manager_extension.transport.pickup_no_landing"));
        } else if (result == CourierBroomFlightService.TickResult.LANDED) {
            beginWaiting(level, driver, data, rider, target);
        }
    }

    private static void beginWaiting(ServerLevel level, EntityMaid driver,
                                     DriverData.Data data, ServerPlayer rider, BlockPos pickup) {
        CourierBroomFlightService.cleanup(driver, data.flight());
        CourierBroomFlightService.holdPosition(driver);
        data.pickup(pickup);
        data.setPhase(DriverData.Phase.WAITING_RIDER);
        EntityBroom broom = CourierBroomFlightService.createWaitingTransportBroom(
                level, driver, data.flight(), rider.getUUID());
        if (broom == null) {
            cancelBeforeBoarding(driver, data);
            return;
        }
        sync(driver, data);
        sendGuidance(rider, driver, pickup);
        rider.sendSystemMessage(Component.translatable(
                "message.maid_storage_manager_extension.transport.waiting"));
    }

    private static void tickWaiting(ServerLevel level, EntityMaid driver,
                                    DriverData.Data data, ServerPlayer rider) {
        EntityBroom broom = CourierBroomFlightService.transportBroom(level, driver, data.flight());
        if (broom == null) {
            broom = CourierBroomFlightService.createWaitingTransportBroom(
                    level, driver, data.flight(), rider.getUUID());
            if (broom == null) return;
        }
        broom.setDeltaMovement(Vec3.ZERO);
        if (!broom.getPassengers().contains(rider)) return;
        clearGuidance(rider);
        data.setPhase(DriverData.Phase.TO_DESTINATION);
        data.flight().retargetFlight(CourierData.Phase.TRANSPORT_TO_DESTINATION);
        sync(driver, data);
        rider.sendSystemMessage(Component.translatable(
                "message.maid_storage_manager_extension.transport.departed"));
    }

    private static void tickToDestination(ServerLevel level, EntityMaid driver,
                                          DriverData.Data data, ServerPlayer rider) {
        EntityBroom broom = CourierBroomFlightService.transportBroom(level, driver, data.flight());
        if (broom == null || !broom.getPassengers().contains(rider)
                || data.destinationAnchor() == null) {
            beginEmergencyLanding(level, driver, data);
            return;
        }
        Vec3 destCenter = data.destinationAnchor().getCenter();
        // Fly toward the destination area first; only search for a landing spot upon arrival
        // so the maid always departs even when the destination has no landing point yet.
        if (horizontalDistanceSqr(broom.blockPosition(), data.destinationAnchor())
                > NEAR_DESTINATION_DISTANCE_SQR) {
            CourierBroomFlightService.flyToward(level, driver, broom, data.flight(),
                    destCenter, PASSENGER_TERRAIN_CLEARANCE);
            return;
        }
        CourierBroomFlightService.TickResult result = CourierBroomFlightService.tickPassenger(
                level, driver, data.flight(), CourierData.Phase.TRANSPORT_TO_DESTINATION,
                destCenter);
        if (result == CourierBroomFlightService.TickResult.LANDED) {
            data.destination(data.flight().flightLandingPos());
            clearGuidance(rider);
            finishTrip(driver, data, true);
            rider.sendSystemMessage(Component.translatable(
                    "message.maid_storage_manager_extension.transport.arrived"));
        } else if (result == CourierBroomFlightService.TickResult.LANDING_SEARCH_EXHAUSTED
                && handControlToRider(level, driver, data, rider)) {
            rider.sendSystemMessage(Component.translatable(
                    "message.maid_storage_manager_extension.transport.no_landing_control"));
        }
    }

    private static void tickReturnToWarehouse(
            ServerLevel level, EntityMaid driver, DriverData.Data data) {
        BlockPos target = data.destinationAnchor();
        if (target == null) {
            finishTrip(driver, data, false);
            return;
        }
        if (!CourierBroomFlightService.isAirborne(level, driver, data.flight())
                && driver.distanceToSqr(target.getCenter()) <= ARRIVAL_DISTANCE_SQR) {
            CourierBroomFlightService.cleanup(driver, data.flight());
            data.standby();
            setHomeAtCurrentPosition(driver);
            sync(driver, data);
            return;
        }
        CourierBroomFlightService.TickResult result = CourierBroomFlightService.tickPassenger(
                level, driver, data.flight(), CourierData.Phase.TRANSPORT_TO_PICKUP,
                target.getCenter(), null, target);
        if (result == CourierBroomFlightService.TickResult.LANDED) {
            data.standby();
            setHomeAtCurrentPosition(driver);
            sync(driver, data);
        }
    }

    private static void tickPlayerControlled(ServerLevel level, EntityMaid driver,
                                             DriverData.Data data, ServerPlayer rider) {
        EntityBroom broom = CourierBroomFlightService.transportBroom(level, driver, data.flight());
        if (rider != null) clearGuidance(rider);
        if (broom != null && broom.getPassengers().contains(rider)) return;
        beginEmergencyLanding(level, driver, data);
    }

    private static void beginEmergencyLanding(ServerLevel level, EntityMaid driver,
                                              DriverData.Data data) {
        EntityBroom broom = CourierBroomFlightService.transportBroom(level, driver, data.flight());
        if (broom == null) {
            finishTrip(driver, data, true);
            return;
        }
        CourierBroomFlightService.resumeTransportAutopilot(broom, driver, data.flight());
        BlockPos current = BlockPos.containing(broom.position());
        data.destination(current);
        data.setPhase(DriverData.Phase.EMERGENCY_LANDING);
        data.flight().retargetFlight(CourierData.Phase.TRANSPORT_EMERGENCY_LANDING);
        sync(driver, data);
    }

    private static void tickEmergency(ServerLevel level, EntityMaid driver,
                                      DriverData.Data data) {
        BlockPos target = data.destination() == null ? driver.blockPosition() : data.destination();
        CourierBroomFlightService.TickResult result = CourierBroomFlightService.tickPassenger(
                level, driver, data.flight(), CourierData.Phase.TRANSPORT_EMERGENCY_LANDING,
                target.getCenter());
        if (result == CourierBroomFlightService.TickResult.LANDED) {
            finishTrip(driver, data, true);
        }
    }

    private static boolean handControlToRider(ServerLevel level, EntityMaid driver,
                                              DriverData.Data data, ServerPlayer rider) {
        EntityBroom broom = CourierBroomFlightService.transportBroom(level, driver, data.flight());
        if (broom == null || !broom.getPassengers().contains(rider)) return false;
        if (!CourierBroomFlightService.handControlToRider(
                broom, driver, rider, data.flight())) return false;
        data.setPhase(DriverData.Phase.PLAYER_CONTROLLED);
        sync(driver, data);
        return true;
    }

    private static void cancelBeforeBoarding(EntityMaid driver, DriverData.Data data) {
        CourierBroomFlightService.discardTransportBroom(driver, data.flight());
        finishTrip(driver, data, true);
    }

    private static void finishTrip(EntityMaid driver, DriverData.Data data, boolean followOwner) {
        CourierBroomFlightService.cleanup(driver, data.flight());
        data.finishTrip(followOwner);
        if (followOwner) {
            driver.setHomeModeEnable(false);
            // Clear the stale schedule position so TLM won't block the next Home-mode
            // activation with "too far from recorded position" when the maid has followed
            // the owner away from the previous work/idle/sleep anchor.
            driver.getSchedulePos().clear(driver);
        }
        sync(driver, data);
    }

    private static void clearStatus(ServerPlayer owner, UUID terminal, UUID driverId) {
        EntityMaid driver = TerminalAccountService.findMaid(owner.getServer(), driverId);
        if (driver == null || !(driver.level() instanceof ServerLevel level)) {
            owner.sendSystemMessage(Component.translatable(
                    "message.maid_storage_manager_extension.transport.driver_offline"));
            update(owner, terminal);
            return;
        }
        DriverData.Data data = DriverData.get(driver);
        if (CourierBroomFlightService.isAirborne(level, driver, data.flight())) {
            BlockPos landing = CourierFlightStandLocator.findLanding(level,
                    driver.blockPosition(), 0, data.flight().broomFlightDistance(), 0);
            if (landing == null) {
                owner.sendSystemMessage(Component.translatable(
                        "message.maid_storage_manager_extension.transport.clear_status_no_safe_landing"));
                update(owner, terminal);
                return;
            }
            CourierBroomFlightService.forceLand(driver, data.flight(), landing);
        }
        CourierBroomFlightService.discardTransportBroom(driver, data.flight());
        CourierBroomFlightService.cleanup(driver, data.flight());
        data.finishTrip(false);
        setHomeAtCurrentPosition(driver);
        sync(driver, data);
        owner.sendSystemMessage(Component.translatable(
                "message.maid_storage_manager_extension.transport.clear_status_complete"));
        update(owner, terminal);
    }

    private static void setHomeAtCurrentPosition(EntityMaid maid) {
        BlockPos home = maid.blockPosition();
        var schedule = maid.getSchedulePos();
        schedule.setWorkPos(home);
        schedule.setIdlePos(home);
        schedule.setSleepPos(home);
        schedule.setDimension(maid.level().dimension().location());
        schedule.setConfigured(true);
        maid.setHomeModeEnable(true);
        schedule.restrictTo(maid);
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
        DriverData.Data data = DriverData.get(driver);
        MaidTransportSnapshot.State state = state(driver, data);
        return new MaidTransportSnapshot.Snapshot(driverId,
                LogisticsDisplayName.encode(driver.getName()), state,
                driver.level().dimension().location(), driver.blockPosition(),
                data.pickup() == null ? data.requestedPickup() : data.pickup(),
                data.destination() == null ? data.destinationAnchor() : data.destination(),
                data.rider(), reason(state));
    }

    private static MaidTransportSnapshot.State state(EntityMaid driver, DriverData.Data data) {
        return switch (data.phase()) {
            case TO_PICKUP, LOCATING_PICKUP -> MaidTransportSnapshot.State.TO_PICKUP;
            case WAITING_RIDER -> MaidTransportSnapshot.State.WAITING_RIDER;
            case TO_DESTINATION -> MaidTransportSnapshot.State.TO_DESTINATION;
            case RETURNING_TO_WAREHOUSE -> MaidTransportSnapshot.State.RETURNING_TO_WAREHOUSE;
            case WAREHOUSE_STANDBY -> MaidTransportSnapshot.State.WAREHOUSE_STANDBY;
            case FOLLOWING_OWNER -> driver.isHomeModeEnable()
                    ? MaidTransportSnapshot.State.READY
                    : MaidTransportSnapshot.State.FOLLOWING_OWNER;
            case PLAYER_CONTROLLED -> MaidTransportSnapshot.State.PLAYER_CONTROLLED;
            case EMERGENCY_LANDING -> MaidTransportSnapshot.State.EMERGENCY_LANDING;
            default -> !driver.getTask().getUid().equals(DriverTask.TASK_ID)
                    ? MaidTransportSnapshot.State.READY
                    : !EnderPocketCompat.hasBroom(driver)
                    ? MaidTransportSnapshot.State.NO_BROOM : MaidTransportSnapshot.State.READY;
        };
    }

    private static String reason(MaidTransportSnapshot.State state) {
        return switch (state) {
            case OFFLINE -> "gui.maid_storage_manager_extension.transport.driver_offline";
            case NO_BROOM -> "gui.maid_storage_manager_extension.transport.no_broom";
            case BUSY -> "gui.maid_storage_manager_extension.transport.driver_busy";
            case WRONG_TASK -> "gui.maid_storage_manager_extension.transport.not_driver";
            default -> "";
        };
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

    private static void ensureSearchAreaLoaded(ServerLevel level, BlockPos center, int radius) {
        for (int x = center.getX() - radius; x <= center.getX() + radius; x += 16) {
            for (int z = center.getZ() - radius; z <= center.getZ() + radius; z += 16) {
                level.getChunk(x >> 4, z >> 4);
            }
        }
        level.getChunk((center.getX() + radius) >> 4, (center.getZ() + radius) >> 4);
    }

    private static BlockPos nearestMailboxPad(
            ServerPlayer owner, UUID terminal, EntityMaid driver) {
        TerminalAccountService.Session session = TerminalAccountService.authenticate(owner, terminal);
        if (session == null || !(driver.level() instanceof ServerLevel level)) return null;
        return session.account().mailboxes().stream()
                .filter(mailbox -> level.dimension().location().equals(mailbox.dimension()))
                .map(mailbox -> level.getBlockEntity(mailbox.position())
                        instanceof CourierWarehouseStationBlockEntity station
                        ? station.landingPos() : null)
                .filter(java.util.Objects::nonNull)
                .filter(pos -> CourierFlightStandLocator.hasSafeVerticalColumn(level, pos))
                .min(Comparator.comparingDouble(pos -> driver.distanceToSqr(pos.getCenter())))
                .orElse(null);
    }

    private static double horizontalDistanceSqr(BlockPos first, BlockPos second) {
        double dx = first.getX() - second.getX();
        double dz = first.getZ() - second.getZ();
        return dx * dx + dz * dz;
    }

    private static void sync(EntityMaid maid, DriverData.Data data) {
        maid.setAndSyncData(DriverData.KEY, data);
    }

    private static void sendGuidance(
            ServerPlayer rider, EntityMaid driver, BlockPos pickup) {
        ExtensionNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> rider),
                new MaidPickupGuidancePacket(true, driver.getUUID(),
                        driver.level().dimension().location(), pickup));
    }

    private static void clearGuidance(ServerPlayer rider) {
        if (rider == null) return;
        ExtensionNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> rider),
                new MaidPickupGuidancePacket(false, null, null, null));
    }
}
