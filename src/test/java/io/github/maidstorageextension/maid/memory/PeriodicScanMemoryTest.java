package io.github.maidstorageextension.maid.memory;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.nbt.NbtOps;
import org.junit.jupiter.api.Test;
import studio.fantasyit.maid_storage_manager.storage.Target;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PeriodicScanMemoryTest {
    @Test
    void progressAndTargetsSurvivePauseState() {
        Target target = new Target(new ResourceLocation("minecraft", "chest"), BlockPos.ZERO);
        PeriodicScanMemory memory = new PeriodicScanMemory(
                1200L, PeriodicScanMemory.Phase.SCANNING, List.of(target), 2, 5);

        assertEquals(1200L, memory.getNextScanGameTime());
        assertEquals(PeriodicScanMemory.Phase.SCANNING, memory.getPhase());
        assertEquals(List.of(target), memory.getPeriodicTargets());
        assertEquals(2, memory.getScanProgress());
        assertEquals(5, memory.getScanTotal());
    }

    @Test
    void resetReturnsToSafeIdleState() {
        PeriodicScanMemory memory = new PeriodicScanMemory();
        memory.setPhase(PeriodicScanMemory.Phase.REFRESH_PENDING);
        memory.setProgress(8, 4);
        memory.reset();

        assertEquals(PeriodicScanMemory.Phase.IDLE, memory.getPhase());
        assertEquals(0, memory.getScanProgress());
        assertEquals(0, memory.getScanTotal());
        assertTrue(memory.getPeriodicTargets().isEmpty());
    }

    @Test
    void targetOnlyCompletesAfterExplicitSuccessfulInspection() {
        Target target = new Target(new ResourceLocation("minecraft", "chest"), BlockPos.ZERO);
        PeriodicScanMemory memory = new PeriodicScanMemory();
        memory.beginInspection(List.of(target));

        assertEquals(List.of(target), memory.getPendingInspectionTargets());
        assertTrue(memory.getSuccessfullyInspectedTargets().isEmpty());
        assertEquals(0, memory.getScanProgress());

        memory.recordSuccessfulInspection(target);

        assertTrue(memory.getPendingInspectionTargets().isEmpty());
        assertEquals(List.of(target), memory.getSuccessfullyInspectedTargets());
        assertEquals(1, memory.getScanProgress());
        assertTrue(memory.hasCompleteInspectionEvidence());
    }

    @Test
    void eachInspectionGetsANewGenerationAndResetDoesNotReuseIt() {
        PeriodicScanMemory memory = new PeriodicScanMemory();

        memory.beginInspection(List.of());
        long first = memory.getScanGeneration();
        memory.reset();
        memory.beginInspection(List.of());

        assertEquals(1L, first);
        assertEquals(2L, memory.getScanGeneration());
    }

    @Test
    void oneShotRescanSurvivesResetAndCodecUntilExplicitlyCompleted() {
        PeriodicScanMemory memory = new PeriodicScanMemory();
        memory.requestImmediateScan();
        memory.reset();

        var encoded = PeriodicScanMemory.CODEC.encodeStart(NbtOps.INSTANCE, memory)
                .result().orElseThrow();
        PeriodicScanMemory decoded = PeriodicScanMemory.CODEC.parse(NbtOps.INSTANCE, encoded)
                .result().orElseThrow();

        assertTrue(decoded.isForceScanRequested());
        assertEquals(0L, decoded.getNextScanGameTime());
        decoded.clearForceScanRequest();
        assertFalse(decoded.isForceScanRequested());
    }
}
