package io.github.maidstorageextension.event;

import com.github.tartaricacid.touhoulittlemaid.api.event.InteractMaidEvent;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import io.github.maidstorageextension.MaidStorageManagerExtension;
import io.github.maidstorageextension.debug.ReachabilityDebugManager;
import io.github.maidstorageextension.item.InventoryMaintenanceDevice;
import io.github.maidstorageextension.maid.courier.CourierBroomFlightService;
import io.github.maidstorageextension.maid.courier.CourierRequestTarget;
import io.github.maidstorageextension.registry.ExtensionItems;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import studio.fantasyit.maid_storage_manager.items.ChangeFlag;
import studio.fantasyit.maid_storage_manager.maid.ChatTexts;
import studio.fantasyit.maid_storage_manager.maid.task.StorageManageTask;
import studio.fantasyit.maid_storage_manager.registry.ItemRegistry;
import studio.fantasyit.maid_storage_manager.storage.Target;
import studio.fantasyit.maid_storage_manager.util.MemoryUtil;
import studio.fantasyit.maid_storage_manager.util.StorageAccessUtil;

import java.util.ArrayList;
import java.util.List;

@Mod.EventBusSubscriber(modid = MaidStorageManagerExtension.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class InteractionEvents {
    private InteractionEvents() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void interactMaid(InteractMaidEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) {
            return;
        }
        EntityMaid maid = event.getMaid();
        if (ReachabilityDebugManager.consumeClick(player, maid)) {
            event.setCanceled(true);
            return;
        }
        ItemStack held = player.getMainHandItem();
        if (!held.is(ItemRegistry.CHANGE_FLAG.get())
                || !maid.isOwnedBy(player)
                || !maid.getTask().getUid().equals(StorageManageTask.TASK_ID)) {
            return;
        }
        List<Target> selected = ChangeFlag.getStorages(held);
        if (selected.isEmpty()) {
            event.setCanceled(true);
            return;
        }
        ServerLevel level = (ServerLevel) maid.level();
        List<Target> rejected = new ArrayList<>();
        int accepted = 0;
        for (Target chosen : selected) {
            List<Target> possible = StorageAccessUtil.findTargetRewrite(level, maid, chosen.withoutSide(), false);
            Target target;
            if (possible.contains(chosen)) {
                target = chosen;
            } else if (!possible.isEmpty()) {
                target = possible.get(0);
            } else {
                ChangeFlag.clearVisForMemories(level, maid, chosen);
                rejected.add(chosen);
                continue;
            }
            Target storage = MemoryUtil.getViewedInventory(maid).ambitiousPos(level, target);
            ChangeFlag.clearVisForMemories(level, maid, storage);
            var queue = MemoryUtil.getViewedInventory(maid).getMarkChanged();
            queue.remove(storage);
            queue.addFirst(storage);
            accepted++;
        }
        setFlagStorages(held, rejected);
        MemoryUtil.clearTarget(maid);
        player.sendSystemMessage(Component.translatable(
                "interaction.maid_storage_manager_extension.flag_changed_result", accepted, rejected.size()));
        if (accepted > 0) {
            ChatTexts.send(maid, ChatTexts.CHAT_CHECK_MARK_CHANGED);
        }
        event.setCanceled(true);
    }

    private static void setFlagStorages(ItemStack stack, List<Target> storages) {
        CompoundTag tag = stack.getOrCreateTag();
        ListTag list = new ListTag();
        storages.forEach(storage -> list.add(storage.toNbt()));
        tag.put(ChangeFlag.TAG_STORAGES, list);
        stack.setTag(tag);
    }

    /** A normal request-list use records the fixed delivery point without replacing its UI. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void markCourierRequestTarget(PlayerInteractEvent.RightClickItem event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !event.getItemStack().is(ItemRegistry.REQUEST_LIST_ITEM.get())
                || player.isShiftKeyDown()) return;
        var target = player.blockPosition();
        CourierRequestTarget.write(event.getItemStack(), target,
                player.serverLevel().dimension().location());
        player.sendSystemMessage(Component.translatable(
                "message.maid_storage_manager_extension.courier.owner_target_marked",
                target.getX(), target.getY(), target.getZ()));
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void interactFrame(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !(event.getTarget() instanceof ItemFrame frame)) {
            return;
        }
        ItemStack held = player.getItemInHand(event.getHand());
        if (!player.isShiftKeyDown() || !held.is(ExtensionItems.INVENTORY_MAINTENANCE_DEVICE.get())) {
            return;
        }
        ItemStack displayed = frame.getItem();
        if (displayed.is(ItemRegistry.INVENTORY_LIST.get())
                || displayed.is(ItemRegistry.WRITTEN_INVENTORY_LIST.get())) {
            InventoryMaintenanceDevice.bind(held, frame);
            player.sendSystemMessage(Component.translatable(
                    "message.maid_storage_manager_extension.inventory_maintenance_device.bound",
                    frame.blockPosition().getX(), frame.blockPosition().getY(), frame.blockPosition().getZ()));
            event.setCancellationResult(InteractionResult.SUCCESS);
        } else {
            player.sendSystemMessage(Component.translatable(
                    "message.maid_storage_manager_extension.inventory_maintenance_device.invalid_frame"));
            event.setCancellationResult(InteractionResult.FAIL);
        }
        event.setCanceled(true);
    }

    /** Temporary courier brooms are state-machine vehicles, not player-mountable entities. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void interactCourierBroom(PlayerInteractEvent.EntityInteract event) {
        if (!CourierBroomFlightService.isCourierBroom(event.getTarget())) return;
        event.setCancellationResult(InteractionResult.FAIL);
        event.setCanceled(true);
    }
}
