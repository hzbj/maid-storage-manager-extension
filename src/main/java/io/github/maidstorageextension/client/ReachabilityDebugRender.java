package io.github.maidstorageextension.client;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import io.github.maidstorageextension.MaidStorageManagerExtension;
import studio.fantasyit.maid_storage_manager.storage.Target;
import studio.fantasyit.maid_storage_manager.util.BoxRenderUtil;

import java.util.HashMap;
import java.util.Map;

@Mod.EventBusSubscriber(modid = MaidStorageManagerExtension.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ReachabilityDebugRender {
    private static final float[] GREEN = {0.1f, 1.0f, 0.2f, 1.0f};
    private static final float[] YELLOW = {1.0f, 0.85f, 0.1f, 1.0f};
    private static final float[] RED = {1.0f, 0.15f, 0.15f, 1.0f};

    private ReachabilityDebugRender() {
    }

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES) {
            return;
        }
        Map<BlockPos, Integer> floating = new HashMap<>();
        for (var entry : ReachabilityDebugClientData.getVisibleEntries()) {
            float[] color = switch (entry.status()) {
                case REACHABLE -> GREEN;
                case UNREACHABLE -> YELLOW;
                case DENIED -> RED;
            };
            String label = Component.translatable("debug.maid_storage_manager_extension.reachable."
                    + entry.status().name().toLowerCase()).getString();
            BoxRenderUtil.renderStorage(Target.virtual(entry.pos(), null), color, event, label, floating);
        }
    }
}
