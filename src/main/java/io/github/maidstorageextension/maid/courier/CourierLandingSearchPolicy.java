package io.github.maidstorageextension.maid.courier;

/** Pure expansion/retry rules for finding a safe broom landing area. */
public final class CourierLandingSearchPolicy {
    public static final int INNER_RADIUS = 4;
    public static final int CHUNK_STEP = 16;
    public static final int FAILURES_BEFORE_RESELECT = 3;

    private CourierLandingSearchPolicy() {
    }

    public static Window first(int configuredMaximum) {
        int maximum = Math.max(INNER_RADIUS, configuredMaximum);
        return new Window(INNER_RADIUS, Math.min(CHUNK_STEP, maximum), false);
    }

    public static Window next(Window current, int configuredMaximum) {
        int maximum = Math.max(INNER_RADIUS, configuredMaximum);
        if (current == null) return first(maximum);
        if (current.exhausted() || current.maxRadius() >= maximum) {
            return new Window(current.minRadius(), current.maxRadius(), true);
        }
        int nextMaximum = Math.min(maximum, current.maxRadius() + CHUNK_STEP);
        return new Window(current.maxRadius() + 1, nextMaximum, false);
    }

    public static Failure fail(int previousFailures) {
        int failures = Math.max(0, previousFailures) + 1;
        return failures >= FAILURES_BEFORE_RESELECT
                ? new Failure(0, true) : new Failure(failures, false);
    }

    public record Window(int minRadius, int maxRadius, boolean exhausted) {
    }

    public record Failure(int failures, boolean reselect) {
    }
}
