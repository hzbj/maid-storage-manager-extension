package io.github.maidstorageextension.maid.courier;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CourierNearbyHandoffContractTest {
    private static final Path SERVICE = Path.of(
            "src/main/java/io/github/maidstorageextension/maid/courier/CourierService.java");

    @Test
    void entityHandoffsHaveNoFacingOrTimedReadinessGate() throws Exception {
        String source = Files.readString(SERVICE);

        assertFalse(source.contains("HANDOFF_TICKS"));
        assertFalse(source.contains("handoffReady("));
        assertFalse(source.contains("faceEachOther("));
        assertFalse(source.contains("other.getNavigation().stop()"));
        assertFalse(source.contains("warehouse.getNavigationManager().resetNavigation()"));
        assertFalse(source.contains("MemoryUtil.clearTarget(warehouse)"));
    }

    @Test
    void warehouseAndOwnerHandoffsPauseOnlyTheCourierAtNearbyRange() throws Exception {
        String source = Files.readString(SERVICE);

        assertTrue(source.contains("if (meeting != null) dispatchRequest(courier, meeting, data);"));
        assertTrue(source.contains("if (meeting != null) dispatchDeposit(level, courier, meeting, data);"));
        assertTrue(source.contains("private static boolean withinNearbyHandoff(Entity courier, Entity target)"));
        assertTrue(source.contains("pauseMovement(courier);\n        courier.swing(InteractionHand.MAIN_HAND, true);"));
    }
}
