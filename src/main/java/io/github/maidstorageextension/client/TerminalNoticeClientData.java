package io.github.maidstorageextension.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Short-lived server acknowledgements rendered inside an open communication terminal. */
@OnlyIn(Dist.CLIENT)
public final class TerminalNoticeClientData {
    private static final long VISIBLE_MILLIS = 5_000L;
    private static final Map<UUID, Entry> NOTICES = new HashMap<>();

    public record Notice(String translationKey, boolean success) {
        public Notice {
            translationKey = translationKey == null ? "" : translationKey;
        }
    }

    private record Entry(Notice notice, long receivedAt) {
    }

    private TerminalNoticeClientData() {
    }

    public static void accept(UUID terminal, String translationKey, boolean success) {
        if (terminal == null || translationKey == null || translationKey.isBlank()) return;
        NOTICES.put(terminal, new Entry(
                new Notice(translationKey, success), System.currentTimeMillis()));
    }

    public static Notice get(UUID terminal) {
        if (terminal == null) return null;
        Entry entry = NOTICES.get(terminal);
        if (entry == null) return null;
        if (System.currentTimeMillis() - entry.receivedAt() > VISIBLE_MILLIS) {
            NOTICES.remove(terminal);
            return null;
        }
        return entry.notice();
    }
}
