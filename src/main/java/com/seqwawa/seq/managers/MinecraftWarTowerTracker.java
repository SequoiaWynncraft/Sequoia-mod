package com.seqwawa.seq.managers;

import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.model.WarTowerUpdate;
import com.seqwawa.seq.utils.PacketTextNormalizer;
import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBossEventPacket;
import net.minecraft.world.BossEvent;

/**
 * Reads Wynncraft tower metrics directly from vanilla boss-event packets.
 *
 * <p>The tracker is client-thread confined. It stores only normalized scalar
 * values, caps both active bars and per-bar samples, and never retains packet,
 * component, player, level, or other Minecraft world objects.
 */
public final class MinecraftWarTowerTracker {
    static final int MAX_TRACKED_BARS = 32;
    static final int MAX_SAMPLES_PER_BAR = 256;
    static final long DPS_WINDOW_MILLIS = 10_000L;
    private static final long DPS_WINDOW_SECONDS = DPS_WINDOW_MILLIS / 1_000L;

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

    private static final MinecraftWarTowerTracker INSTANCE =
            new MinecraftWarTowerTracker(System::currentTimeMillis);

    private final LongSupplier clock;
    private final LinkedHashMap<UUID, TrackedBossBar> bars =
            new LinkedHashMap<>(MAX_TRACKED_BARS, 0.75f, true);
    private final ClientboundBossEventPacket.Handler packetHandler = new PacketHandler();

    public static MinecraftWarTowerTracker getInstance() {
        return INSTANCE;
    }

    MinecraftWarTowerTracker(LongSupplier clock) {
        this.clock = clock;
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
            if (newest == null || bar.lastUpdatedAt > newest.lastUpdatedAt) {
                newest = bar;
            }
        }
        if (newest == null || !Float.isFinite(newest.progress)) {
            return null;
        }

        long now = clock.getAsLong();
        newest.pruneSamples(now);
        return new WarTowerUpdate(
                newest.tower.territory(),
                Math.clamp(newest.progress, 0.0f, 1.0f),
                newest.tower.effectiveHealth(),
                newest.rollingDps());
    }

    /** Clears all packet-derived state at a connection/world boundary. */
    public void reset() {
        bars.clear();
    }

    void add(UUID id, Component name, float progress) {
        if (id == null) {
            return;
        }
        TrackedBossBar bar = getOrCreate(id);
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
        bar.lastUpdatedAt = clock.getAsLong();
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

    int sampleCount(UUID id) {
        TrackedBossBar bar = bars.get(id);
        return bar == null ? 0 : bar.samples.size();
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

            double effectiveHealth = health / (1.0 - defense / 100.0);
            if (!Double.isFinite(effectiveHealth) || effectiveHealth > Long.MAX_VALUE) {
                return null;
            }
            return new TowerTitle(territory, health, defense, (long) Math.floor(effectiveHealth));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private void applyName(TrackedBossBar bar, Component name) {
        TowerTitle parsed = name == null ? null : parseTowerTitle(name.getString());
        long now = clock.getAsLong();
        bar.lastUpdatedAt = now;
        if (parsed == null) {
            bar.tower = null;
            bar.samples.clear();
            return;
        }
        if (bar.tower == null || !bar.tower.territory().equalsIgnoreCase(parsed.territory())) {
            bar.samples.clear();
        }
        bar.tower = parsed;
        bar.addSample(now, parsed.effectiveHealth());
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

    record TowerTitle(String territory, long health, double defense, long effectiveHealth) {}

    private static final class TrackedBossBar {
        private final ArrayDeque<Sample> samples = new ArrayDeque<>();
        private float progress = Float.NaN;
        private TowerTitle tower;
        private long lastUpdatedAt;

        private void addSample(long observedAt, long effectiveHealth) {
            Sample last = samples.peekLast();
            if (last != null && observedAt < last.observedAt()) {
                samples.clear();
                last = null;
            }
            if (last != null && observedAt == last.observedAt()) {
                samples.removeLast();
            }
            samples.addLast(new Sample(observedAt, effectiveHealth));
            pruneSamples(observedAt);
            while (samples.size() > MAX_SAMPLES_PER_BAR) {
                samples.removeFirst();
            }
        }

        private void pruneSamples(long now) {
            long cutoff = now - DPS_WINDOW_MILLIS;
            while (!samples.isEmpty() && samples.peekFirst().observedAt() < cutoff) {
                samples.removeFirst();
            }
        }

        private long rollingDps() {
            Sample first = samples.peekFirst();
            Sample last = samples.peekLast();
            if (first == null || last == null || first == last || first.effectiveHealth() <= last.effectiveHealth()) {
                return 0L;
            }
            long damage;
            try {
                damage = Math.subtractExact(first.effectiveHealth(), last.effectiveHealth());
            } catch (ArithmeticException exception) {
                damage = Long.MAX_VALUE;
            }
            return damage / DPS_WINDOW_SECONDS;
        }
    }

    private record Sample(long observedAt, long effectiveHealth) {}

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
