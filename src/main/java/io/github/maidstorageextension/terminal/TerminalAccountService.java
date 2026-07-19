package io.github.maidstorageextension.terminal;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import io.github.maidstorageextension.block.CourierWarehouseStationBlockEntity;
import io.github.maidstorageextension.compat.EnderPocketCompat;
import io.github.maidstorageextension.data.CourierData;
import io.github.maidstorageextension.data.DriverData;
import io.github.maidstorageextension.data.MaintenanceStatusData;
import io.github.maidstorageextension.item.LogisticsTrackerItem;
import io.github.maidstorageextension.item.InventoryMaintenanceDevice;
import io.github.maidstorageextension.logistics.LogisticsDisplayName;
import io.github.maidstorageextension.logistics.LogisticsTrackerService;
import io.github.maidstorageextension.logistics.NetworkWarehouseService;
import io.github.maidstorageextension.maid.ExtensionMemoryUtil;
import io.github.maidstorageextension.maid.memory.PeriodicScanMemory;
import io.github.maidstorageextension.maid.courier.CourierService;
import io.github.maidstorageextension.maid.task.CourierTask;
import io.github.maidstorageextension.maid.task.DriverTask;
import io.github.maidstorageextension.network.ExtensionNetwork;
import io.github.maidstorageextension.network.TerminalAccountActionPacket;
import io.github.maidstorageextension.network.TerminalAccountSnapshotPacket;
import io.github.maidstorageextension.network.TerminalMailboxActionPacket;
import io.github.maidstorageextension.network.TerminalNoticePacket;
import io.github.maidstorageextension.remote.RemoteMaidService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.PacketDistributor;
import studio.fantasyit.maid_storage_manager.maid.task.StorageManageTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Authenticated entry point shared by both terminal pages. */
public final class TerminalAccountService {
    private static final int MAX_FAILURES = 5;
    private static final long FAILURE_WINDOW_MS = 30_000L;
    private static final Map<UUID, LoginFailures> LOGIN_FAILURES = new HashMap<>();

    public record Session(TerminalAccountData data, TerminalAccountData.Account account,
                          ItemStack terminal) { }

    private record LoginFailures(long windowStart, int count) { }

    private record ScanRequestResult(boolean success, String messageKey) { }

    private TerminalAccountService() {
    }

    public static void handle(ServerPlayer sender, TerminalAccountActionPacket packet) {
        if (packet == null || packet.terminal() == null) return;
        switch (packet.action()) {
            case REFRESH -> update(sender, packet.terminal());
            case CREATE -> create(sender, packet.terminal(), packet.username(), packet.secret());
            case LOGIN -> login(sender, packet.terminal(), packet.username(), packet.secret());
            case LOGOUT -> logout(sender, packet.terminal());
            case SELECT_COURIER, SELECT_DRIVER -> select(sender, packet.terminal(), packet.target(),
                    packet.action() == TerminalAccountActionPacket.Action.SELECT_DRIVER);
            case UNREGISTER_MAID -> unregister(sender, packet.terminal(), packet.target());
            case CHANGE_PASSWORD -> changePassword(sender, packet.terminal(), packet.secret());
        }
    }

    public static Session authenticate(ServerPlayer player, UUID terminalId) {
        if (player == null || terminalId == null) return null;
        ItemStack stack = findTerminal(player, terminalId);
        if (stack.isEmpty()) return null;
        UUID accountId = LogisticsTrackerItem.getAccountId(stack);
        byte[] token = LogisticsTrackerItem.getAccessToken(stack);
        TerminalAccountData data = TerminalAccountData.get(player.getServer());
        if (!data.verifyGrant(accountId, terminalId, token)) return null;
        TerminalAccountData.Account account = data.byId(accountId);
        return account == null ? null : new Session(data, account, stack);
    }

    public static boolean authorizes(ServerPlayer player, UUID terminalId, UUID maid) {
        Session session = authenticate(player, terminalId);
        return session != null && session.data.belongsTo(session.account, maid);
    }

    public static boolean authorizesMailbox(
            ServerPlayer player, UUID terminalId, UUID maid, MailboxKey mailbox) {
        Session session = authenticate(player, terminalId);
        return session != null && session.data.belongsTo(session.account, maid)
                && session.data.selectMailbox(session.account, mailbox);
    }

    public static UUID selectedCourier(ServerPlayer player, UUID terminalId) {
        Session session = authenticate(player, terminalId);
        return session == null ? null : session.account.selectedCourier();
    }

