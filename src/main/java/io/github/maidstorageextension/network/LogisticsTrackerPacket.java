package io.github.maidstorageextension.network;

import io.github.maidstorageextension.MaidStorageManagerExtension;

import io.github.maidstorageextension.client.LogisticsTrackerClientData;
import io.github.maidstorageextension.logistics.LogisticsSnapshot;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

public record LogisticsTrackerPacket(UUID courier, LogisticsSnapshot.Snapshot snapshot) implements CustomPacketPayload {
    public static final Type<LogisticsTrackerPacket> TYPE = new Type<>(MaidStorageManagerExtension.id("logistics_tracker"));
    public static final StreamCodec<RegistryFriendlyByteBuf, LogisticsTrackerPacket> STREAM_CODEC =
            StreamCodec.of((buffer, packet) -> encode(packet, buffer), LogisticsTrackerPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void encode(LogisticsTrackerPacket packet, RegistryFriendlyByteBuf buffer) {
        buffer.writeUUID(packet.courier);
        buffer.writeNbt(LogisticsSnapshot.toTag(packet.snapshot));
    }

    public static LogisticsTrackerPacket decode(RegistryFriendlyByteBuf buffer) {
        UUID courier = buffer.readUUID();
        return new LogisticsTrackerPacket(courier, LogisticsSnapshot.fromTag(buffer.readNbt()));
    }

    public static void handle(LogisticsTrackerPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> LogisticsTrackerClientData.accept(packet.courier, packet.snapshot));
    }
}
