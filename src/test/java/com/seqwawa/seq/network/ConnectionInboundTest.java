package com.seqwawa.seq.network;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seqwawa.seq.managers.GuildRankEventParser;
import com.seqwawa.seq.model.BombShareType;
import com.seqwawa.seq.network.auth.AuthErrorCode;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Pins what every message type the backend sends does when it arrives on a signed-in member's socket. */
class ConnectionInboundTest {

    @TempDir
    Path config;

    private ConnectionHarness harness;
    private FakeBackendSocket socket;

    @BeforeEach
    void start() {
        harness = new ConnectionHarness(config);
    }

    @AfterEach
    void stop() {
        harness.close();
    }

    private void connect() {
        socket = harness.connectAsMember();
        socket.clearSent();
        harness.advance(50);
    }

    private void error(String fields) {
        harness.receive("{\"type\":\"error\"," + fields + "}");
    }

    // ── Membership and presence ──

    @Test
    void connectedUsersAnswersEveryoneWaitingAtOnce() {
        connect();
        List<List<String>> first = new ArrayList<>();
        List<List<String>> second = new ArrayList<>();
        harness.connection().requestConnectedUsers(first::add);
        harness.connection().requestConnectedUsers(second::add);
        assertEquals(List.of("get_connected"), socket.sentTypes(), "the second request was throttled, yet waits too");

        harness.confirmMembership("Sequoian", "Recruit");

        assertEquals(List.of(List.of("Sequoian", "Recruit")), first);
        assertEquals(List.of(List.of("Sequoian", "Recruit")), second);
    }

    @Test
    void aLaterConnectedUsersReachesNobodyAlreadyAnswered() {
        connect();
        List<List<String>> answers = new ArrayList<>();
        harness.connection().requestConnectedUsers(answers::add);
        harness.confirmMembership("Sequoian");

        harness.confirmMembership("Sequoian", "Recruit");

        assertEquals(1, answers.size());
    }

    @Test
    void connectedUsersAfterANonMemberRejectionTurnsMemberFeaturesBackOn() {
        harness.connectAutomatically();
        harness.open();
        harness.signIn();
        harness.rejectMembership();
        assertFalse(ConnectionManager.isLiveWarTelemetryReady());

        harness.confirmMembership("Sequoian");

        assertTrue(ConnectionManager.isLiveWarTelemetryReady());
    }

    @Test
    void aRecordedRankObservationIsNotSentAgain() {
        connect();
        ConnectionManager.observeGuildRankEvent(new GuildRankEventParser.Event(
                new GuildRankEventParser.Identity("GaztheCat", "GaztheCat", "chat"),
                new GuildRankEventParser.Identity("Recruit", "Recruit", "chat"),
                "Recruit", "Recruiter", "GaztheCat promoted Recruit"));
        String id = socket.lastSent("guild_rank_event").get("observation_id").getAsString();

        harness.advance(5_000);
        ConnectionManager.tickGuildRankObservations();
        assertEquals(2, socket.sentTypes().size(), "unrecorded, it is sent again after five seconds");

        harness.receive("{\"type\":\"guild_rank_recorded\",\"observation_id\":\"" + id + "\"}");
        harness.advance(5_000);
        ConnectionManager.tickGuildRankObservations();
        assertEquals(2, socket.sentTypes().size());
    }

    // ── Bomb shares ──

