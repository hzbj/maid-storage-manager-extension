package io.github.maidstorageextension.event;

import io.github.maidstorageextension.MaidStorageManagerExtension;
import io.github.maidstorageextension.debug.ReachabilityDebugManager;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = MaidStorageManagerExtension.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CommandEvents {
    private CommandEvents() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        registerRoot(event, "maid_storage_manager");
        registerRoot(event, MaidStorageManagerExtension.MOD_ID);
    }

    private static void registerRoot(RegisterCommandsEvent event, String root) {
        event.getDispatcher().register(Commands.literal(root)
                .then(Commands.literal("debug_reachable").executes(context -> {
                    ReachabilityDebugManager.prepare(context.getSource().getPlayerOrException());
                    context.getSource().sendSystemMessage(Component.translatable(
                            "debug.maid_storage_manager_extension.reachable.click_maid"));
                    return 1;
                })));
    }
}
