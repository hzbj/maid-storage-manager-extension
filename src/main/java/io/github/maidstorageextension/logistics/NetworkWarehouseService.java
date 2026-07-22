package io.github.maidstorageextension.logistics;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import io.github.maidstorageextension.compat.EnderPocketCompat;
import io.github.maidstorageextension.data.CourierData;
import io.github.maidstorageextension.data.WarehouseCourierData;
import io.github.maidstorageextension.data.WarehouseNetworkData;
import io.github.maidstorageextension.maid.courier.CourierRequestTarget;
import io.github.maidstorageextension.maid.courier.CourierService;
import io.github.maidstorageextension.maid.task.CourierTask;
import io.github.maidstorageextension.maid.MaidRoleService;
import io.github.maidstorageextension.maid.ExtensionMemoryUtil;
import io.github.maidstorageextension.network.ExtensionNetwork;
import io.github.maidstorageextension.network.NetworkWarehouseActionPacket;
import io.github.maidstorageextension.network.NetworkWarehouseSnapshotPacket;
import io.github.maidstorageextension.scan.InventoryListRefreshService;
import io.github.maidstorageextension.terminal.TerminalAccountService;
import io.github.maidstorageextension.terminal.MailboxKey;
import io.github.maidstorageextension.terminal.MailboxWarehouseData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;
import studio.fantasyit.maid_storage_manager.capability.InventoryListDataProvider;
import studio.fantasyit.maid_storage_manager.data.InventoryItem;
import studio.fantasyit.maid_storage_manager.items.WrittenInvListItem;
import studio.fantasyit.maid_storage_manager.maid.task.StorageManageTask;
import studio.fantasyit.maid_storage_manager.registry.ItemRegistry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Server authority for the communication terminal's warehouse view and request-list automation. */
public final class NetworkWarehouseService {
    private static final double NEARBY_HANDOFF_DISTANCE = 2.75D;
    private static final int MAX_INVENTORY_ENTRIES = 512;

    private NetworkWarehouseService() {
    }

    public static void handle(ServerPlayer sender, NetworkWarehouseActionPacket packet) {
        if (packet == null || packet.courier() == null || packet.terminal() == null
                || !TerminalAccountService.authorizesMailbox(
                sender, packet.terminal(), packet.courier(), packet.mailbox())) return;
        switch (packet.action()) {
            case REFRESH -> update(sender, packet.courier(), packet.mailbox());
            case SELECT_WAREHOUSE -> selectWarehouse(
                    sender, packet.courier(), packet.warehouse(), packet.mailbox());
            case SUBMIT_REQUEST -> submitRequest(sender, packet);
            case CONFIRM_DEPOSIT -> confirmDeposit(
                    sender, packet.courier(), packet.warehouse(), packet.mailbox());
        }
    }

    public static void update(ServerPlayer viewer, UUID courierId) {
        update(viewer, courierId, null);
    }

