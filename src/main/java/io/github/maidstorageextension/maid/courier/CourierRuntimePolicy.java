package io.github.maidstorageextension.maid.courier;

import io.github.maidstorageextension.data.CourierData;

/** Pure runtime ownership rules for resources that must survive a physical courier route. */
public final class CourierRuntimePolicy {
    private CourierRuntimePolicy() {
    }

    /**
     * Every physical courier transaction owns navigation while it is active.
     * Touhou Little Maid's normal owner-follow goal otherwise competes with the
     * warehouse/owner handoff target and makes short walking routes oscillate.
     */
    public static boolean shouldSuspendOwnerFollow(CourierData.TransportMode mode) {
        return mode != null && mode != CourierData.TransportMode.NONE;
    }

    /** Home mode owns a competing 40-tick restriction route and must not run during delivery. */
    public static boolean shouldDisableHomeRestriction(boolean followOverrideActive,
                                                       CourierData.TransportMode mode) {
        return followOverrideActive && shouldSuspendOwnerFollow(mode);
    }

    /** Remote handoff leaves the courier at its warehouse instead of restoring owner follow. */
    public static boolean shouldAnchorAfterRemoteTransaction(CourierData.TransportMode mode) {
        return mode != null && mode.usesEnderPocket();
    }

    public static boolean shouldKeepCourierChunkLoaded(CourierData.TransportMode mode,
                                                       CourierData.Phase phase) {
        // The journal, rather than the currently selected task or transport item, owns the
        // loading lifetime. This keeps WALK/ender-pocket warehouse work alive if combat or an
        // accessory change temporarily switches the maid away from the courier task.
        return CourierSortMutex.isActiveTransaction(phase)
                && phase != CourierData.Phase.WAITING_OWNER_PICKUP
                && phase != CourierData.Phase.WAITING_AT_STATION_AFTER_RECALL
                && phase != CourierData.Phase.DELIVERY_CHEST_WAITING_SPACE
                && phase != CourierData.Phase.WAITING_WITH_CARGO_AT_DELIVERY_CHEST;
    }

    /** A selected courier acts as an autonomous worker even when its owner leaves the area. */
    public static boolean shouldKeepCourierChunkLoaded(boolean courierTaskSelected,
                                                       CourierData.TransportMode mode,
                                                       CourierData.Phase phase) {
        return courierTaskSelected || shouldKeepCourierChunkLoaded(mode, phase);
    }
}
