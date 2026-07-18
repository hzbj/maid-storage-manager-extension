package io.github.maidstorageextension.client;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogisticsTrackerMapRenderingContractTest {
    private static final Path SCREEN = Path.of(
            "src/main/java/io/github/maidstorageextension/client/LogisticsTrackerScreen.java");

    @Test
    void terrainUsesTheWholeTextureWhenScaledToTheMapViewport() throws Exception {
        String source = Files.readString(SCREEN, StandardCharsets.UTF_8)
                .replace("\r\n", "\n");

        assertTrue(source.contains(
                "graphics.blit(terrainView.texture(), x, y, w, h, 0.0F, 0.0F,\n"
                        + "                    TerrainMapTexture.TEXTURE_SIZE, "
                        + "TerrainMapTexture.TEXTURE_SIZE,"));
        assertFalse(source.contains(
                "graphics.blit(terrainView.texture(), x, y, 0.0F, 0.0F, w, h,"));
    }
}
