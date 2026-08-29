package com.seqwawa.seq.managers;

import com.seqwawa.seq.model.SeqPointsShopEffect;
import com.seqwawa.seq.utils.ComponentTextEditor;
import com.seqwawa.seq.utils.PacketTextNormalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

/** Applies active temporary aliases to player identities in displayed chat lines. */
public final class SeqPointsChatAliasDecorator {

    private static final Pattern PLAIN_COLON_SPEAKER = Pattern.compile(
            "^(?:\\[\\d{1,2}:\\d{2}(?::\\d{2})?]\\s*)?(?:\\p{C}|\\s)*(?:<\\d+>\\s*)?"
                    + "([A-Za-z0-9_]{3,16})(?:\\s+shouts)?\\s*:");
    private static final Pattern PLAIN_ANGLE_SPEAKER = Pattern.compile(
            "^(?:\\[\\d{1,2}:\\d{2}(?::\\d{2})?]\\s*)?(?:\\p{C}|\\s)*"
                    + "<([A-Za-z0-9_]{3,16})>");

    private SeqPointsChatAliasDecorator() {}

    public static Component decorate(Component message) {
        return decorate(message, SeqPointsShopManager.getInstance()::effectForUsername);
    }

    /** Decoration core, parameterised to keep component handling unit-testable. */
    static Component decorate(
            Component message, Function<String, SeqPointsShopEffect> effectLookup) {
        if (message == null
                || effectLookup == null
                || DiscordRankChatDecorator.isBridgeLine(message)) {
            return message;
        }

        List<ComponentTextEditor.Fragment> fragments = ComponentTextEditor.flatten(message);
        String text = ComponentTextEditor.textOf(fragments);
        List<IdentityRun> identities = identityRuns(fragments, text, effectLookup);
        IdentityRun plainSpeaker = plainSpeaker(fragments, text, effectLookup);
        if (plainSpeaker != null
                && identities.stream().noneMatch(identity -> identity.start() < plainSpeaker.endExclusive()
                        && identity.endExclusive() > plainSpeaker.start())) {
            identities = new ArrayList<>(identities);
            identities.add(plainSpeaker);
        }
        int headerEnd = firstHeaderEnd(text, identities);
        if (headerEnd < 0) {
            return message;
        }

        // A rank pill and the visible name can both carry the same insertion. Keep the
        // last occurrence in the header: that is the actual name next to the separator.
        Map<String, IdentityRun> visibleIdentities = new LinkedHashMap<>();
        for (IdentityRun identity : identities) {
            if (identity.effect() != null
                    && identity.effect().value() != null
                    && !identity.effect().value().isBlank()
                    && identity.endExclusive() <= headerEnd) {
                visibleIdentities.put(identity.username().toLowerCase(Locale.ROOT), identity);
            }
        }
        if (visibleIdentities.isEmpty()) {
            return message;
        }

        List<IdentityRun> replacements = new ArrayList<>(visibleIdentities.values());
        replacements.sort((left, right) -> Integer.compare(right.start(), left.start()));

        Component result = message;
        for (IdentityRun identity : replacements) {
            String realUsername = identity.effect().targetUsername();
            if (realUsername == null || realUsername.isBlank()) {
                realUsername = identity.username();
            }
            Style identityStyle = identity.style().withInsertion(realUsername);
            MutableComponent replacement = Component.empty()
                    .append(Component.literal(identity.effect().value()).withStyle(identityStyle))
                    .append(Component.literal(" (" + realUsername + ")")
                            .withStyle(identityStyle.withColor(ChatFormatting.GRAY)));
            result = ComponentTextEditor.replaceRange(
                    ComponentTextEditor.flatten(result),
                    identity.start(),
                    identity.endExclusive(),
                    replacement);
            if (result == null) {
                return message;
            }
        }
        return result;
    }

