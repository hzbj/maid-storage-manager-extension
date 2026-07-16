package io.github.maidstorageextension.scan;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jetbrains.annotations.Nullable;
import io.github.maidstorageextension.data.ExtensionConfigData;
import io.github.maidstorageextension.data.MaintenanceStatusData;
import io.github.maidstorageextension.data.PeriodicScanInterval;
import io.github.maidstorageextension.item.InventoryMaintenanceDevice;
import io.github.maidstorageextension.maid.ExtensionMemoryUtil;
import io.github.maidstorageextension.maid.memory.MiscSortMemory;
import io.github.maidstorageextension.maid.memory.PeriodicScanMemory;
import studio.fantasyit.maid_storage_manager.maid.memory.ViewedInventoryMemory;
import studio.fantasyit.maid_storage_manager.storage.MaidStorage;
import studio.fantasyit.maid_storage_manager.storage.StoragePredictor;
import studio.fantasyit.maid_storage_manager.storage.Target;
import studio.fantasyit.maid_storage_manager.util.MemoryUtil;
import studio.fantasyit.maid_storage_manager.util.MoveUtil;
import studio.fantasyit.maid_storage_manager.util.StorageAccessUtil;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Shared, rate-limited storage discovery used by periodic scans and reachable-storage debug. */
public final class StorageScanService {
    public static final int CANDIDATES_PER_TICK = 16;

    public enum CandidateStatus {
        REACHABLE,
        UNREACHABLE,
        DENIED
    }

    public record ScanScope(BlockPos center, double radius, boolean usesWorkArea) {
        public boolean contains(BlockPos pos) {
            return center.distSqr(pos) <= radius * radius;
        }

        public String describe() {
            return (usesWorkArea ? "work" : "local") + " sphere @ "
                    + center.getX() + "," + center.getY() + "," + center.getZ()
                    + " r=" + String.format(java.util.Locale.ROOT, "%.1f", radius);
        }
    }

    public record CandidateResult(BlockPos pos, CandidateStatus status, @Nullable Target target, String reason) {
    }

    public static final class ScanSession {
        private final UUID maidUuid;
        private final ResourceKey<Level> dimension;
        private final ScanScope scope;
        private final ArrayDeque<BlockPos> candidates;
        private final List<CandidateResult> results = new ArrayList<>();
        private final LinkedHashMap<String, Target> reachableTargets = new LinkedHashMap<>();
        private final Set<String> reportedStorageKeys = new HashSet<>();
        private final int totalCandidates;
        private int processedCandidates;

        private ScanSession(EntityMaid maid, ScanScope scope, List<BlockPos> candidates) {
            this.maidUuid = maid.getUUID();
            this.dimension = maid.level().dimension();
            this.scope = scope;
            this.candidates = new ArrayDeque<>(candidates);
            this.totalCandidates = candidates.size();
        }

        public UUID maidUuid() {
            return maidUuid;
        }

        public ResourceKey<Level> dimension() {
            return dimension;
        }

        public ScanScope scope() {
            return scope;
        }

        public List<CandidateResult> results() {
            return List.copyOf(results);
        }

        public List<Target> reachableTargets() {
            return List.copyOf(reachableTargets.values());
        }

        public boolean isDone() {
            return candidates.isEmpty();
        }

        public int processedCandidates() {
            return processedCandidates;
        }

        public int totalCandidates() {
            return totalCandidates;
        }
    }

    private static final Map<UUID, ScanSession> PERIODIC_SESSIONS = new HashMap<>();
    private static final Map<UUID, InspectionDispatch> INSPECTION_DISPATCHES = new HashMap<>();

    private record InspectionDispatch(Target target, int failedDispatches) {
    }

    private StorageScanService() {
    }

    public static ScanScope getScope(EntityMaid maid) {
        if (maid.hasRestriction()) {
            return new ScanScope(maid.getRestrictCenter(), maid.getRestrictRadius(), true);
        }
        return new ScanScope(maid.blockPosition(), ExtensionConfigData.get(maid).localScanRadius(), false);
    }

    public static boolean isInsideScope(EntityMaid maid, BlockPos pos) {
        return getScope(maid).contains(pos);
    }

