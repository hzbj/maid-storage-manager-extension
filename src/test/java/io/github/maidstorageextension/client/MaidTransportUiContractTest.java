package io.github.maidstorageextension.client;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MaidTransportUiContractTest {
    private static final Path SCREEN = Path.of(
            "src/main/java/io/github/maidstorageextension/client/LogisticsTrackerScreen.java");

    @Test
    void terminalExposesOnlyWarehouseAndTransportAndUsesShortClickThreshold() throws Exception {
        String source = Files.readString(SCREEN, StandardCharsets.UTF_8);
        String enumBody = source.substring(source.indexOf("private enum ServiceTab"),
                source.indexOf("private enum WarehouseMode"));
        assertEquals(1, count(enumBody, "NETWORK_WAREHOUSE"));
        assertEquals(1, count(enumBody, "MAID_TRANSPORT"));
        assertTrue(source.contains("totalX * totalX + totalY * totalY > 16.0D"));
        assertTrue(source.contains("selectedDestination"));
        assertTrue(source.contains("warehouseOnDuty()"));
        assertTrue(source.contains("transportModeName(maid.transportMode())"));
    }

    private static int count(String source, String needle) {
        int count = 0;
        int index = 0;
        while ((index = source.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }
}
