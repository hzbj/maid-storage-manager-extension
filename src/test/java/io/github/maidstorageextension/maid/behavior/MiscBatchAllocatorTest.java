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

class MiscBatchAllocatorTest {
    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void twoSlotsGiveDifferentPayloadsOneTurnBeforeLargePayloadCanContinue() {
        Target source = target("chest", 1, 2, 3);
        Target destination = target("barrel", 8, 2, 3);
        MiscSortMemory.SourceJob job = new MiscSortMemory.SourceJob(source, "source@canonical", List.of(
                new MiscSortMemory.PayloadPlan(new ItemStack(Items.OAK_LOG), 128L,
                        "item:minecraft:oak_log", List.of(destination)),
                new MiscSortMemory.PayloadPlan(new ItemStack(Items.GOLDEN_HELMET), 2L,
                        "item:minecraft:golden_helmet", List.of(destination))));

        MiscBatchAllocator.Allocation allocation = MiscBatchAllocator.allocate(
                12L, job, List.of(3, 7), stack -> 0);

        assertTrue(allocation.allocated());
        var batch = allocation.activeBatch().orElseThrow();
        assertEquals(12L, batch.scanGeneration());
        assertEquals("source@canonical", batch.canonicalSourceKey());
        assertEquals(List.of(Items.OAK_LOG, Items.GOLDEN_HELMET),
                batch.cargoLines().stream().map(line -> line.stack().getItem()).toList());
        assertEquals(List.of(64, 1),
                batch.cargoLines().stream().map(MiscSortMemory.CargoLine::requestedCount).toList());
        assertEquals(List.of(3, 7),
                batch.cargoLines().stream().map(MiscSortMemory.CargoLine::transportSlot).toList());

        var remainder = allocation.remainder().orElseThrow().payloads();
        assertEquals(List.of(64L, 1L),
                remainder.stream().map(MiscSortMemory.PayloadPlan::remainingCount).toList());
        assertEquals("item:minecraft:oak_log", remainder.get(0).ignoreKey());
        assertEquals(List.of(destination), remainder.get(1).destinations());
    }

    @Test
    void additionalSlotsContinueInRoundRobinOrderUntilAllPayloadsFit() {
        Target source = target("chest", 1, 2, 3);
        Target destination = target("barrel", 8, 2, 3);
        MiscSortMemory.SourceJob job = new MiscSortMemory.SourceJob(source, List.of(
                new MiscSortMemory.PayloadPlan(new ItemStack(Items.OAK_LOG), 128L,
                        "logs", List.of(destination)),
                new MiscSortMemory.PayloadPlan(new ItemStack(Items.GOLDEN_HELMET), 2L,
                        "helmets", List.of(destination))));

        MiscBatchAllocator.Allocation allocation = MiscBatchAllocator.allocate(
                4L, job, List.of(0, 1, 2, 3, 4), stack -> 0);

        var lines = allocation.activeBatch().orElseThrow().cargoLines();
        assertEquals(List.of(Items.OAK_LOG, Items.GOLDEN_HELMET,
                        Items.OAK_LOG, Items.GOLDEN_HELMET),
                lines.stream().map(line -> line.stack().getItem()).toList());
        assertEquals(List.of(0, 1, 2, 3),
                lines.stream().map(MiscSortMemory.CargoLine::transportSlot).toList());
        assertTrue(allocation.remainder().isEmpty());
    }

