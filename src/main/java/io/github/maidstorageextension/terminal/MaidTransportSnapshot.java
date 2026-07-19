package io.github.maidstorageextension.terminal;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/** Client-safe state for the passenger transport page of one logged-in terminal. */
public final class MaidTransportSnapshot {
    public enum State {
        NO_DRIVER,
        OFFLINE,
        WRONG_TASK,
        NO_BROOM,
        BUSY,
        READY,
        FOLLOWING_OWNER,
        RETURNING_TO_WAREHOUSE,
        WAREHOUSE_STANDBY,
        TO_PICKUP,
        WAITING_RIDER,
        TO_DESTINATION,
        PLAYER_CONTROLLED,
        EMERGENCY_LANDING
    }

    public record Snapshot(UUID driver, String driverName, State state,
                           ResourceLocation dimension, BlockPos driverPosition,
                           BlockPos pickup, BlockPos destination, UUID rider,
                           String reason) {
        public Snapshot {
            driverName = driverName == null ? "" : driverName;
            state = state == null ? State.NO_DRIVER : state;
            driverPosition = driverPosition == null ? null : driverPosition.immutable();
            pickup = pickup == null ? null : pickup.immutable();
            destination = destination == null ? null : destination.immutable();
            reason = reason == null ? "" : reason;
        }

        public static Snapshot empty() {
            return new Snapshot(null, "", State.NO_DRIVER, null,
                    null, null, null, null, "");
        }

        public boolean active() {
            return switch (state) {
                case TO_PICKUP, WAITING_RIDER, TO_DESTINATION,
                        RETURNING_TO_WAREHOUSE, PLAYER_CONTROLLED, EMERGENCY_LANDING -> true;
                default -> false;
            };
        }
    }

    private MaidTransportSnapshot() {
    }

    public static CompoundTag toTag(Snapshot snapshot) {
        CompoundTag tag = new CompoundTag();
        if (snapshot.driver() != null) tag.putUUID("driver", snapshot.driver());
        tag.putString("driverName", snapshot.driverName());
        tag.putString("state", snapshot.state().name());
        if (snapshot.dimension() != null) tag.putString("dimension", snapshot.dimension().toString());
        if (snapshot.driverPosition() != null) tag.putLong("driverPosition", snapshot.driverPosition().asLong());
        if (snapshot.pickup() != null) tag.putLong("pickup", snapshot.pickup().asLong());
        if (snapshot.destination() != null) tag.putLong("destination", snapshot.destination().asLong());
        if (snapshot.rider() != null) tag.putUUID("rider", snapshot.rider());
        tag.putString("reason", snapshot.reason());
        return tag;
    }

    public static Snapshot fromTag(CompoundTag tag) {
        if (tag == null) return Snapshot.empty();
        State state;
        try {
            state = State.valueOf(tag.getString("state"));
        } catch (IllegalArgumentException ignored) {
            state = State.NO_DRIVER;
        }
        ResourceLocation dimension = ResourceLocation.tryParse(tag.getString("dimension"));
        return new Snapshot(tag.hasUUID("driver") ? tag.getUUID("driver") : null,
                tag.getString("driverName"), state, dimension,
                tag.contains("driverPosition", Tag.TAG_LONG)
                        ? BlockPos.of(tag.getLong("driverPosition")) : null,
                tag.contains("pickup", Tag.TAG_LONG) ? BlockPos.of(tag.getLong("pickup")) : null,
                tag.contains("destination", Tag.TAG_LONG)
                        ? BlockPos.of(tag.getLong("destination")) : null,
                tag.hasUUID("rider") ? tag.getUUID("rider") : null,
                tag.getString("reason"));
    }
}