    @Test
    void aBombSharePromptReachesItsHandlerAndIsKeptUntilItsResult() {
        List<ConnectionManager.BombSharePromptMessage> prompts = new ArrayList<>();
        List<ConnectionManager.BombShareResultMessage> results = new ArrayList<>();
        ConnectionManager.onBombSharePrompt(prompts::add);
        ConnectionManager.onBombShareResult(results::add);
        connect();

        harness.receive("{\"type\":\"bomb_share_prompt\",\"request_id\":\"r1\",\"requester_username\":\"Recruit\","
                + "\"canonical_key\":\"dxp\",\"requested_types\":[\"combat_xp\",\"COMBAT_XP\",\"nonsense\",\"loot\"],"
                + "\"expires_at\":\"2026-11-18T12:05:00Z\",\"first_prompt\":true}");

        assertEquals(List.of(new ConnectionManager.BombSharePromptMessage("r1", "Recruit", "dxp",
                List.of(BombShareType.COMBAT_XP, BombShareType.LOOT), Instant.parse("2026-11-18T12:05:00Z"), true)),
                prompts);
        assertTrue(harness.connection().hasPendingBombSharePrompt("r1"));

        harness.receive("{\"type\":\"bomb_share_result\",\"request_id\":\"r1\",\"canonical_key\":\"dxp\","
                + "\"requested_types\":[\"combat_xp\"],\"worlds\":[\" WC1 \",\"\",\"WC2\"],\"share_count\":2}");

        assertEquals(List.of(new ConnectionManager.BombShareResultMessage(
                "r1", "dxp", List.of(BombShareType.COMBAT_XP), List.of("WC1", "WC2"), 2)), results);
        assertFalse(harness.connection().hasPendingBombSharePrompt("r1"));
    }

    @Test
    void aBombSharePromptIsKeptEvenWithoutAHandler() {
        connect();

        harness.receive("{\"type\":\"bomb_share_prompt\",\"request_id\":\"r2\",\"requested_types\":[]}");

        assertTrue(harness.connection().getPendingBombSharePrompt("r2").isPresent());
        assertTrue(harness.connection().removePendingBombSharePrompt("r2").isPresent());
        assertFalse(harness.connection().hasPendingBombSharePrompt("r2"));
    }

    @Test
    void anAccountChangeForgetsPendingBombSharePrompts() {
        connect();
        harness.receive("{\"type\":\"bomb_share_prompt\",\"request_id\":\"r3\",\"requested_types\":[]}");
        ConnectionManager before = harness.connection();

        ConnectionManager.resetForAccountChange();

        assertFalse(before.hasPendingBombSharePrompt("r3"));
    }

    // ── Guild storage ──

    @Test
    void aGuildStorageSnapshotIsAppliedOnTheGameThread() {
        connect();

        socket.receive("{\"type\":\"guild_storage_snapshot\",\"emerald_current\":10,\"emerald_max\":20,"
                + "\"aspect_current\":3,\"aspect_max\":4}");
        assertEquals(List.of(), harness.game.storageSnapshots, "handed over, not applied within the socket's call");
        harness.settle();

        assertEquals(1, harness.game.storageSnapshots.size());
        assertArrayEquals(new long[] {10, 20, 3, 4}, harness.game.storageSnapshots.getFirst());
    }

    // ── Guild chat ──

    @Test
    void displayableGuildChatIsDeliveredOnTheGameThread() {
        List<ConnectionManager.GuildChatMessage> received = new ArrayList<>();
        ConnectionManager.onGuildChat(received::add);
        connect();

        socket.receive("{\"type\":\"guild_chat\",\"username\":\"Recruit\",\"nickname\":\"Rec\",\"message\":\"hi\","
                + "\"should_be_displayed\":true}");
        assertEquals(List.of(), received, "handed over, not delivered within the socket's call");
        harness.settle();

        assertEquals(List.of(new ConnectionManager.GuildChatMessage("Recruit", "Rec", "hi")), received);
    }

    @Test
    void guildChatNotMeantForDisplayIsShownOnlyInTheServerQueue() {
        List<ConnectionManager.GuildChatMessage> received = new ArrayList<>();
        ConnectionManager.onGuildChat(received::add);
        connect();
        String echo = "{\"type\":\"guild_chat\",\"username\":\"Recruit\",\"message\":\"hi\",\"should_be_displayed\":false}";

        harness.receive(echo);
        assertEquals(List.of(), received);

        harness.game.inServerQueue = true;
        harness.receive(echo);
        assertEquals(1, received.size());
    }

