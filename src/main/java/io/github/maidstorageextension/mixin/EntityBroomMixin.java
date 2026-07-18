package io.github.maidstorageextension.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.item.EntityBroom;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import io.github.maidstorageextension.maid.courier.CourierBroomFlightService;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

/** Prevents vanilla broom gravity/control from fighting the server-authoritative courier flight. */
@Mixin(EntityBroom.class)
public abstract class EntityBroomMixin {
    @Inject(method = "travel", at = @At("HEAD"), cancellable = true)
    private void maidStorageExtension$freezeCourierBroom(Vec3 movement, CallbackInfo ci) {
        Entity broom = (Entity) (Object) this;
        // Persistent entity data is server-only here. Passenger order is synchronized, so a
        // maid-first broom also suppresses the client's vanilla steering/prediction. Explicit
        // handoff reverses the order (player first) and restores the upstream driving code.
        if (CourierBroomFlightService.isCourierBroom(broom)
                || broom.getFirstPassenger() instanceof EntityMaid) ci.cancel();
    }

    @Inject(method = "interact", at = @At("HEAD"), cancellable = true)
    private void maidStorageExtension$restrictTransportRider(Player player,
                                                             InteractionHand hand,
                                                             CallbackInfoReturnable<InteractionResult> cir) {
        Entity broom = (Entity) (Object) this;
        if (!broom.getPersistentData().hasUUID(CourierBroomFlightService.TAG_TRANSPORT_RIDER)) {
            return;
        }
        UUID intended = broom.getPersistentData().getUUID(
                CourierBroomFlightService.TAG_TRANSPORT_RIDER);
        if (!intended.equals(player.getUUID())) cir.setReturnValue(InteractionResult.FAIL);
    }
}
