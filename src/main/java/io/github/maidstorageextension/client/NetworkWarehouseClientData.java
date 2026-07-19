package io.github.maidstorageextension.client;

import io.github.maidstorageextension.logistics.NetworkWarehouseSnapshot;
import io.github.maidstorageextension.terminal.MailboxKey;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Client-only cache for inventory payloads, which are refreshed less often than route status. */
@OnlyIn(Dist.CLIENT)
public final class NetworkWarehouseClientData {
    private static final long STALE_AFTER_MILLIS = 15_000L;
    private static final Map<Key, Entry> SNAPSHOTS = new HashMap<>();

    private NetworkWarehouseClientData() {
    }

    public static void accept(UUID courier, NetworkWarehouseSnapshot.Snapshot snapshot) {
        if (courier == null || snapshot == null || snapshot.mailboxKey() == null) return;
        Key key = new Key(courier, snapshot.mailboxKey());
        Entry current = SNAPSHOTS.get(key);
        if (current != null && current.snapshot.generation() > snapshot.generation()) return;
        SNAPSHOTS.put(key, new Entry(snapshot, System.currentTimeMillis()));
    }

    public static NetworkWarehouseSnapshot.Snapshot get(UUID courier, MailboxKey mailbox) {
        if (courier == null || mailbox == null) return NetworkWarehouseSnapshot.Snapshot.empty();
        Entry entry = SNAPSHOTS.get(new Key(courier, mailbox));
        if (entry == null) return NetworkWarehouseSnapshot.Snapshot.empty();
        return System.currentTimeMillis() - entry.receivedAt > STALE_AFTER_MILLIS
                ? entry.snapshot.offline() : entry.snapshot;
    }

    public static void remove(UUID courier) {
        if (courier != null) SNAPSHOTS.keySet().removeIf(key -> courier.equals(key.courier));
    }

    private record Entry(NetworkWarehouseSnapshot.Snapshot snapshot, long receivedAt) {
    }

    private record Key(UUID courier, MailboxKey mailbox) {
    }
}
