package io.github.maidstorageextension.item;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import io.github.maidstorageextension.block.TaskBellBlockEntity;
import studio.fantasyit.maid_storage_manager.maid.task.StorageManageTask;

import java.util.List;
import java.util.UUID;

public class TaskBellItem extends BlockItem {
    public static final String BLOCK_ENTITY_TAG = "BlockEntityTag";

    public TaskBellItem(Block block, Properties properties) {
        super(block, properties.stacksTo(1));
    }

    @Override
    public @NotNull InteractionResult interactLivingEntity(@NotNull ItemStack stack, Player player,
                                                            LivingEntity target, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND || !(target instanceof EntityMaid maid)) {
            return super.interactLivingEntity(stack, player, target, hand);
        }

        if (player.level().isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!maid.isOwnedBy(player)) {
            player.sendSystemMessage(Component.translatable("message.maid_storage_manager_extension.task_bell.not_owner"));
            return InteractionResult.FAIL;
        }
        if (!maid.getTask().getUid().equals(StorageManageTask.TASK_ID)) {
            player.sendSystemMessage(Component.translatable("message.maid_storage_manager_extension.task_bell.not_storage_maid"));
            return InteractionResult.FAIL;
        }

        CompoundTag blockEntityTag = stack.getOrCreateTagElement(BLOCK_ENTITY_TAG);
        blockEntityTag.putUUID(TaskBellBlockEntity.TAG_MAID_UUID, maid.getUUID());
        blockEntityTag.putString(TaskBellBlockEntity.TAG_MAID_NAME, maid.getName().getString());
        blockEntityTag.putUUID(TaskBellBlockEntity.TAG_OWNER_UUID, player.getUUID());
        player.sendSystemMessage(Component.translatable("message.maid_storage_manager_extension.task_bell.bound", maid.getName()));
        return InteractionResult.SUCCESS;
    }

    @Nullable
    public static UUID getBoundMaid(ItemStack stack) {
        CompoundTag blockEntityTag = stack.getTagElement(BLOCK_ENTITY_TAG);
        if (blockEntityTag == null || !blockEntityTag.hasUUID(TaskBellBlockEntity.TAG_MAID_UUID)) {
            return null;
        }
        return blockEntityTag.getUUID(TaskBellBlockEntity.TAG_MAID_UUID);
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @Nullable Level level,
                                @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        CompoundTag blockEntityTag = stack.getTagElement(BLOCK_ENTITY_TAG);
        if (blockEntityTag == null || !blockEntityTag.hasUUID(TaskBellBlockEntity.TAG_MAID_UUID)) {
            tooltip.add(Component.translatable("tooltip.maid_storage_manager_extension.task_bell.unbound"));
            tooltip.add(Component.translatable("tooltip.maid_storage_manager_extension.task_bell.bind_hint"));
            return;
        }
        String maidName = blockEntityTag.getString(TaskBellBlockEntity.TAG_MAID_NAME);
        tooltip.add(Component.translatable("tooltip.maid_storage_manager_extension.task_bell.bound",
                maidName.isEmpty() ? getBoundMaid(stack).toString() : maidName));
    }
}
