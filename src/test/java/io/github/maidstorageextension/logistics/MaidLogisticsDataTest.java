package io.github.maidstorageextension.logistics;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class MaidLogisticsDataTest {
    private static final ResourceLocation OVERWORLD = new ResourceLocation("minecraft", "overworld");

    @Test
    void endpointMatrixRejectsOnlyWarehouseToWarehouseAndSameNode() {
        MaidLogisticsData.NodeRef licenseA = license(0, UUID.randomUUID());
        MaidLogisticsData.NodeRef licenseB = license(20, UUID.randomUUID());
        MaidLogisticsData.NodeRef warehouseA = warehouse(40);
        MaidLogisticsData.NodeRef warehouseB = warehouse(60);
        org.junit.jupiter.api.Assertions.assertTrue(MaidLogisticsData.validEndpoints(licenseA, licenseB));
        org.junit.jupiter.api.Assertions.assertTrue(MaidLogisticsData.validEndpoints(licenseA, warehouseA));
        org.junit.jupiter.api.Assertions.assertTrue(MaidLogisticsData.validEndpoints(warehouseA, licenseA));
        org.junit.jupiter.api.Assertions.assertFalse(MaidLogisticsData.validEndpoints(warehouseA, warehouseB));
        org.junit.jupiter.api.Assertions.assertFalse(MaidLogisticsData.validEndpoints(licenseA, licenseA));
    }

    @Test
    void repeatedSlotsCycleForeverAndCursorSurvivesReload() {
        MaidLogisticsData data = new MaidLogisticsData();
        UUID account = UUID.randomUUID();
        UUID courier = UUID.randomUUID();
        UUID[] first = new UUID[1];
        UUID[] second = new UUID[1];
        List<MaidLogisticsData.CargoLine> cargo = List.of(
                new MaidLogisticsData.CargoLine(new ItemStack(Items.STONE), 64));
        assertEquals(MaidLogisticsData.MutationResult.OK,
                data.create(account, license(0, UUID.randomUUID()), warehouse(20), courier, cargo, first));
        assertEquals(MaidLogisticsData.MutationResult.OK,
                data.create(account, warehouse(20), license(40, UUID.randomUUID()), courier, cargo, second));
        data.addSlot(account, courier, first[0]);
        assertEquals(first[0], data.advance(courier).id());
        assertEquals(second[0], data.advance(courier).id());

        MaidLogisticsData decoded = MaidLogisticsData.load(data.save(new CompoundTag()));
        assertEquals(first[0], decoded.advance(courier).id());
        assertEquals(first[0], decoded.advance(courier).id());
        assertEquals(second[0], decoded.advance(courier).id());
    }

    @Test
    void emptyScheduleHasNoOpportunity() {
        assertNull(new MaidLogisticsData().advance(UUID.randomUUID()));
    }

    @Test
    void reassigningRoutePreservesItsRepeatedExecutionSlots() {
        MaidLogisticsData data = new MaidLogisticsData();
        UUID account = UUID.randomUUID();
        UUID firstCourier = UUID.randomUUID();
        UUID secondCourier = UUID.randomUUID();
        UUID[] created = new UUID[1];
        List<MaidLogisticsData.CargoLine> cargo = List.of(
                new MaidLogisticsData.CargoLine(new ItemStack(Items.STONE), 64));
        data.create(account, license(0, UUID.randomUUID()), warehouse(20),
                firstCourier, cargo, created);
        data.addSlot(account, firstCourier, created[0]);
        data.addSlot(account, firstCourier, created[0]);

        MaidLogisticsData.Route route = data.route(created[0]);
        assertEquals(MaidLogisticsData.MutationResult.OK,
                data.update(account, route.id(), route.revision(), secondCourier, cargo));
        assertEquals(List.of(), data.schedule(firstCourier).slots());
        assertEquals(List.of(created[0], created[0], created[0]),
                data.schedule(secondCourier).slots());
    }

    @Test
    void courierSnapshotPreservesActualCargo() {
        UUID courierId = UUID.randomUUID();
        MaidLogisticsSnapshot.Courier courier = new MaidLogisticsSnapshot.Courier(
                courierId, "Courier", true, true, OVERWORLD, new BlockPos(1, 64, 2),
                0x123456, UUID.randomUUID(), List.of(
                new MaidLogisticsData.CargoLine(new ItemStack(Items.DIAMOND), 37)),
                List.of(), 0);
        MaidLogisticsSnapshot.Snapshot decoded = MaidLogisticsSnapshot.fromTag(
                MaidLogisticsSnapshot.toTag(new MaidLogisticsSnapshot.Snapshot(
                        UUID.randomUUID(), 0L, List.of(), List.of(courier), List.of(), "")));

        assertEquals(1, decoded.couriers().size());
        assertEquals(Items.DIAMOND, decoded.couriers().get(0).cargo().get(0).prototype().getItem());
        assertEquals(37, decoded.couriers().get(0).cargo().get(0).amount());
    }

    private static MaidLogisticsData.NodeRef license(int x, UUID id) {
        return new MaidLogisticsData.NodeRef(MaidLogisticsData.NodeKind.LICENSE,
                OVERWORLD, new BlockPos(x, 64, 0), id);
    }

    private static MaidLogisticsData.NodeRef warehouse(int x) {
        return new MaidLogisticsData.NodeRef(MaidLogisticsData.NodeKind.WAREHOUSE,
                OVERWORLD, new BlockPos(x, 64, 0), null);
    }
}
