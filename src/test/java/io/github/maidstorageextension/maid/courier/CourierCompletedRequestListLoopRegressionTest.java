package io.github.maidstorageextension.maid.courier;

import io.github.maidstorageextension.logistics.NetworkWarehouseRequestFactory;
import io.github.maidstorageextension.migration.ItemDataMigration;
import io.github.maidstorageextension.network.NetworkWarehouseActionPacket;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import studio.fantasyit.maid_storage_manager.items.RequestListItem;
import studio.fantasyit.maid_storage_manager.registry.DataComponentRegistry;
import studio.fantasyit.maid_storage_manager.registry.ItemRegistry;
import studio.fantasyit.maid_storage_manager.storage.Target;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CourierCompletedRequestListLoopRegressionTest {
    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void returnedListStaysEditableButOnlyExplicitResubmissionEnablesDispatch() {
        ItemStack list = request(UUID.randomUUID());
        assertTrue(CourierService.canDispatchHeldRequestList(list));

        RequestListItem.setIgnore(list);
        assertTrue(CourierService.canEditHeldRequestList(list));
        assertFalse(CourierService.canDispatchHeldRequestList(list));

        submit(list, UUID.randomUUID());
        assertTrue(CourierService.canDispatchHeldRequestList(list));
        assertEquals(96, RequestListItem.getImmutableRequestData(list).list().getFirst().requested());
    }

    @Test
    void recoveredListPreservesRequestAndOriginalTargetsAndCanBeReused() {
        UUID courier = UUID.randomUUID();
        UUID recipient = UUID.randomUUID();
        ItemStack list = orphan(courier);
        UUID job = list.get(DataComponentRegistry.REQUEST_WORK_UUID.get());
        var items = list.get(DataComponentRegistry.REQUEST_ITEMS.get());
        Target target = new Target(ResourceLocation.fromNamespaceAndPath("minecraft", "chest"),
                new BlockPos(12, 64, 30));
        ItemDataMigration.update(list, root -> {
            root.getCompound(CourierService.META).put("originalStorage", target.toNbt());
            root.getCompound(CourierService.META).putUUID("originalEntity", recipient);
            root.putString("PlayerConfiguredValue", "preserved");
        });

        assertFalse(CourierService.canEditHeldRequestList(list));
        assertTrue(CourierService.restoreOrphanedRequestList(courier, list));
        assertTrue(CourierService.canEditHeldRequestList(list));
        assertFalse(CourierService.canDispatchHeldRequestList(list));
        assertEquals(target.toNbt(), list.get(DataComponentRegistry.REQUEST_STORAGE_BLOCK.get()).toNbt());
        assertEquals(recipient, list.get(DataComponentRegistry.REQUEST_STORAGE_ENTITY.get()));
        assertEquals(job, list.get(DataComponentRegistry.REQUEST_WORK_UUID.get()));
        assertEquals(items, list.get(DataComponentRegistry.REQUEST_ITEMS.get()));
        assertEquals("preserved", ItemDataMigration.read(list).getString("PlayerConfiguredValue"));
        ItemStack restored = list.copy();
        assertFalse(CourierService.restoreOrphanedRequestList(courier, list));
        assertTrue(ItemStack.isSameItemSameComponents(restored, list));
        submit(list, courier);
        assertTrue(CourierService.canDispatchHeldRequestList(list));
    }

    @Test
    void recoveryRemovesTemporaryDestinationWhenOriginalWasUnset() {
        UUID courier = UUID.randomUUID();
        ItemStack list = orphan(courier);
        assertTrue(CourierService.restoreOrphanedRequestList(courier, list));
        assertNull(list.get(DataComponentRegistry.REQUEST_STORAGE_ENTITY.get()));
        assertNull(list.get(DataComponentRegistry.REQUEST_STORAGE_BLOCK.get()));
        assertTrue(RequestListItem.isIgnored(list));
    }

    @Test
    void otherCouriersAndUnmarkedListsAreLeftUntouched() {
        UUID courier = UUID.randomUUID();
        ItemStack list = orphan(courier);
        ItemStack before = list.copy();
        assertFalse(CourierService.restoreOrphanedRequestList(UUID.randomUUID(), list));
        assertTrue(ItemStack.isSameItemSameComponents(before, list));
        assertFalse(CourierService.canDispatchHeldRequestList(list));
        ItemStack fresh = request(courier);
        assertFalse(CourierService.restoreOrphanedRequestList(courier, fresh));
        assertTrue(CourierService.canDispatchHeldRequestList(fresh));
        assertFalse(CourierService.canDispatchHeldRequestList(ItemStack.EMPTY));
        assertFalse(CourierService.canDispatchHeldRequestList(null));
        assertFalse(CourierService.canDispatchHeldRequestList(new ItemStack(Items.PAPER)));
    }

    @Test
    void allAutomaticEntrypointsUseDispatchFilterAndRecoveryIsIdleOnly() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/io/github/maidstorageextension/maid/courier/CourierService.java"));
        assertTrue(source.contains("EditableRequestList selectedList = findDispatchableRequestList(courier)"));
        assertTrue(source.contains("return findDispatchableRequestList(courier) != null"));
        assertTrue(source.contains("EditableRequestList selection = findDispatchableRequestList(courier)"));
        assertTrue(source.contains("if (!hasActiveTransaction(courier)) recoverOrphanedRequestLists(courier)"));
        String terminal = Files.readString(Path.of(
                "src/main/java/io/github/maidstorageextension/logistics/NetworkWarehouseService.java"));
        assertTrue(terminal.contains("CourierService.findEditableRequestList(courier)"));
    }

    private static ItemStack request(UUID courier) {
        ItemStack list = new ItemStack(ItemRegistry.REQUEST_LIST_ITEM.get());
        submit(list, courier);
        return list;
    }

    private static void submit(ItemStack list, UUID courier) {
        NetworkWarehouseRequestFactory.update(list, UUID.randomUUID(), courier, UUID.randomUUID(), 42,
                NetworkWarehouseActionPacket.DeliveryTarget.PLAYER,
                List.of(new NetworkWarehouseActionPacket.RequestedItem(new ItemStack(Items.OAK_LOG), 96)));
    }

    private static ItemStack orphan(UUID courier) {
        ItemStack list = request(courier);
        CompoundTag meta = new CompoundTag();
        meta.putUUID("courier", courier);
        ItemDataMigration.update(list, root -> root.put(CourierService.META, meta));
        list.set(DataComponentRegistry.REQUEST_STORAGE_ENTITY.get(), courier);
        return list;
    }
}
