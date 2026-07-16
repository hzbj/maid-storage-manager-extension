package io.github.maidstorageextension.maid.courier;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CourierWarehouseStationValidatorTest {
    @Test
    void mailboxMustStayOutsideThePadAndWithinSixtyFourBlocks() {
        BlockPos landing = new BlockPos(0, 64, 0);

        assertFalse(CourierWarehouseStationValidator.mailboxInRange(
                landing, landing.offset(1, 0, 1)));
        assertTrue(CourierWarehouseStationValidator.mailboxInRange(
                landing, landing.offset(2, 0, 0)));
        assertTrue(CourierWarehouseStationValidator.mailboxInRange(
                landing, landing.offset(64, 0, 0)));
        assertFalse(CourierWarehouseStationValidator.mailboxInRange(
                landing, landing.offset(65, 0, 0)));
    }

    @Test
    void validStationRequiresEveryCellOfAThreeByThreeFlatOpenSkyPad() {
        BlockPos station = new BlockPos(10, 65, 10);
        FakeProbe probe = FakeProbe.valid(station);

        assertTrue(CourierWarehouseStationValidator.hasValidPad(station, probe));

        probe.solidSupports.remove(station.offset(1, -1, -1));
        assertFalse(CourierWarehouseStationValidator.hasValidPad(station, probe));

        probe = FakeProbe.valid(station);
        probe.clearCells.remove(station.offset(-1, 1, 1));
        assertFalse(CourierWarehouseStationValidator.hasValidPad(station, probe));

        probe = FakeProbe.valid(station);
        probe.skyCells.remove(station.offset(0, 0, 1));
        assertFalse(CourierWarehouseStationValidator.hasValidPad(station, probe));
    }

    private static final class FakeProbe implements CourierWarehouseStationValidator.Probe {
        private final Set<BlockPos> loaded = new HashSet<>();
        private final Set<BlockPos> solidSupports = new HashSet<>();
        private final Set<BlockPos> clearCells = new HashSet<>();
        private final Set<BlockPos> skyCells = new HashSet<>();

        static FakeProbe valid(BlockPos station) {
            FakeProbe probe = new FakeProbe();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos cell = station.offset(dx, 0, dz);
                    probe.loaded.add(cell);
                    probe.solidSupports.add(cell.below());
                    probe.skyCells.add(cell);
                    for (int dy = 0; dy <= CourierWarehouseStationValidator.REQUIRED_CLEARANCE; dy++) {
                        probe.clearCells.add(cell.above(dy));
                    }
                }
            }
            return probe;
        }

        @Override public boolean loaded(BlockPos pos) { return loaded.contains(pos); }
        @Override public boolean solidSupport(BlockPos pos) { return solidSupports.contains(pos); }
        @Override public boolean clear(BlockPos pos) { return clearCells.contains(pos); }
        @Override public boolean openSky(BlockPos pos) { return skyCells.contains(pos); }
    }
}
