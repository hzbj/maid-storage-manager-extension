package io.github.maidstorageextension.data;

import com.github.tartaricacid.touhoulittlemaid.api.entity.data.TaskDataKey;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import io.github.maidstorageextension.MaidStorageManagerExtension;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

public final class ExtensionConfigData implements TaskDataKey<ExtensionConfigData.Data> {
    public static final ResourceLocation LOCATION = MaidStorageManagerExtension.id("config");
    public static TaskDataKey<Data> KEY;

    public static final int DEFAULT_LOCAL_SCAN_RADIUS = 8;
    public static final int MIN_LOCAL_SCAN_RADIUS = 4;
    public static final int MAX_LOCAL_SCAN_RADIUS = 32;
    public static final int DEFAULT_TASK_BELL_RANGE = 64;
    public static final int MIN_TASK_BELL_RANGE = 16;
    public static final int MAX_TASK_BELL_RANGE = 128;
    public static final int DEFAULT_TASK_BELL_TRAVEL_TIMEOUT_SECONDS = 20;
    public static final int MIN_TASK_BELL_TRAVEL_TIMEOUT_SECONDS = 5;
    public static final int MAX_TASK_BELL_TRAVEL_TIMEOUT_SECONDS = 60;
    public static final int DEFAULT_TASK_BELL_STAY_SECONDS = 10;
    public static final int MIN_TASK_BELL_STAY_SECONDS = 0;
    public static final int MAX_TASK_BELL_STAY_SECONDS = 60;

    public static final class Data {
        private PeriodicScanInterval periodicScanInterval;
        private int localScanRadius;
        private int taskBellRange;
        private int taskBellTravelTimeoutSeconds;
        private int taskBellStaySeconds;
        private boolean refreshFrameEffects;
        private boolean refreshOwnerNotification;
        private boolean miscSortMatchNbt;
        private boolean legacyMigrationComplete;

        public Data(PeriodicScanInterval periodicScanInterval, boolean legacyMigrationComplete) {
            this(periodicScanInterval,
                    DEFAULT_LOCAL_SCAN_RADIUS,
                    DEFAULT_TASK_BELL_RANGE,
                    DEFAULT_TASK_BELL_TRAVEL_TIMEOUT_SECONDS,
                    DEFAULT_TASK_BELL_STAY_SECONDS,
                    true,
                    true,
                    false,
                    legacyMigrationComplete);
        }

        public Data(PeriodicScanInterval periodicScanInterval,
                    int localScanRadius,
                    int taskBellRange,
                    int taskBellTravelTimeoutSeconds,
                    int taskBellStaySeconds,
                    boolean refreshFrameEffects,
                    boolean refreshOwnerNotification,
                    boolean legacyMigrationComplete) {
            this(periodicScanInterval,
                    localScanRadius,
                    taskBellRange,
                    taskBellTravelTimeoutSeconds,
                    taskBellStaySeconds,
                    refreshFrameEffects,
                    refreshOwnerNotification,
                    false,
                    legacyMigrationComplete);
        }

        public Data(PeriodicScanInterval periodicScanInterval,
                    int localScanRadius,
                    int taskBellRange,
                    int taskBellTravelTimeoutSeconds,
                    int taskBellStaySeconds,
                    boolean refreshFrameEffects,
                    boolean refreshOwnerNotification,
                    boolean miscSortMatchNbt,
                    boolean legacyMigrationComplete) {
            this.periodicScanInterval = periodicScanInterval;
            this.localScanRadius = clampLocalScanRadius(localScanRadius);
            this.taskBellRange = clampTaskBellRange(taskBellRange);
            this.taskBellTravelTimeoutSeconds = clampTaskBellTravelTimeoutSeconds(taskBellTravelTimeoutSeconds);
            this.taskBellStaySeconds = clampTaskBellStaySeconds(taskBellStaySeconds);
            this.refreshFrameEffects = refreshFrameEffects;
            this.refreshOwnerNotification = refreshOwnerNotification;
            this.miscSortMatchNbt = miscSortMatchNbt;
            this.legacyMigrationComplete = legacyMigrationComplete;
        }

        public static Data defaults() {
            return new Data(PeriodicScanInterval.DISABLED, false);
        }

        public PeriodicScanInterval periodicScanInterval() {
            return periodicScanInterval;
        }

        public void periodicScanInterval(PeriodicScanInterval value) {
            periodicScanInterval = value == null ? PeriodicScanInterval.DISABLED : value;
        }

        public int localScanRadius() {
            return localScanRadius;
        }

        public void localScanRadius(int value) {
            localScanRadius = clampLocalScanRadius(value);
        }

        public int taskBellRange() {
            return taskBellRange;
        }

        public void taskBellRange(int value) {
            taskBellRange = clampTaskBellRange(value);
        }

