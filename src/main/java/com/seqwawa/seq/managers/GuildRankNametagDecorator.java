package com.seqwawa.seq.managers;

import com.seqwawa.seq.accessors.NotificationAccessor;
import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.config.Setting;
import com.seqwawa.seq.model.RankPresentation;
import com.seqwawa.seq.utils.ColorRamp;
import com.seqwawa.seq.utils.ComponentTextEditor;
import com.seqwawa.seq.utils.RankGradientAnimation;
import com.seqwawa.seq.utils.WynnPillGlyphs;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;

/**
 * Swaps the Wynncraft rank badge on a player's in-world nametag for the member's
 * Sequoia Discord rank, the same substitution {@link DiscordRankChatDecorator}
 * makes in guild chat and on the Discord bridge. Players with no linked Sequoia
 * rank keep the nametag Wynncraft sent, untouched.
 * <p>
 * The rewrite happens at the very last step before a nametag is drawn, in
 * {@code SubmitNodeCollection#submitNameTag}, which every renderer funnels
 * through: vanilla, Wynntils' custom nametag feature, and third-party mods that
 * rebuild the tag from their own data. Rewriting the render state earlier would
 * be simpler, but a mod that replaces {@code nameTag} at render time would then
 * silently drop the Sequoia rank again.
 * <p>
 * That submission carries no entity, only a component, so the player behind a
 * nametag is recovered from the name written on it. {@link #rememberRenderedPlayer}
 * publishes the identities being rendered from the avatar extraction pass, which
 * runs earlier in the same frame; only a name registered there is ever decorated,
 * so an ordinary hologram or mob cannot pick up a rank pill.
 * <p>
 * Everything here runs on the render thread.
 */
public final class GuildRankNametagDecorator {

    /** Bounds on the two caches, ample for the players one client can see at once. */
    private static final int MAX_REMEMBERED_PLAYERS = 256;
    private static final int MAX_CACHED_NAMETAGS = 256;

    private static final int MIN_NAME_LENGTH = 3;
    private static final int MAX_NAME_LENGTH = 16;

    /** Names seen on a rendered avatar this session, mapped to who is behind them. */
    private static final Map<String, Member> MEMBERS_BY_NAME = boundedMap(MAX_REMEMBERED_PLAYERS);

    /** What was last published for a player, so an unchanged frame costs one lookup. */
    private static final Map<UUID, Registration> REGISTRATIONS = boundedMap(MAX_REMEMBERED_PLAYERS);

    /**
     * Decoration results keyed by the component handed to the renderer, including
     * the ones left alone. A nametag is submitted every frame from a component that
     * upstream caches, so without this the pill would be rebuilt — and its animated
     * colours re-registered — sixty times a second per player.
     */
    private static final Map<Component, Decoration> DECORATED_NAMETAGS = decorationCache();

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

        RankPresentation rank = rankFor(uuid, username);
        Registration previous = REGISTRATIONS.get(uuid);
        if (previous != null && previous.covers(rank, nameTag)) {
            return;
        }

        // Names are only taken back once the player stops being a member. A tag that
        // merely changed — Wynncraft drops it past the name-rendering distance, so it
        // comes and goes as one walks — republishes over its own entries, and clearing
        // every cached decoration for that would rebuild each visible pill per frame.
        boolean changed = rank == null && previous != null && withdraw(previous.names(), username);
        Member member = rank == null ? null : new Member(username, rank);
        List<String> published = new ArrayList<>(2);
        if (member != null) {
            changed |= publish(username, member, published);
            for (String name : nameCandidates(nameTag == null ? "" : nameTag.getString())) {
                changed |= publish(name, member, published);
            }
        }
        REGISTRATIONS.put(uuid, new Registration(nameTag, rank, List.copyOf(published)));

