package io.github.maidstorageextension.maid.courier;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/** Validates the mandatory flat, open-air 3x3 courier station pad. */
public final class CourierWarehouseStationValidator {
    public static final int REQUIRED_CLEARANCE = 12;
    public static final double MAX_MAILBOX_DISTANCE = 64.0;
    public static final double MAX_MAILBOX_DISTANCE_SQR =
            MAX_MAILBOX_DISTANCE * MAX_MAILBOX_DISTANCE;

    private CourierWarehouseStationValidator() {
    }

    public interface Probe {
        boolean loaded(BlockPos pos);
        boolean solidSupport(BlockPos pos);
        boolean clear(BlockPos pos);
        boolean openSky(BlockPos pos);
    }

    public static boolean hasValidPad(BlockPos station, Probe probe) {
        if (station == null || probe == null) return false;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos cell = station.offset(dx, 0, dz);
                if (!probe.loaded(cell) || !probe.solidSupport(cell.below())
                        || !probe.openSky(cell)) return false;
                for (int dy = 0; dy <= REQUIRED_CLEARANCE; dy++) {
                    if (!probe.clear(cell.above(dy))) return false;
                }
            }
        }
        return true;
    }

    public static boolean hasValidPad(ServerLevel level, BlockPos station) {
        if (level == null || station == null || !level.getWorldBorder().isWithinBounds(station)) {
            return false;
        }
        return hasValidPad(station, new Probe() {
            @Override
            public boolean loaded(BlockPos pos) {
                return level.hasChunkAt(pos);
            }

            @Override
            public boolean solidSupport(BlockPos pos) {
                BlockState state = level.getBlockState(pos);
                return state.getFluidState().isEmpty()
                        && state.isCollisionShapeFullBlock(level, pos);
            }

            @Override
            public boolean clear(BlockPos pos) {
                return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
                        && level.getFluidState(pos).isEmpty();
            }

            @Override
            public boolean openSky(BlockPos pos) {
                return level.canSeeSky(pos);
            }
        });
    }

    public static boolean overlapsPad(BlockPos landing, BlockPos mailbox) {
        return landing != null && mailbox != null
                && Math.abs(landing.getX() - mailbox.getX()) <= 1
                && Math.abs(landing.getZ() - mailbox.getZ()) <= 1;
    }

    public static boolean mailboxInRange(BlockPos landing, BlockPos mailbox) {
        return landing != null && mailbox != null
                && landing.distSqr(mailbox) <= MAX_MAILBOX_DISTANCE_SQR
                && !overlapsPad(landing, mailbox);
    }
}
