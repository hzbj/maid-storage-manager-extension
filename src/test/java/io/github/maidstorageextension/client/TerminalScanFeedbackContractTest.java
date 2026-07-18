package io.github.maidstorageextension.client;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TerminalScanFeedbackContractTest {
    private static final Path SCREEN = Path.of(
            "src/main/java/io/github/maidstorageextension/client/LogisticsTrackerScreen.java");
    private static final Path SERVICE = Path.of(
            "src/main/java/io/github/maidstorageextension/terminal/TerminalAccountService.java");

    @Test
    void warehouseInspectionReturnsAVisibleTerminalNoticeAndActivatesTheWarehouse() throws Exception {
        String screen = Files.readString(SCREEN, StandardCharsets.UTF_8);
        String service = Files.readString(SERVICE, StandardCharsets.UTF_8);

        assertTrue(screen.contains("TerminalNoticeClientData"));
        assertTrue(screen.contains("renderTerminalNotice"));
        assertTrue(service.contains("TerminalNoticePacket"));
        assertTrue(service.contains("activateMailbox"),
                "Scanning a registered mailbox must also leave the terminal viewing that warehouse");
    }
}
