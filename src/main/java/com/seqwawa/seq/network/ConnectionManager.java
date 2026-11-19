package com.seqwawa.seq.network;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.seqwawa.seq.accessors.NotificationAccessor;
import com.seqwawa.seq.client.ClientThread;
import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.config.ConfigManager;
import com.seqwawa.seq.images.BridgeMedia;
import com.seqwawa.seq.managers.GuildRankEventParser;
import com.seqwawa.seq.managers.TreasuryOutManager;
import com.seqwawa.seq.model.BombShareType;
import com.seqwawa.seq.model.ChatItemPreview;
import com.seqwawa.seq.model.GuildWarQueueCancellation;
import com.seqwawa.seq.model.GuildWarQueueSubmission;
import com.seqwawa.seq.model.GuildWarSubmission;
import com.seqwawa.seq.model.WarStatusUpdate;
import com.seqwawa.seq.model.WarTowerUpdate;
import com.seqwawa.seq.model.WynnClassType;
import com.seqwawa.seq.network.auth.AuthErrorCode;
import com.seqwawa.seq.network.auth.AuthException;
import com.seqwawa.seq.network.auth.UnlinkedAccountWarning;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;

/**
 * The backend websocket: signing in, staying connected, and every message the mod sends or
 * receives over it. Everything it reaches outside itself for comes from a {@link ConnectionEnvironment},
 * so a test can play the backend, the game and the clock.
 * <p>
 * Where the connection is lives in one {@link State}, changed only by {@link #step}, on the game
 * thread: the player's Connect and Disconnect, the sign-in's answer, the socket's events, the timer
 * and the end of the session the socket belongs to all go through it. Each try at connecting is an
 * {@link Attempt} with a socket of its own. What an attempt reports once another replaced it, or once
 * this client closed it, changes nothing; every close, whoever started it, is handled in one place,
 * which decides what comes next. Senders and readers on other threads read volatile fields, written
 * so that a sign-in never looks ready before all of it is in place.
 */
public final class ConnectionManager {

    private static final Gson GSON = new Gson();
    static final String DISMISS_UNLINKED_COMMAND = "/seq _dismiss-unlinked";
    // Java UTF-16 units after trim(), matching the backend guild_chat contract.
    private static final int MAX_GUILD_CHAT_MESSAGE_LENGTH = 400;
    private static final long PRIVILEGED_SEND_THROTTLE_MS = 50;
    private static final Pattern MC_USERNAME_PATTERN = Pattern.compile("^[A-Za-z0-9_]{3,16}$");
    private static final Pattern URL_PATTERN = Pattern.compile("^(https?://).+", Pattern.CASE_INSENSITIVE);
    static final int MAX_CONSECUTIVE_AUTH_REJECTIONS = 3;
    private static final int NORMAL_CLOSE = 1000;
    /** The close this client sends to drop a socket the backend stopped accepting as signed in; it reconnects. */
    private static final int CLOSE_TO_REAUTHENTICATE = 4001;
    private static final long NO_SESSION = -1;

    private static final ConnectionEnvironment GAME = ConnectionEnvironment.inGame();
    private static volatile ConnectionManager instance;
    /** Who hears each message type the backend sends. They outlive every connection. */
    private static final Map<Inbound<?>, List<Predicate<Object>>> listeners = new ConcurrentHashMap<>();

    private final ConnectionEnvironment env;

    // Written by step() only, on the game thread.
    private volatile State state = State.IDLE;
    /** The latest attempt, live or not: what the getters describe. Null before the first. */
    private volatile Attempt attempt;
    /** The timer {@link State#WAITING} waits on. */
    private ConnectionEnvironment.Cancellable waiting;
    /** Who holds automatic connects back until the player connects. */
    private Hold hold = Hold.NONE;
    /** Reconnects in a row without a socket opening. */
    private int reconnects;
    /** Sign-in rejections since the backend last accepted one; without a limit, a backend that keeps refusing is retried forever. */
    private int authRejections;
    /** The session whose end the connection already waits for. */
    private long watchedSession = NO_SESSION;
    /** The session a Connect the player asked for, held for a close or the sign-in backoff, belongs to; its end drops it. */
    private long heldConnectFor = NO_SESSION;

    /** Spaces out sign-ins after a refusal, across reconnects; an accepted sign-in or another account clears it. */
    private final SignInBackoff signInBackoff;
    /** Ordinary backend errors, each shown at most once every ten minutes; all are logged. */
    private final RepeatedNotice otherErrors;
    /** War events wait here for a member's socket, in the order they were seen; they outlive any one socket. */
    private final GuildWarOutboundQueue pendingGuildWarEvents = new GuildWarOutboundQueue();
    private final GuildRankObservationQueue guildRankObservations = new GuildRankObservationQueue();
    private final Map<String, Integer> versionRejectionCounts = new ConcurrentHashMap<>();
    private volatile long nextPrivilegedSendAtMs;

    public static ConnectionManager getInstance() {
        ConnectionManager current = instance;
        if (current != null) {
            return current;
        }
        synchronized (ConnectionManager.class) {
            if (instance == null) {
                instance = new ConnectionManager(GAME);
            }
            return instance;
        }
    }

    private ConnectionManager(ConnectionEnvironment env) {
        this.env = env;
        signInBackoff = new SignInBackoff(env.clockMs());
        otherErrors = new RepeatedNotice(Duration.ofMinutes(10).toMillis(), env.clockMs());
    }

    /** Runs the connection in {@code env} from now on, starting afresh with no listeners. For tests. */
    static void useEnvironment(ConnectionEnvironment env) {
        listeners.clear();
        instance = new ConnectionManager(Objects.requireNonNull(env, "env"));
    }

    /** Forgets the connection and every listener; the next {@link #getInstance()} starts afresh in the game. */
    static void resetForTest() {
        listeners.clear();
        instance = null;
    }

    // ── What the rest of the mod asks for ──

    public void connect() {
        step(new Event.Connect(false));
    }

    public void connectManually() {
        // Only the player's own Connect ends a sign-out of theirs; one held for a close or the backoff runs without.
        env.tokens().allowSignIn();
        step(new Event.Connect(true));
    }

    public void disconnect() {
        step(new Event.Disconnect(Stop.CODE));
    }

    public void disconnectManually() {
        step(new Event.Disconnect(Stop.PLAYER));
    }

    /** Called every tick off Wynncraft's main server: stops whatever is connecting, connected or waiting. */
    public static void disconnectForBlockedServer() {
        ConnectionManager current = getInstance();
        if (current.state != State.IDLE && current.currentScope() == WynncraftServerPolicy.Scope.BLOCKED) {
            current.step(new Event.Disconnect(Stop.BLOCKED_SERVER));
        }
    }

    /**
     * Forgets what the backend said about the previous account: a pause it caused, its refusals,
     * the not-linked warning, the sign-in backoff, its bomb share prompts and its war events still
     * waiting to go. The socket itself closes with the session it belonged to.
     */
    public static void resetForAccountChange() {
        ConnectionManager current = getInstance();
        current.pendingGuildWarEvents.clear();
        UnlinkedAccountWarning.getInstance().reset();
        if (current.hold == Hold.BACKEND) {
            current.hold = Hold.NONE;
        }
        current.authRejections = 0;
        current.signInBackoff.reset();
        Attempt latest = current.attempt;
        if (latest != null) {
            latest.bombSharePrompts.clear();
        }
    }

    /** Drops the war events waiting to be sent: they were the ended session's to send. */
    public static void dropPendingWarEvents() {
        getInstance().pendingGuildWarEvents.clear();
    }

    /** Whether nothing is connecting, connected or waiting, and nothing holds automatic connects back. */
    public static boolean canAutoConnectNow() {
        ConnectionManager current = getInstance();
        return current.state == State.IDLE && !current.isAutomaticConnectSuppressed();
    }

    public static boolean isAutoConnectSuppressedByManualDisconnect() {
        return getInstance().hold == Hold.PLAYER;
    }

    static boolean hasReconnectTask() {
        return getInstance().state == State.WAITING;
    }

    /** Whether only the player's Connect may connect: they disconnected or signed out, or the backend said no. */
    private boolean isAutomaticConnectSuppressed() {
        return hold != Hold.NONE || env.tokens().isSignedOutByUser();
    }

    private WynncraftServerPolicy.Scope currentScope() {
        return env.serverScope().get();
    }

    // ── The state ──

    /** Where the connection is. */
    private enum State {
        /** No socket, and nothing waiting to run. */
        IDLE,
        /** Waiting for the token the socket signs in with. */
        SIGNING_IN,
        /** The socket is connecting. */
        CONNECTING,
        /** The socket is open; how far it signed in is its attempt's {@link Standing}. */
        OPEN,
        /** This client is closing the socket: only its close still counts. */
        CLOSING,
        /** A reconnect, or a sign-in the backoff holds back, waits for its time. */
        WAITING
    }

    /** Who holds automatic connects back, until the player connects. */
    private enum Hold { NONE, PLAYER, BACKEND }

    /** How far an attempt's socket got with signing in. */
    private enum Standing {
        NONE,
        /** Signed in with the token; the membership probe hasn't been answered yet. */
        PROBING,
        MEMBER,
        /** Signed in, but outside Sequoia: Sequoia-only traffic stops for this socket. */
        NON_MEMBER,
        /** The backend, or the auth service, refused the sign-in. */
        REFUSED,
        /** The backend refused the account as outside the guild. */
        NOT_IN_GUILD
    }

    /** Why the connection stops. */
    private enum Stop { PLAYER, BLOCKED_SERVER, CODE }

    /** Everything that moves the connection on. {@link #step} handles each, on the game thread. */
    private sealed interface Event {
        record Connect(boolean playerAsked) implements Event {}

        record Disconnect(Stop why) implements Event {}

        record TokenReady(Attempt attempt, String token, Throwable failure) implements Event {}

        record Opened(Attempt attempt) implements Event {}

        /** A text frame, with the server connection and account current as it arrived. */
        record Frame(Attempt attempt, String text, Object serverConnection, Object account) implements Event {}

        record Failed(Attempt attempt, Exception error) implements Event {}

        record Closed(Attempt attempt, int code, String reason, boolean remote, boolean byClient) implements Event {}

        record SessionEnded(long session) implements Event {}

        record TimeUp(Runnable then) implements Event {}
    }

    /** The one place the connection's state changes. Game thread only. */
    private void step(Event event) {
        ClientThread.check("ConnectionManager");
        switch (event) {
            case Event.Connect connect -> connect(connect.playerAsked());
            case Event.Disconnect disconnect -> disconnect(disconnect.why());
            case Event.TokenReady ready when isCurrent(ready.attempt(), State.SIGNING_IN) ->
                    tokenReady(ready.attempt(), ready.token(), ready.failure());
            case Event.Opened opened when isCurrent(opened.attempt(), State.CONNECTING) -> opened(opened.attempt());
            case Event.Frame frame when isCurrent(frame.attempt(), State.OPEN) ->
                    handleMessage(frame.attempt(), frame.text(), frame.serverConnection(), frame.account());
            case Event.Frame frame -> settleTreasuryReply(frame.text());
            case Event.Failed failed when isLive(failed.attempt()) -> failed(failed.attempt(), failed.error());
            case Event.Closed closed when isLive(closed.attempt()) ->
                    closed(closed.attempt(), closed.code(), closed.reason(), closed.remote(), closed.byClient());
            case Event.SessionEnded ended -> {
                // A Connect its player asked for that is still held goes with the session.
                if (heldConnectFor == ended.session()) {
                    heldConnectFor = NO_SESSION;
                    cancelWait();
                }
                // A close already under way keeps its own verdict, such as reconnecting to sign in afresh.
                if (isLive(attempt) && state != State.CLOSING && attempt.session == ended.session()) {
                    SeqClient.LOGGER.info("[WebSocket] Closing the connection of a session that ended");
                    end(attempt);
                }
            }
            case Event.TimeUp timeUp when state == State.WAITING -> {
                waiting = null;
                state = State.IDLE;
                timeUp.then().run();
            }
            default -> SeqClient.LOGGER.debug(
                    "[WebSocket] {} changes nothing in state {}", event.getClass().getSimpleName(), state);
        }
    }

    private boolean isCurrent(Attempt candidate, State expected) {
        return candidate == attempt && state == expected;
    }

    /** Whether {@code candidate} is the attempt under way, from asking for its token until its close is handled. */
    private boolean isLive(Attempt candidate) {
        return candidate != null && candidate == attempt && state != State.IDLE && state != State.WAITING;
    }

