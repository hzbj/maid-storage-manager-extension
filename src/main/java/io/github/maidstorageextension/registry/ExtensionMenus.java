package io.github.maidstorageextension.registry;

import net.minecraft.core.registries.Registries;
import io.github.maidstorageextension.MaidStorageManagerExtension;
import io.github.maidstorageextension.maid.courier.CourierConfigMenu;
import io.github.maidstorageextension.logistics.LogisticsTrackerMenu;
import io.github.maidstorageextension.license.BusinessLicenseMenu;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

public final class ExtensionMenus {
    private static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, MaidStorageManagerExtension.MOD_ID);

    public static final DeferredHolder<MenuType<?>, MenuType<CourierConfigMenu>> COURIER_CONFIG = MENUS.register(
            "courier_config", () -> IMenuTypeExtension.create(CourierConfigMenu::fromNetwork));
    public static final DeferredHolder<MenuType<?>, MenuType<LogisticsTrackerMenu>> LOGISTICS_TRACKER = MENUS.register(
            "logistics_tracker", () -> IMenuTypeExtension.create(LogisticsTrackerMenu::fromNetwork));
    public static final DeferredHolder<MenuType<?>, MenuType<BusinessLicenseMenu>> BUSINESS_LICENSE = MENUS.register(
            "business_license", () -> IMenuTypeExtension.create(BusinessLicenseMenu::fromNetwork));

    private ExtensionMenus() {
    }

    public static void register(IEventBus bus) {
        MENUS.register(bus);
    }
}
