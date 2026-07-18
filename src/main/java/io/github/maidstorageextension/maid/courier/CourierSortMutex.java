package io.github.maidstorageextension.maid.courier;

import io.github.maidstorageextension.data.CourierData;

import java.util.UUID;

/**
 * Pure arbitration rules between courier hand-offs and miscellaneous sorting.
 *
 * <p>An already extracted miscellaneous payload owns the warehouse inventory until it is
 * deposited or returned. Once a courier transaction is active, the warehouse may finish such a
 * protected payload but must not start another miscellaneous batch.</p>
 */
public final class CourierSortMutex {
    private CourierSortMutex() {
    }

    public static boolean mayStartMiscSort(boolean warehouseHasActiveCourierTransaction,
                                           boolean ownCourierTransactionActive) {
        return !warehouseHasActiveCourierTransaction && !ownCourierTransactionActive;
    }

    public static boolean mayCourierMutateWarehouse(boolean miscSortCargoInFlight) {
        return !miscSortCargoInFlight;
    }

    /** A maid recovering its own old storage cargo must not expose it to courier code. */
    public static boolean mayCourierUseOwnInventory(boolean ownMiscSortCargoInFlight) {
        return !ownMiscSortCargoInFlight;
    }

    public static boolean isActiveTransaction(CourierData.Phase phase) {
        if (phase == null) return false;
        return switch (phase) {
            case TRAVEL_TO_WAREHOUSE_REQUEST, REQUEST_HANDOFF,
                    REQUEST_RUNNING, REQUEST_WAITING_SPACE,
                    TRAVEL_TO_OWNER, TRAVEL_TO_DELIVERY_CHEST,
                    DELIVERY_CHEST_WAITING_SPACE, WAITING_WITH_CARGO_AT_DELIVERY_CHEST,
                    OWNER_HANDOFF, OWNER_WAITING_SPACE,
                    RETURNING_TO_ORIGIN, RETURNING_AFTER_LANDING_FAILURE,
                    WAITING_OWNER_PICKUP, WAITING_AT_STATION_AFTER_RECALL,
                    WAITING_FOR_SAFE_LANDING,
                    TRAVEL_TO_WAREHOUSE_DEPOSIT,
                    DEPOSIT_HANDOFF, DEPOSIT_RUNNING, DEPOSIT_RETURNING,
                    DEPOSIT_WAITING_SPACE,
                    TRANSPORT_TO_PICKUP, TRANSPORT_WAITING_RIDER,
                    TRANSPORT_TO_DESTINATION, TRANSPORT_PLAYER_CONTROLLED,
                    TRANSPORT_EMERGENCY_LANDING -> true;
            default -> false;
        };
    }

    public static boolean isActiveForWarehouse(UUID warehouseId, UUID linkedWarehouseId,
                                                boolean authorized, CourierData.Phase phase) {
        return warehouseId != null
                && warehouseId.equals(linkedWarehouseId)
                && authorized
                && isActiveTransaction(phase)
                && !isPassengerTransport(phase);
    }

    public static boolean isPassengerTransport(CourierData.Phase phase) {
        return phase == CourierData.Phase.TRANSPORT_TO_PICKUP
                || phase == CourierData.Phase.TRANSPORT_WAITING_RIDER
                || phase == CourierData.Phase.TRANSPORT_TO_DESTINATION
                || phase == CourierData.Phase.TRANSPORT_PLAYER_CONTROLLED
                || phase == CourierData.Phase.TRANSPORT_EMERGENCY_LANDING;
    }
}
