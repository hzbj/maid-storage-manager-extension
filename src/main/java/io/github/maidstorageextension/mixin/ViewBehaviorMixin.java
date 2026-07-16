package io.github.maidstorageextension.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import io.github.maidstorageextension.scan.StorageScanService;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import studio.fantasyit.maid_storage_manager.maid.behavior.view.ViewBehavior;
import studio.fantasyit.maid_storage_manager.storage.Target;
import studio.fantasyit.maid_storage_manager.storage.base.IStorageContext;

/** Records patrol success only after the official context has finished writing the latest cache. */
@Mixin(value = ViewBehavior.class, remap = false)
public abstract class ViewBehaviorMixin {
    @Shadow private IStorageContext context;
    @Shadow Target target;

    @Inject(method = "tick", at = @At("TAIL"), require = 1)
    private void maidStorageExtension$recordCompletedInspection(
            ServerLevel level, EntityMaid maid, long gameTime, CallbackInfo ci) {
        if (context != null && context.isDone()) {
            StorageScanService.recordSuccessfulInspection(level, maid, target);
        }
    }

    @Inject(method = "stop", at = @At("HEAD"), require = 1)
    private void maidStorageExtension$recordAlreadyCompletedInspectionBeforeStop(
            ServerLevel level, EntityMaid maid, long gameTime, CallbackInfo ci) {
        if (context != null && context.isDone()) {
            StorageScanService.recordSuccessfulInspection(level, maid, target);
        }
    }
}
