package io.github.maidstorageextension.logistics;

import io.github.maidstorageextension.data.BusinessLicenseData;
import io.github.maidstorageextension.terminal.MailboxKey;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Account-owned routes and permanent round-robin courier schedules. */
public final class MaidLogisticsData extends SavedData {
    public static final String DATA_NAME = "maid_storage_manager_extension_maid_logistics";
    public static final int MAX_ROUTES_PER_ACCOUNT = 128;
    public static final int MAX_SLOTS_PER_COURIER = 256;
    public static final int MAX_LINES_PER_ROUTE = 10;
    public static final int MAX_LINE_AMOUNT = 1_000_000;

    public enum NodeKind {
        LICENSE,
        WAREHOUSE
    }

    public enum RouteStatus {
        READY,
        ACTIVE,
        SOURCE_EMPTY,
        DESTINATION_FULL,
        BLOCKED
    }

    public record NodeRef(NodeKind kind, ResourceLocation dimension, BlockPos position, UUID license) {
        public NodeRef {
            kind = kind == null ? NodeKind.LICENSE : kind;
            position = position == null ? null : position.immutable();
        }

        public boolean valid() {
            return dimension != null && position != null
                    && (kind != NodeKind.LICENSE || license != null);
        }

        public MailboxKey mailbox() {
            return kind == NodeKind.WAREHOUSE ? new MailboxKey(dimension, position) : null;
        }

        public BusinessLicenseData.LicenseKey licenseKey() {
            return kind == NodeKind.LICENSE
                    ? new BusinessLicenseData.LicenseKey(dimension, position, license) : null;
        }
    }

    public record CargoLine(ItemStack prototype, int amount) {
        public CargoLine {
            prototype = prototype == null || prototype.isEmpty()
                    ? ItemStack.EMPTY : prototype.copyWithCount(1);
            amount = Math.max(0, Math.min(MAX_LINE_AMOUNT, amount));
        }

        public boolean valid() {
            return !prototype.isEmpty() && amount > 0;
        }
    }

    public record Route(UUID id, UUID account, NodeRef source, NodeRef destination,
                        UUID courier, List<CargoLine> lines, RouteStatus status,
                        String blocker, boolean fullWarningLatched, long revision) {
        public Route {
            lines = lines == null ? List.of() : List.copyOf(lines);
            status = status == null ? RouteStatus.BLOCKED : status;
            blocker = blocker == null ? "" : blocker;
        }
    }

    public record Schedule(UUID courier, List<UUID> slots, int cursor) {
        public Schedule {
            slots = slots == null ? List.of() : List.copyOf(slots);
            cursor = slots.isEmpty() ? 0 : Math.floorMod(cursor, slots.size());
        }
    }

    public enum MutationResult {
        OK,
        NOT_FOUND,
        INVALID,
        LIMIT,
        BUSY,
        CONFLICT
    }

    private static final class MutableRoute {
        private final UUID id;
        private final UUID account;
        private NodeRef source;
        private NodeRef destination;
        private UUID courier;
        private List<CargoLine> lines;
        private RouteStatus status = RouteStatus.READY;
        private String blocker = "";
        private boolean fullWarningLatched;
        private long revision = 1L;

        private MutableRoute(UUID id, UUID account, NodeRef source, NodeRef destination,
                             UUID courier, List<CargoLine> lines) {
            this.id = id;
            this.account = account;
            this.source = source;
            this.destination = destination;
            this.courier = courier;
            this.lines = List.copyOf(lines);
        }

        private Route snapshot() {
            return new Route(id, account, source, destination, courier, lines, status,
                    blocker, fullWarningLatched, revision);
        }

        private void changed() {
            revision = revision == Long.MAX_VALUE ? 1L : revision + 1L;
        }
    }

    private static final class MutableSchedule {
        private final UUID courier;
        private final List<UUID> slots = new ArrayList<>();
        private int cursor;

        private MutableSchedule(UUID courier) {
            this.courier = courier;
        }

        private Schedule snapshot() {
            return new Schedule(courier, slots, cursor);
        }

        private void normalize() {
            cursor = slots.isEmpty() ? 0 : Math.floorMod(cursor, slots.size());
        }
    }

