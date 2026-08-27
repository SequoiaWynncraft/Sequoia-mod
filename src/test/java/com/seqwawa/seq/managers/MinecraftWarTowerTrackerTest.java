package com.seqwawa.seq.managers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.seqwawa.seq.model.WarTowerUpdate;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBossEventPacket;
import net.minecraft.world.BossEvent;
import org.junit.jupiter.api.Test;

class MinecraftWarTowerTrackerTest {

    private static final UUID TOWER_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");

    @Test
    void parsesStyledWynnTowerTitleAndComputesBackendMetrics() {
        MinecraftWarTowerTracker.TowerTitle title = MinecraftWarTowerTracker.parseTowerTitle(
                "§3[SEQ] §bEntrance to Olux Tower§7 - §4❤ 300000§7 (§625.0%§7)"
                        + " - §c⚔ 1200-1800§7 (§b2.5x§7)");

        assertEquals("Entrance to Olux", title.territory());
        assertEquals(300_000L, title.health());
        assertEquals(25.0, title.defense());
        assertEquals(400_000L, title.effectiveHealth());
        assertEquals(1_200L, title.damageLow());
        assertEquals(1_800L, title.damageHigh());
        assertEquals(2.5, title.attackSpeed());
        assertEquals(3_750L, title.towerDps());
    }

    @Test
    void recognizesPrivateUseIconsAfterTextNormalizationAndRejectsLookalikes() {
        MinecraftWarTowerTracker.TowerTitle title = MinecraftWarTowerTracker.parseTowerTitle(
                "§3[Sequoia] §bMangled Lake Tower§7 - §4\uE001 99§7 (§60%§7)"
                        + " - §d\uE002 10-20§7 (§b1.5x§7)");

        assertEquals("Mangled Lake", title.territory());
        assertEquals(99L, title.effectiveHealth());
        assertEquals(25L, title.towerDps());
        assertNull(MinecraftWarTowerTracker.parseTowerTitle("Mangled Lake Tower - 99 (0%)"));
        assertNull(MinecraftWarTowerTracker.parseTowerTitle(
                "[SEQ] Mangled Lake Tower - ❤ 99 (100%) - ⚔ 10-20 (1.5x)"));
        assertNull(MinecraftWarTowerTracker.parseTowerTitle(
                "[SEQ] Mangled Lake Tower - ❤ 99 (0%) - ⚔ 20-10 (1.5x)"));
    }

    @Test
    void packetStateRetainsInitialEhpWhileHealthFalls() {
        MinecraftWarTowerTracker tracker = new MinecraftWarTowerTracker();
        BossEvent tower = new BossEvent(
                TOWER_ID,
                towerTitle("Detlas", 450_000L, 25.0),
                BossEvent.BossBarColor.RED,
                BossEvent.BossBarOverlay.PROGRESS) {};
        tower.setProgress(1.0f);

        tracker.onBossEvent(ClientboundBossEventPacket.createAddPacket(tower));
        assertEquals(new WarTowerUpdate("Detlas", 1.0f, 600_000L, 3_750L), tracker.snapshot("detlas"));

        tracker.updateName(TOWER_ID, towerTitle("Detlas", 300_000L, 25.0, 1_600L, 2_400L, 3.0));
        tracker.updateProgress(TOWER_ID, 0.731f);

        WarTowerUpdate expected = new WarTowerUpdate("Detlas", 0.731f, 600_000L, 6_000L);
        assertEquals(expected, tracker.snapshot("detlas"));
        assertEquals(expected, tracker.latestSnapshot().update());
        assertNull(tracker.snapshot("Ragni"));
    }

    @Test
    void newBossBarLifecycleAndTerritoryEstablishFreshInitialEhp() {
        MinecraftWarTowerTracker tracker = new MinecraftWarTowerTracker();

        tracker.add(TOWER_ID, towerTitle("Detlas", 450_000L, 25.0), 1.0f);
        tracker.updateName(TOWER_ID, towerTitle("Detlas", 300_000L, 25.0));
        assertEquals(600_000L, tracker.snapshot("Detlas").ehp());

        tracker.updateName(TOWER_ID, towerTitle("Ragni", 80L, 20.0));
        assertEquals(100L, tracker.snapshot("Ragni").ehp());

        tracker.add(TOWER_ID, towerTitle("Ragni", 40L, 20.0), 1.0f);
        assertEquals(50L, tracker.snapshot("Ragni").ehp());
    }

