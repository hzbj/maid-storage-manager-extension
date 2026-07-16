package io.github.maidstorageextension.maid.courier;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CourierFlightPolicyTest {
    @Test
    void nearbyTargetsUseTheMaidOriginalGroundNavigation() {
        assertFalse(CourierFlightPolicy.shouldUseBroom(31.0, 0.0, 32));
        assertFalse(CourierFlightPolicy.shouldUseBroom(32.0, 0.0, 32));
        assertTrue(CourierFlightPolicy.shouldUseBroom(33.0, 0.0, 32));
    }

    @Test
    void configuredDistanceUsesOnlyTheHorizontalAxes() {
        assertFalse(CourierFlightPolicy.shouldUseBroom(48.0, 0.0, 64));
        assertTrue(CourierFlightPolicy.shouldUseBroom(39.0, 52.0, 64));
    }

    @Test
    void destinationLandingIsNotPlannedUntilTheMaidWalksToTheTakeoffPoint() {
        assertFalse(CourierFlightPolicy.readyToSearchLanding(false, false));
        assertTrue(CourierFlightPolicy.readyToSearchLanding(false, true));
        assertTrue(CourierFlightPolicy.readyToSearchLanding(true, false));
    }

    @Test
    void broomRoutesNeverUseTheMaidsEndermanStyleGroundRecoveryTeleport() {
        assertFalse(CourierFlightPolicy.mayUseGroundRecoveryTeleport(
                io.github.maidstorageextension.data.CourierData.TransportMode.BROOM));
        assertFalse(CourierFlightPolicy.mayUseGroundRecoveryTeleport(
                io.github.maidstorageextension.data.CourierData.TransportMode.BROOM_ENDER_POCKET));
        assertTrue(CourierFlightPolicy.mayUseGroundRecoveryTeleport(
                io.github.maidstorageextension.data.CourierData.TransportMode.WALK));
        assertTrue(CourierFlightPolicy.mayUseGroundRecoveryTeleport(
                io.github.maidstorageextension.data.CourierData.TransportMode.ENDER_POCKET));
    }

    @Test
    void broomTransportIsAvailableOnlyInTheOverworld() {
        assertTrue(CourierFlightPolicy.supportsBroomDimension(
                new ResourceLocation("minecraft", "overworld")));
        assertFalse(CourierFlightPolicy.supportsBroomDimension(
                new ResourceLocation("minecraft", "the_nether")));
        assertFalse(CourierFlightPolicy.supportsBroomDimension(
                new ResourceLocation("minecraft", "the_end")));
    }
}
