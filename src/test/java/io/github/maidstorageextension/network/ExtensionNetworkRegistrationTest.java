package io.github.maidstorageextension.network;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class ExtensionNetworkRegistrationTest {
    @Test
    void existingMessageIdsRemainStableWhenCompassWarehouseMessagesAreAdded() {
        assertArrayEquals(new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12}, new int[]{
                ExtensionNetwork.EXTENSION_CONFIG_ID,
                ExtensionNetwork.REACHABILITY_DEBUG_ID,
                ExtensionNetwork.COURIER_COMMAND_ID,
                ExtensionNetwork.LOGISTICS_TRACKER_ID,
                ExtensionNetwork.STATION_APPROVAL_ID,
                ExtensionNetwork.NETWORK_WAREHOUSE_ACTION_ID,
                ExtensionNetwork.NETWORK_WAREHOUSE_SNAPSHOT_ID,
                ExtensionNetwork.TERMINAL_ACCOUNT_ACTION_ID,
                ExtensionNetwork.TERMINAL_ACCOUNT_SNAPSHOT_ID,
                ExtensionNetwork.TERMINAL_MAILBOX_ACTION_ID,
                ExtensionNetwork.MAID_TRANSPORT_ACTION_ID,
                ExtensionNetwork.MAID_TRANSPORT_SNAPSHOT_ID,
                ExtensionNetwork.TERMINAL_NOTICE_ID
        });
    }
}
