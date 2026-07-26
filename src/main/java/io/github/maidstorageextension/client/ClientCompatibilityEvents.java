package io.github.maidstorageextension.client;

import io.github.maidstorageextension.MaidStorageManagerExtension;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import io.github.maidstorageextension.registry.ExtensionMenus;

@EventBusSubscriber(modid = MaidStorageManagerExtension.MOD_ID,
        bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientCompatibilityEvents {
    private ClientCompatibilityEvents() {
    }

    @SubscribeEvent
    public static void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(ConfigScreenBridge::validate);
    }

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ExtensionMenus.COURIER_CONFIG.get(), CourierConfigScreen::new);
        event.register(ExtensionMenus.LOGISTICS_TRACKER.get(), LogisticsTrackerScreen::new);
    }
}
