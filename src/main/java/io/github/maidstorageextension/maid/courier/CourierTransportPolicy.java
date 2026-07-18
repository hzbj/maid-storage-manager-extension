package io.github.maidstorageextension.maid.courier;

import io.github.maidstorageextension.data.CourierData;

/** Pure mode-selection rules shared by the courier state machine and regression tests. */
public final class CourierTransportPolicy {
    private CourierTransportPolicy() {
    }

    public static CourierData.TransportMode select(boolean hasEnderPocket, boolean hasBroom,
                                                   boolean nearWarehouse,
                                                   boolean nearOwner) {
        CourierData.TransportMode required = requiredMode(nearWarehouse, nearOwner);
        return select(required, hasEnderPocket, hasBroom);
    }

    public static CourierData.TransportMode select(CourierData.TransportMode required,
                                                   boolean hasEnderPocket,
                                                   boolean hasBroom) {
        // Distance decides the minimum route. An equipped Ender Pocket is still preferred for
        // the owner-facing leg so the courier can finish remotely at the warehouse.
        CourierData.TransportMode selected = switch (required) {
            case WALK -> hasEnderPocket ? CourierData.TransportMode.ENDER_POCKET : required;
            case BROOM -> hasEnderPocket
                    ? CourierData.TransportMode.BROOM_ENDER_POCKET : required;
            default -> required;
        };
        return hasRequiredItems(selected, hasEnderPocket, hasBroom)
                ? selected
                : CourierData.TransportMode.NONE;
    }

    public static CourierData.TransportMode requiredMode(boolean nearWarehouse, boolean nearOwner) {
        if (nearWarehouse) {
            return nearOwner ? CourierData.TransportMode.WALK
                    : CourierData.TransportMode.ENDER_POCKET;
        }
        return nearOwner ? CourierData.TransportMode.BROOM
                : CourierData.TransportMode.BROOM_ENDER_POCKET;
    }

    public static boolean hasRequiredItems(CourierData.TransportMode mode,
                                           boolean hasEnderPocket,
                                           boolean hasBroom) {
        return switch (mode) {
            case BROOM -> hasBroom;
            case ENDER_POCKET -> hasEnderPocket;
            case BROOM_ENDER_POCKET -> hasBroom && hasEnderPocket;
            case NONE, WALK -> true;
        };
    }
}
