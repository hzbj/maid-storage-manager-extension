package io.github.maidstorageextension.maid.courier;

import io.github.maidstorageextension.data.CourierData;

/** Pure mode-selection rules shared by the courier state machine and regression tests. */
public final class CourierTransportPolicy {
    private CourierTransportPolicy() {
    }

    public static CourierData.TransportMode select(boolean hasEnderPocket, boolean hasBroom,
                                                   boolean nearWarehouse,
                                                   boolean nearOwner) {
        if (!hasEnderPocket || !hasBroom) {
            return CourierData.TransportMode.NONE;
        }
        if (nearWarehouse && nearOwner) {
            return CourierData.TransportMode.WALK;
        }
        if (!nearWarehouse && nearOwner) {
            return CourierData.TransportMode.BROOM;
        }
        if (nearWarehouse && !nearOwner) {
            return CourierData.TransportMode.ENDER_POCKET;
        }
        if (!nearWarehouse && !nearOwner) {
            return CourierData.TransportMode.BROOM_ENDER_POCKET;
        }
        return CourierData.TransportMode.NONE;
    }
}
