package com.seqwawa.seq.ui;

import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.config.Setting;
import com.seqwawa.seq.network.WynncraftServerPolicy;
import java.util.concurrent.atomic.AtomicBoolean;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientWorldEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/** One fading flash after the local player queues with a known-cheapest own HQ. */
public final class CheapestQueueEffect {
    static final long FLASH_MS = 900;
    static final long DURATION_MS = 2_400;
    private static final Setting.BooleanSetting ENABLED =
            new Setting.BooleanSetting("cheapest_success_flash", "guild_wars", true);
    private static final AtomicBoolean RESET_REQUESTED = new AtomicBoolean();
    private static long startedAtMs = -1;

    static {
        ENABLED.setPresentation(
                "Cheapest success flash",
                "Flash after you queue a war while your HQ is known to use cheapest. Respects Screen Effects and Easter eggs.",
                "War queue messages");
    }

    private CheapestQueueEffect() {}

    public static Setting.BooleanSetting setting() {
        return ENABLED;
    }

    public static void initialize() {
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> RESET_REQUESTED.set(true));
        ClientWorldEvents.AFTER_CLIENT_WORLD_CHANGE.register((client, world) -> reset());
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (RESET_REQUESTED.getAndSet(false) || !enabled() || client.player == null) reset();
        });
    }

    private static boolean enabled() {
        return ENABLED.getValue()
                && SeqClient.getEasterEggsSetting() != null
                && SeqClient.getEasterEggsSetting().getValue()
                && WynncraftServerPolicy.currentScope() == WynncraftServerPolicy.Scope.MAIN;
    }

    private static boolean ready() {
        if (RESET_REQUESTED.getAndSet(false) || !enabled()) {
            reset();
            return false;
        }
        return true;
    }

    public static void trigger(String territory) {
        if (ready() && Minecraft.getInstance().player != null) startedAtMs = nowMs();
    }

    public static boolean isActive() {
        if (!ready() || startedAtMs < 0) return false;
        if (nowMs() - startedAtMs >= DURATION_MS) {
            startedAtMs = -1;
            return false;
        }
        return true;
    }

    /** Rendered in the final GUI pass so inventory screens cannot cover the effect. */
    public static void render(GuiGraphics graphics) {
        if (!isActive()) return;
        Minecraft client = Minecraft.getInstance();
        float intensity = client.options.screenEffectScale().get().floatValue();
        if (intensity <= 0 || client.player == null) return;
        long elapsed = nowMs() - startedAtMs;
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        int flashAlpha = Math.round(255 * flashOpacity(elapsed, intensity));
        if (flashAlpha > 0) graphics.fill(0, 0, width, height, flashAlpha << 24 | 0xFFFFFF);

        int alpha = Math.round(255 * textOpacity(elapsed) * intensity);
        // Minecraft treats near-zero text alpha as opaque, so skip those frames.
        if (alpha < 4) return;
        String title = "CHEAPEST SUCCESS";
        float scale = Math.min(2.8f, Math.max(0.5f, (width - 16f) / client.font.width(title)));
        graphics.pose().pushMatrix();
        try {
            graphics.pose().translate(width / 2f, height / 2f - 16);
            graphics.pose().scale(scale, scale);
            graphics.drawCenteredString(client.font, title, 0, 0, alpha << 24 | 0xE73131);
        } finally {
            graphics.pose().popMatrix();
        }
        graphics.drawCenteredString(
                client.font, "Your HQ is set to cheapest. Use fastest!", width / 2, height / 2 + 18,
                alpha << 24 | 0xFFFFFF);
    }

    static float flashOpacity(long elapsed, float intensity) {
        if (elapsed < 0 || elapsed >= FLASH_MS) return 0;
        return 0.82f * Math.clamp(intensity, 0, 1) * (1 - elapsed / (float) FLASH_MS);
    }

    static float textOpacity(long elapsed) {
        if (elapsed < 0 || elapsed >= DURATION_MS) return 0;
        return Math.min(1f, (DURATION_MS - elapsed) / 600f);
    }

    private static void reset() {
        startedAtMs = -1;
    }

    private static long nowMs() {
        return System.nanoTime() / 1_000_000L;
    }
}
