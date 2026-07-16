package io.github.maidstorageextension.registry;

import io.github.maidstorageextension.MaidStorageManagerExtension;
import io.github.maidstorageextension.block.TaskBellBlockEntity;
import io.github.maidstorageextension.block.CourierWarehouseStationBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ExtensionBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> REGISTER =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, MaidStorageManagerExtension.MOD_ID);
    public static final RegistryObject<BlockEntityType<TaskBellBlockEntity>> TASK_BELL =
            REGISTER.register("task_bell", () -> BlockEntityType.Builder
                    .of(TaskBellBlockEntity::new, ExtensionBlocks.TASK_BELL.get()).build(null));
    public static final RegistryObject<BlockEntityType<CourierWarehouseStationBlockEntity>>
            COURIER_WAREHOUSE_STATION = REGISTER.register("courier_warehouse_station",
            () -> BlockEntityType.Builder.of(CourierWarehouseStationBlockEntity::new,
                    ExtensionBlocks.COURIER_WAREHOUSE_STATION.get()).build(null));

    private ExtensionBlockEntities() {
    }

    public static void register(IEventBus bus) {
        REGISTER.register(bus);
    }
}
