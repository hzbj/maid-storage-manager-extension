package io.github.maidstorageextension.logistics;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Bounded client-safe view of one terminal account's maid-logistics network. */
public final class MaidLogisticsSnapshot {
    public record LicenseWorker(UUID id, String name, boolean onDuty) {
        public LicenseWorker {
            name = name == null ? "" : name;
        }
    }

    public record Node(MaidLogisticsData.NodeKind kind, UUID license, String name,
                       ResourceLocation dimension, BlockPos position, boolean valid,
                       int inventoryTypes, boolean full, int boundWorkers,
                       String ruleMode, List<ResourceLocation> filterItems,
                       List<LicenseWorker> workers, int containerCount, String blocker) {
        public Node {
            name = name == null ? "" : name;
            position = position == null ? null : position.immutable();
            ruleMode = ruleMode == null ? "" : ruleMode;
            filterItems = filterItems == null ? List.of() : List.copyOf(filterItems);
            workers = workers == null ? List.of() : List.copyOf(workers);
            blocker = blocker == null ? "" : blocker;
        }

        public MaidLogisticsData.NodeRef ref() {
            return new MaidLogisticsData.NodeRef(kind, dimension, position, license);
        }
    }

    public record Courier(UUID id, String name, boolean online, boolean onDuty,
                          ResourceLocation dimension, BlockPos position, int color,
                          UUID activeRoute, List<MaidLogisticsData.CargoLine> cargo,
                          List<UUID> slots, int cursor) {
        public Courier {
            name = name == null ? "" : name;
            position = position == null ? null : position.immutable();
            cargo = cargo == null ? List.of() : List.copyOf(cargo);
            slots = slots == null ? List.of() : List.copyOf(slots);
        }
    }

    public record Snapshot(UUID account, long dayTime, List<Node> nodes,
                           List<Courier> couriers, List<MaidLogisticsData.Route> routes,
                           String error) {
        public Snapshot {
            nodes = nodes == null ? List.of() : List.copyOf(nodes);
            couriers = couriers == null ? List.of() : List.copyOf(couriers);
            routes = routes == null ? List.of() : List.copyOf(routes);
            error = error == null ? "" : error;
        }

        public static Snapshot empty(String error) {
            return new Snapshot(null, 0L, List.of(), List.of(), List.of(), error);
        }
    }

    private MaidLogisticsSnapshot() {
    }

    public static CompoundTag toTag(Snapshot snapshot) {
        CompoundTag tag = new CompoundTag();
        if (snapshot.account() != null) tag.putUUID("account", snapshot.account());
        tag.putLong("dayTime", snapshot.dayTime());
        tag.putString("error", snapshot.error());
        ListTag nodes = new ListTag();
        for (Node node : snapshot.nodes()) {
            CompoundTag value = nodeTag(node.ref());
            value.putString("name", node.name());
            value.putBoolean("valid", node.valid());
            value.putInt("inventoryTypes", node.inventoryTypes());
            value.putBoolean("full", node.full());
            value.putInt("boundWorkers", node.boundWorkers());
            value.putString("ruleMode", node.ruleMode());
            ListTag filters = new ListTag();
            for (ResourceLocation item : node.filterItems()) {
                CompoundTag filter = new CompoundTag();
                filter.putString("item", item.toString());
                filters.add(filter);
            }
            value.put("filterItems", filters);
            ListTag workers = new ListTag();
            for (LicenseWorker worker : node.workers()) {
                CompoundTag entry = new CompoundTag();
                entry.putUUID("id", worker.id());
                entry.putString("name", worker.name());
                entry.putBoolean("onDuty", worker.onDuty());
                workers.add(entry);
            }
            value.put("workers", workers);
            value.putInt("containerCount", node.containerCount());
            value.putString("blocker", node.blocker());
            nodes.add(value);
        }
        tag.put("nodes", nodes);
        ListTag couriers = new ListTag();
        for (Courier courier : snapshot.couriers()) {
            CompoundTag value = new CompoundTag();
            value.putUUID("id", courier.id());
            value.putString("name", courier.name());
            value.putBoolean("online", courier.online());
            value.putBoolean("onDuty", courier.onDuty());
            if (courier.dimension() != null) value.putString("dimension", courier.dimension().toString());
            if (courier.position() != null) value.putLong("position", courier.position().asLong());
            value.putInt("color", courier.color());
            if (courier.activeRoute() != null) value.putUUID("activeRoute", courier.activeRoute());
            value.put("cargo", cargoTag(courier.cargo()));
            ListTag slots = new ListTag();
            for (UUID route : courier.slots()) {
                CompoundTag slot = new CompoundTag();
                slot.putUUID("route", route);
                slots.add(slot);
            }
            value.put("slots", slots);
            value.putInt("cursor", courier.cursor());
            couriers.add(value);
        }
        tag.put("couriers", couriers);
        ListTag routes = new ListTag();
        for (MaidLogisticsData.Route route : snapshot.routes()) routes.add(routeTag(route));
        tag.put("routes", routes);
        return tag;
    }

