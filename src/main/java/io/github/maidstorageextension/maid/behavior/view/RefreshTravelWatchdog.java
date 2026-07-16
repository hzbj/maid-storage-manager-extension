package io.github.maidstorageextension.maid.behavior.view;

/** Tracks the refresh trip timeout independently from the Minecraft behavior wrapper. */
final class RefreshTravelWatchdog {
    private static final double MIN_PROGRESS_BLOCKS = 0.25D;

    private final int timeoutTicks;
    private double bestDistance;
    private int stalledTicks;

    RefreshTravelWatchdog(int timeoutTicks) {
        this.timeoutTicks = timeoutTicks;
    }

    void reset(double distanceSquared) {
        bestDistance = distance(distanceSquared);
        stalledTicks = 0;
    }

    boolean tick(double distanceSquared) {
        double currentDistance = distance(distanceSquared);
        if (currentDistance <= bestDistance - MIN_PROGRESS_BLOCKS) {
            bestDistance = currentDistance;
            stalledTicks = 0;
            return false;
        }
        stalledTicks++;
        return stalledTicks >= timeoutTicks;
    }

    private static double distance(double distanceSquared) {
        return Math.sqrt(Math.max(0.0D, distanceSquared));
    }
}
