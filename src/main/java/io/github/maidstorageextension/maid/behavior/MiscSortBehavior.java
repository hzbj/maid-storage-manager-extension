package io.github.maidstorageextension.maid.behavior;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import io.github.maidstorageextension.maid.ExtensionMemoryUtil;
import io.github.maidstorageextension.maid.ExtensionWorkControl;
import io.github.maidstorageextension.maid.memory.MiscSortMemory;
import io.github.maidstorageextension.maid.memory.PeriodicScanMemory;
import io.github.maidstorageextension.scan.MiscSortService;
import io.github.maidstorageextension.scan.StorageScanService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.items.wrapper.CombinedInvWrapper;
import org.jetbrains.annotations.NotNull;
import studio.fantasyit.maid_storage_manager.Config;
import studio.fantasyit.maid_storage_manager.storage.MaidStorage;
import studio.fantasyit.maid_storage_manager.storage.StorageVisitLock;
import studio.fantasyit.maid_storage_manager.storage.Target;
import studio.fantasyit.maid_storage_manager.storage.base.IFilterable;
import studio.fantasyit.maid_storage_manager.storage.base.IMaidStorage;
import studio.fantasyit.maid_storage_manager.storage.base.ISlotBasedStorage;
import studio.fantasyit.maid_storage_manager.storage.base.IStorageContext;
import studio.fantasyit.maid_storage_manager.storage.base.IStorageExtractableContext;
import studio.fantasyit.maid_storage_manager.storage.base.IStorageInsertableContext;
import studio.fantasyit.maid_storage_manager.storage.base.IStorageInteractContext;
import studio.fantasyit.maid_storage_manager.util.BehaviorBreath;
import studio.fantasyit.maid_storage_manager.util.Conditions;
import studio.fantasyit.maid_storage_manager.util.ItemStackUtil;
import studio.fantasyit.maid_storage_manager.util.MemoryUtil;
import studio.fantasyit.maid_storage_manager.util.MoveUtil;
import studio.fantasyit.maid_storage_manager.util.ViewedInventoryUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Executes source-centric miscellaneous-storage batches planned from one complete patrol cache.
 * Physical cargo is always accounted by exact item NBT, independently of the routing match mode.
 */
public final class MiscSortBehavior extends Behavior<EntityMaid> {
    private enum Operation {
        COLLECT,
        DEPOSIT,
        RETURN_SOURCE
    }

    private static final int MAX_STALLED_CONTEXT_STEPS = 1200;
    private static final int MAX_SLOT_CONTEXT_STEPS = 512;

    private final BehaviorBreath breath = new BehaviorBreath();
    private Operation operation;
    private Target operationTarget;
    private String operationTargetKey;
    private BlockPos standPos;
    private IStorageContext context;
    private StorageVisitLock.LockContext lock = StorageVisitLock.DUMMY;
    private boolean operationDone;
    private int travelTicks;
    private int stalledContextSteps;
    private int lastContextCargoProgress;
    private boolean externalRecovery;

    public MiscSortBehavior() {
        super(Map.of(), 2400);
    }

    @Override
    protected boolean checkExtraStartConditions(@NotNull ServerLevel level, @NotNull EntityMaid maid) {
        PeriodicScanMemory.Phase phase = ExtensionMemoryUtil.getPeriodicScan(maid).getPhase();
        if (phase != PeriodicScanMemory.Phase.SORT_PENDING
                && phase != PeriodicScanMemory.Phase.SORTING) return false;
        MiscSortMemory sort = ExtensionMemoryUtil.getMiscSort(maid);
        boolean protectedPayload = sort.hasActiveCargo();
        return sort.getPhase() != MiscSortMemory.Phase.REFRESH_PENDING
                && (protectedPayload || ExtensionMemoryUtil.getTaskBellCall(maid) == null)
                && maid.getTarget() == null
                && !MemoryUtil.isWorking(maid)
                && (protectedPayload || ExtensionWorkControl.mayUsePeriodicIdleTime(maid));
    }

