package io.github.maidstorageextension.data;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MaintenanceStatusDataTest {
    private final MaintenanceStatusData key = new MaintenanceStatusData();

    @Test
    void serverAuthoredResultRoundTrips() {
        MaintenanceStatusData.Data source = new MaintenanceStatusData.Data(
                MaintenanceStatusData.Phase.REFRESHING,
                MaintenanceStatusData.Result.PATH_TIMEOUT,
                1_752_390_000_000L,
                42,
                17);

        MaintenanceStatusData.Data decoded = key.readSaveData(key.writeSaveData(source));
        assertEquals(MaintenanceStatusData.Phase.REFRESHING, decoded.phase());
        assertEquals(MaintenanceStatusData.Result.PATH_TIMEOUT, decoded.lastResult());
        assertEquals(1_752_390_000_000L, decoded.lastCompletedEpochMillis());
        assertEquals(42, decoded.scannedStorages());
        assertEquals(17, decoded.publishedItemTypes());
    }

    @Test
    void invalidOrMissingStatusUsesSafeDefaults() {
        CompoundTag invalid = new CompoundTag();
        invalid.putString("phase", "BROKEN");
        invalid.putString("lastResult", "BROKEN");
        MaintenanceStatusData.Data decoded = key.readSaveData(invalid);

        assertEquals(MaintenanceStatusData.Phase.IDLE, decoded.phase());
        assertEquals(MaintenanceStatusData.Result.NEVER, decoded.lastResult());
        assertEquals(0L, decoded.lastCompletedEpochMillis());
    }
}
