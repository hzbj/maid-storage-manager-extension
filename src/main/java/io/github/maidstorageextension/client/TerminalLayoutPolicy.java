package io.github.maidstorageextension.client;

/**
 * Pure horizontal layout arithmetic for the communication terminal.
 *
 * <p>The map column must survive narrow windows (small screens, windowed mode or a high
 * GUI scale): instead of dropping the map, the right status panel shrinks first. The
 * math is extracted from {@code LogisticsTrackerScreen} so it stays unit-testable
 * without a client runtime.
 */
final class TerminalLayoutPolicy {
    static final int MARGIN = 6;
    static final int GAP = 4;
    static final int PREFERRED_MAP_WIDTH = 96;
    static final int MIN_MAP_WIDTH = 48;
    static final int MIN_RIGHT_WIDTH = 108;

    private TerminalLayoutPolicy() {
    }

    static Columns horizontal(int width, int leftWidth) {
        int mapWidth;
        int rightWidth;
        if (width < 640) {
            int available = width - MARGIN * 2 - leftWidth - GAP * 2;
            rightWidth = Math.max(MIN_RIGHT_WIDTH,
                    Math.min(214, available - PREFERRED_MAP_WIDTH));
            mapWidth = available - rightWidth;
        } else {
            rightWidth = Math.min(292, Math.max(242, width / 3));
            mapWidth = width - MARGIN * 2 - leftWidth - rightWidth - GAP * 2;
        }
        if (mapWidth < MIN_MAP_WIDTH) {
            mapWidth = 0;
            rightWidth = Math.max(180, width - MARGIN * 2 - leftWidth - GAP);
        }
        int leftX = MARGIN;
        int mapX = leftX + leftWidth + GAP;
        int rightX = mapWidth > 0 ? mapX + mapWidth + GAP : mapX;
        return new Columns(leftX, leftWidth, mapX, mapWidth, rightX, rightWidth);
    }

    record Columns(int leftX, int leftWidth, int mapX, int mapWidth,
                   int rightX, int rightWidth) {
    }
}
