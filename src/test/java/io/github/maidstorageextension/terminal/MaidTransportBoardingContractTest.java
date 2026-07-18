package io.github.maidstorageextension.terminal;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MaidTransportBoardingContractTest {
    private static final Path EVENTS = Path.of(
            "src/main/java/io/github/maidstorageextension/event/ServerEvents.java");

    @Test
    void rightClickingTheMountedDriverIsForwardedToTheTransportBroom() throws Exception {
        String source = Files.readString(EVENTS, StandardCharsets.UTF_8);

        assertTrue(source.contains("PlayerInteractEvent.EntityInteract"),
                "A maid passenger overlaps the broom hitbox, so entity interaction needs a server bridge");
        assertTrue(source.contains("MaidTransportBoardingService.tryBoard"),
                "Both broom and mounted-maid hits must use the same authorized boarding service");
    }
}
