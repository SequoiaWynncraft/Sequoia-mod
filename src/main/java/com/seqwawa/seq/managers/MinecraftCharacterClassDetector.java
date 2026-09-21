package com.seqwawa.seq.managers;

import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.model.WynnClassType;
import com.seqwawa.seq.utils.PacketTextNormalizer;
import com.seqwawa.seq.utils.WynnClassCache;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.HashedPatchMap;
import net.minecraft.network.HashedStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

/**
 * Resolves the active Wynncraft class from vanilla inventory packets without a
 * hard or reflective Wynntils dependency.
 *
 * <p>The detector is client-thread confined. It retains only the parsed enum
 * and bounded request metadata; item stacks, components, menus, players, and
 * world objects are never retained.
 */
public final class MinecraftCharacterClassDetector {
    static final long INITIAL_QUERY_DELAY_MS = 3_000L;
    static final long QUERY_TIMEOUT_MS = 10_000L;
    static final long RETRY_COOLDOWN_MS = 30_000L;

    private static final int CHARACTER_ITEM_SLOT = 7;
    private static final int INVENTORY_HOTBAR_OFFSET = 36;
    private static final int NO_CONTAINER = -1;
    private static final long NO_PENDING_QUERY = -1L;
    private static final String CHARACTER_INFO_TITLE =
            new String(Character.toChars(0xCFFDC)) + '\uE003';
    private static final String CHARACTER_SELECTION_TITLE =
            new String(Character.toChars(0xCFFD5)) + '\uE01F';
    private static final Pattern CHARACTER_ID_LINE = Pattern.compile("^[a-z0-9]{8}$");
    private static final Pattern CHARACTER_INFO_CLASS_LINE = Pattern.compile("^Class:\\s+(.+)$");
    private static final Pattern CHARACTER_CARD_CLASS_LINE = Pattern.compile("^- Class:\\s+(.+)$");
    private static final MinecraftCharacterClassDetector INSTANCE = new MinecraftCharacterClassDetector();

    private volatile WynnClassType currentClass;
    private String activeCharacterId;
    private String queriedCharacterId;
    private long pendingSinceMs = NO_PENDING_QUERY;
    private long nextQueryAtMs = Util.getMillis() + INITIAL_QUERY_DELAY_MS;
    private int pendingContainerId = NO_CONTAINER;
    private int closingContainerId = NO_CONTAINER;
    private long closeSuppressionUntilMs;

    private MinecraftCharacterClassDetector() {}

    public static MinecraftCharacterClassDetector getInstance() {
        return INSTANCE;
    }

    public WynnClassType currentClass() {
        return currentClass;
    }

    /** Records the explicit class lore on a clicked character-selection card. */
    public void observeCharacterSelection(String screenTitle, ItemStack stack) {
        if (!CHARACTER_SELECTION_TITLE.equals(screenTitle)) {
            return;
        }
        WynnClassType observed = parseClass(stack, CHARACTER_CARD_CLASS_LINE);
        if (observed != null) {
            currentClass = observed;
            activeCharacterId = null;
        }
    }

    /**
     * Starts a silent Character Info lookup when the normal in-world inventory
     * state proves that hotbar slot 7 is Wynncraft's character compass.
     */
    public void tick() {
        long now = Util.getMillis();
        if (closingContainerId != NO_CONTAINER && now > closeSuppressionUntilMs) {
            closingContainerId = NO_CONTAINER;
            closeSuppressionUntilMs = 0L;
        }
        if (queryPending() && now - pendingSinceMs >= QUERY_TIMEOUT_MS) {
            int timedOutContainerId = pendingContainerId;
            closeTimedOutContainer();
            finishQuery(now);
            if (timedOutContainerId != NO_CONTAINER) {
                closingContainerId = timedOutContainerId;
                closeSuppressionUntilMs = now + QUERY_TIMEOUT_MS;
            }
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return;
        }
        ClientPacketListener connection = minecraft.getConnection();
        if (minecraft.player == null
                || minecraft.level == null
                || minecraft.gameMode == null
                || connection == null
                || !connection.isAcceptingMessages()
                || minecraft.screen != null
                || minecraft.player.containerMenu != minecraft.player.inventoryMenu
                || minecraft.player.isUsingItem()) {
            return;
        }

        Inventory inventory = minecraft.player.getInventory();
        String observedCharacterId = characterId(inventory.getItem(CHARACTER_ITEM_SLOT));
        if (observedCharacterId == null) {
            return;
        }
        observeCharacterId(observedCharacterId, now);
        if (currentClass != null
                || queryPending()
                || now < nextQueryAtMs) {
            return;
        }

        beginQuery(now);
        try {
            sendCharacterInfoClick(minecraft, connection);
        } catch (RuntimeException exception) {
            SeqClient.LOGGER.debug("Unable to request vanilla Wynncraft character info", exception);
        }
    }

