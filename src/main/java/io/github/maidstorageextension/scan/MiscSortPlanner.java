package io.github.maidstorageextension.scan;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Pure planner built only from the completed patrol's latest cache. */
public final class MiscSortPlanner {
    public enum Match {
        WHITELIST,
        CACHED_SAME_ITEM
    }

    /** Chooses whether one failed variant suppresses only itself or every NBT variant of that item. */
    public enum IgnorePolicy {
        ITEM_ID,
        EXACT_STACK
    }

    public record StackView<S>(String itemId, String exactStackKey, S stack, long count) {
        public StackView {
            itemId = requireKey(itemId, "itemId");
            exactStackKey = requireKey(exactStackKey, "exactStackKey");
            Objects.requireNonNull(stack, "stack");
            if (count <= 0) throw new IllegalArgumentException("count must be positive");
        }
    }

    public record StorageView<T, S>(
            T target,
            String physicalKey,
            String stableTargetKey,
            double x,
            double y,
            double z,
            boolean miscStorage,
            boolean requestStorage,
            boolean collectEligible,
            boolean placeEligible,
            List<String> whitelistAcceptedExactStackKeys,
            List<StackView<S>> stacks) {
        public StorageView {
            Objects.requireNonNull(target, "target");
            physicalKey = requireKey(physicalKey, "physicalKey");
            stableTargetKey = requireKey(stableTargetKey, "stableTargetKey");
            whitelistAcceptedExactStackKeys = List.copyOf(whitelistAcceptedExactStackKeys);
            stacks = List.copyOf(stacks);
        }
    }

    public record Destination<T>(T target, String physicalKey, String stableTargetKey,
                                 Match match, double distanceSquared) {
    }

    public record Transfer<T, S>(T source, String sourcePhysicalKey, String sourceStableTargetKey,
                                 StackView<S> stack, List<Destination<T>> destinations) {
        public Transfer {
            destinations = List.copyOf(destinations);
        }
    }

    public record PayloadPlan<T, S>(StackView<S> stack, String ignoreKey,
                                    List<Destination<T>> destinations) {
        public PayloadPlan {
            Objects.requireNonNull(stack, "stack");
            ignoreKey = requireKey(ignoreKey, "ignoreKey");
            destinations = List.copyOf(destinations);
            if (destinations.isEmpty()) throw new IllegalArgumentException("destinations must not be empty");
        }
    }

    /** All classifiable payloads discovered in one misc storage during the completed patrol. */
    public record SourceJob<T, S>(T source, String sourcePhysicalKey, String sourceStableTargetKey,
                                  List<PayloadPlan<T, S>> payloads) {
        public SourceJob {
            Objects.requireNonNull(source, "source");
            sourcePhysicalKey = requireKey(sourcePhysicalKey, "sourcePhysicalKey");
            sourceStableTargetKey = requireKey(sourceStableTargetKey, "sourceStableTargetKey");
            payloads = List.copyOf(payloads);
            if (payloads.isEmpty()) throw new IllegalArgumentException("payloads must not be empty");
        }
    }

    /** Includes the ignore ledger produced while scanning the whole patrol, not just emitted jobs. */
    public record BatchPlan<T, S>(List<SourceJob<T, S>> sourceJobs, Set<String> ignoredKeys) {
        public BatchPlan {
            sourceJobs = List.copyOf(sourceJobs);
            ignoredKeys = Set.copyOf(ignoredKeys);
        }
    }

    private MiscSortPlanner() {
    }

    public static <T, S> List<Transfer<T, S>> plan(Collection<StorageView<T, S>> completedPatrol) {
        BatchPlan<T, S> batch = planBatch(completedPatrol, IgnorePolicy.ITEM_ID, Set.of());
        return batch.sourceJobs().stream()
                .flatMap(job -> job.payloads().stream().map(payload -> new Transfer<>(
                        job.source(), job.sourcePhysicalKey(), job.sourceStableTargetKey(),
                        payload.stack(), payload.destinations())))
                .toList();
    }

    public static <T, S> BatchPlan<T, S> planBatch(
            Collection<StorageView<T, S>> completedPatrol,
            IgnorePolicy ignorePolicy,
            Collection<String> alreadyIgnoredKeys) {
        Objects.requireNonNull(ignorePolicy, "ignorePolicy");
        List<StorageView<T, S>> storages = canonicalize(completedPatrol);
        LinkedHashSet<String> ignored = new LinkedHashSet<>(alreadyIgnoredKeys);
        List<SourceJob<T, S>> jobs = new ArrayList<>();
        storages.stream().filter(StorageView::miscStorage).filter(StorageView::collectEligible)
                .sorted(storageOrder()).forEach(source -> {
                    List<PayloadPlan<T, S>> payloads = new ArrayList<>();
                    for (StackView<S> stack : aggregateExactStacks(source.stacks())) {
                        String ignoreKey = ignoreKey(stack, ignorePolicy);
                        if (ignored.contains(ignoreKey)) continue;
                        List<Destination<T>> destinations = findDestinations(
                                source, stack, storages, ignorePolicy);
                        if (destinations.isEmpty()) {
                            ignored.add(ignoreKey);
                            continue;
                        }
                        payloads.add(new PayloadPlan<>(stack, ignoreKey, destinations));
                    }
                    if (!payloads.isEmpty()) {
                        jobs.add(new SourceJob<>(source.target(), source.physicalKey(),
                                source.stableTargetKey(), payloads));
                    }
                });
        return new BatchPlan<>(jobs, ignored);
    }

