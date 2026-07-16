package io.github.maidstorageextension.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import io.github.maidstorageextension.compat.MiscStorageAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import studio.fantasyit.maid_storage_manager.Config;
import studio.fantasyit.maid_storage_manager.maid.behavior.place.PlaceMoveBehavior;
import studio.fantasyit.maid_storage_manager.maid.data.StorageManagerConfigData;
import studio.fantasyit.maid_storage_manager.maid.memory.ViewedInventoryMemory;
import studio.fantasyit.maid_storage_manager.storage.MaidStorage;
import studio.fantasyit.maid_storage_manager.storage.Target;
import studio.fantasyit.maid_storage_manager.storage.base.IMaidStorage;
import studio.fantasyit.maid_storage_manager.util.Conditions;
import studio.fantasyit.maid_storage_manager.util.MemoryUtil;
import studio.fantasyit.maid_storage_manager.util.MoveUtil;
import studio.fantasyit.maid_storage_manager.util.RequestItemUtil;
import studio.fantasyit.maid_storage_manager.util.StorageAccessUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Mixin(value = PlaceMoveBehavior.class, remap = false)
public abstract class PlaceMoveBehaviorMixin {
    @Shadow private Target chestPos;
    @Shadow private ArrayList<ItemStack> maidAvailableItems;

    @Redirect(
            method = "priorityTarget",
            at = @At(
                    value = "INVOKE",
                    target = "Lstudio/fantasyit/maid_storage_manager/maid/memory/ViewedInventoryMemory;positionFlatten()Ljava/util/Map;"
            ),
            require = 1
    )
    private Map<Target, List<ViewedInventoryMemory.ItemCount>> maidStorageExtension$excludeMiscFromPriorityCandidates(
            ViewedInventoryMemory memory, ServerLevel level, EntityMaid maid) {
        Map<Target, List<ViewedInventoryMemory.ItemCount>> candidates = memory.positionFlatten();
        candidates.entrySet().removeIf(entry -> MiscStorageAccess.isMiscStorage(level, entry.getKey()));
        return candidates;
    }

    @Inject(method = "priorityTarget", at = @At("RETURN"), cancellable = true)
    private void maidStorageExtension$miscFallback(ServerLevel level, EntityMaid maid,
                                                    CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue() || Conditions.noSortPlacement(maid)
                || StorageManagerConfigData.get(maid).itemTypeLimit() != 0) {
            return;
        }
        for (Target cached : MemoryUtil.getViewedInventory(maid).positionFlatten().keySet()) {
            Target target = MaidStorage.getInstance().isValidTarget(level, maid, cached.getPos(), cached.side);
            if (target == null || RequestItemUtil.isRequestTarget(level, maid, target)
                    || MemoryUtil.getPlacingInv(maid).isVisitedPos(target)
                    || StorageAccessUtil.findTargetRewrite(level, maid, target, false).isEmpty()
                    || !MiscStorageAccess.isMiscStorage(level, target)) {
                continue;
            }
            IMaidStorage storage = MaidStorage.getInstance().getStorage(target.getType());
            if (storage == null || !storage.supportPlace()) {
                continue;
            }
            BlockPos stand = MoveUtil.selectPosForTarget(level, maid, target.getPos());
            if (stand == null) {
                continue;
            }
            MemoryUtil.getPlacingInv(maid).setArrangeItems(maidAvailableItems);
            chestPos = target;
            MemoryUtil.setTarget(maid, stand, (float) Config.placeSpeed);
            cir.setReturnValue(true);
            return;
        }
    }
}