    @Override
    protected void start(ServerLevel level, EntityMaid maid, long gameTime) {
        ExtensionMemoryUtil.getPeriodicScan(maid).setPhase(PeriodicScanMemory.Phase.SORTING);
        ExtensionMemoryUtil.getMiscSort(maid).setPhase(MiscSortMemory.Phase.MOVING);
        resetTransient();
    }

    @Override
    protected boolean canStillUse(ServerLevel level, EntityMaid maid, long gameTime) {
        PeriodicScanMemory.Phase phase = ExtensionMemoryUtil.getPeriodicScan(maid).getPhase();
        MiscSortMemory sort = ExtensionMemoryUtil.getMiscSort(maid);
        if ((phase != PeriodicScanMemory.Phase.SORT_PENDING
                && phase != PeriodicScanMemory.Phase.SORTING)
                || sort.getPhase() == MiscSortMemory.Phase.REFRESH_PENDING
                || maid.getTarget() != null) return false;
        if (sort.hasActiveCargo()) return true;
        if (!MiscSortService.isPolicyCurrent(maid)) return false;
        if (ExtensionMemoryUtil.getTaskBellCall(maid) != null
                || StorageScanService.hasManualChangedTarget(maid)
                || ExtensionWorkControl.hasNonInterruptibleWork(maid)) return false;
        return context != null || ExtensionWorkControl.mayUsePeriodicIdleTime(maid);
    }

    @Override
    protected void tick(ServerLevel level, EntityMaid maid, long gameTime) {
        MiscSortMemory sort = ExtensionMemoryUtil.getMiscSort(maid);
        if (!MiscSortService.isKeepCurrentMode(maid)) {
            sort.clearUnstartedWork();
            sort.forceActiveBatchReturn();
            if (!sort.hasActiveCargo()) {
                MiscSortService.queueFinished(maid);
                return;
            }
        }

        if (!sort.hasActiveCargo() && !MiscSortService.isPolicyCurrent(maid)) {
            invalidateForPolicyChange(maid, sort);
            return;
        }

        if (context != null) {
            boolean attempted = tickContext(level, maid, sort);
            if (attempted) {
                int progress = contextCargoProgress(sort);
                if (progress != lastContextCargoProgress) {
                    lastContextCargoProgress = progress;
                    stalledContextSteps = 0;
                } else {
                    stalledContextSteps++;
                }
            }
            boolean exhausted = context.isDone()
                    || stalledContextSteps >= MAX_STALLED_CONTEXT_STEPS;
            if (operationDone || exhausted) {
                if (!operationDone) handleExhaustedContext(level, sort);
                finishOperation(maid);
            }
            return;
        }

        if (operationTarget == null && !prepareOperation(level, maid, sort)) return;

        travelTicks++;
        boolean physicallyAtStand = standPos != null
                && maid.distanceToSqr(Vec3.atCenterOf(standPos)) < 4.0;
        if (physicallyAtStand && Conditions.hasReachedValidTargetOrReset(maid)) {
            clearTravelTarget(maid);
            openContext(level, maid, sort);
            return;
        }
        if (MaidNavigationRetryPolicy.shouldRetry(
                travelTicks, maid.getNavigation().isDone()) && operationTarget != null) {
            standPos = MoveUtil.selectPosForTarget(level, maid, operationTarget.getPos());
            if (standPos != null) setTravelTarget(maid, standPos);
        }
        if (travelTicks >= 200) {
            handleFailedOperation(level, sort);
            clearTravelTarget(maid);
            resetTransient();
        }
    }

    @Override
    protected void stop(ServerLevel level, EntityMaid maid, long gameTime) {
        closeContext(maid);
        clearTravelTarget(maid);
        resetTransient();
    }

    /** Runs the same exact-NBT return transaction after a task switch. */
    public void tickExternalRecovery(ServerLevel level, EntityMaid maid) {
        if (!externalRecovery) {
            resetTransient();
            ExtensionMemoryUtil.getPeriodicScan(maid).setPhase(PeriodicScanMemory.Phase.SORTING);
            ExtensionMemoryUtil.getMiscSort(maid).setPhase(MiscSortMemory.Phase.MOVING);
            externalRecovery = true;
        }
        MemoryUtil.setWorking(maid, true);
        tick(level, maid, level.getGameTime());
        if (ExtensionMemoryUtil.getMiscSort(maid).hasActiveCargo()) {
            MemoryUtil.setWorking(maid, true);
        }
    }

