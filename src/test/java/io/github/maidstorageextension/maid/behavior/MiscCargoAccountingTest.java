package io.github.maidstorageextension.maid.behavior;

import io.github.maidstorageextension.maid.memory.MiscSortMemory;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import studio.fantasyit.maid_storage_manager.storage.Target;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MiscCargoAccountingTest {
    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void preservedBaselinePreventsUsingPreExistingOrCourierCargo() {
        assertEquals(32, MiscCargoAccounting.available(32, 49, 17));
        assertEquals(12, MiscCargoAccounting.available(32, 29, 17));
        assertEquals(0, MiscCargoAccounting.available(32, 12, 17));
    }

    @Test
    void partialInsertionKeepsExactRemainderAndAdvancesCandidate() {
        var result = MiscCargoAccounting.afterInsertion(64, 64, 44);
        assertEquals(20, result.moved());
        assertEquals(44, result.journalRemaining());
        assertTrue(result.candidateExhausted());
    }

    @Test
    void temporarilyMissingCargoDoesNotFalselyMarkDestinationFull() {
        var result = MiscCargoAccounting.afterInsertion(64, 32, 0);
        assertEquals(32, result.moved());
        assertEquals(32, result.journalRemaining());
        assertFalse(result.candidateExhausted());
    }

    @Test
    void zeroCapacityMarksCandidateFullWithoutClearingJournal() {
        var result = MiscCargoAccounting.afterInsertion(32, 32, 32);
        assertEquals(0, result.moved());
        assertEquals(32, result.journalRemaining());
        assertTrue(result.candidateExhausted());
    }

    @Test
    void oneDestinationOperationGroupsEveryExactCargoLineForTheSamePhysicalTarget() {
        Target firstView = target("chest", 8, 2, 3);
        Target secondView = target("barrel", 9, 2, 3);
        Target elsewhere = target("chest", 20, 2, 3);
        List<MiscSortMemory.CargoLine> lines = List.of(
                cargo(new ItemStack(Items.OAK_LOG), 64, 64, 0, firstView),
                cargo(new ItemStack(Items.GOLDEN_HELMET), 1, 1, 1, secondView),
                cargo(new ItemStack(Items.DIRT), 32, 32, 2, elsewhere));

        List<Integer> grouped = MiscCargoAccounting.lineIndexesAtDestination(
                lines, "double-chest@8,2,3",
                target -> target.equals(elsewhere)
                        ? "chest@20,2,3" : "double-chest@8,2,3");

        assertEquals(List.of(0, 1), grouped,
                "different exact items that resolve to one physical target are deposited together");
    }

    @Test
    void destinationContextStaysOpenWhileAnyCargoStillPointsAtThatPhysicalChest() {
        Target firstView = target("chest", 8, 2, 3);
        Target secondView = target("barrel", 9, 2, 3);
        Target elsewhere = target("chest", 20, 2, 3);
        List<MiscSortMemory.CargoLine> lines = List.of(
                cargo(new ItemStack(Items.OAK_LOG), 64, 0, 0, firstView),
                cargo(new ItemStack(Items.GOLDEN_HELMET), 1, 1, 1, secondView),
                cargo(new ItemStack(Items.DIRT), 32, 32, 2, elsewhere));

        boolean complete = MiscCargoAccounting.destinationOperationComplete(
                lines, "double-chest@8,2,3",
                target -> target.equals(elsewhere)
                        ? "chest@20,2,3" : "double-chest@8,2,3");

        assertFalse(complete,
                "the helmet still targets the open physical chest, so the context must be reused");
    }

    @Test
    void destinationContextClosesAfterCargoIsDeliveredOrAdvancedElsewhere() {
        Target deliveredTarget = target("chest", 8, 2, 3);
        Target elsewhere = target("chest", 20, 2, 3);
        List<MiscSortMemory.CargoLine> lines = List.of(
                cargo(new ItemStack(Items.OAK_LOG), 64, 0, 0, deliveredTarget),
                cargo(new ItemStack(Items.DIRT), 32, 32, 1, elsewhere));

        assertTrue(MiscCargoAccounting.destinationOperationComplete(
                lines, "chest@8,2,3",
                target -> target.equals(elsewhere) ? "chest@20,2,3" : "chest@8,2,3"));
    }

    @Test
    void destinationRemainderAdvancesCandidateButSourceRemainderRetriesSameSource() {
        var destination = MiscCargoAccounting.afterDestinationInsertion(64, 64, 44);
        assertEquals(20, destination.moved());
        assertEquals(44, destination.journalRemaining());
        assertTrue(destination.markCandidateFull());
        assertFalse(destination.retrySameSource());

        var source = MiscCargoAccounting.afterSourceInsertion(64, 64, 44);
        assertEquals(20, source.moved());
        assertEquals(44, source.journalRemaining());
        assertFalse(source.markCandidateFull(),
                "the unique source is never converted into a disposable full candidate");
        assertTrue(source.retrySameSource(),
                "a partially accepting source must retain the ledger and be retried");
    }

    @Test
    void networkRemainderIsAFullSignalOnlyForThatExactCargoJournal() {
        var acceptedLogs = MiscCargoAccounting.afterDestinationInsertion(64, 64, 0);
        var rejectedHelmetRemainder = MiscCargoAccounting.afterDestinationInsertion(1, 1, 1);

        assertEquals(0, acceptedLogs.journalRemaining());
        assertFalse(acceptedLogs.markCandidateFull());
        assertEquals(1, rejectedHelmetRemainder.journalRemaining());
        assertTrue(rejectedHelmetRemainder.markCandidateFull());
    }

    @Test
    void targetGainAndMaidLossRepairAnUncommittedCargoJournal() {
        assertEquals(0, MiscCargoAccounting.reconcileDestinationJournal(
                64, 64, 12,
                64, 0, 76));
    }

    @Test
    void anAlreadyCommittedPartialInsertionIsNotCountedTwice() {
        assertEquals(32, MiscCargoAccounting.reconcileDestinationJournal(
                64, 64, 12,
                32, 32, 44));
    }

    @Test
    void missingMaidCargoWithoutAMatchingTargetGainRemainsProtected() {
        assertEquals(64, MiscCargoAccounting.reconcileDestinationJournal(
                64, 64, 12,
                64, 0, 12));
    }

    private static MiscSortMemory.CargoLine cargo(
            ItemStack stack, int requested, int inFlight, int slot, Target destination) {
        return new MiscSortMemory.CargoLine(stack, requested, inFlight, slot,
                0, "item:test", List.of(destination), 0);
    }

    private static Target target(String block, int x, int y, int z) {
        return new Target(new ResourceLocation("minecraft", block), new BlockPos(x, y, z));
    }
}
