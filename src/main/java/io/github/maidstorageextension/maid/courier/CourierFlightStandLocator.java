package io.github.maidstorageextension.maid.courier;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.pathfinder.Path;
import studio.fantasyit.maid_storage_manager.util.PosUtil;

/** Finds open-air standing positions used before mounting and after dismounting a courier broom. */
public final class CourierFlightStandLocator {
    private static final int LANDING_MIN_RADIUS = 4;
    private static final int VERTICAL_CLEARANCE = 12;
    private static final int HORIZONTAL_CLEARANCE_RADIUS = 2;

    private CourierFlightStandLocator() {
    }

    public static BlockPos findTakeoff(ServerLevel level, EntityMaid courier, int searchRadius) {
        BlockPos anchor = courier.blockPosition();
        return CourierFlightPointSelector.select(anchor,
                candidates(level, anchor, 0, searchRadius),
                level::hasChunkAt,
                pos -> hasSafeVerticalColumn(level, pos),
                pos -> reachable(courier, pos));
    }

    public static BlockPos findLanding(ServerLevel level, BlockPos target, int searchRadius) {
        return findLanding(level, target, LANDING_MIN_RADIUS, searchRadius, 0);
    }

    public static BlockPos findLanding(ServerLevel level, BlockPos target, int minRadius,
                                       int maxRadius, int rejectedCandidates) {
        return CourierFlightPointSelector.select(target,
                candidates(level, target, minRadius, maxRadius),
                level::hasChunkAt,
                pos -> hasSafeVerticalColumn(level, pos),
                pos -> true,
                rejectedCandidates);
    }

    public static boolean hasSafeVerticalColumn(ServerLevel level, BlockPos standPos) {
        if (!footprintLoaded(level, standPos)) return false;
        if (!level.getWorldBorder().isWithinBounds(standPos)
                || !PosUtil.isSafePos(level, standPos)
                || !level.canSeeSky(standPos)) return false;

        // Require a truly flat 5x5 pad. Checking only the centre support allowed stairs,
        // slabs, holes and roof edges to pass even though the dismount or ground path got stuck.
        for (int dx = -HORIZONTAL_CLEARANCE_RADIUS; dx <= HORIZONTAL_CLEARANCE_RADIUS; dx++) {
            for (int dz = -HORIZONTAL_CLEARANCE_RADIUS; dz <= HORIZONTAL_CLEARANCE_RADIUS; dz++) {
                BlockPos supportPos = standPos.offset(dx, -1, dz);
                BlockState support = level.getBlockState(supportPos);
                if (!support.getFluidState().isEmpty()
                        || !support.isCollisionShapeFullBlock(level, supportPos)) return false;
                // The whole broom footprint, not just its centre column, must have open sky.
                for (int dy = 0; dy <= VERTICAL_CLEARANCE; dy++) {
                    if (!isEmpty(level, standPos.offset(dx, dy, dz))) return false;
                }
            }
        }
        return true;
    }

    public static boolean searchAreaLoaded(ServerLevel level, BlockPos target, int radius) {
        int value = Math.max(0, radius);
        return level.hasChunkAt(target.offset(-value, 0, -value))
                && level.hasChunkAt(target.offset(-value, 0, value))
                && level.hasChunkAt(target.offset(value, 0, -value))
                && level.hasChunkAt(target.offset(value, 0, value))
                && level.hasChunkAt(target.offset(-value, 0, 0))
                && level.hasChunkAt(target.offset(value, 0, 0))
                && level.hasChunkAt(target.offset(0, 0, -value))
                && level.hasChunkAt(target.offset(0, 0, value));
    }

    private static boolean isEmpty(ServerLevel level, BlockPos position) {
        return level.getBlockState(position).getCollisionShape(level, position).isEmpty()
                && level.getFluidState(position).isEmpty();
    }

    private static boolean footprintLoaded(ServerLevel level, BlockPos standPos) {
        return level.hasChunkAt(standPos.offset(-HORIZONTAL_CLEARANCE_RADIUS, 0,
                        -HORIZONTAL_CLEARANCE_RADIUS))
                && level.hasChunkAt(standPos.offset(-HORIZONTAL_CLEARANCE_RADIUS, 0,
                        HORIZONTAL_CLEARANCE_RADIUS))
                && level.hasChunkAt(standPos.offset(HORIZONTAL_CLEARANCE_RADIUS, 0,
                        -HORIZONTAL_CLEARANCE_RADIUS))
                && level.hasChunkAt(standPos.offset(HORIZONTAL_CLEARANCE_RADIUS, 0,
                        HORIZONTAL_CLEARANCE_RADIUS));
    }

    private static java.util.List<BlockPos> candidates(ServerLevel level, BlockPos anchor,
                                                        int minRadius, int maxRadius) {
        return CourierFlightPointSelector.surfaceCandidates(anchor, minRadius, maxRadius,
                (x, z) -> {
                    BlockPos probe = new BlockPos(x, anchor.getY(), z);
                    if (!level.hasChunkAt(probe)) return level.getMinBuildHeight();
                    return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                });
    }

    private static boolean reachable(EntityMaid courier, BlockPos position) {
        if (courier.distanceToSqr(position.getCenter()) <= 2.25) return true;
        Path path = courier.getNavigation().createPath(position, 0);
        return path != null && path.canReach();
    }
}
