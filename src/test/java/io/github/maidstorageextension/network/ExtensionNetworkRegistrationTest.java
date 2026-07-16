package io.github.maidstorageextension.network;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class ExtensionNetworkRegistrationTest {
    @Test
    void oneOneSixMessageIdsRemainStableWhenStationApprovalIsAdded() {
        assertArrayEquals(new int[]{0, 1, 2, 3, 4}, new int[]{
                ExtensionNetwork.EXTENSION_CONFIG_ID,
                ExtensionNetwork.REACHABILITY_DEBUG_ID,
                ExtensionNetwork.COURIER_COMMAND_ID,
                ExtensionNetwork.LOGISTICS_TRACKER_ID,
                ExtensionNetwork.STATION_APPROVAL_ID
        });
    }
}
