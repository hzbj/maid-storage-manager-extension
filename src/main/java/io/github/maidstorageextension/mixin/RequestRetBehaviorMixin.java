package io.github.maidstorageextension.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import io.github.maidstorageextension.maid.courier.CourierService;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import studio.fantasyit.maid_storage_manager.entity.VirtualItemEntity;
import studio.fantasyit.maid_storage_manager.maid.behavior.request.ret.RequestRetBehavior;
import studio.fantasyit.maid_storage_manager.util.InvUtil;

/** Prevents MSM's 400-tick virtual delivery from degrading into a dropped ground item. */
@Mixin(value = RequestRetBehavior.class, remap = false)
public abstract class RequestRetBehaviorMixin {
    @Shadow private Entity targetEntity;

    @Redirect(
            method = "tickTargetEntity",
            at = @At(
                    value = "INVOKE",
                    target = "Lstudio/fantasyit/maid_storage_manager/util/InvUtil;throwItemVirtual(Lcom/github/tartaricacid/touhoulittlemaid/entity/passive/EntityMaid;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/phys/Vec3;)Lstudio/fantasyit/maid_storage_manager/entity/VirtualItemEntity;"
            ),
            require = 1
    )
    private VirtualItemEntity maidStorageExtension$persistCourierOverflow(
            EntityMaid warehouse, ItemStack payload, Vec3 velocity) {
        if (targetEntity instanceof EntityMaid courier
                && CourierService.tryRecoverRequestHandoff(warehouse, courier, payload)) {
            VirtualItemEntity completed = VirtualItemEntity.create(
                    warehouse.level(), warehouse.position(), ItemStack.EMPTY);
            completed.discard();
            return completed;
        }
        return InvUtil.throwItemVirtual(warehouse, payload, velocity);
    }
}
