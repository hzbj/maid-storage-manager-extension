package io.github.maidstorageextension.maid.courier;

import io.github.maidstorageextension.data.CourierData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CourierRecallPolicyTest {
    @Test
    void onlyActiveBroomTravelCanBeRecalled() {
        assertTrue(CourierRecallPolicy.canRecall(
                CourierData.TransportMode.BROOM, CourierData.Phase.TRAVEL_TO_OWNER, true));
        assertTrue(CourierRecallPolicy.canRecall(
                CourierData.TransportMode.BROOM, CourierData.Phase.TRAVEL_TO_WAREHOUSE_REQUEST, true));
        assertTrue(CourierRecallPolicy.canRecall(
                CourierData.TransportMode.BROOM_ENDER_POCKET,
                CourierData.Phase.TRAVEL_TO_WAREHOUSE_REQUEST, true));
        assertFalse(CourierRecallPolicy.canRecall(
                CourierData.TransportMode.ENDER_POCKET, CourierData.Phase.TRAVEL_TO_OWNER, true));
        assertFalse(CourierRecallPolicy.canRecall(
                CourierData.TransportMode.BROOM, CourierData.Phase.REQUEST_RUNNING, true));
        assertFalse(CourierRecallPolicy.canRecall(
                CourierData.TransportMode.BROOM, CourierData.Phase.TRAVEL_TO_OWNER, false));
    }

    @Test
    void warehouseBoundLegContinuesButOutboundCargoWaitsAtTheStation() {
        assertEquals(CourierData.Phase.TRAVEL_TO_WAREHOUSE_REQUEST,
                CourierRecallPolicy.afterStationRecall(
                        CourierData.Phase.TRAVEL_TO_WAREHOUSE_REQUEST));
        assertEquals(CourierData.Phase.TRAVEL_TO_WAREHOUSE_DEPOSIT,
                CourierRecallPolicy.afterStationRecall(
                        CourierData.Phase.TRAVEL_TO_WAREHOUSE_DEPOSIT));
        assertEquals(CourierData.Phase.WAITING_AT_STATION_AFTER_RECALL,
                CourierRecallPolicy.afterStationRecall(CourierData.Phase.TRAVEL_TO_OWNER));
    }
}