    public void stopExternalRecovery(EntityMaid maid) {
        closeContext(maid);
        clearTravelTarget(maid);
        resetTransient();
        externalRecovery = false;
    }

    private boolean prepareOperation(ServerLevel level, EntityMaid maid, MiscSortMemory sort) {
        normalizeLegacyCargo(maid.getAvailableInv(false), sort);
        MiscSortMemory.ActiveBatch batch = sort.getActiveBatch().orElse(null);
        if (batch == null) {
            MiscSortMemory.SourceJob job = nextUsableSourceJob(level, maid, sort);
            if (job == null) {
                MiscSortService.queueFinished(maid);
                return false;
            }
            CombinedInvWrapper inventory = maid.getAvailableInv(false);
            List<Integer> emptySlots = findEmptySlots(inventory);
            if (emptySlots.isEmpty()) {
                // Capacity is temporary state. Keep the whole source job and retry instead of
                // publishing a false completion while the maid has no transport slot available.
                sort.prependSourceJob(job);
                return false;
            }
            MiscBatchAllocator.Allocation allocation = MiscBatchAllocator.allocate(
                    sort.getScanGeneration(), job, emptySlots,
                    exact -> countExact(inventory, exact));
            if (!allocation.allocated()) {
                sort.prependSourceJob(allocation.remainder().orElse(job));
                return false;
            }
            allocation.remainder().ifPresent(sort::prependSourceJob);
            sort.setActiveBatch(allocation.activeBatch().orElseThrow());
            batch = sort.getActiveBatch().orElseThrow();
        }

        if (!batch.hasInFlight()) {
            if (!MiscSortService.isValidSource(level, maid, batch.source())) {
                sort.clearCompletedBatch();
                return false;
            }
            operation = Operation.COLLECT;
            operationTarget = batch.source();
            operationTargetKey = batch.canonicalSourceKey();
        } else {
            String destinationKey = selectDestination(level, maid, sort);
            batch = sort.getActiveBatch().orElseThrow();
            if (destinationKey == null) {
                operation = Operation.RETURN_SOURCE;
                operationTarget = batch.source();
                operationTargetKey = batch.canonicalSourceKey();
            } else {
                operation = Operation.DEPOSIT;
                operationTargetKey = destinationKey;
                operationTarget = batch.cargoLines().stream()
                        .filter(MiscSortMemory.CargoLine::hasInFlight)
                        .map(MiscSortMemory.CargoLine::currentDestination)
                        .flatMap(java.util.Optional::stream)
                        .filter(target -> MiscSortService.canonicalStorageKey(level, target)
                                .equals(destinationKey))
                        .findFirst().orElse(null);
                if (operationTarget == null) return false;
            }
        }

        Target live = MiscSortService.liveAccessibleTarget(level, maid, operationTarget);
        if (live == null) {
            handleFailedOperation(level, sort);
            operationTarget = null;
            return false;
        }
        operationTarget = live;
        standPos = MoveUtil.selectPosForTarget(level, maid, live.getPos());
        if (standPos == null) {
            handleFailedOperation(level, sort);
            operationTarget = null;
            return false;
        }
        setTravelTarget(maid, standPos);
        travelTicks = 0;
        return true;
    }

    private void setTravelTarget(EntityMaid maid, BlockPos target) {
        MemoryUtil.setTarget(maid, target, (float) Config.collectSpeed);
        // MoveUtil selected a stand position with Touhou Little Maid's path-finding rules.
        // Dispatch the route through the maid's active navigation as well as the upstream
        // storage-manager memory so ordinary cleanup cannot wait forever on memory alone.
        maid.getNavigation().moveTo(
                target.getX() + 0.5, target.getY(), target.getZ() + 0.5,
                Config.collectSpeed);
    }

    private static void clearTravelTarget(EntityMaid maid) {
        MemoryUtil.clearTarget(maid);
        maid.getNavigation().stop();
    }

