package io.github.maidstorageextension.maid.courier;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CourierFlightPointSelectorTest {
    @Test
    void rejectsCoveredAndUnreachablePointsBeforeChoosingTheNearestSafeOne() {
        BlockPos anchor = new BlockPos(0, 64, 0);
        BlockPos covered = new BlockPos(2, 64, 0);
        BlockPos unreachable = new BlockPos(3, 64, 0);
        BlockPos safe = new BlockPos(5, 64, 0);

        BlockPos selected = CourierFlightPointSelector.select(
                anchor, List.of(covered, unreachable, safe),
                pos -> true,
                pos -> !pos.equals(covered),
                pos -> !pos.equals(unreachable));

        assertEquals(safe, selected);
    }

    @Test
    void skipsPreviouslyRejectedSafePointsWhenAThreeTryLandingIsAbandoned() {
        BlockPos anchor = new BlockPos(0, 64, 0);
        BlockPos first = new BlockPos(5, 64, 0);
        BlockPos second = new BlockPos(6, 64, 0);

        BlockPos selected = CourierFlightPointSelector.select(
                anchor, List.of(first, second), pos -> true, pos -> true, pos -> true, 1);

        assertEquals(second, selected);
    }

    @Test
    void returnsNoPointWhenEveryCandidateWouldClipOrTrapTheMaid() {
        BlockPos anchor = new BlockPos(0, 64, 0);
        List<BlockPos> candidates = List.of(
                new BlockPos(4, 64, 0), new BlockPos(0, 64, 4));

        assertNull(CourierFlightPointSelector.select(
                anchor, candidates, Set.of()::contains, pos -> true, pos -> true));
    }

    @Test
    void landingCandidatesStayOutsideTheGroundHandoffRadius() {
        BlockPos anchor = new BlockPos(10, 64, 10);

        List<BlockPos> candidates = CourierFlightPointSelector.surfaceCandidates(
                anchor, 4, 6, (x, z) -> 64);

        for (BlockPos candidate : candidates) {
            int dx = candidate.getX() - anchor.getX();
            int dz = candidate.getZ() - anchor.getZ();
            int horizontalSquared = dx * dx + dz * dz;
            org.junit.jupiter.api.Assertions.assertTrue(horizontalSquared >= 16);
            org.junit.jupiter.api.Assertions.assertTrue(horizontalSquared <= 36);
        }
    }
}
