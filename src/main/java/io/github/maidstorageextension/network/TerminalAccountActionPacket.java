package io.github.maidstorageextension.network;

import io.github.maidstorageextension.terminal.TerminalAccountService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public record TerminalAccountActionPacket(Action action, UUID terminal, String username,
                                          String secret, UUID target) {
    public enum Action {
        REFRESH,
        CREATE,
        LOGIN,
        LOGOUT,
        SELECT_COURIER,
        SELECT_DRIVER,
        UNREGISTER_MAID,
        CHANGE_PASSWORD,
        CONVERT_TO_COURIER,
        CONVERT_TO_DRIVER
    }

    public TerminalAccountActionPacket {
        action = action == null ? Action.REFRESH : action;
        username = username == null ? "" : username;
        secret = secret == null ? "" : secret;
    }

    public static TerminalAccountActionPacket refresh(UUID terminal) {
        return new TerminalAccountActionPacket(Action.REFRESH, terminal, "", "", null);
    }

    public static TerminalAccountActionPacket login(UUID terminal, String username, String password) {
        return new TerminalAccountActionPacket(Action.LOGIN, terminal, username, password, null);
    }

    public static TerminalAccountActionPacket create(UUID terminal, String username, String password) {
        return new TerminalAccountActionPacket(Action.CREATE, terminal, username, password, null);
    }

    public static TerminalAccountActionPacket select(UUID terminal, UUID maid, boolean driver) {
        return new TerminalAccountActionPacket(driver ? Action.SELECT_DRIVER : Action.SELECT_COURIER,
                terminal, "", "", maid);
    }

    public static TerminalAccountActionPacket convert(UUID terminal, UUID maid, boolean toDriver) {
        return new TerminalAccountActionPacket(
                toDriver ? Action.CONVERT_TO_DRIVER : Action.CONVERT_TO_COURIER,
                terminal, "", "", maid);
    }

    public static void encode(TerminalAccountActionPacket packet, FriendlyByteBuf buffer) {
        buffer.writeEnum(packet.action);
        buffer.writeUUID(packet.terminal);
        buffer.writeUtf(packet.username, 128);
        buffer.writeUtf(packet.secret, 128);
        buffer.writeBoolean(packet.target != null);
        if (packet.target != null) buffer.writeUUID(packet.target);
    }

    public static TerminalAccountActionPacket decode(FriendlyByteBuf buffer) {
        Action action = buffer.readEnum(Action.class);
        UUID terminal = buffer.readUUID();
        String username = buffer.readUtf(128);
        String secret = buffer.readUtf(128);
        UUID target = buffer.readBoolean() ? buffer.readUUID() : null;
        return new TerminalAccountActionPacket(action, terminal, username, secret, target);
    }

    public static void handle(TerminalAccountActionPacket packet,
                              Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender != null) TerminalAccountService.handle(sender, packet);
        });
        context.setPacketHandled(true);
    }
}
