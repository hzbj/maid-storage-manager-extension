package io.github.maidstorageextension.maid.courier;

import com.github.tartaricacid.touhoulittlemaid.entity.item.EntityBroom;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import io.github.maidstorageextension.compat.touhoulittlemaid.BroomAutopilotAccess;
import io.github.maidstorageextension.data.CourierData;
import io.github.maidstorageextension.data.DriverData;
import io.github.maidstorageextension.scan.StorageScanService;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import studio.fantasyit.maid_storage_manager.Config;
import studio.fantasyit.maid_storage_manager.util.MemoryUtil;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Drives safe staged walking, high flight, and open-air landing for broom courier legs. */
public final class CourierBroomFlightService {
    public static final String TAG_COURIER_BROOM = "MaidStorageExtensionCourierBroom";
    public static final String TAG_TRANSPORT_RIDER = "MaidStorageExtensionTransportRider";
    public static final String TAG_PLAYER_CONTROLLED = "MaidStorageExtensionPlayerControlled";
    private static final double CRUISE_CLEARANCE = 32.0;
    private static final double TERRAIN_CLEARANCE = 8.0;
    private static final double PASSENGER_CRUISE_CLEARANCE = 56.0;
    private static final double PASSENGER_TERRAIN_CLEARANCE = 24.0;
    private static final int PASSENGER_LANDING_RADIUS = 12;
    private static final double STAGING_ARRIVAL = 1.5;
    private static final double LANDING_ARRIVAL = 0.8;
    private static final TicketType<UUID> FLIGHT_TICKET = TicketType.create(
            "maid_storage_extension_courier_broom", UUID::compareTo, 40);

    private CourierBroomFlightService() {
    }

    public enum TickResult {
        CONTINUE,
        LANDED,
        LANDING_SEARCH_EXHAUSTED,
        STATION_UNAVAILABLE
    }

    public static TickResult tick(ServerLevel level, EntityMaid courier, CourierData.Data data,
                                  CourierData.Phase leg, Vec3 target) {
        return tick(level, courier, data, leg, target, null, null);
    }

    public static TickResult tick(ServerLevel level, EntityMaid courier, CourierData.Data data,
                                  CourierData.Phase leg, Vec3 target,
                                  BlockPos requiredTakeoff, BlockPos requiredLanding) {
        return tickInternal(level, courier, data, leg, target, requiredTakeoff, requiredLanding,
                data.broomFlightDistance(), false, CRUISE_CLEARANCE, TERRAIN_CLEARANCE);
    }

    public static TickResult tickPassenger(ServerLevel level, EntityMaid courier,
                                           CourierData.Data data, CourierData.Phase leg,
                                           Vec3 target) {
        return tickPassenger(level, courier, data, leg, target, null, null);
    }

    public static TickResult tickPassenger(ServerLevel level, EntityMaid courier,
                                           CourierData.Data data, CourierData.Phase leg,
                                           Vec3 target, BlockPos requiredTakeoff,
                                           BlockPos requiredLanding) {
        return tickInternal(level, courier, data, leg, target, requiredTakeoff, requiredLanding,
                PASSENGER_LANDING_RADIUS, true, PASSENGER_CRUISE_CLEARANCE,
                PASSENGER_TERRAIN_CLEARANCE);
    }

