package io.github.maidstorageextension.client;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import io.github.maidstorageextension.compat.EnderPocketCompat;
import io.github.maidstorageextension.data.CourierData;
import io.github.maidstorageextension.maid.courier.CourierConfigMenu;
import io.github.maidstorageextension.network.CourierCommandPacket;
import io.github.maidstorageextension.network.ExtensionNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;
import java.util.UUID;

public final class CourierConfigScreen extends AbstractContainerScreen<CourierConfigMenu> {
    private int broomFlightDistance;
    private boolean stayHomeAfterDelivery;

    public CourierConfigScreen(CourierConfigMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 248;
        imageHeight = 258;
    }

    @Override
    protected void init() {
        super.init();
        int x = leftPos + 14;
        int y = topPos + 62;
        EntityMaid maid = menu.maid();
        broomFlightDistance = maid == null
                ? CourierData.DEFAULT_BROOM_FLIGHT_DISTANCE
                : CourierData.get(maid).broomFlightDistance();
        stayHomeAfterDelivery = maid != null
                && CourierData.get(maid).stayHomeAfterDelivery();
        List<CourierData.WarehouseBinding> stations = maid == null
                ? List.of() : CourierData.get(maid).warehouses();
        for (int i = 0; i < stations.size(); i++) {
            CourierData.WarehouseBinding station = stations.get(i);
            int index = i;
            addRenderableWidget(Button.builder(stationLabel(station, index == 0), button -> {
                        sendSelect(station.warehouse());
                        onClose();
                    })
                    .bounds(x + (i % 2) * 116, y + (i / 2) * 24, 104, 20).build());
        }
        int controlsY = y + 76;
        addRenderableWidget(Button.builder(
                        Component.translatable("gui.maid_storage_manager_extension.courier.bind_nearest"),
                        button -> send(CourierCommandPacket.Command.BIND_NEAREST))
                .bounds(x, controlsY, 104, 20).build());
        addRenderableWidget(Button.builder(
                        Component.translatable("gui.maid_storage_manager_extension.courier.confirm_deposit"),
                        button -> send(CourierCommandPacket.Command.CONFIRM_DEPOSIT))
                .bounds(x + 116, controlsY, 104, 20).build());
        addRenderableWidget(Button.builder(
                        Component.translatable("gui.maid_storage_manager_extension.courier.unbind"),
                        button -> send(CourierCommandPacket.Command.UNBIND))
                .bounds(x, controlsY + 24, 104, 20).build());
        addRenderableWidget(Button.builder(flightDistanceLabel(), button -> {
                    broomFlightDistance = broomFlightDistance >= CourierData.MAX_BROOM_FLIGHT_DISTANCE
                            ? CourierData.MIN_BROOM_FLIGHT_DISTANCE
                            : broomFlightDistance + 8;
                    button.setMessage(flightDistanceLabel());
                    sendBroomFlightDistance();
                })
                .bounds(x + 116, controlsY + 24, 104, 20).build());
        addRenderableWidget(Button.builder(postDeliveryModeLabel(), button -> {
                    stayHomeAfterDelivery = !stayHomeAfterDelivery;
                    button.setMessage(postDeliveryModeLabel());
                    sendPostDeliveryHomeMode();
                })
                .bounds(x, controlsY + 48, 220, 20).build());
    }

    private void send(CourierCommandPacket.Command command) {
        EntityMaid maid = menu.maid();
        if (maid != null) {
            ExtensionNetwork.CHANNEL.sendToServer(CourierCommandPacket.courier(maid.getId(), command));
        }
    }

    private void sendBroomFlightDistance() {
        EntityMaid maid = menu.maid();
        if (maid != null) {
            ExtensionNetwork.CHANNEL.sendToServer(CourierCommandPacket.broomFlightDistance(
                    maid.getId(), broomFlightDistance));
        }
    }

    private void sendPostDeliveryHomeMode() {
        EntityMaid maid = menu.maid();
        if (maid != null) {
            ExtensionNetwork.CHANNEL.sendToServer(CourierCommandPacket.postDeliveryHomeMode(
                    maid.getId(), stayHomeAfterDelivery));
        }
    }

    private void sendSelect(UUID warehouse) {
        EntityMaid maid = menu.maid();
        if (maid != null && warehouse != null) {
            ExtensionNetwork.CHANNEL.sendToServer(
                    CourierCommandPacket.selectWarehouse(maid.getId(), warehouse));
        }
    }

    private Component stationLabel(CourierData.WarehouseBinding station, boolean selected) {
        String name = station.warehouseName().isBlank()
                ? station.warehouse().toString().substring(0, 8) : station.warehouseName();
        return Component.translatable(selected
                        ? "gui.maid_storage_manager_extension.courier.station_default"
                        : "gui.maid_storage_manager_extension.courier.station",
                name);
    }

    private Component flightDistanceLabel() {
        return Component.translatable(
                "gui.maid_storage_manager_extension.courier.broom_flight_distance",
                broomFlightDistance);
    }

    private Component postDeliveryModeLabel() {
        return Component.translatable(
                "gui.maid_storage_manager_extension.courier.post_delivery_mode",
                Component.translatable(stayHomeAfterDelivery
                        ? "gui.maid_storage_manager_extension.courier.post_delivery_home"
                        : "gui.maid_storage_manager_extension.courier.post_delivery_follow"));
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xE0101010);
        graphics.fill(leftPos + 4, topPos + 4, leftPos + imageWidth - 4, topPos + imageHeight - 4,
                0xE0282830);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, 12, 10, 0xFFFFFF, false);
        EntityMaid maid = menu.maid();
        if (maid == null) return;
        CourierData.Data data = CourierData.get(maid);
        graphics.drawString(font, Component.translatable(
                "gui.maid_storage_manager_extension.courier.transport",
                Component.translatable(EnderPocketCompat.hasCourierTransport(maid)
                        ? "gui.maid_storage_manager_extension.courier.present"
                        : "gui.maid_storage_manager_extension.courier.missing")), 12, 28, 0xDDDDDD, false);
        graphics.drawString(font, Component.translatable(
                "gui.maid_storage_manager_extension.courier.phase",
                Component.translatable("gui.maid_storage_manager_extension.courier.phase."
                        + data.phase().name().toLowerCase(java.util.Locale.ROOT))),
                12, 43, 0xA0E8FF, false);
        graphics.drawString(font, Component.translatable(
                        "gui.maid_storage_manager_extension.courier.station_count",
                        data.warehouses().size(), CourierData.MAX_WAREHOUSES),
                12, 54, 0xD8C6F0, false);
        graphics.drawWordWrap(font, Component.translatable(
                        "gui.maid_storage_manager_extension.courier.broom_flight_distance.tooltip"),
                12, 214, 224, 0xB8B8B8);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }
}
