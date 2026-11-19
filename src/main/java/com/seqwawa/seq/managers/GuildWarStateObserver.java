package com.seqwawa.seq.managers;

import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.model.war.GuildWarObservation;
import com.seqwawa.seq.model.war.GuildWarState;
import com.seqwawa.seq.network.ApiClient;
import com.seqwawa.seq.network.WynncraftServerPolicy;
import com.seqwawa.seq.ui.CheapestQueueEffect;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientWorldEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

/** Reads actual server packets and shares guild observations even when visual effects are off. */
public final class GuildWarStateObserver {
    private static final AtomicBoolean RESET_REQUESTED = new AtomicBoolean();
    private static final GuildWarStateSync SYNC = new GuildWarStateSync(new GuildWarStateSync.Gateway() {
        @Override public CompletableFuture<GuildWarState> fetch() { return ApiClient.getInstance().getGuildWarState(); }
        @Override public CompletableFuture<GuildWarState> observe(GuildWarObservation report) { return ApiClient.getInstance().observeGuildWarState(report); }
    }, () -> System.nanoTime() / 1_000_000L,
        () -> WynncraftServerPolicy.isCurrentServerAllowed() && SeqClient.getWarPlannerManager() != null
                && SeqClient.getWarPlannerManager().isAuthorized(),
        task -> Minecraft.getInstance().execute(task), CheapestQueueEffect::trigger);
    private static UUID account;
    private GuildWarStateObserver() {}

    public static void initialize() {
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> RESET_REQUESTED.set(true));
        ClientWorldEvents.AFTER_CLIENT_WORLD_CHANGE.register((client, world) -> SYNC.reset());
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            UUID current = client.getUser().getProfileId();
            if (!java.util.Objects.equals(current, account)) { account = current; SYNC.reset(); }
            if (ready()) SYNC.tick();
        });
    }

    private static boolean ready() {
        if (RESET_REQUESTED.getAndSet(false) || !WynncraftServerPolicy.isCurrentServerAllowed()) {
            SYNC.reset();
            return false;
        }
        return true;
    }

    public static void onSystemChat(Component message) {
        if (ready() && message != null) SYNC.tracker().onSystemChat(message.getString());
    }

    public static void onAttackClick(String screenTitle, ItemStack stack) {
        if (ready() && stack != null && !stack.isEmpty()) {
            SYNC.tracker().onAttackClick(screenTitle, stack.getHoverName().getString());
        }
    }

    public static void onMenuOpened(int id, Component title, MenuType<?> type) {
        if (!ready()) return;
        List<MenuType<?>> supported = List.of(
                MenuType.GENERIC_9x1, MenuType.GENERIC_9x2, MenuType.GENERIC_9x3,
                MenuType.GENERIC_9x4, MenuType.GENERIC_9x5, MenuType.GENERIC_9x6);
        int rowIndex = supported.indexOf(type);
        SYNC.tracker().onMenuOpened(id, title.getString(), rowIndex < 0 ? 0 : (rowIndex + 1) * 9);
    }

    public static void onMenuContents(int id, List<ItemStack> items) {
        if (!ready()) return;
        int slots = SYNC.tracker().menuSlots(id);
        if (slots == 0 || items.size() < slots) return;
        var entries = new ArrayList<CheapestQueueTracker.TerritoryEntry>();
        for (int slot = 0; slot < slots; slot++) entries.add(entry(items.get(slot)));
        SYNC.tracker().onMenuContents(id, entries);
    }

    public static void onMenuSlot(int id, int slot, ItemStack item) {
        if (ready() && slot >= 0 && slot < SYNC.tracker().menuSlots(id)) SYNC.tracker().onMenuSlot(id, slot, entry(item));
    }

    private static CheapestQueueTracker.TerritoryEntry entry(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        var lore = stack.get(DataComponents.LORE);
        if (lore == null || lore.lines().isEmpty()) return null;
        return new CheapestQueueTracker.TerritoryEntry(
                stack.getHoverName().getString(), lore.lines().getLast().getString());
    }

    public static void onMenuClosed(int id) {
        SYNC.tracker().onMenuClosed(id);
    }

}
