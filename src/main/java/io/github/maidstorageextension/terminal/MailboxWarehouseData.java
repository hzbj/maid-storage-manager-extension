package io.github.maidstorageextension.terminal;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * World authority for mailbox membership and the one physical inventory-list frame shared by
 * every storage maid assigned to that mailbox.
 */
public final class MailboxWarehouseData extends SavedData {
    public static final String DATA_NAME = "maid_storage_manager_extension_mailbox_warehouses";

    public record FrameBinding(ResourceLocation dimension, BlockPos position, UUID entity) {
        public FrameBinding {
            position = position == null ? null : position.immutable();
        }

        public boolean valid() {
            return dimension != null && position != null && entity != null;
        }

        private CompoundTag toTag() {
            CompoundTag tag = new CompoundTag();
            if (dimension != null) tag.putString("dimension", dimension.toString());
            if (position != null) tag.putLong("position", position.asLong());
            if (entity != null) tag.putUUID("entity", entity);
            return tag;
        }

        private static FrameBinding fromTag(CompoundTag tag) {
            ResourceLocation dimension = ResourceLocation.tryParse(tag.getString("dimension"));
            if (dimension == null || !tag.contains("position", Tag.TAG_LONG)
                    || !tag.hasUUID("entity")) return null;
            return new FrameBinding(dimension, BlockPos.of(tag.getLong("position")),
                    tag.getUUID("entity"));
        }
    }

    public record WarehouseSnapshot(MailboxKey key, List<UUID> managers, FrameBinding frame,
                                    UUID inventoryList, long publishedGameTime,
                                    UUID refreshedBy, long generation) {
        public WarehouseSnapshot {
            managers = managers == null ? List.of() : List.copyOf(managers);
        }

        public boolean hasManagers() {
            return !managers.isEmpty();
        }
    }

    public enum BindResult {
        ADDED,
        ALREADY_BOUND,
        BOUND_ELSEWHERE,
        FRAME_MISMATCH,
        INVALID
    }

    private static final class Entry {
        private final MailboxKey key;
        private final LinkedHashSet<UUID> managers = new LinkedHashSet<>();
        private FrameBinding frame;
        private UUID inventoryList;
        private long publishedGameTime = -1L;
        private UUID refreshedBy;
        private long generation;

        private Entry(MailboxKey key) {
            this.key = key;
        }

        private WarehouseSnapshot snapshot() {
            return new WarehouseSnapshot(key, List.copyOf(managers), frame, inventoryList,
                    publishedGameTime, refreshedBy, generation);
        }
    }

    private final Map<MailboxKey, Entry> entries = new LinkedHashMap<>();
    private final Map<UUID, MailboxKey> managerMailboxes = new LinkedHashMap<>();