    @Test
    void guildChatFromAnEndedServerConnectionIsDropped() {
        List<ConnectionManager.GuildChatMessage> received = new ArrayList<>();
        ConnectionManager.onGuildChat(received::add);
        connect();

        socket.receive("{\"type\":\"guild_chat\",\"username\":\"Recruit\",\"message\":\"hi\",\"should_be_displayed\":true}");
        harness.game.serverConnection = new Object();
        harness.settle();

        assertEquals(List.of(), received);
    }

    @Test
    void guildChatForAnAccountThatChangedIsDropped() {
        List<ConnectionManager.GuildChatMessage> received = new ArrayList<>();
        ConnectionManager.onGuildChat(received::add);
        connect();

        socket.receive("{\"type\":\"guild_chat\",\"username\":\"Recruit\",\"message\":\"hi\",\"should_be_displayed\":true}");
        harness.game.account = new Object();
        harness.settle();

        assertEquals(List.of(), received);
    }

    @Test
    void guildChatIsDroppedWhenTheSocketClosesBeforeDelivery() {
        List<ConnectionManager.GuildChatMessage> received = new ArrayList<>();
        ConnectionManager.onGuildChat(received::add);
        connect();

        socket.receive("{\"type\":\"guild_chat\",\"username\":\"Recruit\",\"message\":\"hi\",\"should_be_displayed\":true}");
        socket.closeFromBackend(1006, "");
        harness.settle();

        assertEquals(List.of(), received);
    }

    @Test
    void guildChatWithoutAServerConnectionIsDropped() {
        List<ConnectionManager.GuildChatMessage> received = new ArrayList<>();
        ConnectionManager.onGuildChat(received::add);
        connect();
        harness.game.serverConnection = null;

        harness.receive("{\"type\":\"guild_chat\",\"username\":\"Recruit\",\"message\":\"hi\",\"should_be_displayed\":true}");

        assertEquals(List.of(), received);
    }

    // ── Discord bridge ──

    @Test
    void discordChatReachesItsHandlerWithTheSendersDiscordId() {
        List<ConnectionManager.DiscordChatMessage> received = new ArrayList<>();
        ConnectionManager.onDiscordChat(received::add);
        connect();

        harness.receive("{\"type\":\"discord_chat\",\"username\":\"[Mod] Someone\",\"message\":\"hello\",\"discord_id\":\"42\"}");
        harness.receive("{\"type\":\"discord_chat\",\"username\":\"Legacy\",\"message\":\"hi\"}");

        assertEquals(List.of(
                new ConnectionManager.DiscordChatMessage("[Mod] Someone", "hello", "42"),
                new ConnectionManager.DiscordChatMessage("Legacy", "hi", null)), received);
    }

    @Test
    void discordChatFromAnIgnoredBridgeUserIsDropped() {
        List<ConnectionManager.DiscordChatMessage> received = new ArrayList<>();
        ConnectionManager.onDiscordChat(received::add);
        harness.config.addIgnoredBridgeUser("Pest");
        connect();

        harness.receive("{\"type\":\"discord_chat\",\"username\":\"Guild | pest\",\"message\":\"spam\"}");

        assertEquals(List.of(), received);
    }

    // ── Party finder and raid profiles ──

    @Test
    void partyFinderUpdatesReachTheirHandler() {
        List<ConnectionManager.PartyFinderUpdateMessage> received = new ArrayList<>();
        ConnectionManager.onPartyFinderUpdate(received::add);
        connect();

        harness.receive("{\"type\":\"party_finder_update\",\"action\":\"created\",\"listing\":{\"id\":7}}");
        harness.receive("{\"type\":\"party_finder_update\",\"listing\":{\"id\":8}}");

        assertEquals(2, received.size());
        assertEquals("created", received.get(0).action());
        assertEquals(7, received.get(0).listingJson().get("id").getAsInt());
        assertEquals("unknown", received.get(1).action());
    }

