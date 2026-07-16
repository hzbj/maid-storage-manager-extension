package io.github.maidstorageextension.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import io.github.maidstorageextension.client.ReachabilityDebugClientData;
import io.github.maidstorageextension.scan.StorageScanService;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class ReachabilityDebugPacket {
    public record Entry(BlockPos pos, StorageScanService.CandidateStatus status) {
    }

    public final List<Entry> entries;
    public final int durationTicks;

    public ReachabilityDebugPacket(List<Entry> entries, int durationTicks) {
        this.entries = entries;
        this.durationTicks = durationTicks;
    }

    public static ReachabilityDebugPacket decode(FriendlyByteBuf buffer) {
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

    public static void encode(ReachabilityDebugPacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.entries.size());
        for (Entry entry : packet.entries) {
            buffer.writeBlockPos(entry.pos());
            buffer.writeVarInt(entry.status().ordinal());
        }
        buffer.writeVarInt(packet.durationTicks);
    }

    public static void handle(ReachabilityDebugPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        if (context.getDirection().getReceptionSide().isClient()) {
            context.enqueueWork(() -> ReachabilityDebugClientData.show(packet.entries, packet.durationTicks));
        }
        context.setPacketHandled(true);
    }
}
