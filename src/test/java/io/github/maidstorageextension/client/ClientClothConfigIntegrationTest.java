package io.github.maidstorageextension.client;

import com.github.tartaricacid.touhoulittlemaid.api.event.client.AddClothConfigEvent;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screens.Screen;
import org.junit.jupiter.api.Test;
import studio.fantasyit.maid_storage_manager.maid.config.StorageManagerMaidConfigGui;

import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientClothConfigIntegrationTest {
    @Test
    void storageManagerGlobalCategoryOwnsTheExtensionSubcategory() throws Exception {
        assertDoesNotThrow(() -> AddClothConfigEvent.class.getDeclaredMethod("getRoot"));
        assertDoesNotThrow(() -> AddClothConfigEvent.class.getDeclaredMethod("getEntryBuilder"));
        assertDoesNotThrow(() -> ExtensionClothConfigScreen.class.getDeclaredMethod(
                "appendGlobalTo", ConfigBuilder.class, ConfigEntryBuilder.class,
                com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid.class));
        assertDoesNotThrow(() -> ClientClothConfigEvents.class.getDeclaredMethod(
                "addExtensionCategories", AddClothConfigEvent.class));

        String source = Files.readString(Path.of(
                "src/main/java/io/github/maidstorageextension/client/ExtensionClothConfigScreen.java"));
        assertTrue(source.contains("\"config.maid_storage_manager.title\""));
        assertTrue(source.contains("startSubCategory(Component.translatable(\n"
                + "                \"gui.maid_storage_manager_extension.config.category.extension\"))"));
        assertFalse(source.contains("getOrCreateCategory(\n"
                + "                Component.translatable(\"gui.maid_storage_manager_extension.config.category.scan\"))"));
    }

    @Test
    void storageManagerTaskPageOwnsPeriodicScanAndApprovals() {
        assertDoesNotThrow(() -> ExtensionClothConfigScreen.class.getDeclaredMethod(
                "create", Screen.class,
                com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid.class));
        assertDoesNotThrow(() -> ConfigScreenBridge.class.getDeclaredMethod("validate"));
        assertDoesNotThrow(() -> ConfigScreenBridge.class.getDeclaredMethod(
                "appendExtensionSettingsRow", StorageManagerMaidConfigGui.class));
    }

    @Test
    void storageManagerTaskPageMixinIsRegistered() throws Exception {
        String resource = "maid_storage_manager_extension.mixins.json";
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(stream, resource);
            String json = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(json.contains("StorageManagerConfigScreenMixin"));
        }
    }

    @Test
    void aPageDraftStartsWithEveryHiddenSettingUnchanged() throws Exception {
        io.github.maidstorageextension.data.ExtensionConfigData.Data data =
                new io.github.maidstorageextension.data.ExtensionConfigData.Data(
                        io.github.maidstorageextension.data.PeriodicScanInterval.MINUTES_30,
                        27, 104, 47, 29, false, true, true, true);
        Class<?> draftType = Class.forName(
                "io.github.maidstorageextension.client.ExtensionClothConfigScreen$Draft");
        Constructor<?> constructor = draftType.getDeclaredConstructor(
                io.github.maidstorageextension.data.ExtensionConfigData.Data.class);
        constructor.setAccessible(true);
        Object draft = constructor.newInstance(data);

        assertEquals(data.periodicScanInterval(), field(draftType, draft, "interval"));
        assertEquals(data.localScanRadius(), field(draftType, draft, "localScanRadius"));
        assertEquals(data.taskBellRange(), field(draftType, draft, "taskBellRange"));
        assertEquals(data.taskBellTravelTimeoutSeconds(),
                field(draftType, draft, "taskBellTravelTimeoutSeconds"));
        assertEquals(data.taskBellStaySeconds(), field(draftType, draft, "taskBellStaySeconds"));
        assertEquals(data.refreshFrameEffects(), field(draftType, draft, "refreshFrameEffects"));
        assertEquals(data.refreshOwnerNotification(),
                field(draftType, draft, "refreshOwnerNotification"));
        assertEquals(data.miscSortMatchNbt(), field(draftType, draft, "miscSortMatchNbt"));
    }

    private static Object field(Class<?> type, Object target, String name) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }
}
