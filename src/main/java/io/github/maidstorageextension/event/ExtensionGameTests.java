package io.github.maidstorageextension.event;

import io.github.maidstorageextension.MaidStorageManagerExtension;
import io.github.maidstorageextension.compat.EnderPocketCompat;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

/** Runs only in the development GameTest server, including optional-dependency smoke runs. */
@GameTestHolder("minecraft")
@PrefixGameTestTemplate(false)
public final class ExtensionGameTests {
    private ExtensionGameTests() {
    }

    @GameTest(template = "igloo/top", timeoutTicks = 20)
    public static void releaseRegistriesLoad(GameTestHelper helper) {
        helper.assertTrue(ForgeRegistries.ITEMS.containsKey(
                MaidStorageManagerExtension.id("business_license")), "Business license item missing");
        helper.assertTrue(ForgeRegistries.BLOCKS.containsKey(
                MaidStorageManagerExtension.id("business_license")), "Business license block missing");
        helper.assertTrue(ForgeRegistries.MENU_TYPES.containsKey(
                MaidStorageManagerExtension.id("courier_config")), "Courier menu missing");
        if (!ModList.get().isLoaded("touhou_little_maid_spell")) {
            helper.assertTrue(EnderPocketCompat.isBroom(EnderPocketCompat.icon()),
                    "The broom integration must remain available without Maid Spell");
        }
        helper.succeed();
    }
}
