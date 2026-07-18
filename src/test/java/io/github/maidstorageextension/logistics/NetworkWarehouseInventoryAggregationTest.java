package io.github.maidstorageextension.logistics;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import studio.fantasyit.maid_storage_manager.data.InventoryItem;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NetworkWarehouseInventoryAggregationTest {
    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void flattensUpstreamRegistryGroupsAndMergesExactStacks() {
        Map<String, List<InventoryItem>> grouped = Map.of(
                "minecraft:oak_log", List.of(
                        new InventoryItem(new ItemStack(Items.OAK_LOG), 32),
                        new InventoryItem(new ItemStack(Items.OAK_LOG), 48)),
                "minecraft:iron_ingot", List.of(
                        new InventoryItem(new ItemStack(Items.IRON_INGOT), 19)));

        List<NetworkWarehouseSnapshot.InventoryEntry> result =
                NetworkWarehouseService.aggregate(grouped);

        assertEquals(2, result.size());
        assertEquals(Items.IRON_INGOT, result.get(0).prototype().getItem());
        assertEquals(19, result.get(0).available());
        assertEquals(Items.OAK_LOG, result.get(1).prototype().getItem());
        assertEquals(80, result.get(1).available());
    }

    @Test
    void ignoresCraftableOnlyEntriesWithoutPhysicalStock() {
        Map<String, List<InventoryItem>> grouped = Map.of(
                "minecraft:diamond", List.of(
                        new InventoryItem(new ItemStack(Items.DIAMOND), 0)));

        assertEquals(List.of(), NetworkWarehouseService.aggregate(grouped));
    }
}
