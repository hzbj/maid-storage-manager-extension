package io.github.maidstorageextension.network;

import io.github.maidstorageextension.MaidStorageManagerExtension;

import io.github.maidstorageextension.client.TerminalNoticeClientData;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/** Server acknowledgement for an action initiated while the terminal screen stays open. */
public record TerminalNoticePacket(UUID terminal, String translationKey, boolean success) implements CustomPacketPayload {
    public static final Type<TerminalNoticePacket> TYPE = new Type<>(MaidStorageManagerExtension.id("terminal_notice"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TerminalNoticePacket> STREAM_CODEC =
            StreamCodec.of((buffer, packet) -> encode(packet, buffer), TerminalNoticePacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    private static final int MAX_KEY_LENGTH = 256;

    public TerminalNoticePacket {
        translationKey = translationKey == null ? "" : translationKey;
    }

    public static void encode(TerminalNoticePacket packet, RegistryFriendlyByteBuf buffer) {
        buffer.writeUUID(packet.terminal);
        buffer.writeUtf(packet.translationKey, MAX_KEY_LENGTH);
        buffer.writeBoolean(packet.success);
    }

    public static TerminalNoticePacket decode(RegistryFriendlyByteBuf buffer) {
        return new TerminalNoticePacket(buffer.readUUID(),
                buffer.readUtf(MAX_KEY_LENGTH), buffer.readBoolean());
    }

    public static void handle(TerminalNoticePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> TerminalNoticeClientData.accept(
                    packet.terminal, packet.translationKey, packet.success));
    }
}
