package io.github.maidstorageextension.logistics;

import io.github.maidstorageextension.data.CourierData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import io.github.maidstorageextension.terminal.MailboxKey;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** On-demand inventory and map payload for the communication terminal's warehouse page. */
public final class NetworkWarehouseSnapshot {
    public enum InventoryState {
        UNAVAILABLE,
        FRESH,
        STALE
    }

    public enum Blocker {
        NONE,
        UNBOUND,
        COURIER_OFFLINE,
        UNAUTHORIZED,
        WAREHOUSE_OFFLINE,
        INVENTORY_LIST_UNAVAILABLE,
        ACTIVE_TRANSACTION
    }

    public enum RequestListAvailability {
        NONE,
        HAND,
        BACKPACK
    }

    public enum TransportHint {
        WALK,
        ENDER_POCKET,
        BROOM,
        BROOM_ENDER_POCKET,
        MISSING_ENDER_POCKET,
        MISSING_BROOM,
        MISSING_BROOM_AND_ENDER_POCKET,
        MISSING_MAILBOX,
        STATION_BLOCKED
    }

    public record InventoryEntry(ItemStack prototype, int available) {
        public InventoryEntry {
            prototype = prototype == null || prototype.isEmpty()
                    ? ItemStack.EMPTY : prototype.copyWithCount(1);
            available = Math.max(0, available);
        }
    }

    public record MapPoint(ResourceLocation dimension, BlockPos position) {
        public MapPoint {
            position = position == null ? null : position.immutable();
        }

        public boolean available() {
            return dimension != null && position != null;
        }
    }

    public record Snapshot(boolean online, boolean authorized, MailboxKey mailboxKey,
                           UUID inventoryList, long generation, UUID warehouse,
                           String warehouseName, InventoryState inventoryState,
                           long publishedGameTime, long inventoryAge,
                           List<InventoryEntry> inventory, boolean enderPocketAvailable,
                           boolean nearbyHandoffAvailable, boolean fixedDeliveryAvailable,
                           RequestListAvailability requestListAvailability,
                           TransportHint transportHint,
                           boolean activeTransaction, Blocker blocker,
                           CourierData.PostDeliveryMode postDeliveryMode,
                           CourierData.ReturnBlocker returnBlocker,
                           MapPoint player, MapPoint courier, MapPoint warehousePoint,
                           MapPoint mailbox, MapPoint delivery) {
        public Snapshot {
            warehouseName = warehouseName == null ? "" : warehouseName;
            generation = Math.max(0L, generation);
            inventoryState = inventoryState == null
                    ? InventoryState.UNAVAILABLE : inventoryState;
            publishedGameTime = Math.max(-1L, publishedGameTime);
            inventoryAge = Math.max(0L, inventoryAge);
            inventory = inventory == null ? List.of() : List.copyOf(inventory);
            requestListAvailability = requestListAvailability == null
                    ? RequestListAvailability.NONE : requestListAvailability;
            transportHint = transportHint == null ? TransportHint.MISSING_MAILBOX : transportHint;
            blocker = blocker == null ? Blocker.NONE : blocker;
            postDeliveryMode = postDeliveryMode == null
                    ? CourierData.PostDeliveryMode.FOLLOW_OWNER : postDeliveryMode;
            returnBlocker = returnBlocker == null
                    ? CourierData.ReturnBlocker.NONE : returnBlocker;
        }

        public static Snapshot empty() {
            return new Snapshot(false, true, null, null, 0L, null, "", InventoryState.UNAVAILABLE,
                    -1L, 0L, List.of(), false, false, false,
                    RequestListAvailability.NONE, TransportHint.MISSING_MAILBOX,
                    false, Blocker.UNBOUND,
                    CourierData.PostDeliveryMode.FOLLOW_OWNER, CourierData.ReturnBlocker.NONE,
                    null, null, null, null, null);
        }

        public Snapshot offline() {
            return new Snapshot(false, authorized, mailboxKey, inventoryList, generation,
                    warehouse, warehouseName, inventoryState,
                    publishedGameTime, inventoryAge, inventory, enderPocketAvailable,
                    nearbyHandoffAvailable, fixedDeliveryAvailable, requestListAvailability,
                    transportHint,
                    activeTransaction,
                    online ? Blocker.COURIER_OFFLINE : blocker,
                    postDeliveryMode, returnBlocker,
                    player, courier, warehousePoint, mailbox, delivery);
        }
    }

    private NetworkWarehouseSnapshot() {
    }

