package io.github.maidstorageextension.network;

import io.github.maidstorageextension.client.MaidLogisticsClientData;
import io.github.maidstorageextension.logistics.MaidLogisticsSnapshot;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public record MaidLogisticsSnapshotPacket(UUID terminal, MaidLogisticsSnapshot.Snapshot snapshot) {
    public static void encode(MaidLogisticsSnapshotPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUUID(packet.terminal);
        buffer.writeNbt(MaidLogisticsSnapshot.toTag(packet.snapshot));
    }

    public static MaidLogisticsSnapshotPacket decode(FriendlyByteBuf buffer) {
        return new MaidLogisticsSnapshotPacket(buffer.readUUID(),
                MaidLogisticsSnapshot.fromTag(buffer.readNbt()));
    }

    public static void handle(MaidLogisticsSnapshotPacket packet,
                              Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        if (context.getDirection().getReceptionSide().isClient()) {
            context.enqueueWork(() -> MaidLogisticsClientData.accept(packet.terminal, packet.snapshot));
        }
        context.setPacketHandled(true);
    }
}
