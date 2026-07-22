package io.github.maidstorageextension.data;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BusinessLicenseDataTest {
    private static final ResourceLocation OVERWORLD = new ResourceLocation("minecraft", "overworld");

    @Test
    void emptyWhitelistRejectsAllAndEmptyBlacklistAllowsAll() {
        BusinessLicenseData data = new BusinessLicenseData();
        UUID id = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        ResourceLocation stone = new ResourceLocation("minecraft", "stone");
        assertFalse(data.create(id, owner, OVERWORLD, BlockPos.ZERO).allows(stone));
        assertTrue(data.setMode(id, owner, BusinessLicenseData.RuleMode.BLACKLIST));
        assertTrue(data.get(id).allows(stone));
    }

    @Test
    void containerRangeIsHorizontalAndBoundedToSixtyFourBlocks() {
        BusinessLicenseData data = new BusinessLicenseData();
        UUID id = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        data.create(id, owner, OVERWORLD, new BlockPos(10, 64, 10));
        assertTrue(data.toggleContainer(id, owner,
                new BusinessLicenseData.ContainerRef(new BlockPos(74, 200, 10), Direction.UP)));
        assertFalse(data.toggleContainer(id, owner,
                new BusinessLicenseData.ContainerRef(new BlockPos(75, 64, 10), Direction.UP)));
        assertEquals(1, data.get(id).containers().size());
    }

    @Test
    void completeLicenseJournalRoundTrips() {
        BusinessLicenseData data = new BusinessLicenseData();
        UUID id = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        UUID worker = UUID.randomUUID();
        ResourceLocation task = new ResourceLocation("test", "cook");
        data.create(id, owner, OVERWORLD, new BlockPos(5, 70, -3));
        data.rename(id, owner, "北区餐厅");
        assertEquals(BusinessLicenseData.BindWorkerResult.ADDED,
                data.toggleWorker(id, owner, worker, owner, task));
        data.setLanding(id, owner, new BlockPos(8, 70, -3));
        data.toggleContainer(id, owner,
                new BusinessLicenseData.ContainerRef(new BlockPos(7, 70, -3), Direction.NORTH));

        BusinessLicenseData.Snapshot decoded = BusinessLicenseData.load(data.save(new net.minecraft.nbt.CompoundTag()))
                .get(id);
        assertNotNull(decoded);
        assertEquals("北区餐厅", decoded.name());
        assertEquals(task, decoded.profession());
        assertEquals(java.util.List.of(worker), decoded.workers());
        assertEquals(new BlockPos(8, 70, -3), decoded.landingPos());
        assertEquals(Direction.NORTH, decoded.containers().get(0).side());
    }
}
