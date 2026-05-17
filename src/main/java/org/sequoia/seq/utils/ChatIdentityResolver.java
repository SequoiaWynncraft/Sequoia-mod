package org.sequoia.seq.utils;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.PlainTextContents;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves real player usernames from chat components while keeping plain,
 * already-canonical usernames valid when no nickname metadata is present.
 */
public final class ChatIdentityResolver {

    private ChatIdentityResolver() {}

    private static final Pattern USERNAME_PATTERN = Pattern.compile("[a-zA-Z0-9_]{3,16}");
    private static final Pattern HOVER_REAL_NAME_PATTERN = Pattern.compile(
        "(?i)(?:\\breal\\s+name\\s+is\\s+|\\breal\\s+username:\\s*)([a-zA-Z0-9_]{3,16})\\b"
    );

    public static String resolveCanonicalUsername(Component message, String displayedName) {
        String realUsername = findRealUsername(message);
        if (realUsername != null) return realUsername;

        String cleanedDisplay = cleanVisibleText(displayedName);
        return isValidUsername(cleanedDisplay) ? cleanedDisplay : null;
    }

    public static String formatOutgoingUsername(String canonicalUsername, String displayedName) {
        if (canonicalUsername == null) return null;

        String cleanedDisplay = cleanVisibleText(displayedName);
        if (cleanedDisplay.isEmpty() || canonicalUsername.equalsIgnoreCase(cleanedDisplay)) {
            return canonicalUsername;
        }
        return canonicalUsername + "/" + cleanedDisplay;
    }

    public static List<String> resolveRaidParticipants(Component message, List<String> displayedNames) {
        List<NameCandidate> candidates = collectHeaderCandidates(message);
        List<String> resolved = new ArrayList<>(displayedNames.size());
        Set<Integer> usedCandidates = new HashSet<>();

        for (int i = 0; i < displayedNames.size(); i++) {
            String displayedName = cleanVisibleText(displayedNames.get(i));
            if (displayedName.isEmpty()) continue;

            NameCandidate matched = null;
            int matchedIndex = -1;
            for (int j = 0; j < candidates.size(); j++) {
                if (usedCandidates.contains(j)) continue;

                NameCandidate candidate = candidates.get(j);
                if (textsMatch(displayedName, candidate.visibleText())) {
                    matched = candidate;
                    matchedIndex = j;
                    break;
                }
            }

            if (matched != null) {
                usedCandidates.add(matchedIndex);
                if (matched.username() != null) {
                    resolved.add(matched.username());
                    continue;
                }
            }

            if (!isValidUsername(displayedName)) {
                return List.of();
            }

            resolved.add(displayedName);
        }

        return resolved;
    }

    private static List<NameCandidate> collectHeaderCandidates(Component message) {
        List<NameCandidate> candidates = new ArrayList<>();
        for (Component part : message.toFlatList()) {
            String visibleText = extractOwnText(part);
            String cleanedVisible = cleanVisibleText(visibleText);
            if (cleanedVisible.isEmpty()) continue;
            if (cleanedVisible.toLowerCase(Locale.ROOT).contains("finished")) break;
            if (!looksLikeNameFragment(cleanedVisible)) continue;

            String username = extractDirectUsername(part);
            candidates.add(new NameCandidate(cleanedVisible, username));
        }
        return candidates;
    }

    public static String findRealUsername(Component component) {
        String direct = extractDirectUsername(component);
        if (direct != null) return direct;

        for (Component sibling : component.getSiblings()) {
            String found = findRealUsername(sibling);
            if (found != null) {
                return found;
            }
        }

        return null;
    }

    private static String extractDirectUsername(Component component) {
        Style style = component.getStyle();

        String insertion = style.getInsertion();
        if (isValidUsername(insertion)) {
            return insertion;
        }

        HoverEvent hoverEvent = style.getHoverEvent();
        if (hoverEvent instanceof HoverEvent.ShowText showTextEvent) {
            Component hoverComponent = showTextEvent.value();
            if (hoverComponent != null) {
                String hoverText = hoverComponent.getString()
                    .replace('\u2019', '\'')
                    .replace('\u2018', '\'');
                Matcher matcher = HOVER_REAL_NAME_PATTERN.matcher(hoverText);
                if (matcher.find()) {
                    return matcher.group(1);
                }
            }
        }

        return null;
    }

    public static boolean isValidUsername(String name) {
        return name != null && USERNAME_PATTERN.matcher(name).matches();
    }

    private static boolean textsMatch(String displayedName, String candidateVisible) {
        String left = cleanVisibleText(displayedName);
        String right = cleanVisibleText(candidateVisible);
        if (left.isEmpty() || right.isEmpty()) return false;
        return left.equalsIgnoreCase(right)
            || right.contains(left)
            || left.contains(right);
    }

    private static boolean looksLikeNameFragment(String text) {
        if (text.equalsIgnoreCase("and")) return false;
        return text.chars().anyMatch(ch -> Character.isLetterOrDigit(ch) || ch == '_');
    }

    private static String extractOwnText(Component component) {
        if (component.getContents() instanceof PlainTextContents plainTextContents) {
            return plainTextContents.text();
        }
        return component.getString();
    }

    public static String cleanVisibleText(String text) {
        if (text == null) return "";
        return text
            .replaceAll("[\\n\\r]+", " ")
            .replaceAll("\\p{C}", "")
            .replaceAll(" {2,}", " ")
            .trim();
    }

    private record NameCandidate(String visibleText, String username) {}
}
