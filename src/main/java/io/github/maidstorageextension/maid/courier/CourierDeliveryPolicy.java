package io.github.maidstorageextension.maid.courier;

import io.github.maidstorageextension.data.CourierData;

/** Pure priority rules for a player-marked physical delivery chest. */
public final class CourierDeliveryPolicy {
    public enum MarkerAction {
        SET,
        CLEAR
    }

    private CourierDeliveryPolicy() {
    }

    public static boolean shouldRedirectToChest(CourierData.Phase phase,
                                                boolean hasDeliveryTarget,
                                                boolean hasCargo) {
        if (!hasDeliveryTarget || !hasCargo || phase == null) return false;
        return switch (phase) {
            case TRAVEL_TO_OWNER, OWNER_HANDOFF, OWNER_WAITING_SPACE,
                    WAITING_OWNER_PICKUP, WAITING_WITH_CARGO_AT_DELIVERY_CHEST -> true;
            default -> false;
        };
    }

    public static boolean shouldResumeFromChestWait(boolean hasRequestList,
                                                    boolean depositRequested) {
        return hasRequestList || depositRequested;
    }

    /** Ender Pocket completes owner delivery remotely, but cannot silently consume a chest order. */
    public static boolean shouldCompleteRequestRemotely(boolean usesEnderPocket,
                                                        boolean forceOwnerDelivery,
                                                        boolean hasFixedDeliveryTarget) {
        return usesEnderPocket && (forceOwnerDelivery || !hasFixedDeliveryTarget);
    }

    /** An unloaded target is unknown, not destroyed. */
    public static boolean shouldInvalidateTarget(boolean targetChunkLoaded,
                                                 boolean hasItemHandler) {
        return targetChunkLoaded && !hasItemHandler;
    }

    public static CourierData.Phase afterTargetCleared(CourierData.Phase phase,
                                                       boolean hasCargo) {
        if (phase == null) return CourierData.Phase.IDLE;
        return switch (phase) {
            case TRAVEL_TO_DELIVERY_CHEST, DELIVERY_CHEST_WAITING_SPACE,
                    WAITING_WITH_CARGO_AT_DELIVERY_CHEST, WAITING_AT_DELIVERY_CHEST ->
                    hasCargo ? CourierData.Phase.TRAVEL_TO_OWNER : CourierData.Phase.IDLE;
            default -> phase;
        };
    }

    public static MarkerAction markerAction(boolean itemContainer) {
        return itemContainer ? MarkerAction.SET : MarkerAction.CLEAR;
    }
}