    public static CompoundTag toTag(Snapshot snapshot, HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("online", snapshot.online());
        tag.putBoolean("authorized", snapshot.authorized());
        if (snapshot.mailboxKey() != null) tag.put("mailboxKey", snapshot.mailboxKey().toTag());
        if (snapshot.inventoryList() != null) tag.putUUID("inventoryList", snapshot.inventoryList());
        tag.putLong("generation", snapshot.generation());
        if (snapshot.warehouse() != null) tag.putUUID("warehouse", snapshot.warehouse());
        tag.putString("warehouseName", snapshot.warehouseName());
        tag.putString("inventoryState", snapshot.inventoryState().name());
        tag.putLong("publishedGameTime", snapshot.publishedGameTime());
        tag.putLong("inventoryAge", snapshot.inventoryAge());
        tag.putBoolean("enderPocketAvailable", snapshot.enderPocketAvailable());
        tag.putBoolean("nearbyHandoffAvailable", snapshot.nearbyHandoffAvailable());
        tag.putBoolean("fixedDeliveryAvailable", snapshot.fixedDeliveryAvailable());
        tag.putString("requestListAvailability", snapshot.requestListAvailability().name());
        tag.putString("transportHint", snapshot.transportHint().name());
        tag.putBoolean("activeTransaction", snapshot.activeTransaction());
        tag.putString("blocker", snapshot.blocker().name());
        tag.putString("postDeliveryMode", snapshot.postDeliveryMode().name());
        tag.putString("returnBlocker", snapshot.returnBlocker().name());

        ListTag inventory = new ListTag();
        for (InventoryEntry value : snapshot.inventory()) {
            if (value.prototype().isEmpty() || value.available() <= 0) continue;
            CompoundTag entry = (CompoundTag) value.prototype().save(registries);
            entry.putInt("NetworkAvailable", value.available());
            inventory.add(entry);
        }
        tag.put("inventory", inventory);
        putPoint(tag, "player", snapshot.player());
        putPoint(tag, "courier", snapshot.courier());
        putPoint(tag, "warehousePoint", snapshot.warehousePoint());
        putPoint(tag, "mailbox", snapshot.mailbox());
        putPoint(tag, "delivery", snapshot.delivery());
        return tag;
    }

    public static Snapshot fromTag(CompoundTag tag, HolderLookup.Provider registries) {
        if (tag == null || tag.isEmpty()) return Snapshot.empty();
        List<InventoryEntry> inventory = new ArrayList<>();
        ListTag list = tag.getList("inventory", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            ItemStack stack = ItemStack.parseOptional(registries, entry);
            int available = Math.max(0, entry.getInt("NetworkAvailable"));
            if (!stack.isEmpty() && available > 0) {
                inventory.add(new InventoryEntry(stack, available));
            }
        }
        return new Snapshot(
                tag.getBoolean("online"),
                !tag.contains("authorized") || tag.getBoolean("authorized"),
                tag.contains("mailboxKey", Tag.TAG_COMPOUND)
                        ? MailboxKey.fromTag(tag.getCompound("mailboxKey")) : null,
                tag.hasUUID("inventoryList") ? tag.getUUID("inventoryList") : null,
                tag.getLong("generation"),
                tag.hasUUID("warehouse") ? tag.getUUID("warehouse") : null,
                tag.getString("warehouseName"),
                parseInventoryState(tag.getString("inventoryState")),
                tag.contains("publishedGameTime", Tag.TAG_LONG)
                        ? tag.getLong("publishedGameTime") : -1L,
                tag.getLong("inventoryAge"),
                inventory,
                tag.getBoolean("enderPocketAvailable"),
                tag.getBoolean("nearbyHandoffAvailable"),
                tag.getBoolean("fixedDeliveryAvailable"),
                parseRequestListAvailability(tag),
                parseTransportHint(tag.getString("transportHint")),
                tag.getBoolean("activeTransaction"),
                parseBlocker(tag.getString("blocker")),
                parsePostDeliveryMode(tag.getString("postDeliveryMode")),
                parseReturnBlocker(tag.getString("returnBlocker")),
                readPoint(tag, "player"),
                readPoint(tag, "courier"),
                readPoint(tag, "warehousePoint"),
                readPoint(tag, "mailbox"),
                readPoint(tag, "delivery"));
    }

    private static void putPoint(CompoundTag root, String key, MapPoint point) {
        if (point == null || !point.available()) return;
        CompoundTag tag = new CompoundTag();
        tag.putString("dimension", point.dimension().toString());
        tag.putLong("position", point.position().asLong());
        root.put(key, tag);
    }

    private static MapPoint readPoint(CompoundTag root, String key) {
        if (!root.contains(key, Tag.TAG_COMPOUND)) return null;
        CompoundTag tag = root.getCompound(key);
        ResourceLocation dimension = ResourceLocation.tryParse(tag.getString("dimension"));
        if (dimension == null || !tag.contains("position", Tag.TAG_LONG)) return null;
        return new MapPoint(dimension, BlockPos.of(tag.getLong("position")));
    }

    private static InventoryState parseInventoryState(String value) {
        try {
            return InventoryState.valueOf(value);
        } catch (IllegalArgumentException ignored) {
            return InventoryState.UNAVAILABLE;
        }
    }

    private static RequestListAvailability parseRequestListAvailability(CompoundTag tag) {
        try {
            return RequestListAvailability.valueOf(tag.getString("requestListAvailability"));
        } catch (IllegalArgumentException ignored) {
            return tag.getBoolean("heldRequestListAvailable")
                    ? RequestListAvailability.HAND : RequestListAvailability.NONE;
        }
    }

    private static TransportHint parseTransportHint(String value) {
        try {
            return TransportHint.valueOf(value);
        } catch (IllegalArgumentException ignored) {
            return TransportHint.MISSING_MAILBOX;
        }
    }

    private static CourierData.PostDeliveryMode parsePostDeliveryMode(String value) {
        try {
            return CourierData.PostDeliveryMode.valueOf(value);
        } catch (IllegalArgumentException ignored) {
            return CourierData.PostDeliveryMode.FOLLOW_OWNER;
        }
    }

    private static CourierData.ReturnBlocker parseReturnBlocker(String value) {
        try {
            return CourierData.ReturnBlocker.valueOf(value);
        } catch (IllegalArgumentException ignored) {
            return CourierData.ReturnBlocker.NONE;
        }
    }

    private static Blocker parseBlocker(String value) {
        try {
            return Blocker.valueOf(value);
        } catch (IllegalArgumentException ignored) {
            return Blocker.NONE;
        }
    }
}
