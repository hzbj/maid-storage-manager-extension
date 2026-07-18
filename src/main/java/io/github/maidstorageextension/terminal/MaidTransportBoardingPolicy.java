package io.github.maidstorageextension.terminal;

/** Shared distance rule for waiting at a pickup and accepting the intended rider. */
public final class MaidTransportBoardingPolicy {
    private static final double MAX_DISTANCE_SQUARED = 64.0D;

    private MaidTransportBoardingPolicy() {
    }

    public static boolean withinRange(double distanceSquared) {
        return distanceSquared >= 0.0D && distanceSquared <= MAX_DISTANCE_SQUARED;
    }
}
