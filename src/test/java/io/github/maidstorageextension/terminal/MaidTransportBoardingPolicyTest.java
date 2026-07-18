package io.github.maidstorageextension.terminal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MaidTransportBoardingPolicyTest {
    @Test
    void nearbyDriverCanWaitWithoutStartingPickupFlight() {
        assertTrue(MaidTransportBoardingPolicy.withinRange(0.0D));
        assertTrue(MaidTransportBoardingPolicy.withinRange(64.0D));
        assertFalse(MaidTransportBoardingPolicy.withinRange(64.0001D));
        assertFalse(MaidTransportBoardingPolicy.withinRange(Double.NaN));
    }
}
