package io.github.maidstorageextension.maid.courier;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;

final class CourierInventory {
    private CourierInventory() {
    }

    static int count(IItemHandler inventory, ItemStack prototype) {
        int count = 0;
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (ItemStack.isSameItemSameTags(stack, prototype)) count += stack.getCount();
        }
        return count;
    }

    static ItemStack insert(IItemHandler inventory, ItemStack offered) {
        ItemStack remainder = offered.copy();
        for (int slot = 0; slot < inventory.getSlots() && !remainder.isEmpty(); slot++) {
            remainder = inventory.insertItem(slot, remainder, false);
        }
        return remainder;
    }

    static ItemStack extract(IItemHandler inventory, ItemStack prototype, int amount) {
        ItemStack result = prototype.copyWithCount(0);
        int wanted = Math.max(0, amount);
        for (int slot = 0; slot < inventory.getSlots() && result.getCount() < wanted; slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (!ItemStack.isSameItemSameTags(stack, prototype)) continue;
            ItemStack extracted = inventory.extractItem(slot,
                    Math.min(wanted - result.getCount(), stack.getCount()), false);
            result.grow(extracted.getCount());
        }
        return result;
    }

    /** Extracts only the exact-type amount above a protected pre-transaction baseline. */
    static ItemStack extractAboveBaseline(IItemHandler inventory, ItemStack prototype,
                                          int baseline, int amount) {
        int surplus = Math.max(0, count(inventory, prototype) - Math.max(0, baseline));
        return extract(inventory, prototype, Math.min(surplus, amount));
    }

    static boolean removeExactSlot(IItemHandlerModifiable inventory, int slot, ItemStack expected) {
        ItemStack actual = inventory.getStackInSlot(slot);
        if (!ItemStack.isSameItemSameTags(actual, expected) || actual.getCount() < expected.getCount()) {
            return false;
        }
        ItemStack rest = actual.copy();
        rest.shrink(expected.getCount());
        inventory.setStackInSlot(slot, rest);
        return true;
    }
}
