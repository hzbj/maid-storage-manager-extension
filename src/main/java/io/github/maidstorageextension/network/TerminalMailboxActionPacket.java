package io.github.maidstorageextension.network;

import io.github.maidstorageextension.MaidStorageManagerExtension;

import io.github.maidstorageextension.terminal.TerminalAccountService;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

public record TerminalMailboxActionPacket(Action action, UUID terminal,
                                          ResourceLocation dimension, BlockPos position,
                                          String name) implements CustomPacketPayload {
    public static final Type<TerminalMailboxActionPacket> TYPE = new Type<>(MaidStorageManagerExtension.id("terminal_mailbox_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TerminalMailboxActionPacket> STREAM_CODEC =
            StreamCodec.of((buffer, packet) -> encode(packet, buffer), TerminalMailboxActionPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public enum Action { BIND, UNBIND, SELECT_DEFAULT, ACTIVATE, UNREGISTER, REQUEST_SCAN, RENAME }

    public TerminalMailboxActionPacket {
        name = name == null ? "" : name;
    }

    public TerminalMailboxActionPacket(Action action, UUID terminal,
                                       ResourceLocation dimension, BlockPos position) {
        this(action, terminal, dimension, position, "");
    }

    public static void encode(TerminalMailboxActionPacket packet, RegistryFriendlyByteBuf buffer) {
        buffer.writeEnum(packet.action);
        buffer.writeUUID(packet.terminal);
        buffer.writeResourceLocation(packet.dimension);
        buffer.writeBlockPos(packet.position);
        buffer.writeUtf(packet.name, 128);
    }

    public static TerminalMailboxActionPacket decode(RegistryFriendlyByteBuf buffer) {
        return new TerminalMailboxActionPacket(buffer.readEnum(Action.class), buffer.readUUID(),
                buffer.readResourceLocation(), buffer.readBlockPos(), buffer.readUtf(128));
    }

    public static void handle(TerminalMailboxActionPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer sender = (ServerPlayer) context.player();
            if (sender != null) TerminalAccountService.handleMailbox(sender, packet);
        });
    }
}