    private MiscSortMemory.SourceJob nextUsableSourceJob(
            ServerLevel level, EntityMaid maid, MiscSortMemory sort) {
        while (sort.hasPendingSourceJobs()) {
            MiscSortMemory.SourceJob job = sort.pollNextSourceJob().orElse(null);
            if (job == null) return null;
            List<MiscSortMemory.PayloadPlan> payloads = job.payloads();
            if (payloads.isEmpty()) continue;
            if (!MiscSortService.isValidSource(level, maid, job.source())) continue;
            return new MiscSortMemory.SourceJob(job.source(), job.canonicalSourceKey(), payloads);
        }
        return null;
    }

    /** Advances unusable/full candidates and returns the first remaining physical destination. */
    private String selectDestination(ServerLevel level, EntityMaid maid, MiscSortMemory sort) {
        MiscSortMemory.ActiveBatch batch = sort.getActiveBatch().orElseThrow();
        List<MiscSortMemory.CargoLine> lines = batch.cargoLines();
        boolean changed = false;
        for (int i = 0; i < lines.size(); i++) {
            MiscSortMemory.CargoLine line = lines.get(i);
            if (!line.hasInFlight()) continue;
            while (line.currentDestination().isPresent()) {
                Target candidate = line.currentDestination().orElseThrow();
                String key = MiscSortService.canonicalStorageKey(level, candidate);
                if (!sort.isMarkedFull(batch.scanGeneration(), key, line.stack())
                        && MiscSortService.isValidDestination(level, maid, candidate, line.stack())) {
                    break;
                }
                line.advanceDestination();
                changed = true;
            }
            if (line.currentDestination().isEmpty()) {
                sort.markIgnoredAndPrunePending(
                        sort.resolveIgnoreKey(line.ignoreKey(), line.stack()));
            }
            lines.set(i, line);
        }
        if (changed) sort.replaceCargoLines(lines);
        return lines.stream()
                .filter(MiscSortMemory.CargoLine::hasInFlight)
                .map(MiscSortMemory.CargoLine::currentDestination)
                .flatMap(java.util.Optional::stream)
                .map(target -> MiscSortService.canonicalStorageKey(level, target))
                .findFirst().orElse(null);
    }

    private void openContext(ServerLevel level, EntityMaid maid, MiscSortMemory sort) {
        Target live = MiscSortService.liveAccessibleTarget(level, maid, operationTarget);
        if (live == null) {
            handleFailedOperation(level, sort);
            resetTransient();
            return;
        }
        operationTarget = live;
        IMaidStorage storage = MaidStorage.getInstance().getStorage(live.getType());
        if (storage == null) {
            handleFailedOperation(level, sort);
            resetTransient();
            return;
        }
        context = operation == Operation.COLLECT
                ? storage.onStartCollect(level, maid, live)
                : storage.onStartPlace(level, maid, live);
        boolean supported = operation == Operation.COLLECT
                ? context instanceof IStorageInteractContext || context instanceof IStorageExtractableContext
                : context instanceof IStorageInsertableContext;
        if (!supported || context == null) {
            if (context != null) context.finish();
            context = null;
            handleFailedOperation(level, sort);
            resetTransient();
            return;
        }
        context.start(maid, level, live);
        if (operation == Operation.COLLECT && context instanceof IStorageExtractableContext extractable) {
            extractable.setExtract(extractionTemplates(sort), ItemStackUtil.MATCH_TYPE.MATCHING);
        }
        // Upstream invalidates maid-bound locks whenever the holder is no longer a
        // StorageManageTask maid. External recovery therefore acquires an anonymous
        // target-counter lock only around each real mutation tick (see tickContext),
        // avoiding both invalidation races and an orphaned lock after entity unload.
        lock = externalRecovery
                ? StorageVisitLock.DUMMY
                : StorageVisitLock.getWriteLock(live, maid);
        MemoryUtil.setWorking(maid, true);
        stalledContextSteps = 0;
        lastContextCargoProgress = contextCargoProgress(sort);
        operationDone = false;
    }

