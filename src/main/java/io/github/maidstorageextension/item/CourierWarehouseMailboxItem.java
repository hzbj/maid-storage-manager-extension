package io.github.maidstorageextension.item;

import io.github.maidstorageextension.block.CourierWarehouseStationBlockEntity;
import io.github.maidstorageextension.maid.courier.CourierWarehouseStationValidator;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Stateful mailbox item: mark a flight pad first, then place its mailbox. */
public final class CourierWarehouseMailboxItem extends BlockItem {
    private static final String TAG_LANDING_POS = "CourierLandingPos";
    private static final String TAG_LANDING_DIMENSION = "CourierLandingDimension";

    public CourierWarehouseMailboxItem(Block block, Properties properties) {
        super(block, properties.stacksTo(1));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        ItemStack stack = context.getItemInHand();
        Player player = context.getPlayer();
        if (!hasLanding(stack) || player != null && player.isShiftKeyDown()) {
            return markLanding(context, stack, player);
        }

        Level level = context.getLevel();
        BlockPos landing = landingPos(stack);
        ResourceLocation landingDimension = landingDimension(stack);
        BlockPos mailbox = new BlockPlaceContext(context).getClickedPos();
        if (landing == null || landingDimension == null
                || !landingDimension.equals(level.dimension().location())) {
            if (!level.isClientSide && player != null) player.sendSystemMessage(Component.translatable(
                    "message.maid_storage_manager_extension.courier_mailbox.wrong_dimension"));
            return InteractionResult.FAIL;
        }
        if (landing.distSqr(mailbox) > CourierWarehouseStationValidator.MAX_MAILBOX_DISTANCE_SQR) {
            if (!level.isClientSide && player != null) player.sendSystemMessage(Component.translatable(
                    "message.maid_storage_manager_extension.courier_mailbox.too_far"));
            return InteractionResult.FAIL;
        }
        if (CourierWarehouseStationValidator.overlapsPad(landing, mailbox)) {
            if (!level.isClientSide && player != null) player.sendSystemMessage(Component.translatable(
                    "message.maid_storage_manager_extension.courier_mailbox.inside_pad"));
            return InteractionResult.FAIL;
        }
        if (level instanceof ServerLevel serverLevel
                && !CourierWarehouseStationValidator.hasValidPad(serverLevel, landing)) {
            if (player != null) player.sendSystemMessage(Component.translatable(
                    "message.maid_storage_manager_extension.courier_station.invalid_pad"));
            return InteractionResult.FAIL;
        }

        CompoundTag blockEntityTag = stack.getOrCreateTagElement("BlockEntityTag");
        blockEntityTag.putLong(CourierWarehouseStationBlockEntity.TAG_LANDING_POS, landing.asLong());
        blockEntityTag.putString(CourierWarehouseStationBlockEntity.TAG_LANDING_DIMENSION,
                landingDimension.toString());
        if (player != null) {
            blockEntityTag.putUUID(CourierWarehouseStationBlockEntity.TAG_PLACER, player.getUUID());
            blockEntityTag.putString(CourierWarehouseStationBlockEntity.TAG_PLACER_NAME,
                    player.getName().getString());
        }
        return super.useOn(context);
    }

    private static InteractionResult markLanding(UseOnContext context, ItemStack stack,
                                                 @Nullable Player player) {
        Level level = context.getLevel();
        if (context.getClickedFace() != Direction.UP) {
            if (!level.isClientSide && player != null) player.sendSystemMessage(Component.translatable(
                    "message.maid_storage_manager_extension.courier_mailbox.mark_top"));
            return InteractionResult.FAIL;
        }
        BlockPos landing = context.getClickedPos().above();
        if (level instanceof ServerLevel serverLevel
                && !CourierWarehouseStationValidator.hasValidPad(serverLevel, landing)) {
            if (player != null) player.sendSystemMessage(Component.translatable(
                    "message.maid_storage_manager_extension.courier_station.invalid_pad"));
            return InteractionResult.FAIL;
        }
        if (!level.isClientSide) {
            CompoundTag tag = stack.getOrCreateTag();
            tag.putLong(TAG_LANDING_POS, landing.asLong());
            tag.putString(TAG_LANDING_DIMENSION, level.dimension().location().toString());
            if (player != null) player.sendSystemMessage(Component.translatable(
                    "message.maid_storage_manager_extension.courier_mailbox.marked",
                    landing.getX(), landing.getY(), landing.getZ()));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    public static boolean hasLanding(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.contains(TAG_LANDING_POS, Tag.TAG_LONG)
                && tag.contains(TAG_LANDING_DIMENSION, Tag.TAG_STRING);
    }

    @Nullable
    public static BlockPos landingPos(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.contains(TAG_LANDING_POS, Tag.TAG_LONG)
                ? BlockPos.of(tag.getLong(TAG_LANDING_POS)) : null;
    }

    @Nullable
    public static ResourceLocation landingDimension(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.contains(TAG_LANDING_DIMENSION, Tag.TAG_STRING)
                ? ResourceLocation.tryParse(tag.getString(TAG_LANDING_DIMENSION)) : null;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
                                List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        BlockPos landing = landingPos(stack);
        if (landing == null) {
            tooltip.add(Component.translatable(
                    "tooltip.maid_storage_manager_extension.courier_mailbox.unmarked")
                    .withStyle(ChatFormatting.YELLOW));
        } else {
            tooltip.add(Component.translatable(
                    "tooltip.maid_storage_manager_extension.courier_mailbox.marked",
                    landing.getX(), landing.getY(), landing.getZ()).withStyle(ChatFormatting.AQUA));
            tooltip.add(Component.translatable(
                    "tooltip.maid_storage_manager_extension.courier_mailbox.place_hint")
                    .withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable(
                    "tooltip.maid_storage_manager_extension.courier_mailbox.remark_hint")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
