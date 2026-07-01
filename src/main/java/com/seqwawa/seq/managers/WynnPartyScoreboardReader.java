package com.seqwawa.seq.managers;

import com.seqwawa.seq.client.SeqClient;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.Scoreboard;

public final class WynnPartyScoreboardReader {
    private static final Pattern FIRST_NUMBER = Pattern.compile("(\\d+)");
    private static final Pattern BRACKETED_NUMBER = Pattern.compile("\\[(\\d+)]");
    private static final Pattern USERNAME_PATTERN = Pattern.compile("[A-Za-z0-9_]{3,16}");

    private WynnPartyScoreboardReader() {}

    public record PartyHealth(
            String nickname,
            String username,
            int hp,
            int level,
            boolean online,
            boolean alive,
            boolean fullHealthBar) {}

    private record SidebarLine(String text, PacketNameResolver resolver) {}

    private static List<SidebarLine> readSidebarLineComponents() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return List.of();
        }

        Scoreboard scoreboard = mc.level.getScoreboard();
        Objective sidebar = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
        if (sidebar == null) {
            return List.of();
        }

        List<PlayerScoreEntry> entries = new ArrayList<>(scoreboard.listPlayerScores(sidebar));
        entries.sort(Comparator.comparingInt(PlayerScoreEntry::value).reversed());

        List<SidebarLine> lines = new ArrayList<>();
        for (PlayerScoreEntry entry : entries) {
            Component display = entry.display();
            Component lineComponent = display != null ? display : Component.literal(entry.owner());
            PacketNameResolver resolver = PacketNameResolver.from(lineComponent);
            lines.add(new SidebarLine(resolver.text(), resolver));
        }
        return lines;
    }

    public static List<PartyHealth> readPartyHealth() {
        return withLocalUsernameForSoloUnresolvedPartyMember(
                withWynntilsPartyMembersForUnresolvedRows(readPartyHealthRowsWithoutFallbacks()));
    }

    private static List<PartyHealth> readPartyHealthRowsWithoutFallbacks() {
        List<PartyHealth> partyHealth = new ArrayList<>();
        boolean inPartySection = false;

        for (SidebarLine sidebarLine : readSidebarLineComponents()) {
            String line = sidebarLine.text();
            String trimmedLine = line.trim();

            if (isHeaderLine(trimmedLine)) {
                inPartySection = isPartyHeaderLine(trimmedLine);
                continue;
            }

            int trimOffset = line.indexOf(trimmedLine);
            ParsedPartyLine parsed = parsePartyLine(trimmedLine, inPartySection);
            if (parsed == null) {
                continue;
            }

            boolean alive = parsed.online()
                    && !sidebarLine.resolver().hasStrikethrough(
                            trimOffset + parsed.nicknameStart(),
                            trimOffset + parsed.nicknameEnd());
            boolean nicknameStyle = sidebarLine.resolver().hasItalic(
                    trimOffset + parsed.nicknameStart(),
                    trimOffset + parsed.nicknameEnd());
            boolean fullHealthBar = isFullHealthBar(sidebarLine.resolver(), trimOffset, parsed);

            String username = sidebarLine.resolver().resolveMetadataUsername(
                    trimOffset + parsed.nicknameStart(),
                    trimOffset + parsed.nicknameEnd());
            if (username != null) {
                SeqClient.LOGGER.debug(
                        "[WynnPartyScoreboard] Metadata resolved nickname='{}' username='{}' line='{}'",
                        parsed.nickname(),
                        username,
                        line);
                NicknameResolverCache.remember(parsed.nickname(), username);
            } else {
                username = sidebarLine.resolver().resolveMetadataUsername(
                        trimOffset,
                        trimOffset + trimmedLine.length());
                if (username != null) {
                    SeqClient.LOGGER.debug(
                            "[WynnPartyScoreboard] Line metadata resolved nickname='{}' username='{}' line='{}'",
                            parsed.nickname(),
                            username,
                            line);
                    NicknameResolverCache.remember(parsed.nickname(), username);
                }
            }

            if (username == null) {
                SeqClient.LOGGER.debug(
                        "[WynnPartyScoreboard] Metadata missing nickname='{}' line='{}'; trying nickname cache",
                        parsed.nickname(),
                        line);
                username = NicknameResolverCache.resolveUsername(parsed.nickname());
                if (username == null && !nicknameStyle) {
                    username = resolveVisiblePlayerUsername(parsed.nickname());
                    if (username != null) {
                        SeqClient.LOGGER.debug(
                                "[WynnPartyScoreboard] Visible player resolved non-nick name='{}' username='{}' line='{}'",
                                parsed.nickname(),
                                username,
                                line);
                        NicknameResolverCache.remember(parsed.nickname(), username);
                    }
                }
            }

            if (username != null && !nicknameStyle) {
                String fullVisibleUsername = resolveVisiblePlayerUsername(username);
                if (fullVisibleUsername == null) {
                    fullVisibleUsername = resolveVisiblePlayerUsername(parsed.nickname());
                }
                if (fullVisibleUsername != null && !fullVisibleUsername.equals(username)) {
                    SeqClient.LOGGER.debug(
                            "[WynnPartyScoreboard] Canonicalized non-nick name='{}' username='{}' fullUsername='{}' line='{}'",
                            parsed.nickname(),
                            username,
                            fullVisibleUsername,
                            line);
                    username = fullVisibleUsername;
                    NicknameResolverCache.remember(parsed.nickname(), username);
                }
            }

            SeqClient.LOGGER.debug(
                    "[WynnPartyScoreboard] Parsed nickname='{}' username='{}' hp={} level={} online={} alive={} fullBar={} italic={} line='{}'",
                    parsed.nickname(),
                    username,
                    parsed.hp(),
                    parsed.level(),
                    parsed.online(),
                    alive,
                    fullHealthBar,
                    nicknameStyle,
                    line);

            partyHealth.add(new PartyHealth(
                    parsed.nickname(),
                    username,
                    parsed.hp(),
                    parsed.level(),
                    parsed.online(),
                    alive,
                    fullHealthBar));
        }
        return partyHealth;
    }

    private static List<PartyHealth> withWynntilsPartyMembersForUnresolvedRows(List<PartyHealth> partyHealth) {
        List<String> wynntilsPartyMembers = wynntilsPartyMembers();
        List<String> selectedMembers = matchingWynntilsPartyMemberCandidate(partyHealth, wynntilsPartyMembers);
        if (selectedMembers.isEmpty()) {
            return partyHealth;
        }

        List<PartyHealth> resolved = new ArrayList<>(partyHealth.size());
        boolean changed = false;
        for (int index = 0; index < partyHealth.size(); index++) {
            PartyHealth member = partyHealth.get(index);
            String username = validUsername(selectedMembers.get(index));
            if ((member.username() == null || member.username().isBlank()) && username != null) {
                SeqClient.LOGGER.debug(
                        "[WynnPartyScoreboard] Wynntils party model resolved nickname='{}' username='{}'",
                        member.nickname(),
                        username);
                NicknameResolverCache.remember(member.nickname(), username);
                resolved.add(withUsername(member, username));
                changed = true;
            } else {
                resolved.add(member);
            }
        }
        return changed ? List.copyOf(resolved) : partyHealth;
    }

    private static List<String> matchingWynntilsPartyMemberCandidate(
            List<PartyHealth> partyHealth,
            List<String> wynntilsPartyMembers) {
        if (partyHealth.isEmpty() || wynntilsPartyMembers.isEmpty()) {
            return List.of();
        }

        List<List<String>> candidates = wynntilsPartyMemberCandidates(wynntilsPartyMembers);
        for (List<String> candidate : candidates) {
            if (candidate.size() == partyHealth.size() && existingUsernamesMatch(partyHealth, candidate)) {
                return candidate;
            }
        }
        return List.of();
    }

    private static List<List<String>> wynntilsPartyMemberCandidates(List<String> wynntilsPartyMembers) {
        List<List<String>> candidates = new ArrayList<>();
        if (!wynntilsPartyMembers.isEmpty()) {
            candidates.add(wynntilsPartyMembers);
        }

        String localUsername = localUsername();
        if (localUsername == null || containsIgnoreCase(wynntilsPartyMembers, localUsername)) {
            return List.copyOf(candidates);
        }

        List<String> localFirst = new ArrayList<>(wynntilsPartyMembers.size() + 1);
        localFirst.add(localUsername);
        localFirst.addAll(wynntilsPartyMembers);
        candidates.add(List.copyOf(localFirst));

        List<String> localLast = new ArrayList<>(wynntilsPartyMembers.size() + 1);
        localLast.addAll(wynntilsPartyMembers);
        localLast.add(localUsername);
        candidates.add(List.copyOf(localLast));
        return List.copyOf(candidates);
    }

    private static boolean existingUsernamesMatch(List<PartyHealth> partyHealth, List<String> candidate) {
        for (int index = 0; index < partyHealth.size(); index++) {
            String username = partyHealth.get(index).username();
            if (username != null && !username.isBlank() && !username.equalsIgnoreCase(candidate.get(index))) {
                return false;
            }
        }
        return true;
    }

    private static boolean containsIgnoreCase(List<String> values, String value) {
        for (String candidate : values) {
            if (candidate.equalsIgnoreCase(value)) {
                return true;
            }
        }
        return false;
    }

    private static List<PartyHealth> withLocalUsernameForSoloUnresolvedPartyMember(List<PartyHealth> partyHealth) {
        if (partyHealth.size() != 1) {
            return partyHealth;
        }

        PartyHealth member = partyHealth.getFirst();
        if (member.username() != null && !member.username().isBlank()) {
            return partyHealth;
        }

        String localUsername = localUsername();
        if (localUsername == null) {
            return partyHealth;
        }

        SeqClient.LOGGER.debug(
                "[WynnPartyScoreboard] Resolved solo unresolved party nickname='{}' as local username='{}'",
                member.nickname(),
                localUsername);
        NicknameResolverCache.remember(member.nickname(), localUsername);
        return List.of(withUsername(member, localUsername));
    }

    private static PartyHealth withUsername(PartyHealth member, String username) {
        return new PartyHealth(
                member.nickname(),
                username,
                member.hp(),
                member.level(),
                member.online(),
                member.alive(),
                member.fullHealthBar());
    }

    private static List<String> wynntilsPartyMembers() {
        try {
            Class<?> modelsClass = Class.forName("com.wynntils.core.components.Models");
            Object partyModel = modelsClass.getField("Party").get(null);
            Object members = partyModel.getClass().getMethod("getPartyMembers").invoke(partyModel);
            if (!(members instanceof List<?> rawMembers)) {
                return List.of();
            }

            List<String> usernames = new ArrayList<>(rawMembers.size());
            for (Object member : rawMembers) {
                String username = validUsername(member instanceof String value ? value : null);
                if (username == null) {
                    return List.of();
                }
                usernames.add(username);
            }
            return List.copyOf(usernames);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return List.of();
        }
    }

    private static boolean isHeaderLine(String line) {
        return line.contains("Party:") || line.contains("Raid:");
    }

    private static boolean isPartyHeaderLine(String line) {
        return line.contains("Party:");
    }

    static ParsedPartyLine parsePartyLine(String line, boolean allowOfflineLine) {
        if (line == null || line.isBlank()) {
            return null;
        }

        ParsedPartyLine onlineLine = parseOnlinePartyLine(line);
        if (onlineLine != null) {
            return onlineLine;
        }

        return allowOfflineLine ? parseOfflinePartyLine(line) : null;
    }

    private static ParsedPartyLine parseOnlinePartyLine(String line) {
        BracketedNumber level = lastBracketedNumber(line);
        if (level == null) {
            return null;
        }

        Matcher hpMatcher = FIRST_NUMBER.matcher(line);
        hpMatcher.region(0, level.start());
        if (!findStandaloneHealthNumber(line, hpMatcher)) {
            return null;
        }

        int nicknameStart = hpMatcher.end();
        while (nicknameStart < level.start() && !isNicknameCharacter(line.charAt(nicknameStart))) {
            nicknameStart++;
        }

        int nicknameEnd = level.start();
        while (nicknameEnd > nicknameStart && !isNicknameCharacter(line.charAt(nicknameEnd - 1))) {
            nicknameEnd--;
        }

        if (nicknameStart >= nicknameEnd) {
            return null;
        }

        String nickname = line.substring(nicknameStart, nicknameEnd).trim();
        if (nickname.isBlank()) {
            return null;
        }

        return new ParsedPartyLine(
                Integer.parseInt(hpMatcher.group(1)),
                nickname,
                nicknameStart,
                nicknameEnd,
                level.value(),
                healthBarStart(line, hpMatcher.start()),
                healthBarEnd(line, hpMatcher.end(), level.start()),
                true);
    }

    private static ParsedPartyLine parseOfflinePartyLine(String line) {
        if (!line.startsWith("-")) {
            return null;
        }
        if (lastBracketedNumber(line) != null) {
            return null;
        }

        int nicknameStart = 1;
        while (nicknameStart < line.length() && !isNicknameCharacter(line.charAt(nicknameStart))) {
            nicknameStart++;
        }

        int nicknameEnd = line.length();
        while (nicknameEnd > nicknameStart && !isNicknameCharacter(line.charAt(nicknameEnd - 1))) {
            nicknameEnd--;
        }

        if (nicknameStart >= nicknameEnd) {
            return null;
        }

        String nickname = line.substring(nicknameStart, nicknameEnd).trim();
        if (nickname.isBlank()) {
            return null;
        }
        return new ParsedPartyLine(0, nickname, nicknameStart, nicknameEnd, 0, -1, -1, false);
    }

    private static boolean isFullHealthBar(PacketNameResolver resolver, int trimOffset, ParsedPartyLine parsed) {
        if (parsed.healthBarStart() < 0 || parsed.healthBarEnd() < 0) {
            return false;
        }
        return resolver.hasOnlyRedHealthCharacters(
                trimOffset + parsed.healthBarStart() + 1,
                trimOffset + parsed.healthBarEnd());
    }

    private static int healthBarStart(String line, int hpStart) {
        return line.lastIndexOf('[', hpStart);
    }

    private static int healthBarEnd(String line, int hpEnd, int levelStart) {
        int end = line.indexOf(']', hpEnd);
        return end >= 0 && end < levelStart ? end : -1;
    }

    private static BracketedNumber lastBracketedNumber(String line) {
        Matcher matcher = BRACKETED_NUMBER.matcher(line);
        BracketedNumber lastMatch = null;
        while (matcher.find()) {
            lastMatch = new BracketedNumber(Integer.parseInt(matcher.group(1)), matcher.start(), matcher.end());
        }
        return lastMatch;
    }

    private static boolean findStandaloneHealthNumber(String line, Matcher matcher) {
        while (matcher.find()) {
            if (isStandaloneNumber(line, matcher.start(), matcher.end())) {
                return true;
            }
        }
        return false;
    }

    private static boolean isStandaloneNumber(String line, int startInclusive, int endExclusive) {
        return (startInclusive <= 0 || !isNameTokenCharacter(line.charAt(startInclusive - 1)))
                && (endExclusive >= line.length() || !isNameTokenCharacter(line.charAt(endExclusive)));
    }

    private static boolean isNameTokenCharacter(char value) {
        return Character.isLetterOrDigit(value) || value == '_';
    }

    private static boolean isNicknameCharacter(char value) {
        return isNameTokenCharacter(value) || Character.isWhitespace(value);
    }

    private static String resolveVisiblePlayerUsername(String nickname) {
        String key = nickname == null ? "" : nickname.trim().toLowerCase(Locale.ROOT);
        if (key.length() < 3) {
            return null;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return null;
        }

        String uniqueMatch = null;
        for (AbstractClientPlayer player : mc.level.players()) {
            String canonicalUsername = canonicalVisibleUsername(player);
            if (canonicalUsername == null) {
                continue;
            }

            for (String candidate : visibleNameCandidates(player)) {
                String candidateKey = normalizeName(candidate);
                if (candidateKey.isBlank()) {
                    continue;
                }
                if (candidateKey.equals(key) || candidateKey.startsWith(key)) {
                    if (uniqueMatch != null && !uniqueMatch.equalsIgnoreCase(canonicalUsername)) {
                        return null;
                    }
                    uniqueMatch = canonicalUsername;
                }
            }
        }

        return uniqueMatch;
    }

    private static List<String> visibleNameCandidates(AbstractClientPlayer player) {
        List<String> candidates = new ArrayList<>();
        candidates.add(player.getName().getString());
        candidates.add(player.getGameProfile().name());
        candidates.add(player.getScoreboardName());
        if (player.getCustomName() != null) {
            candidates.add(player.getCustomName().getString());
        }
        return candidates;
    }

    private static String canonicalVisibleUsername(AbstractClientPlayer player) {
        String profileName = validUsername(player.getGameProfile().name());
        if (profileName != null) {
            return profileName;
        }

        return validUsername(player.getScoreboardName());
    }

    private static String validUsername(String username) {
        if (username == null) {
            return null;
        }
        String trimmed = username.trim();
        return USERNAME_PATTERN.matcher(trimmed).matches() ? trimmed : null;
    }

    private static String localUsername() {
        if (SeqClient.mc != null && SeqClient.mc.getUser() != null) {
            String username = validUsername(SeqClient.mc.getUser().getName());
            if (username != null) {
                return username;
            }
        }
        if (SeqClient.mc != null && SeqClient.mc.player != null) {
            return validUsername(SeqClient.mc.player.getName().getString());
        }
        return null;
    }

    private static String normalizeName(String name) {
        return name == null ? "" : name.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    record ParsedPartyLine(
            int hp,
            String nickname,
            int nicknameStart,
            int nicknameEnd,
            int level,
            int healthBarStart,
            int healthBarEnd,
            boolean online) {}

    private record BracketedNumber(int value, int start, int end) {}
}
