package com.seqwawa.seq.managers;

import com.seqwawa.seq.accessors.NotificationAccessor;
import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.network.ConnectionManager;
import com.seqwawa.seq.network.auth.MinecraftAuthService;
import com.seqwawa.seq.network.auth.StoredAuthSession;
import com.seqwawa.seq.utils.PlayerNameCache;
import java.util.concurrent.CompletableFuture;
import net.minecraft.network.chat.Component;

/**
 * The player the party finder acts for, in the game: who they are, the server connection they
 * are on, the commands it sends as them and the chat lines it shows them. A test plays the game.
 */
class PartyFinderPlayer {

    /** Their UUID: the account signed in to Sequoia, else the one playing. */
    String uuid() {
        var player = SeqClient.mc.player;
        return resolveIdentityUuid(
                player == null ? null : player.getUUID().toString(),
                MinecraftAuthService.getInstance().getCurrentSession());
    }

    /** Their name in the game, or null outside one. */
    String name() {
        var player = SeqClient.mc.player;
        return player == null || player.getName() == null ? null : player.getName().getString();
    }

    /** Their server connection, compared by identity, or null without one. */
    Object connection() {
        var player = SeqClient.mc.player;
        return player == null ? null : player.connection;
    }

    /** Looks up {@code uuid}'s name, null when there is none. */
    CompletableFuture<String> nameOf(String uuid) {
        return PlayerNameCache.resolveAsync(uuid);
    }

    /** {@code uuid}'s name if it is known without asking, else "Loading..." while it is looked up. */
    String knownName(String uuid) {
        return PlayerNameCache.resolve(uuid);
    }

    /** Looks up {@code username}'s UUID, null when there is none. */
    CompletableFuture<String> uuidOf(String username) {
        return PlayerNameCache.resolveUUID(username);
    }

    /** Whether they asked to be reminded of open parties, and are connected to Sequoia to hear of them. */
    boolean wantsOpenPartyReminders() {
        return SeqClient.getAnnounceOpenPartiesSetting() != null
                && SeqClient.getAnnounceOpenPartiesIntervalMinutesSetting() != null
                && SeqClient.getAnnounceOpenPartiesSetting().getValue()
                && ConnectionManager.isConnected();
    }

    /** Whether {@code username} is in their Wynncraft party, as the party sync last saw it. Game thread only. */
    boolean inWynnParty(String username) {
        WynnPartySyncManager sync = SeqClient.getWynnPartySyncManager();
        return sync != null && sync.isObservedMember(username);
    }

    /** Sends {@code command} as the player. Game thread only. */
    void sendCommand(String command) {
        var player = SeqClient.mc.player;
        if (player != null && player.connection != null) {
            player.connection.sendCommand(command);
        }
    }

    /** Shows {@code message} in chat after the Sequoia label, from any thread. */
    void notify(String message) {
        NotificationAccessor.notifyPlayer(message);
    }

    /** Shows {@code lines} in chat, in order. Game thread only. */
    void show(Component... lines) {
        var player = SeqClient.mc.player;
        if (player != null) {
            for (Component line : lines) {
                player.displayClientMessage(line, false);
            }
        }
    }

    static String resolveIdentityUuid(String currentPlayerUuid, StoredAuthSession storedAuthSession) {
        if (storedAuthSession != null) {
            String authenticatedUuid = storedAuthSession.minecraftUuid();
            if (authenticatedUuid != null && !authenticatedUuid.isBlank()) {
                return authenticatedUuid;
            }
        }
        return currentPlayerUuid;
    }

    /** {@code name} when it is a looked-up Minecraft username, for a command to name; else null. */
    static String commandName(String name) {
        return isUsername(name) && !"Unknown".equalsIgnoreCase(name) ? name : null;
    }

    static boolean isUsername(String name) {
        return name != null && name.matches("[A-Za-z0-9_]{3,16}");
    }

    /** A looked-up name for a chat line: "a player" while it is unknown. */
    static String displayName(String name) {
        return name == null || name.isBlank() || "Loading...".equalsIgnoreCase(name) || "Unknown".equalsIgnoreCase(name)
                ? "a player"
                : name;
    }
}
