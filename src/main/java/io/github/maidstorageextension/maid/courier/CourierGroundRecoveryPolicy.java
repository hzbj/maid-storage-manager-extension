package io.github.maidstorageextension.maid.courier;

import io.github.maidstorageextension.data.CourierData;
import net.minecraft.core.BlockPos;

/** Progress watchdog for handing a blocked ground route to the maid's native safe teleport. */
public final class CourierGroundRecoveryPolicy {
    public static final long STALL_TICKS = 60L;
    private static final double PROGRESS_DISTANCE_SQUARED = 4.0;

    private CourierGroundRecoveryPolicy() {
    }

    public static Update update(CourierData.Phase previousPhase, BlockPos previousPosition,
                                long previousProgressGameTime, CourierData.Phase currentPhase,
                                BlockPos currentPosition, long gameTime) {
        BlockPos current = currentPosition == null ? null : currentPosition.immutable();
        if (currentPhase == null || current == null) {
            return new Update(null, null, -1L, false, false, previousPhase != null
                    || previousPosition != null || previousProgressGameTime >= 0L);
        }
        if (previousPhase != currentPhase || previousPosition == null
                || previousProgressGameTime < 0L) {
            return new Update(currentPhase, current, gameTime, false, false, true);
        }
        if (previousPosition.distSqr(current) >= PROGRESS_DISTANCE_SQUARED) {
            return new Update(currentPhase, current, gameTime, false, true, true);
        }
        if (gameTime - previousProgressGameTime >= STALL_TICKS) {
            return new Update(currentPhase, current, gameTime, true, false, true);
        }
        return new Update(previousPhase, previousPosition, previousProgressGameTime,
                false, false, false);
    }

    public record Update(CourierData.Phase phase, BlockPos position, long progressGameTime,
                         boolean shouldTeleport, boolean progressed, boolean changed) {
    }
}