    public static UUID selectedDriver(ServerPlayer player, UUID terminalId) {
        Session session = authenticate(player, terminalId);
        return session == null ? null : session.account.selectedDriver();
    }

    public static void registerMaid(ServerPlayer player, ItemStack stack, EntityMaid maid) {
        UUID terminalId = LogisticsTrackerItem.ensureTerminalId(stack);
        Session session = authenticate(player, terminalId);
        if (session == null) {
            message(player, "message.maid_storage_manager_extension.terminal.login_required");
            update(player, terminalId);
            return;
        }
        if (!maid.isOwnedBy(player)) {
            message(player, "message.maid_storage_manager_extension.logistics_tracker.not_owner");
            return;
        }
        TerminalAccountData.RegistrationResult result = session.data.register(session.account, maid.getUUID());
        String key = switch (result) {
            case ADDED -> "message.maid_storage_manager_extension.terminal.maid_registered";
            case ALREADY_REGISTERED -> "message.maid_storage_manager_extension.terminal.maid_already_registered";
            case OWNED_BY_OTHER_ACCOUNT -> "message.maid_storage_manager_extension.terminal.maid_other_account";
            case LIMIT_REACHED -> "message.maid_storage_manager_extension.terminal.maid_limit";
            case INVALID -> "message.maid_storage_manager_extension.terminal.registration_failed";
        };
        player.sendSystemMessage(Component.translatable(key, maid.getName()));
        update(player, terminalId);
    }

    public static void handleMailbox(ServerPlayer player, TerminalMailboxActionPacket packet) {
        Session session = authenticate(player, packet.terminal());
        if (session == null) return;
        TerminalAccountData.Mailbox registered = session.account.mailboxes().stream()
                .filter(value -> value.sameLocation(packet.dimension(), packet.position()))
                .findFirst().orElse(null);
        if (registered == null) return;
        MailboxKey mailboxKey = new MailboxKey(registered.dimension(), registered.position());
        if (packet.action() == TerminalMailboxActionPacket.Action.UNREGISTER) {
            session.data.unregisterMailbox(session.account, packet.dimension(), packet.position());
            update(player, packet.terminal());
            return;
        }
        session.data.selectMailbox(session.account, mailboxKey);
        UUID courierId = session.account.selectedCourier();
        EntityMaid courier = findMaid(player.getServer(), courierId);
        ServerLevel level = level(player.getServer(), registered.dimension());
        CourierData.WarehouseBinding binding = level != null
                && level.getBlockEntity(registered.position())
                instanceof CourierWarehouseStationBlockEntity station
                ? station.binding(level) : null;
        if (packet.action() == TerminalMailboxActionPacket.Action.REQUEST_SCAN) {
            activateMailbox(player, courier, registered.warehouse(), binding);
            sendNotice(player, packet.terminal(),
                    requestImmediateScan(player.getServer(), mailboxKey));
            update(player, packet.terminal());
            return;
        }
        if (courier == null) return;
        if (packet.action() == TerminalMailboxActionPacket.Action.ACTIVATE) {
            if (courier.getTask().getUid().equals(CourierTask.TASK_ID)
                    && !activateMailbox(player, courier, registered.warehouse(), binding)) {
                message(player, "message.maid_storage_manager_extension.terminal.mailbox_invalid");
            }
            update(player, packet.terminal());
            return;
        }
        if (binding == null) {
            message(player, "message.maid_storage_manager_extension.terminal.mailbox_invalid");
            return;
        }
        switch (packet.action()) {
            case BIND -> CourierService.bindStationAuthorized(player, courier, binding);
            case UNBIND -> CourierService.unbindWarehouseAuthorized(
                    player, courier, binding.warehouse());
            case SELECT_DEFAULT -> CourierService.selectWarehouseAuthorized(
                    player, courier, binding.warehouse());
            default -> { }
        }
        update(player, packet.terminal());
    }

    /**
     * Makes a registered mailbox the selected courier's active warehouse. Existing bindings keep
     * working while the station chunk is unloaded; a first-time binding still requires the real,
     * approved station so terminal access never invents warehouse authority.
     */
    private static boolean activateMailbox(ServerPlayer player, EntityMaid courier,
                                           UUID warehouseId,
                                           CourierData.WarehouseBinding stationBinding) {
        UUID targetWarehouse = stationBinding == null ? warehouseId : stationBinding.warehouse();
        if (courier == null || targetWarehouse == null
                || !courier.getTask().getUid().equals(CourierTask.TASK_ID)) return false;
        CourierData.Data data = CourierData.get(courier);
        if (data.binding(targetWarehouse) == null) {
            if (stationBinding == null) return false;
            CourierService.bindStationAuthorized(player, courier, stationBinding);
            data = CourierData.get(courier);
        }
        if (data.binding(targetWarehouse) == null) return false;
        if (!targetWarehouse.equals(data.warehouse())) {
            CourierService.selectWarehouseAuthorized(player, courier, targetWarehouse);
            data = CourierData.get(courier);
        }
        return targetWarehouse.equals(data.warehouse());
    }

