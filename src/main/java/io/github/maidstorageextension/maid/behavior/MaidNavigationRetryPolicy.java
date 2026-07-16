package io.github.maidstorageextension.maid.behavior;

/** Decides when a long-running maid route needs to be dispatched again. */
public final class MaidNavigationRetryPolicy {
    private MaidNavigationRetryPolicy() {
    }

    public static boolean shouldRetry(int travelTicks, boolean navigationDone) {
        return navigationDone && travelTicks > 0 && travelTicks % 20 == 0;
    }
}
