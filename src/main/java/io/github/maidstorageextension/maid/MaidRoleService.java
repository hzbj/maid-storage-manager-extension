package io.github.maidstorageextension.maid;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskManager;
import io.github.maidstorageextension.data.CourierData;
import io.github.maidstorageextension.data.DriverData;
import io.github.maidstorageextension.maid.courier.CourierService;
import io.github.maidstorageextension.maid.task.CourierTask;
import io.github.maidstorageextension.maid.task.DriverTask;
import io.github.maidstorageextension.terminal.MailboxWarehouseData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

/** Validated profession transitions initiated by terminal jobs. */
public final class MaidRoleService {
    public enum Result { READY, BUSY, WAREHOUSE_MANAGER, TASK_UNAVAILABLE }

    private MaidRoleService() {
    }

    public static Result ensureCourier(ServerLevel level, EntityMaid maid) {
        return switchRole(level, maid, CourierTask.TASK_ID);
    }

    public static Result ensureDriver(ServerLevel level, EntityMaid maid) {
        return switchRole(level, maid, DriverTask.TASK_ID);
    }

    private static Result switchRole(ServerLevel level, EntityMaid maid, ResourceLocation task) {
        if (MailboxWarehouseData.get(level.getServer()).mailboxOf(maid.getUUID()) != null) {
            return Result.WAREHOUSE_MANAGER;
        }
        if (maid.getTask().getUid().equals(task)) return Result.READY;
        CourierData.Data courier = CourierData.get(maid);
        if (CourierService.hasActiveTransaction(maid)
                || !courier.pendingList().isEmpty()
                || !courier.pendingCargo().isEmpty()
                || !courier.requestManifest().isEmpty()
                || !courier.depositManifest().isEmpty()
                || courier.depositRequested()
                || DriverData.get(maid).activeTrip()
                || ExtensionMemoryUtil.getMiscSort(maid).hasInFlight()) return Result.BUSY;
        var next = TaskManager.findTask(task);
        if (next.isEmpty()) return Result.TASK_UNAVAILABLE;
        maid.setTask(next.get());
        maid.refreshBrain(level);
        return Result.READY;
    }
}