    private void connect(boolean playerAsked) {
        if (playerAsked) {
            // Asking to connect ends the player's own disconnect, and any pause the backend caused.
            hold = Hold.NONE;
            authRejections = 0;
        } else if (isAutomaticConnectSuppressed()) {
            SeqClient.LOGGER.info("[WebSocket] Skipping automatic connect until the player connects explicitly");
            return;
        }
        WynncraftServerPolicy.Scope scope = currentScope();
        if (scope != WynncraftServerPolicy.Scope.MAIN) {
            if (state == State.SIGNING_IN) {
                // Its token, when it comes, finds the attempt no longer under way.
                state = State.IDLE;
            }
            if (scope == WynncraftServerPolicy.Scope.BLOCKED) {
                cancelWait();
                if (isLive(attempt)) {
                    attempt.reconnect = false;
                }
                if (playerAsked) {
                    notify(WynncraftServerPolicy.MAIN_SERVER_ONLY_MESSAGE);
                }
                SeqClient.LOGGER.info("[WebSocket] Blocking connection outside main Wynncraft host");
            } else {
                if (playerAsked) {
                    notify("Waiting until Wynncraft server transfer finishes.");
                }
                SeqClient.LOGGER.info("[WebSocket] Delaying connection until Wynncraft host is confirmed");
            }
            return;
        }
        Attempt live = isLive(attempt) ? attempt : null;
        if (live != null && (state == State.CLOSING || live.socket.isClosing())) {
            // This socket's close, handled or about to be, decides what comes next; a Connect the player
            // asked for runs on a new socket once it is.
            if (playerAsked) {
                holdConnect();
            }
            SeqClient.LOGGER.info("[WebSocket] Holding connect until the socket's close is handled playerAsked={}", playerAsked);
            return;
        }
        SeqClient.LOGGER.info("[WebSocket] connect playerAsked={} state={} configuredUrl={}", playerAsked, state, BuildConfig.WS_URL);
        if (live != null) {
            if (state != State.OPEN) {
                live.reporting = playerAsked;
            }
            if (playerAsked) {
                notify("Already connected/connecting.");
            }
            return;
        }
        cancelWait();
        if (playerAsked) {
            notify("Connecting to " + BuildConfig.ENVIRONMENT + "...");
        }
        boolean treasury = shouldUseTreasuryOnlyConnection(env.game().minecraftUsername());
        long throttledMs = treasury ? 0 : signInBackoff.waitMs();
        if (throttledMs > 0) {
            SeqClient.LOGGER.warn("[WebSocket] Auth attempt throttled waitMs={}", throttledMs);
            if (playerAsked) {
                notify("Auth throttled. Retrying in " + Math.max(1, throttledMs / 1000) + "s.");
                holdConnect();
            }
            waitThen(throttledMs, playerAsked ? this::runHeldConnect : () -> connect(false));
            return;
        }
        Attempt next = new Attempt(treasury, playerAsked);
        attempt = next;
        next.session = env.tokens().sessionId();
        watch(next.session);
        if (treasury) {
            state = State.CONNECTING;
            connectSocket(next, null);
            return;
        }
        state = State.SIGNING_IN;
        env.tokens()
                .ensureValidToken(playerAsked)
                .whenComplete((token, failure) ->
                        env.scheduler().execute(() -> step(new Event.TokenReady(next, token, failure))));
    }

    /** Holds the player's Connect for a close or the backoff, as the current session's: that session's end drops it. */
    private void holdConnect() {
        heldConnectFor = env.tokens().sessionId();
        watch(heldConnectFor);
    }

    /** Runs the Connect the player asked for while it was held, unless something dropped it since. */
    private void runHeldConnect() {
        if (heldConnectFor != NO_SESSION) {
            heldConnectFor = NO_SESSION;
            connect(true);
        }
    }

    /** Hears when {@code session} ends. */
    private void watch(long session) {
        if (session != watchedSession) {
            watchedSession = session;
            env.tokens().whenSessionEnds(session, () -> step(new Event.SessionEnded(session)));
        }
    }

    private void disconnect(Stop why) {
        if (why == Stop.PLAYER) {
            hold = Hold.PLAYER;
        }
        Attempt live = isLive(attempt) ? attempt : null;
        boolean open = live != null && live.socket.isOpen();
        boolean connecting = state == State.SIGNING_IN || state == State.CONNECTING;
        SeqClient.LOGGER.info("[WebSocket] disconnect why={} state={} open={}", why, state, open);
        cancelWait();
        if (live != null) {
            end(live);
        }
        if (why == Stop.PLAYER) {
            notify(open ? "Disconnected" : "Not connected");
        } else if (why == Stop.BLOCKED_SERVER && (open || connecting)) {
            notify(WynncraftServerPolicy.MAIN_SERVER_ONLY_MESSAGE);
        }
    }

    /**
     * Ends {@code ended} on purpose: what it still reports changes nothing, and its close starts nothing,
     * not even a verdict the backend gave it first, such as a refused handshake, which was for an account
     * or session that is gone.
     */
    private void end(Attempt ended) {
        ended.retired = true;
        ended.reconnect = false;
        ended.reporting = false;
        heldConnectFor = NO_SESSION;
        ended.drop();
        if (state == State.CONNECTING || state == State.OPEN && ended.socket.isOpen()) {
            // A handshake under way is cut short too; the next attempt waits for this socket's close either way.
            closeFromClient(ended, NORMAL_CLOSE, "");
        } else if (state != State.CLOSING) {
            // Not connecting yet, or already closed by the backend: what its socket reports finds it replaced.
            state = State.IDLE;
        }
    }

    /**
     * Closes {@code closing}'s socket, unless the backend already is. Its frames drop from now on; its close
     * decides what follows, and the socket says whether that close answers this one or is the backend's own.
     */
    private void closeFromClient(Attempt closing, int code, String reason) {
        state = State.CLOSING;
        closing.socket.close(code, reason);
    }

    private void tokenReady(Attempt signingIn, String token, Throwable failure) {
        if (failure != null) {
            AuthException authException = unwrapAuthException(failure);
            signingIn.standing = Standing.REFUSED;
            signInBackoff.recordFailure();
            SeqClient.LOGGER.warn(
                    "[WebSocket] Failed to obtain backend auth token code={} message={}",
                    authException.getStableCode(),
                    authException.getMessage(),
                    authException);
            if (UnlinkedAccountWarning.isDismissible(authException.getCode())) {
                notifyUnlinkedAccount(signingIn, authException.getMessage());
            } else {
                signingIn.reportFailure(authException.getMessage(), !authException.isRetryable());
            }
            signingIn.reporting = false;
            state = State.IDLE;
            if (authException.isRetryable() && signingIn.reconnect) {
                scheduleReconnect();
            }
            return;
        }
        UnlinkedAccountWarning.getInstance().reset();
        WynncraftServerPolicy.Scope scope = currentScope();
        if (scope != WynncraftServerPolicy.Scope.MAIN) {
            state = State.IDLE;
            if (scope == WynncraftServerPolicy.Scope.BLOCKED) {
                signingIn.report(WynncraftServerPolicy.MAIN_SERVER_ONLY_MESSAGE);
            } else if (signingIn.reconnect) {
                scheduleReconnect();
            }
            signingIn.reporting = false;
            return;
        }
        signingIn.token = token;
        signingIn.account = env.game().profileId();
        state = State.CONNECTING;
        connectSocket(signingIn, token);
    }

    private void connectSocket(Attempt connecting, String token) {
        try {
            connecting.socket.connect(buildHandshakeHeaders(token));
        } catch (RuntimeException exception) {
            SeqClient.LOGGER.error("[WebSocket] Failed to connect", exception);
            connecting.report("Failed to connect: " + exception.getMessage());
            connecting.reporting = false;
            state = State.IDLE;
            scheduleReconnect();
        }
    }

    private void opened(Attempt opened) {
        SeqClient.LOGGER.info("[WebSocket] onOpen configuredUrl={} clientUri={}", BuildConfig.WS_URL, opened.socket.uri());
        state = State.OPEN;
        reconnects = 0;
        if (opened.treasury) {
            opened.report("Connected for Treasury OUT. Verifying the cinfrascitizen Minecraft session...");
            return;
        }
        String username = env.tokens().signedInUsername();
        opened.report(username != null && !username.isBlank()
                ? "Connected websocket for " + username + ". Waiting for backend authentication..."
                : "Connected websocket to " + BuildConfig.ENVIRONMENT + ". Waiting for backend authentication...");
    }

    private void failed(Attempt failing, Exception error) {
        SeqClient.LOGGER.error(
                "[WebSocket] onError state={} standing={} message={}",
                state,
                failing.standing,
                error != null ? error.getMessage() : "null",
                error);
        failing.resetSession();
        failing.report("Connection error: " + (error != null ? error.getMessage() : "unknown"));
        failing.reporting = false;
    }

    /** Every close comes here, the client's own included, and decides what comes next. */
    private void closed(Attempt closing, int code, String reason, boolean remote, boolean byClient) {
        SeqClient.LOGGER.info(
                "[WebSocket] onClose code={} reason='{}' remote={} byClient={} retired={} reconnect={}",
                code,
                reason,
                remote,
                byClient,
                closing.retired,
                closing.reconnect);
        closing.drop();
        state = State.IDLE;
        BackendCloseClassifier.Kind kind = BackendCloseClassifier.classify(code, reason, byClient || closing.retired);
        handleBackendClose(closing, kind);
        // A close this client started comes back marked remote once the backend echoes it.
        if (closing.reconnect && shouldReconnectAfterClose(code, remote && kind != BackendCloseClassifier.Kind.CLIENT_INITIATED)) {
            SeqClient.LOGGER.info("[WebSocket] Scheduling reconnect after close code={} remote={}", code, remote);
            scheduleReconnect();
        } else {
            SeqClient.LOGGER.info("[WebSocket] Reconnect not scheduled code={} remote={}", code, remote);
            closing.reporting = false;
        }
        runHeldConnect();
    }

    private void handleBackendClose(Attempt closing, BackendCloseClassifier.Kind kind) {
        switch (kind) {
            case AUTH_REJECTED -> {
                closing.standing = Standing.REFUSED;
                String message = "Backend rejected websocket authentication. Re-authenticating.";
                // An error before it may have counted this refusal, and dropped the token only if it said the token was dead.
                if (!closing.refusalCounted) {
                    closing.report(message);
                    signInBackoff.recordFailure();
                    recordAuthRejection(closing);
                }
                if (!closing.tokenDropped) {
                    env.tokens().invalidateSession(closing.token, AuthErrorCode.WEBSOCKET_AUTH_REJECTED, message);
                }
            }
            // The backend explains in an error just before it closes; say it here only if that went unshown.
            case VERSION_UNSUPPORTED -> pauseAutoConnect(closing, closing.versionRejectionShown
                    ? null
                    : "This version of Sequoia is no longer supported. Update the mod, then run /seq connect.");
            case PARTY_FINDER_BANNED -> pauseAutoConnect(closing,
                    "Sequoia closed the connection: this account is banned from the party finder. "
                            + "Run /seq connect to try again.");
            case HANDSHAKE_FORBIDDEN -> pauseAutoConnect(closing,
                    "Sequoia refused to connect this account. Run /seq connect to try again.");
            // The Treasury error before this close already stopped reconnecting and told the player.
            case TREASURY_AUTH_FAILED -> closing.reconnect = false;
            case CLIENT_INITIATED, OTHER -> {}
        }
    }

    static boolean shouldReconnectAfterClose(int code, boolean remote) {
        if (remote) {
            return true;
        }
        // Local clean close (1000) is treated as intentional; do not auto-reconnect.
        // Handshake/protocol close failures (e.g. 1002 from HTTP 502) should retry.
        return code != NORMAL_CLOSE;
    }

    /**
     * Counts a sign-in rejection. After {@link #MAX_CONSECUTIVE_AUTH_REJECTIONS} in a row, with no
     * sign-in accepted in between, the backend is taken to mean it, and reconnecting stops until
     * the player connects.
     */
    private void recordAuthRejection(Attempt refused) {
        authRejections++;
        if (shouldStopAfterAuthRejections(authRejections)) {
            pauseAutoConnect(refused, "Sequoia keeps rejecting this session. Run /seq connect to try again.");
        }
    }

    static boolean shouldStopAfterAuthRejections(int consecutiveRejections) {
        return consecutiveRejections >= MAX_CONSECUTIVE_AUTH_REJECTIONS;
    }

    /** A refusal no retry can fix: stop reconnecting, and connecting automatically, until the player connects. */
    private void pauseAutoConnect(Attempt refused, String message) {
        refused.reconnect = false;
        if (hold == Hold.NONE) {
            hold = Hold.BACKEND;
        }
        SeqClient.LOGGER.warn("[WebSocket] Backend refused this client; automatic connects paused until /seq connect");
        if (message != null) {
            notify(message);
        }
    }

    private void scheduleReconnect() {
        if (currentScope() == WynncraftServerPolicy.Scope.BLOCKED) {
            SeqClient.LOGGER.info("[WebSocket] Auto reconnect suppressed outside main Wynncraft host");
            return;
        }
        if (ReconnectBackoff.exhausted(reconnects)) {
            SeqClient.LOGGER.warn("[WebSocket] Auto reconnect exhausted after {} attempts", reconnects);
            notify("Could not reconnect automatically. Run /seq connect manually.");
            return;
        }
        long delay = ReconnectBackoff.delayMs(reconnects);
        reconnects++;
        SeqClient.LOGGER.info("[WebSocket] Reconnecting in {}ms (attempt {})", delay, reconnects);
        waitThen(delay, this::reconnectNow);
    }

