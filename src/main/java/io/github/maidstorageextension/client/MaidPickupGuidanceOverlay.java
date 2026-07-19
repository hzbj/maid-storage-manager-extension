package io.github.maidstorageextension.client;

import io.github.maidstorageextension.MaidStorageManagerExtension;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Minimal HUD compass shown only while the assigned driver is waiting to board. */
@Mod.EventBusSubscriber(modid = MaidStorageManagerExtension.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class MaidPickupGuidanceOverlay {
    private static final String[] ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};

    private MaidPickupGuidanceOverlay() {
    }

    @SubscribeEvent
    public static void render(RenderGuiOverlayEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        var guidance = MaidPickupGuidanceClientData.current();
        if (guidance == null || minecraft.player == null || minecraft.level == null
                || !minecraft.level.dimension().location().equals(guidance.dimension())) return;
        double dx = guidance.position().getX() + 0.5D - minecraft.player.getX();
        double dz = guidance.position().getZ() + 0.5D - minecraft.player.getZ();
        double worldAngle = Math.toDegrees(Math.atan2(-dx, dz));
        double relative = Math.floorMod(
                Math.round(worldAngle - minecraft.player.getYRot()), 360L);
        int index = (int) Math.floor((relative + 22.5D) / 45.0D) & 7;
        int distance = (int) Math.round(Math.sqrt(dx * dx + dz * dz));
        Component text = Component.literal(ARROWS[index] + " " + distance + "m")
                .append(" ").append(Component.translatable(
                        "gui.maid_storage_manager_extension.transport.pickup_guidance"));
        int x = (event.getWindow().getGuiScaledWidth() - minecraft.font.width(text)) / 2;
        event.getGuiGraphics().drawString(
                minecraft.font, text, x, 34, 0xFFF1D47B, true);
    }
}
