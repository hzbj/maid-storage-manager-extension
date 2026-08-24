package io.github.maidstorageextension.migration;

import io.github.maidstorageextension.data.PeriodicScanInterval;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LegacyConfigMigrationTest {
    @Test
    void newMaidKeepsFifteenMinuteDefault() {
        assertEquals(PeriodicScanInterval.MINUTES_15,
                LegacyConfigMigration.migratedInterval(false, Optional.empty(),
                        PeriodicScanInterval.MINUTES_15));
    }

    @Test
    void oldMissingFieldStaysDisabledAndSavedValueStaysUnchanged() {
        assertEquals(PeriodicScanInterval.DISABLED,
                LegacyConfigMigration.migratedInterval(true, Optional.empty(),
                        PeriodicScanInterval.MINUTES_15));
        assertEquals(PeriodicScanInterval.MINUTES_30,
                LegacyConfigMigration.migratedInterval(true,
                        Optional.of(PeriodicScanInterval.MINUTES_30),
                        PeriodicScanInterval.MINUTES_15));
    }
}
