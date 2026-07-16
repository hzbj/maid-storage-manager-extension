package io.github.maidstorageextension.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import io.github.maidstorageextension.maid.ExtensionMemoryUtil;
import io.github.maidstorageextension.maid.ExtensionWorkControl;
import io.github.maidstorageextension.maid.TaskBellCallController;
import io.github.maidstorageextension.migration.LegacyConfigMigration;
import io.github.maidstorageextension.scan.StorageScanService;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import studio.fantasyit.maid_storage_manager.maid.behavior.ScheduleBehavior;
import studio.fantasyit.maid_storage_manager.util.MemoryUtil;

@Mixin(value = ScheduleBehavior.class, remap = false)
public abstract class ScheduleBehaviorMixin {
    @Inject(method = "start", at = @At("HEAD"), cancellable = true)
    private void maidStorageExtension$tick(ServerLevel level, EntityMaid maid, long gameTime, CallbackInfo ci) {
        LegacyConfigMigration.migrateIfNeeded(maid);
        if (ExtensionMemoryUtil.getTaskBellCall(maid) != null) {
            if (maid.getTarget() != null) {
                TaskBellCallController.fail(level, maid, "combat");
                return;
            }
            if (ExtensionWorkControl.hasNonInterruptibleWork(maid)) {
                TaskBellCallController.fail(level, maid, "busy");
                return;
            }
            StorageScanService.tickPeriodic(level, maid, false);
            ExtensionWorkControl.setBaseSchedule(maid, ScheduleBehavior.Schedule.NO_SCHEDULE);
            ci.cancel();
            return;
        }
        StorageScanService.tickPeriodic(level, maid, ExtensionWorkControl.mayUsePeriodicIdleTime(maid));
        if (ExtensionWorkControl.shouldHoldExclusiveAction(maid)) {
            // The upstream scheduler sees protected misc cargo in the maid inventory and would
            // select ordinary PLACE, which clears the walk target before this mixin's TAIL.
            // Cancel at HEAD so one cleanup operation owns one uninterrupted physical route.
            ExtensionWorkControl.setBaseSchedule(maid, ScheduleBehavior.Schedule.NO_SCHEDULE);
            ci.cancel();
        }
    }

    @Inject(method = "start", at = @At("TAIL"))
    private void maidStorageExtension$prioritize(ServerLevel level, EntityMaid maid, long gameTime, CallbackInfo ci) {
        if (StorageScanService.hasManualChangedTarget(maid)
                && !ExtensionWorkControl.hasNonInterruptibleWork(maid)) {
            ScheduleBehavior.Schedule current = MemoryUtil.getCurrentlyWorking(maid);
            if (current == ScheduleBehavior.Schedule.PLACE || current == ScheduleBehavior.Schedule.NO_SCHEDULE) {
                ExtensionWorkControl.setBaseSchedule(maid, ScheduleBehavior.Schedule.VIEW);
            }
        }
    }
}
