package org.sequoia.seq.managers;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import org.sequoia.seq.accessors.NotificationAccessor;
import org.sequoia.seq.client.SeqClient;
import org.sequoia.seq.events.DiscordChatEvent;
import org.sequoia.seq.network.ConnectionManager;
import org.sequoia.seq.utils.PacketTextNormalizer;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.time.Duration;
import java.time.Instant;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.sequoia.seq.client.SeqClient.mc;

/**
 * Bridges Wynncraft guild chat ↔ Discord via the Sequoia backend.
 * <p>
 * Outgoing: intercepts Wynncraft guild chat, resolves nicknames to real
 * usernames
 * via component insertion tags, and sends to backend.
 * Incoming: listens for Discord chat WS messages, displays in MC chat.
 */
public class ChatManager {

    /**
     * Aqua text color (0x55FFFF / §b) used by Wynncraft for guild chat messages.
     * Adapted from Wynntils' RecipientType.GUILD foreground pattern
     * {@code ^§b(...).*$}.
     * Other message types use different colors: DMs use §#ddcc99ff, party uses §e,
     * shout uses §#bd45ffff, territory/battle info uses §c, etc.
     */
    private static final int GUILD_CHAT_COLOR = 0x55FFFF;
    // Nicknames may contain spaces (e.g. "Emanant Force"), so allow spaces in the
    // display-name capture group. DOTALL so the message group captures across \n.
    // Packet-level normalization strips the icon/banner glyph spam before matching.
    private static final Pattern CHAT_PATTERN = Pattern.compile(
            "^([a-zA-Z0-9_][a-zA-Z0-9_ ]*[a-zA-Z0-9_]|[a-zA-Z0-9_]{3,16})\\s*:\\s*(.*)$",
            Pattern.DOTALL);
    private static final Pattern HOVER_REAL_NAME_PATTERN = Pattern.compile(
            // Wynntils format: "<nick>'s real name is <username>"
            // Also accepts nicknames ending in 's' where Wynncraft uses "<nick>' real name
            // is <username>"
            // Legacy format: "Real Username: <username>"
            "(?:'(?:s)? real name is\\s+|Real Username:\\s*)([a-zA-Z0-9_]{3,16})",
            Pattern.CASE_INSENSITIVE);
    private static final Duration OUTGOING_DEDUPE_WINDOW = Duration.ofMillis(750);
    private static volatile String lastOutgoingKey;
    private static volatile Instant lastOutgoingAt = Instant.EPOCH;

    private static final Pattern WYNNCRAFT_WELCOME_PATTERN = Pattern.compile("§6§lWelcome to Wynncraft!");

    private static boolean firstConnect = true;

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
        // Bumliotech goon parser to auto goon.
        Matcher welcomeMatcher = WYNNCRAFT_WELCOME_PATTERN.matcher(message.getString());
        if (SeqClient.getAutoConnectSetting().getValue() && welcomeMatcher.find() && mc.player != null
                && !ConnectionManager.getInstance().isOpen() && firstConnect) {
            firstConnect = !firstConnect;
            mc.execute(() -> mc.player.connection.sendCommand("seq connect"));
        }

        // Guild chat uses aqua color (§b / 0x55FFFF) per Wynntils' RecipientType.GUILD.
        // This cleanly rejects DMs, party, shout, territory, and other message types
        // that share the \uDAFF\uDFFC icon prefix but use different colors.
        var color = message.getStyle().getColor();
        if (color == null || color.getValue() != GUILD_CHAT_COLOR)
            return;

        if (!ConnectionManager.isConnected())
            return;

        ParsedMessage parsed = parseGuildMessage(message);
        if (parsed == null)
            return;

        if (isDuplicateOutgoing(parsed.username(), parsed.message())) {
            SeqClient.LOGGER.debug(
                    "[GuildChat] Duplicate outgoing guild chat ignored username='{}' content='{}'",
                    parsed.username(),
                    parsed.message());
            return;
        }

        SeqClient.LOGGER.info(
                "[GuildChat] Forwarding parsed guild chat username='{}' nickname='{}' content='{}' avatar='{}'",
                parsed.username(),
                parsed.nickname(),
                parsed.message(),
                parsed.avatarUrl());

