package io.github.maidstorageextension.item;

import com.github.tartaricacid.touhoulittlemaid.api.bauble.IMaidBauble;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import io.github.maidstorageextension.registry.ExtensionItems;
import studio.fantasyit.maid_storage_manager.items.MaidInteractItem;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class InventoryMaintenanceDevice extends MaidInteractItem implements IMaidBauble {
    public static final String TAG_FRAME_UUID = "bound_frame_uuid";
    public static final String TAG_FRAME_DIMENSION = "bound_frame_dimension";
    public static final String TAG_FRAME_POS = "bound_frame_pos";

    public InventoryMaintenanceDevice() {
        super(new Properties().stacksTo(1));
    }

    public static Optional<ItemStack> findOn(EntityMaid maid) {
        IItemHandler baubles = maid.getMaidBauble();
        for (int i = 0; i < baubles.getSlots(); i++) {
            ItemStack stack = baubles.getStackInSlot(i);
            if (stack.is(ExtensionItems.INVENTORY_MAINTENANCE_DEVICE.get())) {
                return Optional.of(stack);
            }
        }
        return Optional.empty();
    }

    public static boolean isBound(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null
                && tag.hasUUID(TAG_FRAME_UUID)
                && tag.contains(TAG_FRAME_DIMENSION)
                && tag.contains(TAG_FRAME_POS);
    }

    public static void bind(ItemStack stack, ItemFrame frame) {
        CompoundTag tag = stack.getOrCreateTag();
        tag.putUUID(TAG_FRAME_UUID, frame.getUUID());
        tag.putString(TAG_FRAME_DIMENSION, frame.level().dimension().location().toString());
        tag.putLong(TAG_FRAME_POS, frame.blockPosition().asLong());
        stack.setTag(tag);
    }

    public static UUID getFrameUuid(ItemStack stack) {
        return stack.getOrCreateTag().getUUID(TAG_FRAME_UUID);
    }

    public static ResourceLocation getFrameDimension(ItemStack stack) {
        return new ResourceLocation(stack.getOrCreateTag().getString(TAG_FRAME_DIMENSION));
    }

    public static BlockPos getFramePos(ItemStack stack) {
        return BlockPos.of(stack.getOrCreateTag().getLong(TAG_FRAME_POS));
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        tooltip.add(Component.translatable("tooltip.maid_storage_manager_extension.inventory_maintenance_device.desc").withStyle(ChatFormatting.GRAY));
        if (isBound(stack)) {
            BlockPos pos = getFramePos(stack);
            tooltip.add(Component.translatable("tooltip.maid_storage_manager_extension.inventory_maintenance_device.bound",
                    pos.getX(), pos.getY(), pos.getZ()).withStyle(ChatFormatting.GREEN));
        } else {
            tooltip.add(Component.translatable("tooltip.maid_storage_manager_extension.inventory_maintenance_device.unbound").withStyle(ChatFormatting.YELLOW));
        }
    }
}
