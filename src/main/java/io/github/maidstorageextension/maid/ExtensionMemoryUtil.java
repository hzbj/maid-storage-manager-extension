package io.github.maidstorageextension.maid;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import io.github.maidstorageextension.maid.memory.MiscSortMemory;
import io.github.maidstorageextension.maid.memory.PeriodicScanMemory;
import io.github.maidstorageextension.maid.memory.TaskBellCallMemory;
import io.github.maidstorageextension.registry.ExtensionMemoryModules;
import studio.fantasyit.maid_storage_manager.util.MemoryUtil;

public final class ExtensionMemoryUtil {
    private ExtensionMemoryUtil() {
    }

    public static PeriodicScanMemory getPeriodicScan(EntityMaid maid) {
        if (maid.getBrain().getMemory(ExtensionMemoryModules.PERIODIC_SCAN.get()).isEmpty()) {
            maid.getBrain().setMemory(ExtensionMemoryModules.PERIODIC_SCAN.get(), new PeriodicScanMemory());
        }
        return maid.getBrain().getMemory(ExtensionMemoryModules.PERIODIC_SCAN.get()).orElseThrow();
    }

    public static MiscSortMemory getMiscSort(EntityMaid maid) {
        if (maid.getBrain().getMemory(ExtensionMemoryModules.MISC_SORT.get()).isEmpty()) {
            maid.getBrain().setMemory(ExtensionMemoryModules.MISC_SORT.get(), new MiscSortMemory());
        }
        return maid.getBrain().getMemory(ExtensionMemoryModules.MISC_SORT.get()).orElseThrow();
    }

    public static TaskBellCallMemory getTaskBellCall(EntityMaid maid) {
        return maid.getBrain().getMemory(ExtensionMemoryModules.TASK_BELL_CALL.get()).orElse(null);
    }

    public static void clearTaskBellCall(EntityMaid maid) {
        maid.getBrain().eraseMemory(ExtensionMemoryModules.TASK_BELL_CALL.get());
        MemoryUtil.clearTarget(maid);
    }
}
