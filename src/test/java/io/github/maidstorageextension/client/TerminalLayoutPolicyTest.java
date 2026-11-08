package io.github.maidstorageextension.client;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TerminalLayoutPolicyTest {
    @Test
    void highGuiScalesKeepAUsableMapColumn() {
        for (int width : new int[]{320, 342, 427, 442}) {
            var columns = TerminalLayoutPolicy.horizontal(width, 112);
            assertTrue(columns.mapWidth() >= 48, "Map hidden at GUI width " + width);
            assertTrue(columns.rightWidth() >= 108);
        }
        assertEquals(80, TerminalLayoutPolicy.horizontal(320, 112).mapWidth());
        assertEquals(96, TerminalLayoutPolicy.horizontal(427, 112).mapWidth());
    }

    @Test
    void bothSidebarLayoutsFitEverySupportedWindowWidth() {
        for (int width = 320; width <= 2560; width++) {
            for (int leftWidth : new int[]{0, width < 640 ? 112 : 158}) {
                var columns = TerminalLayoutPolicy.horizontal(width, leftWidth);
                assertTrue(columns.mapWidth() >= 48, "Map hidden at " + width);
                assertEquals(columns.leftX() + leftWidth + 4, columns.mapX());
                assertEquals(columns.mapX() + columns.mapWidth() + 4, columns.rightX());
                assertEquals(width - 6, columns.rightX() + columns.rightWidth());
                assertTrue(columns.rightWidth() >= 108);
            }
        }
    }

    @Test
    void wideWindowsKeepThePublishedColumnSizes() {
        assertEquals(new TerminalLayoutPolicy.Columns(6, 158, 168, 220, 392, 242),
                TerminalLayoutPolicy.horizontal(640, 158));
        assertEquals(new TerminalLayoutPolicy.Columns(6, 158, 168, 490, 662, 292),
                TerminalLayoutPolicy.horizontal(960, 158));
    }

    @Test
    void unusablySmallWindowsKeepThePublishedFallbackThreshold() {
        assertEquals(48, TerminalLayoutPolicy.horizontal(288, 112).mapWidth());
        assertEquals(0, TerminalLayoutPolicy.horizontal(287, 112).mapWidth());
    }

    @Test
    void terminalUsesThePolicyAndKeepsItsButtonsInsideNarrowPanels() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/io/github/maidstorageextension/client/LogisticsTrackerScreen.java"));
        assertTrue(source.contains("TerminalLayoutPolicy.horizontal(width, leftWidth)"));
        assertFalse(source.contains("mapWidth < 96"));
        assertTrue(source.contains("Math.max(40, (layout.rightWidth - 14) / 2)"));
        assertTrue(source.contains("Math.max(44, (layout.rightWidth - 14) / 2)"));
        assertTrue(source.contains("Math.max(44, (transportWidth - 4) / 2)"));
    }
}
