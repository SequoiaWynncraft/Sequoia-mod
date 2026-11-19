package com.seqwawa.seq.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seqwawa.seq.managers.GuildRankEventParser;
import com.seqwawa.seq.model.BombShareType;
import com.seqwawa.seq.model.GuildWarQueueCancellation;
import com.seqwawa.seq.model.GuildWarQueueSubmission;
import com.seqwawa.seq.model.GuildWarSubmission;
import com.seqwawa.seq.model.WarStatusUpdate;
import com.seqwawa.seq.model.WarTowerUpdate;
import com.seqwawa.seq.model.WynnClassType;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Pins which outbound messages each state of the connection lets through: every send method,
 * from no socket at all to a confirmed member, a non-member, and a member who left the main
 * server.
 */
class ConnectionGatingTest {

    private static final String SUBMITTED_AT = "2026-11-18T12:00:00Z";
    private static final String SUBMITTER = "a9e1132c-c13f-4fd4-abea-812ebf35d5c3";
    private static final Set<String> PARTY_TRAFFIC =
            Set.of("party_class_update", "party_sync_snapshot", "party_sync_member_removed");

    @TempDir
    Path config;

    private ConnectionHarness harness;

    @BeforeEach
    void start() {
        harness = new ConnectionHarness(config);
    }

    @AfterEach
    void stop() {
        harness.close();
    }

    enum State {
        NOT_CONNECTED,
        OPEN_NOT_SIGNED_IN,
        /** Signed in; the membership probe hasn't been answered. */
        SIGNED_IN_PROBING,
        MEMBER,
        NON_MEMBER,
        /** A member whose game moved off the main server, as a transfer does for a moment. */
        OFF_MAIN_SERVER
    }

    /** One way the mod sends a message, and the type that goes out when it does. */
    private record Send(String type, Consumer<ConnectionManager> call) {
        @Override
        public String toString() {
            return type;
        }
    }

    private static final List<Send> SENDS = List.of(
            new Send("get_connected", cm -> cm.requestConnectedUsers(users -> {})),
            new Send("bomb_share_request", cm -> cm.sendBombShareRequest("dxp", List.of(BombShareType.COMBAT_XP))),
            new Send("bomb_share_submit", cm -> cm.sendBombShareSubmit("request-1", List.of("WC1"))),
            new Send("guild_chat", cm -> cm.sendGuildChat("Sequoian", null, "hello", null)),
            new Send("guild_alliance_update", cm -> cm.sendGuildAllianceUpdate("formed", "Avicia")),
            new Send("guild_alliance_snapshot", cm -> cm.sendGuildAllianceSnapshot(List.of("Avicia"))),
            new Send("guild_membership_event", cm -> cm.sendGuildMembershipEvent("invited", "GaztheCat", "NewMember")),
            new Send("guild_raid_announcement", cm -> cm.sendRaidAnnouncement(
                    List.of("Sequoian"), "The Nameless Anomaly", 2, 2048, 10.0, 220, Map.of())),
            new Send("guild_bank_event", cm -> cm.sendGuildBankEvent(
                    "deposited", "Sequoian", 1, "Potion of Healing", null, "Everyone", "Sequoian deposited 1x Potion")),
            new Send("guild_storage_snapshot", cm -> cm.sendGuildStorageSnapshot(1, 10, 1, 10)),
            new Send("guild_storage_reward", cm -> cm.sendGuildStorageReward(
                    "Sequoian", "Recruit", "emeralds", 10, 1, Instant.parse(SUBMITTED_AT))),
            new Send("guild_war_queue", cm -> cm.sendGuildWarQueue(
                    new GuildWarQueueSubmission("Olux", SUBMITTER, SUBMITTED_AT, "Low", 5))),
            new Send("guild_war_queue_cancel", cm -> cm.sendGuildWarQueueCancellation(
                    new GuildWarQueueCancellation("Olux", SUBMITTER, SUBMITTED_AT))),
            new Send("guild_war_submission", cm -> cm.sendGuildWarSubmission(warSubmission())),
            new Send("war_status", cm -> cm.sendWarStatus(WarStatusUpdate.remove())),
            new Send("war_tower_update", cm -> cm.sendWarTowerUpdate(new WarTowerUpdate("Olux", 0.5f, 1L, 1L))),
            new Send("guild_rank_event", cm -> ConnectionManager.observeGuildRankEvent(rankEvent())),
            new Send("party_class_update", cm -> cm.sendPartyClassUpdate(WynnClassType.MAGE)),
            new Send("party_sync_snapshot", cm -> cm.sendPartySyncSnapshot(1L, true, "Sequoian", List.of("Sequoian"))),
            new Send("party_sync_member_removed", cm -> cm.sendPartySyncMemberRemoved("Recruit", "left")));

