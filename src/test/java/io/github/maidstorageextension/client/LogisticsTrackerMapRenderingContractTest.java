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
    private static final Path TERRAIN = Path.of(
            "src/main/java/io/github/maidstorageextension/client/TerrainMapTexture.java");
    private static final Path CACHE = Path.of(
            "src/main/java/io/github/maidstorageextension/client/ExploredTerrainClientCache.java");

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

    @Test
    void terrainSeenWhileWalkingIsCachedBeyondTheCurrentViewDistance() throws Exception {
        String terrain = Files.readString(TERRAIN, StandardCharsets.UTF_8);
        String cache = Files.readString(CACHE, StandardCharsets.UTF_8);

        assertTrue(terrain.contains("ExploredTerrainClientCache.sample("),
                "Map rendering must fall back to explored terrain when a chunk is no longer loaded");
        assertTrue(cache.contains("ChunkEvent.Load")
                        && cache.contains("ClientTickEvent")
                        && cache.contains("LoggingOut"),
                "The client must observe walked chunks continuously and flush them on logout");
        assertTrue(cache.contains("ExploredTerrainStore.load(")
                        && cache.contains(".save("),
                "Explored terrain must survive closing the map and restarting the client");
    }
}
