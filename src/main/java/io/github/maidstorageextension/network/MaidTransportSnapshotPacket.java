package io.github.maidstorageextension.network;

import io.github.maidstorageextension.MaidStorageManagerExtension;

import io.github.maidstorageextension.client.MaidTransportClientData;
import io.github.maidstorageextension.terminal.MaidTransportSnapshot;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

public record MaidTransportSnapshotPacket(UUID terminal,
                                          MaidTransportSnapshot.Snapshot snapshot) implements CustomPacketPayload {
    public static final Type<MaidTransportSnapshotPacket> TYPE = new Type<>(MaidStorageManagerExtension.id("maid_transport_snapshot"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MaidTransportSnapshotPacket> STREAM_CODEC =
            StreamCodec.of((buffer, packet) -> encode(packet, buffer), MaidTransportSnapshotPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void encode(MaidTransportSnapshotPacket packet, RegistryFriendlyByteBuf buffer) {
        buffer.writeUUID(packet.terminal);
        buffer.writeNbt(MaidTransportSnapshot.toTag(packet.snapshot));
    }

    public static MaidTransportSnapshotPacket decode(RegistryFriendlyByteBuf buffer) {
        return new MaidTransportSnapshotPacket(buffer.readUUID(),
                MaidTransportSnapshot.fromTag(buffer.readNbt()));
    }

    public static void handle(MaidTransportSnapshotPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> MaidTransportClientData.accept(packet.terminal, packet.snapshot));
    }
}
