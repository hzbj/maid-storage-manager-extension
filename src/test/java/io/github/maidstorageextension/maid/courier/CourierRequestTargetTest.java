package io.github.maidstorageextension.maid.courier;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CourierRequestTargetTest {
    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void requestListCarriesTheOwnersFixedApplicationPosition() {
        ItemStack list = new ItemStack(Items.PAPER);
        BlockPos position = new BlockPos(120, -32, -48);
        ResourceLocation dimension = new ResourceLocation("minecraft", "overworld");

        assertTrue(CourierRequestTarget.write(list, position, dimension));

        CourierRequestTarget.Target decoded = CourierRequestTarget.read(list);
        assertEquals(position, decoded.position());
        assertEquals(dimension, decoded.dimension());
        assertFalse(CourierRequestTarget.write(list, position, dimension),
                "Writing the same delivery target must not produce repeated feedback");
    }

    @Test
    void movingOrChangingDimensionUpdatesTheDeliveryTarget() {
        ItemStack list = new ItemStack(Items.PAPER);
        ResourceLocation overworld = new ResourceLocation("minecraft", "overworld");
        ResourceLocation nether = new ResourceLocation("minecraft", "the_nether");

        assertTrue(CourierRequestTarget.write(list, new BlockPos(1, 64, 1), overworld));
        assertTrue(CourierRequestTarget.write(list, new BlockPos(2, 64, 1), overworld));
        assertTrue(CourierRequestTarget.write(list, new BlockPos(2, 64, 1), nether));

        CourierRequestTarget.Target decoded = CourierRequestTarget.read(list);
        assertEquals(new BlockPos(2, 64, 1), decoded.position());
        assertEquals(nether, decoded.dimension());
    }

    @Test
    void unmarkedOrMalformedListsHaveNoDeliveryTarget() {
        ItemStack list = new ItemStack(Items.PAPER);
        assertNull(CourierRequestTarget.read(list));

        list.getOrCreateTag().putString(CourierRequestTarget.TAG_DIMENSION, "not a dimension");
        assertNull(CourierRequestTarget.read(list));
    }
}
