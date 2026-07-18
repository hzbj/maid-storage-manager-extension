package io.github.maidstorageextension.maid.courier;

import io.github.maidstorageextension.data.CourierData;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CourierSortMutexTest {
    private static final EnumSet<CourierData.Phase> ACTIVE_PHASES = EnumSet.of(
            CourierData.Phase.TRAVEL_TO_WAREHOUSE_REQUEST,
            CourierData.Phase.REQUEST_HANDOFF,
            CourierData.Phase.REQUEST_RUNNING,
            CourierData.Phase.REQUEST_WAITING_SPACE,
            CourierData.Phase.TRAVEL_TO_OWNER,
            CourierData.Phase.TRAVEL_TO_DELIVERY_CHEST,
            CourierData.Phase.DELIVERY_CHEST_WAITING_SPACE,
            CourierData.Phase.WAITING_WITH_CARGO_AT_DELIVERY_CHEST,
            CourierData.Phase.OWNER_HANDOFF,
            CourierData.Phase.OWNER_WAITING_SPACE,
            CourierData.Phase.RETURNING_TO_ORIGIN,
            CourierData.Phase.RETURNING_AFTER_LANDING_FAILURE,
            CourierData.Phase.WAITING_OWNER_PICKUP,
            CourierData.Phase.WAITING_AT_STATION_AFTER_RECALL,
            CourierData.Phase.WAITING_FOR_SAFE_LANDING,
            CourierData.Phase.TRAVEL_TO_WAREHOUSE_DEPOSIT,
            CourierData.Phase.DEPOSIT_HANDOFF,
            CourierData.Phase.DEPOSIT_RUNNING,
            CourierData.Phase.DEPOSIT_RETURNING,
            CourierData.Phase.DEPOSIT_WAITING_SPACE,
            CourierData.Phase.TRANSPORT_TO_PICKUP,
            CourierData.Phase.TRANSPORT_WAITING_RIDER,
            CourierData.Phase.TRANSPORT_TO_DESTINATION,
            CourierData.Phase.TRANSPORT_PLAYER_CONTROLLED,
            CourierData.Phase.TRANSPORT_EMERGENCY_LANDING);

    @Test
    void activeCourierBlocksOnlyNewMiscBatches() {
        assertFalse(CourierSortMutex.mayStartMiscSort(true, false));
        assertFalse(CourierSortMutex.mayStartMiscSort(false, true));
        assertTrue(CourierSortMutex.mayStartMiscSort(false, false));
    }

    @Test
    void inFlightMiscCargoGetsPriorityAtCourierHandoff() {
        assertFalse(CourierSortMutex.mayCourierMutateWarehouse(true));
        assertTrue(CourierSortMutex.mayCourierMutateWarehouse(false));
    }

    @Test
    void taskSwitchedMaidCannotOfferItsOwnRecoveryCargoToCourierFlow() {
        assertFalse(CourierSortMutex.mayCourierUseOwnInventory(true));
        assertTrue(CourierSortMutex.mayCourierUseOwnInventory(false));
    }

    @Test
    void activePhaseClassificationCoversTheWholeCourierTransaction() {
        for (CourierData.Phase phase : CourierData.Phase.values()) {
            if (ACTIVE_PHASES.contains(phase)) {
                assertTrue(CourierSortMutex.isActiveTransaction(phase), phase.name());
            } else {
                assertFalse(CourierSortMutex.isActiveTransaction(phase), phase.name());
            }
        }
        assertFalse(CourierSortMutex.isActiveTransaction(null));
    }

    @Test
    void warehouseLockRequiresMatchingAuthorizedLink() {
        UUID warehouse = UUID.randomUUID();
        UUID otherWarehouse = UUID.randomUUID();
        assertTrue(CourierSortMutex.isActiveForWarehouse(warehouse, warehouse, true,
                CourierData.Phase.REQUEST_RUNNING));
        assertFalse(CourierSortMutex.isActiveForWarehouse(warehouse, otherWarehouse, true,
                CourierData.Phase.REQUEST_RUNNING));
        assertFalse(CourierSortMutex.isActiveForWarehouse(warehouse, warehouse, false,
                CourierData.Phase.REQUEST_RUNNING));
        assertFalse(CourierSortMutex.isActiveForWarehouse(warehouse, warehouse, true,
                CourierData.Phase.IDLE));
        assertFalse(CourierSortMutex.isActiveForWarehouse(warehouse, warehouse, true,
                CourierData.Phase.TRANSPORT_TO_DESTINATION));
        assertTrue(CourierSortMutex.isPassengerTransport(
                CourierData.Phase.TRANSPORT_PLAYER_CONTROLLED));
    }
}
