package io.github.maidstorageextension.client;

import com.github.tartaricacid.touhoulittlemaid.api.event.client.AddClothConfigEvent;
import com.github.tartaricacid.touhoulittlemaid.client.gui.entity.maid.AbstractMaidContainerGui;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import io.github.maidstorageextension.MaidStorageManagerExtension;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import studio.fantasyit.maid_storage_manager.maid.task.StorageManageTask;

/** Nests this extension's per-maid global options below Maid Storage Manager. */
@Mod.EventBusSubscriber(modid = MaidStorageManagerExtension.MOD_ID, value = Dist.CLIENT)
public final class ClientClothConfigEvents {
    private ClientClothConfigEvents() {
    }

    @SubscribeEvent
    public static void addExtensionCategories(AddClothConfigEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!(minecraft.screen instanceof AbstractMaidContainerGui<?> maidScreen)) {
            return;
        }
        LocalPlayer player = minecraft.player;
        EntityMaid maid = maidScreen.getMaid();
        if (player == null || maid == null || !maid.isOwnedBy(player)
                || !StorageManageTask.TASK_ID.equals(maid.getTask().getUid())) {
            return;
        }
        ExtensionClothConfigScreen.appendGlobalTo(event.getRoot(), event.getEntryBuilder(), maid);
    }
}
