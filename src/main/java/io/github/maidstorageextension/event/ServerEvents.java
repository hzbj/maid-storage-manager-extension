package io.github.maidstorageextension.event;

import io.github.maidstorageextension.MaidStorageManagerExtension;
import io.github.maidstorageextension.debug.ReachabilityDebugManager;
import io.github.maidstorageextension.scan.StorageScanService;
import io.github.maidstorageextension.scan.MiscSortRecoveryService;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import io.github.maidstorageextension.item.LogisticsTrackerItem;
import io.github.maidstorageextension.logistics.LogisticsTrackerService;
import io.github.maidstorageextension.maid.courier.CourierService;
import io.github.maidstorageextension.maid.task.CourierTask;
import io.github.maidstorageextension.registry.ExtensionItems;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

@Mod.EventBusSubscriber(modid = MaidStorageManagerExtension.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ServerEvents {
    private ServerEvents() {
    }

    @SubscribeEvent
    public static void serverTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            ReachabilityDebugManager.tick(event.getServer());
            if (event.getServer().getTickCount() % 20 == 0) {
                refreshMountedLogisticsTrackers(event);
            }
        }
    }

    private static void refreshMountedLogisticsTrackers(TickEvent.ServerTickEvent event) {
        for (ServerLevel level : event.getServer().getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (!(entity instanceof ItemFrame frame)
                        || !frame.getItem().is(ExtensionItems.LOGISTICS_TRACKER.get())) continue;
                ItemStack tracker = frame.getItem();
                UUID courier = LogisticsTrackerItem.getCourier(tracker);
                if (courier == null) continue;
                LogisticsTrackerService.updateMounted(frame, event.getServer(), courier,
                        LogisticsTrackerItem.getBoundOwner(tracker));
            }
        }
    }

    @SubscribeEvent
    public static void entityUnloaded(EntityLeaveLevelEvent event) {
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof EntityMaid maid) {
            MiscSortRecoveryService.release(maid);
            StorageScanService.releaseTransientSession(maid);
        }
    }

    /** Keeps an in-flight journal safe when the selected task changes mid-transfer. */
    @SubscribeEvent
    public static void maidTick(LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof EntityMaid maid)
                || !(maid.level() instanceof ServerLevel level)) {
            return;
        }
        MiscSortRecoveryService.tick(level, maid);
        CourierService.tickBroomFlight(level, maid);
        if (maid.tickCount % 10 != 0 || !CourierService.hasActiveTransaction(maid)) return;
        if (!maid.getTask().getUid().equals(CourierTask.TASK_ID)) {
            CourierService.tick(level, maid);
        }
    }
}
