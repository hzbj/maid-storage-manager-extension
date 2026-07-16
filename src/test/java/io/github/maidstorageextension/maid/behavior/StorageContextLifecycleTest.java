package io.github.maidstorageextension.maid.behavior;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.SharedConstants;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import studio.fantasyit.maid_storage_manager.storage.Target;
import studio.fantasyit.maid_storage_manager.storage.base.IFilterable;
import studio.fantasyit.maid_storage_manager.storage.base.IStorageInsertableContext;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StorageContextLifecycleTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void startsFilterableContextBeforeCheckingWhetherItAcceptsTheStack() {
        StartRequiredFilterContext context = new StartRequiredFilterContext();

        assertDoesNotThrow(() -> StorageContextLifecycle.startAndAccept(
                context, null, null, null, Items.APPLE.getDefaultInstance()));

        assertTrue(context.started);
        assertTrue(context.filterChecked);
    }

    private static final class StartRequiredFilterContext
            implements IStorageInsertableContext, IFilterable {
        private boolean started;
        private boolean filterChecked;

        @Override
        public void start(EntityMaid maid, ServerLevel level, Target target) {
            started = true;
        }

        @Override
        public boolean isAvailable(ItemStack itemStack) {
            if (!started) {
                throw new NullPointerException("filter has not been initialized by context.start");
            }
            filterChecked = true;
            return true;
        }

        @Override
        public boolean isWhitelist() {
            return true;
        }

        @Override
        public ItemStack insert(ItemStack item) {
            return ItemStack.EMPTY;
        }
    }
}
