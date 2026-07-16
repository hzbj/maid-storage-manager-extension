package io.github.maidstorageextension.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidFollowOwnerTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import io.github.maidstorageextension.data.CourierData;
import io.github.maidstorageextension.maid.courier.CourierRuntimePolicy;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Lets the courier own WALK_TARGET without enabling TLM's competing Home restriction. */
@Mixin(value = MaidFollowOwnerTask.class, remap = false)
public abstract class MaidFollowOwnerTaskMixin {
    @Inject(method = "checkExtraStartConditions", at = @At("HEAD"), cancellable = true)
    private void maidStorageExtension$suspendCourierFollow(
            ServerLevel level, EntityMaid maid, CallbackInfoReturnable<Boolean> cir) {
        CourierData.Data data = CourierData.get(maid);
        if (CourierRuntimePolicy.shouldDisableHomeRestriction(
                data.followOverrideActive(), data.transportMode())) {
            cir.setReturnValue(false);
        }
    }
}
