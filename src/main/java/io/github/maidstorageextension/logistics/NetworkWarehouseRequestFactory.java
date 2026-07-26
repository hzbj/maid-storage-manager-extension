package io.github.maidstorageextension.logistics;

import io.github.maidstorageextension.compat.RequestListSafety;
import io.github.maidstorageextension.migration.ItemDataMigration;
import io.github.maidstorageextension.network.NetworkWarehouseActionPacket;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import studio.fantasyit.maid_storage_manager.items.RequestListItem;
import studio.fantasyit.maid_storage_manager.items.data.RequestItemStackList;
import studio.fantasyit.maid_storage_manager.registry.DataComponentRegistry;
import studio.fantasyit.maid_storage_manager.util.ItemStackUtil;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Updates an existing Maid Storage Manager request list for a network warehouse order. */
public final class NetworkWarehouseRequestFactory {
    public static final String TAG_NETWORK_ORDER = "MaidStorageExtensionNetworkOrder";

    private NetworkWarehouseRequestFactory() {
    }

    public static ItemStack update(ItemStack request, UUID owner, UUID courier,
                                   UUID warehouse, long createdGameTime,
                                   NetworkWarehouseActionPacket.DeliveryTarget delivery,
                                   List<NetworkWarehouseActionPacket.RequestedItem> requestedItems) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(courier, "courier");
        Objects.requireNonNull(warehouse, "warehouse");
        Objects.requireNonNull(delivery, "delivery");
        if (requestedItems == null || requestedItems.isEmpty()
                || requestedItems.size() > NetworkWarehouseActionPacket.MAX_REQUEST_LINES) {
            throw new IllegalArgumentException("A network warehouse order needs 1-10 item lines");
        }

        if (request.isEmpty()) {
            throw new IllegalArgumentException("A network warehouse order needs an existing request list");
        }
        RequestItemStackList items = new RequestItemStackList();
        // Upstream creates ten empty editor rows by default; network orders contain only
        // the explicitly requested lines and must not persist those placeholders.
        items.list.clear();
        for (NetworkWarehouseActionPacket.RequestedItem requested : requestedItems) {
            if (requested.prototype().isEmpty() || requested.amount() <= 0) {
                throw new IllegalArgumentException("Network warehouse order contains an empty line");
            }
            items.list.add(new RequestItemStackList.ListItem(
                    requested.prototype().copyWithCount(1), requested.amount(),
                    0, 0, List.of(), "", false));
        }
        request.set(DataComponentRegistry.REQUEST_ITEMS.get(), items.toImmutable());
        request.set(DataComponentRegistry.REQUEST_MATCHING.get(),
                ItemStackUtil.MATCH_TYPE.AUTO.ordinal());
        request.set(DataComponentRegistry.REQUEST_CD_UNIT.get(), false);
        request.set(DataComponentRegistry.REQUEST_INTERVAL.get(), 0);
        request.set(DataComponentRegistry.REQUEST_IGNORE.get(), false);
        UUID order = RequestListSafety.ensureJobUuid(request);

        CompoundTag metadata = new CompoundTag();
        metadata.putUUID("order", order);
        metadata.putUUID("owner", owner);
        metadata.putUUID("courier", courier);
        metadata.putUUID("warehouse", warehouse);
        metadata.putLong("created", Math.max(0L, createdGameTime));
        metadata.putString("delivery", delivery.name());
        ItemDataMigration.update(request, root -> root.put(TAG_NETWORK_ORDER, metadata));
        return request;
    }
}
