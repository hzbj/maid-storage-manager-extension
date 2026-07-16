package io.github.maidstorageextension.scan;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import io.github.maidstorageextension.compat.MiscStorageAccess;
import io.github.maidstorageextension.data.ExtensionConfigData;
import io.github.maidstorageextension.maid.ExtensionMemoryUtil;
import io.github.maidstorageextension.maid.memory.MiscSortMemory;
import io.github.maidstorageextension.maid.memory.PeriodicScanMemory;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;
import studio.fantasyit.maid_storage_manager.maid.data.StorageManagerConfigData;
import studio.fantasyit.maid_storage_manager.maid.memory.ViewedInventoryMemory;
import studio.fantasyit.maid_storage_manager.storage.MaidStorage;
import studio.fantasyit.maid_storage_manager.storage.Target;
import studio.fantasyit.maid_storage_manager.storage.base.IFilterable;
import studio.fantasyit.maid_storage_manager.storage.base.IMaidStorage;
import studio.fantasyit.maid_storage_manager.storage.base.IStorageContext;
import studio.fantasyit.maid_storage_manager.util.Conditions;
import studio.fantasyit.maid_storage_manager.util.MemoryUtil;
import studio.fantasyit.maid_storage_manager.util.MoveUtil;
import studio.fantasyit.maid_storage_manager.util.RequestItemUtil;
import studio.fantasyit.maid_storage_manager.util.StorageAccessUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/** Converts one fully completed patrol cache into safe source -> destination transfers. */
public final class MiscSortService {
    private MiscSortService() {
    }

    public static boolean isKeepCurrentMode(EntityMaid maid) {
        return !Conditions.noSortPlacement(maid)
                && StorageManagerConfigData.get(maid).itemTypeLimit() == 0;
    }

    public static MiscSortPlanner.IgnorePolicy policyFor(EntityMaid maid) {
        return ExtensionConfigData.get(maid).miscSortMatchNbt()
                ? MiscSortPlanner.IgnorePolicy.EXACT_STACK
                : MiscSortPlanner.IgnorePolicy.ITEM_ID;
    }

    public static boolean isPolicyCurrent(EntityMaid maid) {
        return ExtensionMemoryUtil.getMiscSort(maid).getIgnorePolicy() == policyFor(maid);
    }

