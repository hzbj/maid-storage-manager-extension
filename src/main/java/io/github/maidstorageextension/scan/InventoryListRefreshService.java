package io.github.maidstorageextension.scan;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import studio.fantasyit.maid_storage_manager.Config;
import studio.fantasyit.maid_storage_manager.attachment.InventoryListData;
import studio.fantasyit.maid_storage_manager.registry.DataComponentRegistry;
import studio.fantasyit.maid_storage_manager.data.InventoryItem;
import io.github.maidstorageextension.item.InventoryMaintenanceDevice;
import io.github.maidstorageextension.data.MaintenanceStatusData;
import io.github.maidstorageextension.data.WarehouseNetworkData;
import io.github.maidstorageextension.terminal.MailboxWarehouseData;
import io.github.maidstorageextension.terminal.TerminalAccountService;
import studio.fantasyit.maid_storage_manager.items.WrittenInvListItem;
import studio.fantasyit.maid_storage_manager.registry.ItemRegistry;
import studio.fantasyit.maid_storage_manager.util.MemoryUtil;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class InventoryListRefreshService {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<UUID, UUID> FRAME_LOCKS = new ConcurrentHashMap<>();

    private InventoryListRefreshService() {
    }

    public enum Outcome {
        SUCCESS(MaintenanceStatusData.Result.SUCCESS),
        UNBOUND(MaintenanceStatusData.Result.UNBOUND),
        INVALID_FRAME(MaintenanceStatusData.Result.INVALID_FRAME),
        INVALID_CONTENT(MaintenanceStatusData.Result.INVALID_CONTENT),
        OUT_OF_SCOPE(MaintenanceStatusData.Result.OUT_OF_SCOPE),
        NO_SAFE_POSITION(MaintenanceStatusData.Result.NO_SAFE_POSITION),
        PATH_TIMEOUT(MaintenanceStatusData.Result.PATH_TIMEOUT),
        FRAME_BUSY(MaintenanceStatusData.Result.FRAME_BUSY),
        PUBLISH_FAILED(MaintenanceStatusData.Result.PUBLISH_FAILED);

        private final MaintenanceStatusData.Result statusResult;

        Outcome(MaintenanceStatusData.Result statusResult) {
            this.statusResult = statusResult;
        }

        public MaintenanceStatusData.Result statusResult() {
            return statusResult;
        }

        public String translationKey() {
            return statusResult.translationKey();
        }
    }

    public record RefreshResult(Outcome outcome, int publishedItemTypes) {
        public static RefreshResult failed(Outcome outcome) {
            return new RefreshResult(outcome, 0);
        }

        public boolean success() {
            return outcome == Outcome.SUCCESS;
        }
    }

    public record FrameLookup(Outcome outcome, @Nullable ItemFrame frame) {
        public boolean success() {
            return frame != null;
        }
    }

    public static Optional<ItemFrame> findValidFrame(ServerLevel level, EntityMaid maid) {
        return Optional.ofNullable(resolveFrame(level, maid).frame());
    }

    public static FrameLookup resolveFrame(ServerLevel level, EntityMaid maid) {
        Optional<ItemStack> device = InventoryMaintenanceDevice.findOn(maid);
        if (device.isEmpty() || !InventoryMaintenanceDevice.isBound(device.get())) {
            return new FrameLookup(Outcome.UNBOUND, null);
        }
        ItemStack stack = device.get();
        if (!level.dimension().location().equals(InventoryMaintenanceDevice.getFrameDimension(stack))) {
            return new FrameLookup(Outcome.OUT_OF_SCOPE, null);
        }
        if (!StorageScanService.isInsideScope(maid, InventoryMaintenanceDevice.getFramePos(stack))) {
            return new FrameLookup(Outcome.OUT_OF_SCOPE, null);
        }
        Entity entity = level.getEntity(InventoryMaintenanceDevice.getFrameUuid(stack));
        if (!(entity instanceof ItemFrame frame)
                || !frame.blockPosition().equals(InventoryMaintenanceDevice.getFramePos(stack))) {
            return new FrameLookup(Outcome.INVALID_FRAME, null);
        }
        ItemStack displayed = frame.getItem();
        if (!displayed.is(ItemRegistry.INVENTORY_LIST.get())
                && !displayed.is(ItemRegistry.WRITTEN_INVENTORY_LIST.get())) {
            return new FrameLookup(Outcome.INVALID_CONTENT, null);
        }
        return new FrameLookup(Outcome.SUCCESS, frame);
    }

    public static boolean tryLock(ItemFrame frame, EntityMaid maid) {
        UUID existing = FRAME_LOCKS.putIfAbsent(frame.getUUID(), maid.getUUID());
        if (existing == null || existing.equals(maid.getUUID())) {
            return true;
        }
        LOGGER.warn("Inventory-list frame {} is already being refreshed by maid {}; maid {} will retry later",
                frame.getUUID(), existing, maid.getUUID());
        return false;
    }

    public static void unlock(@Nullable UUID frameUuid, EntityMaid maid) {
        if (frameUuid != null) {
            FRAME_LOCKS.remove(frameUuid, maid.getUUID());
        }
    }

    /** Writes the new UUID data first, swaps the frame stack, then deletes the old UUID data. */
    public static RefreshResult refresh(ServerLevel level, EntityMaid maid, ItemFrame frame) {
        FrameLookup lookup = resolveFrame(level, maid);
        if (!lookup.success() || !lookup.frame().getUUID().equals(frame.getUUID())) {
            return RefreshResult.failed(lookup.outcome());
        }
        StorageScanService.pruneOutsideCurrentScope(level, maid);
        ItemStack oldItem = frame.getItem().copy();
        UUID oldUuid = oldItem.get(DataComponentRegistry.INVENTORY_UUID.get());
        UUID newUuid = UUID.randomUUID();
        List<InventoryItem> contents = MemoryUtil.getViewedInventory(maid).flatten();
        ServerLevel dataLevel = level.getServer().overworld();

        InventoryListData data = InventoryListData.get(dataLevel);
        boolean[] published = {false};
        if (data != null) {
            data.addWithCraftable(dataLevel.registryAccess(), newUuid, contents);
            ItemStack newItem = ItemRegistry.WRITTEN_INVENTORY_LIST.get().getDefaultInstance();
            newItem.set(DataComponentRegistry.INVENTORY_UUID.get(), newUuid);
            newItem.set(DataComponentRegistry.INVENTORY_AUTHOR.get(), maid.getName());
            newItem.set(DataComponentRegistry.INVENTORY_TIME.get(), level.getDayTime());
            if (maid.getOwner() instanceof ServerPlayer player) {
                double damage = Config.invListDamageMin
                        + Math.min(Config.invListDamageMax, Config.invListDamageFactor * contents.size());
                ((WrittenInvListItem) ItemRegistry.WRITTEN_INVENTORY_LIST.get()).setAttributes(
                        newItem,
                        damage - player.getAttributeBaseValue(Attributes.ATTACK_DAMAGE),
                        Config.invListDamageAttackSpd - player.getAttributeBaseValue(Attributes.ATTACK_SPEED));
            }
            frame.setItem(newItem, false);
            published[0] = newUuid.equals(
                    frame.getItem().get(DataComponentRegistry.INVENTORY_UUID.get()));
            if (published[0] && oldUuid != null) {
                data.remove(oldUuid);
            } else if (!published[0]) {
                data.remove(newUuid);
            }
        }
        if (!published[0]) {
            frame.setItem(oldItem, false);
            return RefreshResult.failed(Outcome.PUBLISH_FAILED);
        }
        WarehouseNetworkData.Data networkData = WarehouseNetworkData.get(maid);
        if (networkData.publish(newUuid, level.getDayTime())) {
            maid.setAndSyncData(WarehouseNetworkData.KEY, networkData);
        }
        MailboxWarehouseData.FrameBinding frameBinding =
                new MailboxWarehouseData.FrameBinding(level.dimension().location(),
                        frame.blockPosition(), frame.getUUID());
        MailboxWarehouseData mailboxData = MailboxWarehouseData.get(level.getServer());
        if (mailboxData.publish(maid.getUUID(), frameBinding, newUuid, level.getDayTime())) {
            TerminalAccountService.refreshMailboxViewers(
                    level.getServer(), mailboxData.mailboxOf(maid.getUUID()));
        }
        return new RefreshResult(Outcome.SUCCESS, contents.size());
    }
}
