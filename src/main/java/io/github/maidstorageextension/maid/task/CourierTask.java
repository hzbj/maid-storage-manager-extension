package io.github.maidstorageextension.maid.task;

import com.github.tartaricacid.touhoulittlemaid.api.task.IMaidTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitSounds;
import com.mojang.datafixers.util.Pair;
import io.github.maidstorageextension.MaidStorageManagerExtension;
import io.github.maidstorageextension.compat.EnderPocketCompat;
import io.github.maidstorageextension.maid.behavior.CourierBehavior;
import io.github.maidstorageextension.maid.courier.CourierConfigMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class CourierTask implements IMaidTask {
    public static final ResourceLocation TASK_ID = MaidStorageManagerExtension.id("courier");

    @Override public ResourceLocation getUid() { return TASK_ID; }
    @Override public ItemStack getIcon() { return EnderPocketCompat.icon(); }
    @Override public SoundEvent getAmbientSound(EntityMaid maid) { return InitSounds.MAID_IDLE.get(); }

    @Override
    public List<Pair<Integer, BehaviorControl<? super EntityMaid>>> createBrainTasks(EntityMaid maid) {
        List<Pair<Integer, BehaviorControl<? super EntityMaid>>> tasks = new ArrayList<>();
        tasks.add(Pair.of(5, new CourierBehavior()));
        return tasks;
    }

    @Override public boolean isEnable(EntityMaid maid) { return true; }

    @Override
    public List<Pair<String, java.util.function.Predicate<EntityMaid>>> getEnableConditionDesc(EntityMaid maid) {
        // AbstractMaidContainerGui adds task.<namespace>.<path>.enable_condition. itself.
        return List.of(Pair.of("transport", ignored -> true));
    }

    @Override
    public List<String> getDescription(EntityMaid maid) {
        return List.of("task.maid_storage_manager_extension.courier.description.0",
                "task.maid_storage_manager_extension.courier.description.1");
    }

    @Override
    public MenuProvider getTaskConfigGuiProvider(EntityMaid maid) {
        return new SimpleMenuProvider((int containerId, Inventory inventory, Player player) ->
                new CourierConfigMenu(containerId, inventory, maid.getId()),
                Component.translatable("gui.maid_storage_manager_extension.courier.title", maid.getName()));
    }

    @Override public String getMaidActionSummary() { return "Meet storage maids and owners to deliver cargo, then return"; }
}
