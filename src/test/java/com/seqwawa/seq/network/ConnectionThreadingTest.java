package com.seqwawa.seq.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.authlib.exceptions.AuthenticationException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The socket, the Treasury session proof and the reconnect timer finish on their own threads;
 * the connection hands what they report to the game thread and changes its state only there.
 */
class ConnectionThreadingTest {

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

    @Test
    void anOpeningSocketChangesNothingUntilTheGameThreadRuns() {
        harness.connectManually();
        harness.told.clear();

        harness.socket().open();

        assertEquals(List.of(), harness.told);
        harness.settle();
        assertEquals(List.of("Connected websocket to production. Waiting for backend authentication..."), harness.told);
    }

    @Test
    void aSignInIsSeenOnlyOnceTheGameThreadHandlesIt() {
        harness.connectAutomatically();
        FakeBackendSocket socket = harness.open();

        socket.receive("{\"type\":\"authenticated\"}");

        assertFalse(ConnectionManager.isConnected());
        assertEquals(List.of(), socket.sentTypes());
        harness.settle();
        assertTrue(ConnectionManager.isConnected());
    }

    @Test
    void aCloseSchedulesItsReconnectOnTheGameThread() {
        FakeBackendSocket socket = harness.connectAsMember();

        socket.closeFromBackend(1006, "");

        assertFalse(ConnectionManager.hasReconnectTask());
        assertFalse(ConnectionManager.isConnected(), "a closed socket is not connected, even before the game thread runs");
        harness.settle();
        assertTrue(ConnectionManager.hasReconnectTask());
    }

    @Test
    void eventsAreHandledInTheOrderTheSocketReportedThem() {
        harness.connectAutomatically();
        FakeBackendSocket socket = harness.socket();

        socket.open();
        socket.receive("{\"type\":\"authenticated\"}");
        socket.receive("{\"type\":\"connected_users\",\"users\":[]}");
        socket.receive("{\"type\":\"error\",\"code\":\"not_in_guild\",\"message\":\"Limited to Sequoia members\"}");
        harness.settle();

        assertTrue(ConnectionManager.isConnected());
        assertFalse(ConnectionManager.isLiveWarTelemetryReady(), "the rejection came after the membership answer");
    }

    @Test
    void theTreasuryProofIsSentFromTheGameThread() {
        harness.game.username = ConnectionHarness.TREASURY;
        harness.holdProofs = true;
        harness.connectAutomatically();
        FakeBackendSocket socket = harness.open();
        harness.challengeTreasury();

        // The proof's own thread finishes the Mojang join.
        List.copyOf(harness.heldProofs).forEach(Runnable::run);

        assertNull(socket.lastSent(TreasuryAuthResponse.TYPE));
        harness.settle();
        assertTrue(socket.sentTypes().contains(TreasuryAuthResponse.TYPE));
    }

    @Test
    void aFailedTreasuryProofIsHandledOnTheGameThread() {
        harness.game.username = ConnectionHarness.TREASURY;
        harness.game.joinFailure = new com.mojang.authlib.exceptions.AuthenticationException("Invalid session");
        harness.holdProofs = true;
        harness.connectAutomatically();
        FakeBackendSocket socket = harness.open();
        harness.challengeTreasury();

        List.copyOf(harness.heldProofs).forEach(Runnable::run);

        assertEquals(0, socket.closes());
        harness.settle();
        assertEquals(BackendCloseClassifier.POLICY_VIOLATION, socket.closeCode());
    }

    @Test
    void aReconnectRunsOnlyWhenTheGameThreadRunsItsTimers() {
        FakeBackendSocket socket = harness.connectAsMember();
        socket.closeFromBackend(1006, "");
        harness.settle();

        harness.nowMs.addAndGet(1_000);

        assertEquals(1, harness.sockets.size());
        harness.settle();
        assertEquals(2, harness.sockets.size());
    }

