package io.github.maidstorageextension.network;

import io.github.maidstorageextension.MaidStorageManagerExtension;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public final class ExtensionNetwork {
    private static final String PROTOCOL = "9";
    static final int EXTENSION_CONFIG_ID = 0;
    static final int REACHABILITY_DEBUG_ID = 1;
    static final int COURIER_COMMAND_ID = 2;
    static final int LOGISTICS_TRACKER_ID = 3;
    static final int STATION_APPROVAL_ID = 4;
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
    }
}
