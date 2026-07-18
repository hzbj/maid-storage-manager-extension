package io.github.maidstorageextension.network;

import io.github.maidstorageextension.client.MaidTransportClientData;
import io.github.maidstorageextension.terminal.MaidTransportSnapshot;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public record MaidTransportSnapshotPacket(UUID terminal,
                                          MaidTransportSnapshot.Snapshot snapshot) {
    public static void encode(MaidTransportSnapshotPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUUID(packet.terminal);
        buffer.writeNbt(MaidTransportSnapshot.toTag(packet.snapshot));
    }

    public static MaidTransportSnapshotPacket decode(FriendlyByteBuf buffer) {
        return new MaidTransportSnapshotPacket(buffer.readUUID(),
                MaidTransportSnapshot.fromTag(buffer.readNbt()));
    }

    public static void handle(MaidTransportSnapshotPacket packet,
                              Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        if (context.getDirection().getReceptionSide().isClient()) {
            context.enqueueWork(() -> MaidTransportClientData.accept(packet.terminal, packet.snapshot));
        }
        context.setPacketHandled(true);
    }
}
