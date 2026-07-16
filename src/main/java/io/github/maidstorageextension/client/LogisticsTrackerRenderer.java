package io.github.maidstorageextension.client;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.maidstorageextension.MaidStorageManagerExtension;
import io.github.maidstorageextension.item.LogisticsTrackerItem;
import io.github.maidstorageextension.logistics.LogisticsDisplayName;
import io.github.maidstorageextension.logistics.LogisticsSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import studio.fantasyit.maid_storage_manager.event.RenderHandMapLikeEvent;
import studio.fantasyit.maid_storage_manager.render.base.ICustomGraphics;

import java.util.Locale;
import java.util.UUID;

/** Destination/status dashboard for handheld and item-frame tracker contexts. */
@OnlyIn(Dist.CLIENT)
public final class LogisticsTrackerRenderer implements RenderHandMapLikeEvent.MapLikeRenderer {
    public static final LogisticsTrackerRenderer INSTANCE = new LogisticsTrackerRenderer();
    private static final ResourceLocation BACKGROUND = MaidStorageManagerExtension.id(
            "textures/gui/logistics_tracker_map.png");
    private static final RenderType MAP_BACKGROUND = RenderType.text(BACKGROUND);
    private static final int TEXT = 0xff2a183c;
    private static final int MUTED = 0xff705c7d;
    private static final int ACCENT = 0xff5a226f;
    private static final int GOOD = 0xff168a72;
    private static final int ERROR = 0xffad264f;

    private LogisticsTrackerRenderer() {
    }

    @Override
    public float getWidth(RenderHandMapLikeEvent.MapLikeRenderContext context) {
        return isSmall(context) ? 64.0f : 128.0f;
    }

    @Override
    public float getHeight(RenderHandMapLikeEvent.MapLikeRenderContext context) {
        return isSmall(context) ? 64.0f : 128.0f;
    }

    @Override
    public RenderType backgroundRenderType(Minecraft minecraft, PoseStack pose,
                                           MultiBufferSource buffers, int light, ItemStack stack) {
        return MAP_BACKGROUND;
    }

    @Override
    public void renderOnHand(ICustomGraphics graphics, ItemStack stack, int light,
                             RenderHandMapLikeEvent.MapLikeRenderContext context) {
        UUID courier = LogisticsTrackerItem.getCourier(stack);
        LogisticsSnapshot.Snapshot snapshot = LogisticsTrackerClientData.get(courier);
        Font font = Minecraft.getInstance().font;
        graphics.pose().scale(0.5f, 0.5f, 1.0f);
        if (isSmall(context)) renderCompact(graphics, font, snapshot);
        else renderFull(graphics, font, snapshot);
    }

    private static void renderFull(ICustomGraphics graphics, Font font,
                                   LogisticsSnapshot.Snapshot snapshot) {
        centered(graphics, font,
                Component.translatable("gui.maid_storage_manager_extension.logistics_tracker.title"),
                256, 19, ACCENT);
        if (!snapshot.authorized()) {
            centered(graphics, font, Component.translatable(
                    "gui.maid_storage_manager_extension.logistics_tracker.unauthorized"),
                    256, 112, ERROR);
            return;
        }
        if (snapshot.courierName().isBlank()) {
            centered(graphics, font, Component.translatable(
                    "gui.maid_storage_manager_extension.logistics_tracker.no_data"),
                    256, 112, MUTED);
            return;
        }

        centered(graphics, font, trim(font, LogisticsDisplayName.decode(snapshot.courierName()), 210),
                256, 39, TEXT);
        graphics.drawString(font, mode(snapshot), 18, 54, TEXT);
        graphics.drawString(font, phase(snapshot), 18, 68, TEXT);
        graphics.drawString(font, target(snapshot), 18, 84, ACCENT);
        graphics.drawString(font, distance(snapshot), 18, 100,
                snapshot.distance() < 0 && snapshot.target() != LogisticsSnapshot.TargetKind.NONE
                        ? ERROR : TEXT);

        graphics.drawString(font, Component.translatable(
                "gui.maid_storage_manager_extension.logistics_tracker.stations"), 18, 126, TEXT);
        drawStationGrid(graphics, font, snapshot, 20, 140, 112, 25, 100, 256, 176);

        graphics.drawString(font, Component.translatable(snapshot.online()
                        ? "gui.maid_storage_manager_extension.logistics_tracker.online"
                        : "gui.maid_storage_manager_extension.logistics_tracker.offline"),
                18, 232, snapshot.online() ? GOOD : ERROR);
    }

