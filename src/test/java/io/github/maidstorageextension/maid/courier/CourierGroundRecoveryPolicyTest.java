package io.github.maidstorageextension.maid.courier;

import io.github.maidstorageextension.data.CourierData;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CourierGroundRecoveryPolicyTest {
    @Test
    void aBlockedGroundApproachUsesTheMaidTeleportAfterThreeSeconds() {
        BlockPos wallSide = new BlockPos(0, 64, 0);
        CourierGroundRecoveryPolicy.Update started = CourierGroundRecoveryPolicy.update(
                null, null, -1L, CourierData.Phase.TRAVEL_TO_WAREHOUSE_REQUEST,
                wallSide, 100L);
        CourierGroundRecoveryPolicy.Update waiting = CourierGroundRecoveryPolicy.update(
                started.phase(), started.position(), started.progressGameTime(),
                CourierData.Phase.TRAVEL_TO_WAREHOUSE_REQUEST, wallSide, 159L);
        CourierGroundRecoveryPolicy.Update stalled = CourierGroundRecoveryPolicy.update(
                waiting.phase(), waiting.position(), waiting.progressGameTime(),
                CourierData.Phase.TRAVEL_TO_WAREHOUSE_REQUEST, wallSide, 160L);

        assertFalse(started.shouldTeleport());
        assertFalse(waiting.shouldTeleport());
        assertTrue(stalled.shouldTeleport());
    }

    @Test
    void realMovementOrANewLegResetsTheStallWindow() {
        BlockPos start = new BlockPos(0, 64, 0);
        CourierGroundRecoveryPolicy.Update moved = CourierGroundRecoveryPolicy.update(
                CourierData.Phase.TRAVEL_TO_OWNER, start, 100L,
                CourierData.Phase.TRAVEL_TO_OWNER, start.offset(2, 0, 0), 159L);
        CourierGroundRecoveryPolicy.Update changedLeg = CourierGroundRecoveryPolicy.update(
                CourierData.Phase.TRAVEL_TO_OWNER, start, 100L,
                CourierData.Phase.RETURNING_TO_ORIGIN, start, 200L);

        assertFalse(moved.shouldTeleport());
        assertTrue(moved.progressed());
        assertTrue(moved.changed());
        assertFalse(changedLeg.shouldTeleport());
        assertTrue(changedLeg.changed());
    }
}
