package io.github.maidstorageextension.registry;

import io.github.maidstorageextension.MaidStorageManagerExtension;
import io.github.maidstorageextension.maid.memory.MiscSortMemory;
import io.github.maidstorageextension.maid.memory.PeriodicScanMemory;
import io.github.maidstorageextension.maid.memory.TaskBellCallMemory;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

import java.util.Optional;

public final class ExtensionMemoryModules {
    public static final DeferredRegister<MemoryModuleType<?>> REGISTER =
            DeferredRegister.create(Registries.MEMORY_MODULE_TYPE, MaidStorageManagerExtension.MOD_ID);

    public static final RegistryObject<MemoryModuleType<PeriodicScanMemory>> PERIODIC_SCAN =
            REGISTER.register("periodic_scan", () -> new MemoryModuleType<>(Optional.of(PeriodicScanMemory.CODEC)));
    public static final RegistryObject<MemoryModuleType<MiscSortMemory>> MISC_SORT =
            REGISTER.register("misc_storage_cleanup",
                    () -> new MemoryModuleType<>(Optional.of(MiscSortMemory.CODEC)));
    public static final RegistryObject<MemoryModuleType<TaskBellCallMemory>> TASK_BELL_CALL =
            REGISTER.register("task_bell_call", () -> new MemoryModuleType<>(Optional.empty()));

    private ExtensionMemoryModules() {
    }

    public static void register(IEventBus bus) {
        REGISTER.register(bus);
    }
}