    /** 128 logical pixels become exactly 64 item-frame pixels after the shared 0.5 scale. */
    private static void renderCompact(ICustomGraphics graphics, Font font,
                                      LogisticsSnapshot.Snapshot snapshot) {
        centered(graphics, font, Component.translatable(
                "gui.maid_storage_manager_extension.logistics_tracker.compact_title"),
                128, 10, ACCENT);
        if (!snapshot.authorized() || snapshot.courierName().isBlank()) {
            centered(graphics, font, Component.translatable(snapshot.authorized()
                            ? "gui.maid_storage_manager_extension.logistics_tracker.no_data"
                            : "gui.maid_storage_manager_extension.logistics_tracker.unauthorized"),
                    128, 52, snapshot.authorized() ? MUTED : ERROR);
            return;
        }
        centered(graphics, font, trim(font, LogisticsDisplayName.decode(snapshot.courierName()), 112),
                128, 19, TEXT);
        graphics.drawString(font, Component.literal(trim(font, mode(snapshot).getString(), 112)),
                8, 30, TEXT);
        graphics.drawString(font, Component.literal(trim(font, phase(snapshot).getString(), 112)),
                8, 40, TEXT);
        graphics.drawString(font, Component.literal(trim(font, target(snapshot).getString(), 112)),
                8, 50, ACCENT);
        graphics.drawString(font, Component.literal(trim(font, distance(snapshot).getString(), 112)),
                8, 59, TEXT);
        drawStationGrid(graphics, font, snapshot, 8, 69, 60, 15, 52, 128, 85);
        graphics.drawString(font, Component.translatable(snapshot.online()
                        ? "gui.maid_storage_manager_extension.logistics_tracker.online_short"
                        : "gui.maid_storage_manager_extension.logistics_tracker.offline_short"),
                8, 111, snapshot.online() ? GOOD : ERROR);
    }

    private static void drawStationGrid(ICustomGraphics graphics, Font font,
                                        LogisticsSnapshot.Snapshot snapshot,
                                        int startX, int startY, int columnGap,
                                        int rowGap, int maxWidth,
                                        int canvas, int emptyLabelY) {
        int shown = Math.min(6, snapshot.stations().size());
        for (int i = 0; i < shown; i++) {
            LogisticsSnapshot.Station station = snapshot.stations().get(i);
            int x = startX + (i % 2) * columnGap;
            int y = startY + (i / 2) * rowGap;
            String marker = station.selected()
                    ? Component.translatable(
                    "gui.maid_storage_manager_extension.logistics_tracker.default_short").getString()
                    : Integer.toString(i + 1);
            int color = station.valid() ? GOOD : ERROR;
            String name = trim(font, LogisticsDisplayName.decode(station.name()).getString(),
                    Math.max(8, maxWidth - font.width(marker) - 5));
            graphics.drawString(font, marker, x, y, color);
            graphics.drawString(font, name, x + font.width(marker) + 5, y, TEXT);
        }
        if (shown == 0) centered(graphics, font, Component.translatable(
                "gui.maid_storage_manager_extension.logistics_tracker.no_stations"),
                canvas, emptyLabelY, MUTED);
    }

    private static Component mode(LogisticsSnapshot.Snapshot snapshot) {
        return Component.translatable("gui.maid_storage_manager_extension.logistics_tracker.mode",
                Component.translatable("gui.maid_storage_manager_extension.logistics_tracker.mode."
                        + snapshot.transportMode().name().toLowerCase(Locale.ROOT)));
    }

    private static Component phase(LogisticsSnapshot.Snapshot snapshot) {
        return Component.translatable("gui.maid_storage_manager_extension.logistics_tracker.status",
                Component.translatable("gui.maid_storage_manager_extension.courier.phase."
                        + snapshot.phase().toLowerCase(Locale.ROOT)));
    }

    private static Component target(LogisticsSnapshot.Snapshot snapshot) {
        Component kind = Component.translatable(
                "gui.maid_storage_manager_extension.logistics_tracker.target."
                        + snapshot.target().name().toLowerCase(Locale.ROOT));
        if (!snapshot.targetName().isBlank()) {
            kind = Component.translatable(
                    "gui.maid_storage_manager_extension.logistics_tracker.target_named",
                    kind, LogisticsDisplayName.decode(snapshot.targetName()));
        }
        return Component.translatable("gui.maid_storage_manager_extension.logistics_tracker.target", kind);
    }

    private static Component distance(LogisticsSnapshot.Snapshot snapshot) {
        if (snapshot.target() == LogisticsSnapshot.TargetKind.NONE) {
            return Component.translatable(
                    "gui.maid_storage_manager_extension.logistics_tracker.distance_none");
        }
        if (snapshot.distance() < 0) {
            return Component.translatable(
                    "gui.maid_storage_manager_extension.logistics_tracker.distance_unavailable");
        }
        return Component.translatable(
                "gui.maid_storage_manager_extension.logistics_tracker.distance", snapshot.distance());
    }

    private static Component trim(Font font, Component value, int maxWidth) {
        if (font.width(value) <= maxWidth) return value;
        return Component.literal(trim(font, value.getString(), maxWidth));
    }

    private static String trim(Font font, String value, int maxWidth) {
        if (font.width(value) <= maxWidth) return value;
        String suffix = "…";
        int end = value.length();
        while (end > 0 && font.width(value.substring(0, end) + suffix) > maxWidth) end--;
        return value.substring(0, end) + suffix;
    }

    private static void centered(ICustomGraphics graphics, Font font, Component text,
                                 int canvas, int y, int color) {
        graphics.drawString(font, text, (canvas - font.width(text)) / 2, y, color);
    }

    private static boolean isSmall(RenderHandMapLikeEvent.MapLikeRenderContext context) {
        return context == RenderHandMapLikeEvent.MapLikeRenderContext.ITEM_FRAME_SMALL
                || context == RenderHandMapLikeEvent.MapLikeRenderContext.ITEM_FRAME_SIDE;
    }
}
