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
        extension.periodicScanInterval(CAPTURED.getOrDefault(baseData, Optional.empty())
                .orElse(PeriodicScanInterval.DISABLED));
        extension.legacyMigrationComplete(true);
        maid.setAndSyncData(ExtensionConfigData.KEY, extension);
        CAPTURED.remove(baseData);
    }
}
