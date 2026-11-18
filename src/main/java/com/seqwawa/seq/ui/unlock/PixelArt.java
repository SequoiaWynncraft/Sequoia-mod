package com.seqwawa.seq.ui.unlock;

import static com.seqwawa.seq.managers.ThemeManager.color;
import static com.seqwawa.seq.ui.theme.UiColor.*;

import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.managers.AssetManager;
import com.seqwawa.seq.utils.rendering.UiCanvas;
import com.seqwawa.seq.utils.rendering.UiCanvas.HorizontalAlign;
import com.seqwawa.seq.utils.rendering.UiRenderer;
import java.awt.Color;

/**
 * The pieces Leaf's pixel art is drawn with, shared by the popups, the quest
 * board's header and the sidebar's profile card so they all look like one game.
 *
 * <p>Everything sits on a grid of art pixels {@code p} UI pixels wide: sprites
 * at whole scales, frames a pixel at a time, and text in the Minecraft font at
 * sizes where each of its pixels covers whole UI pixels (10, 20, 30).
 */
public final class PixelArt {

    private static final String FONT = "mc";

    private PixelArt() {}

    /** How opaque the popups' frames are: solid, so they read over anything in the world. */
    public static final float SOLID = 1.05f;
    /** How opaque the frames on the Sequoia screens are, so the world shows through a little, like the screens' own panels. */
    public static final float SEE_THROUGH = 0.72f;

    /**
     * A pixel-art frame: a one-pixel coloured border with its corners cut, and
     * inside it the border's colour fading into the theme's popup colour over two
     * rows at the top, and a row of shade at the bottom.
     */
    public static void pixelPanel(UiCanvas canvas, float x, float y, float w, float h, float p, Color accent, float a) {
        pixelPanel(canvas, x, y, w, h, p, accent, a, SOLID);
    }

    /** A {@link #pixelPanel} whose inside is {@code fill} opaque, {@link #SOLID} or {@link #SEE_THROUGH}. */
    public static void pixelPanel(UiCanvas canvas, float x, float y, float w, float h, float p, Color accent, float a,
            float fill) {
        canvas.fillRect(x + p, y + p, w - 2 * p, h - 2 * p, withAlpha(color(BACKGROUND_POPUP), Math.min(1f, a * fill)));
        pixelBorder(canvas, x, y, w, h, p, withAlpha(accent, a));
        canvas.fillRect(x + p, y + p, w - 2 * p, p, withAlpha(accent, 0.42f * a));
        if (h >= 8 * p) canvas.fillRect(x + p, y + 2 * p, w - 2 * p, p, withAlpha(accent, 0.16f * a));
        canvas.fillRect(x + p, y + h - 2 * p, w - 2 * p, p, withAlpha(mix(accent, Color.BLACK, 0.6f), 0.4f * a));
    }

    /** A one-pixel border with its corners cut. */
    public static void pixelBorder(UiCanvas canvas, float x, float y, float w, float h, float p, Color colour) {
        canvas.fillRect(x + p, y, w - 2 * p, p, colour);
        canvas.fillRect(x + p, y + h - p, w - 2 * p, p, colour);
        canvas.fillRect(x, y + p, p, h - 2 * p, colour);
        canvas.fillRect(x + w - p, y + p, p, h - 2 * p, colour);
    }

    /**
     * A progress bar a pixel at a time: a dark outline with cut corners, a sunken
     * track, and a fill with a lit top row and a shaded bottom row. The fill always
     * ends on a whole art pixel, and anything above nothing shows at least one.
     */
    public static void progressBar(UiCanvas canvas, float x, float y, float w, float h, float p, float ratio,
            Color fill, float a) {
        progressBar(canvas, x, y, w, h, p, ratio, fill, a, 0);
    }

    /**
     * A progress bar cut into {@code notches} equal parts by darker one-pixel lines,
     * like Minecraft's experience bar. The notches sit on whole art pixels.
     */
    public static void progressBar(UiCanvas canvas, float x, float y, float w, float h, float p, float ratio,
            Color fill, float a, int notches) {
        if (w < 3 * p || h < 3 * p) return;
        drawBar(canvas, x, y, w, h, p, ratio, fill, a);
        if (notches < 2) return;
        float innerW = w - 2 * p;
        Color line = withAlpha(new Color(8, 6, 12), 0.45f * a);
        for (int i = 1; i < notches; i++) {
            float nx = x + p + snap(innerW * i / notches, p);
            canvas.fillRect(nx, y + p, p, h - 2 * p, line);
        }
    }

