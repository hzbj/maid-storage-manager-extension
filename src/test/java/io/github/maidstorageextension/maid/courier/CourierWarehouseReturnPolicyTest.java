package io.github.maidstorageextension.maid.courier;

import io.github.maidstorageextension.data.CourierData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CourierWarehouseReturnPolicyTest {
    @Test
    void nearWarehouseReturnsOnFootAndFarWarehouseUsesBroom() {
        assertEquals(CourierData.TransportMode.WALK,
                CourierWarehouseReturnPolicy.select(true, true, true, true, false).mode());
        assertEquals(CourierData.TransportMode.BROOM,
                CourierWarehouseReturnPolicy.select(true, true, true, false, true).mode());
    }

    @Test
    void missingRequirementsProduceExplicitSafeWaitBlockers() {
        assertBlocked(CourierData.ReturnBlocker.DEFAULT_WAREHOUSE_UNAVAILABLE,
                CourierWarehouseReturnPolicy.select(false, false, false, false, false));
        assertBlocked(CourierData.ReturnBlocker.WRONG_DIMENSION,
                CourierWarehouseReturnPolicy.select(true, false, false, false, true));
        assertBlocked(CourierData.ReturnBlocker.STATION_UNAVAILABLE,
                CourierWarehouseReturnPolicy.select(true, true, false, false, true));
        assertBlocked(CourierData.ReturnBlocker.BROOM_MISSING,
                CourierWarehouseReturnPolicy.select(true, true, true, false, false));
    }

    private static void assertBlocked(CourierData.ReturnBlocker blocker,
                                      CourierWarehouseReturnPolicy.Decision decision) {
        assertEquals(blocker, decision.blocker());
        assertEquals(CourierData.TransportMode.NONE, decision.mode());
        assertTrue(!decision.ready());
    }
}
