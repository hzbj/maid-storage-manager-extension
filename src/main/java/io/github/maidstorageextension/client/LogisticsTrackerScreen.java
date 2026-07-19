package io.github.maidstorageextension.client;

import io.github.maidstorageextension.logistics.LogisticsDisplayName;
import io.github.maidstorageextension.logistics.LogisticsSnapshot;
import io.github.maidstorageextension.logistics.LogisticsTrackerMenu;
import io.github.maidstorageextension.logistics.NetworkWarehouseSnapshot;
import io.github.maidstorageextension.network.MaidTransportActionPacket;
import io.github.maidstorageextension.network.CourierCommandPacket;
import io.github.maidstorageextension.network.ExtensionNetwork;
import io.github.maidstorageextension.network.NetworkWarehouseActionPacket;
import io.github.maidstorageextension.network.TerminalAccountActionPacket;
import io.github.maidstorageextension.network.TerminalMailboxActionPacket;
import io.github.maidstorageextension.terminal.TerminalAccountSnapshot;
import io.github.maidstorageextension.terminal.MaidTransportSnapshot;
import io.github.maidstorageextension.terminal.MailboxKey;
import net.minecraft.core.BlockPos;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Full-screen maid communication terminal. Only the network warehouse tab is active in phase one. */
public final class LogisticsTrackerScreen extends AbstractContainerScreen<LogisticsTrackerMenu> {
    private static final int SCREEN_BG = 0xE8141214;
    private static final int PANEL_BG = 0xE82A2521;
    private static final int PANEL_ALT = 0xE8332D27;
    private static final int BORDER = 0xFF806A4C;
    private static final int BORDER_ACTIVE = 0xFFB98BCE;
    private static final int TEXT = 0xFFF2E8D5;
    private static final int MUTED = 0xFFB9AA95;
    private static final int ACCENT = 0xFFC18ED4;
    private static final int GOOD = 0xFF62C7A8;
    private static final int WARN = 0xFFE3B75C;
    private static final int ERROR = 0xFFE16A7E;
    private static final int MAP_BG = 0xFF171B1A;
    private static final int MAP_PLAYER = 0xFF45D47A;
    private static final int MAP_MAID = 0xFFFFD34E;
    private static final int MAP_FRIEND = 0xFFF4F4F4;
    private static final int MAP_WAREHOUSE_ROOF = 0xFFB56D42;
    private static final int MAP_WAREHOUSE_WALL = 0xFFF0D09A;
    private static final double BASE_BLOCKS_PER_SCREEN_PIXEL = 1.0D;
    private static final int SLOT = 22;

    private enum ServiceTab {
        NETWORK_WAREHOUSE,
        MAID_TRANSPORT
    }

    private enum WarehouseMode {
        WITHDRAW,
        DEPOSIT
    }

    private enum TransportPointMode {
        PICKUP,
        DESTINATION
    }

    private ServiceTab serviceTab = ServiceTab.NETWORK_WAREHOUSE;
    private WarehouseMode warehouseMode = WarehouseMode.WITHDRAW;
    private NetworkWarehouseActionPacket.DeliveryTarget deliveryTarget =
            NetworkWarehouseActionPacket.DeliveryTarget.PLAYER;
    private final Map<String, Integer> selectedAmounts = new LinkedHashMap<>();
    private final Map<String, ItemStack> selectedStacks = new LinkedHashMap<>();
    private EditBox searchBox;
    private EditBox usernameBox;
    private EditBox passwordBox;
    private final List<Button> serviceButtons = new ArrayList<>();
    private Button withdrawButton;
    private Button depositButton;
    private Button playerDeliveryButton;
    private Button chestDeliveryButton;
    private Button actionButton;
    private Button recallButton;
    private Button locateButton;
    private Button clearWorkButton;
    private Button centerMapButton;
    private Button loginButton;
    private Button createAccountButton;
    private Button changePasswordButton;
    private Button logoutButton;
    private Button settingsButton;
    private Button choosePickupButton;
    private Button chooseDestinationButton;
    private Button startTransportButton;
    private Button endTransportButton;
    private Button returnWarehouseButton;
    private int inventoryScroll;
    private int refreshTicker;
    private boolean staleConfirmed;
    private double mapZoom = 1.0D;
    private double mapCenterX;
    private double mapCenterZ;
    private boolean mapCenterInitialized;
    private final TerrainMapTexture terrainMap = new TerrainMapTexture();
    private TerrainMapTexture.View renderedMapView;
    private TransportPointMode transportPointMode = TransportPointMode.DESTINATION;
    private BlockPos selectedPickup;
    private BlockPos selectedDestination;
    private boolean transportMapPressed;
    private boolean transportMapDragged;
    private double transportMapPressX;
    private double transportMapPressY;
    private int expandedMailbox = -1;
    private boolean settingsPage;

    public LogisticsTrackerScreen(LogisticsTrackerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 320;
        imageHeight = 240;
        inventoryLabelY = Integer.MAX_VALUE;
    }

    @Override
    protected void init() {
        imageWidth = width;
        imageHeight = height;
        super.init();
        leftPos = 0;
        topPos = 0;
        Layout layout = layout();

        serviceButtons.clear();
        int tabEnd = width - 120;
        int tabWidth = Mth.clamp((tabEnd - 84) / ServiceTab.values().length, 58, 112);
        int tabStart = Math.max(84, tabEnd - tabWidth * ServiceTab.values().length);
        ServiceTab[] tabs = ServiceTab.values();
        for (int i = 0; i < tabs.length; i++) {
            ServiceTab tab = tabs[i];
            Button button = addRenderableWidget(Button.builder(serviceName(tab), ignored -> {
                        serviceTab = tab;
                        settingsPage = false;
                        staleConfirmed = false;
                        updateWidgetState();
                    }).bounds(tabStart + i * tabWidth, 5, tabWidth - 2, 20).build());
            serviceButtons.add(button);
        }

        settingsButton = addRenderableWidget(Button.builder(Component.translatable(
                        "gui.maid_storage_manager_extension.terminal.settings_button"), ignored -> {
                    settingsPage = true;
                    updateWidgetState();
                }).bounds(width - 116, 5, 40, 20).build());
        settingsButton.setTooltip(Tooltip.create(Component.translatable(
                "gui.maid_storage_manager_extension.terminal.settings")));

        logoutButton = addRenderableWidget(Button.builder(Component.translatable(
                        "gui.maid_storage_manager_extension.terminal.logout"), ignored ->
                        ExtensionNetwork.CHANNEL.sendToServer(new TerminalAccountActionPacket(
                                TerminalAccountActionPacket.Action.LOGOUT, menu.terminal(),
                                "", "", null)))
                .bounds(width - 72, 5, 64, 20).build());

        int loginWidth = Math.min(260, Math.max(200, width / 3));
        int loginX = (width - loginWidth) / 2;
        int loginY = Math.max(54, height / 2 - 58);
        usernameBox = addRenderableWidget(new EditBox(font, loginX, loginY,
                loginWidth, 20, Component.translatable(
                "gui.maid_storage_manager_extension.terminal.username")));
        usernameBox.setHint(Component.translatable(
                "gui.maid_storage_manager_extension.terminal.username_hint"));
        usernameBox.setMaxLength(24);
        passwordBox = addRenderableWidget(new EditBox(font, loginX, loginY + 27,
                loginWidth, 20, Component.translatable(
                "gui.maid_storage_manager_extension.terminal.password")));
        passwordBox.setHint(Component.translatable(
                "gui.maid_storage_manager_extension.terminal.password_hint"));
        passwordBox.setMaxLength(128);
        passwordBox.setFormatter((value, index) -> Component.literal(
                "•".repeat(value.length())).getVisualOrderText());
        int authButtonWidth = (loginWidth - 6) / 2;
        loginButton = addRenderableWidget(Button.builder(Component.translatable(
                        "gui.maid_storage_manager_extension.terminal.login"), ignored ->
                        authenticate(false))
                .bounds(loginX, loginY + 55, authButtonWidth, 20).build());
        createAccountButton = addRenderableWidget(Button.builder(Component.translatable(
                        "gui.maid_storage_manager_extension.terminal.create"), ignored ->
                        authenticate(true))
                .bounds(loginX + authButtonWidth + 6, loginY + 55,
                        authButtonWidth, 20).build());
        changePasswordButton = addRenderableWidget(Button.builder(Component.translatable(
                        "gui.maid_storage_manager_extension.terminal.change_password"), ignored -> {
                    String password = passwordBox.getValue();
                    ExtensionNetwork.CHANNEL.sendToServer(new TerminalAccountActionPacket(
                            TerminalAccountActionPacket.Action.CHANGE_PASSWORD,
                            menu.terminal(), "", password, null));
                    passwordBox.setValue("");
                }).bounds(loginX, loginY + 55, loginWidth, 20).build());

        centerMapButton = addRenderableWidget(Button.builder(Component.translatable(
                        "gui.maid_storage_manager_extension.compass.center_player"), ignored ->
                        centerMapOnPlayer())
                .bounds(layout.mapX + Math.max(0, layout.mapWidth - 61),
                        layout.contentY + 3, 57, 16).build());

        withdrawButton = addRenderableWidget(Button.builder(Component.translatable(
                        "gui.maid_storage_manager_extension.compass.withdraw"), ignored -> {
                    warehouseMode = WarehouseMode.WITHDRAW;
                    updateWidgetState();
                }).bounds(layout.rightX + 5, layout.contentY + 24,
                        Math.max(55, (layout.rightWidth - 14) / 2), 18).build());
        depositButton = addRenderableWidget(Button.builder(Component.translatable(
                        "gui.maid_storage_manager_extension.compass.deposit"), ignored -> {
                    warehouseMode = WarehouseMode.DEPOSIT;
                    updateWidgetState();
                }).bounds(layout.rightX + 8 + Math.max(55, (layout.rightWidth - 14) / 2),
                        layout.contentY + 24, Math.max(55, (layout.rightWidth - 14) / 2), 18).build());

        searchBox = addRenderableWidget(new EditBox(font, layout.rightX + 6,
                layout.contentY + 47, layout.rightWidth - 12, 18,
                Component.translatable("gui.maid_storage_manager_extension.compass.search")));
        searchBox.setHint(Component.translatable(
                "gui.maid_storage_manager_extension.compass.search_hint"));
        searchBox.setResponder(ignored -> inventoryScroll = 0);
        searchBox.setMaxLength(80);

        int deliveryWidth = Math.max(62, (layout.rightWidth - 14) / 2);
        int deliveryY = layout.contentBottom - 65;
        playerDeliveryButton = addRenderableWidget(Button.builder(Component.translatable(
                        "gui.maid_storage_manager_extension.compass.deliver_player"), ignored -> {
                    deliveryTarget = NetworkWarehouseActionPacket.DeliveryTarget.PLAYER;
                    staleConfirmed = false;
                    updateWidgetState();
                }).bounds(layout.rightX + 5, deliveryY, deliveryWidth, 18).build());
        chestDeliveryButton = addRenderableWidget(Button.builder(Component.translatable(
                        "gui.maid_storage_manager_extension.compass.deliver_chest"), ignored -> {
                    deliveryTarget = NetworkWarehouseActionPacket.DeliveryTarget.FIXED_CHEST;
                    staleConfirmed = false;
                    updateWidgetState();
                }).bounds(layout.rightX + 8 + deliveryWidth, deliveryY, deliveryWidth, 18).build());

        actionButton = addRenderableWidget(Button.builder(Component.translatable(
                        "gui.maid_storage_manager_extension.compass.submit_request"), ignored -> submit())
                .bounds(layout.rightX + 5, layout.contentBottom - 42,
                        layout.rightWidth - 10, 20).build());

        int footerY = height - 25;
        recallButton = addRenderableWidget(Button.builder(Component.translatable(
                        "gui.maid_storage_manager_extension.logistics_tracker.recall"), ignored ->
                        ExtensionNetwork.CHANNEL.sendToServer(
                                CourierCommandPacket.recall(activeCourier())))
                .bounds(8, footerY, 76, 20).build());
        locateButton = addRenderableWidget(Button.builder(Component.translatable(
                        "gui.maid_storage_manager_extension.logistics_tracker.locate"), ignored ->
                        ExtensionNetwork.CHANNEL.sendToServer(
                                CourierCommandPacket.locate(activeCourier())))
                .bounds(87, footerY, 76, 20).build());
        clearWorkButton = addRenderableWidget(Button.builder(Component.translatable(
                        "gui.maid_storage_manager_extension.logistics_tracker.clear_work"), ignored ->
                        ExtensionNetwork.CHANNEL.sendToServer(
                                CourierCommandPacket.clearWork(activeCourier())))
                .bounds(166, footerY, 92, 20).build());
        clearWorkButton.setTooltip(Tooltip.create(Component.translatable(
                "gui.maid_storage_manager_extension.logistics_tracker.clear_work_hint")));

        int transportX = layout.rightX + 6;
        int transportWidth = layout.rightWidth - 12;
        int transportButtonWidth = Math.max(62, (transportWidth - 4) / 2);
        choosePickupButton = addRenderableWidget(Button.builder(Component.translatable(
                        "gui.maid_storage_manager_extension.transport.choose_pickup"), ignored -> {
                    if (hasShiftDown()) selectedPickup = null;
                    transportPointMode = TransportPointMode.PICKUP;
                    updateWidgetState();
                }).bounds(transportX, layout.contentY + 94,
                        transportButtonWidth, 20).build());
        choosePickupButton.setTooltip(Tooltip.create(Component.translatable(
                "gui.maid_storage_manager_extension.transport.choose_pickup_hint")));
        chooseDestinationButton = addRenderableWidget(Button.builder(Component.translatable(
                        "gui.maid_storage_manager_extension.transport.choose_destination"), ignored -> {
                    transportPointMode = TransportPointMode.DESTINATION;
                    updateWidgetState();
                }).bounds(transportX + transportButtonWidth + 4, layout.contentY + 94,
                        transportButtonWidth, 20).build());
        startTransportButton = addRenderableWidget(Button.builder(Component.translatable(
                        "gui.maid_storage_manager_extension.transport.start"), ignored ->
                        ExtensionNetwork.CHANNEL.sendToServer(MaidTransportActionPacket.start(
                                menu.terminal(), selectedPickup, selectedDestination)))
                .bounds(transportX, layout.contentBottom - 48,
                        transportButtonWidth, 20).build());
        returnWarehouseButton = addRenderableWidget(Button.builder(Component.translatable(
                        "gui.maid_storage_manager_extension.transport.return_warehouse"), ignored ->
                        ExtensionNetwork.CHANNEL.sendToServer(
                                MaidTransportActionPacket.returnToWarehouse(
                                        menu.terminal(), activeMailbox())))
                .bounds(transportX + transportButtonWidth + 4, layout.contentBottom - 48,
                        transportButtonWidth, 20).build());
        endTransportButton = addRenderableWidget(Button.builder(Component.translatable(
                        "gui.maid_storage_manager_extension.transport.end"), ignored ->
                        ExtensionNetwork.CHANNEL.sendToServer(
                                MaidTransportActionPacket.end(menu.terminal())))
                .bounds(transportX, layout.contentBottom - 48,
                        transportWidth, 20).build());

        ExtensionNetwork.CHANNEL.sendToServer(
                TerminalAccountActionPacket.refresh(menu.terminal()));
        if (isBound()) {
            ExtensionNetwork.CHANNEL.sendToServer(
                    NetworkWarehouseActionPacket.refresh(
                            menu.terminal(), activeCourier(), activeMailbox()));
        }
        updateWidgetState();
    }

