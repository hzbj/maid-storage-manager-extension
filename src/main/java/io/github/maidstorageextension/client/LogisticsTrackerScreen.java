package io.github.maidstorageextension.client;

import io.github.maidstorageextension.MaidStorageManagerExtension;
import io.github.maidstorageextension.logistics.LogisticsDisplayName;
import io.github.maidstorageextension.logistics.LogisticsSnapshot;
import io.github.maidstorageextension.logistics.LogisticsTrackerMenu;
import io.github.maidstorageextension.network.CourierCommandPacket;
import io.github.maidstorageextension.network.ExtensionNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import java.util.Locale;

public final class LogisticsTrackerScreen extends AbstractContainerScreen<LogisticsTrackerMenu> {
    private static final ResourceLocation BACKGROUND = MaidStorageManagerExtension.id(
            "textures/gui/logistics_tracker_screen.png");
    private static final int TEXT = 0x3A2417;
    private static final int MUTED = 0x74543C;
    private static final int ACCENT = 0x6A367D;
    private static final int GOOD = 0x167C74;
    private static final int ERROR = 0xA62846;
    private Button recallButton;
    private Button locateButton;
    private Button clearWorkButton;

    public LogisticsTrackerScreen(LogisticsTrackerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 256;
        imageHeight = 240;
    }

    @Override
    protected void init() {
        super.init();
        recallButton = addRenderableWidget(Button.builder(Component.translatable(
                        "gui.maid_storage_manager_extension.logistics_tracker.recall"), button ->
                        ExtensionNetwork.CHANNEL.sendToServer(
                                CourierCommandPacket.recall(menu.courier())))
                .bounds(leftPos + 17, topPos + 215, 72, 20).build());
        locateButton = addRenderableWidget(Button.builder(Component.translatable(
                        "gui.maid_storage_manager_extension.logistics_tracker.locate"), button ->
                        ExtensionNetwork.CHANNEL.sendToServer(
                                CourierCommandPacket.locate(menu.courier())))
                .bounds(leftPos + 92, topPos + 215, 72, 20).build());
        clearWorkButton = addRenderableWidget(Button.builder(Component.translatable(
                        "gui.maid_storage_manager_extension.logistics_tracker.clear_work"), button ->
                        ExtensionNetwork.CHANNEL.sendToServer(
                                CourierCommandPacket.clearWork(menu.courier())))
                .bounds(leftPos + 167, topPos + 215, 72, 20).build());
        clearWorkButton.setTooltip(Tooltip.create(Component.translatable(
                "gui.maid_storage_manager_extension.logistics_tracker.clear_work_hint")));
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(BACKGROUND, leftPos, topPos, 0, 0,
                imageWidth, imageHeight, imageWidth, imageHeight);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        LogisticsSnapshot.Snapshot snapshot = LogisticsTrackerClientData.get(menu.courier());
        graphics.drawCenteredString(font, title, imageWidth / 2, 8, ACCENT);
        Component courierName = snapshot.courierName().isBlank()
                ? Component.translatable("gui.maid_storage_manager_extension.logistics_tracker.no_data")
                : LogisticsDisplayName.decode(snapshot.courierName());
        graphics.drawCenteredString(font, trim(courierName, 210), imageWidth / 2, 26, TEXT);
        graphics.drawString(font, trim(mode(snapshot), 222), 17, 43, TEXT, false);
        graphics.drawString(font, trim(phase(snapshot), 222), 17, 56, TEXT, false);
        graphics.drawString(font, trim(target(snapshot), 222), 17, 69, ACCENT, false);
        graphics.drawString(font, trim(distance(snapshot), 222), 17, 82,
                snapshot.distance() < 0 && snapshot.target() != LogisticsSnapshot.TargetKind.NONE
                        ? ERROR : TEXT, false);
        graphics.drawString(font, Component.translatable(
                "gui.maid_storage_manager_extension.logistics_tracker.stations"),
                17, 101, TEXT, false);

        for (int i = 0; i < snapshot.stations().size() && i < 6; i++) {
            LogisticsSnapshot.Station station = snapshot.stations().get(i);
            int x = 18 + (i % 2) * 116;
            int y = 120 + (i / 2) * 27;
            Component name = LogisticsDisplayName.decode(station.name());
            Component row = Component.translatable(station.selected()
                            ? "gui.maid_storage_manager_extension.logistics_tracker.station_default"
                            : "gui.maid_storage_manager_extension.logistics_tracker.station",
                    name);
            graphics.drawString(font, trim(row, 104), x, y,
                    station.valid() ? GOOD : ERROR, false);
        }
        if (snapshot.stations().isEmpty()) {
            graphics.drawCenteredString(font, Component.translatable(
                    "gui.maid_storage_manager_extension.logistics_tracker.no_stations"),
                    imageWidth / 2, 146, MUTED);
        }
        graphics.drawCenteredString(font, trim(Component.translatable(
                        "gui.maid_storage_manager_extension.logistics_tracker.recall_hint"), 220),
                imageWidth / 2, 190, MUTED);
        graphics.drawCenteredString(font, trim(Component.translatable(
                        "gui.maid_storage_manager_extension.logistics_tracker.locate_hint"), 220),
                imageWidth / 2, 201, MUTED);
    }

    private Component mode(LogisticsSnapshot.Snapshot snapshot) {
        return Component.translatable("gui.maid_storage_manager_extension.logistics_tracker.mode",
                Component.translatable("gui.maid_storage_manager_extension.logistics_tracker.mode."
                        + snapshot.transportMode().name().toLowerCase(Locale.ROOT)));
    }

    private Component phase(LogisticsSnapshot.Snapshot snapshot) {
        return Component.translatable("gui.maid_storage_manager_extension.logistics_tracker.status",
                Component.translatable("gui.maid_storage_manager_extension.courier.phase."
                        + snapshot.phase().toLowerCase(Locale.ROOT)));
    }

    private Component target(LogisticsSnapshot.Snapshot snapshot) {
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

    private Component distance(LogisticsSnapshot.Snapshot snapshot) {
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

    private Component trim(Component value, int maxWidth) {
        if (font.width(value) <= maxWidth) return value;
        String text = value.getString();
        String suffix = "…";
        int end = text.length();
        while (end > 0 && font.width(text.substring(0, end) + suffix) > maxWidth) end--;
        return Component.literal(text.substring(0, end) + suffix);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (recallButton != null) {
            recallButton.active = LogisticsTrackerClientData.get(menu.courier()).recallAvailable();
        }
        if (locateButton != null) {
            LogisticsSnapshot.Snapshot snapshot = LogisticsTrackerClientData.get(menu.courier());
            locateButton.active = snapshot.online() && snapshot.authorized();
            if (clearWorkButton != null) {
                clearWorkButton.active = snapshot.online() && snapshot.authorized();
            }
        }
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }
}
