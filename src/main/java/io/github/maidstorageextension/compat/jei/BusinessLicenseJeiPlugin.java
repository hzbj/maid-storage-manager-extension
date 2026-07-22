package io.github.maidstorageextension.compat.jei;

import io.github.maidstorageextension.MaidStorageManagerExtension;
import io.github.maidstorageextension.client.BusinessLicenseScreen;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import org.jetbrains.annotations.NotNull;

/** Optional JEI bridge: right-clicking its ingredient overlay toggles a license filter entry. */
@JeiPlugin
public final class BusinessLicenseJeiPlugin implements IModPlugin {
    private static final ResourceLocation ID = new ResourceLocation(
            MaidStorageManagerExtension.MOD_ID, "business_license_filter");
    private static IJeiRuntime runtime;
    private static boolean listenerRegistered;

    @Override
    public @NotNull ResourceLocation getPluginUid() {
        return ID;
    }

    @Override
    public void onRuntimeAvailable(@NotNull IJeiRuntime jeiRuntime) {
        runtime = jeiRuntime;
        if (!listenerRegistered) {
            MinecraftForge.EVENT_BUS.addListener(BusinessLicenseJeiPlugin::onMousePressed);
            listenerRegistered = true;
        }
    }

    @Override
    public void onRuntimeUnavailable() {
        runtime = null;
    }

    private static void onMousePressed(ScreenEvent.MouseButtonPressed.Pre event) {
        if (event.getButton() != 1 || !(event.getScreen() instanceof BusinessLicenseScreen screen)
                || runtime == null) return;
        ITypedIngredient<?> ingredient = runtime.getIngredientListOverlay()
                .getIngredientUnderMouse().orElse(null);
        if (ingredient == null || ingredient.getType() != VanillaTypes.ITEM_STACK
                || !(ingredient.getIngredient() instanceof ItemStack stack) || stack.isEmpty()) return;
        screen.toggleFilter(stack);
        event.setCanceled(true);
    }
}
