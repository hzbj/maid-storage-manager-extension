package io.github.maidstorageextension.network;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import io.github.maidstorageextension.maid.courier.CourierService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public record CourierCommandPacket(int maidId, Command command, UUID targetCourier, int value) {
    public enum Command {
        BIND_NEAREST,
        CONFIRM_DEPOSIT,
        UNBIND,
        SELECT_WAREHOUSE,
        RECALL,
        LOCATE,
        SET_BROOM_FLIGHT_DISTANCE,
        APPROVE,
        REJECT,
        SET_POST_DELIVERY_HOME,
        CLEAR_WORK
    }

    public CourierCommandPacket(int maidId, Command command, UUID targetCourier) {
        this(maidId, command, targetCourier, 0);
    }

    public static CourierCommandPacket courier(int maidId, Command command) {
        return new CourierCommandPacket(maidId, command, null, 0);
    }

    public static CourierCommandPacket broomFlightDistance(int maidId, int value) {
        return new CourierCommandPacket(maidId, Command.SET_BROOM_FLIGHT_DISTANCE, null, value);
    }

    public static CourierCommandPacket postDeliveryHomeMode(int maidId, boolean stayHome) {
        return new CourierCommandPacket(maidId, Command.SET_POST_DELIVERY_HOME,
                null, stayHome ? 1 : 0);
    }

    public static CourierCommandPacket selectWarehouse(int maidId, UUID warehouse) {
        return new CourierCommandPacket(maidId, Command.SELECT_WAREHOUSE, warehouse, 0);
    }

    public static CourierCommandPacket recall(UUID courier) {
        return new CourierCommandPacket(-1, Command.RECALL, courier, 0);
    }

    public static CourierCommandPacket locate(UUID courier) {
        return new CourierCommandPacket(-1, Command.LOCATE, courier, 0);
    }

    public static CourierCommandPacket clearWork(UUID courier) {
        return new CourierCommandPacket(-1, Command.CLEAR_WORK, courier, 0);
    }

    public static void encode(CourierCommandPacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.maidId);
        buffer.writeEnum(packet.command);
        buffer.writeBoolean(packet.targetCourier != null);
        if (packet.targetCourier != null) buffer.writeUUID(packet.targetCourier);
        buffer.writeVarInt(packet.value);
    }

    public static CourierCommandPacket decode(FriendlyByteBuf buffer) {
        int maidId = buffer.readVarInt();
        Command command = buffer.readEnum(Command.class);
        UUID target = buffer.readBoolean() ? buffer.readUUID() : null;
        int value = buffer.readVarInt();
        return new CourierCommandPacket(maidId, command, target, value);
    }

    public static void handle(CourierCommandPacket packet,
                              Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender == null) return;
            if (packet.command == Command.RECALL || packet.command == Command.LOCATE
                    || packet.command == Command.CLEAR_WORK) {
                if (packet.targetCourier != null) {
                    if (packet.command == Command.RECALL) {
                        CourierService.recall(sender, packet.targetCourier);
                    } else if (packet.command == Command.LOCATE) {
                        CourierService.locateOwnerTarget(sender, packet.targetCourier);
                    } else {
                        CourierService.clearWork(sender, packet.targetCourier);
                    }
                }
                return;
            }
            Entity entity = sender.level().getEntity(packet.maidId);
            if (!(entity instanceof EntityMaid maid)) return;
            switch (packet.command) {
                case BIND_NEAREST -> CourierService.requestNearestWarehouse(sender, maid);
                case CONFIRM_DEPOSIT -> CourierService.confirmDeposit(sender, maid);
                case UNBIND -> CourierService.unbind(sender, maid);
                case SELECT_WAREHOUSE -> {
                    if (packet.targetCourier != null) {
                        CourierService.selectWarehouse(sender, maid, packet.targetCourier);
                    }
                }
                case RECALL -> { }
                case LOCATE -> { }
                case CLEAR_WORK -> { }
                case SET_BROOM_FLIGHT_DISTANCE ->
                        CourierService.setBroomFlightDistance(sender, maid, packet.value);
                case SET_POST_DELIVERY_HOME ->
                        CourierService.setPostDeliveryHomeMode(sender, maid, packet.value != 0);
                case APPROVE -> {
                    if (packet.targetCourier != null) CourierService.approve(sender, maid, packet.targetCourier);
                }
                case REJECT -> {
                    if (packet.targetCourier != null) CourierService.reject(sender, maid, packet.targetCourier);
                }
            }
        });
        context.setPacketHandled(true);
    }
}
