package org.sequoia.seq.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.network.chat.Component;
import org.sequoia.seq.accessors.NotificationAccessor;
import org.sequoia.seq.client.SeqClient;
import org.sequoia.seq.model.PartyRole;
import org.sequoia.seq.network.ConnectionManager;
import org.sequoia.seq.ui.PartyFinderScreen;

public class SeqCommand {

        public static void register() {
                ClientCommandRegistrationCallback.EVENT.register(SeqCommand::registerCommands);
        }

        private static void registerCommands(
                        CommandDispatcher<FabricClientCommandSource> dispatcher,
                        CommandBuildContext registryAccess) {
                var root = ClientCommandManager.literal("seq")
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
                                                        if (!ConnectionManager.getInstance().isConnected()) {
                                                                ctx.getSource().sendFeedback(
                                                                                NotificationAccessor.prefixed(
                                                                                                "Not connected. Use /seq connect first."));
                                                                return 0;
                                                        }
                                                        ConnectionManager.getInstance().requestConnectedUsers(users -> {
                                                                if (users.isEmpty()) {
                                                                        ctx.getSource().sendFeedback(
                                                                                        NotificationAccessor.prefixed(
                                                                                                        "No users connected."));
                                                                } else {
                                                                        ctx.getSource().sendFeedback(
                                                                                        NotificationAccessor.prefixed(
                                                                                                        "Connected users ("
                                                                                                                        + users.size()
                                                                                                                        + "):"));
                                                                        for (String user : users) {
                                                                                ctx.getSource().sendFeedback(Component
                                                                                                .literal("  - " + user));
                                                                        }
                                                                }
                                                        });
                                                        return 1;
                                                }))
                                .then(ClientCommandManager.literal("status")
                                                .executes(ctx -> {
                                                        boolean connected = ConnectionManager.isConnected();
                                                        String token = SeqClient.getConfigManager().getToken();
                                                        boolean hasToken = token != null && !token.isBlank();
                                                        String uptime = ConnectionManager.getInstance()
                                                                        .getUptimeString();
                                                        ctx.getSource().sendFeedback(NotificationAccessor.prefixed(
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
                                .then(ClientCommandManager.literal("internalinvite")
                                                .then(ClientCommandManager.literal("join")
                                                                .then(ClientCommandManager.argument("listingId",
                                                                                LongArgumentType.longArg(1))
                                                                                .then(ClientCommandManager.argument(
                                                                                                "inviteToken",
                                                                                                StringArgumentType
                                                                                                                .string())
                                                                                                .executes(ctx -> runInternalJoinInvite(
                                                                                                                ctx.getSource(),
                                                                                                                LongArgumentType.getLong(
                                                                                                                                ctx,
                                                                                                                                "listingId"),
                                                                                                                StringArgumentType
                                                                                                                                .getString(ctx,
                                                                                                                                                "inviteToken"),
                                                                                                                null))
                                                                                                .then(ClientCommandManager
                                                                                                                .argument("role",
                                                                                                                                StringArgumentType
                                                                                                                                                .word())
                                                                                                                .executes(ctx -> runInternalJoinInvite(
                                                                                                                                ctx.getSource(),
                                                                                                                                LongArgumentType.getLong(
                                                                                                                                                ctx,
                                                                                                                                                "listingId"),
                                                                                                                                StringArgumentType
                                                                                                                                                .getString(ctx,
                                                                                                                                                                "inviteToken"),
                                                                                                                                StringArgumentType
                                                                                                                                                .getString(ctx,
                                                                                                                                                                "role")))))))
                                                .then(ClientCommandManager.literal("deny")
                                                                .then(ClientCommandManager.argument("listingId",
                                                                                LongArgumentType.longArg(1))
                                                                                .executes(ctx -> runInternalDenyInvite(
                                                                                                ctx.getSource(),
                                                                                                LongArgumentType.getLong(
                                                                                                                ctx,
                                                                                                                "listingId"))))));

                dispatcher.register(root);
        }

        public static int runInternalJoinInvite(
                        FabricClientCommandSource source,
                        long listingId,
                        String inviteToken,
                        String roleName) {
                if (inviteToken == null || inviteToken.isBlank()) {
                        source.sendFeedback(NotificationAccessor.prefixed("Invite token is required."));
                        return 0;
                }

                PartyRole role = PartyRole.DPS;
                if (roleName != null && !roleName.isBlank()) {
                        try {
                                role = PartyRole.valueOf(roleName.trim().toUpperCase());
                        } catch (IllegalArgumentException ignored) {
                                source.sendFeedback(NotificationAccessor
                                                .prefixed("Invalid role. Use DPS, HEALER, or TANK."));
                                return 0;
                        }
                }

                SeqClient.getPartyFinderManager().joinPartyWithInviteToken(listingId, role, inviteToken);
                source.sendFeedback(NotificationAccessor.prefixed("Joining party invite..."));
                return 1;
        }

        public static int runInternalDenyInvite(
                        FabricClientCommandSource source,
                        long listingId) {
                source.sendFeedback(NotificationAccessor
                                .prefixed("Denied party invite for listing #" + listingId + "."));
                return 1;
        }
}
