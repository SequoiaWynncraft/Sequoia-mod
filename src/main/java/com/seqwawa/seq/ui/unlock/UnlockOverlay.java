package com.seqwawa.seq.ui.unlock;

import static com.seqwawa.seq.managers.ThemeManager.color;
import static com.seqwawa.seq.ui.theme.UiColor.*;

import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.managers.AssetManager;
import com.seqwawa.seq.managers.CombatState;
import com.seqwawa.seq.ui.theme.UiColor;
import com.seqwawa.seq.ui.unlock.UnlockAnimation.Frame;
import com.seqwawa.seq.ui.unlock.UnlockPopup.Kind;
import com.seqwawa.seq.utils.rendering.UiCanvas;
import com.seqwawa.seq.utils.rendering.UiCanvas.HorizontalAlign;
import com.seqwawa.seq.utils.rendering.UiRenderer;
import java.awt.Color;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

/**
 * Plays the popups for Leaf rewards over the game. Toasts stack in the corner;
 * cards and ceremonies take turns in the middle. A popup's clock starts the
 * first time it is drawn, so one that arrives while a menu is open waits.
 *
 * <p>Everything is pixel art on a grid of art pixels: sprites at whole scales,
 * frames and banners drawn pixel by pixel, text in the Minecraft font at sizes
 * where each of its pixels covers whole UI pixels, and every gap a whole number
 * of art pixels.
 */
public final class UnlockOverlay {

    /**
     * The medal's look. Both share a disc whose middle is 16 art pixels in from
     * their top left, so emblems and numbers sit the same in either. The ribbon
     * medal was the first design; switch back here to use it again.
     */
    enum MedalStyle {
        /** A round medal with ivy climbing its rim from seven o'clock, up the left and over the top. */
        IVY("unlock_medal_ring_", 32, 29, true),
        /** An octagon with a bevelled rim, like the popups' clipped-corner frames. */
        BADGE("unlock_medal_", 32, 27, false),
        /** A round medal hanging two swallowtail ribbons: the first design. */
        RIBBON("unlock_medal_ribbon_", 42, 30, false);

        final String prefix;
        /** The sprite's height in art pixels; all are 32 wide. */
        final int height;
        /** How far below the medal's middle the ceremony's title sits, in art pixels. */
        final int titleBelow;
        /**
         * Whether ivy climbs it: drawn over the medal, 44 art pixels square and 6 out from the medal on every
         * side. The ivy clings to the rim and reaches 3 of those 6 pixels.
         */
        final boolean ivy;

        MedalStyle(String prefix, int height, int titleBelow, boolean ivy) {
            this.prefix = prefix;
            this.height = height;
            this.titleBelow = titleBelow;
            this.ivy = ivy;
        }

        /** How far the medal's drawing reaches out beyond the medal itself on every side, in art pixels. */
        int reach() {
            return ivy ? 3 : 0;
        }
    }

    static final MedalStyle MEDAL_STYLE = MedalStyle.IVY;

    /** UI pixels per art pixel for toasts and cards. */
    static final float SMALL_PIXEL = 2;
    /** UI pixels per art pixel for the ceremony. */
    static final float BIG_PIXEL = 3;
    static final int MAX_TOASTS = 3;
    static final int MAX_WAITING = 8;
    static final float TOAST_WIDTH = 92 * SMALL_PIXEL;
    static final float TOAST_HEIGHT = 26 * SMALL_PIXEL;
    static final float CARD_MAX_WIDTH = 160 * SMALL_PIXEL;
    static final float CARD_MAX_HEIGHT = 50 * SMALL_PIXEL;
    private static final String FONT = "mc";
    private static final int LEAVES = 10;
    private static final int EMBERS = 28;
    /** Where the background stars sit, as fractions of the screen, clear of the middle. */
    private static final float[][] STARS = {
        {0.12f, 0.18f}, {0.84f, 0.14f}, {0.22f, 0.62f}, {0.78f, 0.58f}, {0.06f, 0.4f},
        {0.93f, 0.36f}, {0.32f, 0.08f}, {0.66f, 0.86f}, {0.4f, 0.9f}, {0.58f, 0.06f},
    };

    private static final UnlockOverlay INSTANCE = new UnlockOverlay(
            () -> System.nanoTime() / 1_000_000L, UnlockOverlay::playSound, CombatState::inCombat);

    private final LongSupplier clock;
    private final Consumer<UnlockPopup> onStart;
    private final BooleanSupplier inCombat;
    private final Deque<UnlockPopup> waitingToasts = new ArrayDeque<>();
    private final Deque<UnlockPopup> waitingCentre = new ArrayDeque<>();
    private final List<Playing> toasts = new ArrayList<>();
    private Playing centre;

