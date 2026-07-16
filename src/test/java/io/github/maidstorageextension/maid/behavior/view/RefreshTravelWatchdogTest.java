package io.github.maidstorageextension.maid.behavior.view;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RefreshTravelWatchdogTest {
    @Test
    void continuousProgressDoesNotTimeoutAtTheOldFixedTripDuration() {
        RefreshTravelWatchdog watchdog = new RefreshTravelWatchdog(200);
        watchdog.reset(80.0 * 80.0);

        for (int tick = 1; tick <= 300; tick++) {
            double remainingDistance = 80.0 - tick * 0.1;
            assertFalse(watchdog.tick(remainingDistance * remainingDistance),
                    "a maid that is still approaching the frame must not time out at tick " + tick);
        }
    }

    @Test
    void aStalledTripStillTimesOut() {
        RefreshTravelWatchdog watchdog = new RefreshTravelWatchdog(200);
        watchdog.reset(16.0);

        for (int tick = 1; tick < 200; tick++) {
            assertFalse(watchdog.tick(16.0));
        }
        assertTrue(watchdog.tick(16.0));
    }

    @Test
    void refreshBehaviorDoesNotUseMinecraftsFixedTotalDuration() {
        TestableRefreshBehavior behavior = new TestableRefreshBehavior();

        assertFalse(behavior.exposesTimedOut(Long.MAX_VALUE));
    }

    private static final class TestableRefreshBehavior extends RefreshInventoryListBehavior {
        boolean exposesTimedOut(long gameTime) {
            return timedOut(gameTime);
        }
    }
}
