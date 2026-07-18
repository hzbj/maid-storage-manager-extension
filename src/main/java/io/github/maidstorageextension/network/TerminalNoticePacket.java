package io.github.maidstorageextension.network;

import io.github.maidstorageextension.client.TerminalNoticeClientData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/** Server acknowledgement for an action initiated while the terminal screen stays open. */
public record TerminalNoticePacket(UUID terminal, String translationKey, boolean success) {
    private static final int MAX_KEY_LENGTH = 256;

    public TerminalNoticePacket {
        translationKey = translationKey == null ? "" : translationKey;
    }

    public static void encode(TerminalNoticePacket packet, FriendlyByteBuf buffer) {
        buffer.writeUUID(packet.terminal);
        buffer.writeUtf(packet.translationKey, MAX_KEY_LENGTH);
        buffer.writeBoolean(packet.success);
    }

    public static TerminalNoticePacket decode(FriendlyByteBuf buffer) {
        return new TerminalNoticePacket(buffer.readUUID(),
                buffer.readUtf(MAX_KEY_LENGTH), buffer.readBoolean());
    }

    public static void handle(TerminalNoticePacket packet,
                              Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        if (context.getDirection().getReceptionSide().isClient()) {
            context.enqueueWork(() -> TerminalNoticeClientData.accept(
                    packet.terminal, packet.translationKey, packet.success));
        }
        context.setPacketHandled(true);
    }
}
