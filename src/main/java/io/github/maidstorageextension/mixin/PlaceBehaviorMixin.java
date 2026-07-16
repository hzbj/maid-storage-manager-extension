package io.github.maidstorageextension.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import io.github.maidstorageextension.compat.MiscStorageAccess;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import studio.fantasyit.maid_storage_manager.maid.behavior.place.PlaceBehavior;
import studio.fantasyit.maid_storage_manager.maid.data.StorageManagerConfigData;
import studio.fantasyit.maid_storage_manager.storage.Target;
import studio.fantasyit.maid_storage_manager.storage.base.ISlotBasedStorage;

@Mixin(value = PlaceBehavior.class, remap = false)
public abstract class PlaceBehaviorMixin {
    @Shadow Target target;

    @Inject(method = "exceedSlotLimit", at = @At("HEAD"), cancellable = true)
    private void maidStorageExtension$miscHasNoTypeLimit(ISlotBasedStorage storage, ItemStack item,
                                                          EntityMaid maid,
                                                          CallbackInfoReturnable<Boolean> cir) {
        if (target != null && StorageManagerConfigData.get(maid).itemTypeLimit() == 0
                && MiscStorageAccess.isMiscStorage(maid.level(), target)) {
            cir.setReturnValue(false);
        }
    }
}
