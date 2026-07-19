package io.github.maidstorageextension.network;

import io.github.maidstorageextension.client.MaidPickupGuidanceClientData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/** Short-lived world-HUD target for the driver's actual boarding point. */
public record MaidPickupGuidancePacket(
        boolean active, UUID driver, ResourceLocation dimension, BlockPos position) {
    public static void encode(MaidPickupGuidancePacket packet, FriendlyByteBuf buffer) {
        buffer.writeBoolean(packet.active);
        buffer.writeBoolean(packet.driver != null);
        if (packet.driver != null) buffer.writeUUID(packet.driver);
        buffer.writeBoolean(packet.dimension != null && packet.position != null);
        if (packet.dimension != null && packet.position != null) {
            buffer.writeResourceLocation(packet.dimension);
            buffer.writeBlockPos(packet.position);
        }
    }

    public static MaidPickupGuidancePacket decode(FriendlyByteBuf buffer) {
        boolean active = buffer.readBoolean();
        UUID driver = buffer.readBoolean() ? buffer.readUUID() : null;
        boolean hasTarget = buffer.readBoolean();
        return new MaidPickupGuidancePacket(active, driver,
                hasTarget ? buffer.readResourceLocation() : null,
                hasTarget ? buffer.readBlockPos() : null);
    }

    public static void handle(MaidPickupGuidancePacket packet,
                              Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        if (context.getDirection().getReceptionSide().isClient()) {
            context.enqueueWork(() -> MaidPickupGuidanceClientData.accept(packet));
        }
        context.setPacketHandled(true);
    }
}
