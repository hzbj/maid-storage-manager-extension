package io.github.maidstorageextension.network;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import io.github.maidstorageextension.compat.EnderPocketCompat;
import io.github.maidstorageextension.data.WarehouseStationData;
import io.github.maidstorageextension.maid.courier.CourierWarehouseStationApprovalService;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record StationApprovalPacket(int maidId, Decision decision,
                                    ResourceLocation dimension, BlockPos mailboxPos) {
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

    public static void handle(StationApprovalPacket packet,
                              Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
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
        context.setPacketHandled(true);
    }
}