    @Test
    void nameUpdateCanPromoteAnExistingGenericBarAndRemovalDropsIt() {
        MinecraftWarTowerTracker tracker = new MinecraftWarTowerTracker();

        tracker.add(TOWER_ID, Component.literal("Waiting"), 0.42f);
        tracker.updateName(TOWER_ID, towerTitle("Ragni", 80L, 20.0));

        assertEquals(new WarTowerUpdate("Ragni", 0.42f, 100L, 3_750L), tracker.snapshot("Ragni"));
        tracker.updateName(TOWER_ID, towerTitle("Ragni", 40L, 20.0));
        tracker.updateProgress(TOWER_ID, 0.21f);
        assertEquals(new WarTowerUpdate("Ragni", 0.21f, 100L, 3_750L), tracker.snapshot("Ragni"));
        tracker.remove(TOWER_ID);
        assertNull(tracker.snapshot("Ragni"));
    }

    @Test
    void latestSnapshotCarriesStableBossIdentityAndTracksTheNewestActiveTower() {
        MinecraftWarTowerTracker tracker = new MinecraftWarTowerTracker();
        UUID otherTowerId = UUID.fromString("10000000-0000-0000-0000-000000000002");

        tracker.add(TOWER_ID, towerTitle("Detlas", 100L, 0.0), 0.8f);
        tracker.add(otherTowerId, towerTitle("Ragni", 200L, 0.0), 0.6f);

        assertEquals(otherTowerId, tracker.latestSnapshot().bossBarId());
        assertEquals("Ragni", tracker.latestSnapshot().update().territory());

        tracker.updateProgress(TOWER_ID, 0.4f);
        assertEquals(TOWER_ID, tracker.latestSnapshot().bossBarId());
        assertEquals(new WarTowerUpdate("Detlas", 0.4f, 100L, 3_750L), tracker.latestSnapshot().update());

        tracker.remove(TOWER_ID);
        assertEquals(otherTowerId, tracker.latestSnapshot().bossBarId());
        tracker.remove(otherTowerId);
        assertNull(tracker.latestSnapshot());
    }

    @Test
    void activeBarsStayHardBoundedAndResetClearsThem() {
        MinecraftWarTowerTracker tracker = new MinecraftWarTowerTracker();
        for (int index = 0; index < MinecraftWarTowerTracker.MAX_TRACKED_BARS + 5; index++) {
            tracker.add(new UUID(0L, index + 1L), Component.literal("Unrelated boss " + index), 1.0f);
        }
        assertEquals(MinecraftWarTowerTracker.MAX_TRACKED_BARS, tracker.trackedBarCount());

        tracker.add(TOWER_ID, towerTitle("Detlas", 1_000_000L, 0.0), 1.0f);
        assertEquals(MinecraftWarTowerTracker.MAX_TRACKED_BARS, tracker.trackedBarCount());
        tracker.reset();
        assertEquals(0, tracker.trackedBarCount());
    }

    @Test
    void floorsEhpRoundsTowerDpsAndRejectsOverflow() {
        MinecraftWarTowerTracker.TowerTitle rounded = MinecraftWarTowerTracker.parseTowerTitle(
                "[SEQ] Detlas Tower - ❤ 10 (33%) - ⚔ 1-3 (0.2x)");

        assertEquals(14L, rounded.effectiveHealth());
        assertEquals(1L, rounded.towerDps());
        assertNull(MinecraftWarTowerTracker.parseTowerTitle(
                "[SEQ] Detlas Tower - ❤ 9223372036854775807 (99%) - ⚔ 1-2 (1x)"));
        assertNull(MinecraftWarTowerTracker.parseTowerTitle(
                "[SEQ] Detlas Tower - ❤ 1 (0%) - ⚔ 1-9223372036854775807 (2x)"));
    }

    private static Component towerTitle(String territory, long health, double defense) {
        return towerTitle(territory, health, defense, 1_200L, 1_800L, 2.5);
    }

    private static Component towerTitle(
            String territory, long health, double defense, long damageLow, long damageHigh, double attackSpeed) {
        return Component.literal(
                "[SEQ] " + territory + " Tower - ❤ " + health + " (" + defense + "%) - ⚔ " + damageLow + "-"
                        + damageHigh + " (" + attackSpeed + "x)");
    }
}
