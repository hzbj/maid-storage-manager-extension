package io.github.maidstorageextension.compat;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

/** Registry-based integration with Touhou Little Maid: Spell. */
public final class EnderPocketCompat {
    public static final ResourceLocation ITEM_ID =
            new ResourceLocation("touhou_little_maid_spell", "ender_pocket");
    public static final ResourceLocation BROOM_ITEM_ID =
            new ResourceLocation("touhou_little_maid", "broom");

    private EnderPocketCompat() {
    }

    public static boolean isEquipped(EntityMaid maid) {
        Item item = ForgeRegistries.ITEMS.getValue(ITEM_ID);
        if (item == null || item == Items.AIR) {
            return false;
        }
        var baubles = maid.getMaidBauble();
        for (int slot = 0; slot < baubles.getSlots(); slot++) {
            if (baubles.getStackInSlot(slot).is(item)) {
                return true;
            }
        }
        return false;
    }

    public static boolean hasBroom(EntityMaid maid) {
        Item broom = ForgeRegistries.ITEMS.getValue(BROOM_ITEM_ID);
        if (broom == null || broom == Items.AIR) return false;
        var inventory = maid.getAvailableInv(false);
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            if (inventory.getStackInSlot(slot).is(broom)) return true;
        }
        return false;
    }

    public static boolean isBroom(ItemStack stack) {
        Item broom = ForgeRegistries.ITEMS.getValue(BROOM_ITEM_ID);
        return broom != null && broom != Items.AIR && stack.is(broom);
    }

    public static ItemStack icon() {
        Item item = ForgeRegistries.ITEMS.getValue(ITEM_ID);
        if (item != null && item != Items.AIR) return item.getDefaultInstance();
        Item broom = ForgeRegistries.ITEMS.getValue(BROOM_ITEM_ID);
        return broom == null || broom == Items.AIR
                ? Items.ENDER_EYE.getDefaultInstance() : broom.getDefaultInstance();
    }
}
