package io.github.maidstorageextension.maid.courier;

import io.github.maidstorageextension.block.CourierWarehouseStationBlockEntity;
import io.github.maidstorageextension.data.CourierData;
import io.github.maidstorageextension.registry.ExtensionBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.Comparator;

/** Resolves persisted station blocks without maintaining a second global world index. */
public final class CourierWarehouseStationService {
    private static final int HORIZONTAL_SEARCH = 64;
    private static final int VERTICAL_SEARCH = 16;

    private CourierWarehouseStationService() {
    }

    public static CourierData.WarehouseBinding findNearest(ServerLevel level, BlockPos anchor) {
        return BlockPos.betweenClosedStream(
                        anchor.offset(-HORIZONTAL_SEARCH, -VERTICAL_SEARCH, -HORIZONTAL_SEARCH),
                        anchor.offset(HORIZONTAL_SEARCH, VERTICAL_SEARCH, HORIZONTAL_SEARCH))
                .filter(level::hasChunkAt)
                .filter(pos -> level.getBlockState(pos).is(ExtensionBlocks.COURIER_WAREHOUSE_STATION.get()))
                .map(BlockPos::immutable)
                .map(pos -> level.getBlockEntity(pos) instanceof CourierWarehouseStationBlockEntity station
                        ? station.binding(level) : null)
                .filter(java.util.Objects::nonNull)
                .min(Comparator.comparingDouble(binding ->
                        binding.mailboxPos().distSqr(anchor)))
                .orElse(null);
    }

    public static boolean isValid(ServerLevel level, CourierData.WarehouseBinding binding) {
        if (level == null || binding == null || !binding.hasStation()
                || !level.dimension().location().equals(binding.mailboxDimension())
                || !level.dimension().location().equals(binding.stationDimension())
                || !level.hasChunkAt(binding.mailboxPos())
                || !level.hasChunkAt(binding.stationPos())
                || !level.getBlockState(binding.mailboxPos())
                .is(ExtensionBlocks.COURIER_WAREHOUSE_STATION.get())) {
            return false;
        }
        return level.getBlockEntity(binding.mailboxPos())
                instanceof CourierWarehouseStationBlockEntity station
                && station.isBoundTo(binding.warehouse())
                && binding.stationPos().equals(station.landingPos())
                && station.validConfiguration(level);
    }
}
