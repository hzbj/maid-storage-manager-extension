package io.github.maidstorageextension.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import studio.fantasyit.maid_storage_manager.items.HangUpItem;

import java.util.List;

/** Marker item whose tooltip documents the upstream frame-based misc-container workflow. */
public final class MiscStorageMarkerItem extends HangUpItem {
    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
                                List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        tooltip.add(Component.translatable(
                "tooltip.maid_storage_manager_extension.misc_storage.frame")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(
                "tooltip.maid_storage_manager_extension.misc_storage.keep_types")
                .withStyle(ChatFormatting.YELLOW));
        tooltip.add(Component.translatable(
                "tooltip.maid_storage_manager_extension.misc_storage.scan_sort")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