    UnlockOverlay(LongSupplier clock, Consumer<UnlockPopup> onStart, BooleanSupplier inCombat) {
        this.clock = clock;
        this.onStart = onStart;
        this.inCombat = inCombat;
    }

    public static UnlockOverlay getInstance() {
        return INSTANCE;
    }

    record Playing(UnlockPopup popup, long startedAtMs) {}

    /** Adds a popup. Past a handful waiting, the oldest is dropped: a flood is not worth replaying. */
    public synchronized void enqueue(UnlockPopup popup) {
        if (popup == null) return;
        Deque<UnlockPopup> queue = popup.kind() == Kind.TOAST ? waitingToasts : waitingCentre;
        queue.addLast(popup);
        while (queue.size() > MAX_WAITING) queue.removeFirst();
    }

    /** Whether nothing is showing or waiting. */
    public synchronized boolean isIdle() {
        return centre == null && toasts.isEmpty() && waitingCentre.isEmpty() && waitingToasts.isEmpty();
    }

    public synchronized void clear() {
        waitingToasts.clear();
        waitingCentre.clear();
        toasts.clear();
        centre = null;
    }

    /** Ends finished popups and starts waiting ones. Returns what is on screen now. */
    synchronized List<Playing> advance(long now) {
        toasts.removeIf(playing -> now - playing.startedAtMs() >= UnlockAnimation.duration(Kind.TOAST));
        while (toasts.size() < MAX_TOASTS && !waitingToasts.isEmpty()) {
            Playing started = new Playing(waitingToasts.removeFirst(), now);
            toasts.add(started);
            onStart.accept(started.popup());
        }
        if (centre != null && now - centre.startedAtMs() >= UnlockAnimation.duration(centre.popup().kind())) {
            centre = null;
        }
        // A ceremony covers the screen, so it never plays in a raid or a war: one that is playing when a
        // fight starts stops and plays again from the start afterwards. Cards and toasts are small and carry on.
        boolean fighting = inCombat.getAsBoolean();
        if (fighting && centre != null && centre.popup().kind() == Kind.CEREMONY) {
            waitingCentre.addFirst(centre.popup());
            centre = null;
        }
        if (centre == null) {
            for (Iterator<UnlockPopup> waiting = waitingCentre.iterator(); waiting.hasNext(); ) {
                UnlockPopup next = waiting.next();
                if (fighting && next.kind() == Kind.CEREMONY) continue;
                waiting.remove();
                centre = new Playing(next, now);
                onStart.accept(next);
                break;
            }
        }
        List<Playing> visible = new ArrayList<>(toasts);
        if (centre != null) visible.add(centre);
        return visible;
    }

    public void render(UiCanvas canvas) {
        long now = clock.getAsLong();
        List<Playing> visible = advance(now);
        if (visible.isEmpty()) return;
        float width = canvas.metrics().width();
        float height = canvas.metrics().height();
        // The ceremony first, so toasts and cards stay readable on top of it.
        for (Playing playing : visible) {
            if (playing.popup().kind() != Kind.CEREMONY) continue;
            long elapsed = now - playing.startedAtMs();
            Frame frame = UnlockAnimation.frame(Kind.CEREMONY, elapsed);
            if (frame.active()) drawCeremony(canvas, playing.popup(), frame, elapsed, width, height);
        }
        int toastIndex = 0;
        for (Playing playing : visible) {
            long elapsed = now - playing.startedAtMs();
            Frame frame = UnlockAnimation.frame(playing.popup().kind(), elapsed);
            if (!frame.active()) continue;
            if (playing.popup().kind() == Kind.TOAST) drawToast(canvas, playing.popup(), frame, width, toastIndex++);
            if (playing.popup().kind() == Kind.CARD) drawCard(canvas, playing.popup(), frame, elapsed, width);
        }
    }

    // ── Toast: Leaf from one raid or war ──

