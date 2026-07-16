package io.github.maidstorageextension.maid.courier;

import io.github.maidstorageextension.data.CourierData;
import net.minecraft.resources.ResourceLocation;

/** Decides when a courier should hand movement back to the maid's original navigator. */
public final class CourierFlightPolicy {
    private static final ResourceLocation OVERWORLD = new ResourceLocation("minecraft", "overworld");

    private CourierFlightPolicy() {
    }

    public static boolean shouldUseBroom(double deltaX, double deltaZ, int flightDistance) {
        double horizontalDistanceSquared = deltaX * deltaX + deltaZ * deltaZ;
        return horizontalDistanceSquared > (double) flightDistance * flightDistance;
    }

    /** Landing search starts only after the original navigator reaches the takeoff point. */
    public static boolean readyToSearchLanding(boolean broomActive, boolean atTakeoffPoint) {
        return broomActive || atTakeoffPoint;
    }

    /** Broom routes must stay visually continuous and never use the maid's recovery teleport. */
    public static boolean mayUseGroundRecoveryTeleport(CourierData.TransportMode mode) {
        return mode == null || !mode.usesBroom();
    }

    /** The broom integration is deliberately limited to the vanilla overworld. */
    public static boolean supportsBroomDimension(ResourceLocation dimension) {
        return OVERWORLD.equals(dimension);
    }
}
