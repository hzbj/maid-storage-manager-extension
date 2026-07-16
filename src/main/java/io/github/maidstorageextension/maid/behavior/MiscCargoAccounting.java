package io.github.maidstorageextension.maid.behavior;

import io.github.maidstorageextension.maid.memory.MiscSortMemory;
import studio.fantasyit.maid_storage_manager.storage.Target;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/** Pure exact-cargo ledger arithmetic shared by destination and source insertion. */
final class MiscCargoAccounting {
    record InsertResult(int moved, int journalRemaining, boolean candidateExhausted) {
    }

    /**
     * Operation-level decision. Destination remainder exhausts a replaceable candidate, while
     * source remainder keeps the only recovery route and requests another return attempt.
     */
    record InsertionDecision(int moved, int journalRemaining,
                             boolean markCandidateFull, boolean retrySameSource) {
    }

    private MiscCargoAccounting() {
    }

    static int available(int journalCount, int exactInventoryTotal, int preservedBaseline) {
        if (journalCount < 0 || exactInventoryTotal < 0 || preservedBaseline < 0) {
            throw new IllegalArgumentException("cargo counts must not be negative");
        }
        return Math.min(journalCount, Math.max(0, exactInventoryTotal - preservedBaseline));
    }

    static InsertResult afterInsertion(int journalCount, int offered, int insertionRemainder) {
        if (journalCount < 0 || offered < 0 || offered > journalCount
                || insertionRemainder < 0 || insertionRemainder > offered) {
            throw new IllegalArgumentException("invalid insertion accounting");
        }
        int moved = offered - insertionRemainder;
        return new InsertResult(moved, journalCount - moved, insertionRemainder > 0);
    }

    static InsertionDecision afterDestinationInsertion(
            int journalCount, int offered, int insertionRemainder) {
        InsertResult result = afterInsertion(journalCount, offered, insertionRemainder);
        return new InsertionDecision(result.moved(), result.journalRemaining(),
                result.candidateExhausted(), false);
    }

    static InsertionDecision afterSourceInsertion(
            int journalCount, int offered, int insertionRemainder) {
        InsertResult result = afterInsertion(journalCount, offered, insertionRemainder);
        return new InsertionDecision(result.moved(), result.journalRemaining(),
                false, result.journalRemaining() > 0);
    }

    static int reconcileDestinationJournal(
            int initialJournal, int initialMaidCargo, int initialTargetCount,
            int currentJournal, int currentMaidCargo, int currentTargetCount) {
        if (initialJournal < 0 || initialMaidCargo < 0 || initialTargetCount < 0
                || currentJournal < 0 || currentMaidCargo < 0 || currentTargetCount < 0
                || currentJournal > initialJournal) {
            throw new IllegalArgumentException("invalid destination observation counts");
        }
        int departedFromMaid = Math.max(0, initialMaidCargo - currentMaidCargo);
        int arrivedAtTarget = Math.max(0, currentTargetCount - initialTargetCount);
        int physicallyConfirmed = Math.min(departedFromMaid, arrivedAtTarget);
        int alreadyCommitted = initialJournal - currentJournal;
        int uncommittedDelivery = Math.max(0, physicallyConfirmed - alreadyCommitted);
        return Math.max(0, currentJournal - uncommittedDelivery);
    }

    /** Selects every exact cargo line routed to the same normalized physical target. */
    static List<Integer> lineIndexesAtDestination(
            List<MiscSortMemory.CargoLine> lines,
            String operationTargetKey,
            Function<Target, String> canonicalizer) {
        Objects.requireNonNull(lines, "lines");
        Objects.requireNonNull(operationTargetKey, "operationTargetKey");
        Objects.requireNonNull(canonicalizer, "canonicalizer");
        List<Integer> result = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            MiscSortMemory.CargoLine line = lines.get(i);
            if (!line.hasInFlight() || line.currentDestination().isEmpty()) continue;
            String key = canonicalizer.apply(line.currentDestination().orElseThrow());
            if (operationTargetKey.equals(key)) result.add(i);
        }
        return List.copyOf(result);
    }

    /**
     * A physical destination context can close only after every in-flight line has either
     * been delivered or advanced away from that destination. Closing it after each pass makes
     * normal chest contexts call open/stop again on the next tick and visibly flap the lid.
     */
    static boolean destinationOperationComplete(
            List<MiscSortMemory.CargoLine> lines,
            String operationTargetKey,
            Function<Target, String> canonicalizer) {
        return lineIndexesAtDestination(lines, operationTargetKey, canonicalizer).isEmpty();
    }
}
