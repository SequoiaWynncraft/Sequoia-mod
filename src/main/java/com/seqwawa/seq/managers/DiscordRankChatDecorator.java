package com.seqwawa.seq.managers;

import com.seqwawa.seq.accessors.NotificationAccessor;
import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.model.RankPresentation;
import com.seqwawa.seq.model.SeqBadgeTier;
import com.seqwawa.seq.utils.ColorRamp;
import com.seqwawa.seq.utils.ComponentTextEditor;
import com.seqwawa.seq.utils.PacketTextNormalizer;
import com.seqwawa.seq.utils.WynnPillGlyphs;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;

/**
 * Swaps the Wynncraft guild rank shown in chat for the member's Sequoia Discord
 * rank, and gives guild bridge messages the same badge.
 * <p>
 * In guild chat Wynncraft draws the in-game rank as a glyph "pill"
 * ({@code RECRUITER}); this replaces that pill in place with the linked Discord
 * progression rank ({@code SAPLING}) and leaves the rest of the line alone,
 * including Wynntils' timestamps, nickname rendering and shift-click
 * insertions. Members with no linked Discord rank keep their Wynncraft rank.
 */
public final class DiscordRankChatDecorator {

    /**
     * Wynncraft guild ranks; every other pill (account ranks, etc.) is left alone.
     * Ordered longest first so {@code recruiter} wins over {@code recruit}.
     */
    private static final List<String> WYNNCRAFT_GUILD_RANKS =
            List.of("strategist", "recruiter", "recruit", "captain", "chief", "owner");

    /** Used when the backend publishes no colour for a rank, e.g. Upper Strategist. */
    private static final TextColor FALLBACK_RANK_COLOR = TextColor.fromLegacyFormat(ChatFormatting.DARK_GREEN);

    /**
     * Pill labels are muted greys rather than pure black or white, which read as
     * harsh next to Wynncraft's chat. A little of the background bleeds through so
     * each label sits with its own pill instead of looking pasted on.
     */
    private static final int LIGHT_LABEL_GREY = 0xDCDCDC;
    private static final int DARK_LABEL_GREY = 0x2B2B2B;
    private static final double LABEL_GREY_WEIGHT = 0.82d;

    /**
     * Relative luminance above which a rank colour counts as light and needs dark
     * pill text. Discord role colours are often pastel, so a light label alone would
     * be unreadable on them.
     */
    private static final double LIGHT_BACKGROUND_LUMINANCE = 0.55d;

    /** Aqua Wynncraft uses for guild chat; see {@code ChatManager}. */
    private static final int GUILD_CHAT_COLOR = 0x55FFFF;

    /** Bar drawn down the left of a bridge block, used until a real one is captured. */
    static final String BRIDGE_CONTINUATION_GLYPH = "\uF8F1";
    /** Discord mark dropped into the middle of Wynncraft's arrow. */
    static final String BRIDGE_ICON_GLYPH = "\uF8F2";
    /** One pixel of positive advance, repeated to widen a prefix. */
    private static final char BRIDGE_ADVANCE_PAD = '\uF8FD';
    /** One pixel of negative advance, repeated to narrow one. */
    private static final char BRIDGE_ADVANCE_TRIM = '\uF8FE';
    /** Discord blurple, so a bridged line reads as Discord at a glance. */
    private static final TextColor DISCORD_ACCENT = TextColor.fromRgb(0x5865F2);
    private static final FontDescription BRIDGE_PREFIX_FONT = new FontDescription.Resource(
            Identifier.fromNamespaceAndPath("seq", "discord_bridge"));

    /** Insignia glyphs, in tier order; see {@code assets/seq/font/insignia.json}. */
    private static final FontDescription INSIGNIA_FONT =
            new FontDescription.Resource(Identifier.fromNamespaceAndPath("seq", "insignia"));
    private static final Map<SeqBadgeTier, String> INSIGNIA_GLYPHS = Map.of(
            SeqBadgeTier.BRONZE, "\uF8E0",
            SeqBadgeTier.SILVER, "\uF8E1",
            SeqBadgeTier.GOLD, "\uF8E2",
            SeqBadgeTier.DIAMOND, "\uF8E3");

