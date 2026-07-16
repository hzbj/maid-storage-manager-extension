package io.github.maidstorageextension.maid.behavior;

import io.github.maidstorageextension.maid.memory.MiscSortMemory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.ToIntFunction;

/**
 * Packs one misc-storage source job into the maid's currently empty transport slots.
 *
 * <p>Allocation is round-robin by exact payload. A payload receives at most one item
 * stack per pass, which prevents a large quantity of one item from monopolising every
 * available slot before other sortable items have been considered.</p>
 */
public final class MiscBatchAllocator {
    private MiscBatchAllocator() {
    }

    public record Allocation(Optional<MiscSortMemory.ActiveBatch> activeBatch,
                             Optional<MiscSortMemory.SourceJob> remainder) {
        public Allocation {
            activeBatch = activeBatch == null ? Optional.empty() : activeBatch;
            remainder = remainder == null ? Optional.empty() : remainder;
        }

        public boolean allocated() {
            return activeBatch.isPresent();
        }
    }

    /**
     * Allocates as much of {@code sourceJob} as fits into the supplied empty slots.
     * Counts remain {@code long} until each individual cargo line is bounded to an item
     * stack, so a corrupted or unusually large network-storage count cannot overflow.
     */
    public static Allocation allocate(long scanGeneration,
                                      MiscSortMemory.SourceJob sourceJob,
                                      Collection<Integer> emptySlots,
                                      ToIntFunction<ItemStack> exactBaselineCounter) {
        Objects.requireNonNull(sourceJob, "sourceJob");
        Objects.requireNonNull(emptySlots, "emptySlots");
        Objects.requireNonNull(exactBaselineCounter, "exactBaselineCounter");

        List<Integer> slots = normalizeSlots(emptySlots);
        List<MiscSortMemory.PayloadPlan> payloads = sourceJob.payloads();
        if (slots.isEmpty() || payloads.isEmpty()) {
            return new Allocation(Optional.empty(), Optional.of(sourceJob));
        }

        long[] remaining = new long[payloads.size()];
        int[] baselines = new int[payloads.size()];
        for (int i = 0; i < payloads.size(); i++) {
            MiscSortMemory.PayloadPlan payload = payloads.get(i);
            remaining[i] = payload.remainingCount();
            baselines[i] = Math.max(0, exactBaselineCounter.applyAsInt(payload.stack()));
        }

        List<MiscSortMemory.CargoLine> lines = new ArrayList<>(slots.size());
        int slotIndex = 0;
        boolean allocatedInPass;
        do {
            allocatedInPass = false;
            for (int payloadIndex = 0;
                 payloadIndex < payloads.size() && slotIndex < slots.size();
                 payloadIndex++) {
                if (remaining[payloadIndex] <= 0L) continue;

                MiscSortMemory.PayloadPlan payload = payloads.get(payloadIndex);
                ItemStack exactStack = payload.stack();
                int requested = (int) Math.min(
                        remaining[payloadIndex], Math.max(1, exactStack.getMaxStackSize()));
                lines.add(new MiscSortMemory.CargoLine(
                        exactStack,
                        requested,
                        0,
                        slots.get(slotIndex++),
                        baselines[payloadIndex],
                        payload.ignoreKey(),
                        payload.destinations(),
                        0));
                remaining[payloadIndex] -= requested;
                allocatedInPass = true;
            }
        } while (allocatedInPass && slotIndex < slots.size());

        if (lines.isEmpty()) {
            return new Allocation(Optional.empty(), Optional.of(sourceJob));
        }

        MiscSortMemory.ActiveBatch batch = new MiscSortMemory.ActiveBatch(
                Math.max(0L, scanGeneration),
                sourceJob.source(),
                sourceJob.canonicalSourceKey(),
                lines);

        List<MiscSortMemory.PayloadPlan> remainderPayloads = new ArrayList<>();
        for (int i = 0; i < payloads.size(); i++) {
            if (remaining[i] <= 0L) continue;
            MiscSortMemory.PayloadPlan payload = payloads.get(i);
            remainderPayloads.add(new MiscSortMemory.PayloadPlan(
                    payload.stack(), remaining[i], payload.ignoreKey(), payload.destinations()));
        }
        Optional<MiscSortMemory.SourceJob> remainder = remainderPayloads.isEmpty()
                ? Optional.empty()
                : Optional.of(new MiscSortMemory.SourceJob(
                        sourceJob.source(), sourceJob.canonicalSourceKey(), remainderPayloads));
        return new Allocation(Optional.of(batch), remainder);
    }

    private static List<Integer> normalizeSlots(Collection<Integer> emptySlots) {
        Set<Integer> unique = new LinkedHashSet<>();
        for (Integer slot : emptySlots) {
            if (slot == null) continue;
            if (slot < 0) throw new IllegalArgumentException("transport slots must be non-negative");
            unique.add(slot);
        }
        return List.copyOf(unique);
    }
}
