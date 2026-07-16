package io.github.maidstorageextension;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourceAssetTest {
    private static final Path ASSETS = Path.of(
            "src/main/resources/assets/maid_storage_manager_extension");

    @Test
    void maintenanceDeviceUsesATransparentGeneratedTextureInsteadOfTheClock() throws IOException {
        String model = Files.readString(
                ASSETS.resolve("models/item/inventory_maintenance_device.json"),
                StandardCharsets.UTF_8);
        assertFalse(model.contains("minecraft:item/clock_00"));
        assertTrue(model.contains("maid_storage_manager_extension:item/inventory_maintenance_device"));

        BufferedImage texture = ImageIO.read(
                ASSETS.resolve("textures/item/inventory_maintenance_device.png").toFile());
        assertNotNull(texture);
        assertEquals(16, texture.getWidth());
        assertEquals(16, texture.getHeight());
        assertTrue(texture.getColorModel().hasAlpha());
        assertEquals(0, texture.getRGB(0, 0) >>> 24);
        assertEquals(0, texture.getRGB(15, 15) >>> 24);
    }

    @Test
    void taskBellBlockModelFitsTheCompactEightByEightBySixEnvelope() throws IOException {
        JsonObject model = JsonParser.parseString(Files.readString(
                ASSETS.resolve("models/block/task_bell.json"), StandardCharsets.UTF_8)).getAsJsonObject();
        JsonArray elements = model.getAsJsonArray("elements");
        for (int i = 0; i < elements.size(); i++) {
            JsonObject element = elements.get(i).getAsJsonObject();
            JsonArray from = element.getAsJsonArray("from");
            JsonArray to = element.getAsJsonArray("to");
            assertTrue(from.get(0).getAsDouble() >= 4.0D);
            assertTrue(from.get(2).getAsDouble() >= 4.0D);
            assertTrue(to.get(0).getAsDouble() <= 12.0D);
            assertTrue(to.get(1).getAsDouble() <= 6.0D);
            assertTrue(to.get(2).getAsDouble() <= 12.0D);
        }
    }

    @Test
    void taskBellGuiTransformKeepsTheCompactModelInsideTheInventorySlot() throws IOException {
        JsonObject model = JsonParser.parseString(Files.readString(
                ASSETS.resolve("models/block/task_bell.json"), StandardCharsets.UTF_8)).getAsJsonObject();
        JsonObject gui = model.getAsJsonObject("display").getAsJsonObject("gui");
        JsonArray scale = gui.getAsJsonArray("scale");
        JsonArray translation = gui.getAsJsonArray("translation");

        for (int axis = 0; axis < 3; axis++) {
            assertTrue(scale.get(axis).getAsDouble() <= 1.0D,
                    "Compact 8x8 bell must not be enlarged beyond a full GUI transform");
        }
        assertTrue(translation.get(1).getAsDouble() >= 0.5D,
                "The bell icon should be centered vertically instead of resting on the slot border");
    }

    @Test
    void taskBellModelsResolveAnExplicitBreakParticleTexture() throws IOException {
        JsonObject base = JsonParser.parseString(Files.readString(
                ASSETS.resolve("models/block/task_bell.json"), StandardCharsets.UTF_8)).getAsJsonObject();
        JsonObject pressed = JsonParser.parseString(Files.readString(
                ASSETS.resolve("models/block/task_bell_pressed.json"), StandardCharsets.UTF_8)).getAsJsonObject();

        assertEquals("minecraft:block/gold_block",
                base.getAsJsonObject("textures").get("particle").getAsString(),
                "Block break particles require the model's dedicated particle texture slot");
        assertEquals("maid_storage_manager_extension:block/task_bell",
                pressed.get("parent").getAsString(),
                "The pressed model must inherit the base model's particle texture");
    }

    @Test
    void courierWarehouseStationUsesACustomMailboxModelAndCompleteBlockResources()
            throws IOException {
        JsonObject model = JsonParser.parseString(Files.readString(
                ASSETS.resolve("models/block/courier_warehouse_station.json"),
                StandardCharsets.UTF_8)).getAsJsonObject();
        JsonObject textures = model.getAsJsonObject("textures");
        assertEquals("minecraft:block/spruce_planks",
                textures.get("particle").getAsString());
        assertEquals("minecraft:block/spruce_planks", textures.get("wood").getAsString());
        assertEquals("minecraft:block/dark_oak_planks", textures.get("dark_wood").getAsString());
        assertEquals("minecraft:block/gold_block", textures.get("gold").getAsString());
        for (String key : textures.keySet()) {
            assertTrue(textures.get(key).getAsString().startsWith("minecraft:block/"), key);
        }
        assertTrue(model.getAsJsonArray("elements").size() >= 8,
                "The vanilla-textured mailbox needs a post, body, stepped roof, door, and gold hardware");
        assertTrue(Files.exists(ASSETS.resolve("blockstates/courier_warehouse_station.json")));
        assertTrue(Files.exists(ASSETS.resolve("models/item/courier_warehouse_station.json")));
        assertTrue(Files.exists(ASSETS.resolve("textures/item/courier_warehouse_station.png")));
        assertTrue(Files.exists(Path.of(
                "src/main/resources/data/maid_storage_manager_extension/loot_tables/blocks/courier_warehouse_station.json")));
        assertTrue(Files.exists(Path.of(
                "src/main/resources/data/maid_storage_manager_extension/recipes/courier_warehouse_station.json")));
    }

    @Test
    void redesignedInventoryIconsAreTransparentSixteenPixelSprites() throws IOException {
        String[] names = {"courier_warehouse_station", "inventory_maintenance_device",
                "logistics_tracker", "task_bell", "misc_storage"};
        for (String name : names) {
            BufferedImage texture = ImageIO.read(
                    ASSETS.resolve("textures/item/" + name + ".png").toFile());
            assertNotNull(texture, name);
            assertEquals(16, texture.getWidth(), name);
            assertEquals(16, texture.getHeight(), name);
            assertTrue(texture.getColorModel().hasAlpha(), name);
            assertEquals(0, texture.getRGB(0, 0) >>> 24, name);
            assertCrispMinecraftPixels(texture, name);
            String model = Files.readString(ASSETS.resolve("models/item/" + name + ".json"),
                    StandardCharsets.UTF_8);
            assertTrue(model.contains("maid_storage_manager_extension:item/" + name), name);
        }
        String bellBlock = Files.readString(ASSETS.resolve("models/block/task_bell.json"),
                StandardCharsets.UTF_8);
        assertTrue(bellBlock.contains("minecraft:block/gold_block"),
                "Only the task bell item icon changes; the placed block model stays intact");
    }

    private static void assertCrispMinecraftPixels(BufferedImage texture, String name) {
        Set<Integer> opaqueColors = new HashSet<>();
        int opaquePixels = 0;
        for (int y = 0; y < texture.getHeight(); y++) {
            for (int x = 0; x < texture.getWidth(); x++) {
                int argb = texture.getRGB(x, y);
                int alpha = argb >>> 24;
                assertTrue(alpha == 0 || alpha == 255,
                        name + " must not contain antialiased alpha at " + x + "," + y);
                if (alpha == 255) {
                    opaquePixels++;
                    opaqueColors.add(argb);
                }
            }
        }
        assertTrue(opaquePixels >= 32 && opaquePixels <= 190,
                name + " needs a readable silhouette with transparent breathing room");
        assertTrue(opaqueColors.size() <= 32,
                name + " uses too many shades for a crisp vanilla-style 16px item sprite");
    }

    @Test
    void logisticsTrackerSkinsMatchBothRenderSurfaces() throws IOException {
        BufferedImage map = ImageIO.read(
                ASSETS.resolve("textures/gui/logistics_tracker_map.png").toFile());
        BufferedImage screen = ImageIO.read(
                ASSETS.resolve("textures/gui/logistics_tracker_screen.png").toFile());
        assertEquals(128, map.getWidth());
        assertEquals(128, map.getHeight());
        assertEquals(256, screen.getWidth());
        assertEquals(240, screen.getHeight());
    }
}
