package io.github.maidstorageextension.logistics;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class WarehouseInventoryRefreshBindingContractTest {
    private static final Path REFRESH = Path.of(
            "src/main/java/io/github/maidstorageextension/scan/InventoryListRefreshService.java");
    private static final Path TERMINAL = Path.of(
            "src/main/java/io/github/maidstorageextension/terminal/TerminalAccountService.java");

    @Test
    void refreshedFrameUuidIsPublishedAndMailboxInteractionSelectsItsWarehouse() throws Exception {
        String refresh = Files.readString(REFRESH, StandardCharsets.UTF_8);
        String terminal = Files.readString(TERMINAL, StandardCharsets.UTF_8);

        assertTrue(refresh.contains("networkData.publish(newUuid"),
                "The terminal inventory reference must follow the newly written display-frame list UUID");
        assertTrue(terminal.contains("Action.ACTIVATE")
                        && terminal.contains("activateMailbox"),
                "The registered mailbox must activate its warehouse before the refreshed inventory is requested");
    }
}
