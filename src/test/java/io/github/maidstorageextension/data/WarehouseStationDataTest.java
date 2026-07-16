package io.github.maidstorageextension.data;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WarehouseStationDataTest {
    private final WarehouseStationData key = new WarehouseStationData();

    @Test
    void pendingMailboxRequestsRoundTripAndDeduplicateByWorldPosition() {
        WarehouseStationData.Data data = new WarehouseStationData.Data();
        WarehouseStationData.StationKey station = new WarehouseStationData.StationKey(
                new ResourceLocation("minecraft", "overworld"), new BlockPos(9, 70, -4));
        data.request(new WarehouseStationData.StationRequest(station, "First"));
        data.request(new WarehouseStationData.StationRequest(station, "Owner"));

        WarehouseStationData.Data decoded = key.readSaveData(key.writeSaveData(data));

        assertEquals(1, decoded.pending().size());
        assertEquals("Owner", decoded.pending().get(0).placerName());
        assertTrue(decoded.contains(station));
        assertTrue(decoded.remove(station));
        assertFalse(decoded.contains(station));
    }
}