    public static void planFromLatestCache(ServerLevel level, EntityMaid maid) {
        PeriodicScanMemory patrol = ExtensionMemoryUtil.getPeriodicScan(maid);
        MiscSortMemory sort = ExtensionMemoryUtil.getMiscSort(maid);
        if (!patrol.hasCompleteInspectionEvidence() || !isKeepCurrentMode(maid)) {
            sort.clearUnstartedWork();
            sort.setPhase(MiscSortMemory.Phase.REFRESH_PENDING);
            patrol.setPhase(PeriodicScanMemory.Phase.REFRESH_PENDING);
            return;
        }

        MiscSortPlanner.IgnorePolicy policy = policyFor(maid);
        sort.beginGeneration(patrol.getScanGeneration(), policy);

        Map<Target, List<ViewedInventoryMemory.ItemCount>> cache =
                MemoryUtil.getViewedInventory(maid).positionFlatten();
        LinkedHashMap<String, Target> inspected = new LinkedHashMap<>();
        patrol.getSuccessfullyInspectedTargets().stream()
                .sorted(Comparator.comparing(Target::toStoreString))
                .forEach(target -> inspected.putIfAbsent(canonicalStorageKey(level, target), target));

        List<MiscSortPlanner.StackView<ItemStack>> sourceItems = inspected.values().stream()
                .filter(target -> MiscStorageAccess.isMiscStorage(level, target))
                .flatMap(target -> toPlannerStacks(cachedItemsFor(level, cache, target)).stream())
                .toList();

        List<MiscSortPlanner.StorageView<Target, ItemStack>> storages = new ArrayList<>();
        for (Target inspectedTarget : inspected.values()) {
            Target target = liveAccessibleTarget(level, maid, inspectedTarget);
            if (target == null) continue;
            IMaidStorage storage = MaidStorage.getInstance().getStorage(target.getType());
            if (storage == null) continue;
            boolean misc = MiscStorageAccess.isMiscStorage(level, target);
            boolean request = RequestItemUtil.isRequestTarget(level, maid, target);
            List<MiscSortPlanner.StackView<ItemStack>> stacks =
                    toPlannerStacks(cachedItemsFor(level, cache, target));
            List<String> acceptedWhitelistKeys = misc
                    ? List.of()
                    : acceptedWhitelistKeys(level, maid, storage, target, sourceItems);
            storages.add(new MiscSortPlanner.StorageView<>(
                    target,
                    canonicalStorageKey(level, target),
                    target.toStoreString(),
                    target.getPos().getX(), target.getPos().getY(), target.getPos().getZ(),
                    misc,
                    request,
                    // A batch source must be reversible. Collect-only backends (notably
                    // Create stock tickers) cannot accept an overflow return and may have
                    // asynchronous package requests that outlive the persisted journal.
                    misc && storage.supportCollect() && storage.supportPlace() && !request,
                    !misc && storage.supportPlace() && !request,
                    acceptedWhitelistKeys,
                    stacks));
        }

        MiscSortPlanner.BatchPlan<Target, ItemStack> planned = MiscSortPlanner.planBatch(
                storages, policy, sort.getIgnoredKeys());
        planned.ignoredKeys().forEach(sort::markIgnored);

        List<MiscSortMemory.SourceJob> jobs = new ArrayList<>();
        LinkedHashSet<String> capacityIgnored = new LinkedHashSet<>();
        for (MiscSortPlanner.SourceJob<Target, ItemStack> sourceJob : planned.sourceJobs()) {
            List<MiscSortMemory.PayloadPlan> payloads = new ArrayList<>();
            for (MiscSortPlanner.PayloadPlan<Target, ItemStack> payload : sourceJob.payloads()) {
                if (capacityIgnored.contains(payload.ignoreKey())) continue;
                ItemStack exact = payload.stack().stack().copyWithCount(1);
                List<Target> destinations = new ArrayList<>();
                for (MiscSortPlanner.Destination<Target> destination : payload.destinations()) {
                    if (sort.isMarkedFull(patrol.getScanGeneration(), destination.physicalKey(), exact)) {
                        continue;
                    }
                    Target target = destination.target();
                    if (!isValidDestination(level, maid, target, exact)) continue;
                    ItemStack probeStack = exact.copyWithCount(Math.max(1, exact.getMaxStackSize()));
                    ItemHandlerCapacityProbe.Result capacity =
                            ItemHandlerCapacityProbe.probe(level, target, probeStack);
                    if (capacity.isFull()) {
                        sort.markFull(destination.physicalKey(), exact);
                        continue;
                    }
                    destinations.add(target);
                }
                if (destinations.isEmpty()) {
                    sort.markIgnored(payload.ignoreKey());
                    capacityIgnored.add(payload.ignoreKey());
                    continue;
                }
                payloads.add(new MiscSortMemory.PayloadPlan(
                        exact, payload.stack().count(), payload.ignoreKey(), destinations));
            }
            if (!payloads.isEmpty()) {
                jobs.add(new MiscSortMemory.SourceJob(sourceJob.source(),
                        sourceJob.sourcePhysicalKey(), payloads));
            }
        }

        sort.replaceSourceJobs(jobs);
        sort.replaceTasks(List.of());
        if (jobs.isEmpty()) {
            sort.setPhase(MiscSortMemory.Phase.REFRESH_PENDING);
            patrol.setPhase(PeriodicScanMemory.Phase.REFRESH_PENDING);
        } else {
            sort.setPhase(MiscSortMemory.Phase.READY);
            patrol.setPhase(PeriodicScanMemory.Phase.SORT_PENDING);
        }
    }

    public static boolean isValidSource(ServerLevel level, EntityMaid maid, Target target) {
        Target live = liveAccessibleTarget(level, maid, target);
        if (live == null || !MiscStorageAccess.isMiscStorage(level, live)) return false;
        IMaidStorage storage = MaidStorage.getInstance().getStorage(live.getType());
        return storage != null && storage.supportCollect() && storage.supportPlace()
                && !RequestItemUtil.isRequestTarget(level, maid, live);
    }

    public static boolean isValidSource(ServerLevel level, EntityMaid maid, Target target, ItemStack exact) {
        Target live = liveAccessibleTarget(level, maid, target);
        if (live == null || !isValidSource(level, maid, live)) return false;
        return cachedItemsFor(level, MemoryUtil.getViewedInventory(maid).positionFlatten(), live).stream()
                .anyMatch(item -> item.count() > 0 && ItemStack.isSameItemSameTags(item.item(), exact));
    }

