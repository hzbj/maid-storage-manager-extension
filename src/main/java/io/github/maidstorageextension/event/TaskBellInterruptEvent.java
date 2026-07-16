package io.github.maidstorageextension.event;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import io.github.maidstorageextension.MaidStorageManagerExtension;
import io.github.maidstorageextension.maid.ExtensionMemoryUtil;
import io.github.maidstorageextension.maid.TaskBellCallController;
import net.minecraft.server.level.ServerLevel;

@Mod.EventBusSubscriber(modid = MaidStorageManagerExtension.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class TaskBellInterruptEvent {
    @SubscribeEvent
    public static void onMaidHurt(LivingHurtEvent event) {
        if (event.getEntity().level() instanceof ServerLevel level
                && event.getEntity() instanceof EntityMaid maid
                && ExtensionMemoryUtil.getTaskBellCall(maid) != null) {
            TaskBellCallController.fail(level, maid, "hurt");
        }
    }
}
