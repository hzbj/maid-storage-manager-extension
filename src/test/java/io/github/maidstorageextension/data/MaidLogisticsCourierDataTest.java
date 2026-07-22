package io.github.maidstorageextension.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MaidLogisticsCourierDataTest {
    @Test
    void requestAndCourierSnapshotsKeepCompleteNbtAcrossRestart() {
        ItemStack request = new ItemStack(Items.PAPER);
        request.getOrCreateTag().putString("Custom", "原始值");
        request.getOrCreateTag().putInt("DamageLike", 17);
        CompoundTag courier = new CompoundTag();
        courier.putString("phase", CourierData.Phase.IDLE.name());
        courier.putUUID("warehouse", UUID.randomUUID());
        UUID account = UUID.randomUUID();
        UUID route = UUID.randomUUID();
        UUID token = UUID.randomUUID();
        MaidLogisticsCourierData.Data source = new MaidLogisticsCourierData.Data();
        source.begin(account, route, token, request, courier, 12_345L);
        source.cargo().add(new MaidLogisticsCourierData.CargoEntry(
                new ItemStack(Items.DIAMOND), 7, 2));

        MaidLogisticsCourierData key = new MaidLogisticsCourierData();
        MaidLogisticsCourierData.Data decoded = key.readSaveData(key.writeSaveData(source));
        assertEquals(account, decoded.account());
        assertEquals(route, decoded.route());
        assertEquals(token, decoded.requestToken());
        assertTrue(ItemStack.isSameItemSameTags(request, decoded.originalRequest()));
        assertEquals(courier, decoded.originalCourier());
        assertEquals(7, decoded.cargo().get(0).amount());
        assertEquals(2, decoded.cargo().get(0).baseline());
    }
}
