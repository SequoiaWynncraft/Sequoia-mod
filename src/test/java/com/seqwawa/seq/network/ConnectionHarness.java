package com.seqwawa.seq.network;

import com.google.gson.JsonObject;
import com.mojang.authlib.exceptions.AuthenticationException;
import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.client.TickQueue;
import com.seqwawa.seq.config.ConfigManager;
import com.seqwawa.seq.config.TestConfigs;
import com.seqwawa.seq.model.WynnClassType;
import com.seqwawa.seq.network.auth.AuthErrorCode;
import com.seqwawa.seq.network.auth.UnlinkedAccountWarning;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.network.chat.Component;

/**
 * Runs {@link ConnectionManager} against stand-ins for everything outside it: a backend
 * socket the test drives, a game thread that runs only when the test lets it, a clock the
 * test moves, and an auth service and game that record what they are asked.
 * <p>
 * The helpers that play the backend let the game thread catch up afterwards, as the next
 * ticks would, so a test reads the state the game would see.
 */
final class ConnectionHarness implements AutoCloseable {

    static final UUID PROFILE = UUID.fromString("f1000000-0000-0000-0000-000000000001");
    static final String MEMBER = "Sequoian";
    static final String TREASURY = "cinfrascitizen";
    /** A valid Treasury challenge nonce: 32 URL-safe base64 characters. */
    static final String TREASURY_NONCE = "AAECAwQFBgcICQoLDA0ODxAREhMUFRYX";

    /** Starts well past zero: the game's clock does too, and zero must not mean "never". */
    final AtomicLong nowMs = new AtomicLong(5_000_000L);
    final TickQueue gameThread = new TickQueue(nowMs::get);
    final List<String> told = new ArrayList<>();
    final List<Component> toldLines = new ArrayList<>();
    final Tokens tokens = new Tokens();
    final Game game = new Game();
    final List<FakeBackendSocket> sockets = new ArrayList<>();
    final List<Runnable> heldProofs = new ArrayList<>();
    /** When set, Treasury session joins wait in {@link #heldProofs} instead of running at once. */
    boolean holdProofs;
    /** When set, the next socket made throws this from connect, as a handshake that can't start does. */
    RuntimeException nextConnectFailure;
    volatile WynncraftServerPolicy.Scope scope = WynncraftServerPolicy.Scope.MAIN;

    final ConfigManager config;
    private final ConfigManager previousConfig;

    ConnectionHarness(Path configDirectory) {
        previousConfig = SeqClient.configManager;
        config = TestConfigs.in(configDirectory);
        SeqClient.configManager = config;
        UnlinkedAccountWarning.getInstance().reset();
        ConnectionManager.useEnvironment(environment());
    }

    @Override
    public void close() {
        ConnectionManager.resetForTest();
        UnlinkedAccountWarning.getInstance().reset();
        SeqClient.configManager = previousConfig;
    }

    private ConnectionEnvironment environment() {
        return new ConnectionEnvironment(
                () -> scope,
                nowMs::get,
                new FollowingClock(),
                new ConnectionEnvironment.Scheduler() {
                    @Override
                    public void execute(Runnable task) {
                        gameThread.execute(task);
                    }

                    @Override
                    public ConnectionEnvironment.Cancellable after(long delayMs, Runnable task) {
                        return gameThread.after(delayMs, task)::cancel;
                    }
                },
                new ConnectionEnvironment.Notifier() {
                    @Override
                    public void show(String message) {
                        told.add(message);
                    }

                    @Override
                    public void show(Component message) {
                        toldLines.add(message);
                    }
                },
                tokens,
                (uri, listener) -> {
                    FakeBackendSocket socket = new FakeBackendSocket(uri, listener);
                    socket.connectFailure = nextConnectFailure;
                    nextConnectFailure = null;
                    sockets.add(socket);
                    return socket;
                },
                game,
                task -> {
                    if (holdProofs) {
                        heldProofs.add(task);
                    } else {
                        task.run();
                    }
                });
    }

    // ── The game ──

    ConnectionManager connection() {
        return ConnectionManager.getInstance();
    }

