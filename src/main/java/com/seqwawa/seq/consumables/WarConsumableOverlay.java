package com.seqwawa.seq.consumables;

import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.config.Setting;
import com.seqwawa.seq.integrations.WynntilsWarConsumableAccess;
import com.seqwawa.seq.network.WynncraftServerPolicy;
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
import net.minecraft.network.chat.Component;
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
 */
public final class WarConsumableOverlay {
    public static final int DEFAULT_USED_RGB = 0xFF1744;
    public static final int DEFAULT_NEXT_RGB = 0x00FF66;
    public static final int DEFAULT_UNUSED_RGB = 0xFF9100;
    private static final int SLOT_SIZE = 16;
    private static final int OPAQUE = 0xFF000000;
    private static final String ALREADY_ACTIVE_MESSAGE = "You already have this consumable active";

    private static final WarConsumableTracker TRACKER = new WarConsumableTracker();
    private static Map<ItemStack, WarConsumableTracker.Status> statusByStack = Map.of();

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
        if (slot.container instanceof Inventory) {
            render(graphics, slot.getItem(), slot.x, slot.y);
        }
    }

    public static void renderHotbarSlot(GuiGraphics graphics, int x, int y, ItemStack stack) {
        render(graphics, stack, x, y);
    }

    public static void onSystemChat(Component message) {
        if (isEnabled() && message.getString().contains(ALREADY_ACTIVE_MESSAGE)) {
            TRACKER.onAlreadyActive(Util.getMillis());
        }
    }

    private static void render(GuiGraphics graphics, ItemStack stack, int x, int y) {
        WarConsumableTracker.Status status = statusByStack.get(stack);
        if (status == null || !isEnabled()) {
            return;
        }
        // Two pixels thick: one on the slot's frame, one on the item's edge, filling the
        // 18px slot cell without reaching into neighbouring slots.
        int color = color(status);
        graphics.renderOutline(x - 1, y - 1, SLOT_SIZE + 2, SLOT_SIZE + 2, color);
        graphics.renderOutline(x, y, SLOT_SIZE, SLOT_SIZE, color);
    }

    private static void onTick(Minecraft client) {
        OptionalLong session = isEnabled() && client.player != null && WynncraftServerPolicy.isCurrentServerAllowed()
                ? WynntilsWarConsumableAccess.currentSession()
                : OptionalLong.empty();
        if (session.isEmpty()) {
            statusByStack = Map.of();
            return;
        }

        List<ItemStack> items = client.player.getInventory().getNonEquipmentItems();
        Map<Integer, WarConsumable> slots = new HashMap<>();
        Map<ItemStack, String> fingerprints = new IdentityHashMap<>();
        for (int slot = 0; slot < items.size(); slot++) {
            ItemStack stack = items.get(slot);
            int inventorySlot = slot;
            WynntilsWarConsumableAccess.read(stack).ifPresent(consumable -> {
                slots.put(inventorySlot, consumable);
                fingerprints.put(stack, consumable.fingerprint());
            });
        }
        TRACKER.observe(session.getAsLong(), slots, Util.getMillis());

        Map<ItemStack, WarConsumableTracker.Status> statuses = new IdentityHashMap<>();
        fingerprints.forEach((stack, fingerprint) -> statuses.put(stack, TRACKER.statusOf(fingerprint)));
        statusByStack = statuses;
    }

    private static InteractionResult onUseItem(Player player, Level level, InteractionHand hand) {
        if (level.isClientSide()
                && hand == InteractionHand.MAIN_HAND
                && player == Minecraft.getInstance().player
                && isEnabled()) {
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
