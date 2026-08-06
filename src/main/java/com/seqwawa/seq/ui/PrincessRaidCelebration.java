package com.seqwawa.seq.ui;

import com.seqwawa.seq.managers.PrincessMode;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;

/** A brief royal celebration shown after the local player completes a raid. */
public final class PrincessRaidCelebration {
    static final long DURATION_MS = 5_200;
    private static final long INTRO_MS = 850;
    private static final long OUTRO_START_MS = 4_100;
    private static final int PINK = 0xFF5DD6;
    private static final int PALE_PINK = 0xFFD9F2;
    private static final int GOLD = 0xFFD66B;
    private static final int LAVENDER = 0xC9A7FF;
    private static final int[] CONFETTI_COLORS = {PINK, PALE_PINK, GOLD, 0xFFFFFF, LAVENDER};
    private static final List<String> CROWN = List.of(
            "100010001",
            "110111011",
            "111111111",
            "011111110",
            "001111100");

    private static volatile long startedAtMs = Long.MIN_VALUE;

    private PrincessRaidCelebration() {}

    public static void initialize() {
        HudElementRegistry.addLast(
                Identifier.fromNamespaceAndPath("seq", "princess_raid_celebration"),
                (graphics, ignored) -> render(graphics, monotonicMillis()));
    }

    /** Starts the celebration only when the hidden Princess mode is active. */
    public static void triggerIfEnabled() {
        if (PrincessMode.isEnabled()) {
            forceTrigger();
        }
    }

    /** Starts the celebration regardless of mode, for the local test command. */
    public static void forceTrigger() {
        startedAtMs = monotonicMillis();
    }

    private static void render(GuiGraphics graphics, long nowMs) {
        long started = startedAtMs;
        if (started == Long.MIN_VALUE) {
            return;
        }
        AnimationFrame frame = frameAt(nowMs - started);
        if (!frame.active()) {
            if (started != Long.MIN_VALUE && nowMs - started >= DURATION_MS) {
                startedAtMs = Long.MIN_VALUE;
            }
            return;
        }

        Minecraft client = Minecraft.getInstance();
        if (client.font == null) {
            return;
        }

        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        int centerX = width / 2;
        int bannerY = Math.min(height - 48, Math.max(54, height / 3)) + Math.round(frame.bannerOffset());
        int bannerWidth = Math.max(96, Math.min(250, width - 24));
        int bannerLeft = centerX - bannerWidth / 2;
        int alpha = Math.round(frame.opacity() * 255f);

        if (frame.flashOpacity() > 0f) {
            graphics.fill(0, 0, width, height, argb(Math.round(frame.flashOpacity() * 255f), PINK));
        }
        drawEdgeGlow(graphics, width, height, alpha, nowMs - started);
        drawConfetti(graphics, width, height, nowMs - started, frame.confettiIntensity());

        graphics.fill(
                bannerLeft - 3,
                bannerY - 14,
                bannerLeft + bannerWidth + 3,
                bannerY + 31,
                argb(Math.round(alpha * 0.25f), PINK));
        graphics.fill(
                bannerLeft,
                bannerY - 11,
                bannerLeft + bannerWidth,
                bannerY + 28,
                argb(Math.round(alpha * 0.82f), 0x2A0D32));
        drawOutline(graphics, bannerLeft, bannerY - 11, bannerWidth, 39, argb(alpha, PINK));
        drawOutline(graphics, bannerLeft + 3, bannerY - 8, bannerWidth - 6, 33, argb(alpha, GOLD));

        drawCrown(graphics, centerX, bannerY - 34, alpha);
        graphics.drawCenteredString(client.font, "RAID CONQUERED", centerX, bannerY - 5, argb(alpha, 0xFFF7FC));
        graphics.drawCenteredString(
                client.font,
                "Her Majesty's court remains undefeated",
                centerX,
                bannerY + 11,
                argb(Math.round(alpha * 0.92f), PALE_PINK));
    }

    private static void drawEdgeGlow(GuiGraphics graphics, int width, int height, int alpha, long elapsedMs) {
        int pulse = 3 + Math.round((float) (Math.sin(elapsedMs / 180.0) + 1.0) * 2f);
        int color = argb(Math.round(alpha * 0.38f), PINK);
        graphics.fill(0, 0, pulse, height, color);
        graphics.fill(width - pulse, 0, width, height, color);
        graphics.fill(0, 0, width, 2, color);
        graphics.fill(0, height - 2, width, height, color);
    }