    /** Lets the game thread run until nothing more is due, as the next few ticks would. */
    void settle() {
        for (int i = 0; i < 100; i++) {
            if (gameThread.runDue() == 0) {
                return;
            }
        }
        throw new AssertionError("The game thread never went quiet");
    }

    /** Moves time on and lets the game thread run what came due. */
    void advance(long ms) {
        nowMs.addAndGet(ms);
        settle();
    }

    void connectAutomatically() {
        connection().connect();
        settle();
    }

    void connectManually() {
        connection().connectManually();
        settle();
    }

    void runHeldProofs() {
        List<Runnable> proofs = List.copyOf(heldProofs);
        heldProofs.clear();
        proofs.forEach(Runnable::run);
        settle();
    }

    // ── The backend ──

    FakeBackendSocket socket() {
        if (sockets.isEmpty()) {
            throw new AssertionError("No socket was made");
        }
        return sockets.getLast();
    }

    /** What the latest socket sent; nothing when no socket was made. */
    List<String> sentTypes() {
        return sockets.isEmpty() ? List.of() : socket().sentTypes();
    }

    void clearSent() {
        if (!sockets.isEmpty()) {
            socket().clearSent();
        }
    }

    FakeBackendSocket open() {
        FakeBackendSocket socket = socket();
        socket.open();
        settle();
        return socket;
    }

    void receive(String message) {
        socket().receive(message);
        settle();
    }

    void signIn() {
        receive("{\"type\":\"authenticated\",\"discord_username\":\"sequoian\",\"guild_rank_tracking_supported\":true}");
    }

    /** The backend answers the membership probe: this account is a member. */
    void confirmMembership(String... connectedUsers) {
        StringBuilder users = new StringBuilder();
        for (String user : connectedUsers) {
            if (!users.isEmpty()) users.append(',');
            users.append('"').append(user).append('"');
        }
        receive("{\"type\":\"connected_users\",\"users\":[" + users + "]}");
    }

    /** The backend says this account isn't a Sequoia member, as it does for a non-member's first member-only request. */
    void rejectMembership() {
        receive("{\"type\":\"error\",\"code\":\"not_in_guild\",\"message\":\"This feature is limited to Sequoia members\"}");
    }

    /** The Treasury socket's challenge, as the backend sends it once the socket opens. */
    void challengeTreasury() {
        receive("{\"type\":\"treasury_auth_challenge\",\"nonce\":\"" + TREASURY_NONCE + "\",\"proof_version\":2}");
    }

    /** The backend accepts the session proof the client sent. */
    void acceptTreasuryProof() {
        JsonObject response = socket().lastSent(TreasuryAuthResponse.TYPE);
        if (response == null) {
            throw new AssertionError("The client sent no Treasury session proof");
        }
        receive("{\"type\":\"treasury_authenticated\",\"nonce\":\"" + response.get("nonce").getAsString()
                + "\",\"client_nonce\":\"" + response.get("client_nonce").getAsString() + "\"}");
    }

    /** Connects automatically as the Treasury account and has its session proof accepted. */
    FakeBackendSocket connectAsTreasury() {
        game.username = TREASURY;
        connectAutomatically();
        FakeBackendSocket socket = open();
        challengeTreasury();
        acceptTreasuryProof();
        return socket;
    }

    /** Answers the oldest waiting token request. */
    void answerToken(String token) {
        tokens.pending.removeFirst().complete(token);
        settle();
    }

    /** Fails the oldest waiting token request. */
    void failToken(Throwable failure) {
        tokens.pending.removeFirst().completeExceptionally(failure);
        settle();
    }

    /** Connects automatically, opens and signs in as a confirmed member. */
    FakeBackendSocket connectAsMember() {
        connectAutomatically();
        FakeBackendSocket socket = open();
        signIn();
        confirmMembership(MEMBER);
        return socket;
    }

    /** What a sign-out does to the connection: the player is signed out, then the session, and its socket, end. */
    void signOut() {
        tokens.signedOutByUser = true;
        tokens.endSession();
    }

    /** What switching Minecraft account does: the previous account is forgotten, and its session ends. */
    void switchAccount() {
        ConnectionManager.resetForAccountChange();
        tokens.endSession();
    }

    void closeFromBackend(int code, String reason) {
        socket().closeFromBackend(code, reason);
        settle();
    }

