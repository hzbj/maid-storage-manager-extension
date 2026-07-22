package io.github.maidstorageextension.logistics;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import io.github.maidstorageextension.block.BusinessLicenseBlockEntity;
import io.github.maidstorageextension.data.BusinessLicenseData;
import io.github.maidstorageextension.maid.task.CourierTask;
import io.github.maidstorageextension.license.BusinessLicenseService;
import io.github.maidstorageextension.network.ExtensionNetwork;
import io.github.maidstorageextension.network.MaidLogisticsActionPacket;
import io.github.maidstorageextension.network.MaidLogisticsSnapshotPacket;
import io.github.maidstorageextension.terminal.MailboxKey;
import io.github.maidstorageextension.terminal.MailboxWarehouseData;
import io.github.maidstorageextension.terminal.TerminalAccountData;
import io.github.maidstorageextension.terminal.TerminalAccountService;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;
import studio.fantasyit.maid_storage_manager.capability.InventoryListDataProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Authenticated server boundary for logistics snapshots and route mutations. */
public final class MaidLogisticsService {
    private MaidLogisticsService() {
    }

    public static void handle(ServerPlayer sender, MaidLogisticsActionPacket packet) {
        if (sender == null || packet == null || packet.terminal() == null) return;
        TerminalAccountService.Session session =
                TerminalAccountService.authenticate(sender, packet.terminal());
        if (session == null) {
            send(sender, packet.terminal(), null, "login_required");
            return;
        }
        if (packet.action() == MaidLogisticsActionPacket.Action.REFRESH) {
            send(sender, packet.terminal(), session, "");
            return;
        }
        if (packet.action() == MaidLogisticsActionPacket.Action.OPEN_LICENSE) {
            if (!BusinessLicenseService.openFromTerminal(
                    sender, packet.terminal(), packet.source())) {
                send(sender, packet.terminal(), session, "invalid_license");
            }
            return;
        }

        MaidLogisticsData data = MaidLogisticsData.get(sender.getServer());
        MaidLogisticsData.MutationResult result = switch (packet.action()) {
            case CREATE_ROUTE -> create(sender.getServer(), session, data, packet);
            case UPDATE_ROUTE -> update(sender.getServer(), session, data, packet);
            case DELETE_ROUTE -> delete(sender.getServer(), session, data, packet.route());
            case ADD_SLOT -> mutateSlot(session, data, packet, SlotAction.ADD);
            case REMOVE_SLOT -> mutateSlot(session, data, packet, SlotAction.REMOVE);
            case MOVE_SLOT -> mutateSlot(session, data, packet, SlotAction.MOVE);
            case REFRESH, OPEN_LICENSE -> MaidLogisticsData.MutationResult.OK;
        };
        send(sender, packet.terminal(), session,
                result == MaidLogisticsData.MutationResult.OK ? "" : result.name().toLowerCase());
    }

    public static void update(ServerPlayer sender, UUID terminal) {
        TerminalAccountService.Session session = TerminalAccountService.authenticate(sender, terminal);
        send(sender, terminal, session, session == null ? "login_required" : "");
    }

    private static MaidLogisticsData.MutationResult create(
            MinecraftServer server, TerminalAccountService.Session session, MaidLogisticsData data,
            MaidLogisticsActionPacket packet) {
        if (!validCourier(session, packet.courier())
                || !validRoute(server, session, packet.source(), packet.destination(), packet.lines())) {
            return MaidLogisticsData.MutationResult.INVALID;
        }
        return data.create(session.account().id(), packet.source(), packet.destination(),
                packet.courier(), packet.lines(), null);
    }

    private static MaidLogisticsData.MutationResult update(
            MinecraftServer server, TerminalAccountService.Session session, MaidLogisticsData data,
            MaidLogisticsActionPacket packet) {
        MaidLogisticsData.Route stored = data.route(packet.route());
        if (stored == null || !stored.account().equals(session.account().id())) {
            return MaidLogisticsData.MutationResult.NOT_FOUND;
        }
        if (routeIsActive(server, stored)) return MaidLogisticsData.MutationResult.BUSY;
        if (!validCourier(session, packet.courier())
                || !validRoute(server, session, stored.source(), stored.destination(), packet.lines())) {
            return MaidLogisticsData.MutationResult.INVALID;
        }
        return data.update(session.account().id(), packet.route(), packet.revision(),
                packet.courier(), packet.lines());
    }

    private static MaidLogisticsData.MutationResult delete(
            MinecraftServer server, TerminalAccountService.Session session,
            MaidLogisticsData data, UUID routeId) {
        MaidLogisticsData.Route stored = data.route(routeId);
        if (stored == null || !stored.account().equals(session.account().id())) {
            return MaidLogisticsData.MutationResult.NOT_FOUND;
        }
        if (routeIsActive(server, stored)) return MaidLogisticsData.MutationResult.BUSY;
        return data.delete(session.account().id(), routeId);
    }