    private static void drawBar(UiCanvas canvas, float x, float y, float w, float h, float p, float ratio,
            Color fill, float a) {
        Color outline = withAlpha(mix(fill, new Color(8, 6, 12), 0.8f), a);
        pixelBorder(canvas, x, y, w, h, p, outline);
        float innerW = w - 2 * p;
        float innerH = h - 2 * p;
        canvas.fillRect(x + p, y + p, innerW, innerH, withAlpha(mix(fill, new Color(8, 6, 12), 0.7f), a));
        canvas.fillRect(x + p, y + p, innerW, p, withAlpha(new Color(0, 0, 0), 0.35f * a));
        float filled = barFill(innerW, p, ratio);
        if (filled <= 0) return;
        canvas.fillRect(x + p, y + p, filled, innerH, withAlpha(fill, a));
        canvas.fillRect(x + p, y + p, filled, p, withAlpha(mix(fill, Color.WHITE, 0.45f), a));
        if (innerH >= 3 * p) {
            canvas.fillRect(x + p, y + h - 2 * p, filled, p, withAlpha(mix(fill, Color.BLACK, 0.3f), a));
        }
    }

    /**
     * How much of a bar's inside is filled: whole art pixels, rounded the same way
     * as the notches are placed, so a third fills exactly to the first of three.
     * Anything above nothing shows a pixel, and anything short of full leaves one.
     */
    public static float barFill(float inner, float p, float ratio) {
        if (!(ratio > 0) || inner <= 0) return 0;
        if (ratio >= 1f) return inner;
        float pixels = Math.round(inner * ratio / p);
        return Math.max(1, Math.min(Math.round(inner / p) - 1, pixels)) * p;
    }

    /**
     * A tier's light round a medal drawn at 2 or 3 UI pixels per art pixel: three
     * clean bands just outside its rim, on the same grid as the medal. {@code tier}
     * is {@code bronze} to {@code mythril}, or {@code level}.
     */
    public static void halo(UiCanvas canvas, String tier, float medalX, float medalY, float scale, float a) {
        sprite(canvas, "unlock_halo_" + tier, medalX - HALO_INSET * scale, medalY - HALO_INSET * scale, 44, scale, a);
    }

    /** How far the halo's 44-pixel square starts out from the medal's 32, in art pixels. Its light reaches 5 of them. */
    public static final int HALO_INSET = 6;

    /** The level medal with the level written in its middle the way Minecraft writes yours, drawn at a whole scale. */
    public static void levelMedal(UiCanvas canvas, long level, float x, float y, float scale, float a) {
        sprite(canvas, UnlockOverlay.MEDAL_STYLE.prefix + "level", x, y, 32, scale, a);
        medalNumber(canvas, level, x, y, scale, a);
    }

    /**
     * Writes a level in the middle of a medal's disc (21 art pixels across,
     * centred 16 in from the medal's top left), as large as fits, in Minecraft's
     * experience-level style.
     */
    static void medalNumber(UiCanvas canvas, long number, float medalX, float medalY, float scale, float a) {
        String text = Long.toString(Math.max(0, number));
        float room = 17 * scale;
        float size = 10 * scale + 20;
        while (size > 10 && measure(text, size) > room) size -= 10;
        levelText(canvas, text, medalX + 16 * scale, medalY + 16 * scale + 1, size, a, HorizontalAlign.CENTER);
    }

    /**
     * The level's colours: lettered the way Minecraft letters your experience level
     * over the hotbar, but in the level medal's own greens, the pale of its rim's
     * highlight with the dark of its outline as the edge, so it sits in the medal
     * instead of glaring out of it.
     */
    public static final Color LEVEL_GREEN = new Color(206, 242, 156);
    public static final Color LEVEL_EDGE = new Color(24, 44, 16);

