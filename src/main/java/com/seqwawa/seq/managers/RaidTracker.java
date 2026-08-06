package com.seqwawa.seq.managers;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.seqwawa.seq.mixins.ClientPacketListenerMixin;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.PlainTextContents;
import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.network.ConnectionManager;
import com.seqwawa.seq.ui.PrincessRaidCelebration;
import com.seqwawa.seq.utils.PacketTextNormalizer;

/**
 * Detects raid completions from Wynncraft chat and announces them to the guild.
 * <p>
 * Called from {@link ClientPacketListenerMixin} at the
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
     * Group 3: Reward clause, when present
     * Group 4: Guild Experience (numeric value before 'm')
     * Group 5: Seasonal Rating
     */
    private static final Pattern RAID_FINISH_PATTERN = Pattern.compile(
            "^(.+?) finished ([\\w ']+?) "
            + "and claimed\\s+(?:(.*?)(?:,\\s*)?(?:and\\s+)?)?"
            + "\\+(\\d+)m Guild Experience(?:, and \\+(\\d+) Seasonal Rating)?$");
    private static final Pattern RAID_REWARD_PATTERN =
            Pattern.compile("(?i)(?:(\\d+)x|no) (Emeralds?|Aspects?)");
    private static final Pattern USERNAME_PATTERN = Pattern.compile("\\w{3,16}");
    private static final Pattern PARENTHESIZED_USERNAME_PATTERN = Pattern.compile(".*\\((\\w{3,16})\\)$");
    private static final Pattern COMMA_SPACING_PATTERN = Pattern.compile("\\s*,\\s*");
    private static final String FINISHED_BOUNDARY = " finished ";
    private static final int DEFAULT_MAX_RAID_PARTY_MEMBERS = 4;
    private static final int ANNIHILATION_MAX_RAID_PARTY_MEMBERS = 10;
    private static final String RAID_FAILED_TEXT = "raid failed";
    /**
     * Called from {@link ClientPacketListenerMixin} at the
     * packet level for every non-overlay system chat message.
     */
    public static void onSystemChat(Component message) {
        String plain = message != null ? message.getString() : null;
        boolean raidCandidate = isRaidCandidateText(plain);
        if (!raidCandidate) {
            return;
        }
        SeqClient.LOGGER.info("[RaidTracker] Processing raid candidate raw='{}'", abbreviateForLog(plain));

        ParsedRaidCompletion completion = parseRaidCompletion(message);
        if (completion == null) {
            SeqClient.LOGGER.warn(
                    "[RaidTracker] Raid candidate did not produce a parsed completion raw='{}'",
                    abbreviateForLog(plain));
            return;
        }

        ResolvedRaidCompletion resolved = resolveForClient(completion, localMinecraftUsername());
        finishLocalCompletion(resolved);

        if (!ConnectionManager.isConnected()) {
            SeqClient.LOGGER.warn(
                    "[RaidTracker] Skipping raid announcement because backend is disconnected raw='{}'",
                    abbreviateForLog(plain));
            return;
        }
        if (SeqClient.getRaidAutoAnnounceSetting() == null
                || !SeqClient.getRaidAutoAnnounceSetting().getValue()) {
            SeqClient.LOGGER.info(
                    "[RaidTracker] Skipping raid announcement because auto-announce is disabled raw='{}'",
                    abbreviateForLog(plain));
            return;
        }

        ConnectionManager instance = ConnectionManager.getInstance();
        if (instance == null || resolved.partyMembers().isEmpty()) {
            return;
        }
        SeqClient.LOGGER.info(
                "[RaidTracker] Forwarding raid completion raid='{}' members={} aspects={} emeralds={} guildExp={} seasonalRating={}",
                completion.raidName(),
                resolved.partyMembers(),
                completion.aspects(),
                completion.emeralds(),
                completion.guildExp(),
                completion.seasonalRating());
        instance.sendRaidAnnouncement(
                resolved.partyMembers(),
                completion.raidName(),
                completion.aspects(),
                completion.emeralds(),
                completion.guildExp(),
                completion.seasonalRating());
    }

    static ResolvedRaidCompletion resolveForClient(
            ParsedRaidCompletion completion, String localMinecraftUsername) {
        boolean localCompletion = isLocalCompletion(completion.partyMembers(), localMinecraftUsername);
        List<String> partyMembers = localCompletion
                ? RaidPartySnapshotTracker.resolvePartyMembers(
                        completion.partyMembers(),
                        completion.displayedPartySize(),
                        maximumPartySize(completion.raidName()))
                : completion.partyMembers();
        return new ResolvedRaidCompletion(partyMembers, localCompletion);
    }

    static void finishLocalCompletion(ResolvedRaidCompletion completion) {
        finishLocalCompletion(completion, PrincessRaidCelebration::triggerIfEnabled);
    }

    static void finishLocalCompletion(ResolvedRaidCompletion completion, Runnable completionEffect) {
        if (completion.localCompletion()) {
            RaidPartySnapshotTracker.onRaidCompleted();
            completionEffect.run();
        }
    }

    static boolean isLocalCompletion(List<String> partyMembers, String localMinecraftUsername) {
        if (localMinecraftUsername == null || localMinecraftUsername.isBlank()) {
            return false;
        }
        return partyMembers.stream().anyMatch(localMinecraftUsername::equalsIgnoreCase);
    }

    private static String localMinecraftUsername() {
        if (SeqClient.mc == null) {
            return null;
        }
        if (SeqClient.mc.getUser() != null) {
            return SeqClient.mc.getUser().getName();
        }
        return SeqClient.mc.player != null ? SeqClient.mc.player.getName().getString() : null;
    }

    /** Called from the raw vanilla title packet path, independently of Wynntils. */
    public static void onTitle(Component title) {
        if (isRaidFailedTitle(title)) {
            SeqClient.LOGGER.debug("[RaidTracker] Raid failure title detected");
            RaidPartySnapshotTracker.onRaidFailed();
        }
    }

    static boolean isRaidFailedTitle(Component title) {
        if (title == null) {
            return false;
        }
        String normalized = PacketTextNormalizer.normalizeForParsing(title.getString());
        return normalized.toLowerCase(java.util.Locale.ROOT).contains(RAID_FAILED_TEXT);
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
        if (!matcher.matches()) {
            SeqClient.LOGGER.warn(
                    "[RaidTracker] Regex did not match normalized raid candidate cleaned='{}'",
                    abbreviateForLog(cleaned));
            return null;
        }

        String namesPart = matcher.group(1);
        if (namesPart.contains(":")) {
            return null;
        }
        String raidName = matcher.group(2);
        int maximumPartySize = maximumPartySize(raidName);
        List<String> parsedDisplayedNames = parseDisplayedNames(namesPart);
        if (parsedDisplayedNames.size() > maximumPartySize) {
            SeqClient.LOGGER.warn(
                    "[RaidTracker] Dropping raid candidate with too many displayed names raid='{}' count={} max={} namesPart='{}'",
                    raidName,
                    parsedDisplayedNames.size(),
                    maximumPartySize,
                    namesPart);
            return null;
        }
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

        RaidRewardCounts rewardCounts = parseRaidRewardCounts(matcher.group(3));

        // Wynncraft reports XP in millions (e.g. "10367m" = 10,367,000) — divide
        // by 1000 so the backend receives a friendlier value (10367 -> 10.367).
        double guildExp = Double.parseDouble(matcher.group(4)) / 1000.0;
        int seasonalRating = matcher.group(5) != null ? Integer.parseInt(matcher.group(5)) : 0;

        SeqClient.LOGGER.info(
                "[RaidTracker] Parsed raid completion raid='{}' aspects={} emeralds={} guildExp={} seasonalRating={}",
                raidName,
                rewardCounts.aspects(),
                rewardCounts.emeralds(),
                guildExp,
                seasonalRating);
        return new ParsedRaidCompletion(
                partyMembers,
                parsedDisplayedNames.size(),
                raidName,
                rewardCounts.aspects(),
                rewardCounts.emeralds(),
                guildExp,
                seasonalRating);
    }

    private static RaidRewardCounts parseRaidRewardCounts(String rewardClause) {
        int aspects = 0;
        int emeralds = 0;
        if (rewardClause == null || rewardClause.isBlank()) {
            return new RaidRewardCounts(aspects, emeralds);
        }

        Matcher rewardMatcher = RAID_REWARD_PATTERN.matcher(rewardClause);
        while (rewardMatcher.find()) {
            int amount = rewardMatcher.group(1) != null ? Integer.parseInt(rewardMatcher.group(1)) : 0;
            String rewardType = rewardMatcher.group(2).toLowerCase();
            if (rewardType.startsWith("aspect")) {
                aspects += amount;
            } else {
                emeralds += amount;
            }
        }
        return new RaidRewardCounts(aspects, emeralds);
    }

    static String normalizeForRaidParsing(String rawText) {
        return PacketTextNormalizer.normalizeForParsing(rawText)
                .replace(",and ", ", and ");
    }

    private static boolean isRaidCandidateText(String plain) {
        return plain != null
                && plain.contains("finished")
                && plain.contains("claimed")
                && plain.contains("Guild")
                && plain.contains("Experience");
    }

    private static List<String> parseDisplayedNames(String namesPart) {
        String canonicalNames = namesPart.replace(", and ", ", ").trim();
        if (!canonicalNames.contains(",")) {
            int finalAnd = canonicalNames.lastIndexOf(" and ");
            if (finalAnd >= 0) {
                canonicalNames = canonicalNames.substring(0, finalAnd)
                        + ", "
                        + canonicalNames.substring(finalAnd + " and ".length());
            }
        }

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

        String parenthesizedRealName = parenthesizedUsername(displayedName);
        if (parenthesizedRealName != null) {
            return parenthesizedRealName;
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

    private static String parenthesizedUsername(String displayedName) {
        Matcher matcher = PARENTHESIZED_USERNAME_PATTERN.matcher(displayedName);
        return matcher.matches() ? matcher.group(1) : null;
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
        boolean skipLegacyFormattingCode = false;
        for (int index = 0; index < text.length();) {
            int codePoint = text.codePointAt(index);
            index += Character.charCount(codePoint);

            if (skipLegacyFormattingCode) {
                skipLegacyFormattingCode = false;
                continue;
            }
            if (codePoint == '§') {
                skipLegacyFormattingCode = true;
                continue;
            }

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

    static int maximumPartySize(String raidName) {
        return raidName != null && raidName.toLowerCase(java.util.Locale.ROOT).contains("annihilation")
                ? ANNIHILATION_MAX_RAID_PARTY_MEMBERS
                : DEFAULT_MAX_RAID_PARTY_MEMBERS;
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
            int displayedPartySize,
            String raidName,
            int aspects,
            int emeralds,
            double guildExp,
            int seasonalRating) {
    }

    record ResolvedRaidCompletion(List<String> partyMembers, boolean localCompletion) {
        ResolvedRaidCompletion {
            partyMembers = List.copyOf(partyMembers);
        }
    }

    private record RaidRewardCounts(int aspects, int emeralds) {
    }
}