    private static boolean routeIsActive(MinecraftServer server, MaidLogisticsData.Route route) {
        EntityMaid courier = TerminalAccountService.findMaid(server, route.courier());
        return courier != null && route.id().equals(MaidLogisticsTransactionService.activeRoute(courier));
    }

    private enum SlotAction { ADD, REMOVE, MOVE }

    private static MaidLogisticsData.MutationResult mutateSlot(
            TerminalAccountService.Session session, MaidLogisticsData data,
            MaidLogisticsActionPacket packet, SlotAction action) {
        if (!validCourier(session, packet.courier())) return MaidLogisticsData.MutationResult.INVALID;
        return switch (action) {
            case ADD -> data.addSlot(session.account().id(), packet.courier(), packet.route());
            case REMOVE -> data.removeSlot(session.account().id(), packet.courier(), packet.firstIndex());
            case MOVE -> data.moveSlot(session.account().id(), packet.courier(),
                    packet.firstIndex(), packet.secondIndex());
        };
    }

    private static boolean validCourier(TerminalAccountService.Session session, UUID courier) {
        return courier != null && session.data().belongsTo(session.account(), courier);
    }

    private static boolean validRoute(MinecraftServer server, TerminalAccountService.Session session,
                                      MaidLogisticsData.NodeRef source,
                                      MaidLogisticsData.NodeRef destination,
                                      List<MaidLogisticsData.CargoLine> lines) {
        if (!MaidLogisticsData.validEndpoints(source, destination)
                || !MaidLogisticsData.validLines(lines)
                || !Level.OVERWORLD.location().equals(source.dimension())
                || !Level.OVERWORLD.location().equals(destination.dimension())
                || !validNode(server, session, source) || !validNode(server, session, destination)) return false;
        for (MaidLogisticsData.CargoLine line : lines) {
            ResourceLocation item = ForgeRegistries.ITEMS.getKey(line.prototype().getItem());
            if (item == null || !licenseAllows(server, source, item)
                    || !licenseAllows(server, destination, item)) return false;
        }
        return true;
    }

    private static boolean licenseAllows(MinecraftServer server,
                                         MaidLogisticsData.NodeRef node, ResourceLocation item) {
        if (node.kind() != MaidLogisticsData.NodeKind.LICENSE) return true;
        BusinessLicenseData.Snapshot license = BusinessLicenseData.get(server).get(node.license());
        return license != null && license.allows(item);
    }

    private static boolean validNode(MinecraftServer server, TerminalAccountService.Session session,
                                     MaidLogisticsData.NodeRef node) {
        if (node == null || !node.valid()) return false;
        if (node.kind() == MaidLogisticsData.NodeKind.LICENSE) {
            if (!session.data().ownsLicense(session.account(), node.license())) return false;
            BusinessLicenseData.Snapshot license = BusinessLicenseData.get(server).get(node.license());
            return license != null && license.dimension().equals(node.dimension())
                    && license.position().equals(node.position()) && !license.containers().isEmpty();
        }
        return session.account().mailboxes().stream().anyMatch(mailbox ->
                mailbox.sameLocation(node.dimension(), node.position()))
                && MailboxWarehouseData.get(server).warehouse(node.mailbox()) != null;
    }

