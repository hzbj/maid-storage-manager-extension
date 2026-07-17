package io.github.maidstorageextension.mixin;

import io.github.maidstorageextension.client.ConfigScreenBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import studio.fantasyit.maid_storage_manager.maid.config.StorageManagerMaidConfigGui;

@Mixin(value = StorageManagerMaidConfigGui.class, remap = false)
public abstract class StorageManagerConfigScreenMixin {
    @Inject(method = "initAdditionWidgets", at = @At("TAIL"))
    private void maidStorageExtension$appendExtensionSettingsRow(CallbackInfo callbackInfo) {
        ConfigScreenBridge.appendExtensionSettingsRow(
                (StorageManagerMaidConfigGui) (Object) this);
    }
}
