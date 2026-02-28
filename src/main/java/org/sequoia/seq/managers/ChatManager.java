package org.sequoia.seq.managers;

import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.network.chat.Component;
import org.sequoia.seq.client.SeqClient;
import org.sequoia.seq.events.DiscordChatEvent;
import org.sequoia.seq.network.ConnectionManager;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Bridges Wynncraft guild chat ↔ Discord via the Sequoia backend.
 * <p>
 * Outgoing: intercepts Wynncraft guild chat system messages, sends to backend.
 * Incoming: listens for Discord chat WS messages, displays in MC chat.
 */
public class ChatManager {

    /**
     * Wynncraft guild chat pattern (plain text of the component).
     * Typical format: "[★★★★★ PlayerName] message" or variants with rank symbols.
     */
    private static final Pattern GUILD_CHAT_PATTERN =
            Pattern.compile("^\\[(?:[★☆]+ )?(.+?)]\\s*(.+)$");

    /**
     * Prefix Wynncraft uses in the raw component for guild messages.
     * The component string usually starts with a color-coded "[Guild]" or similar.
     */
    private static final String GUILD_PREFIX = "\uE009"; // Wynncraft guild icon codepoint

    public ChatManager() {
        registerOutgoingHook();
        registerIncomingHook();
    }

    // ── Outgoing: Wynncraft guild → backend → Discord ──

    private void registerOutgoingHook() {
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (overlay) return; // ignore action bar messages
            if (!ConnectionManager.isConnected()) return;

            String plain = message.getString();

            // Detect guild chat messages (either via icon codepoint or pattern)
            if (!plain.contains(GUILD_PREFIX) && !isGuildChat(plain)) return;

            Matcher matcher = GUILD_CHAT_PATTERN.matcher(stripGuildPrefix(plain));
            if (!matcher.find()) return;

            String username = matcher.group(1).trim();
            String content = matcher.group(2).trim();

            // Don't echo our own messages back (backend will handle dedup too)
            if (SeqClient.mc.player != null
                    && username.equalsIgnoreCase(SeqClient.mc.player.getName().getString())) {
                return;
            }

            ConnectionManager.getInstance().sendGuildChat(username, content);
        });
    }

    // ── Incoming: Discord → backend → WS → MC chat ──

    private void registerIncomingHook() {
        ConnectionManager.onDiscordChat(msg -> {
            if (!SeqClient.getShowDiscordChatSetting().getValue()) return;

            String formatted = "§3[§bDiscord§3] §f" + msg.username() + "§7: §r" + msg.message();
            if (SeqClient.mc.player != null) {
                SeqClient.mc.player.displayClientMessage(Component.literal(formatted), false);
            }

            // Fire event for other listeners
            if (SeqClient.getEventBus() != null) {
                SeqClient.getEventBus().dispatch(new DiscordChatEvent(msg.username(), msg.message()));
            }
        });
    }

    // ── Helpers ──

    private boolean isGuildChat(String plain) {
        // Wynncraft guild chat heuristic: starts with rank symbols in brackets
        return plain.startsWith("[") && plain.contains("★");
    }

    private String stripGuildPrefix(String plain) {
        // Remove any leading guild icon codepoints or formatting
        return plain.replace(GUILD_PREFIX, "").trim();
    }
}