    private void reconnectNow() {
        WynncraftServerPolicy.Scope scope = currentScope();
        if (scope == WynncraftServerPolicy.Scope.BLOCKED) {
            SeqClient.LOGGER.info("[WebSocket] Cancelled reconnect because current host is blocked");
            return;
        }
        if (scope != WynncraftServerPolicy.Scope.MAIN) {
            reconnects = Math.max(0, reconnects - 1);
            SeqClient.LOGGER.info("[WebSocket] Delaying reconnect attempt {} until Wynncraft host is confirmed", reconnects);
            scheduleReconnect();
            return;
        }
        SeqClient.LOGGER.info("[WebSocket] Running scheduled reconnect attempt {}", reconnects);
        connect(false);
    }

    /** Waits {@code delayMs}, then runs {@code then} on the game thread, unless the wait is cancelled first. */
    private void waitThen(long delayMs, Runnable then) {
        cancelWait();
        state = State.WAITING;
        waiting = env.scheduler().after(delayMs, () -> step(new Event.TimeUp(then)));
    }

    private void cancelWait() {
        if (state == State.WAITING) {
            waiting.cancel();
            waiting = null;
            // A Connect of the player's that waited out the backoff goes with the wait.
            heldConnectFor = NO_SESSION;
            state = State.IDLE;
        }
    }

    private void notify(String message) {
        env.notifier().show(message);
    }

    private void notifyUnlinkedAccount(Attempt refused, String message) {
        UUID profileId = env.game().profileId();
        if (profileId == null) {
            notify(message);
            return;
        }
        if (!UnlinkedAccountWarning.getInstance().shouldShow(profileId, BuildConfig.API_URL, refused.reporting)) {
            SeqClient.LOGGER.info("[Auth] Not-linked warning hidden for this session");
            return;
        }
        env.notifier().show(unlinkedAccountMessage(message, profileId));
    }

    static Component unlinkedAccountMessage(String message, UUID profileId) {
        return NotificationAccessor.prefixComponent()
                .append(Component.literal(message + " ").withStyle(ChatFormatting.GRAY))
                .append(NotificationAccessor.wynnPill(
                        "Dismiss",
                        ChatFormatting.DARK_GRAY,
                        ChatFormatting.WHITE,
                        new ClickEvent.RunCommand(DISMISS_UNLINKED_COMMAND + " " + profileId)));
    }

    // ── One attempt ──

    /** One try at connecting, with a socket of its own: from asking for a token until its close is handled. */
    private final class Attempt implements BackendSocket.Listener {

        final BackendSocket socket;
        final boolean treasury;
        final TreasurySessionAuthenticator proof;
        /** The token this socket signs in with and the account it signs in for; a Treasury socket has neither. */
        String token;
        UUID account;
        /** The session current when it started; that session's end closes it. */
        long session;
        /** The player asked for this connect: each step is told until it is ready or fails. */
        boolean reporting;
        /** Whether its close reconnects, unless the close itself refuses. */
        boolean reconnect = true;
        /** Ended on purpose, by a disconnect or its session's end: what the backend says to it applies to no one. */
        boolean retired;
        /**
         * A refusal of its sign-in is counted, and its token dropped, once each: the backend may say it twice,
         * an error then a close, and only some errors say the token is dead.
         */
        boolean refusalCounted;
        boolean tokenDropped;
        boolean versionRejectionShown;
        volatile Standing standing = Standing.NONE;
        volatile boolean rankTracking;
        volatile Instant connectedSince;
        /** Everyone waiting on a get_connected reply, so the members panel and /seq connected can overlap. */
        final Queue<Consumer<List<String>>> connectedUsersCallbacks = new ConcurrentLinkedQueue<>();
        final Map<String, BombSharePromptMessage> bombSharePrompts = new ConcurrentHashMap<>();

        Attempt(boolean treasury, boolean reporting) {
            this.treasury = treasury;
            this.reporting = reporting;
            socket = env.sockets().create(URI.create(BuildConfig.WS_URL), this);
            // The session join runs on its own thread. Its result is checked against resets and
            // applied on the game thread, in one step, so no reset can run in between.
            proof = new TreasurySessionAuthenticator(
                    env.game()::joinMinecraftSession,
                    env.sessionProofs(),
                    env.scheduler()::execute,
                    this::sendProof,
                    this::proofFailed,
                    BuildConfig.WS_URL);
        }

        // What the socket reports, on its own thread, handed to the game thread in the order it came.

        @Override
        public void onOpen() {
            env.scheduler().execute(() -> step(new Event.Opened(this)));
        }

        @Override
        public void onMessage(String message) {
            // Read as it arrives: guild chat from a server connection or account that has ended since is dropped.
            Object connection = env.game().serverConnection();
            Object currentAccount = env.game().account();
            env.scheduler().execute(() -> step(new Event.Frame(this, message, connection, currentAccount)));
        }

        @Override
        public void onClose(int code, String reason, boolean remote, boolean byClient) {
            env.scheduler().execute(() -> step(new Event.Closed(this, code, reason, remote, byClient)));
        }

        @Override
        public void onError(Exception error) {
            env.scheduler().execute(() -> step(new Event.Failed(this, error)));
        }

        /** Signed in with a token the backend hasn't refused since. */
        boolean signedIn() {
            Standing now = standing;
            return now == Standing.PROBING || now == Standing.MEMBER || now == Standing.NON_MEMBER;
        }

        void report(String message) {
            if (reporting) {
                ConnectionManager.this.notify(message);
            }
        }

        void reportFailure(String message, boolean requiresManualIntervention) {
            if (requiresManualIntervention) {
                ConnectionManager.this.notify(message);
            } else {
                report(message);
            }
        }

        /** Forgets the sign-in: how the backend judged it, when it began, and the Treasury proof. */
        void resetSession() {
            standing = Standing.NONE;
            connectedSince = null;
            proof.reset();
        }

        /** Forgets everything the socket stood for: its sign-in, its prompts, and answers whoever still waits. */
        void drop() {
            resetSession();
            bombSharePrompts.clear();
            answerConnectedUsers(List.of());
        }

        boolean answerConnectedUsers(List<String> users) {
            boolean answered = false;
            for (Consumer<List<String>> waiting; (waiting = connectedUsersCallbacks.poll()) != null; ) {
                answered = true;
                waiting.accept(users);
            }
            return answered;
        }

        /** Whether a finished session proof still belongs here: this attempt is the one open, on its Treasury socket. */
        private boolean ownsProof() {
            return isCurrent(this, State.OPEN) && treasury && socket.isOpen();
        }

        private void sendProof(TreasuryAuthResponse response) {
            ClientThread.check("ConnectionManager Treasury proof");
            if (!ownsProof()) {
                proof.reset();
                return;
            }
            sendPrepared(this, TreasuryAuthResponse.TYPE, serializeTreasuryAuthResponse(response));
            SeqClient.LOGGER.debug("[TreasuryAuth] Minecraft session joined; response sent to backend");
        }

        private void proofFailed(Throwable failure) {
            ClientThread.check("ConnectionManager Treasury proof");
            if (!ownsProof()) {
                SeqClient.LOGGER.debug("[TreasuryAuth] Ignoring a proof failure for a connection that has ended");
                proof.reset();
                return;
            }
            SeqClient.LOGGER.warn("[TreasuryAuth] Minecraft session proof failed: {}",
                    failure == null || failure.getMessage() == null || failure.getMessage().isBlank()
                            ? "Minecraft session verification failed."
                            : failure.getMessage());
            treasuryFailed(this, "Treasury identity verification failed. Restart Minecraft and try again.");
        }
    }

    /** The Treasury socket failed to prove its session: say so, close it, and stop reconnecting. */
    private void treasuryFailed(Attempt failing, String message) {
        failing.proof.reset();
        failing.standing = Standing.REFUSED;
        failing.reconnect = false;
        notify(message);
        failing.reporting = false;
        closeFromClient(failing, BackendCloseClassifier.POLICY_VIOLATION, "Treasury authentication failed");
    }

    /**
     * The backend no longer accepts this socket's sign-in. It closes, to sign in afresh, and when
     * {@code code} is given the token is dropped too, which ends its session. Closed first, so
     * the frames behind this one are dropped and the session's end finds the socket closing.
     */
    private void rejectToken(Attempt refused, String notice, AuthErrorCode code, String message) {
        refused.standing = Standing.REFUSED;
        refused.refusalCounted = true;
        signInBackoff.recordFailure();
        if (notice != null) {
            notify(notice);
        }
        recordAuthRejection(refused);
        closeFromClient(refused, CLOSE_TO_REAUTHENTICATE, "Re-authenticating");
        if (code != null) {
            refused.tokenDropped = true;
            env.tokens().invalidateSession(refused.token, code, message);
        }
    }

    // ── Readiness ──

    /** The latest attempt while its socket is open and signed in with a token the backend hasn't refused, or null. */
    private Attempt signedIn() {
        Attempt latest = attempt;
        return latest != null && latest.socket.isOpen() && latest.signedIn() ? latest : null;
    }

    /** Whether traffic every signed-in account may send, such as the party finder's, can go now. */
    private boolean canSendSignedInTraffic() {
        return signedIn() != null;
    }

    /** {@link #canSendSignedInTraffic()}, logging why {@code what} was dropped when it can't. */
    private boolean canSendSignedInTraffic(String what) {
        if (canSendSignedInTraffic()) {
            return true;
        }
        Attempt latest = attempt;
        SeqClient.LOGGER.warn("[WebSocket] {} dropped state={} standing={}", what, state,
                latest == null ? null : latest.standing);
        return false;
    }

    /** Whether Sequoia-only traffic can go now: signed in, and the backend hasn't called the account a non-member. */
    private boolean canSendMemberTraffic() {
        Attempt ready = signedIn();
        return ready != null && ready.standing != Standing.NON_MEMBER;
    }

    /** Whether the Treasury account's socket is open and has proved its Minecraft session. */
    private boolean canSendTreasuryTraffic() {
        Attempt latest = attempt;
        return latest != null && latest.treasury && latest.socket.isOpen() && latest.proof.isAuthenticated();
    }

    /** Open and signed in, as the rest of the mod means by connected. */
    public static boolean isConnected() {
        return getInstance().canSendSignedInTraffic();
    }

    public static boolean isLiveWarTelemetryReady() {
        ConnectionManager current = getInstance();
        return current.canSendMemberTraffic() && current.currentScope() == WynncraftServerPolicy.Scope.MAIN;
    }

    public static boolean isTreasuryOutConnected() {
        return getInstance().canSendTreasuryTraffic();
    }

    public boolean isOpen() {
        Attempt latest = attempt;
        return latest != null && latest.socket.isOpen();
    }

    boolean isAuthenticated() {
        Attempt latest = attempt;
        return latest != null && latest.signedIn();
    }

    boolean isAuthFailed() {
        Attempt latest = attempt;
        return latest != null && (latest.standing == Standing.REFUSED || latest.standing == Standing.NOT_IN_GUILD);
    }

    boolean isNotInGuild() {
        Attempt latest = attempt;
        return latest != null && latest.standing == Standing.NOT_IN_GUILD;
    }

    public Instant getConnectedSince() {
        Attempt latest = attempt;
        return latest == null ? null : latest.connectedSince;
    }

    public void requestConnectedUsers(Consumer<List<String>> callback) {
        Attempt ready = canSendMemberTraffic() && currentScope() == WynncraftServerPolicy.Scope.MAIN ? attempt : null;
        SeqClient.LOGGER.info("[WebSocket] requestConnectedUsers ready={}", ready != null);
        if (ready == null) {
            callback.accept(List.of());
            return;
        }
        boolean alreadyAsked = !ready.connectedUsersCallbacks.isEmpty();
        ready.connectedUsersCallbacks.add(callback);
        if (!alreadyAsked) {
            askForConnectedUsers(ready);
        }
    }

    /**
     * Asks for the connected users everyone waiting on {@code asking} wants. A request the send
     * throttle holds back goes once it allows; one that can't go at all is answered with no one.
     */
    private void askForConnectedUsers(Attempt asking) {
        if (asking != attempt || asking.connectedUsersCallbacks.isEmpty()) {
            return;
        }
        if (!canSendMemberTraffic() || currentScope() != WynncraftServerPolicy.Scope.MAIN) {
            asking.answerConnectedUsers(List.of());
            return;
        }
        if (!send("get_connected", null)) {
            env.scheduler().after(PRIVILEGED_SEND_THROTTLE_MS, () -> askForConnectedUsers(asking));
        }
    }

    // ── Outgoing messages ──

    /** Sends {@code type} on the latest attempt if what {@link OutboundMessageType} says it needs is in place. */
    private boolean send(String type, JsonObject payload) {
        Attempt latest = attempt;
        if (latest == null) {
            return false;
        }
        OutboundMessageType outbound = OutboundMessageType.of(type);
        if (outbound != null) {
            if (outbound.mainServerOnly() && currentScope() != WynncraftServerPolicy.Scope.MAIN) {
                SeqClient.LOGGER.warn("[WebSocket] Dropping {} outside confirmed main Wynncraft host", type);
                return false;
            }
            if (outbound.needsBearerSession() && !canSendAuthenticated(latest, outbound)) {
                return false;
            }
            if (outbound.throttled() && !canSendThrottleLimited(type)) {
                return false;
            }
        }
        return sendPrepared(latest, type, payload);
    }

