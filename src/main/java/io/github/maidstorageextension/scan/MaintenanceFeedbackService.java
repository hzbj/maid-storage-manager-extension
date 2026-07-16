package io.github.maidstorageextension.scan;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import io.github.maidstorageextension.data.ExtensionConfigData;
import io.github.maidstorageextension.data.MaintenanceStatusData;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

public final class MaintenanceFeedbackService {
    private static final DustParticleOptions SUCCESS_DUST =
            new DustParticleOptions(new Vector3f(0.2F, 1.0F, 0.25F), 1.0F);
    private static final DustParticleOptions FAILURE_DUST =
            new DustParticleOptions(new Vector3f(1.0F, 0.15F, 0.1F), 1.0F);

    private MaintenanceFeedbackService() {
    }

    public static void setPhase(EntityMaid maid, MaintenanceStatusData.Phase phase) {
        MaintenanceStatusData.Data status = MaintenanceStatusData.get(maid);
        if (status.phase() == phase) {
            return;
        }
        status.phase(phase);
        maid.setAndSyncData(MaintenanceStatusData.KEY, status);
    }

    public static void complete(ServerLevel level, EntityMaid maid,
                                InventoryListRefreshService.RefreshResult result,
                                int scannedStorages,
                                @Nullable ItemFrame frame) {
        MaintenanceStatusData.Data status = MaintenanceStatusData.get(maid);
        status.complete(result.outcome().statusResult(), scannedStorages,
                result.publishedItemTypes(), System.currentTimeMillis());
        maid.setAndSyncData(MaintenanceStatusData.KEY, status);

        ExtensionConfigData.Data config = ExtensionConfigData.get(maid);
        if (config.refreshFrameEffects()) {
            playEffects(level, maid, frame, result.success());
        }
        if (config.refreshOwnerNotification() && maid.getOwner() instanceof ServerPlayer owner) {
            if (result.success()) {
                owner.displayClientMessage(Component.translatable(
                        "message.maid_storage_manager_extension.maintenance.success",
                        maid.getName(), scannedStorages, result.publishedItemTypes()), true);
            } else {
                owner.displayClientMessage(Component.translatable(
                        "message.maid_storage_manager_extension.maintenance.failed",
                        maid.getName(), Component.translatable(result.outcome().translationKey())), true);
            }
        }
    }

    private static void playEffects(ServerLevel level, EntityMaid maid,
                                    @Nullable ItemFrame frame, boolean success) {
        Vec3 position = frame == null ? maid.position().add(0.0D, 1.0D, 0.0D) : frame.position();
        level.sendParticles(success ? SUCCESS_DUST : FAILURE_DUST,
                position.x, position.y + 0.25D, position.z,
                success ? 18 : 10,
                0.35D, 0.35D, 0.35D, 0.02D);
        SoundEvent sound = success ? SoundEvents.EXPERIENCE_ORB_PICKUP : SoundEvents.NOTE_BLOCK_BASS.value();
        level.playSound(null,
                frame == null ? maid.blockPosition() : frame.blockPosition(),
                sound,
                SoundSource.NEUTRAL,
                success ? 0.8F : 0.65F,
                success ? 1.35F : 0.75F);
    }
}
