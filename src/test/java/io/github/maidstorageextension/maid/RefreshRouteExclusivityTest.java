package io.github.maidstorageextension.maid;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RefreshRouteExclusivityTest {
    @Test
    void activeInventoryRefreshKeepsTheSchedulerFromClearingItsWalkTarget() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/io/github/maidstorageextension/maid/ExtensionWorkControl.java"),
                StandardCharsets.UTF_8);
        int refreshBranch = source.indexOf(
                "return phase == PeriodicScanMemory.Phase.REFRESH_PENDING");
        assertTrue(refreshBranch >= 0, "The refresh exclusivity branch is missing");

        String refreshPolicy = source.substring(refreshBranch);
        assertTrue(refreshPolicy.contains("mayContinuePeriodicIdleAction(maid)"),
                "An active refresh must keep exclusivity after it sets IS_WORKING");
        assertFalse(refreshPolicy.contains("mayUsePeriodicIdleTime(maid)"),
                "The start-only idle predicate rejects IS_WORKING and drops the active route");
    }
}
