package io.github.maidstorageextension.maid.courier;

import io.github.maidstorageextension.data.CourierData;

/** Pure transition rules for the emergency station recall action. */
public final class CourierRecallPolicy {
    private CourierRecallPolicy() {
    }

    public static boolean canRecall(CourierData.TransportMode mode, CourierData.Phase phase,
                                    boolean airborne) {
        if (mode == null || !mode.usesBroom() || !airborne || phase == null) return false;
        return switch (phase) {
            case TRAVEL_TO_WAREHOUSE_REQUEST, TRAVEL_TO_WAREHOUSE_DEPOSIT,
                    TRAVEL_TO_OWNER, TRAVEL_TO_DELIVERY_CHEST,
                    RETURNING_TO_ORIGIN, RETURNING_AFTER_LANDING_FAILURE -> true;
            default -> false;
        };
    }

    public static CourierData.Phase afterStationRecall(CourierData.Phase phase) {
        return switch (phase) {
            case TRAVEL_TO_WAREHOUSE_REQUEST -> CourierData.Phase.TRAVEL_TO_WAREHOUSE_REQUEST;
            case TRAVEL_TO_WAREHOUSE_DEPOSIT -> CourierData.Phase.TRAVEL_TO_WAREHOUSE_DEPOSIT;
            default -> CourierData.Phase.WAITING_AT_STATION_AFTER_RECALL;
        };
    }
}
