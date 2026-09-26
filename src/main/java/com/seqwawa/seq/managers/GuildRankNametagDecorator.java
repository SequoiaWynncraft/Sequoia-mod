package com.seqwawa.seq.managers;

import com.seqwawa.seq.accessors.NotificationAccessor;
import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.config.Setting;
import com.seqwawa.seq.model.RankPresentation;
import com.seqwawa.seq.utils.ComponentTextEditor;
import com.seqwawa.seq.utils.WynnPillGlyphs;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;

/**
 * Swaps the Wynncraft rank badge on a player's in-world nametag for the member's
 * Sequoia Discord rank, the same substitution {@link DiscordRankChatDecorator}
 * makes in guild chat and on the Discord bridge. Players with no linked Sequoia
 * rank keep the nametag Wynncraft sent, untouched.
 * <p>
 * The rewrite happens while an {@code AvatarRenderer} submits a player's nametag,
 * after the render state has been extracted. This keeps changes made to that state
 * by other mods while retaining the UUID of the player it belongs to.
 * <p>
 * {@link #rememberRenderedPlayer} records the names that this particular player's
 * tag may display. Decoration is then keyed by the same UUID, so an ordinary
 * hologram, a mob, or another account using the same text cannot pick up the rank.
 * <p>
 * Everything here runs on the render thread.
 */
public final class GuildRankNametagDecorator {

    /** Players remembered, ample for the players one client can see at once. */
    private static final int MAX_REMEMBERED_PLAYERS = 256;

    /** Tags remembered per player; one rarely has more than a line or two decorated. */
    private static final int MAX_TAGS_PER_PLAYER = 4;

    private static final int MIN_NAME_LENGTH = 3;
    private static final int MAX_NAME_LENGTH = 16;

    private static final FontDescription WYNNTILS_NAMETAG_FONT =
            new FontDescription.Resource(Identifier.fromNamespaceAndPath("wynntils", "nametag"));

    /** What is known about each player rendered lately. Render thread only. */
    private static final Map<UUID, PlayerTags> PLAYERS = boundedMap(MAX_REMEMBERED_PLAYERS);

    private GuildRankNametagDecorator() {}

    /**
     * Publishes the identity of a player being rendered this frame, keyed by every
     * name their nametag can show: their account name and whatever Wynncraft
     * currently displays, which differ while they are nicknamed.
     *
     * @param nameTag the nametag Wynncraft supplied, before any mod has touched it
     */
    public static void rememberRenderedPlayer(UUID uuid, String username, Component nameTag) {
        if (uuid == null || !isEnabled()) {
            return;
        }

        // Called for every player on screen every frame, almost always with the tag it
        // had last frame: rebuilt as a new component, but with the same text and styles.
        // Comparing it with the snapshot kept last time needs no lookups and no hashing.
        DiscordRankService service = DiscordRankService.getInstance();
        Object roster = service.rosterSnapshot();
        PlayerTags previous = PLAYERS.get(uuid);
        if (previous != null
                && previous.roster == roster
                && Objects.equals(previous.username, username)
                && previous.shows(nameTag)) {
            return;
        }

        Snapshot snapshot = Snapshot.of(nameTag);
        RankPresentation rank = rankFor(service, uuid, username);
        Member member = rank == null ? null : new Member(username, rank);
        PlayerTags replacement = new PlayerTags(
                username, roster, member, registeredNames(username, snapshot.text()), snapshot, nameTag);
        // Replacing the registration replaces its complete alias set, so no obsolete
        // nickname stays available to this UUID. Decorations only carry over when they
        // would come out the same.
        if (previous != null && previous.decoratesLike(replacement)) {
            replacement.decorations.addAll(previous.decorations);
        }
        PLAYERS.put(uuid, replacement);
    }

