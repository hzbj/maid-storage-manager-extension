package io.github.maidstorageextension.maid.memory;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.maidstorageextension.scan.MiscSortPlanner;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import studio.fantasyit.maid_storage_manager.storage.Target;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Minimal persistent journal for one extracted misc-storage stack. */
public final class MiscSortMemory {
    public enum Phase {
        IDLE,
        READY,
        MOVING,
        REFRESH_PENDING
    }

    private static final Codec<Phase> PHASE_CODEC = Codec.STRING.xmap(
            MiscSortMemory::phaseFromName, Phase::name);
    private static final Codec<MiscSortPlanner.IgnorePolicy> IGNORE_POLICY_CODEC = Codec.STRING.xmap(
            MiscSortMemory::ignorePolicyFromName, MiscSortPlanner.IgnorePolicy::name);

    public static final class TransferTask {
        public static final Codec<TransferTask> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Target.CODEC.fieldOf("source").forGetter(TransferTask::source),
                ItemStack.CODEC.fieldOf("stack").forGetter(TransferTask::stack),
                Target.CODEC.listOf().optionalFieldOf("candidate_targets", List.of())
                        .forGetter(TransferTask::destinations)
        ).apply(instance, TransferTask::new));

        private final Target source;
        private final ItemStack stack;
        private final List<Target> destinations;

        public TransferTask(Target source, ItemStack stack, Collection<Target> destinations) {
            this.source = source;
            this.stack = stack.copy();
            this.destinations = List.copyOf(destinations);
        }

        public Target source() {
            return source;
        }

        public ItemStack stack() {
            return stack.copy();
        }

        public List<Target> destinations() {
            return destinations;
        }

        private TransferTask copy() {
            return new TransferTask(source, stack, destinations);
        }
    }

    public static final class PayloadPlan {
        public static final Codec<PayloadPlan> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ItemStack.CODEC.fieldOf("stack").forGetter(PayloadPlan::stack),
                Codec.LONG.fieldOf("remaining_count").forGetter(PayloadPlan::remainingCount),
                Codec.STRING.optionalFieldOf("ignore_key", "").forGetter(PayloadPlan::ignoreKey),
                Target.CODEC.listOf().optionalFieldOf("candidate_targets", List.of())
                        .forGetter(PayloadPlan::destinations)
        ).apply(instance, PayloadPlan::new));

        private final ItemStack stack;
        private final long remainingCount;
        private final String ignoreKey;
        private final List<Target> destinations;

        public PayloadPlan(ItemStack stack, long remainingCount, String ignoreKey,
                           Collection<Target> destinations) {
            if (stack == null || stack.isEmpty()) throw new IllegalArgumentException("stack must not be empty");
            if (remainingCount <= 0) throw new IllegalArgumentException("remainingCount must be positive");
            this.stack = stack.copyWithCount(1);
            this.remainingCount = remainingCount;
            this.ignoreKey = ignoreKey == null ? "" : ignoreKey;
            this.destinations = List.copyOf(destinations);
            if (this.destinations.isEmpty()) throw new IllegalArgumentException("destinations must not be empty");
        }

        public PayloadPlan(ItemStack stack, long remainingCount, Collection<Target> destinations) {
            this(stack, remainingCount, "", destinations);
        }

        public ItemStack stack() {
            return stack.copy();
        }

        public long remainingCount() {
            return remainingCount;
        }

        public String ignoreKey() {
            return ignoreKey;
        }

        public List<Target> destinations() {
            return destinations;
        }

        private PayloadPlan copy() {
            return new PayloadPlan(stack, remainingCount, ignoreKey, destinations);
        }
    }

    public static final class SourceJob {
        public static final Codec<SourceJob> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Target.CODEC.fieldOf("source").forGetter(SourceJob::source),
                Codec.STRING.optionalFieldOf("canonical_source_key", "")
                        .forGetter(SourceJob::canonicalSourceKey),
                PayloadPlan.CODEC.listOf().fieldOf("payloads").forGetter(SourceJob::payloads)
        ).apply(instance, SourceJob::new));

        private final Target source;
        private final String canonicalSourceKey;
        private final List<PayloadPlan> payloads;

        public SourceJob(Target source, String canonicalSourceKey, Collection<PayloadPlan> payloads) {
            this.source = Objects.requireNonNull(source, "source");
            this.canonicalSourceKey = canonicalSourceKey == null || canonicalSourceKey.isBlank()
                    ? source.toStoreString() : canonicalSourceKey;
            this.payloads = payloads.stream().filter(Objects::nonNull).map(PayloadPlan::copy).toList();
            if (this.payloads.isEmpty()) throw new IllegalArgumentException("payloads must not be empty");
        }

        public SourceJob(Target source, Collection<PayloadPlan> payloads) {
            this(source, source.toStoreString(), payloads);
        }

        public Target source() {
            return source;
        }

        public String canonicalSourceKey() {
            return canonicalSourceKey;
        }

        public List<PayloadPlan> payloads() {
            return payloads.stream().map(PayloadPlan::copy).toList();
        }

        private SourceJob copy() {
            return new SourceJob(source, canonicalSourceKey, payloads);
        }
    }

    public static final class CargoLine {
        public static final Codec<CargoLine> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ItemStack.CODEC.fieldOf("stack").forGetter(CargoLine::stack),
                Codec.INT.fieldOf("requested_count").forGetter(CargoLine::requestedCount),
                Codec.INT.optionalFieldOf("in_flight_count", 0).forGetter(CargoLine::inFlightCount),
                Codec.INT.fieldOf("transport_slot").forGetter(CargoLine::transportSlot),
                Codec.INT.optionalFieldOf("baseline_count", -1).forGetter(CargoLine::baselineCount),
                Codec.STRING.optionalFieldOf("ignore_key", "").forGetter(CargoLine::ignoreKey),
                Target.CODEC.listOf().optionalFieldOf("candidate_targets", List.of())
                        .forGetter(CargoLine::destinations),
                Codec.INT.optionalFieldOf("candidate_target_index", 0)
                        .forGetter(CargoLine::destinationIndex)
        ).apply(instance, CargoLine::new));

        private final ItemStack stack;
        private final int requestedCount;
        private int inFlightCount;
        private final int transportSlot;
        private final int baselineCount;
        private final String ignoreKey;
        private final List<Target> destinations;
        private int destinationIndex;

        public CargoLine(ItemStack stack, int requestedCount, int inFlightCount, int transportSlot,
                         int baselineCount, String ignoreKey, Collection<Target> destinations,
                         int destinationIndex) {
            if (stack == null || stack.isEmpty()) throw new IllegalArgumentException("stack must not be empty");
            if (requestedCount <= 0) throw new IllegalArgumentException("requestedCount must be positive");
            if (transportSlot < 0) throw new IllegalArgumentException("transportSlot must be non-negative");
            this.stack = stack.copyWithCount(1);
            this.requestedCount = requestedCount;
            this.inFlightCount = Math.max(0, Math.min(requestedCount, inFlightCount));
            this.transportSlot = transportSlot;
            this.baselineCount = Math.max(-1, baselineCount);
            this.ignoreKey = ignoreKey == null ? "" : ignoreKey;
            this.destinations = List.copyOf(destinations);
            this.destinationIndex = Math.max(0, Math.min(destinationIndex, this.destinations.size()));
        }

        public CargoLine(ItemStack stack, int requestedCount, int inFlightCount, int transportSlot,
                         Collection<Target> destinations, int destinationIndex) {
            this(stack, requestedCount, inFlightCount, transportSlot, -1, "",
                    destinations, destinationIndex);
        }

        public ItemStack stack() { return stack.copy(); }
        public int requestedCount() { return requestedCount; }
        public int inFlightCount() { return inFlightCount; }
        public int transportSlot() { return transportSlot; }
        public int baselineCount() { return baselineCount; }
        public String ignoreKey() { return ignoreKey; }
        public List<Target> destinations() { return destinations; }
        public int destinationIndex() { return destinationIndex; }
        public boolean hasInFlight() { return inFlightCount > 0; }
        public ItemStack inFlightStack() { return stack.copyWithCount(inFlightCount); }

        public Optional<Target> currentDestination() {
            return destinationIndex < destinations.size()
                    ? Optional.of(destinations.get(destinationIndex)) : Optional.empty();
        }

        public void setInFlightCount(int count) {
            inFlightCount = Math.max(0, Math.min(requestedCount, count));
        }

        public void advanceDestination() {
            if (destinationIndex < destinations.size()) destinationIndex++;
        }

        public CargoLine withBaselineCount(int baselineCount) {
            return new CargoLine(stack, requestedCount, inFlightCount, transportSlot,
                    baselineCount, ignoreKey, destinations, destinationIndex);
        }

        public CargoLine withIgnoreKey(String ignoreKey) {
            return new CargoLine(stack, requestedCount, inFlightCount, transportSlot,
                    baselineCount, ignoreKey, destinations, destinationIndex);
        }

        public CargoLine withDestinationIndex(int destinationIndex) {
            return new CargoLine(stack, requestedCount, inFlightCount, transportSlot,
                    baselineCount, ignoreKey, destinations, destinationIndex);
        }

        private CargoLine copy() {
            return new CargoLine(stack, requestedCount, inFlightCount, transportSlot,
                    baselineCount, ignoreKey, destinations, destinationIndex);
        }
    }

    public static final class ActiveBatch {
        public static final Codec<ActiveBatch> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.LONG.optionalFieldOf("scan_generation", 0L).forGetter(ActiveBatch::scanGeneration),
                Target.CODEC.fieldOf("source").forGetter(ActiveBatch::source),
                Codec.STRING.optionalFieldOf("canonical_source_key", "")
                        .forGetter(ActiveBatch::canonicalSourceKey),
                CargoLine.CODEC.listOf().fieldOf("cargo_lines").forGetter(ActiveBatch::cargoLines)
        ).apply(instance, ActiveBatch::new));

        private final long scanGeneration;
        private final Target source;
        private final String canonicalSourceKey;
        private final List<CargoLine> cargoLines;

        public ActiveBatch(long scanGeneration, Target source, String canonicalSourceKey,
                           Collection<CargoLine> cargoLines) {
            this.scanGeneration = Math.max(0L, scanGeneration);
            this.source = Objects.requireNonNull(source, "source");
            this.canonicalSourceKey = canonicalSourceKey == null || canonicalSourceKey.isBlank()
                    ? source.toStoreString() : canonicalSourceKey;
            this.cargoLines = cargoLines.stream().filter(Objects::nonNull).map(CargoLine::copy).toList();
            if (this.cargoLines.isEmpty()) throw new IllegalArgumentException("cargoLines must not be empty");
            Set<Integer> slots = new LinkedHashSet<>();
            for (CargoLine line : this.cargoLines) {
                if (!slots.add(line.transportSlot())) {
                    throw new IllegalArgumentException("transport slots must be unique inside one batch");
                }
            }
        }

        public long scanGeneration() { return scanGeneration; }
        public Target source() { return source; }
        public String canonicalSourceKey() { return canonicalSourceKey; }
        public List<CargoLine> cargoLines() {
            List<CargoLine> snapshot = new ArrayList<>(cargoLines.size());
            cargoLines.forEach(line -> snapshot.add(line.copy()));
            return snapshot;
        }
        public boolean hasInFlight() { return cargoLines.stream().anyMatch(CargoLine::hasInFlight); }
        public Set<Integer> transportSlots() {
            LinkedHashSet<Integer> result = new LinkedHashSet<>();
            cargoLines.forEach(line -> result.add(line.transportSlot()));
            return Set.copyOf(result);
        }

        private ActiveBatch copy() {
            return new ActiveBatch(scanGeneration, source, canonicalSourceKey, cargoLines);
        }
    }

    public static final class FullMark {
        public static final Codec<FullMark> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.LONG.fieldOf("scan_generation").forGetter(FullMark::scanGeneration),
                Codec.STRING.fieldOf("canonical_target_key").forGetter(FullMark::canonicalTargetKey),
                ItemStack.CODEC.fieldOf("stack").forGetter(FullMark::stack)
        ).apply(instance, FullMark::new));

        private final long scanGeneration;
        private final String canonicalTargetKey;
        private final ItemStack stack;

        public FullMark(long scanGeneration, String canonicalTargetKey, ItemStack stack) {
            if (canonicalTargetKey == null || canonicalTargetKey.isBlank()) {
                throw new IllegalArgumentException("canonicalTargetKey must not be blank");
            }
            if (stack == null || stack.isEmpty()) throw new IllegalArgumentException("stack must not be empty");
            this.scanGeneration = Math.max(0L, scanGeneration);
            this.canonicalTargetKey = canonicalTargetKey;
            this.stack = stack.copyWithCount(1);
        }

        public long scanGeneration() { return scanGeneration; }
        public String canonicalTargetKey() { return canonicalTargetKey; }
        public ItemStack stack() { return stack.copy(); }

        private FullMark copy() { return new FullMark(scanGeneration, canonicalTargetKey, stack); }
    }

    public static final Codec<MiscSortMemory> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            PHASE_CODEC.optionalFieldOf("phase", Phase.IDLE).forGetter(MiscSortMemory::getPhase),
            Codec.LONG.optionalFieldOf("scan_generation", 0L).forGetter(MiscSortMemory::getScanGeneration),
            IGNORE_POLICY_CODEC.optionalFieldOf("ignore_policy", MiscSortPlanner.IgnorePolicy.ITEM_ID)
                    .forGetter(MiscSortMemory::getIgnorePolicy),
            Codec.STRING.listOf().optionalFieldOf("ignored_keys", List.of())
                    .forGetter(MiscSortMemory::ignoredKeysForCodec),
            SourceJob.CODEC.listOf().optionalFieldOf("pending_source_jobs", List.of())
                    .forGetter(MiscSortMemory::getPendingSourceJobs),
            ActiveBatch.CODEC.optionalFieldOf("active_batch").forGetter(MiscSortMemory::activeBatchForCodec),
            FullMark.CODEC.listOf().optionalFieldOf("full_marks", List.of())
                    .forGetter(MiscSortMemory::getFullMarks),
            TransferTask.CODEC.listOf().optionalFieldOf("pending_tasks", List.of())
                    .forGetter(MiscSortMemory::legacyPendingTasksForCodec),
            TransferTask.CODEC.optionalFieldOf("current_task").forGetter(MiscSortMemory::legacyCurrentForCodec),
            Codec.INT.optionalFieldOf("candidate_target_index", 0)
                    .forGetter(MiscSortMemory::legacyDestinationIndexForCodec),
            ItemStack.CODEC.optionalFieldOf("in_flight_stack").forGetter(MiscSortMemory::inFlightForCodec),
            Codec.INT.optionalFieldOf("buffer_slot", -1).forGetter(MiscSortMemory::legacyBufferSlotForCodec),
            Codec.INT.optionalFieldOf("cleanup_source_total", 0)
                    .forGetter(MiscSortMemory::getCleanupSourceTotal)
    ).apply(instance, MiscSortMemory::new));

    private Phase phase;
    private long scanGeneration;
    private MiscSortPlanner.IgnorePolicy ignorePolicy;
    private final LinkedHashSet<String> ignoredKeys = new LinkedHashSet<>();
    private final Deque<SourceJob> pendingSourceJobs = new ArrayDeque<>();
    private ActiveBatch activeBatch;
    private final List<FullMark> fullMarks = new ArrayList<>();
    private final Deque<TransferTask> pending = new ArrayDeque<>();
    private TransferTask current;
    private int destinationIndex;
    private ItemStack inFlight;
    private int bufferSlot;
    private int cleanupSourceTotal;

    private MiscSortMemory(Phase phase, long scanGeneration, MiscSortPlanner.IgnorePolicy ignorePolicy,
                           List<String> ignoredKeys, List<SourceJob> pendingSourceJobs,
                           Optional<ActiveBatch> activeBatch, List<FullMark> fullMarks,
                           List<TransferTask> pending, Optional<TransferTask> current,
                           int destinationIndex, Optional<ItemStack> inFlight, int bufferSlot,
                           int cleanupSourceTotal) {
        this.phase = phase == null ? Phase.IDLE : phase;
        this.scanGeneration = Math.max(0L, scanGeneration);
        this.ignorePolicy = ignorePolicy == null ? MiscSortPlanner.IgnorePolicy.ITEM_ID : ignorePolicy;
        ignoredKeys.stream().filter(Objects::nonNull).filter(key -> !key.isBlank())
                .forEach(this.ignoredKeys::add);
        this.inFlight = ItemStack.EMPTY;
        this.bufferSlot = -1;
        for (TransferTask task : pending) {
            if (isValidTask(task)) this.pending.addLast(task.copy());
        }
        this.current = current.map(TransferTask::copy).orElse(null);
        this.destinationIndex = Math.max(0, destinationIndex);
        this.inFlight = inFlight.filter(stack -> !stack.isEmpty()).map(ItemStack::copy).orElse(ItemStack.EMPTY);
        this.bufferSlot = this.inFlight.isEmpty() ? -1 : Math.max(-1, bufferSlot);
        pendingSourceJobs.stream().filter(Objects::nonNull).map(SourceJob::copy)
                .forEach(this.pendingSourceJobs::addLast);
        this.activeBatch = activeBatch.map(ActiveBatch::copy).orElse(null);
        fullMarks.stream().filter(Objects::nonNull)
                .filter(mark -> mark.scanGeneration() == this.scanGeneration)
                .map(FullMark::copy).forEach(this.fullMarks::add);

        boolean hasNewJournal = this.activeBatch != null || !this.pendingSourceJobs.isEmpty();
        if (!hasNewJournal) migrateLegacyJournal();
        this.cleanupSourceTotal = Math.max(Math.max(0, cleanupSourceTotal), openSourceCount());
    }

    public MiscSortMemory() {
        this(Phase.IDLE, 0L, MiscSortPlanner.IgnorePolicy.ITEM_ID, List.of(), List.of(),
                Optional.empty(), List.of(), List.of(), Optional.empty(), 0, Optional.empty(), -1, 0);
    }

    private static Phase phaseFromName(String name) {
        if ("CLEANUP_PENDING".equals(name)) return Phase.READY;
        if ("CLEANING".equals(name)) return Phase.MOVING;
        if ("SCANNING".equals(name)) return Phase.IDLE;
        try {
            return Phase.valueOf(name);
        } catch (IllegalArgumentException ignored) {
            return Phase.IDLE;
        }
    }

    private static MiscSortPlanner.IgnorePolicy ignorePolicyFromName(String name) {
        try {
            return MiscSortPlanner.IgnorePolicy.valueOf(name);
        } catch (IllegalArgumentException ignored) {
            return MiscSortPlanner.IgnorePolicy.ITEM_ID;
        }
    }

    private void migrateLegacyJournal() {
        List<TransferTask> unstarted = new ArrayList<>();
        if (current != null && inFlight.isEmpty()) unstarted.add(current.copy());
        pending.stream().map(TransferTask::copy).forEach(unstarted::add);
        sourceJobsFromTasks(unstarted, ignorePolicy).forEach(pendingSourceJobs::addLast);

        if (current != null && !inFlight.isEmpty()) {
            int requested = Math.max(current.stack().getCount(), inFlight.getCount());
            CargoLine line = new CargoLine(current.stack(), requested, inFlight.getCount(),
                    Math.max(0, bufferSlot), -1, ignoreKeyFor(current.stack(), ignorePolicy),
                    current.destinations(), destinationIndex);
            activeBatch = new ActiveBatch(scanGeneration, current.source(),
                    current.source().toStoreString(), List.of(line));
        }
        pending.clear();
        current = null;
        destinationIndex = 0;
        inFlight = ItemStack.EMPTY;
        bufferSlot = -1;
    }

    private static List<SourceJob> sourceJobsFromTasks(
            Collection<TransferTask> tasks, MiscSortPlanner.IgnorePolicy ignorePolicy) {
        List<SourceJob> jobs = new ArrayList<>();
        for (TransferTask task : tasks) {
            if (!isValidTask(task)) continue;
            PayloadPlan payload = new PayloadPlan(task.stack(), task.stack().getCount(),
                    ignoreKeyFor(task.stack(), ignorePolicy), task.destinations());
            int existing = -1;
            for (int i = 0; i < jobs.size(); i++) {
                if (jobs.get(i).source().equals(task.source())) {
                    existing = i;
                    break;
                }
            }
            if (existing < 0) {
                jobs.add(new SourceJob(task.source(), List.of(payload)));
            } else {
                SourceJob old = jobs.get(existing);
                List<PayloadPlan> payloads = new ArrayList<>(old.payloads());
                payloads.add(payload);
                jobs.set(existing, new SourceJob(old.source(), old.canonicalSourceKey(), payloads));
            }
        }
        return List.copyOf(jobs);
    }

    private static boolean isValidTask(TransferTask task) {
        return task != null && !task.stack().isEmpty() && !task.destinations().isEmpty();
    }

    public static String ignoreKeyFor(ItemStack stack, MiscSortPlanner.IgnorePolicy policy) {
        if (stack == null || stack.isEmpty()) throw new IllegalArgumentException("stack must not be empty");
        var itemId = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (itemId == null) throw new IllegalArgumentException("stack item is not registered");
        if (policy == MiscSortPlanner.IgnorePolicy.EXACT_STACK) {
            CompoundTag saved = stack.copyWithCount(1).save(new CompoundTag());
            return "exact:" + saved;
        }
        return "item:" + itemId;
    }

    public String resolveIgnoreKey(String storedKey, ItemStack stack) {
        return storedKey == null || storedKey.isBlank()
                ? ignoreKeyFor(stack, ignorePolicy) : storedKey;
    }

    public long getScanGeneration() {
        return scanGeneration;
    }

    public MiscSortPlanner.IgnorePolicy getIgnorePolicy() {
        return ignorePolicy;
    }

    /** Starts a fresh patrol ledger. Physical cargo must be recovered before crossing generations. */
    public void beginGeneration(long generation, MiscSortPlanner.IgnorePolicy policy) {
        long normalized = Math.max(0L, generation);
        MiscSortPlanner.IgnorePolicy normalizedPolicy = policy == null
                ? MiscSortPlanner.IgnorePolicy.ITEM_ID : policy;
        if ((normalized != scanGeneration || normalizedPolicy != ignorePolicy) && hasActiveCargo()) {
            throw new IllegalStateException("Cannot replace generation while cargo is in flight");
        }
        if (normalized != scanGeneration || normalizedPolicy != ignorePolicy) {
            scanGeneration = normalized;
            ignorePolicy = normalizedPolicy;
            ignoredKeys.clear();
            fullMarks.clear();
            pendingSourceJobs.clear();
            activeBatch = null;
            cleanupSourceTotal = 0;
        }
    }

    public Set<String> getIgnoredKeys() {
        return Set.copyOf(ignoredKeys);
    }

    private List<String> ignoredKeysForCodec() {
        return List.copyOf(ignoredKeys);
    }

    public boolean markIgnored(String key) {
        return key != null && !key.isBlank() && ignoredKeys.add(key);
    }

    /**
     * Records a runtime routing failure and removes only work that has not started yet.
     * The active batch is deliberately untouched because any extracted exact cargo must
     * still reach another candidate or return to its unique source.
     */
    public boolean markIgnoredAndPrunePending(String key) {
        if (key == null || key.isBlank()) return false;
        boolean added = ignoredKeys.add(key);
        List<SourceJob> retained = new ArrayList<>();
        for (SourceJob job : pendingSourceJobs) {
            List<PayloadPlan> payloads = job.payloads().stream()
                    .filter(payload -> !resolveIgnoreKey(
                            payload.ignoreKey(), payload.stack()).equals(key))
                    .toList();
            if (!payloads.isEmpty()) {
                retained.add(new SourceJob(job.source(), job.canonicalSourceKey(), payloads));
            }
        }
        pendingSourceJobs.clear();
        retained.forEach(pendingSourceJobs::addLast);
        return added;
    }

    public boolean isIgnored(String key) {
        return key != null && ignoredKeys.contains(key);
    }

    public void replaceSourceJobs(Collection<SourceJob> jobs) {
        if (hasActiveCargo()) throw new IllegalStateException("Cannot replace jobs while cargo is in flight");
        pendingSourceJobs.clear();
        jobs.stream().filter(Objects::nonNull).map(SourceJob::copy).forEach(pendingSourceJobs::addLast);
        activeBatch = null;
        cleanupSourceTotal = openSourceCount();
    }

    /** Number of distinct misc source boxes selected for this patrol's cleanup. */
    public int getCleanupSourceTotal() {
        return cleanupSourceTotal;
    }

    /**
     * Number of source boxes whose work has left both the pending queue and active batch.
     * A split source keeps the same canonical key, so allocating multiple cargo batches never
     * advances the visible progress more than once.
     */
    public int getCleanupSourceProgress() {
        return Math.max(0, cleanupSourceTotal - openSourceCount());
    }

    private int openSourceCount() {
        LinkedHashSet<String> sources = new LinkedHashSet<>();
        pendingSourceJobs.stream().map(SourceJob::canonicalSourceKey).forEach(sources::add);
        if (activeBatch != null) sources.add(activeBatch.canonicalSourceKey());
        return sources.size();
    }

    public List<SourceJob> getPendingSourceJobs() {
        return pendingSourceJobs.stream().map(SourceJob::copy).toList();
    }

    public boolean hasPendingSourceJobs() {
        return !pendingSourceJobs.isEmpty();
    }

    public Optional<SourceJob> pollNextSourceJob() {
        SourceJob job = pendingSourceJobs.pollFirst();
        return job == null ? Optional.empty() : Optional.of(job.copy());
    }

    public void prependSourceJob(SourceJob job) {
        pendingSourceJobs.addFirst(Objects.requireNonNull(job, "job").copy());
    }

    public Optional<ActiveBatch> getActiveBatch() {
        return activeBatch == null ? Optional.empty() : Optional.of(activeBatch.copy());
    }

    private Optional<ActiveBatch> activeBatchForCodec() {
        return getActiveBatch();
    }

    public void setActiveBatch(ActiveBatch batch) {
        Objects.requireNonNull(batch, "batch");
        if (batch.scanGeneration() != scanGeneration) {
            throw new IllegalArgumentException("batch generation does not match current scan");
        }
        activeBatch = batch.copy();
    }

    public void replaceCargoLine(int index, CargoLine line) {
        if (activeBatch == null) throw new IllegalStateException("No active batch");
        List<CargoLine> lines = new ArrayList<>(activeBatch.cargoLines());
        lines.set(index, Objects.requireNonNull(line, "line").copy());
        activeBatch = new ActiveBatch(activeBatch.scanGeneration(), activeBatch.source(),
                activeBatch.canonicalSourceKey(), lines);
    }

    public void advanceCargoLineDestination(int index) {
        if (activeBatch == null) throw new IllegalStateException("No active batch");
        List<CargoLine> lines = new ArrayList<>(activeBatch.cargoLines());
        CargoLine line = lines.get(index);
        line.advanceDestination();
        lines.set(index, line);
        activeBatch = new ActiveBatch(activeBatch.scanGeneration(), activeBatch.source(),
                activeBatch.canonicalSourceKey(), lines);
    }

    public void replaceCargoLines(Collection<CargoLine> lines) {
        if (activeBatch == null) throw new IllegalStateException("No active batch");
        activeBatch = new ActiveBatch(activeBatch.scanGeneration(), activeBatch.source(),
                activeBatch.canonicalSourceKey(), lines);
    }

    /** Discards every remaining destination route while retaining the physical cargo journal. */
    public void forceActiveBatchReturn() {
        if (activeBatch == null) return;
        List<CargoLine> lines = activeBatch.cargoLines().stream()
                .map(line -> line.withDestinationIndex(line.destinations().size()))
                .toList();
        activeBatch = new ActiveBatch(activeBatch.scanGeneration(), activeBatch.source(),
                activeBatch.canonicalSourceKey(), lines);
    }

    public void clearActiveBatch() {
        if (hasActiveCargo()) throw new IllegalStateException("Cannot clear cargo that is in flight");
        activeBatch = null;
    }

    public void clearCompletedBatch() {
        clearActiveBatch();
    }

    public boolean hasActiveCargo() {
        return activeBatch != null && activeBatch.hasInFlight();
    }

    public List<FullMark> getFullMarks() {
        return fullMarks.stream().map(FullMark::copy).toList();
    }

    public boolean markFull(String canonicalTargetKey, ItemStack exactStack) {
        if (isMarkedFull(scanGeneration, canonicalTargetKey, exactStack)) return false;
        fullMarks.add(new FullMark(scanGeneration, canonicalTargetKey, exactStack));
        return true;
    }

    public boolean isMarkedFull(long generation, String canonicalTargetKey, ItemStack exactStack) {
        if (canonicalTargetKey == null || exactStack == null || exactStack.isEmpty()) return false;
        return fullMarks.stream().anyMatch(mark -> mark.scanGeneration() == generation
                && mark.canonicalTargetKey().equals(canonicalTargetKey)
                && ItemStack.isSameItemSameTags(mark.stack(), exactStack));
    }

    public Phase getPhase() {
        return phase;
    }

    public void setPhase(Phase phase) {
        this.phase = phase == null ? Phase.IDLE : phase;
    }

    public void replaceTasks(Collection<TransferTask> tasks) {
        pending.clear();
        for (TransferTask task : tasks) {
            if (task != null && !task.stack().isEmpty() && !task.destinations().isEmpty()) {
                pending.addLast(task.copy());
            }
        }
        if (!hasInFlight()) {
            current = null;
            destinationIndex = 0;
        }
    }

    public List<TransferTask> getPendingTasks() {
        if (!pending.isEmpty()) return pending.stream().map(TransferTask::copy).toList();
        List<TransferTask> projected = new ArrayList<>();
        for (SourceJob job : pendingSourceJobs) {
            for (PayloadPlan payload : job.payloads) {
                long remaining = payload.remainingCount();
                int unit = Math.max(1, payload.stack().getMaxStackSize());
                while (remaining > 0) {
                    int count = (int) Math.min(remaining, unit);
                    projected.add(new TransferTask(job.source(), payload.stack().copyWithCount(count),
                            payload.destinations()));
                    remaining -= count;
                }
            }
        }
        return List.copyOf(projected);
    }

    private List<TransferTask> legacyPendingTasksForCodec() {
        return pending.stream().map(TransferTask::copy).toList();
    }

    public boolean hasPendingTasks() {
        return !pending.isEmpty() || !pendingSourceJobs.isEmpty();
    }

    public Optional<TransferTask> beginNextTask() {
        if (current == null && !pending.isEmpty()) {
            current = pending.removeFirst();
            destinationIndex = 0;
        }
        return getCurrentTask();
    }

    public Optional<TransferTask> getCurrentTask() {
        if (current != null) return Optional.of(current.copy());
        if (activeBatch == null || activeBatch.cargoLines.isEmpty()) return Optional.empty();
        CargoLine line = activeBatch.cargoLines.get(0);
        return Optional.of(new TransferTask(activeBatch.source,
                line.stack().copyWithCount(line.requestedCount()), line.destinations()));
    }

    private Optional<TransferTask> legacyCurrentForCodec() {
        return current == null ? Optional.empty() : Optional.of(current.copy());
    }

    public Optional<Target> getCurrentDestination() {
        if (current != null && destinationIndex < current.destinations.size()) {
            return Optional.of(current.destinations.get(destinationIndex));
        }
        if (activeBatch != null && activeBatch.cargoLines.size() == 1) {
            return activeBatch.cargoLines.get(0).currentDestination();
        }
        return Optional.empty();
    }

    public int getDestinationIndex() {
        if (current != null) return destinationIndex;
        return activeBatch != null && activeBatch.cargoLines.size() == 1
                ? activeBatch.cargoLines.get(0).destinationIndex() : 0;
    }

    private int legacyDestinationIndexForCodec() { return destinationIndex; }

    public void advanceDestination() {
        if (current != null && destinationIndex < current.destinations.size()) destinationIndex++;
        if (activeBatch != null && activeBatch.cargoLines.size() == 1) {
            CargoLine line = activeBatch.cargoLines.get(0).copy();
            line.advanceDestination();
            activeBatch = new ActiveBatch(activeBatch.scanGeneration, activeBatch.source,
                    activeBatch.canonicalSourceKey, List.of(line));
        }
    }

    public void finishCurrentTask() {
        if (hasInFlight()) throw new IllegalStateException("Cannot finish with an in-flight stack");
        current = null;
        destinationIndex = 0;
        if (activeBatch != null && !activeBatch.hasInFlight()) activeBatch = null;
    }

    public void deferCurrentTask() {
        if (hasInFlight()) throw new IllegalStateException("Cannot defer with an in-flight stack");
        if (current != null) pending.addLast(current);
        current = null;
        destinationIndex = 0;
    }

    public boolean hasInFlight() {
        return !inFlight.isEmpty() || hasActiveCargo();
    }

    public ItemStack getInFlight() {
        if (!inFlight.isEmpty()) return inFlight.copy();
        if (activeBatch != null) {
            return activeBatch.cargoLines.stream().filter(CargoLine::hasInFlight)
                    .findFirst().map(CargoLine::inFlightStack).orElse(ItemStack.EMPTY);
        }
        return ItemStack.EMPTY;
    }

    public int getBufferSlot() {
        if (!inFlight.isEmpty()) return bufferSlot;
        if (activeBatch != null) {
            return activeBatch.cargoLines.stream().filter(CargoLine::hasInFlight)
                    .findFirst().map(CargoLine::transportSlot).orElse(-1);
        }
        return -1;
    }

    private int legacyBufferSlotForCodec() {
        return bufferSlot;
    }

    public void setInFlight(ItemStack stack, int slot) {
        if (stack == null || stack.isEmpty()) {
            clearInFlight();
            return;
        }
        if (slot < 0) throw new IllegalArgumentException("slot must be non-negative");
        inFlight = stack.copy();
        bufferSlot = slot;
        if (activeBatch != null && activeBatch.cargoLines.size() == 1) {
            CargoLine old = activeBatch.cargoLines.get(0);
            if (ItemStack.isSameItemSameTags(old.stack(), stack)) {
                CargoLine replacement = new CargoLine(old.stack(),
                        Math.max(old.requestedCount(), stack.getCount()), stack.getCount(), slot,
                        old.baselineCount(), old.ignoreKey(), old.destinations(), old.destinationIndex());
                activeBatch = new ActiveBatch(activeBatch.scanGeneration, activeBatch.source,
                        activeBatch.canonicalSourceKey, List.of(replacement));
            }
        }
    }

    public void clearInFlight() {
        inFlight = ItemStack.EMPTY;
        bufferSlot = -1;
        if (activeBatch != null && activeBatch.cargoLines.size() == 1) {
            CargoLine old = activeBatch.cargoLines.get(0);
            CargoLine replacement = new CargoLine(old.stack(), old.requestedCount(), 0,
                    old.transportSlot(), old.baselineCount(), old.ignoreKey(),
                    old.destinations(), old.destinationIndex());
            activeBatch = new ActiveBatch(activeBatch.scanGeneration, activeBatch.source,
                    activeBatch.canonicalSourceKey, List.of(replacement));
        }
    }

    private Optional<ItemStack> inFlightForCodec() {
        return inFlight.isEmpty() ? Optional.empty() : Optional.of(inFlight.copy());
    }

    public void clearUnstartedWork() {
        pending.clear();
        pendingSourceJobs.clear();
        if (!hasInFlight()) {
            current = null;
            destinationIndex = 0;
            activeBatch = null;
        }
    }

    public void resetAfterPublish() {
        if (hasInFlight()) throw new IllegalStateException("Cannot publish with an in-flight stack");
        pending.clear();
        pendingSourceJobs.clear();
        current = null;
        activeBatch = null;
        destinationIndex = 0;
        ignoredKeys.clear();
        fullMarks.clear();
        cleanupSourceTotal = 0;
        phase = Phase.IDLE;
    }

}