    private static GuildWarSubmission warSubmission() {
        return new GuildWarSubmission("Olux", SUBMITTER, SUBMITTED_AT, SUBMITTED_AT, List.of("Sequoian"),
                new GuildWarSubmission.TowerStats(1, 2, 1.0, 100, 0.1), 0, SUBMITTED_AT);
    }

    private static GuildRankEventParser.Event rankEvent() {
        return new GuildRankEventParser.Event(
                new GuildRankEventParser.Identity("GaztheCat", "GaztheCat", "chat"),
                new GuildRankEventParser.Identity("Recruit", "Recruit", "chat"),
                "Recruit",
                "Recruiter",
                "GaztheCat has set Recruit guild rank from Recruit to Recruiter");
    }

    private static boolean expectedToSend(Send send, State state) {
        return switch (state) {
            case NOT_CONNECTED, OPEN_NOT_SIGNED_IN, OFF_MAIN_SERVER -> false;
            // Membership isn't known yet, so member traffic goes; only rank observations wait for the answer.
            case SIGNED_IN_PROBING -> !send.type().equals("guild_rank_event");
            case MEMBER -> true;
            case NON_MEMBER -> PARTY_TRAFFIC.contains(send.type());
        };
    }

    private static Stream<Arguments> everySendInEveryState() {
        return SENDS.stream().flatMap(send -> Stream.of(State.values())
                .map(state -> Arguments.of(send, state, expectedToSend(send, state))));
    }

    private void reach(State state) {
        switch (state) {
            case NOT_CONNECTED -> harness.connection();
            case OPEN_NOT_SIGNED_IN -> {
                harness.connectAutomatically();
                harness.open();
            }
            case SIGNED_IN_PROBING -> {
                harness.connectAutomatically();
                harness.open();
                harness.signIn();
            }
            case MEMBER -> harness.connectAsMember();
            case NON_MEMBER -> {
                harness.connectAutomatically();
                harness.open();
                harness.signIn();
                harness.rejectMembership();
            }
            case OFF_MAIN_SERVER -> {
                harness.connectAsMember();
                harness.scope = WynncraftServerPolicy.Scope.UNKNOWN;
            }
        }
        harness.clearSent();
        // Past the send throttle anything before this used.
        harness.advance(50);
    }

    @ParameterizedTest(name = "{0} when {1}: sent={2}")
    @MethodSource("everySendInEveryState")
    void eachMessageGoesOnlyWhenTheConnectionIsReadyForIt(Send send, State state, boolean sent) {
        reach(state);

        send.call().accept(harness.connection());
        harness.settle();

        assertEquals(sent, harness.sentTypes().contains(send.type()), harness.sentTypes().toString());
    }

    // ── What callers are told ──

    @Test
    void sendsThatReportDeliveryAnswerWhetherTheSocketTookThem() {
        harness.connectAsMember();
        harness.advance(50);

        assertTrue(harness.connection().sendGuildChat("Sequoian", null, "hello", null, List.of(), false));
        harness.advance(50);
        assertTrue(harness.connection().sendBombShareRequest("dxp", List.of(BombShareType.COMBAT_XP)));
        harness.advance(50);
        assertTrue(harness.connection().sendPartySyncSnapshot(1L, true, "Sequoian", List.of("Sequoian")));
        assertTrue(harness.connection().sendWarStatus(WarStatusUpdate.remove()));

        harness.connection().disconnect();
        assertFalse(harness.connection().sendGuildChat("Sequoian", null, "hello", null, List.of(), false));
        assertFalse(harness.connection().sendBombShareRequest("dxp", List.of(BombShareType.COMBAT_XP)));
        assertFalse(harness.connection().sendPartySyncSnapshot(1L, true, "Sequoian", List.of("Sequoian")));
        assertFalse(harness.connection().sendWarStatus(WarStatusUpdate.remove()));
    }

