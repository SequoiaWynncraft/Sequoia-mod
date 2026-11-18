package com.seqwawa.seq.network;

import com.mojang.authlib.exceptions.AuthenticationException;
import com.seqwawa.seq.accessors.NotificationAccessor;
import com.seqwawa.seq.client.ClientThread;
import com.seqwawa.seq.client.Clocks;
import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.managers.GuildStorageTracker;
import com.seqwawa.seq.managers.ServerQueueDetector;
import com.seqwawa.seq.managers.TreasuryOutManager;
import com.seqwawa.seq.model.WynnClassType;
import com.seqwawa.seq.network.auth.AuthErrorCode;
import com.seqwawa.seq.network.auth.MinecraftAuthService;
import com.seqwawa.seq.network.auth.StoredAuthSession;
import com.seqwawa.seq.session.EndReason;
import com.seqwawa.seq.session.SessionManager;
import com.seqwawa.seq.utils.WynnClassCache;
import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

/**
 * Everything {@link ConnectionManager} reaches outside itself for, so a test can stand in for
 * the game, the clock, the auth service and the backend. {@link #inGame()} is what the game uses.
 *
 * @param serverScope  which Wynncraft server the player is on; traffic goes only to the main one
 * @param clockMs      milliseconds for throttles and backoff ({@link Clocks#monotonicMillis()} in the game)
 * @param wallClock    the moments the connection shows or sends, such as when it signed in
 * @param scheduler    the game thread, which callbacks are handed to, now or after a reconnect delay
 * @param notifier     what the player is told in chat
 * @param tokens       the backend sign-in: tokens, and whether the player signed out
 * @param sockets      makes one backend socket per connection attempt
 * @param game         the account, world and features the connection reads from or reports to
 * @param sessionProofs runs the Treasury account's Minecraft session join, a slow web request
 */
