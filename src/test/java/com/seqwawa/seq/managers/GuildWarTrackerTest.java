package com.seqwawa.seq.managers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seqwawa.seq.model.GuildWarQueueSubmission;
import com.wynntils.models.war.type.WarBattleInfo;
import com.wynntils.models.war.type.WarTowerState;
import com.wynntils.utils.type.RangedValue;
import java.util.ArrayList;
import java.util.List;
import com.seqwawa.seq.model.GuildWarSubmission;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

class GuildWarTrackerTest {

    @Test
    void normalCaptureWithSeasonRatingPublishesInitialTowerStats() {
        MutableWarInfoProvider warInfoProvider = new MutableWarInfoProvider();
        CapturingPublisher publisher = new CapturingPublisher();
        GuildWarTracker tracker = newTracker(
                warInfoProvider,
                new FakePlayerContext(
                        "LocalUser",
                        "550e8400-e29b-41d4-a716-446655440000",
                        List.of("Alpha", "Bravo", "Charlie")),
                publisher);

        warInfoProvider.currentWar =
                war("Detlas Suburbs", towerState(1_000L, 450_000L), towerState(2_000L, 0L));

        tracker.tick();
        assertTrue(publisher.warSubmissions.isEmpty());

        tracker.onSystemChat(Component.literal(
                "Territory Captured! Captured \"Detlas Suburbs\" and +410 Seasonal Rating"));

        assertEquals(1, publisher.warSubmissions.size());
        GuildWarSubmission submission = publisher.warSubmissions.getFirst();
        assertEquals("Detlas Suburbs", submission.territory());
        assertEquals(List.of("LocalUser", "Alpha", "Bravo", "Charlie"), submission.warrers());
        assertEquals(450_000L, submission.stats().health());
        assertEquals(410, submission.seasonRating());
        assertEquals("2024-03-28T01:06:40Z", submission.completedAt());
    }

    @Test
    void towerDestroyedBeforeSeasonRatingStaysPendingUntilWarDisappears() {
        MutableWarInfoProvider warInfoProvider = new MutableWarInfoProvider();
        CapturingPublisher publisher = new CapturingPublisher();
        GuildWarTracker tracker = newTracker(
                warInfoProvider,
                new FakePlayerContext("LocalUser", "uuid-1", List.of("Alpha")),
                publisher);

        warInfoProvider.currentWar = war("Nemract", towerState(1_000L, 250_000L), towerState(2_000L, 0L));

        tracker.tick();
        assertTrue(publisher.warSubmissions.isEmpty());

        warInfoProvider.currentWar = null;
        tracker.tick();

        assertEquals(1, publisher.warSubmissions.size());
        GuildWarSubmission submission = publisher.warSubmissions.getFirst();
        assertEquals(0, submission.seasonRating());
        assertEquals("1970-01-01T00:00:02Z", submission.completedAt());
    }

    @Test
    void localDeathForceSendsCurrentWar() {
        MutableWarInfoProvider warInfoProvider = new MutableWarInfoProvider();
        CapturingPublisher publisher = new CapturingPublisher();
        GuildWarTracker tracker = newTracker(
                warInfoProvider,
                new FakePlayerContext("LocalUser", "uuid-2", List.of("Alpha", "Bravo")),
                publisher);

        warInfoProvider.currentWar = war("Olux", towerState(1_000L, 350_000L), towerState(2_000L, 300_000L));

        tracker.tick();
        tracker.onCharacterDeath();

        assertEquals(1, publisher.warSubmissions.size());
        GuildWarSubmission submission = publisher.warSubmissions.getFirst();
        assertEquals(0, submission.seasonRating());
        assertNull(submission.completedAt());
        assertEquals(350_000L, submission.stats().health());
    }

    @Test
    void mismatchedCapturedTerritoryIsIgnored() {
        MutableWarInfoProvider warInfoProvider = new MutableWarInfoProvider();
        CapturingPublisher publisher = new CapturingPublisher();
        GuildWarTracker tracker = newTracker(
                warInfoProvider,
                new FakePlayerContext("LocalUser", "uuid-3", List.of("Alpha")),
                publisher);

        warInfoProvider.currentWar = war("Llevigar Plains", towerState(1_000L, 500_000L), towerState(2_000L, 0L));

        tracker.tick();
        tracker.onSystemChat(Component.literal(
                "Territory Captured! Captured \"Wrong Territory\" and +225 Seasonal Rating"));

        assertTrue(publisher.warSubmissions.isEmpty());

        warInfoProvider.currentWar = null;
        tracker.tick();

        assertEquals(1, publisher.warSubmissions.size());
        assertEquals(0, publisher.warSubmissions.getFirst().seasonRating());
    }

