package io.github.maidstorageextension.network;

import io.github.maidstorageextension.MaidStorageManagerExtension;
import io.github.maidstorageextension.client.MaidLogisticsClientData;
import io.github.maidstorageextension.logistics.MaidLogisticsSnapshot;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

public record MaidLogisticsSnapshotPacket(UUID terminal, MaidLogisticsSnapshot.Snapshot snapshot)
        implements CustomPacketPayload {
    public static final Type<MaidLogisticsSnapshotPacket> TYPE =
            new Type<>(MaidStorageManagerExtension.id("maid_logistics_snapshot"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MaidLogisticsSnapshotPacket> STREAM_CODEC =
            StreamCodec.of((buffer, packet) -> encode(packet, buffer), MaidLogisticsSnapshotPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void encode(MaidLogisticsSnapshotPacket packet, RegistryFriendlyByteBuf buffer) {
        buffer.writeUUID(packet.terminal);
        buffer.writeNbt(MaidLogisticsSnapshot.toTag(packet.snapshot));
    }

    public static MaidLogisticsSnapshotPacket decode(RegistryFriendlyByteBuf buffer) {
        return new MaidLogisticsSnapshotPacket(buffer.readUUID(),
                MaidLogisticsSnapshot.fromTag(buffer.readNbt()));
    }

    public static void handle(MaidLogisticsSnapshotPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> MaidLogisticsClientData.accept(packet.terminal, packet.snapshot));
    }
}
