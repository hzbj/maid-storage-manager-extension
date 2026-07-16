package io.github.maidstorageextension.maid.courier;

/** Pure per-tick broom motion plan. Long legs always clear the takeoff column first. */
public final class CourierFlightMotionPolicy {
    static final double HORIZONTAL_SPEED = 0.72;
    static final double ASCEND_SPEED = 0.45;
    static final double DESCEND_SPEED = 0.36;
    private static final double APPROACH_HORIZONTAL_SPEED = 0.32;
    private static final double CRUISE_VERTICAL_ADJUSTMENT = 0.18;
    private static final double LONG_LEG_DISTANCE = 6.0;

    private CourierFlightMotionPolicy() {
    }

    public static Motion plan(double horizontalDistance, double currentY,
                              double cruiseY, double destinationY) {
        if (horizontalDistance > LONG_LEG_DISTANCE && currentY < cruiseY) {
            return new Motion(0.0, Math.min(ASCEND_SPEED, cruiseY - currentY));
        }
        if (horizontalDistance > LONG_LEG_DISTANCE) {
            return new Motion(HORIZONTAL_SPEED,
                    clamp(cruiseY - currentY,
                            -CRUISE_VERTICAL_ADJUSTMENT, CRUISE_VERTICAL_ADJUSTMENT));
        }
        return new Motion(Math.min(APPROACH_HORIZONTAL_SPEED, horizontalDistance),
                clamp(destinationY - currentY, -DESCEND_SPEED, DESCEND_SPEED));
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    public record Motion(double horizontalSpeed, double verticalSpeed) {
    }
}
