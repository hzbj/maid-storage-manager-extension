package io.github.maidstorageextension.logistics;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

/** Preserves translatable entity/model names across the server-authored logistics snapshot. */
public final class LogisticsDisplayName {
    private static final String TRANSLATION_PREFIX = "@translation:";

    private LogisticsDisplayName() {
    }

    public static String encode(Component name) {
        if (name != null && name.getContents() instanceof TranslatableContents translated) {
            return TRANSLATION_PREFIX + translated.getKey();
        }
        return name == null ? "" : name.getString();
    }

    public static Component decode(String encoded) {
        if (encoded == null || encoded.isBlank()) return Component.empty();
        if (encoded.startsWith(TRANSLATION_PREFIX)) {
            return Component.translatable(encoded.substring(TRANSLATION_PREFIX.length()));
        }
        // Reads snapshots produced by 1.1.3 before the explicit marker was introduced.
        if (encoded.startsWith("model.") && encoded.endsWith(".name")) {
            return Component.translatable(encoded);
        }
        return Component.literal(encoded);
    }
}
