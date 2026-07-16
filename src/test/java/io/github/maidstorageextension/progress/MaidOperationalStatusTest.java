package io.github.maidstorageextension.progress;

import io.github.maidstorageextension.maid.memory.MiscSortMemory;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import studio.fantasyit.maid_storage_manager.craft.work.ProgressData;
import studio.fantasyit.maid_storage_manager.storage.Target;

import java.util.List;

import static io.github.maidstorageextension.progress.MaidOperationalStatus.Action;
import static io.github.maidstorageextension.progress.MaidOperationalStatus.ExtensionMode;
import static io.github.maidstorageextension.progress.MaidOperationalStatus.Kind;
import static io.github.maidstorageextension.progress.MaidOperationalStatus.Snapshot;
import static io.github.maidstorageextension.progress.MaidOperationalStatus.WorkMode;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class MaidOperationalStatusTest {
    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void sleepingWinsOverEveryQueuedOrStaleTask() {
        var result = MaidOperationalStatus.resolve(snapshot(
                true, false, false, ExtensionMode.INSPECTION_VIEWING, WorkMode.REQUEST, true));

        assertEquals(Kind.SLEEPING, result.kind());
        assertEquals(Action.NONE, result.action());
    }

    @Test
    void taskBellTravelIsReportedAsPathingBeforePausedInspection() {
        var result = MaidOperationalStatus.resolve(snapshot(
                false, false, false, ExtensionMode.TASK_BELL_TRAVELLING, WorkMode.NONE, true));

        assertEquals(Kind.TASK_BELL, result.kind());
        assertEquals(Action.PATHING, result.action());
    }

    @Test
    void inspectionStageAndProgressRemainVisible() {
        Snapshot snapshot = new Snapshot(false, false, false, false, false,
                ExtensionMode.INSPECTION_VIEWING, WorkMode.VIEWING, false, 7, 12);

        var result = MaidOperationalStatus.resolve(snapshot);

        assertEquals(Kind.INSPECTION, result.kind());
        assertEquals(Action.INSPECTING, result.action());
        assertEquals(7, result.progress());
        assertEquals(12, result.total());
    }

    @Test
    void cleanupShowsSourceBoxProgressEvenWhileMaidIsPathing() {
        Snapshot snapshot = new Snapshot(false, false, false, false, false,
                ExtensionMode.INSPECTION_CLEANING, WorkMode.SORTING, true, 1, 3);

        var result = MaidOperationalStatus.resolve(snapshot);

        assertEquals(Kind.INSPECTION, result.kind());
        assertEquals(Action.CLEANING, result.action());
        assertEquals(1, result.progress());
        assertEquals(3, result.total());
    }

    @Test
    void ordinaryTaskShowsItsPurposeAndPathingState() {
        var result = MaidOperationalStatus.resolve(snapshot(
                false, false, false, ExtensionMode.NONE, WorkMode.LOGISTICS, true));

        assertEquals(Kind.LOGISTICS, result.kind());
        assertEquals(Action.PATHING, result.action());
    }

    @Test
    void restAndIdleAreDistinct() {
        var resting = MaidOperationalStatus.resolve(new Snapshot(
                false, false, false, true, false,
                ExtensionMode.NONE, WorkMode.NONE, false, 0, 0));
        var idle = MaidOperationalStatus.resolve(new Snapshot(
                false, false, false, false, true,
                ExtensionMode.NONE, WorkMode.NONE, false, 0, 0));

        assertEquals(Kind.RESTING, resting.kind());
        assertEquals(Kind.STANDBY, idle.kind());
    }

    @Test
    void normalHeaderUsesReadableStatusAndKeepsInspectionProgress() {
        var status = new MaidOperationalStatus.Resolved(Kind.INSPECTION, Action.INSPECTING, 7, 12);

        var text = ProgressPadStatusService.statusText(status, false);

        var contents = assertInstanceOf(TranslatableContents.class, text.getContents());
        assertEquals("gui.maid_storage_manager_extension.progress_pad.status.combined", contents.getKey());
        assertEquals(" 7/12", text.getSiblings().get(0).getString());
    }

    @Test
    void smallFrameUsesCompactHeaderTranslation() {
        var status = new MaidOperationalStatus.Resolved(Kind.TASK_BELL, Action.PATHING, 0, 0);

        var text = ProgressPadStatusService.statusText(status, true);

        var contents = assertInstanceOf(TranslatableContents.class, text.getContents());
        assertEquals("gui.maid_storage_manager_extension.progress_pad.status.compact.combined", contents.getKey());
    }

    @Test
    void cleanupCargoAppearsAsWorkingTasksInsteadOfNoTaskMessage() {
        Target sourceTarget = new Target(new ResourceLocation("minecraft", "chest"), new BlockPos(1, 2, 3));
        Target destination = new Target(new ResourceLocation("minecraft", "barrel"), new BlockPos(8, 2, 3));
        MiscSortMemory cleanup = new MiscSortMemory();
        cleanup.setActiveBatch(new MiscSortMemory.ActiveBatch(0L, sourceTarget, "source", List.of(
                new MiscSortMemory.CargoLine(new ItemStack(Items.OAK_LOG), 32, 24, 4,
                        0, "item:minecraft:oak_log", List.of(destination), 0))));
        ProgressData source = new ProgressData(
                List.of(), Component.literal("maid"), List.of(), List.of(),
                0, 0, 0, 4, ProgressData.Status.NORMAL);

        ProgressData result = ProgressPadStatusService.applyHeader(
                source,
                new MaidOperationalStatus.Resolved(Kind.STORING, Action.CLEANING, 0, 2),
                cleanup);

        assertFalse(result.working.isEmpty());
        assertEquals(Items.OAK_LOG, result.working.get(0).outputs().get(0).getItem());
        assertEquals(24, result.working.get(0).progress());
        assertEquals(32, result.working.get(0).total());
        var action = assertInstanceOf(TranslatableContents.class,
                result.working.get(0).taker().get(0).getContents());
        assertEquals("gui.maid_storage_manager_extension.progress_pad.cleanup.transporting",
                action.getKey());
    }

    @Test
    void cleanupPendingPayloadAppearsBeforeTheMaidCollectsIt() {
        Target sourceTarget = new Target(new ResourceLocation("minecraft", "chest"), new BlockPos(1, 2, 3));
        Target destination = new Target(new ResourceLocation("minecraft", "barrel"), new BlockPos(8, 2, 3));
        MiscSortMemory cleanup = new MiscSortMemory();
        cleanup.replaceSourceJobs(List.of(new MiscSortMemory.SourceJob(sourceTarget, List.of(
                new MiscSortMemory.PayloadPlan(new ItemStack(Items.GOLDEN_HELMET), 2,
                        "item:minecraft:golden_helmet", List.of(destination))))));
        ProgressData source = new ProgressData(
                List.of(), Component.literal("maid"), List.of(), List.of(),
                0, 0, 0, 4, ProgressData.Status.NORMAL);

        ProgressData result = ProgressPadStatusService.applyHeader(
                source,
                new MaidOperationalStatus.Resolved(Kind.STORING, Action.CLEANING, 0, 2),
                cleanup);

        assertEquals(Items.GOLDEN_HELMET, result.working.get(0).outputs().get(0).getItem());
        assertEquals(0, result.working.get(0).progress());
        assertEquals(2, result.working.get(0).total());
        var action = assertInstanceOf(TranslatableContents.class,
                result.working.get(0).taker().get(0).getContents());
        assertEquals("gui.maid_storage_manager_extension.progress_pad.cleanup.collecting",
                action.getKey());
    }

    @Test
    void mixedCargoDoesNotClaimToReturnWhileTheBatchIsStillDelivering() {
        Target sourceTarget = new Target(new ResourceLocation("minecraft", "chest"), new BlockPos(1, 2, 3));
        Target destination = new Target(new ResourceLocation("minecraft", "barrel"), new BlockPos(8, 2, 3));
        MiscSortMemory cleanup = new MiscSortMemory();
        cleanup.setActiveBatch(new MiscSortMemory.ActiveBatch(0L, sourceTarget, "source", List.of(
                new MiscSortMemory.CargoLine(new ItemStack(Items.OAK_LOG), 32, 32, 4,
                        0, "item:minecraft:oak_log", List.of(destination), 0),
                new MiscSortMemory.CargoLine(new ItemStack(Items.BONE), 6, 6, 5,
                        0, "item:minecraft:bone", List.of(), 0))));
        ProgressData source = new ProgressData(
                List.of(), Component.literal("maid"), List.of(), List.of(),
                0, 0, 0, 4, ProgressData.Status.NORMAL);

        ProgressData result = ProgressPadStatusService.applyHeader(
                source,
                new MaidOperationalStatus.Resolved(Kind.STORING, Action.CLEANING, 0, 2),
                cleanup);

        var oakAction = assertInstanceOf(TranslatableContents.class,
                result.working.get(0).taker().get(0).getContents());
        var boneAction = assertInstanceOf(TranslatableContents.class,
                result.working.get(1).taker().get(0).getContents());
        assertEquals("gui.maid_storage_manager_extension.progress_pad.cleanup.transporting",
                oakAction.getKey());
        assertEquals("gui.maid_storage_manager_extension.progress_pad.cleanup.awaiting_return",
                boneAction.getKey());
    }

    private static Snapshot snapshot(boolean sleeping, boolean sitting, boolean combat,
                                     ExtensionMode extension, WorkMode work, boolean pathing) {
        return new Snapshot(sleeping, sitting, combat, false, false,
                extension, work, pathing, 0, 0);
    }
}
