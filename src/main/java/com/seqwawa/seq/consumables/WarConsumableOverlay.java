package com.seqwawa.seq.consumables;

import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.config.Setting;
import com.seqwawa.seq.integrations.WynntilsWarConsumableAccess;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Outlines the war consumables in the player's inventory and hotbar: red while their effect is
 * active, green for the one to use next, orange for the others.
 *
 * <p>This runs every tick and for every slot drawn, so both paths stay minimal: a tick compares
 * the inventory's stacks by reference and only reads them again when one was replaced, and a
 * slot costs one map lookup plus, for a consumable, a single sprite.
 */
public final class WarConsumableOverlay {
    public static final int DEFAULT_USED_RGB = 0xFF1744;
    public static final int DEFAULT_NEXT_RGB = 0x00FF66;
    public static final int DEFAULT_UNUSED_RGB = 0xFF9100;
    private static final Identifier OUTLINE_SPRITE = Identifier.fromNamespaceAndPath("seq", "consumable_outline");
    private static final int OUTLINE_SIZE = 18;
    private static final int OPAQUE = 0xFF000000;
    /**
     * Wynntils can finish reading an item after it lands in the inventory without replacing the
     * stack, and the colors can change in the settings, so the inventory is read this often anyway.
     */
    private static final int FULL_REFRESH_TICKS = 20;
    private static final String ALREADY_ACTIVE_MESSAGE = "You already have this consumable active";

    private static final WarConsumableTracker TRACKER = new WarConsumableTracker();
    private static final ItemStack[] observedStacks = new ItemStack[Inventory.INVENTORY_SIZE];
    private static Map<ItemStack, Integer> outlineColors = Map.of();
    private static long observedSession;
    private static long nextExpiryMillis = Long.MAX_VALUE;
    private static boolean refreshRequested = true;
    private static int ticksSinceRefresh;

    private WarConsumableOverlay() {}

    public static void initialize() {
        // Consumables are read through Wynntils' item parsing.
        if (!FabricLoader.getInstance().isModLoaded("wynntils")) {
            SeqClient.LOGGER.info("[WarConsumables] Wynntils not found; consumable overlay unavailable.");
            return;
        }
        ClientTickEvents.END_CLIENT_TICK.register(WarConsumableOverlay::onTick);
        UseItemCallback.EVENT.register(WarConsumableOverlay::onUseItem);
    }

    public static void renderSlot(GuiGraphics graphics, Slot slot) {
        if (!outlineColors.isEmpty() && slot.container instanceof Inventory) {
            render(graphics, slot.getItem(), slot.x, slot.y);
        }
    }

    public static void renderHotbarSlot(GuiGraphics graphics, int x, int y, ItemStack stack) {
        if (!outlineColors.isEmpty()) {
            render(graphics, stack, x, y);
        }
    }

    public static void onSystemChat(Component message) {
        if (!outlineColors.isEmpty() && message.getString().contains(ALREADY_ACTIVE_MESSAGE)) {
            TRACKER.onAlreadyActive(Util.getMillis());
            refreshRequested = true;
        }
    }

    private static void render(GuiGraphics graphics, ItemStack stack, int x, int y) {
        Integer color = outlineColors.get(stack);
        if (color != null) {
            // One sprite rather than a fill per edge: the GUI places every element by searching
            // everything already drawn on screen for overlaps.
            graphics.blitSprite(
                    RenderPipelines.GUI_TEXTURED, OUTLINE_SPRITE, x - 1, y - 1, OUTLINE_SIZE, OUTLINE_SIZE, color);
        }
    }

    private static void onTick(Minecraft client) {
        OptionalLong session = isEnabled() && client.player != null
                ? WynntilsWarConsumableAccess.currentSession()
                : OptionalLong.empty();
        if (session.isEmpty()) {
            outlineColors = Map.of();
            refreshRequested = true;
            return;
        }

        long nowMillis = Util.getMillis();
        List<ItemStack> items = client.player.getInventory().getNonEquipmentItems();
        boolean changed = refreshRequested
                || session.getAsLong() != observedSession
                || nowMillis >= nextExpiryMillis
                || ++ticksSinceRefresh >= FULL_REFRESH_TICKS;
        for (int slot = 0; slot < observedStacks.length; slot++) {
            ItemStack stack = items.get(slot);
            if (stack != observedStacks[slot]) {
                observedStacks[slot] = stack;
                changed = true;
            }
        }
        if (changed) {
            refresh(session.getAsLong(), items, nowMillis);
        }
    }

    private static void refresh(long session, List<ItemStack> items, long nowMillis) {
        refreshRequested = false;
        ticksSinceRefresh = 0;
        observedSession = session;

        Map<Integer, WarConsumable> slots = new HashMap<>();
        Map<ItemStack, String> fingerprints = new IdentityHashMap<>();
        for (int slot = 0; slot < observedStacks.length; slot++) {
            ItemStack stack = items.get(slot);
            int inventorySlot = slot;
            WynntilsWarConsumableAccess.read(stack).ifPresent(consumable -> {
                slots.put(inventorySlot, consumable);
                fingerprints.put(stack, consumable.fingerprint());
            });
        }
        TRACKER.observe(session, slots, nowMillis);
        nextExpiryMillis = TRACKER.nextExpiryMillis(nowMillis);

        Map<ItemStack, Integer> colors = new IdentityHashMap<>();
        fingerprints.forEach((stack, fingerprint) -> colors.put(stack, color(TRACKER.statusOf(fingerprint))));
        outlineColors = colors.isEmpty() ? Map.of() : colors;
    }

    private static InteractionResult onUseItem(Player player, Level level, InteractionHand hand) {
        if (level.isClientSide()
                && hand == InteractionHand.MAIN_HAND
                && player == Minecraft.getInstance().player
                && !outlineColors.isEmpty()) {
            Inventory inventory = player.getInventory();
            int slot = inventory.getSelectedSlot();
            WynntilsWarConsumableAccess.read(inventory.getItem(slot))
                    .ifPresent(consumable -> TRACKER.onUseAttempt(slot, consumable, Util.getMillis()));
        }
        return InteractionResult.PASS;
    }

    private static boolean isEnabled() {
        Setting.BooleanSetting setting = SeqClient.getWarConsumableOverlaySetting();
        return setting != null && setting.getValue();
    }

    private static int color(WarConsumableTracker.Status status) {
        int rgb = switch (status) {
            case ACTIVE -> rgb(SeqClient.getWarConsumableUsedColorSetting(), DEFAULT_USED_RGB);
            case NEXT -> rgb(SeqClient.getWarConsumableNextColorSetting(), DEFAULT_NEXT_RGB);
            case UNUSED -> rgb(SeqClient.getWarConsumableUnusedColorSetting(), DEFAULT_UNUSED_RGB);
        };
        return OPAQUE | rgb;
    }

    private static int rgb(Setting.ColorSetting setting, int fallback) {
        return setting == null ? fallback : setting.getValue();
    }
}
