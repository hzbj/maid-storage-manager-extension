package io.github.maidstorageextension.registry;

import io.github.maidstorageextension.MaidStorageManagerExtension;
import io.github.maidstorageextension.item.InventoryMaintenanceDevice;
import io.github.maidstorageextension.item.LogisticsTrackerItem;
import io.github.maidstorageextension.item.TaskBellItem;
import io.github.maidstorageextension.item.CourierWarehouseMailboxItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.BlockItem;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import studio.fantasyit.maid_storage_manager.items.HangUpItem;

public final class ExtensionItems {
    public static final DeferredRegister<Item> REGISTER =
            DeferredRegister.create(ForgeRegistries.ITEMS, MaidStorageManagerExtension.MOD_ID);

    public static final RegistryObject<Item> MISC_STORAGE =
            REGISTER.register("misc_storage", HangUpItem::new);
    public static final RegistryObject<Item> INVENTORY_MAINTENANCE_DEVICE =
            REGISTER.register("inventory_maintenance_device", InventoryMaintenanceDevice::new);
    public static final RegistryObject<Item> LOGISTICS_TRACKER =
            REGISTER.register("logistics_tracker", LogisticsTrackerItem::new);
    public static final RegistryObject<Item> TASK_BELL =
            REGISTER.register("task_bell", () -> new TaskBellItem(ExtensionBlocks.TASK_BELL.get(), new Item.Properties()));
    public static final RegistryObject<Item> COURIER_WAREHOUSE_STATION = REGISTER.register(
            "courier_warehouse_station", () -> new CourierWarehouseMailboxItem(
                    ExtensionBlocks.COURIER_WAREHOUSE_STATION.get(), new Item.Properties()));

    private ExtensionItems() {
    }

    public static void register(IEventBus bus) {
        REGISTER.register(bus);
    }
}
