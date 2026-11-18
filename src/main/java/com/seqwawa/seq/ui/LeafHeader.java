package com.seqwawa.seq.ui;

import static com.seqwawa.seq.managers.ThemeManager.color;
import static com.seqwawa.seq.ui.theme.UiColor.*;

import com.seqwawa.seq.model.LeafAccount;
import com.seqwawa.seq.ui.unlock.PixelArt;
import com.seqwawa.seq.utils.rendering.UiCanvas;
import com.seqwawa.seq.utils.rendering.UiCanvas.HorizontalAlign;
import java.awt.Color;

/**
 * The top of the quest board, drawn like the reward popups: the level medal in
 * its halo, the level and an XP bar beside
 * it, and the Leaf balance on the right. Where the board is too narrow for the
 * balance beside the level, it gets a strip of its own underneath.
 *
 * <p>Measured in art pixels two UI pixels wide, like the popups' cards.
 */
final class LeafHeader {

    /** UI pixels per art pixel. */
    static final float P = 2;
    /** The header's height with the balance beside the level, in art pixels. */
    static final int HEIGHT = 46;
    /** The strip the balance gets underneath on a narrow board, in art pixels. */
    static final int STRIP = 17;
    /** The narrowest the level's column may be before the balance moves underneath, in UI pixels. */
    static final float MIN_LEVEL_COLUMN = 124;

    /** Shown in place of nothing under the balance when the last read failed. */
    static final String STALE_NOTE = "Couldn't reach Leaf";

    private LeafHeader() {}

    /**
     * Where things go.
     *
     * @param height the header's height in UI pixels
     * @param balanceBeside whether the balance sits on the right, beside the level, or in its own strip underneath
     * @param levelColumn the level column's left edge and width, in UI pixels from the header's left
     */
    record Layout(float height, boolean balanceBeside, float levelX, float levelWidth, float balanceSize) {}

    /** How far in the medal's 32 art pixels start: the same on the left as above and below it. */
    static int medalInset() {
        return (HEIGHT - 32) / 2;
    }

    /**
     * The layout for a header {@code width} UI pixels wide whose balance, drawn
     * large, is {@code largeBalance} wide and, drawn smaller, {@code smallBalance}.
     */
    static Layout layout(float width, float largeBalance, float smallBalance) {
        float levelX = (medalInset() + 32 + 6) * P;
        float right = 7 * P;
        // The balance's column: its own width, then a divider with six art pixels either side.
        for (float[] option : new float[][] {{largeBalance, 30}, {smallBalance, 20}}) {
            float levelWidth = width - levelX - right - option[0] - 13 * P;
            if (levelWidth >= MIN_LEVEL_COLUMN) {
                return new Layout(HEIGHT * P, true, levelX, PixelArt.snap(levelWidth, P), option[1]);
            }
        }
        return new Layout((HEIGHT + STRIP) * P, false, levelX, PixelArt.snap(Math.max(0, width - levelX - right), P), 20);
    }

    /** "640 / 2,500 XP". */
    static String xpLine(LeafAccount.Level level) {
        return QuestBoardScreen.formatCount(level.xpIntoLevel()) + " / " + QuestBoardScreen.formatCount(level.xpForNextLevel())
                + " XP";
    }

    /** "1,860 XP to go": the next level's badge at the track's end says which level. */
    static String toNextLine(LeafAccount.Level level) {
        long left = Math.max(0, level.xpForNextLevel() - level.xpIntoLevel());
        return QuestBoardScreen.formatCount(left) + " XP to go";
    }

    /** The next level's medal at the end of the track, and the disc inside its rim, in art pixels. */
    static final int NEXT = 16;
    static final int NEXT_DISC = 9;
    /** How faded the next level's medal is, until it is reached. */
    static final float NEXT_FADE = 0.7f;

    static float xpRatio(LeafAccount.Level level) {
        if (level.xpForNextLevel() <= 0) return 0;
        return Math.max(0, Math.min(1, (float) level.xpIntoLevel() / level.xpForNextLevel()));
    }