record ConnectionEnvironment(
        Supplier<WynncraftServerPolicy.Scope> serverScope,
        LongSupplier clockMs,
        Clock wallClock,
        Scheduler scheduler,
        Notifier notifier,
        Tokens tokens,
        BackendSocket.Factory sockets,
        Game game,
        Executor sessionProofs) {

    ConnectionEnvironment {
        Objects.requireNonNull(serverScope, "serverScope");
        Objects.requireNonNull(clockMs, "clockMs");
        Objects.requireNonNull(wallClock, "wallClock");
        Objects.requireNonNull(scheduler, "scheduler");
        Objects.requireNonNull(notifier, "notifier");
        Objects.requireNonNull(tokens, "tokens");
        Objects.requireNonNull(sockets, "sockets");
        Objects.requireNonNull(game, "game");
        Objects.requireNonNull(sessionProofs, "sessionProofs");
    }

    /** Stops a delayed task from running, unless it already has. */
    @FunctionalInterface
    interface Cancellable {
        void cancel();
    }

    /** Where the connection's callbacks run. */
    interface Scheduler {

        /** Hands {@code task} to the game thread. */
        void execute(Runnable task);

        /** Runs {@code task} once {@code delayMs} has passed, unless cancelled first. */
        Cancellable after(long delayMs, Runnable task);
    }

    /** What the player is told. */
    interface Notifier {

        /** A line in chat, behind the Sequoia prefix. */
        void show(String message);

        /** A line in chat built by the caller, such as one with a button. */
        void show(Component message);
    }

    /** The backend sign-in, which {@link MinecraftAuthService} runs in the game. */
    interface Tokens {

        /** A token for the bearer handshake, signing in afresh when there is none or {@code forceRefresh}. */
        CompletableFuture<String> ensureValidToken(boolean forceRefresh);

        /** Ends the session because the backend refused its token; the next connect signs in again. */
        void invalidateSession(AuthErrorCode code, String message);

        /** The message of the last sign-in failure, which {@link #invalidateSession} sets. */
        String lastErrorMessage();

        /** The player asked to connect, so a sign-out of theirs no longer holds. */
        void allowSignIn();

        boolean isSignedOutByUser();

        /** The Minecraft name the current sign-in speaks for, or null while signed out. */
        String signedInUsername();
    }

    /** The parts of the game the connection reads from, or reports to. */
    interface Game {

        /** The signed-in Minecraft account's name, or null when there is no account. */
        String minecraftUsername();

        /** The signed-in Minecraft account's id, or null when there is no account. */
        UUID profileId();

        /** The current server connection, compared by identity: a delayed message from an older one is dropped. */
        Object serverConnection();

        /** The current Minecraft account, compared by identity, for the same reason. */
        Object account();

        /** Whether the player waits in Wynncraft's server queue, where guild chat is shown regardless. */
        boolean inServerQueue();

        /** The class the player is on, for the party finder, or null when it isn't known. */
        WynnClassType localClassType();

        /** Shows a backend error in the party finder. */
        void showPartyFinderError(String message);

        /** Applies the guild storage totals the backend sent. Called on the game thread. */
        void applyGuildStorageSnapshot(long emeraldCurrent, long emeraldMax, long aspectCurrent, long aspectMax);

        /** Joins {@code serverId} with the Treasury account's session, proving the account is ours. */
        void joinMinecraftSession(String serverId) throws AuthenticationException;
    }

    /** The environment the game runs the connection in. */
    static ConnectionEnvironment inGame() {
        return new ConnectionEnvironment(
                WynncraftServerPolicy::currentScope,
                Clocks::monotonicMillis,
                Clock.systemUTC(),
                new GameScheduler(),
                new ChatNotifier(),
                new AuthServiceTokens(),
                JavaBackendSocket::new,
                new MinecraftGame(),
                Executors.newSingleThreadExecutor(r -> {
                    Thread t = new Thread(r, "seq-treasury-auth");
                    t.setDaemon(true);
                    return t;
                }));
    }

    /**
     * The game thread's queue: socket events, sign-in answers, session proofs and reconnects
     * all land there, so the connection's state changes on one thread.
     */
    private static final class GameScheduler implements Scheduler {

        @Override
        public void execute(Runnable task) {
            ClientThread.execute(task);
        }

        @Override
        public Cancellable after(long delayMs, Runnable task) {
            return ClientThread.after(Duration.ofMillis(delayMs), task)::cancel;
        }
    }

    private static final class ChatNotifier implements Notifier {

        @Override
        public void show(String message) {
            NotificationAccessor.notifyPlayer(message);
        }

        @Override
        public void show(Component message) {
            Minecraft.getInstance().execute(() -> {
                var player = Minecraft.getInstance().player;
                if (player != null) {
                    player.displayClientMessage(message, false);
                }
            });
        }
    }

    /** Reads the auth service each call: it is created during start-up, after this. */
    private static final class AuthServiceTokens implements Tokens {

        @Override
        public CompletableFuture<String> ensureValidToken(boolean forceRefresh) {
            return SeqClient.getAuthService().ensureValidToken(forceRefresh);
        }

        @Override
        public void invalidateSession(AuthErrorCode code, String message) {
            SessionManager.getInstance().end(new EndReason.TokenRejected(code, message));
        }

        @Override
        public String lastErrorMessage() {
            return SeqClient.getAuthService().getLastError().getMessage();
        }

        @Override
        public void allowSignIn() {
            MinecraftAuthService auth = SeqClient.getAuthService();
            if (auth != null) {
                auth.allowSignIn();
            }
        }

        @Override
        public boolean isSignedOutByUser() {
            MinecraftAuthService auth = SeqClient.getAuthService();
            return auth != null && auth.isSignedOutByUser();
        }

        @Override
        public String signedInUsername() {
            StoredAuthSession signedIn = SeqClient.getAuthService().getCurrentSession();
            return signedIn == null ? null : signedIn.minecraftUsername();
        }
    }

    private static final class MinecraftGame implements Game {

        @Override
        public String minecraftUsername() {
            User user = Minecraft.getInstance().getUser();
            return user == null ? null : user.getName();
        }

        @Override
        public UUID profileId() {
            User user = Minecraft.getInstance().getUser();
            return user == null ? null : user.getProfileId();
        }

        @Override
        public Object serverConnection() {
            // Called on the socket's thread for every frame. getConnection() reads the player
            // twice, and the world can be torn down in between; read it once.
            LocalPlayer player = Minecraft.getInstance().player;
            return player == null ? null : player.connection;
        }

        @Override
        public Object account() {
            return Minecraft.getInstance().getUser();
        }

        @Override
        public boolean inServerQueue() {
            return ServerQueueDetector.isInQueue();
        }

        @Override
        public WynnClassType localClassType() {
            return WynnClassCache.resolveLocalClassType();
        }

        @Override
        public void showPartyFinderError(String message) {
            SeqClient.getPartyFinderManager().pushUiError(message);
        }

        @Override
        public void applyGuildStorageSnapshot(long emeraldCurrent, long emeraldMax, long aspectCurrent, long aspectMax) {
            GuildStorageTracker.getInstance().applyRemoteSnapshot(emeraldCurrent, emeraldMax, aspectCurrent, aspectMax);
        }

        @Override
        public void joinMinecraftSession(String serverId) throws AuthenticationException {
            Minecraft minecraft = Minecraft.getInstance();
            User user = minecraft.getUser();
            if (user == null
                    || !TreasuryOutManager.isTreasuryMinecraftAccount(user.getName())
                    || user.getProfileId() == null
                    || user.getAccessToken() == null
                    || user.getAccessToken().isBlank()) {
                throw new IllegalStateException("The active Minecraft session is not eligible for Treasury OUT.");
            }
            minecraft.services()
                    .sessionService()
                    .joinServer(user.getProfileId(), user.getAccessToken(), serverId);
        }
    }
}
