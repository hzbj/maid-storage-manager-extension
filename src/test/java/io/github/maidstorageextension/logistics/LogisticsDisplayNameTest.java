package io.github.maidstorageextension.logistics;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class LogisticsDisplayNameTest {
    @Test
    void translatableMaidModelNameStaysTranslatableOnTheClient() {
        String encoded = LogisticsDisplayName.encode(
                Component.translatable("model.touhou_little_maid.koakuma.name"));

        Component decoded = LogisticsDisplayName.decode(encoded);

        TranslatableContents contents = assertInstanceOf(
                TranslatableContents.class, decoded.getContents());
        assertEquals("model.touhou_little_maid.koakuma.name", contents.getKey());
    }

    @Test
    void customMaidNameStaysLiteral() {
        assertEquals("仓库小助手",
                LogisticsDisplayName.decode(LogisticsDisplayName.encode(
                        Component.literal("仓库小助手"))).getString());
    }

    @Test
    void numberedMailboxNameRemainsClientLocalizable() {
        Component decoded = LogisticsDisplayName.decode("@mailbox:2");
        TranslatableContents contents = assertInstanceOf(
                TranslatableContents.class, decoded.getContents());
        assertEquals("gui.maid_storage_manager_extension.terminal.default_mailbox_name",
                contents.getKey());
        assertEquals("2", contents.getArgs()[0]);
    }
}
