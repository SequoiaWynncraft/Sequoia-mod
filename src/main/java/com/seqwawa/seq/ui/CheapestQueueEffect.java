package com.seqwawa.seq.ui;

import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.config.Setting;
import com.seqwawa.seq.managers.CheapestQueueTracker;
import com.seqwawa.seq.network.WynncraftServerPolicy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientWorldEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

/** One fading flash after the local player queues with a known-cheapest own HQ. */
public final class CheapestQueueEffect {
    static final long FLASH_MS = 900;
    static final long DURATION_MS = 2_400;
    private static final Setting.BooleanSetting ENABLED =
            new Setting.BooleanSetting("cheapest_success_flash", "guild_wars", true);
    private static final AtomicBoolean RESET_REQUESTED = new AtomicBoolean();
    private static final CheapestQueueTracker TRACKER =
            new CheapestQueueTracker(CheapestQueueEffect::nowMs, ignored -> startedAtMs = nowMs());
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

    public static void onSystemChat(Component message) {
        if (ready() && message != null) TRACKER.onSystemChat(message.getString());
    }

    public static void onAttackClick(String screenTitle, ItemStack stack) {
        if (ready() && stack != null && !stack.isEmpty()) {
            TRACKER.onAttackClick(screenTitle, stack.getHoverName().getString());
        }
    }

    public static void onMenuOpened(int id, Component title, MenuType<?> type) {
        if (!ready()) return;
        List<MenuType<?>> supported = List.of(
                MenuType.GENERIC_9x1, MenuType.GENERIC_9x2, MenuType.GENERIC_9x3,
                MenuType.GENERIC_9x4, MenuType.GENERIC_9x5, MenuType.GENERIC_9x6);
        int rowIndex = supported.indexOf(type);
        TRACKER.onMenuOpened(id, title.getString(), rowIndex < 0 ? 0 : (rowIndex + 1) * 9);
    }

    public static void onMenuContents(int id, List<ItemStack> items) {
        if (!ready()) return;
        int slots = TRACKER.menuSlots(id);
        if (slots == 0 || items.size() < slots) return;
        var entries = new ArrayList<CheapestQueueTracker.TerritoryEntry>();
        for (int slot = 0; slot < slots; slot++) entries.add(entry(items.get(slot)));
        TRACKER.onMenuContents(id, entries);
    }

    public static void onMenuSlot(int id, int slot, ItemStack item) {
        if (ready() && slot >= 0 && slot < TRACKER.menuSlots(id)) TRACKER.onMenuSlot(id, slot, entry(item));
    }

    private static CheapestQueueTracker.TerritoryEntry entry(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        var lore = stack.get(DataComponents.LORE);
        if (lore == null || lore.lines().isEmpty()) return null;
        return new CheapestQueueTracker.TerritoryEntry(
                stack.getHoverName().getString(), lore.lines().getLast().getString());
    }

    public static void onMenuClosed(int id) {
        TRACKER.onMenuClosed(id);
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
        TRACKER.reset();
        startedAtMs = -1;
    }

    private static long nowMs() {
        return System.nanoTime() / 1_000_000L;
    }
}
