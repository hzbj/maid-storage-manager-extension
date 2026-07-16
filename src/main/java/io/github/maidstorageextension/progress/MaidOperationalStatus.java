package io.github.maidstorageextension.progress;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitEntities;
import io.github.maidstorageextension.maid.ExtensionMemoryUtil;
import io.github.maidstorageextension.maid.memory.MiscSortMemory;
import io.github.maidstorageextension.maid.memory.PeriodicScanMemory;
import io.github.maidstorageextension.maid.memory.TaskBellCallMemory;
import net.minecraft.world.entity.schedule.Activity;
import studio.fantasyit.maid_storage_manager.maid.behavior.ScheduleBehavior;
import studio.fantasyit.maid_storage_manager.util.MemoryUtil;
import io.github.maidstorageextension.maid.task.CourierTask;

/**
 * One authoritative, server-side classification of the maid's current visible state.
 * The progress pad adapter serializes this state through the base mod's existing packet.
 */
public final class MaidOperationalStatus {
    public enum Kind {
        SLEEPING,
        RESTING,
        COMBAT,
        TASK_BELL,
        INSPECTION,
        COURIER,
        REQUEST,
        LOGISTICS,
        STORING,
        VIEWING,
        RESORTING,
        SORTING,
        EATING,
        COMMUNICATING,
        CO_WORKING,
        PICKING_UP,
        TASK,
        STANDBY
    }

    public enum Action {
        NONE,
        PATHING,
        WAITING,
        DISCOVERING,
        INSPECTING,
        CLEANING,
        REFRESHING
    }

    public enum ExtensionMode {
        NONE,
        TASK_BELL_TRAVELLING,
        TASK_BELL_WAITING,
        INSPECTION_DISCOVERING,
        INSPECTION_VIEWING,
        INSPECTION_CLEANING,
        INSPECTION_REFRESHING
    }

    public enum WorkMode {
        NONE,
        COURIER,
        REQUEST,
        LOGISTICS,
        STORING,
        VIEWING,
        RESORTING,
        SORTING,
        EATING,
        COMMUNICATING,
        CO_WORKING,
        PICKING_UP,
        TASK
    }

    public record Snapshot(boolean sleeping, boolean sitting, boolean combat,
                           boolean restActivity, boolean idleActivity,
                           ExtensionMode extensionMode, WorkMode workMode,
                           boolean pathing, int progress, int total) {
        public Snapshot {
            extensionMode = extensionMode == null ? ExtensionMode.NONE : extensionMode;
            workMode = workMode == null ? WorkMode.NONE : workMode;
            progress = Math.max(0, progress);
            total = Math.max(progress, total);
        }
    }

    public record Resolved(Kind kind, Action action, int progress, int total) {
        public Resolved {
            kind = kind == null ? Kind.STANDBY : kind;
            action = action == null ? Action.NONE : action;
            progress = Math.max(0, progress);
            total = Math.max(progress, total);
        }

        public String kindTranslationKey() {
            return "gui.maid_storage_manager_extension.progress_pad.status.kind."
                    + kind.name().toLowerCase(java.util.Locale.ROOT);
        }