    /**
     * 92 by 26 art pixels: a one-pixel frame, a 20-pixel tile three pixels in
     * showing where the Leaf came from, and the amount and its source three
     * pixels after the tile.
     */
    private static void drawToast(UiCanvas canvas, UnlockPopup popup, Frame frame, float screenWidth, int index) {
        float p = SMALL_PIXEL;
        float x = snap(screenWidth - 4 * p - TOAST_WIDTH + frame.slide() * (TOAST_WIDTH + 4 * p), p);
        float y = snap(toastTop(screenWidth) + index * (TOAST_HEIGHT + 3 * p), p);
        float a = frame.opacity();
        Color accent = color(CONTROL_SUCCESS);
        pixelPanel(canvas, x, y, TOAST_WIDTH, TOAST_HEIGHT, p, accent, a);
        iconTile(canvas, popup.icon(), x + 3 * p, y + 3 * p, 20 * p, p, accent, a);
        float textX = x + 26 * p;
        long counted = Math.round(popup.leaf() * frame.counter());
        leafAmount(canvas, "+" + UnlockPopup.formatCount(counted), textX, y + 10 * p, a, p);
        float textRight = x + TOAST_WIDTH - 4 * p;
        fitted(canvas, popup.detail(), textX, y + 19 * p, textRight - textX, 10, color(TEXT_SECONDARY), a);
    }

    /**
     * A tier's medal with the popup's emblem set into its middle at the same whole
     * scale; a level up's medal shows the new level in pixel digits instead.
     */
    private static void medal(UiCanvas canvas, UnlockPopup popup, float x, float y, float scale, float a) {
        String tier = popup.tier() == null ? "gold" : popup.tier();
        sprite(canvas, MEDAL_STYLE.prefix + tier, x, y, 32, scale, a);
        if (MEDAL_STYLE.ivy) sprite(canvas, "unlock_ivy", x - IVY_INSET * scale, y - IVY_INSET * scale, 44, scale, a);
        if ("level".equals(tier)) {
            medalNumber(canvas, popup.level(), x, y, scale, mix(color(tierToken(tier)), Color.WHITE, 0.55f), a);
            return;
        }
        AssetManager.Asset emblem = popup.icon() == null ? null : asset(popup.icon());
        if (emblem == null) return;
        int size = Math.round(emblem.getWidth());
        // The medal's disc is centred 16 art pixels in from its top left.
        float offset = (32 - size) / 2f;
        sprite(canvas, popup.icon(), x + Math.round(offset) * scale, y + Math.round(offset) * scale, size, scale, a);
    }

    /**
     * Writes a number in the middle of a medal's disc (21 art pixels across,
     * centred 16 in from the medal's top left), in the same outlined pixel font as
     * the banner title, as large as fits.
     */
    static void medalNumber(UiCanvas canvas, long number, float medalX, float medalY, float scale, Color fill,
            float a) {
        String text = Long.toString(Math.max(0, number));
        float room = 17 * scale;
        float size = 10 * scale + 20;
        while (size > 10 && measure(text, size) > room) size -= 10;
        // The disc's middle is the line between its 16th and 17th art pixels. Measured in game, the font's
        // middle sits half a UI pixel above where digits look centred at this size.
        float y = medalY + 16 * scale + size * 0.01f;
        outlinedText(canvas, text, medalX + 16 * scale, y, size, fill, a, HorizontalAlign.CENTER);
    }

    /** How far the ivy's square starts out from the medal's, in art pixels. */
    static final int IVY_INSET = 6;

    /** A framed tile with a picture inside it: a raid's art, the war swords or the Sequoia icon. */
    private static void iconTile(UiCanvas canvas, String icon, float x, float y, float size, float p, Color accent, float a) {
        canvas.fillRect(x + p, y + p, size - 2 * p, size - 2 * p, withAlpha(color(CONTROL_INPUT), a));
        pixelBorder(canvas, x, y, size, size, p, withAlpha(accent, a));
        AssetManager.Asset art = icon == null ? null : asset(icon);
        if (art == null) art = asset("icon");
        if (art == null) return;
        float inner = size - 6 * p;
        int artWidth = Math.round(art.getWidth());
        if (artWidth <= 32) {
            // Pixel art at the largest whole scale that fits inside a two-pixel margin, centred.
            float scale = Math.max(1, (float) Math.floor((size - 4 * p) / artWidth));
            float drawn = artWidth * scale;
            sprite(canvas, icon, x + (size - drawn) / 2, y + (size - drawn) / 2, artWidth, scale, a);
        } else {
            canvas.drawImage(art.getImage(), x + 3 * p, y + 3 * p, inner, inner, Math.min(1f, a));
        }
    }

    // ── Card: a finished quest or a Bronze to Gold milestone ──

