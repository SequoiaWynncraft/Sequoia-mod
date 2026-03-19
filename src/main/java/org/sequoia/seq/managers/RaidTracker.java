package org.sequoia.seq.managers;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.PlainTextContents;
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
    private static final String FINISHED_BOUNDARY = " finished ";
    /**
     * Called from {@link org.sequoia.seq.mixins.ClientPacketListenerMixin} at the
     * packet level for every non-overlay system chat message.
     */
    public static void onSystemChat(Component message) {
        String plain = message != null ? message.getString() : null;
        boolean raidCandidate = isRaidCandidateText(plain);

        if (!ConnectionManager.isConnected()) {
            if (raidCandidate) {
                SeqClient.LOGGER.warn(
                        "[RaidTracker] Skipping raid candidate because backend is disconnected raw='{}'",
                        abbreviateForLog(plain));
            }
            return;
        }
        if (!SeqClient.getRaidAutoAnnounceSetting().getValue()) {
            if (raidCandidate) {
                SeqClient.LOGGER.info(
                        "[RaidTracker] Skipping raid candidate because auto-announce is disabled raw='{}'",
                        abbreviateForLog(plain));
            }
            return;
        }

        if (raidCandidate) {
            SeqClient.LOGGER.info("[RaidTracker] Processing raid candidate raw='{}'", abbreviateForLog(plain));
        }

        ParsedRaidCompletion completion = parseRaidCompletion(message);
        if (completion == null) {
            if (raidCandidate) {
                SeqClient.LOGGER.warn(
                        "[RaidTracker] Raid candidate did not produce a parsed completion raw='{}'",
                        abbreviateForLog(plain));
            }
            return;
        }

        ConnectionManager instance = ConnectionManager.getInstance();
        if (instance != null && !completion.partyMembers().isEmpty()) {
            SeqClient.LOGGER.info(
                    "[RaidTracker] Forwarding raid completion raid='{}' members={} aspects={} emeralds={} guildExp={} seasonalRating={}",
                    completion.raidName(),
                    completion.partyMembers(),
                    completion.aspects(),
                    completion.emeralds(),
                    completion.guildExp(),
                    completion.seasonalRating());
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
        if (!isRaidCandidateText(plain))
            return null;

        String cleaned = normalizeForRaidParsing(plain);
        SeqClient.LOGGER.info("[RaidTracker] Normalized raid candidate cleaned='{}'", abbreviateForLog(cleaned));

        Matcher matcher = RAID_FINISH_PATTERN.matcher(cleaned);
        if (!matcher.find()) {
            SeqClient.LOGGER.warn(
                    "[RaidTracker] Regex did not match normalized raid candidate cleaned='{}'",
                    abbreviateForLog(cleaned));
            return null;
        }

        String namesPart = matcher.group(1);
        List<String> parsedDisplayedNames = parseDisplayedNames(namesPart);
        List<String> partyMembers = resolvePartyMembers(parsedDisplayedNames, message);
        SeqClient.LOGGER.info(
                "[RaidTracker] Parsed raid candidate namesPart='{}' displayedNames={} resolvedMembers={}",
                namesPart,
                parsedDisplayedNames,
                partyMembers);

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

        SeqClient.LOGGER.info(
                "[RaidTracker] Parsed raid completion raid='{}' aspects={} emeralds={} guildExp={} seasonalRating={}",
                raidName,
                aspects,
                emeralds,
                guildExp,
                seasonalRating);
        return new ParsedRaidCompletion(partyMembers, raidName, aspects, emeralds, guildExp, seasonalRating);
    }

    static String normalizeForRaidParsing(String rawText) {
        return PacketTextNormalizer.normalizeForParsing(rawText)
                .replace(",and ", ", and ");
    }

    private static boolean isRaidCandidateText(String plain) {
        return plain != null
                && plain.contains("finished")
                && plain.contains("and claimed")
                && plain.contains("Guild Experience");
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

    private static List<String> resolvePartyMembers(List<String> parsedDisplayedNames, Component message) {
        PrefixMetadata prefixMetadata = buildPrefixMetadata(message);
        boolean hasInvalidDisplayedNames = parsedDisplayedNames.stream()
                .anyMatch(name -> !USERNAME_PATTERN.matcher(name).matches());

        Set<String> resolved = new LinkedHashSet<>();
        int searchFrom = 0;
        for (String displayedName : parsedDisplayedNames) {
            if (displayedName.isBlank()) {
                continue;
            }

            int start = prefixMetadata.text().indexOf(displayedName, searchFrom);
            NameMetadata metadata = start >= 0
                    ? prefixMetadata.metadataForRange(start, start + displayedName.length())
                    : NameMetadata.empty();

            String resolvedName = resolveDisplayedName(displayedName, metadata, hasInvalidDisplayedNames);
            if (resolvedName != null) {
                resolved.add(resolvedName);
            }

            if (start >= 0) {
                searchFrom = start + displayedName.length();
            }
        }

        return List.copyOf(new ArrayList<>(resolved));
    }

    private static String resolveDisplayedName(
            String displayedName, NameMetadata metadata, boolean hasInvalidDisplayedNames) {
        String hoverRealName = metadata.firstHoverRealName();
        if (hoverRealName != null) {
            return hoverRealName;
        }

        boolean displayedLooksLikeUsername = USERNAME_PATTERN.matcher(displayedName).matches();
        if (displayedLooksLikeUsername && !hasInvalidDisplayedNames) {
            return displayedName;
        }

        String insertionName = metadata.firstInsertionName();
        if (insertionName != null && (!displayedLooksLikeUsername || hasInvalidDisplayedNames)) {
            return insertionName;
        }

        return displayedLooksLikeUsername ? displayedName : null;
    }

    private static PrefixMetadata buildPrefixMetadata(Component message) {
        List<MetaChar> characters = new ArrayList<>();
        for (Component fragment : message.toFlatList()) {
            appendNormalizedFragment(characters, fragment);
        }

        String fullText = toText(characters);
        int boundaryIndex = fullText.indexOf(FINISHED_BOUNDARY);
        if (boundaryIndex < 0) {
            return new PrefixMetadata(fullText, List.copyOf(characters));
        }

        List<MetaChar> prefixCharacters = new ArrayList<>(characters.subList(0, boundaryIndex));
        return new PrefixMetadata(toText(prefixCharacters), List.copyOf(prefixCharacters));
    }

    private static void appendNormalizedFragment(List<MetaChar> target, Component fragment) {
        String text = extractFragmentText(fragment);
        if (text.isEmpty()) {
            return;
        }

        Style style = fragment.getStyle();
        String hoverRealName = validUsername(ChatManager.extractHoverRealUsername(style));
        String insertionName = validUsername(ChatManager.extractInsertionUsername(style));

        boolean previousWasSpace = !target.isEmpty() && target.getLast().value() == ' ';
        for (int index = 0; index < text.length();) {
            int codePoint = text.codePointAt(index);
            index += Character.charCount(codePoint);

            if (Character.isWhitespace(codePoint) || isIgnorableForParsing(codePoint)) {
                if (!previousWasSpace) {
                    target.add(new MetaChar(' ', hoverRealName, insertionName));
                    previousWasSpace = true;
                }
                continue;
            }

            String value = new String(Character.toChars(codePoint));
            for (int charIndex = 0; charIndex < value.length(); charIndex++) {
                target.add(new MetaChar(value.charAt(charIndex), hoverRealName, insertionName));
            }
            previousWasSpace = false;
        }
    }

    private static String extractFragmentText(Component fragment) {
        if (fragment.getContents() instanceof PlainTextContents plainTextContents) {
            return plainTextContents.text();
        }
        return fragment.getString();
    }

    private static boolean isIgnorableForParsing(int codePoint) {
        return switch (Character.getType(codePoint)) {
            case Character.CONTROL,
                    Character.FORMAT,
                    Character.PRIVATE_USE,
                    Character.SURROGATE,
                    Character.UNASSIGNED -> true;
            default -> false;
        };
    }

    private static String validUsername(String candidate) {
        return candidate != null && USERNAME_PATTERN.matcher(candidate).matches() ? candidate : null;
    }

    private static String toText(List<MetaChar> characters) {
        StringBuilder builder = new StringBuilder(characters.size());
        for (MetaChar character : characters) {
            builder.append(character.value());
        }
        return builder.toString();
    }

    private static String abbreviateForLog(String value) {
        if (value == null) {
            return "null";
        }
        return value.length() <= 512 ? value : value.substring(0, 512) + "...";
    }

    private record MetaChar(char value, String hoverRealName, String insertionName) {}

    private record NameMetadata(List<String> hoverRealNames, List<String> insertionNames) {
        private static NameMetadata empty() {
            return new NameMetadata(List.of(), List.of());
        }

        private String firstHoverRealName() {
            return hoverRealNames.isEmpty() ? null : hoverRealNames.get(0);
        }

        private String firstInsertionName() {
            return insertionNames.isEmpty() ? null : insertionNames.get(0);
        }
    }

    private record PrefixMetadata(String text, List<MetaChar> characters) {
        private NameMetadata metadataForRange(int startInclusive, int endExclusive) {
            LinkedHashSet<String> hoverRealNames = new LinkedHashSet<>();
            LinkedHashSet<String> insertionNames = new LinkedHashSet<>();

            int cappedStart = Math.max(0, startInclusive);
            int cappedEnd = Math.min(endExclusive, characters.size());
            for (int index = cappedStart; index < cappedEnd; index++) {
                MetaChar character = characters.get(index);
                if (character.hoverRealName() != null) {
                    hoverRealNames.add(character.hoverRealName());
                }
                if (character.insertionName() != null) {
                    insertionNames.add(character.insertionName());
                }
            }

            return new NameMetadata(List.copyOf(hoverRealNames), List.copyOf(insertionNames));
        }
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
