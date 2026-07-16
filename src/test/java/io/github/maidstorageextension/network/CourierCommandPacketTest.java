package io.github.maidstorageextension.network;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CourierCommandPacketTest {
    @Test
    void broomFlightDistanceRoundTrips() {
        CourierCommandPacket source = CourierCommandPacket.broomFlightDistance(23, 56);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());

        CourierCommandPacket.encode(source, buffer);

        assertEquals(source, CourierCommandPacket.decode(buffer));
        assertEquals(0, buffer.readableBytes());
    }

    @Test
    void remoteRecallAndDefaultStationCommandsRoundTrip() {
        UUID courier = UUID.randomUUID();
        UUID warehouse = UUID.randomUUID();
        CourierCommandPacket[] packets = {
                CourierCommandPacket.recall(courier),
                CourierCommandPacket.locate(courier),
                CourierCommandPacket.clearWork(courier),
                CourierCommandPacket.selectWarehouse(23, warehouse)
        };

        for (CourierCommandPacket source : packets) {
            FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
            CourierCommandPacket.encode(source, buffer);
            assertEquals(source, CourierCommandPacket.decode(buffer));
            assertEquals(0, buffer.readableBytes());
        }
    }

    @Test
    void postDeliveryHomeModeRoundTrips() {
        CourierCommandPacket source = CourierCommandPacket.postDeliveryHomeMode(23, true);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());

        CourierCommandPacket.encode(source, buffer);

        assertEquals(source, CourierCommandPacket.decode(buffer));
        assertEquals(0, buffer.readableBytes());
    }
}
