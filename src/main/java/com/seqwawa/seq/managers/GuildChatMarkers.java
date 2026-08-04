package com.seqwawa.seq.managers;

import com.seqwawa.seq.utils.ComponentTextEditor;
import java.util.List;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;

/**
 * Remembers the arrow and the continuation bar Wynncraft draws down the left of
 * guild chat, captured from real guild lines as they go by.
 * <p>
 * Both are glyphs of a Wynncraft font this mod cannot read: the server resource
 * pack ships them obfuscated. Redrawing them by hand never matches, because the
 * shape, the width and the spacing all have to be right. Copying the glyphs
 * straight off an incoming line sidesteps that and stays correct even if
 * Wynncraft changes the artwork.
 *
 * @see DiscordRankChatDecorator#bridgePrefix()
 */
public final class GuildChatMarkers {

    /** A captured marker: the glyphs plus the style, crucially the font, that renders them. */
    public record Marker(String glyphs, Style style) {

        /** Codepoint count, which tells the arrow (longer) from the bar. */
        int length() {
            return glyphs.codePointCount(0, glyphs.length());
        }
    }

    /** The bar is a short run; the arrow carries an icon and is longer. */
    private static final int MAX_BAR_CODEPOINTS = 3;

    private static volatile Marker arrow;
    private static volatile Marker bar;

    private GuildChatMarkers() {}

    public static Marker arrow() {
        return arrow;
    }

    public static Marker bar() {
        return bar;
    }

    /**
     * Records the marker sitting at the start of a guild line: the leading run of
     * fragments drawn in a font other than the one carrying the message text, taken
     * from the start of the line up to {@code badgeStart}. Long runs are the arrow,
     * short ones the bar.
     */
    public static void observe(List<ComponentTextEditor.Fragment> fragments, int badgeStart) {
        if (fragments.isEmpty() || badgeStart <= 0) {
            return;
        }

        Style markerStyle = null;
        StringBuilder glyphs = new StringBuilder();
        int cursor = 0;

        for (ComponentTextEditor.Fragment fragment : fragments) {
            int fragmentStart = cursor;
            cursor += fragment.text().length();
            if (fragmentStart >= badgeStart) {
                break;
            }

            String text = fragment.text();
            if (text.isBlank()) {
                continue;
            }
            // The marker is drawn in a font of its own. Everything else ahead of the
            // badge, such as a timestamp added by another mod and the spaces around it,
            // uses the default font, which is what keeps it out of the capture.
            if (FontDescription.DEFAULT.equals(fragment.style().getFont())) {
                continue;
            }
            if (markerStyle == null || !markerStyle.getFont().equals(fragment.style().getFont())) {
                markerStyle = fragment.style();
                glyphs.setLength(0);
            }
            glyphs.append(text.strip());
        }

        if (markerStyle == null || glyphs.isEmpty()) {
            return;
        }

        Marker captured = new Marker(glyphs.toString(), markerStyle);
        if (captured.length() <= MAX_BAR_CODEPOINTS) {
            bar = captured;
        } else {
            arrow = captured;
        }
    }

    /** Forgets both markers, so a resource pack change is picked up cleanly. */
    public static void reset() {
        arrow = null;
        bar = null;
    }
}