    /**
     * 160 art pixels wide, 42 tall for a quest and 50 for a milestone. The icon
     * sits five pixels in; the text starts six pixels after it, with its lines
     * ten pixels apart. A band of light sweeps across as it lands.
     */
    private static void drawCard(UiCanvas canvas, UnlockPopup popup, Frame frame, long elapsed, float screenWidth) {
        float p = SMALL_PIXEL;
        boolean milestone = popup.eyebrow() != null && popup.eyebrow().startsWith("Milestone");
        float w = Math.min(CARD_MAX_WIDTH, Math.max(110 * p, screenWidth - 16 * p));
        float h = milestone ? CARD_MAX_HEIGHT : 42 * p;
        float x = snap((screenWidth - w) / 2, p);
        float y = snap(6 * p - frame.slide() * (h + 12 * p), p);
        float a = frame.opacity();
        Color accent = color(tierToken(popup.tier()));
        pixelPanel(canvas, x, y, w, h, p, accent, a);

        float textX;
        float textRight = x + w - 5 * p;
        if (milestone) {
            float medalX = x + (5 + MEDAL_STYLE.reach()) * p;
            float medalY = snap(y + (h - MEDAL_STYLE.height * p) / 2, p);
            float medalCx = medalX + 16 * p;
            float medalCy = medalY + 16 * p;
            // The tier's light behind the medal, kept inside the card.
            canvas.save();
            canvas.scissor(x + p, y + p, w - 2 * p, h - 2 * p);
            sprite(canvas, "unlock_glow_" + popup.tier(), medalCx - 48, medalCy - 48, 96, 1, 0.85f * a);
            canvas.restore();
            shine(canvas, x, y, w, h, p, elapsed, a);
            medal(canvas, popup, medalX, medalY, p, a);
            starBurst(canvas, medalCx, medalCy, elapsed, p, a);
            // Stars twinkling at the card's right end, in their own space clear of the text and the frame.
            twinkle(canvas, x + w - 12 * p, y + 8 * p, frame, 0, p, a);
            twinkle(canvas, x + w - 19 * p, y + 21 * p, frame, 1, p, a);
            textX = medalX + (32 + 6 + MEDAL_STYLE.reach()) * p;
            textRight -= 16 * p;
        } else {
            shine(canvas, x, y, w, h, p, elapsed, a);
            iconTile(canvas, popup.icon(), x + 5 * p, y + (h - 22 * p) / 2, 22 * p, p, accent, a);
            textX = x + 33 * p;
        }
        float textWidth = textRight - textX;
        outlinedText(canvas, fit(popup.eyebrow(), textWidth, 10), textX, y + 9 * p, 10, accent, a, HorizontalAlign.LEFT);
        outlinedText(canvas, fit(popup.title(), textWidth, 20), textX, y + 19 * p, 20, color(TEXT_PRIMARY), a,
                HorizontalAlign.LEFT);
        if (milestone && popup.detail() != null) {
            fitted(canvas, popup.detail(), textX, y + 28 * p, textWidth, 10, color(TEXT_SECONDARY), a);
        }
        drawReward(canvas, popup, textX, y + h - (milestone ? 11 : 10) * p, frame.counter(), a, p);
    }

    /** A diagonal band of light, one art pixel per row, crossing the card once as it lands. */
    private static void shine(UiCanvas canvas, float x, float y, float w, float h, float p, long elapsed, float a) {
        float t = UnlockAnimation.progress(elapsed, 320, 820);
        if (t <= 0 || t >= 1) return;
        int rows = Math.round(h / p) - 2;
        float band = 6 * p;
        float start = snap(x - band - rows * p / 2 + t * (w + band + rows * p), p);
        canvas.save();
        canvas.scissor(x + p, y + p, w - 2 * p, h - 2 * p);
        Color light = new Color(255, 255, 255, Math.round(46 * a));
        for (int row = 1; row <= rows; row++) {
            float rowX = start + Math.round((rows - row) / 2f) * p;
            canvas.fillRect(rowX, y + row * p, band, p, light);
        }
        canvas.restore();
    }

    // ── Ceremony: Platinum and up, and level ups ──

