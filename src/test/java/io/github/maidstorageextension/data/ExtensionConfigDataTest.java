package io.github.maidstorageextension.data;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExtensionConfigDataTest {
    private final ExtensionConfigData key = new ExtensionConfigData();

    @Test
    void oldOrInvalidDataDefaultsToDisabled() {
        ExtensionConfigData.Data missing = key.readSaveData(new CompoundTag());
        assertEquals(PeriodicScanInterval.DISABLED, missing.periodicScanInterval());
        assertEquals(ExtensionConfigData.DEFAULT_LOCAL_SCAN_RADIUS, missing.localScanRadius());
        assertEquals(ExtensionConfigData.DEFAULT_TASK_BELL_RANGE, missing.taskBellRange());
        assertEquals(ExtensionConfigData.DEFAULT_TASK_BELL_TRAVEL_TIMEOUT_SECONDS,
                missing.taskBellTravelTimeoutSeconds());
        assertEquals(ExtensionConfigData.DEFAULT_TASK_BELL_STAY_SECONDS, missing.taskBellStaySeconds());
        assertTrue(missing.refreshFrameEffects());
        assertTrue(missing.refreshOwnerNotification());
        assertFalse(missing.miscSortMatchNbt());
        assertFalse(missing.legacyMigrationComplete());

        CompoundTag invalidTag = new CompoundTag();
        invalidTag.putString("periodicScanInterval", "NOT_A_REAL_INTERVAL");
        assertEquals(PeriodicScanInterval.DISABLED,
                key.readSaveData(invalidTag).periodicScanInterval());
    }

    @Test
    void allFiveIntervalsRoundTrip() {
        for (PeriodicScanInterval interval : PeriodicScanInterval.values()) {
            ExtensionConfigData.Data source = new ExtensionConfigData.Data(interval, true);
            ExtensionConfigData.Data decoded = key.readSaveData(key.writeSaveData(source));
            assertEquals(interval, decoded.periodicScanInterval());
            assertTrue(decoded.legacyMigrationComplete());
        }
    }

    @Test
    void intervalTicksUseServerRuntimeTicks() {
        assertEquals(0L, PeriodicScanInterval.DISABLED.ticks());
        assertEquals(5L * 60L * 20L, PeriodicScanInterval.MINUTES_5.ticks());
        assertEquals(60L * 60L * 20L, PeriodicScanInterval.MINUTES_60.ticks());
    }

    @Test
    void allEditableSettingsRoundTripAndServerBoundsAreApplied() {
        ExtensionConfigData.Data source = new ExtensionConfigData.Data(
                PeriodicScanInterval.MINUTES_30,
                24,
                96,
                45,
                25,
                false,
                false,
                true,
                true);
        ExtensionConfigData.Data decoded = key.readSaveData(key.writeSaveData(source));

        assertEquals(PeriodicScanInterval.MINUTES_30, decoded.periodicScanInterval());
        assertEquals(24, decoded.localScanRadius());
        assertEquals(96, decoded.taskBellRange());
        assertEquals(45, decoded.taskBellTravelTimeoutSeconds());
        assertEquals(25, decoded.taskBellStaySeconds());
        assertFalse(decoded.refreshFrameEffects());
        assertFalse(decoded.refreshOwnerNotification());
        assertTrue(decoded.miscSortMatchNbt());

        decoded.localScanRadius(1000);
        decoded.taskBellRange(67);
        decoded.taskBellTravelTimeoutSeconds(-100);
        decoded.taskBellStaySeconds(1000);
        assertEquals(ExtensionConfigData.MAX_LOCAL_SCAN_RADIUS, decoded.localScanRadius());
        assertEquals(64, decoded.taskBellRange());
        assertEquals(ExtensionConfigData.MIN_TASK_BELL_TRAVEL_TIMEOUT_SECONDS,
                decoded.taskBellTravelTimeoutSeconds());
        assertEquals(ExtensionConfigData.MAX_TASK_BELL_STAY_SECONDS, decoded.taskBellStaySeconds());
    }

    @Test
    void matchModeIsIndependentForDifferentMaidsConfigRecords() {
        ExtensionConfigData.Data first = ExtensionConfigData.Data.defaults();
        ExtensionConfigData.Data second = ExtensionConfigData.Data.defaults();

        first.miscSortMatchNbt(true);

        assertTrue(first.miscSortMatchNbt());
        assertFalse(second.miscSortMatchNbt());
    }
}