    @Test
    void raidProfileUpdatesReachTheirHandlerOnlyWithAProfile() {
        List<ConnectionManager.RaidProfileUpdateMessage> received = new ArrayList<>();
        ConnectionManager.onRaidProfileUpdate(received::add);
        connect();

        harness.receive("{\"type\":\"raid_profile_update\",\"action\":\"removed\",\"profile\":{\"minecraft\":\"Recruit\"}}");
        harness.receive("{\"type\":\"raid_profile_update\",\"action\":\"updated\",\"profile\":null}");

        assertEquals(1, received.size());
        assertTrue(received.getFirst().isRemoval());
        assertEquals("Recruit", received.getFirst().profileJson().get("minecraft").getAsString());
    }

    @Test
    void partyFinderInvitesReachTheirHandler() {
        List<ConnectionManager.PartyFinderInviteMessage> received = new ArrayList<>();
        ConnectionManager.onPartyFinderInvite(received::add);
        connect();

        harness.receive("{\"type\":\"party_finder_invite\",\"listing_id\":7,\"inviter_uuid\":\"u-1\","
                + "\"invite_token\":\"t-1\",\"listing\":{\"id\":7}}");
        harness.receive("{\"type\":\"party_finder_invite\",\"listing_id\":8,\"inviter_uuid\":\"u-2\",\"invite_token\":\"t-2\"}");

        assertEquals(2, received.size());
        assertEquals(7, received.get(0).listingId());
        assertEquals("u-1", received.get(0).inviterUUID());
        assertEquals("t-1", received.get(0).inviteToken());
        assertEquals(7, received.get(0).listingJson().get("id").getAsInt());
        assertNull(received.get(1).listingJson());
    }

    @Test
    void partyFinderStaleWarningsReachTheirHandler() {
        List<ConnectionManager.PartyFinderStaleWarningMessage> received = new ArrayList<>();
        ConnectionManager.onPartyFinderStaleWarning(received::add);
        connect();

        harness.receive("{\"type\":\"party_finder_stale_warning\",\"reason\":\"idle\",\"listing_id\":7,"
                + "\"disband_at\":\"2026-11-18T12:10:00Z\",\"minutes_remaining\":5}");
        harness.receive("{\"type\":\"party_finder_stale_warning\",\"listing_id\":8}");

        assertEquals(List.of(
                new ConnectionManager.PartyFinderStaleWarningMessage("idle", 7, Instant.parse("2026-11-18T12:10:00Z"), 5),
                new ConnectionManager.PartyFinderStaleWarningMessage(null, 8, null, 0)), received);
    }

    // ── Errors ──

    @Test
    void aGuildChatRelayRefusedForTheSenderSaysNothing() {
        connect();

        error("\"code\":\"not_in_guild\",\"capability\":\"guild_chat\",\"message\":\"Guild chat sender is not in Sequoia\"");

        assertEquals(List.of(), harness.told);
        assertTrue(ConnectionManager.isLiveWarTelemetryReady(), "the session is untouched");
    }

    @Test
    void aVersionRejectionForTheWholeClientIsShownAndStopsReconnecting() {
        connect();

        error("\"code\":\"mod_version_unsupported\",\"message\":\"Unknown backend error\",\"minimum_safe_version\":\"1.13.0\"");
        error("\"code\":\"mod_version_unsupported\",\"message\":\"Unknown backend error\",\"minimum_safe_version\":\"1.13.0\"");
        harness.closeFromBackend(1001, "");

        assertEquals(List.of("Update Sequoia to at least 1.13.0."), harness.told);
        assertFalse(ConnectionManager.hasReconnectTask());
    }