    public static boolean isValidDestination(
            ServerLevel level, EntityMaid maid, Target target, ItemStack exact) {
        Target live = liveAccessibleTarget(level, maid, target);
        if (live == null || MiscStorageAccess.isMiscStorage(level, live)
                || RequestItemUtil.isRequestTarget(level, maid, live)) return false;
        IMaidStorage storage = MaidStorage.getInstance().getStorage(live.getType());
        return storage != null && storage.supportPlace()
                && previewAccepts(level, maid, storage, live, exact);
    }

    public static @Nullable Target liveAccessibleTarget(
            ServerLevel level, EntityMaid maid, Target target) {
        BlockPos pos = target.getPos();
        if (!level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) return null;
        Target live = MaidStorage.getInstance().isValidTarget(level, maid, target);
        if (live == null || StorageAccessUtil.findTargetRewrite(level, maid, live, false).isEmpty()
                || MoveUtil.selectPosForTarget(level, maid, live.getPos()) == null) return null;
        return live;
    }

    public static void queueFinished(EntityMaid maid) {
        ExtensionMemoryUtil.getMiscSort(maid).setPhase(MiscSortMemory.Phase.REFRESH_PENDING);
        ExtensionMemoryUtil.getPeriodicScan(maid).setPhase(PeriodicScanMemory.Phase.REFRESH_PENDING);
    }

    public static String canonicalStorageKey(ServerLevel level, Target target) {
        List<BlockPos> positions = new ArrayList<>();
        positions.add(target.getPos());
        StorageAccessUtil.checkNearByContainers(level, target.getPos(), positions::add);
        BlockPos min = positions.stream().min(Comparator.comparingLong(BlockPos::asLong))
                .orElse(target.getPos());
        return target.getType() + "@" + min.asLong();
    }

    private static boolean previewAccepts(
            ServerLevel level, EntityMaid maid, IMaidStorage storage, Target target, ItemStack exact) {
        IStorageContext context = storage.onPreviewFilter(level, maid, target);
        if (context == null) return true;
        try {
            context.start(maid, level, target);
            return !(context instanceof IFilterable filter) || filter.isAvailable(exact);
        } finally {
            context.finish();
        }
    }

    private static List<String> acceptedWhitelistKeys(
            ServerLevel level,
            EntityMaid maid,
            IMaidStorage storage,
            Target target,
            Collection<MiscSortPlanner.StackView<ItemStack>> sourceItems) {
        if (sourceItems.isEmpty()) return List.of();
        IStorageContext context = storage.onPreviewFilter(level, maid, target);
        if (context == null) return List.of();
        try {
            context.start(maid, level, target);
            if (!(context instanceof IFilterable filter) || !filter.isWhitelist()) return List.of();
            LinkedHashSet<String> accepted = new LinkedHashSet<>();
            for (MiscSortPlanner.StackView<ItemStack> source : sourceItems) {
                if (filter.isAvailable(source.stack())) accepted.add(source.exactStackKey());
            }
            return List.copyOf(accepted);
        } finally {
            context.finish();
        }
    }

    private static List<ViewedInventoryMemory.ItemCount> cachedItemsFor(
            ServerLevel level,
            Map<Target, List<ViewedInventoryMemory.ItemCount>> cache,
            Target target) {
        List<ViewedInventoryMemory.ItemCount> direct = cache.get(target);
        if (direct != null) return List.copyOf(direct);
        String physicalKey = canonicalStorageKey(level, target);
        return cache.entrySet().stream()
                .filter(entry -> canonicalStorageKey(level, entry.getKey()).equals(physicalKey))
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(Target::toStoreString)))
                .map(Map.Entry::getValue)
                .findFirst()
                .map(List::copyOf)
                .orElseGet(List::of);
    }

    private static List<MiscSortPlanner.StackView<ItemStack>> toPlannerStacks(
            Collection<ViewedInventoryMemory.ItemCount> items) {
        List<MiscSortPlanner.StackView<ItemStack>> result = new ArrayList<>();
        for (ViewedInventoryMemory.ItemCount item : items) {
            if (item.item().isEmpty() || item.count() <= 0) continue;
            ItemStack exact = item.item().copyWithCount(1);
            var itemId = ForgeRegistries.ITEMS.getKey(exact.getItem());
            if (itemId == null) continue;
            CompoundTag saved = exact.save(new CompoundTag());
            result.add(new MiscSortPlanner.StackView<>(
                    itemId.toString(), saved.toString(), exact, item.count()));
        }
        return result;
    }
}