    public static MailboxWarehouseData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                MailboxWarehouseData::load, MailboxWarehouseData::new, DATA_NAME);
    }

    public BindResult bind(MailboxKey key, UUID manager, FrameBinding frame) {
        if (key == null || !key.valid() || manager == null || frame == null || !frame.valid()) {
            return BindResult.INVALID;
        }
        MailboxKey current = managerMailboxes.get(manager);
        if (current != null && !current.equals(key)) return BindResult.BOUND_ELSEWHERE;
        Entry entry = entries.computeIfAbsent(key, Entry::new);
        if (entry.frame != null && !entry.frame.equals(frame)) return BindResult.FRAME_MISMATCH;
        if (entry.managers.contains(manager)) return BindResult.ALREADY_BOUND;
        entry.frame = frame;
        entry.managers.add(manager);
        managerMailboxes.put(manager, key);
        entry.generation++;
        setDirty();
        return BindResult.ADDED;
    }

    public boolean unbind(MailboxKey key, UUID manager) {
        Entry entry = entries.get(key);
        if (entry == null || manager == null || !entry.managers.remove(manager)) return false;
        managerMailboxes.remove(manager, key);
        entry.generation++;
        if (entry.managers.isEmpty()) {
            // Keep the physical frame identity for diagnostics, but revoke the network inventory.
            entry.inventoryList = null;
            entry.publishedGameTime = -1L;
            entry.refreshedBy = null;
        }
        setDirty();
        return true;
    }

    public MailboxKey mailboxOf(UUID manager) {
        return manager == null ? null : managerMailboxes.get(manager);
    }

    public WarehouseSnapshot warehouse(MailboxKey key) {
        Entry entry = key == null ? null : entries.get(key);
        return entry == null ? null : entry.snapshot();
    }

    public List<WarehouseSnapshot> warehouses() {
        return entries.values().stream().map(Entry::snapshot).toList();
    }

    public Set<UUID> managers() {
        return Set.copyOf(managerMailboxes.keySet());
    }

    public boolean publish(UUID manager, FrameBinding frame, UUID inventoryList, long gameTime) {
        MailboxKey key = mailboxOf(manager);
        Entry entry = key == null ? null : entries.get(key);
        if (entry == null || frame == null || !frame.equals(entry.frame)
                || inventoryList == null || !entry.managers.contains(manager)) return false;
        long safeTime = Math.max(0L, gameTime);
        if (inventoryList.equals(entry.inventoryList)
                && safeTime == entry.publishedGameTime
                && manager.equals(entry.refreshedBy)) return false;
        entry.inventoryList = inventoryList;
        entry.publishedGameTime = safeTime;
        entry.refreshedBy = manager;
        entry.generation++;
        setDirty();
        return true;
    }

    public boolean invalidateManager(UUID manager) {
        MailboxKey key = mailboxOf(manager);
        Entry entry = key == null ? null : entries.get(key);
        if (entry == null || !entry.managers.contains(manager)) return false;
        if (entry.managers.size() > 1) return false;
        if (entry.inventoryList == null) return false;
        entry.inventoryList = null;
        entry.publishedGameTime = -1L;
        entry.refreshedBy = null;
        entry.generation++;
        setDirty();
        return true;
    }

    public boolean invalidateWarehouse(MailboxKey key) {
        Entry entry = key == null ? null : entries.get(key);
        if (entry == null || entry.inventoryList == null) return false;
        entry.inventoryList = null;
        entry.publishedGameTime = -1L;
        entry.refreshedBy = null;
        entry.generation++;
        setDirty();
        return true;
    }

    public Collection<MailboxKey> keysForManagers(Collection<UUID> managers) {
        if (managers == null || managers.isEmpty()) return List.of();
        LinkedHashSet<MailboxKey> keys = new LinkedHashSet<>();
        for (UUID manager : managers) {
            MailboxKey key = managerMailboxes.get(manager);
            if (key != null) keys.add(key);
        }
        return List.copyOf(keys);
    }

    @Override
    public CompoundTag save(CompoundTag root) {
        ListTag list = new ListTag();
        for (Entry entry : entries.values()) {
            CompoundTag tag = entry.key.toTag();
            ListTag managers = new ListTag();
            for (UUID manager : entry.managers) {
                CompoundTag managerTag = new CompoundTag();
                managerTag.putUUID("id", manager);
                managers.add(managerTag);
            }
            tag.put("managers", managers);
            if (entry.frame != null) tag.put("frame", entry.frame.toTag());
            if (entry.inventoryList != null) tag.putUUID("inventoryList", entry.inventoryList);
            if (entry.publishedGameTime >= 0L) {
                tag.putLong("publishedGameTime", entry.publishedGameTime);
            }
            if (entry.refreshedBy != null) tag.putUUID("refreshedBy", entry.refreshedBy);
            tag.putLong("generation", Math.max(0L, entry.generation));
            list.add(tag);
        }
        root.put("warehouses", list);
        return root;
    }

    public static MailboxWarehouseData load(CompoundTag root) {
        MailboxWarehouseData data = new MailboxWarehouseData();
        ListTag list = root.getList("warehouses", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag tag = list.getCompound(i);
            MailboxKey key = MailboxKey.fromTag(tag);
            if (key == null || !key.valid()) continue;
            Entry entry = new Entry(key);
            if (tag.contains("frame", Tag.TAG_COMPOUND)) {
                entry.frame = FrameBinding.fromTag(tag.getCompound("frame"));
            }
            ListTag managers = tag.getList("managers", Tag.TAG_COMPOUND);
            for (int j = 0; j < managers.size(); j++) {
                CompoundTag managerTag = managers.getCompound(j);
                if (!managerTag.hasUUID("id")) continue;
                UUID manager = managerTag.getUUID("id");
                if (data.managerMailboxes.putIfAbsent(manager, key) == null) {
                    entry.managers.add(manager);
                }
            }
            entry.inventoryList = tag.hasUUID("inventoryList")
                    ? tag.getUUID("inventoryList") : null;
            entry.publishedGameTime = tag.contains("publishedGameTime", Tag.TAG_LONG)
                    ? Math.max(0L, tag.getLong("publishedGameTime")) : -1L;
            entry.refreshedBy = tag.hasUUID("refreshedBy") ? tag.getUUID("refreshedBy") : null;
            entry.generation = tag.contains("generation", Tag.TAG_LONG)
                    ? Math.max(0L, tag.getLong("generation")) : 0L;
            data.entries.put(key, entry);
        }
        return data;
    }
}
