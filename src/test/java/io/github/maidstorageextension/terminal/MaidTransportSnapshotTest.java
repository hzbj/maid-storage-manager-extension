package io.github.maidstorageextension.terminal;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MaidTransportSnapshotTest {
    @Test
    void activeRouteRoundTripsWithoutLosingUnknownDestinationCoordinates() {
        UUID driver = UUID.randomUUID();
        UUID rider = UUID.randomUUID();
        ResourceLocation overworld = new ResourceLocation("minecraft", "overworld");
        MaidTransportSnapshot.Snapshot source = new MaidTransportSnapshot.Snapshot(
                driver, "driver", MaidTransportSnapshot.State.TO_DESTINATION,
                overworld, new BlockPos(8, 96, 8), new BlockPos(10, 64, 10),
                new BlockPos(120_000, 0, -90_000), rider, "");

        MaidTransportSnapshot.Snapshot decoded = MaidTransportSnapshot.fromTag(
                MaidTransportSnapshot.toTag(source));

        assertEquals(source, decoded);
        assertTrue(decoded.active());
    }
}
