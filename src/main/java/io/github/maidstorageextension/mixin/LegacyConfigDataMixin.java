package io.github.maidstorageextension.mixin;

import io.github.maidstorageextension.migration.LegacyConfigMigration;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import studio.fantasyit.maid_storage_manager.maid.data.StorageManagerConfigData;

@Mixin(value = StorageManagerConfigData.class, remap = false)
public abstract class LegacyConfigDataMixin {
    @Inject(method = "readSaveData", at = @At("RETURN"))
    private void maidStorageExtension$captureLegacy(CompoundTag source,
            CallbackInfoReturnable<StorageManagerConfigData.Data> cir) {
        LegacyConfigMigration.capture(cir.getReturnValue(), source);
    }
}
