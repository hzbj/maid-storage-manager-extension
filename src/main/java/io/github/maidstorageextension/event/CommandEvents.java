package io.github.maidstorageextension.event;

import com.mojang.brigadier.arguments.StringArgumentType;
import io.github.maidstorageextension.MaidStorageManagerExtension;
import io.github.maidstorageextension.debug.ReachabilityDebugManager;
import io.github.maidstorageextension.terminal.TerminalAccountData;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

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
                }))
                .then(Commands.literal("terminal_account")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("reset_password")
                                .then(Commands.argument("username", StringArgumentType.word())
                                        .executes(context -> {
                                            String username = StringArgumentType.getString(
                                                    context, "username");
                                            TerminalAccountData data = TerminalAccountData.get(
                                                    context.getSource().getServer());
                                            TerminalAccountData.Account account = data.byUsername(username);
                                            if (account == null) {
                                                context.getSource().sendFailure(Component.translatable(
                                                        "command.maid_storage_manager_extension.terminal.account_missing",
                                                        username));
                                                return 0;
                                            }
                                            String temporary = UUID.randomUUID().toString()
                                                    .replace("-", "").substring(0, 16);
                                            data.issueResetCode(account, temporary);
                                            context.getSource().sendSuccess(() -> Component.translatable(
                                                    "command.maid_storage_manager_extension.terminal.password_reset",
                                                    username, temporary), false);
                                            return 1;
                                        })))
                        .then(Commands.literal("unbind_maid")
                                .then(Commands.argument("maid", UuidArgument.uuid())
                                        .executes(context -> {
                                            UUID maid = UuidArgument.getUuid(context, "maid");
                                            boolean changed = TerminalAccountData.get(
                                                    context.getSource().getServer()).forceUnregister(maid);
                                            if (!changed) {
                                                context.getSource().sendFailure(Component.translatable(
                                                        "command.maid_storage_manager_extension.terminal.maid_missing",
                                                        maid));
                                                return 0;
                                            }
                                            context.getSource().sendSuccess(() -> Component.translatable(
                                                    "command.maid_storage_manager_extension.terminal.maid_unbound",
                                                    maid), true);
                                            return 1;
                                        })))));
    }
}