    @Test
    void sendsRacingTheBackendsCloseReportThatNothingWent() {
        FakeBackendSocket socket = harness.connectAsMember();
        harness.advance(50);
        // The backend has started closing; the game thread hasn't heard yet, so the sign-in still stands.
        socket.startClosingFromBackend();

        // Fixed in step 6: these three didn't check the socket, and threw once it closed.
        assertFalse(harness.connection().sendGuildAllianceUpdate("formed", "Avicia"));
        assertFalse(harness.connection().sendGuildMembershipEvent("invited", "GaztheCat", "NewMember"));
        assertFalse(harness.connection().sendGuildAllianceSnapshot(List.of("Avicia")));
        assertFalse(harness.connection().sendGuildChat("Sequoian", null, "hello", null, List.of(), false));
        assertFalse(harness.connection().sendWarStatus(WarStatusUpdate.remove()));
        assertEquals(List.of("get_connected"), socket.sentTypes());
    }

    @Test
    void warEventsAreAcceptedWhileDisconnectedAndSentInOrderOncePerFlush() {
        ConnectionManager cm = harness.connection();
        assertTrue(cm.sendGuildWarQueue(new GuildWarQueueSubmission("Olux", SUBMITTER, SUBMITTED_AT, "Low", 5)));
        assertTrue(cm.sendGuildWarQueueCancellation(new GuildWarQueueCancellation("Olux", SUBMITTER, SUBMITTED_AT)));
        assertTrue(cm.sendGuildWarSubmission(warSubmission()));

        harness.connectAutomatically();
        FakeBackendSocket socket = harness.open();
        harness.signIn();
        assertEquals(List.of("get_connected", "guild_war_queue"), socket.sentTypes());

        ConnectionManager.flushPendingOutbound();
        assertEquals(2, socket.sentTypes().size(), "the queue send still holds the throttle");
        harness.advance(50);
        ConnectionManager.flushPendingOutbound();
        harness.advance(50);
        ConnectionManager.flushPendingOutbound();
        ConnectionManager.flushPendingOutbound();

        assertEquals(List.of("get_connected", "guild_war_queue", "guild_war_queue_cancel", "guild_war_submission"),
                socket.sentTypes());
    }

    @Test
    void warEventsAreRefusedOnABlockedServer() {
        harness.connectAsMember();
        harness.scope = WynncraftServerPolicy.Scope.BLOCKED;

        assertFalse(harness.connection().sendGuildWarQueue(
                new GuildWarQueueSubmission("Olux", SUBMITTER, SUBMITTED_AT, "Low", 5)));
        assertFalse(harness.connection().sendGuildWarSubmission(warSubmission()));
    }

    @Test
    void warEventsWaitOutAServerTransfer() {
        FakeBackendSocket socket = harness.connectAsMember();
        harness.scope = WynncraftServerPolicy.Scope.UNKNOWN;
        harness.advance(50);
        assertTrue(harness.connection().sendGuildWarQueue(
                new GuildWarQueueSubmission("Olux", SUBMITTER, SUBMITTED_AT, "Low", 5)));
        assertFalse(socket.sentTypes().contains("guild_war_queue"));

        harness.scope = WynncraftServerPolicy.Scope.MAIN;
        ConnectionManager.flushPendingOutbound();

        assertTrue(socket.sentTypes().contains("guild_war_queue"));
    }

    @Test
    void privilegedSendsShareAFiftyMillisecondThrottle() {
        FakeBackendSocket socket = harness.connectAsMember();
        harness.advance(50);

        assertTrue(harness.connection().sendGuildChat("Sequoian", null, "one", null, List.of(), false));
        assertFalse(harness.connection().sendGuildChat("Sequoian", null, "two", null, List.of(), false));
        assertFalse(harness.connection().sendPartySyncMemberRemoved("Recruit", "left"), "throttled across types");
        harness.advance(49);
        assertFalse(harness.connection().sendGuildChat("Sequoian", null, "three", null, List.of(), false));
        harness.advance(1);
        assertTrue(harness.connection().sendGuildChat("Sequoian", null, "four", null, List.of(), false));
        assertEquals(List.of("guild_chat", "guild_chat"), socket.sentTypes().subList(1, 3));
    }