    /**
     * Draws the header with its top left at ({@code x}, {@code y}).
     */
    static void render(UiCanvas canvas, LeafAccount account, boolean stale, float x, float y, float width) {
        float p = P;
        LeafAccount.Level level = account.levelOrFirst();
        String amount = QuestBoardScreen.formatCount(account.leaf());
        Layout layout = layout(width, PixelArt.leafAmountWidth(amount, 30, p), PixelArt.leafAmountWidth(amount, 20, p));
        float h = layout.height();
        Color green = color(CONTROL_SUCCESS);
        PixelArt.pixelPanel(canvas, x, y, width, h, p, PixelArt.mix(green, color(BACKGROUND_POPUP), 0.35f), 1f,
                PixelArt.SEE_THROUGH);

        float medalX = x + medalInset() * p;
        float medalY = y + (HEIGHT - 32) / 2f * p;
        PixelArt.halo(canvas, "level", medalX, medalY, p, 1f);
        PixelArt.levelMedal(canvas, level.number(), medalX, medalY, p, 1f);

        // The level's track: from the medal to the next level's badge, level with the medal's middle, with
        // what it is above it and how far along under it. The medal says which level this is.
        float tx = x + layout.levelX();
        float tw = layout.levelWidth();
        float next = NEXT * p;
        float nextX = tx + tw - next;
        float barWidth = tw - next - 3 * p;
        PixelArt.outlinedText(canvas, "SEQUOIA LEVEL", tx, y + 13 * p, 10, green, 1f, HorizontalAlign.LEFT);
        PixelArt.progressBar(canvas, tx, y + 19 * p, barWidth, 8 * p, p, xpRatio(level), green, 1f, 10);
        // The next level's medal, a small copy of this one, faded until it is reached.
        float nextY = y + (23 - NEXT / 2) * p;
        PixelArt.sprite(canvas, "unlock_medal_mini_level", nextX, nextY, NEXT, p, NEXT_FADE);
        String nextLevel = Long.toString(level.number() + 1L);
        float nextSize = PixelArt.measure(nextLevel, 20) <= NEXT_DISC * p ? 20 : 10;
        PixelArt.levelText(canvas, nextLevel, nextX + next / 2, nextY + next / 2 + 1, nextSize, NEXT_FADE,
                HorizontalAlign.CENTER);
        PixelArt.text(canvas, xpLine(level), tx, y + 33 * p, 10, color(TEXT_SECONDARY), HorizontalAlign.LEFT);
        String toNext = toNextLine(level);
        if (PixelArt.measure(xpLine(level), 10) + PixelArt.measure(toNext, 10) + 6 * p <= barWidth) {
            PixelArt.text(canvas, toNext, tx + barWidth, y + 33 * p, 10, color(TEXT_MUTED), HorizontalAlign.RIGHT);
        }

        if (layout.balanceBeside()) {
            float rx = x + width - 7 * p;
            float divider = tx + tw + 6 * p;
            canvas.fillRect(divider, y + 8 * p, p, (HEIGHT - 16) * p, PixelArt.withAlpha(color(ACCENT_DIVIDER), 0.8f));
            PixelArt.outlinedText(canvas, "BALANCE", rx, y + 13 * p, 10, green, 1f, HorizontalAlign.RIGHT);
            float size = layout.balanceSize();
            float amountWidth = PixelArt.leafAmountWidth(amount, size, p);
            // Large digits' ink sits above their middle, so the amount goes a little lower to clear the label.
            PixelArt.leafAmount(canvas, amount, PixelArt.snap(rx - amountWidth, p), y + (size >= 30 ? 26 : 24) * p, size,
                    1f, p);
            if (stale) {
                PixelArt.text(canvas, STALE_NOTE, rx, y + 35 * p, 10, color(CONTROL_WARNING), HorizontalAlign.RIGHT);
            }
            return;
        }
        // Narrow: the balance in a strip underneath, the label on the left and the amount on the right.
        float stripTop = y + HEIGHT * p;
        canvas.fillRect(x + 4 * p, stripTop - p, width - 8 * p, p, PixelArt.withAlpha(color(ACCENT_DIVIDER), 0.8f));
        float mid = stripTop + (STRIP - 2) / 2f * p;
        PixelArt.outlinedText(canvas, stale ? STALE_NOTE : "BALANCE", x + 7 * p, mid, 10,
                stale ? color(CONTROL_WARNING) : green, 1f, HorizontalAlign.LEFT);
        float amountWidth = PixelArt.leafAmountWidth(amount, 20, p);
        PixelArt.leafAmount(canvas, amount, PixelArt.snap(x + width - 7 * p - amountWidth, p), mid, 20, 1f, p);
    }
}
