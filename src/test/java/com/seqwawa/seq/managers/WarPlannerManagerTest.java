package com.seqwawa.seq.managers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seqwawa.seq.model.war.WarPlannerDrafts.TeamDraft;
import com.seqwawa.seq.model.war.WarPlannerDrafts.TeamMemberDraft;
import com.seqwawa.seq.model.war.WarPlannerDrafts.ZoneDraft;
import com.seqwawa.seq.model.war.WarCompositionRole;
import com.seqwawa.seq.model.war.WarPlannerSnapshot;
import com.seqwawa.seq.model.war.WarTeamRole;
import com.seqwawa.seq.network.ApiClient;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

class WarPlannerManagerTest {
    private static final Instant NOW = Instant.parse("2026-08-16T12:00:00Z");

    @Test
    void coalescesRefreshAndAuthorizesOnlySupportedSuccessfulSnapshot() {
        FakeGateway gateway = new FakeGateway();
        WarPlannerManager manager = manager(gateway);

        manager.tick(true, true);
        manager.tick(true, true);

        assertEquals(1, gateway.calls);
        assertEquals(WarPlannerManager.State.LOADING, manager.state());
        assertFalse(manager.isAuthorized());

        WarPlannerSnapshot snapshot = snapshot(1, true);
        gateway.next.complete(snapshot);

        assertEquals(WarPlannerManager.State.READY, manager.state());
        assertTrue(manager.isAuthorized());
        assertTrue(manager.canManage());
        assertSame(snapshot, manager.snapshot());
    }

    @Test
    void rejectsUnknownSchemaWithoutExposingPlanner() {
        FakeGateway gateway = new FakeGateway();
        WarPlannerManager manager = manager(gateway);
        manager.tick(true, true);

        gateway.next.complete(snapshot(2, true));

        assertEquals(WarPlannerManager.State.OFFLINE, manager.state());
        assertFalse(manager.isAuthorized());
        assertNull(manager.snapshot());
        assertTrue(manager.lastError().contains("schema 2"));
    }

    @Test
    void incompatibleRefreshRevokesAnOlderAuthorizedSnapshot() {
        FakeGateway gateway = new FakeGateway();
        WarPlannerManager manager = manager(gateway);
        manager.tick(true, true);
        gateway.next.complete(snapshot(1, true));
        gateway.next = new CompletableFuture<>();

        manager.refreshNow();
        gateway.next.complete(snapshot(9, true));

        assertEquals(WarPlannerManager.State.OFFLINE, manager.state());
        assertNull(manager.snapshot());
        assertFalse(manager.isAuthorized());
    }

    @Test
    void forbiddenResponseClearsPreviouslyAuthorizedData() {
        FakeGateway gateway = new FakeGateway();
        WarPlannerManager manager = manager(gateway);
        manager.tick(true, true);
        gateway.next.complete(snapshot(1, true));
        gateway.next = new CompletableFuture<>();

        manager.refreshNow();
        gateway.next.completeExceptionally(new ApiClient.ApiException(
                403, "{\"code\":\"not_seq_member\",\"message\":\"Seq members only\"}"));

        assertEquals(WarPlannerManager.State.FORBIDDEN, manager.state());
        assertFalse(manager.isAuthorized());
        assertNull(manager.snapshot());
        assertEquals("Seq members only", manager.lastError());
    }

    @Test
    void countdownUsesBackendServerTimeOffset() {
        FakeGateway gateway = new FakeGateway();
        WarPlannerManager manager = manager(gateway);
        manager.tick(true, true);
        gateway.next.complete(snapshot(1, false));

        assertEquals(Duration.ofMinutes(30), manager.ownAvailabilityRemaining());
    }

    @Test
    void regularMemberCannotDispatchManagementMutation() {
        FakeGateway gateway = new FakeGateway();
        WarPlannerManager manager = manager(gateway);
        manager.tick(true, true);
        gateway.next.complete(snapshot(1, false));

        var result = manager.saveTeam(null, new TeamDraft(
                "Alpha", null, List.of(new TeamMemberDraft("self", WarTeamRole.WAR_LEADER)))).join();

        assertFalse(result.success());
        assertEquals(1, gateway.calls);
    }

    private static WarPlannerManager manager(FakeGateway gateway) {
        return new WarPlannerManager(gateway, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static WarPlannerSnapshot snapshot(int schema, boolean canManage) {
        Instant serverNow = NOW.plusSeconds(90);
        return new WarPlannerSnapshot(
                schema,
                serverNow,
                new WarPlannerSnapshot.Self("self", canManage),
                true,
                List.of(new WarPlannerSnapshot.RosterMember(
                        "self", "Player", "discord-id", "discord", List.of(WarCompositionRole.DPS),
                        true, true, serverNow.plus(Duration.ofMinutes(30)), null, null)),
                List.of(),
                List.of(),
                List.of("Ragni"));
    }

    private static final class FakeGateway implements WarPlannerManager.Gateway {
        private CompletableFuture<WarPlannerSnapshot> next = new CompletableFuture<>();
        private int calls;

        private CompletableFuture<WarPlannerSnapshot> call() {
            calls++;
            return next;
        }

        @Override public CompletableFuture<WarPlannerSnapshot> snapshot() { return call(); }
        @Override public CompletableFuture<WarPlannerSnapshot> setAvailability(int durationMinutes) { return call(); }
        @Override public CompletableFuture<WarPlannerSnapshot> clearAvailability() { return call(); }
        @Override public CompletableFuture<WarPlannerSnapshot> createTeam(TeamDraft draft) { return call(); }
        @Override public CompletableFuture<WarPlannerSnapshot> updateTeam(long id, TeamDraft draft) { return call(); }
        @Override public CompletableFuture<WarPlannerSnapshot> deleteTeam(long id) { return call(); }
        @Override public CompletableFuture<WarPlannerSnapshot> createZone(ZoneDraft draft) { return call(); }
        @Override public CompletableFuture<WarPlannerSnapshot> updateZone(long id, ZoneDraft draft) { return call(); }
        @Override public CompletableFuture<WarPlannerSnapshot> deleteZone(long id) { return call(); }
    }
}
