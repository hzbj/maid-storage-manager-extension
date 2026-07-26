package io.github.maidstorageextension.logistics;

import io.github.maidstorageextension.migration.ItemDataMigration;
import io.github.maidstorageextension.network.NetworkWarehouseActionPacket;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import studio.fantasyit.maid_storage_manager.items.RequestListItem;
import studio.fantasyit.maid_storage_manager.registry.DataComponentRegistry;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class NetworkWarehouseRequestFactoryTest {
    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void updatesExistingRequestListUsingPublicComponentsAndPreservesCustomData() {
        UUID owner = UUID.randomUUID();
        UUID courier = UUID.randomUUID();
        UUID warehouse = UUID.randomUUID();
        UUID originalJob = UUID.randomUUID();
        ItemStack request = new ItemStack(Items.PAPER);
        request.set(DataComponentRegistry.REQUEST_WORK_UUID.get(), originalJob);
        ItemDataMigration.update(request,
                tag -> tag.putString("PlayerConfiguredValue", "preserved"));

        ItemStack updated = NetworkWarehouseRequestFactory.update(
                request, owner, courier, warehouse, 42L,
                NetworkWarehouseActionPacket.DeliveryTarget.PLAYER,
                List.of(
                        new NetworkWarehouseActionPacket.RequestedItem(
                                new ItemStack(Items.OAK_LOG), 96),
                        new NetworkWarehouseActionPacket.RequestedItem(
                                new ItemStack(Items.IRON_INGOT), 12)));

        assertSame(request, updated);
        assertEquals(Items.PAPER, updated.getItem());
        assertEquals(originalJob,
                updated.get(DataComponentRegistry.REQUEST_WORK_UUID.get()));
        assertEquals("preserved",
                ItemDataMigration.read(updated).getString("PlayerConfiguredValue"));
        var items = RequestListItem.getImmutableRequestData(updated).list();
        assertEquals(2, items.size());
        assertEquals(96, items.get(0).requested());
        assertEquals(Items.OAK_LOG, items.get(0).item().getItem());

        var metadata = ItemDataMigration.read(updated).getCompound(
                NetworkWarehouseRequestFactory.TAG_NETWORK_ORDER);
        assertEquals(owner, metadata.getUUID("owner"));
        assertEquals(courier, metadata.getUUID("courier"));
        assertEquals(warehouse, metadata.getUUID("warehouse"));
        assertEquals("PLAYER", metadata.getString("delivery"));
    }
}