    private static TickResult tickInternal(ServerLevel level, EntityMaid courier,
                                           CourierData.Data data, CourierData.Phase leg,
                                           Vec3 target, BlockPos requiredTakeoff,
                                           BlockPos requiredLanding, int searchRadius,
                                           boolean forceBroom, double cruiseClearance,
                                           double terrainClearance) {
        if (!data.transportMode().usesBroom() || target == null) {
            cleanup(courier, data);
            return TickResult.CONTINUE;
        }

        if (data.flightLeg() != leg) {
            discardVehicle(courier, data);
            data.prepareFlight(leg, null);
            sync(courier, data);
        }
        if (data.flightLanded()) return TickResult.LANDED;

        EntityBroom broom = findExisting(level, courier, data);
        if (broom == null && !forceBroom && !CourierFlightPolicy.shouldUseBroom(
                target.x - courier.getX(), target.z - courier.getZ(),
                data.broomFlightDistance())) {
            // Do not create a second pathfinder here. Mark the flight portion complete so the
            // courier state machine resumes Touhou Little Maid's existing MemoryUtil navigation.
            data.finishFlight();
            sync(courier, data);
            return TickResult.LANDED;
        }

        BlockPos targetPos = BlockPos.containing(target);
        keepLoaded(level, targetPos, courier.getUUID());
        if (!level.hasChunkAt(targetPos)) return TickResult.CONTINUE;

        searchRadius = Math.max(1, searchRadius);
        if (broom == null) {
            BlockPos takeoff = data.flightTakeoffPos();
            if (requiredTakeoff != null
                    && !CourierWarehouseStationValidator.hasValidPad(level, requiredTakeoff)) {
                hover(courier);
                return TickResult.STATION_UNAVAILABLE;
            }
            if (requiredTakeoff != null) takeoff = requiredTakeoff.immutable();
            if (takeoff == null || requiredTakeoff == null
                    && !CourierFlightStandLocator.hasSafeVerticalColumn(level, takeoff)) {
                takeoff = requiredTakeoff != null ? requiredTakeoff
                        : CourierFlightStandLocator.findTakeoff(level, courier, searchRadius);
                if (takeoff == null) {
                    warnOnce(courier, data,
                            "message.maid_storage_manager_extension.courier.no_safe_takeoff");
                    return TickResult.CONTINUE;
                }
                data.prepareFlight(leg, takeoff);
                sync(courier, data);
            } else if (!takeoff.equals(data.flightTakeoffPos())) {
                data.prepareFlight(leg, takeoff);
                sync(courier, data);
            }
            BlockPos courierFeet = courier.blockPosition();
            boolean atTakeoff = requiredTakeoff != null
                    ? reachedSafeStationTakeoff(level, courierFeet, takeoff)
                    : courier.distanceToSqr(takeoff.getCenter())
                    <= STAGING_ARRIVAL * STAGING_ARRIVAL;
            if (!CourierFlightPolicy.readyToSearchLanding(false, atTakeoff)) {
                // Destination chunks and landing candidates are deliberately not considered yet.
                // Touhou Little Maid's original navigator owns this ground stage.
                dispatchTakeoffRoute(level, courier, takeoff, requiredTakeoff != null,
                        data.groundTeleportFailures());
                watchTakeoffRoute(level, courier, data);
                return TickResult.CONTINUE;
            }
            data.clearGroundApproach();
            courier.getNavigation().stop();
            MemoryUtil.clearTarget(courier);
        }

        BlockPos landing = data.flightLandingPos();
        if (requiredLanding != null) {
            if (!CourierWarehouseStationValidator.hasValidPad(level, requiredLanding)) {
                hover(courier);
                return TickResult.STATION_UNAVAILABLE;
            }
            if (!requiredLanding.equals(landing)) {
                landing = requiredLanding.immutable();
                data.flightLandingPos(landing);
                sync(courier, data);
            }
        }
        if (landing == null && data.flightWarningSent() && level.getGameTime() % 20L != 0L) {
            hover(courier);
            return TickResult.CONTINUE;
        }
        if (requiredLanding == null && landing != null
                && (!withinHorizontalRadius(landing, targetPos, searchRadius)
                || !CourierFlightStandLocator.hasSafeVerticalColumn(level, landing))) {
            data.rejectFlightLanding();
            landing = null;
        }
        if (requiredLanding == null && landing == null) {
            CourierLandingSearchPolicy.Window window = currentSearchWindow(data, searchRadius);
            keepLandingAreaLoaded(level, targetPos, window.maxRadius(), courier.getUUID());
            if (!CourierFlightStandLocator.searchAreaLoaded(level, targetPos, window.maxRadius())) {
                hover(courier);
                return TickResult.CONTINUE;
            }
            landing = CourierFlightStandLocator.findLanding(level, targetPos,
                    window.minRadius(), window.maxRadius(), data.flightRejectedLandings());
            if (landing == null) {
                CourierLandingSearchPolicy.Window next = CourierLandingSearchPolicy.next(
                        window, searchRadius);
                if (next.exhausted()) {
                    hover(courier);
                    warnOnce(courier, data,
                            "message.maid_storage_manager_extension.courier.no_safe_landing");
                    return TickResult.LANDING_SEARCH_EXHAUSTED;
                }
                data.flightSearchWindow(next.minRadius(), next.maxRadius());
                data.flightRejectedLandings(0);
                sync(courier, data);
                return TickResult.CONTINUE;
            }
            data.flightLandingPos(landing);
            sync(courier, data);
        }

        if (broom == null) {
            broom = create(level, courier, data, leg, landing, cruiseClearance);
            if (broom == null) {
                warnOnce(courier, data,
                        "message.maid_storage_manager_extension.courier.broom_spawn_failed");
                return TickResult.CONTINUE;
            }
        }
        clearWarning(courier, data);

        return fly(level, courier, broom, data, landing, terrainClearance);
    }

