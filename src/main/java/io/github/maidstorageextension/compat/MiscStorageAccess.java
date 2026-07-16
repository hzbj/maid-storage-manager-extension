package io.github.maidstorageextension.compat;

import io.github.maidstorageextension.registry.ExtensionItems;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import studio.fantasyit.maid_storage_manager.storage.Target;
import studio.fantasyit.maid_storage_manager.util.StorageAccessUtil;

import java.util.ArrayList;
import java.util.List;

public final class MiscStorageAccess {
    private MiscStorageAccess() {
    }

    public static boolean isMiscStorage(Level level, Target target) {
        List<BlockPos> containerParts = new ArrayList<>();
        containerParts.add(target.getPos());
        StorageAccessUtil.checkNearByContainers(level, target.getPos(), containerParts::add);
        AABB search = AABB.ofSize(target.getPos().getCenter(), 5.0, 5.0, 5.0);
        return !level.getEntities(EntityTypeTest.forClass(ItemFrame.class), search, frame -> {
            if (!frame.getItem().is(ExtensionItems.MISC_STORAGE.get())) {
                return false;
            }
            if (target.side != null && target.side != frame.getDirection()) {
                return false;
            }
            BlockPos attachedTo = frame.blockPosition().relative(frame.getDirection(), -1);
            return containerParts.contains(attachedTo);
        }).isEmpty();
    }
}
