package io.github.maidstorageextension.registry;

import net.minecraft.core.registries.Registries;
import io.github.maidstorageextension.MaidStorageManagerExtension;
import io.github.maidstorageextension.block.TaskBellBlockEntity;
import io.github.maidstorageextension.block.CourierWarehouseStationBlockEntity;
import io.github.maidstorageextension.block.BusinessLicenseBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

public final class ExtensionBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> REGISTER =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MaidStorageManagerExtension.MOD_ID);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TaskBellBlockEntity>> TASK_BELL =
            REGISTER.register("task_bell", () -> BlockEntityType.Builder
                    .of(TaskBellBlockEntity::new, ExtensionBlocks.TASK_BELL.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CourierWarehouseStationBlockEntity>>
            COURIER_WAREHOUSE_STATION = REGISTER.register("courier_warehouse_station",
            () -> BlockEntityType.Builder.of(CourierWarehouseStationBlockEntity::new,
                    ExtensionBlocks.COURIER_WAREHOUSE_STATION.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BusinessLicenseBlockEntity>> BUSINESS_LICENSE =
            REGISTER.register("business_license", () -> BlockEntityType.Builder
                    .of(BusinessLicenseBlockEntity::new, ExtensionBlocks.BUSINESS_LICENSE.get()).build(null));

    private ExtensionBlockEntities() {
    }

    public static void register(IEventBus bus) {
        REGISTER.addAlias(ResourceLocation.fromNamespaceAndPath("maid_storage_manager", "task_bell"),
                MaidStorageManagerExtension.id("task_bell"));
        REGISTER.register(bus);
    }
}