    /**
     * Drops every decorated tag, once the fonts have been reloaded. A pill is laid out
     * to the widths of the glyphs it is made of, and a tag decorated before Wynncraft's
     * resource pack arrived was laid out to the wrong ones.
     */
    public static void forgetDecorations() {
        PLAYERS.clear();
    }

    /**
     * The nametag to draw in place of {@code nameTag}: the same tag with its rank
     * badge replaced by the speaker's Sequoia rank, or {@code nameTag} itself when
     * it belongs to nobody the roster knows.
     */
    public static Component decorate(UUID uuid, Component nameTag) {
        if (uuid == null || nameTag == null || !isEnabled()) {
            return nameTag;
        }

        PlayerTags player = PLAYERS.get(uuid);
        if (player == null || player.member == null) {
            return nameTag;
        }
        return player.decorated(nameTag);
    }

    /** Decoration core, parameterised on the identity lookup so it stays unit-testable. */
    static Component decorate(Component nameTag, Function<String, Member> members) {
        return decorate(Snapshot.of(nameTag), nameTag, members);
    }

    private static Component decorate(Snapshot source, Component nameTag, Function<String, Member> members) {
        List<ComponentTextEditor.Fragment> fragments = source.fragments();
        String text = source.text();
        DisplayedName name = displayedName(text, members);
        if (name == null) {
            return nameTag;
        }

        Member member = name.member();
        String label = PrincessRankEasterEgg.pillLabel(member.rank().pillLabel(), member.username());
        Badge badge = badgeBefore(fragments, text, name.start());
        if (alreadyDecorated(text, name.start(), label, badge)) {
            return nameTag;
        }
        Component rewritten = rewrite(fragments, name, badge, label, badgeColor(fragments, badge));
        return rewritten == null ? nameTag : rewritten;
    }

    private static Component rewrite(
            List<ComponentTextEditor.Fragment> fragments,
            DisplayedName name,
            Badge badge,
            String label,
            TextColor badgeColor) {
        RankPresentation rank = name.member().rank();
        // Chat's pill, and the name painted in the member's colours so the two read as
        // one label rather than as a Sequoia rank stuck on a Wynncraft-coloured name,
        // joined onto one gradient.
        NotificationAccessor.GradientPill pill = NotificationAccessor.gradientPill(
                label,
                DiscordRankChatDecorator.rampFor(rank),
                DiscordRankChatDecorator.roleRampFor(rank),
                DiscordRankChatDecorator.PILL_LABEL_COLOR,
                null,
                badgeColor);
        List<ComponentTextEditor.Fragment> coloured = DiscordRankChatDecorator.paintName(
                fragments, name.start(), name.endExclusive(), rank, null, pill);
        MutableComponent replacement = Component.empty()
                .append(pill.component())
                .append(Component.literal(" "));

        if (badge == null) {
            return ComponentTextEditor.toComponent(
                    ComponentTextEditor.insertAt(coloured, name.start(), replacement));
        }
        return ComponentTextEditor.replaceRange(coloured, badge.start(), badge.endExclusive(), replacement);
    }

    /**
     * Whether the tag already carries this rank: as the label written in front of the
     * name, or as a badge spelling it. A nametag is decorated every frame from the tag
     * its renderer holds, and a mod may hand back one this already rewrote.
     */
    private static boolean alreadyDecorated(String text, int nameStart, String label, Badge badge) {
        if (label.equalsIgnoreCase(badgeLabel(text, badge))) {
            return true;
        }
        String before = text.substring(0, nameStart).stripTrailing();
        return before.length() >= label.length()
                && before.regionMatches(true, before.length() - label.length(), label, 0, label.length());
    }

    /**
     * The colour Wynncraft drew the badge in, which the rank returns to when member
     * colouring is switched off. Without it the rank would fall back to the plain
     * white a nametag is drawn in, rather than to the rank colour it replaced.
     */
    private static TextColor badgeColor(List<ComponentTextEditor.Fragment> fragments, Badge badge) {
        if (badge == null) {
            return null;
        }
        int cursor = 0;
        for (ComponentTextEditor.Fragment fragment : fragments) {
            cursor += fragment.text().length();
            if (cursor > badge.start()) {
                return fragment.style().getColor();
            }
        }
        return null;
    }

