package io.github.maidstorageextension.maid.courier;

import io.github.maidstorageextension.data.CourierData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CourierTransportPolicyTest {
    @Test
    void nearbyOwnerAndWarehouseNeedNoTransportItems() {
        assertEquals(CourierData.TransportMode.WALK,
                CourierTransportPolicy.select(false, false, true, true));
    }

    @Test
    void eachDistanceQuadrantOnlyRequiresItsOwnTransportItems() {
        assertEquals(CourierData.TransportMode.BROOM,
                CourierTransportPolicy.select(false, true, false, true));
        assertEquals(CourierData.TransportMode.ENDER_POCKET,
                CourierTransportPolicy.select(true, false, true, false));
        assertEquals(CourierData.TransportMode.BROOM_ENDER_POCKET,
                CourierTransportPolicy.select(true, true, false, false));
    }

    @Test
    void routeIsUnavailableOnlyWhenItsSelectedModeIsMissingAnItem() {
        assertEquals(CourierData.TransportMode.NONE,
                CourierTransportPolicy.select(false, false, false, true));
        assertEquals(CourierData.TransportMode.NONE,
                CourierTransportPolicy.select(true, false, false, true));
        assertEquals(CourierData.TransportMode.NONE,
                CourierTransportPolicy.select(false, false, true, false));
        assertEquals(CourierData.TransportMode.NONE,
                CourierTransportPolicy.select(false, true, true, false));
        assertEquals(CourierData.TransportMode.NONE,
                CourierTransportPolicy.select(true, false, false, false));
        assertEquals(CourierData.TransportMode.NONE,
                CourierTransportPolicy.select(false, true, false, false));
    }

    @Test
    void requiredModeAndItemChecksShareOneSourceOfTruth() {
        assertEquals(CourierData.TransportMode.WALK,
                CourierTransportPolicy.requiredMode(true, true));
        assertEquals(CourierData.TransportMode.BROOM,
                CourierTransportPolicy.requiredMode(false, true));
        assertEquals(CourierData.TransportMode.ENDER_POCKET,
                CourierTransportPolicy.requiredMode(true, false));
        assertEquals(CourierData.TransportMode.BROOM_ENDER_POCKET,
                CourierTransportPolicy.requiredMode(false, false));

        assertTrue(CourierTransportPolicy.hasRequiredItems(
                CourierData.TransportMode.WALK, false, false));
        assertTrue(CourierTransportPolicy.hasRequiredItems(
                CourierData.TransportMode.BROOM, false, true));
        assertTrue(CourierTransportPolicy.hasRequiredItems(
                CourierData.TransportMode.ENDER_POCKET, true, false));
        assertTrue(CourierTransportPolicy.hasRequiredItems(
                CourierData.TransportMode.BROOM_ENDER_POCKET, true, true));
        assertFalse(CourierTransportPolicy.hasRequiredItems(
                CourierData.TransportMode.BROOM_ENDER_POCKET, true, false));
    }

    @Test
    void ownerInAnotherDimensionUsesEnderPocketWhenCourierIsAtTheWarehouse() {
        // A cross-dimension owner is deliberately classified as not near the courier.
        assertEquals(CourierData.TransportMode.ENDER_POCKET,
                CourierTransportPolicy.requiredMode(true, false));
        assertEquals(CourierData.TransportMode.ENDER_POCKET,
                CourierTransportPolicy.select(true, false, true, false));
    }
}
