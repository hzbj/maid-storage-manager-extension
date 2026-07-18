package io.github.maidstorageextension.network;

import io.github.maidstorageextension.terminal.TerminalAccountService;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public record TerminalMailboxActionPacket(Action action, UUID terminal,
                                          ResourceLocation dimension, BlockPos position) {
    public enum Action { BIND, UNBIND, SELECT_DEFAULT, ACTIVATE, UNREGISTER, REQUEST_SCAN }

    public static void encode(TerminalMailboxActionPacket packet, FriendlyByteBuf buffer) {
        buffer.writeEnum(packet.action);
        buffer.writeUUID(packet.terminal);
        buffer.writeResourceLocation(packet.dimension);
        buffer.writeBlockPos(packet.position);
    }

    public static TerminalMailboxActionPacket decode(FriendlyByteBuf buffer) {
        return new TerminalMailboxActionPacket(buffer.readEnum(Action.class), buffer.readUUID(),
                buffer.readResourceLocation(), buffer.readBlockPos());
    }

    public static void handle(TerminalMailboxActionPacket packet,
                              Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender != null) TerminalAccountService.handleMailbox(sender, packet);
        });
        context.setPacketHandled(true);
    }
}