    /**
     * The first name on the tag that belongs to a player being rendered, and where it
     * starts. Wynncraft's badge glyphs are private-use characters, so they never form
     * one of these tokens and the name is always found past the badge.
     */
    static DisplayedName displayedName(String text, Function<String, Member> members) {
        for (Name name : names(text)) {
            Member member = members.apply(normalize(name.value()));
            if (member != null) {
                return new DisplayedName(name.start(), name.start() + name.value().length(), member);
            }
        }
        return null;
    }

    /**
     * The rank badge standing in front of the name, as one span running up to the
     * name itself.
     * <p>
     * Taken as an unbroken block of glyph characters rather than as a single decoded
     * pill, because Wynncraft's newer badges are drawn in layers — a foreground pill,
     * a back-advance, and a shadow that repeats the label — held together by
     * supplementary-plane characters that break a badge into several runs. Replacing
     * one run leaves the other layers behind as a ghost of the old rank.
     * <p>
     * A real space ends the search. Wynntils' logo font also forms a boundary:
     * its private-use glyph is a decoration even when no account badge follows it,
     * or when the logo and badge have no separating space.
     */
    private static Badge badgeBefore(
            List<ComponentTextEditor.Fragment> fragments, String text, int nameStart) {
        int lowerBound = 0;
        int cursor = 0;
        for (ComponentTextEditor.Fragment fragment : fragments) {
            if (cursor >= nameStart) {
                break;
            }
            cursor += fragment.text().length();
            if (WYNNTILS_NAMETAG_FONT.equals(fragment.style().getFont())) {
                lowerBound = Math.min(cursor, nameStart);
            }
        }

        int end = nameStart;
        while (end > lowerBound && Character.isWhitespace(text.charAt(end - 1))) {
            end--;
        }

        int start = end;
        while (start > lowerBound) {
            int codePoint = text.codePointBefore(start);
            if (!isBadgeCharacter(codePoint)) {
                break;
            }
            start -= Character.charCount(codePoint);
        }
        // The spacing belongs to the badge: the replacement brings its own.
        return start == end ? null : new Badge(start, nameStart);
    }

    /**
     * Glyph characters as Wynncraft builds badges from: its private-use font, the
     * unassigned supplementary codepoints it uses to advance between layers, and the
     * zero-width padding that separates them.
     */
    private static boolean isBadgeCharacter(int codePoint) {
        int type = Character.getType(codePoint);
        return type == Character.PRIVATE_USE || type == Character.UNASSIGNED || type == Character.FORMAT;
    }

    /** The rank the badge spells out, or {@code null} when this build cannot read it. */
    private static String badgeLabel(String text, Badge badge) {
        if (badge == null) {
            return null;
        }
        // Decode only the badge span: an adjacent logo in another font must not
        // become part of this glyph run and hide an already-decorated rank.
        List<WynnPillGlyphs.Pill> pills =
                WynnPillGlyphs.findPills(text.substring(badge.start(), badge.endExclusive()));
        return pills.isEmpty() ? null : pills.getFirst().label();
    }

    /** Every name on {@code text} that could be a Minecraft account name. */
    static List<String> nameCandidates(String text) {
        return names(text).stream().map(Name::value).toList();
    }

    /**
     * Every account-name-shaped run of {@code text}, with where it starts. Wynncraft's
     * badge glyphs are private-use characters, so a badge never forms one of these and
     * the name is always found past it.
     */
    private static List<Name> names(String text) {
        List<Name> names = new ArrayList<>(2);
        int index = 0;
        while (index < text.length()) {
            if (!isNameCharacter(text.charAt(index))) {
                index++;
                continue;
            }

            int start = index;
            while (index < text.length() && isNameCharacter(text.charAt(index))) {
                index++;
            }
            String candidate = text.substring(start, index);
            if (isNameSized(candidate)) {
                names.add(new Name(candidate, start));
            }
        }
        return names;
    }