    @Test
    void aVersionRejectionForOneFeatureRepeatsAtThatFeaturesPaceAndKeepsReconnecting() {
        connect();
        for (int rejection = 1; rejection <= 20; rejection++) {
            error("\"code\":\"mod_version_unsupported\",\"capability\":\"guild_chat\",\"message\":\"Guild chat needs 1.13.0\","
                    + "\"minimum_safe_version\":\"1.13.0\"");
        }

        assertEquals(List.of(
                "Guild chat needs 1.13.0",
                "Sequoia is outdated. Some guild chat relays may not work until you update to 1.13.0."), harness.told);
        harness.closeFromBackend(1001, "");
        assertTrue(ConnectionManager.hasReconnectTask());
    }

    @Test
    void aRepeatedVersionWarningForAnUnnamedFeatureSaysSomeTwice() {
        connect();
        for (int rejection = 1; rejection <= 5; rejection++) {
            error("\"status\":426,\"capability\":\"war_status\",\"message\":\"Unknown backend error\"");
        }

        // Pinned as it reads today: "Some some Sequoia features". A later fix may word it better.
        assertEquals(List.of(
                "Update Sequoia to at least the required version.",
                "Sequoia is outdated. Some some Sequoia features may not work until you update to a newer version."),
                harness.told);
    }

    @Test
    void aMalformedSignInRequestIsShownAndTheSocketClosesToSignInAgain() {
        connect();

        error("\"status\":400,\"message\":\"Invalid auth request\"");

        assertEquals(List.of("Invalid auth request. Run /seq connect to start a fresh backend session."), harness.told);
        assertFalse(ConnectionManager.isConnected());
        assertEquals(4001, socket.closeCode());
        assertEquals(List.of(), harness.tokens.invalidations, "the stored session is kept");
    }

    @Test
    void aRejectedTokenIsDroppedShownAndTheSocketClosesToSignInAgain() {
        connect();

        error("\"code\":\"token_invalid\",\"message\":\"Token invalid\"");

        assertEquals(List.of(AuthErrorCode.TOKEN_INVALID), harness.tokens.invalidations);
        assertEquals(List.of("Backend rejected the stored token. Re-authentication required."), harness.told);
        assertEquals(4001, socket.closeCode());
        assertTrue(harness.connection().isAuthFailed());
    }

    @Test
    void anUnauthorisedStatusCountsAsARejectedToken() {
        connect();

        error("\"status\":401,\"message\":\"Unauthorized\"");

        assertEquals(List.of(AuthErrorCode.TOKEN_INVALID), harness.tokens.invalidations);
        assertEquals(4001, socket.closeCode());
    }

    @Test
    void anExpiredTokenIsDroppedShownAndTheSocketClosesToSignInAgain() {
        connect();

        error("\"code\":\"token_expired\",\"message\":\"Token expired\"");

        assertEquals(List.of(AuthErrorCode.TOKEN_EXPIRED), harness.tokens.invalidations);
        assertEquals(List.of("Backend token expired. Re-authentication required."), harness.told);
        assertEquals(4001, socket.closeCode());
    }

    @Test
    void aPartyFinderErrorGoesToThePartyFinder() {
        connect();

        error("\"code\":\"invalid_request\",\"message\":\"Listing is full\"");
        error("\"code\":\"invalid_request\",\"message\":\"Nope\",\"request_type\":\"party_join\"");

        assertEquals(List.of("Listing is full", "Nope"), harness.game.partyFinderErrors);
        assertEquals(List.of(), harness.told);
    }

    @Test
    void aFeatureClosedToThisAccountIsNamedAndTheSessionKept() {
        connect();

        error("\"status\":403,\"capability\":\"guild_bank_event\",\"message\":\"Only officers may relay bank events\"");
        error("\"status\":403,\"capability\":\"war_status\",\"message\":\"Unknown backend error\"");

        assertEquals(List.of(
                "guild bank event unavailable: Only officers may relay bank events",
                "war status unavailable for this account."), harness.told);
        assertTrue(ConnectionManager.isConnected());
    }

