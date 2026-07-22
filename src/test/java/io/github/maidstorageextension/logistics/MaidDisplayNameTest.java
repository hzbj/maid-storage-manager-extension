package io.github.maidstorageextension.logistics;

import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class MaidDisplayNameTest {
    @Test
    void modelPackTranslationWrapperBecomesAClientTranslation() {
        String encoded = MaidDisplayName.encodeModelName(
                "touhou_little_maid_seihou:vivit",
                "{model.touhou_little_maid_seihou.vivit.name}");

        TranslatableContents contents = assertInstanceOf(TranslatableContents.class,
                LogisticsDisplayName.decode(encoded).getContents());
        assertEquals("model.touhou_little_maid_seihou.vivit.name", contents.getKey());
    }

    @Test
    void missingDeclaredNameFallsBackToTheModelIdTranslationKey() {
        String encoded = MaidDisplayName.encodeModelName(
                "touhou_little_maid:koakuma", "");

        TranslatableContents contents = assertInstanceOf(TranslatableContents.class,
                LogisticsDisplayName.decode(encoded).getContents());
        assertEquals("model.touhou_little_maid.koakuma.name", contents.getKey());
    }

    @Test
    void literalModelNameStaysLiteral() {
        assertEquals("VIVIT", LogisticsDisplayName.decode(
                MaidDisplayName.encodeModelName("example:vivit", "VIVIT")).getString());
    }
}
