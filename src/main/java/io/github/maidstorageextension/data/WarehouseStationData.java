package io.github.maidstorageextension.data;

import com.github.tartaricacid.touhoulittlemaid.api.entity.data.TaskDataKey;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import io.github.maidstorageextension.MaidStorageManagerExtension;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Pending mailbox approvals owned by a warehouse maid. */
public final class WarehouseStationData implements TaskDataKey<WarehouseStationData.Data> {
    public static final ResourceLocation LOCATION = MaidStorageManagerExtension.id("warehouse_stations");
    public static TaskDataKey<Data> KEY;

    public record StationKey(ResourceLocation dimension, BlockPos mailboxPos) {
        public StationKey {
            mailboxPos = mailboxPos == null ? null : mailboxPos.immutable();
        }

        public boolean valid() {
            return dimension != null && mailboxPos != null;
        }
    }

    public record StationRequest(StationKey key, String placerName) {
        public StationRequest {
            placerName = placerName == null ? "" : placerName;
        }
    }

    public static final class Data {
        private final Map<StationKey, StationRequest> pending = new LinkedHashMap<>();

        public List<StationRequest> pending() {
            return List.copyOf(pending.values());
        }

        public void request(StationRequest request) {
            if (request != null && request.key() != null && request.key().valid()) {
                pending.put(request.key(), request);
            }
        }

        public boolean contains(StationKey key) {
            return key != null && pending.containsKey(key);
        }

        public boolean remove(StationKey key) {
            return key != null && pending.remove(key) != null;
        }
    }

    @Override
    public ResourceLocation getKey() {
        return LOCATION;
    }

    @Override
    public CompoundTag writeSaveData(Data data) {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        for (StationRequest request : data.pending()) {
            CompoundTag entry = new CompoundTag();
            entry.putString("dimension", request.key().dimension().toString());
            entry.putLong("mailboxPos", request.key().mailboxPos().asLong());
            if (!request.placerName().isBlank()) entry.putString("placerName", request.placerName());
            list.add(entry);
        }
        tag.put("pending", list);
        return tag;
    }

    @Override
    public Data readSaveData(CompoundTag tag) {
        Data data = new Data();
        ListTag list = tag.getList("pending", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            ResourceLocation dimension = ResourceLocation.tryParse(entry.getString("dimension"));
            if (dimension == null || !entry.contains("mailboxPos", Tag.TAG_LONG)) continue;
            StationKey key = new StationKey(dimension, BlockPos.of(entry.getLong("mailboxPos")));
            data.request(new StationRequest(key, entry.getString("placerName")));
        }
        return data;
    }

    public static Data get(EntityMaid maid) {
        return maid.getOrCreateData(KEY, new Data());
    }
}
