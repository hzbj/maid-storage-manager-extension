package io.github.maidstorageextension.maid.behavior;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import studio.fantasyit.maid_storage_manager.Config;
import io.github.maidstorageextension.block.TaskBellBlockEntity;
import io.github.maidstorageextension.data.ExtensionConfigData;
import io.github.maidstorageextension.maid.ExtensionMemoryUtil;
import io.github.maidstorageextension.maid.TaskBellCallController;
import io.github.maidstorageextension.maid.TaskBellStandLocator;
import io.github.maidstorageextension.maid.memory.TaskBellCallMemory;
import io.github.maidstorageextension.registry.ExtensionMemoryModules;
import studio.fantasyit.maid_storage_manager.maid.task.StorageManageTask;
import studio.fantasyit.maid_storage_manager.util.MemoryUtil;

import java.util.Map;

public class TaskBellBehavior extends Behavior<EntityMaid> {
    private static final double ARRIVAL_DISTANCE_SQR = 1.5 * 1.5;

    public TaskBellBehavior() {
        super(Map.of(ExtensionMemoryModules.TASK_BELL_CALL.get(), MemoryStatus.VALUE_PRESENT), 2600);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, EntityMaid maid) {
        TaskBellCallMemory call = ExtensionMemoryUtil.getTaskBellCall(maid);
        String invalidReason = invalidReason(level, maid, call);
        if (invalidReason != null) {
            TaskBellCallController.fail(level, maid, invalidReason);
            return false;
        }
        return !MemoryUtil.isWorking(maid);
    }

    @Override
    protected void start(ServerLevel level, EntityMaid maid, long gameTime) {
        TaskBellCallMemory call = ExtensionMemoryUtil.getTaskBellCall(maid);
        if (call != null) {
            if (call.hasArrived()) {
                maid.getNavigation().stop();
                MemoryUtil.clearTarget(maid);
                MemoryUtil.setLookAt(maid, call.bellPos());
                return;
            }
            if (!TaskBellStandLocator.isUsable(level, maid, call.bellPos(), call.standPos())) {
                call.standPos(TaskBellStandLocator.find(level, maid, call.bellPos()));
            }
            if (call.standPos() == null) {
                TaskBellCallController.fail(level, maid, "no_safe_position");
                return;
            }
            MemoryUtil.setTarget(maid, call.standPos(), (float) Config.collectSpeed);
        }
    }

    @Override
    protected void tick(ServerLevel level, EntityMaid maid, long gameTime) {
        TaskBellCallMemory call = ExtensionMemoryUtil.getTaskBellCall(maid);
        int serverTick = level.getServer().getTickCount();
        String invalidReason = invalidReason(level, maid, call);
        if (invalidReason != null) {
            TaskBellCallController.fail(level, maid, invalidReason);
            return;
        }
        if (maid.getTarget() != null) {
            TaskBellCallController.fail(level, maid, "combat");
            return;
        }

        if (!call.hasArrived()) {
            ExtensionConfigData.Data config = ExtensionConfigData.get(maid);
            if (serverTick - call.travelStartedAt() >= config.taskBellTravelTimeoutTicks()) {
                TaskBellCallController.fail(level, maid, "timeout");
                return;
            }
            if (maid.distanceToSqr(call.standPos().getCenter()) <= ARRIVAL_DISTANCE_SQR) {
                call.markArrived(serverTick);
                maid.getNavigation().stop();
                MemoryUtil.clearTarget(maid);
                MemoryUtil.setLookAt(maid, call.bellPos());
                TaskBellCallController.notifyArrived(level, maid);
            } else if (serverTick % 10 == 0) {
                if (serverTick % 20 == 0) {
                    var standPos = TaskBellStandLocator.find(level, maid, call.bellPos());
                    if (standPos == null) {
                        TaskBellCallController.fail(level, maid, "no_safe_position");
                        return;
                    }
                    call.standPos(standPos);
                }
                MemoryUtil.setTarget(maid, call.standPos(), (float) Config.collectSpeed);
            }
            return;
        }

        maid.getNavigation().stop();
        MemoryUtil.setLookAt(maid, call.bellPos());
        if (serverTick - call.arrivedAt() >= ExtensionConfigData.get(maid).taskBellStayTicks()) {
            ExtensionMemoryUtil.clearTaskBellCall(maid);
        }
    }

    @Override
    protected boolean canStillUse(ServerLevel level, EntityMaid maid, long gameTime) {
        return ExtensionMemoryUtil.getTaskBellCall(maid) != null;
    }

    @Override
    protected void stop(ServerLevel level, EntityMaid maid, long gameTime) {
        if (ExtensionMemoryUtil.getTaskBellCall(maid) == null) {
            MemoryUtil.clearTarget(maid);
        }
    }

    private String invalidReason(ServerLevel level, EntityMaid maid, TaskBellCallMemory call) {
        if (call == null || !maid.isAlive() || maid.isPassenger()
                || !maid.getTask().getUid().equals(StorageManageTask.TASK_ID)) {
            return call == null ? null : "invalid_target";
        }
        double range = ExtensionConfigData.get(maid).taskBellRange();
        if (maid.distanceToSqr(call.bellPos().getCenter()) > range * range) {
            return "out_of_range";
        }
        if (!(level.getBlockEntity(call.bellPos()) instanceof TaskBellBlockEntity bell)
                || !bell.isBoundTo(maid.getUUID())) {
            return "bell_invalid";
        }
        return null;
    }
}
