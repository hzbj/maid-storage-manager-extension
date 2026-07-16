package io.github.maidstorageextension.maid.courier;

import io.github.maidstorageextension.data.CourierData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CourierTransportPolicyTest {
    @Test
    void nearbyOwnerAndWarehouseUseOriginalGroundPathing() {
        assertEquals(CourierData.TransportMode.WALK,
                CourierTransportPolicy.select(true, true, true, true));
    }

    @Test
    void distanceQuadrantsSelectBroomEnderOrCombinedRoute() {
        assertEquals(CourierData.TransportMode.BROOM,
                CourierTransportPolicy.select(true, true, false, true));
        assertEquals(CourierData.TransportMode.ENDER_POCKET,
                CourierTransportPolicy.select(true, true, true, false));
        assertEquals(CourierData.TransportMode.BROOM_ENDER_POCKET,
                CourierTransportPolicy.select(true, true, false, false));
    }

    @Test
    void missingRequiredTransportLeavesTheRouteUnavailable() {
        assertEquals(CourierData.TransportMode.NONE,
                CourierTransportPolicy.select(true, false, true, true));
        assertEquals(CourierData.TransportMode.NONE,
                CourierTransportPolicy.select(false, true, true, true));
        assertEquals(CourierData.TransportMode.NONE,
                CourierTransportPolicy.select(true, false, true, false));
        assertEquals(CourierData.TransportMode.NONE,
                CourierTransportPolicy.select(false, true, false, true));
    }
}
