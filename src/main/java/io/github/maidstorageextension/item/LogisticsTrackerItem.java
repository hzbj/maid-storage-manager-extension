package io.github.maidstorageextension.item;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import io.github.maidstorageextension.client.LogisticsTrackerClientData;
import io.github.maidstorageextension.logistics.LogisticsSnapshot;
import io.github.maidstorageextension.logistics.LogisticsDisplayName;
import io.github.maidstorageextension.logistics.LogisticsTrackerService;
import io.github.maidstorageextension.logistics.NetworkWarehouseService;
import io.github.maidstorageextension.maid.courier.CourierDeliveryPolicy;
import io.github.maidstorageextension.maid.courier.CourierService;
import io.github.maidstorageextension.maid.task.CourierTask;
import io.github.maidstorageextension.terminal.TerminalAccountService;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraftforge.network.NetworkHooks;
import io.github.maidstorageextension.logistics.LogisticsTrackerMenu;
import org.jetbrains.annotations.Nullable;
import studio.fantasyit.maid_storage_manager.items.HangUpItem;

import java.util.List;
import java.util.UUID;

/** Backward-compatible tracker item upgraded into the right-click compass terminal. */
public final class LogisticsTrackerItem extends HangUpItem {
    public static final String TAG_TERMINAL = "terminal_uuid";
    public static final String TAG_ACCOUNT = "terminal_account_uuid";
    public static final String TAG_ACCESS_TOKEN = "terminal_access_token";
    /** Legacy 1.2.x binding, consumed after a successful account login. */
    public static final String TAG_COURIER = "bound_courier_uuid";
    public static final String TAG_OWNER = "bound_owner_uuid";
    public static final UUID UNBOUND_COURIER = new UUID(0L, 0L);

    public LogisticsTrackerItem() {
        super(new Properties().stacksTo(1));
    }

    public static UUID getCourier(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.hasUUID(TAG_COURIER) ? tag.getUUID(TAG_COURIER) : null;
    }

    public static UUID getTerminalId(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.hasUUID(TAG_TERMINAL) ? tag.getUUID(TAG_TERMINAL) : null;
    }

    public static UUID ensureTerminalId(ItemStack stack) {
        UUID existing = getTerminalId(stack);
        if (existing != null) return existing;
        UUID created = UUID.randomUUID();
        stack.getOrCreateTag().putUUID(TAG_TERMINAL, created);
        return created;
    }

    public static UUID getAccountId(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.hasUUID(TAG_ACCOUNT) ? tag.getUUID(TAG_ACCOUNT) : null;
    }

    public static byte[] getAccessToken(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null ? new byte[0] : tag.getByteArray(TAG_ACCESS_TOKEN);
    }

    public static void rememberAccount(ItemStack stack, UUID account, byte[] token) {
        CompoundTag tag = stack.getOrCreateTag();
        if (account != null) tag.putUUID(TAG_ACCOUNT, account);
        if (token != null && token.length > 0) tag.putByteArray(TAG_ACCESS_TOKEN, token);
    }

