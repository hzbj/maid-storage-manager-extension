package io.github.maidstorageextension.scan;

/** Prevents a removed, unloaded, or permanently unreachable chest from wedging a patrol forever. */
final class InspectionRetryPolicy {
    static final int MAX_FAILED_DISPATCHES = 3;

    private InspectionRetryPolicy() {
    }

    static boolean exhausted(int failedDispatches) {
        return failedDispatches >= MAX_FAILED_DISPATCHES;
    }
}
