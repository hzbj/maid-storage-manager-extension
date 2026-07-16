package io.github.maidstorageextension.maid.task;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.maidstorageextension.data.CourierData;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CourierTaskTranslationContractTest {
    @Test
    void enableConditionReturnsOnlyTheSuffixExpectedByTheUpstreamGui() {
        assertEquals("transport", new CourierTask().getEnableConditionDesc(null).get(0).getFirst());
    }

    @Test
    void enableConditionExplicitlyRequiresBothCourierItems() {
        JsonObject chinese = translations("zh_cn");
        JsonObject english = translations("en_us");
        String key = "task.maid_storage_manager_extension.courier.enable_condition.transport";

        assertTrue(chinese.get(key).getAsString().contains("同时"));
        assertTrue(english.get(key).getAsString().contains("both"));
    }

    @Test
    void everyCourierPhaseShownByTheLogisticsTrackerHasChineseAndEnglishText() {
        for (String locale : new String[]{"zh_cn", "en_us"}) {
            JsonObject translations = translations(locale);
            for (CourierData.Phase phase : CourierData.Phase.values()) {
                String key = "gui.maid_storage_manager_extension.courier.phase."
                        + phase.name().toLowerCase(Locale.ROOT);
                assertTrue(translations.has(key), locale + ":" + key);
            }
        }
    }

    private JsonObject translations(String locale) {
        String resource = "assets/maid_storage_manager_extension/lang/" + locale + ".json";
        var stream = getClass().getClassLoader().getResourceAsStream(resource);
        assertNotNull(stream, resource);
        return JsonParser.parseReader(new InputStreamReader(
                stream, StandardCharsets.UTF_8)).getAsJsonObject();
    }
}
