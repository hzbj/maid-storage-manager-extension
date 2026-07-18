package io.github.maidstorageextension.logistics;

import io.github.maidstorageextension.compat.RequestListSafety;
import io.github.maidstorageextension.network.NetworkWarehouseActionPacket;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.ItemStack;
import studio.fantasyit.maid_storage_manager.items.RequestListItem;
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
        CompoundTag root = request.getOrCreateTag();
        ListTag items = new ListTag();
        for (NetworkWarehouseActionPacket.RequestedItem requested : requestedItems) {
            if (requested.prototype().isEmpty() || requested.amount() <= 0) {
                throw new IllegalArgumentException("Network warehouse order contains an empty line");
            }
            CompoundTag entry = new CompoundTag();
            entry.put(RequestListItem.TAG_ITEMS_ITEM,
                    requested.prototype().copyWithCount(1).save(new CompoundTag()));
            entry.putInt(RequestListItem.TAG_ITEMS_REQUESTED, requested.amount());
            items.add(entry);
        }
        root.put(RequestListItem.TAG_ITEMS, items);
        root.putInt(RequestListItem.TAG_MATCH, ItemStackUtil.MATCH_TYPE.AUTO.ordinal());
        root.putBoolean(RequestListItem.TAG_BLACKMODE, false);
        root.putBoolean(RequestListItem.TAG_STOCK_MODE, false);
        root.putBoolean(RequestListItem.TAG_UNIT_SECOND, false);
        root.putInt(RequestListItem.TAG_REPEAT_INTERVAL, 0);
        root.putBoolean(RequestListItem.TAG_IGNORE_TASK, false);
        RequestListSafety.ensureJobUuid(root);

        CompoundTag metadata = new CompoundTag();
        metadata.putUUID("order", root.getUUID(RequestListItem.TAG_UUID));
        metadata.putUUID("owner", owner);
        metadata.putUUID("courier", courier);
        metadata.putUUID("warehouse", warehouse);
        metadata.putLong("created", Math.max(0L, createdGameTime));
        metadata.putString("delivery", delivery.name());
        root.put(TAG_NETWORK_ORDER, metadata);
        return request;
    }
}
