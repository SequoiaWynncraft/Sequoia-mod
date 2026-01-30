package op.legends.seq.command;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.network.chat.Component;
import op.legends.seq.network.ConnectionManager;

public class SeqCommand {

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register(SeqCommand::registerCommands);
    }

    private static void registerCommands(
            CommandDispatcher<FabricClientCommandSource> dispatcher,
            CommandBuildContext registryAccess
    ) {
        dispatcher.register(
                ClientCommandManager.literal("seq")
                        .then(ClientCommandManager.literal("connect")
                                .executes(ctx -> {
                                    ConnectionManager.getInstance().connect();
                                    return 1;
                                }))
                        .then(ClientCommandManager.literal("disconnect")
                                .executes(ctx -> {
                                    ConnectionManager.getInstance().disconnect();
                                    return 1;
                                }))
                        .then(ClientCommandManager.literal("connected")
                                .executes(ctx -> {
                                    if (!ConnectionManager.getInstance().isConnected()) {
                                        ctx.getSource().sendFeedback(
                                                Component.literal("[Seq] Not connected. Use /seq connect first."));
                                        return 0;
                                    }
                                    ConnectionManager.getInstance().requestConnectedUsers(users -> {
                                        if (users.isEmpty()) {
                                            ctx.getSource().sendFeedback(
                                                    Component.literal("[Seq] No users connected."));
                                        } else {
                                            ctx.getSource().sendFeedback(
                                                    Component.literal("[Seq] Connected users (" + users.size() + "):"));
                                            for (String user : users) {
                                                ctx.getSource().sendFeedback(
                                                        Component.literal("  - " + user));
                                            }
                                        }
                                    });
                                    return 1;
                                }))
        );
    }
}
