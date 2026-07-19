package io.github.maidstorageextension.terminal;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MailboxWarehouseDataTest {
    private static final ResourceLocation OVERWORLD = new ResourceLocation("minecraft", "overworld");

    @Test
    void oneMailboxAcceptsManyManagersSharingOneFrame() {
        MailboxWarehouseData data = new MailboxWarehouseData();
        MailboxKey mailbox = new MailboxKey(OVERWORLD, new BlockPos(10, 64, 20));
        var frame = new MailboxWarehouseData.FrameBinding(
                OVERWORLD, new BlockPos(11, 65, 20), UUID.randomUUID());
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        assertEquals(MailboxWarehouseData.BindResult.ADDED,
                data.bind(mailbox, first, frame));
        assertEquals(MailboxWarehouseData.BindResult.ADDED,
                data.bind(mailbox, second, frame));
        assertEquals(2, data.warehouse(mailbox).managers().size());
    }

    @Test
    void managerCannotBelongToTwoMailboxesAndFramesCannotDiverge() {
        MailboxWarehouseData data = new MailboxWarehouseData();
        MailboxKey firstMailbox = new MailboxKey(OVERWORLD, BlockPos.ZERO);
        MailboxKey secondMailbox = new MailboxKey(OVERWORLD, new BlockPos(32, 64, 0));
        UUID manager = UUID.randomUUID();
        var firstFrame = new MailboxWarehouseData.FrameBinding(
                OVERWORLD, new BlockPos(1, 64, 0), UUID.randomUUID());
        var secondFrame = new MailboxWarehouseData.FrameBinding(
                OVERWORLD, new BlockPos(2, 64, 0), UUID.randomUUID());

        assertEquals(MailboxWarehouseData.BindResult.ADDED,
                data.bind(firstMailbox, manager, firstFrame));
        assertEquals(MailboxWarehouseData.BindResult.BOUND_ELSEWHERE,
                data.bind(secondMailbox, manager, firstFrame));
        assertEquals(MailboxWarehouseData.BindResult.FRAME_MISMATCH,
                data.bind(firstMailbox, UUID.randomUUID(), secondFrame));
    }

    @Test
    void finalUnbindRevokesNetworkListWithoutDeletingFrameIdentity() {
        MailboxWarehouseData data = new MailboxWarehouseData();
        MailboxKey mailbox = new MailboxKey(OVERWORLD, BlockPos.ZERO);
        UUID manager = UUID.randomUUID();
        var frame = new MailboxWarehouseData.FrameBinding(
                OVERWORLD, new BlockPos(1, 64, 0), UUID.randomUUID());
        UUID list = UUID.randomUUID();
        data.bind(mailbox, manager, frame);
        assertTrue(data.publish(manager, frame, list, 200L));

        assertTrue(data.unbind(mailbox, manager));
        var snapshot = data.warehouse(mailbox);
        assertNotNull(snapshot.frame());
        assertNull(snapshot.inventoryList());
        assertFalse(snapshot.hasManagers());
    }

    @Test
    void saveRoundTripKeepsMembershipAndCanonicalList() {
        MailboxWarehouseData data = new MailboxWarehouseData();
        MailboxKey mailbox = new MailboxKey(OVERWORLD, new BlockPos(-20, 70, 8));
        UUID manager = UUID.randomUUID();
        var frame = new MailboxWarehouseData.FrameBinding(
                OVERWORLD, new BlockPos(-19, 70, 8), UUID.randomUUID());
        UUID list = UUID.randomUUID();
        data.bind(mailbox, manager, frame);
        data.publish(manager, frame, list, 400L);

        MailboxWarehouseData decoded = MailboxWarehouseData.load(data.save(new net.minecraft.nbt.CompoundTag()));
        var snapshot = decoded.warehouse(mailbox);
        assertEquals(mailbox, decoded.mailboxOf(manager));
        assertEquals(frame, snapshot.frame());
        assertEquals(list, snapshot.inventoryList());
        assertEquals(400L, snapshot.publishedGameTime());
    }
}
