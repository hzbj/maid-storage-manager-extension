package io.github.maidstorageextension.compat;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;

public final class CompatibilityGuard {
    private static final String LEGACY_PATCH_MARKER =
            "studio.fantasyit.maid_storage_manager.maid.memory.PeriodicScanMemory";

    private CompatibilityGuard() {
    }

    public static void rejectPatchedBaseJar() {
        try {
            Class.forName(LEGACY_PATCH_MARKER, false, CompatibilityGuard.class.getClassLoader());
        } catch (ClassNotFoundException expectedForOfficialJar) {
            return;
        }
        throw new IllegalStateException(
                "检测到旧魔改版女仆仓管。请备份世界后更换为官方 Maid Storage Manager 1.15.6-1.15.x；"
                        + "旧魔改 JAR 与女仆仓管扩展不能同时运行。");
    }

    public static void rejectLegacyRegistryOwners() {
        rejectCanonicalOldItem("misc_storage");
        rejectCanonicalOldItem("inventory_maintenance_device");
        rejectCanonicalOldItem("task_bell");
    }

    private static void rejectCanonicalOldItem(String path) {
        ResourceLocation oldId = new ResourceLocation("maid_storage_manager", path);
        var item = ForgeRegistries.ITEMS.getValue(oldId);
        if (item != null && oldId.equals(ForgeRegistries.ITEMS.getKey(item))) {
            throw new IllegalStateException(
                    "检测到旧魔改版女仆仓管直接注册了 " + oldId
                            + "。请更换为官方 1.15.x JAR 后再安装女仆仓管扩展。");
        }
    }
}