    private static void drawConfetti(
            GuiGraphics graphics, int width, int height, long elapsedMs, float intensity) {
        if (intensity <= 0f) {
            return;
        }
        for (int index = 0; index < 56; index++) {
            long spawnAt = Math.round(random01(index * 31 + 7) * 2_700f);
            float progress = (elapsedMs - spawnAt) / (1_700f + random01(index * 47 + 11) * 900f);
            if (progress < 0f || progress > 1f) {
                continue;
            }

            float originX = random01(index * 67 + 13) * width;
            float sway = (float) Math.sin(progress * Math.PI * (2.0 + index % 3) + index) * (7 + index % 9);
            int x = Math.round(originX + sway);
            int y = Math.round(-8 + progress * (height + 18));
            int size = 1 + index % 3;
            int alpha = Math.round(255f * intensity * Math.min(1f, (1f - progress) * 3f));
            int color = argb(alpha, CONFETTI_COLORS[index % CONFETTI_COLORS.length]);

            switch (index % 4) {
                case 0 -> {
                    graphics.fill(x - size, y, x + size + 1, y + 1, color);
                    graphics.fill(x, y - size, x + 1, y + size + 1, color);
                }
                case 1 -> graphics.fill(x, y, x + size + 1, y + size + 1, color);
                case 2 -> graphics.fill(x, y, x + 1, y + size * 3, color);
                default -> {
                    graphics.fill(x - 1, y, x + 2, y + 1, color);
                    graphics.fill(x, y - 1, x + 1, y + 2, color);
                }
            }
        }
    }

    private static void drawCrown(GuiGraphics graphics, int centerX, int top, int alpha) {
        int scale = 3;
        int left = centerX - CROWN.getFirst().length() * scale / 2;
        for (int row = 0; row < CROWN.size(); row++) {
            String pixels = CROWN.get(row);
            for (int column = 0; column < pixels.length(); column++) {
                if (pixels.charAt(column) != '1') {
                    continue;
                }
                int color = row == CROWN.size() - 1 ? PINK : GOLD;
                graphics.fill(
                        left + column * scale,
                        top + row * scale,
                        left + (column + 1) * scale,
                        top + (row + 1) * scale,
                        argb(alpha, color));
            }
        }
    }

    private static void drawOutline(
            GuiGraphics graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x, y, x + width, y + 1, color);
        graphics.fill(x, y + height - 1, x + width, y + height, color);
        graphics.fill(x, y, x + 1, y + height, color);
        graphics.fill(x + width - 1, y, x + width, y + height, color);
    }

    static AnimationFrame frameAt(long elapsedMs) {
        if (elapsedMs < 0 || elapsedMs >= DURATION_MS) {
            return AnimationFrame.INACTIVE;
        }

        float intro = easeOutBack(clamp01(elapsedMs / (float) INTRO_MS));
        float outro = smoothstep(clamp01((elapsedMs - OUTRO_START_MS) / (float) (DURATION_MS - OUTRO_START_MS)));
        float opacity = clamp01(smoothstep(clamp01(elapsedMs / 260f)) * (1f - outro));
        float bannerOffset = -72f * (1f - intro) - 24f * outro;
        float flashOpacity = 0.30f * (1f - clamp01(elapsedMs / 650f));
        float confettiIntensity = smoothstep(clamp01(elapsedMs / 350f)) * (1f - outro);
        return new AnimationFrame(true, opacity, bannerOffset, flashOpacity, confettiIntensity);
    }

    private static float clamp01(float value) {
        return Math.clamp(value, 0f, 1f);
    }

    private static float smoothstep(float value) {
        return value * value * (3f - 2f * value);
    }

    private static float easeOutBack(float value) {
        float shifted = value - 1f;
        return 1f + 2.70158f * shifted * shifted * shifted + 1.70158f * shifted * shifted;
    }

    private static float random01(int seed) {
        int mixed = seed;
        mixed = ((mixed >>> 16) ^ mixed) * 0x45D9F3B;
        mixed = ((mixed >>> 16) ^ mixed) * 0x45D9F3B;
        mixed = (mixed >>> 16) ^ mixed;
        return (mixed & 0xFFFF) / 65_535f;
    }

    private static int argb(int alpha, int rgb) {
        return Math.clamp(alpha, 0, 255) << 24 | rgb & 0xFFFFFF;
    }

    private static long monotonicMillis() {
        return System.nanoTime() / 1_000_000L;
    }

    record AnimationFrame(
            boolean active,
            float opacity,
            float bannerOffset,
            float flashOpacity,
            float confettiIntensity) {
        private static final AnimationFrame INACTIVE = new AnimationFrame(false, 0f, 0f, 0f, 0f);
    }
}