    public static void forgetAccount(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null) return;
        tag.remove(TAG_ACCOUNT);
        tag.remove(TAG_ACCESS_TOKEN);
    }

    public static UUID getBoundOwner(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.hasUUID(TAG_OWNER) ? tag.getUUID(TAG_OWNER) : null;
    }

    public static void bind(ItemStack stack, EntityMaid courier, Player owner) {
        stack.getOrCreateTag().putUUID(TAG_COURIER, courier.getUUID());
        stack.getOrCreateTag().putUUID(TAG_OWNER, owner.getUUID());
        LogisticsSnapshot.clearLegacy(stack);
    }

    public static void unbind(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag != null) {
            tag.remove(TAG_COURIER);
            tag.remove(TAG_OWNER);
        }
        LogisticsSnapshot.clearLegacy(stack);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player,
                                                  LivingEntity target, InteractionHand hand) {
        if (target instanceof EntityMaid maid) {
            if (!maid.isOwnedBy(player)) {
                if (!player.level().isClientSide) player.sendSystemMessage(Component.translatable(
                        "message.maid_storage_manager_extension.logistics_tracker.not_owner"));
                return InteractionResult.sidedSuccess(player.level().isClientSide);
            }
            if (!player.level().isClientSide) {
                if (!maid.getTask().getUid().equals(CourierTask.TASK_ID)) {
                    player.sendSystemMessage(Component.translatable(
                            "message.maid_storage_manager_extension.logistics_tracker.not_courier"));
                } else if (player instanceof ServerPlayer serverPlayer) {
                    ensureTerminalId(stack);
                    TerminalAccountService.registerMaid(serverPlayer, stack, maid);
                }
            }
            return InteractionResult.sidedSuccess(player.level().isClientSide);
        }
        return super.interactLivingEntity(stack, player, target, hand);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer serverPlayer) {
            UUID terminal = ensureTerminalId(stack);
            MenuProvider provider = new SimpleMenuProvider(
                    (containerId, inventory, ignored) ->
                            new LogisticsTrackerMenu(containerId, inventory, terminal),
                    Component.translatable(
                            "gui.maid_storage_manager_extension.logistics_tracker.title"));
            NetworkHooks.openScreen(serverPlayer, provider, buffer -> buffer.writeUUID(terminal));
            TerminalAccountService.update(serverPlayer, terminal);
            return InteractionResultHolder.success(stack);
        }
        if (level.isClientSide) {
            return InteractionResultHolder.success(stack);
        }
        return InteractionResultHolder.fail(stack);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player != null && player.isShiftKeyDown()) {
            if (!context.getLevel().isClientSide && player instanceof ServerPlayer serverPlayer) {
                ItemStack terminalStack = context.getItemInHand();
                UUID terminal = ensureTerminalId(terminalStack);
                if (TerminalAccountService.registerMailbox(serverPlayer, terminal,
                        serverPlayer.serverLevel(), context.getClickedPos())) {
                    return InteractionResult.SUCCESS;
                }
                UUID courier = TerminalAccountService.selectedCourier(serverPlayer, terminal);
                if (courier == null) return InteractionResult.FAIL;
                boolean container = CourierService.setDeliveryChestAuthorized(
                        serverPlayer, courier, serverPlayer.serverLevel(), context.getClickedPos());
                if (CourierDeliveryPolicy.markerAction(container)
                        == CourierDeliveryPolicy.MarkerAction.SET) {
                    player.sendSystemMessage(Component.translatable(
                            "message.maid_storage_manager_extension.logistics_tracker.delivery_chest_set"));
                } else if (CourierService.clearDeliveryChestAuthorized(
                        courier, serverPlayer.serverLevel())) {
                    player.sendSystemMessage(Component.translatable(
                            "message.maid_storage_manager_extension.logistics_tracker.delivery_chest_cleared"));
                } else {
                    player.sendSystemMessage(Component.translatable(
                            "message.maid_storage_manager_extension.logistics_tracker.not_container"));
                    return InteractionResult.FAIL;
                }
            }
            return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
        }
        return player == null ? InteractionResult.PASS
                : use(context.getLevel(), player, context.getHand()).getResult();
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        super.inventoryTick(stack, level, entity, slot, selected);
        if (!(entity instanceof ServerPlayer player) || level.isClientSide
                || (player.tickCount + slot) % 20 != 0) return;
        UUID terminal = getTerminalId(stack);
        if (terminal != null) TerminalAccountService.update(player, terminal);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
                                List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        tooltip.add(Component.translatable(
                "tooltip.maid_storage_manager_extension.logistics_tracker.desc")
                .withStyle(ChatFormatting.GRAY));
        UUID account = getAccountId(stack);
        if (account == null) {
            tooltip.add(Component.translatable(
                    "tooltip.maid_storage_manager_extension.logistics_tracker.unbound")
                    .withStyle(ChatFormatting.YELLOW));
        } else {
            tooltip.add(Component.translatable(
                    "tooltip.maid_storage_manager_extension.logistics_tracker.account",
                    account.toString().substring(0, 8)).withStyle(ChatFormatting.GREEN));
            tooltip.add(Component.translatable(
                    "tooltip.maid_storage_manager_extension.logistics_tracker.remembered")
                    .withStyle(ChatFormatting.DARK_GRAY));
            tooltip.add(Component.translatable(
                    "tooltip.maid_storage_manager_extension.logistics_tracker.delivery_hint")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }

}
