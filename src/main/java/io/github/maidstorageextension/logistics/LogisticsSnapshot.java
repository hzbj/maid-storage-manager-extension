package io.github.maidstorageextension.logistics;

import io.github.maidstorageextension.data.CourierData;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Small server-authored payload sent to tracker viewers without mutating the held item. */
public final class LogisticsSnapshot {
    /** Removed from live updates in 1.1.3; retained only so old item data can be cleaned. */
    public static final String TAG_SNAPSHOT = "LogisticsSnapshot";

    public enum TargetKind {
        NONE,
        WAREHOUSE,
        OWNER,
        DELIVERY_CHEST,
        ORIGIN,
        WAREHOUSE_AREA
    }

    public record Station(UUID warehouse, String name, boolean selected, boolean valid,
                          ResourceLocation dimension, BlockPos position) {
        public Station {
            name = name == null ? "" : name;
            position = position == null ? null : position.immutable();
        }

        public boolean hasMapPosition() {
            return dimension != null && position != null;
        }
    }

    public record Snapshot(boolean online, boolean authorized, String courierName, String phase,
                           CourierData.TransportMode transportMode, TargetKind target,
                           String targetName, int distance, boolean targetLoaded,
                           boolean recallAvailable, long updatedGameTime,
                           List<Station> stations, int stationLimit) {
        public Snapshot {
            courierName = courierName == null ? "" : courierName;
            phase = phase == null || phase.isBlank() ? "UNBOUND" : phase;
            transportMode = transportMode == null ? CourierData.TransportMode.NONE : transportMode;
            target = target == null ? TargetKind.NONE : target;
            targetName = targetName == null ? "" : targetName;
            distance = Math.max(-1, distance);
            stations = stations == null ? List.of() : List.copyOf(stations);
            stationLimit = Math.max(stations.size(), stationLimit);
        }

        public static Snapshot empty() {
            return new Snapshot(false, true, "", "UNBOUND", CourierData.TransportMode.NONE,
                    TargetKind.NONE, "", -1, false, false, 0L, List.of(), 0);
        }

        public Snapshot offline() {
            return new Snapshot(false, authorized, courierName, phase, transportMode, target,
                    targetName, distance, false, false, updatedGameTime, stations, stationLimit);
        }
    }

    private LogisticsSnapshot() {
    }

    public static CompoundTag toTag(Snapshot snapshot) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("online", snapshot.online());
        tag.putBoolean("authorized", snapshot.authorized());
        tag.putString("courierName", snapshot.courierName());
        tag.putString("phase", snapshot.phase());
        tag.putString("transportMode", snapshot.transportMode().name());
        tag.putString("target", snapshot.target().name());
        tag.putString("targetName", snapshot.targetName());
        tag.putInt("distance", snapshot.distance());
        tag.putBoolean("targetLoaded", snapshot.targetLoaded());
        tag.putBoolean("recallAvailable", snapshot.recallAvailable());
        tag.putLong("updated", snapshot.updatedGameTime());
        ListTag stations = new ListTag();
        for (Station value : snapshot.stations()) {
            if (value.warehouse() == null) continue;
            CompoundTag entry = new CompoundTag();
            entry.putUUID("warehouse", value.warehouse());
            entry.putString("name", value.name());
            entry.putBoolean("selected", value.selected());
            entry.putBoolean("valid", value.valid());
            if (value.hasMapPosition()) {
                entry.putString("dimension", value.dimension().toString());
                entry.putLong("position", value.position().asLong());
            }
            stations.add(entry);
        }
        tag.put("stations", stations);
        tag.putInt("stationLimit", snapshot.stationLimit());
        return tag;
    }

    public static Snapshot fromTag(CompoundTag tag) {
        if (tag == null || tag.isEmpty()) return Snapshot.empty();
        List<Station> stations = new ArrayList<>();
        ListTag list = tag.getList("stations", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            if (!entry.hasUUID("warehouse")) continue;
            ResourceLocation dimension = ResourceLocation.tryParse(entry.getString("dimension"));
            BlockPos position = entry.contains("position", Tag.TAG_LONG)
                    ? BlockPos.of(entry.getLong("position")) : null;
            stations.add(new Station(entry.getUUID("warehouse"), entry.getString("name"),
                    entry.getBoolean("selected"), entry.getBoolean("valid"),
                    dimension, position));
        }
        return new Snapshot(
                tag.getBoolean("online"),
                !tag.contains("authorized") || tag.getBoolean("authorized"),
                tag.getString("courierName"),
                tag.getString("phase"),
                parseMode(tag.getString("transportMode")),
                parseTarget(tag.getString("target")),
                tag.getString("targetName"),
                tag.contains("distance") ? tag.getInt("distance") : -1,
                tag.getBoolean("targetLoaded"),
                tag.getBoolean("recallAvailable"),
                tag.getLong("updated"),
                stations,
                tag.contains("stationLimit") ? tag.getInt("stationLimit")
                        : CourierData.MAX_WAREHOUSES);
    }

    public static void clearLegacy(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag != null) tag.remove(TAG_SNAPSHOT);
    }

    private static CourierData.TransportMode parseMode(String value) {
        try {
            return CourierData.TransportMode.valueOf(value);
        } catch (IllegalArgumentException ignored) {
            return CourierData.TransportMode.NONE;
        }
    }

    private static TargetKind parseTarget(String value) {
        try {
            return TargetKind.valueOf(value);
        } catch (IllegalArgumentException ignored) {
            return TargetKind.NONE;
        }
    }
}