    public static boolean landedFor(CourierData.Data data, CourierData.Phase phase) {
        return data.transportMode().usesBroom()
                && data.flightLanded() && data.flightLeg() == phase;
    }

    public static boolean isCourierBroom(Entity entity) {
        return entity instanceof EntityBroom
                && entity.getPersistentData().hasUUID(TAG_COURIER_BROOM);
    }

    /** A mounted/remembered courier broom exists only between takeoff and landing. */
    public static boolean isAirborne(ServerLevel level, EntityMaid courier,
                                     CourierData.Data data) {
        if (isCourierBroom(courier.getVehicle())) return true;
        UUID known = data.courierBroom();
        return known != null && isCourierBroom(level.getEntity(known));
    }

    /** Stops flight movement without dismounting the maid in mid-air. */
    public static void holdPosition(EntityMaid courier) {
        hover(courier);
    }

    public static void keepActiveCourierLoaded(ServerLevel level, EntityMaid courier) {
        keepLoaded(level, courier.blockPosition(), courier.getUUID());
    }

    public static void keepCourierContextLoaded(ServerLevel level, EntityMaid courier,
                                                CourierData.Data data) {
        keepActiveCourierLoaded(level, courier);
        if (data.warehousePos() != null && data.warehouseDimension() != null
                && data.warehouseDimension().equals(level.dimension().location())) {
            keepLoaded(level, data.warehousePos(), courier.getUUID());
        }
        CourierData.WarehouseBinding binding = data.binding(data.warehouse());
        if (binding != null) {
            if (binding.stationPos() != null && binding.stationDimension() != null
                    && binding.stationDimension().equals(level.dimension().location())) {
                keepLoaded(level, binding.stationPos(), courier.getUUID());
            }
            if (binding.mailboxPos() != null && binding.mailboxDimension() != null
                    && binding.mailboxDimension().equals(level.dimension().location())) {
                keepLoaded(level, binding.mailboxPos(), courier.getUUID());
            }
        }
        if (!CourierWarehouseLoadingPolicy.shouldKeepWarehouseTaskLoaded(data.phase())
                || data.warehouse() == null) return;
        Entity entity = level.getEntity(data.warehouse());
        if (entity instanceof EntityMaid warehouse && warehouse.isAlive()) {
            keepWarehouseTaskLoaded(level, courier.getUUID(), warehouse);
        }
    }

    private static void keepWarehouseTaskLoaded(ServerLevel level, UUID courier,
                                                EntityMaid warehouse) {
        // A storage maid may leave her recorded work centre while visiting a chest or returning
        // rejected cargo. Keep her moving position, full configured work scope and the two MSM
        // navigation memories ticking until the courier journal leaves its warehouse phase.
        keepLoaded(level, warehouse.blockPosition(), courier);
        StorageScanService.ScanScope scope = StorageScanService.getScope(warehouse);
        int workChunkRadius = CourierWarehouseLoadingPolicy.chunkRadius(
                scope.center().getX(), scope.center().getZ(), scope.radius());
        keepLoaded(level, scope.center(), workChunkRadius, courier);
        BlockPos movementTarget = MemoryUtil.getTargetPos(warehouse);
        if (movementTarget != null) keepLoaded(level, movementTarget, courier);
        BlockPos interactionTarget = MemoryUtil.getInteractPos(warehouse);
        if (interactionTarget != null) keepLoaded(level, interactionTarget, courier);
    }

