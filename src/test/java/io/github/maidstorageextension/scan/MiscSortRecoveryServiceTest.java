package io.github.maidstorageextension.scan;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MiscSortRecoveryServiceTest {
    @Test
    void extractedPayloadKeepsARecoveryRunnerAfterTheMaidChangesTask() {
        assertTrue(MiscSortRecoveryService.requiresExternalRecovery(true, false));
        assertFalse(MiscSortRecoveryService.requiresExternalRecovery(false, false));
        assertFalse(MiscSortRecoveryService.requiresExternalRecovery(true, true));
        assertTrue(MiscSortRecoveryService.shouldSuspendBrain(true, false));
        assertFalse(MiscSortRecoveryService.shouldSuspendBrain(false, false));
        assertFalse(MiscSortRecoveryService.shouldSuspendBrain(true, true));
    }
}
