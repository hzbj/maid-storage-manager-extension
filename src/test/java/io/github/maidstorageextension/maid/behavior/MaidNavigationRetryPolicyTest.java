package io.github.maidstorageextension.maid.behavior;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MaidNavigationRetryPolicyTest {
    @Test
    void activeRouteIsNeverRebuiltOnThePeriodicRetryTick() {
        assertFalse(MaidNavigationRetryPolicy.shouldRetry(20, false));
        assertFalse(MaidNavigationRetryPolicy.shouldRetry(40, false));
    }

    @Test
    void finishedRouteMayRetryAtTheBoundedInterval() {
        assertFalse(MaidNavigationRetryPolicy.shouldRetry(19, true));
        assertTrue(MaidNavigationRetryPolicy.shouldRetry(20, true));
        assertFalse(MaidNavigationRetryPolicy.shouldRetry(21, true));
    }
}
