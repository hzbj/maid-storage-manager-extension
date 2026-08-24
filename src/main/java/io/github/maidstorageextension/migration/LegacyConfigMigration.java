package io.github.maidstorageextension.migration;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import io.github.maidstorageextension.data.ExtensionConfigData;
import io.github.maidstorageextension.data.PeriodicScanInterval;
import net.minecraft.nbt.CompoundTag;
import studio.fantasyit.maid_storage_manager.maid.data.StorageManagerConfigData;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

public final class LegacyConfigMigration {
    private static final Map<Object, Optional<PeriodicScanInterval>> CAPTURED =
            Collections.synchronizedMap(new WeakHashMap<>());

    private LegacyConfigMigration() {
    }

    public static void capture(Object baseData, CompoundTag source) {
        Optional<PeriodicScanInterval> value = source.contains("periodicScanInterval")
                ? Optional.of(PeriodicScanInterval.fromLegacyName(source.getString("periodicScanInterval")))
                : Optional.empty();
        CAPTURED.put(baseData, value);
    }

    public static void migrateIfNeeded(EntityMaid maid) {
        ExtensionConfigData.Data extension = ExtensionConfigData.get(maid);
        if (extension.legacyMigrationComplete()) {
            return;
        }
        Object baseData = StorageManagerConfigData.get(maid);
        boolean loadedLegacyData = CAPTURED.containsKey(baseData);
        Optional<PeriodicScanInterval> captured = CAPTURED.getOrDefault(
                baseData, Optional.empty());
        extension.periodicScanInterval(migratedInterval(loadedLegacyData, captured,
                extension.periodicScanInterval()));
        extension.legacyMigrationComplete(true);
        maid.setAndSyncData(ExtensionConfigData.KEY, extension);
        CAPTURED.remove(baseData);
    }

    static PeriodicScanInterval migratedInterval(boolean loadedLegacyData,
                                                 Optional<PeriodicScanInterval> captured,
                                                 PeriodicScanInterval newDefault) {
        // A captured empty value means an old save omitted the field; no capture means a new maid.
        return loadedLegacyData ? captured.orElse(PeriodicScanInterval.DISABLED) : newDefault;
    }
}
