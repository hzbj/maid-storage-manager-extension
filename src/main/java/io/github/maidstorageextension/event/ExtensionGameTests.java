package io.github.maidstorageextension.event;

import io.github.maidstorageextension.MaidStorageManagerExtension;
import io.github.maidstorageextension.compat.EnderPocketCompat;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.fml.ModList;

/** Runtime smoke test proving the release registries survive a real server bootstrap. */
@GameTestHolder("minecraft")
@PrefixGameTestTemplate(false)
public final class ExtensionGameTests {
    private ExtensionGameTests() {
    }

    @GameTest(template = "igloo/top", timeoutTicks = 20)
    public static void releaseRegistriesLoad(GameTestHelper helper) {
        helper.assertTrue(BuiltInRegistries.ITEM.containsKey(
                        MaidStorageManagerExtension.id("business_license")),
                "Business license item was not registered");
        helper.assertTrue(BuiltInRegistries.BLOCK.containsKey(
                        MaidStorageManagerExtension.id("business_license")),
                "Business license block was not registered");
        helper.assertTrue(BuiltInRegistries.MENU.containsKey(
                        MaidStorageManagerExtension.id("business_license")),
                "Business license menu was not registered");
        if (!ModList.get().isLoaded("touhou_little_maid_spell")) {
            helper.assertTrue(EnderPocketCompat.isBroom(EnderPocketCompat.icon()),
                    "The broom integration must remain available without Maid Spell");
        }
        helper.succeed();
    }
}
