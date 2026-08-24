package io.github.maidstorageextension.client;

import io.github.maidstorageextension.logistics.MaidLogisticsSnapshot;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class MaidLogisticsClientData {
    private static final Map<UUID, MaidLogisticsSnapshot.Snapshot> VALUES = new LinkedHashMap<>();

    private MaidLogisticsClientData() {
    }

    public static void accept(UUID terminal, MaidLogisticsSnapshot.Snapshot snapshot) {
        if (terminal != null && snapshot != null) VALUES.put(terminal, snapshot);
    }

    public static MaidLogisticsSnapshot.Snapshot get(UUID terminal) {
        return terminal == null ? null : VALUES.get(terminal);
    }
}
