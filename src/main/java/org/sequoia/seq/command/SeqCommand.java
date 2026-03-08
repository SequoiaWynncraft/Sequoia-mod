package org.sequoia.seq.command;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.commands.CommandBuildContext;
import org.sequoia.seq.accessors.NotificationAccessor;
import org.sequoia.seq.client.SeqClient;
import org.sequoia.seq.model.PartyRole;
import org.sequoia.seq.network.ConnectionManager;
import org.sequoia.seq.network.auth.AuthState;
import org.sequoia.seq.ui.PartyFinderScreen;

public class SeqCommand {

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register(SeqCommand::registerCommands);
        ClientSendMessageEvents.ALLOW_COMMAND.register(SeqCommand::onOutgoingCommand);
    }

    private static void registerCommands(
            CommandDispatcher<FabricClientCommandSource> dispatcher, CommandBuildContext registryAccess) {
        var root = ClientCommandManager.literal("seq")
                .executes(ctx -> {
                    SeqClient.openMainScreen();
                    return 1;
                })
                .then(ClientCommandManager.literal("connect").executes(ctx -> {
                    ConnectionManager.getInstance().connect();
                    return 1;
                }))
                .then(ClientCommandManager.literal("link").executes(ctx -> {
                    ConnectionManager.getInstance().link();
                    return 1;
                }))
                .then(ClientCommandManager.literal("disconnect").executes(ctx -> {
                    ConnectionManager.getInstance().disconnect();
                    return 1;
                }))
                .then(ClientCommandManager.literal("connected").executes(ctx -> {
                    if (!ConnectionManager.isConnected()) {
                        ctx.getSource()
                                .sendFeedback(NotificationAccessor.prefixed("Not connected. Use /seq connect first."));
                        return 0;
                    }
                    ConnectionManager.getInstance().requestConnectedUsers(users -> {
                        if (users.isEmpty()) {
                            ctx.getSource().sendFeedback(NotificationAccessor.prefixed("No users connected."));
                        } else {
                            ctx.getSource()
                                    .sendFeedback(
                                            NotificationAccessor.prefixed("Connected users (" + users.size() + "):"));
                            for (String user : users) {
                                ctx.getSource().sendFeedback(NotificationAccessor.prefixed("• " + user));
                            }
                        }
                    });
                    return 1;
                }))
                .then(ClientCommandManager.literal("status").executes(ctx -> {
                    boolean connected = ConnectionManager.isConnected();
                    String token = SeqClient.getAuthService().getCurrentToken();
                    boolean hasToken = token != null && !token.isBlank();
                    String uptime = ConnectionManager.getInstance().getUptimeString();
                    AuthState authState = SeqClient.getAuthService().getState();
                    ctx.getSource()
                            .sendFeedback(NotificationAccessor.prefixed("Connected: " + connected
                                    + " | Auth: " + authState.name()
                                    + " | Token: "
                                    + (hasToken ? "present" : "none")
                                    + (uptime != null ? " | Uptime: " + uptime : "")));
                    return 1;
                }))
                .then(ClientCommandManager.literal("logout").executes(ctx -> {
                    ConnectionManager.getInstance().disconnect();
                    SeqClient.getAuthService().clearSession();
                    ctx.getSource().sendFeedback(NotificationAccessor.prefixed("Logged out and token cleared."));
                    return 1;
                }))
                .then(ClientCommandManager.literal("party").executes(ctx -> {
                    SeqClient.mc.execute(() -> SeqClient.mc.setScreen(new PartyFinderScreen(SeqClient.mc.screen)));
                    return 1;
                }));

        dispatcher.register(root);
    }

    private static int runInternalJoinInvite(long listingId, String inviteToken) {
        if (inviteToken == null || inviteToken.isBlank()) {
            return 0;
        }

        SeqClient.getPartyFinderManager().joinPartyWithInviteToken(listingId, PartyRole.DPS, inviteToken);
        return 1;
    }

    private static int runInternalDenyInvite(long listingId) {
        return 1;
    }

    private static boolean onOutgoingCommand(String command) {
        String raw = command == null ? "" : command.trim();
        if (raw.isEmpty()) {
            return true;
        }
        if (raw.charAt(0) == '/') {
            raw = raw.substring(1).trim();
        }
        if (!raw.startsWith("_seqinvite")) {
            return true;
        }

        String[] parts = raw.split("\\s+", 4);
        if (parts.length < 3) {
            return false;
        }

        String action = parts[1];
        long listingId;
        try {
            listingId = Long.parseLong(parts[2]);
        } catch (NumberFormatException ignored) {
            return false;
        }

        if ("join".equalsIgnoreCase(action)) {
            if (parts.length < 4) {
                return false;
            }
            String inviteToken = unquoteCommandValue(parts[3]);
            runInternalJoinInvite(listingId, inviteToken);
            return false;
        }

        if ("deny".equalsIgnoreCase(action)) {
            runInternalDenyInvite(listingId);
            return false;
        }

        return false;
    }

    private static String unquoteCommandValue(String rawValue) {
        if (rawValue == null) {
            return "";
        }
        String value = rawValue.trim();
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            value = value.substring(1, value.length() - 1).replace("\\\"", "\"").replace("\\\\", "\\");
        }
        return value;
    }
}
