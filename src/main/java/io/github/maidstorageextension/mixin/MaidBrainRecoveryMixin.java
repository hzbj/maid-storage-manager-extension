package io.github.maidstorageextension.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import io.github.maidstorageextension.maid.ExtensionMemoryUtil;
import io.github.maidstorageextension.scan.MiscSortRecoveryService;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import studio.fantasyit.maid_storage_manager.maid.task.StorageManageTask;

/**
 * Prevents a newly selected task from consuming a persisted misc-sort payload while the
 * task-independent recovery runner is returning it to the original source.
 */
@Mixin(Brain.class)
public abstract class MaidBrainRecoveryMixin {
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void maidStorageExtension$holdNewTaskDuringCargoRecovery(
            ServerLevel level, LivingEntity entity, CallbackInfo ci) {
        if (!(entity instanceof EntityMaid maid)) return;
        // Preserve the existing combat defer rule; recovery resumes as soon as the
        // attack target clears, when task work would otherwise become eligible again.
        if (maid.getTarget() != null) return;
        boolean storageTask = maid.getTask().getUid().equals(StorageManageTask.TASK_ID);
        if (MiscSortRecoveryService.shouldSuspendBrain(
                ExtensionMemoryUtil.getMiscSort(maid).hasInFlight(), storageTask)) {
            ci.cancel();
        }
    }
}
