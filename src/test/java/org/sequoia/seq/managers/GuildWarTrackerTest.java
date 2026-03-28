package org.sequoia.seq.managers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wynntils.models.war.type.WarBattleInfo;
import com.wynntils.models.war.type.WarTowerState;
import com.wynntils.utils.type.RangedValue;
import java.util.ArrayList;
import java.util.List;
import org.sequoia.seq.model.GuildWarSubmission;
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
        assertTrue(publisher.submissions.isEmpty());

        tracker.onSystemChat(Component.literal(
                "Territory Captured! Captured \"Detlas Suburbs\" and +410 Seasonal Rating"));

        assertEquals(1, publisher.submissions.size());
        GuildWarSubmission submission = publisher.submissions.getFirst();
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
        assertTrue(publisher.submissions.isEmpty());

        warInfoProvider.currentWar = null;
        tracker.tick();

        assertEquals(1, publisher.submissions.size());
        GuildWarSubmission submission = publisher.submissions.getFirst();
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

        assertEquals(1, publisher.submissions.size());
        GuildWarSubmission submission = publisher.submissions.getFirst();
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

        assertTrue(publisher.submissions.isEmpty());

        warInfoProvider.currentWar = null;
        tracker.tick();

        assertEquals(1, publisher.submissions.size());
        assertEquals(0, publisher.submissions.getFirst().seasonRating());
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

        assertEquals(1, publisher.submissions.size());
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

        assertEquals(1, publisher.submissions.size());
        assertEquals(List.of("LocalUser"), publisher.submissions.getFirst().warrers());
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
        private final ArrayList<GuildWarSubmission> submissions = new ArrayList<>();

        @Override
        public boolean publish(GuildWarSubmission submission) {
            submissions.add(submission);
            return true;
        }
    }
}
