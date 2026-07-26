package io.github.maidstorageextension.network;

import io.github.maidstorageextension.MaidStorageManagerExtension;

import io.github.maidstorageextension.client.NetworkWarehouseClientData;
import io.github.maidstorageextension.logistics.NetworkWarehouseSnapshot;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

public record NetworkWarehouseSnapshotPacket(
        UUID courier, NetworkWarehouseSnapshot.Snapshot snapshot) implements CustomPacketPayload {
    public static final Type<NetworkWarehouseSnapshotPacket> TYPE = new Type<>(MaidStorageManagerExtension.id("network_warehouse_snapshot"));
    public static final StreamCodec<RegistryFriendlyByteBuf, NetworkWarehouseSnapshotPacket> STREAM_CODEC =
            StreamCodec.of((buffer, packet) -> encode(packet, buffer), NetworkWarehouseSnapshotPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void encode(NetworkWarehouseSnapshotPacket packet, RegistryFriendlyByteBuf buffer) {
        buffer.writeUUID(packet.courier);
        buffer.writeNbt(NetworkWarehouseSnapshot.toTag(packet.snapshot, buffer.registryAccess()));
    }

    public static NetworkWarehouseSnapshotPacket decode(RegistryFriendlyByteBuf buffer) {
        return new NetworkWarehouseSnapshotPacket(
                buffer.readUUID(), NetworkWarehouseSnapshot.fromTag(
                        buffer.readNbt(), buffer.registryAccess()));
    }

    public static void handle(NetworkWarehouseSnapshotPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> NetworkWarehouseClientData.accept(
                    packet.courier, packet.snapshot));
    }
}
