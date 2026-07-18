package io.github.maidstorageextension.maid;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitEntities;
import io.github.maidstorageextension.maid.memory.PeriodicScanMemory;
import io.github.maidstorageextension.maid.memory.MiscSortMemory;
import io.github.maidstorageextension.maid.courier.CourierService;
import io.github.maidstorageextension.maid.courier.CourierSortMutex;
import io.github.maidstorageextension.scan.StorageScanService;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.network.PacketDistributor;
import studio.fantasyit.maid_storage_manager.communicate.CommunicateUtil;
import studio.fantasyit.maid_storage_manager.maid.behavior.ScheduleBehavior;
import studio.fantasyit.maid_storage_manager.network.MaidDataSyncToClientPacket;
import studio.fantasyit.maid_storage_manager.registry.MemoryModuleRegistry;
import studio.fantasyit.maid_storage_manager.util.Conditions;
import studio.fantasyit.maid_storage_manager.util.MemoryUtil;
import studio.fantasyit.maid_storage_manager.network.Network;

public final class ExtensionWorkControl {
    private ExtensionWorkControl() {
    }

    public static boolean mayUsePeriodicIdleTime(EntityMaid maid) {
        return !MemoryUtil.isWorking(maid) && mayContinuePeriodicIdleAction(maid);
    }

    /**
     * Keeps an already-started periodic route eligible while its own behavior owns IS_WORKING.
     * Other work gates still interrupt the route normally.
     */
    private static boolean mayContinuePeriodicIdleAction(EntityMaid maid) {
        if (!CourierSortMutex.mayStartMiscSort(
                CourierService.hasActiveWarehouseTransaction(maid),
                CourierService.hasActiveTransaction(maid))) return false;
        if (ExtensionMemoryUtil.getTaskBellCall(maid) != null) return false;
        if (MemoryUtil.getViewedInventory(maid).isViewing()) return false;
        if (!MemoryUtil.getViewedInventory(maid).getMarkChanged().isEmpty()) return false;
        if (hasNonInterruptibleWork(maid)) return false;
        if (!Conditions.isNothingToPlace(maid)) return false;
        if (MemoryUtil.getResorting(maid).hasTarget() || MemoryUtil.getSorting(maid).hasAny()) return false;
        if (MemoryUtil.isCoWorking(maid)) return false;
        return !maid.getBrain().hasMemoryValue(InitEntities.VISIBLE_PICKUP_ENTITIES.get());
    }

    public static boolean hasNonInterruptibleWork(EntityMaid maid) {
        if (CommunicateUtil.hasCommunicateRequest(maid)
                && CommunicateUtil.getCommunicateRequest(maid).isWorking()) return true;
        if (MemoryUtil.getCrafting(maid).isGoPlacingBeforeCraft()) return true;
        if (Conditions.takingRequestList(maid)) return true;
        if (MemoryUtil.getLogistics(maid).shouldWork()) return true;
        return MemoryUtil.getMeal(maid).isEating() || MemoryUtil.getMeal(maid).hasTarget();
    }

    public static boolean shouldHoldExclusiveAction(EntityMaid maid) {
        if (ExtensionMemoryUtil.getTaskBellCall(maid) != null) {
            return true;
        }
        PeriodicScanMemory.Phase phase = ExtensionMemoryUtil.getPeriodicScan(maid).getPhase();
        MiscSortMemory sort = ExtensionMemoryUtil.getMiscSort(maid);
        if ((phase == PeriodicScanMemory.Phase.SORT_PENDING
                || phase == PeriodicScanMemory.Phase.SORTING)
                && sort.getPhase() != MiscSortMemory.Phase.REFRESH_PENDING) {
            if (sort.hasInFlight() || MemoryUtil.isWorking(maid)) {
                return true;
            }
            return !StorageScanService.hasManualChangedTarget(maid)
                    && mayUsePeriodicIdleTime(maid);
        }
        return phase == PeriodicScanMemory.Phase.REFRESH_PENDING
                && !StorageScanService.hasManualChangedTarget(maid)
                && mayContinuePeriodicIdleAction(maid);
    }

    public static void setBaseSchedule(EntityMaid maid, ScheduleBehavior.Schedule schedule) {
        ScheduleBehavior.Schedule current = MemoryUtil.getCurrentlyWorking(maid);
        if (current == schedule) {
            return;
        }
        maid.getBrain().setMemory(MemoryModuleRegistry.CURRENTLY_WORKING.get(), schedule);
        MemoryUtil.clearTarget(maid);
        CompoundTag value = new CompoundTag();
        value.putInt("id", schedule.ordinal());
        Network.INSTANCE.send(PacketDistributor.TRACKING_ENTITY.with(() -> maid),
                new MaidDataSyncToClientPacket(MaidDataSyncToClientPacket.Type.WORKING, maid.getId(), value));
    }
}