    @Test
    void anAccountOutsideTheGuildIsToldAndDisconnectedForGood() {
        connect();

        error("\"code\":\"not_in_guild\",\"message\":\"Not in guild\"");

        assertEquals(List.of("Access denied by backend authorization."), harness.told);
        assertTrue(harness.connection().isNotInGuild());
        assertFalse(ConnectionManager.isConnected());
        assertEquals(1000, socket.closeCode());
        harness.echoClientClose();
        assertFalse(ConnectionManager.hasReconnectTask());
    }

    @Test
    void aForbiddenStatusWithoutACapabilityCountsAsOutsideTheGuild() {
        connect();

        error("\"status\":403,\"message\":\"Forbidden\"");

        assertEquals(List.of("Access denied by backend authorization."), harness.told);
    }

    @Test
    void aValidationErrorIsShownAsTheBackendWordedIt() {
        connect();

        error("\"code\":\"invalid_request\",\"message\":\"Payload failed validation: name\"");

        assertEquals(List.of("Payload failed validation: name"), harness.told);
        assertTrue(ConnectionManager.isConnected());
    }

    @Test
    void anOrdinaryErrorIsShownOnceEveryTenMinutes() {
        connect();

        error("\"code\":\"rate_limited\",\"message\":\"Too many guild war submissions\"");
        error("\"code\":\"rate_limited\",\"message\":\"Too many guild war submissions\"");
        error("\"code\":\"rate_limited\",\"message\":\"Something else\"");
        harness.advance(600_000);
        error("\"code\":\"rate_limited\",\"message\":\"Too many guild war submissions\"");

        assertEquals(List.of(
                "Error: Too many guild war submissions",
                "Error: Something else",
                "Error: Too many guild war submissions"), harness.told);
        assertTrue(ConnectionManager.isConnected());
    }

    @Test
    void anErrorWithNoTextIsShownAsUnknown() {
        connect();

        error("\"code\":\"oops\"");

        assertEquals(List.of("Error: Unknown backend error"), harness.told);
    }

    // ── What isn't a message ──

    @Test
    void unknownUntypedAndMalformedMessagesAreIgnoredAndTheConnectionCarriesOn() {
        List<ConnectionManager.DiscordChatMessage> received = new ArrayList<>();
        ConnectionManager.onDiscordChat(received::add);
        connect();

        harness.receive("{\"type\":\"something_new\"}");
        harness.receive("{\"message\":\"no type\"}");
        harness.receive("not json at all");
        harness.receive("{\"type\":\"party_finder_invite\"}");
        harness.receive("{\"type\":\"discord_chat\",\"username\":\"Recruit\",\"message\":\"still here\"}");

        assertEquals(List.of(new ConnectionManager.DiscordChatMessage("Recruit", "still here", null)), received);
        assertTrue(ConnectionManager.isConnected());
        assertEquals(List.of(), harness.told);
    }

    @Test
    void messagesFromAReplacedSocketAreIgnored() {
        List<ConnectionManager.DiscordChatMessage> received = new ArrayList<>();
        ConnectionManager.onDiscordChat(received::add);
        connect();
        FakeBackendSocket first = socket;
        harness.connection().disconnect();
        first.echoClientClose();
        harness.settle();
        harness.connectAsMember();

        first.receive("{\"type\":\"discord_chat\",\"username\":\"Ghost\",\"message\":\"boo\"}");
        harness.settle();

        assertEquals(List.of(), received);
    }

    @Test
    void messagesWithNoHandlerAreDroppedQuietly() {
        connect();

        harness.receive("{\"type\":\"discord_chat\",\"username\":\"Recruit\",\"message\":\"hi\"}");
        harness.receive("{\"type\":\"party_finder_update\",\"action\":\"created\",\"listing\":{}}");
        harness.receive("{\"type\":\"treasury_out_recorded\",\"request_id\":\"r\"}");

        assertTrue(ConnectionManager.isConnected());
        assertEquals(List.of(), harness.told);
    }
}