    /**
     * A level lettered like Minecraft's experience level: a hard edge one font pixel
     * wide above, below and to either side, and no shading. The ink lines up with
     * {@code x} as {@link #outlinedText} does.
     */
    public static void levelText(UiCanvas canvas, String value, float x, float y, float size, float a,
            HorizontalAlign align) {
        if (value == null || value.isEmpty() || a <= 0) return;
        float o = fontPixel(size);
        x += inkShift(align, o);
        Color edge = withAlpha(LEVEL_EDGE, a);
        text(canvas, value, Math.round(x + o), Math.round(y), size, edge, align);
        text(canvas, value, Math.round(x - o), Math.round(y), size, edge, align);
        text(canvas, value, Math.round(x), Math.round(y + o), size, edge, align);
        text(canvas, value, Math.round(x), Math.round(y - o), size, edge, align);
        text(canvas, value, Math.round(x), Math.round(y), size, withAlpha(LEVEL_GREEN, a), align);
    }

    /** A framed tile with a picture inside it: a raid's art, the war swords or the Sequoia icon. */
    public static void iconTile(UiCanvas canvas, String icon, float x, float y, float size, float p, Color accent, float a) {
        canvas.fillRect(x + p, y + p, size - 2 * p, size - 2 * p, withAlpha(color(CONTROL_INPUT), a));
        pixelBorder(canvas, x, y, size, size, p, withAlpha(accent, a));
        AssetManager.Asset art = icon == null ? null : asset(icon);
        String key = icon;
        if (art == null) {
            art = asset("icon");
            key = "icon";
        }
        if (art == null) return;
        float inner = size - 6 * p;
        int artWidth = Math.round(art.getWidth());
        if (artWidth <= 32) {
            // Pixel art at the largest whole scale that fits inside a two-pixel margin, centred.
            float scale = Math.max(1, (float) Math.floor((size - 4 * p) / artWidth));
            float drawn = artWidth * scale;
            sprite(canvas, key, x + (size - drawn) / 2, y + (size - drawn) / 2, artWidth, scale, a);
        } else {
            canvas.drawImage(art.getImage(), x + 3 * p, y + 3 * p, inner, inner, Math.min(1f, a));
        }
    }

    /** The Leaf drawn like a glyph of the pixel font: 9 font pixels square, outline included, like an outlined digit. */
    static final int LEAF_GLYPH = 9;
    /** Font pixels between an amount and its Leaf. */
    static final int LEAF_GAP = 2;

    /**
     * An amount in green, outlined, at {@code size} (10, 20 or 30), with the Leaf
     * after it on the same font-pixel grid, so it scales with the digits. Its ink
     * starts at {@code x}. Returns where the Leaf ends.
     */
    public static float leafAmount(UiCanvas canvas, String amount, float x, float y, float size, float a, float p) {
        outlinedText(canvas, amount, x, y, size, color(CONTROL_SUCCESS), a, HorizontalAlign.LEFT);
        float s = fontPixel(size);
        float leafX = Math.round(x + outlinedInkWidth(amount, size) + LEAF_GAP * s);
        sprite(canvas, "leaf_glyph", leafX, leafTop(y, size), LEAF_GLYPH, s, a);
        return leafX + LEAF_GLYPH * s;
    }

    /** How wide {@link #leafAmount} draws an amount, from its ink's left edge to the Leaf's right edge. */
    public static float leafAmountWidth(String amount, float size, float p) {
        return outlinedInkWidth(amount, size) + (LEAF_GAP + LEAF_GLYPH) * fontPixel(size);
    }

    /**
     * The Leaf's top for text centred on {@code y}, so its nine rows sit on the
     * digits' seven and their outline. Measured in game at 10 and 30: the font's
     * outlined digits start six font pixels less one UI pixel above {@code y}.
     */
    static float leafTop(float y, float size) {
        return Math.round(y - 6 * fontPixel(size) + 1);
    }

    /** UI pixels per pixel of the Minecraft font at {@code size}: 1 at 10, 2 at 20, 3 at 30. */
    public static float fontPixel(float size) {
        return Math.max(1, Math.round(size / 10));
    }

    /**
     * How wide outlined text's ink is: its glyphs' advances, less the one-pixel gap
     * after the last glyph, plus the outline on both sides.
     */
    public static float outlinedInkWidth(String value, float size) {
        return measure(value, size) + fontPixel(size);
    }