    static List<ItemStack> extractionTemplates(MiscSortMemory sort) {
        List<ItemStack> templates = new ArrayList<>();
        for (MiscSortMemory.CargoLine line : sort.getActiveBatch().orElseThrow().cargoLines()) {
            int remaining = line.requestedCount() - line.inFlightCount();
            if (remaining <= 0) continue;
            int existingIndex = -1;
            for (int i = 0; i < templates.size(); i++) {
                if (ItemStack.isSameItemSameTags(templates.get(i), line.stack())) {
                    existingIndex = i;
                    break;
                }
            }
            if (existingIndex < 0) {
                templates.add(line.stack().copyWithCount(remaining));
            } else {
                ItemStack aggregate = templates.get(existingIndex);
                long total = (long) aggregate.getCount() + remaining;
                aggregate.setCount((int) Math.min(Integer.MAX_VALUE, total));
            }
        }
        return List.copyOf(templates);
    }

    private boolean tickContext(ServerLevel level, EntityMaid maid, MiscSortMemory sort) {
        if (externalRecovery) {
            StorageVisitLock.LockContext tickLock =
                    StorageVisitLock.getWriteLock(operationTarget, null);
            try {
                if (!tickLock.checkAndTryGrantLock() || !breath.breathTick(maid)) return false;
                runContextOperation(level, maid, sort);
                return true;
            } finally {
                tickLock.release();
            }
        }
        if (!lock.checkAndTryGrantLock() || !breath.breathTick(maid)) return false;
        runContextOperation(level, maid, sort);
        return true;
    }

    private void runContextOperation(ServerLevel level, EntityMaid maid, MiscSortMemory sort) {
        switch (operation) {
            case COLLECT -> tickCollect(level, maid, sort);
            case DEPOSIT -> tickDeposit(level, maid, sort);
            case RETURN_SOURCE -> tickReturn(level, maid, sort);
        }
    }

    private static int contextCargoProgress(MiscSortMemory sort) {
        long total = sort.getActiveBatch().stream()
                .flatMap(batch -> batch.cargoLines().stream())
                .mapToLong(MiscSortMemory.CargoLine::inFlightCount)
                .sum();
        return (int) Math.min(Integer.MAX_VALUE, total);
    }

    private void tickCollect(ServerLevel level, EntityMaid maid, MiscSortMemory sort) {
        CombinedInvWrapper inventory = maid.getAvailableInv(false);
        java.util.function.Function<ItemStack, ItemStack> collector = storageStack -> {
            if (storageStack == null || storageStack.isEmpty()) return ItemStack.EMPTY;
            List<MiscSortMemory.CargoLine> lines = sort.getActiveBatch().orElseThrow().cargoLines();
            ItemStack left = storageStack.copy();
            int movedTotal = 0;
            for (int i = 0; i < lines.size() && !left.isEmpty(); i++) {
                MiscSortMemory.CargoLine line = lines.get(i);
                if (!ItemStack.isSameItemSameTags(left, line.stack())) continue;
                int wanted = Math.min(left.getCount(), line.requestedCount() - line.inFlightCount());
                if (wanted <= 0) continue;
                ItemStack remainder = inventory.insertItem(
                        line.transportSlot(), left.copyWithCount(wanted), false);
                int moved = wanted - remainder.getCount();
                if (moved <= 0) continue;
                line.setInFlightCount(line.inFlightCount() + moved);
                lines.set(i, line);
                left.shrink(moved);
                movedTotal += moved;
            }
            if (movedTotal > 0) {
                sort.replaceCargoLines(lines);
                ViewedInventoryUtil.ambitiousRemoveItemAndSync(
                        maid, level, operationTarget, storageStack, movedTotal);
            }
            return left;
        };

        if (context instanceof IStorageInteractContext interact
                && context instanceof ISlotBasedStorage slots) {
            int steps = Math.min(MAX_SLOT_CONTEXT_STEPS, Math.max(1, slots.getSlots()));
            for (int step = 0; step < steps && !context.isDone() && !collectionComplete(sort); step++) {
                interact.tick(collector);
            }
        } else if (context instanceof IStorageInteractContext interact) {
            interact.tick(collector);
        } else if (context instanceof IStorageExtractableContext extractable) {
            extractable.tick(collector);
        }
        operationDone = collectionComplete(sort);
    }