    @Override
    public void containerTick() {
        super.containerTick();
        if (searchBox != null) searchBox.tick();
        if (usernameBox != null) usernameBox.tick();
        if (passwordBox != null) passwordBox.tick();
        if (++refreshTicker >= 20) {
            refreshTicker = 0;
            ExtensionNetwork.CHANNEL.sendToServer(
                    TerminalAccountActionPacket.refresh(menu.terminal()));
        }
        updateWidgetState();
    }

    private void updateWidgetState() {
        boolean authenticated = accountSnapshot().authenticated();
        boolean mustChangePassword = authenticated
                && accountSnapshot().passwordChangeRequired();
        boolean usable = authenticated && !mustChangePassword;
        boolean network = usable && !settingsPage
                && serviceTab == ServiceTab.NETWORK_WAREHOUSE && isBound();
        boolean transport = usable && !settingsPage && serviceTab == ServiceTab.MAID_TRANSPORT
                && accountSnapshot().selectedDriver() != null;
        NetworkWarehouseSnapshot.Snapshot warehouse = warehouseSnapshot();
        LogisticsSnapshot.Snapshot logistics = logisticsSnapshot();
        MaidTransportSnapshot.Snapshot ride = transportSnapshot();
        for (int i = 0; i < serviceButtons.size(); i++) {
            serviceButtons.get(i).visible = usable;
            serviceButtons.get(i).active = settingsPage || serviceTab.ordinal() != i;
        }
        usernameBox.setVisible(!authenticated);
        passwordBox.setVisible(!authenticated || mustChangePassword);
        loginButton.visible = !authenticated;
        createAccountButton.visible = !authenticated;
        changePasswordButton.visible = mustChangePassword;
        changePasswordButton.active = passwordBox.getValue().getBytes(
                java.nio.charset.StandardCharsets.UTF_8).length >= 8;
        logoutButton.visible = authenticated;
        logoutButton.setMessage(Component.literal(authenticated
                ? accountSnapshot().username() : "").append(" ").append(Component.translatable(
                "gui.maid_storage_manager_extension.terminal.logout")));
        settingsButton.visible = usable;
        settingsButton.active = !settingsPage;
        centerMapButton.visible = usable && !settingsPage && layout().mapWidth >= 150;
        if (withdrawButton == null) return;
        withdrawButton.visible = network;
        depositButton.visible = network;
        withdrawButton.active = warehouseMode != WarehouseMode.WITHDRAW;
        depositButton.active = warehouseMode != WarehouseMode.DEPOSIT;
        boolean withdraw = network && warehouseMode == WarehouseMode.WITHDRAW;
        searchBox.setVisible(withdraw);
        playerDeliveryButton.visible = withdraw;
        chestDeliveryButton.visible = withdraw;
        playerDeliveryButton.active = deliveryTarget
                != NetworkWarehouseActionPacket.DeliveryTarget.PLAYER;
        chestDeliveryButton.active = warehouse.fixedDeliveryAvailable()
                && deliveryTarget != NetworkWarehouseActionPacket.DeliveryTarget.FIXED_CHEST;
        if (!warehouse.fixedDeliveryAvailable()
                && deliveryTarget == NetworkWarehouseActionPacket.DeliveryTarget.FIXED_CHEST) {
            deliveryTarget = NetworkWarehouseActionPacket.DeliveryTarget.PLAYER;
            playerDeliveryButton.active = false;
        }
        actionButton.visible = network;
        actionButton.active = warehouse.online() && warehouse.authorized()
                && warehouse.warehouse() != null && !warehouse.activeTransaction()
                && (warehouseMode == WarehouseMode.DEPOSIT
                || warehouse.heldRequestListAvailable() && !selectedAmounts.isEmpty()
                && warehouse.inventoryState() != NetworkWarehouseSnapshot.InventoryState.UNAVAILABLE);
        actionButton.setMessage(Component.translatable(warehouseMode == WarehouseMode.DEPOSIT
                ? "gui.maid_storage_manager_extension.compass.confirm_deposit"
                : !warehouse.heldRequestListAvailable()
                ? "gui.maid_storage_manager_extension.compass.request_list_required"
                : warehouse.inventoryState() == NetworkWarehouseSnapshot.InventoryState.STALE
                && !staleConfirmed
                ? "gui.maid_storage_manager_extension.compass.confirm_stale"
                : "gui.maid_storage_manager_extension.compass.submit_request"));

        boolean controls = network;
        recallButton.visible = controls;
        locateButton.visible = controls;
        clearWorkButton.visible = controls;
        recallButton.active = logistics.recallAvailable();
        locateButton.active = logistics.online() && logistics.authorized();
        clearWorkButton.active = logistics.online() && logistics.authorized();

        choosePickupButton.visible = transport && !ride.active();
        chooseDestinationButton.visible = transport && !ride.active();
        choosePickupButton.active = transportPointMode != TransportPointMode.PICKUP;
        chooseDestinationButton.active = transportPointMode != TransportPointMode.DESTINATION;
        startTransportButton.visible = transport && !ride.active();
        startTransportButton.active = ride.state() == MaidTransportSnapshot.State.READY
                && selectedDestination != null;
        returnWarehouseButton.visible = transport && !ride.active();
        returnWarehouseButton.active = activeMailbox() != null
                && (ride.state() == MaidTransportSnapshot.State.READY
                || ride.state() == MaidTransportSnapshot.State.FOLLOWING_OWNER
                || ride.state() == MaidTransportSnapshot.State.WAREHOUSE_STANDBY);
        endTransportButton.visible = transport && ride.active();
        endTransportButton.active = ride.state() != MaidTransportSnapshot.State.EMERGENCY_LANDING
                && ride.state() != MaidTransportSnapshot.State.PLAYER_CONTROLLED;
    }

    private void authenticate(boolean create) {
        String username = usernameBox.getValue();
        String password = passwordBox.getValue();
        ExtensionNetwork.CHANNEL.sendToServer(create
                ? TerminalAccountActionPacket.create(menu.terminal(), username, password)
                : TerminalAccountActionPacket.login(menu.terminal(), username, password));
        passwordBox.setValue("");
    }

