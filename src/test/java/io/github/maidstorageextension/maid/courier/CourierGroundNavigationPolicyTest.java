package io.github.maidstorageextension.maid.courier;

import io.github.maidstorageextension.data.CourierData;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CourierGroundNavigationPolicyTest {
    @Test
    void returningToOwnerNeverUsesTheRepeatedSafeTeleportFallback() {
        assertFalse(CourierGroundNavigationPolicy.mayUseSafeTeleport(
                CourierData.TransportMode.WALK, true));
        assertTrue(CourierGroundNavigationPolicy.mayUseSafeTeleport(
                CourierData.TransportMode.WALK, false));
        assertFalse(CourierGroundNavigationPolicy.mayUseSafeTeleport(
                CourierData.TransportMode.BROOM, false));
    }

    @Test
    void anyStandingCellInsideTheValidatedThreeByThreePadCanLaunch() {
        BlockPos centre = new BlockPos(10, 65, 10);

        assertTrue(CourierGroundNavigationPolicy.reachedStationPad(centre, centre));
        assertTrue(CourierGroundNavigationPolicy.reachedStationPad(
                centre.offset(1, 0, -1), centre));
        assertFalse(CourierGroundNavigationPolicy.reachedStationPad(
                centre.offset(2, 0, 0), centre));
        assertFalse(CourierGroundNavigationPolicy.reachedStationPad(
                centre.above(2), centre));
    }

    @Test
    void nativePathEndpointOneBlockOutsideThePadRemainsAStationTakeoffCandidate() {
        BlockPos centre = new BlockPos(10, 65, 10);

        assertTrue(CourierGroundNavigationPolicy.withinStationTakeoffVicinity(
                centre.offset(2, 0, 0), centre));
        assertTrue(CourierGroundNavigationPolicy.withinStationTakeoffVicinity(
                centre.offset(-2, 1, 2), centre));
        assertFalse(CourierGroundNavigationPolicy.withinStationTakeoffVicinity(
                centre.offset(3, 0, 0), centre));
        assertFalse(CourierGroundNavigationPolicy.withinStationTakeoffVicinity(
                centre.above(2), centre));
    }

    @Test
    void nativeRouteIsDispatchedOnceThenOnlyRetriedAfterNavigationEnds() {
        assertTrue(CourierGroundNavigationPolicy.shouldDispatchRoute(true, false, 1L));
        assertFalse(CourierGroundNavigationPolicy.shouldDispatchRoute(false, false, 20L));
        assertTrue(CourierGroundNavigationPolicy.shouldDispatchRoute(false, true, 20L));
        assertFalse(CourierGroundNavigationPolicy.shouldDispatchRoute(false, true, 21L));
    }

    @Test
    void threeStalledNativeRoutesProduceAVisibleTakeoffWarning() {
        CourierGroundNavigationPolicy.TakeoffRetry first =
                CourierGroundNavigationPolicy.recordTakeoffFailure(0);
        CourierGroundNavigationPolicy.TakeoffRetry second =
                CourierGroundNavigationPolicy.recordTakeoffFailure(first.failures());
        CourierGroundNavigationPolicy.TakeoffRetry third =
                CourierGroundNavigationPolicy.recordTakeoffFailure(second.failures());

        assertFalse(first.warn());
        assertFalse(second.warn());
        assertTrue(third.warn());
        assertTrue(third.failures() == 0);
    }

    @Test
    void ownerPickupWaitOnlyResumesAtRealHandoffDistance() {
        double handoffDistance = 2.75;

        assertTrue(CourierGroundNavigationPolicy.shouldResumeOwnerPickup(
                true, 2.0 * 2.0, handoffDistance));
        assertFalse(CourierGroundNavigationPolicy.shouldResumeOwnerPickup(
                true, 4.0 * 4.0, handoffDistance));
        assertFalse(CourierGroundNavigationPolicy.shouldResumeOwnerPickup(
                false, 1.0, handoffDistance));
    }
}
