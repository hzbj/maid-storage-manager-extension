package io.github.maidstorageextension.network;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExtensionNetworkRegistrationTest {
    @Test
    void payloadIdsAreUniqueAndDirectionsStayExplicit() {
        List<CustomPacketPayload.Type<?>> serverbound = List.of(
                ExtensionMaidConfigPacket.TYPE,
                CourierCommandPacket.TYPE,
                StationApprovalPacket.TYPE,
                NetworkWarehouseActionPacket.TYPE,
                TerminalAccountActionPacket.TYPE,
                TerminalMailboxActionPacket.TYPE,
                MaidTransportActionPacket.TYPE,
                BusinessLicenseActionPacket.TYPE,
                MaidLogisticsActionPacket.TYPE);
        List<CustomPacketPayload.Type<?>> clientbound = List.of(
                ReachabilityDebugPacket.TYPE,
                LogisticsTrackerPacket.TYPE,
                NetworkWarehouseSnapshotPacket.TYPE,
                TerminalAccountSnapshotPacket.TYPE,
                MaidTransportSnapshotPacket.TYPE,
                TerminalNoticePacket.TYPE,
                MaidPickupGuidancePacket.TYPE,
                BusinessLicenseSnapshotPacket.TYPE,
                MaidLogisticsSnapshotPacket.TYPE);

        assertEquals(9, serverbound.size());
        assertEquals(9, clientbound.size());
        assertEquals(18, Set.copyOf(
                java.util.stream.Stream.concat(serverbound.stream(), clientbound.stream())
                        .map(CustomPacketPayload.Type::id).toList()).size());
        java.util.stream.Stream.concat(serverbound.stream(), clientbound.stream())
                .forEach(type -> assertEquals(
                        "maid_storage_manager_extension", type.id().getNamespace()));
        assertEquals("17", ExtensionNetwork.PROTOCOL);
    }

    @Test
    void maidLogisticsPayloadNamesAreStable() {
        assertEquals("maid_pickup_guidance", MaidPickupGuidancePacket.TYPE.id().getPath());
        assertEquals("business_license_action", BusinessLicenseActionPacket.TYPE.id().getPath());
        assertEquals("business_license_snapshot", BusinessLicenseSnapshotPacket.TYPE.id().getPath());
        assertEquals("maid_logistics_action", MaidLogisticsActionPacket.TYPE.id().getPath());
        assertEquals("maid_logistics_snapshot", MaidLogisticsSnapshotPacket.TYPE.id().getPath());
    }
}
