package io.github.maidstorageextension.data;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** World authority for placed business licenses and their local operating areas. */
public final class BusinessLicenseData extends SavedData {
    public static final String DATA_NAME = "maid_storage_manager_extension_business_licenses";
    public static final int MAX_WORKERS = 6;
    public static final int MAX_CONTAINERS = 64;
    public static final int MAX_FILTER_ITEMS = 54;
    public static final int MAX_NAME_LENGTH = 32;
    public static final int RANGE = 64;

    public enum RuleMode {
        WHITELIST,
        BLACKLIST
    }

    public record LicenseKey(ResourceLocation dimension, BlockPos position, UUID id) {
        public LicenseKey {
            position = position == null ? null : position.immutable();
        }

        public boolean valid() {
            return dimension != null && position != null && id != null;
        }
    }

    public record ContainerRef(BlockPos position, Direction side) {
        public ContainerRef {
            position = position == null ? null : position.immutable();
            side = side == null ? Direction.UP : side;
        }

        public boolean valid() {
            return position != null && side != null;
        }
    }

    public record Snapshot(UUID id, UUID owner, String name, ResourceLocation dimension,
                           BlockPos position, RuleMode mode, List<ResourceLocation> filterItems,
                           ResourceLocation profession, List<UUID> workers,
                           List<ContainerRef> containers, BlockPos landingPos,
                           long revision, String blocker) {
        public Snapshot {
            name = name == null ? "" : name;
            mode = mode == null ? RuleMode.WHITELIST : mode;
            filterItems = filterItems == null ? List.of() : List.copyOf(filterItems);
            workers = workers == null ? List.of() : List.copyOf(workers);
            containers = containers == null ? List.of() : List.copyOf(containers);
            position = position == null ? null : position.immutable();
            landingPos = landingPos == null ? null : landingPos.immutable();
            blocker = blocker == null ? "" : blocker;
        }

        public LicenseKey key() {
            return new LicenseKey(dimension, position, id);
        }

        public boolean allows(ResourceLocation item) {
            boolean listed = item != null && filterItems.contains(item);
            return mode == RuleMode.WHITELIST ? listed : !listed;
        }
    }

    public enum BindWorkerResult {
        ADDED,
        REMOVED,
        FULL,
        WRONG_OWNER,
        WRONG_PROFESSION,
        BOUND_ELSEWHERE,
        INVALID
    }

    private static final class Entry {
        private final UUID id;
        private final UUID owner;
        private final ResourceLocation dimension;
        private final BlockPos position;
        private String name = "";
        private RuleMode mode = RuleMode.WHITELIST;
        private final LinkedHashSet<ResourceLocation> filterItems = new LinkedHashSet<>();
        private ResourceLocation profession;
        private final LinkedHashSet<UUID> workers = new LinkedHashSet<>();
        private final List<ContainerRef> containers = new ArrayList<>();
        private BlockPos landingPos;
        private long revision = 1L;
        private String blocker = "";

        private Entry(UUID id, UUID owner, ResourceLocation dimension, BlockPos position) {
            this.id = id;
            this.owner = owner;
            this.dimension = dimension;
            this.position = position.immutable();
        }

        private Snapshot snapshot() {
            return new Snapshot(id, owner, name, dimension, position, mode,
                    List.copyOf(filterItems), profession, List.copyOf(workers),
                    List.copyOf(containers), landingPos, revision, blocker);
        }

        private void changed() {
            revision = revision == Long.MAX_VALUE ? 1L : revision + 1L;
        }
    }

    private final Map<UUID, Entry> entries = new LinkedHashMap<>();

