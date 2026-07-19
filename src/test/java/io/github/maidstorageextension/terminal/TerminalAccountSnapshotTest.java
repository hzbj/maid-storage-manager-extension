package io.github.maidstorageextension.terminal;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TerminalAccountSnapshotTest {
    @Test
    void managerRefreshStatesRoundTripWithTheirMailbox() {
        ResourceLocation dimension = new ResourceLocation("minecraft", "overworld");
        MailboxKey key = new MailboxKey(dimension, new BlockPos(12, 64, -8));
        UUID manager = UUID.randomUUID();
        TerminalAccountSnapshot.Mailbox mailbox = new TerminalAccountSnapshot.Mailbox(
                dimension, key.position(), manager, "仓管", true, true, true,
                List.of(new TerminalAccountSnapshot.WarehouseManager(
                        manager, "仓管", "failed",
                        "gui.maid_storage_manager_extension.status.result.path_timeout")));
        TerminalAccountSnapshot.Snapshot source = new TerminalAccountSnapshot.Snapshot(
                true, false, "owner", UUID.randomUUID(), null, null, key,
                List.of(), List.of(mailbox), "");

        TerminalAccountSnapshot.Snapshot decoded = TerminalAccountSnapshot.fromTag(
                TerminalAccountSnapshot.toTag(source));

        assertEquals(key, decoded.selectedMailbox());
        assertEquals("failed", decoded.mailboxes().get(0).managers().get(0).status());
        assertEquals("gui.maid_storage_manager_extension.status.result.path_timeout",
                decoded.mailboxes().get(0).managers().get(0).detail());
    }
}
