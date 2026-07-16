package io.github.maidstorageextension.data;

import com.github.tartaricacid.touhoulittlemaid.api.entity.data.TaskDataKey;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import io.github.maidstorageextension.MaidStorageManagerExtension;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import java.util.Locale;

/** Server-authored, persisted maintenance state shown on the maid's extension config page. */
public final class MaintenanceStatusData implements TaskDataKey<MaintenanceStatusData.Data> {
    public static final ResourceLocation LOCATION = MaidStorageManagerExtension.id("maintenance_status");
    public static TaskDataKey<Data> KEY;

    public enum Phase {
        IDLE,
        DISCOVERING,
        VIEWING,
        CLEANING,
        REFRESHING;

        public String translationKey() {
            return "gui.maid_storage_manager_extension.status.phase." + name().toLowerCase(Locale.ROOT);
        }
    }

    public enum Result {
        NEVER,
        SUCCESS,
        UNBOUND,
        INVALID_FRAME,
        INVALID_CONTENT,
        OUT_OF_SCOPE,
        NO_SAFE_POSITION,
        PATH_TIMEOUT,
        FRAME_BUSY,
        PUBLISH_FAILED;

        public String translationKey() {
            return "gui.maid_storage_manager_extension.status.result." + name().toLowerCase(Locale.ROOT);
        }

        public static Result safeValueOf(String value) {
            try {
                return value == null || value.isBlank() ? NEVER : valueOf(value);
            } catch (IllegalArgumentException ignored) {
                return NEVER;
            }
        }
    }

    public static final class Data {
        private Phase phase;
        private Result lastResult;
        private long lastCompletedEpochMillis;
        private int scannedStorages;
        private int publishedItemTypes;

        public Data(Phase phase, Result lastResult, long lastCompletedEpochMillis,
                    int scannedStorages, int publishedItemTypes) {
            this.phase = phase == null ? Phase.IDLE : phase;
            this.lastResult = lastResult == null ? Result.NEVER : lastResult;
            this.lastCompletedEpochMillis = Math.max(0L, lastCompletedEpochMillis);
            this.scannedStorages = Math.max(0, scannedStorages);
            this.publishedItemTypes = Math.max(0, publishedItemTypes);
        }

        public static Data defaults() {
            return new Data(Phase.IDLE, Result.NEVER, 0L, 0, 0);
        }

        public Phase phase() {
            return phase;
        }

        public Result lastResult() {
            return lastResult;
        }

        public long lastCompletedEpochMillis() {
            return lastCompletedEpochMillis;
        }

        public int scannedStorages() {
            return scannedStorages;
        }

        public int publishedItemTypes() {
            return publishedItemTypes;
        }

        public void phase(Phase value) {
            phase = value == null ? Phase.IDLE : value;
        }

        public void complete(Result result, int storages, int itemTypes, long epochMillis) {
            phase = Phase.IDLE;
            lastResult = result == null ? Result.PUBLISH_FAILED : result;
            lastCompletedEpochMillis = Math.max(0L, epochMillis);
            scannedStorages = Math.max(0, storages);
            publishedItemTypes = Math.max(0, itemTypes);
        }
    }

    @Override
    public ResourceLocation getKey() {
        return LOCATION;
    }

    @Override
    public CompoundTag writeSaveData(Data data) {
        CompoundTag tag = new CompoundTag();
        tag.putString("phase", data.phase().name());
        tag.putString("lastResult", data.lastResult().name());
        tag.putLong("lastCompletedEpochMillis", data.lastCompletedEpochMillis());
        tag.putInt("scannedStorages", data.scannedStorages());
        tag.putInt("publishedItemTypes", data.publishedItemTypes());
        return tag;
    }

    @Override
    public Data readSaveData(CompoundTag tag) {
        Phase phase;
        try {
            phase = tag.contains("phase") ? Phase.valueOf(tag.getString("phase")) : Phase.IDLE;
        } catch (IllegalArgumentException ignored) {
            phase = Phase.IDLE;
        }
        return new Data(
                phase,
                Result.safeValueOf(tag.getString("lastResult")),
                tag.getLong("lastCompletedEpochMillis"),
                tag.getInt("scannedStorages"),
                tag.getInt("publishedItemTypes"));
    }

    public static Data get(EntityMaid maid) {
        return maid.getOrCreateData(KEY, Data.defaults());
    }
}