        public String actionTranslationKey() {
            return "gui.maid_storage_manager_extension.progress_pad.status.action."
                    + action.name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    private MaidOperationalStatus() {
    }

    public static Resolved capture(EntityMaid maid) {
        TaskBellCallMemory bell = ExtensionMemoryUtil.getTaskBellCall(maid);
        PeriodicScanMemory inspection = ExtensionMemoryUtil.getPeriodicScan(maid);
        MiscSortMemory cleanup = ExtensionMemoryUtil.getMiscSort(maid);
        ExtensionMode extensionMode = extensionMode(bell, inspection);
        ScheduleBehavior.Schedule schedule = MemoryUtil.getCurrentlyWorking(maid);
        Activity activity = maid.getScheduleDetail();
        boolean pathing = MemoryUtil.getTargetPos(maid) != null || !maid.getNavigation().isDone();
        boolean cleaning = inspection.getPhase() == PeriodicScanMemory.Phase.SORT_PENDING
                || inspection.getPhase() == PeriodicScanMemory.Phase.SORTING;
        return resolve(new Snapshot(
                maid.isSleeping(),
                maid.isMaidInSittingPose(),
                maid.getTarget() != null,
                activity == Activity.REST,
                activity == Activity.IDLE,
                extensionMode,
                workMode(maid, schedule),
                pathing,
                cleaning ? cleanup.getCleanupSourceProgress() : inspection.getScanProgress(),
                cleaning ? cleanup.getCleanupSourceTotal() : inspection.getScanTotal()));
    }

    public static Resolved resolve(Snapshot snapshot) {
        if (snapshot.sleeping()) {
            return simple(Kind.SLEEPING);
        }
        if (snapshot.combat()) {
            return simple(Kind.COMBAT);
        }
        if (snapshot.sitting()) {
            return withPathing(Kind.RESTING, snapshot.pathing());
        }

        Resolved extension = resolveExtension(snapshot);
        if (extension != null) {
            return extension;
        }
        if (snapshot.restActivity()) {
            return withPathing(Kind.RESTING, snapshot.pathing());
        }
        if (snapshot.idleActivity() && snapshot.workMode() == WorkMode.NONE) {
            return withPathing(Kind.STANDBY, snapshot.pathing());
        }

        Kind workKind = switch (snapshot.workMode()) {
            case COURIER -> Kind.COURIER;
            case REQUEST -> Kind.REQUEST;
            case LOGISTICS -> Kind.LOGISTICS;
            case STORING -> Kind.STORING;
            case VIEWING -> Kind.VIEWING;
            case RESORTING -> Kind.RESORTING;
            case SORTING -> Kind.SORTING;
            case EATING -> Kind.EATING;
            case COMMUNICATING -> Kind.COMMUNICATING;
            case CO_WORKING -> Kind.CO_WORKING;
            case PICKING_UP -> Kind.PICKING_UP;
            case TASK -> Kind.TASK;
            case NONE -> Kind.STANDBY;
        };
        return withPathing(workKind, snapshot.pathing());
    }

    private static Resolved resolveExtension(Snapshot snapshot) {
        return switch (snapshot.extensionMode()) {
            case TASK_BELL_TRAVELLING -> new Resolved(Kind.TASK_BELL, Action.PATHING, 0, 0);
            case TASK_BELL_WAITING -> new Resolved(Kind.TASK_BELL, Action.WAITING, 0, 0);
            case INSPECTION_DISCOVERING -> new Resolved(
                    Kind.INSPECTION, Action.DISCOVERING, snapshot.progress(), snapshot.total());
            case INSPECTION_VIEWING -> new Resolved(
                    Kind.INSPECTION,
                    snapshot.pathing() ? Action.PATHING : Action.INSPECTING,
                    snapshot.progress(), snapshot.total());
            case INSPECTION_CLEANING -> new Resolved(
                    Kind.INSPECTION,
                    Action.CLEANING,
                    snapshot.progress(), snapshot.total());
            case INSPECTION_REFRESHING -> new Resolved(
                    Kind.INSPECTION,
                    snapshot.pathing() ? Action.PATHING : Action.REFRESHING,
                    0, 0);
            case NONE -> null;
        };
    }

    private static ExtensionMode extensionMode(TaskBellCallMemory bell, PeriodicScanMemory inspection) {
        if (bell != null) {
            return bell.hasArrived()
                    ? ExtensionMode.TASK_BELL_WAITING
                    : ExtensionMode.TASK_BELL_TRAVELLING;
        }
        return switch (inspection.getPhase()) {
            case PREPARING -> ExtensionMode.INSPECTION_DISCOVERING;
            case SCANNING -> ExtensionMode.INSPECTION_VIEWING;
            case SORT_PENDING, SORTING -> ExtensionMode.INSPECTION_CLEANING;
            case REFRESH_PENDING -> ExtensionMode.INSPECTION_REFRESHING;
            case IDLE -> ExtensionMode.NONE;
        };
    }

    private static WorkMode workMode(EntityMaid maid, ScheduleBehavior.Schedule schedule) {
        if (maid.getTask().getUid().equals(CourierTask.TASK_ID)) {
            return WorkMode.COURIER;
        }
        return switch (schedule) {
            case REQUEST -> WorkMode.REQUEST;
            case LOGISTICS -> WorkMode.LOGISTICS;
            case PLACE -> WorkMode.STORING;
            case VIEW -> MemoryUtil.getViewedInventory(maid).isViewing()
                    || !MemoryUtil.getViewedInventory(maid).getMarkChanged().isEmpty()
                    ? WorkMode.VIEWING : WorkMode.NONE;
            case RESORT -> WorkMode.RESORTING;
            case SORTING -> WorkMode.SORTING;
            case MEAL -> WorkMode.EATING;
            case COMMUNICATE -> WorkMode.COMMUNICATING;
            case CO_WORK -> WorkMode.CO_WORKING;
            case NO_SCHEDULE -> maid.getBrain().hasMemoryValue(InitEntities.VISIBLE_PICKUP_ENTITIES.get())
                    ? WorkMode.PICKING_UP : WorkMode.NONE;
        };
    }

    private static Resolved simple(Kind kind) {
        return new Resolved(kind, Action.NONE, 0, 0);
    }

    private static Resolved withPathing(Kind kind, boolean pathing) {
        return new Resolved(kind, pathing ? Action.PATHING : Action.NONE, 0, 0);
    }
}
