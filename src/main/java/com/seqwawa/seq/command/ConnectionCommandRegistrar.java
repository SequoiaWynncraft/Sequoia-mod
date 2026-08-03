package com.seqwawa.seq.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.network.ConnectionManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

final class ConnectionCommandRegistrar {
    private ConnectionCommandRegistrar() {}

    static void register(LiteralArgumentBuilder<FabricClientCommandSource> root) {
        root.then(ClientCommandManager.literal("connect")
                .executes(ctx -> {
                    ConnectionManager.getInstance().connectManually();
                    return 1;
                }));
        root.then(ClientCommandManager.literal("disconnect")
                .executes(ctx -> {
                    ConnectionManager.getInstance().disconnectManually();
                    return 1;
                }));
        root.then(ClientCommandManager.literal("connected")
                .executes(ctx -> {
                    if (!ConnectionManager.isConnected()) {
                        SeqCommand.sendFeedback(ctx.getSource(), "Not connected. Use /seq connect first.");
                        return 0;
                    }
                    ConnectionManager.getInstance().requestConnectedUsers(users -> {
                        if (users.isEmpty()) {
                            SeqCommand.sendFeedback(ctx.getSource(), "No users connected.");
                            return;
                        }

                        SeqCommand.sendFeedback(ctx.getSource(), "Connected users (" + users.size() + "):");
                        for (String user : users) {
                            SeqCommand.sendFeedback(ctx.getSource(), "• " + user);
                        }
                    });
                    return 1;
                }));
        root.then(ClientCommandManager.literal("status").executes(SeqCommand::runStatus));
        root.then(ClientCommandManager.literal("logout")
                .executes(ctx -> {
                    ConnectionManager.getInstance().disconnect();
                    SeqClient.getConfigManager().clearToken();
                    SeqCommand.sendFeedback(ctx.getSource(), "Logged out and token cleared.");
                    return 1;
                }));
        root.then(buildIgnoreCommand());
        root.then(buildUnignoreCommand());
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> buildIgnoreCommand() {
        return ClientCommandManager.literal("ignore")
                .executes(SeqCommand::runIgnoredBridgeUsersList)
                .then(ClientCommandManager.argument("username", StringArgumentType.word())
                        .executes(SeqCommand::runIgnoreBridgeUser));
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> buildUnignoreCommand() {
        return ClientCommandManager.literal("unignore")
                .executes(SeqCommand::runIgnoredBridgeUsersList)
                .then(ClientCommandManager.argument("username", StringArgumentType.word())
                        .suggests(SeqCommand::suggestIgnoredBridgeUsers)
                        .executes(SeqCommand::runUnignoreBridgeUser));
    }
}
