package io.github.maidstorageextension.migration;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

/** Codec bridge for upstream attachment APIs that still expose registry-less CompoundTag hooks. */
public final class ItemStackNbt {
    private ItemStackNbt() {
    }

    public static CompoundTag save(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return new CompoundTag();
        Tag encoded = ItemStack.CODEC.encodeStart(NbtOps.INSTANCE, stack)
                .result().orElseGet(CompoundTag::new);
        return encoded instanceof CompoundTag compound ? compound : new CompoundTag();
    }

    public static ItemStack load(CompoundTag tag) {
        if (tag == null || tag.isEmpty()) return ItemStack.EMPTY;
        return ItemStack.CODEC.parse(NbtOps.INSTANCE, tag).result().orElse(ItemStack.EMPTY);
    }
}