    private static void send(ServerPlayer player, UUID terminal,
                             TerminalAccountService.Session session, String error) {
        MaidLogisticsSnapshot.Snapshot snapshot = session == null
                ? MaidLogisticsSnapshot.Snapshot.empty(error)
                : snapshot(player, session, error);
        ExtensionNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new MaidLogisticsSnapshotPacket(terminal, snapshot));
    }

    private static MaidLogisticsSnapshot.Snapshot snapshot(
            ServerPlayer viewer, TerminalAccountService.Session session, String error) {
        MinecraftServer server = viewer.getServer();
        List<MaidLogisticsSnapshot.Node> nodes = new ArrayList<>();
        BusinessLicenseData licenses = BusinessLicenseData.get(server);
        MaidLogisticsData routes = MaidLogisticsData.get(server);
        for (TerminalAccountData.BusinessLicense registered : session.account().licenses()) {
            BusinessLicenseData.Snapshot license = licenses.get(registered.id());
            boolean valid = license != null && license.dimension().equals(registered.dimension())
                    && license.position().equals(registered.position())
                    && license.owner().equals(viewer.getUUID());
            MaidLogisticsData.NodeRef ref = new MaidLogisticsData.NodeRef(
                    MaidLogisticsData.NodeKind.LICENSE, registered.dimension(),
                    registered.position(), registered.id());
            nodes.add(new MaidLogisticsSnapshot.Node(MaidLogisticsData.NodeKind.LICENSE,
                    registered.id(), license == null ? registered.name() : license.name(),
                    registered.dimension(), registered.position(), valid,
                    0, destinationFull(routes, session.account().id(), ref),
                    license == null ? 0 : license.workers().size(),
                    license == null ? "" : license.mode().name().toLowerCase(),
                    license == null ? List.of() : license.filterItems(),
                    licenseWorkers(server, license),
                    license == null ? 0 : license.containers().size(),
                    license == null ? "license_missing" : license.blocker()));
        }
        for (TerminalAccountData.Mailbox mailbox : session.account().mailboxes()) {
            MailboxKey key = new MailboxKey(mailbox.dimension(), mailbox.position());
            MailboxWarehouseData.WarehouseSnapshot warehouse =
                    MailboxWarehouseData.get(server).warehouse(key);
            boolean valid = warehouse != null && warehouse.hasManagers();
            MaidLogisticsData.NodeRef ref = new MaidLogisticsData.NodeRef(
                    MaidLogisticsData.NodeKind.WAREHOUSE, mailbox.dimension(),
                    mailbox.position(), null);
            nodes.add(new MaidLogisticsSnapshot.Node(MaidLogisticsData.NodeKind.WAREHOUSE,
                    null, mailbox.warehouseName(), mailbox.dimension(), mailbox.position(), valid,
                    inventoryTypes(server, warehouse),
                    destinationFull(routes, session.account().id(), ref),
                    warehouse == null ? 0 : warehouse.managers().size(),
                    "", List.of(), List.of(), 0, ""));
        }

        List<MaidLogisticsSnapshot.Courier> couriers = new ArrayList<>();
        for (UUID id : session.account().maids()) {
            EntityMaid maid = TerminalAccountService.findMaid(server, id);
            if (maid != null && maid.getTask().getUid().equals(CourierTask.TASK_ID)
                    || maid == null) {
                MaidLogisticsData.Schedule schedule = routes.schedule(id);
                couriers.add(new MaidLogisticsSnapshot.Courier(id,
                        maid == null ? offlineMaidName(session.account(), id)
                                : MaidDisplayName.encode(maid),
                        maid != null, maid != null && maid.getTask().getUid().equals(CourierTask.TASK_ID),
                        maid == null ? null : maid.level().dimension().location(),
                        maid == null ? null : maid.blockPosition(), stableColor(id),
                        maid == null ? null : MaidLogisticsTransactionService.activeRoute(maid),
                        maid == null ? List.of() : MaidLogisticsTransactionService.actualCargo(maid),
                        schedule.slots(), schedule.cursor()));
            }
        }
        return new MaidLogisticsSnapshot.Snapshot(session.account().id(),
                server.overworld().getDayTime(), nodes, couriers,
                routes.routes(session.account().id()), error);
    }

    private static boolean destinationFull(MaidLogisticsData data, UUID account,
                                           MaidLogisticsData.NodeRef node) {
        return data.routes(account).stream().anyMatch(route -> route.destination().equals(node)
                && route.status() == MaidLogisticsData.RouteStatus.DESTINATION_FULL);
    }

    private static List<MaidLogisticsSnapshot.LicenseWorker> licenseWorkers(
            MinecraftServer server, BusinessLicenseData.Snapshot license) {
        if (license == null) return List.of();
        List<MaidLogisticsSnapshot.LicenseWorker> workers = new ArrayList<>();
        for (UUID id : license.workers()) {
            EntityMaid maid = TerminalAccountService.findMaid(server, id);
            String name = maid == null ? id.toString().substring(0, 8) : MaidDisplayName.encode(maid);
            boolean onDuty = maid != null && license.profession() != null
                    && license.profession().equals(maid.getTask().getUid());
            workers.add(new MaidLogisticsSnapshot.LicenseWorker(id, name, onDuty));
        }
        return List.copyOf(workers);
    }

    private static String offlineMaidName(TerminalAccountData.Account account, UUID maid) {
        String stored = account == null ? "" : account.maidName(maid);
        return stored.isBlank() ? maid.toString().substring(0, 8) : stored;
    }

    private static int inventoryTypes(MinecraftServer server,
                                      MailboxWarehouseData.WarehouseSnapshot warehouse) {
        if (warehouse == null || warehouse.inventoryList() == null) return 0;
        var data = server.overworld().getCapability(
                InventoryListDataProvider.INVENTORY_LIST_DATA_CAPABILITY).orElse(null);
        if (data == null) return 0;
        var inventory = data.dataMap.get(warehouse.inventoryList());
        if (inventory == null) return 0;
        return (int) inventory.values().stream().flatMap(List::stream)
                .filter(item -> item != null && item.itemStack != null
                        && !item.itemStack.isEmpty() && item.totalCount > 0)
                .map(item -> ForgeRegistries.ITEMS.getKey(item.itemStack.getItem()))
                .distinct().count();
    }

    private static int stableColor(UUID courier) {
        int[] palette = {0x4FC3F7, 0xFF8A65, 0x81C784, 0xBA68C8,
                0xFFD54F, 0x4DB6AC, 0xF06292, 0x7986CB};
        return palette[Math.floorMod(courier.hashCode(), palette.length)];
    }
}