    @Test
    void snapshotsAndLiveTelemetryAreNotThrottled() {
        FakeBackendSocket socket = harness.connectAsMember();
        socket.clearSent();

        harness.connection().sendGuildStorageSnapshot(1, 10, 1, 10);
        harness.connection().sendGuildStorageSnapshot(2, 10, 1, 10);
        harness.connection().sendWarStatus(WarStatusUpdate.remove());
        harness.connection().sendWarStatus(WarStatusUpdate.remove());

        assertEquals(List.of("guild_storage_snapshot", "guild_storage_snapshot", "war_status", "war_status"),
                socket.sentTypes());
    }

    @Test
    void connectedUsersOffTheMainServerAreAnsweredWithNoOneAtOnce() {
        harness.connectAsMember();
        harness.scope = WynncraftServerPolicy.Scope.UNKNOWN;
        List<List<String>> answers = new ArrayList<>();

        harness.connection().requestConnectedUsers(answers::add);

        assertEquals(List.of(List.of()), answers);
    }

    @Test
    void anOverlongGuildChatMessageIsRefusedAndTheSenderToldUnlessItWasForDisplay() {
        harness.connectAsMember();
        String tooLong = "x".repeat(401);

        assertFalse(harness.connection().sendGuildChat("Sequoian", null, tooLong, null, List.of(), false));
        assertFalse(harness.connection().sendGuildChat("Sequoian", null, tooLong, null, List.of(), true));

        assertEquals(List.of("Guild chat message too long."), harness.told);
    }

    @Test
    void invalidPayloadsAreRefusedEvenWhenReady() {
        FakeBackendSocket socket = harness.connectAsMember();
        socket.clearSent();
        ConnectionManager cm = harness.connection();

        assertFalse(cm.sendBombShareRequest(" ", List.of(BombShareType.LOOT)));
        assertFalse(cm.sendBombShareSubmit("request-1", List.of()));
        assertFalse(cm.sendGuildChat("Sequoian", null, "   ", null, List.of(), false));
        assertFalse(cm.sendGuildAllianceUpdate("merged", "Avicia"));
        assertFalse(cm.sendGuildMembershipEvent("invited", "not a name!", "NewMember"));
        assertFalse(cm.sendGuildAllianceSnapshot(List.of("Bad: Name")));
        assertFalse(cm.sendPartySyncMemberRemoved("Recruit", " "));
        assertFalse(cm.sendWarTowerUpdate(null));
        assertFalse(cm.sendWarStatus(null));
        assertFalse(cm.sendGuildWarSubmission(new GuildWarSubmission("Olux", SUBMITTER, SUBMITTED_AT, SUBMITTED_AT,
                List.of("not a name!"), new GuildWarSubmission.TowerStats(1, 2, 1.0, 100, 0.1), 0, null)));
        assertFalse(cm.sendGuildWarQueue(new GuildWarQueueSubmission("Olux", SUBMITTER, SUBMITTED_AT, "Low", 61)));
        cm.sendGuildStorageSnapshot(-1, 10, 1, 10);
        cm.sendGuildStorageReward("Sequoian", "Recruit", "gold", 10, 1, Instant.parse(SUBMITTED_AT));
        cm.sendGuildBankEvent("deposited", "Sequoian", 1, " ", null, "Everyone", "raw");
        cm.sendRaidAnnouncement(List.of(), "The Nameless Anomaly", 2, 2048, 10.0, 220, Map.of());
        cm.sendPartyClassUpdate(null);

        assertEquals(List.of(), socket.sentTypes());
    }

    @Test
    void rankObservationsWaitForTheMembershipAnswerThenGo() {
        harness.connectAutomatically();
        FakeBackendSocket socket = harness.open();
        harness.signIn();
        harness.advance(50);
        ConnectionManager.observeGuildRankEvent(rankEvent());
        assertFalse(socket.sentTypes().contains("guild_rank_event"));

        harness.confirmMembership(ConnectionHarness.MEMBER);
        ConnectionManager.tickGuildRankObservations();

        assertTrue(socket.sentTypes().contains("guild_rank_event"));
    }

    @Test
    void rankObservationsWaitForABackendThatTracksRanks() {
        harness.connectAutomatically();
        FakeBackendSocket socket = harness.open();
        harness.receive("{\"type\":\"authenticated\"}");
        harness.confirmMembership(ConnectionHarness.MEMBER);
        harness.advance(50);

        ConnectionManager.observeGuildRankEvent(rankEvent());

        assertFalse(socket.sentTypes().contains("guild_rank_event"));
    }
}
