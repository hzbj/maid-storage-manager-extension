package io.github.maidstorageextension.remote;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Last authoritative world location for registered maids, used before their chunks are loaded. */
public final class RemoteMaidIndexData extends SavedData {
    public static final String DATA_NAME = "maid_storage_manager_extension_remote_maids";

    public record Entry(UUID maid, ResourceLocation dimension, BlockPos position,
                        String name, ResourceLocation task) {
        public Entry {
            position = position == null ? null : position.immutable();
            name = name == null ? "" : name;
        }

        public boolean valid() {
            return maid != null && dimension != null && position != null;
        }
    }

    private final Map<UUID, Entry> entries = new LinkedHashMap<>();

    public static RemoteMaidIndexData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                RemoteMaidIndexData::load, RemoteMaidIndexData::new, DATA_NAME);
    }

    public Entry get(UUID maid) {
        return maid == null ? null : entries.get(maid);
    }

    public List<Entry> entries() {
        return List.copyOf(entries.values());
    }

    public boolean observe(EntityMaid maid) {
        if (maid == null || !maid.isAlive()) return false;
        Entry next = new Entry(maid.getUUID(), maid.level().dimension().location(),
                maid.blockPosition(), maid.getName().getString(), maid.getTask().getUid());
        Entry previous = entries.put(maid.getUUID(), next);
        if (next.equals(previous)) return false;
        setDirty();
        return true;
    }

    public boolean remove(UUID maid) {
        if (maid == null || entries.remove(maid) == null) return false;
        setDirty();
        return true;
    }

    @Override
    public CompoundTag save(CompoundTag root) {
        ListTag list = new ListTag();
        for (Entry value : entries.values()) {
            if (!value.valid()) continue;
            CompoundTag tag = new CompoundTag();
            tag.putUUID("maid", value.maid());
            tag.putString("dimension", value.dimension().toString());
            tag.putLong("position", value.position().asLong());
            if (!value.name().isBlank()) tag.putString("name", value.name());
            if (value.task() != null) tag.putString("task", value.task().toString());
            list.add(tag);
        }
        root.put("maids", list);
        return root;
    }

    public static RemoteMaidIndexData load(CompoundTag root) {
        RemoteMaidIndexData data = new RemoteMaidIndexData();
        ListTag list = root.getList("maids", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag tag = list.getCompound(i);
            if (!tag.hasUUID("maid") || !tag.contains("position", Tag.TAG_LONG)) continue;
            ResourceLocation dimension = ResourceLocation.tryParse(tag.getString("dimension"));
            ResourceLocation task = ResourceLocation.tryParse(tag.getString("task"));
            Entry entry = new Entry(tag.getUUID("maid"), dimension,
                    BlockPos.of(tag.getLong("position")), tag.getString("name"), task);
            if (entry.valid()) data.entries.put(entry.maid(), entry);
        }
        return data;
    }
}
