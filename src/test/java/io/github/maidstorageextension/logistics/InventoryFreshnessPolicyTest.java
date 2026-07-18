package io.github.maidstorageextension.logistics;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InventoryFreshnessPolicyTest {
    @Test
    void inventoryListBecomesStaleAfterSevenGameDays() {
        assertEquals(168_000L, InventoryFreshnessPolicy.STALE_AFTER_TICKS);
        assertFalse(InventoryFreshnessPolicy.isStale(10_000L, 178_000L));
        assertTrue(InventoryFreshnessPolicy.isStale(10_000L, 178_001L));
    }

    @Test
    void timeRollbackDoesNotMakeAListArtificiallyOld() {
        assertEquals(0L, InventoryFreshnessPolicy.age(200_000L, 10_000L));
        assertFalse(InventoryFreshnessPolicy.isStale(200_000L, 10_000L));
    }
}
