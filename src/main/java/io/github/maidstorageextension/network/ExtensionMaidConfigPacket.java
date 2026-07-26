package io.github.maidstorageextension.network;

import io.github.maidstorageextension.MaidStorageManagerExtension;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import io.github.maidstorageextension.compat.EnderPocketCompat;
import io.github.maidstorageextension.data.ExtensionConfigData;
import io.github.maidstorageextension.data.PeriodicScanInterval;
import io.github.maidstorageextension.maid.ExtensionMemoryUtil;
import io.github.maidstorageextension.maid.memory.MiscSortMemory;
import io.github.maidstorageextension.maid.memory.PeriodicScanMemory;
import io.github.maidstorageextension.scan.StorageScanService;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.minecraft.server.level.ServerPlayer;
import studio.fantasyit.maid_storage_manager.maid.task.StorageManageTask;


/** Complete, owner-validated update for one maid's editable extension settings. */
public record ExtensionMaidConfigPacket(
        int maidId,
        int intervalOrdinal,
        int localScanRadius,
        int taskBellRange,
        int taskBellTravelTimeoutSeconds,
        int taskBellStaySeconds,
        boolean refreshFrameEffects,
        boolean refreshOwnerNotification,
        boolean miscSortMatchNbt) implements CustomPacketPayload {
    public static final Type<ExtensionMaidConfigPacket> TYPE = new Type<>(MaidStorageManagerExtension.id("extension_maid_config"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ExtensionMaidConfigPacket> STREAM_CODEC =
            StreamCodec.of((buffer, packet) -> encode(packet, buffer), ExtensionMaidConfigPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }


    public static ExtensionMaidConfigPacket from(EntityMaid maid, ExtensionConfigData.Data data) {
        return new ExtensionMaidConfigPacket(
                maid.getId(),
                data.periodicScanInterval().ordinal(),
                data.localScanRadius(),
                data.taskBellRange(),
                data.taskBellTravelTimeoutSeconds(),
                data.taskBellStaySeconds(),
                data.refreshFrameEffects(),
                data.refreshOwnerNotification(),
                data.miscSortMatchNbt());
    }

    public static void encode(ExtensionMaidConfigPacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.maidId);
        buffer.writeVarInt(packet.intervalOrdinal);
        buffer.writeVarInt(packet.localScanRadius);
        buffer.writeVarInt(packet.taskBellRange);
        buffer.writeVarInt(packet.taskBellTravelTimeoutSeconds);
        buffer.writeVarInt(packet.taskBellStaySeconds);
        buffer.writeBoolean(packet.refreshFrameEffects);
        buffer.writeBoolean(packet.refreshOwnerNotification);
        buffer.writeBoolean(packet.miscSortMatchNbt);
    }

    public static ExtensionMaidConfigPacket decode(FriendlyByteBuf buffer) {
        return new ExtensionMaidConfigPacket(
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readBoolean());
    }

    public static void handle(ExtensionMaidConfigPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer sender = (ServerPlayer) context.player();
            if (sender == null) {
                return;
            }
            EntityMaid maid = EnderPocketCompat.resolveRemoteMaid(sender, packet.maidId);
            if (maid == null
                    || !maid.isOwnedBy(sender)
                    || !maid.getTask().getUid().equals(StorageManageTask.TASK_ID)) {
                return;
            }

            ExtensionConfigData.Data data = ExtensionConfigData.get(maid);
            PeriodicScanInterval previous = data.periodicScanInterval();
            boolean matchModeChanged = data.miscSortMatchNbt() != packet.miscSortMatchNbt;
            PeriodicScanInterval selected = PeriodicScanInterval.byOrdinal(packet.intervalOrdinal);
            data.periodicScanInterval(selected);
            data.localScanRadius(packet.localScanRadius);
            data.taskBellRange(packet.taskBellRange);
            data.taskBellTravelTimeoutSeconds(packet.taskBellTravelTimeoutSeconds);
            data.taskBellStaySeconds(packet.taskBellStaySeconds);
            data.refreshFrameEffects(packet.refreshFrameEffects);
            data.refreshOwnerNotification(packet.refreshOwnerNotification);
            data.miscSortMatchNbt(packet.miscSortMatchNbt);

            PeriodicScanMemory scan = ExtensionMemoryUtil.getPeriodicScan(maid);
            if (selected == PeriodicScanInterval.DISABLED) {
                StorageScanService.cancelPeriodic(maid);
                scan.clearForceScanRequest();
            } else if (previous == PeriodicScanInterval.DISABLED) {
                scan.setNextScanGameTime(0L);
            } else if (scan.getPhase() == PeriodicScanMemory.Phase.IDLE) {
                scan.setNextScanGameTime(maid.level().getGameTime() + selected.ticks());
            }
            if (matchModeChanged) {
                MiscSortMemory sort = ExtensionMemoryUtil.getMiscSort(maid);
                sort.clearUnstartedWork();
                if (!sort.hasInFlight()) {
                    StorageScanService.cancelPeriodic(maid);
                    scan.requestImmediateScan();
                }
            }
            maid.setAndSyncData(ExtensionConfigData.KEY, data);
            EnderPocketCompat.syncRemoteProxy(sender, maid);
        });
    }
}
