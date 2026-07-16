package io.github.maidstorageextension.maid.courier;

import net.minecraft.core.BlockPos;

import java.util.Collection;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.IntBinaryOperator;

/** Pure ordering/filter seam for selecting a safe courier flight staging point. */
public final class CourierFlightPointSelector {
    private CourierFlightPointSelector() {
    }

    public static BlockPos select(BlockPos anchor,
                                  Collection<BlockPos> candidates,
                                  Predicate<BlockPos> isLoaded,
                                  Predicate<BlockPos> hasSafeClearance,
                                  Predicate<BlockPos> isReachable) {
        return select(anchor, candidates, isLoaded, hasSafeClearance, isReachable, 0);
    }

    public static BlockPos select(BlockPos anchor,
                                  Collection<BlockPos> candidates,
                                  Predicate<BlockPos> isLoaded,
                                  Predicate<BlockPos> hasSafeClearance,
                                  Predicate<BlockPos> isReachable,
                                  int skipSafeCandidates) {
        Objects.requireNonNull(anchor, "anchor");
        Objects.requireNonNull(candidates, "candidates");
        Objects.requireNonNull(isLoaded, "isLoaded");
        Objects.requireNonNull(hasSafeClearance, "hasSafeClearance");
        Objects.requireNonNull(isReachable, "isReachable");
        return candidates.stream()
                .filter(Objects::nonNull)
                .filter(isLoaded)
                .sorted(Comparator.comparingDouble((BlockPos pos) -> pos.distSqr(anchor))
                        .thenComparingLong(BlockPos::asLong))
                // Clearance checks inspect a 5x5x3 footprint. Sort first so the stream stops
                // as soon as the nearest valid point is found instead of probing the full radius.
                .filter(hasSafeClearance)
                .filter(isReachable)
                .skip(Math.max(0, skipSafeCandidates))
                .findFirst()
                .orElse(null);
    }

    /** Creates surface positions in an annulus, keeping landing outside hand-off range. */
    static List<BlockPos> surfaceCandidates(BlockPos anchor, int minRadius, int maxRadius,
                                            IntBinaryOperator surfaceHeight) {
        int minimum = Math.max(0, minRadius);
        int maximum = Math.max(minimum, maxRadius);
        int minSquared = minimum * minimum;
        int maxSquared = maximum * maximum;
        List<BlockPos> result = new ArrayList<>();
        for (int dx = -maximum; dx <= maximum; dx++) {
            for (int dz = -maximum; dz <= maximum; dz++) {
                int horizontalSquared = dx * dx + dz * dz;
                if (horizontalSquared < minSquared || horizontalSquared > maxSquared) continue;
                int x = anchor.getX() + dx;
                int z = anchor.getZ() + dz;
                result.add(new BlockPos(x, surfaceHeight.applyAsInt(x, z), z));
            }
        }
        return result;
    }
}
