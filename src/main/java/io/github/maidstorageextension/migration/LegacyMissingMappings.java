package io.github.maidstorageextension.migration;

import io.github.maidstorageextension.MaidStorageManagerExtension;
import io.github.maidstorageextension.registry.ExtensionBlockEntities;
import io.github.maidstorageextension.registry.ExtensionBlocks;
import io.github.maidstorageextension.registry.ExtensionItems;
import io.github.maidstorageextension.registry.ExtensionMemoryModules;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.MissingMappingsEvent;

@Mod.EventBusSubscriber(modid = MaidStorageManagerExtension.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class LegacyMissingMappings {
    private static final String OLD_NAMESPACE = "maid_storage_manager";

    private LegacyMissingMappings() {
    }

    @SubscribeEvent
    public static void remap(MissingMappingsEvent event) {
        for (MissingMappingsEvent.Mapping<Item> mapping :
                event.getMappings(ForgeRegistries.Keys.ITEMS, OLD_NAMESPACE)) {
            switch (mapping.getKey().getPath()) {
                case "misc_storage" -> mapping.remap(ExtensionItems.MISC_STORAGE.get());
                case "inventory_maintenance_device" ->
                        mapping.remap(ExtensionItems.INVENTORY_MAINTENANCE_DEVICE.get());
                case "task_bell" -> mapping.remap(ExtensionItems.TASK_BELL.get());
                default -> { }
            }
        }
        for (MissingMappingsEvent.Mapping<Block> mapping :
                event.getMappings(ForgeRegistries.Keys.BLOCKS, OLD_NAMESPACE)) {
            if (mapping.getKey().getPath().equals("task_bell")) {
                mapping.remap(ExtensionBlocks.TASK_BELL.get());
            }
        }
        for (MissingMappingsEvent.Mapping<BlockEntityType<?>> mapping :
                event.getMappings(ForgeRegistries.Keys.BLOCK_ENTITY_TYPES, OLD_NAMESPACE)) {
            if (mapping.getKey().getPath().equals("task_bell")) {
                mapping.remap(ExtensionBlockEntities.TASK_BELL.get());
            }
        }
        for (MissingMappingsEvent.Mapping<MemoryModuleType<?>> mapping :
                event.getMappings(Registries.MEMORY_MODULE_TYPE, OLD_NAMESPACE)) {
            if (mapping.getKey().getPath().equals("periodic_scan")) {
                mapping.remap(ExtensionMemoryModules.PERIODIC_SCAN.get());
            }
        }
    }
}