    public static boolean prepareCourierForMailbox(
            ServerPlayer player, EntityMaid courier, MailboxKey mailbox) {
        if (player == null || courier == null || mailbox == null) return false;
        ServerLevel level = level(player.getServer(), mailbox.dimension());
        if (level == null || !(level.getBlockEntity(mailbox.position())
                instanceof CourierWarehouseStationBlockEntity station)) return false;
        CourierData.WarehouseBinding binding = station.binding(level);
        return binding != null && activateMailbox(
                player, courier, binding.warehouse(), binding);
    }

    private static ScanRequestResult requestImmediateScan(
            MinecraftServer server, MailboxKey mailboxKey) {
        MailboxWarehouseData.WarehouseSnapshot warehouse =
                MailboxWarehouseData.get(server).warehouse(mailboxKey);
        if (warehouse == null || warehouse.managers().isEmpty()) {
            return new ScanRequestResult(false,
                    "message.maid_storage_manager_extension.terminal.scan_warehouse_offline");
        }
        int requested = 0;
        int queued = 0;
        for (UUID manager : warehouse.managers()) {
            EntityMaid maid = findMaid(server, manager);
            if (maid == null || !maid.getTask().getUid().equals(StorageManageTask.TASK_ID)) continue;
            ItemStack inspectionDevice = InventoryMaintenanceDevice.findOn(maid)
                    .orElse(ItemStack.EMPTY);
            if (inspectionDevice.isEmpty() || !InventoryMaintenanceDevice.isBound(inspectionDevice)) {
                continue;
            }
            PeriodicScanMemory scan = ExtensionMemoryUtil.getPeriodicScan(maid);
            if (scan.getPhase() == PeriodicScanMemory.Phase.IDLE) requested++;
            else queued++;
            // The persisted boolean is deliberately deduplicated while a manager is busy.
            scan.requestImmediateScan();
        }
        if (requested + queued == 0) {
            return new ScanRequestResult(false,
                    "message.maid_storage_manager_extension.terminal.scan_no_available_manager");
        }
        return new ScanRequestResult(true, queued > 0
                ? "message.maid_storage_manager_extension.terminal.scan_queued"
                : "message.maid_storage_manager_extension.terminal.scan_requested");
    }

