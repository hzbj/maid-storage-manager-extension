package io.github.maidstorageextension.network;

import io.github.maidstorageextension.terminal.MaidTransportService;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public record MaidTransportActionPacket(Action action, UUID terminal,
                                        BlockPos pickup, BlockPos destination) {
    public enum Action { REFRESH, START, END }

    public MaidTransportActionPacket {
        action = action == null ? Action.REFRESH : action;
        pickup = pickup == null ? null : new BlockPos(pickup.getX(), 0, pickup.getZ());
        destination = destination == null ? null
                : new BlockPos(destination.getX(), 0, destination.getZ());
    }

    public static MaidTransportActionPacket refresh(UUID terminal) {
        return new MaidTransportActionPacket(Action.REFRESH, terminal, null, null);
    }

    public static MaidTransportActionPacket start(UUID terminal, BlockPos pickup,
                                                   BlockPos destination) {
        return new MaidTransportActionPacket(Action.START, terminal, pickup, destination);
    }

    public static MaidTransportActionPacket end(UUID terminal) {
        return new MaidTransportActionPacket(Action.END, terminal, null, null);
    }

    public static void encode(MaidTransportActionPacket packet, FriendlyByteBuf buffer) {
        buffer.writeEnum(packet.action);
        buffer.writeUUID(packet.terminal);
        buffer.writeBoolean(packet.pickup != null);
        if (packet.pickup != null) buffer.writeBlockPos(packet.pickup);
        buffer.writeBoolean(packet.destination != null);
        if (packet.destination != null) buffer.writeBlockPos(packet.destination);
    }

    public static MaidTransportActionPacket decode(FriendlyByteBuf buffer) {
        Action action = buffer.readEnum(Action.class);
        UUID terminal = buffer.readUUID();
        BlockPos pickup = buffer.readBoolean() ? buffer.readBlockPos() : null;
        BlockPos destination = buffer.readBoolean() ? buffer.readBlockPos() : null;
        return new MaidTransportActionPacket(action, terminal, pickup, destination);
    }

    public static void handle(MaidTransportActionPacket packet,
                              Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender != null) MaidTransportService.handle(sender, packet);
        });
        context.setPacketHandled(true);
    }
}
