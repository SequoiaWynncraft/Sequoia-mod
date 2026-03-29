package org.sequoia.seq.managers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import org.junit.jupiter.api.Test;

class GuildStorageTrackerTest {

    @Test
    void parseSnapshotLinesExtractsDynamicCurrentAndMaxValues() {
        GuildStorageTracker.StorageSnapshot snapshot = GuildStorageTracker.parseSnapshotLines(List.of(
                "Emeralds: 12,345/92,160",
                "Aspects: 37/120",
                "Guild Tomes: 5/10"));

        assertNotNull(snapshot);
        assertEquals(12_345L, snapshot.emeralds().current());
        assertEquals(92_160L, snapshot.emeralds().max());
        assertEquals(37L, snapshot.aspects().current());
        assertEquals(120L, snapshot.aspects().max());
    }

    @Test
    void parseSnapshotLinesReturnsNullWhenRewardsUnavailable() {
        assertNull(GuildStorageTracker.parseSnapshotLines(List.of("Rewards are unavailable right now")));
    }

    @Test
    void applyRaidRewardsUpdatesSeededStateWithoutPublishingSnapshot() {
        List<String> notifications = new ArrayList<>();
        AtomicReference<GuildStorageTracker.StorageSnapshot> published = new AtomicReference<>();
        GuildStorageTracker tracker = new GuildStorageTracker(
                published::set,
                reward -> {},
                notifications::add,
                () -> true,
                () -> 100,
                () -> 100,
                System::currentTimeMillis);

        tracker.applyObservedSnapshot(new GuildStorageTracker.StorageSnapshot(
                new GuildStorageTracker.ResourceSnapshot(30_000, 30_720),
                new GuildStorageTracker.ResourceSnapshot(39, 40)));
        published.set(null);

        tracker.applyRaidRewards(1_024, 1);

        assertEquals(31_024L, tracker.currentSnapshot().emeralds().current());
        assertEquals(40L, tracker.currentSnapshot().aspects().current());
        assertNull(published.get());
        assertEquals(List.of("Emeralds storage reached 100% (31024/30720).", "Aspects storage reached 100% (40/40)."), notifications);
    }

    @Test
    void applyObservedSnapshotOnlyNotifiesOnUpwardCrossingAfterInitialSeed() {
        List<String> notifications = new ArrayList<>();
        GuildStorageTracker tracker = new GuildStorageTracker(
                snapshot -> {},
                reward -> {},
                notifications::add,
                () -> true,
                () -> 80,
                () -> 80,
                System::currentTimeMillis);

        tracker.applyObservedSnapshot(new GuildStorageTracker.StorageSnapshot(
                new GuildStorageTracker.ResourceSnapshot(24_575, 30_720),
                new GuildStorageTracker.ResourceSnapshot(20, 40)));
        tracker.applyObservedSnapshot(new GuildStorageTracker.StorageSnapshot(
                new GuildStorageTracker.ResourceSnapshot(24_577, 30_720),
                new GuildStorageTracker.ResourceSnapshot(32, 40)));
        tracker.applyObservedSnapshot(new GuildStorageTracker.StorageSnapshot(
                new GuildStorageTracker.ResourceSnapshot(20_000, 30_720),
                new GuildStorageTracker.ResourceSnapshot(20, 40)));
        tracker.applyObservedSnapshot(new GuildStorageTracker.StorageSnapshot(
                new GuildStorageTracker.ResourceSnapshot(25_000, 30_720),
                new GuildStorageTracker.ResourceSnapshot(33, 40)));

        assertEquals(
                List.of(
                        "Emeralds storage reached 80% (24577/30720).",
                        "Aspects storage reached 80% (32/40).",
                        "Emeralds storage reached 80% (25000/30720).",
                        "Aspects storage reached 80% (33/40)."),
                notifications);
    }

    @Test
    void applyObservedSnapshotRearmsThresholdWhenCapacityIncreases() {
        List<String> notifications = new ArrayList<>();
        GuildStorageTracker tracker = new GuildStorageTracker(
                snapshot -> {},
                reward -> {},
                notifications::add,
                () -> true,
                () -> 100,
                () -> 100,
                System::currentTimeMillis);

        tracker.applyObservedSnapshot(new GuildStorageTracker.StorageSnapshot(
                new GuildStorageTracker.ResourceSnapshot(30_720, 30_720),
                new GuildStorageTracker.ResourceSnapshot(40, 40)));
        tracker.applyObservedSnapshot(new GuildStorageTracker.StorageSnapshot(
                new GuildStorageTracker.ResourceSnapshot(30_720, 92_160),
                new GuildStorageTracker.ResourceSnapshot(40, 120)));
        tracker.applyObservedSnapshot(new GuildStorageTracker.StorageSnapshot(
                new GuildStorageTracker.ResourceSnapshot(92_160, 92_160),
                new GuildStorageTracker.ResourceSnapshot(120, 120)));

        assertEquals(
                List.of("Emeralds storage reached 100% (92160/92160).", "Aspects storage reached 100% (120/120)."),
                notifications);
    }

