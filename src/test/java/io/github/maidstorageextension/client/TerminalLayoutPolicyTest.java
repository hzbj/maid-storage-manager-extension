package io.github.maidstorageextension.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TerminalLayoutPolicyTest {

    private static final int TAB_WIDTH = 112;
    private static final int MAID_LOGISTICS_WIDTH = 0;

    @Test
    void narrowWindowsThatAlreadyFitKeepTheHistoricalColumns() {
        // 1920x1080 with auto GUI scale 4 renders 480 GUI pixels wide.
        TerminalLayoutPolicy.Columns columns =
                TerminalLayoutPolicy.horizontal(480, TAB_WIDTH);
        assertEquals(TAB_WIDTH, columns.leftWidth());
        assertEquals(214, columns.rightWidth());
        assertEquals(134, columns.mapWidth());
    }

    @Test
    void laptopAutoGuiScaleWindowsKeepTheMap() {
        // 1280x720 fullscreen with auto GUI scale 3 renders 426 GUI pixels wide;
        // the map used to be dropped entirely at this width.
        TerminalLayoutPolicy.Columns columns =
                TerminalLayoutPolicy.horizontal(426, TAB_WIDTH);
        assertEquals(TAB_WIDTH, columns.leftWidth());
        assertEquals(TerminalLayoutPolicy.PREFERRED_MAP_WIDTH, columns.mapWidth());
        assertEquals(198, columns.rightWidth());
        assertEquals(426 - TerminalLayoutPolicy.MARGIN,
                columns.rightX() + columns.rightWidth());
    }

    @Test
    void forcedHighGuiScaleWindowsStillShowAMap() {
        // 1366x768 with manual GUI scale 4 renders 341 GUI pixels wide.
        TerminalLayoutPolicy.Columns columns =
                TerminalLayoutPolicy.horizontal(341, TAB_WIDTH);
        assertEquals(TerminalLayoutPolicy.PREFERRED_MAP_WIDTH, columns.mapWidth());
        assertEquals(113, columns.rightWidth());
        assertEquals(341 - TerminalLayoutPolicy.MARGIN,
                columns.rightX() + columns.rightWidth());
    }

    @Test
    void minimumMinecraftWindowsKeepAUsableMap() {
        // 320 GUI pixels is the narrowest window Minecraft renders at its size floor.
        TerminalLayoutPolicy.Columns columns =
                TerminalLayoutPolicy.horizontal(320, TAB_WIDTH);
        assertEquals(80, columns.mapWidth());
        assertEquals(TerminalLayoutPolicy.MIN_RIGHT_WIDTH, columns.rightWidth());
    }

    @Test
    void maidLogisticsTabKeepsTheMapAtForcedHighGuiScale() {
        // The maid logistics tab has no roster column, so the map survives even
        // narrower windows than the transport tab.
        TerminalLayoutPolicy.Columns columns =
                TerminalLayoutPolicy.horizontal(341, MAID_LOGISTICS_WIDTH);
        assertTrue(columns.mapWidth() >= TerminalLayoutPolicy.PREFERRED_MAP_WIDTH);
        assertEquals(214, columns.rightWidth());
        assertEquals(341 - TerminalLayoutPolicy.MARGIN,
                columns.rightX() + columns.rightWidth());
    }

    @Test
    void maidLogisticsTabKeepsTheMapAtTheWindowFloor() {
        TerminalLayoutPolicy.Columns columns =
                TerminalLayoutPolicy.horizontal(320, MAID_LOGISTICS_WIDTH);
        assertEquals(TerminalLayoutPolicy.PREFERRED_MAP_WIDTH, columns.mapWidth());
        assertEquals(204, columns.rightWidth());
    }

    @Test
    void pathologicalWindowsFallBackToDroppingTheMap() {
        TerminalLayoutPolicy.Columns columns =
                TerminalLayoutPolicy.horizontal(260, TAB_WIDTH);
        assertEquals(0, columns.mapWidth());
        assertEquals(180, columns.rightWidth());
    }

    @Test
    void wideWindowsNeverCollapse() {
        for (int width = 640; width <= 1920; width += 73) {
            TerminalLayoutPolicy.Columns columns =
                    TerminalLayoutPolicy.horizontal(width, 158);
            assertTrue(columns.mapWidth() >= 220, "map column collapsed at width " + width);
            assertEquals(width - TerminalLayoutPolicy.MARGIN,
                    columns.rightX() + columns.rightWidth(), "width " + width);
        }
    }

    @Test
    void everyRenderableWidthFitsInsideTheWindow() {
        for (int width = 320; width <= 1280; width++) {
            TerminalLayoutPolicy.Columns columns =
                    TerminalLayoutPolicy.horizontal(width, TAB_WIDTH);
            assertTrue(columns.leftX() >= 0, "width " + width);
            assertTrue(columns.rightX() + columns.rightWidth()
                            <= width - TerminalLayoutPolicy.MARGIN, "width " + width);
            if (columns.mapWidth() > 0) {
                assertTrue(columns.mapWidth() >= TerminalLayoutPolicy.MIN_MAP_WIDTH,
                        "map column too small at width " + width);
                assertTrue(columns.mapX() + columns.mapWidth() <= columns.rightX(),
                        "map column overlaps the right panel at width " + width);
            }
        }
    }
}
