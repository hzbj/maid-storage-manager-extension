package io.github.maidstorageextension.network;

import io.github.maidstorageextension.logistics.NetworkWarehouseService;
import io.github.maidstorageextension.terminal.MailboxKey;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/** Client intent for the communication terminal's warehouse page; every value is revalidated server-side. */
public record NetworkWarehouseActionPacket(Action action, UUID terminal, UUID courier, UUID warehouse,
                                           MailboxKey mailbox,
                                           DeliveryTarget deliveryTarget, boolean acceptStale,
                                           List<RequestedItem> requestedItems) {
    public static final int MAX_REQUEST_LINES = 10;
    public static final int MAX_REQUEST_AMOUNT = 1_000_000;

    public enum Action {
        REFRESH,
        SELECT_WAREHOUSE,
        SUBMIT_REQUEST,
        CONFIRM_DEPOSIT
    }

    public enum DeliveryTarget {
        PLAYER,
        FIXED_CHEST
    }

    public record RequestedItem(ItemStack prototype, int amount) {
        public RequestedItem {
            prototype = prototype == null || prototype.isEmpty()
                    ? ItemStack.EMPTY : prototype.copyWithCount(1);
        }
    }

    public NetworkWarehouseActionPacket {
        action = action == null ? Action.REFRESH : action;
        deliveryTarget = deliveryTarget == null ? DeliveryTarget.PLAYER : deliveryTarget;
        requestedItems = requestedItems == null ? List.of() : List.copyOf(requestedItems);
    }

    public static NetworkWarehouseActionPacket refresh(UUID terminal, UUID courier, MailboxKey mailbox) {
        return new NetworkWarehouseActionPacket(Action.REFRESH, terminal, courier, null, mailbox,
                DeliveryTarget.PLAYER, false, List.of());
    }

    public static NetworkWarehouseActionPacket select(
            UUID terminal, UUID courier, UUID warehouse, MailboxKey mailbox) {
        return new NetworkWarehouseActionPacket(
                Action.SELECT_WAREHOUSE, terminal, courier, warehouse, mailbox,
                DeliveryTarget.PLAYER, false, List.of());
    }

    public static NetworkWarehouseActionPacket submit(
            UUID terminal, UUID courier, UUID warehouse, MailboxKey mailbox,
            DeliveryTarget target, boolean acceptStale,
            List<RequestedItem> requestedItems) {
        return new NetworkWarehouseActionPacket(Action.SUBMIT_REQUEST, terminal, courier, warehouse,
                mailbox,
                target, acceptStale, requestedItems);
    }

    public static NetworkWarehouseActionPacket confirmDeposit(
            UUID terminal, UUID courier, UUID warehouse, MailboxKey mailbox) {
        return new NetworkWarehouseActionPacket(
                Action.CONFIRM_DEPOSIT, terminal, courier, warehouse, mailbox,
                DeliveryTarget.PLAYER, false, List.of());
    }

    public static void encode(NetworkWarehouseActionPacket packet, FriendlyByteBuf buffer) {
        buffer.writeEnum(packet.action);
        buffer.writeUUID(packet.terminal);
        buffer.writeUUID(packet.courier);
        buffer.writeBoolean(packet.warehouse != null);
        if (packet.warehouse != null) buffer.writeUUID(packet.warehouse);
        buffer.writeBoolean(packet.mailbox != null && packet.mailbox.valid());
        if (packet.mailbox != null && packet.mailbox.valid()) {
            buffer.writeResourceLocation(packet.mailbox.dimension());
            buffer.writeBlockPos(packet.mailbox.position());
        }
        buffer.writeEnum(packet.deliveryTarget);
        buffer.writeBoolean(packet.acceptStale);
        buffer.writeVarInt(packet.requestedItems.size());
        for (RequestedItem item : packet.requestedItems) {
            buffer.writeItem(item.prototype);
            buffer.writeVarInt(item.amount);
        }
    }

    public static NetworkWarehouseActionPacket decode(FriendlyByteBuf buffer) {
        Action action = buffer.readEnum(Action.class);
        UUID terminal = buffer.readUUID();
        UUID courier = buffer.readUUID();
        UUID warehouse = buffer.readBoolean() ? buffer.readUUID() : null;
        MailboxKey mailbox = buffer.readBoolean()
                ? new MailboxKey(buffer.readResourceLocation(), buffer.readBlockPos()) : null;
        DeliveryTarget delivery = buffer.readEnum(DeliveryTarget.class);
        boolean acceptStale = buffer.readBoolean();
        int size = buffer.readVarInt();
        if (size < 0 || size > MAX_REQUEST_LINES) {
            throw new IllegalArgumentException("Invalid network warehouse request line count: " + size);
        }
        List<RequestedItem> items = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            ItemStack stack = buffer.readItem();
            int amount = buffer.readVarInt();
            if (amount <= 0 || amount > MAX_REQUEST_AMOUNT) {
                throw new IllegalArgumentException("Invalid network warehouse request amount: " + amount);
            }
            items.add(new RequestedItem(stack, amount));
        }
        return new NetworkWarehouseActionPacket(
                action, terminal, courier, warehouse, mailbox, delivery, acceptStale, items);
    }

    public static void handle(NetworkWarehouseActionPacket packet,
                              Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender != null) NetworkWarehouseService.handle(sender, packet);
        });
        context.setPacketHandled(true);
    }
}
