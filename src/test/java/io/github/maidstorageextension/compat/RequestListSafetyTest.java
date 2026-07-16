package io.github.maidstorageextension.compat;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import studio.fantasyit.maid_storage_manager.items.RequestListItem;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequestListSafetyTest {
    @Test
    void missingJobUuidIsRepairedBeforeTheUpstreamEndEventReadsIt() {
        CompoundTag tag = new CompoundTag();

        assertTrue(RequestListSafety.ensureJobUuid(tag));
        assertTrue(tag.hasUUID(RequestListItem.TAG_UUID));
        assertDoesNotThrow(() -> tag.getUUID(RequestListItem.TAG_UUID));
    }

    @Test
    void malformedJobUuidIsRepairedBeforeTheUpstreamEndEventReadsIt() {
        CompoundTag tag = new CompoundTag();
        tag.putString(RequestListItem.TAG_UUID, "not-a-uuid");

        assertTrue(RequestListSafety.ensureJobUuid(tag));
        assertTrue(tag.hasUUID(RequestListItem.TAG_UUID));
        assertDoesNotThrow(() -> tag.getUUID(RequestListItem.TAG_UUID));
    }

    @Test
    void validJobUuidIsPreserved() {
        CompoundTag tag = new CompoundTag();
        UUID original = UUID.randomUUID();
        tag.putUUID(RequestListItem.TAG_UUID, original);

        assertFalse(RequestListSafety.ensureJobUuid(tag));

        assertEquals(original, tag.getUUID(RequestListItem.TAG_UUID));
    }
}
