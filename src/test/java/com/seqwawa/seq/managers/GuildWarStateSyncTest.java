package com.seqwawa.seq.managers;

import static org.junit.jupiter.api.Assertions.*;
import com.seqwawa.seq.model.war.GuildWarObservation;
import com.seqwawa.seq.model.war.GuildWarState;
import com.seqwawa.seq.network.ApiClient;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class GuildWarStateSyncTest {
    private static final Instant NOW = Instant.parse("2026-10-27T12:00:00Z");
    private final AtomicLong clock = new AtomicLong(1_000);
    private final AtomicBoolean allowed = new AtomicBoolean(true);
    private final FakeGateway gateway = new FakeGateway();
    private final List<String> effects = new ArrayList<>();
    private final GuildWarStateSync sync = new GuildWarStateSync(gateway, clock::get, allowed::get, Runnable::run, effects::add);

    @Test void newJoinerUsesAnotherMembersSharedHqAndStyle() {
        load(state(3, "cheapest"));
        success();
        assertEquals(List.of("Ragni"), effects);
        assertEquals(0, gateway.reports.size()); // Never re-publish backend-derived evidence.
    }

    @Test void collectsWithoutAnyVisualSettingOrEffectCallback() {
        var silent = new GuildWarStateSync(gateway, clock::get, allowed::get, Runnable::run, ignored -> {});
        silent.tick();
        gateway.complete(state(1, null));
        silent.tracker().onSystemChat("Torment changed the style of Detlas to cheapest");
        silent.tick();
        assertEquals("STYLE_CHANGED", gateway.reports.getFirst().source());
        assertEquals("cheapest", gateway.reports.getFirst().style());
    }

    @Test void capturesAgainstServerTimeAndNeverTheComputersWallClock() {
        sync.tick();
        localCheapest();
        clock.addAndGet(100);
        gateway.complete(state(1, null));
        sync.tick();
        assertEquals(NOW.minusMillis(100), gateway.reports.getFirst().observedAt());
        assertEquals("HQ_CHANGED", gateway.reports.getFirst().source());
    }

    @Test void lateInitialGetCannotEraseNewLocalEvidence() {
        sync.tick();
        localCheapest();
        gateway.complete(state(1, null));
        success();
        assertEquals(List.of("Ragni"), effects);
    }

    @Test void resetDropsOldCallbacksAndNeverReplaysTheirObservations() {
        sync.tick();
        CompletableFuture<GuildWarState> old = gateway.current;
        localCheapest();
        sync.reset();
        sync.tick();
        gateway.complete(state(8, "fastest"));
        old.complete(state(2, "cheapest"));
        success();
        sync.tick();
        assertEquals(List.of(), effects);
        assertEquals(8, sync.shared().revision());
        assertEquals(0, gateway.reports.size());
    }

    @Test void lowerRevisionResponseCannotRevertKnownFastest() {
        load(state(8, "fastest"));
        clock.addAndGet(GuildWarStateSync.POLL_MS);
        sync.tick();
        gateway.complete(state(7, "cheapest"));
        success();
        assertEquals(List.of(), effects);
        assertEquals(8, sync.shared().revision());
    }

    @Test void authorizationLossClearsSharedAndPendingEvidence() {
        load(state(3, "cheapest"));
        allowed.set(false);
        sync.tick();
        allowed.set(true);
        success();
        assertEquals(List.of(), effects);
        assertNull(sync.shared());
    }

    @Test void forbiddenResponseImmediatelyClearsSharedEvidence() {
        load(state(3, "cheapest"));
        clock.addAndGet(GuildWarStateSync.POLL_MS);
        sync.tick();
        gateway.current.completeExceptionally(new ApiClient.ApiException(403, "{}"));
        success();
        assertEquals(List.of(), effects);
        assertNull(sync.shared());
    }

    @Test void expiredSharedEvidenceCannotTriggerFlash() {
        load(state(3, "cheapest"));
        clock.addAndGet(6 * 60 * 60_000L);
        sync.tick();
        success();
        assertEquals(List.of(), effects);
    }

    @Test void retriesAreBoundedAndKeepOriginalCaptureTime() {
        load(state(1, null));
        sync.tracker().onSystemChat("Torment changed the style of Detlas to cheapest");
        for (int i = 0; i < 3; i++) {
            sync.tick();
            gateway.current.completeExceptionally(new IllegalStateException("offline"));
            clock.addAndGet(5_000);
        }
        sync.tick();
        assertEquals(3, gateway.reports.size());
        assertTrue(gateway.reports.stream().allMatch(report -> report.observedAt().equals(NOW)));
    }

    @Test void oldPendingEventsAreDroppedInsteadOfReplayed() {
        sync.tick();
        localCheapest();
        clock.addAndGet(GuildWarStateSync.MAX_PENDING_AGE_MS);
        gateway.complete(state(1, null));
        sync.tick();
        assertEquals(0, gateway.reports.size());
    }

    @Test void unrelatedMenuSlotsNeverPublishCachedHqAgain() {
        load(state(1, null));
        var tracker = sync.tracker();
        tracker.onMenuOpened(7, "Sequoia: Territories", 54);
        tracker.onMenuSlot(7, 10, new CheapestQueueTracker.TerritoryEntry("Detlas (HQ)", "Click to Select"));
        sync.tick();
        gateway.complete(state(2, null));
        clock.addAndGet(61_000);
        tracker.onMenuSlot(7, 11, new CheapestQueueTracker.TerritoryEntry("Ragni", "Click to Select"));
        sync.tick();
        assertEquals(1, gateway.reports.size());
    }

    @Test void hqChangeRetiresOldMenuEvidence() {
        load(state(1, null));
        var tracker = sync.tracker();
        tracker.onMenuOpened(7, "Sequoia: Territories", 54);
        tracker.onMenuSlot(7, 10, new CheapestQueueTracker.TerritoryEntry("Detlas (HQ)", "Click to Select"));
        tracker.onSystemChat("Envy set the guild headquarters to Ragni");
        clock.addAndGet(61_000);
        tracker.onMenuSlot(7, 10, new CheapestQueueTracker.TerritoryEntry("Detlas (HQ)", "Click to Select"));
        assertEquals(0, tracker.menuSlots(7));
    }

    @Test void delayedPostResponseCannotRedateTheNextQueuedStyleChange() {
        load(state(1, null));
        clock.addAndGet(10);
        sync.tracker().onSystemChat("Torment changed the style of Detlas to fastest");
        sync.tick();
        clock.addAndGet(1);
        sync.tracker().onSystemChat("Torment changed the style of Detlas to cheapest");
        clock.addAndGet(100);
        var delayed = new GuildWarState(1, 2, NOW.plusMillis(20), "Detlas", NOW, "HQ_MENU",
                "fastest", NOW.plusMillis(10), null, NOW.plusSeconds(21600));
        gateway.complete(delayed);
        sync.tick();
        assertEquals(NOW.plusMillis(10), gateway.reports.get(0).observedAt());
        assertEquals(NOW.plusMillis(11), gateway.reports.get(1).observedAt());
    }

    @Test void failedUploadNeverLetsOlderSharedCheapestReplaceNewLocalFastest() {
        load(state(1, "cheapest"));
        clock.addAndGet(10);
        sync.tracker().onSystemChat("Torment changed the style of Detlas to fastest");
        for (int i = 0; i < 3; i++) {
            sync.tick();
            gateway.current.completeExceptionally(new IllegalStateException("offline"));
            clock.addAndGet(5_000);
        }
        sync.tick(); // The upload is exhausted; a GET still holds the older cheapest.
        gateway.complete(state(1, "cheapest"));
        success();
        assertEquals(List.of(), effects);
    }

    @Test void expiryAppliesEvenWhileARequestIsStillInFlight() {
        load(state(1, "cheapest"));
        clock.addAndGet(6 * 60 * 60_000L - 1);
        sync.tick(); // Keep the GET unresolved across the expiry boundary.
        clock.addAndGet(2);
        sync.tick();
        success();
        assertEquals(List.of(), effects);
    }

    @Test void aFailedRequestCannotOverflowThePendingObservationBound() {
        load(state(1, null));
        clock.incrementAndGet();
        sync.tracker().onSystemChat("Torment changed the style of Detlas to cheapest");
        sync.tick();
        for (int i = 0; i < 40; i++) {
            clock.incrementAndGet();
            sync.tracker().onSystemChat("Torment changed the style of Detlas to " + (i % 2 == 0 ? "fastest" : "cheapest"));
        }
        gateway.current.completeExceptionally(new IllegalStateException("offline"));
        clock.addAndGet(5_000);
        for (int i = 0; i < 24; i++) { sync.tick(); gateway.complete(state(i + 2, null)); }
        sync.tick();
        assertEquals(25, gateway.reports.size()); // One in flight plus at most 24 pending.
    }

    private void load(GuildWarState state) { sync.tick(); gateway.complete(state); }
    private void localCheapest() {
        sync.tracker().onSystemChat("Envy set the guild headquarters to Detlas");
        sync.tracker().onSystemChat("Torment changed the style of Detlas to cheapest");
    }
    private void success() {
        sync.tracker().onAttackClick("Attacking: Ragni", "Attack");
        sync.tracker().onSystemChat("The war for Ragni will start in 3 minutes.");
    }
    private static GuildWarState state(long revision, String style) {
        return new GuildWarState(1, revision, NOW, "Detlas", NOW, "HQ_MENU", style,
                style == null ? null : NOW, null, NOW.plusSeconds(6 * 60 * 60));
    }
    private static final class FakeGateway implements GuildWarStateSync.Gateway {
        private CompletableFuture<GuildWarState> current;
        private final List<GuildWarObservation> reports = new ArrayList<>();
        @Override public CompletableFuture<GuildWarState> fetch() { current = new CompletableFuture<>(); return current; }
        @Override public CompletableFuture<GuildWarState> observe(GuildWarObservation report) {
            reports.add(report); current = new CompletableFuture<>(); return current;
        }
        private void complete(GuildWarState state) { current.complete(state); }
    }
}
