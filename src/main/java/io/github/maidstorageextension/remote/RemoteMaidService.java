package io.github.maidstorageextension.remote;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import io.github.maidstorageextension.terminal.MailboxKey;
import io.github.maidstorageextension.terminal.MailboxWarehouseData;
import io.github.maidstorageextension.terminal.TerminalAccountData;
import io.github.maidstorageextension.item.InventoryMaintenanceDevice;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import studio.fantasyit.maid_storage_manager.maid.task.StorageManageTask;

import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/** Server-owned chunk residency and UUID resolver for registered maids and mailbox nodes. */
public final class RemoteMaidService {
    private static final int TICKET_TIMEOUT = 100;
    private static final int TICKET_DISTANCE = 2;
    private static final TicketType<UUID> REGISTERED_NODE_TICKET = TicketType.create(
            "maid_storage_extension_registered_node",
            Comparator.<UUID>naturalOrder(), TICKET_TIMEOUT);
    private static final Map<MinecraftServer, Map<UUID, Residency>> ACTIVE_TICKETS =
            new WeakHashMap<>();

    private record Residency(net.minecraft.resources.ResourceLocation dimension,
                             ChunkPos chunk, UUID key) {
    }

    private RemoteMaidService() {
    }

    public static void observe(MinecraftServer server, EntityMaid maid) {
        if (server == null || maid == null || !references(server).contains(maid.getUUID())) return;
        RemoteMaidIndexData.get(server).observe(maid);
    }

    public static EntityMaid find(MinecraftServer server, UUID maidId) {
        if (server == null || maidId == null) return null;
        EntityMaid loaded = findLoaded(server, maidId);
        if (loaded != null) {
            RemoteMaidIndexData.get(server).observe(loaded);
            return loaded;
        }
        RemoteMaidIndexData.Entry entry = RemoteMaidIndexData.get(server).get(maidId);
        if (entry == null || entry.dimension() == null || entry.position() == null) return null;
        ServerLevel level = level(server, entry.dimension());
        if (level == null) return null;
        keepLoaded(level, new ChunkPos(entry.position()), maidId);
        // Terminal actions are infrequent and need a deterministic resolver, not an async retry.
        level.getChunk(entry.position().getX() >> 4, entry.position().getZ() >> 4);
        Entity entity = level.getEntity(maidId);
        if (entity instanceof EntityMaid maid && maid.isAlive()) {
            RemoteMaidIndexData.get(server).observe(maid);
            return maid;
        }
        return null;
    }

    public static void tick(MinecraftServer server) {
        if (server == null) return;
        Set<UUID> references = references(server);
        RemoteMaidIndexData index = RemoteMaidIndexData.get(server);
        Map<UUID, Residency> desired = new LinkedHashMap<>();
        for (RemoteMaidIndexData.Entry entry : index.entries()) {
            if (!references.contains(entry.maid())) {
                index.remove(entry.maid());
                continue;
            }
            ServerLevel level = level(server, entry.dimension());
            if (level != null) {
                desired.put(entry.maid(), new Residency(entry.dimension(),
                        new ChunkPos(entry.position()), entry.maid()));
            }
        }
        LinkedHashSet<MailboxKey> mailboxes = new LinkedHashSet<>(
                TerminalAccountData.get(server).registeredMailboxKeys());
        MailboxWarehouseData.get(server).warehouses().stream()
                .filter(MailboxWarehouseData.WarehouseSnapshot::hasManagers)
                .map(MailboxWarehouseData.WarehouseSnapshot::key)
                .forEach(mailboxes::add);
        for (MailboxKey mailbox : mailboxes) {
            ServerLevel level = level(server, mailbox.dimension());
            if (level != null) {
                UUID key = ticketKey(mailbox);
                desired.put(key, new Residency(mailbox.dimension(),
                        new ChunkPos(mailbox.position()), key));
            }
        }
        reconcileTickets(server, desired);
        validateWarehouseFrames(server);
    }

    private static EntityMaid findLoaded(MinecraftServer server, UUID maidId) {
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(maidId);
            if (entity instanceof EntityMaid maid && maid.isAlive()) return maid;
        }
        return null;
    }

    private static Set<UUID> references(MinecraftServer server) {
        LinkedHashSet<UUID> result = new LinkedHashSet<>(
                TerminalAccountData.get(server).registeredMaidIds());
        result.addAll(MailboxWarehouseData.get(server).managers());
        return result;
    }

    private static ServerLevel level(MinecraftServer server, net.minecraft.resources.ResourceLocation id) {
        return id == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
    }

    private static void keepLoaded(ServerLevel level, ChunkPos chunk, UUID key) {
        level.getChunkSource().addRegionTicket(
                REGISTERED_NODE_TICKET, chunk, TICKET_DISTANCE, key);
    }

    private static void reconcileTickets(MinecraftServer server, Map<UUID, Residency> desired) {
        Map<UUID, Residency> active;
        synchronized (ACTIVE_TICKETS) {
            active = ACTIVE_TICKETS.computeIfAbsent(server, ignored -> new LinkedHashMap<>());
        }
        // Acquire every new residency first so a moving maid never loses both Tick chunks.
        for (Residency next : desired.values()) {
            ServerLevel level = level(server, next.dimension());
            if (level != null) keepLoaded(level, next.chunk(), next.key());
        }
        for (Residency previous : List.copyOf(active.values())) {
            Residency next = desired.get(previous.key());
            if (previous.equals(next)) continue;
            ServerLevel level = level(server, previous.dimension());
            if (level != null) {
                level.getChunkSource().removeRegionTicket(
                        REGISTERED_NODE_TICKET, previous.chunk(),
                        TICKET_DISTANCE, previous.key());
            }
        }
        active.clear();
        active.putAll(desired);
    }

    private static void validateWarehouseFrames(MinecraftServer server) {
        MailboxWarehouseData data = MailboxWarehouseData.get(server);
        for (MailboxWarehouseData.WarehouseSnapshot warehouse : data.warehouses()) {
            if (!warehouse.hasManagers() || warehouse.frame() == null) continue;
            java.util.List<EntityMaid> loaded = warehouse.managers().stream()
                    .map(id -> findLoaded(server, id))
                    .filter(java.util.Objects::nonNull)
                    .toList();
            // Do not revoke a list while one member is still waiting for its indexed chunk.
            if (loaded.size() < warehouse.managers().size()) continue;
            boolean valid = loaded.stream()
                    .filter(maid -> maid.getTask().getUid().equals(StorageManageTask.TASK_ID))
                    .map(InventoryMaintenanceDevice::findOn)
                    .flatMap(java.util.Optional::stream)
                    .anyMatch(stack -> InventoryMaintenanceDevice.isBound(stack)
                            && warehouse.frame().dimension().equals(
                            InventoryMaintenanceDevice.getFrameDimension(stack))
                            && warehouse.frame().position().equals(
                            InventoryMaintenanceDevice.getFramePos(stack))
                            && warehouse.frame().entity().equals(
                            InventoryMaintenanceDevice.getFrameUuid(stack)));
            if (!valid && data.invalidateWarehouse(warehouse.key())) {
                io.github.maidstorageextension.terminal.TerminalAccountService
                        .refreshMailboxViewers(server, warehouse.key());
            }
        }
    }

    private static UUID ticketKey(MailboxKey mailbox) {
        String value = mailbox.dimension() + "@"
                + mailbox.position().getX() + ","
                + mailbox.position().getY() + ","
                + mailbox.position().getZ();
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
    }
}