    private static final Map<SeqBadgeTier, ChatFormatting> INSIGNIA_COLORS = Map.of(
            SeqBadgeTier.BRONZE, ChatFormatting.GOLD,
            SeqBadgeTier.SILVER, ChatFormatting.GRAY,
            SeqBadgeTier.GOLD, ChatFormatting.YELLOW,
            SeqBadgeTier.DIAMOND, ChatFormatting.AQUA);

    /** Legacy formatting marker, which Wynncraft and add-ons embed straight in the text. */
    private static final char LEGACY_FORMATTING_PREFIX = '§';

    private static final Pattern USERNAME_PATTERN = Pattern.compile("[a-zA-Z0-9_]{3,16}");
    /** Wynntils renders nicked players as {@code Nickname(RealUsername)}. */
    private static final Pattern NICKNAME_WITH_USERNAME_PATTERN =
            Pattern.compile("\\(([a-zA-Z0-9_]{3,16})\\)\\s*$");

    private static volatile boolean debug;
    private static boolean suppressed;
    private static boolean bridgeSequenceOpen;
    /** The last line this mod displayed itself, kept so it can be recognised by identity. */
    private static Component lastBridgeLine;

    private DiscordRankChatDecorator() {}

    // ── Wynncraft guild chat ──

    /**
     * Returns {@code message} with its Wynncraft guild rank pill replaced by the
     * speaker's Discord rank, or the untouched {@code message} when the line is not
     * guild chat or the speaker has no linked rank.
     */
    public static Component decorateGuildChat(Component message) {
        // Every ordinary chat line ends the current bridge block. A bridged line must
        // not, so it is recognised by identity as well as by the suppression flag:
        // another mod may queue the message and deliver it here after
        // displayUndecorated has returned, closing the very block it opened.
        boolean ownLine = suppressed || message == lastBridgeLine;
        if (!ownLine) {
            bridgeSequenceOpen = false;
        }

        if (message == null || !isEnabled() || ownLine) {
            return message;
        }

        if (!WynnPillGlyphs.containsPill(message.getString())) {
            logInspection(message, "no glyph badge in line");
            return message;
        }

        DiscordRankService service = DiscordRankService.getInstance();
        if (!service.hasRanks()) {
            logInspection(message, "no linked ranks loaded yet");
            return message;
        }

        try {
            Component decorated = decorateGuildChat(message, service::presentationForMinecraftUsername);
            logInspection(message, decorated == message ? "no guild rank badge or unlinked speaker" : "rewritten");
            return decorated;
        } catch (RuntimeException exception) {
            SeqClient.LOGGER.debug("[DiscordRanks] Failed to decorate guild chat line.", exception);
            return message;
        }
    }

    /** Decoration core, parameterised on the rank lookup so it stays unit-testable. */
    static Component decorateGuildChat(Component message, Function<String, RankPresentation> rankLookup) {
        List<ComponentTextEditor.Fragment> fragments = ComponentTextEditor.flatten(message);
        String text = ComponentTextEditor.textOf(fragments);

        for (WynnPillGlyphs.Pill pill : WynnPillGlyphs.findPills(text)) {
            String guildRank = guildRankOf(pill.label());
            if (guildRank == null) {
                continue;
            }

            Component rewritten = replaceBadge(fragments, text, pill, rankLookup, guildRank);
            if (rewritten != null) {
                return rewritten;
            }
        }

        return decorateByPosition(fragments, text, rankLookup, message);
    }

    /**
     * Fallback for when the badge text cannot be read, which happens if Wynncraft
     * moves its rank glyphs to codepoints this build does not know. It replaces the
     * glyph run sitting closest to the speaker's name, so it is restricted to lines
     * carrying Wynncraft's guild chat colour: that keeps it off global chat, where
     * the same shape holds an account rank badge instead.
     */
    private static Component decorateByPosition(
            List<ComponentTextEditor.Fragment> fragments,
            String text,
            Function<String, RankPresentation> rankLookup,
            Component original) {
        if (!hasGuildChatColor(fragments)) {
            return original;
        }

        List<WynnPillGlyphs.Pill> runs = WynnPillGlyphs.findGlyphRuns(text);
        for (int index = runs.size() - 1; index >= 0; index--) {
            Component rewritten = replaceBadge(fragments, text, runs.get(index), rankLookup, null);
            if (rewritten != null) {
                return rewritten;
            }
        }
        return original;
    }

