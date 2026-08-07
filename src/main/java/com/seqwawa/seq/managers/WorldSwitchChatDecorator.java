package com.seqwawa.seq.managers;

import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.config.Setting;
import com.seqwawa.seq.integrations.WynntilsWorldStateAccess;
import com.seqwawa.seq.utils.ComponentTextEditor;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;

/**
 * Turns a world named in chat into a link that switches to it, so "na6" in guild
 * chat becomes one click rather than a retyped command.
 * <p>
 * Worlds are called out constantly — a fresh profession world, a bomb someone
 * threw — and every one of them is a race against the queue filling. The mention
 * is only underlined and made clickable: the text itself, its colour and the rank
 * decoration around it are left exactly as they were, so a linked line still reads
 * as the line that was sent.
 */
public final class WorldSwitchChatDecorator {

    /** Wynncraft's world command; the mention supplies the world. */
    private static final String SWITCH_COMMAND = "/switch ";

    /** The three regions Wynncraft names its worlds after. */
    private static final Set<String> KNOWN_WORLD_PREFIXES = Set.of("NA", "EU", "AS");

    /**
     * A world mention: letters then digits, bounded so it is a word of its own.
     * Both the prefix and the number are checked afterwards, since this shape also
     * covers plenty of ordinary text.
     */
    private static final Pattern WORLD_MENTION_PATTERN =
            Pattern.compile("(?<![A-Za-z0-9_])([A-Za-z]{2,3})(\\d{1,3})(?![A-Za-z0-9_])");

    private static final char LEGACY_FORMATTING_PREFIX = '§';

    /**
     * Links made on one line. A line listing worlds (shared bombs, {@code /worlds})
     * is the reason this is not one or two, and a line with more mentions than this
     * is not someone naming a world to switch to.
     */
    private static final int MAX_LINKS_PER_LINE = 24;

    private WorldSwitchChatDecorator() {}

    /**
     * Returns {@code message} with every world mention linked, or the untouched
     * {@code message} when it names no world or the feature is off.
     */
    public static Component decorate(Component message) {
        if (message == null || !isEnabled()) {
            return message;
        }
        return decorate(message, worldPrefixes(), runsSwitchOnClick());
    }

    /** Decoration core, parameterised on the settings it reads so it stays unit-testable. */
    static Component decorate(Component message, Set<String> worldPrefixes, boolean runsSwitchOnClick) {
        List<ComponentTextEditor.Fragment> fragments = ComponentTextEditor.flatten(message);
        String text = ComponentTextEditor.textOf(fragments);

        boolean linked = false;
        for (WorldMention mention : findWorldMentions(text, worldPrefixes)) {
            if (!isLinkable(fragments, mention)) {
                continue;
            }
            fragments = ComponentTextEditor.restyleRange(
                    fragments,
                    mention.start(),
                    mention.endExclusive(),
                    style -> linkStyle(style, mention.world(), runsSwitchOnClick));
            linked = true;
        }

        return linked ? ComponentTextEditor.toComponent(fragments) : message;
    }

    /** Every world named in {@code text}, in display order. */
    static List<WorldMention> findWorldMentions(String text, Set<String> worldPrefixes) {
        if (text == null || text.isEmpty() || worldPrefixes == null || worldPrefixes.isEmpty()) {
            return List.of();
        }

        List<WorldMention> mentions = new ArrayList<>();
        Matcher matcher = WORLD_MENTION_PATTERN.matcher(maskLegacyFormatting(text));
        while (matcher.find() && mentions.size() < MAX_LINKS_PER_LINE) {
            String prefix = matcher.group(1).toUpperCase(Locale.ROOT);
            String number = matcher.group(2);
            // Wynncraft never pads a world number, so "NA06" is something else.
            if (!worldPrefixes.contains(prefix) || number.startsWith("0")) {
                continue;
            }
            mentions.add(new WorldMention(matcher.start(), matcher.end(), prefix + number));
        }
        return List.copyOf(mentions);
    }

