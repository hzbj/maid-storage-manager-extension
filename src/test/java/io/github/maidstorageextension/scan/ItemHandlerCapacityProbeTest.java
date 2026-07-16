package io.github.maidstorageextension.scan;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.items.IItemHandler;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemHandlerCapacityProbeTest {
    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void emptySlotAcceptsTheWholeProbeWithoutWriting() {
        FakeItemHandler handler = new FakeItemHandler(ItemStack.EMPTY);
        ItemStack probe = new ItemStack(Items.OAK_LOG, 32);

        ItemHandlerCapacityProbe.Result result = ItemHandlerCapacityProbe.probe(handler, probe);

        assertTrue(result.isKnown());
        assertTrue(result.acceptsAny());
        assertFalse(result.isFull());
        assertEquals(32, result.insertableCount());
        assertTrue(handler.getStackInSlot(0).isEmpty(), "simulation must not populate the empty slot");
        assertEquals(32, probe.getCount(), "simulation must not mutate the caller's stack");
        assertTrue(handler.onlySimulatedCalls());
    }

    @Test
    void mergeCapacityReportsOnlyTheExistingStacksHeadroom() {
        FakeItemHandler handler = new FakeItemHandler(new ItemStack(Items.OAK_LOG, 48));

        ItemHandlerCapacityProbe.Result result = ItemHandlerCapacityProbe.probe(
                handler, new ItemStack(Items.OAK_LOG, 32));

        assertEquals(16, result.insertableCount());
        assertEquals(48, handler.getStackInSlot(0).getCount(), "simulation must not merge into the real slot");
    }

    @Test
    void partlyFilledSlotWithDifferentNbtStillRejectsTheExactProbeStack() {
        ItemStack namedLogs = new ItemStack(Items.OAK_LOG, 48);
        namedLogs.getOrCreateTag().putString("variant", "named");
        FakeItemHandler handler = new FakeItemHandler(namedLogs);

        ItemHandlerCapacityProbe.Result result = ItemHandlerCapacityProbe.probe(
                handler, new ItemStack(Items.OAK_LOG, 1));

        assertTrue(result.isKnown());
        assertTrue(result.isFull());
        assertEquals(0, result.insertableCount());
        assertEquals("named", handler.getStackInSlot(0).getTag().getString("variant"));
    }

    private static final class FakeItemHandler implements IItemHandler {
        private final List<ItemStack> slots;
        private boolean onlySimulatedCalls = true;

        private FakeItemHandler(ItemStack... initial) {
            slots = new ArrayList<>(Arrays.stream(initial).map(ItemStack::copy).toList());
        }

        private boolean onlySimulatedCalls() {
            return onlySimulatedCalls;
        }

        @Override
        public int getSlots() {
            return slots.size();
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return slots.get(slot);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            onlySimulatedCalls &= simulate;
            if (stack.isEmpty()) return ItemStack.EMPTY;
            ItemStack existing = slots.get(slot);
            if (!existing.isEmpty() && !ItemStack.isSameItemSameTags(existing, stack)) return stack.copy();

            int limit = Math.min(getSlotLimit(slot), stack.getMaxStackSize());
            int occupied = existing.isEmpty() ? 0 : existing.getCount();
            int moved = Math.min(stack.getCount(), Math.max(0, limit - occupied));
            if (!simulate && moved > 0) {
                if (existing.isEmpty()) slots.set(slot, stack.copyWithCount(moved));
                else existing.grow(moved);
            }
            ItemStack remainder = stack.copy();
            remainder.shrink(moved);
            return remainder;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            ItemStack existing = slots.get(slot);
            if (existing.isEmpty() || amount <= 0) return ItemStack.EMPTY;
            int extracted = Math.min(amount, existing.getCount());
            ItemStack result = existing.copyWithCount(extracted);
            if (!simulate) existing.shrink(extracted);
            return result;
        }

        @Override
        public int getSlotLimit(int slot) {
            return 64;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return true;
        }
    }
}
