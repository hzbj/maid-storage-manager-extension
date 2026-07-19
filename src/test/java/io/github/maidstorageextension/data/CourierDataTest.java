package io.github.maidstorageextension.data;

import net.minecraft.core.BlockPos;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CourierDataTest {
    private final CourierData key = new CourierData();

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void physicalRouteJournalRoundTripsAcrossRestart() {
        UUID warehouse = UUID.randomUUID();
        BlockPos warehousePos = new BlockPos(96, 70, -24);
        BlockPos ownerDeparture = new BlockPos(8, 65, 12);
        BlockPos deliveryChest = new BlockPos(16, 64, 20);
        BlockPos ownerTarget = new BlockPos(7, 64, 11);
        ResourceLocation overworld = new ResourceLocation("minecraft", "overworld");
        CourierData.Data source = new CourierData.Data();

        source.bind(warehouse, warehousePos, overworld);
        source.beginRoute(ownerDeparture, overworld, true);
        source.deliveryTarget(deliveryChest, overworld);
        source.ownerTarget(ownerTarget, overworld);
        source.forceOwnerDelivery(true);
        source.phase(CourierData.Phase.OWNER_HANDOFF);
        source.handoffStartedGameTime(12_345L);
        BlockPos groundCheckpoint = new BlockPos(80, 70, -8);
        source.groundApproach(CourierData.Phase.OWNER_HANDOFF, groundCheckpoint, 12_300L);
        source.requestFinished(true);
        source.targetWarningSent(true);
        source.transportMode(CourierData.TransportMode.BROOM);
        source.dispatchSource(CourierData.DispatchSource.TERMINAL);
        source.broomFlightDistance(56);
        source.stayHomeAfterDelivery(true);
        source.beginFollowOverride(true);
        UUID broom = UUID.randomUUID();
        BlockPos takeoff = new BlockPos(12, 66, 15);
        BlockPos landing = new BlockPos(88, 72, -12);
        source.prepareFlight(CourierData.Phase.OWNER_HANDOFF, takeoff);
        source.flightLandingPos(landing);
        source.flightSearchWindow(33, 48);
        source.flightRejectedLandings(2);
        source.flightLandingFailures(1);
        source.groundTeleportFailures(2);
        source.flight(broom, CourierData.Phase.OWNER_HANDOFF, 112);

        CourierData.Data decoded = key.readSaveData(key.writeSaveData(source));
        assertEquals(warehouse, decoded.warehouse());
        assertEquals(warehousePos, decoded.warehousePos());
        assertEquals(overworld, decoded.warehouseDimension());
        assertEquals(ownerDeparture, decoded.originPos());
        assertEquals(overworld, decoded.originDimension());
        assertTrue(decoded.originOwner());
        assertEquals(deliveryChest, decoded.deliveryPos());
        assertEquals(overworld, decoded.deliveryDimension());
        assertEquals(ownerTarget, decoded.ownerTargetPos());
        assertEquals(overworld, decoded.ownerTargetDimension());
        assertTrue(decoded.forceOwnerDelivery());
        assertEquals(CourierData.Phase.OWNER_HANDOFF, decoded.phase());
        assertEquals(12_345L, decoded.handoffStartedGameTime());
        assertEquals(CourierData.Phase.OWNER_HANDOFF, decoded.groundApproachPhase());
        assertEquals(groundCheckpoint, decoded.groundApproachPos());
        assertEquals(12_300L, decoded.groundApproachProgressGameTime());
        assertTrue(decoded.requestFinished());
        assertTrue(decoded.targetWarningSent());
        assertEquals(CourierData.TransportMode.BROOM, decoded.transportMode());
        assertEquals(CourierData.DispatchSource.TERMINAL, decoded.dispatchSource());
        assertEquals(56, decoded.broomFlightDistance());
        assertTrue(decoded.stayHomeAfterDelivery());
        assertTrue(decoded.followOverrideActive());
        assertTrue(decoded.homeModeBeforeCourier());
        assertEquals(broom, decoded.courierBroom());
        assertEquals(CourierData.Phase.OWNER_HANDOFF, decoded.flightLeg());
        assertEquals(112, decoded.flightCruiseY());
        assertEquals(takeoff, decoded.flightTakeoffPos());
        assertEquals(landing, decoded.flightLandingPos());
        assertEquals(33, decoded.flightSearchMinRadius());
        assertEquals(48, decoded.flightSearchMaxRadius());
        assertEquals(2, decoded.flightRejectedLandings());
        assertEquals(1, decoded.flightLandingFailures());
        assertEquals(2, decoded.groundTeleportFailures());
        assertFalse(decoded.flightLanded());
    }

    @Test
    void oldBindingWithoutRouteCoordinatesRemainsReadable() {
        UUID warehouse = UUID.randomUUID();
        CompoundTag legacy = new CompoundTag();
        legacy.putUUID("warehouse", warehouse);
        legacy.putString("phase", "REQUEST_RUNNING");

        CourierData.Data decoded = key.readSaveData(legacy);
        assertEquals(warehouse, decoded.warehouse());
        assertEquals(CourierData.Phase.REQUEST_RUNNING, decoded.phase());
        assertNull(decoded.warehousePos());
        assertNull(decoded.warehouseDimension());
        assertNull(decoded.originPos());
        assertNull(decoded.deliveryPos());
        assertNull(decoded.deliveryDimension());
        assertNull(decoded.ownerTargetPos());
        assertNull(decoded.ownerTargetDimension());
        assertFalse(decoded.originOwner());
        assertEquals(CourierData.TransportMode.NONE, decoded.transportMode());
        assertEquals(CourierData.DEFAULT_BROOM_FLIGHT_DISTANCE, decoded.broomFlightDistance());
        assertFalse(decoded.stayHomeAfterDelivery());
        assertFalse(decoded.followOverrideActive());
        assertNull(decoded.courierBroom());
        assertNull(decoded.flightTakeoffPos());
        assertFalse(decoded.flightLanded());
        assertEquals(-1L, decoded.handoffStartedGameTime());
    }

    @Test
    void broomFlightDistanceIsClampedToTheInGameRange() {
        CourierData.Data data = new CourierData.Data();

        data.broomFlightDistance(1);
        assertEquals(CourierData.MIN_BROOM_FLIGHT_DISTANCE, data.broomFlightDistance());

        data.broomFlightDistance(1000);
        assertEquals(CourierData.MAX_BROOM_FLIGHT_DISTANCE, data.broomFlightDistance());
    }

    @Test
    void courierKeepsAtMostSixWarehouseStationsAndMovesTheDefaultToTheFront() {
        ResourceLocation overworld = new ResourceLocation("minecraft", "overworld");
        CourierData.Data data = new CourierData.Data();
        UUID[] warehouses = new UUID[7];

        for (int i = 0; i < warehouses.length; i++) {
            warehouses[i] = UUID.randomUUID();
            CourierData.WarehouseBinding binding = new CourierData.WarehouseBinding(
                    warehouses[i], new BlockPos(i * 10, 64, 0), overworld,
                    new BlockPos(i * 10 + 1, 64, 0), overworld,
                    new BlockPos(i * 10 + 2, 64, 0), overworld, "Warehouse " + (i + 1));
            assertEquals(i < CourierData.MAX_WAREHOUSES, data.addWarehouse(binding));
        }

        assertEquals(CourierData.MAX_WAREHOUSES, data.warehouses().size());
        assertEquals(warehouses[0], data.warehouse());
        assertTrue(data.selectWarehouse(warehouses[4]));
        assertEquals(warehouses[4], data.warehouse());
        assertEquals(warehouses[4], data.warehouses().get(0).warehouse());

        CourierData.Data decoded = key.readSaveData(key.writeSaveData(data));
        assertEquals(CourierData.MAX_WAREHOUSES, decoded.warehouses().size());
        assertEquals(warehouses[4], decoded.warehouse());
        assertEquals(new BlockPos(41, 64, 0), decoded.warehouses().get(0).mailboxPos());
        assertEquals(new BlockPos(42, 64, 0), decoded.stationPos());
        assertEquals("Warehouse 5", decoded.warehouseName());
    }

    @Test
    void oldSingleWarehouseBindingMigratesIntoTheStationList() {
        UUID warehouse = UUID.randomUUID();
        BlockPos warehousePos = new BlockPos(40, 70, -20);
        ResourceLocation overworld = new ResourceLocation("minecraft", "overworld");
        CompoundTag legacy = new CompoundTag();
        legacy.putUUID("warehouse", warehouse);
        legacy.putLong("warehousePos", warehousePos.asLong());
        legacy.putString("warehouseDimension", overworld.toString());
        legacy.putString("phase", "IDLE");

        CourierData.Data decoded = key.readSaveData(legacy);

        assertEquals(1, decoded.warehouses().size());
        assertEquals(warehouse, decoded.warehouses().get(0).warehouse());
        assertNull(decoded.warehouses().get(0).mailboxPos());
        assertNull(decoded.warehouses().get(0).stationPos());
    }

    @Test
    void passengerTransportJournalKeepsCallerAndUnknownDestinationAcrossRestart() {
        CourierData.Data source = new CourierData.Data();
        UUID rider = UUID.randomUUID();
        ResourceLocation overworld = new ResourceLocation("minecraft", "overworld");
        BlockPos pickup = new BlockPos(4, 70, 8);
        BlockPos destination = new BlockPos(240_000, 70, -180_000);
        source.beginPassengerTransport(rider, pickup, destination, overworld);
        source.transportPickup(new BlockPos(6, 71, 9));

        CourierData.Data decoded = key.readSaveData(key.writeSaveData(source));

        assertEquals(CourierData.Phase.TRANSPORT_TO_PICKUP, decoded.phase());
        assertEquals(CourierData.TransportMode.BROOM, decoded.transportMode());
        assertEquals(rider, decoded.transportRider());
        assertEquals(pickup, decoded.transportPickupAnchor());
        assertEquals(new BlockPos(6, 71, 9), decoded.transportPickup());
        assertEquals(destination, decoded.transportDestinationAnchor());
        assertEquals(overworld, decoded.transportDimension());
    }
}
