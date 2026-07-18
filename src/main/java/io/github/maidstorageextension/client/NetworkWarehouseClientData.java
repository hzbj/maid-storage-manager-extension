package io.github.maidstorageextension.client;

import io.github.maidstorageextension.logistics.NetworkWarehouseSnapshot;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Client-only cache for inventory payloads, which are refreshed less often than route status. */
@OnlyIn(Dist.CLIENT)
public final class NetworkWarehouseClientData {
    private static final long STALE_AFTER_MILLIS = 15_000L;
    private static final Map<UUID, Entry> SNAPSHOTS = new HashMap<>();

    private NetworkWarehouseClientData() {
    }

    public static void accept(UUID courier, NetworkWarehouseSnapshot.Snapshot snapshot) {
        if (courier != null) {
            SNAPSHOTS.put(courier, new Entry(snapshot, System.currentTimeMillis()));
        }
    }

    public static NetworkWarehouseSnapshot.Snapshot get(UUID courier) {
        if (courier == null) return NetworkWarehouseSnapshot.Snapshot.empty();
        Entry entry = SNAPSHOTS.get(courier);
        if (entry == null) return NetworkWarehouseSnapshot.Snapshot.empty();
        return System.currentTimeMillis() - entry.receivedAt > STALE_AFTER_MILLIS
                ? entry.snapshot.offline() : entry.snapshot;
    }

    public static void remove(UUID courier) {
        if (courier != null) SNAPSHOTS.remove(courier);
    }

    private record Entry(NetworkWarehouseSnapshot.Snapshot snapshot, long receivedAt) {
    }
}
