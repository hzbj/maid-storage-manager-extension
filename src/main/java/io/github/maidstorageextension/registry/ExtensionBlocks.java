package io.github.maidstorageextension.registry;

import io.github.maidstorageextension.MaidStorageManagerExtension;
import io.github.maidstorageextension.block.TaskBellBlock;
import io.github.maidstorageextension.block.CourierWarehouseStationBlock;
import io.github.maidstorageextension.block.BusinessLicenseBlock;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ExtensionBlocks {
    public static final DeferredRegister<Block> REGISTER =
            DeferredRegister.create(ForgeRegistries.BLOCKS, MaidStorageManagerExtension.MOD_ID);
    public static final RegistryObject<Block> TASK_BELL = REGISTER.register("task_bell", TaskBellBlock::new);
    public static final RegistryObject<Block> COURIER_WAREHOUSE_STATION = REGISTER.register(
            "courier_warehouse_station", CourierWarehouseStationBlock::new);
    public static final RegistryObject<Block> BUSINESS_LICENSE = REGISTER.register(
            "business_license", BusinessLicenseBlock::new);

    private ExtensionBlocks() {
    }

    public static void register(IEventBus bus) {
        REGISTER.register(bus);
    }
}
