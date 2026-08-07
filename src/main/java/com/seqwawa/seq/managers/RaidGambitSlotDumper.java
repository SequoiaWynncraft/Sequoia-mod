package com.seqwawa.seq.managers;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

/** Periodically prints the raid party slots to local chat for protocol investigation. */
public final class RaidGambitSlotDumper {
    static final long DUMP_INTERVAL_MS = 5_000L;
    static final List<Integer> TARGET_SLOTS = List.of(18, 19, 20, 21);
    private static final String PREFIX = "[Seq Gambit Debug] ";

    private static AbstractContainerMenu activeMenu;
    private static long lastDumpAtMs = Long.MIN_VALUE;

    private RaidGambitSlotDumper() {}

    public static void tick(AbstractContainerMenu menu, Component screenTitle) {
        if (menu == null || !RaidStartScreenDetector.isRaidStartScreen(screenTitle)) {
            reset();
            return;
        }

        long nowMs = System.currentTimeMillis();
        boolean newlyOpened = activeMenu != menu;
        if (!newlyOpened && !dumpIntervalElapsed(lastDumpAtMs, nowMs)) {
            return;
        }

        activeMenu = menu;
        lastDumpAtMs = nowMs;
        dump(menu);
    }

    public static void reset() {
        activeMenu = null;
        lastDumpAtMs = Long.MIN_VALUE;
    }

    static boolean dumpIntervalElapsed(long previousMs, long nowMs) {
        return previousMs == Long.MIN_VALUE || nowMs < previousMs || nowMs - previousMs >= DUMP_INTERVAL_MS;
    }

    private static void dump(AbstractContainerMenu menu) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            return;
        }

        client.player.displayClientMessage(
                Component.literal(PREFIX + "raid slots 18-21 (repeats every 5s)"), false);
        for (int slotIndex : TARGET_SLOTS) {
            if (slotIndex >= menu.slots.size()) {
                sendLiteral(client, "slot " + slotIndex + ": <missing; menu has " + menu.slots.size() + " slots>");
                continue;
            }
            dumpStack(client, slotIndex, menu.slots.get(slotIndex).getItem());
        }
    }

    private static void dumpStack(Minecraft client, int slotIndex, ItemStack stack) {
        if (stack.isEmpty()) {
            sendLiteral(client, "slot " + slotIndex + ": <empty>");
            return;
        }

        String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        MutableComponent summary = Component.literal(PREFIX + "slot " + slotIndex + ": " + itemId + " x"
                        + stack.getCount() + " name=")
                .append(stack.getHoverName());
        client.player.displayClientMessage(summary, false);

        ItemLore lore = stack.get(DataComponents.LORE);
        if (lore == null || lore.lines().isEmpty()) {
            sendLiteral(client, "  lore: <none>");
        } else {
            for (int lineIndex = 0; lineIndex < lore.lines().size(); lineIndex++) {
                MutableComponent loreLine = Component.literal(PREFIX + "  lore[" + lineIndex + "]: ")
                        .append(lore.lines().get(lineIndex));
                client.player.displayClientMessage(loreLine, false);
            }
        }

        sendLiteral(client, "  components: " + stack.getComponentsPatch());
    }

    private static void sendLiteral(Minecraft client, String message) {
        client.player.displayClientMessage(Component.literal(PREFIX + message), false);
    }
}
