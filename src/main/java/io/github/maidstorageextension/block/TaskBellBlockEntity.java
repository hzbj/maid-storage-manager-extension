package io.github.maidstorageextension.block;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import io.github.maidstorageextension.maid.memory.TaskBellCallMemory;
import io.github.maidstorageextension.maid.ExtensionWorkControl;
import io.github.maidstorageextension.maid.TaskBellStandLocator;
import io.github.maidstorageextension.data.ExtensionConfigData;
import io.github.maidstorageextension.registry.ExtensionBlockEntities;
import io.github.maidstorageextension.registry.ExtensionMemoryModules;
import studio.fantasyit.maid_storage_manager.maid.task.StorageManageTask;

import java.util.UUID;

public class TaskBellBlockEntity extends BlockEntity {
    public static final String TAG_MAID_UUID = "MaidUUID";
    public static final String TAG_MAID_NAME = "MaidName";
    public static final String TAG_OWNER_UUID = "OwnerUUID";
    @Nullable
    private UUID maidUuid;
    private String maidName = "";
    @Nullable
    private UUID ownerUuid;

    public TaskBellBlockEntity(BlockPos pos, BlockState state) {
        super(ExtensionBlockEntities.TASK_BELL.get(), pos, state);
    }

    public void callMaid(Player caller) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (maidUuid == null) {
            caller.sendSystemMessage(Component.translatable("message.maid_storage_manager_extension.task_bell.unbound"));
            return;
        }

        Entity entity = serverLevel.getEntity(maidUuid);
        if (!(entity instanceof EntityMaid maid)) {
            caller.sendSystemMessage(Component.translatable("message.maid_storage_manager_extension.task_bell.not_loaded"));
            return;
        }
        if (!maid.getTask().getUid().equals(StorageManageTask.TASK_ID)) {
            caller.sendSystemMessage(Component.translatable("message.maid_storage_manager_extension.task_bell.not_storage_maid"));
            return;
        }
        ExtensionConfigData.Data config = ExtensionConfigData.get(maid);
        double callRange = config.taskBellRange();
        if (!maid.isAlive() || maid.distanceToSqr(worldPosition.getCenter()) > callRange * callRange) {
            caller.sendSystemMessage(Component.translatable(
                    "message.maid_storage_manager_extension.task_bell.out_of_range", config.taskBellRange()));
            return;
        }
        if (maid.getTarget() != null) {
            caller.sendSystemMessage(Component.translatable(
                    "message.maid_storage_manager_extension.task_bell.combat", maid.getName()));
            return;
        }
        if (ExtensionWorkControl.hasNonInterruptibleWork(maid)) {
            caller.sendSystemMessage(Component.translatable(
                    "message.maid_storage_manager_extension.task_bell.busy", maid.getName()));
            return;
        }

        BlockPos standPos = TaskBellStandLocator.find(serverLevel, maid, worldPosition);
        if (standPos == null) {
            caller.sendSystemMessage(Component.translatable(
                    "message.maid_storage_manager_extension.task_bell.no_safe_position", maid.getName()));
            return;
        }

        TaskBellCallMemory call = new TaskBellCallMemory(
                worldPosition.immutable(),
                standPos,
                caller.getUUID(),
                serverLevel.getServer().getTickCount()
        );
        maid.getBrain().setMemory(ExtensionMemoryModules.TASK_BELL_CALL.get(), call);
        studio.fantasyit.maid_storage_manager.util.MemoryUtil.clearTarget(maid);
        caller.sendSystemMessage(Component.translatable("message.maid_storage_manager_extension.task_bell.called", maid.getName()));
    }

    public boolean isBoundTo(UUID uuid) {
        return uuid.equals(maidUuid);
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag) {
        super.saveAdditional(tag);
        if (maidUuid != null) {
            tag.putUUID(TAG_MAID_UUID, maidUuid);
        }
        if (!maidName.isEmpty()) {
            tag.putString(TAG_MAID_NAME, maidName);
        }
        if (ownerUuid != null) {
            tag.putUUID(TAG_OWNER_UUID, ownerUuid);
        }
    }

    @Override
    public void load(@NotNull CompoundTag tag) {
        super.load(tag);
        maidUuid = tag.hasUUID(TAG_MAID_UUID) ? tag.getUUID(TAG_MAID_UUID) : null;
        maidName = tag.getString(TAG_MAID_NAME);
        ownerUuid = tag.hasUUID(TAG_OWNER_UUID) ? tag.getUUID(TAG_OWNER_UUID) : null;
    }
}