    private static List<IdentityRun> identityRuns(
            List<ComponentTextEditor.Fragment> fragments,
            String text,
            Function<String, SeqPointsShopEffect> effectLookup) {
        List<IdentityRun> runs = new ArrayList<>();
        int cursor = 0;
        for (ComponentTextEditor.Fragment fragment : fragments) {
            int start = cursor;
            cursor += fragment.text().length();

            String hovered = ChatManager.extractHoverRealUsername(fragment.style());
            String username = hovered != null
                    ? hovered
                    : ChatManager.extractInsertionUsername(fragment.style());
            if (username == null) {
                continue;
            }

            IdentityRun previous = runs.isEmpty() ? null : runs.getLast();
            if (previous != null
                    && previous.endExclusive() == start
                    && previous.username().equalsIgnoreCase(username)) {
                runs.set(
                        runs.size() - 1,
                        new IdentityRun(
                                previous.start(),
                                cursor,
                                username,
                                null,
                                previous.style(),
                                previous.hovered() || hovered != null));
            } else {
                runs.add(new IdentityRun(start, cursor, username, null, fragment.style(), hovered != null));
            }
        }

        List<IdentityRun> trusted = new ArrayList<>();
        for (IdentityRun run : runs) {
            String visible = text.substring(run.start(), run.endExclusive());
            // Generic shift-click insertions can be inherited or stale. A real-name
            // hover is explicit; an insertion must also appear in the visible name.
            if (run.hovered() || visiblyContainsUsername(visible, run.username())) {
                trusted.add(new IdentityRun(
                        run.start(),
                        run.endExclusive(),
                        run.username(),
                        effectLookup.apply(run.username()),
                        run.style(),
                        run.hovered()));
            }
        }
        return trusted;
    }

    private static boolean visiblyContainsUsername(String visible, String username) {
        String normalized = PacketTextNormalizer.normalizeForParsing(visible);
        for (String token : normalized.split("[^A-Za-z0-9_]+")) {
            if (token.equalsIgnoreCase(username)) {
                return true;
            }
        }
        return false;
    }

    /** Safe fallback for an undecorated canonical username in the speaker slot. */
    private static IdentityRun plainSpeaker(
            List<ComponentTextEditor.Fragment> fragments,
            String text,
            Function<String, SeqPointsShopEffect> effectLookup) {
        Matcher matcher = PLAIN_COLON_SPEAKER.matcher(text);
        if (!matcher.find()) {
            matcher = PLAIN_ANGLE_SPEAKER.matcher(text);
            if (!matcher.find()) {
                return null;
            }
        }
        String username = matcher.group(1);
        return new IdentityRun(
                matcher.start(1),
                matcher.end(1),
                username,
                effectLookup.apply(username),
                styleAt(fragments, matcher.start(1)),
                false);
    }

    private static Style styleAt(List<ComponentTextEditor.Fragment> fragments, int index) {
        int cursor = 0;
        for (ComponentTextEditor.Fragment fragment : fragments) {
            cursor += fragment.text().length();
            if (cursor > index) {
                return fragment.style();
            }
        }
        return Style.EMPTY;
    }

    /**
     * Finds the first message separator after a styled player identity. Colons in a
     * prepended timestamp occur before any identity and are therefore ignored.
     */
    private static int firstHeaderEnd(String text, List<IdentityRun> identities) {
        for (int colon = text.indexOf(':'); colon >= 0; colon = text.indexOf(':', colon + 1)) {
            for (IdentityRun identity : identities) {
                if (identity.endExclusive() <= colon) {
                    return colon;
                }
            }
        }

        // Vanilla-shaped player chat can use "<name> message" instead of a colon.
        for (IdentityRun identity : identities) {
            if (identity.start() > 0
                    && identity.endExclusive() < text.length()
                    && text.charAt(identity.start() - 1) == '<'
                    && text.charAt(identity.endExclusive()) == '>') {
                return identity.endExclusive();
            }
        }
        return -1;
    }

    private record IdentityRun(
            int start,
            int endExclusive,
            String username,
            SeqPointsShopEffect effect,
            Style style,
            boolean hovered) {}
}
