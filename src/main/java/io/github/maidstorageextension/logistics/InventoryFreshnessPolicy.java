package io.github.maidstorageextension.logistics;

/** Shared seven-day freshness rule for written warehouse inventory lists. */
public final class InventoryFreshnessPolicy {
    public static final long STALE_AFTER_TICKS = 7L * 24_000L;

    private InventoryFreshnessPolicy() {
    }

    public static long age(long publishedGameTime, long currentGameTime) {
        if (publishedGameTime < 0L) return Long.MAX_VALUE;
        return Math.max(0L, currentGameTime - publishedGameTime);
    }

    public static boolean isStale(long publishedGameTime, long currentGameTime) {
        return age(publishedGameTime, currentGameTime) > STALE_AFTER_TICKS;
    }
}
