package io.github.maidstorageextension.maid.courier;

import io.github.maidstorageextension.data.CourierData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CourierDeliveryPolicyTest {
    @Test
    void markedChestOverridesEveryOwnerHandoffStageWhileCargoRemains() {
        assertTrue(CourierDeliveryPolicy.shouldRedirectToChest(
                CourierData.Phase.TRAVEL_TO_OWNER, true, true));
        assertTrue(CourierDeliveryPolicy.shouldRedirectToChest(
                CourierData.Phase.OWNER_HANDOFF, true, true));
        assertTrue(CourierDeliveryPolicy.shouldRedirectToChest(
                CourierData.Phase.OWNER_WAITING_SPACE, true, true));
        assertTrue(CourierDeliveryPolicy.shouldRedirectToChest(
                CourierData.Phase.WAITING_OWNER_PICKUP, true, true));
        assertTrue(CourierDeliveryPolicy.shouldRedirectToChest(
                CourierData.Phase.WAITING_WITH_CARGO_AT_DELIVERY_CHEST, true, true));
    }

    @Test
    void emptyOrInvalidChestRouteDoesNotHijackOwnerReturn() {
        assertFalse(CourierDeliveryPolicy.shouldRedirectToChest(
                CourierData.Phase.TRAVEL_TO_OWNER, false, true));
        assertFalse(CourierDeliveryPolicy.shouldRedirectToChest(
                CourierData.Phase.TRAVEL_TO_OWNER, true, false));
        assertFalse(CourierDeliveryPolicy.shouldRedirectToChest(
                CourierData.Phase.RETURNING_TO_ORIGIN, true, true));
    }

    @Test
    void completedChestWaitOnlyWakesForNewCourierWork() {
        assertTrue(CourierDeliveryPolicy.shouldResumeFromChestWait(true, false));
        assertTrue(CourierDeliveryPolicy.shouldResumeFromChestWait(false, true));
        assertFalse(CourierDeliveryPolicy.shouldResumeFromChestWait(false, false));
    }

    @Test
    void onlyALoadedDestroyedDeliveryTargetIsInvalidated() {
        assertTrue(CourierDeliveryPolicy.shouldInvalidateTarget(true, false));
        assertFalse(CourierDeliveryPolicy.shouldInvalidateTarget(true, true));
        assertFalse(CourierDeliveryPolicy.shouldInvalidateTarget(false, false));
    }

    @Test
    void clearingAChestRouteFallsBackToOwnerOrIdle() {
        assertEquals(CourierData.Phase.TRAVEL_TO_OWNER,
                CourierDeliveryPolicy.afterTargetCleared(
                        CourierData.Phase.TRAVEL_TO_DELIVERY_CHEST, true));
        assertEquals(CourierData.Phase.TRAVEL_TO_OWNER,
                CourierDeliveryPolicy.afterTargetCleared(
                        CourierData.Phase.WAITING_WITH_CARGO_AT_DELIVERY_CHEST, true));
        assertEquals(CourierData.Phase.IDLE,
                CourierDeliveryPolicy.afterTargetCleared(
                        CourierData.Phase.WAITING_AT_DELIVERY_CHEST, false));
        assertEquals(CourierData.Phase.REQUEST_RUNNING,
                CourierDeliveryPolicy.afterTargetCleared(
                        CourierData.Phase.REQUEST_RUNNING, true));
    }

    @Test
    void sneakClickSetsContainersAndClearsNonContainers() {
        assertEquals(CourierDeliveryPolicy.MarkerAction.SET,
                CourierDeliveryPolicy.markerAction(true));
        assertEquals(CourierDeliveryPolicy.MarkerAction.CLEAR,
                CourierDeliveryPolicy.markerAction(false));
    }
}
