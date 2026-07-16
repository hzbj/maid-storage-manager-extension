package io.github.maidstorageextension.block;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import io.github.maidstorageextension.data.CourierData;
import io.github.maidstorageextension.data.WarehouseStationData;
import io.github.maidstorageextension.maid.courier.CourierWarehouseStationValidator;
import io.github.maidstorageextension.registry.ExtensionBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import studio.fantasyit.maid_storage_manager.maid.task.StorageManageTask;

import java.util.Comparator;
import java.util.UUID;

/** Mailbox authority for a separately marked 3x3 courier flight pad. */
public final class CourierWarehouseStationBlockEntity extends BlockEntity {
    public static final double BIND_RANGE = 64.0;
    public static final String TAG_LANDING_POS = "LandingPos";
    public static final String TAG_LANDING_DIMENSION = "LandingDimension";
    public static final String TAG_PLACER = "PlacerUUID";
    public static final String TAG_PLACER_NAME = "PlacerName";
    private static final String TAG_WAREHOUSE = "WarehouseUUID";
    private static final String TAG_WAREHOUSE_NAME = "WarehouseName";
    private static final String TAG_WAREHOUSE_POS = "WarehousePos";
    private static final String TAG_APPROVAL = "Approval";

    public enum Approval {
        UNCONFIGURED,
        UNBOUND,
        PENDING,
        APPROVED,
        REJECTED
    }

    @Nullable private BlockPos landingPos;
    @Nullable private ResourceLocation landingDimension;
    @Nullable private UUID placerUuid;
    private String placerName = "";
    @Nullable private UUID warehouseUuid;
    private String warehouseName = "";
    @Nullable private BlockPos warehousePos;
    private Approval approval = Approval.UNCONFIGURED;

    public CourierWarehouseStationBlockEntity(BlockPos pos, BlockState state) {
        super(ExtensionBlockEntities.COURIER_WAREHOUSE_STATION.get(), pos, state);
    }

    public void initializeFromPlacedStack(Player player, ItemStack stack) {
        if (!(level instanceof ServerLevel)) return;
        if (placerUuid == null) placerUuid = player.getUUID();
        if (placerName.isBlank()) placerName = player.getName().getString();
        if (landingPos == null || landingDimension == null) {
            approval = Approval.UNCONFIGURED;
            setChanged();
            player.sendSystemMessage(Component.translatable(
                    "message.maid_storage_manager_extension.courier_mailbox.unconfigured"));
            return;
        }
        requestNearest(player, true);
    }

    public void requestNearest(Player player, boolean notify) {
        if (!(level instanceof ServerLevel serverLevel)) return;
        removePendingRequest();
        if (!validConfiguration(serverLevel)) {
            clearWarehouse(Approval.UNBOUND);
            if (notify) player.sendSystemMessage(Component.translatable(
                    "message.maid_storage_manager_extension.courier_mailbox.invalid_configuration"));
            return;
        }

        var candidates = serverLevel.getEntitiesOfClass(EntityMaid.class,
                        new net.minecraft.world.phys.AABB(worldPosition).inflate(BIND_RANGE),
                        maid -> isWarehouseCandidate(maid, worldPosition));
        EntityMaid warehouse = candidates.stream()
                .filter(maid -> maid.isOwnedBy(player))
                .min(Comparator.comparingDouble(maid -> maid.distanceToSqr(worldPosition.getCenter())))
                .orElseGet(() -> candidates.stream()
                        .min(Comparator.comparingDouble(
                                maid -> maid.distanceToSqr(worldPosition.getCenter())))
                        .orElse(null));
        if (warehouse == null) {
            clearWarehouse(Approval.UNBOUND);
            if (notify) player.sendSystemMessage(Component.translatable(
                    "message.maid_storage_manager_extension.courier_station.no_warehouse"));
            return;
        }

        warehouseUuid = warehouse.getUUID();
        warehouseName = warehouse.getName().getString();
        warehousePos = warehouse.hasRestriction()
                ? warehouse.getRestrictCenter().immutable() : warehouse.blockPosition().immutable();
        placerUuid = player.getUUID();
        placerName = player.getName().getString();
        if (warehouse.isOwnedBy(player)) {
            approval = Approval.APPROVED;
            if (notify) player.sendSystemMessage(Component.translatable(
                    "message.maid_storage_manager_extension.courier_mailbox.auto_approved",
                    warehouse.getName()));
        } else {
            approval = Approval.PENDING;
            WarehouseStationData.Data data = WarehouseStationData.get(warehouse);
            data.request(new WarehouseStationData.StationRequest(stationKey(), placerName));
            sync(warehouse, data);
            if (notify) player.sendSystemMessage(Component.translatable(
                    "message.maid_storage_manager_extension.courier_mailbox.approval_requested",
                    warehouse.getName()));
            if (warehouse.getOwner() instanceof ServerPlayer owner) {
                owner.sendSystemMessage(Component.translatable(
                        "message.maid_storage_manager_extension.courier_mailbox.approval_pending",
                        placerName, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ()));
            }
        }
        setChanged();
    }

