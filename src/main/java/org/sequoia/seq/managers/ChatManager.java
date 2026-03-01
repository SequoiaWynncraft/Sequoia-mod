package org.sequoia.seq.managers;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import org.sequoia.seq.client.SeqClient;
import org.sequoia.seq.events.DiscordChatEvent;
import org.sequoia.seq.network.ConnectionManager;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Bridges Wynncraft guild chat ↔ Discord via the Sequoia backend.
 * <p>
 * Outgoing: intercepts Wynncraft guild chat, resolves nicknames to real usernames
 * via component insertion tags, and sends to backend.
 * Incoming: listens for Discord chat WS messages, displays in MC chat.
 */
public class ChatManager {

    /**
     * Aqua text color (0x55FFFF / §b) used by Wynncraft for guild chat messages.
     * Adapted from Wynntils' RecipientType.GUILD foreground pattern {@code ^§b(...).*$}.
     * Other message types use different colors: DMs use §#ddcc99ff, party uses §e,
     * shout uses §#bd45ffff, territory/battle info uses §c, etc.
     */
    private static final int GUILD_CHAT_COLOR = 0x55FFFF;
    // Nicknames may contain spaces (e.g. "Emanant Force"), so allow spaces in the
    // display-name capture group. DOTALL so the message group captures across \n.
    // The leading \\S+ skips the guild/rank icon prefix so we only match the first
    // "Name: message" occurrence, not something like "Server:" buried in the text.
    private static final Pattern CHAT_PATTERN = Pattern.compile(
        "\\S+\\s+([a-zA-Z0-9_][a-zA-Z0-9_ ]*[a-zA-Z0-9_]|[a-zA-Z0-9_]{3,16}):\\s*(.*)",
        Pattern.DOTALL
    );
    private static final Pattern HOVER_REAL_NAME_PATTERN = Pattern.compile(
        // Wynntils format: "<nick>'s real name is <username>"
        // Legacy format:   "Real Username: <username>"
        "(?:'s real name is\\s+|Real Username:\\s*)([a-zA-Z0-9_]{3,16})",
        Pattern.CASE_INSENSITIVE
    );

    public ChatManager() {
        registerIncomingHook();
    }

    // ── Outgoing: Wynncraft guild → backend → Discord ──

    /**
     * Called from {@link org.sequoia.seq.mixins.ClientPacketListenerMixin} at the
     * packet level, before Wynntils or Fabric's message API can cancel/reformat
     * the message. This ensures multiline guild messages are never missed.
     */
    public static void onSystemChat(Component message) {
        // Guild chat uses aqua color (§b / 0x55FFFF) per Wynntils' RecipientType.GUILD.
        // This cleanly rejects DMs, party, shout, territory, and other message types
        // that share the \uDAFF\uDFFC icon prefix but use different colors.
        var color = message.getStyle().getColor();
        if (color == null || color.getValue() != GUILD_CHAT_COLOR) return;

        String rawText = message.getString();

        SeqClient.LOGGER.info("[ChatManager] Guild message detected: {}", rawText);

        if (!ConnectionManager.isConnected()) {
            SeqClient.LOGGER.warn("[ChatManager] Dropping guild message: not connected/authenticated");
            return;
        }

        ParsedMessage parsed = parseGuildMessage(message);
        if (parsed == null) {
            SeqClient.LOGGER.warn("[ChatManager] Failed to parse guild message: {}", rawText);
            return;
        }

        SeqClient.LOGGER.info("[ChatManager] Parsed -> username='{}' avatarUrl='{}' message='{}'",
            parsed.username(), parsed.avatarUrl(), parsed.message());

        ConnectionManager.getInstance().sendGuildChat(parsed.username(), parsed.message(), parsed.avatarUrl());
    }

