package io.github.maidstorageextension.lang;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

class GuidanceTranslationTest {
    private static final Path LANG = Path.of(
            "src/main/resources/assets/maid_storage_manager_extension/lang");

    @Test
    void guidanceAndNewCourierStatesExistInBothLanguages() throws Exception {
        JsonObject chinese = read("zh_cn.json");
        JsonObject english = read("en_us.json");
        Set<String> keys = Set.of(
                "gui.maid_storage_manager_extension.terminal.guide",
                "gui.maid_storage_manager_extension.compass.scan_now",
                "gui.maid_storage_manager_extension.compass.inventory_never_detail",
                "gui.maid_storage_manager_extension.compass.preflight.station_blocked",
                "gui.maid_storage_manager_extension.business_license.purpose",
                "gui.maid_storage_manager_extension.business_license.checklist.terminal",
                "message.maid_storage_manager_extension.business_license.mode.container",
                "message.maid_storage_manager_extension.business_license.mode.landing",
                "message.maid_storage_manager_extension.business_license.mode.worker",
                "gui.maid_storage_manager_extension.courier.post_delivery_follow_owner",
                "gui.maid_storage_manager_extension.courier.post_delivery_stay_at_delivery",
                "gui.maid_storage_manager_extension.courier.post_delivery_return_to_default_warehouse",
                "gui.maid_storage_manager_extension.courier.phase.returning_to_warehouse_standby",
                "gui.maid_storage_manager_extension.courier.phase.waiting_for_warehouse_return",
                "gui.maid_storage_manager_extension.courier.phase.waiting_at_warehouse");

        for (String key : keys) {
            assertTrue(chinese.has(key), "Missing zh_cn key: " + key);
            assertTrue(english.has(key), "Missing en_us key: " + key);
        }
    }

    private static JsonObject read(String name) throws Exception {
        return JsonParser.parseString(Files.readString(
                LANG.resolve(name), StandardCharsets.UTF_8)).getAsJsonObject();
    }
}
