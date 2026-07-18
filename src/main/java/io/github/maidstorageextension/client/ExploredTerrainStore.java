package io.github.maidstorageextension.client;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/** Compact persistent store for surface samples from chunks the client has actually seen. */
final class ExploredTerrainStore {
    static final int CHUNK_WIDTH = 16;
    static final int SAMPLES_PER_CHUNK = CHUNK_WIDTH * CHUNK_WIDTH;
    private static final int MAGIC = 0x4D534D54;
    private static final int FORMAT_VERSION = 1;
    private static final int MAX_CHUNKS = 1_000_000;
    private static final int MAX_MAP_COLOR_ID = 63;

    private final Map<Long, int[]> chunks = new HashMap<>();

    void putChunk(int chunkX, int chunkZ, int[] samples) {
        if (samples.length != SAMPLES_PER_CHUNK) {
            throw new IllegalArgumentException("A terrain chunk must contain 256 samples");
        }
        chunks.put(chunkKey(chunkX, chunkZ), samples.clone());
    }

    Sample sample(int worldX, int worldZ) {
        int[] chunk = chunks.get(chunkKey(Math.floorDiv(worldX, CHUNK_WIDTH),
                Math.floorDiv(worldZ, CHUNK_WIDTH)));
        if (chunk == null) return Sample.UNKNOWN;
        int localX = Math.floorMod(worldX, CHUNK_WIDTH);
        int localZ = Math.floorMod(worldZ, CHUNK_WIDTH);
        int packed = chunk[localZ * CHUNK_WIDTH + localX];
        return new Sample(true, unpackHeight(packed), packed & 0xFF);
    }

    int chunkCount() {
        return chunks.size();
    }

    void save(Path target) throws IOException {
        Path parent = target.getParent();
        if (parent != null) Files.createDirectories(parent);
        Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
        try (DataOutputStream output = new DataOutputStream(new BufferedOutputStream(
                Files.newOutputStream(temporary)))) {
            output.writeInt(MAGIC);
            output.writeInt(FORMAT_VERSION);
            output.writeInt(chunks.size());
            // Stable ordering makes cache files reproducible and easier to diagnose.
            for (Map.Entry<Long, int[]> entry : new TreeMap<>(chunks).entrySet()) {
                output.writeLong(entry.getKey());
                for (int sample : entry.getValue()) output.writeInt(sample);
            }
        }
        try {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    static ExploredTerrainStore load(Path source) throws IOException {
        ExploredTerrainStore store = new ExploredTerrainStore();
        if (!Files.isRegularFile(source)) return store;
        try (DataInputStream input = new DataInputStream(new BufferedInputStream(
                Files.newInputStream(source)))) {
            if (input.readInt() != MAGIC) throw new IOException("Unknown terrain cache file");
            int version = input.readInt();
            if (version != FORMAT_VERSION) {
                throw new IOException("Unsupported terrain cache version " + version);
            }
            int count = input.readInt();
            if (count < 0 || count > MAX_CHUNKS) {
                throw new IOException("Invalid terrain cache chunk count " + count);
            }
            for (int chunkIndex = 0; chunkIndex < count; chunkIndex++) {
                long key = input.readLong();
                int[] samples = new int[SAMPLES_PER_CHUNK];
                for (int index = 0; index < samples.length; index++) {
                    int sample = input.readInt();
                    if ((sample & 0xFF) > MAX_MAP_COLOR_ID) {
                        throw new IOException("Invalid terrain map color");
                    }
                    samples[index] = sample;
                }
                store.chunks.put(key, samples);
            }
        } catch (EOFException exception) {
            throw new IOException("Truncated terrain cache file", exception);
        }
        return store;
    }

    static int pack(int height, int mapColorId) {
        if (mapColorId < 0 || mapColorId > MAX_MAP_COLOR_ID) {
            throw new IllegalArgumentException("Invalid terrain map color " + mapColorId);
        }
        return (height & 0x00FF_FFFF) << 8 | mapColorId & 0xFF;
    }

    private static int unpackHeight(int packed) {
        return packed >> 8;
    }

    private static long chunkKey(int chunkX, int chunkZ) {
        return Integer.toUnsignedLong(chunkX) | (long) chunkZ << 32;
    }

    record Sample(boolean known, int height, int mapColorId) {
        private static final Sample UNKNOWN = new Sample(false, 0, 0);
    }
}