    /** Whether the session {@code type} needs is in place on {@code sending}. */
    private boolean canSendAuthenticated(Attempt sending, OutboundMessageType type) {
        if (!sending.signedIn()) {
            SeqClient.LOGGER.warn("[WebSocket] Dropping {}: standing={}", type.wireName(), sending.standing);
            return false;
        }
        if (sending.standing == Standing.NON_MEMBER && type.memberOnly()) {
            SeqClient.LOGGER.debug("[WebSocket] Dropping disabled Sequoia-only feature type={}", type.wireName());
            return false;
        }
        return true;
    }

    private boolean canSendThrottleLimited(String type) {
        long now = env.clockMs().getAsLong();
        if (now < nextPrivilegedSendAtMs) {
            SeqClient.LOGGER.debug("[WebSocket] Throttled {} send", type);
            return false;
        }
        nextPrivilegedSendAtMs = now + PRIVILEGED_SEND_THROTTLE_MS;
        return true;
    }

    /** Hands {@code type} to {@code sending}'s socket; false when the socket isn't open. */
    private boolean sendPrepared(Attempt sending, String type, JsonObject payload) {
        if (payload == null) payload = new JsonObject();
        payload.addProperty("type", type);
        if (TreasuryOutRequest.TYPE.equals(type)) {
            SeqClient.LOGGER.debug(
                    "[WebSocket] send type={} requestId={}", type, extractPrimitiveString(payload, "request_id"));
        } else {
            SeqClient.LOGGER.debug("[WebSocket] send type={} payload={}", type, truncate(payload.toString(), 512));
        }
        return sending.socket.send(GSON.toJson(payload));
    }

    static Map<String, String> buildHandshakeHeaders(String token) {
        return buildHandshakeHeaders(token, ClientVersion.resolveInstalledVersion());
    }

    static Map<String, String> buildHandshakeHeaders(String token, String modVersion) {
        Map<String, String> headers = new LinkedHashMap<>();
        if (token != null && !token.isBlank()) {
            headers.put("Authorization", buildAuthorizationHeaderValue(token));
        }
        if (modVersion != null && !modVersion.isBlank()) {
            headers.put(ClientVersion.MOD_VERSION_HEADER, modVersion.trim());
        }
        return headers;
    }

    static String buildAuthorizationHeaderValue(String token) {
        return "Bearer " + token.trim();
    }

    static JsonObject buildGuildWarSubmissionPayload(GuildWarSubmission submission) {
        JsonObject payload = new JsonObject();
        payload.addProperty("territory", submission.territory());
        payload.addProperty("submitted_by", submission.submittedBy());
        payload.addProperty("submitted_at", submission.submittedAt());
        payload.addProperty("start_time", submission.startTime());

        JsonArray warrers = new JsonArray();
        for (String warrer : submission.warrers()) {
            warrers.add(warrer);
        }
        payload.add("warrers", warrers);

        JsonObject damage = new JsonObject();
        damage.addProperty("low", submission.stats().damageLow());
        damage.addProperty("high", submission.stats().damageHigh());

        JsonObject stats = new JsonObject();
        stats.add("damage", damage);
        stats.addProperty("attack", submission.stats().attackSpeed());
        stats.addProperty("health", submission.stats().health());
        stats.addProperty("defence", submission.stats().defence());

        JsonObject results = new JsonObject();
        results.add("stats", stats);
        payload.add("results", results);

        payload.addProperty("sr", submission.seasonRating());
        if (submission.completedAt() != null && !submission.completedAt().isBlank()) {
            payload.addProperty("completed_at", submission.completedAt());
        }
        return payload;
    }

    static JsonObject buildWarStatusPayload(WarStatusUpdate update) {
        JsonObject payload = new JsonObject();
        payload.addProperty("status", update.status().name());
        if (update.classType() != null) {
            payload.addProperty("class", update.classType().name());
        }
        if (update.territory() != null) {
            payload.addProperty("territory", update.territory());
        }
        if (update.x() != null && update.z() != null) {
            payload.addProperty("x", update.x());
            payload.addProperty("z", update.z());
        }
        return payload;
    }

    static JsonObject buildWarTowerUpdatePayload(WarTowerUpdate update) {
        JsonObject payload = new JsonObject();
        payload.addProperty("territory", update.territory());
        payload.addProperty("health", update.health());
        payload.addProperty("ehp", update.ehp());
        payload.addProperty("dps", update.dps());
        return payload;
    }

    static JsonObject buildBombShareRequestPayload(String canonicalKey, List<BombShareType> requestedTypes) {
        JsonObject payload = new JsonObject();
        payload.addProperty("canonical_key", canonicalKey);
        JsonArray types = new JsonArray();
        if (requestedTypes != null) {
            for (BombShareType requestedType : requestedTypes) {
                if (requestedType != null) {
                    types.add(requestedType.name());
                }
            }
        }
        payload.add("requested_types", types);
        return payload;
    }

    static JsonObject buildBombShareSubmitPayload(String requestId, List<String> worlds) {
        JsonObject payload = new JsonObject();
        payload.addProperty("request_id", requestId);
        JsonArray worldArray = new JsonArray();
        if (worlds != null) {
            for (String world : worlds) {
                if (world != null && !world.isBlank()) {
                    worldArray.add(world.trim());
                }
            }
        }
        payload.add("worlds", worldArray);
        return payload;
    }

    static JsonObject serializeTreasuryOutRequest(TreasuryOutRequest request) {
        return GSON.toJsonTree(request).getAsJsonObject();
    }

    static JsonObject serializeTreasuryAuthResponse(TreasuryAuthResponse response) {
        return GSON.toJsonTree(response).getAsJsonObject();
    }

    private static AuthException unwrapAuthException(Throwable throwable) {
        Throwable cause = throwable;
        while (cause instanceof java.util.concurrent.CompletionException && cause.getCause() != null) {
            cause = cause.getCause();
        }
        if (cause instanceof AuthException authException) {
            return authException;
        }
        return new AuthException(
                AuthErrorCode.NETWORK_FAILURE,
                cause != null && cause.getMessage() != null ? cause.getMessage() : "Authentication request failed.",
                true,
                cause);
    }

    public boolean sendBombShareRequest(String canonicalKey, List<BombShareType> requestedTypes) {
        if (!canSendSignedInTraffic("sendBombShareRequest")) {
            return false;
        }
        if (canonicalKey == null || canonicalKey.isBlank() || requestedTypes == null || requestedTypes.isEmpty()) {
            SeqClient.LOGGER.warn("[WebSocket] sendBombShareRequest dropped invalid payload canonicalKey={}", canonicalKey);
            return false;
        }

        return send("bomb_share_request", buildBombShareRequestPayload(canonicalKey, requestedTypes));
    }

    public boolean sendBombShareSubmit(String requestId, List<String> worlds) {
        if (!canSendSignedInTraffic("sendBombShareSubmit")) {
            return false;
        }
        if (requestId == null || requestId.isBlank() || worlds == null || worlds.isEmpty()) {
            SeqClient.LOGGER.warn("[WebSocket] sendBombShareSubmit dropped invalid payload requestId={}", requestId);
            return false;
        }

        return send("bomb_share_submit", buildBombShareSubmitPayload(requestId, worlds));
    }

    public boolean sendTreasuryOut(TreasuryOutRequest request) {
        if (!TreasuryOutManager.isTreasuryMinecraftAccount(env.game().minecraftUsername())) {
            SeqClient.LOGGER.warn("[WebSocket] sendTreasuryOut dropped because active account is not authorized");
            return false;
        }
        if (request == null
                || request.requestId() == null
                || request.requestId().isBlank()
                || request.amount() == null
                || request.amount().isBlank()
                || request.payouter() == null
                || request.payouter().isBlank()
                || request.reason() == null
                || request.reason().isBlank()) {
            SeqClient.LOGGER.warn(
                    "[WebSocket] sendTreasuryOut dropped invalid payload requestId={}",
                    request == null ? null : request.requestId());
            return false;
        }
        if (!canSendTreasuryTraffic()) {
            SeqClient.LOGGER.warn(
                    "[WebSocket] sendTreasuryOut dropped because Treasury session is not verified requestId={}",
                    request.requestId());
            return false;
        }
        return send(TreasuryOutRequest.TYPE, serializeTreasuryOutRequest(request));
    }

    public void sendGuildChat(String username, String nickname, String message, String avatarUrl) {
        sendGuildChat(username, nickname, message, avatarUrl, List.of());
    }

    public void sendGuildChat(
            String username, String nickname, String message, String avatarUrl, List<ChatItemPreview> itemPreviews) {
        sendGuildChat(username, nickname, message, avatarUrl, itemPreviews, false);
    }

    /** Returns true when handed to the socket; delivery is confirmed by the backend echo. */
    public boolean sendGuildChat(
            String username, String nickname, String message, String avatarUrl,
            List<ChatItemPreview> itemPreviews, boolean shouldBeDisplayed) {
        if (!canSendSignedInTraffic("sendGuildChat")) {
            return false;
        }

        String cleanedMessage = message == null ? "" : message.trim();
        if (cleanedMessage.isBlank()) {
            SeqClient.LOGGER.warn("[ConnectionManager] sendGuildChat dropped: empty message");
            return false;
        }
        JsonObject msg;
        try {
            msg = buildGuildChatPayload(username, nickname, cleanedMessage, avatarUrl, itemPreviews, shouldBeDisplayed);
        } catch (IllegalArgumentException exception) {
            SeqClient.LOGGER.warn(
                    "[ConnectionManager] sendGuildChat dropped: message too long={} max={}",
                    cleanedMessage.length(),
                    MAX_GUILD_CHAT_MESSAGE_LENGTH);
            if (!shouldBeDisplayed) notify("Guild chat message too long.");
            return false;
        }
        SeqClient.LOGGER.info(
                "[ConnectionManager] Sending guild_chat username='{}' nickname='{}' message='{}'",
                extractPrimitiveString(msg, "username"),
                extractPrimitiveString(msg, "nickname"),
                cleanedMessage);
        return send("guild_chat", msg);
    }

    /** Builds the wire payload used by sendGuildChat, before session/throttle checks. */
    static JsonObject buildGuildChatPayload(
            String username, String nickname, String message, String avatarUrl, List<ChatItemPreview> itemPreviews) {
        return buildGuildChatPayload(username, nickname, message, avatarUrl, itemPreviews, false);
    }

    static JsonObject buildGuildChatPayload(
            String username, String nickname, String message, String avatarUrl,
            List<ChatItemPreview> itemPreviews, boolean shouldBeDisplayed) {
        String cleanedMessage = message == null ? "" : message.trim();
        if (cleanedMessage.isBlank()) {
            throw new IllegalArgumentException("Guild chat message is required");
        }
        if (cleanedMessage.length() > MAX_GUILD_CHAT_MESSAGE_LENGTH) {
            throw new IllegalArgumentException("Guild chat message is too long");
        }
        String safeAvatarUrl = sanitizeAvatarUrl(avatarUrl);
        String safeReportedUsername = sanitizeMinecraftUsername(username);
        String safeNickname = sanitizeNickname(nickname);
        JsonObject msg = new JsonObject();
        msg.addProperty("type", "guild_chat");
        msg.addProperty("should_be_displayed", shouldBeDisplayed);
        if (safeReportedUsername != null) {
            msg.addProperty("username", safeReportedUsername);
        }
        if (safeNickname != null) {
            msg.addProperty("nickname", safeNickname);
        }
        msg.addProperty("message", cleanedMessage);
        if (safeAvatarUrl != null) msg.addProperty("avatar_url", safeAvatarUrl);
        JsonArray itemPreviewArray = itemPreviewArray(itemPreviews);
        if (itemPreviewArray.size() > 0) {
            msg.add("item_previews", itemPreviewArray);
        }
        return msg;
    }

    static boolean shouldDisplayGuildChat(JsonObject json, boolean inQueue) {
        var flag = json.get("should_be_displayed");
        return inQueue || (flag != null && flag.isJsonPrimitive() && flag.getAsJsonPrimitive().isBoolean()
                && flag.getAsBoolean());
    }

    static GuildChatMessage parseGuildChatMessage(JsonObject json) {
        String username = guildChatString(json, "username");
        String message = guildChatString(json, "message");
        if (username == null || message == null) return null;
        return new GuildChatMessage(username, guildChatString(json, "nickname"), message);
    }

    /** Called on the client thread so queue eligibility uses the current GUI state. */
    static void dispatchGuildChat(JsonObject json, boolean inQueue) {
        if (!shouldDisplayGuildChat(json, inQueue)) return;
        GuildChatMessage msg = parseGuildChatMessage(json);
        if (msg == null) {
            SeqClient.LOGGER.warn("[WebSocket] Ignoring malformed displayable guild_chat");
        } else {
            deliver(Inbound.GUILD_CHAT, msg);
        }
    }

    /** Rechecks delivery eligibility when the client thread runs, rather than when the socket receives it. */
    static Runnable guildChatDelivery(
            JsonObject json, BooleanSupplier currentSession, BooleanSupplier inQueue) {
        return () -> {
            if (!currentSession.getAsBoolean()) {
                SeqClient.LOGGER.debug("[WebSocket] Ignoring guild_chat from an expired socket or game session");
                return;
            }
            dispatchGuildChat(json, inQueue.getAsBoolean());
        };
    }

