package io.github.maidstorageextension.maid.task;

import com.github.tartaricacid.touhoulittlemaid.api.task.IMaidTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitSounds;
import com.mojang.datafixers.util.Pair;
import io.github.maidstorageextension.MaidStorageManagerExtension;
import io.github.maidstorageextension.compat.EnderPocketCompat;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** Distinct persistent profession for passenger transport. */
public final class DriverTask implements IMaidTask {
    public static final ResourceLocation TASK_ID = MaidStorageManagerExtension.id("driver");

    @Override public ResourceLocation getUid() { return TASK_ID; }
    @Override public ItemStack getIcon() { return EnderPocketCompat.icon(); }
    @Override public SoundEvent getAmbientSound(EntityMaid maid) { return InitSounds.MAID_IDLE.get(); }
    @Override public List<Pair<Integer, BehaviorControl<? super EntityMaid>>> createBrainTasks(EntityMaid maid) {
        return List.of();
    }
    @Override public boolean isEnable(EntityMaid maid) { return true; }
    @Override public List<Pair<String, java.util.function.Predicate<EntityMaid>>> getEnableConditionDesc(EntityMaid maid) {
        return List.of(Pair.of("transport", ignored -> true));
    }
    @Override public List<String> getDescription(EntityMaid maid) {
        return List.of("task.maid_storage_manager_extension.driver.description.0",
                "task.maid_storage_manager_extension.driver.description.1");
    }
    @Override public String getMaidActionSummary() {
        return "Drive the owner by broom and wait as a driver";
    }
}
