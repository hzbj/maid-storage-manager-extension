package io.github.maidstorageextension.logistics;

import io.github.maidstorageextension.item.LogisticsTrackerItem;
import io.github.maidstorageextension.registry.ExtensionMenus;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

public final class LogisticsTrackerMenu extends AbstractContainerMenu {
    private final Inventory inventory;
    private final UUID courier;

    public LogisticsTrackerMenu(int containerId, Inventory inventory, UUID courier) {
        super(ExtensionMenus.LOGISTICS_TRACKER.get(), containerId);
        this.inventory = inventory;
        this.courier = courier;
    }

    public static LogisticsTrackerMenu fromNetwork(int containerId, Inventory inventory,
                                                   FriendlyByteBuf buffer) {
        return new LogisticsTrackerMenu(containerId, inventory, buffer.readUUID());
    }

    public UUID courier() {
        return courier;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.getItem() instanceof LogisticsTrackerItem
                    && courier.equals(LogisticsTrackerItem.getCourier(stack))) return true;
        }
        return false;
    }
}