    private static void drawCeremony(
            UiCanvas canvas, UnlockPopup popup, Frame frame, long elapsed, float screenWidth, float screenHeight) {
        // Big art pixels when there is room, smaller ones on a short screen, so the whole group fits.
        float p = screenHeight >= 300 ? BIG_PIXEL : SMALL_PIXEL;
        float a = frame.opacity();
        float dark = frame.backdrop();
        String tier = popup.tier() == null ? "gold" : popup.tier();
        Color accent = color(tierToken(popup.tier()));

        // The world dims a little, and a lot towards the corners.
        canvas.fillRect(0, 0, screenWidth, screenHeight, new Color(6, 4, 10, Math.round(75 * dark)));
        AssetManager.Asset vignette = asset("unlock_vignette");
        if (vignette != null) canvas.drawImage(vignette.getImage(), 0, 0, screenWidth, screenHeight, dark);

        // Embers rising across the whole screen, and stars coming out one by one.
        for (int seed = 1; seed <= EMBERS; seed++) {
            float[] ember = UnlockAnimation.ember(seed, elapsed);
            if (ember == null) continue;
            float size = seed % 4 == 0 ? 2 * p : p;
            Color colour = switch (seed % 3) {
                case 0 -> accent;
                case 1 -> mix(accent, Color.WHITE, 0.6f);
                default -> new Color(255, 236, 170);
            };
            canvas.fillRect(snap(ember[0] * screenWidth, p), snap(ember[1] * screenHeight, p), size, size,
                    withAlpha(colour, 0.9f * a * dark));
        }
        for (int i = 0; i < STARS.length; i++) {
            if (elapsed < 250 + i * 90L) continue;
            int step = (int) ((elapsed / 150 + i) % 3);
            sprite(canvas, "unlock_sparkle_" + step, snap(STARS[i][0] * screenWidth, p), snap(STARS[i][1] * screenHeight, p),
                    7, p, 0.8f * a);
        }

        float cx = snap(screenWidth / 2, p);
        float cy = snap(ceremonyCentre(screenHeight, p), p);
        int[] shake = UnlockAnimation.shake(elapsed);
        float sx = shake[0] * p;
        float sy = shake[1] * p;

        // Light: the tier's glow, breathing, and two layers of rays turning opposite ways.
        float glow = 96 * p;
        float breath = (elapsed / 420) % 2 == 0 ? 1f : 0.82f;
        sprite(canvas, "unlock_glow_" + tier, cx - glow / 2 + sx, cy - glow / 2 + sy, 96, p, a * breath);
        float slow = 96 * (p - 1);
        sprite(canvas, "unlock_rays_" + UnlockAnimation.counterRaysFrame(elapsed), cx - slow / 2, cy - slow / 2, 96, p - 1,
                0.45f * a);
        float rays = 96 * p;
        sprite(canvas, "unlock_rays_" + frame.raysFrame(), cx - rays / 2, cy - rays / 2, 96, p, 0.8f * a);

        // The shockwave as the medal lands.
        int ring = UnlockAnimation.ringFrame(elapsed);
        if (ring >= 0) sprite(canvas, "unlock_ring_" + ring, cx - 32 * p, cy - 32 * p, 64, p, a);

        // Leaves thrown out of the medal.
        if (elapsed > UnlockAnimation.LAND_MS) {
            float fade = elapsed > 3_000 ? Math.max(0, 1 - (elapsed - 3_000) / 1_500f) : 1f;
            String[] leaves = {"unlock_leaf_a", "unlock_leaf_b", "unlock_leaf_c"};
            for (int seed = 1; seed <= LEAVES; seed++) {
                int[] offset = UnlockAnimation.leafOffset(seed * 3, elapsed - UnlockAnimation.LAND_MS + 300);
                sprite(canvas, leaves[seed % leaves.length], cx + offset[0] * p - 3 * p, cy - 6 * p + offset[1] * p, 7, p,
                        a * fade);
            }
        }

        // The medal, popping through whole scales, white for a beat as it lands.
        int scale = medalScaleFor(frame.medalScale(), p);
        if (scale > 0) {
            float mx = cx - 16 * scale + sx;
            float my = cy - 16 * scale + sy;
            medal(canvas, popup, mx, my, scale, a);
            if (UnlockAnimation.flashing(elapsed)) {
                sprite(canvas, MEDAL_STYLE.prefix + "flash", mx, my, 32, scale, 0.85f * a);
            }
        }
        if (elapsed > 400) {
            twinkle(canvas, cx - 30 * p, cy - 18 * p, frame, 0, p, a);
            twinkle(canvas, cx + 24 * p, cy - 24 * p, frame, 1, p, a);
            twinkle(canvas, cx + 27 * p, cy + 8 * p, frame, 2, p, a);
            twinkle(canvas, cx - 27 * p, cy + 12 * p, frame, 1, p, a);
        }

        // The title on a ribbon banner in the tier's colours, typing itself.
        String title = popup.title() == null ? "" : popup.title().toUpperCase(Locale.ROOT);
        float titleSize = 10 * p + 10;
        float titleY = cy + MEDAL_STYLE.titleBelow * p + sy;
        float bannerWidth = snap(measure(title, titleSize) + 16 * p, p) * UnlockAnimation.bannerOpen(elapsed);
        float bannerX = banner(canvas, cx + sx, titleY, snap(bannerWidth, p), BANNER_HEIGHT * p, p, accent, a);
        // Centred on the banner as drawn, which sits on the art-pixel grid, so the margins match.
        float titleX = bannerX + snap(bannerWidth, p) / 2;
        String typed = title.substring(0, UnlockAnimation.typedLetters(elapsed, title.length()));
        // While it types, it grows from where the whole title starts, not from the middle.
        float titleLeft = titleX - measure(title, titleSize) / 2;
        outlinedText(canvas, typed, titleLeft, titleY, titleSize, mix(accent, Color.WHITE, 0.35f), a, HorizontalAlign.LEFT);
        if (elapsed > 450 + UnlockAnimation.TYPE_MS_PER_LETTER * title.length() && popup.detail() != null) {
            outlinedText(canvas, popup.detail(), cx + sx, titleY + 15 * p, 20, color(TEXT_PRIMARY), a,
                    HorizontalAlign.CENTER);
        }
        if (popup.leaf() > 0 && elapsed > 1_100) {
            float chipHeight = 13 * p;
            float chipWidth = snap(rewardWidth(popup) + 10 * p, p);
            float chipX = snap(cx - chipWidth / 2, p);
            float chipY = snap(titleY + 22 * p, p);
            pixelPanel(canvas, chipX, chipY, chipWidth, chipHeight, p, color(CONTROL_SUCCESS), a);
            drawReward(canvas, popup, chipX + 5 * p, chipY + chipHeight / 2, frame.counter(), a, SMALL_PIXEL);
        }
    }