    public boolean approve(ServerPlayer owner, EntityMaid warehouse) {
        if (!(level instanceof ServerLevel serverLevel)
                || warehouseUuid == null || !warehouseUuid.equals(warehouse.getUUID())
                || !warehouse.isOwnedBy(owner)
                || !warehouse.getTask().getUid().equals(StorageManageTask.TASK_ID)
                || warehouse.level() != serverLevel
                || warehouse.distanceToSqr(worldPosition.getCenter()) > BIND_RANGE * BIND_RANGE
                || !validConfiguration(serverLevel)) {
            return false;
        }
        approval = Approval.APPROVED;
        warehouseName = warehouse.getName().getString();
        warehousePos = warehouse.hasRestriction()
                ? warehouse.getRestrictCenter().immutable() : warehouse.blockPosition().immutable();
        removePendingRequest(warehouse);
        setChanged();
        return true;
    }

    public boolean reject(ServerPlayer owner, EntityMaid warehouse) {
        if (warehouseUuid == null || !warehouseUuid.equals(warehouse.getUUID())
                || !warehouse.isOwnedBy(owner)) return false;
        approval = Approval.REJECTED;
        removePendingRequest(warehouse);
        setChanged();
        return true;
    }

    public void describe(Player player) {
        if (!(level instanceof ServerLevel serverLevel)) return;
        if (landingPos == null || landingDimension == null) {
            player.sendSystemMessage(Component.translatable(
                    "message.maid_storage_manager_extension.courier_mailbox.unconfigured"));
        } else if (!validConfiguration(serverLevel)) {
            player.sendSystemMessage(Component.translatable(
                    "message.maid_storage_manager_extension.courier_mailbox.invalid_configuration"));
        } else if (warehouseUuid == null) {
            player.sendSystemMessage(Component.translatable(
                    "message.maid_storage_manager_extension.courier_station.unbound"));
        } else {
            player.sendSystemMessage(Component.translatable(
                    "message.maid_storage_manager_extension.courier_mailbox.status",
                    warehouseName, Component.translatable(
                            "message.maid_storage_manager_extension.courier_mailbox.approval."
                                    + approval.name().toLowerCase(java.util.Locale.ROOT)),
                    landingPos.getX(), landingPos.getY(), landingPos.getZ()));
        }
    }

    @Nullable
    public CourierData.WarehouseBinding binding(ServerLevel serverLevel) {
        if (approval != Approval.APPROVED || warehouseUuid == null
                || !validConfiguration(serverLevel)) return null;
        EntityMaid warehouse = serverLevel.getEntity(warehouseUuid) instanceof EntityMaid maid
                ? maid : null;
        BlockPos currentWarehousePos = warehouse == null ? warehousePos
                : warehouse.hasRestriction() ? warehouse.getRestrictCenter() : warehouse.blockPosition();
        String currentName = warehouse == null ? warehouseName : warehouse.getName().getString();
        return new CourierData.WarehouseBinding(warehouseUuid, currentWarehousePos,
                serverLevel.dimension().location(), worldPosition,
                serverLevel.dimension().location(), landingPos,
                landingDimension, currentName);
    }

    public boolean validConfiguration(ServerLevel serverLevel) {
        return landingPos != null && landingDimension != null
                && landingDimension.equals(serverLevel.dimension().location())
                && CourierWarehouseStationValidator.mailboxInRange(landingPos, worldPosition)
                && CourierWarehouseStationValidator.hasValidPad(serverLevel, landingPos);
    }