    public static void cleanup(EntityMaid courier, CourierData.Data data) {
        discardVehicle(courier, data);
        if (data.courierBroom() != null || data.flightLeg() != null
                || data.flightTakeoffPos() != null || data.flightLandingPos() != null
                || data.flightLanded()) {
            data.clearFlight();
            sync(courier, data);
        }
    }

    public static void forceLand(EntityMaid courier, CourierData.Data data, BlockPos landing) {
        discardVehicle(courier, data);
        courier.setPos(landing.getX() + 0.5, landing.getY(), landing.getZ() + 0.5);
        courier.setDeltaMovement(Vec3.ZERO);
        data.flightLandingPos(landing);
        data.finishFlight();
        sync(courier, data);
    }

    private static TickResult fly(ServerLevel level, EntityMaid courier, EntityBroom broom,
                                  CourierData.Data data, BlockPos landing,
                                  double terrainClearance) {
        Vec3 destination = Vec3.atBottomCenterOf(landing).add(0.0, 0.2, 0.0);
        Vec3 position = broom.position();
        Vec3 horizontal = new Vec3(destination.x - position.x, 0.0, destination.z - position.z);
        double horizontalDistance = horizontal.length();
        if (horizontalDistance <= LANDING_ARRIVAL
                && Math.abs(position.y - destination.y) <= LANDING_ARRIVAL) {
            land(courier, broom, data, landing);
            return TickResult.LANDED;
        }
        return cruise(level, courier, broom, data, destination, terrainClearance);
    }

    /**
     * Moves an airborne broom toward a target position without attempting to land. Used by the
     * driver transport service to fly toward the destination area before searching for a landing
     * spot, so the maid always departs even when no landing point is found yet.
     */
    public static void flyToward(ServerLevel level, EntityMaid courier, EntityBroom broom,
                                 CourierData.Data data, Vec3 target, double terrainClearance) {
        Vec3 position = broom.position();
        Vec3 horizontal = new Vec3(target.x - position.x, 0.0, target.z - position.z);
        if (horizontal.lengthSqr() <= LANDING_ARRIVAL * LANDING_ARRIVAL) return;
        cruise(level, courier, broom, data, target, terrainClearance);
    }

    private static TickResult cruise(ServerLevel level, EntityMaid courier, EntityBroom broom,
                                     CourierData.Data data, Vec3 destination,
                                     double terrainClearance) {
        Vec3 position = broom.position();
        Vec3 horizontal = new Vec3(destination.x - position.x, 0.0, destination.z - position.z);
        double horizontalDistance = horizontal.length();

        Vec3 direction = horizontalDistance < 1.0e-4
                ? Vec3.ZERO : horizontal.scale(1.0 / horizontalDistance);
        int terrainCruise = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                Mth.floor(position.x + direction.x), Mth.floor(position.z + direction.z))
                + Mth.ceil(terrainClearance);
        if (horizontalDistance > 6.0 && terrainCruise > data.flightCruiseY()) {
            data.flight(broom.getUUID(), data.flightLeg(),
                    Math.min(terrainCruise, level.getMaxBuildHeight() - 8));
            sync(courier, data);
        }

        CourierFlightMotionPolicy.Motion motion = CourierFlightMotionPolicy.plan(
                horizontalDistance, position.y, data.flightCruiseY(), destination.y);

        Vec3 next = position.add(direction.scale(motion.horizontalSpeed()))
                .add(0.0, motion.verticalSpeed(), 0.0);
        keepLoaded(level, BlockPos.containing(next), courier.getUUID());
        if (!level.hasChunkAt(BlockPos.containing(next))) {
            broom.setDeltaMovement(Vec3.ZERO);
            return TickResult.CONTINUE;
        }
        if (!canOccupy(level, courier, broom, position, next)) {
            int raisedCruise = Math.min(level.getMaxBuildHeight() - 8,
                    Math.max(data.flightCruiseY() + 4, Mth.ceil(position.y + 4.0)));
            if (raisedCruise > data.flightCruiseY()) {
                data.flight(broom.getUUID(), data.flightLeg(), raisedCruise);
                sync(courier, data);
            }
            Vec3 upward = position.add(0.0, CourierFlightMotionPolicy.ASCEND_SPEED, 0.0);
            if (!canOccupy(level, courier, broom, position, upward)) {
                broom.setDeltaMovement(Vec3.ZERO);
                CourierLandingSearchPolicy.Failure failure = CourierLandingSearchPolicy.fail(
                        data.flightLandingFailures());
                if (failure.reselect()) {
                    data.rejectFlightLanding();
                } else {
                    data.flightLandingFailures(failure.failures());
                }
                sync(courier, data);
                return TickResult.CONTINUE;
            }
            next = upward;
        }