    /** Pushes a newly published physical inventory list to every terminal viewing this mailbox. */
    public static void refreshMailboxViewers(MinecraftServer server, MailboxKey mailboxKey) {
        if (server == null || mailboxKey == null) return;
        TerminalAccountData data = TerminalAccountData.get(server);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
                ItemStack stack = player.getInventory().getItem(slot);
                if (!(stack.getItem() instanceof LogisticsTrackerItem)) continue;
                UUID accountId = LogisticsTrackerItem.getAccountId(stack);
                UUID terminalId = LogisticsTrackerItem.getTerminalId(stack);
                TerminalAccountData.Account account = data.byId(accountId);
                if (terminalId != null && account != null
                        && mailboxKey.equals(account.selectedMailbox())) {
                    update(player, terminalId);
                }
            }
        }
    }

    private static void sendNotice(ServerPlayer player, UUID terminal,
                                   ScanRequestResult result) {
        message(player, result.messageKey());
        ExtensionNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new TerminalNoticePacket(terminal, result.messageKey(), result.success()));
    }

    /** Sneak-use a configured, approved mailbox to add it to the logged-in account. */
    public static boolean registerMailbox(ServerPlayer player, UUID terminalId,
                                          ServerLevel level, BlockPos position) {
        if (!(level.getBlockEntity(position) instanceof CourierWarehouseStationBlockEntity station)) {
            return false;
        }
        Session session = authenticate(player, terminalId);
        if (session == null) {
            message(player, "message.maid_storage_manager_extension.terminal.login_required");
            return true;
        }
        var binding = station.binding(level);
        if (binding == null) {
            message(player, "message.maid_storage_manager_extension.terminal.mailbox_invalid");
            return true;
        }
        boolean changed = session.data.registerMailbox(session.account,
                new TerminalAccountData.Mailbox(level.dimension().location(), position,
                        binding.warehouse(), binding.warehouseName()));
        message(player, changed
                ? "message.maid_storage_manager_extension.terminal.mailbox_registered"
                : "message.maid_storage_manager_extension.terminal.mailbox_limit");
        update(player, terminalId);
        return true;
    }

    public static void update(ServerPlayer player, UUID terminalId) {
        Session session = authenticate(player, terminalId);
        TerminalAccountSnapshot.Snapshot snapshot = session == null
                ? TerminalAccountSnapshot.Snapshot.loggedOut("") : snapshot(player, session);
        send(player, terminalId, snapshot);
        if (session == null) return;
        UUID courier = session.account.selectedCourier();
        if (courier != null) {
            LogisticsTrackerService.updateAuthorized(player, courier);
            NetworkWarehouseService.updateAuthorized(
                    player, courier, session.account.selectedMailbox());
        }
        MaidTransportService.update(player, terminalId);
    }

    private static void create(ServerPlayer player, UUID terminalId, String username, String password) {
        if (rateLimited(player)) {
            sendLoggedOut(player, terminalId, "gui.maid_storage_manager_extension.terminal.too_many_attempts");
            return;
        }
        ItemStack stack = findTerminal(player, terminalId);
        if (stack.isEmpty()) return;
        TerminalAccountData data = TerminalAccountData.get(player.getServer());
        TerminalAccountData.Account account = data.create(username, password);
        if (account == null) {
            recordFailure(player);
            sendLoggedOut(player, terminalId, "gui.maid_storage_manager_extension.terminal.create_failed");
            return;
        }
        remember(player, stack, terminalId, data, account);
    }

    private static void login(ServerPlayer player, UUID terminalId, String username, String password) {
        if (rateLimited(player)) {
            sendLoggedOut(player, terminalId, "gui.maid_storage_manager_extension.terminal.too_many_attempts");
            return;
        }
        ItemStack stack = findTerminal(player, terminalId);
        if (stack.isEmpty()) return;
        TerminalAccountData data = TerminalAccountData.get(player.getServer());
        TerminalAccountData.Account account = data.authenticate(username, password);
        if (account == null) account = data.authenticateReset(username, password);
        if (account == null) {
            recordFailure(player);
            sendLoggedOut(player, terminalId, "gui.maid_storage_manager_extension.terminal.login_failed");
            return;
        }
        remember(player, stack, terminalId, data, account);
    }

    private static void remember(ServerPlayer player, ItemStack stack, UUID terminalId,
                                 TerminalAccountData data, TerminalAccountData.Account account) {
        UUID previous = LogisticsTrackerItem.getAccountId(stack);
        if (previous != null && !previous.equals(account.id())) data.revoke(previous, terminalId);
        LogisticsTrackerItem.rememberAccount(stack, account.id(), data.grant(account, terminalId));
        migrateLegacy(player, stack, data, account);
        LOGIN_FAILURES.remove(player.getUUID());
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
        update(player, terminalId);
    }

    private static void migrateLegacy(ServerPlayer player, ItemStack stack,
                                      TerminalAccountData data, TerminalAccountData.Account account) {
        UUID legacy = LogisticsTrackerItem.getCourier(stack);
        if (legacy == null) return;
        EntityMaid maid = findMaid(player.getServer(), legacy);
        if (maid != null && maid.isOwnedBy(player)) {
            TerminalAccountData.RegistrationResult result = data.register(account, legacy);
            if (result == TerminalAccountData.RegistrationResult.ADDED
                    || result == TerminalAccountData.RegistrationResult.ALREADY_REGISTERED) {
                data.selectCourier(account, legacy);
            }
        }
        CompoundTagAccess.removeLegacy(stack);
    }

    private static void logout(ServerPlayer player, UUID terminalId) {
        ItemStack stack = findTerminal(player, terminalId);
        if (stack.isEmpty()) return;
        UUID account = LogisticsTrackerItem.getAccountId(stack);
        TerminalAccountData.get(player.getServer()).revoke(account, terminalId);
        LogisticsTrackerItem.forgetAccount(stack);
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
        sendLoggedOut(player, terminalId, "");
    }

    private static void select(ServerPlayer player, UUID terminalId, UUID maid, boolean driver) {
        Session session = authenticate(player, terminalId);
        if (session == null || maid == null) return;
        if (driver) session.data.selectDriver(session.account, maid);
        else session.data.selectCourier(session.account, maid);
        update(player, terminalId);
    }

    private static void unregister(ServerPlayer player, UUID terminalId, UUID maid) {
        Session session = authenticate(player, terminalId);
        if (session == null || maid == null) return;
        EntityMaid entity = findMaid(player.getServer(), maid);
        if (entity != null && (CourierService.hasActiveTransaction(entity)
                || DriverData.get(entity).activeTrip())) {
            message(player, "message.maid_storage_manager_extension.terminal.maid_busy");
            return;
        }
        session.data.unregister(session.account, maid);
        update(player, terminalId);
    }

    private static void changePassword(ServerPlayer player, UUID terminalId, String password) {
        Session session = authenticate(player, terminalId);
        if (session == null || !session.data.changePassword(session.account, password)) return;
        byte[] token = session.data.grant(session.account, terminalId);
        LogisticsTrackerItem.rememberAccount(session.terminal, session.account.id(), token);
        update(player, terminalId);
    }

    private static TerminalAccountSnapshot.Snapshot snapshot(ServerPlayer viewer, Session session) {
        List<TerminalAccountSnapshot.Maid> maids = new ArrayList<>();
        for (UUID id : session.account.maids()) {
            EntityMaid maid = findMaid(viewer.getServer(), id);
            CourierData.Data courierData = maid == null ? null : CourierData.get(maid);
            DriverData.Data driverData = maid == null ? null : DriverData.get(maid);
            boolean courierTask = maid != null && maid.getTask().getUid().equals(CourierTask.TASK_ID);
            boolean driverTask = maid != null && maid.getTask().getUid().equals(DriverTask.TASK_ID);
            maids.add(new TerminalAccountSnapshot.Maid(id,
                    maid == null ? id.toString().substring(0, 8) : LogisticsDisplayName.encode(maid.getName()),
                    maid != null, courierTask, driverTask,
                    maid != null && EnderPocketCompat.hasBroom(maid),
                    maid != null && (CourierService.hasActiveTransaction(maid)
                            || driverData.activeTrip()
                            || ExtensionMemoryUtil.getMiscSort(maid).hasInFlight()),
                    maid == null ? "offline" : driverTask
                            ? driverData.phase().name().toLowerCase(java.util.Locale.ROOT)
                            : courierData == null ? "offline"
                            : courierData.phase().name().toLowerCase(java.util.Locale.ROOT),
                    courierData == null ? "none"
                            : courierData.transportMode().name().toLowerCase(java.util.Locale.ROOT),
                    maid == null ? null : maid.level().dimension().location(),
                    maid == null ? null : maid.blockPosition()));
        }
        List<TerminalAccountSnapshot.Mailbox> mailboxes = new ArrayList<>();
        for (TerminalAccountData.Mailbox value : session.account.mailboxes()) {
            MailboxKey key = new MailboxKey(value.dimension(), value.position());
            MailboxWarehouseData.WarehouseSnapshot stored =
                    MailboxWarehouseData.get(viewer.getServer()).warehouse(key);
            List<UUID> managers = stored == null ? List.of() : stored.managers();
            UUID displayManager = managers.isEmpty() ? value.warehouse() : managers.get(0);
            EntityMaid warehouse = managers.stream()
                    .map(id -> findMaid(viewer.getServer(), id))
                    .filter(java.util.Objects::nonNull)
                    .findFirst().orElse(null);
            boolean valid = stored != null && stored.hasManagers();
            boolean warehouseOnline = warehouse != null;
            boolean warehouseOnDuty = managers.stream()
                    .map(id -> findMaid(viewer.getServer(), id))
                    .filter(java.util.Objects::nonNull)
                    .anyMatch(maid -> maid.getTask().getUid().equals(StorageManageTask.TASK_ID));
            List<TerminalAccountSnapshot.WarehouseManager> managerStatuses = managers.stream()
                    .map(id -> managerSnapshot(viewer.getServer(), id))
                    .toList();
            mailboxes.add(new TerminalAccountSnapshot.Mailbox(value.dimension(), value.position(),
                    displayManager, warehouse == null ? value.warehouseName()
                    : warehouse.getName().getString(), valid,
                    warehouseOnline, warehouseOnDuty, managerStatuses));
        }
        return new TerminalAccountSnapshot.Snapshot(true,
                session.account.passwordResetRequired(), session.account.username(),
                session.account.id(), session.account.selectedCourier(),
                session.account.selectedDriver(), session.account.selectedMailbox(),
                maids, mailboxes, "");
    }

    private static TerminalAccountSnapshot.WarehouseManager managerSnapshot(
            MinecraftServer server, UUID managerId) {
        EntityMaid maid = findMaid(server, managerId);
        if (maid == null) {
            return new TerminalAccountSnapshot.WarehouseManager(managerId,
                    managerId.toString().substring(0, 8), "offline", "");
        }
        String name = LogisticsDisplayName.encode(maid.getName());
        if (!maid.getTask().getUid().equals(StorageManageTask.TASK_ID)) {
            return new TerminalAccountSnapshot.WarehouseManager(
                    managerId, name, "off_duty", "");
        }
        PeriodicScanMemory scan = ExtensionMemoryUtil.getPeriodicScan(maid);
        MaintenanceStatusData.Data status = MaintenanceStatusData.get(maid);
        if (scan.isQueuedRefreshRequested()) {
            return new TerminalAccountSnapshot.WarehouseManager(
                    managerId, name, "queued", "");
        }
        if (scan.getPhase() == PeriodicScanMemory.Phase.REFRESH_PENDING
                || status.phase() == MaintenanceStatusData.Phase.REFRESHING) {
            return new TerminalAccountSnapshot.WarehouseManager(
                    managerId, name, "writing", "");
        }
        if (scan.getPhase() != PeriodicScanMemory.Phase.IDLE
                || ExtensionMemoryUtil.getMiscSort(maid).hasInFlight()) {
            return new TerminalAccountSnapshot.WarehouseManager(
                    managerId, name, "inspecting", "");
        }
        if (status.lastResult() == MaintenanceStatusData.Result.SUCCESS) {
            return new TerminalAccountSnapshot.WarehouseManager(
                    managerId, name, "completed", "");
        }
        if (status.lastResult() != MaintenanceStatusData.Result.NEVER) {
            return new TerminalAccountSnapshot.WarehouseManager(
                    managerId, name, "failed", status.lastResult().translationKey());
        }
        return new TerminalAccountSnapshot.WarehouseManager(
                managerId, name, "on_duty", "");
    }

    private static void sendLoggedOut(ServerPlayer player, UUID terminal, String error) {
        send(player, terminal, TerminalAccountSnapshot.Snapshot.loggedOut(error));
    }

    private static void send(ServerPlayer player, UUID terminal,
                             TerminalAccountSnapshot.Snapshot snapshot) {
        ExtensionNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new TerminalAccountSnapshotPacket(terminal, snapshot));
    }

    public static ItemStack findTerminal(ServerPlayer player, UUID terminalId) {
        if (player == null || terminalId == null) return ItemStack.EMPTY;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.getItem() instanceof LogisticsTrackerItem
                    && terminalId.equals(LogisticsTrackerItem.getTerminalId(stack))) return stack;
        }
        return ItemStack.EMPTY;
    }

    public static EntityMaid findMaid(MinecraftServer server, UUID id) {
        return RemoteMaidService.find(server, id);
    }

    private static ServerLevel level(MinecraftServer server, ResourceLocation dimension) {
        return dimension == null ? null : server.getLevel(
                ResourceKey.create(Registries.DIMENSION, dimension));
    }

    private static boolean rateLimited(ServerPlayer player) {
        LoginFailures value = LOGIN_FAILURES.get(player.getUUID());
        return value != null && System.currentTimeMillis() - value.windowStart < FAILURE_WINDOW_MS
                && value.count >= MAX_FAILURES;
    }

    private static void recordFailure(ServerPlayer player) {
        long now = System.currentTimeMillis();
        LoginFailures previous = LOGIN_FAILURES.get(player.getUUID());
        LOGIN_FAILURES.put(player.getUUID(), previous == null
                || now - previous.windowStart >= FAILURE_WINDOW_MS
                ? new LoginFailures(now, 1) : new LoginFailures(previous.windowStart, previous.count + 1));
    }

    private static void message(ServerPlayer player, String key) {
        player.sendSystemMessage(Component.translatable(key));
    }

    /** Keeps legacy tag details out of the public account API. */
    private static final class CompoundTagAccess {
        private static void removeLegacy(ItemStack stack) {
            if (stack.getTag() == null) return;
            stack.getTag().remove(LogisticsTrackerItem.TAG_COURIER);
            stack.getTag().remove(LogisticsTrackerItem.TAG_OWNER);
        }
    }
}
