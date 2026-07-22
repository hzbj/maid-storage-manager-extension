package io.github.maidstorageextension.client;

import io.github.maidstorageextension.data.BusinessLicenseData;
import io.github.maidstorageextension.license.BusinessLicenseMenu;
import io.github.maidstorageextension.license.BusinessLicenseSnapshot;
import io.github.maidstorageextension.network.BusinessLicenseActionPacket;
import io.github.maidstorageextension.network.ExtensionNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class BusinessLicenseScreen extends AbstractContainerScreen<BusinessLicenseMenu> {
    private static final int PANEL = 0xE61B2028;
    private static final int TEXT = 0xFFF0EEE8;
    private static final int MUTED = 0xFFAAA8A2;
    private static final int ACCENT = 0xFFD6A85F;
    private static final int FILTER_X = 12;
    private static final int FILTER_Y = 90;
    private static final int FILTER_COLUMNS = 9;
    private static final int FILTER_SLOT = 16;
    private EditBox nameBox;
    private Button modeButton;
    private int refreshTicker;

    public BusinessLicenseScreen(BusinessLicenseMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 330;
        imageHeight = 250;
    }

    @Override
    protected void init() {
        super.init();
        nameBox = new EditBox(font, leftPos + 12, topPos + 28, 198, 20,
                Component.translatable("gui.maid_storage_manager_extension.business_license.name"));
        nameBox.setMaxLength(BusinessLicenseData.MAX_NAME_LENGTH);
        addRenderableWidget(nameBox);
        addRenderableWidget(Button.builder(Component.translatable(
                "gui.maid_storage_manager_extension.business_license.save_name"), ignored ->
                send(BusinessLicenseActionPacket.Action.RENAME,
                        BusinessLicenseData.RuleMode.WHITELIST, nameBox.getValue()))
                .bounds(leftPos + 214, topPos + 28, 104, 20).build());
        modeButton = addRenderableWidget(Button.builder(Component.empty(), ignored -> {
            BusinessLicenseSnapshot.Snapshot snapshot = snapshot();
            BusinessLicenseData.RuleMode next = snapshot != null
                    && snapshot.mode() == BusinessLicenseData.RuleMode.WHITELIST
                    ? BusinessLicenseData.RuleMode.BLACKLIST : BusinessLicenseData.RuleMode.WHITELIST;
            send(BusinessLicenseActionPacket.Action.SET_MODE, next, "");
        }).bounds(leftPos + 12, topPos + 54, 98, 20).build());
        addRenderableWidget(button("add_held", 116, 54,
                BusinessLicenseActionPacket.Action.TOGGLE_HELD_FILTER));
        addRenderableWidget(button("clear", 220, 54,
                BusinessLicenseActionPacket.Action.CLEAR_FILTER));
        addRenderableWidget(button("container", 12, 224,
                BusinessLicenseActionPacket.Action.ARM_CONTAINER));
        addRenderableWidget(button("landing", 116, 224,
                BusinessLicenseActionPacket.Action.ARM_LANDING));
        addRenderableWidget(button("worker", 220, 224,
                BusinessLicenseActionPacket.Action.ARM_WORKER));
        ExtensionNetwork.CHANNEL.sendToServer(BusinessLicenseActionPacket.refresh(menu.licenseId()));
    }

    private Button button(String suffix, int x, int y, BusinessLicenseActionPacket.Action action) {
        return Button.builder(Component.translatable(
                        "gui.maid_storage_manager_extension.business_license." + suffix),
                ignored -> send(action, BusinessLicenseData.RuleMode.WHITELIST, ""))
                .bounds(leftPos + x, topPos + y, 98, 20).build();
    }

    private void send(BusinessLicenseActionPacket.Action action,
                      BusinessLicenseData.RuleMode mode, String value) {
        ExtensionNetwork.CHANNEL.sendToServer(new BusinessLicenseActionPacket(
                action, menu.licenseId(), mode, value));
    }

    private BusinessLicenseSnapshot.Snapshot snapshot() {
        return BusinessLicenseClientData.get(menu.licenseId());
    }

    @Override
    public void containerTick() {
        super.containerTick();
        BusinessLicenseSnapshot.Snapshot snapshot = snapshot();
        if (snapshot != null) {
            if (!nameBox.isFocused() && !nameBox.getValue().equals(snapshot.name())) {
                nameBox.setValue(snapshot.name());
            }
            modeButton.setMessage(Component.translatable(
                    "gui.maid_storage_manager_extension.business_license.mode."
                            + snapshot.mode().name().toLowerCase(java.util.Locale.ROOT)));
        }
        if (++refreshTicker >= 40) {
            refreshTicker = 0;
            ExtensionNetwork.CHANNEL.sendToServer(BusinessLicenseActionPacket.refresh(menu.licenseId()));
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, PANEL);
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + 2, ACCENT);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, 12, 9, TEXT, false);
        BusinessLicenseSnapshot.Snapshot snapshot = snapshot();
        if (snapshot == null) {
            graphics.drawString(font, Component.translatable(
                    "gui.maid_storage_manager_extension.business_license.loading"),
                    12, 82, MUTED, false);
            return;
        }
        graphics.drawString(font, Component.translatable(
                "gui.maid_storage_manager_extension.business_license.filter_count",
                snapshot.filterItems().size(), BusinessLicenseData.MAX_FILTER_ITEMS),
                12, 80, MUTED, false);
        for (int i = 0; i < BusinessLicenseData.MAX_FILTER_ITEMS; i++) {
            int column = i % FILTER_COLUMNS;
            int row = i / FILTER_COLUMNS;
            int slotX = FILTER_X + column * FILTER_SLOT;
            int slotY = FILTER_Y + row * FILTER_SLOT;
            graphics.fill(slotX, slotY, slotX + 15, slotY + 15, 0xFF292D34);
        }
        List<ResourceLocation> items = snapshot.filterItems();
        for (int i = 0; i < Math.min(BusinessLicenseData.MAX_FILTER_ITEMS, items.size()); i++) {
            int column = i % FILTER_COLUMNS;
            int row = i / FILTER_COLUMNS;
            int slotX = FILTER_X + column * FILTER_SLOT;
            int slotY = FILTER_Y + row * FILTER_SLOT;
            Item item = ForgeRegistries.ITEMS.getValue(items.get(i));
            if (item != null) graphics.renderItem(new ItemStack(item), slotX, slotY);
        }
        graphics.drawString(font, Component.translatable(
                "gui.maid_storage_manager_extension.business_license.summary",
                snapshot.workers().size(), BusinessLicenseData.MAX_WORKERS,
                snapshot.containers(), BusinessLicenseData.MAX_CONTAINERS),
                166, 92, MUTED, false);
        String landing = snapshot.landing() == null ? "-"
                : snapshot.landing().getX() + ", " + snapshot.landing().getY()
                + ", " + snapshot.landing().getZ();
        graphics.drawString(font, Component.translatable(
                "gui.maid_storage_manager_extension.business_license.landing_value", landing),
                166, 106, MUTED, false);
        if (!snapshot.blocker().isBlank()) {
            graphics.drawString(font, Component.translatable(snapshot.blocker()),
                    166, 120, 0xFFFF7777, false);
        }
        graphics.drawWordWrap(font, Component.translatable(
                        "gui.maid_storage_manager_extension.business_license.jei_help"),
                166, 144, 150, MUTED);
    }

    public void toggleFilter(ItemStack stack) {
        ResourceLocation item = stack == null || stack.isEmpty()
                ? null : ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (item != null) send(BusinessLicenseActionPacket.Action.TOGGLE_FILTER,
                BusinessLicenseData.RuleMode.WHITELIST, item.toString());
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        BusinessLicenseSnapshot.Snapshot snapshot = snapshot();
        if (snapshot != null && (button == 0 || button == 1)) {
            int localX = (int) mouseX - leftPos - FILTER_X;
            int localY = (int) mouseY - topPos - FILTER_Y;
            if (localX >= 0 && localY >= 0
                    && localX < FILTER_COLUMNS * FILTER_SLOT
                    && localY < 6 * FILTER_SLOT) {
                int index = localY / FILTER_SLOT * FILTER_COLUMNS + localX / FILTER_SLOT;
                if (index < snapshot.filterItems().size()) {
                    send(BusinessLicenseActionPacket.Action.TOGGLE_FILTER,
                            BusinessLicenseData.RuleMode.WHITELIST,
                            snapshot.filterItems().get(index).toString());
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private Component trim(Component value, int width) {
        return font.width(value) <= width ? value
                : Component.literal(font.plainSubstrByWidth(value.getString(), Math.max(0, width - 8)) + "…");
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        BusinessLicenseSnapshot.Snapshot snapshot = snapshot();
        if (snapshot != null) {
            int localX = mouseX - leftPos - FILTER_X;
            int localY = mouseY - topPos - FILTER_Y;
            if (localX >= 0 && localY >= 0
                    && localX < FILTER_COLUMNS * FILTER_SLOT && localY < 6 * FILTER_SLOT) {
                int index = localY / FILTER_SLOT * FILTER_COLUMNS + localX / FILTER_SLOT;
                if (index < snapshot.filterItems().size()) {
                    Item item = ForgeRegistries.ITEMS.getValue(snapshot.filterItems().get(index));
                    if (item != null) graphics.renderTooltip(font, new ItemStack(item), mouseX, mouseY);
                }
            }
        }
        renderTooltip(graphics, mouseX, mouseY);
    }
}
