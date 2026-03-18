package org.sequoia.seq.managers;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.network.chat.Component;
import org.sequoia.seq.client.SeqClient;
import org.sequoia.seq.network.ConnectionManager;

/**
 * Detects Wynncraft guild-bank messages and forwards structured events to the
 * backend over WebSocket.
 */
public final class GuildBankTracker {
    private static final Pattern GUILD_BANK_PATTERN = Pattern.compile(
            "^(.+?)\\s+(deposited|withdrew)\\s+(.+?)\\s+(to|from)\\s+the Guild Bank\\s+\\((.+)\\)$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern QUANTITY_PATTERN = Pattern.compile("^(?:(\\d+)x\\s+)?(.+)$");
    private static final Pattern CHARGES_PATTERN = Pattern.compile("^(.*?)(?:\\s+\\[(\\d+/\\d+)\\])?$");
    private static final Duration DEDUPE_WINDOW = Duration.ofSeconds(2);

    private static GuildBankTracker instance;

    private volatile String lastSentMessage;
    private volatile Instant lastSentAt = Instant.EPOCH;

    public static synchronized GuildBankTracker getInstance() {
        if (instance == null) {
            instance = new GuildBankTracker();
        }
        return instance;
    }

    public void onSystemChat(Component message) {
        GuildBankEvent event = parseEvent(message);
        if (event == null) {
            return;
        }
        if (isDuplicate(event.rawMessage(), Instant.now())) {
            SeqClient.LOGGER.debug("[GuildBank] Duplicate guild-bank message suppressed: {}", event.rawMessage());
            return;
        }
        ConnectionManager.getInstance().sendGuildBankEvent(
                event.action().wireValue(),
                event.player(),
                event.quantity(),
                event.itemName(),
                event.charges(),
                event.accessTier(),
                event.rawMessage());
    }

    GuildBankEvent parseEvent(Component message) {
        if (message == null) {
            return null;
        }
        return parseEvent(message.getString());
    }

    GuildBankEvent parseEvent(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return null;
        }
        if (!rawText.contains("Guild Bank")) {
            return null;
        }
        if (!rawText.contains("deposited") && !rawText.contains("withdrew")) {
            return null;
        }

        String cleaned = normalizeForParsing(rawText);
        Matcher matcher = GUILD_BANK_PATTERN.matcher(cleaned);
        if (!matcher.matches()) {
            return null;
        }

        String player = matcher.group(1).trim();
        String actionToken = matcher.group(2).toLowerCase();
        String itemBlock = matcher.group(3).trim();
        String accessTier = matcher.group(5).trim();

        Matcher quantityMatcher = QUANTITY_PATTERN.matcher(itemBlock);
        if (!quantityMatcher.matches()) {
            return null;
        }

        Integer quantity = quantityMatcher.group(1) == null ? null : Integer.parseInt(quantityMatcher.group(1));
        String itemWithOptionalCharges = quantityMatcher.group(2).trim();

        Matcher chargesMatcher = CHARGES_PATTERN.matcher(itemWithOptionalCharges);
        if (!chargesMatcher.matches()) {
            return null;
        }

        String itemName = chargesMatcher.group(1).trim();
        if (itemName.isEmpty()) {
            return null;
        }

        return new GuildBankEvent(
                "deposited".equals(actionToken) ? GuildBankAction.DEPOSIT : GuildBankAction.WITHDRAWAL,
                player,
                quantity,
                itemName,
                chargesMatcher.group(2),
                accessTier,
                cleaned);
    }

    static String normalizeForParsing(String rawText) {
        return rawText
                .replaceAll("[\\n\\r]+", " ")
                .replaceAll("[\\p{C}\\p{Co}]", "")
                .replace('§', ' ')
                .replaceAll(" +", " ")
                .trim();
    }

    private synchronized boolean isDuplicate(String rawMessage, Instant now) {
        if (Objects.equals(rawMessage, lastSentMessage)
                && Duration.between(lastSentAt, now).compareTo(DEDUPE_WINDOW) < 0) {
            return true;
        }
        lastSentMessage = rawMessage;
        lastSentAt = now;
        return false;
    }

    enum GuildBankAction {
        DEPOSIT,
        WITHDRAWAL;

        String wireValue() {
            return this == DEPOSIT ? "deposit" : "withdrawal";
        }
    }

    record GuildBankEvent(
            GuildBankAction action,
            String player,
            Integer quantity,
            String itemName,
            String charges,
            String accessTier,
            String rawMessage) {
    }
}
