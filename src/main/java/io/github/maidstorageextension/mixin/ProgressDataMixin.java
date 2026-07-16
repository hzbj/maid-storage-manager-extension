package io.github.maidstorageextension.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import io.github.maidstorageextension.maid.ExtensionMemoryUtil;
import io.github.maidstorageextension.progress.MaidOperationalStatus;
import io.github.maidstorageextension.progress.ProgressPadStatusService;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import studio.fantasyit.maid_storage_manager.craft.work.ProgressData;
import studio.fantasyit.maid_storage_manager.items.ProgressPad;

@Mixin(value = ProgressData.class, remap = false)
public abstract class ProgressDataMixin {
    @Inject(method = "fromMaidAuto", at = @At("RETURN"), cancellable = true, require = 1)
    private static void maidStorageExtension$publishOperationalStatusInHeader(
            EntityMaid maid, ServerLevel level, ProgressPad.Viewing viewing,
            ProgressPad.Merge merge, int maxSz,
            CallbackInfoReturnable<ProgressData> cir) {
        cir.setReturnValue(ProgressPadStatusService.applyHeader(
                cir.getReturnValue(), MaidOperationalStatus.capture(maid),
                ExtensionMemoryUtil.getMiscSort(maid)));
    }
}