    private static Component replaceBadge(
            List<ComponentTextEditor.Fragment> fragments,
            String text,
            WynnPillGlyphs.Pill badge,
            Function<String, RankPresentation> rankLookup,
            String replacedGuildRank) {
        int colonIndex = text.indexOf(':', badge.endExclusive());
        if (colonIndex < 0) {
            return null;
        }
        Speaker speaker = resolveSpeaker(rankLookup, fragments, text, badge.endExclusive());
        if (speaker == null) {
            return null;
        }
        RankPresentation rank = speaker.rank();

        int start = decorationStart(fragments, text, badge.start());
        GuildChatMarkers.observe(fragments, start);
        int end = decorationEnd(text, badge.endExclusive(), colonIndex);

        TextColor rankColor = colorFor(rank);
        int nameEnd = speakerNameEnd(fragments, text, end, colonIndex, speaker.username());
        // The resolved username is stamped on as the shift-click insertion too: names
        // are matched from the displayed "Nick(Username)" text rather than from an
        // insertion, so one is not guaranteed to be there, and where Wynncraft does set
        // it a nicked player's carries the nickname rather than the account.
        String speakerName = speaker.username();
        List<ComponentTextEditor.Fragment> recoloured = ComponentTextEditor.restyleRange(
                fragments, end, nameEnd, style -> style.withColor(rankColor).withInsertion(speakerName));

        // The pill keeps the default font on purpose: Wynncraft's badge uses a font of
        // its own in which these glyphs mean nothing, so inheriting it collapses the
        // pill to no width and drags the rest of the line left.
        MutableComponent replacement =
                Component.empty().append(rankPill(rank, replacedGuildRank)).append(Component.literal(" "));
        List<ComponentTextEditor.Fragment> withBadge =
                insertInsignia(recoloured, colonIndex, speaker.username());
        return ComponentTextEditor.replaceRange(withBadge, start, end, replacement);
    }

    /**
     * Upper bound of the speaker's displayed name, i.e. where Wynncraft's
     * {@code Nickname(RealUsername)} reveal begins.
     * <p>
     * The cut is the first fragment carrying the resolved username, the one piece of
     * information known to be right here. Colours and brackets are not usable:
     * Wynncraft and Wynntils both build these lines with legacy {@code §} codes
     * inside the text, so a fragment's own colour says nothing about how it renders.
     * When the name and the reveal share one fragment there is no safe cut, and this
     * returns {@code nameStart} so nothing is recoloured: leaving the name plain is
     * harmless, repainting the reveal is not.
     */
    static int speakerNameEnd(
            List<ComponentTextEditor.Fragment> fragments,
            String text,
            int nameStart,
            int colonIndex,
            String username) {
        if (username == null || username.isBlank()) {
            return colonIndex;
        }
        String region = text.substring(nameStart, colonIndex);
        if (region.strip().equalsIgnoreCase(username)) {
            return colonIndex;
        }

        // The reveal is appended as a legacy "§c(...)" run inside the text, so the
        // first section sign already marks the end of the name.
        int legacyCode = region.indexOf(LEGACY_FORMATTING_PREFIX);
        if (legacyCode >= 0) {
            return nameStart + legacyCode;
        }

        int cursor = 0;
        for (ComponentTextEditor.Fragment fragment : fragments) {
            int fragmentStart = cursor;
            cursor += fragment.text().length();
            if (cursor <= nameStart || fragmentStart >= colonIndex) {
                continue;
            }
            if (!containsIgnoreCase(fragment.text(), username)) {
                continue;
            }
            // A fragment that is exactly the username is the name itself, not a
            // reveal, so recolour it and stop after it.
            return fragment.text().strip().equalsIgnoreCase(username)
                    ? Math.min(colonIndex, cursor)
                    : Math.max(nameStart, fragmentStart);
        }
        return colonIndex;
    }

    private static boolean containsIgnoreCase(String haystack, String needle) {
        return haystack.toLowerCase(Locale.ROOT).contains(needle.toLowerCase(Locale.ROOT));
    }

