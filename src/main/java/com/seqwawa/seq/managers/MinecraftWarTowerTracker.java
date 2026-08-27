package com.seqwawa.seq.managers;

import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.model.WarTowerUpdate;
import com.seqwawa.seq.utils.PacketTextNormalizer;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBossEventPacket;
import net.minecraft.world.BossEvent;

/**
 * Reads Wynncraft tower metrics directly from vanilla boss-event packets.
 *
 * <p>The tracker is client-thread confined. It stores only normalized scalar
 * values, caps active bars, and never retains packet, component, player, level,
 * or other Minecraft world objects.
 */
public final class MinecraftWarTowerTracker {
    static final int MAX_TRACKED_BARS = 32;
    private static final double LONG_UPPER_BOUND_EXCLUSIVE = 0x1.0p63;

    /**
     * Plain-text equivalent of Wynncraft's formatted tower bar. Icon glyphs are
     * deliberately treated as arbitrary non-digits because resource packs may
     * render them with either Unicode or private-use code points.
     */
    private static final Pattern TOWER_TITLE = Pattern.compile(
            "^\\[(?<guild>[^\\]]+)]\\s+(?<territory>.+?)\\s+Tower\\s+-\\s+"
                    + "[^\\d]*(?<health>\\d+)\\s+"
                    + "\\((?<defense>\\d+(?:\\.\\d+)?)%\\)\\s+-\\s+"
                    + "[^\\d]*(?<damageLow>\\d+)\\s*-\\s*(?<damageHigh>\\d+)\\s+"
                    + "\\((?<attackSpeed>\\d+(?:\\.\\d+)?)x\\)$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private static final MinecraftWarTowerTracker INSTANCE = new MinecraftWarTowerTracker();

    private final LinkedHashMap<UUID, TrackedBossBar> bars =
            new LinkedHashMap<>(MAX_TRACKED_BARS, 0.75f, true);
    private final ClientboundBossEventPacket.Handler packetHandler = new PacketHandler();
    private long updateSequence;

    public static MinecraftWarTowerTracker getInstance() {
        return INSTANCE;
    }

    /** Called from the raw vanilla packet hook after client-thread confinement. */
    public void onBossEvent(ClientboundBossEventPacket packet) {
        if (packet == null) {
            return;
        }
        try {
            packet.dispatch(packetHandler);
        } catch (RuntimeException exception) {
            SeqClient.LOGGER.debug("Ignoring malformed boss-bar update", exception);
        }
    }

    /** Returns the newest active tower bar for the exact requested territory. */
    public WarTowerUpdate snapshot(String expectedTerritory) {
        String expected = trimToNull(expectedTerritory);
        if (expected == null) {
            return null;
        }

        TrackedBossBar newest = null;
        for (TrackedBossBar bar : bars.values()) {
            if (bar.tower == null || !bar.tower.territory().equalsIgnoreCase(expected)) {
                continue;
            }
            if (newest == null || bar.lastUpdatedOrder > newest.lastUpdatedOrder) {
                newest = bar;
            }
        }
        if (newest == null || !Float.isFinite(newest.progress)) {
            return null;
        }

        return toUpdate(newest);
    }

    /**
     * Returns the newest parsed tower together with its stable vanilla boss-bar
     * identity. This is the authoritative war observation when Wynntils is not
     * installed.
     */
    public TowerSnapshot latestSnapshot() {
        UUID newestId = null;
        TrackedBossBar newest = null;
        for (Map.Entry<UUID, TrackedBossBar> entry : bars.entrySet()) {
            TrackedBossBar bar = entry.getValue();
            if (bar.tower == null || !Float.isFinite(bar.progress)) {
                continue;
            }
            if (newest == null || bar.lastUpdatedOrder > newest.lastUpdatedOrder) {
                newestId = entry.getKey();
                newest = bar;
            }
        }
        return newest == null ? null : new TowerSnapshot(newestId, toUpdate(newest));
    }

    /** Clears all packet-derived state at a connection/world boundary. */
    public void reset() {
        bars.clear();
        updateSequence = 0L;
    }

    void add(UUID id, Component name, float progress) {
        if (id == null) {
            return;
        }
        TrackedBossBar bar = getOrCreate(id);
        bar.resetLifecycle();
        bar.progress = progress;
        applyName(bar, name);
    }

    void remove(UUID id) {
        if (id != null) {
            bars.remove(id);
        }
    }

    void updateProgress(UUID id, float progress) {
        if (id == null) {
            return;
        }
        TrackedBossBar bar = getOrCreate(id);
        bar.progress = progress;
        markUpdated(bar);
    }

    void updateName(UUID id, Component name) {
        if (id == null) {
            return;
        }
        applyName(getOrCreate(id), name);
    }

    int trackedBarCount() {
        return bars.size();
    }

    static TowerTitle parseTowerTitle(String rawTitle) {
        String normalized = PacketTextNormalizer.normalizeForParsing(rawTitle);
        Matcher matcher = TOWER_TITLE.matcher(normalized);
        if (!matcher.matches()) {
            return null;
        }

        try {
            String territory = trimToNull(matcher.group("territory"));
            long health = Long.parseLong(matcher.group("health"));
            double defense = Double.parseDouble(matcher.group("defense"));
            long damageLow = Long.parseLong(matcher.group("damageLow"));
            long damageHigh = Long.parseLong(matcher.group("damageHigh"));
            double attackSpeed = Double.parseDouble(matcher.group("attackSpeed"));
            if (territory == null
                    || !Double.isFinite(defense)
                    || defense < 0.0
                    || defense >= 100.0
                    || damageHigh < damageLow
                    || !Double.isFinite(attackSpeed)
                    || attackSpeed < 0.0) {
                return null;
            }

            // Match the backend war-log formulas: effective health is floored like
            // Wynntils, while expected outgoing tower DPS is rounded like %.0f.
            // Publication retains the first effective-health value for this bar
            // lifecycle as initial EHP while the boss-bar progress keeps changing.
            double effectiveHealth = health / (1.0 - defense / 100.0);
            double towerDps = damageHigh * 5d / 6d * attackSpeed;
            if (!isNonnegativeLongValue(effectiveHealth) || !isNonnegativeLongValue(towerDps)) {
                return null;
            }
            return new TowerTitle(
                    territory,
                    health,
                    defense,
                    damageLow,
                    damageHigh,
                    attackSpeed,
                    (long) Math.floor(effectiveHealth),
                    Math.round(towerDps));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private void applyName(TrackedBossBar bar, Component name) {
        TowerTitle parsed = name == null ? null : parseTowerTitle(name.getString());
        markUpdated(bar);
        if (parsed == null) {
            bar.tower = null;
            return;
        }
        if (bar.initialEffectiveHealth == null
                || bar.initialTerritory == null
                || !bar.initialTerritory.equalsIgnoreCase(parsed.territory())) {
            bar.initialTerritory = parsed.territory();
            bar.initialEffectiveHealth = parsed.effectiveHealth();
        }
        bar.tower = parsed;
    }

    private void markUpdated(TrackedBossBar bar) {
        bar.lastUpdatedOrder = ++updateSequence;
    }

    private WarTowerUpdate toUpdate(TrackedBossBar bar) {
        return new WarTowerUpdate(
                bar.tower.territory(),
                Math.clamp(bar.progress, 0.0f, 1.0f),
                bar.initialEffectiveHealth,
                bar.tower.towerDps());
    }

    private TrackedBossBar getOrCreate(UUID id) {
        TrackedBossBar existing = bars.get(id);
        if (existing != null) {
            return existing;
        }
        if (bars.size() >= MAX_TRACKED_BARS) {
            evictOldestBar();
        }
        TrackedBossBar created = new TrackedBossBar();
        bars.put(id, created);
        return created;
    }

    private void evictOldestBar() {
        Iterator<Map.Entry<UUID, TrackedBossBar>> iterator = bars.entrySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().getValue().tower == null) {
                iterator.remove();
                return;
            }
        }
        iterator = bars.entrySet().iterator();
        if (iterator.hasNext()) {
            iterator.next();
            iterator.remove();
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static boolean isNonnegativeLongValue(double value) {
        return Double.isFinite(value) && value >= 0.0 && value < LONG_UPPER_BOUND_EXCLUSIVE;
    }

    record TowerTitle(
            String territory,
            long health,
            double defense,
            long damageLow,
            long damageHigh,
            double attackSpeed,
            long effectiveHealth,
            long towerDps) {}

    public record TowerSnapshot(UUID bossBarId, WarTowerUpdate update) {}

    private static final class TrackedBossBar {
        private float progress = Float.NaN;
        private TowerTitle tower;
        private String initialTerritory;
        private Long initialEffectiveHealth;
        private long lastUpdatedOrder;

        private void resetLifecycle() {
            progress = Float.NaN;
            tower = null;
            initialTerritory = null;
            initialEffectiveHealth = null;
        }
    }

    private final class PacketHandler implements ClientboundBossEventPacket.Handler {
        @Override
        public void add(
                UUID id,
                Component name,
                float progress,
                BossEvent.BossBarColor color,
                BossEvent.BossBarOverlay overlay,
                boolean darkenScreen,
                boolean playMusic,
                boolean createWorldFog) {
            MinecraftWarTowerTracker.this.add(id, name, progress);
        }

        @Override
        public void remove(UUID id) {
            MinecraftWarTowerTracker.this.remove(id);
        }

        @Override
        public void updateProgress(UUID id, float progress) {
            MinecraftWarTowerTracker.this.updateProgress(id, progress);
        }

        @Override
        public void updateName(UUID id, Component name) {
            MinecraftWarTowerTracker.this.updateName(id, name);
        }

        @Override
        public void updateStyle(UUID id, BossEvent.BossBarColor color, BossEvent.BossBarOverlay overlay) {}

        @Override
        public void updateProperties(UUID id, boolean darkenScreen, boolean playMusic, boolean createWorldFog) {}
    }
}
