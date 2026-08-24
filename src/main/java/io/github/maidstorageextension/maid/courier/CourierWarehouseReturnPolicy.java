package io.github.maidstorageextension.maid.courier;

import io.github.maidstorageextension.data.CourierData;

/** Pure decision table for post-delivery return-to-warehouse standby. */
public final class CourierWarehouseReturnPolicy {
    public record Decision(CourierData.TransportMode mode, CourierData.ReturnBlocker blocker) {
        public boolean ready() {
            return blocker == CourierData.ReturnBlocker.NONE;
        }
    }

    private CourierWarehouseReturnPolicy() {
    }

    public static Decision select(boolean hasDefaultWarehouse, boolean sameDimension,
                                  boolean stationValid, boolean withinWalkingRange,
                                  boolean hasBroom) {
        if (!hasDefaultWarehouse) return blocked(
                CourierData.ReturnBlocker.DEFAULT_WAREHOUSE_UNAVAILABLE);
        if (!sameDimension) return blocked(CourierData.ReturnBlocker.WRONG_DIMENSION);
        if (!stationValid) return blocked(CourierData.ReturnBlocker.STATION_UNAVAILABLE);
        if (withinWalkingRange) return new Decision(
                CourierData.TransportMode.WALK, CourierData.ReturnBlocker.NONE);
        return hasBroom
                ? new Decision(CourierData.TransportMode.BROOM, CourierData.ReturnBlocker.NONE)
                : blocked(CourierData.ReturnBlocker.BROOM_MISSING);
    }

    private static Decision blocked(CourierData.ReturnBlocker blocker) {
        return new Decision(CourierData.TransportMode.NONE, blocker);
    }
}
