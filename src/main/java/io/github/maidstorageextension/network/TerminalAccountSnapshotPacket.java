package io.github.maidstorageextension.network;

import io.github.maidstorageextension.MaidStorageManagerExtension;

import io.github.maidstorageextension.client.TerminalAccountClientData;
import io.github.maidstorageextension.terminal.TerminalAccountSnapshot;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

public record TerminalAccountSnapshotPacket(UUID terminal,
                                            TerminalAccountSnapshot.Snapshot snapshot) implements CustomPacketPayload {
    public static final Type<TerminalAccountSnapshotPacket> TYPE = new Type<>(MaidStorageManagerExtension.id("terminal_account_snapshot"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TerminalAccountSnapshotPacket> STREAM_CODEC =
            StreamCodec.of((buffer, packet) -> encode(packet, buffer), TerminalAccountSnapshotPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void encode(TerminalAccountSnapshotPacket packet, RegistryFriendlyByteBuf buffer) {
        buffer.writeUUID(packet.terminal);
        buffer.writeNbt(TerminalAccountSnapshot.toTag(packet.snapshot));
    }

    public static TerminalAccountSnapshotPacket decode(RegistryFriendlyByteBuf buffer) {
        return new TerminalAccountSnapshotPacket(buffer.readUUID(),
                TerminalAccountSnapshot.fromTag(buffer.readNbt()));
    }

    public static void handle(TerminalAccountSnapshotPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> TerminalAccountClientData.accept(packet.terminal, packet.snapshot));
    }
}
