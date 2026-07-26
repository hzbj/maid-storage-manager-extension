package io.github.maidstorageextension.network;

import io.github.maidstorageextension.MaidStorageManagerExtension;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import io.github.maidstorageextension.client.ReachabilityDebugClientData;
import io.github.maidstorageextension.scan.StorageScanService;

import java.util.ArrayList;
import java.util.List;

public class ReachabilityDebugPacket implements CustomPacketPayload {
    public static final Type<ReachabilityDebugPacket> TYPE = new Type<>(MaidStorageManagerExtension.id("reachability_debug"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ReachabilityDebugPacket> STREAM_CODEC =
            StreamCodec.of((buffer, packet) -> encode(packet, buffer), ReachabilityDebugPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public record Entry(BlockPos pos, StorageScanService.CandidateStatus status) {
    }

    public final List<Entry> entries;
    public final int durationTicks;

    public ReachabilityDebugPacket(List<Entry> entries, int durationTicks) {
        this.entries = entries;
        this.durationTicks = durationTicks;
    }

    public static ReachabilityDebugPacket decode(RegistryFriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        List<Entry> decoded = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            BlockPos pos = buffer.readBlockPos();
            int status = buffer.readVarInt();
            status = Math.max(0, Math.min(StorageScanService.CandidateStatus.values().length - 1, status));
            decoded.add(new Entry(pos, StorageScanService.CandidateStatus.values()[status]));
        }
        return new ReachabilityDebugPacket(List.copyOf(decoded), buffer.readVarInt());
    }

    public static void encode(ReachabilityDebugPacket packet, RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.entries.size());
        for (Entry entry : packet.entries) {
            buffer.writeBlockPos(entry.pos());
            buffer.writeVarInt(entry.status().ordinal());
        }
        buffer.writeVarInt(packet.durationTicks);
    }

    public static void handle(ReachabilityDebugPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ReachabilityDebugClientData.show(packet.entries, packet.durationTicks));
    }
}