    private static boolean isNameCharacter(char character) {
        return (character >= 'a' && character <= 'z')
                || (character >= 'A' && character <= 'Z')
                || (character >= '0' && character <= '9')
                || character == '_';
    }

    private static boolean isNameSized(String candidate) {
        return candidate.length() >= MIN_NAME_LENGTH && candidate.length() <= MAX_NAME_LENGTH;
    }

    /** All account-name-shaped aliases currently shown for one rendered player. */
    private static List<String> registeredNames(String username, String nameTagText) {
        List<String> names = new ArrayList<>(3);
        addRegisteredName(names, username);
        for (String candidate : nameCandidates(nameTagText)) {
            addRegisteredName(names, candidate);
        }
        return List.copyOf(names);
    }

    private static void addRegisteredName(List<String> names, String name) {
        String key = key(name);
        if (key != null && !names.contains(key)) {
            names.add(key);
        }
    }

    /** The map key a name is stored under, or {@code null} when it cannot be one. */
    private static String key(String name) {
        if (name == null) {
            return null;
        }
        String trimmed = name.trim();
        return isNameSized(trimmed) ? normalize(trimmed) : null;
    }

    private static String normalize(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    /**
     * The member's rank. The UUID is preferred because it cannot be spoofed by a
     * nickname and survives a rename, but the roster only carries one for members
     * whose profile records it, so the account name still has to answer for the rest.
     */
    static RankPresentation rankFor(DiscordRankService service, UUID uuid, String username) {
        RankPresentation byAccount = service.presentationForMinecraftUuid(uuid);
        return byAccount != null ? byAccount : service.presentationForMinecraftUsername(username);
    }

    private static boolean isEnabled() {
        Setting.BooleanSetting setting = SeqClient.getShowNametagRanksSetting();
        return setting != null && setting.getValue();
    }

    private static <K, V> Map<K, V> boundedMap(int maximumEntries) {
        // Insertion ordered on purpose: an access-ordered map mutates on a read, and
        // this is read from the render loop.
        return new LinkedHashMap<>(16, 0.75f, false) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                return size() > maximumEntries;
            }
        };
    }

    /** A player whose nametag should carry a Sequoia rank. */
    record Member(String username, RankPresentation rank) {}

    /** Where a known member's name sits on a nametag. */
    record DisplayedName(int start, int endExclusive, Member member) {}

    /** A run of text on a nametag that is shaped like a Minecraft account name. */
    private record Name(String value, int start) {}

    /** The span a rank badge occupies, up to and including the space after it. */
    record Badge(int start, int endExclusive) {}

    /**
     * A nametag's text and styles as they were: immutable, so it can be kept and set
     * against the next frame's tag without walking or hashing that one twice.
     */
    record Snapshot(List<ComponentTextEditor.Fragment> fragments, String text) {

        static Snapshot of(Component nameTag) {
            List<ComponentTextEditor.Fragment> fragments = List.copyOf(ComponentTextEditor.flatten(nameTag));
            return new Snapshot(fragments, ComponentTextEditor.textOf(fragments));
        }

        /** Whether {@code nameTag} shows exactly this text in exactly these styles. */
        boolean matches(Component nameTag) {
            if (nameTag == null) {
                return false;
            }
            SnapshotMatcher matcher = new SnapshotMatcher(fragments);
            return nameTag.visit(matcher, Style.EMPTY).isEmpty() && matcher.index == fragments.size();
        }
    }

    /**
     * Walks a tag piece by piece against a snapshot and stops at the first difference.
     * The pieces are those {@link ComponentTextEditor#flatten} produces: each run of
     * text with its resolved style, empty runs left out.
     */
    private static final class SnapshotMatcher implements FormattedText.StyledContentConsumer<Boolean> {
        private static final Optional<Boolean> DIFFERENT = Optional.of(Boolean.FALSE);

        private final List<ComponentTextEditor.Fragment> fragments;
        private int index;

        private SnapshotMatcher(List<ComponentTextEditor.Fragment> fragments) {
            this.fragments = fragments;
        }

        @Override
        public Optional<Boolean> accept(Style style, String text) {
            if (text.isEmpty()) {
                return Optional.empty();
            }
            if (index >= fragments.size()) {
                return DIFFERENT;
            }
            ComponentTextEditor.Fragment expected = fragments.get(index++);
            return expected.text().equals(text) && expected.style().equals(style) ? Optional.empty() : DIFFERENT;
        }
    }

    /**
     * Everything known about one rendered player's nametags: who they are, the names
     * their tag shows, the tag they were last seen with, and the tags lately decorated
     * for them.
     */
    private static final class PlayerTags {
        final String username;
        final Object roster;
        final Member member;
        final List<String> names;
        final Snapshot registered;
        /** The component instance last found to show {@link #registered}. */
        Component lastSeen;
        final ArrayDeque<Decorated> decorations = new ArrayDeque<>(MAX_TAGS_PER_PLAYER);

        PlayerTags(
                String username,
                Object roster,
                Member member,
                List<String> names,
                Snapshot registered,
                Component lastSeen) {
            this.username = username;
            this.roster = roster;
            this.member = member;
            this.names = names;
            this.registered = registered;
            this.lastSeen = lastSeen;
        }

        /** Whether {@code nameTag} is still the tag this player was registered with. */
        boolean shows(Component nameTag) {
            if (nameTag == lastSeen) {
                return true;
            }
            if (!registered.matches(nameTag)) {
                return false;
            }
            lastSeen = nameTag;
            return true;
        }

        /**
         * {@code nameTag} decorated for this player. The same component handed back, as
         * the see-through and normal passes of one frame do, costs a comparison; one
         * rebuilt with the same text and styles, one walk against a snapshot.
         */
        Component decorated(Component nameTag) {
            for (Decorated decorated : decorations) {
                if (decorated.input == nameTag) {
                    return decorated.output(nameTag);
                }
            }
            Snapshot seen = nameTag == lastSeen ? registered : null;
            for (Decorated decorated : decorations) {
                if (decorated.source == seen || decorated.source.matches(nameTag)) {
                    decorated.input = nameTag;
                    return decorated.output(nameTag);
                }
            }

            Snapshot source = seen != null ? seen : Snapshot.of(nameTag);
            Component output;
            try {
                output = decorate(source, nameTag, this::memberFor);
            } catch (RuntimeException exception) {
                SeqClient.LOGGER.debug("[DiscordRanks] Failed to decorate a nametag.", exception);
                output = nameTag;
            }
            decorations.addFirst(new Decorated(source, nameTag, output == nameTag ? null : output));
            while (decorations.size() > MAX_TAGS_PER_PLAYER) {
                decorations.removeLast();
            }
            return output;
        }

        Member memberFor(String candidate) {
            return names.contains(normalize(candidate)) ? member : null;
        }

        /** Whether a tag decorated for this player would come out the same for {@code other}. */
        boolean decoratesLike(PlayerTags other) {
            return Objects.equals(member, other.member) && names.equals(other.names);
        }
    }

    /** A tag as it was handed over, and what it is drawn as: {@code null} when left as it is. */
    private static final class Decorated {
        final Snapshot source;
        Component input;
        final Component output;

        Decorated(Snapshot source, Component input, Component output) {
            this.source = source;
            this.input = input;
            this.output = output;
        }

        Component output(Component nameTag) {
            return output == null ? nameTag : output;
        }
    }
}
