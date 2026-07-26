package io.github.maidstorageextension.network;

import io.github.maidstorageextension.MaidStorageManagerExtension;

import io.github.maidstorageextension.terminal.MaidTransportService;
import io.github.maidstorageextension.terminal.MailboxKey;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

public record MaidTransportActionPacket(Action action, UUID terminal,
                                        BlockPos pickup, BlockPos destination,
                                        MailboxKey mailbox) implements CustomPacketPayload {
    public static final Type<MaidTransportActionPacket> TYPE = new Type<>(MaidStorageManagerExtension.id("maid_transport_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MaidTransportActionPacket> STREAM_CODEC =
            StreamCodec.of((buffer, packet) -> encode(packet, buffer), MaidTransportActionPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public enum Action { REFRESH, START, END, RETURN_TO_WAREHOUSE, CLEAR_STATUS }

    public MaidTransportActionPacket {
        action = action == null ? Action.REFRESH : action;
        pickup = pickup == null ? null : new BlockPos(pickup.getX(), 0, pickup.getZ());
        destination = destination == null ? null
                : new BlockPos(destination.getX(), 0, destination.getZ());
    }

    public static MaidTransportActionPacket refresh(UUID terminal) {
        return new MaidTransportActionPacket(Action.REFRESH, terminal, null, null, null);
    }

    public static MaidTransportActionPacket start(UUID terminal, BlockPos pickup,
                                                   BlockPos destination) {
        return new MaidTransportActionPacket(Action.START, terminal, pickup, destination, null);
    }

    public static MaidTransportActionPacket end(UUID terminal) {
        return new MaidTransportActionPacket(Action.END, terminal, null, null, null);
    }

    public static MaidTransportActionPacket returnToWarehouse(
            UUID terminal, MailboxKey mailbox) {
        return new MaidTransportActionPacket(
                Action.RETURN_TO_WAREHOUSE, terminal, null, null, mailbox);
    }

    public static MaidTransportActionPacket clearStatus(UUID terminal) {
        return new MaidTransportActionPacket(Action.CLEAR_STATUS, terminal, null, null, null);
    }

    public static void encode(MaidTransportActionPacket packet, RegistryFriendlyByteBuf buffer) {
        buffer.writeEnum(packet.action);
        buffer.writeUUID(packet.terminal);
        buffer.writeBoolean(packet.pickup != null);
        if (packet.pickup != null) buffer.writeBlockPos(packet.pickup);
        buffer.writeBoolean(packet.destination != null);
        if (packet.destination != null) buffer.writeBlockPos(packet.destination);
        buffer.writeBoolean(packet.mailbox != null && packet.mailbox.valid());
        if (packet.mailbox != null && packet.mailbox.valid()) {
            buffer.writeResourceLocation(packet.mailbox.dimension());
            buffer.writeBlockPos(packet.mailbox.position());
        }
    }

    public static MaidTransportActionPacket decode(RegistryFriendlyByteBuf buffer) {
        Action action = buffer.readEnum(Action.class);
        UUID terminal = buffer.readUUID();
        BlockPos pickup = buffer.readBoolean() ? buffer.readBlockPos() : null;
        BlockPos destination = buffer.readBoolean() ? buffer.readBlockPos() : null;
        MailboxKey mailbox = buffer.readBoolean()
                ? new MailboxKey(buffer.readResourceLocation(), buffer.readBlockPos()) : null;
        return new MaidTransportActionPacket(action, terminal, pickup, destination, mailbox);
    }

    public static void handle(MaidTransportActionPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer sender = (ServerPlayer) context.player();
            if (sender != null) MaidTransportService.handle(sender, packet);
        });
    }
}
