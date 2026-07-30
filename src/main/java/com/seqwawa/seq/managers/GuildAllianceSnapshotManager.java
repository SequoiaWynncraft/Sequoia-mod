package com.seqwawa.seq.managers;

import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.network.ConnectionManager;
import com.seqwawa.seq.utils.PacketTextNormalizer;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.world.item.ItemStack;

public final class GuildAllianceSnapshotManager {
    private static final Pattern DIPLOMACY_TITLE_PATTERN = Pattern.compile("^[A-Za-z][A-Za-z ]*: Diplomacy$");
    private static final Pattern ALLIED_GUILD_ITEM_PATTERN =
            Pattern.compile("^(?<name>.+?) \\[[A-Za-z]{3,4}]$");
    private static final List<Integer> ALLIANCE_SLOTS = List.of(2, 3, 4, 5, 6, 7, 8);
    private static final GuildAllianceSnapshotManager INSTANCE = new GuildAllianceSnapshotManager(
            GuildAllianceSnapshotManager::currentAuthenticatedSession,
            guildNames -> ConnectionManager.getInstance().sendGuildAllianceSnapshot(guildNames));

    private final Supplier<Object> authenticatedSessionSupplier;
    private final SnapshotPublisher snapshotPublisher;

    private PendingMenu pendingMenu;

    public static GuildAllianceSnapshotManager getInstance() {
        return INSTANCE;
    }

    GuildAllianceSnapshotManager(
            Supplier<Object> authenticatedSessionSupplier, SnapshotPublisher snapshotPublisher) {
        this.authenticatedSessionSupplier =
                Objects.requireNonNull(authenticatedSessionSupplier, "authenticatedSessionSupplier");
        this.snapshotPublisher = Objects.requireNonNull(snapshotPublisher, "snapshotPublisher");
    }

    public void reset() {
        pendingMenu = null;
    }

    public void onMenuClosed(int containerId) {
        if (pendingMenu != null && pendingMenu.containerId == containerId) {
            pendingMenu = null;
        }
    }

    public void onMenuOpened(int containerId, String title) {
        pendingMenu = null;
        if (!isDiplomacyMenuTitle(title)) {
            return;
        }

        SeqClient.LOGGER.info(
                "[GuildAllianceSnapshot] Diplomacy menu detected containerId={}; waiting for contents",
                containerId);
        Object authenticatedSession = authenticatedSessionSupplier.get();
        if (authenticatedSession == null) {
            SeqClient.LOGGER.info(
                    "[GuildAllianceSnapshot] Diplomacy menu detected but snapshot skipped: WebSocket is not authenticated");
            return;
        }
        pendingMenu = new PendingMenu(containerId, authenticatedSession);
    }

    public void onContainerContents(int containerId, List<ItemStack> items) {
        PendingMenu menu = pendingMenu;
        if (menu == null || menu.containerId != containerId) {
            return;
        }

        if (!Objects.equals(authenticatedSessionSupplier.get(), menu.authenticatedSession)) {
            pendingMenu = null;
            return;
        }

        Optional<List<String>> parsedGuildNames = parseAllianceNames(items);
        if (parsedGuildNames.isEmpty()) {
            SeqClient.LOGGER.warn(
                    "[GuildAllianceSnapshot] Diplomacy contents incomplete or ambiguous containerId={} itemCount={}",
                    containerId,
                    items == null ? -1 : items.size());
            return;
        }

        pendingMenu = null;
        List<String> guildNames = parsedGuildNames.get();
        boolean sent;
        try {
            sent = snapshotPublisher.publish(guildNames);
        } catch (RuntimeException exception) {
            SeqClient.LOGGER.warn("[GuildAllianceSnapshot] Failed to send diplomacy snapshot", exception);
            return;
        }
        if (sent) {
            SeqClient.LOGGER.info(
                    "[GuildAllianceSnapshot] Sent authoritative diplomacy snapshot allies={}",
                    guildNames.size());
        } else {
            SeqClient.LOGGER.warn(
                    "[GuildAllianceSnapshot] Dropped diplomacy snapshot because the authenticated session became unavailable");
        }
    }

    static boolean isDiplomacyMenuTitle(String title) {
        return title != null && DIPLOMACY_TITLE_PATTERN.matcher(title.trim()).matches();
    }

    static Optional<List<String>> parseAllianceNames(List<ItemStack> items) {
        if (items == null || items.size() <= ALLIANCE_SLOTS.getLast()) {
            return Optional.empty();
        }

        Map<String, String> uniqueGuildNames = new LinkedHashMap<>();
        for (int slot : ALLIANCE_SLOTS) {
            ItemStack stack = items.get(slot);
            if (stack == null) {
                return Optional.empty();
            }
            if (stack.isEmpty()) {
                continue;
            }

            String itemName = PacketTextNormalizer.normalizeForParsing(stack.getHoverName().getString());
            Matcher matcher = ALLIED_GUILD_ITEM_PATTERN.matcher(itemName);
            if (!matcher.matches()) {
                SeqClient.LOGGER.warn(
                        "[GuildAllianceSnapshot] Unrecognized diplomacy entry slot={} item='{}'",
                        slot,
                        itemName);
                return Optional.empty();
            }

            String guildName = matcher.group("name").trim();
            if (guildName.isEmpty() || guildName.length() > 64 || guildName.contains(":")) {
                return Optional.empty();
            }
            uniqueGuildNames.putIfAbsent(guildName.toLowerCase(Locale.ROOT), guildName);
            if (uniqueGuildNames.size() > 16) {
                return Optional.empty();
            }
        }

        return Optional.of(List.copyOf(uniqueGuildNames.values()));
    }

    private static Object currentAuthenticatedSession() {
        if (!ConnectionManager.isConnected()) {
            return null;
        }
        ConnectionManager connection = ConnectionManager.getInstance();
        return new AuthenticatedSession(connection, connection.getConnectedSince());
    }

    @FunctionalInterface
    interface SnapshotPublisher {
        boolean publish(List<String> guildNames);
    }

    private record AuthenticatedSession(ConnectionManager connection, Instant connectedSince) {}

    private static final class PendingMenu {
        private final int containerId;
        private final Object authenticatedSession;

        private PendingMenu(int containerId, Object authenticatedSession) {
            this.containerId = containerId;
            this.authenticatedSession = authenticatedSession;
        }
    }
}
