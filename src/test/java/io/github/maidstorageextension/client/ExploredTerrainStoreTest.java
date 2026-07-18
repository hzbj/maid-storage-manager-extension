package io.github.maidstorageextension.client;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExploredTerrainStoreTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void exploredChunksRemainQueryableAfterSaveAndReload() throws Exception {
        ExploredTerrainStore store = new ExploredTerrainStore();
        int[] origin = samples(72, 1);
        int[] distant = samples(-12, 12);
        origin[3 * 16 + 2] = ExploredTerrainStore.pack(91, 5);
        distant[15 * 16 + 15] = ExploredTerrainStore.pack(-7, 9);
        store.putChunk(0, 0, origin);
        store.putChunk(-20, 34, distant);

        Path file = temporaryDirectory.resolve("terrain.bin");
        store.save(file);
        ExploredTerrainStore restored = ExploredTerrainStore.load(file);

        assertEquals(2, restored.chunkCount());
        assertEquals(new ExploredTerrainStore.Sample(true, 91, 5),
                restored.sample(2, 3));
        assertEquals(new ExploredTerrainStore.Sample(true, -7, 9),
                restored.sample(-20 * 16 + 15, 34 * 16 + 15));
        assertFalse(restored.sample(9000, 9000).known());
    }

    @Test
    void recapturingAChunkReplacesItsOldSurface() {
        ExploredTerrainStore store = new ExploredTerrainStore();
        store.putChunk(4, -3, samples(64, 2));
        assertEquals(64, store.sample(64, -48).height());

        store.putChunk(4, -3, samples(80, 7));

        ExploredTerrainStore.Sample updated = store.sample(64, -48);
        assertTrue(updated.known());
        assertEquals(80, updated.height());
        assertEquals(7, updated.mapColorId());
    }

    private static int[] samples(int height, int color) {
        int[] samples = new int[ExploredTerrainStore.SAMPLES_PER_CHUNK];
        Arrays.fill(samples, ExploredTerrainStore.pack(height, color));
        return samples;
    }
}
