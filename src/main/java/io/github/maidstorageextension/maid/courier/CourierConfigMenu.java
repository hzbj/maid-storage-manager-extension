package io.github.maidstorageextension.maid.courier;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import io.github.maidstorageextension.compat.EnderPocketCompat;
import io.github.maidstorageextension.registry.ExtensionMenus;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

public final class CourierConfigMenu extends AbstractContainerMenu {
    private final Inventory playerInventory;
    private final int maidId;

    public CourierConfigMenu(int containerId, Inventory playerInventory, int maidId) {
        super(ExtensionMenus.COURIER_CONFIG.get(), containerId);
        this.playerInventory = playerInventory;
        this.maidId = maidId;
        EnderPocketCompat.syncRemoteProxyBeforeMenu(playerInventory.player, maidId);
    }

    public static CourierConfigMenu fromNetwork(int containerId, Inventory inventory,
                                                FriendlyByteBuf buffer) {
        // EntityMaid.openMaidGui writes the entity id with FriendlyByteBuf.writeInt.
        return new CourierConfigMenu(containerId, inventory, buffer.readInt());
    }

    public EntityMaid maid() {
        return EnderPocketCompat.resolveRemoteMaid(playerInventory.player, maidId);
    }

    public int maidId() {
        return maidId;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        EntityMaid maid = maid();
        return maid != null && maid.isAlive() && (player.level().isClientSide || maid.isOwnedBy(player));
    }
}
