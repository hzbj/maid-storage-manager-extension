package io.github.maidstorageextension.data;

import com.github.tartaricacid.touhoulittlemaid.api.entity.data.TaskDataKey;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import io.github.maidstorageextension.MaidStorageManagerExtension;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/** Cross-owner authorization lives on the warehouse maid, never only on the courier. */
public final class WarehouseCourierData implements TaskDataKey<WarehouseCourierData.Data> {
    public static final ResourceLocation LOCATION = MaidStorageManagerExtension.id("warehouse_couriers");
    public static TaskDataKey<Data> KEY;

    public static final class Data {
        private final Set<UUID> authorized = new LinkedHashSet<>();
        private final Set<UUID> pending = new LinkedHashSet<>();

        public Set<UUID> authorized() { return authorized; }
        public Set<UUID> pending() { return pending; }
        public boolean isAuthorized(UUID courier) { return courier != null && authorized.contains(courier); }
        public void request(UUID courier) { if (courier != null && !authorized.contains(courier)) pending.add(courier); }
        public boolean approve(UUID courier) {
            if (courier == null || !pending.remove(courier)) return false;
            return authorized.add(courier);
        }
        public void reject(UUID courier) { if (courier != null) pending.remove(courier); }
        public void revoke(UUID courier) {
            if (courier != null) {
                pending.remove(courier);
                authorized.remove(courier);
            }
        }
    }

    @Override
    public ResourceLocation getKey() { return LOCATION; }

    @Override
    public CompoundTag writeSaveData(Data data) {
        CompoundTag tag = new CompoundTag();
        tag.put("authorized", write(data.authorized));
        tag.put("pending", write(data.pending));
        return tag;
    }

    @Override
    public Data readSaveData(CompoundTag tag) {
        Data data = new Data();
        read(tag.getList("authorized", Tag.TAG_STRING), data.authorized);
        read(tag.getList("pending", Tag.TAG_STRING), data.pending);
        return data;
    }

    private static ListTag write(Set<UUID> values) {
        ListTag list = new ListTag();
        values.forEach(value -> list.add(StringTag.valueOf(value.toString())));
        return list;
    }

    private static void read(ListTag list, Set<UUID> output) {
        for (int i = 0; i < list.size(); i++) {
            try { output.add(UUID.fromString(list.getString(i))); }
            catch (IllegalArgumentException ignored) { }
        }
    }

    public static Data get(EntityMaid maid) {
        return maid.getOrCreateData(KEY, new Data());
    }
}