    /**
     * Blanks each legacy {@code §x} pair, keeping every other character where it is.
     * <p>
     * Wynncraft and Wynntils both leave these codes in the text of the components
     * they build, and a code sitting directly in front of a mention would otherwise
     * join onto it and hide the word boundary the match needs. Offsets have to
     * survive because they index the fragments this line is rebuilt from.
     */
    private static String maskLegacyFormatting(String text) {
        if (text.indexOf(LEGACY_FORMATTING_PREFIX) < 0) {
            return text;
        }

        char[] scanned = text.toCharArray();
        for (int index = 0; index < scanned.length; index++) {
            if (scanned[index] != LEGACY_FORMATTING_PREFIX) {
                continue;
            }
            scanned[index] = ' ';
            if (index + 1 < scanned.length) {
                scanned[index + 1] = ' ';
                index++;
            }
        }
        return new String(scanned);
    }

    /**
     * Whether {@code mention} is free to become a link. A fragment already carrying a
     * click action owns the click, and one carrying a shift-click insertion is a
     * player's name — a three-character username can look exactly like a world.
     */
    private static boolean isLinkable(List<ComponentTextEditor.Fragment> fragments, WorldMention mention) {
        int cursor = 0;
        for (ComponentTextEditor.Fragment fragment : fragments) {
            int fragmentStart = cursor;
            cursor += fragment.text().length();
            if (cursor <= mention.start() || fragmentStart >= mention.endExclusive()) {
                continue;
            }
            if (fragment.style().getClickEvent() != null || fragment.style().getInsertion() != null) {
                return false;
            }
        }
        return true;
    }

    private static Style linkStyle(Style style, String world, boolean runsSwitchOnClick) {
        String command = SWITCH_COMMAND + world;
        return style.withUnderlined(true)
                .withClickEvent(
                        runsSwitchOnClick ? new ClickEvent.RunCommand(command) : new ClickEvent.SuggestCommand(command))
                .withHoverEvent(new HoverEvent.ShowText(tooltip(world, command, runsSwitchOnClick)));
    }

    private static Component tooltip(String world, String command, boolean runsSwitchOnClick) {
        if (!runsSwitchOnClick) {
            return Component.literal("Click to type ")
                    .withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(command).withStyle(ChatFormatting.WHITE));
        }
        return Component.literal("Click to switch to ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(world).withStyle(ChatFormatting.AQUA))
                .append(Component.literal("\n" + command).withStyle(ChatFormatting.DARK_GRAY));
    }

    /**
     * The prefixes worth linking, which is the known set plus the one the current
     * world uses. Wynncraft has renamed its worlds before, and a region this build
     * has never heard of is still recognisable once it is being played on.
     */
    private static Set<String> worldPrefixes() {
        String currentWorld = WynntilsWorldStateAccess.currentWorldName().orElse(null);
        String currentPrefix = prefixOf(currentWorld);
        if (currentPrefix == null || KNOWN_WORLD_PREFIXES.contains(currentPrefix)) {
            return KNOWN_WORLD_PREFIXES;
        }

        Set<String> prefixes = new LinkedHashSet<>(KNOWN_WORLD_PREFIXES);
        prefixes.add(currentPrefix);
        return Set.copyOf(prefixes);
    }

    /** The letters starting {@code worldName}, or {@code null} when it is not world-shaped. */
    static String prefixOf(String worldName) {
        if (worldName == null) {
            return null;
        }
        Matcher matcher = WORLD_MENTION_PATTERN.matcher(worldName.trim());
        return matcher.matches() ? matcher.group(1).toUpperCase(Locale.ROOT) : null;
    }

    private static boolean isEnabled() {
        Setting.BooleanSetting setting = SeqClient.getLinkWorldNamesSetting();
        return setting != null && setting.getValue();
    }

    private static boolean runsSwitchOnClick() {
        Setting.BooleanSetting setting = SeqClient.getWorldLinkRunsSwitchSetting();
        return setting == null || setting.getValue();
    }

    /** A world named at {@code [start, endExclusive)} of a chat line's text. */
    record WorldMention(int start, int endExclusive, String world) {}
}