        ConnectionManager.getInstance().sendGuildChat(
                parsed.username(),
                parsed.nickname(),
                parsed.message(),
                parsed.avatarUrl());
    }

    /**
     * Extracts real username and message content from Wynncraft guild chat.
     * 
     * <p>
     * Wynncraft sends nicknames as the visible text, but puts the real username
     * in the Style's insertion field (used for shift-click @mentions). We look for
     * the component with a valid username insertion to identify the speaker.
     */
    static ParsedMessage parseGuildMessage(Component message) {
        String cleaned = PacketTextNormalizer.normalizeForParsing(message == null ? null : message.getString());
        Matcher matcher = CHAT_PATTERN.matcher(cleaned);

        if (!matcher.find())
            return null;

        String displayedName = matcher.group(1).trim();
        String content = matcher.group(2).trim();

        if (content.isEmpty())
            return null;

        // Search the component tree for the real username. Prefer candidates that
        // differ from the displayed nickname (e.g. nicked/decorated names).
        String realUsername = findRealUsername(message, displayedName);
        String avatarUsername = resolveAvatarUsername(displayedName, realUsername);
        if (avatarUsername == null) {
            SeqClient.LOGGER.warn(
                    "[GuildChat] Dropping guild chat: no avatar-safe username displayed='{}' real='{}'",
                    displayedName,
                    realUsername);
            return null;
        }

        if (!avatarUsername.equals(displayedName) && realUsername == null) {
            SeqClient.LOGGER.debug(
                    "[GuildChat] Using normalized fallback username='{}' from displayed='{}'",
                    avatarUsername,
                    displayedName);
        }

        String avatarUrl = "https://mc-heads.net/avatar/"
                + URLEncoder.encode(avatarUsername, StandardCharsets.UTF_8).replace("+", "%20")
                + "/128";
        String nickname = deriveNickname(displayedName, avatarUsername);
        return new ParsedMessage(avatarUsername, nickname, content, avatarUrl);
    }

    private static String deriveNickname(String displayedName, String actualUsername) {
        if (displayedName == null) {
            return null;
        }
        String trimmed = displayedName.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (actualUsername != null && trimmed.equalsIgnoreCase(actualUsername)) {
            return null;
        }
        return trimmed;
    }

    private static String resolveAvatarUsername(String displayedName, String realUsername) {
        if (realUsername != null && realUsername.matches("[a-zA-Z0-9_]{3,16}")) {
            return realUsername;
        }
        if (displayedName != null && displayedName.matches("[a-zA-Z0-9_]{3,16}")) {
            return displayedName;
        }

        String normalized = displayedName == null ? "" : displayedName.replaceAll("[^a-zA-Z0-9_]", "");
        if (normalized.matches("[a-zA-Z0-9_]{3,16}")) {
            return normalized;
        }

        return null;
    }

    private static boolean isDuplicateOutgoing(String username, String message) {
        String key = username + "\u0000" + message;
        Instant now = Instant.now();

        synchronized (ChatManager.class) {
            if (key.equals(lastOutgoingKey)
                    && Duration.between(lastOutgoingAt, now).compareTo(OUTGOING_DEDUPE_WINDOW) < 0) {
                return true;
            }
            lastOutgoingKey = key;
            lastOutgoingAt = now;
            return false;
        }
    }

    private static String findRealUsername(Component component, String displayedName) {
        List<String> usernames = extractRealUsernames(component);
        if (usernames.isEmpty()) {
            return null;
        }

        if (displayedName == null || displayedName.isBlank()) {
            return usernames.get(0);
        }

        String displayedTrimmed = displayedName.trim();
        String displayedLower = displayedTrimmed.toLowerCase();

        String bestPrefixExpansion = null;
        for (String candidate : usernames) {
            if (candidate == null) {
                continue;
            }
            String candidateLower = candidate.toLowerCase();
            if (candidateLower.equals(displayedLower)) {
                continue;
            }
            if (candidateLower.startsWith(displayedLower)
                    && candidate.length() > displayedTrimmed.length()
                    && (bestPrefixExpansion == null || candidate.length() > bestPrefixExpansion.length())) {
                bestPrefixExpansion = candidate;
            }
        }
        if (bestPrefixExpansion != null) {
            return bestPrefixExpansion;
        }

        for (String candidate : usernames) {
            if (candidate != null && !candidate.equalsIgnoreCase(displayedTrimmed)) {
                return candidate;
            }
        }

        return usernames.get(0);
    }

    public static List<String> extractRealUsernames(Component component) {
        if (component == null) {
            return List.of();
        }

        Set<String> usernames = new LinkedHashSet<>();
        collectRealUsernames(component, usernames);
        return List.copyOf(new ArrayList<>(usernames));
    }

    private static void collectRealUsernames(Component component, Set<String> usernames) {
        collectRealUsernames(component, usernames, 0);
    }

    private static void collectRealUsernames(Component component, Set<String> usernames, int depth) {
        if (component == null) {
            return;
        }

        Style style = component.getStyle();

        String insertion = style.getInsertion();
        HoverEvent hoverEvent = style.getHoverEvent();

        if (hoverEvent instanceof HoverEvent.ShowText showTextEvent) {
            Component hoverComponent = showTextEvent.value();
            if (hoverComponent != null) {
                String hoverText = hoverComponent.getString();
                Matcher matcher = HOVER_REAL_NAME_PATTERN.matcher(hoverText);
                if (matcher.find()) {
                    usernames.add(matcher.group(1));
                }
            }
        }

        if (insertion != null && insertion.matches("[a-zA-Z0-9_]{3,16}")) {
            usernames.add(insertion);
        }

        for (Component sibling : component.getSiblings()) {
            collectRealUsernames(sibling, usernames, depth + 1);
        }
    }

    // ── Incoming: Discord → backend → WS → MC chat ──

    private void registerIncomingHook() {
        ConnectionManager.onDiscordChat(msg -> {
            if (!SeqClient.getShowDiscordChatSetting().getValue())
                return;

            mc.execute(() -> {
                if (mc.player != null) {
                    MutableComponent formatted = NotificationAccessor.prefixComponent()
                            .append(Component.literal(msg.username()).withStyle(ChatFormatting.WHITE))
                            .append(Component.literal(": ").withStyle(ChatFormatting.GRAY))
                            .append(Component.literal(msg.message()).withStyle(ChatFormatting.WHITE));
                    mc.player.displayClientMessage(formatted, false);
                }

                if (SeqClient.getEventBus() != null) {
                    SeqClient.getEventBus().dispatch(new DiscordChatEvent(msg.username(), msg.message()));
                }
            });
        });
    }

    record ParsedMessage(String username, String nickname, String message, String avatarUrl) {
    }
}
