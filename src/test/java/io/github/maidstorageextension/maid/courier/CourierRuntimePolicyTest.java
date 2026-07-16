package io.github.maidstorageextension.maid.courier;

import io.github.maidstorageextension.data.CourierData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CourierRuntimePolicyTest {
    @Test
    void everyActiveTransportModeTemporarilySuspendsOwnerFollow() {
        assertTrue(CourierRuntimePolicy.shouldSuspendOwnerFollow(CourierData.TransportMode.WALK));
        assertTrue(CourierRuntimePolicy.shouldSuspendOwnerFollow(CourierData.TransportMode.ENDER_POCKET));
        assertTrue(CourierRuntimePolicy.shouldSuspendOwnerFollow(CourierData.TransportMode.BROOM));
        assertTrue(CourierRuntimePolicy.shouldSuspendOwnerFollow(
                CourierData.TransportMode.BROOM_ENDER_POCKET));
        assertFalse(CourierRuntimePolicy.shouldSuspendOwnerFollow(CourierData.TransportMode.NONE));
        assertFalse(CourierRuntimePolicy.shouldSuspendOwnerFollow(null));
    }

    @Test
    void courierNavigationDisablesHomeRestrictionWhileFollowIsSuspended() {
        assertTrue(CourierRuntimePolicy.shouldDisableHomeRestriction(true,
                CourierData.TransportMode.BROOM));
        assertTrue(CourierRuntimePolicy.shouldDisableHomeRestriction(true,
                CourierData.TransportMode.WALK));
        assertFalse(CourierRuntimePolicy.shouldDisableHomeRestriction(false,
                CourierData.TransportMode.BROOM));
        assertFalse(CourierRuntimePolicy.shouldDisableHomeRestriction(true,
                CourierData.TransportMode.NONE));
    }

    @Test
    void enderPocketCompletionAnchorsCourierAtWarehouseInsteadOfRestoringOwnerFollow() {
        assertTrue(CourierRuntimePolicy.shouldAnchorAfterRemoteTransaction(
                CourierData.TransportMode.ENDER_POCKET));
        assertTrue(CourierRuntimePolicy.shouldAnchorAfterRemoteTransaction(
                CourierData.TransportMode.BROOM_ENDER_POCKET));
        assertFalse(CourierRuntimePolicy.shouldAnchorAfterRemoteTransaction(
                CourierData.TransportMode.WALK));
        assertFalse(CourierRuntimePolicy.shouldAnchorAfterRemoteTransaction(
                CourierData.TransportMode.BROOM));
        assertFalse(CourierRuntimePolicy.shouldAnchorAfterRemoteTransaction(
                CourierData.TransportMode.NONE));
        assertFalse(CourierRuntimePolicy.shouldAnchorAfterRemoteTransaction(null));
    }

    @Test
    void broomTransactionsKeepTheCourierChunkAliveWhileWaitingAndReturning() {
        assertTrue(CourierRuntimePolicy.shouldKeepCourierChunkLoaded(
                CourierData.TransportMode.BROOM, CourierData.Phase.REQUEST_RUNNING));
        assertTrue(CourierRuntimePolicy.shouldKeepCourierChunkLoaded(
                CourierData.TransportMode.BROOM, CourierData.Phase.TRAVEL_TO_OWNER));
        assertTrue(CourierRuntimePolicy.shouldKeepCourierChunkLoaded(
                CourierData.TransportMode.BROOM, CourierData.Phase.RETURNING_TO_ORIGIN));
        assertTrue(CourierRuntimePolicy.shouldKeepCourierChunkLoaded(
                CourierData.TransportMode.BROOM,
                CourierData.Phase.RETURNING_AFTER_LANDING_FAILURE));
        assertTrue(CourierRuntimePolicy.shouldKeepCourierChunkLoaded(
                CourierData.TransportMode.BROOM, CourierData.Phase.WAITING_FOR_SAFE_LANDING));
        assertTrue(CourierRuntimePolicy.shouldKeepCourierChunkLoaded(
                CourierData.TransportMode.BROOM_ENDER_POCKET,
                CourierData.Phase.TRAVEL_TO_WAREHOUSE_REQUEST));
    }

    @Test
    void everyActiveTransactionKeepsItsContextLoadedButTerminalWaitingDoesNot() {
        assertFalse(CourierRuntimePolicy.shouldKeepCourierChunkLoaded(
                CourierData.TransportMode.BROOM, CourierData.Phase.IDLE));
        assertTrue(CourierRuntimePolicy.shouldKeepCourierChunkLoaded(
                CourierData.TransportMode.ENDER_POCKET, CourierData.Phase.REQUEST_RUNNING));
        assertTrue(CourierRuntimePolicy.shouldKeepCourierChunkLoaded(
                CourierData.TransportMode.WALK, CourierData.Phase.DEPOSIT_RUNNING));
        assertFalse(CourierRuntimePolicy.shouldKeepCourierChunkLoaded(
                CourierData.TransportMode.BROOM, CourierData.Phase.WAITING_OWNER_PICKUP));
        assertFalse(CourierRuntimePolicy.shouldKeepCourierChunkLoaded(
                CourierData.TransportMode.BROOM,
                CourierData.Phase.WAITING_AT_STATION_AFTER_RECALL));
        assertFalse(CourierRuntimePolicy.shouldKeepCourierChunkLoaded(
                CourierData.TransportMode.BROOM, CourierData.Phase.WAITING_AT_DELIVERY_CHEST));
        assertFalse(CourierRuntimePolicy.shouldKeepCourierChunkLoaded(
                CourierData.TransportMode.BROOM,
                CourierData.Phase.WAITING_WITH_CARGO_AT_DELIVERY_CHEST));
    }

    @Test
    void selectedCourierTaskKeepsItsContextLoadedEvenWhileIdleAndFarFromOwner() {
        assertTrue(CourierRuntimePolicy.shouldKeepCourierChunkLoaded(true,
                CourierData.TransportMode.NONE, CourierData.Phase.IDLE));
        assertTrue(CourierRuntimePolicy.shouldKeepCourierChunkLoaded(true,
                CourierData.TransportMode.ENDER_POCKET, CourierData.Phase.REQUEST_RUNNING));
        assertFalse(CourierRuntimePolicy.shouldKeepCourierChunkLoaded(false,
                CourierData.TransportMode.NONE, CourierData.Phase.IDLE));
        assertTrue(CourierRuntimePolicy.shouldKeepCourierChunkLoaded(false,
                CourierData.TransportMode.BROOM, CourierData.Phase.TRAVEL_TO_OWNER));
    }
}
