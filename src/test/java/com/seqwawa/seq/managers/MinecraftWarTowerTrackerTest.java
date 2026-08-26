package com.seqwawa.seq.managers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.seqwawa.seq.model.WarTowerUpdate;
import java.util.UUID;
import java.util.function.LongSupplier;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBossEventPacket;
import net.minecraft.world.BossEvent;
import org.junit.jupiter.api.Test;

class MinecraftWarTowerTrackerTest {

    private static final UUID TOWER_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");

    @Test
    void parsesStyledWynnTowerTitleAndComputesEffectiveHealth() {
        MinecraftWarTowerTracker.TowerTitle title = MinecraftWarTowerTracker.parseTowerTitle(
                "§3[SEQ] §bEntrance to Olux Tower§7 - §4❤ 300000§7 (§625.0%§7)"
                        + " - §c⚔ 1200-1800§7 (§b2.5x§7)");

        assertEquals("Entrance to Olux", title.territory());
        assertEquals(300_000L, title.health());
        assertEquals(25.0, title.defense());
        assertEquals(400_000L, title.effectiveHealth());
    }

    @Test
    void recognizesPrivateUseIconsAfterTextNormalizationAndRejectsLookalikes() {
        MinecraftWarTowerTracker.TowerTitle title = MinecraftWarTowerTracker.parseTowerTitle(
                "§3[Sequoia] §bMangled Lake Tower§7 - §4\uE001 99§7 (§60%§7)"
                        + " - §d\uE002 10-20§7 (§b1.5x§7)");

        assertEquals("Mangled Lake", title.territory());
        assertEquals(99L, title.effectiveHealth());
        assertNull(MinecraftWarTowerTracker.parseTowerTitle("Mangled Lake Tower - 99 (0%)"));
        assertNull(MinecraftWarTowerTracker.parseTowerTitle(
                "[SEQ] Mangled Lake Tower - ❤ 99 (100%) - ⚔ 10-20 (1.5x)"));
        assertNull(MinecraftWarTowerTracker.parseTowerTitle(
                "[SEQ] Mangled Lake Tower - ❤ 99 (0%) - ⚔ 20-10 (1.5x)"));
    }

    @Test
    void packetStateProvidesFillEffectiveHealthAndRollingTenSecondDps() {
        MutableClock clock = new MutableClock(1_000L);
        MinecraftWarTowerTracker tracker = new MinecraftWarTowerTracker(clock);
        BossEvent tower = new BossEvent(
                TOWER_ID,
                towerTitle("Detlas", 450_000L, 25.0),
                BossEvent.BossBarColor.RED,
                BossEvent.BossBarOverlay.PROGRESS) {};
        tower.setProgress(1.0f);

        tracker.onBossEvent(ClientboundBossEventPacket.createAddPacket(tower));
        clock.advance(5_000L);
        tracker.updateName(TOWER_ID, towerTitle("Detlas", 300_000L, 25.0));
        tracker.updateProgress(TOWER_ID, 0.731f);

        assertEquals(new WarTowerUpdate("Detlas", 0.731f, 400_000L, 20_000L), tracker.snapshot("detlas"));
        assertNull(tracker.snapshot("Ragni"));

        clock.advance(5_001L);
        assertEquals(0L, tracker.snapshot("Detlas").dps());
    }

    @Test
    void nameUpdateCanPromoteAnExistingGenericBarAndRemovalDropsIt() {
        MutableClock clock = new MutableClock(10_000L);
        MinecraftWarTowerTracker tracker = new MinecraftWarTowerTracker(clock);

        tracker.add(TOWER_ID, Component.literal("Waiting"), 0.42f);
        tracker.updateName(TOWER_ID, towerTitle("Ragni", 80L, 20.0));

        assertEquals(new WarTowerUpdate("Ragni", 0.42f, 100L, 0L), tracker.snapshot("Ragni"));
        tracker.remove(TOWER_ID);
        assertNull(tracker.snapshot("Ragni"));
    }

    @Test
    void activeBarsAndDamageSamplesStayHardBounded() {
        MutableClock clock = new MutableClock(20_000L);
        MinecraftWarTowerTracker tracker = new MinecraftWarTowerTracker(clock);
        for (int index = 0; index < MinecraftWarTowerTracker.MAX_TRACKED_BARS + 5; index++) {
            tracker.add(new UUID(0L, index + 1L), Component.literal("Unrelated boss " + index), 1.0f);
        }
        assertEquals(MinecraftWarTowerTracker.MAX_TRACKED_BARS, tracker.trackedBarCount());

        tracker.add(TOWER_ID, towerTitle("Detlas", 1_000_000L, 0.0), 1.0f);
        for (int index = 1; index <= MinecraftWarTowerTracker.MAX_SAMPLES_PER_BAR + 50; index++) {
            clock.advance(1L);
            tracker.updateName(TOWER_ID, towerTitle("Detlas", 1_000_000L - index, 0.0));
        }

        assertEquals(MinecraftWarTowerTracker.MAX_TRACKED_BARS, tracker.trackedBarCount());
        assertEquals(MinecraftWarTowerTracker.MAX_SAMPLES_PER_BAR, tracker.sampleCount(TOWER_ID));
        tracker.reset();
        assertEquals(0, tracker.trackedBarCount());
    }

    private static Component towerTitle(String territory, long health, double defense) {
        return Component.literal(
                "[SEQ] " + territory + " Tower - ❤ " + health + " (" + defense + "%) - ⚔ 1200-1800 (2.5x)");
    }

    private static final class MutableClock implements LongSupplier {
        private long now;

        private MutableClock(long now) {
            this.now = now;
        }

        @Override
        public long getAsLong() {
            return now;
        }

        private void advance(long millis) {
            now += millis;
        }
    }
}
