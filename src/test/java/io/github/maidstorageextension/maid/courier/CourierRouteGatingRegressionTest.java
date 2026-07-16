package io.github.maidstorageextension.maid.courier;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CourierRouteGatingRegressionTest {
    @Test
    void stationIsRequiredByBroomRoutesInsteadOfCarriedInventory() throws IOException {
        String service = source("maid/courier/CourierService.java");
        String tracker = source("logistics/LogisticsTrackerService.java");

        assertFalse(service.contains("EnderPocketCompat.hasBroom(courier) || mode.usesBroom()"));
        assertTrue(service.contains("if (mode.usesBroom() && !validStation"));
        assertFalse(tracker.contains(": !EnderPocketCompat.hasBroom(courier) && warehouse != null"));
    }

    private String source(String relativePath) throws IOException {
        return Files.readString(Path.of("src/main/java/io/github/maidstorageextension")
                .resolve(relativePath), StandardCharsets.UTF_8);
    }
}
