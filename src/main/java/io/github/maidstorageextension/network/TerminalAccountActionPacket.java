package io.github.maidstorageextension.network;

import io.github.maidstorageextension.MaidStorageManagerExtension;

import io.github.maidstorageextension.terminal.TerminalAccountService;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

public record TerminalAccountActionPacket(Action action, UUID terminal, String username,
                                          String secret, UUID target) implements CustomPacketPayload {
    public static final Type<TerminalAccountActionPacket> TYPE = new Type<>(MaidStorageManagerExtension.id("terminal_account_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TerminalAccountActionPacket> STREAM_CODEC =
            StreamCodec.of((buffer, packet) -> encode(packet, buffer), TerminalAccountActionPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

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

    public static void encode(TerminalAccountActionPacket packet, RegistryFriendlyByteBuf buffer) {
        buffer.writeEnum(packet.action);
        buffer.writeUUID(packet.terminal);
        buffer.writeUtf(packet.username, 128);
        buffer.writeUtf(packet.secret, 128);
        buffer.writeBoolean(packet.target != null);
        if (packet.target != null) buffer.writeUUID(packet.target);
    }

    public static TerminalAccountActionPacket decode(RegistryFriendlyByteBuf buffer) {
        Action action = buffer.readEnum(Action.class);
        UUID terminal = buffer.readUUID();
        String username = buffer.readUtf(128);
        String secret = buffer.readUtf(128);
        UUID target = buffer.readBoolean() ? buffer.readUUID() : null;
        return new TerminalAccountActionPacket(action, terminal, username, secret, target);
    }

    public static void handle(TerminalAccountActionPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer sender = (ServerPlayer) context.player();
            if (sender != null) TerminalAccountService.handle(sender, packet);
        });
    }
}
