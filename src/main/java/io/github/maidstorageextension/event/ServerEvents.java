package io.github.maidstorageextension.event;

import io.github.maidstorageextension.MaidStorageManagerExtension;
import io.github.maidstorageextension.debug.ReachabilityDebugManager;
import io.github.maidstorageextension.scan.StorageScanService;
import io.github.maidstorageextension.scan.MiscSortRecoveryService;
import io.github.maidstorageextension.terminal.MaidTransportBoardingService;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import io.github.maidstorageextension.maid.courier.CourierService;
import io.github.maidstorageextension.maid.task.CourierTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;

@Mod.EventBusSubscriber(modid = MaidStorageManagerExtension.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ServerEvents {
    private ServerEvents() {
    }

    @SubscribeEvent
    public static void serverTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            ReachabilityDebugManager.tick(event.getServer());
        }
    }

    @SubscribeEvent
    public static void entityUnloaded(EntityLeaveLevelEvent event) {
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof EntityMaid maid) {
            MiscSortRecoveryService.release(maid);
            StorageScanService.releaseTransientSession(maid);
        }
    }

    /** The waiting maid overlaps the broom hitbox, so either entity must board the same ride. */
    @SubscribeEvent
    public static void interactTransport(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        MaidTransportBoardingService.Result result =
                MaidTransportBoardingService.tryBoard(player, event.getTarget());
        if (result == MaidTransportBoardingService.Result.NOT_TRANSPORT) return;
        event.setCancellationResult(result == MaidTransportBoardingService.Result.BOARDED
                ? InteractionResult.SUCCESS : InteractionResult.FAIL);
        event.setCanceled(true);
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
