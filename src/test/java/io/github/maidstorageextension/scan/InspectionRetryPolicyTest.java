package io.github.maidstorageextension.scan;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InspectionRetryPolicyTest {
    @Test
    void permanentlyFailedTargetAbortsSafelyInsteadOfCompletingOrWedgingThePatrol() {
        assertFalse(InspectionRetryPolicy.exhausted(2));
        assertTrue(InspectionRetryPolicy.exhausted(3));
    }
}
