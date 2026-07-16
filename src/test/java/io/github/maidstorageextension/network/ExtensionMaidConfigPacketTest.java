package io.github.maidstorageextension.network;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExtensionMaidConfigPacketTest {
    @Test
    void miscSortMatchNbtRoundTripsAsTheTrailingField() {
        ExtensionMaidConfigPacket source = new ExtensionMaidConfigPacket(
                17, 3, 24, 96, 45, 25, false, true, true);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());

        ExtensionMaidConfigPacket.encode(source, buffer);

        assertEquals(source, ExtensionMaidConfigPacket.decode(buffer));
        assertEquals(0, buffer.readableBytes());
    }
}