    /** Returns true when the exact pending Character Info screen was suppressed. */
    public boolean onCharacterInfoOpened(int containerId, Component title) {
        if (!queryPending() || containerId <= 0 || !isCharacterInfoTitle(title)) {
            return false;
        }
        pendingContainerId = containerId;
        return true;
    }

    /**
     * Consumes the response for the suppressed Character Info container and
     * closes it server-side without installing a client menu.
     */
    public boolean onCharacterInfoContents(int containerId, List<ItemStack> items) {
        if (!matchesPendingContainer(containerId)) {
            return false;
        }
        if (Objects.equals(activeCharacterId, queriedCharacterId)
                && items != null
                && items.size() > CHARACTER_ITEM_SLOT) {
            WynnClassType observed = parseClass(items.get(CHARACTER_ITEM_SLOT), CHARACTER_INFO_CLASS_LINE);
            if (observed != null) {
                currentClass = observed;
            }
        }

        sendContainerClose(containerId);
        long now = Util.getMillis();
        finishQuery(now);
        closingContainerId = containerId;
        closeSuppressionUntilMs = now + QUERY_TIMEOUT_MS;
        return true;
    }

    /** Consumes the close acknowledgement for the menu that was never shown. */
    public boolean onCharacterInfoClosed(int containerId) {
        if (matchesPendingContainer(containerId)) {
            finishQuery(Util.getMillis());
            return true;
        }
        if (closingContainerId == containerId && Util.getMillis() <= closeSuppressionUntilMs) {
            closingContainerId = NO_CONTAINER;
            closeSuppressionUntilMs = 0L;
            return true;
        }
        return false;
    }

    /** Clears class and query state at a world or connection boundary. */
    public void reset() {
        currentClass = null;
        activeCharacterId = null;
        queriedCharacterId = null;
        pendingSinceMs = NO_PENDING_QUERY;
        nextQueryAtMs = Util.getMillis() + INITIAL_QUERY_DELAY_MS;
        pendingContainerId = NO_CONTAINER;
        closingContainerId = NO_CONTAINER;
        closeSuppressionUntilMs = 0L;
    }

    static WynnClassType parseClass(ItemStack stack) {
        WynnClassType infoClass = parseClass(stack, CHARACTER_INFO_CLASS_LINE);
        return infoClass != null ? infoClass : parseClass(stack, CHARACTER_CARD_CLASS_LINE);
    }

