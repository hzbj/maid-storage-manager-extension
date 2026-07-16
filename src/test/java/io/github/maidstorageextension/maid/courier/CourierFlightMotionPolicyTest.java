package io.github.maidstorageextension.maid.courier;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CourierFlightMotionPolicyTest {
    @Test
    void longDistanceTakeoffClimbsVerticallyBeforeAnyHorizontalMovement() {
        CourierFlightMotionPolicy.Motion motion = CourierFlightMotionPolicy.plan(
                100.0, 64.0, 80.0, 70.0);

        assertEquals(0.0, motion.horizontalSpeed(), 1.0e-9);
        assertEquals(0.45, motion.verticalSpeed(), 1.0e-9);
    }

    @Test
    void horizontalCruiseStartsOnlyAfterCruiseHeightIsReached() {
        CourierFlightMotionPolicy.Motion almostThere = CourierFlightMotionPolicy.plan(
                100.0, 79.9, 80.0, 70.0);
        CourierFlightMotionPolicy.Motion atCruise = CourierFlightMotionPolicy.plan(
                100.0, 80.0, 80.0, 70.0);

        assertEquals(0.0, almostThere.horizontalSpeed(), 1.0e-9);
        assertEquals(0.1, almostThere.verticalSpeed(), 1.0e-9);
        assertEquals(0.72, atCruise.horizontalSpeed(), 1.0e-9);
        assertEquals(0.0, atCruise.verticalSpeed(), 1.0e-9);
    }

    @Test
    void closeRangeLandingKeepsTheExistingApproachAndDescentSpeeds() {
        CourierFlightMotionPolicy.Motion motion = CourierFlightMotionPolicy.plan(
                5.0, 80.0, 96.0, 70.0);

        assertEquals(0.32, motion.horizontalSpeed(), 1.0e-9);
        assertEquals(-0.36, motion.verticalSpeed(), 1.0e-9);
    }
}