    public static Snapshot fromTag(CompoundTag tag) {
        if (tag == null) return Snapshot.empty("");
        List<Node> nodes = new ArrayList<>();
        ListTag nodeTags = tag.getList("nodes", Tag.TAG_COMPOUND);
        for (int i = 0; i < nodeTags.size() && i < 64; i++) {
            CompoundTag value = nodeTags.getCompound(i);
            MaidLogisticsData.NodeRef ref = nodeFromTag(value);
            if (ref == null) continue;
            List<ResourceLocation> filters = new ArrayList<>();
            ListTag filterTags = value.getList("filterItems", Tag.TAG_COMPOUND);
            for (int j = 0; j < filterTags.size()
                    && j < io.github.maidstorageextension.data.BusinessLicenseData.MAX_FILTER_ITEMS; j++) {
                ResourceLocation item = ResourceLocation.tryParse(filterTags.getCompound(j).getString("item"));
                if (item != null) filters.add(item);
            }
            List<LicenseWorker> workers = new ArrayList<>();
            ListTag workerTags = value.getList("workers", Tag.TAG_COMPOUND);
            for (int j = 0; j < workerTags.size()
                    && j < io.github.maidstorageextension.data.BusinessLicenseData.MAX_WORKERS; j++) {
                CompoundTag worker = workerTags.getCompound(j);
                if (worker.hasUUID("id")) workers.add(new LicenseWorker(worker.getUUID("id"),
                        worker.getString("name"), worker.getBoolean("onDuty")));
            }
            nodes.add(new Node(ref.kind(), ref.license(), value.getString("name"),
                    ref.dimension(), ref.position(), value.getBoolean("valid"),
                    Math.max(0, value.getInt("inventoryTypes")), value.getBoolean("full"),
                    Math.max(0, value.getInt("boundWorkers")), value.getString("ruleMode"),
                    filters, workers, Math.max(0, value.getInt("containerCount")),
                    value.getString("blocker")));
        }
        List<Courier> couriers = new ArrayList<>();
        ListTag courierTags = tag.getList("couriers", Tag.TAG_COMPOUND);
        for (int i = 0; i < courierTags.size() && i < 32; i++) {
            CompoundTag value = courierTags.getCompound(i);
            if (!value.hasUUID("id")) continue;
            List<UUID> slots = new ArrayList<>();
            ListTag slotTags = value.getList("slots", Tag.TAG_COMPOUND);
            for (int j = 0; j < slotTags.size() && j < MaidLogisticsData.MAX_SLOTS_PER_COURIER; j++) {
                if (slotTags.getCompound(j).hasUUID("route")) {
                    slots.add(slotTags.getCompound(j).getUUID("route"));
                }
            }
            couriers.add(new Courier(value.getUUID("id"), value.getString("name"),
                    value.getBoolean("online"), value.getBoolean("onDuty"),
                    ResourceLocation.tryParse(value.getString("dimension")),
                    value.contains("position", Tag.TAG_LONG) ? BlockPos.of(value.getLong("position")) : null,
                    value.getInt("color"),
                    value.hasUUID("activeRoute") ? value.getUUID("activeRoute") : null,
                    cargoFromTag(value.getList("cargo", Tag.TAG_COMPOUND)),
                    slots, value.getInt("cursor")));
        }
        List<MaidLogisticsData.Route> routes = new ArrayList<>();
        ListTag routeTags = tag.getList("routes", Tag.TAG_COMPOUND);
        for (int i = 0; i < routeTags.size() && i < MaidLogisticsData.MAX_ROUTES_PER_ACCOUNT; i++) {
            MaidLogisticsData.Route route = routeFromTag(routeTags.getCompound(i));
            if (route != null) routes.add(route);
        }
        return new Snapshot(tag.hasUUID("account") ? tag.getUUID("account") : null,
                tag.getLong("dayTime"), nodes, couriers, routes, tag.getString("error"));
    }

