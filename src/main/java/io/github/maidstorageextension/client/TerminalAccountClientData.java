package io.github.maidstorageextension.client;

import io.github.maidstorageextension.terminal.TerminalAccountSnapshot;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class TerminalAccountClientData {
    private static final Map<UUID, TerminalAccountSnapshot.Snapshot> SNAPSHOTS = new HashMap<>();

    private TerminalAccountClientData() {
    }

    public static void accept(UUID terminal, TerminalAccountSnapshot.Snapshot snapshot) {
        if (terminal != null && snapshot != null) SNAPSHOTS.put(terminal, snapshot);
    }

    public static TerminalAccountSnapshot.Snapshot get(UUID terminal) {
        return terminal == null ? TerminalAccountSnapshot.Snapshot.loggedOut("")
                : SNAPSHOTS.getOrDefault(terminal, TerminalAccountSnapshot.Snapshot.loggedOut(""));
    }
}
