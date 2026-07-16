package io.github.maidstorageextension.maid.courier;

/** Converts requested courier chunk radii to Minecraft's entity-ticking ticket distance. */
public final class CourierChunkTicketPolicy {
    // ChunkLevel.FULL is 33 and ENTITY_TICKING is 31. DistanceManager subtracts this value
    // from FULL, so a region-ticket distance of 1 reaches only block ticking (32).
    private static final int MIN_ENTITY_TICKING_RADIUS = 2;

    private CourierChunkTicketPolicy() {
    }

    public static int entityTickingRadius(int requestedRadius) {
        return Math.max(MIN_ENTITY_TICKING_RADIUS, requestedRadius);
    }

    /**
     * Converts a desired radius of entity-ticking chunks around the source chunk into the
     * region-ticket distance consumed by Minecraft's {@code DistanceManager}.
     */
    public static int ticketDistanceForEntityTickingRadius(int chunkRadius) {
        return Math.max(0, chunkRadius) + MIN_ENTITY_TICKING_RADIUS;
    }
}
