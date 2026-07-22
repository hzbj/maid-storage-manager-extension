package io.github.maidstorageextension.terminal;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TerminalAccountDataTest {
    @Test
    void credentialsTokensRosterAndMailboxesSurviveSaveLoad() {
        TerminalAccountData data = new TerminalAccountData();
        TerminalAccountData.Account account = data.create("Scarlet_Devil", "tea-room-123");
        assertNotNull(account);
        assertSame(account, data.authenticate("scarlet_devil", "tea-room-123"));
        assertNull(data.authenticate("Scarlet_Devil", "wrong-pass"));

        UUID terminal = UUID.randomUUID();
        byte[] token = data.grant(account, terminal);
        assertTrue(data.verifyGrant(account.id(), terminal, token));
        UUID maid = UUID.randomUUID();
        assertEquals(TerminalAccountData.RegistrationResult.ADDED,
                data.register(account, maid));
        ResourceLocation overworld = new ResourceLocation("minecraft", "overworld");
        BlockPos mailbox = new BlockPos(12, 64, -4);
        UUID warehouse = UUID.randomUUID();
        assertTrue(data.registerMailbox(account, new TerminalAccountData.Mailbox(
                overworld, mailbox, warehouse, "Kitchen Stores")));

        TerminalAccountData decoded = TerminalAccountData.load(data.save(new CompoundTag()));
        TerminalAccountData.Account restored = decoded.byUsername("SCARLET_DEVIL");
        assertNotNull(restored);
        assertTrue(decoded.verifyGrant(restored.id(), terminal, token));
        assertTrue(decoded.belongsTo(restored, maid));
        assertEquals(maid, restored.selectedCourier());
        assertEquals(maid, restored.selectedDriver());
        assertEquals(1, restored.mailboxes().size());
        assertEquals(warehouse, restored.mailboxes().get(0).warehouse());
    }

    @Test
    void oneMaidCannotBelongToTwoAccountsAndPasswordResetRevokesDevices() {
        TerminalAccountData data = new TerminalAccountData();
        TerminalAccountData.Account first = data.create("first_account", "password-one");
        TerminalAccountData.Account second = data.create("second_account", "password-two");
        UUID maid = UUID.randomUUID();
        UUID terminal = UUID.randomUUID();
        assertEquals(TerminalAccountData.RegistrationResult.ADDED,
                data.register(first, maid));
        assertEquals(TerminalAccountData.RegistrationResult.OWNED_BY_OTHER_ACCOUNT,
                data.register(second, maid));
        byte[] token = data.grant(first, terminal);

        assertTrue(data.issueResetCode(first, "one-time-code-123"));
        assertFalse(data.verifyGrant(first.id(), terminal, token));
        assertNull(data.authenticate("first_account", "password-one"));
        assertSame(first, data.authenticateReset("first_account", "one-time-code-123"));
        assertNull(data.authenticateReset("first_account", "one-time-code-123"));
        assertTrue(first.passwordResetRequired());
        assertTrue(data.changePassword(first, "replacement-password"));
        assertFalse(first.passwordResetRequired());
        assertSame(first, data.authenticate("first_account", "replacement-password"));
        assertTrue(data.forceUnregister(maid));
        assertEquals(TerminalAccountData.RegistrationResult.ADDED,
                data.register(second, maid));
    }

    @Test
    void settingsUnbindRemovesMailboxAndAdvancesBothDefaultRoles() {
        TerminalAccountData data = new TerminalAccountData();
        TerminalAccountData.Account account = data.create("settings_account", "password-three");
        assertNotNull(account);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        assertEquals(TerminalAccountData.RegistrationResult.ADDED,
                data.register(account, first));
        assertEquals(TerminalAccountData.RegistrationResult.ADDED,
                data.register(account, second));

        ResourceLocation dimension = new ResourceLocation("minecraft", "overworld");
        BlockPos position = new BlockPos(4, 70, 9);
        assertTrue(data.registerMailbox(account, new TerminalAccountData.Mailbox(
                dimension, position, UUID.randomUUID(), "Main Warehouse")));

        assertTrue(data.unregister(account, first));
        assertFalse(data.belongsTo(account, first));
        assertEquals(second, account.selectedCourier());
        assertEquals(second, account.selectedDriver());
        assertTrue(data.unregisterMailbox(account, dimension, position));
        assertTrue(account.mailboxes().isEmpty());
    }

    @Test
    void unnamedMailboxesReceiveStableNumberedNamesAndCanBeRenamed() {
        TerminalAccountData data = new TerminalAccountData();
        TerminalAccountData.Account account = data.create("mailbox_names", "password-four");
        ResourceLocation dimension = new ResourceLocation("minecraft", "overworld");
        BlockPos first = new BlockPos(1, 64, 1);
        BlockPos second = new BlockPos(2, 64, 2);

        assertTrue(data.registerMailbox(account, new TerminalAccountData.Mailbox(
                dimension, first, UUID.randomUUID(), "")));
        assertTrue(data.registerMailbox(account, new TerminalAccountData.Mailbox(
                dimension, second, UUID.randomUUID(), "")));
        assertEquals("@mailbox:1", account.mailboxes().get(0).warehouseName());
        assertEquals("@mailbox:2", account.mailboxes().get(1).warehouseName());
        assertTrue(data.renameMailbox(account, dimension, first, "红魔馆收货处"));

        TerminalAccountData.Account restored = TerminalAccountData.load(
                data.save(new CompoundTag())).byUsername("mailbox_names");
        assertEquals("红魔馆收货处", restored.mailboxes().get(0).warehouseName());
        assertEquals("@mailbox:2", restored.mailboxes().get(1).warehouseName());
    }

    @Test
    void lastKnownMaidModelNameSurvivesOfflineReload() {
        TerminalAccountData data = new TerminalAccountData();
        TerminalAccountData.Account account = data.create("model_names", "password-five");
        UUID maid = UUID.randomUUID();
        String modelName = "@translation:model.touhou_little_maid_seihou.vivit.name";

        assertEquals(TerminalAccountData.RegistrationResult.ADDED,
                data.register(account, maid, modelName));
        TerminalAccountData.Account restored = TerminalAccountData.load(
                data.save(new CompoundTag())).byUsername("model_names");

        assertEquals(modelName, restored.maidName(maid));
    }
}