    private void submit() {
        NetworkWarehouseSnapshot.Snapshot snapshot = warehouseSnapshot();
        if (warehouseMode == WarehouseMode.DEPOSIT) {
            ExtensionNetwork.CHANNEL.sendToServer(
                    NetworkWarehouseActionPacket.confirmDeposit(
                            menu.terminal(), activeCourier(), snapshot.warehouse(),
                            snapshot.mailboxKey()));
            return;
        }
        if (snapshot.inventoryState() == NetworkWarehouseSnapshot.InventoryState.STALE
                && !staleConfirmed) {
            staleConfirmed = true;
            updateWidgetState();
            return;
        }
        List<NetworkWarehouseActionPacket.RequestedItem> items = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : selectedAmounts.entrySet()) {
            ItemStack stack = selectedStacks.get(entry.getKey());
            if (stack != null && !stack.isEmpty() && entry.getValue() > 0) {
                items.add(new NetworkWarehouseActionPacket.RequestedItem(stack, entry.getValue()));
            }
        }
        if (items.isEmpty()) return;
        ExtensionNetwork.CHANNEL.sendToServer(NetworkWarehouseActionPacket.submit(
                menu.terminal(), activeCourier(), snapshot.warehouse(),
                snapshot.mailboxKey(), deliveryTarget,
                staleConfirmed, items));
        staleConfirmed = false;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(0, 0, width, height, SCREEN_BG);
        Layout layout = layout();
        if (settingsPage && accountSnapshot().authenticated()
                && !accountSnapshot().passwordChangeRequired()) {
            panel(graphics, layout.leftX, layout.contentY,
                    width - layout.leftX * 2, layout.contentHeight);
        } else {
            panel(graphics, layout.leftX, layout.contentY, layout.leftWidth, layout.contentHeight);
            if (layout.mapWidth > 0) {
                panel(graphics, layout.mapX, layout.contentY, layout.mapWidth, layout.contentHeight);
            }
            panel(graphics, layout.rightX, layout.contentY, layout.rightWidth, layout.contentHeight);
        }
        graphics.fill(0, height - 31, width, height, PANEL_BG);
        graphics.hLine(0, width, height - 31, BORDER);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, 10, 10, ACCENT, false);
        if (!accountSnapshot().authenticated()) {
            renderLogin(graphics);
            return;
        }
        if (accountSnapshot().passwordChangeRequired()) {
            renderPasswordChange(graphics);
            return;
        }
        if (settingsPage) {
            renderSettings(graphics, mouseX, mouseY);
            return;
        }
        if (serviceTab == ServiceTab.NETWORK_WAREHOUSE && !isBound()
                || serviceTab == ServiceTab.MAID_TRANSPORT
                && accountSnapshot().selectedDriver() == null) {
            renderUnbound(graphics);
            return;
        }
        if (serviceTab == ServiceTab.MAID_TRANSPORT) {
            renderMaidTransport(graphics, mouseX, mouseY);
            return;
        }
        renderNetworkWarehouse(graphics, mouseX, mouseY);
    }

    private void renderSettings(GuiGraphics graphics, int mouseX, int mouseY) {
        TerminalAccountSnapshot.Snapshot account = accountSnapshot();
        SettingsLayout settings = settingsLayout();
        graphics.drawString(font, Component.translatable(
                        "gui.maid_storage_manager_extension.terminal.settings"),
                settings.leftX, settings.titleY, TEXT, false);
        graphics.drawString(font, trim(Component.translatable(
                        "gui.maid_storage_manager_extension.terminal.settings_help"),
                        width - settings.leftX * 2),
                settings.leftX, settings.titleY + 13, MUTED, false);

        renderSettingsHeading(graphics, settings.leftX, settings.headingY,
                settings.columnWidth, "gui.maid_storage_manager_extension.terminal.settings_mailboxes");
        renderSettingsHeading(graphics, settings.rightX, settings.headingY,
                settings.columnWidth, "gui.maid_storage_manager_extension.terminal.settings_couriers");

        if (account.mailboxes().isEmpty()) {
            graphics.drawString(font, Component.translatable(
                            "gui.maid_storage_manager_extension.terminal.settings_no_mailboxes"),
                    settings.leftX + 4, settings.rowsY + 8, MUTED, false);
        }
        int mailboxRows = Math.min(settings.maxRows, account.mailboxes().size());
        for (int i = 0; i < mailboxRows; i++) {
            TerminalAccountSnapshot.Mailbox mailbox = account.mailboxes().get(i);
            int rowY = settings.rowsY + i * settings.rowStride;
            boolean hovered = inside(mouseX, mouseY, settings.leftX + settings.columnWidth - 51,
                    rowY + 3, 47, 20);
            Component name = mailbox.warehouseName().isBlank()
                    ? Component.literal(mailbox.warehouse() == null ? "?"
                    : mailbox.warehouse().toString().substring(0, 8))
                    : LogisticsDisplayName.decode(mailbox.warehouseName());
            renderSettingsRow(graphics, settings.leftX, rowY, settings.columnWidth,
                    name, Component.literal(mailbox.position().getX() + ", "
                            + mailbox.position().getY() + ", " + mailbox.position().getZ()), hovered);
        }

        if (account.maids().isEmpty()) {
            graphics.drawString(font, Component.translatable(
                            "gui.maid_storage_manager_extension.terminal.settings_no_couriers"),
                    settings.rightX + 4, settings.rowsY + 8, MUTED, false);
        }
        int maidRows = Math.min(settings.maxRows, account.maids().size());
        for (int i = 0; i < maidRows; i++) {
            TerminalAccountSnapshot.Maid maid = account.maids().get(i);
            int rowY = settings.rowsY + i * settings.rowStride;
            boolean hovered = inside(mouseX, mouseY, settings.rightX + settings.columnWidth - 51,
                    rowY + 3, 47, 20);
            Component state = Component.translatable(maid.busy()
                    ? "gui.maid_storage_manager_extension.courier.phase." + maid.phase()
                    : maid.online() ? "gui.maid_storage_manager_extension.terminal.ready"
                    : "gui.maid_storage_manager_extension.terminal.offline");
            renderSettingsRow(graphics, settings.rightX, rowY, settings.columnWidth,
                    LogisticsDisplayName.decode(maid.name()), state, hovered);
        }
    }

    private void renderSettingsHeading(GuiGraphics graphics, int x, int y, int width, String key) {
        graphics.drawString(font, trim(Component.translatable(key), width), x, y, ACCENT, false);
        graphics.hLine(x, x + width - 1, y + 12, BORDER);
    }

    private void renderSettingsRow(GuiGraphics graphics, int x, int y, int width,
                                   Component name, Component detail, boolean hovered) {
        graphics.fill(x, y, x + width, y + 26, PANEL_ALT);
        outline(graphics, x, y, width, 26, BORDER);
        graphics.drawString(font, trim(name, width - 60), x + 5, y + 4, TEXT, false);
        graphics.drawString(font, trim(detail, width - 60), x + 5, y + 15, MUTED, false);
        int buttonX = x + width - 51;
        graphics.fill(buttonX, y + 3, buttonX + 47, y + 23,
                hovered ? 0xFF7A3442 : 0xFF542B34);
        outline(graphics, buttonX, y + 3, 47, 20, hovered ? ERROR : 0xFF9B5260);
        graphics.drawCenteredString(font, Component.translatable(
                        "gui.maid_storage_manager_extension.terminal.unbind"),
                buttonX + 23, y + 9, TEXT);
    }

    private void renderLogin(GuiGraphics graphics) {
        TerminalAccountSnapshot.Snapshot snapshot = accountSnapshot();
        int y = Math.max(32, height / 2 - 86);
        graphics.drawCenteredString(font, Component.translatable(
                        "gui.maid_storage_manager_extension.terminal.login_title"),
                width / 2, y, TEXT);
        graphics.drawCenteredString(font, Component.translatable(
                        "gui.maid_storage_manager_extension.terminal.login_help"),
                width / 2, y + 14, MUTED);
        if (!snapshot.error().isBlank()) {
            graphics.drawCenteredString(font, Component.translatable(snapshot.error()),
                    width / 2, y + 102, ERROR);
        }
    }

    private void renderPasswordChange(GuiGraphics graphics) {
        int y = Math.max(32, height / 2 - 86);
        graphics.drawCenteredString(font, Component.translatable(
                        "gui.maid_storage_manager_extension.terminal.change_password_title"),
                width / 2, y, TEXT);
        graphics.drawCenteredString(font, Component.translatable(
                        "gui.maid_storage_manager_extension.terminal.change_password_help"),
                width / 2, y + 14, WARN);
    }

    private void renderUnbound(GuiGraphics graphics) {
        Layout layout = layout();
        graphics.drawCenteredString(font, Component.translatable(
                        "gui.maid_storage_manager_extension.compass.unbound_title"),
                width / 2, layout.contentY + layout.contentHeight / 2 - 10, WARN);
        graphics.drawCenteredString(font, Component.translatable(
                        "gui.maid_storage_manager_extension.compass.unbound_help"),
                width / 2, layout.contentY + layout.contentHeight / 2 + 7, MUTED);
    }

    private void renderComingSoon(GuiGraphics graphics) {
        Layout layout = layout();
        graphics.drawCenteredString(font, serviceName(serviceTab), width / 2,
                layout.contentY + layout.contentHeight / 2 - 10, TEXT);
        graphics.drawCenteredString(font, Component.translatable(
                        "gui.maid_storage_manager_extension.compass.coming_soon"), width / 2,
                layout.contentY + layout.contentHeight / 2 + 7, MUTED);
    }

    private void renderMaidTransport(GuiGraphics graphics, int mouseX, int mouseY) {
        Layout layout = layout();
        renderTransportRoster(graphics, layout);
        if (layout.mapWidth > 0) renderMap(graphics, layout, mouseX, mouseY);
        renderTransportPanel(graphics, layout);
        renderTransportFooter(graphics);
    }

    private void renderTransportRoster(GuiGraphics graphics, Layout layout) {
        TerminalAccountSnapshot.Snapshot account = accountSnapshot();
        graphics.drawString(font, Component.translatable(
                        "gui.maid_storage_manager_extension.transport.registered_drivers"),
                layout.leftX + 6, layout.contentY + 7, TEXT, false);
        int y = layout.contentY + 22;
        int rows = Math.max(1, (layout.contentHeight - 29) / 27);
        for (int i = 0; i < Math.min(rows, account.maids().size()); i++) {
            TerminalAccountSnapshot.Maid maid = account.maids().get(i);
            boolean selected = maid.id().equals(account.selectedDriver());
            graphics.fill(layout.leftX + 4, y, layout.leftX + layout.leftWidth - 4, y + 24,
                    selected ? PANEL_ALT : PANEL_BG);
            outline(graphics, layout.leftX + 4, y, layout.leftWidth - 8, 24,
                    selected ? BORDER_ACTIVE : BORDER);
            graphics.drawString(font, trim(LogisticsDisplayName.decode(maid.name()),
                            layout.leftWidth - 16), layout.leftX + 8, y + 4,
                    maid.online() ? TEXT : MUTED, false);
            String stateKey = !maid.online()
                    ? "gui.maid_storage_manager_extension.terminal.offline"
                    : !maid.driverTask()
                    ? "gui.maid_storage_manager_extension.transport.driver_wrong_task"
                    : !maid.hasBroom()
                    ? "gui.maid_storage_manager_extension.transport.no_broom"
                    : maid.busy()
                    ? "gui.maid_storage_manager_extension.courier.phase." + maid.phase()
                    : "gui.maid_storage_manager_extension.terminal.ready";
            Component state = Component.translatable(stateKey);
            graphics.drawString(font, trim(state, layout.leftWidth - 16),
                    layout.leftX + 8, y + 14,
                    maid.busy() ? WARN : maid.online() && maid.driverTask()
                            && maid.hasBroom() ? GOOD : MUTED, false);
            y += 27;
        }
    }

    private void renderTransportPanel(GuiGraphics graphics, Layout layout) {
        MaidTransportSnapshot.Snapshot ride = transportSnapshot();
        Component name = ride.driverName().isBlank()
                ? Component.translatable("gui.maid_storage_manager_extension.transport.no_driver")
                : LogisticsDisplayName.decode(ride.driverName());
        graphics.drawString(font, trim(name, layout.rightWidth - 12),
                layout.rightX + 6, layout.contentY + 8, TEXT, false);
        Component state = Component.translatable(
                "gui.maid_storage_manager_extension.transport.state."
                        + ride.state().name().toLowerCase(Locale.ROOT));
        int stateColor = ride.state() == MaidTransportSnapshot.State.READY ? GOOD
                : ride.active() ? WARN : ERROR;
        graphics.drawString(font, trim(state, layout.rightWidth - 12),
                layout.rightX + 6, layout.contentY + 21, stateColor, false);
        int y = layout.contentY + 39;
        if (!ride.reason().isBlank()) {
            for (var line : font.split(Component.translatable(ride.reason()),
                    layout.rightWidth - 12)) {
                graphics.drawString(font, line, layout.rightX + 6, y, ERROR, false);
                y += 10;
            }
        }
        BlockPos pickup = ride.active() ? ride.pickup() : selectedPickup;
        BlockPos destination = ride.active() ? ride.destination() : selectedDestination;
        y = layout.contentY + 122;
        graphics.drawString(font, pointText(
                        "gui.maid_storage_manager_extension.transport.pickup", pickup, true),
                layout.rightX + 6, y, pickup == null ? MUTED : GOOD, false);
        graphics.drawString(font, pointText(
                        "gui.maid_storage_manager_extension.transport.destination",
                        destination, false), layout.rightX + 6, y + 15,
                destination == null ? WARN : TEXT, false);
        y += 38;
        List<Component> notes = List.of(
                Component.translatable("gui.maid_storage_manager_extension.transport.unknown_ok"),
                Component.translatable("gui.maid_storage_manager_extension.transport.high_cruise"),
                Component.translatable("gui.maid_storage_manager_extension.transport.no_landing_handoff"));
        for (Component note : notes) {
            for (var line : font.split(note, layout.rightWidth - 12)) {
                if (y >= layout.contentBottom - 58) return;
                graphics.drawString(font, line, layout.rightX + 6, y, MUTED, false);
                y += 10;
            }
            y += 3;
        }
    }

    private Component pointText(String key, BlockPos point, boolean pickup) {
        if (point == null) return Component.translatable(pickup
                ? "gui.maid_storage_manager_extension.transport.pickup_default"
                : "gui.maid_storage_manager_extension.transport.destination_unset");
        return Component.translatable(key, point.getX(), point.getZ());
    }

    private void renderTransportFooter(GuiGraphics graphics) {
        MaidTransportSnapshot.Snapshot ride = transportSnapshot();
        Component help = Component.translatable(ride.active()
                ? "gui.maid_storage_manager_extension.transport.end_help"
                : "gui.maid_storage_manager_extension.transport.map_help");
        graphics.drawString(font, trim(help, Math.max(10, width - 274)),
                268, height - 23, ride.active() ? WARN : MUTED, false);
    }

    private void renderNetworkWarehouse(GuiGraphics graphics, int mouseX, int mouseY) {
        Layout layout = layout();
        renderAccountSidebar(graphics, layout);
        if (layout.mapWidth > 0) renderMap(graphics, layout, mouseX, mouseY);
        renderWarehousePanel(graphics, layout, mouseX, mouseY);
        renderFooterStatus(graphics);
    }

    private void renderAccountSidebar(GuiGraphics graphics, Layout layout) {
        TerminalAccountSnapshot.Snapshot account = accountSnapshot();
        graphics.drawString(font, Component.translatable(
                        "gui.maid_storage_manager_extension.terminal.registered_mailboxes"),
                layout.leftX + 6, layout.contentY + 7, TEXT, false);
        int y = layout.contentY + 21;
        if (account.mailboxes().isEmpty()) {
            graphics.drawString(font, Component.translatable(
                            "gui.maid_storage_manager_extension.terminal.no_mailboxes"),
                    layout.leftX + 7, y + 8, MUTED, false);
            return;
        }
        if (expandedMailbox >= account.mailboxes().size()) expandedMailbox = -1;
        for (int i = 0; i < account.mailboxes().size(); i++) {
            if (y + 31 > layout.contentBottom - 4) break;
            TerminalAccountSnapshot.Mailbox mailbox = account.mailboxes().get(i);
            boolean expanded = expandedMailbox == i;
            graphics.fill(layout.leftX + 4, y, layout.leftX + layout.leftWidth - 4, y + 30,
                    expanded ? PANEL_ALT : PANEL_BG);
            outline(graphics, layout.leftX + 4, y, layout.leftWidth - 8, 30,
                    expanded ? BORDER_ACTIVE : mailbox.valid() ? BORDER : ERROR);
            Component name = mailbox.warehouseName().isBlank()
                    ? Component.literal(mailbox.warehouse() == null ? "?"
                    : mailbox.warehouse().toString().substring(0, 8))
                    : Component.literal(mailbox.warehouseName());
            graphics.drawString(font, trim(Component.literal(expanded ? "▾ " : "▸ ").append(name),
                            layout.leftWidth - 16), layout.leftX + 8, y + 4,
                    mailbox.valid() ? TEXT : ERROR, false);
            String dutyKey = mailbox.warehouseOnDuty()
                    ? "gui.maid_storage_manager_extension.terminal.warehouse_on_duty"
                    : mailbox.warehouseOnline()
                    ? "gui.maid_storage_manager_extension.terminal.warehouse_not_on_duty"
                    : "gui.maid_storage_manager_extension.terminal.warehouse_offline";
            graphics.drawString(font, trim(Component.translatable(dutyKey), layout.leftWidth - 16),
                    layout.leftX + 8, y + 17,
                    mailbox.warehouseOnDuty() ? GOOD : mailbox.warehouseOnline() ? WARN : MUTED,
                    false);
            y += 33;
            if (!expanded) continue;
            for (TerminalAccountSnapshot.WarehouseManager manager : mailbox.managers()) {
                if (y + 25 > layout.contentBottom - 4) break;
                graphics.fill(layout.leftX + 10, y,
                        layout.leftX + layout.leftWidth - 4, y + 23, 0xE82A2420);
                outline(graphics, layout.leftX + 10, y, layout.leftWidth - 14, 23,
                        0xFF665747);
                graphics.drawString(font, trim(LogisticsDisplayName.decode(manager.name()),
                                layout.leftWidth - 28), layout.leftX + 14, y + 3,
                        "offline".equals(manager.status()) ? MUTED : TEXT, false);
                Component managerState = Component.translatable(
                        "gui.maid_storage_manager_extension.terminal.manager_status."
                                + manager.status());
                if (!manager.detail().isBlank()) {
                    managerState = managerState.copy().append(" · ")
                            .append(Component.translatable(manager.detail()));
                }
                graphics.drawString(font, trim(managerState, layout.leftWidth - 28),
                        layout.leftX + 14, y + 13,
                        "failed".equals(manager.status()) ? ERROR
                                : "completed".equals(manager.status())
                                || "on_duty".equals(manager.status()) ? GOOD
                                : "offline".equals(manager.status())
                                || "off_duty".equals(manager.status()) ? MUTED : WARN,
                        false);
                y += 25;
            }
            for (TerminalAccountSnapshot.Maid maid : account.maids()) {
                if (y + 31 > layout.contentBottom - 4) break;
                boolean selected = maid.id().equals(account.selectedCourier());
                graphics.fill(layout.leftX + 10, y,
                        layout.leftX + layout.leftWidth - 4, y + 29,
                        selected ? PANEL_ALT : 0xE8221E1B);
                outline(graphics, layout.leftX + 10, y, layout.leftWidth - 14, 29,
                        selected ? BORDER_ACTIVE : 0xFF51483E);
                graphics.drawString(font, trim(LogisticsDisplayName.decode(maid.name()),
                                layout.leftWidth - 28), layout.leftX + 14, y + 4,
                        maid.online() ? TEXT : MUTED, false);
                Component details = Component.translatable(maid.busy()
                                ? "gui.maid_storage_manager_extension.courier.phase." + maid.phase()
                                : maid.online()
                                ? "gui.maid_storage_manager_extension.terminal.ready"
                                : "gui.maid_storage_manager_extension.terminal.offline")
                        .append(" · ").append(transportModeName(maid.transportMode()));
                graphics.drawString(font, trim(details, layout.leftWidth - 28),
                        layout.leftX + 14, y + 16,
                        maid.busy() ? WARN : maid.online() ? GOOD : MUTED, false);
                y += 31;
            }
        }
    }

    private Component transportModeName(String mode) {
        String normalized = mode == null || mode.isBlank() ? "none" : mode;
        return Component.translatable(
                "gui.maid_storage_manager_extension.transport_mode." + normalized);
    }

    private void renderStations(GuiGraphics graphics, Layout layout, int mouseX, int mouseY) {
        graphics.drawString(font, Component.translatable(
                "gui.maid_storage_manager_extension.compass.stations"),
                layout.leftX + 6, layout.contentY + 7, TEXT, false);
        LogisticsSnapshot.Snapshot snapshot = logisticsSnapshot();
        int y = layout.contentY + 25;
        int shown = Math.min(snapshot.stations().size(),
                Math.max(1, (layout.contentHeight - 50) / 30));
        for (int i = 0; i < shown; i++) {
            LogisticsSnapshot.Station station = snapshot.stations().get(i);
            int rowY = y + i * 30;
            boolean hovered = inside(mouseX, mouseY, layout.leftX + 4, rowY,
                    layout.leftWidth - 8, 26);
            int border = station.selected() ? BORDER_ACTIVE : hovered ? BORDER : 0xFF4D443A;
            graphics.fill(layout.leftX + 4, rowY, layout.leftX + layout.leftWidth - 4,
                    rowY + 26, hovered ? PANEL_ALT : PANEL_BG);
            outline(graphics, layout.leftX + 4, rowY, layout.leftWidth - 8, 26, border);
            Component name = station.name().isBlank()
                    ? Component.literal(station.warehouse().toString().substring(0, 8))
                    : LogisticsDisplayName.decode(station.name());
            graphics.drawString(font, trim(name, layout.leftWidth - 28),
                    layout.leftX + 9, rowY + 5, station.valid() ? TEXT : ERROR, false);
            graphics.drawString(font, Component.translatable(station.selected()
                            ? "gui.maid_storage_manager_extension.compass.station_selected"
                            : "gui.maid_storage_manager_extension.compass.station_available"),
                    layout.leftX + 9, rowY + 16,
                    station.valid() ? GOOD : ERROR, false);
        }
        if (snapshot.stations().isEmpty()) {
            graphics.drawCenteredString(font, Component.translatable(
                            "gui.maid_storage_manager_extension.logistics_tracker.no_stations"),
                    layout.leftX + layout.leftWidth / 2, y + 28, MUTED);
        }
    }

    private void renderMap(GuiGraphics graphics, Layout layout, int mouseX, int mouseY) {
        int x = layout.mapX + 5;
        int y = layout.contentY + 22;
        int w = layout.mapWidth - 10;
        int h = layout.contentHeight - 28;
        graphics.drawString(font, Component.translatable(
                "gui.maid_storage_manager_extension.compass.world_map"),
                layout.mapX + 6, layout.contentY + 7, TEXT, false);
        graphics.fill(x, y, x + w, y + h, MAP_BG);
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            graphics.drawCenteredString(font, Component.translatable(
                            "gui.maid_storage_manager_extension.compass.map_unavailable"),
                    x + w / 2, y + h / 2, MUTED);
            return;
        }
        if (!mapCenterInitialized) centerMapOnPlayer();
        double blocksPerPixel = blocksPerScreenPixel();
        TerrainMapTexture.View terrainView = terrainMap.update(
                minecraft, mapCenterX, mapCenterZ,
                blocksPerPixel, w, h);
        renderedMapView = terrainView;
        if (terrainView.texture() != null) {
            graphics.blit(terrainView.texture(), x, y, w, h, 0.0F, 0.0F,
                    TerrainMapTexture.TEXTURE_SIZE, TerrainMapTexture.TEXTURE_SIZE,
                    TerrainMapTexture.TEXTURE_SIZE, TerrainMapTexture.TEXTURE_SIZE);
        }
        if (terrainView.knownPixels() == 0) {
            graphics.drawCenteredString(font, Component.translatable(
                            "gui.maid_storage_manager_extension.compass.terrain_unloaded"),
                    x + w / 2, y + h / 2, MUTED);
        }

        NetworkWarehouseSnapshot.Snapshot snapshot = warehouseSnapshot();
        List<MapMarker> markers = mapMarkers(minecraft, snapshot);
        if (serviceTab == ServiceTab.MAID_TRANSPORT) {
            drawTransportRoute(graphics, markers, minecraft.level.dimension().location(),
                    x, y, w, h, terrainView);
        }
        MapMarker hovered = null;
        for (MapMarker marker : markers) {
            Projected point = project(marker, minecraft.level.dimension().location(),
                    x, y, w, h, terrainView.centerX(), terrainView.centerZ(),
                    terrainView.blocksPerScreenPixel());
            if (point == null) continue;
            if (marker.kind() == MarkerKind.WAREHOUSE) {
                drawWarehouse(graphics, point, marker.selected(), marker.valid());
            } else if (marker.kind() == MarkerKind.PICKUP
                    || marker.kind() == MarkerKind.DESTINATION) {
                drawTransportPoint(graphics, point,
                        marker.kind() == MarkerKind.PICKUP ? "起" : "终",
                        marker.kind() == MarkerKind.PICKUP ? GOOD : ACCENT);
            } else {
                drawDot(graphics, point, marker.color(), marker.selected());
            }
            if (inside(mouseX, mouseY, point.x - 6, point.y - 6, 13, 13)) hovered = marker;
        }
        renderMapOverlay(graphics, hovered, mouseX, mouseY,
                x, y, w, h, terrainView.centerX(), terrainView.centerZ(),
                terrainView.blocksPerScreenPixel());
        if (serviceTab == ServiceTab.MAID_TRANSPORT) {
            Component legend = Component.translatable(
                    "gui.maid_storage_manager_extension.transport.route_legend");
            int legendWidth = font.width(legend) + 8;
            graphics.fill(x + 4, y + 4, x + 4 + legendWidth, y + 17, 0xB0101212);
            graphics.drawString(font, legend, x + 8, y + 7, TEXT, false);
        }
    }

    private void drawTransportRoute(GuiGraphics graphics, List<MapMarker> markers,
                                    ResourceLocation dimension, int x, int y,
                                    int width, int height, TerrainMapTexture.View view) {
        MapMarker pickup = markers.stream().filter(value -> value.kind() == MarkerKind.PICKUP)
                .findFirst().orElse(null);
        MapMarker destination = markers.stream()
                .filter(value -> value.kind() == MarkerKind.DESTINATION)
                .findFirst().orElse(null);
        if (pickup == null || destination == null) return;
        Projected from = project(pickup, dimension, x, y, width, height,
                view.centerX(), view.centerZ(), view.blocksPerScreenPixel());
        Projected to = project(destination, dimension, x, y, width, height,
                view.centerX(), view.centerZ(), view.blocksPerScreenPixel());
        if (from == null || to == null) return;
        int dx = to.x - from.x;
        int dy = to.y - from.y;
        int steps = Math.max(Math.abs(dx), Math.abs(dy));
        if (steps == 0) return;
        for (int i = 0; i <= steps; i++) {
            if ((i / 4 & 1) != 0) continue;
            int px = from.x + Math.round(dx * (i / (float) steps));
            int py = from.y + Math.round(dy * (i / (float) steps));
            graphics.fill(px, py, px + 2, py + 2, ACCENT);
        }
    }

    private void renderWarehousePanel(GuiGraphics graphics, Layout layout,
                                      int mouseX, int mouseY) {
        NetworkWarehouseSnapshot.Snapshot snapshot = warehouseSnapshot();
        Component name = snapshot.warehouseName().isBlank()
                ? Component.translatable("gui.maid_storage_manager_extension.compass.no_warehouse")
                : LogisticsDisplayName.decode(snapshot.warehouseName());
        graphics.drawString(font, trim(name, layout.rightWidth - 12),
                layout.rightX + 6, layout.contentY + 7, TEXT, false);
        int stateColor = snapshot.inventoryState() == NetworkWarehouseSnapshot.InventoryState.FRESH
                ? GOOD : snapshot.inventoryState() == NetworkWarehouseSnapshot.InventoryState.STALE
                ? WARN : ERROR;
        graphics.drawString(font, inventoryState(snapshot), layout.rightX + 6,
                layout.contentY + 17, stateColor, false);
        if (warehouseMode == WarehouseMode.DEPOSIT) {
            renderDepositHelp(graphics, layout, snapshot);
            return;
        }

        List<NetworkWarehouseSnapshot.InventoryEntry> filtered = filteredInventory(snapshot);
        int columns = Math.max(3, (layout.rightWidth - 12) / SLOT);
        int gridY = layout.contentY + 70;
        int gridBottom = layout.contentBottom - 70;
        int rows = Math.max(1, (gridBottom - gridY) / SLOT);
        int maxScroll = Math.max(0, (filtered.size() + columns - 1) / columns - rows);
        inventoryScroll = Mth.clamp(inventoryScroll, 0, maxScroll);
        int start = inventoryScroll * columns;
        int end = Math.min(filtered.size(), start + columns * rows);
        ItemStack hovered = ItemStack.EMPTY;
        for (int index = start; index < end; index++) {
            int local = index - start;
            int slotX = layout.rightX + 6 + local % columns * SLOT;
            int slotY = gridY + local / columns * SLOT;
            NetworkWarehouseSnapshot.InventoryEntry entry = filtered.get(index);
            graphics.fill(slotX, slotY, slotX + 20, slotY + 20, 0xFF171514);
            outline(graphics, slotX, slotY, 20, 20, 0xFF51483E);
            graphics.renderItem(entry.prototype(), slotX + 2, slotY + 2);
            String selectedKey = itemKey(entry.prototype());
            int selected = selectedAmounts.getOrDefault(selectedKey, 0);
            if (selected > 0) {
                graphics.fill(slotX + 1, slotY + 1, slotX + 19, slotY + 19, 0x664F8D78);
                graphics.drawString(font, Integer.toString(selected), slotX + 3, slotY + 11,
                        TEXT, true);
            } else {
                String amount = compactAmount(entry.available());
                graphics.drawString(font, amount, slotX + 19 - font.width(amount),
                        slotY + 11, TEXT, true);
            }
            if (inside(mouseX, mouseY, slotX, slotY, 20, 20)) hovered = entry.prototype();
        }
        if (filtered.isEmpty()) {
            graphics.drawCenteredString(font, Component.translatable(
                            "gui.maid_storage_manager_extension.compass.no_inventory"),
                    layout.rightX + layout.rightWidth / 2, gridY + 24, MUTED);
        }
        graphics.drawString(font, Component.translatable(
                        "gui.maid_storage_manager_extension.compass.order_lines",
                        selectedAmounts.size(), NetworkWarehouseActionPacket.MAX_REQUEST_LINES),
                layout.rightX + 6, layout.contentBottom - 78, MUTED, false);
        if (!hovered.isEmpty()) graphics.renderTooltip(font, hovered, mouseX, mouseY);
    }

    private void renderDepositHelp(GuiGraphics graphics, Layout layout,
                                   NetworkWarehouseSnapshot.Snapshot snapshot) {
        int y = layout.contentY + 55;
        List<Component> lines = List.of(
                Component.translatable("gui.maid_storage_manager_extension.compass.deposit_help_1"),
                Component.translatable("gui.maid_storage_manager_extension.compass.deposit_help_2"),
                Component.translatable(snapshot.enderPocketAvailable()
                        ? "gui.maid_storage_manager_extension.compass.ender_ready"
                        : snapshot.nearbyHandoffAvailable()
                        ? "gui.maid_storage_manager_extension.compass.nearby_ready"
                        : "gui.maid_storage_manager_extension.compass.manual_handoff"));
        for (Component line : lines) {
            for (var sequence : font.split(line, layout.rightWidth - 14)) {
                graphics.drawString(font, sequence, layout.rightX + 7, y, MUTED, false);
                y += 11;
            }
            y += 4;
        }
    }

    private void renderFooterStatus(GuiGraphics graphics) {
        LogisticsSnapshot.Snapshot logistics = logisticsSnapshot();
        NetworkWarehouseSnapshot.Snapshot warehouse = warehouseSnapshot();
        int footerX = 268;
        int availableWidth = width - footerX - 8;
        if (availableWidth <= 20) return;
        Component courier = logistics.courierName().isBlank()
                ? Component.translatable("gui.maid_storage_manager_extension.logistics_tracker.no_data")
                : LogisticsDisplayName.decode(logistics.courierName());
        if (availableWidth < 162) {
            graphics.drawString(font, trim(courier, availableWidth),
                    footerX, height - 23, logistics.online() ? GOOD : ERROR, false);
            return;
        }
        int segment = availableWidth / 3;
        graphics.drawString(font, trim(courier, segment - 5),
                footerX, height - 23, logistics.online() ? GOOD : ERROR, false);
        Component status = Component.translatable(
                "gui.maid_storage_manager_extension.courier.phase."
                        + logistics.phase().toLowerCase(Locale.ROOT));
        graphics.drawString(font, trim(status, segment - 5),
                footerX + segment, height - 23, TEXT, false);
        Component link = Component.translatable(warehouse.enderPocketAvailable()
                ? "gui.maid_storage_manager_extension.compass.ender_ready"
                : "gui.maid_storage_manager_extension.compass.ender_missing");
        graphics.drawString(font, trim(link, segment - 5), footerX + segment * 2, height - 23,
                warehouse.enderPocketAvailable() ? GOOD : WARN, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!accountSnapshot().authenticated()) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        if (settingsPage) {
            return handleSettingsClick(mouseX, mouseY, button)
                    || super.mouseClicked(mouseX, mouseY, button);
        }
        if (serviceTab == ServiceTab.MAID_TRANSPORT) {
            Layout layout = layout();
            TerminalAccountSnapshot.Snapshot account = accountSnapshot();
            int rowY = layout.contentY + 22;
            int rows = Math.max(1, (layout.contentHeight - 29) / 27);
            for (int i = 0; i < Math.min(rows, account.maids().size()); i++) {
                if (inside(mouseX, mouseY, layout.leftX + 4, rowY,
                        layout.leftWidth - 8, 24)) {
                    ExtensionNetwork.CHANNEL.sendToServer(TerminalAccountActionPacket.select(
                            menu.terminal(), account.maids().get(i).id(), true));
                    return true;
                }
                rowY += 27;
            }
            if (button == 2 && insideTransportMap(mouseX, mouseY, layout)) {
                centerMapOnPlayer();
                return true;
            }
            if (button == 0 && !transportSnapshot().active()
                    && insideTransportMap(mouseX, mouseY, layout)) {
                transportMapPressed = true;
                transportMapDragged = false;
                transportMapPressX = mouseX;
                transportMapPressY = mouseY;
                return true;
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }
        if (!isBound() || serviceTab != ServiceTab.NETWORK_WAREHOUSE) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        Layout layout = layout();
        if (button == 2 && layout.mapWidth > 0
                && inside(mouseX, mouseY, layout.mapX, layout.contentY,
                layout.mapWidth, layout.contentHeight)) {
            centerMapOnPlayer();
            return true;
        }
        if (button == 1 && requestWarehouseScanFromMap(mouseX, mouseY, layout)) return true;
        if (button == 0 && selectWarehouseFromMap(mouseX, mouseY, layout)) return true;
        TerminalAccountSnapshot.Snapshot account = accountSnapshot();
        int rowY = layout.contentY + 21;
        for (int i = 0; i < account.mailboxes().size(); i++) {
            if (rowY + 31 > layout.contentBottom - 4) break;
            TerminalAccountSnapshot.Mailbox mailbox = account.mailboxes().get(i);
            if (inside(mouseX, mouseY, layout.leftX + 4, rowY,
                    layout.leftWidth - 8, 30)) {
                if (button == 1) {
                    requestWarehouseScan(mailbox);
                } else if (button == 0) {
                    activateMailbox(mailbox);
                    expandedMailbox = expandedMailbox == i ? -1 : i;
                }
                return true;
            }
            rowY += 33;
            if (expandedMailbox != i) continue;
            for (TerminalAccountSnapshot.WarehouseManager ignored : mailbox.managers()) {
                if (rowY + 25 > layout.contentBottom - 4) break;
                if (inside(mouseX, mouseY, layout.leftX + 10, rowY,
                        layout.leftWidth - 14, 23)) return true;
                rowY += 25;
            }
            for (TerminalAccountSnapshot.Maid maid : account.maids()) {
                if (rowY + 31 > layout.contentBottom - 4) break;
                if (inside(mouseX, mouseY, layout.leftX + 10, rowY,
                        layout.leftWidth - 14, 29)) {
                    if (button == 0) {
                        ExtensionNetwork.CHANNEL.sendToServer(TerminalAccountActionPacket.select(
                                menu.terminal(), maid.id(), false));
                        activateMailbox(mailbox);
                    }
                    return true;
                }
                rowY += 31;
            }
        }
        if (warehouseMode == WarehouseMode.WITHDRAW) {
            NetworkWarehouseSnapshot.Snapshot snapshot = warehouseSnapshot();
            List<NetworkWarehouseSnapshot.InventoryEntry> filtered = filteredInventory(snapshot);
            int columns = Math.max(3, (layout.rightWidth - 12) / SLOT);
            int gridY = layout.contentY + 70;
            int gridBottom = layout.contentBottom - 70;
            int rows = Math.max(1, (gridBottom - gridY) / SLOT);
            int start = inventoryScroll * columns;
            int end = Math.min(filtered.size(), start + columns * rows);
            for (int index = start; index < end; index++) {
                int local = index - start;
                int slotX = layout.rightX + 6 + local % columns * SLOT;
                int slotY = gridY + local / columns * SLOT;
                if (!inside(mouseX, mouseY, slotX, slotY, 20, 20)) continue;
                updateSelection(filtered.get(index), button);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean handleSettingsClick(double mouseX, double mouseY, int button) {
        if (button != 0) return false;
        TerminalAccountSnapshot.Snapshot account = accountSnapshot();
        SettingsLayout settings = settingsLayout();
        int mailboxRows = Math.min(settings.maxRows, account.mailboxes().size());
        for (int i = 0; i < mailboxRows; i++) {
            int rowY = settings.rowsY + i * settings.rowStride;
            if (!inside(mouseX, mouseY, settings.leftX + settings.columnWidth - 51,
                    rowY + 3, 47, 20)) continue;
            TerminalAccountSnapshot.Mailbox mailbox = account.mailboxes().get(i);
            ExtensionNetwork.CHANNEL.sendToServer(new TerminalMailboxActionPacket(
                    TerminalMailboxActionPacket.Action.UNREGISTER, menu.terminal(),
                    mailbox.dimension(), mailbox.position()));
            return true;
        }
        int maidRows = Math.min(settings.maxRows, account.maids().size());
        for (int i = 0; i < maidRows; i++) {
            int rowY = settings.rowsY + i * settings.rowStride;
            if (!inside(mouseX, mouseY, settings.rightX + settings.columnWidth - 51,
                    rowY + 3, 47, 20)) continue;
            ExtensionNetwork.CHANNEL.sendToServer(new TerminalAccountActionPacket(
                    TerminalAccountActionPacket.Action.UNREGISTER_MAID, menu.terminal(),
                    "", "", account.maids().get(i).id()));
            return true;
        }
        return false;
    }

    private void requestWarehouseScan(TerminalAccountSnapshot.Mailbox mailbox) {
        if (mailbox == null) return;
        ExtensionNetwork.CHANNEL.sendToServer(new TerminalMailboxActionPacket(
                TerminalMailboxActionPacket.Action.REQUEST_SCAN, menu.terminal(),
                mailbox.dimension(), mailbox.position()));
    }

    private void activateMailbox(TerminalAccountSnapshot.Mailbox mailbox) {
        if (mailbox == null) return;
        ExtensionNetwork.CHANNEL.sendToServer(new TerminalMailboxActionPacket(
                TerminalMailboxActionPacket.Action.ACTIVATE, menu.terminal(),
                mailbox.dimension(), mailbox.position()));
    }

    private boolean requestWarehouseScanFromMap(double mouseX, double mouseY, Layout layout) {
        if (layout.mapWidth <= 0 || renderedMapView == null) return false;
        int x = layout.mapX + 5;
        int y = layout.contentY + 22;
        int width = layout.mapWidth - 10;
        int height = layout.contentHeight - 28;
        if (!inside(mouseX, mouseY, x, y, width, height)) return false;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return false;
        for (MapMarker marker : mapMarkers(minecraft, warehouseSnapshot())) {
            if (marker.kind() != MarkerKind.WAREHOUSE || marker.warehouse() == null) continue;
            Projected point = project(marker, minecraft.level.dimension().location(),
                    x, y, width, height, renderedMapView.centerX(), renderedMapView.centerZ(),
                    renderedMapView.blocksPerScreenPixel());
            if (point == null || !inside(mouseX, mouseY, point.x - 7, point.y - 7, 15, 15)) continue;
            TerminalAccountSnapshot.Mailbox mailbox = accountSnapshot().mailboxes().stream()
                    .filter(value -> marker.warehouse().equals(value.warehouse()))
                    .findFirst().orElse(null);
            if (mailbox == null) return false;
            requestWarehouseScan(mailbox);
            return true;
        }
        return false;
    }

    private void updateSelection(NetworkWarehouseSnapshot.InventoryEntry entry, int button) {
        String key = itemKey(entry.prototype());
        int current = selectedAmounts.getOrDefault(key, 0);
        int next;
        if (button == 1) {
            next = Math.max(0, current - (hasShiftDown() ? entry.prototype().getMaxStackSize() : 1));
        } else if (button == 0) {
            if (current == 0 && selectedAmounts.size()
                    >= NetworkWarehouseActionPacket.MAX_REQUEST_LINES) return;
            int step = hasShiftDown() ? entry.prototype().getMaxStackSize() : 1;
            next = Math.min(entry.available(), current + step);
        } else {
            return;
        }
        if (next <= 0) {
            selectedAmounts.remove(key);
            selectedStacks.remove(key);
        } else {
            selectedAmounts.put(key, next);
            selectedStacks.put(key, entry.prototype().copyWithCount(1));
        }
        staleConfirmed = false;
        updateWidgetState();
    }

    private boolean selectWarehouseFromMap(double mouseX, double mouseY, Layout layout) {
        if (layout.mapWidth <= 0 || renderedMapView == null) return false;
        int x = layout.mapX + 5;
        int y = layout.contentY + 22;
        int width = layout.mapWidth - 10;
        int height = layout.contentHeight - 28;
        if (!inside(mouseX, mouseY, x, y, width, height)) return false;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return false;
        for (MapMarker marker : mapMarkers(minecraft, warehouseSnapshot())) {
            if (marker.kind() != MarkerKind.WAREHOUSE || marker.warehouse() == null) continue;
            Projected point = project(marker, minecraft.level.dimension().location(),
                    x, y, width, height, renderedMapView.centerX(), renderedMapView.centerZ(),
                    renderedMapView.blocksPerScreenPixel());
            if (point != null && inside(mouseX, mouseY, point.x - 7, point.y - 7, 15, 15)) {
                selectWarehouse(marker.warehouse());
                return true;
            }
        }
        return false;
    }

    private void selectWarehouse(UUID warehouse) {
        if (warehouse == null) return;
        selectedAmounts.clear();
        selectedStacks.clear();
        inventoryScroll = 0;
        staleConfirmed = false;
        TerminalAccountSnapshot.Mailbox mailbox = accountSnapshot().mailboxes().stream()
                .filter(value -> warehouse.equals(value.warehouse()))
                .findFirst().orElse(null);
        if (mailbox != null) activateMailbox(mailbox);
        else ExtensionNetwork.CHANNEL.sendToServer(
                NetworkWarehouseActionPacket.select(
                        menu.terminal(), activeCourier(), warehouse, activeMailbox()));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        Layout layout = layout();
        int mapX = layout.mapX + 5;
        int mapY = layout.contentY + 22;
        int mapWidth = layout.mapWidth - 10;
        int mapHeight = layout.contentHeight - 28;
        if (layout.mapWidth > 0 && inside(mouseX, mouseY,
                mapX, mapY, mapWidth, mapHeight)) {
            if (!mapCenterInitialized) centerMapOnPlayer();
            double oldBlocksPerPixel = blocksPerScreenPixel();
            double offsetX = mouseX - (mapX + mapWidth / 2.0D);
            double offsetY = mouseY - (mapY + mapHeight / 2.0D);
            double worldUnderCursorX = mapCenterX + offsetX * oldBlocksPerPixel;
            double worldUnderCursorZ = mapCenterZ + offsetY * oldBlocksPerPixel;
            mapZoom = Mth.clamp(mapZoom * Math.pow(1.18D, delta), 0.25D, 4.0D);
            double newBlocksPerPixel = blocksPerScreenPixel();
            mapCenterX = worldUnderCursorX - offsetX * newBlocksPerPixel;
            mapCenterZ = worldUnderCursorZ - offsetY * newBlocksPerPixel;
            terrainMap.markDirty();
            return true;
        }
        if (inside(mouseX, mouseY, layout.rightX, layout.contentY,
                layout.rightWidth, layout.contentHeight)) {
            inventoryScroll = Math.max(0, inventoryScroll - (int) Math.signum(delta));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button,
                                double dragX, double dragY) {
        Layout layout = layout();
        if (serviceTab == ServiceTab.MAID_TRANSPORT && button == 0
                && transportMapPressed) {
            double totalX = mouseX - transportMapPressX;
            double totalY = mouseY - transportMapPressY;
            if (!transportMapDragged && totalX * totalX + totalY * totalY > 16.0D) {
                transportMapDragged = true;
            }
            if (transportMapDragged) {
                if (!mapCenterInitialized) centerMapOnPlayer();
                mapCenterX -= dragX * blocksPerScreenPixel();
                mapCenterZ -= dragY * blocksPerScreenPixel();
                terrainMap.markDirty();
            }
            return true;
        }
        if (button == 0 && layout.mapWidth > 0 && inside(mouseX, mouseY,
                layout.mapX + 5, layout.contentY + 22,
                layout.mapWidth - 10, layout.contentHeight - 28)) {
            if (!mapCenterInitialized) centerMapOnPlayer();
            mapCenterX -= dragX * blocksPerScreenPixel();
            mapCenterZ -= dragY * blocksPerScreenPixel();
            terrainMap.markDirty();
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (serviceTab == ServiceTab.MAID_TRANSPORT && button == 0
                && transportMapPressed) {
            boolean choose = !transportMapDragged
                    && insideTransportMap(mouseX, mouseY, layout());
            transportMapPressed = false;
            transportMapDragged = false;
            if (choose) {
                BlockPos point = transportPointAt(mouseX, mouseY, layout());
                if (point != null) {
                    if (transportPointMode == TransportPointMode.PICKUP) selectedPickup = point;
                    else selectedDestination = point;
                }
            }
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private boolean insideTransportMap(double mouseX, double mouseY, Layout layout) {
        return layout.mapWidth > 0 && inside(mouseX, mouseY,
                layout.mapX + 5, layout.contentY + 22,
                layout.mapWidth - 10, layout.contentHeight - 28);
    }

    private BlockPos transportPointAt(double mouseX, double mouseY, Layout layout) {
        if (renderedMapView == null || !insideTransportMap(mouseX, mouseY, layout)) return null;
        int mapX = layout.mapX + 5;
        int mapY = layout.contentY + 22;
        int mapWidth = layout.mapWidth - 10;
        int mapHeight = layout.contentHeight - 28;
        int worldX = Mth.floor(renderedMapView.centerX()
                + (mouseX - mapX - mapWidth / 2.0D)
                * renderedMapView.blocksPerScreenPixel());
        int worldZ = Mth.floor(renderedMapView.centerZ()
                + (mouseY - mapY - mapHeight / 2.0D)
                * renderedMapView.blocksPerScreenPixel());
        return new BlockPos(worldX, 0, worldZ);
    }

    @Override
    public void removed() {
        terrainMap.close();
        super.removed();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        updateWidgetState();
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTerminalNotice(graphics);
        renderTooltip(graphics, mouseX, mouseY);
    }

    private void renderTerminalNotice(GuiGraphics graphics) {
        TerminalNoticeClientData.Notice notice = TerminalNoticeClientData.get(menu.terminal());
        if (notice == null) return;
        Component text = Component.translatable(notice.translationKey());
        int textWidth = Math.min(font.width(text), Math.max(40, width - 36));
        int boxWidth = textWidth + 20;
        int x = (width - boxWidth) / 2;
        int y = Math.max(31, height - 62);
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 400.0F);
        graphics.fill(x, y, x + boxWidth, y + 22, 0xF0201C19);
        outline(graphics, x, y, boxWidth, 22, notice.success() ? GOOD : ERROR);
        graphics.drawCenteredString(font, trim(text, boxWidth - 12), width / 2,
                y + 7, notice.success() ? GOOD : ERROR);
        graphics.pose().popPose();
    }

    private List<NetworkWarehouseSnapshot.InventoryEntry> filteredInventory(
            NetworkWarehouseSnapshot.Snapshot snapshot) {
        String query = searchBox == null ? "" : searchBox.getValue().trim().toLowerCase(Locale.ROOT);
        if (query.isEmpty()) return snapshot.inventory();
        return snapshot.inventory().stream().filter(entry -> {
            ResourceLocation key = ForgeRegistries.ITEMS.getKey(entry.prototype().getItem());
            return entry.prototype().getHoverName().getString().toLowerCase(Locale.ROOT).contains(query)
                    || key != null && key.toString().toLowerCase(Locale.ROOT).contains(query);
        }).toList();
    }

    private Component inventoryState(NetworkWarehouseSnapshot.Snapshot snapshot) {
        return switch (snapshot.inventoryState()) {
            case FRESH -> Component.translatable(
                    "gui.maid_storage_manager_extension.compass.inventory_fresh",
                    Math.max(0L, snapshot.inventoryAge() / 24_000L));
            case STALE -> Component.translatable(
                    "gui.maid_storage_manager_extension.compass.inventory_stale",
                    Math.max(0L, snapshot.inventoryAge() / 24_000L));
            case UNAVAILABLE -> Component.translatable(
                    "gui.maid_storage_manager_extension.compass.inventory_unavailable");
        };
    }

    private List<MapMarker> mapMarkers(Minecraft minecraft,
                                       NetworkWarehouseSnapshot.Snapshot snapshot) {
        List<MapMarker> result = new ArrayList<>();
        ResourceLocation currentDimension = minecraft.level.dimension().location();
        result.add(new MapMarker(minecraft.player.getX(), minecraft.player.getZ(),
                currentDimension,
                Component.translatable("gui.maid_storage_manager_extension.compass.marker.self"),
                Component.literal(minecraft.player.getName().getString()), MAP_PLAYER,
                MarkerKind.DOT, null, false, true));

        LogisticsSnapshot.Snapshot logistics = logisticsSnapshot();
        for (TerminalAccountSnapshot.Maid maid : accountSnapshot().maids()) {
            if (!maid.hasPosition()) continue;
            result.add(new MapMarker(maid.position().getX() + 0.5D,
                    maid.position().getZ() + 0.5D, maid.dimension(),
                    Component.translatable("gui.maid_storage_manager_extension.compass.marker.maid"),
                    LogisticsDisplayName.decode(maid.name()), MAP_MAID, MarkerKind.DOT, null,
                    maid.id().equals(serviceTab == ServiceTab.MAID_TRANSPORT
                            ? accountSnapshot().selectedDriver() : activeCourier()), maid.online()));
        }
        for (LogisticsSnapshot.Station station : logistics.stations()) {
            if (!station.hasMapPosition()) continue;
            Component name = station.name().isBlank()
                    ? Component.literal(station.warehouse().toString().substring(0, 8))
                    : LogisticsDisplayName.decode(station.name());
            result.add(new MapMarker(station.position().getX() + 0.5D,
                    station.position().getZ() + 0.5D, station.dimension(),
                    Component.translatable(
                            "gui.maid_storage_manager_extension.compass.marker.warehouse"),
                    name, MAP_WAREHOUSE_WALL, MarkerKind.WAREHOUSE,
                    station.warehouse(), station.selected(), station.valid()));
        }

        for (AbstractClientPlayer player : minecraft.level.players()) {
            if (player.getUUID().equals(minecraft.player.getUUID())) continue;
            result.add(new MapMarker(player.getX(), player.getZ(), currentDimension,
                    Component.translatable(
                            "gui.maid_storage_manager_extension.compass.marker.friend"),
                    Component.literal(player.getName().getString()), MAP_FRIEND,
                    MarkerKind.DOT, null, false, true));
        }
        if (serviceTab == ServiceTab.MAID_TRANSPORT) {
            MaidTransportSnapshot.Snapshot ride = transportSnapshot();
            BlockPos pickup = ride.active() ? ride.pickup() : selectedPickup;
            BlockPos destination = ride.active() ? ride.destination() : selectedDestination;
            if (pickup != null) result.add(new MapMarker(pickup.getX() + 0.5D,
                    pickup.getZ() + 0.5D, currentDimension,
                    Component.translatable("gui.maid_storage_manager_extension.transport.pickup_marker"),
                    Component.literal(pickup.getX() + ", " + pickup.getZ()), GOOD,
                    MarkerKind.PICKUP, null, true, true));
            if (destination != null) result.add(new MapMarker(destination.getX() + 0.5D,
                    destination.getZ() + 0.5D, currentDimension,
                    Component.translatable("gui.maid_storage_manager_extension.transport.destination_marker"),
                    Component.literal(destination.getX() + ", " + destination.getZ()), ACCENT,
                    MarkerKind.DESTINATION, null, true, true));
        }
        return result;
    }

    private void addMaidMarker(List<MapMarker> markers,
                               NetworkWarehouseSnapshot.MapPoint point, Component name) {
        if (point == null || !point.available()) return;
        markers.add(new MapMarker(point.position().getX() + 0.5D,
                point.position().getZ() + 0.5D, point.dimension(),
                Component.translatable("gui.maid_storage_manager_extension.compass.marker.maid"),
                name, MAP_MAID, MarkerKind.DOT, null, false, true));
    }

    private Projected project(MapMarker marker, ResourceLocation currentDimension,
                              int x, int y, int width, int height,
                              double renderedCenterX, double renderedCenterZ,
                              double blocksPerPixel) {
        if (!currentDimension.equals(marker.dimension())) return null;
        int px = (int) Math.round(x + width / 2.0D
                + (marker.worldX() - renderedCenterX) / blocksPerPixel);
        int py = (int) Math.round(y + height / 2.0D
                + (marker.worldZ() - renderedCenterZ) / blocksPerPixel);
        if (px < x + 5 || px >= x + width - 5 || py < y + 5 || py >= y + height - 5) {
            return null;
        }
        return new Projected(px, py);
    }

    private static void drawDot(GuiGraphics graphics, Projected point, int color,
                                boolean selected) {
        if (selected) {
            graphics.fill(point.x - 6, point.y - 1, point.x + 7, point.y + 2, BORDER_ACTIVE);
            graphics.fill(point.x - 1, point.y - 6, point.x + 2, point.y + 7, BORDER_ACTIVE);
        }
        graphics.fill(point.x - 4, point.y - 2, point.x + 5, point.y + 3, 0xDD111111);
        graphics.fill(point.x - 2, point.y - 4, point.x + 3, point.y + 5, 0xDD111111);
        graphics.fill(point.x - 3, point.y - 2, point.x + 4, point.y + 3, color);
        graphics.fill(point.x - 2, point.y - 3, point.x + 3, point.y + 4, color);
    }

    private static void drawWarehouse(GuiGraphics graphics, Projected point,
                                      boolean selected, boolean valid) {
        int outline = selected ? BORDER_ACTIVE : valid ? 0xFF2B211B : ERROR;
        graphics.fill(point.x - 1, point.y - 6, point.x + 2, point.y - 5, outline);
        graphics.fill(point.x - 3, point.y - 5, point.x + 4, point.y - 4, outline);
        graphics.fill(point.x - 5, point.y - 4, point.x + 6, point.y - 2, outline);
        graphics.fill(point.x - 4, point.y - 2, point.x + 5, point.y + 6, outline);
        graphics.fill(point.x - 3, point.y - 4, point.x + 4, point.y - 2,
                valid ? MAP_WAREHOUSE_ROOF : ERROR);
        graphics.fill(point.x - 3, point.y - 2, point.x + 4, point.y + 5,
                valid ? MAP_WAREHOUSE_WALL : 0xFF7B5757);
        graphics.fill(point.x, point.y + 1, point.x + 2, point.y + 5, 0xFF5A3928);
    }

    private void drawTransportPoint(GuiGraphics graphics, Projected point,
                                    String label, int color) {
        graphics.fill(point.x - 7, point.y - 7, point.x + 8, point.y + 8, 0xDD111111);
        graphics.fill(point.x - 6, point.y - 6, point.x + 7, point.y + 7, color);
        graphics.drawCenteredString(font, label, point.x, point.y - 4, 0xFF111111);
    }

    private void renderMapOverlay(GuiGraphics graphics, MapMarker hovered,
                                  int mouseX, int mouseY,
                                  int x, int y, int width, int height,
                                  double renderedCenterX, double renderedCenterZ,
                                  double blocksPerPixel) {
        if (width >= 245) {
            graphics.fill(x + 4, y + height - 18, x + 244,
                    y + height - 4, 0xB0101212);
            int legendX = x + 9;
            legendX = legendEntry(graphics, legendX, y + height - 14, MAP_PLAYER,
                    Component.translatable("gui.maid_storage_manager_extension.compass.legend.self"));
            legendX = legendEntry(graphics, legendX + 8, y + height - 14, MAP_MAID,
                    Component.translatable("gui.maid_storage_manager_extension.compass.legend.maid"));
            legendX = legendEntry(graphics, legendX + 8, y + height - 14, MAP_FRIEND,
                    Component.translatable("gui.maid_storage_manager_extension.compass.legend.friend"));
            warehouseLegendEntry(graphics, legendX + 8, y + height - 14,
                    Component.translatable(
                            "gui.maid_storage_manager_extension.compass.legend.warehouse"));
        } else {
            int legendWidth = Math.min(width - 8, 96);
            graphics.fill(x + 4, y + height - 48, x + 4 + legendWidth,
                    y + height - 4, 0xB0101212);
            legendEntry(graphics, x + 9, y + height - 42, MAP_PLAYER,
                    Component.translatable("gui.maid_storage_manager_extension.compass.legend.self"));
            legendEntry(graphics, x + 9, y + height - 32, MAP_MAID,
                    Component.translatable("gui.maid_storage_manager_extension.compass.legend.maid"));
            legendEntry(graphics, x + 9, y + height - 22, MAP_FRIEND,
                    Component.translatable("gui.maid_storage_manager_extension.compass.legend.friend"));
            warehouseLegendEntry(graphics, x + 6, y + height - 12,
                    Component.translatable(
                            "gui.maid_storage_manager_extension.compass.legend.warehouse"));
        }

        int cursorWorldX = (int) Math.floor(renderedCenterX
                + (mouseX - x - width / 2.0D) * blocksPerPixel);
        int cursorWorldZ = (int) Math.floor(renderedCenterZ
                + (mouseY - y - height / 2.0D) * blocksPerPixel);
        long renderedZoom = Math.round(BASE_BLOCKS_PER_SCREEN_PIXEL
                / blocksPerPixel * 100.0D);
        Component coordinates = trim(Component.translatable(
                "gui.maid_storage_manager_extension.compass.map_coordinates",
                cursorWorldX, cursorWorldZ, renderedZoom), width - 12);
        int coordinateWidth = font.width(coordinates) + 8;
        graphics.fill(x + width - coordinateWidth - 4, y + 4,
                x + width - 4, y + 17, 0xB0101212);
        graphics.drawString(font, coordinates, x + width - coordinateWidth,
                y + 7, TEXT, false);

        if (hovered != null) {
            Component detail = Component.translatable(
                    "gui.maid_storage_manager_extension.compass.marker.tooltip",
                    hovered.role(), hovered.name(), (int) Math.floor(hovered.worldX()),
                    (int) Math.floor(hovered.worldZ()));
            int tooltipWidth = Math.min(width - 12, font.width(detail) + 8);
            int tooltipX = Mth.clamp(mouseX + 8,
                    x + 4, x + width - tooltipWidth - 4);
            int tooltipY = Mth.clamp(mouseY + 8,
                    y + 4, y + height - 18);
            graphics.fill(tooltipX, tooltipY, tooltipX + tooltipWidth,
                    tooltipY + 13, 0xE0101212);
            graphics.drawString(font, trim(detail, tooltipWidth - 6),
                    tooltipX + 3, tooltipY + 3, TEXT, false);
        }
    }

    private int legendEntry(GuiGraphics graphics, int x, int y,
                            int color, Component label) {
        graphics.fill(x, y - 1, x + 5, y + 4, 0xDD111111);
        graphics.fill(x + 1, y, x + 4, y + 3, color);
        graphics.drawString(font, label, x + 7, y - 3, TEXT, false);
        return x + 7 + font.width(label);
    }

    private int warehouseLegendEntry(GuiGraphics graphics, int x, int y, Component label) {
        drawWarehouse(graphics, new Projected(x + 3, y + 1), false, true);
        graphics.drawString(font, label, x + 10, y - 3, TEXT, false);
        return x + 10 + font.width(label);
    }

    private double blocksPerScreenPixel() {
        return BASE_BLOCKS_PER_SCREEN_PIXEL / mapZoom;
    }

    private void centerMapOnPlayer() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;
        mapCenterX = minecraft.player.getX();
        mapCenterZ = minecraft.player.getZ();
        mapCenterInitialized = true;
        terrainMap.markDirty();
    }

    private LogisticsSnapshot.Snapshot logisticsSnapshot() {
        return LogisticsTrackerClientData.get(activeCourier());
    }

    private NetworkWarehouseSnapshot.Snapshot warehouseSnapshot() {
        return NetworkWarehouseClientData.get(activeCourier(), activeMailbox());
    }

    private MaidTransportSnapshot.Snapshot transportSnapshot() {
        return MaidTransportClientData.get(menu.terminal());
    }

    private boolean isBound() {
        return accountSnapshot().authenticated() && activeCourier() != null;
    }

    private UUID activeCourier() {
        return accountSnapshot().selectedCourier();
    }

    private MailboxKey activeMailbox() {
        return accountSnapshot().selectedMailbox();
    }

    private TerminalAccountSnapshot.Snapshot accountSnapshot() {
        return TerminalAccountClientData.get(menu.terminal());
    }

    private Component serviceName(ServiceTab tab) {
        return Component.translatable("gui.maid_storage_manager_extension.compass.tab."
                + tab.name().toLowerCase(Locale.ROOT));
    }

    private Component trim(Component value, int maxWidth) {
        if (font.width(value) <= maxWidth) return value;
        String text = value.getString();
        int end = text.length();
        while (end > 0 && font.width(text.substring(0, end) + "…") > maxWidth) end--;
        return Component.literal(text.substring(0, end) + "…");
    }

    private static String itemKey(ItemStack stack) {
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return (id == null ? "unknown" : id.toString()) + '|'
                + (stack.hasTag() ? stack.getTag().toString() : "");
    }

    private static String compactAmount(int value) {
        if (value >= 1_000_000) return value / 1_000_000 + "m";
        if (value >= 1_000) return value / 1_000 + "k";
        return Integer.toString(value);
    }

    private Layout layout() {
        int margin = 6;
        int contentY = 31;
        int contentBottom = height - 36;
        int contentHeight = Math.max(100, contentBottom - contentY);
        int leftWidth = width < 640 ? 112 : 158;
        int rightWidth = width < 640 ? 214 : Math.min(292, Math.max(242, width / 3));
        int gap = 4;
        int mapWidth = width - margin * 2 - leftWidth - rightWidth - gap * 2;
        if (mapWidth < 96) {
            mapWidth = 0;
            rightWidth = Math.max(180, width - margin * 2 - leftWidth - gap);
        }
        int leftX = margin;
        int mapX = leftX + leftWidth + gap;
        int rightX = mapWidth > 0 ? mapX + mapWidth + gap : mapX;
        return new Layout(leftX, leftWidth, mapX, mapWidth, rightX, rightWidth,
                contentY, contentBottom, contentHeight);
    }

    private SettingsLayout settingsLayout() {
        Layout layout = layout();
        int inset = layout.leftX + 8;
        int gap = 10;
        int columnWidth = Math.max(116, (width - inset * 2 - gap) / 2);
        int rightX = inset + columnWidth + gap;
        int titleY = layout.contentY + 9;
        int headingY = layout.contentY + 39;
        int rowsY = headingY + 18;
        int rowStride = 30;
        int maxRows = Math.max(1, (layout.contentBottom - rowsY - 7) / rowStride);
        return new SettingsLayout(inset, rightX, columnWidth, titleY,
                headingY, rowsY, rowStride, maxRows);
    }

    private static void panel(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, PANEL_BG);
        outline(graphics, x, y, width, height, BORDER);
    }

    private static void outline(GuiGraphics graphics, int x, int y,
                                int width, int height, int color) {
        graphics.hLine(x, x + width - 1, y, color);
        graphics.hLine(x, x + width - 1, y + height - 1, color);
        graphics.vLine(x, y, y + height - 1, color);
        graphics.vLine(x + width - 1, y, y + height - 1, color);
    }

    private static boolean inside(double mouseX, double mouseY,
                                  int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private record Layout(int leftX, int leftWidth, int mapX, int mapWidth,
                          int rightX, int rightWidth, int contentY,
                          int contentBottom, int contentHeight) {
    }

    private record SettingsLayout(int leftX, int rightX, int columnWidth,
                                  int titleY, int headingY, int rowsY,
                                  int rowStride, int maxRows) {
    }

    private record Projected(int x, int y) {
    }

    private enum MarkerKind {
        DOT,
        WAREHOUSE,
        PICKUP,
        DESTINATION
    }

    private record MapMarker(double worldX, double worldZ, ResourceLocation dimension,
                             Component role, Component name, int color, MarkerKind kind,
                             UUID warehouse, boolean selected, boolean valid) {
    }
}
