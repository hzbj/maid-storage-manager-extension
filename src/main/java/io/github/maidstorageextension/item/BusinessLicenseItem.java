package io.github.maidstorageextension.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class BusinessLicenseItem extends BlockItem {
    public BusinessLicenseItem(Block block) {
        super(block, new Item.Properties());
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
                                List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        tooltip.add(Component.translatable(
                "tooltip.maid_storage_manager_extension.business_license.purpose")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(
                "tooltip.maid_storage_manager_extension.business_license.steps")
                .withStyle(ChatFormatting.YELLOW));
        tooltip.add(Component.translatable(
                "tooltip.maid_storage_manager_extension.business_license.terminal")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
