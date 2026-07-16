package io.github.maidstorageextension.data;

import java.util.Locale;

public enum PeriodicScanInterval {
    DISABLED(0),
    MINUTES_5(5),
    MINUTES_15(15),
    MINUTES_30(30),
    MINUTES_60(60);

    private final int minutes;

    PeriodicScanInterval(int minutes) {
        this.minutes = minutes;
    }

    public long ticks() {
        return minutes * 60L * 20L;
    }

    public int minutes() {
        return minutes;
    }

    public String translationKey() {
        return "gui.maid_storage_manager_extension.config.periodic_scan_interval."
                + name().toLowerCase(Locale.ROOT);
    }

    public static PeriodicScanInterval byOrdinal(int ordinal) {
        PeriodicScanInterval[] values = values();
        return values[Math.max(0, Math.min(values.length - 1, ordinal))];
    }

    public static PeriodicScanInterval fromLegacyName(String value) {
        try {
            return value == null || value.isBlank() ? DISABLED : valueOf(value);
        } catch (IllegalArgumentException ignored) {
            return DISABLED;
        }
    }
}
