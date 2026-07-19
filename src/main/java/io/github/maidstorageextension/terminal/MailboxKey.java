package io.github.maidstorageextension.terminal;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

/** Stable server identity for one physical warehouse mailbox. */
public record MailboxKey(ResourceLocation dimension, BlockPos position) {
    public MailboxKey {
        position = position == null ? null : position.immutable();
    }

    public boolean valid() {
        return dimension != null && position != null;
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        if (dimension != null) tag.putString("dimension", dimension.toString());
        if (position != null) tag.putLong("position", position.asLong());
        return tag;
    }

    public static MailboxKey fromTag(CompoundTag tag) {
        if (tag == null || !tag.contains("dimension", Tag.TAG_STRING)
                || !tag.contains("position", Tag.TAG_LONG)) return null;
        ResourceLocation dimension = ResourceLocation.tryParse(tag.getString("dimension"));
        if (dimension == null) return null;
        return new MailboxKey(dimension, BlockPos.of(tag.getLong("position")));
    }
}
