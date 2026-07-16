package io.github.maidstorageextension.scan;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import io.github.maidstorageextension.maid.ExtensionMemoryUtil;
import io.github.maidstorageextension.maid.behavior.MiscSortBehavior;
import io.github.maidstorageextension.maid.memory.MiscSortMemory;
import net.minecraft.server.level.ServerLevel;
import studio.fantasyit.maid_storage_manager.maid.task.StorageManageTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Returns an already extracted payload even if the player changes the maid's selected task. */
public final class MiscSortRecoveryService {
    private static final Map<UUID, MiscSortBehavior> RECOVERIES = new HashMap<>();

    private MiscSortRecoveryService() {
    }

    public static void tick(ServerLevel level, EntityMaid maid) {
        MiscSortMemory sort = ExtensionMemoryUtil.getMiscSort(maid);
        boolean storageTask = maid.getTask().getUid().equals(StorageManageTask.TASK_ID);
        if (!requiresExternalRecovery(sort.hasInFlight(), storageTask)) {
            release(maid);
            return;
        }
        if (maid.getTarget() != null) {
            release(maid);
            return;
        }

        // The task switch cancels work not yet extracted. The physical payload must go only home.
        sort.clearUnstartedWork();
        sort.forceActiveBatchReturn();
        MiscSortBehavior behavior = RECOVERIES.computeIfAbsent(
                maid.getUUID(), ignored -> new MiscSortBehavior());
        behavior.tickExternalRecovery(level, maid);
        if (!sort.hasInFlight()) {
            behavior.stopExternalRecovery(maid);
            RECOVERIES.remove(maid.getUUID());
            StorageScanService.cancelPeriodic(maid);
        }
    }

    public static void release(EntityMaid maid) {
        MiscSortBehavior behavior = RECOVERIES.remove(maid.getUUID());
        if (behavior != null) behavior.stopExternalRecovery(maid);
    }

    static boolean requiresExternalRecovery(boolean inFlight, boolean storageTask) {
        return inFlight && !storageTask;
    }

    public static boolean shouldSuspendBrain(boolean inFlight, boolean storageTask) {
        return requiresExternalRecovery(inFlight, storageTask);
    }
}
