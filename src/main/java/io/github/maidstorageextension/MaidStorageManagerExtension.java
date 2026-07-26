package io.github.maidstorageextension;

import io.github.maidstorageextension.compat.CompatibilityGuard;
import io.github.maidstorageextension.registry.ExtensionBlockEntities;
import io.github.maidstorageextension.registry.ExtensionBlocks;
import io.github.maidstorageextension.registry.ExtensionCreativeTabs;
import io.github.maidstorageextension.registry.ExtensionItems;
import io.github.maidstorageextension.registry.ExtensionMemoryModules;
import io.github.maidstorageextension.registry.ExtensionMenus;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

@Mod(MaidStorageManagerExtension.MOD_ID)
public final class MaidStorageManagerExtension {
    public static final String MOD_ID = "maid_storage_manager_extension";

    public MaidStorageManagerExtension(IEventBus modBus) {
        CompatibilityGuard.rejectPatchedBaseJar();
        ExtensionBlocks.register(modBus);
        ExtensionBlockEntities.register(modBus);
        ExtensionItems.register(modBus);
        ExtensionMemoryModules.register(modBus);
        ExtensionMenus.register(modBus);
        ExtensionCreativeTabs.register(modBus);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
