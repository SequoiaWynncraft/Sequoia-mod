package org.sequoia.seq.command;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.network.chat.Component;
import org.sequoia.seq.accessors.NotificationAccessor;
import org.sequoia.seq.client.SeqClient;
import org.sequoia.seq.network.ConnectionManager;
import org.sequoia.seq.ui.PartyFinderScreen;
import org.sequoia.seq.update.UpdateManager;

public class SeqCommand {

        public static void register() {
                ClientCommandRegistrationCallback.EVENT.register(SeqCommand::registerCommands);
        }

        private static void registerCommands(
                        CommandDispatcher<FabricClientCommandSource> dispatcher,
                        CommandBuildContext registryAccess) {
                dispatcher.register(
                                ClientCommandManager.literal("seq")
                                                .executes(ctx -> {
                                                        SeqClient.openMainScreen();
                                                        return 1;
                                                })
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
                                                                        if (!ConnectionManager.getInstance()
                                                                                        .isConnected()) {
                                                                                ctx.getSource().sendFeedback(
                                                                                                NotificationAccessor
                                                                                                                .prefixed("Not connected. Use /seq connect first."));
                                                                                return 0;
                                                                        }
                                                                        ConnectionManager.getInstance()
                                                                                        .requestConnectedUsers(
                                                                                                        users -> {
                                                                                                                if (users.isEmpty()) {
                                                                                                                        ctx.getSource().sendFeedback(
                                                                                                                                        NotificationAccessor
                                                                                                                                                        .prefixed("No users connected."));
                                                                                                                } else {
                                                                                                                        ctx.getSource().sendFeedback(
                                                                                                                                        NotificationAccessor
                                                                                                                                                        .prefixed("Connected users ("
                                                                                                                                                                        + users.size()
                                                                                                                                                                        + "):"));
                                                                                                                        for (String user : users) {
                                                                                                                                ctx.getSource().sendFeedback(
                                                                                                                                                Component.literal(
                                                                                                                                                                "  - " + user));
                                                                                                                        }
                                                                                                                }
                                                                                                        });
                                                                        return 1;
                                                                }))
                                                .then(ClientCommandManager.literal("status")
                                                                .executes(ctx -> {
                                                                        boolean connected = ConnectionManager
                                                                                        .isConnected();
                                                                        String token = SeqClient.getConfigManager()
                                                                                        .getToken();
                                                                        boolean hasToken = token != null
                                                                                        && !token.isBlank();
                                                                        String uptime = ConnectionManager.getInstance()
                                                                                        .getUptimeString();
                                                                        ctx.getSource().sendFeedback(
                                                                                        NotificationAccessor.prefixed(
                                                                                                        "Connected: " + connected
                                                                                                                        + " | Token: "
                                                                                                                        + (hasToken ? "present"
                                                                                                                                        : "none")
                                                                                                                        + (uptime != null
                                                                                                                                        ? " | Uptime: " + uptime
                                                                                                                                        : "")));
                                                                        return 1;
                                                                }))
                                                .then(ClientCommandManager.literal("logout")
                                                                .executes(ctx -> {
                                                                        ConnectionManager.getInstance().disconnect();
                                                                        SeqClient.getConfigManager().clearToken();
                                                                        ctx.getSource().sendFeedback(
                                                                                        NotificationAccessor.prefixed(
                                                                                                        "Logged out and token cleared."));
                                                                        return 1;
                                                                }))
                                                .then(ClientCommandManager.literal("party")
                                                                .executes(ctx -> {
                                                                        SeqClient.mc.execute(() -> SeqClient.mc
                                                                                        .setScreen(new PartyFinderScreen(
                                                                                                        SeqClient.mc.screen)));
                                                                        return 1;
                                                                }))
                                                .then(ClientCommandManager.literal("update")
                                                                .executes(ctx -> {
                                                                        UpdateManager.getInstance()
                                                                                        .checkForUpdatesManually();
                                                                        ctx.getSource().sendFeedback(
                                                                                        NotificationAccessor.prefixed(
                                                                                                        "Checking for updates..."));
                                                                        return 1;
                                                                })
                                                                .then(ClientCommandManager.literal("check")
                                                                                .executes(ctx -> {
                                                                                        UpdateManager.getInstance()
                                                                                                        .checkForUpdatesManually();
                                                                                        ctx.getSource().sendFeedback(
                                                                                                        NotificationAccessor
                                                                                                                        .prefixed(
                                                                                                                                        "Checking for updates..."));
                                                                                        return 1;
                                                                                }))
                                                                .then(ClientCommandManager.literal("apply")
                                                                                .executes(ctx -> {
                                                                                        UpdateManager.getInstance()
                                                                                                        .applyPendingUpdate(
                                                                                                                        false);
                                                                                        return 1;
                                                                                }))
                                                                .then(ClientCommandManager.literal("apply-and-exit")
                                                                                .executes(ctx -> {
                                                                                        UpdateManager.getInstance()
                                                                                                        .applyPendingUpdate(
                                                                                                                        true);
                                                                                        return 1;
                                                                                }))
                                                                .then(ClientCommandManager.literal("status")
                                                                                .executes(ctx -> {
                                                                                        ctx.getSource().sendFeedback(
                                                                                                        NotificationAccessor
                                                                                                                        .prefixed(
                                                                                                                                        UpdateManager.getInstance()
                                                                                                                                                        .getStatusLine()));
                                                                                        return 1;
                                                                                }))));
        }
}