    /**
     * A ribbon banner: a band with a lit top row and a shaded bottom row, and a
     * folded tail with a notch at each end, all a pixel at a time. Returns its left edge.
     */
    private static float banner(UiCanvas canvas, float cx, float cy, float width, float height, float p, Color accent,
            float a) {
        if (width <= 0) return snap(cx, p);
        Color outline = withAlpha(mix(accent, Color.BLACK, 0.85f), a);
        Color band = withAlpha(mix(accent, Color.BLACK, 0.6f), a * 0.92f);
        Color tail = withAlpha(mix(accent, Color.BLACK, 0.75f), a * 0.92f);
        Color lit = withAlpha(mix(accent, Color.BLACK, 0.3f), a);
        float x = snap(cx - width / 2, p);
        float y = snap(cy - height / 2, p);
        // Tails, behind the band and a little lower, each with a V cut into its far end.
        int rows = Math.round(height / p);
        for (int side = 0; side < 2; side++) {
            float tx = side == 0 ? x - 5 * p : x + width - p;
            float ty = y + 3 * p;
            for (int row = 0; row < rows; row++) {
                for (int col = 0; col < 6; col++) {
                    int fromEnd = side == 0 ? col : 5 - col;
                    if (inNotch(row, rows, fromEnd)) continue;
                    boolean edge = row == 0 || row == rows - 1 || fromEnd == 0 || fromEnd == 5
                            || inNotch(row - 1, rows, fromEnd) || inNotch(row + 1, rows, fromEnd)
                            || inNotch(row, rows, fromEnd - 1);
                    canvas.fillRect(tx + col * p, ty + row * p, p, p, edge ? outline : tail);
                }
            }
        }
        canvas.fillRect(x, y, width, height, outline);
        canvas.fillRect(x + p, y + p, width - 2 * p, height - 2 * p, band);
        canvas.fillRect(x + p, y + p, width - 2 * p, p, lit);
        canvas.fillRect(x + p, y + height - 2 * p, width - 2 * p, p, withAlpha(Color.BLACK, 0.3f * a));
        return x;
    }

    /** The V cut into a banner tail's far end: two pixels deep in the middle rows, one pixel either side. */
    static boolean inNotch(int row, int rows, int fromEnd) {
        int middle = rows / 2;
        int depth = row == middle - 1 || row == middle ? 2 : row == middle - 2 || row == middle + 1 ? 1 : 0;
        return fromEnd >= 0 && fromEnd < depth;
    }

    // ── Pieces ──

    /** "+300 [leaf] +150 XP", counting up. */
    private static void drawReward(UiCanvas canvas, UnlockPopup popup, float x, float y, float counter, float a, float p) {
        if (popup.leaf() <= 0) return;
        float after = leafAmount(canvas, "+" + UnlockPopup.formatCount(Math.round(popup.leaf() * counter)), x, y, a, p);
        if (popup.xp() > 0) {
            outlinedText(canvas, "+" + UnlockPopup.formatCount(Math.round(popup.xp() * counter)) + " XP",
                    after + 4 * p, y, 20, color(TEXT_SECONDARY), a, HorizontalAlign.LEFT);
        }
    }

