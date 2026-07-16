package io.github.maidstorageextension.maid.courier;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CourierChunkTicketPolicyTest {
    @Test
    void courierTicketsAlwaysReachMinecraftEntityTickingLevel() {
        assertEquals(2, CourierChunkTicketPolicy.entityTickingRadius(0));
        assertEquals(2, CourierChunkTicketPolicy.entityTickingRadius(1));
        assertEquals(2, CourierChunkTicketPolicy.entityTickingRadius(2));
        assertEquals(4, CourierChunkTicketPolicy.entityTickingRadius(4));
    }

    @Test
    void entityTickingAreaAddsMinecraftsTwoLevelOffset() {
        assertEquals(2, CourierChunkTicketPolicy.ticketDistanceForEntityTickingRadius(0));
        assertEquals(3, CourierChunkTicketPolicy.ticketDistanceForEntityTickingRadius(1));
        assertEquals(4, CourierChunkTicketPolicy.ticketDistanceForEntityTickingRadius(2));
    }
}
