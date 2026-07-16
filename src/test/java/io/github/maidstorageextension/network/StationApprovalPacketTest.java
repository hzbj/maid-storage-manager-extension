package io.github.maidstorageextension.network;

import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StationApprovalPacketTest {
    @Test
    void stationDecisionRoundTripsDimensionAndMailboxPosition() {
        StationApprovalPacket source = new StationApprovalPacket(42,
                StationApprovalPacket.Decision.APPROVE,
                new ResourceLocation("minecraft", "overworld"), new BlockPos(12, 70, -8));
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());

        StationApprovalPacket.encode(source, buffer);

        assertEquals(source, StationApprovalPacket.decode(buffer));
        assertEquals(0, buffer.readableBytes());
    }
}