    /** An amount in green with the Leaf after it, two art pixels apart. Returns where the Leaf ends. */
    private static float leafAmount(UiCanvas canvas, String amount, float x, float y, float a, float p) {
        outlinedText(canvas, amount, x, y, 20, color(CONTROL_SUCCESS), a, HorizontalAlign.LEFT);
        float leafX = snap(x + measure(amount, 20) + 2 * p, p);
        sprite(canvas, "leaf", leafX, y - 8, 16, 1, a);
        return leafX + 16;
    }

    /** Stars flung out of a milestone's medal as the card lands, in whole-pixel steps, fading as they go. */
    private static void starBurst(UiCanvas canvas, float cx, float cy, long elapsed, float p, float a) {
        float t = UnlockAnimation.progress(elapsed, 260, 1_000);
        if (t <= 0 || t >= 1) return;
        float distance = UnlockAnimation.steps(t, 8) * 26 * p;
        int step = t < 0.35f ? 2 : t < 0.7f ? 1 : 0;
        for (int i = 0; i < 6; i++) {
            double angle = Math.PI / 6 + i * Math.PI / 3;
            float sx = snap(cx + (float) Math.cos(angle) * distance - 3.5f * p, p);
            float sy = snap(cy + (float) Math.sin(angle) * distance * 0.7f - 3.5f * p, p);
            sprite(canvas, "unlock_sparkle_" + step, sx, sy, 7, p, a * (1 - t * t));
        }
    }

    /** How wide the reward line is once counted up, for sizing its chip. */
    private static float rewardWidth(UnlockPopup popup) {
        float p = SMALL_PIXEL;
        float width = measure("+" + UnlockPopup.formatCount(popup.leaf()), 20) + 2 * p + 16;
        if (popup.xp() > 0) width += 4 * p + measure("+" + UnlockPopup.formatCount(popup.xp()) + " XP", 20);
        return width;
    }

    /** A twinkling sparkle, out of step with its neighbours. */
    private static void twinkle(UiCanvas canvas, float x, float y, Frame frame, int phase, float p, float a) {
        int step = (frame.bob() + phase) % 3;
        sprite(canvas, "unlock_sparkle_" + step, x, y, 7, p, a);
    }

    /**
     * A pixel-art frame: a one-pixel coloured border with its corners cut, a lit
     * top edge and a shadowed bottom edge inside it, over the theme's popup colour.
     */
    static void pixelPanel(UiCanvas canvas, float x, float y, float w, float h, float p, Color accent, float a) {
        canvas.fillRect(x + p, y + p, w - 2 * p, h - 2 * p, withAlpha(color(BACKGROUND_POPUP), Math.min(1f, a * 1.05f)));
        pixelBorder(canvas, x, y, w, h, p, withAlpha(accent, a));
        canvas.fillRect(x + p, y + p, w - 2 * p, p, new Color(255, 255, 255, Math.round(40 * a)));
        canvas.fillRect(x + p, y + h - 2 * p, w - 2 * p, p, new Color(0, 0, 0, Math.round(90 * a)));
    }

    static void pixelBorder(UiCanvas canvas, float x, float y, float w, float h, float p, Color colour) {
        canvas.fillRect(x + p, y, w - 2 * p, p, colour);
        canvas.fillRect(x + p, y + h - p, w - 2 * p, p, colour);
        canvas.fillRect(x, y + p, p, h - 2 * p, colour);
        canvas.fillRect(x + w - p, y + p, p, h - 2 * p, colour);
    }

    private static AssetManager.Asset asset(String key) {
        AssetManager.Asset asset = SeqClient.assetManager == null ? null : SeqClient.assetManager.getAsset(key);
        return asset == null || asset.getImage() == null ? null : asset;
    }

