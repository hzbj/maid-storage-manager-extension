package io.github.maidstorageextension.client;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import io.github.maidstorageextension.data.ExtensionConfigData;
import io.github.maidstorageextension.data.MaintenanceStatusData;
import io.github.maidstorageextension.data.PeriodicScanInterval;
import io.github.maidstorageextension.data.WarehouseCourierData;
import io.github.maidstorageextension.data.WarehouseStationData;
import io.github.maidstorageextension.network.CourierCommandPacket;
import io.github.maidstorageextension.network.ExtensionMaidConfigPacket;
import io.github.maidstorageextension.network.ExtensionNetwork;
import io.github.maidstorageextension.network.StationApprovalPacket;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.stream.IntStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class ExtensionClothConfigScreen {
    private static final Integer[] BELL_RANGES = IntStream.rangeClosed(2, 16)
            .map(value -> value * 8)
            .boxed()
            .toArray(Integer[]::new);
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    private ExtensionClothConfigScreen() {
    }

    public static void appendTo(ConfigBuilder builder, ConfigEntryBuilder entries, EntityMaid maid) {
        ExtensionConfigData.Data current = ExtensionConfigData.get(maid);
        MaintenanceStatusData.Data status = MaintenanceStatusData.get(maid);
        Draft draft = new Draft(current);

        ConfigCategory scan = builder.getOrCreateCategory(
                Component.translatable("gui.maid_storage_manager_extension.config.category.scan"));
        scan.addEntry(entries.startTextDescription(Component.translatable(
                        "gui.maid_storage_manager_extension.config.current_maid", maid.getName()))
                .setColor(ChatFormatting.AQUA.getColor())
                .build());
        scan.addEntry(entries.startEnumSelector(
                        Component.translatable("gui.maid_storage_manager_extension.config.periodic_scan_interval"),
                        PeriodicScanInterval.class,
                        current.periodicScanInterval())
                .setEnumNameProvider(value -> Component.translatable(
                        ((PeriodicScanInterval) value).translationKey()))
                .setDefaultValue(PeriodicScanInterval.DISABLED)
                .setSaveConsumer(value -> draft.interval = value)
                .build());
        scan.addEntry(entries.startIntSlider(
                        Component.translatable("gui.maid_storage_manager_extension.config.local_scan_radius"),
                        current.localScanRadius(),
                        ExtensionConfigData.MIN_LOCAL_SCAN_RADIUS,
                        ExtensionConfigData.MAX_LOCAL_SCAN_RADIUS)
                .setDefaultValue(ExtensionConfigData.DEFAULT_LOCAL_SCAN_RADIUS)
                .setTextGetter(value -> Component.translatable(
                        "gui.maid_storage_manager_extension.config.blocks", value))
                .setSaveConsumer(value -> draft.localScanRadius = value)
                .setTooltip(Component.translatable(
                        "gui.maid_storage_manager_extension.config.local_scan_radius.tooltip"))
                .build());
        scan.addEntry(entries.startBooleanToggle(
                        Component.translatable(
                                "gui.maid_storage_manager_extension.config.misc_sort_match_nbt"),
                        current.miscSortMatchNbt())
                .setDefaultValue(false)
                .setSaveConsumer(value -> draft.miscSortMatchNbt = value)
                .setTooltip(Component.translatable(
                        "gui.maid_storage_manager_extension.config.misc_sort_match_nbt.tooltip"))
                .build());

        ConfigCategory bell = builder.getOrCreateCategory(
                Component.translatable("gui.maid_storage_manager_extension.config.category.task_bell"));
        bell.addEntry(entries.startSelector(
                        Component.translatable("gui.maid_storage_manager_extension.config.task_bell_range"),
                        BELL_RANGES,
                        current.taskBellRange())
                .setNameProvider(value -> Component.translatable(
                        "gui.maid_storage_manager_extension.config.blocks", value))
                .setDefaultValue(ExtensionConfigData.DEFAULT_TASK_BELL_RANGE)
                .setSaveConsumer(value -> draft.taskBellRange = value)
                .build());
        bell.addEntry(entries.startIntSlider(
                        Component.translatable("gui.maid_storage_manager_extension.config.task_bell_timeout"),
                        current.taskBellTravelTimeoutSeconds(),
                        ExtensionConfigData.MIN_TASK_BELL_TRAVEL_TIMEOUT_SECONDS,
                        ExtensionConfigData.MAX_TASK_BELL_TRAVEL_TIMEOUT_SECONDS)
                .setDefaultValue(ExtensionConfigData.DEFAULT_TASK_BELL_TRAVEL_TIMEOUT_SECONDS)
                .setTextGetter(value -> Component.translatable(
                        "gui.maid_storage_manager_extension.config.seconds", value))
                .setSaveConsumer(value -> draft.taskBellTravelTimeoutSeconds = value)
                .build());
        bell.addEntry(entries.startIntSlider(
                        Component.translatable("gui.maid_storage_manager_extension.config.task_bell_stay"),
                        current.taskBellStaySeconds(),
                        ExtensionConfigData.MIN_TASK_BELL_STAY_SECONDS,
                        ExtensionConfigData.MAX_TASK_BELL_STAY_SECONDS)
                .setDefaultValue(ExtensionConfigData.DEFAULT_TASK_BELL_STAY_SECONDS)
                .setTextGetter(value -> Component.translatable(
                        "gui.maid_storage_manager_extension.config.seconds", value))
                .setSaveConsumer(value -> draft.taskBellStaySeconds = value)
                .build());

        ConfigCategory feedback = builder.getOrCreateCategory(
                Component.translatable("gui.maid_storage_manager_extension.config.category.feedback"));
        feedback.addEntry(entries.startBooleanToggle(
                        Component.translatable("gui.maid_storage_manager_extension.config.refresh_frame_effects"),
                        current.refreshFrameEffects())
                .setDefaultValue(true)
                .setSaveConsumer(value -> draft.refreshFrameEffects = value)
                .build());
        feedback.addEntry(entries.startBooleanToggle(
                        Component.translatable("gui.maid_storage_manager_extension.config.refresh_owner_notification"),
                        current.refreshOwnerNotification())
                .setDefaultValue(true)
                .setSaveConsumer(value -> draft.refreshOwnerNotification = value)
                .build());

        ConfigCategory statusCategory = builder.getOrCreateCategory(
                Component.translatable("gui.maid_storage_manager_extension.config.category.status"));
        statusCategory.addEntry(entries.startTextDescription(Component.translatable(
                        "gui.maid_storage_manager_extension.status.phase",
                        Component.translatable(status.phase().translationKey())))
                .setColor(ChatFormatting.AQUA.getColor())
                .build());
        statusCategory.addEntry(entries.startTextDescription(Component.translatable(
                        "gui.maid_storage_manager_extension.status.last_result",
                        Component.translatable(status.lastResult().translationKey())))
                .setColor(status.lastResult() == MaintenanceStatusData.Result.SUCCESS
                        ? ChatFormatting.GREEN.getColor()
                        : ChatFormatting.GOLD.getColor())
                .build());
        statusCategory.addEntry(entries.startTextDescription(Component.translatable(
                        "gui.maid_storage_manager_extension.status.last_time",
                        formatTime(status.lastCompletedEpochMillis())))
                .build());
        statusCategory.addEntry(entries.startTextDescription(Component.translatable(
                        "gui.maid_storage_manager_extension.status.counts",
                        status.scannedStorages(), status.publishedItemTypes()))
                .build());

        WarehouseCourierData.Data couriers = WarehouseCourierData.get(maid);
        WarehouseStationData.Data stations = WarehouseStationData.get(maid);
        if (!couriers.pending().isEmpty() || !stations.pending().isEmpty()) {
            ConfigCategory courierCategory = builder.getOrCreateCategory(
                    Component.translatable("gui.maid_storage_manager_extension.config.category.courier"));
            if (!couriers.pending().isEmpty()) courierCategory.addEntry(entries.startTextDescription(Component.translatable(
                            "gui.maid_storage_manager_extension.courier.pending_help"))
                    .setColor(ChatFormatting.GOLD.getColor())
                    .build());
            for (UUID courierId : couriers.pending()) {
                courierCategory.addEntry(entries.startEnumSelector(
                                Component.translatable(
                                        "gui.maid_storage_manager_extension.courier.pending", shortId(courierId)),
                                ApprovalChoice.class,
                                ApprovalChoice.PENDING)
                        .setEnumNameProvider(value -> Component.translatable(
                                ((ApprovalChoice) value).translationKey()))
                        .setSaveConsumer(value -> draft.approvals.put(courierId, value))
                        .build());
            }
            if (!stations.pending().isEmpty()) {
                courierCategory.addEntry(entries.startTextDescription(Component.translatable(
                                "gui.maid_storage_manager_extension.courier_mailbox.pending_help"))
                        .setColor(ChatFormatting.AQUA.getColor())
                        .build());
                for (WarehouseStationData.StationRequest request : stations.pending()) {
                    var pos = request.key().mailboxPos();
                    courierCategory.addEntry(entries.startEnumSelector(
                                    Component.translatable(
                                            "gui.maid_storage_manager_extension.courier_mailbox.pending",
                                            request.placerName(), pos.getX(), pos.getY(), pos.getZ()),
                                    ApprovalChoice.class,
                                    ApprovalChoice.PENDING)
                            .setEnumNameProvider(value -> Component.translatable(
                                    ((ApprovalChoice) value).translationKey()))
                            .setSaveConsumer(value -> draft.stationApprovals.put(request.key(), value))
                            .build());
                }
            }
        }

        Runnable previousSave = builder.getSavingRunnable();
        builder.setSavingRunnable(() -> {
            if (previousSave != null) {
                previousSave.run();
            }
            ExtensionNetwork.CHANNEL.sendToServer(new ExtensionMaidConfigPacket(
                        maid.getId(),
                        draft.interval.ordinal(),
                        draft.localScanRadius,
                        draft.taskBellRange,
                        draft.taskBellTravelTimeoutSeconds,
                        draft.taskBellStaySeconds,
                        draft.refreshFrameEffects,
                        draft.refreshOwnerNotification,
                        draft.miscSortMatchNbt));
            draft.approvals.forEach((courierId, choice) -> {
                if (choice == ApprovalChoice.APPROVE) {
                    ExtensionNetwork.CHANNEL.sendToServer(new CourierCommandPacket(
                            maid.getId(), CourierCommandPacket.Command.APPROVE, courierId));
                } else if (choice == ApprovalChoice.REJECT) {
                    ExtensionNetwork.CHANNEL.sendToServer(new CourierCommandPacket(
                            maid.getId(), CourierCommandPacket.Command.REJECT, courierId));
                }
            });
            draft.stationApprovals.forEach((key, choice) -> {
                if (choice == ApprovalChoice.PENDING) return;
                ExtensionNetwork.CHANNEL.sendToServer(new StationApprovalPacket(
                        maid.getId(), choice == ApprovalChoice.APPROVE
                        ? StationApprovalPacket.Decision.APPROVE
                        : StationApprovalPacket.Decision.REJECT,
                        key.dimension(), key.mailboxPos()));
            });
        });
    }

    private static String shortId(UUID id) {
        String value = id.toString();
        return value.substring(0, 8);
    }

    private enum ApprovalChoice {
        PENDING,
        APPROVE,
        REJECT;

        private String translationKey() {
            return "gui.maid_storage_manager_extension.courier.approval."
                    + name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    private static Component formatTime(long epochMillis) {
        if (epochMillis <= 0L) {
            return Component.translatable("gui.maid_storage_manager_extension.status.never");
        }
        return Component.literal(TIME_FORMAT.format(Instant.ofEpochMilli(epochMillis)));
    }

    private static final class Draft {
        private PeriodicScanInterval interval;
        private int localScanRadius;
        private int taskBellRange;
        private int taskBellTravelTimeoutSeconds;
        private int taskBellStaySeconds;
        private boolean refreshFrameEffects;
        private boolean refreshOwnerNotification;
        private boolean miscSortMatchNbt;
        private final Map<UUID, ApprovalChoice> approvals = new LinkedHashMap<>();
        private final Map<WarehouseStationData.StationKey, ApprovalChoice> stationApprovals =
                new LinkedHashMap<>();

        private Draft(ExtensionConfigData.Data data) {
            interval = data.periodicScanInterval();
            localScanRadius = data.localScanRadius();
            taskBellRange = data.taskBellRange();
            taskBellTravelTimeoutSeconds = data.taskBellTravelTimeoutSeconds();
            taskBellStaySeconds = data.taskBellStaySeconds();
            refreshFrameEffects = data.refreshFrameEffects();
            refreshOwnerNotification = data.refreshOwnerNotification();
            miscSortMatchNbt = data.miscSortMatchNbt();
        }
    }
}
