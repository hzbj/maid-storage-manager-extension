package io.github.maidstorageextension.maid.courier;

import io.github.maidstorageextension.data.CourierData;
import net.minecraft.core.BlockPos;

/** Pure rules for native ground-route dispatch, station arrival and teleport recovery. */
public final class CourierGroundNavigationPolicy {
    private static final int ROUTE_RETRY_TICKS = 20;
    private static final int TAKEOFF_FAILURES_BEFORE_WARNING = 3;

    private CourierGroundNavigationPolicy() {
    }

    public static boolean mayUseSafeTeleport(CourierData.TransportMode mode,
                                             boolean ownerTarget) {
        return !ownerTarget && CourierFlightPolicy.mayUseGroundRecoveryTeleport(mode);
    }

    /** Every clear cell in the validated 3x3 area is a valid mounting position. */
    public static boolean reachedStationPad(BlockPos courier, BlockPos stationCentre) {
        return courier != null && stationCentre != null
                && Math.abs(courier.getX() - stationCentre.getX()) <= 1
                && Math.abs(courier.getZ() - stationCentre.getZ()) <= 1
                && Math.abs(courier.getY() - stationCentre.getY()) <= 1;
    }

    /**
     * Native pathfinding may legally stop one block outside the requested 3x3 goal. The caller
     * must still validate the maid's live 3x3 footprint before treating this vicinity as safe.
     */
    public static boolean withinStationTakeoffVicinity(BlockPos courier,
                                                       BlockPos stationCentre) {
        return courier != null && stationCentre != null
                && Math.abs(courier.getX() - stationCentre.getX()) <= 2
                && Math.abs(courier.getZ() - stationCentre.getZ()) <= 2
                && Math.abs(courier.getY() - stationCentre.getY()) <= 1;
    }

    public static boolean shouldDispatchRoute(boolean targetChanged, boolean navigationDone,
                                              long gameTime) {
        return targetChanged || navigationDone
                && Math.floorMod(gameTime, ROUTE_RETRY_TICKS) == 0L;
    }

    /** A terminal pickup wait must not restart merely because the owner entered the wider area. */
    public static boolean shouldResumeOwnerPickup(boolean sameDimension,
                                                  double distanceSquared,
                                                  double handoffDistance) {
        return sameDimension && withinNearbyHandoff(distanceSquared, handoffDistance);
    }

    public static boolean withinNearbyHandoff(double distanceSquared,
                                              double handoffDistance) {
        return handoffDistance >= 0.0
                && distanceSquared <= handoffDistance * handoffDistance;
    }

    public static TakeoffRetry recordTakeoffFailure(int previousFailures) {
        int failures = Math.max(0, previousFailures) + 1;
        return failures >= TAKEOFF_FAILURES_BEFORE_WARNING
                ? new TakeoffRetry(0, true) : new TakeoffRetry(failures, false);
    }

    public record TakeoffRetry(int failures, boolean warn) {
    }
}
