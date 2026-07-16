package io.github.maidstorageextension.progress;

import io.github.maidstorageextension.registry.ExtensionItems;
import io.github.maidstorageextension.maid.memory.MiscSortMemory;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import studio.fantasyit.maid_storage_manager.craft.work.ProgressData;
import studio.fantasyit.maid_storage_manager.registry.ItemRegistry;
import io.github.maidstorageextension.compat.EnderPocketCompat;

import java.util.ArrayList;
import java.util.List;

/** Publishes the current status through the progress pad's non-grid header area. */
public final class ProgressPadStatusService {
    private ProgressPadStatusService() {
    }

    public static ProgressData applyHeader(ProgressData source, MaidOperationalStatus.Resolved status) {
        return applyHeader(source, status, null);
    }

    public static ProgressData applyHeader(ProgressData source, MaidOperationalStatus.Resolved status,
                                           MiscSortMemory cleanup) {
        boolean compact = source.maxSz <= 2;
        List<ProgressData.TaskProgress> working = source.working;
        if (status.action() == MaidOperationalStatus.Action.CLEANING && cleanup != null) {
            List<ProgressData.TaskProgress> cleanupTasks = cleanupTasks(
                    cleanup, Math.max(1, source.maxSz));
            if (!cleanupTasks.isEmpty()) working = cleanupTasks;
        }
        return new ProgressData(
                working,
                source.maidName,
                List.of(statusText(status, compact)),
                List.of(icon(status.kind())),
                source.total,
                source.progress,
                source.tickCount,
                source.maxSz,
                source.status);
    }

    static List<ProgressData.TaskProgress> cleanupTasks(MiscSortMemory cleanup, int maxTasks) {
        List<ProgressData.TaskProgress> tasks = new ArrayList<>();
        MiscSortMemory.ActiveBatch batch = cleanup.getActiveBatch().orElse(null);
        if (batch != null) {
            boolean carrying = batch.hasInFlight();
            boolean deliveringBatch = carrying && batch.cargoLines().stream()
                    .anyMatch(line -> line.hasInFlight() && line.currentDestination().isPresent());
            for (MiscSortMemory.CargoLine line : batch.cargoLines()) {
                if (tasks.size() >= maxTasks) break;
                if (carrying && !line.hasInFlight()) continue;
                boolean awaitingReturn = deliveringBatch
                        && line.hasInFlight() && line.currentDestination().isEmpty();
                boolean returning = !deliveringBatch
                        && line.hasInFlight() && line.currentDestination().isEmpty();
                int quantity = line.hasInFlight() ? line.inFlightCount() : line.requestedCount();
                String action = awaitingReturn ? "awaiting_return"
                        : returning ? "returning"
                        : line.hasInFlight() ? "transporting" : "collecting";
                ProgressData.Status cardStatus = line.hasInFlight() && !returning && !awaitingReturn
                        ? ProgressData.Status.NORMAL : ProgressData.Status.WAITING;
                tasks.add(new ProgressData.TaskProgress(
                        List.of(displayStack(line.stack(), quantity)),
                        line.requestedCount(),
                        line.hasInFlight() ? line.inFlightCount() : 0,
                        cardStatus,
                        List.of(Component.translatable(
                                "gui.maid_storage_manager_extension.progress_pad.cleanup." + action))));
            }
            if (!tasks.isEmpty()) return List.copyOf(tasks);
        }

        for (MiscSortMemory.SourceJob job : cleanup.getPendingSourceJobs()) {
            for (MiscSortMemory.PayloadPlan payload : job.payloads()) {
                if (tasks.size() >= maxTasks) return List.copyOf(tasks);
                int total = boundedCount(payload.remainingCount());
                tasks.add(new ProgressData.TaskProgress(
                        List.of(displayStack(payload.stack(), total)),
                        total,
                        0,
                        ProgressData.Status.WAITING,
                        List.of(Component.translatable(
                                "gui.maid_storage_manager_extension.progress_pad.cleanup.collecting"))));
            }
        }
        return List.copyOf(tasks);
    }

    private static ItemStack displayStack(ItemStack exact, long quantity) {
        int renderCount = (int) Math.max(1L,
                Math.min(quantity, Math.max(1, exact.getMaxStackSize())));
        return exact.copyWithCount(renderCount);
    }

    private static int boundedCount(long count) {
        return (int) Math.max(1L, Math.min(Integer.MAX_VALUE, count));
    }

    static Component statusText(MaidOperationalStatus.Resolved status, boolean compact) {
        String prefix = compact
                ? "gui.maid_storage_manager_extension.progress_pad.status.compact."
                : "gui.maid_storage_manager_extension.progress_pad.status.";
        Component text = Component.translatable(prefix + "kind."
                + status.kind().name().toLowerCase(java.util.Locale.ROOT));
        if (status.action() != MaidOperationalStatus.Action.NONE) {
            text = Component.translatable(
                    prefix + "combined",
                    text,
                    Component.translatable(prefix + "action."
                            + status.action().name().toLowerCase(java.util.Locale.ROOT)));
        }
        if (status.total() > 0) {
            text = text.copy().append(Component.literal(" " + status.progress() + "/" + status.total()));
        }
        return text;
    }

    private static ItemStack icon(MaidOperationalStatus.Kind kind) {
        return switch (kind) {
            case SLEEPING, RESTING -> Items.RED_BED.getDefaultInstance();
            case COMBAT -> Items.IRON_SWORD.getDefaultInstance();
            case TASK_BELL -> ExtensionItems.TASK_BELL.get().getDefaultInstance();
            case INSPECTION -> ExtensionItems.INVENTORY_MAINTENANCE_DEVICE.get().getDefaultInstance();
            case COURIER -> EnderPocketCompat.icon();
            case REQUEST -> ItemRegistry.REQUEST_LIST_ITEM.get().getDefaultInstance();
            case LOGISTICS -> ItemRegistry.LOGISTICS_GUIDE.get().getDefaultInstance();
            case STORING, VIEWING, RESORTING, SORTING -> Items.CHEST.getDefaultInstance();
            case EATING -> Items.COOKED_BEEF.getDefaultInstance();
            case COMMUNICATING -> Items.WRITABLE_BOOK.getDefaultInstance();
            case CO_WORKING -> Items.PLAYER_HEAD.getDefaultInstance();
            case PICKING_UP -> Items.HOPPER.getDefaultInstance();
            case TASK -> Items.PAPER.getDefaultInstance();
            case STANDBY -> Items.FEATHER.getDefaultInstance();
        };
    }

}
