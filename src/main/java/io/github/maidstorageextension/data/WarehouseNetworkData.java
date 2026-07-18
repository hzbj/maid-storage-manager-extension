package io.github.maidstorageextension.data;

import com.github.tartaricacid.touhoulittlemaid.api.entity.data.TaskDataKey;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import io.github.maidstorageextension.MaidStorageManagerExtension;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/** Last inventory list published by one warehouse maid, retained while its item frame is unloaded. */
public final class WarehouseNetworkData implements TaskDataKey<WarehouseNetworkData.Data> {
    public static final ResourceLocation LOCATION = MaidStorageManagerExtension.id("warehouse_network");
    public static TaskDataKey<Data> KEY;

    public static final class Data {
        private UUID inventoryList;
        private long publishedGameTime = -1L;

        public UUID inventoryList() {
            return inventoryList;
        }

        public long publishedGameTime() {
            return publishedGameTime;
        }

        public boolean publish(UUID list, long gameTime) {
            if (list == null) return false;
            long safeTime = Math.max(0L, gameTime);
            if (list.equals(inventoryList) && safeTime == publishedGameTime) return false;
            inventoryList = list;
            publishedGameTime = safeTime;
            return true;
        }

        public void clear() {
            inventoryList = null;
            publishedGameTime = -1L;
        }
    }

    @Override
    public ResourceLocation getKey() {
        return LOCATION;
    }

    @Override
    public CompoundTag writeSaveData(Data data) {
        CompoundTag tag = new CompoundTag();
        if (data.inventoryList != null) tag.putUUID("inventoryList", data.inventoryList);
        if (data.publishedGameTime >= 0L) tag.putLong("publishedGameTime", data.publishedGameTime);
        return tag;
    }

    @Override
    public Data readSaveData(CompoundTag tag) {
        Data data = new Data();
        data.inventoryList = tag.hasUUID("inventoryList") ? tag.getUUID("inventoryList") : null;
        data.publishedGameTime = tag.contains("publishedGameTime", Tag.TAG_LONG)
                ? Math.max(0L, tag.getLong("publishedGameTime")) : -1L;
        return data;
    }

    public static Data get(EntityMaid maid) {
        return maid.getOrCreateData(KEY, new Data());
    }
}
