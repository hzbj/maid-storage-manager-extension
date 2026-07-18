package io.github.maidstorageextension.client;

import io.github.maidstorageextension.terminal.MaidTransportSnapshot;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class MaidTransportClientData {
    private static final Map<UUID, MaidTransportSnapshot.Snapshot> SNAPSHOTS = new HashMap<>();

    private MaidTransportClientData() {
    }

    public static void accept(UUID terminal, MaidTransportSnapshot.Snapshot snapshot) {
        if (terminal != null && snapshot != null) SNAPSHOTS.put(terminal, snapshot);
    }

    public static MaidTransportSnapshot.Snapshot get(UUID terminal) {
        return terminal == null ? MaidTransportSnapshot.Snapshot.empty()
                : SNAPSHOTS.getOrDefault(terminal, MaidTransportSnapshot.Snapshot.empty());
    }
}
