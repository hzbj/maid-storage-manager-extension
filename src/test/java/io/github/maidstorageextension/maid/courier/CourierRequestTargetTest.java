package io.github.maidstorageextension.maid.courier;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CourierRequestTargetTest {
    @Test
    void requestListCarriesTheOwnersFixedApplicationPosition() {
        ItemStack list = new ItemStack(Items.PAPER);
        BlockPos position = new BlockPos(120, -32, -48);
        ResourceLocation dimension = new ResourceLocation("minecraft", "overworld");

        CourierRequestTarget.write(list, position, dimension);

        CourierRequestTarget.Target decoded = CourierRequestTarget.read(list);
        assertEquals(position, decoded.position());
        assertEquals(dimension, decoded.dimension());
    }

    @Test
    void unmarkedOrMalformedListsHaveNoDeliveryTarget() {
        ItemStack list = new ItemStack(Items.PAPER);
        assertNull(CourierRequestTarget.read(list));

        list.getOrCreateTag().putString(CourierRequestTarget.TAG_DIMENSION, "not a dimension");
        assertNull(CourierRequestTarget.read(list));
    }
}
