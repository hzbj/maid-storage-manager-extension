package io.github.maidstorageextension.network;

import io.github.maidstorageextension.client.LogisticsTrackerClientData;
import io.github.maidstorageextension.logistics.LogisticsSnapshot;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public record LogisticsTrackerPacket(UUID courier, LogisticsSnapshot.Snapshot snapshot) {
    public static void encode(LogisticsTrackerPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUUID(packet.courier);
        buffer.writeNbt(LogisticsSnapshot.toTag(packet.snapshot));
    }

    public static LogisticsTrackerPacket decode(FriendlyByteBuf buffer) {
        UUID courier = buffer.readUUID();
        return new LogisticsTrackerPacket(courier, LogisticsSnapshot.fromTag(buffer.readNbt()));
    }

    public static void handle(LogisticsTrackerPacket packet,
                              Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        if (context.getDirection().getReceptionSide().isClient()) {
            context.enqueueWork(() -> LogisticsTrackerClientData.accept(packet.courier, packet.snapshot));
        }
        context.setPacketHandled(true);
    }
}
