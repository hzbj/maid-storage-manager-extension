package io.github.maidstorageextension.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import io.github.maidstorageextension.compat.RequestListSafety;
import io.github.maidstorageextension.maid.courier.CourierService;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import studio.fantasyit.maid_storage_manager.registry.ItemRegistry;
import studio.fantasyit.maid_storage_manager.storage.base.IStorageContext;
import studio.fantasyit.maid_storage_manager.util.RequestItemUtil;

@Mixin(value = RequestItemUtil.class, remap = false)
public abstract class RequestItemUtilMixin {
    @Inject(method = "stopJobAndStoreOrThrowItem", at = @At("HEAD"), cancellable = true)
    private static void maidStorageExtension$returnCourierRequestWithoutDropping(
            EntityMaid warehouse, IStorageContext context, Entity target,
            CallbackInfo ci) {
        ItemStack requestList = warehouse.getMainHandItem();
        if (requestList.is(ItemRegistry.REQUEST_LIST_ITEM.get())) {
            RequestListSafety.ensureJobUuid(requestList.getOrCreateTag());
        }
        if (CourierService.finishRequest(warehouse, requestList)) {
            ci.cancel();
        }
    }
}
