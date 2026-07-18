package io.github.maidstorageextension.logistics;

import io.github.maidstorageextension.data.CourierData;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogisticsSnapshotTest {
    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void destinationModeAndWarehouseStationsRoundTripThroughPacketTag() {
        UUID warehouse = UUID.randomUUID();
        ResourceLocation dimension = new ResourceLocation("minecraft", "overworld");
        BlockPos position = new BlockPos(128, 70, -48);
        LogisticsSnapshot.Snapshot source = new LogisticsSnapshot.Snapshot(
                true, true, "Courier", "TRAVEL_TO_WAREHOUSE_REQUEST",
                CourierData.TransportMode.BROOM, LogisticsSnapshot.TargetKind.WAREHOUSE,
                "Warehouse", 84, true, true, 12_345L,
                List.of(new LogisticsSnapshot.Station(warehouse, "Warehouse", true, true,
                        dimension, position)), 6);

        LogisticsSnapshot.Snapshot decoded = LogisticsSnapshot.fromTag(
                LogisticsSnapshot.toTag(source));

        assertTrue(decoded.online());
        assertTrue(decoded.authorized());
        assertEquals("Courier", decoded.courierName());
        assertEquals("TRAVEL_TO_WAREHOUSE_REQUEST", decoded.phase());
        assertEquals(CourierData.TransportMode.BROOM, decoded.transportMode());
        assertEquals(LogisticsSnapshot.TargetKind.WAREHOUSE, decoded.target());
        assertEquals("Warehouse", decoded.targetName());
        assertEquals(84, decoded.distance());
        assertTrue(decoded.targetLoaded());
        assertTrue(decoded.recallAvailable());
        assertEquals(12_345L, decoded.updatedGameTime());
        assertEquals(6, decoded.stationLimit());
        assertEquals(warehouse, decoded.stations().get(0).warehouse());
        assertEquals("Warehouse", decoded.stations().get(0).name());
        assertTrue(decoded.stations().get(0).selected());
        assertTrue(decoded.stations().get(0).valid());
        assertEquals(dimension, decoded.stations().get(0).dimension());
        assertEquals(position, decoded.stations().get(0).position());
    }

    @Test
    void staleClientViewKeepsLastDetailsButMarksConnectionOffline() {
        LogisticsSnapshot.Snapshot source = new LogisticsSnapshot.Snapshot(
                true, true, "Courier", "OWNER_HANDOFF", CourierData.TransportMode.BROOM,
                LogisticsSnapshot.TargetKind.OWNER, "Owner", 2, true,
                true, 9L, List.of(), CourierData.MAX_WAREHOUSES);

        LogisticsSnapshot.Snapshot stale = source.offline();

        assertFalse(stale.online());
        assertEquals("OWNER_HANDOFF", stale.phase());
        assertEquals(LogisticsSnapshot.TargetKind.OWNER, stale.target());
        assertEquals(2, stale.distance());
        assertFalse(stale.targetLoaded());
        assertFalse(stale.recallAvailable());
    }
}
