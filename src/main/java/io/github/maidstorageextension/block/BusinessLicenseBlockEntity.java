package io.github.maidstorageextension.block;

import io.github.maidstorageextension.data.BusinessLicenseData;
import io.github.maidstorageextension.license.BusinessLicenseMenu;
import io.github.maidstorageextension.registry.ExtensionBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/** Keeps only physical identity; mutable license configuration lives in world SavedData. */
public final class BusinessLicenseBlockEntity extends BlockEntity implements MenuProvider {
    private UUID licenseId = UUID.randomUUID();
    private UUID owner;

    public BusinessLicenseBlockEntity(BlockPos pos, BlockState state) {
        super(ExtensionBlockEntities.BUSINESS_LICENSE.get(), pos, state);
    }

    public void initialize(Player player) {
        if (level == null || level.isClientSide || level.getServer() == null) return;
        owner = player.getUUID();
        BusinessLicenseData.get(level.getServer()).create(licenseId, owner,
                level.dimension().location(), worldPosition);
        setChanged();
    }

    public UUID licenseId() {
        return licenseId;
    }

    public UUID owner() {
        return owner;
    }

    public boolean isOwner(Player player) {
        return owner != null && player != null && owner.equals(player.getUUID());
    }

    @Override
    public @NotNull Component getDisplayName() {
        return Component.translatable("gui.maid_storage_manager_extension.business_license.title");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new BusinessLicenseMenu(containerId, inventory, worldPosition, licenseId);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putUUID("LicenseId", licenseId);
        if (owner != null) tag.putUUID("Owner", owner);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        licenseId = tag.hasUUID("LicenseId") ? tag.getUUID("LicenseId") : UUID.randomUUID();
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
    }
}
