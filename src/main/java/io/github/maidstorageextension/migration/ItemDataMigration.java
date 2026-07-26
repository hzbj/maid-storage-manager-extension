package io.github.maidstorageextension.migration;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.function.Consumer;

/**
 * Single migration boundary for the extension's former item NBT.
 * Minecraft's 1.20.5+ data fixer moves unknown item tags into {@code custom_data};
 * writing through this helper keeps that representation idempotent.
 */
public final class ItemDataMigration {
    private ItemDataMigration() {
    }

    public static CompoundTag read(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return new CompoundTag();
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    }

    public static boolean has(ItemStack stack) {
        return stack != null && !stack.isEmpty()
                && !stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).isEmpty();
    }

    public static void set(ItemStack stack, CompoundTag tag) {
        if (stack == null || stack.isEmpty()) return;
        CustomData.set(DataComponents.CUSTOM_DATA, stack,
                tag == null ? new CompoundTag() : tag.copy());
    }

    public static void update(ItemStack stack, Consumer<CompoundTag> editor) {
        if (stack == null || stack.isEmpty()) return;
        CustomData.update(DataComponents.CUSTOM_DATA, stack, editor);
    }

    public static CompoundTag blockEntityData(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return new CompoundTag();
        return stack.getOrDefault(DataComponents.BLOCK_ENTITY_DATA, CustomData.EMPTY).copyTag();
    }

    public static void setBlockEntityData(ItemStack stack, CompoundTag tag) {
        if (stack == null || stack.isEmpty()) return;
        CustomData.set(DataComponents.BLOCK_ENTITY_DATA, stack,
                tag == null ? new CompoundTag() : tag.copy());
    }
}