    private static WynnClassType parseClass(ItemStack stack, Pattern classLinePattern) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        ItemLore lore = stack.get(DataComponents.LORE);
        if (lore == null) {
            return null;
        }
        for (Component line : lore.lines()) {
            String normalized = PacketTextNormalizer.normalizeForParsing(line.getString());
            Matcher matcher = classLinePattern.matcher(normalized);
            if (!matcher.matches()) {
                continue;
            }
            WynnClassType parsed = WynnClassCache.parseClassType(matcher.group(1));
            if (parsed != null) {
                return parsed;
            }
        }
        return null;
    }

    static String characterId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        ItemLore lore = stack.get(DataComponents.LORE);
        if (lore == null) {
            return null;
        }
        for (Component line : lore.lines()) {
            String normalized = PacketTextNormalizer.normalizeForParsing(line.getString());
            if (CHARACTER_ID_LINE.matcher(normalized).matches()) {
                return normalized;
            }
        }
        return null;
    }

    static boolean isCharacterInfoTitle(Component title) {
        return title != null && CHARACTER_INFO_TITLE.equals(title.getString());
    }

    private boolean queryPending() {
        return pendingSinceMs != NO_PENDING_QUERY;
    }

    private boolean matchesPendingContainer(int containerId) {
        return queryPending() && pendingContainerId != NO_CONTAINER && pendingContainerId == containerId;
    }

    void beginQuery(long now) {
        pendingSinceMs = now;
        pendingContainerId = NO_CONTAINER;
        queriedCharacterId = activeCharacterId;
        nextQueryAtMs = now + RETRY_COOLDOWN_MS;
    }

    /**
     * Mirrors Wynncraft's established inventory-click protocol for opening the
     * Character Info menu. The packet is sent directly, so the local compass,
     * carried item, and selected hotbar slot never change.
     */
    private static void sendCharacterInfoClick(Minecraft minecraft, ClientPacketListener connection) {
        int menuSlot = INVENTORY_HOTBAR_OFFSET + CHARACTER_ITEM_SLOT;
        List<ItemStack> menuItems = minecraft.player.containerMenu.getItems();
        if (minecraft.player.containerMenu.containerId != 0 || menuItems.size() <= menuSlot) {
            throw new IllegalStateException("Character compass inventory slot is unavailable");
        }
        connection.send(createCharacterInfoClickPacket(
                menuItems, connection.decoratedHashOpsGenenerator()));
    }

    static ServerboundContainerClickPacket createCharacterInfoClickPacket(
            List<ItemStack> menuItems, HashedPatchMap.HashGenerator hashGenerator) {
        int menuSlot = INVENTORY_HOTBAR_OFFSET + CHARACTER_ITEM_SLOT;
        if (menuItems == null || menuItems.size() <= menuSlot || hashGenerator == null) {
            throw new IllegalArgumentException("Character compass inventory contents are incomplete");
        }
        Int2ObjectMap<HashedStack> changedSlots = new Int2ObjectOpenHashMap<>();
        changedSlots.put(menuSlot, HashedStack.create(new ItemStack(Items.BARRIER), hashGenerator));
        HashedStack carriedItem = HashedStack.create(menuItems.get(menuSlot), hashGenerator);
        return new ServerboundContainerClickPacket(
                0,
                0,
                (short) menuSlot,
                (byte) 0,
                ClickType.PICKUP,
                changedSlots,
                carriedItem);
    }

    void observeCharacterId(String observedCharacterId, long now) {
        if (observedCharacterId == null || !CHARACTER_ID_LINE.matcher(observedCharacterId).matches()) {
            return;
        }
        if (activeCharacterId == null) {
            activeCharacterId = observedCharacterId;
            return;
        }
        if (activeCharacterId.equals(observedCharacterId)) {
            return;
        }
        activeCharacterId = observedCharacterId;
        currentClass = null;
        nextQueryAtMs = now + INITIAL_QUERY_DELAY_MS;
    }

    private void closeTimedOutContainer() {
        if (pendingContainerId == NO_CONTAINER) {
            return;
        }
        sendContainerClose(pendingContainerId);
    }

    private void sendContainerClose(int containerId) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientPacketListener connection = minecraft == null ? null : minecraft.getConnection();
        if (connection != null) {
            try {
                connection.send(new ServerboundContainerClosePacket(containerId));
            } catch (RuntimeException exception) {
                SeqClient.LOGGER.debug("Unable to close the silent Wynncraft character-info query", exception);
            }
        }
    }

    private void finishQuery(long now) {
        boolean characterChanged = queriedCharacterId != null
                && !Objects.equals(activeCharacterId, queriedCharacterId);
        pendingSinceMs = NO_PENDING_QUERY;
        pendingContainerId = NO_CONTAINER;
        queriedCharacterId = null;
        nextQueryAtMs = now + (characterChanged ? INITIAL_QUERY_DELAY_MS : RETRY_COOLDOWN_MS);
    }
}
