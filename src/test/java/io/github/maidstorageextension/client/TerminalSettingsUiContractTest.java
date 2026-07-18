package io.github.maidstorageextension.client;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TerminalSettingsUiContractTest {
    private static final Path SCREEN = Path.of(
            "src/main/java/io/github/maidstorageextension/client/LogisticsTrackerScreen.java");

    @Test
    void destructiveAccountBindingsLiveOnADedicatedSettingsPage() throws Exception {
        String source = Files.readString(SCREEN, StandardCharsets.UTF_8);

        assertTrue(source.contains("settingsPage"));
        assertTrue(source.contains("terminal.settings_button"));
        assertTrue(source.contains("Action.UNREGISTER_MAID"));
        assertTrue(source.contains("Action.UNREGISTER"));
    }

    @Test
    void normalWarehouseRightClickRequestsAnImmediateInspection() throws Exception {
        String source = Files.readString(SCREEN, StandardCharsets.UTF_8);

        assertTrue(source.contains("Action.REQUEST_SCAN"));
        assertTrue(source.contains("requestWarehouseScan"));
    }
}
