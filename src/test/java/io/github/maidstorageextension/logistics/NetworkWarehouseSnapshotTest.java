package io.github.maidstorageextension.logistics;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import io.github.maidstorageextension.terminal.MailboxKey;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NetworkWarehouseSnapshotTest {
    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void inventoryFreshnessAndMapNodesRoundTripThroughPacketTag() {
        ResourceLocation overworld = new ResourceLocation("minecraft", "overworld");
        UUID warehouse = UUID.randomUUID();
        UUID list = UUID.randomUUID();
        MailboxKey mailbox = new MailboxKey(overworld, new BlockPos(76, 64, -6));
        NetworkWarehouseSnapshot.Snapshot source = new NetworkWarehouseSnapshot.Snapshot(
                true, true, mailbox, list, 7L, warehouse, "Warehouse",
                NetworkWarehouseSnapshot.InventoryState.STALE,
                20_000L, 168_001L,
                List.of(new NetworkWarehouseSnapshot.InventoryEntry(
                        new ItemStack(Items.OAK_LOG), 512)),
                true, false, true, true, true,
                NetworkWarehouseSnapshot.Blocker.ACTIVE_TRANSACTION,
                new NetworkWarehouseSnapshot.MapPoint(overworld, new BlockPos(0, 64, 0)),
                new NetworkWarehouseSnapshot.MapPoint(overworld, new BlockPos(20, 64, 4)),
                new NetworkWarehouseSnapshot.MapPoint(overworld, new BlockPos(80, 70, -10)),
                new NetworkWarehouseSnapshot.MapPoint(overworld, new BlockPos(76, 64, -6)),
                new NetworkWarehouseSnapshot.MapPoint(overworld, new BlockPos(4, 64, 4)));

        NetworkWarehouseSnapshot.Snapshot decoded = NetworkWarehouseSnapshot.fromTag(
                NetworkWarehouseSnapshot.toTag(source));

        assertEquals(warehouse, decoded.warehouse());
        assertEquals(mailbox, decoded.mailboxKey());
        assertEquals(list, decoded.inventoryList());
        assertEquals(7L, decoded.generation());
        assertEquals(NetworkWarehouseSnapshot.InventoryState.STALE, decoded.inventoryState());
        assertEquals(168_001L, decoded.inventoryAge());
        assertEquals(Items.OAK_LOG, decoded.inventory().get(0).prototype().getItem());
        assertEquals(512, decoded.inventory().get(0).available());
        assertEquals(new BlockPos(76, 64, -6), decoded.mailbox().position());
        assertTrue(decoded.enderPocketAvailable());
        assertTrue(decoded.fixedDeliveryAvailable());
        assertTrue(decoded.heldRequestListAvailable());
        assertTrue(decoded.activeTransaction());
        assertEquals(NetworkWarehouseSnapshot.Blocker.ACTIVE_TRANSACTION, decoded.blocker());

        NetworkWarehouseSnapshot.Snapshot offline = decoded.offline();
        assertFalse(offline.online());
        assertEquals(NetworkWarehouseSnapshot.Blocker.COURIER_OFFLINE, offline.blocker());
        assertEquals(512, offline.inventory().get(0).available());
    }
}
