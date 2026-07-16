package io.github.maidstorageextension.maid.courier;

import io.github.maidstorageextension.data.CourierData;

/** Pure rules for keeping a storage maid's courier job alive without nearby players. */
public final class CourierWarehouseLoadingPolicy {
    private static final int CHUNK_SIZE = 16;

    private CourierWarehouseLoadingPolicy() {
    }

    public static boolean shouldKeepWarehouseTaskLoaded(CourierData.Phase phase) {
        if (phase == null) return false;
        return switch (phase) {
            case TRAVEL_TO_WAREHOUSE_REQUEST, REQUEST_HANDOFF,
                    REQUEST_RUNNING, REQUEST_WAITING_SPACE,
                    TRAVEL_TO_WAREHOUSE_DEPOSIT, DEPOSIT_HANDOFF,
                    DEPOSIT_RUNNING, DEPOSIT_RETURNING, DEPOSIT_WAITING_SPACE -> true;
            default -> false;
        };
    }

    /** Returns the chunk radius needed to cover a block radius around an arbitrary block. */
    public static int chunkRadius(int centerX, int centerZ, double blockRadius) {
        double radius = Math.max(0.0, blockRadius);
        int centerChunkX = Math.floorDiv(centerX, CHUNK_SIZE);
        int centerChunkZ = Math.floorDiv(centerZ, CHUNK_SIZE);
        int minChunkX = floorChunk(centerX - radius);
        int maxChunkX = floorChunk(centerX + radius);
        int minChunkZ = floorChunk(centerZ - radius);
        int maxChunkZ = floorChunk(centerZ + radius);
        return Math.max(
                Math.max(Math.abs(minChunkX - centerChunkX), Math.abs(maxChunkX - centerChunkX)),
                Math.max(Math.abs(minChunkZ - centerChunkZ), Math.abs(maxChunkZ - centerChunkZ)));
    }

    private static int floorChunk(double blockCoordinate) {
        return (int) Math.floor(blockCoordinate / CHUNK_SIZE);
    }
}
