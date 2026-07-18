package io.github.maidstorageextension.terminal;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MaidTransportDefaultPickupContractTest {
    private static final Path SERVICE = Path.of(
            "src/main/java/io/github/maidstorageextension/terminal/MaidTransportService.java");

    @Test
    void omittedPickupUsesTheRiderPositionAndNearbyDriverWaitsWithoutFlying() throws Exception {
        String source = Files.readString(SERVICE, StandardCharsets.UTF_8);
        String compact = source.replaceAll("\\s+", " ");
        int pickupTick = compact.indexOf("private static void tickToPickup");
        int nearbyCheck = compact.indexOf(
                "MaidTransportBoardingPolicy.withinRange("
                        + " driver.distanceToSqr(target.getCenter()))", pickupTick);
        int flight = compact.indexOf("CourierBroomFlightService.tickPassenger(", pickupTick);

        assertTrue(source.contains("requestedPickup == null")
                        && source.contains("? rider.blockPosition()"),
                "An omitted pickup must resolve to the rider's position when the request starts");
        assertTrue(nearbyCheck >= pickupTick && nearbyCheck < flight,
                "A driver already within boarding range must wait before any pickup flight starts");
    }
}