        if (data.flightLandingFailures() != 0) {
            data.flightLandingFailures(0);
            sync(courier, data);
        }

        Vec3 movement = next.subtract(position);
        // Entity#setPos is synchronized as a teleport and made the maid disappear beside her
        // owner. A normal entity move produces continuous takeoff, cruise and descent packets.
        broom.setDeltaMovement(Vec3.ZERO);
        broom.move(MoverType.SELF, movement);
        if (horizontalDistance > 0.05) {
            float yaw = (float) (Mth.atan2(horizontal.z, horizontal.x) * (180.0 / Math.PI)) - 90.0f;
            broom.setYRot(yaw);
            courier.setYRot(yaw);
        }
        return TickResult.CONTINUE;
    }

    private static boolean canOccupy(ServerLevel level, EntityMaid courier, EntityBroom broom,
                                     Vec3 current, Vec3 next) {
        Vec3 movement = next.subtract(current);
        AABB broomBox = broom.getBoundingBox().move(movement).inflate(0.05);
        AABB courierBox = courier.getBoundingBox().move(movement).inflate(0.05);
        if (!level.noCollision(broom, broomBox) || !level.noCollision(courier, courierBox)) {
            return false;
        }
        for (Entity passenger : broom.getPassengers()) {
            if (passenger == courier) continue;
            if (!level.noCollision(passenger,
                    passenger.getBoundingBox().move(movement).inflate(0.05))) return false;
        }
        return true;
    }

    private static boolean withinHorizontalRadius(BlockPos position, BlockPos target, int radius) {
        long dx = (long) position.getX() - target.getX();
        long dz = (long) position.getZ() - target.getZ();
        return dx * dx + dz * dz <= (long) radius * radius;
    }

    private static void dispatchTakeoffRoute(ServerLevel level, EntityMaid courier,
                                             BlockPos takeoff, boolean stationPad,
                                             int padAttempt) {
        BlockPos previous = MemoryUtil.getTargetPos(courier);
        boolean retryDue = CourierGroundNavigationPolicy.shouldDispatchRoute(false,
                courier.getNavigation().isDone(), level.getGameTime());
        boolean previousTargetsPad = stationPad
                && CourierGroundNavigationPolicy.reachedStationPad(previous, takeoff);
        BlockPos approach = stationPad && (!previousTargetsPad || retryDue)
                ? selectReachablePadCell(courier, takeoff, padAttempt)
                : stationPad ? previous : takeoff;
        boolean targetChanged = previous == null || !previous.equals(approach);
        MemoryUtil.setTarget(courier, approach, (float) Config.collectSpeed);
        MemoryUtil.setLookAt(courier, approach);
        if (CourierGroundNavigationPolicy.shouldDispatchRoute(targetChanged,
                courier.getNavigation().isDone(), level.getGameTime())) {
            courier.getNavigation().moveTo(approach.getX() + 0.5, approach.getY(),
                    approach.getZ() + 0.5, Config.collectSpeed);
        }
    }

    private static boolean reachedSafeStationTakeoff(ServerLevel level, BlockPos courierFeet,
                                                     BlockPos stationCentre) {
        if (CourierGroundNavigationPolicy.reachedStationPad(courierFeet, stationCentre)) {
            return true;
        }
        // Do not teleport or invent a second long-range pathfinder. If the native path ends one
        // block outside the marked pad, launch only after validating a fresh flat/open 3x3 around
        // the maid's actual feet, so vertical takeoff remains as safe as launching from the mark.
        return CourierGroundNavigationPolicy.withinStationTakeoffVicinity(
                courierFeet, stationCentre)
                && CourierWarehouseStationValidator.hasValidPad(level, courierFeet);
    }

    private static void watchTakeoffRoute(ServerLevel level, EntityMaid courier,
                                          CourierData.Data data) {
        CourierGroundRecoveryPolicy.Update update = CourierGroundRecoveryPolicy.update(
                data.groundApproachPhase(), data.groundApproachPos(),
                data.groundApproachProgressGameTime(), data.phase(),
                courier.blockPosition(), level.getGameTime());
        if (update.progressed()) data.groundTeleportFailures(0);
        if (update.changed()) {
            data.groundApproach(update.phase(), update.position(), update.progressGameTime());
        }
        if (!update.shouldTeleport()) {
            if (update.changed()) sync(courier, data);
            return;
        }
        CourierGroundNavigationPolicy.TakeoffRetry retry =
                CourierGroundNavigationPolicy.recordTakeoffFailure(
                        data.groundTeleportFailures());
        data.groundTeleportFailures(retry.failures());
        data.groundApproach(data.phase(), courier.blockPosition(), level.getGameTime());
        courier.getNavigation().stop();
        MemoryUtil.clearTarget(courier);
        sync(courier, data);
        if (retry.warn()) {
            warnOnce(courier, data,
                    "message.maid_storage_manager_extension.courier.station_takeoff_path_blocked");
        }
    }

    private static BlockPos selectReachablePadCell(EntityMaid courier, BlockPos centre,
                                                   int attempt) {
        List<BlockPos> reachable = new ArrayList<>();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos candidate = centre.offset(dx, 0, dz);
                Path path = courier.getNavigation().createPath(candidate, 0);
                if (path == null || !path.canReach()) continue;
                reachable.add(candidate);
            }
        }
        if (reachable.isEmpty()) return centre;
        reachable.sort(Comparator
                .comparingDouble((BlockPos pos) -> courier.distanceToSqr(pos.getCenter()))
                .thenComparingLong(BlockPos::asLong));
        return reachable.get(Math.floorMod(attempt, reachable.size()));
    }

    private static CourierLandingSearchPolicy.Window currentSearchWindow(
            CourierData.Data data, int configuredMaximum) {
        if (data.flightSearchMaxRadius() <= 0
                || data.flightSearchMaxRadius() > configuredMaximum) {
            CourierLandingSearchPolicy.Window first = CourierLandingSearchPolicy.first(
                    configuredMaximum);
            data.flightSearchWindow(first.minRadius(), first.maxRadius());
            data.flightRejectedLandings(0);
            return first;
        }
        return new CourierLandingSearchPolicy.Window(data.flightSearchMinRadius(),
                data.flightSearchMaxRadius(), false);
    }

    private static EntityBroom findExisting(ServerLevel level, EntityMaid courier,
                                            CourierData.Data data) {
        if (courier.getVehicle() instanceof EntityBroom mounted && isCourierBroom(mounted)) {
            setAutopilot(mounted, true);
            return mounted;
        }
        UUID known = data.courierBroom();
        if (known != null && level.getEntity(known) instanceof EntityBroom existing
                && isCourierBroom(existing)) {
            setAutopilot(existing, true);
            courier.startRiding(existing, true);
            return existing;
        }
        return null;
    }

    private static EntityBroom create(ServerLevel level, EntityMaid courier,
                                      CourierData.Data data, CourierData.Phase leg,
                                      BlockPos landing, double cruiseClearance) {
        EntityBroom broom = new EntityBroom(level);
        broom.setPos(courier.getX(), courier.getY() + 0.2, courier.getZ());
        broom.setOwnerUUID(courier.getOwnerUUID());
        broom.setNoGravity(true);
        broom.setInvulnerable(true);
        broom.noPhysics = true;
        broom.getPersistentData().putUUID(TAG_COURIER_BROOM, courier.getUUID());
        setAutopilot(broom, true);
        if (!level.addFreshEntity(broom) || !courier.startRiding(broom, true)) {
            broom.discard();
            return null;
        }
        data.flight(broom.getUUID(), leg,
                cruiseHeight(level, courier.getY(), landing.getY(), cruiseClearance));
        sync(courier, data);
        return broom;
    }

    private static int cruiseHeight(ServerLevel level, double startY, double targetY,
                                    double clearance) {
        int desired = Mth.ceil(Math.max(startY, targetY) + clearance);
        return Mth.clamp(desired, level.getMinBuildHeight() + 8, level.getMaxBuildHeight() - 8);
    }

    public static EntityBroom createWaitingTransportBroom(ServerLevel level,
                                                           EntityMaid courier,
                                                           CourierData.Data data,
                                                           UUID rider) {
        EntityBroom broom = new EntityBroom(level);
        broom.setPos(courier.getX(), courier.getY() + 0.2, courier.getZ());
        broom.setOwnerUUID(courier.getOwnerUUID());
        broom.setNoGravity(true);
        broom.setInvulnerable(true);
        broom.noPhysics = true;
        broom.getPersistentData().putUUID(TAG_COURIER_BROOM, courier.getUUID());
        if (rider != null) broom.getPersistentData().putUUID(TAG_TRANSPORT_RIDER, rider);
        setAutopilot(broom, true);
        if (!level.addFreshEntity(broom) || !courier.startRiding(broom, true)) {
            broom.discard();
            return null;
        }
        data.flight(broom.getUUID(), CourierData.Phase.TRANSPORT_WAITING_RIDER,
                cruiseHeight(level, courier.getY(), courier.getY(), PASSENGER_CRUISE_CLEARANCE));
        sync(courier, data);
        return broom;
    }

    public static EntityBroom transportBroom(ServerLevel level, EntityMaid courier,
                                              CourierData.Data data) {
        if (courier.getVehicle() instanceof EntityBroom broom
                && (isCourierBroom(broom) || isPlayerControlledTransportBroom(broom))) return broom;
        Entity entity = data.courierBroom() == null ? null : level.getEntity(data.courierBroom());
        return entity instanceof EntityBroom broom
                && (isCourierBroom(broom) || isPlayerControlledTransportBroom(broom)) ? broom : null;
    }

    public static boolean isPlayerControlledTransportBroom(Entity entity) {
        return entity instanceof EntityBroom
                && entity.getPersistentData().getBoolean(TAG_PLAYER_CONTROLLED);
    }

    /** Changes the temporary vehicle to normal player control without changing any broom item. */
    public static boolean handControlToRider(EntityBroom broom, EntityMaid courier,
                                             ServerPlayer rider, CourierData.Data data) {
        if (broom == null || courier == null || rider == null
                || !broom.getPassengers().contains(rider)) return false;
        setAutopilot(broom, false);
        broom.ejectPassengers();
        broom.getPersistentData().remove(TAG_COURIER_BROOM);
        broom.getPersistentData().putBoolean(TAG_PLAYER_CONTROLLED, true);
        broom.getPersistentData().putUUID(TAG_TRANSPORT_RIDER, rider.getUUID());
        broom.setOwnerUUID(rider.getUUID());
        broom.setNoGravity(false);
        broom.noPhysics = false;
        if (!rider.startRiding(broom, true) || !courier.startRiding(broom, true)) {
            setAutopilot(broom, true);
            broom.getPersistentData().remove(TAG_PLAYER_CONTROLLED);
            broom.getPersistentData().putUUID(TAG_COURIER_BROOM, courier.getUUID());
            broom.setNoGravity(true);
            broom.noPhysics = true;
            if (!courier.isPassenger()) courier.startRiding(broom, true);
            return false;
        }
        data.flight(broom.getUUID(), CourierData.Phase.TRANSPORT_PLAYER_CONTROLLED,
                data.flightCruiseY());
        sync(courier, data);
        return true;
    }

    /** Restores server autopilot after the rider dismounts, disconnects, or dies. */
    public static void resumeTransportAutopilot(EntityBroom broom, EntityMaid courier,
                                                CourierData.Data data) {
        if (broom == null || courier == null) return;
        setAutopilot(broom, true);
        broom.ejectPassengers();
        broom.getPersistentData().remove(TAG_PLAYER_CONTROLLED);
        broom.getPersistentData().putUUID(TAG_COURIER_BROOM, courier.getUUID());
        broom.setOwnerUUID(courier.getOwnerUUID());
        broom.setNoGravity(true);
        broom.setInvulnerable(true);
        broom.noPhysics = true;
        courier.startRiding(broom, true);
        data.flight(broom.getUUID(), CourierData.Phase.TRANSPORT_EMERGENCY_LANDING,
                Math.max(data.flightCruiseY(), Mth.ceil(broom.getY() + 8.0)));
        sync(courier, data);
    }

    private static void setAutopilot(EntityBroom broom, boolean autopilot) {
        ((BroomAutopilotAccess) broom).maidStorageExtension$setAutopilot(autopilot);
    }

    public static void discardTransportBroom(EntityMaid courier, CourierData.Data data) {
        EntityBroom broom = courier.level() instanceof ServerLevel level
                ? transportBroom(level, courier, data) : null;
        if (broom != null) {
            broom.ejectPassengers();
            broom.discard();
        }
        if (courier.isPassenger()) courier.stopRiding();
        data.clearFlight();
        sync(courier, data);
    }

    private static void land(EntityMaid courier, EntityBroom broom, CourierData.Data data,
                             BlockPos landing) {
        List<Entity> passengers = new ArrayList<>(broom.getPassengers());
        broom.ejectPassengers();
        courier.stopRiding();
        courier.setPos(landing.getX() + 0.5, landing.getY(), landing.getZ() + 0.5);
        courier.setDeltaMovement(Vec3.ZERO);
        for (Entity passenger : passengers) {
            if (passenger == courier) continue;
            passenger.setPos(landing.getX() + 0.5, landing.getY(), landing.getZ() + 0.5);
            passenger.setDeltaMovement(Vec3.ZERO);
        }
        broom.discard();
        data.finishFlight();
        sync(courier, data);
    }

    private static void discardVehicle(EntityMaid courier, CourierData.Data data) {
        Entity vehicle = courier.getVehicle();
        if (isCourierBroom(vehicle)) {
            courier.stopRiding();
            vehicle.discard();
        } else if (data.courierBroom() != null
                && courier.level() instanceof ServerLevel level) {
            Entity broom = level.getEntity(data.courierBroom());
            if (isCourierBroom(broom)) broom.discard();
        }
    }

    private static void hover(EntityMaid courier) {
        if (courier.getVehicle() instanceof EntityBroom broom && isCourierBroom(broom)) {
            broom.setDeltaMovement(Vec3.ZERO);
        } else {
            courier.getNavigation().stop();
            MemoryUtil.clearTarget(courier);
        }
    }

    private static void keepLoaded(ServerLevel level, BlockPos position, UUID courier) {
        keepLoaded(level, position, 0, courier);
    }

    private static void keepLandingAreaLoaded(ServerLevel level, BlockPos position,
                                              int radius, UUID courier) {
        int chunkRadius = Math.max(1, Mth.ceil(radius / 16.0));
        keepLoaded(level, position, chunkRadius, courier);
    }

    private static void keepLoaded(ServerLevel level, BlockPos position,
                                   int chunkRadius, UUID courier) {
        level.getChunkSource().addRegionTicket(FLIGHT_TICKET, new ChunkPos(position),
                CourierChunkTicketPolicy.ticketDistanceForEntityTickingRadius(chunkRadius), courier);
    }

    private static void warnOnce(EntityMaid courier, CourierData.Data data, String key) {
        if (data.flightWarningSent()) return;
        data.flightWarningSent(true);
        sync(courier, data);
        if (!(courier.level() instanceof ServerLevel level) || courier.getOwnerUUID() == null) return;
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(courier.getOwnerUUID());
        if (owner != null) owner.sendSystemMessage(Component.translatable(key));
    }

    private static void clearWarning(EntityMaid courier, CourierData.Data data) {
        if (!data.flightWarningSent()) return;
        data.flightWarningSent(false);
        sync(courier, data);
    }

    private static void sync(EntityMaid courier, CourierData.Data data) {
        if (!DriverData.syncFlight(courier, data)) {
            courier.setAndSyncData(CourierData.KEY, data);
        }
    }
}
