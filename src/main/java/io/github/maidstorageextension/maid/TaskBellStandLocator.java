package io.github.maidstorageextension.maid;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.pathfinder.Path;
import org.jetbrains.annotations.Nullable;
import studio.fantasyit.maid_storage_manager.util.PosUtil;

import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Stream;

/** Finds a reachable standing block beside a task bell without requiring it to be inside the maid work restriction. */
public final class TaskBellStandLocator {
    private TaskBellStandLocator() {
    }

    @Nullable
    public static BlockPos find(ServerLevel level, EntityMaid maid, BlockPos bellPos) {
        BlockPos current = maid.blockPosition();
        if (current.distManhattan(bellPos) <= 3 && isUsable(level, maid, bellPos, current)) {
            return current.immutable();
        }

        Optional<Candidate> best = candidates(bellPos)
                .filter(pos -> isUsable(level, maid, bellPos, pos))
                .map(pos -> toCandidate(maid, pos))
                .flatMap(Optional::stream)
                .min(Comparator.comparingInt(Candidate::pathNodes)
                        .thenComparingDouble(candidate -> maid.distanceToSqr(candidate.pos().getCenter())));
        return best.map(Candidate::pos).orElse(null);
    }

    public static boolean isUsable(ServerLevel level, EntityMaid maid, BlockPos bellPos, @Nullable BlockPos standPos) {
        return standPos != null
                && !standPos.equals(bellPos)
                && PosUtil.isSafePos(level, standPos)
                && PosUtil.canTouch(level, standPos, bellPos);
    }

    private static Stream<BlockPos> candidates(BlockPos bellPos) {
        Stream.Builder<BlockPos> builder = Stream.builder();
        for (int y = -1; y <= 1; y++) {
            for (int x = -2; x <= 2; x++) {
                for (int z = -2; z <= 2; z++) {
                    if ((x == 0 && z == 0) || Math.abs(x) + Math.abs(z) > 3) {
                        continue;
                    }
                    builder.add(bellPos.offset(x, y, z).immutable());
                }
            }
        }
        return builder.build().distinct();
    }

    private static Optional<Candidate> toCandidate(EntityMaid maid, BlockPos pos) {
        Path path = maid.getNavigation().createPath(pos, 0);
        if (path == null || !path.canReach()) {
            return Optional.empty();
        }
        return Optional.of(new Candidate(pos, path.getNodeCount()));
    }

    private record Candidate(BlockPos pos, int pathNodes) {
    }
}