    private static boolean collectionComplete(MiscSortMemory sort) {
        return sort.getActiveBatch().orElseThrow().cargoLines().stream()
                .allMatch(line -> line.inFlightCount() >= line.requestedCount());
    }

    private void tickDeposit(ServerLevel level, EntityMaid maid, MiscSortMemory sort) {
        if (!(context instanceof IStorageInsertableContext insertable)) {
            handleFailedOperation(level, sort);
            operationDone = true;
            return;
        }
        CombinedInvWrapper inventory = maid.getAvailableInv(false);
        MiscSortMemory.ActiveBatch batch = sort.getActiveBatch().orElseThrow();
        List<MiscSortMemory.CargoLine> lines = batch.cargoLines();
        boolean changed = false;
        List<Integer> operationLines = MiscCargoAccounting.lineIndexesAtDestination(
                lines, operationTargetKey,
                target -> MiscSortService.canonicalStorageKey(level, target));
        for (int i : operationLines) {
            MiscSortMemory.CargoLine line = lines.get(i);
            Target candidate = line.currentDestination().orElseThrow();
            String candidateKey = MiscSortService.canonicalStorageKey(level, candidate);

            if (sort.isMarkedFull(batch.scanGeneration(), candidateKey, line.stack())
                    || context instanceof IFilterable filter && !filter.isAvailable(line.stack())) {
                line.advanceDestination();
                markIgnoredWhenExhausted(sort, line);
                lines.set(i, line);
                changed = true;
                continue;
            }

            ItemStack payload = availablePayload(inventory, line);
            if (payload.isEmpty()) continue;
            ItemStack insertionRemainder = insertable.insert(payload.copy());
            int remainderCount = validRemainderCount(payload, insertionRemainder);
            MiscCargoAccounting.InsertionDecision result = MiscCargoAccounting.afterDestinationInsertion(
                    line.inFlightCount(), payload.getCount(), remainderCount);
            int moved = result.moved();
            if (moved > 0) {
                removeTransactionItems(inventory, line, moved);
                ViewedInventoryUtil.ambitiousAddItemAndSync(
                        maid, level, operationTarget, payload.copyWithCount(moved));
                line.setInFlightCount(result.journalRemaining());
                changed = true;
            }
            if (result.markCandidateFull()) {
                sort.markFull(candidateKey, line.stack());
                line.advanceDestination();
                markIgnoredWhenExhausted(sort, line);
                changed = true;
            }
            lines.set(i, line);
        }
        if (changed) sort.replaceCargoLines(lines);
        clearBatchIfDelivered(maid, sort);
        MiscSortMemory.ActiveBatch remainingBatch = sort.getActiveBatch().orElse(null);
        operationDone = remainingBatch == null
                || MiscCargoAccounting.destinationOperationComplete(
                        remainingBatch.cargoLines(), operationTargetKey,
                        target -> MiscSortService.canonicalStorageKey(level, target));
    }

    private void tickReturn(ServerLevel level, EntityMaid maid, MiscSortMemory sort) {
        if (!(context instanceof IStorageInsertableContext insertable)) {
            operationDone = true;
            return;
        }
        CombinedInvWrapper inventory = maid.getAvailableInv(false);
        List<MiscSortMemory.CargoLine> lines = sort.getActiveBatch().orElseThrow().cargoLines();
        boolean changed = false;
        for (int i = 0; i < lines.size(); i++) {
            MiscSortMemory.CargoLine line = lines.get(i);
            if (!line.hasInFlight()) continue;
            ItemStack payload = availablePayload(inventory, line);
            if (payload.isEmpty()) continue;
            ItemStack insertionRemainder = insertable.insert(payload.copy());
            int remainderCount = validRemainderCount(payload, insertionRemainder);
            MiscCargoAccounting.InsertionDecision result = MiscCargoAccounting.afterSourceInsertion(
                    line.inFlightCount(), payload.getCount(), remainderCount);
            int moved = result.moved();
            if (moved <= 0) continue;
            removeTransactionItems(inventory, line, moved);
            ViewedInventoryUtil.ambitiousAddItemAndSync(
                    maid, level, operationTarget, payload.copyWithCount(moved));
            line.setInFlightCount(result.journalRemaining());
            lines.set(i, line);
            changed = true;
        }
        if (changed) sort.replaceCargoLines(lines);
        clearBatchIfDelivered(maid, sort);
        operationDone = !sort.hasActiveCargo();
    }