    @Test
    void callersOnManyThreadsAtOnceShareOneConnection() throws Exception {
        int callers = 16;
        ExecutorService pool = Executors.newFixedThreadPool(callers);
        CountDownLatch go = new CountDownLatch(1);
        Set<ConnectionManager> seen = ConcurrentHashMap.newKeySet();
        try {
            for (int i = 0; i < callers; i++) {
                pool.execute(() -> {
                    try {
                        go.await();
                        seen.add(ConnectionManager.getInstance());
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
            }
            go.countDown();
            pool.shutdown();
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }

        assertEquals(1, seen.size());
        assertEquals(List.of(), harness.sockets, "a socket is made per attempt, not per connection");
    }

    // ── Work queued before the connection changed ──

    @Test
    void aSignInQueuedBeforeClearSessionDoesNotRestoreTheDiscordLink() {
        harness.connectAutomatically();
        FakeBackendSocket socket = harness.open();
        socket.receive("{\"type\":\"authenticated\",\"discord_username\":\"sequoian\"}");

        harness.signOut();
        harness.config.clearDiscordUsername(ConnectionHarness.PROFILE);
        harness.settle();

        assertNull(harness.config.getDiscordUsername(ConnectionHarness.PROFILE));
        assertFalse(ConnectionManager.isConnected());
    }

    @Test
    void aPartyFinderUpdateQueuedBeforeASignOutIsDropped() {
        List<Integer> listings = new ArrayList<>();
        ConnectionManager.listen(ConnectionManager.Inbound.PARTY_FINDER_UPDATE, update -> listings.add(update.listingJson().get("id").getAsInt()));
        FakeBackendSocket socket = harness.connectAsMember();
        socket.receive(partyFinderUpdate(7));

        harness.signOut();
        harness.settle();

        assertEquals(List.of(), listings);
    }

    @Test
    void partyFinderFramesQueuedAfterMinecraftSwitchedAccountAreDropped() {
        List<Object> heard = new ArrayList<>();
        ConnectionManager.listen(ConnectionManager.Inbound.PARTY_FINDER_UPDATE, heard::add);
        ConnectionManager.listen(ConnectionManager.Inbound.PARTY_FINDER_INVITE, heard::add);
        ConnectionManager.listen(ConnectionManager.Inbound.PARTY_FINDER_STALE_WARNING, heard::add);
        FakeBackendSocket socket = harness.connectAsMember();
        socket.receive(partyFinderUpdate(7));
        socket.receive("{\"type\":\"party_finder_invite\",\"listing_id\":7,\"inviter_uuid\":\"u-1\",\"invite_token\":\"t-1\"}");
        socket.receive("{\"type\":\"party_finder_stale_warning\",\"listing_id\":7,\"minutes_remaining\":5}");
        socket.receive("{\"type\":\"error\",\"code\":\"invalid_request\",\"message\":\"Listing is full\"}");

        // Minecraft has switched profile; the session ends at the end of the tick, after the queue drains.
        harness.game.profileId = java.util.UUID.fromString("20000000-0000-0000-0000-000000000002");
        harness.settle();

        assertEquals(List.of(), heard);
        assertEquals(List.of(), harness.game.partyFinderErrors, "nor is its error shown on the party finder's banner");
    }

    @Test
    void aPartyFinderFrameCarriesTheServerConnectionItArrivedOn() {
        List<ConnectionManager.PartyFinderUpdateMessage> heard = new ArrayList<>();
        ConnectionManager.listen(ConnectionManager.Inbound.PARTY_FINDER_UPDATE, heard::add);
        FakeBackendSocket socket = harness.connectAsMember();
        Object arrivedOn = harness.game.serverConnection;
        socket.receive(partyFinderUpdate(7));

        // The player reconnects before the game thread hands it on.
        harness.game.serverConnection = new Object();
        harness.settle();

        assertEquals(1, heard.size());
        assertTrue(heard.getFirst().serverConnection() == arrivedOn, "its chat lines are for the connection it arrived on");
    }

    @Test
    void aPartyFinderErrorCarriesTheServerConnectionItArrivedOn() {
        FakeBackendSocket socket = harness.connectAsMember();
        Object arrivedOn = harness.game.serverConnection;
        socket.receive("{\"type\":\"error\",\"code\":\"invalid_request\",\"message\":\"Listing is full\"}");

        // The same account reconnects to Wynncraft before the game thread hands it on.
        harness.game.serverConnection = new Object();
        harness.settle();

        assertEquals(List.of("Listing is full"), harness.game.partyFinderErrors);
        assertTrue(harness.game.partyFinderErrorConnections.getFirst() == arrivedOn,
                "its banner is for the connection it arrived on");
    }

    @Test
    void aListingQueuedBehindTheBackendsRefusalOfTheTokenIsDropped() {
        List<Integer> listings = new ArrayList<>();
        ConnectionManager.listen(ConnectionManager.Inbound.PARTY_FINDER_UPDATE, update -> listings.add(update.listingJson().get("id").getAsInt()));
        FakeBackendSocket socket = harness.connectAsMember();

        // Both reach the game thread together; the refusal ends the session and clears Party Finder.
        socket.receive("{\"type\":\"error\",\"code\":\"token_expired\",\"message\":\"Token expired\"}");
        socket.receive(partyFinderUpdate(7));
        harness.settle();

        assertEquals(List.of(), listings);
        harness.echoClientClose();
        assertTrue(ConnectionManager.hasReconnectTask(), "its close still reconnects");
    }

    @Test
    void framesBeforeABackendCloseStillRunInOrderBeforeIt() {
        List<Integer> listings = new ArrayList<>();
        ConnectionManager.listen(ConnectionManager.Inbound.PARTY_FINDER_UPDATE, update -> listings.add(update.listingJson().get("id").getAsInt()));
        FakeBackendSocket socket = harness.connectAsMember();

        socket.receive(partyFinderUpdate(7));
        socket.receive(partyFinderUpdate(8));
        socket.closeFromBackend(1006, "");
        harness.settle();

        assertEquals(List.of(7, 8), listings);
        assertTrue(ConnectionManager.hasReconnectTask());
    }

    @Test
    void aConnectWhileTheCloseIsQueuedRunsOnANewSocketOnceTheCloseIsHandled() {
        FakeBackendSocket first = harness.connectAsMember();
        first.closeFromBackend(1006, "");

        harness.connection().connectManually();
        harness.settle();

        assertEquals(2, harness.sockets.size());
        assertTrue(harness.socket().connected());
        assertFalse(ConnectionManager.hasReconnectTask(), "the player's connect replaces the reconnect");
        assertEquals(1, harness.tokens.allowSignIns, "only the player's own Connect lifts a sign-out, not its held run");
    }

    @Test
    void recoveryWaitsForAQueuedTreasuryCloseAndKeepsItsVerdict() {
        FakeBackendSocket socket = harness.connectAsTreasury();
        harness.told.clear();
        socket.closeFromBackend(BackendCloseClassifier.POLICY_VIOLATION, "mod_version_unsupported");

        // SeqClient's periodic recovery, on a tick before the close is handled.
        assertFalse(ConnectionManager.canAutoConnectNow());
        harness.connection().connect();
        harness.settle();

        assertEquals(1, harness.sockets.size());
        assertFalse(ConnectionManager.hasReconnectTask());
        assertEquals(
                List.of("This version of Sequoia is no longer supported. Update the mod, then run /seq connect."),
                harness.told);
    }

    @Test
    void recoveryWaitsForATreasuryCloseTheLibraryHasNotReportedYet() {
        FakeBackendSocket socket = harness.connectAsTreasury();
        harness.told.clear();
        socket.startClosingFromBackend();

        // SeqClient's periodic recovery, after the socket started closing and before the library says so.
        assertFalse(ConnectionManager.canAutoConnectNow());
        harness.connection().connect();
        harness.settle();
        assertEquals(1, harness.sockets.size(), "the closing socket isn't reused");

        socket.finishClosingFromBackend(BackendCloseClassifier.POLICY_VIOLATION, "mod_version_unsupported");
        harness.settle();
        assertFalse(ConnectionManager.hasReconnectTask());
        assertEquals(
                List.of("This version of Sequoia is no longer supported. Update the mod, then run /seq connect."),
                harness.told);
    }

    @Test
    void aSocketThatStartsClosingWhileConnectLooksIsNotConnectedAgain() {
        FakeBackendSocket socket = harness.connectAsTreasury();
        harness.told.clear();
        socket.startClosingAfterNextClosingCheck = true;

        harness.connection().connect();
        harness.settle();
        assertEquals(1, harness.sockets.size(), "the socket that began closing isn't reused");

        socket.finishClosingFromBackend(BackendCloseClassifier.POLICY_VIOLATION, "mod_version_unsupported");
        harness.settle();
        assertFalse(ConnectionManager.hasReconnectTask());
        assertEquals(
                List.of("This version of Sequoia is no longer supported. Update the mod, then run /seq connect."),
                harness.told);
    }

    @Test
    void recoveryIsNotOfferedForASocketThatStartsClosingWhileItLooks() {
        FakeBackendSocket socket = harness.connectAsTreasury();
        socket.startClosingAfterNextClosingCheck = true;

        assertFalse(ConnectionManager.canAutoConnectNow(), "no recovery, and so no cooldown, for a socket still closing");
    }

    @Test
    void aTreasuryProofThatFinishesAfterAnErrorResetIsNotSent() {
        FakeBackendSocket socket = holdTreasuryProof();

        socket.error(new RuntimeException("handler failed"));
        List.copyOf(harness.heldProofs).forEach(Runnable::run);
        harness.settle();

        assertTrue(socket.isOpen());
        assertNull(socket.lastSent(TreasuryAuthResponse.TYPE));
    }

    @Test
    void aTreasuryProofFailureThatFinishesAfterAnErrorResetChangesNothing() {
        harness.game.joinFailure = new AuthenticationException("Invalid session");
        FakeBackendSocket socket = holdTreasuryProof();

        socket.error(new RuntimeException("handler failed"));
        List.copyOf(harness.heldProofs).forEach(Runnable::run);
        harness.settle();

        assertEquals(0, socket.closes());
        assertFalse(harness.told.contains("Treasury identity verification failed. Restart Minecraft and try again."));
        socket.closeFromBackend(1006, "");
        harness.settle();
        assertTrue(ConnectionManager.hasReconnectTask(), "a stale failure does not stop reconnecting");
    }

    /** A Treasury socket whose session join for the backend's challenge waits in {@code heldProofs}. */
    private FakeBackendSocket holdTreasuryProof() {
        harness.game.username = ConnectionHarness.TREASURY;
        harness.holdProofs = true;
        harness.connectAutomatically();
        FakeBackendSocket socket = harness.open();
        harness.challengeTreasury();
        return socket;
    }

    private static String partyFinderUpdate(int listingId) {
        return "{\"type\":\"party_finder_update\",\"action\":\"created\",\"listing\":{\"id\":" + listingId + "}}";
    }
}
