package com.seqwawa.seq.network;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.seqwawa.seq.model.BombShareType;
import com.seqwawa.seq.model.GuildWarSubmission;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class ConnectionManagerTest {

    @Test
    void websocketHandshakeUsesBearerTokenHeader() {
        Map<String, String> headers = ConnectionManager.buildHandshakeHeaders("abc123", "0.1.3");

        assertEquals(2, headers.size());
        assertEquals("Bearer abc123", headers.get("Authorization"));
        assertEquals("0.1.3", headers.get(ClientVersion.MOD_VERSION_HEADER));
    }

    @Test
    void blankTokenProducesNoHandshakeHeaders() {
        Map<String, String> headers = ConnectionManager.buildHandshakeHeaders("   ", "0.1.3");

        assertEquals(1, headers.size());
        assertEquals("0.1.3", headers.get(ClientVersion.MOD_VERSION_HEADER));
    }

    @Test
    void treasuryOnlyConnectionIsSelectedOnlyForCinfrascitizen() {
        assertTrue(ConnectionManager.shouldUseTreasuryOnlyConnection("cinfrascitizen"));
        assertTrue(ConnectionManager.shouldUseTreasuryOnlyConnection("CinfrasCitizen"));
        assertFalse(ConnectionManager.shouldUseTreasuryOnlyConnection("reyzhia"));
        assertFalse(ConnectionManager.shouldUseTreasuryOnlyConnection(null));
    }

    @Test
    void treasuryConnectionIsReadyOnlyAfterMinecraftSessionProof() {
        assertFalse(ConnectionManager.treasuryConnectionReady(true, true, false));
        assertFalse(ConnectionManager.treasuryConnectionReady(true, false, true));
        assertFalse(ConnectionManager.treasuryConnectionReady(false, true, true));
        assertTrue(ConnectionManager.treasuryConnectionReady(true, true, true));
    }

    @Test
    void guildWarSubmissionPayloadUsesExpectedNestedShape() {
        GuildWarSubmission submission = new GuildWarSubmission(
                "Detlas Suburbs",
                "550e8400-e29b-41d4-a716-446655440000",
                "2026-03-28T01:00:00Z",
                "2026-03-28T00:55:00Z",
                java.util.List.of("Alpha", "Bravo"),
                new GuildWarSubmission.TowerStats(1200, 1800, 2.5, 450000, 0.35),
                410,
                "2026-03-28T00:58:00Z");

        var payload = ConnectionManager.buildGuildWarSubmissionPayload(submission);

        assertEquals("Detlas Suburbs", payload.get("territory").getAsString());
        assertEquals("550e8400-e29b-41d4-a716-446655440000", payload.get("submitted_by").getAsString());
        assertEquals("2026-03-28T01:00:00Z", payload.get("submitted_at").getAsString());
        assertEquals("2026-03-28T00:55:00Z", payload.get("start_time").getAsString());
        assertEquals("Alpha", payload.getAsJsonArray("warrers").get(0).getAsString());
        assertEquals(1200L, payload.getAsJsonObject("results")
                .getAsJsonObject("stats")
                .getAsJsonObject("damage")
                .get("low")
                .getAsLong());
        assertEquals(0.35, payload.getAsJsonObject("results").getAsJsonObject("stats").get("defence").getAsDouble());
        assertEquals(410, payload.get("sr").getAsInt());
        assertEquals("2026-03-28T00:58:00Z", payload.get("completed_at").getAsString());
    }

    @Test
    void discordUsernamePresenceTreatsBlankValuesAsUnlinked() {
        assertTrue(ConnectionManager.hasDiscordUsername("SequoiaUser"));
        assertEquals(false, ConnectionManager.hasDiscordUsername(""));
        assertEquals(false, ConnectionManager.hasDiscordUsername("   "));
    }

    @Test
    void discordChatIgnoreMatchesUsernameContainingIgnoredMinecraftName() {
        assertTrue(ConnectionManager.shouldIgnoreDiscordChatSender("SomeUser", List.of("someuser")));
        assertTrue(ConnectionManager.shouldIgnoreDiscordChatSender("[VIP] SomeUser", List.of("someuser")));
        assertTrue(ConnectionManager.shouldIgnoreDiscordChatSender("Guild | someuser", List.of("someuser")));
        assertFalse(ConnectionManager.shouldIgnoreDiscordChatSender("OtherUser", List.of("someuser")));
        assertFalse(ConnectionManager.shouldIgnoreDiscordChatSender(null, List.of("someuser")));
    }

    @Test
    void localCleanCloseDoesNotReconnect() {
        assertFalse(ConnectionManager.shouldReconnectAfterClose(1000, false));
    }

    @Test
    void remoteCloseStillReconnects() {
        assertTrue(ConnectionManager.shouldReconnectAfterClose(1000, true));
    }

    @Test
    void resetForTestClearsReconnectState() {
        ConnectionManager.resetForTest();

        assertFalse(ConnectionManager.hasReconnectTask());
        assertFalse(ConnectionManager.isConnected());
    }

    @Test
    void automaticConnectStaysSuppressedAfterManualDisconnect() {
        assertFalse(ConnectionManager.shouldAttemptAutomaticConnect(false, false, false, false, true));
    }

    @Test
    void automaticConnectSkipsWhileReconnectAlreadyScheduled() {
        assertFalse(ConnectionManager.shouldAttemptAutomaticConnect(false, false, false, true, false));
    }

    @Test
    void automaticConnectRunsOnlyWhenSocketIsIdle() {
        assertTrue(ConnectionManager.shouldAttemptAutomaticConnect(false, false, false, false, false));
        assertFalse(ConnectionManager.shouldAttemptAutomaticConnect(true, false, false, false, false));
        assertFalse(ConnectionManager.shouldAttemptAutomaticConnect(false, true, false, false, false));
        assertFalse(ConnectionManager.shouldAttemptAutomaticConnect(false, false, true, false, false));
    }

    @Test
    void guildStorageMessagesAreServerScopedAuthenticatedOutbound() {
        assertTrue(ConnectionManager.isServerScopedType("guild_storage_snapshot"));
        assertTrue(ConnectionManager.isServerScopedType("guild_storage_reward"));
        assertTrue(ConnectionManager.isServerScopedType("guild_alliance_update"));
        assertTrue(ConnectionManager.isServerScopedType("guild_alliance_snapshot"));
        assertTrue(ConnectionManager.isAuthenticatedOutboundType("guild_storage_snapshot"));
        assertTrue(ConnectionManager.isAuthenticatedOutboundType("guild_storage_reward"));
        assertTrue(ConnectionManager.isAuthenticatedOutboundType("guild_alliance_update"));
        assertTrue(ConnectionManager.isAuthenticatedOutboundType("guild_alliance_snapshot"));
    }

    @Test
    void guildStorageMessagesDoNotUseGenericPrivilegedThrottle() {
        assertFalse(ConnectionManager.isThrottleLimitedType("guild_storage_snapshot"));
        assertFalse(ConnectionManager.isThrottleLimitedType("guild_storage_reward"));
        assertTrue(ConnectionManager.isThrottleLimitedType("guild_chat"));
    }

    @Test
    void scopedGuildChatMembershipRejectsAreSilent() {
        assertTrue(ConnectionManager.isSilentGuildChatMembershipReject(
                "not_in_guild", "guild_chat", "guild chat sender is not in sequoia"));
    }

    @Test
    void unscopedGuildMembershipRejectIdentifiesNonMemberSession() {
        assertTrue(ConnectionManager.isSessionMembershipReject(
                "not_in_guild", null, "this feature is limited to sequoia members"));
        assertTrue(ConnectionManager.isSessionMembershipReject(
                "not_in_guild", "", "you must be a member of sequoia guild to use this feature"));
    }

    @Test
    void scopedMembershipRejectDoesNotDisableWholeSession() {
        assertFalse(ConnectionManager.isSessionMembershipReject(
                "not_in_guild", "guild_chat", "guild chat sender is not in sequoia"));
        assertFalse(ConnectionManager.isSilentGuildChatMembershipReject(
                "not_in_guild", "guild_bank_event", "this feature is limited to sequoia members"));
    }

    @Test
    void memberOnlyOutboundTypesExcludeUnrestrictedPartyClassUpdates() {
        assertTrue(ConnectionManager.isSequoiaMemberOnlyType("guild_chat"));
        assertTrue(ConnectionManager.isSequoiaMemberOnlyType("guild_alliance_snapshot"));
        assertTrue(ConnectionManager.isSequoiaMemberOnlyType("guild_storage_snapshot"));
        assertTrue(ConnectionManager.isSequoiaMemberOnlyType("guild_war_submission"));
        assertTrue(ConnectionManager.isSequoiaMemberOnlyType("party_sync_snapshot"));
        assertTrue(ConnectionManager.isSequoiaMemberOnlyType("get_connected"));
        assertFalse(ConnectionManager.isSequoiaMemberOnlyType("party_class_update"));
    }

    @Test
    void bombShareMessagesAreScopedAuthenticatedAndThrottleLimited() {
        assertTrue(ConnectionManager.isServerScopedType("bomb_share_request"));
        assertTrue(ConnectionManager.isServerScopedType("bomb_share_submit"));
        assertTrue(ConnectionManager.isAuthenticatedOutboundType("bomb_share_request"));
        assertTrue(ConnectionManager.isAuthenticatedOutboundType("bomb_share_submit"));
        assertTrue(ConnectionManager.isThrottleLimitedType("bomb_share_request"));
        assertTrue(ConnectionManager.isThrottleLimitedType("bomb_share_submit"));
        assertFalse(ConnectionManager.isThrottleLimitedType("guild_alliance_snapshot"));
    }

    @Test
    void treasuryOutUsesTypedGsonSerializationWithoutClaimedIdentity() {
        TreasuryOutRequest request = new TreasuryOutRequest(
                "550e8400-e29b-41d4-a716-446655440000",
                "2stx5le+1stx5le+4stx4le",
                "cinfrascitizen",
                "guild event prizes");

        var json = ConnectionManager.serializeTreasuryOutRequest(request);

        assertEquals(protocolFixture("outbound/treasury-out.json"), json);
        assertEquals("treasury_out", json.get("type").getAsString());
        assertEquals("550e8400-e29b-41d4-a716-446655440000", json.get("request_id").getAsString());
        assertEquals("2stx5le+1stx5le+4stx4le", json.get("amount").getAsString());
        assertEquals("cinfrascitizen", json.get("payouter").getAsString());
        assertEquals("guild event prizes", json.get("reason").getAsString());
        assertFalse(json.has("username"));
        assertFalse(json.has("minecraft_username"));
        assertFalse(json.has("uuid"));
        assertFalse(json.has("minecraft_uuid"));
    }

    @Test
    void treasuryAuthResponseContainsOnlyTypeAndBackendNonce() {
        var json = ConnectionManager.serializeTreasuryAuthResponse(
                new TreasuryAuthResponse("7505801b-9e89-4ef8-a32e-8d55e2f4d011"));

        assertEquals("treasury_auth_response", json.get("type").getAsString());
        assertEquals("7505801b-9e89-4ef8-a32e-8d55e2f4d011", json.get("nonce").getAsString());
        assertEquals(2, json.size());
        assertFalse(json.has("access_token"));
        assertFalse(json.has("minecraft_uuid"));
        assertFalse(json.has("username"));
    }

    @Test
    void treasuryOutUsesSessionProofInsteadOfBearerAuthAndRemainsThrottleLimited() {
        assertTrue(ConnectionManager.isServerScopedType("treasury_out"));
        assertFalse(ConnectionManager.isAuthenticatedOutboundType("treasury_out"));
        assertTrue(ConnectionManager.isThrottleLimitedType("treasury_out"));
        assertFalse(ConnectionManager.isSequoiaMemberOnlyType("treasury_out"));
    }

    @Test
    void dispatchesTreasuryOutRecordedWithItsRequestId() {
        AtomicReference<TreasuryOutRecordedMessage> dispatched = new AtomicReference<>();
        ConnectionManager.onTreasuryOutRecorded(dispatched::set);
        try {
            ConnectionManager.getInstance().onMessage(protocolFixtureText("inbound/treasury-out-recorded.json"));

            assertEquals("550e8400-e29b-41d4-a716-446655440000", dispatched.get().requestId());
            assertEquals("Season 32", dispatched.get().sheetName());
            assertEquals(7, dispatched.get().row());
        } finally {
            ConnectionManager.onTreasuryOutRecorded(null);
            ConnectionManager.resetForTest();
        }
    }

    @Test
    void dispatchesCorrelatedTreasuryErrorBeforeGenericStatusHandling() {
        AtomicReference<TreasuryOutErrorMessage> dispatched = new AtomicReference<>();
        ConnectionManager.onTreasuryOutError(error -> {
            dispatched.set(error);
            return true;
        });
        try {
            ConnectionManager.getInstance().onMessage(protocolFixtureText("inbound/treasury-out-error.json"));

            assertEquals("550e8400-e29b-41d4-a716-446655440000", dispatched.get().requestId());
            assertEquals("invalid_request", dispatched.get().code());
            assertEquals("amount must use a supported denomination", dispatched.get().message());
        } finally {
            ConnectionManager.onTreasuryOutError(null);
            ConnectionManager.resetForTest();
        }
    }

    @Test
    void parsedBombSharePromptKeepsExistingCallbackAndPendingStateBehavior() {
        AtomicReference<ConnectionManager.BombSharePromptMessage> dispatched = new AtomicReference<>();
        ConnectionManager.onBombSharePrompt(dispatched::set);
        try {
            ConnectionManager manager = ConnectionManager.getInstance();
            manager.onMessage(protocolFixtureText("inbound/bomb-share-prompt.json"));

            ConnectionManager.BombSharePromptMessage expected = new ConnectionManager.BombSharePromptMessage(
                    "04677645-a0d0-4b5f-bd5d-590b3f7f2f5d",
                    "CinfrasCitizen",
                    "loot-and-combat",
                    List.of(BombShareType.LOOT, BombShareType.COMBAT_XP),
                    Instant.parse("2026-08-03T12:45:30Z"),
                    true);
            assertEquals(expected, dispatched.get());
            assertEquals(expected, manager.getPendingBombSharePrompt(expected.requestId()).orElseThrow());
        } finally {
            ConnectionManager.onBombSharePrompt(null);
            ConnectionManager.resetForTest();
        }
    }

    @Test
    void malformedIncomingPayloadRemainsContainedByConnectionManager() {
        try {
            assertDoesNotThrow(() -> ConnectionManager.getInstance().onMessage("{"));
        } finally {
            ConnectionManager.resetForTest();
        }
    }

    @Test
    void routesPartyFinderUpdateThroughExistingCallbackApi() {
        AtomicReference<ConnectionManager.PartyFinderUpdateMessage> dispatched = new AtomicReference<>();
        ConnectionManager.onPartyFinderUpdate(dispatched::set);
        try {
            ConnectionManager.getInstance().onMessage(protocolFixtureText("inbound/party-finder-update.json"));

            assertEquals("updated", dispatched.get().action());
            assertEquals(42, dispatched.get().listingJson().get("id").getAsInt());
            assertEquals("TNA full run", dispatched.get().listingJson().get("title").getAsString());
        } finally {
            ConnectionManager.onPartyFinderUpdate(null);
            ConnectionManager.resetForTest();
        }
    }

    @Test
    void routesBombShareResultAndClearsPendingPrompt() {
        AtomicReference<ConnectionManager.BombShareResultMessage> dispatched = new AtomicReference<>();
        ConnectionManager.onBombShareResult(dispatched::set);
        try {
            ConnectionManager manager = ConnectionManager.getInstance();
            manager.onMessage(protocolFixtureText("inbound/bomb-share-prompt.json"));
            assertTrue(manager.hasPendingBombSharePrompt("04677645-a0d0-4b5f-bd5d-590b3f7f2f5d"));

            manager.onMessage(protocolFixtureText("inbound/bomb-share-result.json"));

            assertEquals(List.of("WC1", "WC2"), dispatched.get().worlds());
            assertEquals(2, dispatched.get().shareCount());
            assertFalse(manager.hasPendingBombSharePrompt("04677645-a0d0-4b5f-bd5d-590b3f7f2f5d"));
        } finally {
            ConnectionManager.onBombShareResult(null);
            ConnectionManager.resetForTest();
        }
    }

    @Test
    void guildAllianceSnapshotPayloadNormalizesNamesAndUsesExpectedShape() {
        List<String> guildNames = ConnectionManager.normalizeGuildAllianceNames(
                List.of(" Avicia ", "avicia", "Nefarious Ravens"));

        assertEquals(List.of("Avicia", "Nefarious Ravens"), guildNames);

        var payload = ConnectionManager.buildGuildAllianceSnapshotPayload(guildNames);
        assertEquals(protocolFixture("outbound/guild-alliance-snapshot.json"), payload);
        assertEquals(1, payload.size());
        assertEquals("Avicia", payload.getAsJsonArray("guild_names").get(0).getAsString());
        assertEquals("Nefarious Ravens", payload.getAsJsonArray("guild_names").get(1).getAsString());

        assertEquals(
                0,
                ConnectionManager.buildGuildAllianceSnapshotPayload(List.of())
                        .getAsJsonArray("guild_names")
                        .size());
    }

    @Test
    void guildAllianceSnapshotRejectsMalformedOrOversizedNames() {
        assertNull(ConnectionManager.normalizeGuildAllianceNames(List.of("")));
        assertNull(ConnectionManager.normalizeGuildAllianceNames(List.of("Guild: Name")));
        assertNull(ConnectionManager.normalizeGuildAllianceNames(List.of("A".repeat(65))));
        assertNull(ConnectionManager.normalizeGuildAllianceNames(
                java.util.stream.IntStream.range(0, 17)
                        .mapToObj(index -> "Guild " + index)
                        .toList()));
    }

    private static JsonElement protocolFixture(String name) {
        return JsonParser.parseString(protocolFixtureText(name));
    }

    private static String protocolFixtureText(String name) {
        String path = "/protocol/" + name;
        try (InputStream stream = ConnectionManagerTest.class.getResourceAsStream(path)) {
            if (stream == null) {
                throw new AssertionError("Missing protocol fixture " + path);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new AssertionError("Failed to read protocol fixture " + path, exception);
        }
    }

}