    @Test
    void parseRewardGrantResolvesNicknameToUsername() {
        Style senderStyle = Style.EMPTY.withHoverEvent(new HoverEvent.ShowText(
                Component.literal("Total Obliteration's real name is Dwoc")));
        Component message = Component.empty()
                .append(Component.literal("󏿼󐀆 "))
                .append(Component.literal("Total Obliteration").withStyle(senderStyle))
                .append(Component.literal(" rewarded 1024 Emeralds to cinfrascitizen"));

        GuildStorageTracker.RewardGrant grant = GuildStorageTracker.parseRewardGrant(message);

        assertNotNull(grant);
        assertEquals("Dwoc", grant.senderUsername());
        assertEquals("cinfrascitizen", grant.recipientUsername());
        assertEquals(GuildStorageTracker.ResourceType.EMERALDS, grant.resourceType());
        assertEquals(1_024L, grant.amount());
    }

    @Test
    void onSystemChatCountsEveryRewardMessageForStorageDrainAndFlushesOneBurst() {
        List<GuildStorageTracker.RewardBurst> publishedRewards = new ArrayList<>();
        AtomicLong now = new AtomicLong(Instant.parse("2026-03-29T20:00:00Z").toEpochMilli());
        GuildStorageTracker tracker = new GuildStorageTracker(
                snapshot -> {},
                publishedRewards::add,
                message -> {},
                () -> true,
                () -> 100,
                () -> 100,
                now::get);

        tracker.applyObservedSnapshot(new GuildStorageTracker.StorageSnapshot(
                new GuildStorageTracker.ResourceSnapshot(5_000, 92_160),
                new GuildStorageTracker.ResourceSnapshot(10, 120)));

        Component message = Component.literal("Dwoc rewarded 1024 Emeralds to cinfrascitizen");
        tracker.onSystemChat(message);
        tracker.onSystemChat(message);
        tracker.onSystemChat(message);

        assertEquals(1_928L, tracker.currentSnapshot().emeralds().current());
        assertEquals(0, publishedRewards.size());

        now.addAndGet(GuildStorageTracker.REWARD_BURST_IDLE_GAP_MS + 1);
        tracker.tick();

        assertEquals(1, publishedRewards.size());
        GuildStorageTracker.RewardBurst burst = publishedRewards.getFirst();
        assertEquals("Dwoc", burst.senderUsername());
        assertEquals("cinfrascitizen", burst.recipientUsername());
        assertEquals(GuildStorageTracker.ResourceType.EMERALDS, burst.resourceType());
        assertEquals(1_024L, burst.amount());
        assertEquals(3, burst.count());
        assertEquals(Instant.parse("2026-03-29T20:00:00Z"), burst.windowStartedAt());
    }

    @Test
    void rewardBurstsSplitAfterIdleGap() {
        List<GuildStorageTracker.RewardBurst> publishedRewards = new ArrayList<>();
        AtomicLong now = new AtomicLong(Instant.parse("2026-03-29T20:05:00Z").toEpochMilli());
        GuildStorageTracker tracker = new GuildStorageTracker(
                snapshot -> {},
                publishedRewards::add,
                message -> {},
                () -> true,
                () -> 100,
                () -> 100,
                now::get);

        Component message = Component.literal("Dwoc rewarded 1024 Emeralds to cinfrascitizen");
        tracker.onSystemChat(message);
        now.addAndGet(GuildStorageTracker.REWARD_BURST_IDLE_GAP_MS + 1);
        tracker.tick();

        now.addAndGet(500L);
        tracker.onSystemChat(message);
        now.addAndGet(GuildStorageTracker.REWARD_BURST_IDLE_GAP_MS + 1);
        tracker.tick();

        assertEquals(2, publishedRewards.size());
        assertEquals(1, publishedRewards.get(0).count());
        assertEquals(1, publishedRewards.get(1).count());
    }

    @Test
    void rewardBurstsHardCapFlushesLongRunningBurst() {
        List<GuildStorageTracker.RewardBurst> publishedRewards = new ArrayList<>();
        AtomicLong now = new AtomicLong(Instant.parse("2026-03-29T20:10:00Z").toEpochMilli());
        GuildStorageTracker tracker = new GuildStorageTracker(
                snapshot -> {},
                publishedRewards::add,
                message -> {},
                () -> true,
                () -> 100,
                () -> 100,
                now::get);

        Component message = Component.literal("Dwoc rewarded 1024 Emeralds to cinfrascitizen");
        tracker.onSystemChat(message);
        now.addAndGet(GuildStorageTracker.REWARD_BURST_MAX_WINDOW_MS);
        tracker.onSystemChat(message);

        assertEquals(1, publishedRewards.size());
        assertEquals(1, publishedRewards.getFirst().count());
    }

    @Test
    void resetFlushesPendingRewardBurst() {
        List<GuildStorageTracker.RewardBurst> publishedRewards = new ArrayList<>();
        AtomicLong now = new AtomicLong(Instant.parse("2026-03-29T20:15:00Z").toEpochMilli());
        GuildStorageTracker tracker = new GuildStorageTracker(
                snapshot -> {},
                publishedRewards::add,
                message -> {},
                () -> true,
                () -> 100,
                () -> 100,
                now::get);

        tracker.onSystemChat(Component.literal("Dwoc rewarded 1024 Emeralds to cinfrascitizen"));
        tracker.reset();

        assertEquals(1, publishedRewards.size());
        assertEquals(1, publishedRewards.getFirst().count());
    }
}
