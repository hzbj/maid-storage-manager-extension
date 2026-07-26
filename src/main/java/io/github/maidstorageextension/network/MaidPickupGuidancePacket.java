package io.github.maidstorageextension.network;

import io.github.maidstorageextension.MaidStorageManagerExtension;

import io.github.maidstorageextension.client.MaidPickupGuidanceClientData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/** Short-lived world-HUD target for the driver's actual boarding point. */
public record MaidPickupGuidancePacket(
        boolean active, UUID driver, ResourceLocation dimension, BlockPos position) implements CustomPacketPayload {
    public static final Type<MaidPickupGuidancePacket> TYPE = new Type<>(MaidStorageManagerExtension.id("maid_pickup_guidance"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MaidPickupGuidancePacket> STREAM_CODEC =
            StreamCodec.of((buffer, packet) -> encode(packet, buffer), MaidPickupGuidancePacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void encode(MaidPickupGuidancePacket packet, RegistryFriendlyByteBuf buffer) {
        buffer.writeBoolean(packet.active);
        buffer.writeBoolean(packet.driver != null);
        if (packet.driver != null) buffer.writeUUID(packet.driver);
        buffer.writeBoolean(packet.dimension != null && packet.position != null);
        if (packet.dimension != null && packet.position != null) {
            buffer.writeResourceLocation(packet.dimension);
            buffer.writeBlockPos(packet.position);
        }
    }

    public static MaidPickupGuidancePacket decode(RegistryFriendlyByteBuf buffer) {
        boolean active = buffer.readBoolean();
        UUID driver = buffer.readBoolean() ? buffer.readUUID() : null;
        boolean hasTarget = buffer.readBoolean();
        return new MaidPickupGuidancePacket(active, driver,
                hasTarget ? buffer.readResourceLocation() : null,
                hasTarget ? buffer.readBlockPos() : null);
    }

    public static void handle(MaidPickupGuidancePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> MaidPickupGuidanceClientData.accept(packet));
    }
}
