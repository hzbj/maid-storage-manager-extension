package io.github.maidstorageextension.scan;

import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import studio.fantasyit.maid_storage_manager.storage.ItemHandler.ItemHandlerStorage;
import studio.fantasyit.maid_storage_manager.storage.Target;

import java.util.Objects;

/**
 * Read-only capacity hint for the official item-handler storage backend.
 * Other storage backends deliberately report {@link Status#UNKNOWN}; their
 * latest viewed cache does not expose enough information to infer capacity.
 */
public final class ItemHandlerCapacityProbe {
    public enum Status {
        KNOWN,
        UNKNOWN
    }

    public record Result(Status status, int requestedCount, int insertableCount) {
        public Result {
            Objects.requireNonNull(status, "status");
            if (requestedCount < 0) throw new IllegalArgumentException("requestedCount must not be negative");
            if (insertableCount < 0 || insertableCount > requestedCount) {
                throw new IllegalArgumentException("insertableCount must be within the requested count");
            }
        }

        public static Result unknown(int requestedCount) {
            return new Result(Status.UNKNOWN, Math.max(0, requestedCount), 0);
        }

        public static Result known(int requestedCount, int insertableCount) {
            return new Result(Status.KNOWN, requestedCount, insertableCount);
        }

        public boolean isKnown() {
            return status == Status.KNOWN;
        }

        public boolean isFull() {
            return isKnown() && requestedCount > 0 && insertableCount == 0;
        }

        public boolean acceptsAny() {
            return isKnown() && insertableCount > 0;
        }
    }

    private ItemHandlerCapacityProbe() {
    }

    /**
     * Probes the capability selected by the target side without modifying it.
     */
    public static Result probe(ServerLevel level, Target target, ItemStack stack) {
        int requested = stack == null ? 0 : Math.max(0, stack.getCount());
        if (level == null || target == null || stack == null || stack.isEmpty()
                || !ItemHandlerStorage.TYPE.equals(target.getType())) {
            return Result.unknown(requested);
        }

        var pos = target.getPos();
        if (!level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) {
            return Result.unknown(requested);
        }
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity == null) return Result.unknown(requested);

        Direction side = target.getSide().orElse(null);
        LazyOptional<IItemHandler> capability = side == null
                ? blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER)
                : blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER, side);
        return capability.map(handler -> probe(handler, stack)).orElseGet(() -> Result.unknown(requested));
    }

    /** Package-level pure seam used by tests and by no world-specific fallback. */
    static Result probe(IItemHandler handler, ItemStack stack) {
        int requested = stack == null ? 0 : Math.max(0, stack.getCount());
        if (handler == null || stack == null || stack.isEmpty()) return Result.unknown(requested);

        ItemStack remainder = stack.copy();
        try {
            for (int slot = 0; slot < handler.getSlots() && !remainder.isEmpty(); slot++) {
                int before = remainder.getCount();
                ItemStack next = handler.insertItem(slot, remainder.copy(), true);
                if (next == null || (!next.isEmpty()
                        && (!ItemStack.isSameItemSameTags(stack, next) || next.getCount() > before))) {
                    return Result.unknown(requested);
                }
                remainder = next.copy();
            }
        } catch (RuntimeException ignored) {
            return Result.unknown(requested);
        }

        int remaining = remainder.isEmpty() ? 0 : remainder.getCount();
        return Result.known(requested, requested - remaining);
    }
}
