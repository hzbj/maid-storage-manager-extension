package io.github.maidstorageextension.compat;

import com.github.yimeng261.maidspell.item.bauble.enderPocket.EnderPocketMaidProxyCache;
import com.github.yimeng261.maidspell.item.bauble.enderPocket.EnderPocketService;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

/** Registry-based integration with Touhou Little Maid: Spell. */
public final class EnderPocketCompat {
    private static final String MOD_ID = "touhou_little_maid_spell";
    public static final ResourceLocation ITEM_ID =
            new ResourceLocation(MOD_ID, "ender_pocket");
    public static final ResourceLocation BROOM_ITEM_ID =
            new ResourceLocation("touhou_little_maid", "broom");

    private EnderPocketCompat() {
    }

    public static EntityMaid resolveRemoteMaid(Player player, int maidId) {
        Entity local = player.level().getEntity(maidId);
        if (local instanceof EntityMaid maid) {
            return maid;
        }
        if (!ModList.get().isLoaded(MOD_ID)) return null;
        if (player instanceof ServerPlayer serverPlayer) {
            // The upstream service validates the active session, owner and maid UUID.
            return EnderPocketService.resolveRemoteMaid(serverPlayer, maidId);
        }
        return EnderPocketMaidProxyCache.find(player.level(), maidId);
    }

    public static void syncRemoteProxyBeforeMenu(Player player, int maidId) {
        if (!ModList.get().isLoaded(MOD_ID)
                || !(player instanceof ServerPlayer serverPlayer)) return;
        EntityMaid maid = resolveRemoteMaid(serverPlayer, maidId);
        if (maid != null) {
            EnderPocketService.syncRemoteProxyBeforeMenu(serverPlayer, maid);
        }
    }

    public static void syncRemoteProxy(ServerPlayer player, EntityMaid maid) {
        if (!ModList.get().isLoaded(MOD_ID)) return;
        EnderPocketService.syncRemoteProxyBeforeMenu(player, maid);
    }

    public static boolean isRemoteSessionActive(ServerPlayer player, EntityMaid maid) {
        return player != null && maid != null && ModList.get().isLoaded(MOD_ID)
                && EnderPocketService.isRemoteSessionActive(player, maid);
    }

    public static boolean isEquipped(EntityMaid maid) {
        Item item = ForgeRegistries.ITEMS.getValue(ITEM_ID);
        if (item == null || item == Items.AIR) {
            return false;
        }
        var baubles = maid.getMaidBauble();
        for (int slot = 0; slot < baubles.getSlots(); slot++) {
            if (baubles.getStackInSlot(slot).is(item)) {
                return true;
            }
        }
        return false;
    }

    public static boolean hasBroom(EntityMaid maid) {
        Item broom = ForgeRegistries.ITEMS.getValue(BROOM_ITEM_ID);
        if (broom == null || broom == Items.AIR) return false;
        var inventory = maid.getAvailableInv(false);
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            if (inventory.getStackInSlot(slot).is(broom)) return true;
        }
        return false;
    }

    public static boolean isBroom(ItemStack stack) {
        Item broom = ForgeRegistries.ITEMS.getValue(BROOM_ITEM_ID);
        return broom != null && broom != Items.AIR && stack.is(broom);
    }

    public static ItemStack icon() {
        Item item = ForgeRegistries.ITEMS.getValue(ITEM_ID);
        if (item != null && item != Items.AIR) return item.getDefaultInstance();
        Item broom = ForgeRegistries.ITEMS.getValue(BROOM_ITEM_ID);
        return broom == null || broom == Items.AIR
                ? Items.ENDER_EYE.getDefaultInstance() : broom.getDefaultInstance();
    }
}
