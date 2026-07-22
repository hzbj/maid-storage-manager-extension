package io.github.maidstorageextension.network;

import io.github.maidstorageextension.logistics.MaidLogisticsData;
import io.github.maidstorageextension.logistics.MaidLogisticsService;
import io.github.maidstorageextension.logistics.MaidLogisticsSnapshot;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

public record MaidLogisticsActionPacket(Action action, UUID terminal, UUID route,
                                        long revision, MaidLogisticsData.NodeRef source,
                                        MaidLogisticsData.NodeRef destination, UUID courier,
                                        List<MaidLogisticsData.CargoLine> lines,
                                        int firstIndex, int secondIndex) {
    public enum Action {
        REFRESH,
        CREATE_ROUTE,
        UPDATE_ROUTE,
        DELETE_ROUTE,
        ADD_SLOT,
        REMOVE_SLOT,
        MOVE_SLOT,
        OPEN_LICENSE
    }

    public MaidLogisticsActionPacket {
        action = action == null ? Action.REFRESH : action;
        lines = lines == null ? List.of() : List.copyOf(lines);
    }

    public static MaidLogisticsActionPacket refresh(UUID terminal) {
        return new MaidLogisticsActionPacket(Action.REFRESH, terminal, null, 0L,
                null, null, null, List.of(), 0, 0);
    }

    public static MaidLogisticsActionPacket openLicense(UUID terminal,
                                                        MaidLogisticsData.NodeRef license) {
        return new MaidLogisticsActionPacket(Action.OPEN_LICENSE, terminal, null, 0L,
                license, null, null, List.of(), 0, 0);
    }

    public static void encode(MaidLogisticsActionPacket packet, FriendlyByteBuf buffer) {
        CompoundTag tag = new CompoundTag();
        tag.putString("action", packet.action.name());
        tag.putUUID("terminal", packet.terminal);
        if (packet.route != null) tag.putUUID("route", packet.route);
        tag.putLong("revision", packet.revision);
        if (packet.source != null) tag.put("source", MaidLogisticsSnapshot.nodeTag(packet.source));
        if (packet.destination != null) tag.put("destination", MaidLogisticsSnapshot.nodeTag(packet.destination));
        if (packet.courier != null) tag.putUUID("courier", packet.courier);
        if (!packet.lines.isEmpty()) {
            UUID id = packet.route == null ? UUID.randomUUID() : packet.route;
            UUID account = new UUID(0L, 0L);
            MaidLogisticsData.NodeRef source = packet.source == null
                    ? new MaidLogisticsData.NodeRef(MaidLogisticsData.NodeKind.LICENSE,
                    net.minecraft.world.level.Level.OVERWORLD.location(), net.minecraft.core.BlockPos.ZERO,
                    new UUID(0L, 1L)) : packet.source;
            MaidLogisticsData.NodeRef destination = packet.destination == null
                    ? new MaidLogisticsData.NodeRef(MaidLogisticsData.NodeKind.WAREHOUSE,
                    net.minecraft.world.level.Level.OVERWORLD.location(), net.minecraft.core.BlockPos.ZERO, null)
                    : packet.destination;
            tag.put("routePayload", MaidLogisticsSnapshot.routeTag(new MaidLogisticsData.Route(
                    id, account, source, destination,
                    packet.courier == null ? new UUID(0L, 2L) : packet.courier,
                    packet.lines, MaidLogisticsData.RouteStatus.READY, "", false, 1L)));
        }
        tag.putInt("first", packet.firstIndex);
        tag.putInt("second", packet.secondIndex);
        buffer.writeNbt(tag);
    }

    public static MaidLogisticsActionPacket decode(FriendlyByteBuf buffer) {
        CompoundTag tag = buffer.readNbt();
        if (tag == null || !tag.hasUUID("terminal")) throw new IllegalArgumentException("Missing terminal");
        Action action = Action.valueOf(tag.getString("action"));
        List<MaidLogisticsData.CargoLine> lines = List.of();
        if (tag.contains("routePayload", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            MaidLogisticsData.Route payload = MaidLogisticsSnapshot.routeFromTag(
                    tag.getCompound("routePayload"));
            if (payload == null) throw new IllegalArgumentException("Invalid route payload");
            lines = payload.lines();
        }
        return new MaidLogisticsActionPacket(action, tag.getUUID("terminal"),
                tag.hasUUID("route") ? tag.getUUID("route") : null, tag.getLong("revision"),
                tag.contains("source", net.minecraft.nbt.Tag.TAG_COMPOUND)
                        ? MaidLogisticsSnapshot.nodeFromTag(tag.getCompound("source")) : null,
                tag.contains("destination", net.minecraft.nbt.Tag.TAG_COMPOUND)
                        ? MaidLogisticsSnapshot.nodeFromTag(tag.getCompound("destination")) : null,
                tag.hasUUID("courier") ? tag.getUUID("courier") : null,
                lines, tag.getInt("first"), tag.getInt("second"));
    }

    public static void handle(MaidLogisticsActionPacket packet,
                              Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            if (context.getSender() != null) MaidLogisticsService.handle(context.getSender(), packet);
        });
        context.setPacketHandled(true);
    }
}
