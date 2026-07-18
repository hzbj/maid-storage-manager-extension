package io.github.maidstorageextension.logistics;

import net.minecraft.SharedConstants;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import io.github.maidstorageextension.network.NetworkWarehouseActionPacket;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import studio.fantasyit.maid_storage_manager.items.RequestListItem;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NetworkWarehouseRequestFactoryTest {
    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void updatesExistingRequestListInPlaceWithoutCreatingAnItem() {
        UUID owner = UUID.randomUUID();
        UUID courier = UUID.randomUUID();
        UUID warehouse = UUID.randomUUID();

        ItemStack request = new ItemStack(Items.PAPER);
        UUID originalJob = UUID.randomUUID();
        request.getOrCreateTag().putUUID(RequestListItem.TAG_UUID, originalJob);
        request.getOrCreateTag().putString("PlayerConfiguredValue", "preserved");

        ItemStack updated = NetworkWarehouseRequestFactory.update(
                request,
                owner,
                courier,
                warehouse,
                42L,
                NetworkWarehouseActionPacket.DeliveryTarget.PLAYER,
                List.of(
                        new NetworkWarehouseActionPacket.RequestedItem(
                                new ItemStack(Items.OAK_LOG), 96),
                        new NetworkWarehouseActionPacket.RequestedItem(
                                new ItemStack(Items.IRON_INGOT), 12)));

        assertSame(request, updated);
        assertEquals(Items.PAPER, updated.getItem());
        assertEquals(originalJob, updated.getOrCreateTag().getUUID(RequestListItem.TAG_UUID));
        assertEquals("preserved", updated.getOrCreateTag().getString("PlayerConfiguredValue"));
        ListTag items = updated.getOrCreateTag().getList(
                RequestListItem.TAG_ITEMS, Tag.TAG_COMPOUND);
        assertEquals(2, items.size());
        assertEquals(96, items.getCompound(0).getInt(RequestListItem.TAG_ITEMS_REQUESTED));
        assertEquals(Items.OAK_LOG, ItemStack.of(items.getCompound(0)
                .getCompound(RequestListItem.TAG_ITEMS_ITEM)).getItem());

        var metadata = updated.getOrCreateTag().getCompound(
                NetworkWarehouseRequestFactory.TAG_NETWORK_ORDER);
        assertEquals(owner, metadata.getUUID("owner"));
        assertEquals(courier, metadata.getUUID("courier"));
        assertEquals(warehouse, metadata.getUUID("warehouse"));
        assertEquals("PLAYER", metadata.getString("delivery"));
    }
}