    /**
     * Extracts real username and message content from Wynncraft guild chat.
     * 
     * <p>Wynncraft sends nicknames as the visible text, but puts the real username
     * in the Style's insertion field (used for shift-click @mentions). We look for
     * the component with a valid username insertion to identify the speaker.
     */
    private static ParsedMessage parseGuildMessage(Component message) {
        String rawText = message.getString();
        Matcher matcher = CHAT_PATTERN.matcher(rawText);

        if (!matcher.find()) {
            SeqClient.LOGGER.warn("[ChatManager] CHAT_PATTERN did not match: '{}'", rawText);
            return null; // Not a standard "Name: Message" format
        }

        String displayedName = matcher.group(1);
        String content = matcher.group(2)
            .replaceAll("[\\n\\r]+", " ")           // collapse newlines into spaces
            .replaceAll("\\p{C}", "")               // strip all Unicode "Other" chars (control, format, private-use, surrogates, unassigned)
            .replaceAll(" {2,}", " ")               // collapse multiple spaces
            .trim();

        if (content.isEmpty()) return null;

        // Search the component tree for the real username
        String realUsername = findRealUsername(message);
        SeqClient.LOGGER.info("[ChatManager] displayedName='{}' realUsername='{}'", displayedName, realUsername);

        String avatarUrl = "https://mc-heads.net/avatar/" + (realUsername != null ? realUsername : displayedName) + "/64";

        String username = realUsername != null ? realUsername + "/" + displayedName : displayedName;
        return new ParsedMessage(username, content, avatarUrl);
    }

    // Recursively search the component tree for an insertion tag
    private static String findRealUsername(Component component) {
        return findRealUsername(component, 0);
    }

    private static String findRealUsername(Component component, int depth) {
        String indent = "  ".repeat(depth);
        String text = component.getString();
        Style style = component.getStyle();

        String insertion = style.getInsertion();
        HoverEvent hoverEvent = style.getHoverEvent();
        SeqClient.LOGGER.info("[ChatManager] {}component text='{}' insertion='{}' hover={}",
            indent, text, insertion,
            hoverEvent != null ? hoverEvent.getClass().getSimpleName() : "null");

        // 1. Try insertion tag (Standard for non-nicked players)
        if (insertion != null && insertion.matches("[a-zA-Z0-9_]{3,16}")) {
            SeqClient.LOGGER.info("[ChatManager] {}  → found via insertion: '{}'", indent, insertion);
            return insertion;
        }

        // 2. Try Hover Event (Wynntils method for nicked players)
        if (hoverEvent instanceof HoverEvent.ShowText showTextEvent) {
            Component hoverComponent = showTextEvent.value();
            if (hoverComponent != null) {
                String hoverText = hoverComponent.getString();
                SeqClient.LOGGER.info("[ChatManager] {}  hover text: '{}'", indent, hoverText);
                Matcher matcher = HOVER_REAL_NAME_PATTERN.matcher(hoverText);
                if (matcher.find()) {
                    SeqClient.LOGGER.info("[ChatManager] {}  → found via hover: '{}'", indent, matcher.group(1));
                    return matcher.group(1);
                }
            }
        }

        // 3. Search children components
        for (Component sibling : component.getSiblings()) {
            String found = findRealUsername(sibling, depth + 1);
            if (found != null) {
                return found;
            }
        }

        return null;
    }

    // ── Incoming: Discord → backend → WS → MC chat ──

    private void registerIncomingHook() {
        ConnectionManager.onDiscordChat(msg -> {
            if (!SeqClient.getShowDiscordChatSetting().getValue()) return;

            String formatted = "§3[§bDiscord§3] §f" + msg.username() + "§7: §r" + msg.message();
            if (SeqClient.mc.player != null) {
                SeqClient.mc.player.displayClientMessage(Component.literal(formatted), false);
            }

            if (SeqClient.getEventBus() != null) {
                SeqClient.getEventBus().dispatch(new DiscordChatEvent(msg.username(), msg.message()));
            }
        });
    }

    private record ParsedMessage(String username, String message, String avatarUrl) {}
}