    public static String ignoreKey(StackView<?> stack, IgnorePolicy policy) {
        Objects.requireNonNull(stack, "stack");
        Objects.requireNonNull(policy, "policy");
        return switch (policy) {
            case ITEM_ID -> "item:" + stack.itemId();
            case EXACT_STACK -> "exact:" + stack.exactStackKey();
        };
    }

    private static <T, S> List<StorageView<T, S>> canonicalize(Collection<StorageView<T, S>> input) {
        List<StorageView<T, S>> ordered = new ArrayList<>(input);
        ordered.forEach(storage -> Objects.requireNonNull(storage, "completedPatrol contains null"));
        ordered.sort(storageOrder());
        Map<String, StorageView<T, S>> unique = new LinkedHashMap<>();
        ordered.forEach(storage -> unique.putIfAbsent(storage.physicalKey(), storage));
        return List.copyOf(unique.values());
    }

    private static <T, S> Comparator<StorageView<T, S>> storageOrder() {
        return Comparator.comparing(StorageView<T, S>::stableTargetKey)
                .thenComparing(StorageView::physicalKey);
    }

    private static <S> List<StackView<S>> aggregateExactStacks(List<StackView<S>> stacks) {
        record Identity(String itemId, String exactKey) {
        }
        final class Aggregate {
            private final StackView<S> first;
            private long count;

            private Aggregate(StackView<S> first) {
                this.first = first;
                this.count = first.count();
            }

            private void add(long amount) {
                count = Math.addExact(count, amount);
            }

            private StackView<S> snapshot() {
                return new StackView<>(first.itemId(), first.exactStackKey(), first.stack(), count);
            }
        }
        Map<Identity, Aggregate> totals = new LinkedHashMap<>();
        for (StackView<S> stack : stacks) {
            Identity identity = new Identity(stack.itemId(), stack.exactStackKey());
            Aggregate aggregate = totals.get(identity);
            if (aggregate == null) totals.put(identity, new Aggregate(stack));
            else aggregate.add(stack.count());
        }
        return totals.values().stream().map(Aggregate::snapshot)
                .sorted(Comparator.comparing(StackView<S>::itemId)
                        .thenComparing(StackView::exactStackKey))
                .toList();
    }

    private static <T, S> List<Destination<T>> findDestinations(
            StorageView<T, S> source, StackView<S> stack, List<StorageView<T, S>> storages,
            IgnorePolicy ignorePolicy) {
        return storages.stream()
                .filter(candidate -> !candidate.physicalKey().equals(source.physicalKey()))
                .filter(candidate -> !candidate.miscStorage())
                .filter(candidate -> !candidate.requestStorage())
                .filter(StorageView::placeEligible)
                .map(candidate -> destination(source, candidate, stack, ignorePolicy))
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(Destination<T>::match)
                        .thenComparingDouble(Destination::distanceSquared)
                        .thenComparing(Destination::stableTargetKey)
                        .thenComparing(Destination::physicalKey))
                .toList();
    }

    private static <T, S> Destination<T> destination(
            StorageView<T, S> source, StorageView<T, S> candidate, StackView<S> stack,
            IgnorePolicy ignorePolicy) {
        boolean whitelist = candidate.whitelistAcceptedExactStackKeys().contains(stack.exactStackKey());
        boolean containsItem = candidate.stacks().stream()
                .anyMatch(candidateStack -> ignorePolicy == IgnorePolicy.ITEM_ID
                        ? candidateStack.itemId().equals(stack.itemId())
                        : candidateStack.exactStackKey().equals(stack.exactStackKey()));
        if (!whitelist && !containsItem) return null;
        Match match = whitelist ? Match.WHITELIST : Match.CACHED_SAME_ITEM;
        double dx = source.x() - candidate.x();
        double dy = source.y() - candidate.y();
        double dz = source.z() - candidate.z();
        return new Destination<>(candidate.target(), candidate.physicalKey(),
                candidate.stableTargetKey(), match, dx * dx + dy * dy + dz * dz);
    }

    private static String requireKey(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value;
    }
}
