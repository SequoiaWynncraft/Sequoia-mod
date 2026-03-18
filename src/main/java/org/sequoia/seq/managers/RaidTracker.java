package org.sequoia.seq.managers;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import net.minecraft.network.chat.Component;
import org.sequoia.seq.client.SeqClient;
import org.sequoia.seq.network.ConnectionManager;
import org.sequoia.seq.utils.PacketTextNormalizer;

/**
 * Detects raid completions from Wynncraft chat and announces them to the guild.
 * <p>
 * Called from {@link org.sequoia.seq.mixins.ClientPacketListenerMixin} at the
 * packet level, before Wynntils or Fabric's message API can cancel/reformat
 * the message. Wynncraft sends raid completions as multiline system chat with
 * Unicode private-use prefix characters on each line, e.g.:
 * 
 * <pre>
 * \uDBFF\uDFFC\uDBFF\uDFFF\uDBFF\uDFFE Player1, Player2, and Player3
 * \uDBFF\uDFFC\uDC00\uDC06  finished The Nameless Anomaly and claimed 2x Aspects,
 * \uDBFF\uDFFC\uDC00\uDC06 2048x Emeralds, +10367m Guild Experience, and +410
 * \uDBFF\uDFFC\uDC00\uDC06 Seasonal Rating
 * </pre>
 */
public class RaidTracker {

    /**
     * Pattern for Wynncraft raid completion (applied after Unicode/whitespace
     * cleanup).
     * Group 1: Comma-separated player names (with optional "and")
     * Group 2: Raid name (e.g., "The Canyon Colossus")
     * Group 3: Aspects count
     * Group 4: Emeralds count
     * Group 5: Guild Experience (numeric value before 'm')
     * Group 6: Seasonal Rating
     */
    private static final Pattern RAID_FINISH_PATTERN = Pattern.compile(
            "(.+?)\\s+finished\\s+(.+?)\\s+and claimed\\s+(\\d+)x Aspects,\\s+(\\d+)x Emeralds,\\s+(?:and\\s+)?\\+([\\d.]+)m Guild Experience(?:,\\s+and\\s+\\+(\\d+)\\s+Seasonal Rating)?");
    private static final Pattern USERNAME_PATTERN = Pattern.compile("[a-zA-Z0-9_]{3,16}");
    private static final Pattern COMMA_SPACING_PATTERN = Pattern.compile("\\s*,\\s*");
    /**
     * Called from {@link org.sequoia.seq.mixins.ClientPacketListenerMixin} at the
     * packet level for every non-overlay system chat message.
     */
    public static void onSystemChat(Component message) {
        if (!ConnectionManager.isConnected())
            return;
        if (!SeqClient.getRaidAutoAnnounceSetting().getValue())
            return;

        ParsedRaidCompletion completion = parseRaidCompletion(message);
        if (completion == null) {
            return;
        }

        ConnectionManager instance = ConnectionManager.getInstance();
        if (instance != null && !completion.partyMembers().isEmpty()) {
            instance.sendRaidAnnouncement(
                    completion.partyMembers(),
                    completion.raidName(),
                    completion.aspects(),
                    completion.emeralds(),
                    completion.guildExp(),
                    completion.seasonalRating());
        }
    }

    static ParsedRaidCompletion parseRaidCompletion(Component message) {
        if (message == null) {
            return null;
        }

        String plain = message.getString();

        // Quick keyword gate — skip cleanup & regex for the vast majority of messages.
        // Seasonal Rating is optional, so gate on core raid-completion tokens only.
        if (!plain.contains("finished") || !plain.contains("and claimed") || !plain.contains("Guild Experience"))
            return null;

        String cleaned = normalizeForRaidParsing(plain);

        Matcher matcher = RAID_FINISH_PATTERN.matcher(cleaned);
        if (!matcher.find())
            return null;

        List<String> parsedDisplayedNames = parseDisplayedNames(matcher.group(1));

        List<String> extractedRealNames = ChatManager.extractRealUsernames(message);
        List<String> partyMembers = resolvePartyMembers(parsedDisplayedNames, extractedRealNames);

        if (partyMembers.isEmpty()) {
            SeqClient.LOGGER
                    .warn("[RaidTracker] Dropping raid announcement: no valid usernames found in completion message");
            return null;
        }

        String raidName = matcher.group(2);
        int aspects = Integer.parseInt(matcher.group(3));
        int emeralds = Integer.parseInt(matcher.group(4));
        // Wynncraft reports XP in millions (e.g. "10367m" = 10,367,000) — divide
        // by 1000 so the backend receives a friendlier value (10367 -> 10.367).
        double guildExp = Double.parseDouble(matcher.group(5)) / 1000.0;
        int seasonalRating = matcher.group(6) != null ? Integer.parseInt(matcher.group(6)) : 0;

        return new ParsedRaidCompletion(partyMembers, raidName, aspects, emeralds, guildExp, seasonalRating);
    }

    static String normalizeForRaidParsing(String rawText) {
        return PacketTextNormalizer.normalizeForParsing(rawText)
                .replace(",and ", ", and ");
    }

    private static List<String> parseDisplayedNames(String namesPart) {
        String canonicalNames = namesPart
                .replace(", and ", ", ")
                .replace(" and ", ", ")
                .trim();

        return COMMA_SPACING_PATTERN.splitAsStream(canonicalNames)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    private static List<String> resolvePartyMembers(List<String> parsedDisplayedNames,
            List<String> extractedRealNames) {
        List<String> validExtractedNames = extractedRealNames == null
                ? List.of()
                : extractedRealNames.stream()
                        .filter(name -> USERNAME_PATTERN.matcher(name).matches())
                        .toList();

        if (validExtractedNames.isEmpty()) {
            return parsedDisplayedNames.stream()
                    .filter(name -> USERNAME_PATTERN.matcher(name).matches())
                    .toList();
        }

        int invalidDisplayedCount = (int) parsedDisplayedNames.stream()
                .filter(name -> !USERNAME_PATTERN.matcher(name).matches())
                .count();
        int usernameLikeReplacementBudget = Math.max(0, validExtractedNames.size() - invalidDisplayedCount);
        int extractedIndex = 0;

        Set<String> resolved = new LinkedHashSet<>();
        for (String displayedName : parsedDisplayedNames) {
            boolean usernameLike = USERNAME_PATTERN.matcher(displayedName).matches();

            if (!usernameLike) {
                if (extractedIndex < validExtractedNames.size()) {
                    resolved.add(validExtractedNames.get(extractedIndex++));
                }
                continue;
            }

            if (usernameLikeReplacementBudget > 0 && extractedIndex < validExtractedNames.size()) {
                resolved.add(validExtractedNames.get(extractedIndex++));
                usernameLikeReplacementBudget--;
                continue;
            }

            resolved.add(displayedName);
        }

        return List.copyOf(new ArrayList<>(resolved));
    }

    record ParsedRaidCompletion(
            List<String> partyMembers,
            String raidName,
            int aspects,
            int emeralds,
            double guildExp,
            int seasonalRating) {
    }
}