    /**
     * Where the replacement starts: the later of the two bounds below, so both have
     * to agree before a character is dropped.
     */
    static int decorationStart(List<ComponentTextEditor.Fragment> fragments, String text, int badgeStart) {
        return Math.max(badgeFontRunStart(fragments, badgeStart), glyphRunStart(text, badgeStart));
    }

    /**
     * Walks back over glyphs and spaces, stopping at the first visible character.
     * This is what protects a line whose badge shares the default font with the rest
     * of it: a bridged message, say, where a timestamp added by another mod sits
     * right before the pill and must not be swallowed.
     */
    static int glyphRunStart(String text, int badgeStart) {
        int start = badgeStart;
        int index = badgeStart;
        while (index > 0) {
            int codePoint = text.codePointBefore(index);
            if (!isDecoration(codePoint) && codePoint != ' ') {
                break;
            }
            index -= Character.charCount(codePoint);
            if (codePoint != ' ') {
                start = index;
            }
        }
        return start;
    }

    /**
     * Start of the run of fragments sharing the badge's font.
     * <p>
     * Wynncraft draws a guild line with three fonts: one for the guild icon, one for
     * the rank badge, and the default one for the text. The badge's own pieces
     * (background blocks, corners and letters) all sit in the badge font even though
     * they differ in colour, while the icon does not. The font boundary is therefore
     * exactly where the badge begins, and it keeps the icon whole without having to
     * recognise its codepoints.
     */
    static int badgeFontRunStart(List<ComponentTextEditor.Fragment> fragments, int badgeStart) {
        FontDescription badgeFont = fontAt(fragments, badgeStart);
        int runStart = 0;
        FontDescription runFont = null;
        int cursor = 0;

        for (ComponentTextEditor.Fragment fragment : fragments) {
            int fragmentStart = cursor;
            cursor += fragment.text().length();

            FontDescription font = fragment.style().getFont();
            if (fragmentStart == 0 || !Objects.equals(font, runFont)) {
                runFont = font;
                runStart = fragmentStart;
            }
            if (cursor > badgeStart) {
                return Objects.equals(font, badgeFont) ? runStart : badgeStart;
            }
        }
        return badgeStart;
    }

    /**
     * Widens the replaced span forwards to where the speaker's name begins, so no
     * fragment of the original badge survives between the new pill and the name.
     * Spaces are absorbed here because the replacement re-adds one.
     */
    static int decorationEnd(String text, int badgeEnd, int limit) {
        int end = badgeEnd;
        while (end < limit) {
            int codePoint = text.codePointAt(end);
            if (!isDecoration(codePoint) && codePoint != ' ') {
                break;
            }
            end += Character.charCount(codePoint);
        }
        return end;
    }

    /**
     * Wynncraft's chat decoration glyphs. The categories mirror
     * {@link PacketTextNormalizer}: the badge and icon glyphs are spread across
     * private-use <em>and</em> unassigned codepoints, and missing the unassigned ones
     * cuts the walk short, leaving the old pill showing behind the new one.
     */
    private static boolean isDecoration(int codePoint) {
        return switch (Character.getType(codePoint)) {
            case Character.CONTROL,
                    Character.FORMAT,
                    Character.PRIVATE_USE,
                    Character.SURROGATE,
                    Character.UNASSIGNED -> true;
            default -> false;
        };
    }

    /** The font of the fragment holding {@code index}, so the new pill renders identically. */
    private static FontDescription fontAt(List<ComponentTextEditor.Fragment> fragments, int index) {
        return styleAt(fragments, index).getFont();
    }

    /** The style of the fragment holding {@code index}, or {@link Style#EMPTY}. */
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

    /** True when any part of the line uses Wynncraft's aqua guild chat colour. */
    private static boolean hasGuildChatColor(List<ComponentTextEditor.Fragment> fragments) {
        return fragments.stream().anyMatch(fragment -> {
            TextColor color = fragment.style().getColor();
            return color != null && color.getValue() == GUILD_CHAT_COLOR;
        });
    }

    /**
     * The Wynncraft guild rank a decoded pill label denotes, or {@code null} for any
     * other badge. A suffix match tolerates a stray leading glyph decoding into the
     * label, which happens when Wynncraft pads a badge with an extra font character.
     */
    static String guildRankOf(String pillLabel) {
        String normalized = pillLabel == null ? "" : pillLabel.toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
        for (String rank : WYNNCRAFT_GUILD_RANKS) {
            if (normalized.endsWith(rank)) {
                return rank;
            }
        }
        return null;
    }