    public boolean isBoundTo(UUID warehouse) {
        return approval == Approval.APPROVED && warehouse != null && warehouse.equals(warehouseUuid);
    }

    public Approval approval() {
        return approval;
    }

    @Nullable
    public BlockPos landingPos() {
        return landingPos;
    }

    public WarehouseStationData.StationKey stationKey() {
        ResourceLocation dimension = level == null ? landingDimension : level.dimension().location();
        return new WarehouseStationData.StationKey(dimension, worldPosition);
    }

    public void detach() {
        removePendingRequest();
    }

    private void clearWarehouse(Approval next) {
        warehouseUuid = null;
        warehouseName = "";
        warehousePos = null;
        approval = next;
        setChanged();
    }

    private void removePendingRequest() {
        if (!(level instanceof ServerLevel serverLevel) || warehouseUuid == null) return;
        Entity entity = serverLevel.getEntity(warehouseUuid);
        if (entity instanceof EntityMaid warehouse) removePendingRequest(warehouse);
    }

    private void removePendingRequest(EntityMaid warehouse) {
        WarehouseStationData.Data data = WarehouseStationData.get(warehouse);
        if (data.remove(stationKey())) sync(warehouse, data);
    }

    private static boolean isWarehouseCandidate(EntityMaid maid, BlockPos mailbox) {
        return maid.isAlive()
                && maid.getTask().getUid().equals(StorageManageTask.TASK_ID)
                && maid.distanceToSqr(mailbox.getCenter()) <= BIND_RANGE * BIND_RANGE;
    }

    private static void sync(EntityMaid maid, WarehouseStationData.Data data) {
        maid.setAndSyncData(WarehouseStationData.KEY, data);
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag) {
        super.saveAdditional(tag);
        if (landingPos != null) tag.putLong(TAG_LANDING_POS, landingPos.asLong());
        if (landingDimension != null) tag.putString(TAG_LANDING_DIMENSION, landingDimension.toString());
        if (placerUuid != null) tag.putUUID(TAG_PLACER, placerUuid);
        if (!placerName.isEmpty()) tag.putString(TAG_PLACER_NAME, placerName);
        if (warehouseUuid != null) tag.putUUID(TAG_WAREHOUSE, warehouseUuid);
        if (!warehouseName.isEmpty()) tag.putString(TAG_WAREHOUSE_NAME, warehouseName);
        if (warehousePos != null) tag.putLong(TAG_WAREHOUSE_POS, warehousePos.asLong());
        tag.putString(TAG_APPROVAL, approval.name());
    }

    @Override
    public void load(@NotNull CompoundTag tag) {
        super.load(tag);
        landingPos = tag.contains(TAG_LANDING_POS, Tag.TAG_LONG)
                ? BlockPos.of(tag.getLong(TAG_LANDING_POS)) : null;
        landingDimension = tag.contains(TAG_LANDING_DIMENSION, Tag.TAG_STRING)
                ? ResourceLocation.tryParse(tag.getString(TAG_LANDING_DIMENSION)) : null;
        placerUuid = tag.hasUUID(TAG_PLACER) ? tag.getUUID(TAG_PLACER) : null;
        placerName = tag.getString(TAG_PLACER_NAME);
        warehouseUuid = tag.hasUUID(TAG_WAREHOUSE) ? tag.getUUID(TAG_WAREHOUSE) : null;
        warehouseName = tag.getString(TAG_WAREHOUSE_NAME);
        warehousePos = tag.contains(TAG_WAREHOUSE_POS, Tag.TAG_LONG)
                ? BlockPos.of(tag.getLong(TAG_WAREHOUSE_POS)) : null;
        try {
            approval = tag.contains(TAG_APPROVAL, Tag.TAG_STRING)
                    ? Approval.valueOf(tag.getString(TAG_APPROVAL)) : Approval.UNCONFIGURED;
        } catch (IllegalArgumentException ignored) {
            approval = Approval.UNCONFIGURED;
        }
        if (landingPos == null || landingDimension == null) {
            approval = Approval.UNCONFIGURED;
            warehouseUuid = null;
            warehouseName = "";
            warehousePos = null;
        }
    }
}
