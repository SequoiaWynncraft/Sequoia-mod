package com.seqwawa.seq.ui.unlock;

import static com.seqwawa.seq.managers.ThemeManager.color;
import static com.seqwawa.seq.ui.theme.UiColor.*;

import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.managers.AssetManager;
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
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

/**
 * Plays the popups for Leaf rewards over the game. Toasts stack in the corner;
 * cards and ceremonies take turns in the middle. A popup's clock starts the
 * first time it is drawn, so one that arrives while a menu is open waits.
 *
 * <p>Everything is pixel art: sprites drawn at whole scales on a grid of art
 * pixels, frames drawn pixel by pixel, text in the Minecraft font at sizes
 * where each of its pixels covers whole UI pixels.
 */
public final class UnlockOverlay {

    /** UI pixels per art pixel for toasts and cards. */
    static final float SMALL_PIXEL = 2;
    /** UI pixels per art pixel for the ceremony. */
    static final float BIG_PIXEL = 3;
    static final int MAX_TOASTS = 3;
    static final int MAX_WAITING = 8;
    private static final String FONT = "mc";
    private static final int PARTICLES = 16;

    private static final UnlockOverlay INSTANCE =
            new UnlockOverlay(() -> System.nanoTime() / 1_000_000L, UnlockOverlay::playSound);

    private final LongSupplier clock;
    private final Consumer<UnlockPopup> onStart;
    private final Deque<UnlockPopup> waitingToasts = new ArrayDeque<>();
    private final Deque<UnlockPopup> waitingCentre = new ArrayDeque<>();
    private final List<Playing> toasts = new ArrayList<>();
    private Playing centre;