        public int taskBellTravelTimeoutSeconds() {
            return taskBellTravelTimeoutSeconds;
        }

        public int taskBellTravelTimeoutTicks() {
            return taskBellTravelTimeoutSeconds * 20;
        }

        public void taskBellTravelTimeoutSeconds(int value) {
            taskBellTravelTimeoutSeconds = clampTaskBellTravelTimeoutSeconds(value);
        }

        public int taskBellStaySeconds() {
            return taskBellStaySeconds;
        }

        public int taskBellStayTicks() {
            return taskBellStaySeconds * 20;
        }

        public void taskBellStaySeconds(int value) {
            taskBellStaySeconds = clampTaskBellStaySeconds(value);
        }

        public boolean refreshFrameEffects() {
            return refreshFrameEffects;
        }

        public void refreshFrameEffects(boolean value) {
            refreshFrameEffects = value;
        }

        public boolean refreshOwnerNotification() {
            return refreshOwnerNotification;
        }

        public void refreshOwnerNotification(boolean value) {
            refreshOwnerNotification = value;
        }

        public boolean miscSortMatchNbt() {
            return miscSortMatchNbt;
        }

        public void miscSortMatchNbt(boolean value) {
            miscSortMatchNbt = value;
        }

        public boolean legacyMigrationComplete() {
            return legacyMigrationComplete;
        }

        public void legacyMigrationComplete(boolean value) {
            legacyMigrationComplete = value;
        }
    }

    @Override
    public ResourceLocation getKey() {
        return LOCATION;
    }

    @Override
    public CompoundTag writeSaveData(Data data) {
        CompoundTag tag = new CompoundTag();
        tag.putString("periodicScanInterval", data.periodicScanInterval().name());
        tag.putInt("localScanRadius", data.localScanRadius());
        tag.putInt("taskBellRange", data.taskBellRange());
        tag.putInt("taskBellTravelTimeoutSeconds", data.taskBellTravelTimeoutSeconds());
        tag.putInt("taskBellStaySeconds", data.taskBellStaySeconds());
        tag.putBoolean("refreshFrameEffects", data.refreshFrameEffects());
        tag.putBoolean("refreshOwnerNotification", data.refreshOwnerNotification());
        tag.putBoolean("miscSortMatchNbt", data.miscSortMatchNbt());
        tag.putBoolean("legacyMigrationComplete", data.legacyMigrationComplete());
        return tag;
    }

    @Override
    public Data readSaveData(CompoundTag tag) {
        PeriodicScanInterval interval = tag.contains("periodicScanInterval")
                ? PeriodicScanInterval.fromLegacyName(tag.getString("periodicScanInterval"))
                : PeriodicScanInterval.DISABLED;
        return new Data(
                interval,
                tag.contains("localScanRadius") ? tag.getInt("localScanRadius") : DEFAULT_LOCAL_SCAN_RADIUS,
                tag.contains("taskBellRange") ? tag.getInt("taskBellRange") : DEFAULT_TASK_BELL_RANGE,
                tag.contains("taskBellTravelTimeoutSeconds")
                        ? tag.getInt("taskBellTravelTimeoutSeconds")
                        : DEFAULT_TASK_BELL_TRAVEL_TIMEOUT_SECONDS,
                tag.contains("taskBellStaySeconds")
                        ? tag.getInt("taskBellStaySeconds")
                        : DEFAULT_TASK_BELL_STAY_SECONDS,
                !tag.contains("refreshFrameEffects") || tag.getBoolean("refreshFrameEffects"),
                !tag.contains("refreshOwnerNotification") || tag.getBoolean("refreshOwnerNotification"),
                tag.getBoolean("miscSortMatchNbt"),
                tag.getBoolean("legacyMigrationComplete"));
    }

    public static Data get(EntityMaid maid) {
        return maid.getOrCreateData(KEY, Data.defaults());
    }

    public static int clampLocalScanRadius(int value) {
        return Math.max(MIN_LOCAL_SCAN_RADIUS, Math.min(MAX_LOCAL_SCAN_RADIUS, value));
    }

    public static int clampTaskBellRange(int value) {
        int clamped = Math.max(MIN_TASK_BELL_RANGE, Math.min(MAX_TASK_BELL_RANGE, value));
        return Math.round(clamped / 8.0F) * 8;
    }

    public static int clampTaskBellTravelTimeoutSeconds(int value) {
        return Math.max(MIN_TASK_BELL_TRAVEL_TIMEOUT_SECONDS,
                Math.min(MAX_TASK_BELL_TRAVEL_TIMEOUT_SECONDS, value));
    }

    public static int clampTaskBellStaySeconds(int value) {
        return Math.max(MIN_TASK_BELL_STAY_SECONDS, Math.min(MAX_TASK_BELL_STAY_SECONDS, value));
    }
}