    void echoClientClose() {
        socket().echoClientClose();
        settle();
    }

    // ── Stand-ins ──

    /** The auth service: hands out tokens, by default at once, and records what it is asked. */
    static final class Tokens implements ConnectionEnvironment.Tokens {
        final List<Boolean> requests = new ArrayList<>();
        final Deque<CompletableFuture<String>> pending = new ArrayDeque<>();
        final List<AuthErrorCode> invalidations = new ArrayList<>();
        /** The token every request gets at once, or null to leave requests waiting in {@link #pending}. */
        String token = "session-token";
        boolean signedOutByUser;
        String signedInUsername;
        /** The current sign-in, which moves on when it ends. */
        long session;
        int allowSignIns;
        private final java.util.Map<Long, List<Runnable>> sessionEnds = new java.util.HashMap<>();

        /** Ends the current session, as the session manager does, and starts the next. */
        void endSession() {
            List<Runnable> ended = sessionEnds.getOrDefault(session, List.of());
            sessionEnds.remove(session);
            session++;
            ended.forEach(Runnable::run);
        }

        @Override
        public CompletableFuture<String> ensureValidToken(boolean forceRefresh) {
            requests.add(forceRefresh);
            if (token != null) {
                return CompletableFuture.completedFuture(token);
            }
            CompletableFuture<String> request = new CompletableFuture<>();
            pending.add(request);
            return request;
        }

        @Override
        public void invalidateSession(String token, AuthErrorCode code, String message) {
            invalidations.add(code);
            endSession();
        }

        @Override
        public void allowSignIn() {
            allowSignIns++;
            signedOutByUser = false;
        }

        @Override
        public boolean isSignedOutByUser() {
            return signedOutByUser;
        }

        @Override
        public String signedInUsername() {
            return signedInUsername;
        }

        @Override
        public long sessionId() {
            return session;
        }

        @Override
        public void whenSessionEnds(long sessionId, Runnable action) {
            if (sessionId == session) {
                sessionEnds.computeIfAbsent(sessionId, ignored -> new ArrayList<>()).add(action);
            } else {
                action.run();
            }
        }
    }

    /** The game: one account on one server connection, recording what it is told. */
    static final class Game implements ConnectionEnvironment.Game {
        String username = MEMBER;
        UUID profileId = PROFILE;
        Object serverConnection = new Object();
        Object account = new Object();
        boolean inServerQueue;
        WynnClassType classType;
        final List<String> partyFinderErrors = new ArrayList<>();
        /** The server connection each party finder error arrived on, in the same order. */
        final List<Object> partyFinderErrorConnections = new ArrayList<>();
        final List<long[]> storageSnapshots = new ArrayList<>();
        final List<String> joinedServerIds = new ArrayList<>();
        /** Thrown by the next session join, to stand for Mojang refusing it. */
        AuthenticationException joinFailure;

        @Override
        public String minecraftUsername() {
            return username;
        }

        @Override
        public UUID profileId() {
            return profileId;
        }

        @Override
        public Object serverConnection() {
            return serverConnection;
        }

        @Override
        public Object account() {
            return account;
        }

        @Override
        public boolean inServerQueue() {
            return inServerQueue;
        }

        @Override
        public WynnClassType localClassType() {
            return classType;
        }

        @Override
        public void showPartyFinderError(String message, Object heardOn) {
            partyFinderErrors.add(message);
            partyFinderErrorConnections.add(heardOn);
        }

        @Override
        public void applyGuildStorageSnapshot(long emeraldCurrent, long emeraldMax, long aspectCurrent, long aspectMax) {
            storageSnapshots.add(new long[] {emeraldCurrent, emeraldMax, aspectCurrent, aspectMax});
        }

        @Override
        public void joinMinecraftSession(String serverId) throws AuthenticationException {
            joinedServerIds.add(serverId);
            if (joinFailure != null) {
                throw joinFailure;
            }
        }
    }

    /** A wall clock that moves with {@link #nowMs}. */
    private final class FollowingClock extends Clock {
        private static final long EPOCH_OFFSET_MS = Instant.parse("2026-11-18T12:00:00Z").toEpochMilli();

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return Instant.ofEpochMilli(EPOCH_OFFSET_MS + nowMs.get());
        }
    }
}
