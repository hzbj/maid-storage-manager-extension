package io.github.maidstorageextension.maid;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import io.github.maidstorageextension.maid.memory.TaskBellCallMemory;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public final class TaskBellCallController {
    private TaskBellCallController() {
    }

    public static void notifyArrived(ServerLevel level, EntityMaid maid) {
        TaskBellCallMemory call = ExtensionMemoryUtil.getTaskBellCall(maid);
        sendToCaller(level, call,
                Component.translatable("message.maid_storage_manager_extension.task_bell.arrived", maid.getName()));
    }

    public static void fail(ServerLevel level, EntityMaid maid, String reason) {
        TaskBellCallMemory call = ExtensionMemoryUtil.getTaskBellCall(maid);
        sendToCaller(level, call, Component.translatable(
                "message.maid_storage_manager_extension.task_bell.failed." + reason, maid.getName()));
        ExtensionMemoryUtil.clearTaskBellCall(maid);
    }

    private static void sendToCaller(ServerLevel level, TaskBellCallMemory call, Component message) {
        if (call == null) {
            return;
        }
        ServerPlayer caller = level.getServer().getPlayerList().getPlayer(call.callerUuid());
        if (caller != null) {
            caller.sendSystemMessage(message);
        }
    }
}
