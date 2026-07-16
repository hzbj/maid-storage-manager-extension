package io.github.maidstorageextension.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import studio.fantasyit.maid_storage_manager.render.map_like.ProgressPadRender;

/** Keeps every paper-surface label readable against the light progress-pad background. */
@Mixin(value = ProgressPadRender.class, remap = false)
public abstract class ProgressPadRenderMixin {
    private static final int PURE_BLACK = 0xff000000;

    @ModifyConstant(
            method = "renderOnHand",
            constant = @Constant(intValue = 0xff2b0609),
            require = 1)
    private int maidStorageExtension$makeBrownTextBlack(int original) {
        return PURE_BLACK;
    }

    @ModifyConstant(
            method = "renderOnHand",
            constant = @Constant(intValue = 0xff917200),
            require = 1)
    private int maidStorageExtension$makeGoldTextBlack(int original) {
        return PURE_BLACK;
    }
}