        // A name that belonged to nobody a moment ago may belong to a member now, so
        // the answers cached against the previous roster cannot be trusted.
        if (changed) {
            forgetDecorations();
        }
    }

    /**
     * The nametag to draw in place of {@code nameTag}: the same tag with its rank
     * badge replaced by the speaker's Sequoia rank, or {@code nameTag} itself when
     * it belongs to nobody the roster knows.
     */
    public static Component decorate(Component nameTag) {
        if (nameTag == null || !isEnabled() || MEMBERS_BY_NAME.isEmpty()) {
            return nameTag;
        }

        Decoration cached = DECORATED_NAMETAGS.get(nameTag);
        if (cached != null) {
            return cached.component();
        }

        Decoration decorated;
        try {
            decorated = decorate(nameTag, GuildRankNametagDecorator::member);
        } catch (RuntimeException exception) {
            SeqClient.LOGGER.debug("[DiscordRanks] Failed to decorate a nametag.", exception);
            decorated = Decoration.unchanged(nameTag);
        }
        DECORATED_NAMETAGS.put(nameTag, decorated);
        return decorated.component();
    }

    /**
     * Decoration core, parameterised on the identity lookup so it stays unit-testable.
     * <p>
     * The colours are pinned rather than merely registered: a nametag stands on screen
     * for as long as its owner is in sight, far longer than the chat line this registry
     * was built for, and a stop evicted underneath it would drop that one glyph out of
     * step with the rest of the pill.
     */
    static Decoration decorate(Component nameTag, Function<String, Member> members) {
        List<ComponentTextEditor.Fragment> fragments = ComponentTextEditor.flatten(nameTag);
        String text = ComponentTextEditor.textOf(fragments);
        DisplayedName name = displayedName(text, members);
        if (name == null) {
            return Decoration.unchanged(nameTag);
        }

        Member member = name.member();
        String label = PrincessRankEasterEgg.pillLabel(member.rank().pillLabel(), member.username());
        Badge badge = badgeBefore(text, name.start());
        if (label.equalsIgnoreCase(badgeLabel(text, badge))) {
            return Decoration.unchanged(nameTag);
        }

        RankGradientAnimation.Pinned<Component> pinned = RankGradientAnimation.pin(
                () -> rewrite(fragments, name, badge, label, badgeColor(fragments, badge)));
        return pinned.value() == null
                ? Decoration.unchanged(nameTag)
                : new Decoration(pinned.value(), pinned.colors());
    }

    private static Component rewrite(
            List<ComponentTextEditor.Fragment> fragments,
            DisplayedName name,
            Badge badge,
            String label,
            TextColor badgeColor) {
        RankPresentation rank = name.member().rank();
        List<ComponentTextEditor.Fragment> coloured = paintName(fragments, name, rank);
        MutableComponent replacement = Component.empty()
                .append(NotificationAccessor.wynnPill(
                        label,
                        DiscordRankChatDecorator.rampFor(rank),
                        DiscordRankChatDecorator.roleRampFor(rank),
                        DiscordRankChatDecorator.PILL_LABEL_COLOR,
                        null,
                        badgeColor))
                .append(Component.literal(" "));

        if (badge == null) {
            return ComponentTextEditor.toComponent(
                    ComponentTextEditor.insertAt(coloured, name.start(), replacement));
        }
        return ComponentTextEditor.replaceRange(coloured, badge.start(), badge.endExclusive(), replacement);
    }

    /**
     * The colour Wynncraft drew the badge in, which the pill returns to when member
     * colouring is switched off. Without it the pill would fall back to the plain
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
     * Paints the member's name in their rank colours, so the name and the pill in
     * front of it read as one label rather than as a Sequoia rank stuck on a
     * Wynncraft-coloured name.
     * <p>
     * A gradient has to be painted a code point at a time, since a component leaf
     * carries one colour; a solid rank keeps the name in one piece, which is a
     * nametag's usual shape and less for the font to lay out every frame.
     */
    private static List<ComponentTextEditor.Fragment> paintName(
            List<ComponentTextEditor.Fragment> fragments, DisplayedName name, RankPresentation rank) {
        ColorRamp displayRamp = DiscordRankChatDecorator.rampFor(rank);
        ColorRamp roleRamp = DiscordRankChatDecorator.roleRampFor(rank);
        if (!displayRamp.isGradient() && !roleRamp.isGradient()) {
            return ComponentTextEditor.restyleRange(
                    fragments,
                    name.start(),
                    name.endExclusive(),
                    style -> DiscordRankChatDecorator.withRegisteredColor(
                            style,
                            RankGradientAnimation.colorAt(
                                    displayRamp,
                                    roleRamp,
                                    0d,
                                    RankGradientAnimation.Target.USERNAME,
                                    style.getColor())));
        }
        return ComponentTextEditor.restyleRangeByPosition(
                fragments,
                name.start(),
                name.endExclusive(),
                (style, position) -> DiscordRankChatDecorator.withRegisteredColor(
                        style,
                        RankGradientAnimation.colorAt(
                                displayRamp,
                                roleRamp,
                                position,
                                RankGradientAnimation.Target.USERNAME,
                                style.getColor())));
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
     * A real space ends the search, so a marker another mod puts in front of the
     * badge is not swallowed with it.
     */
    static Badge badgeBefore(String text, int nameStart) {
        int end = nameStart;
        while (end > 0 && Character.isWhitespace(text.charAt(end - 1))) {
            end--;
        }

        int start = end;
        while (start > 0) {
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
        for (WynnPillGlyphs.Pill pill : WynnPillGlyphs.findPills(text)) {
            if (pill.start() >= badge.start() && pill.endExclusive() <= badge.endExclusive()) {
                return pill.label();
            }
        }
        return null;
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

    private static Member member(String name) {
        return MEMBERS_BY_NAME.get(name);
    }

    /**
     * Maps {@code name} to {@code member}, recording it as published so it can be
     * taken back later.
     *
     * @return whether this changed who that name resolves to
     */
    private static boolean publish(String name, Member member, List<String> published) {
        String key = key(name);
        if (key == null) {
            return false;
        }
        published.add(key);
        return !Objects.equals(MEMBERS_BY_NAME.put(key, member), member);
    }

    /**
     * Takes back the names a player published, e.g. once they no longer hold a rank.
     * A name another player has since claimed is left with its current owner.
     *
     * @return whether any name stopped resolving to a member
     */
    private static boolean withdraw(List<String> names, String username) {
        boolean changed = false;
        for (String name : names) {
            Member current = MEMBERS_BY_NAME.get(name);
            if (current != null && current.username().equalsIgnoreCase(username)) {
                MEMBERS_BY_NAME.remove(name);
                changed = true;
            }
        }
        return changed;
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

    /** The member's rank, resolved on their account first and their name second. */
    private static RankPresentation rankFor(UUID uuid, String username) {
        DiscordRankService service = DiscordRankService.getInstance();
        RankPresentation byAccount = service.presentationForMinecraftUuid(uuid);
        return byAccount != null ? byAccount : service.presentationForMinecraftUsername(username);
    }

    private static boolean isEnabled() {
        Setting.BooleanSetting setting = SeqClient.getShowNametagRanksSetting();
        return setting != null && setting.getValue();
    }

    /**
     * The decoration cache, which hands a dropped decoration's colours back to the
     * animation registry: nothing else knows when a nametag has stopped being drawn.
     */
    private static Map<Component, Decoration> decorationCache() {
        return Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, false) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Component, Decoration> eldest) {
                if (size() <= MAX_CACHED_NAMETAGS) {
                    return false;
                }
                RankGradientAnimation.release(eldest.getValue().colors());
                return true;
            }
        });
    }

    private static void forgetDecorations() {
        synchronized (DECORATED_NAMETAGS) {
            DECORATED_NAMETAGS.values().forEach(decoration -> RankGradientAnimation.release(decoration.colors()));
            DECORATED_NAMETAGS.clear();
        }
    }

    private static <K, V> Map<K, V> boundedMap(int maximumEntries) {
        // Insertion ordered on purpose: an access-ordered map mutates on a read, and
        // these are read from the render loop.
        return Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, false) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                return size() > maximumEntries;
            }
        });
    }

    /** A player whose nametag should carry a Sequoia rank. */
    record Member(String username, RankPresentation rank) {}

    /** A rewritten nametag and the pinned colours it is drawn with. */
    record Decoration(Component component, List<TextColor> colors) {

        /** A nametag that belongs to nobody known, cached so it is only examined once. */
        static Decoration unchanged(Component nameTag) {
            return new Decoration(nameTag, List.of());
        }
    }

    /** Where a known member's name sits on a nametag. */
    record DisplayedName(int start, int endExclusive, Member member) {}

    /** A run of text on a nametag that is shaped like a Minecraft account name. */
    private record Name(String value, int start) {}

    /** The span a rank badge occupies, up to and including the space after it. */
    record Badge(int start, int endExclusive) {}

    /** What was last published for a player, to recognise a frame that changed nothing. */
    private record Registration(Component nameTag, RankPresentation rank, List<String> names) {
        private boolean covers(RankPresentation currentRank, Component currentNameTag) {
            return Objects.equals(rank, currentRank) && Objects.equals(nameTag, currentNameTag);
        }
    }
}
