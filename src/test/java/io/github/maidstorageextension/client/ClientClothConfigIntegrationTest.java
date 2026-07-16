package io.github.maidstorageextension.client;

import com.github.tartaricacid.touhoulittlemaid.api.event.client.AddClothConfigEvent;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ClientClothConfigIntegrationTest {
    @Test
    void tlmGlobalConfigEventIsTheSupportedExtensionSeam() {
        assertDoesNotThrow(() -> AddClothConfigEvent.class.getDeclaredMethod("getRoot"));
        assertDoesNotThrow(() -> AddClothConfigEvent.class.getDeclaredMethod("getEntryBuilder"));
        assertDoesNotThrow(() -> ExtensionClothConfigScreen.class.getDeclaredMethod(
                "appendTo", ConfigBuilder.class, ConfigEntryBuilder.class,
                com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid.class));
        assertDoesNotThrow(() -> ClientClothConfigEvents.class.getDeclaredMethod(
                "addExtensionCategories", AddClothConfigEvent.class));
    }

    @Test
    void legacyNestedStorageManagerButtonMixinIsRemoved() throws Exception {
        String resource = "maid_storage_manager_extension.mixins.json";
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(stream, resource);
            String json = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            assertFalse(json.contains("StorageManagerConfigScreenMixin"));
        }
    }
}
