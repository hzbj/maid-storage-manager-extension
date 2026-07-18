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
    void synchronizedAutopilotAuthoritySuppressesVanillaControlUntilHandoff() throws Exception {
        String mixin = Files.readString(MIXIN, StandardCharsets.UTF_8);
        String flight = Files.readString(FLIGHT, StandardCharsets.UTF_8);

        assertTrue(mixin.contains("EntityDataAccessor<Boolean> AUTOPILOT"),
                "Autopilot authority must be synchronized instead of inferred from passenger order");
        assertTrue(mixin.contains("method = \"getControllingPassenger\"")
                        && mixin.contains("cir.setReturnValue(null)"),
                "Autopilot must revoke vanilla's controlling passenger before movement packets are accepted");
        assertTrue(flight.contains("setAutopilot(broom, true)"),
                "Courier flight must explicitly acquire broom control");

        int handoff = flight.indexOf("handControlToRider");
        int release = flight.indexOf("setAutopilot(broom, false)", handoff);
        int riderMount = flight.indexOf("rider.startRiding(broom, true)", handoff);
        assertTrue(release >= handoff && release < riderMount,
                "Explicit handoff must release autopilot authority before the rider mounts");
    }
}
