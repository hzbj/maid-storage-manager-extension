package io.github.maidstorageextension.network;

import io.github.maidstorageextension.client.NetworkWarehouseClientData;
import io.github.maidstorageextension.logistics.NetworkWarehouseSnapshot;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public record NetworkWarehouseSnapshotPacket(
        UUID courier, NetworkWarehouseSnapshot.Snapshot snapshot) {
    public static void encode(NetworkWarehouseSnapshotPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUUID(packet.courier);
        buffer.writeNbt(NetworkWarehouseSnapshot.toTag(packet.snapshot));
    }

    public static NetworkWarehouseSnapshotPacket decode(FriendlyByteBuf buffer) {
        return new NetworkWarehouseSnapshotPacket(
                buffer.readUUID(), NetworkWarehouseSnapshot.fromTag(buffer.readNbt()));
    }

    public static void handle(NetworkWarehouseSnapshotPacket packet,
                              Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        if (context.getDirection().getReceptionSide().isClient()) {
            context.enqueueWork(() -> NetworkWarehouseClientData.accept(
                    packet.courier, packet.snapshot));
        }
        context.setPacketHandled(true);
    }
}
