package io.github.maidstorageextension.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.MapColor;

/** Client-only terrain tile used by the communication terminal without loading remote chunks. */
final class TerrainMapTexture implements AutoCloseable {
    static final int TEXTURE_SIZE = 256;
    private static final int UNKNOWN_A = nativeColor(24, 27, 26);
    private static final int UNKNOWN_B = nativeColor(29, 33, 31);
    private static final int VOID = nativeColor(15, 17, 17);
    private static final int REFRESH_INTERVAL_TICKS = 40;
    private static final int MOVING_REFRESH_INTERVAL_TICKS = 1;

    private DynamicTexture texture;
    private ResourceLocation location;
    private ResourceLocation dimension;
    private double centerX;
    private double centerZ;
    private double blocksPerScreenPixel;
    private int displayWidth;
    private int displayHeight;
    private long lastRefresh = Long.MIN_VALUE;
    private boolean dirty = true;
    private int knownPixels;

    View update(Minecraft minecraft, double requestedCenterX,
                double requestedCenterZ, double requestedBlocksPerPixel,
                int requestedDisplayWidth, int requestedDisplayHeight) {
        ClientLevel level = minecraft.level;
        if (level == null || requestedDisplayWidth <= 0 || requestedDisplayHeight <= 0) {
            return view();
        }
        ResourceLocation requestedDimension = level.dimension().location();
        boolean changed = dimension == null || !dimension.equals(requestedDimension)
                || Math.abs(centerX - requestedCenterX) > 0.01D
                || Math.abs(centerZ - requestedCenterZ) > 0.01D
                || Math.abs(blocksPerScreenPixel - requestedBlocksPerPixel) > 0.0001D
                || displayWidth != requestedDisplayWidth || displayHeight != requestedDisplayHeight;
        long gameTime = level.getGameTime();
        if (!changed && !dirty && gameTime - lastRefresh < REFRESH_INTERVAL_TICKS) {
            return view();
        }
        if ((changed || dirty) && lastRefresh != Long.MIN_VALUE
                && gameTime - lastRefresh < MOVING_REFRESH_INTERVAL_TICKS) {
            return view();
        }
        ensureTexture(minecraft);
        centerX = requestedCenterX;
        centerZ = requestedCenterZ;
        blocksPerScreenPixel = requestedBlocksPerPixel;
        displayWidth = requestedDisplayWidth;
        displayHeight = requestedDisplayHeight;
        dimension = requestedDimension;
        ExploredTerrainClientCache.activate(minecraft, level);
        rebuild(level);
        lastRefresh = gameTime;
        dirty = false;
        return view();
    }

    private View view() {
        return new View(location, centerX, centerZ,
                blocksPerScreenPixel <= 0.0D ? 1.0D : blocksPerScreenPixel, knownPixels);
    }

    void markDirty() {
        dirty = true;
    }

    private void ensureTexture(Minecraft minecraft) {
        if (texture != null) return;
        texture = new DynamicTexture(TEXTURE_SIZE, TEXTURE_SIZE, true);
        location = minecraft.getTextureManager().register(
                "maid_communication_terminal_terrain", texture);
    }

    private void rebuild(ClientLevel level) {
        NativeImage pixels = texture.getPixels();
        if (pixels == null) return;
        knownPixels = 0;
        double sampleX = blocksPerScreenPixel * displayWidth / TEXTURE_SIZE;
        double sampleZ = blocksPerScreenPixel * displayHeight / TEXTURE_SIZE;
        int[] previousHeights = new int[TEXTURE_SIZE];
        java.util.Arrays.fill(previousHeights, Integer.MIN_VALUE);

        for (int imageY = 0; imageY < TEXTURE_SIZE; imageY++) {
            int worldZ = (int) Math.floor(centerZ
                    + (imageY + 0.5D - TEXTURE_SIZE / 2.0D) * sampleZ);
            for (int imageX = 0; imageX < TEXTURE_SIZE; imageX++) {
                int worldX = (int) Math.floor(centerX
                        + (imageX + 0.5D - TEXTURE_SIZE / 2.0D) * sampleX);
                ExploredTerrainStore.Sample sample =
                        ExploredTerrainClientCache.sample(level, worldX, worldZ);
                if (!sample.known()) {
                    pixels.setPixelRGBA(imageX, imageY,
                            ((imageX >> 3) + (imageY >> 3) & 1) == 0 ? UNKNOWN_A : UNKNOWN_B);
                    previousHeights[imageX] = Integer.MIN_VALUE;
                    continue;
                }

                int height = sample.height();
                MapColor mapColor = MapColor.byId(sample.mapColorId());
                if (mapColor == MapColor.NONE) {
                    pixels.setPixelRGBA(imageX, imageY, VOID);
                    previousHeights[imageX] = height;
                    continue;
                }
                MapColor.Brightness brightness = brightness(
                        previousHeights[imageX], height, imageX, imageY, mapColor);
                pixels.setPixelRGBA(imageX, imageY, mapColor.calculateRGBColor(brightness));
                previousHeights[imageX] = height;
                knownPixels++;
            }
        }
        texture.upload();
    }

    private static MapColor.Brightness brightness(int previousHeight, int height,
                                                   int imageX, int imageY,
                                                   MapColor color) {
        if (color == MapColor.WATER) {
            return ((imageX + imageY) & 1) == 0
                    ? MapColor.Brightness.NORMAL : MapColor.Brightness.LOW;
        }
        if (previousHeight == Integer.MIN_VALUE) return MapColor.Brightness.NORMAL;
        int difference = height - previousHeight;
        if (difference >= 2) return MapColor.Brightness.HIGH;
        if (difference <= -2) return MapColor.Brightness.LOW;
        return MapColor.Brightness.NORMAL;
    }

    private static int nativeColor(int red, int green, int blue) {
        return 0xFF000000 | blue << 16 | green << 8 | red;
    }

    @Override
    public void close() {
        Minecraft minecraft = Minecraft.getInstance();
        if (location != null) minecraft.getTextureManager().release(location);
        texture = null;
        location = null;
    }

    record View(ResourceLocation texture, double centerX, double centerZ,
                double blocksPerScreenPixel, int knownPixels) {
    }
}
