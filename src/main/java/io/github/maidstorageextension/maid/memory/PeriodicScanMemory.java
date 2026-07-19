package io.github.maidstorageextension.maid.memory;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import studio.fantasyit.maid_storage_manager.storage.Target;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Persistent evidence for one discovery -> inspection -> sort -> publish cycle. */
public final class PeriodicScanMemory {
    public enum Phase {
        IDLE,
        PREPARING,
        SCANNING,
        SORT_PENDING,
        SORTING,
        REFRESH_PENDING
    }

    private static final Codec<Phase> PHASE_CODEC = Codec.STRING.xmap(
            PeriodicScanMemory::phaseFromName, Phase::name);

    public static final Codec<PeriodicScanMemory> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.LONG.optionalFieldOf("next_scan_game_time", 0L)
                    .forGetter(PeriodicScanMemory::getNextScanGameTime),
            PHASE_CODEC.optionalFieldOf("phase", Phase.IDLE).forGetter(PeriodicScanMemory::getPhase),
            Target.CODEC.listOf().optionalFieldOf("periodic_targets", List.of())
                    .forGetter(PeriodicScanMemory::getPendingInspectionTargets),
            Target.CODEC.listOf().optionalFieldOf("successfully_inspected_targets", List.of())
                    .forGetter(PeriodicScanMemory::getSuccessfullyInspectedTargets),
            Codec.INT.optionalFieldOf("scan_progress", 0).forGetter(PeriodicScanMemory::getScanProgress),
            Codec.INT.optionalFieldOf("scan_total", 0).forGetter(PeriodicScanMemory::getScanTotal),
            Codec.LONG.optionalFieldOf("scan_generation", 0L)
                    .forGetter(PeriodicScanMemory::getScanGeneration),
            Codec.BOOL.optionalFieldOf("force_scan_requested", false)
                    .forGetter(PeriodicScanMemory::isForceScanRequested),
            Codec.BOOL.optionalFieldOf("queued_refresh_requested", false)
                    .forGetter(PeriodicScanMemory::isQueuedRefreshRequested)
    ).apply(instance, PeriodicScanMemory::new));

    private long nextScanGameTime;
    private Phase phase;
    private final LinkedHashSet<Target> pendingInspectionTargets;
    private final LinkedHashSet<Target> successfullyInspectedTargets;
    private int scanProgress;
    private int scanTotal;
    private long scanGeneration;
    private boolean forceScanRequested;
    private boolean queuedRefreshRequested;

    private PeriodicScanMemory(long nextScanGameTime, Phase phase, List<Target> pendingTargets,
                               List<Target> successfulTargets, int scanProgress, int scanTotal,
                               long scanGeneration, boolean forceScanRequested,
                               boolean queuedRefreshRequested) {
        this.nextScanGameTime = nextScanGameTime;
        this.phase = phase == null ? Phase.IDLE : phase;
        this.pendingInspectionTargets = new LinkedHashSet<>(pendingTargets);
        this.successfullyInspectedTargets = new LinkedHashSet<>(successfulTargets);
        this.scanProgress = Math.max(0, scanProgress);
        this.scanTotal = Math.max(this.scanProgress, scanTotal);
        this.scanGeneration = Math.max(0L, scanGeneration);
        this.forceScanRequested = forceScanRequested;
        this.queuedRefreshRequested = queuedRefreshRequested;
    }

    /** Kept for source compatibility with status/tests created before the explicit-success ledger. */
    public PeriodicScanMemory(long nextScanGameTime, Phase phase, List<Target> pendingTargets,
                              int scanProgress, int scanTotal) {
        this(nextScanGameTime, phase, pendingTargets, List.of(), scanProgress, scanTotal,
                0L, false, false);
    }

    public PeriodicScanMemory() {
        this(0L, Phase.IDLE, List.of(), List.of(), 0, 0, 0L, false, false);
    }

    private static Phase phaseFromName(String name) {
        if ("CLEANUP_PENDING".equals(name)) return Phase.SORT_PENDING;
        if ("CLEANING".equals(name)) return Phase.SORTING;
        try {
            return Phase.valueOf(name);
        } catch (IllegalArgumentException ignored) {
            return Phase.IDLE;
        }
    }

    public long getNextScanGameTime() {
        return nextScanGameTime;
    }

    public void setNextScanGameTime(long nextScanGameTime) {
        this.nextScanGameTime = nextScanGameTime;
    }

    public Phase getPhase() {
        return phase;
    }

    public void setPhase(Phase phase) {
        this.phase = phase == null ? Phase.IDLE : phase;
    }

    public void beginInspection(Collection<Target> targets) {
        scanGeneration = scanGeneration == Long.MAX_VALUE ? 1L : scanGeneration + 1L;
        pendingInspectionTargets.clear();
        pendingInspectionTargets.addAll(targets);
        successfullyInspectedTargets.clear();
        scanProgress = 0;
        scanTotal = pendingInspectionTargets.size();
        phase = Phase.SCANNING;
    }

    public List<Target> getPendingInspectionTargets() {
        return new ArrayList<>(pendingInspectionTargets);
    }

    public Set<Target> pendingInspectionTargetSet() {
        return pendingInspectionTargets;
    }

    public List<Target> getSuccessfullyInspectedTargets() {
        return new ArrayList<>(successfullyInspectedTargets);
    }

    public Set<Target> cycleTargetSet() {
        LinkedHashSet<Target> targets = new LinkedHashSet<>(pendingInspectionTargets);
        targets.addAll(successfullyInspectedTargets);
        return targets;
    }

    public boolean recordSuccessfulInspection(Target target) {
        if (target == null || !pendingInspectionTargets.remove(target)) {
            return false;
        }
        successfullyInspectedTargets.add(target);
        scanProgress = successfullyInspectedTargets.size();
        return true;
    }

    public boolean hasCompleteInspectionEvidence() {
        return phase == Phase.SCANNING
                && pendingInspectionTargets.isEmpty()
                && successfullyInspectedTargets.size() == scanTotal;
    }

    /** Legacy accessor retained for status and compatibility code while the field now means pending. */
    public List<Target> getPeriodicTargets() {
        return getPendingInspectionTargets();
    }

    public Set<Target> periodicTargetSet() {
        return pendingInspectionTargetSet();
    }

    public void replaceTargets(Collection<Target> targets) {
        pendingInspectionTargets.clear();
        pendingInspectionTargets.addAll(targets);
    }

    public void removeTarget(Target target) {
        pendingInspectionTargets.remove(target);
    }

    public int getScanProgress() {
        return scanProgress;
    }

    public int getScanTotal() {
        return scanTotal;
    }

    /** Monotonic identity of the current patrol. Resetting transient state never reuses it. */
    public long getScanGeneration() {
        return scanGeneration;
    }

    /** Schedules one complete patrol without enabling future periodic patrols. */
    public void requestImmediateScan() {
        if (phase == Phase.IDLE) {
            forceScanRequested = true;
            nextScanGameTime = 0L;
        } else {
            queuedRefreshRequested = true;
        }
    }

    public boolean isForceScanRequested() {
        return forceScanRequested;
    }

    public void clearForceScanRequest() {
        forceScanRequested = false;
    }

    public boolean isQueuedRefreshRequested() {
        return queuedRefreshRequested;
    }

    public boolean consumeQueuedRefreshRequest() {
        boolean queued = queuedRefreshRequested;
        queuedRefreshRequested = false;
        return queued;
    }

    public void setProgress(int progress, int total) {
        scanProgress = Math.max(0, progress);
        scanTotal = Math.max(scanProgress, total);
    }

    public void reset() {
        phase = Phase.IDLE;
        pendingInspectionTargets.clear();
        successfullyInspectedTargets.clear();
        scanProgress = 0;
        scanTotal = 0;
    }
}
