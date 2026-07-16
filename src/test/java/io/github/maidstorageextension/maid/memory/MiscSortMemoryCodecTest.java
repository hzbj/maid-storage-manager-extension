package io.github.maidstorageextension.maid.memory;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import io.github.maidstorageextension.scan.MiscSortPlanner;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import studio.fantasyit.maid_storage_manager.storage.Target;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MiscSortMemoryCodecTest {
    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void codecRoundTripPreservesTheOnlyActiveTransferJournal() {
        Target source = target("chest", 1, 2, 3);
        Target first = target("chest", 8, 2, 3);
        Target second = target("barrel", 12, 2, 3);
        ItemStack exact = new ItemStack(Items.DIAMOND, 17);
        exact.getOrCreateTag().putString("marker", "kept");

        MiscSortMemory memory = new MiscSortMemory();
        memory.setPhase(MiscSortMemory.Phase.MOVING);
        memory.replaceTasks(List.of(
                new MiscSortMemory.TransferTask(source, exact, List.of(first, second)),
                new MiscSortMemory.TransferTask(source, exact.copyWithCount(3), List.of(second))));
        memory.beginNextTask();
        memory.advanceDestination();
        memory.setInFlight(exact.copyWithCount(12), 4);

        Tag encoded = MiscSortMemory.CODEC.encodeStart(NbtOps.INSTANCE, memory).result().orElseThrow();
        MiscSortMemory decoded = MiscSortMemory.CODEC.parse(NbtOps.INSTANCE, encoded)
                .result().orElseThrow();

        assertEquals(MiscSortMemory.Phase.MOVING, decoded.getPhase());
        assertEquals(source, decoded.getCurrentTask().orElseThrow().source());
        assertEquals(second, decoded.getCurrentDestination().orElseThrow());
        assertEquals(1, decoded.getPendingTasks().size());
        assertEquals(12, decoded.getInFlight().getCount());
        assertEquals("kept", decoded.getInFlight().getTag().getString("marker"));
        assertEquals(4, decoded.getBufferSlot());
        assertTrue(decoded.hasInFlight());
        assertEquals(-1, decoded.getActiveBatch().orElseThrow().cargoLines().get(0).baselineCount());

        CompoundTag migrated = (CompoundTag) MiscSortMemory.CODEC
                .encodeStart(NbtOps.INSTANCE, decoded).result().orElseThrow();
        assertTrue(migrated.contains("active_batch"));
        assertFalse(migrated.contains("current_task"));
        assertFalse(migrated.contains("in_flight_stack"));
        assertFalse(migrated.contains("pending_tasks"));
    }

    @Test
    void batchCodecPreservesMultipleCargoLinesBaselinesIgnoreLedgerAndFullMarks() {
        Target source = target("chest", 1, 2, 3);
        Target first = target("chest", 8, 2, 3);
        Target second = target("barrel", 12, 2, 3);
        ItemStack diamonds = new ItemStack(Items.DIAMOND);
        ItemStack dirt = new ItemStack(Items.DIRT);
        dirt.getOrCreateTag().putString("variant", "kept");

        MiscSortMemory memory = new MiscSortMemory();
        memory.beginGeneration(7L, MiscSortPlanner.IgnorePolicy.EXACT_STACK);
        String dirtKey = MiscSortMemory.ignoreKeyFor(dirt, MiscSortPlanner.IgnorePolicy.EXACT_STACK);
        memory.markIgnored(dirtKey);
        memory.replaceSourceJobs(List.of(new MiscSortMemory.SourceJob(source, "chest@canonical", List.of(
                new MiscSortMemory.PayloadPlan(diamonds, 128L, "exact:diamonds", List.of(first, second))))));
        memory.setActiveBatch(new MiscSortMemory.ActiveBatch(7L, source, "chest@canonical", List.of(
                new MiscSortMemory.CargoLine(diamonds, 17, 12, 4,
                        9, "exact:diamonds", List.of(first, second), 1),
                new MiscSortMemory.CargoLine(dirt, 32, 32, 6,
                        0, dirtKey, List.of(second), 0))));
        memory.markFull("barrel@canonical", dirt);

        Tag encoded = MiscSortMemory.CODEC.encodeStart(NbtOps.INSTANCE, memory).result().orElseThrow();
        MiscSortMemory decoded = MiscSortMemory.CODEC.parse(NbtOps.INSTANCE, encoded)
                .result().orElseThrow();

        assertEquals(7L, decoded.getScanGeneration());
        assertEquals(MiscSortPlanner.IgnorePolicy.EXACT_STACK, decoded.getIgnorePolicy());
        assertTrue(decoded.isIgnored(dirtKey));
        assertEquals(1, decoded.getPendingSourceJobs().size());
        var lines = decoded.getActiveBatch().orElseThrow().cargoLines();
        assertEquals(2, lines.size());
        assertEquals(9, lines.get(0).baselineCount());
        assertEquals("exact:diamonds", lines.get(0).ignoreKey());
        assertEquals(0, lines.get(1).baselineCount());
        assertEquals(1, lines.get(0).destinationIndex());
        assertTrue(decoded.isMarkedFull(7L, "barrel@canonical", dirt));

        decoded.advanceCargoLineDestination(1);
        decoded.forceActiveBatchReturn();
        assertTrue(decoded.getActiveBatch().orElseThrow().cargoLines().stream()
                .allMatch(line -> line.currentDestination().isEmpty()));

        ItemStack otherNbt = dirt.copy();
        otherNbt.getOrCreateTag().putString("variant", "other");
        assertFalse(decoded.isMarkedFull(7L, "barrel@canonical", otherNbt));
    }

    @Test
    void changingGenerationExpiresIgnoreAndFullLedgersButKeepsMonotonicIdentity() {
        ItemStack dirt = new ItemStack(Items.DIRT);
        MiscSortMemory memory = new MiscSortMemory();
        memory.beginGeneration(3L, MiscSortPlanner.IgnorePolicy.ITEM_ID);
        memory.markIgnored("item:minecraft:dirt");
        memory.markFull("chest@one", dirt);

        memory.beginGeneration(4L, MiscSortPlanner.IgnorePolicy.ITEM_ID);

        assertEquals(4L, memory.getScanGeneration());
        assertTrue(memory.getIgnoredKeys().isEmpty());
        assertTrue(memory.getFullMarks().isEmpty());
        assertFalse(memory.isMarkedFull(3L, "chest@one", dirt));
    }

    @Test
    void runtimeIgnorePrunesOnlyUnstartedPayloadsAndKeepsActiveCargo() {
        Target source = target("chest", 1, 2, 3);
        Target destination = target("barrel", 8, 2, 3);
        ItemStack logs = new ItemStack(Items.OAK_LOG);
        ItemStack dirt = new ItemStack(Items.DIRT);
        MiscSortMemory memory = new MiscSortMemory();
        memory.beginGeneration(8L, MiscSortPlanner.IgnorePolicy.ITEM_ID);
        memory.replaceSourceJobs(List.of(new MiscSortMemory.SourceJob(source, List.of(
                new MiscSortMemory.PayloadPlan(dirt, 64, "item:minecraft:dirt", List.of(destination)),
                new MiscSortMemory.PayloadPlan(logs, 64, "item:minecraft:oak_log", List.of(destination))))));
        memory.setActiveBatch(new MiscSortMemory.ActiveBatch(8L, source, "source", List.of(
                new MiscSortMemory.CargoLine(dirt, 32, 12, 4,
                        0, "item:minecraft:dirt", List.of(destination), 0))));

        memory.markIgnoredAndPrunePending("item:minecraft:dirt");

        assertEquals(12, memory.getActiveBatch().orElseThrow()
                .cargoLines().get(0).inFlightCount());
        assertEquals(List.of(Items.OAK_LOG), memory.getPendingSourceJobs().get(0).payloads().stream()
                .map(payload -> payload.stack().getItem()).toList());
    }

    @Test
    void policyChangeCanDiscardUnstartedWorkWithoutClearingExtractedCargo() {
        Target source = target("chest", 1, 2, 3);
        Target destination = target("barrel", 8, 2, 3);
        ItemStack logs = new ItemStack(Items.OAK_LOG);
        MiscSortMemory memory = new MiscSortMemory();
        memory.beginGeneration(11L, MiscSortPlanner.IgnorePolicy.ITEM_ID);
        memory.replaceSourceJobs(List.of(new MiscSortMemory.SourceJob(source, List.of(
                new MiscSortMemory.PayloadPlan(logs, 64,
                        "item:minecraft:oak_log", List.of(destination))))));
        memory.setActiveBatch(new MiscSortMemory.ActiveBatch(11L, source, "source", List.of(
                new MiscSortMemory.CargoLine(logs, 32, 20, 3,
                        0, "item:minecraft:oak_log", List.of(destination), 0))));

        memory.clearUnstartedWork();

        assertTrue(memory.getPendingSourceJobs().isEmpty());
        assertEquals(20, memory.getActiveBatch().orElseThrow()
                .cargoLines().get(0).inFlightCount());
        assertEquals(MiscSortPlanner.IgnorePolicy.ITEM_ID, memory.getIgnorePolicy(),
                "the new policy is adopted only by the replacement full patrol");
    }

    @Test
    void activeBatchCargoSnapshotSupportsJournalReplacementWithoutMutatingStoredState() {
        Target source = target("chest", 1, 2, 3);
        Target destination = target("barrel", 8, 2, 3);
        ItemStack logs = new ItemStack(Items.OAK_LOG);
        MiscSortMemory memory = new MiscSortMemory();
        memory.beginGeneration(2L, MiscSortPlanner.IgnorePolicy.ITEM_ID);
        memory.setActiveBatch(new MiscSortMemory.ActiveBatch(2L, source, "source", List.of(
                new MiscSortMemory.CargoLine(logs, 32, 12, 4,
                        -1, "item:minecraft:oak_log", List.of(destination), 0))));

        List<MiscSortMemory.CargoLine> snapshot = memory.getActiveBatch().orElseThrow().cargoLines();
        snapshot.set(0, snapshot.get(0).withBaselineCount(7));

        assertEquals(-1, memory.getActiveBatch().orElseThrow().cargoLines().get(0).baselineCount(),
                "editing the behavior snapshot must not bypass the persistent journal");
        memory.replaceCargoLines(snapshot);
        assertEquals(7, memory.getActiveBatch().orElseThrow().cargoLines().get(0).baselineCount());
    }

    @Test
    void taskSwitchReturnsTheWholeBatchToItsUniqueSourceAcrossReload() {
        Target source = target("chest", 1, 2, 3);
        Target first = target("barrel", 8, 2, 3);
        Target second = target("chest", 12, 2, 3);
        ItemStack logs = new ItemStack(Items.OAK_LOG);
        ItemStack helmet = new ItemStack(Items.GOLDEN_HELMET);
        helmet.getOrCreateTag().putString("enchantment", "kept-exactly");
        MiscSortMemory memory = new MiscSortMemory();
        memory.beginGeneration(14L, MiscSortPlanner.IgnorePolicy.ITEM_ID);
        memory.replaceSourceJobs(List.of(new MiscSortMemory.SourceJob(source, List.of(
                new MiscSortMemory.PayloadPlan(logs, 64, "item:minecraft:oak_log", List.of(first))))));
        memory.setActiveBatch(new MiscSortMemory.ActiveBatch(14L, source, "unique-source", List.of(
                new MiscSortMemory.CargoLine(logs, 64, 44, 2,
                        3, "item:minecraft:oak_log", List.of(first, second), 1),
                new MiscSortMemory.CargoLine(helmet, 1, 1, 5,
                        0, "item:minecraft:golden_helmet", List.of(second), 0))));

        memory.clearUnstartedWork();
        memory.forceActiveBatchReturn();
        Tag encoded = MiscSortMemory.CODEC.encodeStart(NbtOps.INSTANCE, memory).result().orElseThrow();
        MiscSortMemory decoded = MiscSortMemory.CODEC.parse(NbtOps.INSTANCE, encoded)
                .result().orElseThrow();

        assertTrue(decoded.getPendingSourceJobs().isEmpty());
        var recovered = decoded.getActiveBatch().orElseThrow();
        assertEquals(source, recovered.source());
        assertEquals("unique-source", recovered.canonicalSourceKey());
        assertEquals(List.of(44, 1), recovered.cargoLines().stream()
                .map(MiscSortMemory.CargoLine::inFlightCount).toList());
        assertTrue(recovered.cargoLines().stream()
                .allMatch(line -> line.currentDestination().isEmpty()));
        assertEquals("kept-exactly", recovered.cargoLines().get(1).stack()
                .getTag().getString("enchantment"));
    }

    @Test
    void cleanupProgressCountsEachSourceOnceAcrossSplitBatchesAndReload() {
        Target firstSource = target("chest", 1, 2, 3);
        Target secondSource = target("barrel", 4, 2, 3);
        Target destination = target("chest", 8, 2, 3);
        ItemStack logs = new ItemStack(Items.OAK_LOG);
        ItemStack dirt = new ItemStack(Items.DIRT);
        MiscSortMemory.SourceJob firstJob = new MiscSortMemory.SourceJob(
                firstSource, "source:first", List.of(new MiscSortMemory.PayloadPlan(
                logs, 128, "item:minecraft:oak_log", List.of(destination))));
        MiscSortMemory.SourceJob secondJob = new MiscSortMemory.SourceJob(
                secondSource, "source:second", List.of(new MiscSortMemory.PayloadPlan(
                dirt, 64, "item:minecraft:dirt", List.of(destination))));

        MiscSortMemory memory = new MiscSortMemory();
        memory.beginGeneration(9L, MiscSortPlanner.IgnorePolicy.ITEM_ID);
        memory.replaceSourceJobs(List.of(firstJob, secondJob));
        assertEquals(0, memory.getCleanupSourceProgress());
        assertEquals(2, memory.getCleanupSourceTotal());

        memory.pollNextSourceJob();
        memory.prependSourceJob(firstJob); // This source needs another transport batch.
        memory.setActiveBatch(activeBatch(9L, firstSource, "source:first", logs, destination));
        assertEquals(0, memory.getCleanupSourceProgress());
        memory.clearCompletedBatch();

        memory.pollNextSourceJob();
        memory.setActiveBatch(activeBatch(9L, firstSource, "source:first", logs, destination));
        memory.clearCompletedBatch();
        assertEquals(1, memory.getCleanupSourceProgress());

        memory.pollNextSourceJob();
        memory.setActiveBatch(activeBatch(9L, secondSource, "source:second", dirt, destination));
        Tag encoded = MiscSortMemory.CODEC.encodeStart(NbtOps.INSTANCE, memory).result().orElseThrow();
        MiscSortMemory decoded = MiscSortMemory.CODEC.parse(NbtOps.INSTANCE, encoded)
                .result().orElseThrow();
        assertEquals(1, decoded.getCleanupSourceProgress());
        assertEquals(2, decoded.getCleanupSourceTotal());

        decoded.clearCompletedBatch();
        assertEquals(2, decoded.getCleanupSourceProgress());
    }

    private static MiscSortMemory.ActiveBatch activeBatch(
            long generation, Target source, String sourceKey, ItemStack stack, Target destination) {
        return new MiscSortMemory.ActiveBatch(generation, source, sourceKey, List.of(
                new MiscSortMemory.CargoLine(stack, 1, 0, 0,
                        0, "item:test", List.of(destination), 0)));
    }

    private static Target target(String block, int x, int y, int z) {
        return new Target(new ResourceLocation("minecraft", block), new BlockPos(x, y, z));
    }
}
