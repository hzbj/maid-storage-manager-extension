package io.github.maidstorageextension.registry;

import io.github.maidstorageextension.MaidStorageManagerExtension;
import io.github.maidstorageextension.maid.courier.CourierConfigMenu;
import io.github.maidstorageextension.logistics.LogisticsTrackerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ExtensionMenus {
    private static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, MaidStorageManagerExtension.MOD_ID);

    public static final RegistryObject<MenuType<CourierConfigMenu>> COURIER_CONFIG = MENUS.register(
            "courier_config", () -> IForgeMenuType.create(CourierConfigMenu::fromNetwork));
    public static final RegistryObject<MenuType<LogisticsTrackerMenu>> LOGISTICS_TRACKER = MENUS.register(
            "logistics_tracker", () -> IForgeMenuType.create(LogisticsTrackerMenu::fromNetwork));

    private ExtensionMenus() {
    }

    public static void register(IEventBus bus) {
        MENUS.register(bus);
    }
}
