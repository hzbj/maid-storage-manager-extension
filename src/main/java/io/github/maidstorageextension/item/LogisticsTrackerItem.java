package io.github.maidstorageextension.item;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import io.github.maidstorageextension.client.LogisticsTrackerClientData;
import io.github.maidstorageextension.client.LogisticsTrackerRenderer;
import io.github.maidstorageextension.logistics.LogisticsSnapshot;
import io.github.maidstorageextension.logistics.LogisticsDisplayName;
import io.github.maidstorageextension.logistics.LogisticsTrackerService;
import io.github.maidstorageextension.maid.courier.CourierDeliveryPolicy;
import io.github.maidstorageextension.maid.courier.CourierService;
import io.github.maidstorageextension.maid.task.CourierTask;
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
import studio.fantasyit.maid_storage_manager.event.RenderHandMapLikeEvent;
import studio.fantasyit.maid_storage_manager.items.HangUpItem;

import java.util.List;
import java.util.UUID;

/** Bindable map-like item that tracks one courier route and its transaction cargo. */
public final class LogisticsTrackerItem extends HangUpItem
        implements RenderHandMapLikeEvent.MapLikeRenderItem {
    public static final String TAG_COURIER = "bound_courier_uuid";
    public static final String TAG_OWNER = "bound_owner_uuid";

    public LogisticsTrackerItem() {
        super(new Properties().stacksTo(1));
    }

    public static UUID getCourier(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.hasUUID(TAG_COURIER) ? tag.getUUID(TAG_COURIER) : null;
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
            if (!maid.getTask().getUid().equals(CourierTask.TASK_ID)) {
                if (!player.level().isClientSide) player.sendSystemMessage(Component.translatable(
                        "message.maid_storage_manager_extension.logistics_tracker.not_courier"));
                return InteractionResult.sidedSuccess(player.level().isClientSide);
            }
            if (!player.level().isClientSide) {
                bind(stack, maid, player);
                if (player instanceof ServerPlayer serverPlayer) {
                    LogisticsTrackerService.update(serverPlayer, maid.getUUID());
                }
                player.sendSystemMessage(Component.translatable(
                        "message.maid_storage_manager_extension.logistics_tracker.bound", maid.getName()));
            }
            return InteractionResult.sidedSuccess(player.level().isClientSide);
        }
        return super.interactLivingEntity(stack, player, target, hand);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown() && getCourier(stack) != null) {
            if (!level.isClientSide) {
                unbind(stack);
                player.sendSystemMessage(Component.translatable(
                        "message.maid_storage_manager_extension.logistics_tracker.unbound"));
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        UUID courier = getCourier(stack);
        if (courier != null && player instanceof ServerPlayer serverPlayer) {
            MenuProvider provider = new SimpleMenuProvider(
                    (containerId, inventory, ignored) ->
                            new LogisticsTrackerMenu(containerId, inventory, courier),
                    Component.translatable(
                            "gui.maid_storage_manager_extension.logistics_tracker.title"));
            NetworkHooks.openScreen(serverPlayer, provider, buffer -> buffer.writeUUID(courier));
            LogisticsTrackerService.update(serverPlayer, courier);
            return InteractionResultHolder.success(stack);
        }
        if (courier != null && level.isClientSide) {
            return InteractionResultHolder.success(stack);
        }
        return InteractionResultHolder.pass(stack);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        UUID courier = getCourier(context.getItemInHand());
        if (!context.getLevel().isClientSide && courier == null) {
            return InteractionResult.FAIL;
        }
        Player player = context.getPlayer();
        if (player != null && player.isShiftKeyDown() && courier != null) {
            if (!context.getLevel().isClientSide && player instanceof ServerPlayer serverPlayer) {
                boolean container = CourierService.setDeliveryChest(serverPlayer, courier,
                        serverPlayer.serverLevel(), context.getClickedPos());
                if (CourierDeliveryPolicy.markerAction(container)
                        == CourierDeliveryPolicy.MarkerAction.SET) {
                    player.sendSystemMessage(Component.translatable(
                            "message.maid_storage_manager_extension.logistics_tracker.delivery_chest_set"));
                } else if (CourierService.clearDeliveryChest(serverPlayer, courier)) {
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
        return super.useOn(context);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        super.inventoryTick(stack, level, entity, slot, selected);
        if (!(entity instanceof ServerPlayer player) || level.isClientSide
                || (player.tickCount + slot) % 20 != 0) return;
        UUID courier = getCourier(stack);
        if (courier != null) LogisticsTrackerService.update(player, courier);
    }

    @Override
    public RenderHandMapLikeEvent.MapLikeRenderer getRenderer() {
        return LogisticsTrackerRenderer.INSTANCE;
    }

    @Override
    public boolean available(ItemStack stack) {
        return getCourier(stack) != null;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
                                List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        tooltip.add(Component.translatable(
                "tooltip.maid_storage_manager_extension.logistics_tracker.desc")
                .withStyle(ChatFormatting.GRAY));
        UUID courier = getCourier(stack);
        if (courier == null) {
            tooltip.add(Component.translatable(
                    "tooltip.maid_storage_manager_extension.logistics_tracker.unbound")
                    .withStyle(ChatFormatting.YELLOW));
        } else {
            LogisticsSnapshot.Snapshot snapshot = LogisticsTrackerClientData.get(courier);
            Component name = snapshot.courierName().isBlank()
                    ? Component.literal(courier.toString())
                    : LogisticsDisplayName.decode(snapshot.courierName());
            tooltip.add(Component.translatable(
                    "tooltip.maid_storage_manager_extension.logistics_tracker.bound", name)
                    .withStyle(snapshot.online() ? ChatFormatting.GREEN : ChatFormatting.GOLD));
            tooltip.add(Component.translatable(
                    "tooltip.maid_storage_manager_extension.logistics_tracker.unbind_hint")
                    .withStyle(ChatFormatting.DARK_GRAY));
            tooltip.add(Component.translatable(
                    "tooltip.maid_storage_manager_extension.logistics_tracker.delivery_hint")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    public boolean allowClickThrough() {
        return true;
    }
}
