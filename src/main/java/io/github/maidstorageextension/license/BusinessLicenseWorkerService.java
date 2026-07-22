package io.github.maidstorageextension.license;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import io.github.maidstorageextension.data.BusinessLicenseData;
import io.github.maidstorageextension.data.DriverData;
import io.github.maidstorageextension.maid.ExtensionWorkControl;
import io.github.maidstorageextension.maid.courier.CourierService;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.registries.ForgeRegistries;
import studio.fantasyit.maid_storage_manager.util.MemoryUtil;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Opportunistic local unload route for bound production maids. */
public final class BusinessLicenseWorkerService {
    private static final int CHECK_INTERVAL = 10;
    private static final int RETRY_COOLDOWN_TICKS = 100;
    private static final double INTERACTION_DISTANCE_SQR = 9.0D;

    private record Work(UUID license, BusinessLicenseData.ContainerRef target) {
    }

    private static final Map<UUID, Work> ACTIVE = new LinkedHashMap<>();
    private static final Map<UUID, Long> RETRY_AT = new LinkedHashMap<>();

    private BusinessLicenseWorkerService() {
    }

    public static void tick(ServerLevel level, EntityMaid maid) {
        if (maid.tickCount % CHECK_INTERVAL != 0) return;
        BusinessLicenseData data = BusinessLicenseData.get(level.getServer());
        BusinessLicenseData.Snapshot license = data.forWorker(maid.getUUID());
        if (!eligible(level, maid, license)) {
            ACTIVE.remove(maid.getUUID());
            return;
        }
        Work active = ACTIVE.get(maid.getUUID());
        if (active != null && (!active.license.equals(license.id())
                || !license.containers().contains(active.target))) {
            ACTIVE.remove(maid.getUUID());
            active = null;
        }
        if (active == null) {
            long retry = RETRY_AT.getOrDefault(maid.getUUID(), 0L);
            if (level.getGameTime() < retry || !nearFullWithAllowedCargo(maid, license)) return;
            BusinessLicenseData.ContainerRef target = nearestAccepting(level, maid, license);
            if (target == null) {
                data.blocker(license.id(),
                        "message.maid_storage_manager_extension.business_license.no_container_space");
                RETRY_AT.put(maid.getUUID(), level.getGameTime() + RETRY_COOLDOWN_TICKS);
                return;
            }
            active = new Work(license.id(), target);
            ACTIVE.put(maid.getUUID(), active);
        }
        BlockPos targetPos = active.target.position();
        if (!level.hasChunkAt(targetPos)) {
            fail(level, maid, data, license.id());
            return;
        }
        if (maid.distanceToSqr(targetPos.getCenter()) > INTERACTION_DISTANCE_SQR) {
            maid.getNavigation().moveTo(targetPos.getX() + 0.5, targetPos.getY() + 0.5,
                    targetPos.getZ() + 0.5, 0.6D);
            return;
        }
        int moved = transfer(level, maid, license, active.target);
        maid.getNavigation().stop();
        ACTIVE.remove(maid.getUUID());
        if (moved > 0) {
            data.blocker(license.id(), "");
            RETRY_AT.remove(maid.getUUID());
        } else {
            fail(level, maid, data, license.id());
        }
    }

    private static boolean eligible(ServerLevel level, EntityMaid maid,
                                    BusinessLicenseData.Snapshot license) {
        return license != null && maid.isAlive() && !maid.isPassenger() && maid.getTarget() == null
                && license.dimension().equals(level.dimension().location())
                && BusinessLicenseData.withinHorizontal(license.position(), maid.blockPosition(),
                BusinessLicenseData.RANGE)
                && license.profession() != null && license.profession().equals(maid.getTask().getUid())
                && !CourierService.hasActiveTransaction(maid) && !DriverData.get(maid).activeTrip()
                && !ExtensionWorkControl.hasNonInterruptibleWork(maid);
    }

    private static boolean nearFullWithAllowedCargo(EntityMaid maid,
                                                    BusinessLicenseData.Snapshot license) {
        IItemHandler backpack = maid.getAvailableBackpackInv();
        int empty = 0;
        boolean allowed = false;
        for (int slot = 0; slot < backpack.getSlots(); slot++) {
            ItemStack stack = backpack.getStackInSlot(slot);
            if (stack.isEmpty()) empty++;
            else if (allows(license, stack)) allowed = true;
        }
        return empty <= 1 && allowed;
    }

    private static BusinessLicenseData.ContainerRef nearestAccepting(
            ServerLevel level, EntityMaid maid, BusinessLicenseData.Snapshot license) {
        BusinessLicenseData.ContainerRef best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BusinessLicenseData.ContainerRef ref : license.containers()) {
            if (!level.hasChunkAt(ref.position())) continue;
            IItemHandler target = handler(level, ref);
            if (target == null || !acceptsAny(maid.getAvailableBackpackInv(), target, license)) continue;
            double distance = maid.distanceToSqr(ref.position().getCenter());
            if (distance < bestDistance) {
                best = ref;
                bestDistance = distance;
            }
        }
        return best;
    }

    private static boolean acceptsAny(IItemHandler source, IItemHandler target,
                                      BusinessLicenseData.Snapshot license) {
        for (int slot = 0; slot < source.getSlots(); slot++) {
            ItemStack stack = source.getStackInSlot(slot);
            if (!stack.isEmpty() && allows(license, stack)
                    && ItemHandlerHelper.insertItem(target, stack.copyWithCount(1), true).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static int transfer(ServerLevel level, EntityMaid maid,
                                BusinessLicenseData.Snapshot license,
                                BusinessLicenseData.ContainerRef ref) {
        IItemHandler target = handler(level, ref);
        if (target == null) return 0;
        IItemHandler source = maid.getAvailableBackpackInv();
        int total = 0;
        for (int slot = 0; slot < source.getSlots(); slot++) {
            ItemStack current = source.getStackInSlot(slot);
            if (current.isEmpty() || !allows(license, current)) continue;
            ItemStack simulated = ItemHandlerHelper.insertItem(target, current.copy(), true);
            int movable = current.getCount() - simulated.getCount();
            if (movable <= 0) continue;
            ItemStack extracted = source.extractItem(slot, movable, false);
            ItemStack remainder = ItemHandlerHelper.insertItem(target, extracted, false);
            total += extracted.getCount() - remainder.getCount();
            if (!remainder.isEmpty()) ItemHandlerHelper.insertItem(source, remainder, false);
        }
        return total;
    }

    private static IItemHandler handler(ServerLevel level, BusinessLicenseData.ContainerRef ref) {
        var block = level.getBlockEntity(ref.position());
        return block == null ? null : block.getCapability(ForgeCapabilities.ITEM_HANDLER, ref.side())
                .resolve().orElse(null);
    }

    private static boolean allows(BusinessLicenseData.Snapshot license, ItemStack stack) {
        ResourceLocation item = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return license.allows(item);
    }

    private static void fail(ServerLevel level, EntityMaid maid, BusinessLicenseData data, UUID license) {
        ACTIVE.remove(maid.getUUID());
        RETRY_AT.put(maid.getUUID(), level.getGameTime() + RETRY_COOLDOWN_TICKS);
        data.blocker(license,
                "message.maid_storage_manager_extension.business_license.no_container_space");
    }
}