    @Test
    void duplicateStateChangesDoNotResubmit() {
        MutableWarInfoProvider warInfoProvider = new MutableWarInfoProvider();
        CapturingPublisher publisher = new CapturingPublisher();
        GuildWarTracker tracker = newTracker(
                warInfoProvider,
                new FakePlayerContext("LocalUser", "uuid-4", List.of("Alpha")),
                publisher);

        warInfoProvider.currentWar = war("Ahmsord", towerState(1_000L, 500_000L), towerState(2_000L, 0L));

        tracker.tick();
        tracker.onSystemChat(Component.literal(
                "Territory Captured! Captured \"Ahmsord\" and +111 Seasonal Rating"));
        tracker.tick();
        tracker.onSystemChat(Component.literal(
                "Territory Captured! Captured \"Ahmsord\" and +111 Seasonal Rating"));

        assertEquals(1, publisher.warSubmissions.size());
    }

    @Test
    void invalidNearbyWarrersFallBackToLocalPlayer() {
        MutableWarInfoProvider warInfoProvider = new MutableWarInfoProvider();
        CapturingPublisher publisher = new CapturingPublisher();
        GuildWarTracker tracker = newTracker(
                warInfoProvider,
                new FakePlayerContext("LocalUser", "uuid-5", List.of("bad name", "", "ab", "???")),
                publisher);

        warInfoProvider.currentWar = war("Corkus City", towerState(1_000L, 500_000L), towerState(2_000L, 0L));

        tracker.tick();
        warInfoProvider.currentWar = null;
        tracker.tick();

        assertEquals(1, publisher.warSubmissions.size());
        assertEquals(List.of("LocalUser"), publisher.warSubmissions.getFirst().warrers());
    }

    @Test
    void queueClickWaitsForMatchingServerConfirmationAndRoundsOnlyLegacyMinutes() {
        MutableWarInfoProvider warInfoProvider = new MutableWarInfoProvider();
        CapturingPublisher publisher = new CapturingPublisher();
        GuildWarTracker tracker = newTracker(
                warInfoProvider,
                new FakePlayerContext("LocalUser", "uuid-queue", List.of()),
                publisher);

        tracker.rememberQueueAttempt("Sulphuric Hollow", "Very Low");
        tracker.onSystemChat(Component.literal("The war for Detlas will start in 4 minutes."));
        assertTrue(publisher.queueSubmissions.isEmpty());

        tracker.onSystemChat(Component.literal(
                "The war for Sulphuric Hollow will start in 6 minutes and 31 seconds.\u2064\u2064"));

        assertEquals(1, publisher.queueSubmissions.size());
        GuildWarQueueSubmission submission = publisher.queueSubmissions.getFirst();
        assertEquals("Sulphuric Hollow", submission.territory());
        assertEquals("Very Low", submission.defenseRating());
        assertEquals(7, submission.queueMinutes());
    }

    @Test
    void unconfirmedQueueClickExpiresWithoutPublishing() {
        MutableWarInfoProvider warInfoProvider = new MutableWarInfoProvider();
        CapturingPublisher publisher = new CapturingPublisher();
        long[] now = {1_711_588_000_000L};
        GuildWarTracker tracker = new GuildWarTracker(
                warInfoProvider,
                new FakePlayerContext("LocalUser", "uuid-queue", List.of()),
                publisher,
                () -> true,
                () -> now[0],
                false);

        tracker.rememberQueueAttempt("Detlas", "High");
        now[0] += 15_001L;
        tracker.onSystemChat(Component.literal("The war for Detlas will start in 1 minute."));

        assertTrue(publisher.queueSubmissions.isEmpty());
    }

    private GuildWarTracker newTracker(
            MutableWarInfoProvider warInfoProvider,
            FakePlayerContext playerContext,
            CapturingPublisher publisher) {
        return new GuildWarTracker(warInfoProvider, playerContext, publisher, () -> true, () -> 1_711_588_000_000L, false);
    }

    private WarBattleInfo war(String territory, WarTowerState initialState, WarTowerState currentState) {
        WarBattleInfo info = new WarBattleInfo(territory, "Sequoia", initialState);
        if (!initialState.equals(currentState)) {
            info.addNewState(currentState);
        }
        return info;
    }

    private WarTowerState towerState(long timestamp, long health) {
        return new WarTowerState(health, 0.25, new RangedValue(1200, 1800), 2.5, timestamp);
    }

    private static final class MutableWarInfoProvider implements GuildWarTracker.WarInfoProvider {
        private WarBattleInfo currentWar;

        @Override
        public WarBattleInfo getCurrentWar() {
            return currentWar;
        }
    }

    private record FakePlayerContext(String localUsername, String localUuid, List<String> nearbyNames)
            implements GuildWarTracker.PlayerContext {
        @Override
        public List<String> nearbyPlayerNames(double radiusSq) {
            return nearbyNames;
        }
    }

    private static final class CapturingPublisher implements GuildWarTracker.SubmissionPublisher {
        private final ArrayList<GuildWarSubmission> warSubmissions = new ArrayList<>();
        private final ArrayList<GuildWarQueueSubmission> queueSubmissions = new ArrayList<>();

        @Override
        public boolean publishWar(GuildWarSubmission submission) {
            warSubmissions.add(submission);
            return true;
        }

        @Override
        public boolean publishQueue(GuildWarQueueSubmission submission) {
            queueSubmissions.add(submission);
            return true;
        }
    }
}
