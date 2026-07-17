package io.github.maidstorageextension.client;

import io.github.maidstorageextension.MaidStorageManagerExtension;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraft.client.gui.screens.MenuScreens;
import io.github.maidstorageextension.registry.ExtensionMenus;

@Mod.EventBusSubscriber(modid = MaidStorageManagerExtension.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientCompatibilityEvents {
    private ClientCompatibilityEvents() {
    }

    @SubscribeEvent
    public static void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(ConfigScreenBridge::validate);
        event.enqueueWork(() -> MenuScreens.register(
                ExtensionMenus.COURIER_CONFIG.get(), CourierConfigScreen::new));
        event.enqueueWork(() -> MenuScreens.register(
                ExtensionMenus.LOGISTICS_TRACKER.get(), LogisticsTrackerScreen::new));
    }
}
