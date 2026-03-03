package org.sequoia.seq.managers;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import net.minecraft.network.chat.Component;
import org.sequoia.seq.client.SeqClient;
import org.sequoia.seq.network.ConnectionManager;

/**
 * Detects raid completions from Wynncraft chat and announces them to the guild.
 * <p>
 * Called from {@link org.sequoia.seq.mixins.ClientPacketListenerMixin} at the
 * packet level, before Wynntils or Fabric's message API can cancel/reformat
 * the message. Wynncraft sends raid completions as multiline system chat with
 * Unicode private-use prefix characters on each line, e.g.:
 * <pre>
 * \uDBFF\uDFFC\uDBFF\uDFFF\uDBFF\uDFFE Player1, Player2, and Player3
 * \uDBFF\uDFFC\uDC00\uDC06  finished The Nameless Anomaly and claimed 2x Aspects,
 * \uDBFF\uDFFC\uDC00\uDC06 2048x Emeralds, +10367m Guild Experience, and +410
 * \uDBFF\uDFFC\uDC00\uDC06 Seasonal Rating
 * </pre>
 */
public class RaidTracker {

    /**
     * Pattern for Wynncraft raid completion (applied after Unicode/whitespace cleanup).
     * Group 1: Comma-separated player names (with optional "and")
     * Group 2: Raid name (e.g., "The Canyon Colossus")
     * Group 3: Aspects count
     * Group 4: Emeralds count
     * Group 5: Guild Experience (numeric value before 'm')
     * Group 6: Seasonal Rating
     */
    private static final Pattern RAID_FINISH_PATTERN = Pattern.compile(
        "(.+?)\\s+finished\\s+(.+?)\\s+and claimed\\s+(\\d+)x Aspects,\\s+(\\d+)x Emeralds,\\s+(?:and\\s+)?\\+([\\d.]+)m Guild Experience(?:,\\s+and\\s+\\+(\\d+)\\s+Seasonal Rating)?"
    );

    /**
     * Called from {@link org.sequoia.seq.mixins.ClientPacketListenerMixin} at the
     * packet level for every non-overlay system chat message.
     */
    public static void onSystemChat(Component message) {
        if (!ConnectionManager.isConnected()) return;
        if (!SeqClient.getRaidAutoAnnounceSetting().getValue()) return;

        String plain = message.getString();

        // Quick keyword gate — skip cleanup & regex for the vast majority of messages.
        if (
            !plain.contains("finished") || !plain.contains("Seasonal Rating")
        ) return;

        // Strip newlines, Unicode control/format/private-use/surrogate chars,
        // and collapse multiple spaces — same cleanup ChatManager uses.
        String cleaned = plain
            .replaceAll("[\\n\\r]+", " ")
            .replaceAll("\\p{C}", "")
            .replaceAll(" {2,}", " ")
            .trim();

        Matcher matcher = RAID_FINISH_PATTERN.matcher(cleaned);
        if (!matcher.find()) return;

        // Parse player names: "A, B, C, and D" -> [A, B, C, D]
        String namesPart = matcher.group(1).replace(", and ", ", ");
        List<String> partyMembers = Arrays.stream(namesPart.split(", "))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .collect(Collectors.toList());

        String raidName = matcher.group(2);
        int aspects = Integer.parseInt(matcher.group(3));
        int emeralds = Integer.parseInt(matcher.group(4));
        // Wynncraft reports XP in millions (e.g. "10367m" = 10,367,000) — divide
        // by 1000 so the backend receives a friendlier value (10367 -> 10.367).
        double guildExp = Double.parseDouble(matcher.group(5)) / 1000.0;
        int seasonalRating =
            matcher.group(6) != null ? Integer.parseInt(matcher.group(6)) : 0;

        ConnectionManager instance = ConnectionManager.getInstance();
        if (instance != null && !partyMembers.isEmpty()) {
            instance.sendRaidAnnouncement(
                partyMembers,
                raidName,
                aspects,
                emeralds,
                guildExp,
                seasonalRating
            );
        }
    }
}