    private static String guildChatString(JsonObject json, String key) {
        var value = json.get(key);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
                ? value.getAsString() : null;
    }

    public boolean sendGuildAllianceUpdate(String action, String guildName) {
        String safeAction = action == null ? "" : action.trim().toLowerCase(java.util.Locale.ROOT);
        String safeGuildName = guildName == null ? "" : guildName.trim();
        if (!"formed".equals(safeAction) && !"revoked".equals(safeAction)) {
            SeqClient.LOGGER.warn("[WebSocket] sendGuildAllianceUpdate dropped invalid action={}", action);
            return false;
        }
        if (safeGuildName.isEmpty() || safeGuildName.length() > 64) {
            SeqClient.LOGGER.warn("[WebSocket] sendGuildAllianceUpdate dropped invalid guild='{}'", guildName);
            return false;
        }

        JsonObject msg = new JsonObject();
        msg.addProperty("action", safeAction);
        msg.addProperty("guild_name", safeGuildName);
        return send("guild_alliance_update", msg);
    }

    public static void observeGuildRankEvent(GuildRankEventParser.Event event) {
        getInstance().observe(event);
    }

    private void observe(GuildRankEventParser.Event event) {
        UUID observer = env.game().profileId();
        if (currentScope() != WynncraftServerPolicy.Scope.MAIN || observer == null) return;
        SeqClient.LOGGER.debug(
                "[GuildRank] Observed assignment actor='{}' target='{}' oldRank='{}' newRank='{}'",
                event.actor().displayName(), event.target().displayName(), event.oldRank(), event.newRank());
        guildRankObservations.add(event, observer, env.wallClock().instant());
        sendGuildRankObservations();
    }

    public static void tickGuildRankObservations() {
        getInstance().sendGuildRankObservations();
    }

    private void sendGuildRankObservations() {
        Attempt ready = signedIn();
        UUID observer = env.game().profileId();
        // Observations wait for the membership probe to answer, and go only to a backend that records them.
        if (ready == null || !canSendMemberTraffic() || ready.standing == Standing.PROBING
                || !ready.rankTracking || observer == null
                || currentScope() != WynncraftServerPolicy.Scope.MAIN) return;
        Instant now = env.wallClock().instant();
        for (JsonObject payload : guildRankObservations.due(observer, now)) {
            if (send("guild_rank_event", payload)) {
                guildRankObservations.sent(payload.get("observation_id").getAsString(), now);
                SeqClient.LOGGER.info("[GuildRank] Sent observation id={}", payload.get("observation_id").getAsString());
            }
        }
    }

    public boolean sendGuildMembershipEvent(String action, String actor, String target) {
        String safeAction = action == null ? "" : action.trim().toLowerCase(Locale.ROOT);
        String safeActor = sanitizeMinecraftUsername(actor);
        String safeTarget = sanitizeMinecraftUsername(target);
        if ((!"invited".equals(safeAction) && !"uninvited".equals(safeAction))
                || safeActor == null
                || safeTarget == null) {
            SeqClient.LOGGER.warn(
                    "[WebSocket] sendGuildMembershipEvent dropped invalid action='{}' actor='{}' target='{}'",
                    action,
                    actor,
                    target);
            return false;
        }

        return send("guild_membership_event", buildGuildMembershipEventPayload(safeAction, safeActor, safeTarget));
    }

    static JsonObject buildGuildMembershipEventPayload(String action, String actor, String target) {
        JsonObject payload = new JsonObject();
        payload.addProperty("action", action);
        payload.addProperty("actor", actor);
        payload.addProperty("target", target);
        return payload;
    }

    public boolean sendGuildAllianceSnapshot(Collection<String> guildNames) {
        List<String> safeGuildNames = normalizeGuildAllianceNames(guildNames);
        if (safeGuildNames == null) {
            SeqClient.LOGGER.warn("[WebSocket] sendGuildAllianceSnapshot dropped invalid guild list");
            return false;
        }

        return send("guild_alliance_snapshot", buildGuildAllianceSnapshotPayload(safeGuildNames));
    }

    static JsonObject buildGuildAllianceSnapshotPayload(List<String> guildNames) {
        JsonArray names = new JsonArray();
        for (String guildName : guildNames) {
            names.add(guildName);
        }

        JsonObject payload = new JsonObject();
        payload.add("guild_names", names);
        return payload;
    }

    static List<String> normalizeGuildAllianceNames(Collection<String> guildNames) {
        if (guildNames == null) {
            return null;
        }

        Map<String, String> uniqueNames = new LinkedHashMap<>();
        for (String guildName : guildNames) {
            if (guildName == null) {
                return null;
            }
            String trimmedName = guildName.trim();
            if (trimmedName.isEmpty() || trimmedName.length() > 64 || trimmedName.contains(":")) {
                return null;
            }
            uniqueNames.putIfAbsent(trimmedName.toLowerCase(Locale.ROOT), trimmedName);
        }
        if (uniqueNames.size() > 16) {
            return null;
        }
        return List.copyOf(uniqueNames.values());
    }

    static JsonArray itemPreviewArray(List<ChatItemPreview> itemPreviews) {
        JsonArray previews = new JsonArray();
        if (itemPreviews == null || itemPreviews.isEmpty()) {
            return previews;
        }
        for (ChatItemPreview preview : itemPreviews) {
            if (preview == null || preview.name() == null || preview.name().isBlank()) {
                continue;
            }
            JsonObject json = new JsonObject();
            json.addProperty("name", preview.name());
            if (preview.subtitle() != null && !preview.subtitle().isBlank()) {
                json.addProperty("subtitle", preview.subtitle());
            }
            if (preview.color() != null) {
                json.addProperty("color", preview.color());
            }
            JsonArray attributes = stringArray(preview.attributes());
            if (attributes.size() > 0) {
                json.add("attributes", attributes);
            }
            JsonArray statLines = stringArray(preview.statLines());
            if (statLines.size() > 0) {
                json.add("stat_lines", statLines);
            }
            JsonArray statRolls = statRollArray(preview.statRolls());
            if (statRolls.size() > 0) {
                json.add("stat_rolls", statRolls);
            }
            if (preview.shinyStat() != null) {
                json.add("shiny_stat", shinyStatJson(preview.shinyStat()));
            }
            JsonArray sections = itemSectionArray(preview.sections());
            if (sections.size() > 0) {
                json.add("sections", sections);
            }
            previews.add(json);
        }
        return previews;
    }

    private static JsonArray itemSectionArray(List<ChatItemPreview.Section> sections) {
        JsonArray array = new JsonArray();
        if (sections == null || sections.isEmpty()) {
            return array;
        }
        for (ChatItemPreview.Section section : sections) {
            if (section == null || section.title() == null || section.title().isBlank()) {
                continue;
            }
            JsonArray lines = stringArray(section.lines());
            if (lines.size() == 0) {
                continue;
            }
            JsonObject json = new JsonObject();
            json.addProperty("title", section.title());
            json.add("lines", lines);
            array.add(json);
        }
        return array;
    }

    private static JsonObject shinyStatJson(ChatItemPreview.ShinyStat shinyStat) {
        JsonObject json = new JsonObject();
        if (shinyStat.key() != null && !shinyStat.key().isBlank()) {
            json.addProperty("key", shinyStat.key());
        }
        if (shinyStat.displayName() != null && !shinyStat.displayName().isBlank()) {
            json.addProperty("display_name", shinyStat.displayName());
        }
        json.addProperty("value", shinyStat.value());
        json.addProperty("rerolls", shinyStat.rerolls());
        return json;
    }

    private static JsonArray statRollArray(List<ChatItemPreview.StatRoll> statRolls) {
        JsonArray array = new JsonArray();
        if (statRolls == null || statRolls.isEmpty()) {
            return array;
        }
        for (ChatItemPreview.StatRoll statRoll : statRolls) {
            if (statRoll == null || statRoll.percentage() == null) {
                continue;
            }
            JsonObject json = new JsonObject();
            if (statRoll.apiName() != null && !statRoll.apiName().isBlank()) {
                json.addProperty("api_name", statRoll.apiName());
            }
            if (statRoll.key() != null && !statRoll.key().isBlank()) {
                json.addProperty("key", statRoll.key());
            }
            if (statRoll.displayName() != null && !statRoll.displayName().isBlank()) {
                json.addProperty("display_name", statRoll.displayName());
            }
            json.addProperty("value", statRoll.value());
            json.addProperty("percentage", statRoll.percentage());
            array.add(json);
        }
        return array;
    }

