package io.github.maidstorageextension.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import studio.fantasyit.maid_storage_manager.items.render.CustomEmptyModel;

@Mixin(value = CustomEmptyModel.class, remap = false)
public abstract class CustomEmptyModelMixin {
    @Inject(method = {"getParticleIcon", "m_6160_"}, at = @At("RETURN"), cancellable = true)
    private void maidStorageExtension$nonNullParticle(CallbackInfoReturnable<TextureAtlasSprite> cir) {
        if (cir.getReturnValue() == null) {
            cir.setReturnValue(Minecraft.getInstance().getModelManager().getMissingModel().getParticleIcon());
        }
    }
}
