package io.github.maidstorageextension.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.item.EntityBroom;
import io.github.maidstorageextension.compat.touhoulittlemaid.BroomAutopilotAccess;
import io.github.maidstorageextension.maid.courier.CourierBroomFlightService;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

/** Prevents vanilla broom gravity/control from fighting the server-authoritative courier flight. */
@Mixin(EntityBroom.class)
public abstract class EntityBroomMixin implements BroomAutopilotAccess {
    @Unique
    private static final EntityDataAccessor<Boolean> AUTOPILOT =
            SynchedEntityData.defineId(EntityBroom.class, EntityDataSerializers.BOOLEAN);
    @Unique
    private static final String AUTOPILOT_NBT = "MaidStorageExtensionAutopilot";

    @Inject(method = "defineSynchedData", at = @At("TAIL"))
    private void maidStorageExtension$defineAutopilot(CallbackInfo ci) {
        ((Entity) (Object) this).getEntityData().define(AUTOPILOT, false);
    }

    @Override
    public boolean maidStorageExtension$isAutopilot() {
        Entity broom = (Entity) (Object) this;
        return broom.getEntityData().get(AUTOPILOT)
                || !broom.level().isClientSide
                && CourierBroomFlightService.isCourierBroom(broom);
    }

    @Override
    public void maidStorageExtension$setAutopilot(boolean autopilot) {
        ((Entity) (Object) this).getEntityData().set(AUTOPILOT, autopilot);
    }

    @Inject(method = "getControllingPassenger", at = @At("HEAD"), cancellable = true)
    private void maidStorageExtension$revokePlayerControl(
            CallbackInfoReturnable<LivingEntity> cir) {
        // Minecraft promotes a newly mounted player ahead of non-player passengers. Returning
        // null here also makes the server reject that player's vehicle-movement packets.
        if (maidStorageExtension$isAutopilot()) cir.setReturnValue(null);
    }

    @Inject(method = "travel", at = @At("HEAD"), cancellable = true)
    private void maidStorageExtension$freezeCourierBroom(Vec3 movement, CallbackInfo ci) {
        if (maidStorageExtension$isAutopilot()) ci.cancel();
    }

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void maidStorageExtension$saveAutopilot(CompoundTag tag, CallbackInfo ci) {
        if (maidStorageExtension$isAutopilot()) tag.putBoolean(AUTOPILOT_NBT, true);
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void maidStorageExtension$loadAutopilot(CompoundTag tag, CallbackInfo ci) {
        maidStorageExtension$setAutopilot(tag.getBoolean(AUTOPILOT_NBT));
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