    /**
     * Identifies the speaker from the name region between the rank pill and the
     * {@code ':'} separator, so player names mentioned inside the message body are
     * never mistaken for the sender.
     */
    private static Speaker resolveSpeaker(
            Function<String, RankPresentation> rankLookup,
            List<ComponentTextEditor.Fragment> fragments,
            String text,
            int pillEnd) {
        int colonIndex = text.indexOf(':', pillEnd);
        if (colonIndex < 0) {
            return null;
        }

        for (String candidate : speakerCandidates(fragments, text, pillEnd, colonIndex)) {
            RankPresentation rank = rankLookup.apply(candidate);
            if (rank != null) {
                return new Speaker(candidate, rank);
            }
        }
        return null;
    }

    /** The player a guild chat line belongs to, and the rank that was matched for them. */
    private record Speaker(String username, RankPresentation rank) {}

    private static Set<String> speakerCandidates(
            List<ComponentTextEditor.Fragment> fragments, String text, int regionStart, int regionEnd) {
        Set<String> candidates = new LinkedHashSet<>();

        String displayedName = PacketTextNormalizer.normalizeForParsing(text.substring(regionStart, regionEnd));
        Matcher nicknameMatcher = NICKNAME_WITH_USERNAME_PATTERN.matcher(displayedName);
        if (nicknameMatcher.find()) {
            candidates.add(nicknameMatcher.group(1));
        }
        addUsernameCandidate(candidates, displayedName);
        addUsernameCandidate(candidates, NicknameResolverCache.resolveUsername(displayedName));

        int cursor = 0;
        for (ComponentTextEditor.Fragment fragment : fragments) {
            int fragmentStart = cursor;
            cursor += fragment.text().length();
            if (cursor <= regionStart || fragmentStart >= regionEnd) {
                continue;
            }
            addUsernameCandidate(candidates, ChatManager.extractHoverRealUsername(fragment.style()));
            addUsernameCandidate(candidates, ChatManager.extractInsertionUsername(fragment.style()));
        }

        return candidates;
    }

    private static void addUsernameCandidate(Set<String> candidates, String candidate) {
        if (candidate != null && USERNAME_PATTERN.matcher(candidate.trim()).matches()) {
            candidates.add(candidate.trim());
        }
    }

    // ── Discord guild bridge ──

    /**
     * Rank of a bridge message sender, or {@code null} when they have none. Callers
     * use it for both the badge and the sender name colour.
     */
    public static RankPresentation bridgeRank(String senderName, String discordId) {
        return isEnabled()
                ? DiscordRankService.getInstance().presentationForBridgeSender(senderName, discordId)
                : null;
    }

    /** The aqua Wynncraft paints guild chat with, so bridged lines read the same. */
    public static TextColor guildChatColor() {
        return TextColor.fromRgb(GUILD_CHAT_COLOR);
    }

    /**
     * Prefix for the next Discord bridge line: the Discord arrow at the start of a
     * block, then an aligned vertical bar while bridge messages remain consecutive.
     */
    public static MutableComponent bridgePrefix() {
        return padToCommonWidth(bridgeSequenceOpen ? continuationBar() : discordMark());
    }

    /**
     * Pads the prefix out to whichever of the two forms is wider, so the message text
     * starts on the same column whether the line opens a bridge block or continues
     * one. The logo and the bar are unrelated glyphs and have no reason to advance by
     * the same amount; measuring both and topping up the shorter keeps them aligned
     * without either being redrawn to a particular width.
     */
    private static MutableComponent padToCommonWidth(MutableComponent prefix) {
        try {
            Font font = Minecraft.getInstance().font;
            int target = Math.max(font.width(discordMark()), font.width(continuationBar()));
            return appendAdvance(prefix, target - font.width(prefix));
        } catch (RuntimeException | LinkageError ignored) {
            // No client available (unit tests); the prefix keeps its natural width.
            return prefix;
        }
    }

