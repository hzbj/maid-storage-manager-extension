package io.github.maidstorageextension.license;

import io.github.maidstorageextension.block.BusinessLicenseBlockEntity;
import io.github.maidstorageextension.registry.ExtensionMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

public final class BusinessLicenseMenu extends AbstractContainerMenu {
    private final Inventory inventory;
    private final BlockPos position;
    private final UUID licenseId;
    private final UUID terminalId;

    public BusinessLicenseMenu(int id, Inventory inventory, BlockPos position, UUID licenseId) {
        this(id, inventory, position, licenseId, null);
    }

    public BusinessLicenseMenu(int id, Inventory inventory, BlockPos position, UUID licenseId,
                               UUID terminalId) {
        super(ExtensionMenus.BUSINESS_LICENSE.get(), id);
        this.inventory = inventory;
        this.position = position.immutable();
        this.licenseId = licenseId;
        this.terminalId = terminalId;
    }

    public static BusinessLicenseMenu fromNetwork(int id, Inventory inventory, FriendlyByteBuf buffer) {
        BlockPos position = buffer.readBlockPos();
        UUID license = buffer.readUUID();
        UUID terminal = buffer.readBoolean() ? buffer.readUUID() : null;
        return new BusinessLicenseMenu(id, inventory, position, license, terminal);
    }

    public BlockPos position() {
        return position;
    }

    public UUID licenseId() {
        return licenseId;
    }

    public UUID terminalId() {
        return terminalId;
    }

    public boolean remote() {
        return terminalId != null;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        if (remote()) return player.level().isClientSide ||
                player instanceof net.minecraft.server.level.ServerPlayer serverPlayer
                && io.github.maidstorageextension.license.BusinessLicenseService
                .canConfigureFromTerminal(serverPlayer, terminalId, licenseId);
        if (player.distanceToSqr(position.getX() + 0.5, position.getY() + 0.5,
                position.getZ() + 0.5) > 64.0D) return false;
        return player.level().getBlockEntity(position) instanceof BusinessLicenseBlockEntity license
                && licenseId.equals(license.licenseId()) && license.isOwner(player);
    }
}