    private static int validRemainderCount(ItemStack payload, ItemStack remainder) {
        if (remainder == null) return payload.getCount();
        if (!remainder.isEmpty()
                && (!ItemStack.isSameItemSameTags(payload, remainder)
                || remainder.getCount() > payload.getCount())) return payload.getCount();
        return remainder.isEmpty() ? 0 : remainder.getCount();
    }

    private static ItemStack availablePayload(
            CombinedInvWrapper inventory, MiscSortMemory.CargoLine line) {
        int total = countExact(inventory, line.stack());
        int baseline = Math.max(0, line.baselineCount());
        int available = MiscCargoAccounting.available(line.inFlightCount(), total, baseline);
        return available <= 0 ? ItemStack.EMPTY : line.stack().copyWithCount(available);
    }

    private static void removeTransactionItems(
            CombinedInvWrapper inventory, MiscSortMemory.CargoLine line, int count) {
        int removable = Math.max(0, countExact(inventory, line.stack()) - Math.max(0, line.baselineCount()));
        if (count > removable) {
            throw new IllegalStateException("Storage accepted more transaction items than the maid carried");
        }
        int remaining = removeFromSlot(inventory, line.transportSlot(), line.stack(), count);
        for (int slot = 0; slot < inventory.getSlots() && remaining > 0; slot++) {
            if (slot == line.transportSlot()) continue;
            remaining = removeFromSlot(inventory, slot, line.stack(), remaining);
        }
        if (remaining != 0) {
            throw new IllegalStateException("Exact transaction cargo disappeared during insertion");
        }
    }

    private static int removeFromSlot(
            CombinedInvWrapper inventory, int slot, ItemStack expected, int count) {
        if (slot < 0 || slot >= inventory.getSlots() || count <= 0) return count;
        ItemStack actual = inventory.getStackInSlot(slot);
        if (!ItemStack.isSameItemSameTags(expected, actual)) return count;
        int removed = Math.min(count, actual.getCount());
        ItemStack left = actual.copy();
        left.shrink(removed);
        inventory.setStackInSlot(slot, left);
        return count - removed;
    }

    private void handleExhaustedContext(ServerLevel level, MiscSortMemory sort) {
        if (operation == Operation.COLLECT) {
            if (!sort.hasActiveCargo() && sort.getActiveBatch().isPresent()) {
                sort.getActiveBatch().orElseThrow().cargoLines().forEach(line ->
                        sort.markIgnoredAndPrunePending(
                                sort.resolveIgnoreKey(line.ignoreKey(), line.stack())));
                sort.clearCompletedBatch();
            }
        } else if (operation == Operation.DEPOSIT) {
            advanceOperationDestination(level, sort, false);
        }
        // RETURN_SOURCE deliberately keeps the whole exact ledger and retries its unique source.
    }

    private void handleFailedOperation(ServerLevel level, MiscSortMemory sort) {
        if (operation == Operation.DEPOSIT) {
            advanceOperationDestination(level, sort, false);
        } else if (operation == Operation.COLLECT && !sort.hasActiveCargo()
                && sort.getActiveBatch().isPresent()) {
            sort.clearCompletedBatch();
        }
        // RETURN_SOURCE failure intentionally retains the physical cargo journal.
    }