    /** Draws a square-pixel sprite at a whole scale, on the art-pixel grid. */
    private static void sprite(UiCanvas canvas, String key, float x, float y, int artWidth, float scale, float a) {
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
    private static void outlinedText(UiCanvas canvas, String value, float x, float y, float size, Color colour, float a,
            HorizontalAlign align) {
        if (value == null || value.isEmpty() || a <= 0) return;
        // Every glyph's advance includes the one-font-pixel gap after it, so centring the advances leaves the
        // letters half a font pixel left of centre. Move them back.
        if (align == HorizontalAlign.CENTER) x += size / 20;
        float o = Math.max(1, Math.round(size / 10));
        Color dark = withAlpha(mix(colour, new Color(8, 6, 12), 0.82f), a);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                if (dx != 0 || dy != 0) text(canvas, value, Math.round(x + dx * o), Math.round(y + dy * o), size, dark, align);
            }
        }
        text(canvas, value, Math.round(x), Math.round(y), size, withAlpha(colour, a), align);
    }

    private static void fitted(UiCanvas canvas, String value, float x, float y, float width, float size, Color colour,
            float a) {
        if (value == null || a <= 0) return;
        text(canvas, fit(value, width, size), Math.round(x), Math.round(y), size, withAlpha(colour, a),
                HorizontalAlign.LEFT);
    }

    private static String fit(String value, float width, float size) {
        if (value == null || width <= 0) return "";
        String shown = value;
        if (measure(shown, size) > width) {
            while (!shown.isEmpty() && measure(shown + "...", size) > width) shown = shown.substring(0, shown.length() - 1);
            shown = shown.isEmpty() ? "" : shown + "...";
        }
        return shown;
    }

    private static void text(UiCanvas canvas, String value, float x, float y, float size, Color colour,
            HorizontalAlign align) {
        canvas.drawText(value, x, y, new UiCanvas.TextStyle(FONT, size, colour, align, UiCanvas.VerticalAlign.MIDDLE));
    }

    private static float measure(String value, float size) {
        return UiRenderer.measureText(value, FONT, size).width();
    }

    /** Toasts sit in the top corner, or under the card's place when the screen is too narrow for both. */
    static float toastTop(float screenWidth) {
        boolean sideBySide = screenWidth >= CARD_MAX_WIDTH + 2 * (TOAST_WIDTH + 16);
        return sideBySide ? 4 * SMALL_PIXEL : 6 * SMALL_PIXEL + CARD_MAX_HEIGHT + 4 * SMALL_PIXEL;
    }

    /** The title banner's height in art pixels: the title, with clear space above and below it. */
    static final int BANNER_HEIGHT = 18;

    /** How far the ceremony reaches above the medal's middle, in art pixels: its drawing's top, and one more. */
    static int groupAbove() {
        return 16 + MEDAL_STYLE.reach() + 1;
    }

    /** How far the ceremony reaches below the medal's middle, in art pixels: to the reward chip's bottom. */
    static int groupBelow() {
        return MEDAL_STYLE.titleBelow + 35;
    }

    /** Where the medal's middle goes so the group, from the medal's drawing's top to the reward chip's bottom, sits in the middle. */
    static float ceremonyCentre(float screenHeight, float p) {
        return Math.max(groupAbove() * p + 4, screenHeight / 2 - (groupBelow() - groupAbove()) / 2f * p);
    }

    /** The animation's medal scales are for 3-pixel art; smaller art pops through smaller scales. */
    static int medalScaleFor(int scale, float p) {
        if (p >= BIG_PIXEL) return scale;
        return scale >= 4 ? 3 : Math.min(scale, 2);
    }

    static float snap(float value, float pixel) {
        return Math.round(value / pixel) * pixel;
    }

    static Color withAlpha(Color colour, float a) {
        return new Color(colour.getRed(), colour.getGreen(), colour.getBlue(),
                Math.max(0, Math.min(255, Math.round(colour.getAlpha() * a))));
    }

    static Color mix(Color from, Color to, float t) {
        return new Color(
                Math.round(from.getRed() + (to.getRed() - from.getRed()) * t),
                Math.round(from.getGreen() + (to.getGreen() - from.getGreen()) * t),
                Math.round(from.getBlue() + (to.getBlue() - from.getBlue()) * t),
                from.getAlpha());
    }

    static UiColor tierToken(String tier) {
        if (tier == null) return ACCENT_PRIMARY;
        return switch (tier) {
            case "bronze" -> ACHIEVEMENT_BRONZE;
            case "silver" -> ACHIEVEMENT_SILVER;
            case "gold" -> ACHIEVEMENT_GOLD;
            case "platinum" -> ACHIEVEMENT_PLATINUM;
            case "diamond" -> ACHIEVEMENT_DIAMOND;
            case "obsidian" -> ACHIEVEMENT_OBSIDIAN;
            case "mythril" -> ACHIEVEMENT_MYTHRIL;
            case "level" -> CONTROL_SUCCESS;
            default -> ACCENT_PRIMARY;
        };
    }

    /** A short sound for each size of moment, from Minecraft's own UI sounds. */
    private static void playSound(UnlockPopup popup) {
        if (SeqClient.mc == null || SeqClient.mc.getSoundManager() == null) return;
        SimpleSoundInstance sound = switch (popup.kind()) {
            case TOAST -> SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.25f, 0.35f);
            case CARD -> SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, 1.0f, 1.0f);
            case CEREMONY -> "level".equals(popup.tier())
                    ? SimpleSoundInstance.forUI(SoundEvents.PLAYER_LEVELUP, 1.0f, 0.8f)
                    : SimpleSoundInstance.forUI(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 0.8f);
        };
        SeqClient.mc.getSoundManager().play(sound);
    }
}
