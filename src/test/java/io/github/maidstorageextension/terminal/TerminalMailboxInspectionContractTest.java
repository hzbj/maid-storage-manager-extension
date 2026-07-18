package io.github.maidstorageextension.terminal;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TerminalMailboxInspectionContractTest {
    private static final Path SERVICE = Path.of(
            "src/main/java/io/github/maidstorageextension/terminal/TerminalAccountService.java");

    @Test
    void scanRequestUsesTheWarehouseMaidOneShotInspectionFlag() throws Exception {
        String source = Files.readString(SERVICE, StandardCharsets.UTF_8);

        assertTrue(source.contains("requestImmediateScan"));
        assertTrue(source.contains("REQUEST_SCAN"));
        assertTrue(source.contains("StorageManageTask.TASK_ID"));
    }
}
