package io.github.maidstorageextension.data;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WarehouseNetworkDataTest {
    @Test
    void publishedInventoryListReferenceSurvivesWarehouseUnload() {
        WarehouseNetworkData key = new WarehouseNetworkData();
        WarehouseNetworkData.Data source = new WarehouseNetworkData.Data();
        UUID inventoryList = UUID.randomUUID();

        assertTrue(source.publish(inventoryList, 123_456L));
        WarehouseNetworkData.Data decoded = key.readSaveData(key.writeSaveData(source));

        assertEquals(inventoryList, decoded.inventoryList());
        assertEquals(123_456L, decoded.publishedGameTime());
    }
}
