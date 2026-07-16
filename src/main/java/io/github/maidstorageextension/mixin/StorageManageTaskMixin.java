package io.github.maidstorageextension.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.mojang.datafixers.util.Pair;
import io.github.maidstorageextension.maid.behavior.TaskBellBehavior;
import io.github.maidstorageextension.maid.behavior.MiscSortBehavior;
import io.github.maidstorageextension.maid.behavior.view.RefreshInventoryListBehavior;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import studio.fantasyit.maid_storage_manager.maid.task.StorageManageTask;

import java.util.List;

@Mixin(value = StorageManageTask.class, remap = false)
public abstract class StorageManageTaskMixin {
    @Inject(method = "createBrainTasks", at = @At("RETURN"))
    private void maidStorageExtension$appendBehaviors(EntityMaid maid,
            CallbackInfoReturnable<List<Pair<Integer, BehaviorControl<? super EntityMaid>>>> cir) {
        List<Pair<Integer, BehaviorControl<? super EntityMaid>>> tasks = cir.getReturnValue();
        tasks.add(Pair.of(11, new TaskBellBehavior()));
        tasks.add(Pair.of(11, new MiscSortBehavior()));
        tasks.add(Pair.of(11, new RefreshInventoryListBehavior()));
    }
}
