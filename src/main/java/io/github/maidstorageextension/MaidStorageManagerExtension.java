package io.github.maidstorageextension;

import io.github.maidstorageextension.network.ExtensionNetwork;
import io.github.maidstorageextension.compat.CompatibilityGuard;
import io.github.maidstorageextension.registry.ExtensionBlockEntities;
import io.github.maidstorageextension.registry.ExtensionBlocks;
import io.github.maidstorageextension.registry.ExtensionCreativeTabs;
import io.github.maidstorageextension.registry.ExtensionItems;
import io.github.maidstorageextension.registry.ExtensionMemoryModules;
import io.github.maidstorageextension.registry.ExtensionMenus;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(MaidStorageManagerExtension.MOD_ID)
public final class MaidStorageManagerExtension {
    public static final String MOD_ID = "maid_storage_manager_extension";

    public MaidStorageManagerExtension() {
        CompatibilityGuard.rejectPatchedBaseJar();
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ExtensionBlocks.register(modBus);
        ExtensionBlockEntities.register(modBus);
        ExtensionItems.register(modBus);
        ExtensionMemoryModules.register(modBus);
        ExtensionMenus.register(modBus);
        ExtensionCreativeTabs.register(modBus);
        ExtensionNetwork.register();
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MOD_ID, path);
    }
}
