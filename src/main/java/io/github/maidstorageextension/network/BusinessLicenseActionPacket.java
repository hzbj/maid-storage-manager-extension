package io.github.maidstorageextension.network;

import io.github.maidstorageextension.data.BusinessLicenseData;
import io.github.maidstorageextension.license.BusinessLicenseService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public record BusinessLicenseActionPacket(Action action, UUID license,
                                          BusinessLicenseData.RuleMode mode, String value) {
    private static final int MAX_VALUE_BYTES = 128;

    public enum Action {
        REFRESH,
        SET_MODE,
        RENAME,
        TOGGLE_HELD_FILTER,
        TOGGLE_FILTER,
        CLEAR_FILTER,
        ARM_CONTAINER,
        ARM_LANDING,
        ARM_WORKER
    }

    public BusinessLicenseActionPacket {
        action = action == null ? Action.REFRESH : action;
        mode = mode == null ? BusinessLicenseData.RuleMode.WHITELIST : mode;
        value = value == null ? "" : value;
    }

    public static BusinessLicenseActionPacket refresh(UUID license) {
        return new BusinessLicenseActionPacket(Action.REFRESH, license,
                BusinessLicenseData.RuleMode.WHITELIST, "");
    }

    public static void encode(BusinessLicenseActionPacket packet, FriendlyByteBuf buffer) {
        buffer.writeEnum(packet.action);
        buffer.writeUUID(packet.license);
        buffer.writeEnum(packet.mode);
        buffer.writeUtf(packet.value, MAX_VALUE_BYTES);
    }

    public static BusinessLicenseActionPacket decode(FriendlyByteBuf buffer) {
        return new BusinessLicenseActionPacket(buffer.readEnum(Action.class), buffer.readUUID(),
                buffer.readEnum(BusinessLicenseData.RuleMode.class), buffer.readUtf(MAX_VALUE_BYTES));
    }

    public static void handle(BusinessLicenseActionPacket packet,
                              Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            if (context.getSender() != null) BusinessLicenseService.handle(context.getSender(), packet);
        });
        context.setPacketHandled(true);
    }
}
