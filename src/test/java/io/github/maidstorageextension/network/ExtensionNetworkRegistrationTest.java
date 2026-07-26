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
                MaidTransportActionPacket.TYPE);
        List<CustomPacketPayload.Type<?>> clientbound = List.of(
                ReachabilityDebugPacket.TYPE,
                LogisticsTrackerPacket.TYPE,
                NetworkWarehouseSnapshotPacket.TYPE,
                TerminalAccountSnapshotPacket.TYPE,
                MaidTransportSnapshotPacket.TYPE,
                TerminalNoticePacket.TYPE,
                MaidPickupGuidancePacket.TYPE);

        assertEquals(7, serverbound.size());
        assertEquals(7, clientbound.size());
        assertEquals(14, Set.copyOf(
                java.util.stream.Stream.concat(serverbound.stream(), clientbound.stream())
                        .map(CustomPacketPayload.Type::id).toList()).size());
        java.util.stream.Stream.concat(serverbound.stream(), clientbound.stream())
                .forEach(type -> assertEquals(
                        "maid_storage_manager_extension", type.id().getNamespace()));
        assertEquals("14", ExtensionNetwork.PROTOCOL);
    }
}
