package io.github.maidstorageextension.debug;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.PacketDistributor;
import io.github.maidstorageextension.scan.StorageScanService;
import studio.fantasyit.maid_storage_manager.maid.task.StorageManageTask;
import io.github.maidstorageextension.network.ExtensionNetwork;
import io.github.maidstorageextension.network.ReachabilityDebugPacket;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class ReachabilityDebugManager {
    public static final int DISPLAY_TICKS = 15 * 20;
    private static final Set<UUID> PREPARED_PLAYERS = new HashSet<>();
    private static final Map<UUID, DebugJob> JOBS = new HashMap<>();

    private record DebugJob(UUID playerUuid, UUID maidUuid, ResourceKey<Level> dimension,
                            StorageScanService.ScanSession session) {
    }

    private ReachabilityDebugManager() {
    }

    public static void prepare(ServerPlayer player) {
        PREPARED_PLAYERS.add(player.getUUID());
    }

    public static boolean consumeClick(ServerPlayer player, EntityMaid maid) {
        if (!PREPARED_PLAYERS.remove(player.getUUID())) {
            return false;
        }
        if (!maid.getTask().getUid().equals(StorageManageTask.TASK_ID)) {
            player.sendSystemMessage(Component.translatable("debug.maid_storage_manager_extension.reachable.not_storage_maid"));
            return true;
        }
        if (!maid.isOwnedBy(player) && !player.hasPermissions(2)) {
            player.sendSystemMessage(Component.translatable("debug.maid_storage_manager_extension.reachable.not_owner"));
            return true;
        }
        ServerLevel level = (ServerLevel) maid.level();
        StorageScanService.ScanSession session = StorageScanService.createSession(level, maid);
        JOBS.put(player.getUUID(), new DebugJob(player.getUUID(), maid.getUUID(), level.dimension(), session));
        player.sendSystemMessage(Component.translatable("debug.maid_storage_manager_extension.reachable.started"));
        return true;
    }

    public static void tick(MinecraftServer server) {
        Iterator<Map.Entry<UUID, DebugJob>> iterator = JOBS.entrySet().iterator();
        while (iterator.hasNext()) {
            DebugJob job = iterator.next().getValue();
            ServerPlayer player = server.getPlayerList().getPlayer(job.playerUuid());
            ServerLevel level = server.getLevel(job.dimension());
            if (player == null || level == null) {
                iterator.remove();
                continue;
            }
            if (!player.level().dimension().equals(job.dimension())) {
                player.sendSystemMessage(Component.translatable("debug.maid_storage_manager_extension.reachable.dimension_changed"));
                iterator.remove();
                continue;
            }
            Entity entity = level.getEntity(job.maidUuid());
            if (!(entity instanceof EntityMaid maid)) {
                player.sendSystemMessage(Component.translatable("debug.maid_storage_manager_extension.reachable.maid_unloaded"));
                iterator.remove();
                continue;
            }
            if (!StorageScanService.tickSession(level, maid, job.session())) {
                continue;
            }
            publish(player, job.session());
            iterator.remove();
        }
    }

    private static void publish(ServerPlayer player, StorageScanService.ScanSession session) {
        int reachable = 0;
        int unreachable = 0;
        int denied = 0;
        List<ReachabilityDebugPacket.Entry> packetEntries = new ArrayList<>();
        for (StorageScanService.CandidateResult result : session.results()) {
            packetEntries.add(new ReachabilityDebugPacket.Entry(result.pos(), result.status()));
            switch (result.status()) {
                case REACHABLE -> reachable++;
                case UNREACHABLE -> unreachable++;
                case DENIED -> denied++;
            }
        }
        ExtensionNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new ReachabilityDebugPacket(packetEntries, DISPLAY_TICKS));
        player.sendSystemMessage(Component.translatable(
                "debug.maid_storage_manager_extension.reachable.summary",
                reachable, unreachable, denied, session.scope().describe()));
    }
}
