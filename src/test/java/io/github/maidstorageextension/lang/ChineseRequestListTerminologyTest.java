package io.github.maidstorageextension.lang;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ChineseRequestListTerminologyTest {
    private static final Path ROOT = Path.of("").toAbsolutePath();

    @Test
    void activeChineseResourcesUseRequestListTerminology() throws IOException {
        Path languageFile = ROOT.resolve(
                "src/main/resources/assets/maid_storage_manager_extension/lang/zh_cn.json");
        String language = Files.readString(languageFile, StandardCharsets.UTF_8);
        JsonObject json = JsonParser.parseString(language).getAsJsonObject();

        assertEquals("需要女仆手持请求列表",
                json.get("gui.maid_storage_manager_extension.compass.request_list_required")
                        .getAsString());
        assertFalse(language.contains("需求清单"));
    }

    @Test
    void playerDocumentationUsesTheSameItemName() throws IOException {
        for (Path relative : List.of(
                Path.of("README.md"),
                Path.of("CHANGELOG.md"),
                Path.of("docs/gameplay/01_NETWORK_WAREHOUSE.md"))) {
            assertFalse(Files.readString(ROOT.resolve(relative), StandardCharsets.UTF_8)
                    .contains("需求清单"), relative + " contains the obsolete term");
        }
    }
}