    private static void update(ServerPlayer viewer, UUID courierId, MailboxKey mailbox) {
        NetworkWarehouseSnapshot.Snapshot snapshot = build(viewer, courierId, mailbox, false);
        ExtensionNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> viewer),
                new NetworkWarehouseSnapshotPacket(courierId, snapshot));
    }

    public static void updateAuthorized(ServerPlayer viewer, UUID courierId, MailboxKey mailbox) {
        NetworkWarehouseSnapshot.Snapshot snapshot = build(viewer, courierId, mailbox, true);
        ExtensionNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> viewer),
                new NetworkWarehouseSnapshotPacket(courierId, snapshot));
    }

    private static NetworkWarehouseSnapshot.Snapshot build(ServerPlayer viewer, UUID courierId,
                                                            MailboxKey selectedMailbox,
                                                            boolean accountAuthorized) {
        EntityMaid courier = findMaid(viewer.getServer(), courierId);
        if (courier == null) {
            return unavailable(viewer, NetworkWarehouseSnapshot.Blocker.COURIER_OFFLINE);
        }
        if (!accountAuthorized && (!courier.isOwnedBy(viewer)
                || !courier.getTask().getUid().equals(CourierTask.TASK_ID))) {
            return new NetworkWarehouseSnapshot.Snapshot(
                    true, false, selectedMailbox, null, 0L, null, "",
                    NetworkWarehouseSnapshot.InventoryState.UNAVAILABLE,
                    -1L, 0L, List.of(), false, false, false, false, false,
                    NetworkWarehouseSnapshot.Blocker.UNAUTHORIZED,
                    point(viewer), point(courier), null, null, null);
        }

        CourierData.Data courierData = CourierData.get(courier);
        UUID warehouseId = courierData.warehouse();
        CourierData.WarehouseBinding binding = courierData.binding(warehouseId);
        MailboxKey mailbox = selectedMailbox != null ? selectedMailbox
                : binding == null ? null
                : new MailboxKey(binding.mailboxDimension(), binding.mailboxPos());
        MailboxWarehouseData.WarehouseSnapshot warehouseSnapshot =
                MailboxWarehouseData.get(viewer.getServer()).warehouse(mailbox);
        if (mailbox == null || warehouseSnapshot == null || !warehouseSnapshot.hasManagers()) {
            return new NetworkWarehouseSnapshot.Snapshot(
                    true, true, mailbox, null,
                    warehouseSnapshot == null ? 0L : warehouseSnapshot.generation(),
                    null, "", NetworkWarehouseSnapshot.InventoryState.UNAVAILABLE,
                    -1L, 0L, List.of(), EnderPocketCompat.isEquipped(courier),
                    nearby(viewer, courier), hasFixedDelivery(courierData),
                    hasEditableHeldRequestList(courier),
                    CourierService.hasActiveTransaction(courier),
                    NetworkWarehouseSnapshot.Blocker.UNBOUND,
                    point(viewer), point(courier), null, null, deliveryPoint(courierData));
        }

        EntityMaid warehouse = selectManager(viewer.getServer(), warehouseSnapshot, mailbox.position());
        warehouseId = warehouse == null ? warehouseSnapshot.managers().get(0) : warehouse.getUUID();
        String warehouseName = warehouse == null
                ? binding == null ? warehouseId.toString().substring(0, 8) : binding.warehouseName()
                : MaidDisplayName.encode(warehouse);

        boolean authorized = accountAuthorized || warehouse != null
                && WarehouseCourierData.get(warehouse).isAuthorized(courier.getUUID());
        if (!authorized || warehouse != null
                && !warehouse.getTask().getUid().equals(StorageManageTask.TASK_ID)) {
            return new NetworkWarehouseSnapshot.Snapshot(
                    true, false, mailbox, warehouseSnapshot.inventoryList(),
                    warehouseSnapshot.generation(), warehouseId, warehouseName,
                    NetworkWarehouseSnapshot.InventoryState.UNAVAILABLE,
                    -1L, 0L, List.of(), EnderPocketCompat.isEquipped(courier),
                    nearby(viewer, courier), hasFixedDelivery(courierData),
                    hasEditableHeldRequestList(courier), false,
                    NetworkWarehouseSnapshot.Blocker.UNAUTHORIZED,
                    point(viewer), point(courier), point(warehouse),
                    point(mailbox.dimension(), mailbox.position()),
                    deliveryPoint(courierData));
        }

        InventorySource source = inventorySource(viewer.getServer(), warehouseSnapshot,
                viewer.serverLevel().getDayTime());
        boolean active = CourierService.hasActiveTransaction(courier)
                || warehouseSnapshot.managers().stream()
                .map(id -> findMaid(viewer.getServer(), id))
                .filter(java.util.Objects::nonNull)
                .anyMatch(CourierService::hasActiveWarehouseTransaction);
        NetworkWarehouseSnapshot.Blocker blocker = active
                ? NetworkWarehouseSnapshot.Blocker.ACTIVE_TRANSACTION
                : source.state == NetworkWarehouseSnapshot.InventoryState.UNAVAILABLE
                ? NetworkWarehouseSnapshot.Blocker.INVENTORY_LIST_UNAVAILABLE
                : NetworkWarehouseSnapshot.Blocker.NONE;
        return new NetworkWarehouseSnapshot.Snapshot(
                true, true, mailbox, warehouseSnapshot.inventoryList(),
                warehouseSnapshot.generation(), warehouseId, warehouseName, source.state,
                source.publishedGameTime, source.age, source.inventory,
                EnderPocketCompat.isEquipped(courier), nearby(viewer, courier),
                hasFixedDelivery(courierData), hasEditableHeldRequestList(courier),
                active, blocker,
                point(viewer), point(courier), point(warehouse),
                point(mailbox.dimension(), mailbox.position()),
                deliveryPoint(courierData));
    }

    private static NetworkWarehouseSnapshot.Snapshot unavailable(
            ServerPlayer viewer, NetworkWarehouseSnapshot.Blocker blocker) {
        return new NetworkWarehouseSnapshot.Snapshot(
                false, true, null, null, 0L, null, "",
                NetworkWarehouseSnapshot.InventoryState.UNAVAILABLE,
                -1L, 0L, List.of(), false, false, false, false, false, blocker,
                point(viewer), null, null, null, null);
    }

    private static void selectWarehouse(
            ServerPlayer sender, UUID courierId, UUID warehouseId, MailboxKey mailbox) {
        EntityMaid courier = findAuthorizedCourier(sender, courierId);
        if (courier == null || warehouseId == null) {
            update(sender, courierId, mailbox);
            return;
        }
        CourierData.Data data = CourierData.get(courier);
        if (!warehouseId.equals(data.warehouse()) && data.binding(warehouseId) != null) {
            CourierService.selectWarehouseAuthorized(sender, courier, warehouseId);
        }
        LogisticsTrackerService.update(sender, courierId);
        update(sender, courierId, mailbox);
    }

    private static void submitRequest(ServerPlayer sender,
                                      NetworkWarehouseActionPacket packet) {
        EntityMaid courier = findAuthorizedCourier(sender, packet.courier());
        MailboxWarehouseData.WarehouseSnapshot mailboxWarehouse =
                MailboxWarehouseData.get(sender.getServer()).warehouse(packet.mailbox());
        if (courier == null || mailboxWarehouse == null) {
            fail(sender, "message.maid_storage_manager_extension.network_warehouse.invalid_courier");
            update(sender, packet.courier(), packet.mailbox());
            return;
        }
        if (!TerminalAccountService.prepareCourierForMailbox(
                sender, courier, packet.mailbox())) {
            fail(sender, "message.maid_storage_manager_extension.network_warehouse.invalid_warehouse");
            update(sender, packet.courier(), packet.mailbox());
            return;
        }
        CourierData.Data courierData = CourierData.get(courier);
        UUID selectedManager = courierData.warehouse();
        if (selectedManager == null || !mailboxWarehouse.managers().contains(selectedManager)) {
            fail(sender, "message.maid_storage_manager_extension.network_warehouse.invalid_warehouse");
            update(sender, packet.courier(), packet.mailbox());
            return;
        }

        EntityMaid warehouse = findMaid(sender.getServer(), selectedManager);
        if (warehouse == null || !warehouse.getTask().getUid().equals(StorageManageTask.TASK_ID)
                || !WarehouseCourierData.get(warehouse).isAuthorized(courier.getUUID())) {
            fail(sender, "message.maid_storage_manager_extension.network_warehouse.invalid_warehouse");
            updateBoth(sender, packet.courier(), packet.mailbox());
            return;
        }
        if (CourierService.hasActiveTransaction(courier)
                || CourierService.hasActiveWarehouseTransaction(warehouse)) {
            fail(sender, "message.maid_storage_manager_extension.network_warehouse.busy");
            updateBoth(sender, packet.courier(), packet.mailbox());
            return;
        }

        InventorySource source = inventorySource(
                sender.getServer(), mailboxWarehouse, sender.serverLevel().getDayTime());
        if (source.state == NetworkWarehouseSnapshot.InventoryState.UNAVAILABLE) {
            fail(sender, "message.maid_storage_manager_extension.network_warehouse.no_inventory_list");
            updateBoth(sender, packet.courier(), packet.mailbox());
            return;
        }
        if (source.state == NetworkWarehouseSnapshot.InventoryState.STALE && !packet.acceptStale()) {
            fail(sender, "message.maid_storage_manager_extension.network_warehouse.stale_confirm");
            updateBoth(sender, packet.courier(), packet.mailbox());
            return;
        }
        if (packet.deliveryTarget() == NetworkWarehouseActionPacket.DeliveryTarget.FIXED_CHEST
                && !hasFixedDelivery(courierData)) {
            fail(sender, "message.maid_storage_manager_extension.network_warehouse.no_delivery_chest");
            updateBoth(sender, packet.courier(), packet.mailbox());
            return;
        }

        List<NetworkWarehouseActionPacket.RequestedItem> normalized = normalize(
                packet.requestedItems(), source.inventory);
        if (normalized.isEmpty()) {
            fail(sender, "message.maid_storage_manager_extension.network_warehouse.invalid_items");
            updateBoth(sender, packet.courier(), packet.mailbox());
            return;
        }

        InteractionHand requestHand = editableHeldRequestHand(courier);
        if (requestHand == null) {
            fail(sender, "message.maid_storage_manager_extension.network_warehouse.no_held_request_list");
            updateBoth(sender, packet.courier(), packet.mailbox());
            return;
        }
        ItemStack request = courier.getItemInHand(requestHand);
        courierData.dispatchSource(CourierData.DispatchSource.TERMINAL);
        courier.setAndSyncData(CourierData.KEY, courierData);
        NetworkWarehouseRequestFactory.update(
                request, sender.getUUID(), courier.getUUID(),
                warehouse.getUUID(), sender.serverLevel().getGameTime(),
                packet.deliveryTarget(), normalized);
        CourierRequestTarget.write(request, sender.blockPosition(),
                sender.serverLevel().dimension().location());
        CourierRequestTarget.forceOwnerDelivery(request,
                packet.deliveryTarget() == NetworkWarehouseActionPacket.DeliveryTarget.PLAYER);

        courier.setItemInHand(requestHand, request);
        courier.swing(requestHand, true);
        sender.sendSystemMessage(Component.translatable(
                "message.maid_storage_manager_extension.network_warehouse.request_list_updated"));
        updateBoth(sender, packet.courier(), packet.mailbox());
    }

    private static void confirmDeposit(
            ServerPlayer sender, UUID courierId, UUID warehouseId, MailboxKey mailbox) {
        EntityMaid courier = findAuthorizedCourier(sender, courierId);
        if (courier == null || warehouseId == null) {
            fail(sender, "message.maid_storage_manager_extension.network_warehouse.invalid_courier");
            update(sender, courierId, mailbox);
            return;
        }
        if (!TerminalAccountService.prepareCourierForMailbox(sender, courier, mailbox)) {
            fail(sender, "message.maid_storage_manager_extension.network_warehouse.invalid_warehouse");
            update(sender, courierId, mailbox);
            return;
        }
        CourierData.Data data = CourierData.get(courier);
        data.dispatchSource(CourierData.DispatchSource.TERMINAL);
        courier.setAndSyncData(CourierData.KEY, data);
        CourierService.confirmDepositAuthorized(sender, courier);
        updateBoth(sender, courierId, mailbox);
    }

    private static List<NetworkWarehouseActionPacket.RequestedItem> normalize(
            List<NetworkWarehouseActionPacket.RequestedItem> requested,
            List<NetworkWarehouseSnapshot.InventoryEntry> available) {
        if (requested == null || requested.isEmpty()
                || requested.size() > NetworkWarehouseActionPacket.MAX_REQUEST_LINES) {
            return List.of();
        }
        List<NetworkWarehouseActionPacket.RequestedItem> result = new ArrayList<>();
        for (NetworkWarehouseActionPacket.RequestedItem line : requested) {
            if (line == null || line.prototype().isEmpty() || line.amount() <= 0
                    || line.amount() > NetworkWarehouseActionPacket.MAX_REQUEST_AMOUNT) {
                return List.of();
            }
            NetworkWarehouseSnapshot.InventoryEntry source = available.stream()
                    .filter(entry -> ItemStack.isSameItemSameTags(
                            entry.prototype(), line.prototype()))
                    .findFirst().orElse(null);
            if (source == null) return List.of();
            int existing = result.stream()
                    .filter(entry -> ItemStack.isSameItemSameTags(
                            entry.prototype(), source.prototype()))
                    .mapToInt(NetworkWarehouseActionPacket.RequestedItem::amount).sum();
            long total = (long) existing + line.amount();
            if (total > source.available()) return List.of();
            if (existing == 0) {
                result.add(new NetworkWarehouseActionPacket.RequestedItem(
                        source.prototype(), line.amount()));
            } else {
                for (int i = 0; i < result.size(); i++) {
                    var current = result.get(i);
                    if (ItemStack.isSameItemSameTags(current.prototype(), source.prototype())) {
                        result.set(i, new NetworkWarehouseActionPacket.RequestedItem(
                                source.prototype(), (int) total));
                        break;
                    }
                }
            }
        }
        return List.copyOf(result);
    }

    private static InventorySource inventorySource(
            MinecraftServer server, MailboxWarehouseData.WarehouseSnapshot warehouse,
            long currentGameTime) {
        if (warehouse == null || !warehouse.hasManagers()) return InventorySource.unavailable();
        UUID listUuid = warehouse.inventoryList();
        long published = warehouse.publishedGameTime();
        if (listUuid == null) return InventorySource.unavailable();

        var data = server.overworld()
                .getCapability(InventoryListDataProvider.INVENTORY_LIST_DATA_CAPABILITY)
                .orElse(null);
        if (data == null || !data.dataMap.containsKey(listUuid)) return InventorySource.unavailable();
        List<NetworkWarehouseSnapshot.InventoryEntry> inventory = aggregate(
                data.dataMap.get(listUuid));
        long age = InventoryFreshnessPolicy.age(published, currentGameTime);
        NetworkWarehouseSnapshot.InventoryState state = InventoryFreshnessPolicy.isStale(
                published, currentGameTime) ? NetworkWarehouseSnapshot.InventoryState.STALE
                : NetworkWarehouseSnapshot.InventoryState.FRESH;
        return new InventorySource(state, published, age, inventory);
    }

    private static EntityMaid selectManager(
            MinecraftServer server, MailboxWarehouseData.WarehouseSnapshot warehouse,
            BlockPos mailboxPos) {
        if (warehouse == null) return null;
        return warehouse.managers().stream()
                .map(id -> findMaid(server, id))
                .filter(java.util.Objects::nonNull)
                .filter(EntityMaid::isAlive)
                .filter(maid -> maid.getTask().getUid().equals(StorageManageTask.TASK_ID))
                .filter(maid -> !CourierService.hasActiveWarehouseTransaction(maid))
                .filter(maid -> !ExtensionMemoryUtil.getMiscSort(maid).hasInFlight())
                .filter(maid -> maid.level().dimension().location().equals(
                        warehouse.key().dimension()))
                .min(Comparator.comparingDouble(maid ->
                        maid.distanceToSqr(mailboxPos.getCenter())))
                .orElse(null);
    }

    static List<NetworkWarehouseSnapshot.InventoryEntry> aggregate(
            Map<String, List<InventoryItem>> groupedInventory) {
        List<NetworkWarehouseSnapshot.InventoryEntry> result = new ArrayList<>();
        if (groupedInventory == null || groupedInventory.isEmpty()) return List.of();
        for (List<InventoryItem> group : groupedInventory.values()) {
            if (group == null) continue;
            for (InventoryItem item : group) {
                if (item == null || item.itemStack == null || item.itemStack.isEmpty()
                        || item.totalCount <= 0) continue;
                int existing = -1;
                for (int j = 0; j < result.size(); j++) {
                    if (ItemStack.isSameItemSameTags(
                            result.get(j).prototype(), item.itemStack)) {
                        existing = j;
                        break;
                    }
                }
                if (existing >= 0) {
                    var current = result.get(existing);
                    long total = (long) current.available() + item.totalCount;
                    result.set(existing, new NetworkWarehouseSnapshot.InventoryEntry(
                            current.prototype(), (int) Math.min(Integer.MAX_VALUE, total)));
                } else if (result.size() < MAX_INVENTORY_ENTRIES) {
                    result.add(new NetworkWarehouseSnapshot.InventoryEntry(
                            item.itemStack, item.totalCount));
                }
            }
        }
        result.sort(Comparator.comparing(entry -> {
            ResourceLocation key = ForgeRegistries.ITEMS.getKey(entry.prototype().getItem());
            return key == null ? "" : key.toString();
        }));
        return List.copyOf(result);
    }

    private static EntityMaid findAuthorizedCourier(ServerPlayer viewer, UUID courierId) {
        EntityMaid courier = findMaid(viewer.getServer(), courierId);
        if (courier == null || !(courier.level() instanceof ServerLevel level)) return null;
        return MaidRoleService.ensureCourier(level, courier) == MaidRoleService.Result.READY
                ? courier : null;
    }

    private static EntityMaid findMaid(MinecraftServer server, UUID id) {
        return TerminalAccountService.findMaid(server, id);
    }

    private static boolean nearby(ServerPlayer player, EntityMaid courier) {
        return player.level() == courier.level()
                && player.distanceToSqr(courier) <= NEARBY_HANDOFF_DISTANCE * NEARBY_HANDOFF_DISTANCE;
    }

    private static boolean hasEditableHeldRequestList(EntityMaid courier) {
        return editableHeldRequestHand(courier) != null;
    }

    private static InteractionHand editableHeldRequestHand(EntityMaid courier) {
        if (courier == null) return null;
        if (CourierService.canEditHeldRequestList(courier.getMainHandItem())) {
            return InteractionHand.MAIN_HAND;
        }
        if (CourierService.canEditHeldRequestList(courier.getOffhandItem())) {
            return InteractionHand.OFF_HAND;
        }
        return null;
    }

    private static boolean hasFixedDelivery(CourierData.Data data) {
        return data.deliveryPos() != null && data.deliveryDimension() != null;
    }

    private static NetworkWarehouseSnapshot.MapPoint deliveryPoint(CourierData.Data data) {
        return point(data.deliveryDimension(), data.deliveryPos());
    }

    private static NetworkWarehouseSnapshot.MapPoint point(Entity entity) {
        if (entity == null) return null;
        return point(entity.level().dimension().location(), entity.blockPosition());
    }

    private static NetworkWarehouseSnapshot.MapPoint point(
            ResourceLocation dimension, BlockPos position) {
        return dimension == null || position == null
                ? null : new NetworkWarehouseSnapshot.MapPoint(dimension, position);
    }

    private static void updateBoth(
            ServerPlayer sender, UUID courierId, MailboxKey mailbox) {
        LogisticsTrackerService.update(sender, courierId);
        update(sender, courierId, mailbox);
    }

    private static void fail(ServerPlayer sender, String key) {
        sender.sendSystemMessage(Component.translatable(key));
    }

    private record InventorySource(NetworkWarehouseSnapshot.InventoryState state,
                                   long publishedGameTime, long age,
                                   List<NetworkWarehouseSnapshot.InventoryEntry> inventory) {
        private static InventorySource unavailable() {
            return new InventorySource(NetworkWarehouseSnapshot.InventoryState.UNAVAILABLE,
                    -1L, 0L, List.of());
        }
    }
}
