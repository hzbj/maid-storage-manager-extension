package io.github.maidstorageextension.client;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import studio.fantasyit.maid_storage_manager.maid.config.StorageManagerMaidConfigGui;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.List;
import java.util.function.Consumer;

/** Version-checked bridge to Maid Storage Manager 1.15.x's private option-row model. */
public final class ConfigScreenBridge {
    private static final Constructor<?> OPTION_ROW_CONSTRUCTOR;
    private static final Field OPTIONS_FIELD;
    private static final Field MAID_FIELD;

    static {
        try {
            Class<?> optionRow = Class.forName(
                    "studio.fantasyit.maid_storage_manager.maid.config."
                            + "StorageManagerMaidConfigGui$OptionRow");
            OPTION_ROW_CONSTRUCTOR = optionRow.getDeclaredConstructor(
                    Component.class, Component.class, Consumer.class, Consumer.class);
            OPTION_ROW_CONSTRUCTOR.setAccessible(true);

            OPTIONS_FIELD = StorageManagerMaidConfigGui.class.getDeclaredField("options");
            OPTIONS_FIELD.setAccessible(true);
            MAID_FIELD = findField(StorageManagerMaidConfigGui.class, "maid");
            MAID_FIELD.setAccessible(true);
        } catch (ReflectiveOperationException exception) {
            throw new ExceptionInInitializerError(
                    "Maid Storage Manager 1.15.x config seam is incompatible: " + exception);
        }
    }

    private ConfigScreenBridge() {
    }

    /** Forces the private seam to be checked during client setup instead of on first click. */
    public static void validate() {
    }

    public static void appendExtensionSettingsRow(StorageManagerMaidConfigGui screen) {
        try {
            EntityMaid maid = (EntityMaid) MAID_FIELD.get(screen);
            Consumer<Object> open = ignored -> Minecraft.getInstance().setScreen(
                    ExtensionClothConfigScreen.create(screen, maid));
            Object row = OPTION_ROW_CONSTRUCTOR.newInstance(
                    Component.translatable("gui.maid_storage_manager_extension.config.open"),
                    Component.translatable("gui.maid_storage_manager_extension.config.open.value"),
                    open,
                    open);
            @SuppressWarnings("unchecked")
            List<Object> options = (List<Object>) OPTIONS_FIELD.get(screen);
            options.add(row);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(
                    "Maid Storage Manager 1.15.x config seam failed while adding extension settings",
                    exception);
        }
    }

    private static Field findField(Class<?> start, String name) throws NoSuchFieldException {
        Class<?> type = start;
        while (type != null) {
            try {
                return type.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            }
        }
        throw new NoSuchFieldException("MaidTaskConfigGui." + name);
    }
}
