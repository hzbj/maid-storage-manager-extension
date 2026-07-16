package io.github.maidstorageextension.scan;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import studio.fantasyit.maid_storage_manager.storage.Target;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InspectionTargetSelectorTest {
    @Test
    void recalculatesTheNearestPendingTargetForEveryPatrolLeg() {
        Target first = target("chest", 4, 0, 0);
        Target west = target("barrel", -5, 0, 0);
        Target east = target("chest", 8, 0, 0);

        assertEquals(first, InspectionTargetSelector.selectNearest(
                BlockPos.ZERO, List.of(first, west, east)).orElseThrow());
        assertEquals(east, InspectionTargetSelector.selectNearest(
                first.getPos(), List.of(west, east)).orElseThrow());
    }

    private static Target target(String block, int x, int y, int z) {
        return new Target(new ResourceLocation("minecraft", block), new BlockPos(x, y, z));
    }
}