    /**
     * Appends {@code pixels} of pure advance, positive or negative, so a prefix can be
     * widened or narrowed without drawing anything.
     */
    private static MutableComponent appendAdvance(MutableComponent target, int pixels) {
        if (pixels == 0) {
            return target;
        }
        char glyph = pixels < 0 ? BRIDGE_ADVANCE_TRIM : BRIDGE_ADVANCE_PAD;
        return target.append(Component.literal(String.valueOf(glyph).repeat(Math.abs(pixels)))
                .withStyle(style -> style.withFont(BRIDGE_PREFIX_FONT)));
    }

    /**
     * A separating space on an unstyled root. It must not be a sibling of the marker,
     * or it inherits the marker's font, which has no space glyph and would draw
     * Minecraft's missing-character box.
     */
    private static MutableComponent withSeparator(MutableComponent marker) {
        return Component.empty().append(marker).append(Component.literal(" "));
    }

    /**
     * Opens a bridge block with Wynncraft's own guild chat marker, its icon swapped
     * for the Discord logo. The arrow is not redrawn: the captured glyph is re-emitted
     * unchanged and only the icon slot, which Wynncraft itself varies between the
     * guild shield, the bomb and the raid icons, carries the Discord mark instead.
     */
    private static MutableComponent discordMark() {
        GuildChatMarkers.Marker marker = GuildChatMarkers.arrow();
        MutableComponent mark = Component.empty();
        if (marker != null) {
            // Only the glyph that pins Wynncraft's markers to the left margin, so the
            // logo lines up with the bar drawn under it. The rest of the frame is left
            // out: it is fixed-size art, and forcing a wider logo through it detaches
            // its right-hand piece into what looks like a second arrow.
            int[] codePoints = marker.glyphs().codePoints().toArray();
            mark.append(Component.literal(new String(codePoints, 0, 1))
                    .withStyle(marker.style().withColor(DISCORD_ACCENT).withoutShadow()));
        }
        mark.append(Component.literal(BRIDGE_ICON_GLYPH).withStyle(bridgeMarkStyle()));
        return withSeparator(matchGuildMarkerWidth(mark, marker));
    }

    /**
     * Makes the Discord mark advance exactly as far as the guild marker it stands in
     * for, so a bridged line and an in-game guild line put their rank pill on the same
     * column.
     * <p>
     * Dropping most of Wynncraft's arrow and substituting a logo of a different width
     * is what pushes the two apart, and by an amount only the loaded resource pack
     * knows. Measuring both and closing the gap is therefore the only way to align
     * them; redrawing the logo to a guessed width is not.
     */
    private static MutableComponent matchGuildMarkerWidth(MutableComponent mark, GuildChatMarkers.Marker marker) {
        if (marker == null) {
            return mark;
        }
        try {
            Font font = Minecraft.getInstance().font;
            int guildMarker = font.width(Component.literal(marker.glyphs()).withStyle(marker.style()));
            return appendAdvance(mark, guildMarker - font.width(mark));
        } catch (RuntimeException | LinkageError ignored) {
            // No client available (unit tests); the mark keeps its natural width.
            return mark;
        }
    }

    /**
     * Inserts the member's insignia just behind their name, after the nick reveal and
     * before the {@code ':'}, or returns the fragments unchanged when they hold none.
     * The caller passes the separator index it already resolved: searching for one
     * here would have to skip the rank badge, and any glyph inside the message body
     * would throw that search off.
     */
    static List<ComponentTextEditor.Fragment> insertInsignia(
            List<ComponentTextEditor.Fragment> fragments, int colonIndex, String username) {
        SeqBadgeTier tier = insigniaOf(username);
        String glyph = tier == null ? null : INSIGNIA_GLYPHS.get(tier);
        if (glyph == null || colonIndex < 0) {
            return fragments;
        }

        return ComponentTextEditor.insertAt(fragments, colonIndex, insigniaBadge(tier));
    }

