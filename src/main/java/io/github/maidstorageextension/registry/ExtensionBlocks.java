package io.github.maidstorageextension.registry;

import net.minecraft.core.registries.Registries;
import io.github.maidstorageextension.MaidStorageManagerExtension;
import io.github.maidstorageextension.block.TaskBellBlock;
import io.github.maidstorageextension.block.CourierWarehouseStationBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

public final class ExtensionBlocks {
    public static final DeferredRegister<Block> REGISTER =
            DeferredRegister.create(Registries.BLOCK, MaidStorageManagerExtension.MOD_ID);
    public static final DeferredHolder<Block, Block> TASK_BELL = REGISTER.register("task_bell", TaskBellBlock::new);
    public static final DeferredHolder<Block, Block> COURIER_WAREHOUSE_STATION = REGISTER.register(
            "courier_warehouse_station", CourierWarehouseStationBlock::new);

    private ExtensionBlocks() {
    }

    public static void register(IEventBus bus) {
        REGISTER.addAlias(ResourceLocation.fromNamespaceAndPath("maid_storage_manager", "task_bell"),
                MaidStorageManagerExtension.id("task_bell"));
        REGISTER.register(bus);
    }
}