    private void advanceOperationDestination(
            ServerLevel level, MiscSortMemory sort, boolean markFull) {
        MiscSortMemory.ActiveBatch batch = sort.getActiveBatch().orElse(null);
        if (batch == null || operationTargetKey == null) return;
        List<MiscSortMemory.CargoLine> lines = batch.cargoLines();
        boolean changed = false;
        for (int i = 0; i < lines.size(); i++) {
            MiscSortMemory.CargoLine line = lines.get(i);
            if (!line.hasInFlight() || line.currentDestination().isEmpty()) continue;
            String key = MiscSortService.canonicalStorageKey(
                    level, line.currentDestination().orElseThrow());
            if (!key.equals(operationTargetKey)) continue;
            if (markFull) sort.markFull(key, line.stack());
            line.advanceDestination();
            markIgnoredWhenExhausted(sort, line);
            lines.set(i, line);
            changed = true;
        }
        if (changed) sort.replaceCargoLines(lines);
    }

    private static void markIgnoredWhenExhausted(
            MiscSortMemory sort, MiscSortMemory.CargoLine line) {
        if (line.hasInFlight() && line.currentDestination().isEmpty()) {
            sort.markIgnoredAndPrunePending(
                    sort.resolveIgnoreKey(line.ignoreKey(), line.stack()));
        }
    }

    private void clearBatchIfDelivered(EntityMaid maid, MiscSortMemory sort) {
        if (sort.hasActiveCargo() || sort.getActiveBatch().isEmpty()) return;
        sort.clearCompletedBatch();
        if (!MiscSortService.isPolicyCurrent(maid)) invalidateForPolicyChange(maid, sort);
    }

    private static void invalidateForPolicyChange(EntityMaid maid, MiscSortMemory sort) {
        sort.clearUnstartedWork();
        StorageScanService.cancelPeriodic(maid);
        ExtensionMemoryUtil.getPeriodicScan(maid).requestImmediateScan();
    }

    private void finishOperation(EntityMaid maid) {
        closeContext(maid);
        operation = null;
        operationTarget = null;
        operationTargetKey = null;
        standPos = null;
        operationDone = false;
        travelTicks = 0;
        stalledContextSteps = 0;
        lastContextCargoProgress = 0;
    }

    private void closeContext(EntityMaid maid) {
        StorageVisitLock.LockContext closingLock = lock;
        IStorageContext closingContext = context;
        lock = StorageVisitLock.DUMMY;
        context = null;
        try {
            closingLock.release();
        } finally {
            try {
                if (closingContext != null) closingContext.finish();
            } finally {
                MemoryUtil.setWorking(maid, false);
            }
        }
    }

    private void resetTransient() {
        operation = null;
        operationTarget = null;
        operationTargetKey = null;
        standPos = null;
        context = null;
        lock = StorageVisitLock.DUMMY;
        operationDone = false;
        travelTicks = 0;
        stalledContextSteps = 0;
        lastContextCargoProgress = 0;
    }

    private static void normalizeLegacyCargo(
            CombinedInvWrapper inventory, MiscSortMemory sort) {
        MiscSortMemory.ActiveBatch batch = sort.getActiveBatch().orElse(null);
        if (batch == null) return;
        List<MiscSortMemory.CargoLine> lines = batch.cargoLines();
        boolean changed = false;
        for (int i = 0; i < lines.size(); i++) {
            MiscSortMemory.CargoLine line = lines.get(i);
            if (line.baselineCount() < 0) {
                line = line.withBaselineCount(Math.max(
                        0, countExact(inventory, line.stack()) - line.inFlightCount()));
                changed = true;
            }
            String ignoreKey = sort.resolveIgnoreKey(line.ignoreKey(), line.stack());
            if (!ignoreKey.equals(line.ignoreKey())) {
                line = line.withIgnoreKey(ignoreKey);
                changed = true;
            }
            lines.set(i, line);
        }
        if (changed) sort.replaceCargoLines(lines);
    }

    private static int countExact(CombinedInvWrapper inventory, ItemStack expected) {
        long total = 0L;
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            ItemStack actual = inventory.getStackInSlot(slot);
            if (ItemStack.isSameItemSameTags(expected, actual)) total += actual.getCount();
        }
        return (int) Math.min(Integer.MAX_VALUE, total);
    }

    private static List<Integer> findEmptySlots(CombinedInvWrapper inventory) {
        List<Integer> slots = new ArrayList<>();
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            if (inventory.getStackInSlot(slot).isEmpty()) slots.add(slot);
        }
        return List.copyOf(slots);
    }
}
