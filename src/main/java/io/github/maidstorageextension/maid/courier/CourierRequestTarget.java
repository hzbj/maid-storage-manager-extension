package io.github.maidstorageextension.maid.courier;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/** Fixed owner destination carried by the request list itself. */
public final class CourierRequestTarget {
    public static final String TAG_POSITION = "CourierOwnerTargetPos";
    public static final String TAG_DIMENSION = "CourierOwnerTargetDimension";

    private CourierRequestTarget() {
    }

    public static void write(ItemStack stack, BlockPos position, ResourceLocation dimension) {
        if (stack == null || stack.isEmpty() || position == null || dimension == null) return;
        CompoundTag tag = stack.getOrCreateTag();
        tag.putLong(TAG_POSITION, position.asLong());
        tag.putString(TAG_DIMENSION, dimension.toString());
    }

    @Nullable
    public static Target read(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(TAG_POSITION, Tag.TAG_LONG)
                || !tag.contains(TAG_DIMENSION, Tag.TAG_STRING)) return null;
        ResourceLocation dimension = ResourceLocation.tryParse(tag.getString(TAG_DIMENSION));
        return dimension == null ? null : new Target(BlockPos.of(tag.getLong(TAG_POSITION)), dimension);
    }

    public record Target(BlockPos position, ResourceLocation dimension) {
    }
}