    private static JsonArray stringArray(List<String> values) {
        JsonArray array = new JsonArray();
        if (values == null) {
            return array;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                array.add(value);
            }
        }
        return array;
    }

    public void sendRaidAnnouncement(
            List<String> usernames,
            String raidType,
            int aspectCount,
            int emeraldCount,
            double experienceCount,
            int srCount,
            Map<String, Integer> gambitCounts) {
        if (!canSendSignedInTraffic("sendRaidAnnouncement")) {
            return;
        }
        if (usernames == null || usernames.isEmpty() || raidType == null || raidType.isBlank()) {
            SeqClient.LOGGER.warn(
                    "[WebSocket] sendRaidAnnouncement dropped: invalid payload usernames={} raidType='{}' aspects={} emeralds={} experience={} sr={}",
                    usernames,
                    raidType,
                    aspectCount,
                    emeraldCount,
                    experienceCount,
                    srCount);
            return;
        }
        SeqClient.LOGGER.info(
                "[WebSocket] Sending guild_raid_announcement type={} usernames={} payloadMembers={}",
                raidType,
                usernames.size(),
                usernames);
        JsonObject msg = buildRaidAnnouncementPayload(
                usernames, raidType, aspectCount, emeraldCount, experienceCount, srCount, gambitCounts);
        send("guild_raid_announcement", msg);
    }

    static JsonObject buildRaidAnnouncementPayload(
            List<String> usernames,
            String raidType,
            int aspectCount,
            int emeraldCount,
            double experienceCount,
            int srCount,
            Map<String, Integer> gambitCounts) {
        JsonObject msg = new JsonObject();
        JsonArray names = new JsonArray();
        usernames.forEach(names::add);
        msg.add("usernames", names);
        msg.addProperty("raid_type", raidType);
        msg.addProperty("aspect_count", aspectCount);
        msg.addProperty("emerald_count", emeraldCount);
        msg.addProperty("experience_count", experienceCount);
        msg.addProperty("sr_count", srCount);
        if (gambitCounts != null && !gambitCounts.isEmpty()) {
            JsonObject counts = new JsonObject();
            gambitCounts.forEach(counts::addProperty);
            msg.add("gambit_counts", counts);
        }
        return msg;
    }

    public void sendGuildBankEvent(
            String action,
            String player,
            Integer quantity,
            String itemName,
            String charges,
            String accessTier,
            String rawMessage) {
        if (!canSendSignedInTraffic("sendGuildBankEvent")) {
            return;
        }
        if (action == null || action.isBlank() || player == null || player.isBlank() || itemName == null
                || itemName.isBlank() || accessTier == null || accessTier.isBlank() || rawMessage == null
                || rawMessage.isBlank()) {
            SeqClient.LOGGER.warn("[WebSocket] sendGuildBankEvent dropped: invalid payload");
            return;
        }

        JsonObject msg = new JsonObject();
        msg.addProperty("action", action);
        msg.addProperty("player", player.trim());
        if (quantity != null) {
            msg.addProperty("quantity", quantity);
        }
        msg.addProperty("item_name", itemName.trim());
        if (charges != null && !charges.isBlank()) {
            msg.addProperty("charges", charges.trim());
        }
        msg.addProperty("access_tier", accessTier.trim());
        msg.addProperty("raw_message", rawMessage.trim());
        SeqClient.LOGGER.info(
                "[WebSocket] Sending guild_bank_event action={} player='{}' item='{}'",
                action,
                player,
                itemName);
        send("guild_bank_event", msg);
    }

    public void sendGuildStorageSnapshot(long emeraldCurrent, long emeraldMax, long aspectCurrent, long aspectMax) {
        if (!canSendSignedInTraffic("sendGuildStorageSnapshot")) {
            return;
        }
        if (emeraldCurrent < 0 || emeraldMax <= 0 || aspectCurrent < 0 || aspectMax <= 0) {
            SeqClient.LOGGER.warn(
                    "[WebSocket] sendGuildStorageSnapshot dropped invalid payload emerald={}/{} aspect={}/{}",
                    emeraldCurrent,
                    emeraldMax,
                    aspectCurrent,
                    aspectMax);
            return;
        }

        JsonObject msg = new JsonObject();
        msg.addProperty("emerald_current", emeraldCurrent);
        msg.addProperty("emerald_max", emeraldMax);
        msg.addProperty("aspect_current", aspectCurrent);
        msg.addProperty("aspect_max", aspectMax);
        SeqClient.LOGGER.info(
                "[WebSocket] Sending guild_storage_snapshot emerald={}/{} aspect={}/{}",
                emeraldCurrent,
                emeraldMax,
                aspectCurrent,
                aspectMax);
        send("guild_storage_snapshot", msg);
    }

    public void sendGuildStorageReward(
            String senderUsername,
            String recipientUsername,
            String resourceType,
            long amount,
            int count,
            Instant windowStartedAt) {
        if (!canSendSignedInTraffic("sendGuildStorageReward")) {
            return;
        }

        String safeSender = sanitizeMinecraftUsername(senderUsername);
        String safeRecipient = sanitizeMinecraftUsername(recipientUsername);
        String normalizedResourceType = resourceType == null ? "" : resourceType.trim().toLowerCase(Locale.ROOT);
        if (safeSender == null
                || safeRecipient == null
                || count <= 0
                || amount <= 0
                || windowStartedAt == null
                || (!normalizedResourceType.equals("emeralds") && !normalizedResourceType.equals("aspects"))) {
            SeqClient.LOGGER.warn(
                    "[WebSocket] sendGuildStorageReward dropped invalid payload sender='{}' recipient='{}' resource='{}' amount={} count={} windowStartedAt={}",
                    senderUsername,
                    recipientUsername,
                    resourceType,
                    amount,
                    count,
                    windowStartedAt);
            return;
        }

        JsonObject msg = new JsonObject();
        msg.addProperty("sender_username", safeSender);
        msg.addProperty("recipient_username", safeRecipient);
        msg.addProperty("resource_type", normalizedResourceType);
        msg.addProperty("amount", amount);
        msg.addProperty("count", count);
        msg.addProperty("window_started_at", windowStartedAt.toString());
        SeqClient.LOGGER.info(
                "[WebSocket] Sending guild_storage_reward sender='{}' recipient='{}' resource='{}' amount={} count={} windowStartedAt={}",
                safeSender,
                safeRecipient,
                normalizedResourceType,
                amount,
                count,
                windowStartedAt);
        send("guild_storage_reward", msg);
    }

    public boolean sendGuildWarSubmission(GuildWarSubmission submission) {
        if (submission == null
                || submission.territory() == null
                || submission.territory().isBlank()
                || submission.submittedBy() == null
                || submission.submittedBy().isBlank()
                || submission.submittedAt() == null
                || submission.submittedAt().isBlank()
                || submission.startTime() == null
                || submission.startTime().isBlank()
                || submission.warrers() == null
                || submission.warrers().isEmpty()
                || submission.stats() == null) {
            SeqClient.LOGGER.warn("[WebSocket] sendGuildWarSubmission dropped: invalid payload");
            return false;
        }

        for (String warrer : submission.warrers()) {
            if (warrer == null || !MC_USERNAME_PATTERN.matcher(warrer).matches()) {
                SeqClient.LOGGER.warn("[WebSocket] sendGuildWarSubmission dropped invalid warrer={}", warrer);
                return false;
            }
        }

        return enqueueGuildWarEvent("guild_war_submission", buildGuildWarSubmissionPayload(submission));
    }

    public boolean sendGuildWarQueue(GuildWarQueueSubmission submission) {
        if (submission == null
                || submission.territory() == null
                || submission.territory().isBlank()
                || submission.submittedBy() == null
                || submission.submittedBy().isBlank()
                || submission.submittedAt() == null
                || submission.submittedAt().isBlank()
                || submission.defenseRating() == null
                || submission.defenseRating().isBlank()
                || submission.queueMinutes() < 1
                || submission.queueMinutes() > 60) {
            SeqClient.LOGGER.warn("[WebSocket] sendGuildWarQueue dropped: invalid payload");
            return false;
        }

        JsonObject msg = new JsonObject();
        msg.addProperty("territory", submission.territory());
        msg.addProperty("submitted_by", submission.submittedBy());
        msg.addProperty("submitted_at", submission.submittedAt());
        msg.addProperty("defense_rating", submission.defenseRating());
        msg.addProperty("queue_minutes", submission.queueMinutes());

        return enqueueGuildWarEvent("guild_war_queue", msg);
    }

    public boolean sendGuildWarQueueCancellation(GuildWarQueueCancellation cancellation) {
        if (cancellation == null
                || cancellation.territory() == null
                || cancellation.territory().isBlank()
                || cancellation.submittedBy() == null
                || cancellation.submittedBy().isBlank()
                || cancellation.submittedAt() == null
                || cancellation.submittedAt().isBlank()) {
            SeqClient.LOGGER.warn("[WebSocket] sendGuildWarQueueCancellation dropped: invalid payload");
            return false;
        }

        JsonObject msg = new JsonObject();
        msg.addProperty("territory", cancellation.territory());
        msg.addProperty("submitted_by", cancellation.submittedBy());
        msg.addProperty("submitted_at", cancellation.submittedAt());
        return enqueueGuildWarEvent("guild_war_queue_cancel", msg);
    }

    public boolean sendWarStatus(WarStatusUpdate update) {
        if (update == null
                || (update.status() != WarStatusUpdate.Status.REMOVE && update.classType() == null)
                || !canSendMemberTraffic()
                || currentScope() != WynncraftServerPolicy.Scope.MAIN) {
            return false;
        }
        return send("war_status", buildWarStatusPayload(update));
    }

    public boolean sendWarTowerUpdate(WarTowerUpdate update) {
        if (update == null
                || update.territory() == null
                || update.territory().isBlank()
                || !Float.isFinite(update.health())
                || update.health() < 0.0f
                || update.health() > 1.0f
                || update.ehp() < 0L
                || update.dps() < 0L
                || !canSendMemberTraffic()
                || currentScope() != WynncraftServerPolicy.Scope.MAIN) {
            return false;
        }
        return send("war_tower_update", buildWarTowerUpdatePayload(update));
    }

    public static void flushPendingOutbound() {
        getInstance().flushPendingGuildWarEvents();
    }

    /**
     * Keeps a war event until a member's socket can take it. Refused off Wynncraft; accepted and
     * dropped for a non-member, whose war tracking the backend doesn't want.
     */
    private boolean enqueueGuildWarEvent(String type, JsonObject payload) {
        if (currentScope() == WynncraftServerPolicy.Scope.BLOCKED) {
            SeqClient.LOGGER.warn("[WebSocket] {} dropped outside main Wynncraft host", type);
            return false;
        }
        Attempt latest = attempt;
        if (latest != null && latest.standing == Standing.NON_MEMBER) {
            SeqClient.LOGGER.debug("[WebSocket] {} disabled for non-member session", type);
            return true;
        }
        if (!pendingGuildWarEvents.offer(type, payload)) {
            SeqClient.LOGGER.warn("[WebSocket] War event buffer full; could not retain {} territory='{}'",
                    type, payload.get("territory").getAsString());
            return false;
        }
        SeqClient.LOGGER.info("[WebSocket] Buffered {} territory='{}' submittedAt={}",
                type, payload.get("territory").getAsString(), payload.get("submitted_at").getAsString());
        flushPendingGuildWarEvents();
        // Accepted for delivery, even when the socket or throttle prevents an immediate send.
        return true;
    }

    private void flushPendingGuildWarEvents() {
        pendingGuildWarEvents.flushOne(this::sendGuildWarEventNow);
    }

    private boolean sendGuildWarEventNow(String type, JsonObject payload) {
        if (!canSendMemberTraffic() || currentScope() != WynncraftServerPolicy.Scope.MAIN || !send(type, payload)) {
            return false;
        }
        SeqClient.LOGGER.info("[WebSocket] Sent buffered {} territory='{}' submittedAt={}",
                type, payload.get("territory").getAsString(), payload.get("submitted_at").getAsString());
        return true;
    }

    public void sendPartyClassUpdate(WynnClassType classType) {
        if (classType == null) {
            SeqClient.LOGGER.warn("[WebSocket] sendPartyClassUpdate dropped classType=null");
            return;
        }
        if (!canSendSignedInTraffic("sendPartyClassUpdate")) {
            return;
        }
        SeqClient.LOGGER.info("[WebSocket] Sending party_class_update classType={}", classType);
        JsonObject msg = new JsonObject();
        msg.addProperty("class_type", classType.name());
        send("party_class_update", msg);
    }

    public boolean sendPartySyncSnapshot(
            long listingId, boolean active, String leaderUsername, List<String> memberUsernames) {
        if (!canSendSignedInTraffic("sendPartySyncSnapshot")) {
            return false;
        }

        JsonObject msg = buildPartySyncSnapshotPayload(listingId, active, leaderUsername, memberUsernames);
        SeqClient.LOGGER.info(
                "[WebSocket] Sending party_sync_snapshot listingId={} active={} leader={} members={} usernames={}",
                listingId,
                active,
                leaderUsername,
                msg.getAsJsonArray("member_usernames").size(),
                memberUsernames);
        return send("party_sync_snapshot", msg);
    }

    static JsonObject buildPartySyncSnapshotPayload(
            long listingId, boolean active, String leaderUsername, List<String> memberUsernames) {
        if (listingId <= 0L) {
            throw new IllegalArgumentException("listingId must be positive");
        }

        JsonObject msg = new JsonObject();
        msg.addProperty("listing_id", listingId);
        msg.addProperty("active", active);
        if (leaderUsername != null && !leaderUsername.isBlank()) {
            msg.addProperty("leader_username", leaderUsername);
        }
        JsonArray usernames = new JsonArray();
        if (memberUsernames != null) {
            for (String memberUsername : memberUsernames) {
                if (memberUsername != null && !memberUsername.isBlank()) {
                    usernames.add(memberUsername);
                }
            }
        }
        msg.add("member_usernames", usernames);
        return msg;
    }

    public boolean sendPartySyncMemberRemoved(String username, String reason) {
        if (!canSendSignedInTraffic("sendPartySyncMemberRemoved")) {
            return false;
        }
        if (username == null || username.isBlank() || reason == null || reason.isBlank()) {
            SeqClient.LOGGER.warn("[WebSocket] sendPartySyncMemberRemoved dropped invalid payload");
            return false;
        }

        JsonObject msg = new JsonObject();
        msg.addProperty("username", username);
        msg.addProperty("reason", reason);
        SeqClient.LOGGER.info("[WebSocket] Sending party_sync_member_removed username={} reason={}", username, reason);
        return send("party_sync_member_removed", msg);
    }

    public void sendLocalPartyClassUpdate() {
        WynnClassType classType = env.game().localClassType();
        if (classType == null) {
            return;
        }
        sendPartyClassUpdate(classType);
    }

    // ── Incoming messages ──

    private void handleMessage(Attempt receiving, String message, Object receivedConnection, Object receivedAccount) {
        SeqClient.LOGGER.debug("[WebSocket] onMessage raw={} chars", message != null ? message.length() : -1);
        message = BridgeMedia.rewrite(message);
        if (message == null) return;
        try {
            JsonObject json = GSON.fromJson(message, JsonObject.class);
            if (json == null || !json.has("type")) {
                SeqClient.LOGGER.warn("[WebSocket] Dropping message without type: {}", truncate(message, 512));
                return;
            }
            String type = json.get("type").getAsString();
            if ("treasury_out_recorded".equals(type)
                    || TreasuryAuthResponse.CHALLENGE_TYPE.equals(type)
                    || TreasuryAuthResponse.AUTHENTICATED_TYPE.equals(type)) {
                SeqClient.LOGGER.debug(
                        "[WebSocket] Received message type={} requestId={}",
                        type,
                        extractPrimitiveString(json, "request_id"));
            } else {
                SeqClient.LOGGER.info("[WebSocket] Received message type={} payload={}", type, truncate(message, 512));
            }

            switch (type) {
                case TreasuryAuthResponse.CHALLENGE_TYPE -> {
                    if (!receiving.treasury) {
                        SeqClient.LOGGER.warn("[TreasuryAuth] Ignoring challenge on an authenticated WebSocket");
                        return;
                    }
                    String nonce = extractPrimitiveString(json, "nonce");
                    boolean supportedProof = json.has("proof_version")
                            && json.get("proof_version").isJsonPrimitive()
                            && json.getAsJsonPrimitive("proof_version").isNumber()
                            && "2".equals(json.get("proof_version").getAsString());
                    if (!receiving.proof.handleChallenge(nonce, supportedProof ? 2 : 0)) {
                        SeqClient.LOGGER.warn("[TreasuryAuth] Ignoring invalid or unexpected challenge");
                    }
                }
                case TreasuryAuthResponse.AUTHENTICATED_TYPE -> {
                    String nonce = extractPrimitiveString(json, "nonce");
                    if (!receiving.treasury
                            || !receiving.proof.confirm(nonce, extractPrimitiveString(json, "client_nonce"))) {
                        SeqClient.LOGGER.warn("[TreasuryAuth] Ignoring uncorrelated authentication confirmation");
                        return;
                    }
                    receiving.connectedSince = env.wallClock().instant();
                    receiving.reconnect = true;
                    receiving.report("Treasury OUT identity verified as cinfrascitizen.");
                    receiving.reporting = false;
                    SeqClient.LOGGER.info("[TreasuryAuth] Minecraft session proof accepted by backend");
                }
                case "authenticated" -> signedIn(receiving, json);
                case "guild_rank_recorded" -> {
                    String observationId = extractPrimitiveString(json, "observation_id");
                    guildRankObservations.acknowledge(observationId);
                    SeqClient.LOGGER.info("[GuildRank] Backend recorded observation id={}", observationId);
                }
                case "connected_users" -> {
                    boolean wasMembershipProbe = receiving.standing == Standing.PROBING;
                    if (wasMembershipProbe || receiving.standing == Standing.NON_MEMBER) {
                        receiving.standing = Standing.MEMBER;
                    }
                    List<String> users = new ArrayList<>();
                    json.getAsJsonArray("users").forEach(el -> users.add(el.getAsString()));
                    SeqClient.LOGGER.info(
                            "[WebSocket] connected_users received count={} waiting={}",
                            users.size(),
                            receiving.connectedUsersCallbacks.size());
                    if (!receiving.answerConnectedUsers(users) && !wasMembershipProbe) {
                        SeqClient.LOGGER.warn("[WebSocket] connected_users had no callback listener");
                    }
                }
                case "bomb_share_prompt" -> {
                    List<BombShareType> requestedTypes =
                            parseBombShareTypes(json.has("requested_types") ? json.getAsJsonArray("requested_types") : null);
                    BombSharePromptMessage prompt = new BombSharePromptMessage(
                            extractPrimitiveString(json, "request_id"),
                            extractPrimitiveString(json, "requester_username"),
                            extractPrimitiveString(json, "canonical_key"),
                            requestedTypes,
                            json.has("expires_at") && !json.get("expires_at").isJsonNull()
                                    ? Instant.parse(json.get("expires_at").getAsString())
                                    : null,
                            json.has("first_prompt")
                                    && json.get("first_prompt").isJsonPrimitive()
                                    && json.get("first_prompt").getAsBoolean());
                    if (prompt.requestId() != null) {
                        receiving.bombSharePrompts.put(prompt.requestId(), prompt);
                    }
                    deliver(Inbound.BOMB_SHARE_PROMPT, prompt);
                }
                case "bomb_share_result" -> {
                    BombShareResultMessage result = new BombShareResultMessage(
                            extractPrimitiveString(json, "request_id"),
                            extractPrimitiveString(json, "canonical_key"),
                            parseBombShareTypes(json.has("requested_types") ? json.getAsJsonArray("requested_types") : null),
                            parsePrimitiveStringArray(json.has("worlds") ? json.getAsJsonArray("worlds") : null),
                            json.has("share_count") && json.get("share_count").isJsonPrimitive()
                                    ? json.get("share_count").getAsInt()
                                    : 0);
                    if (result.requestId() != null) {
                        receiving.bombSharePrompts.remove(result.requestId());
                    }
                    deliver(Inbound.BOMB_SHARE_RESULT, result);
                }
                case "treasury_out_recorded" -> settleTreasury(json, type);
                case "guild_storage_snapshot" -> {
                    long emeraldCurrent = json.get("emerald_current").getAsLong();
                    long emeraldMax = json.get("emerald_max").getAsLong();
                    long aspectCurrent = json.get("aspect_current").getAsLong();
                    long aspectMax = json.get("aspect_max").getAsLong();
                    SeqClient.LOGGER.info(
                            "[WebSocket] Applying guild_storage_snapshot emerald={}/{} aspect={}/{}",
                            emeraldCurrent,
                            emeraldMax,
                            aspectCurrent,
                            aspectMax);
                    env.game().applyGuildStorageSnapshot(emeraldCurrent, emeraldMax, aspectCurrent, aspectMax);
                }
                case "guild_chat" -> {
                    // Checked against the server connection and account the message arrived in.
                    ConnectionEnvironment.Game game = env.game();
                    guildChatDelivery(json,
                            () -> receiving.socket.isOpen() && receiving.signedIn()
                                    && receivedConnection != null && receivedConnection == game.serverConnection()
                                    && receivedAccount != null && receivedAccount == game.account(),
                            game::inServerQueue)
                            .run();
                }
                case "discord_chat" -> {
                    String username = json.get("username").getAsString();
                    String msg = json.get("message").getAsString();
                    ConfigManager configManager = SeqClient.getConfigManager();
                    if (configManager != null
                            && shouldIgnoreDiscordChatSender(username, configManager.ignoredBridgeUsers())) {
                        SeqClient.LOGGER.debug("[WebSocket] Ignoring discord_chat from {}", username);
                        return;
                    }
                    // The Discord user id is the only stable handle on a bridge sender: display names
                    // change and collide. Older backends don't send it; matching then falls back to the name.
                    deliver(Inbound.DISCORD_CHAT, new DiscordChatMessage(username, msg, extractPrimitiveString(json, "discord_id")));
                }
                case "party_finder_update" -> deliver(Inbound.PARTY_FINDER_UPDATE, new PartyFinderUpdateMessage(
                        json.has("action") && !json.get("action").isJsonNull() ? json.get("action").getAsString() : "unknown",
                        json.getAsJsonObject("listing")));
                case "raid_profile_update" -> {
                    if (json.has("profile") && json.get("profile").isJsonObject()) {
                        deliver(Inbound.RAID_PROFILE_UPDATE, new RaidProfileUpdateMessage(
                                extractPrimitiveString(json, "action"), json.getAsJsonObject("profile")));
                    }
                }
                case "party_finder_invite" -> deliver(Inbound.PARTY_FINDER_INVITE, new PartyFinderInviteMessage(
                        json.get("listing_id").getAsLong(),
                        json.get("inviter_uuid").getAsString(),
                        json.get("invite_token").getAsString(),
                        json.has("listing") && json.get("listing").isJsonObject() ? json.getAsJsonObject("listing") : null));
                case "party_finder_stale_warning" -> deliver(Inbound.PARTY_FINDER_STALE_WARNING, new PartyFinderStaleWarningMessage(
                        extractPrimitiveString(json, "reason"),
                        json.get("listing_id").getAsLong(),
                        json.has("disband_at") && !json.get("disband_at").isJsonNull()
                                ? Instant.parse(json.get("disband_at").getAsString())
                                : null,
                        json.has("minutes_remaining") ? json.get("minutes_remaining").getAsLong() : 0L));
                case "error" -> handleBackendError(receiving, json);
                default -> SeqClient.LOGGER.warn("[WebSocket] Unhandled incoming message type={}", type);
            }
        } catch (Exception e) {
            SeqClient.LOGGER.error("[WebSocket] Failed to handle message payload={}", truncate(message, 512), e);
        }
    }

    /** The backend accepted the token: the socket is signed in, and probes whether the account is a member. */
    private void signedIn(Attempt receiving, JsonObject json) {
        if (receiving.treasury) {
            SeqClient.LOGGER.warn("[TreasuryAuth] Ignoring bearer-authenticated response on Treasury-only WebSocket");
            return;
        }
        authRejections = 0;
        signInBackoff.reset();
        receiving.rankTracking = json.has("guild_rank_tracking_supported")
                && json.get("guild_rank_tracking_supported").isJsonPrimitive()
                && json.get("guild_rank_tracking_supported").getAsBoolean();
        if (receiving.rankTracking) {
            SeqClient.LOGGER.info("[GuildRank] Backend supports rank tracking");
        } else {
            SeqClient.LOGGER.warn("[GuildRank] Backend did not advertise rank tracking; observations are held for up to "
                    + "10 minutes. Reconnect after the backend deployment finishes.");
        }
        receiving.connectedSince = env.wallClock().instant();
        receiving.reconnect = true;
        // Last, so a reader on another thread that sees the sign-in sees all of the above too.
        receiving.standing = Standing.PROBING;
        String discordUser = discordUsernameIn(json);
        discordNameDelivery(discordUser, receiving.account,
                () -> env.tokens().sessionId() == receiving.session,
                SeqClient.getConfigManager()::setDiscordUsername).run();
        if (discordUser != null) {
            receiving.report("Connected as " + discordUser);
        }
        receiving.reporting = false;
        sendPrepared(receiving, "get_connected", null);
        flushPendingGuildWarEvents();
        sendLocalPartyClassUpdate();
    }

    private void handleBackendError(Attempt receiving, JsonObject json) {
        String error = extractBackendErrorMessage(json);
        String backendCode = extractBackendErrorCode(json);
        String capability = extractCapability(json);
        int status = extractStatusCode(json);

        if (settleTreasury(json, "error")) {
            if ("token_invalid".equalsIgnoreCase(backendCode)) {
                rejectToken(receiving, null, AuthErrorCode.TOKEN_INVALID,
                        "Backend rejected the stored token. Re-authentication required.");
            } else if ("mod_version_unsupported".equalsIgnoreCase(backendCode)) {
                receiving.reconnect = false;
            }
            return;
        }

        BackendErrorClassifier.Kind kind = BackendErrorClassifier.classify(
                new BackendErrorClassifier.BackendError(
                        extractPrimitiveString(json, "request_id"),
                        backendCode,
                        status,
                        capability,
                        error,
                        extractPrimitiveString(json, "context"),
                        extractPrimitiveString(json, "request_type")),
                receiving.treasury);
        if (kind != BackendErrorClassifier.Kind.GUILD_CHAT_SENDER_NOT_IN_GUILD
                && kind != BackendErrorClassifier.Kind.NOT_A_SEQUOIA_MEMBER) {
            SeqClient.LOGGER.warn("[WebSocket] Backend error status={} code={} kind={} message={}", status, backendCode, kind, error);
        }

        switch (kind) {
            case TREASURY_AUTH_FAILED -> treasuryFailed(receiving, "Treasury identity verification failed: " + error);
            case GUILD_CHAT_SENDER_NOT_IN_GUILD -> SeqClient.LOGGER.info(
                    "[WebSocket] Guild chat relay rejected because sender is not in guild");
            case NOT_A_SEQUOIA_MEMBER -> disableMemberFeatures(receiving);
            case VERSION_UNSUPPORTED -> {
                if (shouldDisableReconnectForVersionRejection(capability)) {
                    receiving.reconnect = false;
                }
                maybeNotifyVersionRejection(receiving, capability, extractMinimumSafeVersion(json), error);
            }
            case SESSION_AUTH_INVALID -> rejectToken(receiving,
                    "Invalid auth request. Run /seq connect to start a fresh backend session.", null, null);
            case TOKEN_INVALID, TOKEN_EXPIRED -> {
                boolean expired = kind == BackendErrorClassifier.Kind.TOKEN_EXPIRED;
                String rejection = expired
                        ? "Backend token expired. Re-authentication required."
                        : "Backend rejected the stored token. Re-authentication required.";
                rejectToken(receiving, rejection,
                        expired ? AuthErrorCode.TOKEN_EXPIRED : AuthErrorCode.TOKEN_INVALID, rejection);
            }
            case PARTY_FINDER -> env.game().showPartyFinderError(error);
            case CAPABILITY_DENIED -> {
                SeqClient.LOGGER.info("[WebSocket] Capability authorization rejected capability={} message={}", capability, error);
                notify(formatCapabilityAccessDenied(capability, error));
            }
            case NOT_IN_GUILD -> {
                receiving.standing = Standing.NOT_IN_GUILD;
                notify("Access denied by backend authorization.");
                SeqClient.LOGGER.warn("[WebSocket] Guild error detected; disabling auto-reconnect and closing socket");
                receiving.reconnect = false;
                closeFromClient(receiving, NORMAL_CLOSE, "");
            }
            case VALIDATION -> notify(error);
            case OTHER -> {
                if (otherErrors.shouldShow(error)) {
                    notify("Error: " + error);
                }
            }
        }
    }

    private void disableMemberFeatures(Attempt receiving) {
        pendingGuildWarEvents.clear();
        if (receiving.standing == Standing.NON_MEMBER) {
            SeqClient.LOGGER.debug("[WebSocket] Additional non-member feature rejection suppressed");
            return;
        }
        if (receiving.signedIn()) {
            receiving.standing = Standing.NON_MEMBER;
        }
        SeqClient.LOGGER.info("[WebSocket] Non-member session detected; Sequoia-only features are disabled");
        notify("You are not a Sequoia member. Sequoia-only features are disabled for this session.");
    }

    /**
     * Hands a Treasury OUT reply to the request it names, and says whether one claimed it: a
     * recording, or an error carrying the request's id. The request settles itself, so a reply
     * that arrives after its socket ended still completes it without touching the connection.
     */
    private static boolean settleTreasury(JsonObject json, String type) {
        if ("treasury_out_recorded".equals(type)) {
            deliver(Inbound.TREASURY_OUT_RECORDED, GSON.fromJson(json, TreasuryOutRecordedMessage.class));
            return true;
        }
        String requestId = extractPrimitiveString(json, "request_id");
        return "error".equals(type) && requestId != null && deliver(Inbound.TREASURY_OUT_ERROR,
                new TreasuryOutErrorMessage(requestId, extractBackendErrorCode(json), extractBackendErrorMessage(json)));
    }

    /** A frame from a socket this client closed, or replaced: only a Treasury OUT reply still counts. */
    private static void settleTreasuryReply(String message) {
        try {
            JsonObject json = GSON.fromJson(message, JsonObject.class);
            String type = json == null ? null : extractPrimitiveString(json, "type");
            if (type == null || !settleTreasury(json, type)) {
                SeqClient.LOGGER.debug("[WebSocket] Ignoring message from a connection that was ended, refused or replaced");
            }
        } catch (RuntimeException e) {
            SeqClient.LOGGER.debug("[WebSocket] Ignoring unreadable message from an ended connection", e);
        }
    }

    // ── Who hears what the backend sends ──

    /** A message type the backend sends, and what it is read into; see {@link #listen}. */
    public record Inbound<T>(String name) {
        public static final Inbound<GuildChatMessage> GUILD_CHAT = new Inbound<>("guild_chat");
        public static final Inbound<DiscordChatMessage> DISCORD_CHAT = new Inbound<>("discord_chat");
        public static final Inbound<PartyFinderUpdateMessage> PARTY_FINDER_UPDATE = new Inbound<>("party_finder_update");
        public static final Inbound<PartyFinderInviteMessage> PARTY_FINDER_INVITE = new Inbound<>("party_finder_invite");
        public static final Inbound<PartyFinderStaleWarningMessage> PARTY_FINDER_STALE_WARNING =
                new Inbound<>("party_finder_stale_warning");
        public static final Inbound<RaidProfileUpdateMessage> RAID_PROFILE_UPDATE = new Inbound<>("raid_profile_update");
        public static final Inbound<BombSharePromptMessage> BOMB_SHARE_PROMPT = new Inbound<>("bomb_share_prompt");
        public static final Inbound<BombShareResultMessage> BOMB_SHARE_RESULT = new Inbound<>("bomb_share_result");
        public static final Inbound<TreasuryOutRecordedMessage> TREASURY_OUT_RECORDED = new Inbound<>("treasury_out_recorded");
        /** An error naming a request id. A listener claims the ones it sent; only those skip the usual error handling. */
        public static final Inbound<TreasuryOutErrorMessage> TREASURY_OUT_ERROR = new Inbound<>("treasury_out_error");
    }

    /** Hands each {@code type} message to {@code listener}, on the game thread, from now on. */
    public static <T> void listen(Inbound<T> type, Consumer<? super T> listener) {
        claim(type, message -> {
            listener.accept(message);
            return true;
        });
    }

    /** As {@link #listen}, for a listener that says whether a message was its own to handle. */
    @SuppressWarnings("unchecked")
    public static <T> void claim(Inbound<T> type, Predicate<? super T> listener) {
        listeners.computeIfAbsent(type, ignored -> new CopyOnWriteArrayList<>()).add((Predicate<Object>) listener);
    }

    /** Hands {@code message} to everyone listening for {@code type}; says whether one claimed it. */
    private static <T> boolean deliver(Inbound<T> type, T message) {
        List<Predicate<Object>> heard = listeners.getOrDefault(type, List.of());
        if (heard.isEmpty()) {
            SeqClient.LOGGER.debug("[WebSocket] Nobody listens for {}", type.name());
        }
        boolean claimed = false;
        for (Predicate<Object> listener : heard) {
            claimed |= listener.test(message);
        }
        return claimed;
    }

    public Optional<BombSharePromptMessage> getPendingBombSharePrompt(String requestId) {
        Attempt latest = attempt;
        if (latest == null || requestId == null || requestId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(latest.bombSharePrompts.get(requestId));
    }

    public Optional<BombSharePromptMessage> removePendingBombSharePrompt(String requestId) {
        Attempt latest = attempt;
        if (latest == null || requestId == null || requestId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(latest.bombSharePrompts.remove(requestId));
    }

    public boolean hasPendingBombSharePrompt(String requestId) {
        return getPendingBombSharePrompt(requestId).isPresent();
    }

    private static String truncate(String input, int maxLength) {
        if (input == null) {
            return "null";
        }
        if (input.length() <= maxLength) {
            return input;
        }
        return input.substring(0, maxLength) + "...";
    }

    public boolean isDiscordLinked() {
        return hasDiscordUsername(getLinkedDiscordUsername());
    }

    public String getLinkedDiscordUsername() {
        return SeqClient.getConfigManager().getDiscordUsername(env.game().profileId());
    }

    static boolean shouldUseTreasuryOnlyConnection(String activeMinecraftUsername) {
        return TreasuryOutManager.isTreasuryMinecraftAccount(activeMinecraftUsername);
    }

    private static String sanitizeAvatarUrl(String avatarUrl) {
        if (avatarUrl == null || avatarUrl.isBlank()) {
            return null;
        }
        String trimmed = avatarUrl.trim();
        if (!URL_PATTERN.matcher(trimmed).matches()) {
            return null;
        }
        try {
            URI uri = URI.create(trimmed);
            String scheme = uri.getScheme();
            if (scheme == null || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))) {
                return null;
            }
            if (uri.getHost() == null || uri.getHost().isBlank()) {
                return null;
            }
            return trimmed;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String sanitizeMinecraftUsername(String username) {
        if (username == null || username.isBlank()) {
            return null;
        }
        String trimmed = username.trim();
        if (!MC_USERNAME_PATTERN.matcher(trimmed).matches()) {
            return null;
        }
        return trimmed;
    }

    private static String sanitizeNickname(String nickname) {
        if (nickname == null || nickname.isBlank()) {
            return null;
        }
        String trimmed = nickname.trim();
        if (trimmed.length() > 64) {
            return null;
        }
        return trimmed;
    }

    private static int extractStatusCode(JsonObject json) {
        if (json == null) {
            return -1;
        }
        if (json.has("status") && json.get("status").isJsonPrimitive()) {
            try {
                return json.get("status").getAsInt();
            } catch (Exception ignored) {
            }
        }
        if (json.has("code") && json.get("code").isJsonPrimitive()) {
            try {
                return json.get("code").getAsInt();
            } catch (Exception ignored) {
            }
        }
        return -1;
    }

    private static String extractBackendErrorCode(JsonObject json) {
        return extractPrimitiveString(json, "code");
    }

    private static String extractBackendErrorMessage(JsonObject json) {
        if (json == null) {
            return "Unknown backend error";
        }
        String message = extractPrimitiveString(json, "message");
        if (message != null) {
            return message;
        }
        message = extractPrimitiveString(json, "error");
        if (message != null) {
            return message;
        }
        message = extractPrimitiveString(json, "detail");
        if (message != null) {
            return message;
        }
        return "Unknown backend error";
    }

    private static String extractMinimumSafeVersion(JsonObject json) {
        return extractPrimitiveString(json, "minimum_safe_version");
    }

    private static String extractCapability(JsonObject json) {
        return extractPrimitiveString(json, "capability");
    }

    static boolean shouldDisableReconnectForVersionRejection(String capability) {
        return capability == null || capability.isBlank();
    }

    private static String formatCapabilityAccessDenied(String capability, String backendMessage) {
        String feature = capability.replace('_', ' ');
        if (backendMessage != null && !backendMessage.isBlank() && !"Unknown backend error".equals(backendMessage)) {
            return feature + " unavailable: " + backendMessage;
        }
        return feature + " unavailable for this account.";
    }

    private void maybeNotifyVersionRejection(
            Attempt receiving, String capability, String minimumSafeVersion, String backendMessage) {
        String scope = capability == null || capability.isBlank() ? "general" : capability;
        int count = versionRejectionCounts.merge(scope, 1, Integer::sum);
        OutboundMessageType outbound = OutboundMessageType.of(scope);
        int interval = outbound == null ? 0 : outbound.versionReminderEvery();
        if (count != 1 && (interval == 0 || count % interval != 0)) {
            return;
        }
        // Only a connection-wide notice explains a version close; a warning about one feature doesn't.
        if (capability == null || capability.isBlank()) {
            receiving.versionRejectionShown = true;
        }

        if (count == 1) {
            if (backendMessage != null && !backendMessage.isBlank() && !"Unknown backend error".equals(backendMessage)) {
                notify(backendMessage);
                return;
            }
            String targetVersion = minimumSafeVersion != null && !minimumSafeVersion.isBlank()
                    ? minimumSafeVersion
                    : "the required version";
            notify("Update Sequoia to at least " + targetVersion + ".");
            return;
        }

        String feature = outbound != null && outbound.featureName() != null
                ? outbound.featureName()
                : "Sequoia features";
        String targetVersion = minimumSafeVersion != null && !minimumSafeVersion.isBlank()
                ? minimumSafeVersion
                : "a newer version";
        notify("Sequoia is outdated. Some " + feature + " may not work until you update to " + targetVersion + ".");
    }

    private static String extractPrimitiveString(JsonObject json, String key) {
        if (!json.has(key) || !json.get(key).isJsonPrimitive()) {
            return null;
        }
        try {
            String value = json.get(key).getAsString();
            if (value == null || value.isBlank()) {
                return null;
            }
            return value;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static List<BombShareType> parseBombShareTypes(JsonArray requestedTypesJson) {
        if (requestedTypesJson == null) {
            return List.of();
        }

        List<BombShareType> requestedTypes = new ArrayList<>();
        requestedTypesJson.forEach(element -> BombShareType.fromWireValue(element.getAsString()).ifPresent(type -> {
            if (!requestedTypes.contains(type)) {
                requestedTypes.add(type);
            }
        }));
        return List.copyOf(requestedTypes);
    }

    private static List<String> parsePrimitiveStringArray(JsonArray jsonArray) {
        if (jsonArray == null) {
            return List.of();
        }

        List<String> values = new ArrayList<>();
        jsonArray.forEach(element -> {
            if (element != null && element.isJsonPrimitive()) {
                String value = element.getAsString();
                if (value != null && !value.isBlank()) {
                    values.add(value.trim());
                }
            }
        });
        return List.copyOf(values);
    }

    public String getUptimeString() {
        Instant connectedSince = getConnectedSince();
        if (connectedSince == null) return null;
        java.time.Duration dur = java.time.Duration.between(connectedSince, env.wallClock().instant());
        long hours = dur.toHours();
        long mins = dur.toMinutesPart();
        long secs = dur.toSecondsPart();
        if (hours > 0) return hours + "h " + mins + "m";
        if (mins > 0) return mins + "m " + secs + "s";
        return secs + "s";
    }

    static boolean hasDiscordUsername(String discordUsername) {
        return discordUsername != null && !discordUsername.isBlank();
    }

    static boolean shouldIgnoreDiscordChatSender(String username, Collection<String> ignoredBridgeUsers) {
        if (username == null || ignoredBridgeUsers == null) {
            return false;
        }
        String loweredUsername = username.toLowerCase(Locale.ROOT);
        for (String ignoredBridgeUser : ignoredBridgeUsers) {
            if (ignoredBridgeUser != null && !ignoredBridgeUser.isBlank() && loweredUsername.contains(ignoredBridgeUser)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Keeps the Discord name a sign-in reply links, or that it links none, for {@code account}, the one
     * the socket signed in for, and only while {@code signInCurrent}: a reply that lands after a switch changes no name.
     */
    static Runnable discordNameDelivery(String discordUsername, UUID account, BooleanSupplier signInCurrent,
            java.util.function.BiConsumer<UUID, String> store) {
        return () -> {
            if (account == null || !signInCurrent.getAsBoolean()) {
                SeqClient.LOGGER.debug("[WebSocket] Ignoring the Discord name of a sign-in that has ended");
                return;
            }
            store.accept(account, discordUsername);
        };
    }

    /** The Discord name a sign-in reply links to the account, or null when it names none: absent and blank alike. */
    static String discordUsernameIn(JsonObject reply) {
        // The backend leaves the field out when the account has no Discord link.
        JsonElement name = reply == null ? null : reply.get("discord_username");
        if (name == null || !name.isJsonPrimitive()) {
            return null;
        }
        String value = name.getAsString();
        return hasDiscordUsername(value) ? value : null;
    }

    // ── Message records ──

    public record GuildChatMessage(String username, String nickname, String message) {}

    /**
     * A message relayed from the Discord guild bridge.
     *
     * @param discordId the sender's Discord user id, or {@code null} when the backend
     *                  does not supply one. Prefer it over {@code username} for any
     *                  identity matching: it is stable, whereas a display name can be
     *                  changed or shared.
     */
    public record DiscordChatMessage(String username, String message, String discordId) {

        public DiscordChatMessage(String username, String message) {
            this(username, message, null);
        }
    }

    public record PartyFinderUpdateMessage(String action, JsonObject listingJson) {}

    /**
     * One member's raid profile changing.
     * <p>
     * {@code action} is {@code updated} or {@code removed}. On a removal the
     * payload is still shaped like a profile, with its fields set to null rather
     * than omitted, so only {@code minecraft} is meaningful there.
     */
    public record RaidProfileUpdateMessage(String action, JsonObject profileJson) {

        public boolean isRemoval() {
            return "removed".equalsIgnoreCase(action);
        }
    }

    public record PartyFinderInviteMessage(
            long listingId, String inviterUUID, String inviteToken, JsonObject listingJson) {}

    public record PartyFinderStaleWarningMessage(
            String reason, long listingId, Instant disbandAt, long minutesRemaining) {}

    public record BombSharePromptMessage(
            String requestId,
            String requesterUsername,
            String canonicalKey,
            List<BombShareType> requestedTypes,
            Instant expiresAt,
            boolean firstPrompt) {}

    public record BombShareResultMessage(
            String requestId,
            String canonicalKey,
            List<BombShareType> requestedTypes,
            List<String> worlds,
            int shareCount) {}
}
