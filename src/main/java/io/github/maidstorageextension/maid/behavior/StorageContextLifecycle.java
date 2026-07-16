package io.github.maidstorageextension.maid.behavior;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import studio.fantasyit.maid_storage_manager.storage.Target;
import studio.fantasyit.maid_storage_manager.storage.base.IFilterable;
import studio.fantasyit.maid_storage_manager.storage.base.IStorageContext;

/** Shared ordering seam for starting a storage context and evaluating its optional filter. */
final class StorageContextLifecycle {
    private StorageContextLifecycle() {
    }

    static boolean startAndAccept(IStorageContext context, EntityMaid maid, ServerLevel level,
                                  Target target, @Nullable ItemStack stackToInsert) {
        context.start(maid, level, target);
        if (stackToInsert != null && context instanceof IFilterable filter
                && !filter.isAvailable(stackToInsert)) {
            return false;
        }
        return true;
    }
}
