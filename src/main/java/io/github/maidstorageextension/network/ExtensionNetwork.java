package io.github.maidstorageextension.network;

import io.github.maidstorageextension.MaidStorageManagerExtension;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = MaidStorageManagerExtension.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class ExtensionNetwork {
    public static final String PROTOCOL = "17";

    private ExtensionNetwork() {
    }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL);

        registrar.playToServer(ExtensionMaidConfigPacket.TYPE,
                ExtensionMaidConfigPacket.STREAM_CODEC, ExtensionMaidConfigPacket::handle);
        registrar.playToServer(CourierCommandPacket.TYPE,
                CourierCommandPacket.STREAM_CODEC, CourierCommandPacket::handle);
        registrar.playToServer(StationApprovalPacket.TYPE,
                StationApprovalPacket.STREAM_CODEC, StationApprovalPacket::handle);
        registrar.playToServer(NetworkWarehouseActionPacket.TYPE,
                NetworkWarehouseActionPacket.STREAM_CODEC, NetworkWarehouseActionPacket::handle);
        registrar.playToServer(TerminalAccountActionPacket.TYPE,
                TerminalAccountActionPacket.STREAM_CODEC, TerminalAccountActionPacket::handle);
        registrar.playToServer(TerminalMailboxActionPacket.TYPE,
                TerminalMailboxActionPacket.STREAM_CODEC, TerminalMailboxActionPacket::handle);
        registrar.playToServer(MaidTransportActionPacket.TYPE,
                MaidTransportActionPacket.STREAM_CODEC, MaidTransportActionPacket::handle);
        registrar.playToServer(BusinessLicenseActionPacket.TYPE,
                BusinessLicenseActionPacket.STREAM_CODEC, BusinessLicenseActionPacket::handle);
        registrar.playToServer(MaidLogisticsActionPacket.TYPE,
                MaidLogisticsActionPacket.STREAM_CODEC, MaidLogisticsActionPacket::handle);

        registrar.playToClient(ReachabilityDebugPacket.TYPE,
                ReachabilityDebugPacket.STREAM_CODEC, ReachabilityDebugPacket::handle);
        registrar.playToClient(LogisticsTrackerPacket.TYPE,
                LogisticsTrackerPacket.STREAM_CODEC, LogisticsTrackerPacket::handle);
        registrar.playToClient(NetworkWarehouseSnapshotPacket.TYPE,
                NetworkWarehouseSnapshotPacket.STREAM_CODEC, NetworkWarehouseSnapshotPacket::handle);
        registrar.playToClient(TerminalAccountSnapshotPacket.TYPE,
                TerminalAccountSnapshotPacket.STREAM_CODEC, TerminalAccountSnapshotPacket::handle);
        registrar.playToClient(MaidTransportSnapshotPacket.TYPE,
                MaidTransportSnapshotPacket.STREAM_CODEC, MaidTransportSnapshotPacket::handle);
        registrar.playToClient(TerminalNoticePacket.TYPE,
                TerminalNoticePacket.STREAM_CODEC, TerminalNoticePacket::handle);
        registrar.playToClient(MaidPickupGuidancePacket.TYPE,
                MaidPickupGuidancePacket.STREAM_CODEC, MaidPickupGuidancePacket::handle);
        registrar.playToClient(BusinessLicenseSnapshotPacket.TYPE,
                BusinessLicenseSnapshotPacket.STREAM_CODEC, BusinessLicenseSnapshotPacket::handle);
        registrar.playToClient(MaidLogisticsSnapshotPacket.TYPE,
                MaidLogisticsSnapshotPacket.STREAM_CODEC, MaidLogisticsSnapshotPacket::handle);
    }
}