    UnlockOverlay(LongSupplier clock, Consumer<UnlockPopup> onStart) {
        this.clock = clock;
        this.onStart = onStart;
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
        if (centre == null && !waitingCentre.isEmpty()) {
            centre = new Playing(waitingCentre.removeFirst(), now);
            onStart.accept(centre.popup());
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
        int toastIndex = 0;
        for (Playing playing : visible) {
            long elapsed = now - playing.startedAtMs();
            Frame frame = UnlockAnimation.frame(playing.popup().kind(), elapsed);
            if (!frame.active()) continue;
            switch (playing.popup().kind()) {
                case TOAST -> drawToast(canvas, playing.popup(), frame, width, toastIndex++);
                case CARD -> drawCard(canvas, playing.popup(), frame, width);
                case CEREMONY -> drawCeremony(canvas, playing.popup(), frame, elapsed, width, height);
            }
        }
    }

    // ── Toast: Leaf from one raid or war ──

    private static void drawToast(UiCanvas canvas, UnlockPopup popup, Frame frame, float screenWidth, int index) {
        float p = SMALL_PIXEL;
        float w = 176;
        float h = 44;
        float x = snap(screenWidth - 8 - w + frame.slide() * (w + 8), p);
        float y = snap(8 + index * (h + 6), p);
        float a = frame.opacity();
        pixelPanel(canvas, x, y, w, h, p, color(CONTROL_SUCCESS), a);
        sprite(canvas, "leaf", x + 6 * p, y + 3 * p - frame.bob() * p, 16, p, a);
        long counted = Math.round(popup.leaf() * frame.counter());
        shadowText(canvas, "+" + UnlockPopup.formatCount(counted), x + 21 * p, y + 8 * p, 20, color(CONTROL_SUCCESS), a,
                HorizontalAlign.LEFT, p);
        fitted(canvas, popup.detail(), x + 21 * p, y + 17 * p, w - 25 * p, 10, color(TEXT_SECONDARY), a);
    }

    // ── Card: a finished quest or a Bronze to Gold milestone ──

    private static void drawCard(UiCanvas canvas, UnlockPopup popup, Frame frame, float screenWidth) {
        float p = SMALL_PIXEL;
        boolean milestone = popup.tier() != null && !"level".equals(popup.tier()) && popup.eyebrow() != null
                && popup.eyebrow().startsWith("Milestone");
        float w = Math.min(320, Math.max(220, screenWidth - 32));
        float h = milestone ? 96 : 76;
        float x = snap((screenWidth - w) / 2, p);
        float y = snap(12 - frame.slide() * (h + 24), p);
        float a = frame.opacity();
        Color accent = color(tierToken(popup.tier()));
        pixelPanel(canvas, x, y, w, h, p, accent, a);

        float textX;
        if (milestone) {
            sprite(canvas, "unlock_medal_" + popup.tier(), x + 5 * p, y + 3 * p, 32, p, a);
            textX = x + 42 * p;
        } else {
            float tile = 20 * p;
            float tx = x + 6 * p;
            float ty = y + (h - tile) / 2;
            canvas.fillRect(tx, ty, tile, tile, withAlpha(color(CONTROL_INPUT), a));
            pixelBorder(canvas, tx, ty, tile, tile, p, withAlpha(accent, a));
            sprite(canvas, "leaf", tx + 2 * p, ty + 2 * p - frame.bob() * p, 16, p, a);
            textX = x + 32 * p;
        }
        float textWidth = x + w - 6 * p - textX;
        shadowText(canvas, popup.eyebrow(), textX, y + 7 * p, 10, accent, a, HorizontalAlign.LEFT, p / 2);
        fittedShadow(canvas, popup.title(), textX, y + 17 * p, textWidth, 20, color(TEXT_PRIMARY), a, p);
        if (popup.detail() != null) {
            fitted(canvas, popup.detail(), textX, y + 27 * p, textWidth, 10, color(TEXT_SECONDARY), a);
        }
        drawReward(canvas, popup, textX, y + h - 9 * p, frame.counter(), a, p);
        twinkle(canvas, x + w - 7 * p, y + 3 * p, frame, 0, p, a);
        twinkle(canvas, x + w - 15 * p, y + h - 9 * p, frame, 1, p, a);
    }

    // ── Ceremony: Platinum and up, and level ups ──

    private static void drawCeremony(
            UiCanvas canvas, UnlockPopup popup, Frame frame, long elapsed, float screenWidth, float screenHeight) {
        float p = BIG_PIXEL;
        float a = frame.opacity();
        canvas.fillRect(0, 0, screenWidth, screenHeight, new Color(8, 6, 5, Math.round(165 * frame.backdrop())));
        float cx = snap(screenWidth / 2, p);
        float cy = snap(Math.max(110, screenHeight * 0.38f), p);

        float burst = 96 * p;
        sprite(canvas, "unlock_burst_" + frame.burstFrame(), cx - burst / 2, cy - burst / 2, 96, p, 0.55f * a);

        if (elapsed > 300) {
            float fade = elapsed > 3_000 ? Math.max(0, 1 - (elapsed - 3_000) / 1_500f) : 1f;
            String[] leaves = {"unlock_leaf_a", "unlock_leaf_b", "unlock_leaf_c", "unlock_leaf_gold"};
            for (int seed = 1; seed <= PARTICLES; seed++) {
                int[] offset = UnlockAnimation.leafOffset(seed, elapsed);
                sprite(canvas, leaves[seed % leaves.length], cx + offset[0] * p - 3.5f * p,
                        cy - 6 * p + offset[1] * p, 7, p, a * fade);
            }
        }

        int scale = frame.medalScale();
        if (scale > 0) {
            float medalWidth = 32 * scale;
            float medalHeight = 42 * scale;
            sprite(canvas, "unlock_medal_" + (popup.tier() == null ? "gold" : popup.tier()),
                    cx - medalWidth / 2, cy - medalHeight / 2 + 4 * scale, 32, scale, a);
        }
        if (elapsed > 400) {
            twinkle(canvas, cx - 30 * p, cy - 18 * p, frame, 0, p, a);
            twinkle(canvas, cx + 24 * p, cy - 24 * p, frame, 1, p, a);
            twinkle(canvas, cx + 29 * p, cy + 6 * p, frame, 2, p, a);
            twinkle(canvas, cx - 26 * p, cy + 14 * p, frame, 1, p, a);
        }

        Color accent = color(tierToken(popup.tier()));
        String title = popup.title() == null ? "" : popup.title().toUpperCase(Locale.ROOT);
        String typed = title.substring(0, UnlockAnimation.typedLetters(elapsed, title.length()));
        float titleY = cy + 30 * p;
        shadowText(canvas, typed, cx, titleY, 40, accent, a, HorizontalAlign.CENTER, 4);
        if (elapsed > 450 + UnlockAnimation.TYPE_MS_PER_LETTER * title.length() && popup.detail() != null) {
            shadowText(canvas, popup.detail(), cx, titleY + 13 * p, 20, color(TEXT_PRIMARY), a, HorizontalAlign.CENTER, 2);
        }
        if (popup.leaf() > 0 && elapsed > 1_100) {
            float chipWidth = 70 * p;
            float chipHeight = 13 * p;
            float chipX = snap(cx - chipWidth / 2, p);
            float chipY = titleY + 21 * p;
            pixelPanel(canvas, chipX, chipY, chipWidth, chipHeight, p, color(CONTROL_SUCCESS), a);
            drawReward(canvas, popup, chipX + 5 * p, chipY + chipHeight / 2, frame.counter(), a, p * 2 / 3);
        }
    }

    // ── Pieces ──

    /** "+300 [leaf] +150 XP", counting up. */
    private static void drawReward(UiCanvas canvas, UnlockPopup popup, float x, float y, float counter, float a, float p) {
        if (popup.leaf() <= 0) return;
        String amount = "+" + UnlockPopup.formatCount(Math.round(popup.leaf() * counter));
        shadowText(canvas, amount, x, y, 20, color(CONTROL_SUCCESS), a, HorizontalAlign.LEFT, 2);
        float after = x + measure(amount, 20) + 4;
        sprite(canvas, "leaf", after, y - 8, 16, 1, a);
        if (popup.xp() > 0) {
            shadowText(canvas, "+" + UnlockPopup.formatCount(Math.round(popup.xp() * counter)) + " XP",
                    after + 24, y, 20, color(TEXT_SECONDARY), a, HorizontalAlign.LEFT, 2);
        }
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

    /** Draws a square-pixel sprite at a whole scale, on the art-pixel grid. */
    private static void sprite(UiCanvas canvas, String key, float x, float y, int artWidth, float scale, float a) {
        AssetManager.Asset asset = SeqClient.assetManager == null ? null : SeqClient.assetManager.getAsset(key);
        if (asset == null || asset.getImage() == null || a <= 0) return;
        float width = artWidth * scale;
        float height = asset.getHeight() * (width / asset.getWidth());
        canvas.drawImage(asset.getImage(), Math.round(x), Math.round(y), width, height, Math.min(1f, a));
    }

    /** Text with the Minecraft drop shadow: the same text, darker, one font pixel down and right. */
    private static void shadowText(UiCanvas canvas, String value, float x, float y, float size, Color colour, float a,
            HorizontalAlign align, float shadow) {
        if (value == null || value.isEmpty() || a <= 0) return;
        Color dark = new Color(colour.getRed() / 4, colour.getGreen() / 4, colour.getBlue() / 4, Math.round(220 * a));
        text(canvas, value, Math.round(x + shadow), Math.round(y + shadow), size, dark, align);
        text(canvas, value, Math.round(x), Math.round(y), size, withAlpha(colour, a), align);
    }

    private static void fittedShadow(UiCanvas canvas, String value, float x, float y, float width, float size, Color colour,
            float a, float shadow) {
        shadowText(canvas, fit(value, width, size), x, y, size, colour, a, HorizontalAlign.LEFT, shadow);
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

    static float snap(float value, float pixel) {
        return Math.round(value / pixel) * pixel;
    }

    static Color withAlpha(Color colour, float a) {
        return new Color(colour.getRed(), colour.getGreen(), colour.getBlue(),
                Math.max(0, Math.min(255, Math.round(colour.getAlpha() * a))));
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
