package io.github.maidstorageextension.event;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import io.github.maidstorageextension.MaidStorageManagerExtension;
import io.github.maidstorageextension.maid.ExtensionMemoryUtil;
import io.github.maidstorageextension.maid.TaskBellCallController;
import net.minecraft.server.level.ServerLevel;

@EventBusSubscriber(modid = MaidStorageManagerExtension.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public class TaskBellInterruptEvent {
    @SubscribeEvent
    public static void onMaidHurt(LivingIncomingDamageEvent event) {
        if (event.getEntity().level() instanceof ServerLevel level
                && event.getEntity() instanceof EntityMaid maid
                && ExtensionMemoryUtil.getTaskBellCall(maid) != null) {
            TaskBellCallController.fail(level, maid, "hurt");
        }
    }
}
