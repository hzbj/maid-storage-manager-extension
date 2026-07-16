package io.github.maidstorageextension.scan;

import net.minecraft.core.BlockPos;
import studio.fantasyit.maid_storage_manager.storage.Target;

import java.util.Collection;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;

/** Chooses the nearest not-yet-successful target for each patrol leg. */
final class InspectionTargetSelector {
    private InspectionTargetSelector() {
    }

    static Optional<Target> selectNearest(BlockPos origin, Collection<Target> pending) {
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(pending, "pending");
        return pending.stream()
                .min(Comparator.comparingDouble((Target target) -> origin.distSqr(target.getPos()))
                        .thenComparing(Target::toStoreString));
    }
}
