package io.github.maidstorageextension.logistics;

import com.github.tartaricacid.touhoulittlemaid.entity.info.models.ServerMaidModels;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** Resolves the selected maid model's localized display name instead of its entity UUID name. */
public final class MaidDisplayName {
    private MaidDisplayName() {
    }

    public static String encode(EntityMaid maid) {
        if (maid == null) return "";
        if (maid.isYsmModel()) {
            Component name = maid.getYsmModelName();
            if (name != null && !name.getString().isBlank()) return LogisticsDisplayName.encode(name);
        }
        String modelId = maid.getModelId();
        String configured = ServerMaidModels.getInstance().getInfo(modelId)
                .map(info -> info.getName()).orElse("");
        String resolved = encodeModelName(modelId, configured);
        return resolved.isBlank() ? LogisticsDisplayName.encode(maid.getName()) : resolved;
    }

    public static String encodeModelName(String modelId, String configuredName) {
        String name = configuredName == null ? "" : configuredName.trim();
        if (name.startsWith("{") && name.endsWith("}") && name.length() > 2) {
            return LogisticsDisplayName.encode(Component.translatable(
                    name.substring(1, name.length() - 1)));
        }
        if (!name.isBlank()) return LogisticsDisplayName.encode(Component.literal(name));
        ResourceLocation id = ResourceLocation.tryParse(modelId);
        if (id == null) return "";
        String key = "model." + id.getNamespace() + "."
                + id.getPath().replace('/', '.') + ".name";
        return LogisticsDisplayName.encode(Component.translatable(key));
    }
}