    public static ScanSession createSession(ServerLevel level, EntityMaid maid) {
        ScanScope scope = getScope(maid);
        List<BlockPos> candidates = new ArrayList<>();
        int minChunkX = (int) Math.floor((scope.center().getX() - scope.radius()) / 16.0);
        int maxChunkX = (int) Math.floor((scope.center().getX() + scope.radius()) / 16.0);
        int minChunkZ = (int) Math.floor((scope.center().getZ() - scope.radius()) / 16.0);
        int maxChunkZ = (int) Math.floor((scope.center().getZ() + scope.radius()) / 16.0);
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                if (!level.hasChunk(chunkX, chunkZ)) {
                    continue;
                }
                LevelChunk chunk = level.getChunk(chunkX, chunkZ);
                for (BlockPos pos : chunk.getBlockEntities().keySet()) {
                    if (scope.contains(pos)) {
                        candidates.add(pos.immutable());
                    }
                }
            }
        }
        candidates.sort(Comparator.comparingDouble(scope.center()::distSqr));
        return new ScanSession(maid, scope, candidates);
    }

    /** Processes at most 16 block-entity candidates. Returns true when the session is complete. */
    public static boolean tickSession(ServerLevel level, EntityMaid maid, ScanSession session) {
        if (!maid.getUUID().equals(session.maidUuid()) || !level.dimension().equals(session.dimension())) {
            return true;
        }
        int budget = CANDIDATES_PER_TICK;
        while (budget-- > 0 && !session.candidates.isEmpty()) {
            BlockPos pos = session.candidates.removeFirst();
            session.processedCandidates++;
            if (!level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) {
                continue;
            }
            CandidateResult result = analyze(level, maid, pos);
            if (result.target() == null) {
                session.results.add(result);
                continue;
            }
            String key = canonicalStorageKey(level, result.target());
            if (session.reportedStorageKeys.add(key)) {
                session.results.add(result);
            }
            if (result.status() == CandidateStatus.REACHABLE) {
                session.reachableTargets.putIfAbsent(key, result.target());
            }
        }
        return session.isDone();
    }

    public static CandidateResult analyze(ServerLevel level, EntityMaid maid, BlockPos pos) {
        Target raw = MaidStorage.getInstance().isValidTarget(level, maid, pos);
        if (raw == null || !StoragePredictor.isViewable(raw)) {
            return new CandidateResult(pos, CandidateStatus.DENIED, raw, "unsupported_storage");
        }
        List<Target> allowed = StorageAccessUtil.findTargetRewrite(level, maid, raw, false).stream()
                .filter(StoragePredictor::isViewable)
                .filter(target -> MaidStorage.getInstance().isValidTarget(level, maid, target) != null)
                .distinct()
                .toList();
        if (allowed.isEmpty()) {
            return new CandidateResult(pos, CandidateStatus.DENIED, raw, "access_denied");
        }
        for (Target target : allowed) {
            if (MoveUtil.selectPosForTarget(level, maid, target.getPos()) != null) {
                return new CandidateResult(pos, CandidateStatus.REACHABLE, target, "reachable");
            }
        }
        return new CandidateResult(pos, CandidateStatus.UNREACHABLE, allowed.get(0), "no_safe_path");
    }

    public static void tickPeriodic(ServerLevel level, EntityMaid maid, boolean mayUseIdleTime) {
        PeriodicScanMemory memory = ExtensionMemoryUtil.getPeriodicScan(maid);
        PeriodicScanInterval interval = ExtensionConfigData.get(maid).periodicScanInterval();
        boolean enabled = (interval != PeriodicScanInterval.DISABLED || memory.isForceScanRequested())
                && InventoryMaintenanceDevice.findOn(maid).isPresent();
        if (!enabled) {
            cancelPeriodic(maid);
            return;
        }

        if (memory.getPhase() == PeriodicScanMemory.Phase.SCANNING) {
            dispatchNextInspection(level, maid);
            ViewedInventoryMemory viewed = MemoryUtil.getViewedInventory(maid);
            if (memory.hasCompleteInspectionEvidence() && !viewed.isViewing() && !viewed.hasTarget()) {
                MiscSortService.planFromLatestCache(level, maid);
                MaintenanceFeedbackService.setPhase(maid,
                        memory.getPhase() == PeriodicScanMemory.Phase.SORT_PENDING
                                ? MaintenanceStatusData.Phase.CLEANING
                                : MaintenanceStatusData.Phase.REFRESHING);
            }
        }

        if (memory.getPhase() == PeriodicScanMemory.Phase.IDLE
                && memory.getNextScanGameTime() <= level.getGameTime()
                && mayUseIdleTime) {
            ScanSession session = createSession(level, maid);
            PERIODIC_SESSIONS.put(maid.getUUID(), session);
            memory.setPhase(PeriodicScanMemory.Phase.PREPARING);
            memory.replaceTargets(List.of());
            memory.setProgress(0, session.totalCandidates());
            MiscSortMemory sort = ExtensionMemoryUtil.getMiscSort(maid);
            sort.clearUnstartedWork();
            if (!sort.hasInFlight()) sort.setPhase(MiscSortMemory.Phase.IDLE);
            MaintenanceFeedbackService.setPhase(maid, MaintenanceStatusData.Phase.DISCOVERING);
        }

        if (memory.getPhase() == PeriodicScanMemory.Phase.PREPARING && mayUseIdleTime) {
            ScanSession session = PERIODIC_SESSIONS.computeIfAbsent(maid.getUUID(), ignored -> createSession(level, maid));
            boolean done = tickSession(level, maid, session);
            memory.setProgress(session.processedCandidates(), session.totalCandidates());
            if (done) {
                finishDiscovery(level, maid, session);
                PERIODIC_SESSIONS.remove(maid.getUUID());
            }
        }
    }

    private static void finishDiscovery(ServerLevel level, EntityMaid maid, ScanSession session) {
        removeDisappearedLoadedStorages(level, maid, session.scope());
        LinkedHashSet<Target> targets = new LinkedHashSet<>(session.reachableTargets());
        PeriodicScanMemory memory = ExtensionMemoryUtil.getPeriodicScan(maid);
        memory.beginInspection(targets);
        if (targets.isEmpty()) {
            MiscSortService.planFromLatestCache(level, maid);
            MaintenanceFeedbackService.setPhase(maid,
                    memory.getPhase() == PeriodicScanMemory.Phase.SORT_PENDING
                            ? MaintenanceStatusData.Phase.CLEANING
                            : MaintenanceStatusData.Phase.REFRESHING);
        } else {
            dispatchNextInspection(level, maid);
            MaintenanceFeedbackService.setPhase(maid, MaintenanceStatusData.Phase.VIEWING);
        }
    }

    private static void removeDisappearedLoadedStorages(ServerLevel level, EntityMaid maid, ScanScope scope) {
        List<Target> cached = new ArrayList<>(MemoryUtil.getViewedInventory(maid).positionFlatten().keySet());
        for (Target target : cached) {
            BlockPos pos = target.getPos();
            if (!scope.contains(pos) || !level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) {
                continue;
            }
            if (MaidStorage.getInstance().isValidTarget(level, maid, target) == null) {
                MemoryUtil.getViewedInventory(maid).resetViewedInvForPosAsRemoved(target);
            }
        }
    }

    /** Adds exactly one nearest patrol target to the official view flow. */
    private static void dispatchNextInspection(ServerLevel level, EntityMaid maid) {
        PeriodicScanMemory periodic = ExtensionMemoryUtil.getPeriodicScan(maid);
        ViewedInventoryMemory viewed = MemoryUtil.getViewedInventory(maid);
        if (periodic.getPhase() != PeriodicScanMemory.Phase.SCANNING
                || periodic.pendingInspectionTargetSet().isEmpty()
                || viewed.isViewing() || viewed.hasTarget() || !viewed.getMarkChanged().isEmpty()) return;

        InspectionDispatch previous = INSPECTION_DISPATCHES.get(maid.getUUID());
        if (previous != null && periodic.pendingInspectionTargetSet().contains(previous.target())) {
            int failures = previous.failedDispatches() + 1;
            if (InspectionRetryPolicy.exhausted(failures)) {
                abortIncompleteInspection(level, maid);
                return;
            }
            INSPECTION_DISPATCHES.put(maid.getUUID(),
                    new InspectionDispatch(previous.target(), failures));
        }

        InspectionTargetSelector.selectNearest(maid.blockPosition(), periodic.getPendingInspectionTargets())
                .ifPresent(target -> {
                    InspectionDispatch state = INSPECTION_DISPATCHES.get(maid.getUUID());
                    int failures = state != null && state.target().equals(target)
                            ? state.failedDispatches() : 0;
                    INSPECTION_DISPATCHES.put(maid.getUUID(), new InspectionDispatch(target, failures));
                    viewed.resetMarkFailTime();
                    viewed.addMarkChanged(target);
                });
    }

    /** Called only from ViewBehavior.tick after context.isDone(), never from queue disappearance. */
    public static void recordSuccessfulInspection(ServerLevel level, EntityMaid maid, Target viewedTarget) {
        PeriodicScanMemory periodic = ExtensionMemoryUtil.getPeriodicScan(maid);
        if (periodic.getPhase() != PeriodicScanMemory.Phase.SCANNING || viewedTarget == null) return;
        String viewedKey = canonicalStorageKey(level, viewedTarget);
        Target pending = periodic.getPendingInspectionTargets().stream()
                .filter(target -> canonicalStorageKey(level, target).equals(viewedKey))
                .findFirst()
                .orElse(null);
        if (pending != null && periodic.recordSuccessfulInspection(pending)) {
            INSPECTION_DISPATCHES.remove(maid.getUUID());
        }
    }

    private static void abortIncompleteInspection(ServerLevel level, EntityMaid maid) {
        PeriodicScanMemory periodic = ExtensionMemoryUtil.getPeriodicScan(maid);
        Set<Target> cycleTargets = periodic.cycleTargetSet();
        MemoryUtil.getViewedInventory(maid).getMarkChanged().removeIf(cycleTargets::contains);
        periodic.reset();
        periodic.setNextScanGameTime(level.getGameTime() + 200L);
        ExtensionMemoryUtil.getMiscSort(maid).resetAfterPublish();
        INSPECTION_DISPATCHES.remove(maid.getUUID());
        MaintenanceFeedbackService.setPhase(maid, MaintenanceStatusData.Phase.IDLE);
    }

    public static boolean hasPendingPeriodicView(EntityMaid maid) {
        PeriodicScanMemory memory = ExtensionMemoryUtil.getPeriodicScan(maid);
        if (memory.getPhase() != PeriodicScanMemory.Phase.SCANNING) {
            return false;
        }
        return !memory.pendingInspectionTargetSet().isEmpty();
    }

    public static boolean hasManualChangedTarget(EntityMaid maid) {
        Set<Target> periodic = ExtensionMemoryUtil.getPeriodicScan(maid).cycleTargetSet();
        return MemoryUtil.getViewedInventory(maid).getMarkChanged().stream().anyMatch(target -> !periodic.contains(target));
    }

    public static void completeCycle(ServerLevel level, EntityMaid maid,
                                     InventoryListRefreshService.RefreshResult result,
                                     @Nullable ItemFrame frame) {
        PeriodicScanMemory memory = ExtensionMemoryUtil.getPeriodicScan(maid);
        PeriodicScanInterval interval = ExtensionConfigData.get(maid).periodicScanInterval();
        int scannedStorages = memory.getScanTotal();
        MaintenanceFeedbackService.complete(level, maid, result, scannedStorages, frame);
        ExtensionMemoryUtil.getMiscSort(maid).resetAfterPublish();
        memory.reset();
        memory.clearForceScanRequest();
        memory.setNextScanGameTime(interval == PeriodicScanInterval.DISABLED
                ? 0L : level.getGameTime() + interval.ticks());
        PERIODIC_SESSIONS.remove(maid.getUUID());
        INSPECTION_DISPATCHES.remove(maid.getUUID());
    }

    public static void cancelPeriodic(EntityMaid maid) {
        PeriodicScanMemory memory = ExtensionMemoryUtil.getPeriodicScan(maid);
        MiscSortMemory sort = ExtensionMemoryUtil.getMiscSort(maid);
        Set<Target> periodic = Set.copyOf(memory.cycleTargetSet());
        MemoryUtil.getViewedInventory(maid).getMarkChanged().removeIf(periodic::contains);
        memory.replaceTargets(List.of());
        memory.setProgress(0, 0);
        PERIODIC_SESSIONS.remove(maid.getUUID());
        INSPECTION_DISPATCHES.remove(maid.getUUID());
        if (sort.hasInFlight()) {
            sort.clearUnstartedWork();
            sort.forceActiveBatchReturn();
            sort.setPhase(MiscSortMemory.Phase.MOVING);
            memory.setPhase(PeriodicScanMemory.Phase.SORTING);
            memory.setNextScanGameTime(0L);
            MaintenanceFeedbackService.setPhase(maid, MaintenanceStatusData.Phase.CLEANING);
            return;
        }
        sort.resetAfterPublish();
        memory.reset();
        memory.setNextScanGameTime(0L);
        MaintenanceFeedbackService.setPhase(maid, MaintenanceStatusData.Phase.IDLE);
    }

    /** Drops transient discovery state; persisted PREPARING state restarts safely when the maid loads again. */
    public static void releaseTransientSession(EntityMaid maid) {
        PERIODIC_SESSIONS.remove(maid.getUUID());
        INSPECTION_DISPATCHES.remove(maid.getUUID());
    }

    private static String canonicalStorageKey(ServerLevel level, Target target) {
        List<BlockPos> positions = new ArrayList<>();
        positions.add(target.getPos());
        StorageAccessUtil.checkNearByContainers(level, target.getPos(), positions::add);
        BlockPos min = positions.stream().min(Comparator.comparingLong(BlockPos::asLong)).orElse(target.getPos());
        return target.getType() + "@" + min.asLong();
    }
}