    public static AssetManager.Asset asset(String key) {
        AssetManager.Asset asset = SeqClient.assetManager == null ? null : SeqClient.assetManager.getAsset(key);
        return asset == null || asset.getImage() == null ? null : asset;
    }

    /** Draws a square-pixel sprite at a whole scale, on the art-pixel grid. */
    public static void sprite(UiCanvas canvas, String key, float x, float y, int artWidth, float scale, float a) {
        AssetManager.Asset asset = asset(key);
        if (asset == null || a <= 0) return;
        float width = artWidth * scale;
        float height = asset.getHeight() * (width / asset.getWidth());
        canvas.drawImage(asset.getImage(), Math.round(x), Math.round(y), width, height, Math.min(1f, a));
    }

    /**
     * Text with a one-font-pixel outline all round, as pixel games letter their
     * titles: the text in a dark tint of its colour in eight directions, then on top.
     */
    public static void outlinedText(UiCanvas canvas, String value, float x, float y, float size, Color colour, float a,
            HorizontalAlign align) {
        if (value == null || value.isEmpty() || a <= 0) return;
        float o = fontPixel(size);
        x += inkShift(align, o);
        Color dark = withAlpha(mix(colour, new Color(8, 6, 12), 0.82f), a);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                if (dx != 0 || dy != 0) text(canvas, value, Math.round(x + dx * o), Math.round(y + dy * o), size, dark, align);
            }
        }
        text(canvas, value, Math.round(x), Math.round(y), size, withAlpha(colour, a), align);
    }

    /**
     * How far to move text with a one-font-pixel edge so its ink, edge included,
     * lines up with x. Left-aligned, the edge would stick out a font pixel to the
     * left. Centred, {@link #text} already centres the glyphs and the edge is the same
     * on both sides. Right-aligned, {@link #text} already moves the glyphs onto x,
     * so the edge would stick out a font pixel past it.
     */
    private static float inkShift(HorizontalAlign align, float o) {
        return switch (align) {
            case LEFT -> o;
            case CENTER -> 0;
            case RIGHT -> -o;
        };
    }

    /** Plain text in the pixel font, cut short with "..." to fit {@code width}. */
    public static void fitted(UiCanvas canvas, String value, float x, float y, float width, float size, Color colour,
            float a) {
        if (value == null || a <= 0) return;
        text(canvas, fit(value, width, size), Math.round(x), Math.round(y), size, withAlpha(colour, a),
                HorizontalAlign.LEFT);
    }

    public static String fit(String value, float width, float size) {
        if (value == null || width <= 0) return "";
        String shown = value;
        if (measure(shown, size) > width) {
            while (!shown.isEmpty() && measure(shown + "...", size) > width) shown = shown.substring(0, shown.length() - 1);
            shown = shown.isEmpty() ? "" : shown + "...";
        }
        return shown;
    }

    /**
     * Plain text in the pixel font, placed by its ink. Every glyph's advance includes
     * a one-font-pixel gap after it, so right-aligned text would end a font pixel
     * short of {@code x} and centred text would sit half a font pixel left of it.
     */
    public static void text(UiCanvas canvas, String value, float x, float y, float size, Color colour,
            HorizontalAlign align) {
        if (align == HorizontalAlign.RIGHT) x += fontPixel(size);
        if (align == HorizontalAlign.CENTER) x += fontPixel(size) / 2;
        canvas.drawText(value, x, y, new UiCanvas.TextStyle(FONT, size, colour, align, UiCanvas.VerticalAlign.MIDDLE));
    }

    public static float measure(String value, float size) {
        return UiRenderer.measureText(value, FONT, size).width();
    }

    public static float snap(float value, float pixel) {
        return Math.round(value / pixel) * pixel;
    }

    public static Color withAlpha(Color colour, float a) {
        return new Color(colour.getRed(), colour.getGreen(), colour.getBlue(),
                Math.max(0, Math.min(255, Math.round(colour.getAlpha() * a))));
    }

    public static Color mix(Color from, Color to, float t) {
        return new Color(
                Math.round(from.getRed() + (to.getRed() - from.getRed()) * t),
                Math.round(from.getGreen() + (to.getGreen() - from.getGreen()) * t),
                Math.round(from.getBlue() + (to.getBlue() - from.getBlue()) * t),
                from.getAlpha());
    }
}
