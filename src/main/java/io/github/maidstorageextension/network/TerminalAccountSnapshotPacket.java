package io.github.maidstorageextension.network;

import io.github.maidstorageextension.client.TerminalAccountClientData;
import io.github.maidstorageextension.terminal.TerminalAccountSnapshot;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public record TerminalAccountSnapshotPacket(UUID terminal,
                                            TerminalAccountSnapshot.Snapshot snapshot) {
    public static void encode(TerminalAccountSnapshotPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUUID(packet.terminal);
        buffer.writeNbt(TerminalAccountSnapshot.toTag(packet.snapshot));
    }

    public static TerminalAccountSnapshotPacket decode(FriendlyByteBuf buffer) {
        return new TerminalAccountSnapshotPacket(buffer.readUUID(),
                TerminalAccountSnapshot.fromTag(buffer.readNbt()));
    }

    public static void handle(TerminalAccountSnapshotPacket packet,
                              Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        if (context.getDirection().getReceptionSide().isClient()) {
            context.enqueueWork(() -> TerminalAccountClientData.accept(packet.terminal, packet.snapshot));
        }
        context.setPacketHandled(true);
    }
}
