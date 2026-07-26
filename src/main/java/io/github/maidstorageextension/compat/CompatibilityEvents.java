package io.github.maidstorageextension.compat;

import io.github.maidstorageextension.MaidStorageManagerExtension;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

@EventBusSubscriber(modid = MaidStorageManagerExtension.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class CompatibilityEvents {
    private CompatibilityEvents() {
    }

    @SubscribeEvent
    public static void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(CompatibilityGuard::rejectLegacyRegistryOwners);
    }
}
