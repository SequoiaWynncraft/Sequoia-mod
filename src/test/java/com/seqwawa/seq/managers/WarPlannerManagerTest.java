package com.seqwawa.seq.managers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seqwawa.seq.model.war.WarPlannerDrafts.TeamDraft;
import com.seqwawa.seq.model.war.WarPlannerDrafts.TeamMemberDraft;
import com.seqwawa.seq.model.war.WarPlannerDrafts.TeamMemberMoveDraft;
import com.seqwawa.seq.model.war.WarPlannerDrafts.SupportDraft;
import com.seqwawa.seq.model.war.WarPlannerDrafts.ZoneDraft;
import com.seqwawa.seq.model.war.WarPlannerDrafts.ZoneCategoryDraft;
import com.seqwawa.seq.model.war.WarPlannerDrafts.ZonePlacementDraft;
import com.seqwawa.seq.model.war.WarCompositionRole;
import com.seqwawa.seq.model.war.WarPlannerSnapshot;
import com.seqwawa.seq.model.war.WarTeamType;
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

        WarPlannerSnapshot snapshot = snapshot(3, true);
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

        gateway.next.complete(snapshot(1, true));

        assertEquals(WarPlannerManager.State.OFFLINE, manager.state());
        assertFalse(manager.isAuthorized());
        assertNull(manager.snapshot());
        assertTrue(manager.lastError().contains("schema 1"));
    }

    @Test
    void incompatibleRefreshRevokesAnOlderAuthorizedSnapshot() {
        FakeGateway gateway = new FakeGateway();
        WarPlannerManager manager = manager(gateway);
        manager.tick(true, true);
        gateway.next.complete(snapshot(3, true));
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
        gateway.next.complete(snapshot(3, true));
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
        gateway.next.complete(snapshot(3, false));

        assertEquals(Duration.ofMinutes(30), manager.ownAvailabilityRemaining());
    }

    @Test
    void regularMemberCannotDispatchManagementMutation() {
        FakeGateway gateway = new FakeGateway();
        WarPlannerManager manager = manager(gateway);
        manager.tick(true, true);
        gateway.next.complete(snapshot(3, false));

        var result = manager.saveTeam(null, new TeamDraft(
                WarTeamType.VLOW_MUNCH, null, List.of(new TeamMemberDraft("self")))).join();

        assertFalse(result.success());
        assertEquals("war_manager_required", result.code());
        assertFalse(manager.saveSupport(new SupportDraft(1L, List.of())).join().success());
        assertEquals(1, gateway.calls);
    }

    @Test
    void regularMemberCanDispatchWarChatPing() {
        FakeGateway gateway = new FakeGateway();
        WarPlannerManager manager = manager(gateway);
        manager.tick(true, true);
        gateway.next.complete(snapshot(3, false));
        gateway.next = new CompletableFuture<>();

        CompletableFuture<WarPlannerManager.ActionResult> result = manager.pingPlayer("target");
        gateway.next.complete(snapshot(3, false));

        assertTrue(result.join().success());
        assertEquals(2, gateway.calls);
    }

    @Test
    void regularMemberCanDispatchOwnTeamMutation() {
        FakeGateway gateway = new FakeGateway();
        WarPlannerManager manager = manager(gateway);
        manager.tick(true, true);
        gateway.next.complete(snapshot(3, false));
        gateway.next = new CompletableFuture<>();

        CompletableFuture<WarPlannerManager.ActionResult> result = manager.joinTeam(7L);
        gateway.next.complete(snapshot(3, false));

        assertTrue(result.join().success());
        assertEquals("Joined war team.", result.join().message());
        assertEquals(2, gateway.calls);
    }

    @Test
    void regularMemberCanReplaceOwnDiscordCompositionRoles() {
        FakeGateway gateway = new FakeGateway();
        WarPlannerManager manager = manager(gateway);
        manager.tick(true, true);
        gateway.next.complete(snapshot(3, false));
        gateway.next = new CompletableFuture<>();

        CompletableFuture<WarPlannerManager.ActionResult> result =
                manager.updateCompositionRoles(List.of(WarCompositionRole.SOLO, WarCompositionRole.TANK));
        gateway.next.complete(snapshot(3, false));

        assertTrue(result.join().success());
        assertEquals("Discord war roles updated.", result.join().message());
        assertEquals(2, gateway.calls);
    }

    @Test
    void managerCanReplaceTheSharedHqTerritory() {
        FakeGateway gateway = new FakeGateway();
        WarPlannerManager manager = manager(gateway);
        manager.tick(true, true);
        gateway.next.complete(snapshot(3, true));
        gateway.next = new CompletableFuture<>();

        CompletableFuture<WarPlannerManager.ActionResult> result = manager.setHqTerritory("Detlas", 42L);
        gateway.next.complete(snapshot(3, true));

        assertTrue(result.join().success());
        assertEquals("Detlas", gateway.hqTerritory);
        assertEquals(42L, gateway.hqVersion);
    }

    @Test
    void managerRequiredMutationDowngradesCachedSnapshotToViewOnly() {
        FakeGateway gateway = new FakeGateway();
        WarPlannerManager manager = manager(gateway);
        manager.tick(true, true);
        gateway.next.complete(snapshot(3, true));
        gateway.next = new CompletableFuture<>();

        CompletableFuture<WarPlannerManager.ActionResult> result = manager.deleteTeam(7L, 3L);
        gateway.next.completeExceptionally(new ApiClient.ApiException(
                403,
                "{\"code\":\"war_manager_required\",\"message\":\"Managers only\"}"));

        assertFalse(result.join().success());
        assertEquals("war_manager_required", result.join().code());
        assertEquals(WarPlannerManager.State.READY, manager.state());
        assertTrue(manager.isAuthorized());
        assertFalse(manager.canManage());
        assertEquals("self", manager.snapshot().self().playerUuid());
    }

    @Test
    void expectedMutationErrorsRetainReadySnapshot() {
        for (int status : List.of(400, 404, 409, 422, 429)) {
            FakeGateway gateway = new FakeGateway();
            WarPlannerManager manager = manager(gateway);
            manager.tick(true, true);
            WarPlannerSnapshot cached = snapshot(3, true);
            gateway.next.complete(cached);
            gateway.next = new CompletableFuture<>();

            CompletableFuture<WarPlannerManager.ActionResult> result = manager.setAvailability(30);
            gateway.next.completeExceptionally(new ApiClient.ApiException(
                    status,
                    "{\"code\":\"expected_mutation_error\",\"message\":\"Try again\"}"));

            assertFalse(result.join().success(), "status " + status);
            assertEquals("expected_mutation_error", result.join().code());
            assertEquals(WarPlannerManager.State.READY, manager.state(), "status " + status);
            assertSame(cached, manager.snapshot(), "status " + status);
        }
    }

    @Test
    void authenticationGuildAndUpgradeErrorsClearSensitiveSnapshot() {
        Object[][] failures = {
            {401, "token_invalid"},
            {422, "not_in_guild"},
            {426, "upgrade_required"}
        };
        for (Object[] failure : failures) {
            FakeGateway gateway = new FakeGateway();
            WarPlannerManager manager = manager(gateway);
            manager.tick(true, true);
            gateway.next.complete(snapshot(3, true));
            gateway.next = new CompletableFuture<>();

            CompletableFuture<WarPlannerManager.ActionResult> result = manager.setAvailability(30);
            int status = (int) failure[0];
            String code = (String) failure[1];
            gateway.next.completeExceptionally(new ApiClient.ApiException(
                    status,
                    "{\"code\":\"" + code + "\",\"message\":\"Access changed\"}"));

            assertFalse(result.join().success());
            assertEquals(code, result.join().code());
            assertEquals(WarPlannerManager.State.FORBIDDEN, manager.state());
            assertNull(manager.snapshot());
            assertFalse(manager.isAuthorized());
        }
    }

    @Test
    void staleGuildAuthorizationDependencyClearsSensitiveSnapshotButUsesOfflineRetry() {
        FakeGateway gateway = new FakeGateway();
        WarPlannerManager manager = manager(gateway);
        manager.tick(true, true);
        gateway.next.complete(snapshot(3, true));
        gateway.next = new CompletableFuture<>();

        CompletableFuture<WarPlannerManager.ActionResult> result = manager.refreshNow();
        gateway.next.completeExceptionally(new ApiClient.ApiException(
                503,
                "{\"code\":\"guild_roster_unavailable\",\"message\":\"Roster unavailable\",\"retryable\":true}"));

        assertFalse(result.join().success());
        assertEquals("guild_roster_unavailable", result.join().code());
        assertEquals(WarPlannerManager.State.OFFLINE, manager.state());
        assertNull(manager.snapshot());
        assertFalse(manager.isAuthorized());
    }

    @Test
    void moveAndDeleteDispatchCapturedVersionsThroughGateway() {
        FakeGateway gateway = new FakeGateway();
        WarPlannerManager manager = manager(gateway);
        manager.tick(true, true);
        gateway.next.complete(snapshot(3, true));
        gateway.next = new CompletableFuture<>();
        TeamMemberMoveDraft move = new TeamMemberMoveDraft(1L, 2L, 3L, 4L);

        CompletableFuture<WarPlannerManager.ActionResult> moveResult = manager.moveTeamMember("player", move);
        assertEquals("player", gateway.movedPlayerUuid);
        assertSame(move, gateway.moveDraft);
        gateway.next.complete(snapshot(3, true));
        assertTrue(moveResult.join().success());

        gateway.next = new CompletableFuture<>();
        CompletableFuture<WarPlannerManager.ActionResult> deleteResult = manager.deleteZone(9L, 6L);
        assertEquals(6L, gateway.deleteVersion);
        gateway.next.complete(snapshot(3, true));
        assertTrue(deleteResult.join().success());
    }

    @Test
    void parsesBackendErrorCodeSeparatelyFromItsMessage() {
        WarPlannerManager.ApiErrorDetails details = WarPlannerManager.apiError(new ApiClient.ApiException(
                409,
                "{\"code\":\"stale_version\",\"message\":\"Reload this team\"}"));

        assertEquals("stale_version", details.code());
        assertEquals("Reload this team", details.message());
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
                        true, true, serverNow.plus(Duration.ofMinutes(30)), null)),
                List.of(),
                new WarPlannerSnapshot.SupportBoard(1L, List.of()),
                List.of(),
                List.of("Ragni"),
                List.of());
    }

    private static final class FakeGateway implements WarPlannerManager.Gateway {
        private CompletableFuture<WarPlannerSnapshot> next = new CompletableFuture<>();
        private int calls;
        private String movedPlayerUuid;
        private TeamMemberMoveDraft moveDraft;
        private long deleteVersion;
        private String hqTerritory;
        private long hqVersion;

        private CompletableFuture<WarPlannerSnapshot> call() {
            calls++;
            return next;
        }

        @Override public CompletableFuture<WarPlannerSnapshot> snapshot() { return call(); }
        @Override public CompletableFuture<WarPlannerSnapshot> setAvailability(int durationMinutes) { return call(); }
        @Override public CompletableFuture<WarPlannerSnapshot> clearAvailability() { return call(); }
        @Override public CompletableFuture<WarPlannerSnapshot> updateCompositionRoles(List<WarCompositionRole> roles) { return call(); }
        @Override public CompletableFuture<WarPlannerSnapshot> pingPlayer(String playerUuid) { return call(); }
        @Override public CompletableFuture<WarPlannerSnapshot> createTeam(TeamDraft draft) { return call(); }
        @Override public CompletableFuture<WarPlannerSnapshot> updateTeam(long id, TeamDraft draft) { return call(); }
        @Override public CompletableFuture<WarPlannerSnapshot> deleteTeam(long id, long version) {
            deleteVersion = version;
            return call();
        }
        @Override public CompletableFuture<WarPlannerSnapshot> moveTeamMember(
                String playerUuid, TeamMemberMoveDraft draft) {
            movedPlayerUuid = playerUuid;
            moveDraft = draft;
            return call();
        }
        @Override public CompletableFuture<WarPlannerSnapshot> joinTeam(long id) { return call(); }
        @Override public CompletableFuture<WarPlannerSnapshot> leaveTeam() { return call(); }
        @Override public CompletableFuture<WarPlannerSnapshot> updateSupport(com.seqwawa.seq.model.war.WarPlannerDrafts.SupportDraft draft) { return call(); }
        @Override public CompletableFuture<WarPlannerSnapshot> createZone(ZoneDraft draft) { return call(); }
        @Override public CompletableFuture<WarPlannerSnapshot> updateZone(long id, ZoneDraft draft) { return call(); }
        @Override public CompletableFuture<WarPlannerSnapshot> deleteZone(long id, long version) {
            deleteVersion = version;
            return call();
        }
        @Override public CompletableFuture<WarPlannerSnapshot> moveZone(long id, ZonePlacementDraft draft) {
            return call();
        }
        @Override public CompletableFuture<WarPlannerSnapshot> createZoneCategory(ZoneCategoryDraft draft) {
            return call();
        }
        @Override public CompletableFuture<WarPlannerSnapshot> updateZoneCategory(long id, ZoneCategoryDraft draft) {
            return call();
        }
        @Override public CompletableFuture<WarPlannerSnapshot> deleteZoneCategory(long id, long version) {
            deleteVersion = version;
            return call();
        }
        @Override public CompletableFuture<WarPlannerSnapshot> setHqTerritory(String territory, long version) {
            hqTerritory = territory;
            hqVersion = version;
            return call();
        }
    }
}
