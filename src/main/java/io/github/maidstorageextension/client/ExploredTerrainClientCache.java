package io.github.maidstorageextension.client;

import com.mojang.logging.LogUtils;
import io.github.maidstorageextension.MaidStorageManagerExtension;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Set;

/** Records client-visible terrain so the terminal map can revisit explored chunks. */
@Mod.EventBusSubscriber(modid = MaidStorageManagerExtension.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ExploredTerrainClientCache {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int SAVE_INTERVAL_TICKS = 1_200;
    private static final Set<Long> CAPTURED_CHUNKS = new HashSet<>();
    private static final BlockPos.MutableBlockPos CURSOR = new BlockPos.MutableBlockPos();

    private static ExploredTerrainStore store = new ExploredTerrainStore();
    private static String scope;
    private static Path file;
    private static boolean dirty;
    private static int ticksSinceSave;

    private ExploredTerrainClientCache() {
    }

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ClientLevel level
                && event.getChunk() instanceof LevelChunk chunk && !chunk.isEmpty()) {
            activate(Minecraft.getInstance(), level);
            capture(level, chunk);
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || minecraft.player == null) return;
        activate(minecraft, level);

        LevelChunk walkedChunk = level.getChunkSource().getChunk(
                minecraft.player.chunkPosition().x, minecraft.player.chunkPosition().z,
                ChunkStatus.FULL, false);
        if (walkedChunk != null && !walkedChunk.isEmpty()) capture(level, walkedChunk);
        if (dirty && ++ticksSinceSave >= SAVE_INTERVAL_TICKS) save();
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        save();
        scope = null;
        file = null;
        store = new ExploredTerrainStore();
        CAPTURED_CHUNKS.clear();
        dirty = false;
        ticksSinceSave = 0;
    }

    static void activate(Minecraft minecraft, ClientLevel level) {
        String requestedScope = scopeKey(minecraft, level.dimension().location());
        if (requestedScope.equals(scope)) return;
        save();
        scope = requestedScope;
        file = cacheFile(minecraft, requestedScope);
        CAPTURED_CHUNKS.clear();
        try {
            store = ExploredTerrainStore.load(file);
        } catch (IOException exception) {
            LOGGER.warn("Could not read explored terrain cache {}; starting empty", file,
                    exception);
            store = new ExploredTerrainStore();
        }
        dirty = false;
        ticksSinceSave = 0;
    }

    static ExploredTerrainStore.Sample sample(ClientLevel level, int worldX, int worldZ) {
        int chunkX = Math.floorDiv(worldX, ExploredTerrainStore.CHUNK_WIDTH);
        int chunkZ = Math.floorDiv(worldZ, ExploredTerrainStore.CHUNK_WIDTH);
        LevelChunk loaded = level.getChunkSource().getChunk(
                chunkX, chunkZ, ChunkStatus.FULL, false);
        if (loaded != null && !loaded.isEmpty()) capture(level, loaded);
        return store.sample(worldX, worldZ);
    }

    private static void capture(ClientLevel level, LevelChunk chunk) {
        int chunkX = chunk.getPos().x;
        int chunkZ = chunk.getPos().z;
        long chunkKey = Integer.toUnsignedLong(chunkX) | (long) chunkZ << 32;
        if (!CAPTURED_CHUNKS.add(chunkKey)) return;

        int[] samples = new int[ExploredTerrainStore.SAMPLES_PER_CHUNK];
        int startX = chunk.getPos().getMinBlockX();
        int startZ = chunk.getPos().getMinBlockZ();
        for (int localZ = 0; localZ < ExploredTerrainStore.CHUNK_WIDTH; localZ++) {
            int worldZ = startZ + localZ;
            for (int localX = 0; localX < ExploredTerrainStore.CHUNK_WIDTH; localX++) {
                int worldX = startX + localX;
                int height = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, worldX, worldZ);
                MapColor color = surfaceColor(level, chunk, worldX, worldZ, height);
                samples[localZ * ExploredTerrainStore.CHUNK_WIDTH + localX] =
                        ExploredTerrainStore.pack(height, color.id);
            }
        }
        store.putChunk(chunkX, chunkZ, samples);
        dirty = true;
    }

    private static MapColor surfaceColor(ClientLevel level, LevelChunk chunk,
                                         int worldX, int worldZ, int surfaceY) {
        int minimum = level.getMinBuildHeight();
        int y = Math.max(minimum, surfaceY);
        for (int depth = 0; y >= minimum && depth < 32; depth++, y--) {
            CURSOR.set(worldX, y, worldZ);
            MapColor color = chunk.getBlockState(CURSOR).getMapColor(level, CURSOR);
            if (color != MapColor.NONE) return color;
        }
        return MapColor.NONE;
    }

    private static void save() {
        if (!dirty || file == null) return;
        try {
            store.save(file);
            dirty = false;
            ticksSinceSave = 0;
        } catch (IOException exception) {
            LOGGER.warn("Could not save explored terrain cache {}", file, exception);
            ticksSinceSave = 0;
        }
    }

    private static Path cacheFile(Minecraft minecraft, String requestedScope) {
        return minecraft.gameDirectory.toPath()
                .resolve(MaidStorageManagerExtension.MOD_ID)
                .resolve("terrain_cache")
                .resolve(sha256(requestedScope) + ".bin");
    }

    private static String scopeKey(Minecraft minecraft, ResourceLocation dimension) {
        String world;
        if (minecraft.hasSingleplayerServer() && minecraft.getSingleplayerServer() != null) {
            world = "singleplayer:" + minecraft.getSingleplayerServer()
                    .getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        } else {
            ServerData server = minecraft.getCurrentServer();
            if (server != null) {
                world = "remote:" + server.ip;
            } else if (minecraft.getConnection() != null) {
                world = "remote:" + minecraft.getConnection().getConnection().getRemoteAddress();
            } else {
                world = "remote:unknown";
            }
        }
        return minecraft.getUser().getUuid() + "|" + world + "|" + dimension;
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