    @Test
    void duplicateSlotNumbersAreUsedOnceAndEveryLineKeepsItsExactBaseline() {
        Target source = target("chest", 1, 2, 3);
        Target destination = target("barrel", 8, 2, 3);
        ItemStack namedHelmet = new ItemStack(Items.GOLDEN_HELMET);
        namedHelmet.getOrCreateTag().putString("variant", "named");
        MiscSortMemory.SourceJob job = new MiscSortMemory.SourceJob(source, List.of(
                new MiscSortMemory.PayloadPlan(new ItemStack(Items.OAK_LOG), 128L,
                        "logs", List.of(destination)),
                new MiscSortMemory.PayloadPlan(namedHelmet, 2L,
                        "named-helmets", List.of(destination))));

        MiscBatchAllocator.Allocation allocation = MiscBatchAllocator.allocate(
                9L, job, List.of(5, 5, 2, 2, 8),
                stack -> stack.getItem() == Items.OAK_LOG ? 17 : 3);

        var lines = allocation.activeBatch().orElseThrow().cargoLines();
        assertEquals(3, lines.size());
        assertEquals(List.of(5, 2, 8),
                lines.stream().map(MiscSortMemory.CargoLine::transportSlot).toList());
        assertEquals(List.of(17, 3, 17),
                lines.stream().map(MiscSortMemory.CargoLine::baselineCount).toList());
        assertEquals("named", lines.get(1).stack().getTag().getString("variant"));
        assertEquals("named-helmets", lines.get(1).ignoreKey());
        assertFalse(allocation.remainder().isEmpty());
        assertEquals(1L, allocation.remainder().orElseThrow().payloads().get(0).remainingCount(),
                "the remaining helmet is retained after the third slot goes back to logs");
    }

    @Test
    void noEmptySlotProducesNoBatchAndReturnsTheWholeSourceJob() {
        Target source = target("chest", 1, 2, 3);
        Target destination = target("barrel", 8, 2, 3);
        MiscSortMemory.SourceJob job = new MiscSortMemory.SourceJob(source, List.of(
                new MiscSortMemory.PayloadPlan(new ItemStack(Items.OAK_LOG), Long.MAX_VALUE,
                        "logs", List.of(destination))));

        MiscBatchAllocator.Allocation allocation = MiscBatchAllocator.allocate(
                1L, job, List.of(), stack -> 0);

        assertFalse(allocation.allocated());
        assertEquals(Long.MAX_VALUE,
                allocation.remainder().orElseThrow().payloads().get(0).remainingCount());
    }

    @Test
    void networkExtractionTemplatesAggregateEveryReservedLineByExactNbt() {
        Target source = target("chest", 1, 2, 3);
        Target destination = target("barrel", 8, 2, 3);
        ItemStack namedHelmet = new ItemStack(Items.GOLDEN_HELMET);
        namedHelmet.getOrCreateTag().putString("variant", "named");
        MiscSortMemory memory = new MiscSortMemory();
        memory.beginGeneration(6L, io.github.maidstorageextension.scan.MiscSortPlanner.IgnorePolicy.ITEM_ID);
        memory.setActiveBatch(new MiscSortMemory.ActiveBatch(6L, source, "source", List.of(
                new MiscSortMemory.CargoLine(new ItemStack(Items.OAK_LOG), 64, 0, 0,
                        0, "logs", List.of(destination), 0),
                new MiscSortMemory.CargoLine(new ItemStack(Items.OAK_LOG), 64, 16, 1,
                        0, "logs", List.of(destination), 0),
                new MiscSortMemory.CargoLine(namedHelmet, 1, 0, 2,
                        0, "helmets", List.of(destination), 0))));

        List<ItemStack> templates = MiscSortBehavior.extractionTemplates(memory);

        assertEquals(2, templates.size());
        ItemStack logs = templates.stream().filter(stack -> stack.is(Items.OAK_LOG))
                .findFirst().orElseThrow();
        ItemStack helmet = templates.stream().filter(stack -> stack.is(Items.GOLDEN_HELMET))
                .findFirst().orElseThrow();
        assertEquals(112, logs.getCount());
        assertEquals(1, helmet.getCount());
        assertEquals("named", helmet.getTag().getString("variant"));
    }

    private static Target target(String block, int x, int y, int z) {
        return new Target(new ResourceLocation("minecraft", block), new BlockPos(x, y, z));
    }
}
