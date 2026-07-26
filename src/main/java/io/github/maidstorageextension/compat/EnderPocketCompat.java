package io.github.maidstorageextension.compat;

import com.github.yimeng261.maidspell.Global;
import com.github.yimeng261.maidspell.item.bauble.enderPocket.EnderPocketService;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.fml.ModList;
import net.minecraft.core.registries.BuiltInRegistries;

/** Registry-based integration with Touhou Little Maid: Spell. */
public final class EnderPocketCompat {
    private static final String MOD_ID = "touhou_little_maid_spell";
    public static final ResourceLocation ITEM_ID =
            ResourceLocation.fromNamespaceAndPath(MOD_ID, "ender_pocket");
    public static final ResourceLocation BROOM_ITEM_ID =
            ResourceLocation.fromNamespaceAndPath("touhou_little_maid", "broom");

    private EnderPocketCompat() {
    }

    public static EntityMaid resolveRemoteMaid(Player player, int maidId) {
        Entity local = player.level().getEntity(maidId);
        if (local instanceof EntityMaid maid) {
            return maid;
        }
        if (!ModList.get().isLoaded(MOD_ID)) return null;
        if (player instanceof ServerPlayer serverPlayer) {
            return EnderPocketService.getPlayerEnderPocketMaids(serverPlayer).stream()
                    .filter(info -> info.getMaidEntityId() == maidId)
                    .map(info -> Global.getOrCreatePlayerMaidMap(serverPlayer.getUUID())
                            .get(info.getMaidUUID()))
                    .filter(maid -> maid != null && maid.isAlive())
                    .filter(maid -> maid.isOwnedBy(serverPlayer))
                    .filter(EnderPocketCompat::isEquipped)
                    .filter(maid -> EnderPocketService.getPlayerEnderPocketMaids(serverPlayer).stream()
                            .anyMatch(info -> info.getMaidUUID().equals(maid.getUUID())
                                    && info.getMaidEntityId() == maid.getId()
                                    && info.getLevelKey().equals(maid.level().dimension())))
                    .findFirst().orElse(null);
        }
        return null;
    }

    public static void syncRemoteProxyBeforeMenu(Player player, int maidId) {
        if (!ModList.get().isLoaded(MOD_ID)
                || !(player instanceof ServerPlayer serverPlayer)) return;
        resolveRemoteMaid(serverPlayer, maidId);
    }

    public static void syncRemoteProxy(ServerPlayer player, EntityMaid maid) {
        // 1.8.4 exposes the authoritative maid through Global; there is no proxy to flush.
    }

    public static boolean isRemoteSessionActive(ServerPlayer player, EntityMaid maid) {
        return player != null && maid != null && ModList.get().isLoaded(MOD_ID)
                && maid.isOwnedBy(player) && isEquipped(maid)
                && EnderPocketService.getPlayerEnderPocketMaids(player).stream()
                .anyMatch(info -> info.getMaidUUID().equals(maid.getUUID())
                        && info.getLevelKey().equals(maid.level().dimension()));
    }

    public static boolean isEquipped(EntityMaid maid) {
        Item item = BuiltInRegistries.ITEM.get(ITEM_ID);
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
        Item broom = BuiltInRegistries.ITEM.get(BROOM_ITEM_ID);
        if (broom == null || broom == Items.AIR) return false;
        var inventory = maid.getAvailableInv(false);
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            if (inventory.getStackInSlot(slot).is(broom)) return true;
        }
        return false;
    }

    public static boolean isBroom(ItemStack stack) {
        Item broom = BuiltInRegistries.ITEM.get(BROOM_ITEM_ID);
        return broom != null && broom != Items.AIR && stack.is(broom);
    }

    public static ItemStack icon() {
        Item item = BuiltInRegistries.ITEM.get(ITEM_ID);
        if (item != null && item != Items.AIR) return item.getDefaultInstance();
        Item broom = BuiltInRegistries.ITEM.get(BROOM_ITEM_ID);
        return broom == null || broom == Items.AIR
                ? Items.ENDER_EYE.getDefaultInstance() : broom.getDefaultInstance();
    }
}
