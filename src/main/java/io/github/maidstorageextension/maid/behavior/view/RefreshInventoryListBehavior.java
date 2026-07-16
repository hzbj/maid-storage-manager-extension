package io.github.maidstorageextension.maid.behavior.view;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.decoration.ItemFrame;
import org.jetbrains.annotations.NotNull;
import studio.fantasyit.maid_storage_manager.Config;
import io.github.maidstorageextension.maid.behavior.MaidNavigationRetryPolicy;
import io.github.maidstorageextension.maid.ExtensionMemoryUtil;
import io.github.maidstorageextension.maid.ExtensionWorkControl;
import io.github.maidstorageextension.maid.memory.PeriodicScanMemory;
import io.github.maidstorageextension.scan.InventoryListRefreshService;
import io.github.maidstorageextension.scan.StorageScanService;
import studio.fantasyit.maid_storage_manager.util.MemoryUtil;
import studio.fantasyit.maid_storage_manager.util.MoveUtil;

import java.util.Map;
import java.util.UUID;

public class RefreshInventoryListBehavior extends Behavior<EntityMaid> {
    private static final int PATH_TIMEOUT_TICKS = 200;

    private ItemFrame frame;
    private UUID lockedFrame;
    private boolean done;
    private int lockRetryTicks;
    private int travelTicks;
    private BlockPos standPos;
    private final RefreshTravelWatchdog travelWatchdog = new RefreshTravelWatchdog(PATH_TIMEOUT_TICKS);

    public RefreshInventoryListBehavior() {
        super(Map.of(), 240);
    }

    @Override
    protected boolean checkExtraStartConditions(@NotNull ServerLevel level, @NotNull EntityMaid maid) {
        return !MemoryUtil.isWorking(maid)
                && ExtensionMemoryUtil.getTaskBellCall(maid) == null
                && ExtensionMemoryUtil.getPeriodicScan(maid).getPhase() == PeriodicScanMemory.Phase.REFRESH_PENDING;
    }

    @Override
    protected void start(ServerLevel level, EntityMaid maid, long gameTime) {
        done = false;
        InventoryListRefreshService.FrameLookup lookup = InventoryListRefreshService.resolveFrame(level, maid);
        frame = lookup.frame();
        if (!lookup.success()) {
            finishCycle(level, maid, InventoryListRefreshService.RefreshResult.failed(lookup.outcome()));
            return;
        }
        if (!InventoryListRefreshService.tryLock(frame, maid)) {
            lockRetryTicks++;
            done = true;
            if (lockRetryTicks >= 200) {
                finishCycle(level, maid,
                        InventoryListRefreshService.RefreshResult.failed(
                                InventoryListRefreshService.Outcome.FRAME_BUSY));
            }
            return;
        }
        lockRetryTicks = 0;
        lockedFrame = frame.getUUID();
        standPos = MoveUtil.selectPosForTarget(level, maid, frame.blockPosition());
        if (standPos == null) {
            finishCycle(level, maid,
                    InventoryListRefreshService.RefreshResult.failed(
                            InventoryListRefreshService.Outcome.NO_SAFE_POSITION));
            return;
        }
        setTravelTarget(maid, standPos);
        MemoryUtil.setLookAt(maid, frame);
        MemoryUtil.setWorking(maid, true);
        travelTicks = 0;
        travelWatchdog.reset(maid.distanceToSqr(frame));
    }

    @Override
    protected boolean canStillUse(ServerLevel level, EntityMaid maid, long gameTime) {
        return !done
                && ExtensionMemoryUtil.getTaskBellCall(maid) == null
                && !ExtensionWorkControl.hasNonInterruptibleWork(maid)
                && ExtensionMemoryUtil.getPeriodicScan(maid).getPhase() == PeriodicScanMemory.Phase.REFRESH_PENDING;
    }

    @Override
    protected boolean timedOut(long gameTime) {
        // Long but progressing routes are valid. The travel watchdog below owns the
        // timeout and only fails after the maid has made no meaningful progress.
        return false;
    }

    @Override
    protected void tick(ServerLevel level, EntityMaid maid, long gameTime) {
        InventoryListRefreshService.FrameLookup lookup = InventoryListRefreshService.resolveFrame(level, maid);
        ItemFrame current = lookup.frame();
        if (!lookup.success() || frame == null || !current.getUUID().equals(frame.getUUID())) {
            finishCycle(level, maid, InventoryListRefreshService.RefreshResult.failed(
                    lookup.success() ? InventoryListRefreshService.Outcome.INVALID_FRAME : lookup.outcome()));
            return;
        }
        double distanceSquared = maid.distanceToSqr(current);
        if (distanceSquared <= 9.0) {
            clearTravelTarget(maid);
            MemoryUtil.setLookAt(maid, current);
            maid.swing(InteractionHand.MAIN_HAND);
            InventoryListRefreshService.RefreshResult result =
                    InventoryListRefreshService.refresh(level, maid, current);
            finishCycle(level, maid, result);
        } else if (travelWatchdog.tick(distanceSquared)) {
            finishCycle(level, maid,
                    InventoryListRefreshService.RefreshResult.failed(
                            InventoryListRefreshService.Outcome.PATH_TIMEOUT));
        } else if (MaidNavigationRetryPolicy.shouldRetry(
                ++travelTicks, maid.getNavigation().isDone())) {
            standPos = MoveUtil.selectPosForTarget(level, maid, current.blockPosition());
            if (standPos == null) {
                finishCycle(level, maid,
                        InventoryListRefreshService.RefreshResult.failed(
                                InventoryListRefreshService.Outcome.NO_SAFE_POSITION));
            } else {
                setTravelTarget(maid, standPos);
            }
        }
    }

    @Override
    protected void stop(ServerLevel level, EntityMaid maid, long gameTime) {
        InventoryListRefreshService.unlock(lockedFrame, maid);
        lockedFrame = null;
        frame = null;
        standPos = null;
        travelTicks = 0;
        MemoryUtil.setWorking(maid, false);
        clearTravelTarget(maid);
    }

    private void finishCycle(ServerLevel level, EntityMaid maid,
                             InventoryListRefreshService.RefreshResult result) {
        done = true;
        lockRetryTicks = 0;
        StorageScanService.completeCycle(level, maid, result, frame);
    }

    private static void setTravelTarget(EntityMaid maid, BlockPos target) {
        MemoryUtil.setTarget(maid, target, (float) Config.viewChangeSpeed);
        maid.getNavigation().moveTo(
                target.getX() + 0.5, target.getY(), target.getZ() + 0.5,
                Config.viewChangeSpeed);
    }

    private static void clearTravelTarget(EntityMaid maid) {
        MemoryUtil.clearTarget(maid);
        maid.getNavigation().stop();
    }
}
