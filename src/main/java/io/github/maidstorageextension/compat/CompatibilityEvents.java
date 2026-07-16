package io.github.maidstorageextension.compat;

import io.github.maidstorageextension.MaidStorageManagerExtension;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

@Mod.EventBusSubscriber(modid = MaidStorageManagerExtension.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class CompatibilityEvents {
    private CompatibilityEvents() {
    }

    @SubscribeEvent
    public static void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(CompatibilityGuard::rejectLegacyRegistryOwners);
    }
}
