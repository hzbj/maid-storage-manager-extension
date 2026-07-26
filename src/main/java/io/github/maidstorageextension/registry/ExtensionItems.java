package io.github.maidstorageextension.registry;

import net.minecraft.core.registries.Registries;
import io.github.maidstorageextension.MaidStorageManagerExtension;
import io.github.maidstorageextension.item.InventoryMaintenanceDevice;
import io.github.maidstorageextension.item.LogisticsTrackerItem;
import io.github.maidstorageextension.item.TaskBellItem;
import io.github.maidstorageextension.item.CourierWarehouseMailboxItem;
import net.minecraft.world.item.Item;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;
import studio.fantasyit.maid_storage_manager.items.HangUpItem;

public final class ExtensionItems {
    public static final DeferredRegister<Item> REGISTER =
            DeferredRegister.create(Registries.ITEM, MaidStorageManagerExtension.MOD_ID);

    public static final DeferredHolder<Item, Item> MISC_STORAGE =
            REGISTER.register("misc_storage", () -> new HangUpItem());
    public static final DeferredHolder<Item, Item> INVENTORY_MAINTENANCE_DEVICE =
            REGISTER.register("inventory_maintenance_device", InventoryMaintenanceDevice::new);
    public static final DeferredHolder<Item, Item> LOGISTICS_TRACKER =
            REGISTER.register("logistics_tracker", LogisticsTrackerItem::new);
    public static final DeferredHolder<Item, Item> TASK_BELL =
            REGISTER.register("task_bell", () -> new TaskBellItem(ExtensionBlocks.TASK_BELL.get(), new Item.Properties()));
    public static final DeferredHolder<Item, Item> COURIER_WAREHOUSE_STATION = REGISTER.register(
            "courier_warehouse_station", () -> new CourierWarehouseMailboxItem(
                    ExtensionBlocks.COURIER_WAREHOUSE_STATION.get(), new Item.Properties()));

    private ExtensionItems() {
    }

    public static void register(IEventBus bus) {
        REGISTER.addAlias(legacy("misc_storage"), MaidStorageManagerExtension.id("misc_storage"));
        REGISTER.addAlias(legacy("inventory_maintenance_device"),
                MaidStorageManagerExtension.id("inventory_maintenance_device"));
        REGISTER.addAlias(legacy("task_bell"), MaidStorageManagerExtension.id("task_bell"));
        REGISTER.register(bus);
    }

    private static ResourceLocation legacy(String path) {
        return ResourceLocation.fromNamespaceAndPath("maid_storage_manager", path);
    }
}