    public static CompoundTag routeTag(MaidLogisticsData.Route route) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("id", route.id());
        tag.putUUID("account", route.account());
        tag.put("source", nodeTag(route.source()));
        tag.put("destination", nodeTag(route.destination()));
        tag.putUUID("courier", route.courier());
        tag.put("lines", cargoTag(route.lines()));
        tag.putString("status", route.status().name());
        tag.putString("blocker", route.blocker());
        tag.putBoolean("warning", route.fullWarningLatched());
        tag.putLong("revision", route.revision());
        return tag;
    }

    public static MaidLogisticsData.Route routeFromTag(CompoundTag tag) {
        if (tag == null || !tag.hasUUID("id") || !tag.hasUUID("account")
                || !tag.hasUUID("courier")) return null;
        MaidLogisticsData.NodeRef source = nodeFromTag(tag.getCompound("source"));
        MaidLogisticsData.NodeRef destination = nodeFromTag(tag.getCompound("destination"));
        List<MaidLogisticsData.CargoLine> lines = cargoFromTag(
                tag.getList("lines", Tag.TAG_COMPOUND));
        if (!MaidLogisticsData.validEndpoints(source, destination)
                || !MaidLogisticsData.validLines(lines)) return null;
        MaidLogisticsData.RouteStatus status;
        try {
            status = MaidLogisticsData.RouteStatus.valueOf(tag.getString("status"));
        } catch (IllegalArgumentException ignored) {
            status = MaidLogisticsData.RouteStatus.BLOCKED;
        }
        return new MaidLogisticsData.Route(tag.getUUID("id"), tag.getUUID("account"), source,
                destination, tag.getUUID("courier"), lines, status, tag.getString("blocker"),
                tag.getBoolean("warning"), Math.max(1L, tag.getLong("revision")));
    }

    public static CompoundTag nodeTag(MaidLogisticsData.NodeRef node) {
        CompoundTag tag = new CompoundTag();
        tag.putString("kind", node.kind().name());
        tag.putString("dimension", node.dimension().toString());
        tag.putLong("position", node.position().asLong());
        if (node.license() != null) tag.putUUID("license", node.license());
        return tag;
    }

    public static MaidLogisticsData.NodeRef nodeFromTag(CompoundTag tag) {
        try {
            MaidLogisticsData.NodeKind kind = MaidLogisticsData.NodeKind.valueOf(tag.getString("kind"));
            ResourceLocation dimension = ResourceLocation.tryParse(tag.getString("dimension"));
            if (dimension == null || !tag.contains("position", Tag.TAG_LONG)) return null;
            return new MaidLogisticsData.NodeRef(kind, dimension, BlockPos.of(tag.getLong("position")),
                    tag.hasUUID("license") ? tag.getUUID("license") : null);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static ListTag cargoTag(List<MaidLogisticsData.CargoLine> cargo) {
        ListTag values = new ListTag();
        for (MaidLogisticsData.CargoLine line : cargo) {
            CompoundTag value = new CompoundTag();
            value.put("item", line.prototype().save(new CompoundTag()));
            value.putInt("amount", line.amount());
            values.add(value);
        }
        return values;
    }

    private static List<MaidLogisticsData.CargoLine> cargoFromTag(ListTag tags) {
        List<MaidLogisticsData.CargoLine> lines = new ArrayList<>();
        for (int i = 0; i < tags.size() && i < MaidLogisticsData.MAX_LINES_PER_ROUTE; i++) {
            CompoundTag value = tags.getCompound(i);
            if (!value.contains("item", Tag.TAG_COMPOUND)) continue;
            MaidLogisticsData.CargoLine line = new MaidLogisticsData.CargoLine(
                    ItemStack.of(value.getCompound("item")), value.getInt("amount"));
            if (line.valid()) lines.add(line);
        }
        return List.copyOf(lines);
    }
}
