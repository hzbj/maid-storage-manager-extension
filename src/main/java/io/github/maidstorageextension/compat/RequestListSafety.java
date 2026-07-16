package io.github.maidstorageextension.compat;

import net.minecraft.nbt.CompoundTag;
import studio.fantasyit.maid_storage_manager.items.RequestListItem;

import java.util.UUID;

/** Repairs legacy or malformed request-list bookkeeping before upstream reads it unconditionally. */
public final class RequestListSafety {
    private RequestListSafety() {
    }

    /**
     * Upstream 1.15.6 calls {@link CompoundTag#getUUID(String)} while ending every request job.
     * That method throws when the tag is absent or has the wrong NBT type, so assign a fresh
     * correlation id only for the malformed cases and preserve every valid existing id.
     *
     * @return {@code true} when the tag had to be repaired
     */
    public static boolean ensureJobUuid(CompoundTag tag) {
        if (tag.hasUUID(RequestListItem.TAG_UUID)) return false;
        tag.putUUID(RequestListItem.TAG_UUID, UUID.randomUUID());
        return true;
    }
}
