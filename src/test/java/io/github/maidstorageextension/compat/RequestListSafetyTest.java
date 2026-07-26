package io.github.maidstorageextension.compat;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import studio.fantasyit.maid_storage_manager.registry.DataComponentRegistry;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class RequestListSafetyTest {
    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void missingJobUuidIsCreatedAsThePublicDataComponent() {
        ItemStack stack = new ItemStack(Items.PAPER);

        UUID created = RequestListSafety.ensureJobUuid(stack);

        assertNotNull(created);
        assertEquals(created, stack.get(DataComponentRegistry.REQUEST_WORK_UUID.get()));
    }

    @Test
    void validJobUuidIsPreservedIdempotently() {
        ItemStack stack = new ItemStack(Items.PAPER);
        UUID original = UUID.randomUUID();
        stack.set(DataComponentRegistry.REQUEST_WORK_UUID.get(), original);

        assertEquals(original, RequestListSafety.ensureJobUuid(stack));
        assertEquals(original, RequestListSafety.ensureJobUuid(stack));
    }
}
