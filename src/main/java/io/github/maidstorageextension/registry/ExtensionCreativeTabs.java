package io.github.maidstorageextension.registry;

import io.github.maidstorageextension.MaidStorageManagerExtension;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

public final class ExtensionCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> REGISTER =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MaidStorageManagerExtension.MOD_ID);
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN = REGISTER.register("main", () ->
            CreativeModeTab.builder()
                    .icon(() -> new ItemStack(ExtensionItems.INVENTORY_MAINTENANCE_DEVICE.get()))
                    .title(Component.translatable("itemGroup.maid_storage_manager_extension"))
                    .displayItems((parameters, output) -> ExtensionItems.REGISTER.getEntries().stream()
                            .map(DeferredHolder::get).forEach(output::accept))
                    .build());

    private ExtensionCreativeTabs() {
    }

    public static void register(IEventBus bus) {
        REGISTER.register(bus);
    }
}
