package io.github.maidstorageextension.network;

import io.github.maidstorageextension.MaidStorageManagerExtension;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public final class ExtensionNetwork {
    private static final String PROTOCOL = "16";
    static final int EXTENSION_CONFIG_ID = 0;
    static final int REACHABILITY_DEBUG_ID = 1;
    static final int COURIER_COMMAND_ID = 2;
    static final int LOGISTICS_TRACKER_ID = 3;
    static final int STATION_APPROVAL_ID = 4;
    static final int NETWORK_WAREHOUSE_ACTION_ID = 5;
    static final int NETWORK_WAREHOUSE_SNAPSHOT_ID = 6;
    static final int TERMINAL_ACCOUNT_ACTION_ID = 7;
    static final int TERMINAL_ACCOUNT_SNAPSHOT_ID = 8;
    static final int TERMINAL_MAILBOX_ACTION_ID = 9;
    static final int MAID_TRANSPORT_ACTION_ID = 10;
    static final int MAID_TRANSPORT_SNAPSHOT_ID = 11;
    static final int TERMINAL_NOTICE_ID = 12;
    static final int MAID_PICKUP_GUIDANCE_ID = 13;
    static final int BUSINESS_LICENSE_ACTION_ID = 14;
    static final int BUSINESS_LICENSE_SNAPSHOT_ID = 15;
    static final int MAID_LOGISTICS_ACTION_ID = 16;
    static final int MAID_LOGISTICS_SNAPSHOT_ID = 17;
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(MaidStorageManagerExtension.MOD_ID, "main"),
            () -> PROTOCOL,
            PROTOCOL::equals,
            PROTOCOL::equals
    );
    private static boolean registered;

    private ExtensionNetwork() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        CHANNEL.registerMessage(EXTENSION_CONFIG_ID, ExtensionMaidConfigPacket.class,
                ExtensionMaidConfigPacket::encode,
                ExtensionMaidConfigPacket::decode,
                ExtensionMaidConfigPacket::handle);
        CHANNEL.registerMessage(REACHABILITY_DEBUG_ID, ReachabilityDebugPacket.class,
                ReachabilityDebugPacket::encode,
                ReachabilityDebugPacket::decode,
                ReachabilityDebugPacket::handle);
        CHANNEL.registerMessage(COURIER_COMMAND_ID, CourierCommandPacket.class,
                CourierCommandPacket::encode,
                CourierCommandPacket::decode,
                CourierCommandPacket::handle);
        CHANNEL.registerMessage(LOGISTICS_TRACKER_ID, LogisticsTrackerPacket.class,
                LogisticsTrackerPacket::encode,
                LogisticsTrackerPacket::decode,
                LogisticsTrackerPacket::handle);
        CHANNEL.registerMessage(STATION_APPROVAL_ID, StationApprovalPacket.class,
                StationApprovalPacket::encode,
                StationApprovalPacket::decode,
                StationApprovalPacket::handle);
        CHANNEL.registerMessage(NETWORK_WAREHOUSE_ACTION_ID, NetworkWarehouseActionPacket.class,
                NetworkWarehouseActionPacket::encode,
                NetworkWarehouseActionPacket::decode,
                NetworkWarehouseActionPacket::handle);
        CHANNEL.registerMessage(NETWORK_WAREHOUSE_SNAPSHOT_ID, NetworkWarehouseSnapshotPacket.class,
                NetworkWarehouseSnapshotPacket::encode,
                NetworkWarehouseSnapshotPacket::decode,
                NetworkWarehouseSnapshotPacket::handle);
        CHANNEL.registerMessage(TERMINAL_ACCOUNT_ACTION_ID, TerminalAccountActionPacket.class,
                TerminalAccountActionPacket::encode,
                TerminalAccountActionPacket::decode,
                TerminalAccountActionPacket::handle);
        CHANNEL.registerMessage(TERMINAL_ACCOUNT_SNAPSHOT_ID, TerminalAccountSnapshotPacket.class,
                TerminalAccountSnapshotPacket::encode,
                TerminalAccountSnapshotPacket::decode,
                TerminalAccountSnapshotPacket::handle);
        CHANNEL.registerMessage(TERMINAL_MAILBOX_ACTION_ID, TerminalMailboxActionPacket.class,
                TerminalMailboxActionPacket::encode,
                TerminalMailboxActionPacket::decode,
                TerminalMailboxActionPacket::handle);
        CHANNEL.registerMessage(MAID_TRANSPORT_ACTION_ID, MaidTransportActionPacket.class,
                MaidTransportActionPacket::encode,
                MaidTransportActionPacket::decode,
                MaidTransportActionPacket::handle);
        CHANNEL.registerMessage(MAID_TRANSPORT_SNAPSHOT_ID, MaidTransportSnapshotPacket.class,
                MaidTransportSnapshotPacket::encode,
                MaidTransportSnapshotPacket::decode,
                MaidTransportSnapshotPacket::handle);
        CHANNEL.registerMessage(TERMINAL_NOTICE_ID, TerminalNoticePacket.class,
                TerminalNoticePacket::encode,
                TerminalNoticePacket::decode,
                TerminalNoticePacket::handle);
        CHANNEL.registerMessage(MAID_PICKUP_GUIDANCE_ID, MaidPickupGuidancePacket.class,
                MaidPickupGuidancePacket::encode,
                MaidPickupGuidancePacket::decode,
                MaidPickupGuidancePacket::handle);
        CHANNEL.registerMessage(BUSINESS_LICENSE_ACTION_ID, BusinessLicenseActionPacket.class,
                BusinessLicenseActionPacket::encode,
                BusinessLicenseActionPacket::decode,
                BusinessLicenseActionPacket::handle);
        CHANNEL.registerMessage(BUSINESS_LICENSE_SNAPSHOT_ID, BusinessLicenseSnapshotPacket.class,
                BusinessLicenseSnapshotPacket::encode,
                BusinessLicenseSnapshotPacket::decode,
                BusinessLicenseSnapshotPacket::handle);
        CHANNEL.registerMessage(MAID_LOGISTICS_ACTION_ID, MaidLogisticsActionPacket.class,
                MaidLogisticsActionPacket::encode,
                MaidLogisticsActionPacket::decode,
                MaidLogisticsActionPacket::handle);
        CHANNEL.registerMessage(MAID_LOGISTICS_SNAPSHOT_ID, MaidLogisticsSnapshotPacket.class,
                MaidLogisticsSnapshotPacket::encode,
                MaidLogisticsSnapshotPacket::decode,
                MaidLogisticsSnapshotPacket::handle);
    }
}
