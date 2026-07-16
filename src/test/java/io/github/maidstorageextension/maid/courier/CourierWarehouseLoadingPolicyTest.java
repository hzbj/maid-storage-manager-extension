package io.github.maidstorageextension.maid.courier;

import io.github.maidstorageextension.data.CourierData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CourierWarehouseLoadingPolicyTest {
    @Test
    void warehouseStaysEntityTickingThroughoutRequestAndDepositWork() {
        assertTrue(CourierWarehouseLoadingPolicy.shouldKeepWarehouseTaskLoaded(
                CourierData.Phase.REQUEST_RUNNING));
        assertTrue(CourierWarehouseLoadingPolicy.shouldKeepWarehouseTaskLoaded(
                CourierData.Phase.REQUEST_WAITING_SPACE));
        assertTrue(CourierWarehouseLoadingPolicy.shouldKeepWarehouseTaskLoaded(
                CourierData.Phase.DEPOSIT_RUNNING));
        assertTrue(CourierWarehouseLoadingPolicy.shouldKeepWarehouseTaskLoaded(
                CourierData.Phase.DEPOSIT_RETURNING));
        assertTrue(CourierWarehouseLoadingPolicy.shouldKeepWarehouseTaskLoaded(
                CourierData.Phase.DEPOSIT_WAITING_SPACE));

        assertFalse(CourierWarehouseLoadingPolicy.shouldKeepWarehouseTaskLoaded(
                CourierData.Phase.IDLE));
        assertFalse(CourierWarehouseLoadingPolicy.shouldKeepWarehouseTaskLoaded(
                CourierData.Phase.TRAVEL_TO_OWNER));
    }

    @Test
    void workAreaRadiusCoversChunksEvenWhenCenterIsAtAChunkEdge() {
        assertEquals(0, CourierWarehouseLoadingPolicy.chunkRadius(8, 8, 4.0));
        assertEquals(1, CourierWarehouseLoadingPolicy.chunkRadius(15, 15, 4.0));
        assertEquals(2, CourierWarehouseLoadingPolicy.chunkRadius(15, 15, 32.0));
        assertEquals(2, CourierWarehouseLoadingPolicy.chunkRadius(-16, -16, 32.0));
    }
}
