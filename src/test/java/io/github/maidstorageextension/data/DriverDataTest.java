package io.github.maidstorageextension.data;

import io.github.maidstorageextension.terminal.MailboxKey;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DriverDataTest {
    private static final ResourceLocation OVERWORLD =
            new ResourceLocation("minecraft", "overworld");

    @Test
    void passengerJournalRoundTripsWithoutCourierCargoState() {
        DriverData key = new DriverData();
        DriverData.Data source = new DriverData.Data();
        UUID rider = UUID.randomUUID();
        BlockPos requested = new BlockPos(40, 70, 40);
        BlockPos pickup = new BlockPos(42, 71, 39);
        BlockPos destination = new BlockPos(300, 82, -120);
        BlockPos takeoff = new BlockPos(12, 68, 5);

        source.begin(rider, OVERWORLD, requested, pickup, destination, takeoff, 48);
        DriverData.Data decoded = key.readSaveData(key.writeSaveData(source));

        assertEquals(DriverData.Phase.TO_PICKUP, decoded.phase());
        assertEquals(rider, decoded.rider());
        assertEquals(requested, decoded.requestedPickup());
        assertEquals(pickup, decoded.pickup());
        assertEquals(destination, decoded.destinationAnchor());
        assertEquals(takeoff, decoded.takeoff());
        assertEquals(48, decoded.flight().broomFlightDistance());
        assertEquals(0, decoded.flight().requestManifest().size());
        assertNull(decoded.flight().warehouse());
    }

    @Test
    void warehouseReturnRetainsDriverStandbyState() {
        DriverData key = new DriverData();
        DriverData.Data source = new DriverData.Data();
        MailboxKey mailbox = new MailboxKey(OVERWORLD, new BlockPos(8, 64, 8));
        source.beginReturn(mailbox, new BlockPos(10, 65, 10));
        source.standby();

        DriverData.Data decoded = key.readSaveData(key.writeSaveData(source));

        assertEquals(DriverData.Phase.WAREHOUSE_STANDBY, decoded.phase());
        assertNull(decoded.rider());
    }
}
