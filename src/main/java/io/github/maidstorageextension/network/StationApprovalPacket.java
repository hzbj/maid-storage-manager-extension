package io.github.maidstorageextension.network;

import io.github.maidstorageextension.MaidStorageManagerExtension;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import io.github.maidstorageextension.compat.EnderPocketCompat;
import io.github.maidstorageextension.data.WarehouseStationData;
import io.github.maidstorageextension.maid.courier.CourierWarehouseStationApprovalService;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;


public record StationApprovalPacket(int maidId, Decision decision,
                                    ResourceLocation dimension, BlockPos mailboxPos) implements CustomPacketPayload {
    public static final Type<StationApprovalPacket> TYPE = new Type<>(MaidStorageManagerExtension.id("station_approval"));
    public static final StreamCodec<RegistryFriendlyByteBuf, StationApprovalPacket> STREAM_CODEC =
            StreamCodec.of((buffer, packet) -> encode(packet, buffer), StationApprovalPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public enum Decision { APPROVE, REJECT }

    public static void encode(StationApprovalPacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.maidId);
        buffer.writeEnum(packet.decision);
        buffer.writeResourceLocation(packet.dimension);
        buffer.writeBlockPos(packet.mailboxPos);
    }

    public static StationApprovalPacket decode(FriendlyByteBuf buffer) {
        return new StationApprovalPacket(buffer.readVarInt(), buffer.readEnum(Decision.class),
                buffer.readResourceLocation(), buffer.readBlockPos());
    }

    public static void handle(StationApprovalPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer sender = (ServerPlayer) context.player();
            if (sender == null) return;
            EntityMaid warehouse = EnderPocketCompat.resolveRemoteMaid(sender, packet.maidId);
            if (warehouse == null) return;
            CourierWarehouseStationApprovalService.decide(sender, warehouse,
                    new WarehouseStationData.StationKey(packet.dimension, packet.mailboxPos),
                    packet.decision == Decision.APPROVE
                            ? CourierWarehouseStationApprovalService.Decision.APPROVE
                            : CourierWarehouseStationApprovalService.Decision.REJECT);
            EnderPocketCompat.syncRemoteProxy(sender, warehouse);
        });
    }
}
