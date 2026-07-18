package io.github.maidstorageextension.maid.courier;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PassengerAutopilotControlContractTest {
    private static final Path MIXIN = Path.of(
            "src/main/java/io/github/maidstorageextension/mixin/EntityBroomMixin.java");
    private static final Path FLIGHT = Path.of(
            "src/main/java/io/github/maidstorageextension/maid/courier/CourierBroomFlightService.java");

    @Test
    void maidFirstPassengerSuppressesClientVanillaControlUntilHandoff() throws Exception {
        String mixin = Files.readString(MIXIN, StandardCharsets.UTF_8);
        String flight = Files.readString(FLIGHT, StandardCharsets.UTF_8);

        assertTrue(mixin.contains("getFirstPassenger() instanceof EntityMaid"),
                "Client persistent data does not contain the server courier tag; passenger order must identify autopilot");
        assertTrue(flight.indexOf("rider.startRiding(broom, true)")
                        < flight.indexOf("courier.startRiding(broom, true)",
                        flight.indexOf("handControlToRider")),
                "Explicit handoff must make the player first passenger before restoring vanilla control");
    }
}