    private final Map<UUID, MutableRoute> routes = new LinkedHashMap<>();
    private final Map<UUID, MutableSchedule> schedules = new LinkedHashMap<>();

    public static MaidLogisticsData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                MaidLogisticsData::load, MaidLogisticsData::new, DATA_NAME);
    }

    public Route route(UUID id) {
        MutableRoute route = id == null ? null : routes.get(id);
        return route == null ? null : route.snapshot();
    }

    public List<Route> routes(UUID account) {
        return routes.values().stream().filter(value -> value.account.equals(account))
                .map(MutableRoute::snapshot).toList();
    }

    public Schedule schedule(UUID courier) {
        MutableSchedule schedule = courier == null ? null : schedules.get(courier);
        return schedule == null ? new Schedule(courier, List.of(), 0) : schedule.snapshot();
    }

    public MutationResult create(UUID account, NodeRef source, NodeRef destination,
                                 UUID courier, List<CargoLine> lines, UUID[] created) {
        if (account == null || courier == null || !validEndpoints(source, destination)
                || !validLines(lines)) return MutationResult.INVALID;
        if (routes(account).size() >= MAX_ROUTES_PER_ACCOUNT) return MutationResult.LIMIT;
        MutableSchedule schedule = schedules.computeIfAbsent(courier, MutableSchedule::new);
        if (schedule.slots.size() >= MAX_SLOTS_PER_COURIER) return MutationResult.LIMIT;
        UUID id = UUID.randomUUID();
        routes.put(id, new MutableRoute(id, account, source, destination, courier, lines));
        schedule.slots.add(id);
        schedule.normalize();
        if (created != null && created.length > 0) created[0] = id;
        setDirty();
        return MutationResult.OK;
    }

    public MutationResult update(UUID account, UUID id, long expectedRevision,
                                 UUID courier, List<CargoLine> lines) {
        MutableRoute route = owned(account, id);
        if (route == null) return MutationResult.NOT_FOUND;
        if (expectedRevision > 0L && route.revision != expectedRevision) return MutationResult.CONFLICT;
        if (courier == null || !validLines(lines)) return MutationResult.INVALID;
        if (!courier.equals(route.courier)) {
            MutableSchedule next = schedules.computeIfAbsent(courier, MutableSchedule::new);
            MutableSchedule current = schedules.get(route.courier);
            int occurrences = current == null ? 0
                    : (int) current.slots.stream().filter(route.id::equals).count();
            occurrences = Math.max(1, occurrences);
            if (next.slots.size() + occurrences > MAX_SLOTS_PER_COURIER) {
                return MutationResult.LIMIT;
            }
            removeRouteSlots(route.courier, route.id);
            for (int i = 0; i < occurrences; i++) next.slots.add(route.id);
            next.normalize();
            route.courier = courier;
        }
        route.lines = List.copyOf(lines);
        route.status = RouteStatus.READY;
        route.blocker = "";
        route.changed();
        setDirty();
        return MutationResult.OK;
    }

    public MutationResult delete(UUID account, UUID id) {
        MutableRoute route = owned(account, id);
        if (route == null) return MutationResult.NOT_FOUND;
        routes.remove(id);
        removeRouteSlots(route.courier, id);
        setDirty();
        return MutationResult.OK;
    }

    public MutationResult addSlot(UUID account, UUID courier, UUID routeId) {
        MutableRoute route = owned(account, routeId);
        if (route == null || !courier.equals(route.courier)) return MutationResult.INVALID;
        MutableSchedule schedule = schedules.computeIfAbsent(courier, MutableSchedule::new);
        if (schedule.slots.size() >= MAX_SLOTS_PER_COURIER) return MutationResult.LIMIT;
        schedule.slots.add(routeId);
        schedule.normalize();
        setDirty();
        return MutationResult.OK;
    }

    public MutationResult removeSlot(UUID account, UUID courier, int index) {
        MutableSchedule schedule = schedules.get(courier);
        if (schedule == null || index < 0 || index >= schedule.slots.size()) {
            return MutationResult.INVALID;
        }
        MutableRoute route = owned(account, schedule.slots.get(index));
        if (route == null || !courier.equals(route.courier)) return MutationResult.INVALID;
        schedule.slots.remove(index);
        if (index < schedule.cursor) schedule.cursor--;
        schedule.normalize();
        setDirty();
        return MutationResult.OK;
    }

    public MutationResult moveSlot(UUID account, UUID courier, int from, int to) {
        MutableSchedule schedule = schedules.get(courier);
        if (schedule == null || from < 0 || to < 0
                || from >= schedule.slots.size() || to >= schedule.slots.size()) {
            return MutationResult.INVALID;
        }
        MutableRoute route = owned(account, schedule.slots.get(from));
        if (route == null || !courier.equals(route.courier)) return MutationResult.INVALID;
        UUID value = schedule.slots.remove(from);
        schedule.slots.add(to, value);
        schedule.cursor = to;
        schedule.normalize();
        setDirty();
        return MutationResult.OK;
    }

    /** Returns the current opportunity and advances the persistent round-robin cursor. */
    public Route advance(UUID courier) {
        MutableSchedule schedule = schedules.get(courier);
        if (schedule == null || schedule.slots.isEmpty()) return null;
        schedule.normalize();
        UUID id = schedule.slots.get(schedule.cursor);
        schedule.cursor = (schedule.cursor + 1) % schedule.slots.size();
        setDirty();
        MutableRoute route = routes.get(id);
        return route == null ? null : route.snapshot();
    }

    public void status(UUID routeId, RouteStatus status, String blocker) {
        MutableRoute route = routes.get(routeId);
        if (route == null) return;
        String normalized = blocker == null ? "" : blocker;
        if (route.status == status && route.blocker.equals(normalized)) return;
        route.status = status;
        route.blocker = normalized;
        if (status == RouteStatus.DESTINATION_FULL) route.fullWarningLatched = true;
        else if (status == RouteStatus.READY) route.fullWarningLatched = false;
        route.changed();
        setDirty();
    }

    public boolean warningLatched(UUID routeId) {
        MutableRoute route = routes.get(routeId);
        return route != null && route.fullWarningLatched;
    }

    public static boolean validEndpoints(NodeRef source, NodeRef destination) {
        if (source == null || destination == null || !source.valid() || !destination.valid()
                || source.equals(destination)
                || !source.dimension().equals(destination.dimension())) return false;
        return source.kind() == NodeKind.LICENSE || destination.kind() == NodeKind.LICENSE;
    }

    public static boolean validLines(List<CargoLine> lines) {
        return lines != null && !lines.isEmpty() && lines.size() <= MAX_LINES_PER_ROUTE
                && lines.stream().allMatch(CargoLine::valid);
    }

    private MutableRoute owned(UUID account, UUID id) {
        MutableRoute route = id == null ? null : routes.get(id);
        return route != null && route.account.equals(account) ? route : null;
    }

    private void removeRouteSlots(UUID courier, UUID route) {
        MutableSchedule schedule = schedules.get(courier);
        if (schedule == null) return;
        schedule.slots.removeIf(route::equals);
        schedule.normalize();
        if (schedule.slots.isEmpty()) schedules.remove(courier);
    }

    @Override
    public CompoundTag save(CompoundTag root) {
        ListTag routeTags = new ListTag();
        for (MutableRoute route : routes.values()) {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("id", route.id);
            tag.putUUID("account", route.account);
            tag.put("source", nodeToTag(route.source));
            tag.put("destination", nodeToTag(route.destination));
            tag.putUUID("courier", route.courier);
            ListTag lines = new ListTag();
            for (CargoLine line : route.lines) {
                CompoundTag value = new CompoundTag();
                value.put("item", line.prototype().save(new CompoundTag()));
                value.putInt("amount", line.amount());
                lines.add(value);
            }
            tag.put("lines", lines);
            tag.putString("status", route.status.name());
            tag.putString("blocker", route.blocker);
            tag.putBoolean("fullWarningLatched", route.fullWarningLatched);
            tag.putLong("revision", route.revision);
            routeTags.add(tag);
        }
        root.put("routes", routeTags);
        ListTag scheduleTags = new ListTag();
        for (MutableSchedule schedule : schedules.values()) {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("courier", schedule.courier);
            ListTag slots = new ListTag();
            for (UUID route : schedule.slots) {
                CompoundTag value = new CompoundTag();
                value.putUUID("route", route);
                slots.add(value);
            }
            tag.put("slots", slots);
            tag.putInt("cursor", schedule.cursor);
            scheduleTags.add(tag);
        }
        root.put("schedules", scheduleTags);
        return root;
    }

    public static MaidLogisticsData load(CompoundTag root) {
        MaidLogisticsData data = new MaidLogisticsData();
        ListTag routeTags = root.getList("routes", Tag.TAG_COMPOUND);
        Map<UUID, Integer> accountCounts = new LinkedHashMap<>();
        for (int i = 0; i < routeTags.size(); i++) {
            CompoundTag tag = routeTags.getCompound(i);
            if (!tag.hasUUID("id") || !tag.hasUUID("account") || !tag.hasUUID("courier")) continue;
            UUID account = tag.getUUID("account");
            if (accountCounts.getOrDefault(account, 0) >= MAX_ROUTES_PER_ACCOUNT) continue;
            NodeRef source = nodeFromTag(tag.getCompound("source"));
            NodeRef destination = nodeFromTag(tag.getCompound("destination"));
            List<CargoLine> lines = linesFromTag(tag.getList("lines", Tag.TAG_COMPOUND));
            if (!validEndpoints(source, destination) || !validLines(lines)) continue;
            MutableRoute route = new MutableRoute(tag.getUUID("id"), account, source,
                    destination, tag.getUUID("courier"), lines);
            try {
                route.status = RouteStatus.valueOf(tag.getString("status"));
            } catch (IllegalArgumentException ignored) {
                route.status = RouteStatus.BLOCKED;
            }
            route.blocker = tag.getString("blocker");
            route.fullWarningLatched = tag.getBoolean("fullWarningLatched");
            route.revision = Math.max(1L, tag.getLong("revision"));
            data.routes.put(route.id, route);
            accountCounts.merge(account, 1, Integer::sum);
        }
        ListTag scheduleTags = root.getList("schedules", Tag.TAG_COMPOUND);
        for (int i = 0; i < scheduleTags.size(); i++) {
            CompoundTag tag = scheduleTags.getCompound(i);
            if (!tag.hasUUID("courier")) continue;
            MutableSchedule schedule = new MutableSchedule(tag.getUUID("courier"));
            ListTag slots = tag.getList("slots", Tag.TAG_COMPOUND);
            for (int j = 0; j < slots.size() && schedule.slots.size() < MAX_SLOTS_PER_COURIER; j++) {
                CompoundTag value = slots.getCompound(j);
                if (!value.hasUUID("route")) continue;
                UUID route = value.getUUID("route");
                MutableRoute stored = data.routes.get(route);
                if (stored != null && stored.courier.equals(schedule.courier)) schedule.slots.add(route);
            }
            schedule.cursor = tag.getInt("cursor");
            schedule.normalize();
            if (!schedule.slots.isEmpty()) data.schedules.put(schedule.courier, schedule);
        }
        return data;
    }

    private static CompoundTag nodeToTag(NodeRef node) {
        CompoundTag tag = new CompoundTag();
        tag.putString("kind", node.kind().name());
        tag.putString("dimension", node.dimension().toString());
        tag.putLong("position", node.position().asLong());
        if (node.license() != null) tag.putUUID("license", node.license());
        return tag;
    }

    private static NodeRef nodeFromTag(CompoundTag tag) {
        try {
            NodeKind kind = NodeKind.valueOf(tag.getString("kind"));
            ResourceLocation dimension = ResourceLocation.tryParse(tag.getString("dimension"));
            if (dimension == null || !tag.contains("position", Tag.TAG_LONG)) return null;
            return new NodeRef(kind, dimension, BlockPos.of(tag.getLong("position")),
                    tag.hasUUID("license") ? tag.getUUID("license") : null);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static List<CargoLine> linesFromTag(ListTag tags) {
        List<CargoLine> lines = new ArrayList<>();
        for (int i = 0; i < tags.size() && lines.size() < MAX_LINES_PER_ROUTE; i++) {
            CompoundTag tag = tags.getCompound(i);
            if (!tag.contains("item", Tag.TAG_COMPOUND)) continue;
            CargoLine line = new CargoLine(ItemStack.of(tag.getCompound("item")), tag.getInt("amount"));
            if (line.valid()) lines.add(line);
        }
        return List.copyOf(lines);
    }
}
