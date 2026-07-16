package io.github.maidstorageextension.client;

import io.github.maidstorageextension.logistics.LogisticsSnapshot;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Ephemeral client cache. Keeping live data off ItemStack NBT prevents held-item re-equip spam. */
@OnlyIn(Dist.CLIENT)
public final class LogisticsTrackerClientData {
    private static final long STALE_AFTER_MILLIS = 3_500L;
    private static final Map<UUID, Entry> SNAPSHOTS = new HashMap<>();

    private LogisticsTrackerClientData() {
    }

    public static void accept(UUID courier, LogisticsSnapshot.Snapshot snapshot) {
        if (courier != null) SNAPSHOTS.put(courier, new Entry(snapshot, System.currentTimeMillis()));
    }

    public static LogisticsSnapshot.Snapshot get(UUID courier) {
        if (courier == null) return LogisticsSnapshot.Snapshot.empty();
        Entry entry = SNAPSHOTS.get(courier);
        if (entry == null) return LogisticsSnapshot.Snapshot.empty();
        return System.currentTimeMillis() - entry.receivedAt > STALE_AFTER_MILLIS
                ? entry.snapshot.offline() : entry.snapshot;
    }

    public static void remove(UUID courier) {
        if (courier != null) SNAPSHOTS.remove(courier);
    }

    private record Entry(LogisticsSnapshot.Snapshot snapshot, long receivedAt) {
    }
}
