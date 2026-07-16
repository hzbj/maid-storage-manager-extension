package io.github.maidstorageextension.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.item.EntityBroom;
import io.github.maidstorageextension.maid.courier.CourierBroomFlightService;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Prevents vanilla broom gravity/control from fighting the server-authoritative courier flight. */
@Mixin(EntityBroom.class)
public abstract class EntityBroomMixin {
    @Inject(method = "travel", at = @At("HEAD"), cancellable = true)
    private void maidStorageExtension$freezeCourierBroom(Vec3 movement, CallbackInfo ci) {
        if (CourierBroomFlightService.isCourierBroom((Entity) (Object) this)) ci.cancel();
    }
}