    public static BusinessLicenseData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                BusinessLicenseData::load, BusinessLicenseData::new, DATA_NAME);
    }

    public Snapshot create(UUID id, UUID owner, ResourceLocation dimension, BlockPos position) {
        if (id == null || owner == null || dimension == null || position == null) return null;
        Entry existing = entries.get(id);
        if (existing != null) return existing.snapshot();
        Entry entry = new Entry(id, owner, dimension, position);
        entries.put(id, entry);
        setDirty();
        return entry.snapshot();
    }

    public Snapshot get(UUID id) {
        Entry entry = id == null ? null : entries.get(id);
        return entry == null ? null : entry.snapshot();
    }

    public Snapshot get(LicenseKey key) {
        Snapshot value = key == null ? null : get(key.id());
        return value != null && value.dimension().equals(key.dimension())
                && value.position().equals(key.position()) ? value : null;
    }

    public List<Snapshot> all() {
        return entries.values().stream().map(Entry::snapshot).toList();
    }

    public Snapshot forWorker(UUID worker) {
        if (worker == null) return null;
        return entries.values().stream().filter(entry -> entry.workers.contains(worker))
                .map(Entry::snapshot).findFirst().orElse(null);
    }

    public boolean remove(UUID id) {
        if (id == null || entries.remove(id) == null) return false;
        setDirty();
        return true;
    }

    public boolean rename(UUID id, UUID actor, String name) {
        Entry entry = owned(id, actor);
        if (entry == null) return false;
        String normalized = normalizeName(name);
        if (entry.name.equals(normalized)) return true;
        entry.name = normalized;
        entry.changed();
        setDirty();
        return true;
    }

    public boolean setMode(UUID id, UUID actor, RuleMode mode) {
        Entry entry = owned(id, actor);
        if (entry == null || mode == null) return false;
        if (entry.mode == mode) return true;
        entry.mode = mode;
        entry.changed();
        setDirty();
        return true;
    }

    public boolean toggleFilter(UUID id, UUID actor, ResourceLocation item) {
        Entry entry = owned(id, actor);
        if (entry == null || item == null || !ForgeRegistries.ITEMS.containsKey(item)) return false;
        if (!entry.filterItems.remove(item)) {
            if (entry.filterItems.size() >= MAX_FILTER_ITEMS) return false;
            entry.filterItems.add(item);
        }
        entry.changed();
        setDirty();
        return true;
    }

    public boolean clearFilter(UUID id, UUID actor) {
        Entry entry = owned(id, actor);
        if (entry == null) return false;
        if (entry.filterItems.isEmpty()) return true;
        entry.filterItems.clear();
        entry.changed();
        setDirty();
        return true;
    }

    public boolean toggleContainer(UUID id, UUID actor, ContainerRef container) {
        Entry entry = owned(id, actor);
        if (entry == null || container == null || !container.valid()
                || !withinHorizontal(entry.position, container.position, RANGE)) return false;
        int index = entry.containers.indexOf(container);
        if (index >= 0) entry.containers.remove(index);
        else {
            if (entry.containers.size() >= MAX_CONTAINERS) return false;
            entry.containers.add(container);
        }
        entry.blocker = "";
        entry.changed();
        setDirty();
        return true;
    }

    public boolean setLanding(UUID id, UUID actor, BlockPos landing) {
        Entry entry = owned(id, actor);
        if (entry == null || landing == null
                || !withinHorizontal(entry.position, landing, RANGE)) return false;
        entry.landingPos = landing.immutable();
        entry.changed();
        setDirty();
        return true;
    }

    public BindWorkerResult toggleWorker(UUID id, UUID actor, UUID worker, UUID workerOwner,
                                         ResourceLocation profession) {
        Entry entry = owned(id, actor);
        if (entry == null || worker == null || profession == null) return BindWorkerResult.INVALID;
        if (!actor.equals(workerOwner)) return BindWorkerResult.WRONG_OWNER;
        if (entry.workers.remove(worker)) {
            if (entry.workers.isEmpty()) entry.profession = null;
            entry.changed();
            setDirty();
            return BindWorkerResult.REMOVED;
        }
        if (entry.workers.size() >= MAX_WORKERS) return BindWorkerResult.FULL;
        if (entries.values().stream().anyMatch(other -> other != entry
                && other.workers.contains(worker))) return BindWorkerResult.BOUND_ELSEWHERE;
        if (entry.profession != null && !entry.profession.equals(profession)) {
            return BindWorkerResult.WRONG_PROFESSION;
        }
        entry.profession = profession;
        entry.workers.add(worker);
        entry.changed();
        setDirty();
        return BindWorkerResult.ADDED;
    }

    public void blocker(UUID id, String blocker) {
        Entry entry = entries.get(id);
        if (entry == null) return;
        String normalized = blocker == null ? "" : blocker;
        if (entry.blocker.equals(normalized)) return;
        entry.blocker = normalized;
        entry.changed();
        setDirty();
    }

    private Entry owned(UUID id, UUID actor) {
        Entry entry = id == null ? null : entries.get(id);
        return entry != null && actor != null && actor.equals(entry.owner) ? entry : null;
    }

    public static boolean withinHorizontal(BlockPos first, BlockPos second, int range) {
        if (first == null || second == null || range < 0) return false;
        long dx = (long) first.getX() - second.getX();
        long dz = (long) first.getZ() - second.getZ();
        return dx * dx + dz * dz <= (long) range * range;
    }

    public static String normalizeName(String value) {
        if (value == null) return "";
        String trimmed = value.trim();
        int end = trimmed.offsetByCodePoints(0,
                Math.min(MAX_NAME_LENGTH, trimmed.codePointCount(0, trimmed.length())));
        return trimmed.substring(0, end);
    }

    @Override
    public CompoundTag save(CompoundTag root) {
        ListTag values = new ListTag();
        for (Entry entry : entries.values()) {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("id", entry.id);
            tag.putUUID("owner", entry.owner);
            tag.putString("dimension", entry.dimension.toString());
            tag.putLong("position", entry.position.asLong());
            tag.putString("name", entry.name);
            tag.putString("mode", entry.mode.name());
            ListTag filters = new ListTag();
            for (ResourceLocation item : entry.filterItems) {
                CompoundTag filter = new CompoundTag();
                filter.putString("item", item.toString());
                filters.add(filter);
            }
            tag.put("filters", filters);
            if (entry.profession != null) tag.putString("profession", entry.profession.toString());
            ListTag workers = new ListTag();
            for (UUID worker : entry.workers) {
                CompoundTag value = new CompoundTag();
                value.putUUID("id", worker);
                workers.add(value);
            }
            tag.put("workers", workers);
            ListTag containers = new ListTag();
            for (ContainerRef container : entry.containers) {
                CompoundTag value = new CompoundTag();
                value.putLong("position", container.position().asLong());
                value.putString("side", container.side().getName());
                containers.add(value);
            }
            tag.put("containers", containers);
            if (entry.landingPos != null) tag.putLong("landing", entry.landingPos.asLong());
            tag.putLong("revision", Math.max(1L, entry.revision));
            if (!entry.blocker.isBlank()) tag.putString("blocker", entry.blocker);
            values.add(tag);
        }
        root.put("licenses", values);
        return root;
    }

    public static BusinessLicenseData load(CompoundTag root) {
        BusinessLicenseData data = new BusinessLicenseData();
        ListTag values = root.getList("licenses", Tag.TAG_COMPOUND);
        for (int i = 0; i < values.size(); i++) {
            CompoundTag tag = values.getCompound(i);
            if (!tag.hasUUID("id") || !tag.hasUUID("owner")
                    || !tag.contains("position", Tag.TAG_LONG)) continue;
            ResourceLocation dimension = ResourceLocation.tryParse(tag.getString("dimension"));
            if (dimension == null) continue;
            Entry entry = new Entry(tag.getUUID("id"), tag.getUUID("owner"), dimension,
                    BlockPos.of(tag.getLong("position")));
            entry.name = normalizeName(tag.getString("name"));
            try {
                entry.mode = RuleMode.valueOf(tag.getString("mode"));
            } catch (IllegalArgumentException ignored) {
                entry.mode = RuleMode.WHITELIST;
            }
            ListTag filters = tag.getList("filters", Tag.TAG_COMPOUND);
            for (int j = 0; j < filters.size() && entry.filterItems.size() < MAX_FILTER_ITEMS; j++) {
                ResourceLocation item = ResourceLocation.tryParse(filters.getCompound(j).getString("item"));
                if (item != null && ForgeRegistries.ITEMS.containsKey(item)) entry.filterItems.add(item);
            }
            entry.profession = ResourceLocation.tryParse(tag.getString("profession"));
            ListTag workers = tag.getList("workers", Tag.TAG_COMPOUND);
            for (int j = 0; j < workers.size() && entry.workers.size() < MAX_WORKERS; j++) {
                if (workers.getCompound(j).hasUUID("id")) {
                    entry.workers.add(workers.getCompound(j).getUUID("id"));
                }
            }
            ListTag containers = tag.getList("containers", Tag.TAG_COMPOUND);
            for (int j = 0; j < containers.size() && entry.containers.size() < MAX_CONTAINERS; j++) {
                CompoundTag value = containers.getCompound(j);
                if (!value.contains("position", Tag.TAG_LONG)) continue;
                Direction side = Direction.byName(value.getString("side"));
                entry.containers.add(new ContainerRef(BlockPos.of(value.getLong("position")), side));
            }
            entry.landingPos = tag.contains("landing", Tag.TAG_LONG)
                    ? BlockPos.of(tag.getLong("landing")) : null;
            entry.revision = tag.contains("revision", Tag.TAG_LONG)
                    ? Math.max(1L, tag.getLong("revision")) : 1L;
            entry.blocker = tag.getString("blocker");
            data.entries.put(entry.id, entry);
        }
        return data;
    }
}
