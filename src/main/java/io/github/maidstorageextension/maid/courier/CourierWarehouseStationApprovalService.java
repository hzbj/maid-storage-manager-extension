package io.github.maidstorageextension.maid.courier;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import io.github.maidstorageextension.block.CourierWarehouseStationBlockEntity;
import io.github.maidstorageextension.data.WarehouseStationData;
import io.github.maidstorageextension.registry.ExtensionBlocks;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import studio.fantasyit.maid_storage_manager.maid.task.StorageManageTask;

/** Server-authoritative approval entry point for cross-owner mailbox placement. */
public final class CourierWarehouseStationApprovalService {
    public enum Decision { APPROVE, REJECT }

    private CourierWarehouseStationApprovalService() {
    }

    public static void decide(ServerPlayer owner, EntityMaid warehouse,
                              WarehouseStationData.StationKey key, Decision decision) {
        if (owner == null || warehouse == null || key == null || !key.valid()
                || !warehouse.isOwnedBy(owner)
                || !warehouse.getTask().getUid().equals(StorageManageTask.TASK_ID)) return;
        WarehouseStationData.Data data = WarehouseStationData.get(warehouse);
        if (!data.contains(key)) return;

        ServerLevel level = owner.server.getLevel(ResourceKey.create(Registries.DIMENSION, key.dimension()));
        if (level == null || !level.hasChunkAt(key.mailboxPos())
                || !level.getBlockState(key.mailboxPos())
                .is(ExtensionBlocks.COURIER_WAREHOUSE_STATION.get())
                || !(level.getBlockEntity(key.mailboxPos())
                instanceof CourierWarehouseStationBlockEntity mailbox)) {
            data.remove(key);
            sync(warehouse, data);
            owner.sendSystemMessage(Component.translatable(
                    "message.maid_storage_manager_extension.courier_mailbox.approval_stale"));
            return;
        }

        boolean changed = decision == Decision.APPROVE
                ? mailbox.approve(owner, warehouse) : mailbox.reject(owner, warehouse);
        if (!changed) {
            owner.sendSystemMessage(Component.translatable(
                    "message.maid_storage_manager_extension.courier_mailbox.approval_invalid"));
            return;
        }
        owner.sendSystemMessage(Component.translatable(decision == Decision.APPROVE
                ? "message.maid_storage_manager_extension.courier_mailbox.approved"
                : "message.maid_storage_manager_extension.courier_mailbox.rejected"));
    }

    private static void sync(EntityMaid maid, WarehouseStationData.Data data) {
        maid.setAndSyncData(WarehouseStationData.KEY, data);
    }
}
