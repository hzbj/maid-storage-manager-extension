package io.github.maidstorageextension.item;

import org.junit.jupiter.api.Test;
import studio.fantasyit.maid_storage_manager.event.RenderHandMapLikeEvent;

import static org.junit.jupiter.api.Assertions.assertFalse;

class LogisticsTrackerItemContractTest {
    @Test
    void compassDoesNotRenderAFirstPersonHandheldMap() {
        assertFalse(RenderHandMapLikeEvent.MapLikeRenderItem.class
                .isAssignableFrom(LogisticsTrackerItem.class));
    }
}