    /** The insignia glyph with its tooltip, shared by guild chat and the bridge. */
    static MutableComponent insigniaBadge(SeqBadgeTier tier) {
        MutableComponent tooltip = Component.literal("Sequoia insignia: ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(insigniaName(tier)).withStyle(INSIGNIA_COLORS.get(tier)));
        return Component.literal(INSIGNIA_GLYPHS.get(tier))
                .withStyle(style -> style.withFont(INSIGNIA_FONT).withHoverEvent(new HoverEvent.ShowText(tooltip)));
    }

    /**
     * Insignia of a bridge sender, or {@code null} when they hold none or the setting
     * is off. Guild chat gets the same check from {@link #decorateGuildChat(Component)},
     * which returns early; the bridge builds its line itself and so must check here.
     */
    public static MutableComponent bridgeInsignia(String senderName, String discordId) {
        try {
            // The lookup is a lambda so the roster is not even touched when the
            // setting is off; the gate lives in the overload below.
            return bridgeInsignia(
                    senderName, name -> DiscordRankService.getInstance().insigniaForBridgeSender(name, discordId));
        } catch (RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    /** Insignia core, parameterised on the lookup so the setting gate stays testable. */
    static MutableComponent bridgeInsignia(String senderName, Function<String, SeqBadgeTier> insigniaLookup) {
        if (!isEnabled()) {
            return null;
        }
        SeqBadgeTier tier = insigniaLookup.apply(senderName);
        return tier == null ? null : insigniaBadge(tier);
    }

    /** Title case, so the tooltip reads "Diamond" rather than the enum name. */
    private static String insigniaName(SeqBadgeTier tier) {
        String name = tier.name();
        return name.charAt(0) + name.substring(1).toLowerCase(Locale.ROOT);
    }

    /** The member's insignia tier, or {@code null} when the service is unavailable. */
    private static SeqBadgeTier insigniaOf(String username) {
        try {
            return DiscordRankService.getInstance().insigniaForMinecraftUsername(username);
        } catch (RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    /**
     * Style of the Discord mark: its own font, blurple, and keeping Minecraft's text
     * shadow so it sits under the logo the way Wynncraft's chat icons do.
     */
    static Style bridgeMarkStyle() {
        return Style.EMPTY.withFont(BRIDGE_PREFIX_FONT).withColor(DISCORD_ACCENT);
    }

    /**
     * Continues a bridge block with the bar Wynncraft draws down the left of a wrapped
     * guild line, captured from a real line so width and spacing match exactly. Falls
     * back to this mod's own glyph until one has been seen.
     */
    private static MutableComponent continuationBar() {
        GuildChatMarkers.Marker bar = GuildChatMarkers.bar();
        if (bar == null) {
            return withSeparator(Component.literal(BRIDGE_CONTINUATION_GLYPH)
                    .withStyle(style ->
                            style.withFont(BRIDGE_PREFIX_FONT).withColor(DISCORD_ACCENT).withoutShadow()));
        }
        // Wynncraft's bar ends with its own spacer glyph. Adding a separator on top of
        // it would push the line right of the one above.
        return Component.empty()
                .append(Component.literal(bar.glyphs())
                        .withStyle(bar.style().withColor(DISCORD_ACCENT).withoutShadow()));
    }

    /**
     * Runs {@code display} with decoration turned off, for lines this mod builds
     * itself. A bridged message already carries its rank pill, and re-processing it
     * would only risk clipping whatever another mod prepended, such as a timestamp.
     * Chat is displayed on the render thread and {@code addMessage} runs
     * synchronously, so a plain flag is enough.
     */
    public static void displayUndecorated(Component line, Runnable display) {
        lastBridgeLine = line;
        suppressed = true;
        try {
            display.run();
            bridgeSequenceOpen = true;
        } finally {
            suppressed = false;
        }
    }

    // ── Shared ──

    /** Builds the glyph pill for {@code rank}, tooltipped with the rank it replaces. */
    static MutableComponent rankPill(RankPresentation rank, String replacedWynncraftRank) {
        MutableComponent pill = NotificationAccessor.wynnPill(
                rank.pillLabel(), rampFor(rank), DiscordRankChatDecorator::labelColorOn, null);

        // The tooltip is one component, so it cannot carry the gradient; it takes the
        // primary stop, which is the colour Discord itself shows the role under.
        TextColor primary = colorFor(rank);
        MutableComponent tooltip = Component.literal("Sequoia rank: ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(rank.label()).withStyle(style -> style.withColor(primary)));
        if (replacedWynncraftRank != null && !replacedWynncraftRank.isBlank()) {
            tooltip.append(Component.literal("\nGuild rank: " + capitalize(replacedWynncraftRank))
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        return pill.withStyle(style -> style.withHoverEvent(new HoverEvent.ShowText(tooltip)));
    }

    /**
     * The member's colour stops, falling back to a neutral single colour when neither
     * they nor their role publishes any, so an uncoloured rank still renders a
     * readable pill.
     */
    static ColorRamp rampFor(RankPresentation rank) {
        return rank.colors().isEmpty() ? ColorRamp.of(FALLBACK_RANK_COLOR.getValue()) : rank.colors();
    }

    /**
     * A single colour for the rank, used where a gradient cannot be drawn: the
     * speaker's name, the bridge sender, and tooltips are each one component.
     */
    static TextColor colorFor(RankPresentation rank) {
        return TextColor.fromRgb(rampFor(rank).first());
    }

    /**
     * A muted label colour for {@code background}: mostly grey, tinted with the
     * background's own hue, and light or dark depending on which side stays legible.
     */
    static TextColor labelColorOn(TextColor background) {
        int rgb = background.getValue();
        int grey = luminanceOf(rgb) > LIGHT_BACKGROUND_LUMINANCE ? DARK_LABEL_GREY : LIGHT_LABEL_GREY;
        return TextColor.fromRgb(blend(rgb, grey, LABEL_GREY_WEIGHT));
    }

    /** Mixes {@code target} over {@code base}, {@code weight} being the share of target. */
    static int blend(int base, int target, double weight) {
        return ColorRamp.blend(base, target, weight);
    }

    /** Rec. 709 relative luminance in {@code [0, 1]}. */
    static double luminanceOf(int rgb) {
        double red = ((rgb >> 16) & 0xFF) / 255d;
        double green = ((rgb >> 8) & 0xFF) / 255d;
        double blue = (rgb & 0xFF) / 255d;
        return 0.2126d * red + 0.7152d * green + 0.0722d * blue;
    }

    private static boolean isEnabled() {
        return SeqClient.getShowDiscordRanksSetting() != null
                && SeqClient.getShowDiscordRanksSetting().getValue();
    }

    // ── Diagnostics ──

    /**
     * When on, every chat line the decorator declines to rewrite is dumped to the log
     * with its codepoints. Wynncraft's rank pill glyphs are invisible in normal
     * output, so this is the only practical way to confirm which codepoints a given
     * server build actually sends. Toggled by {@code /seq rank debug}.
     */
    public static void setDebug(boolean enabled) {
        debug = enabled;
    }

    /** Whether undecorated guild lines are being dumped to the log. */
    public static boolean isDebug() {
        return debug;
    }

    private static void logInspection(Component message, String outcome) {
        if (!debug) {
            return;
        }
        String text = ComponentTextEditor.textOf(ComponentTextEditor.flatten(message));
        if (text.indexOf(':') < 0) {
            return;
        }

        List<WynnPillGlyphs.Pill> pills = WynnPillGlyphs.findPills(text);
        String speakers = pills.stream()
                .map(pill -> pill.label() + "->"
                        + speakerCandidates(
                                ComponentTextEditor.flatten(message),
                                text,
                                pill.endExclusive(),
                                Math.max(pill.endExclusive(), indexOfColon(text, pill.endExclusive()))))
                .toList()
                .toString();
        SeqClient.LOGGER.info(
                "[DiscordRanks] {} | badges={} | speakers={} | codepoints={}",
                outcome,
                pills.stream().map(WynnPillGlyphs.Pill::label).toList(),
                speakers,
                describeCodepoints(text));
    }

    private static int indexOfColon(String text, int from) {
        int colonIndex = text.indexOf(':', from);
        return colonIndex < 0 ? from : colonIndex;
    }

    /** Renders a chat line with every non-ASCII character shown as {@code U+XXXX}. */
    static String describeCodepoints(String text) {
        StringBuilder described = new StringBuilder();
        text.codePoints().limit(220).forEach(codePoint -> {
            if (codePoint >= 0x20 && codePoint < 0x7F) {
                described.append((char) codePoint);
            } else {
                described.append(String.format("<U+%04X>", codePoint));
            }
        });
        return described.toString();
    }

    private static String capitalize(String value) {
        return value.substring(0, 1).toUpperCase(Locale.ROOT) + value.substring(1).toLowerCase(Locale.ROOT);
    }
}
