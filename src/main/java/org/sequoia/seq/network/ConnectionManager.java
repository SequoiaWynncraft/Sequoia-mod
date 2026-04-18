package org.sequoia.seq.network;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.awt.Desktop;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import lombok.Getter;
import net.minecraft.client.Minecraft;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.sequoia.seq.accessors.NotificationAccessor;
import org.sequoia.seq.client.SeqClient;
import org.sequoia.seq.managers.GuildStorageTracker;
import org.sequoia.seq.model.GuildWarSubmission;
import org.sequoia.seq.model.WynnClassType;
import org.sequoia.seq.network.auth.AuthErrorCode;
import org.sequoia.seq.network.auth.AuthException;
import org.sequoia.seq.network.auth.StoredAuthSession;
import org.sequoia.seq.utils.WynnClassCache;

public class ConnectionManager extends WebSocketClient implements NotificationAccessor {

    private static final Gson GSON = new Gson();
    private static final long RECONNECT_BASE_MS = 1_000;
    private static final long RECONNECT_CAP_MS = 60_000;
    private static final int MAX_AUTO_RECONNECT_ATTEMPTS = 5;
    private static final int MAX_GUILD_CHAT_MESSAGE_LENGTH = 512;
    private static final long AUTH_BACKOFF_BASE_MS = 2_000;
    private static final long AUTH_BACKOFF_CAP_MS = 60_000;
    private static final long PRIVILEGED_SEND_THROTTLE_MS = 150;
    private static final Pattern MC_USERNAME_PATTERN = Pattern.compile("^[A-Za-z0-9_]{3,16}$");
    private static final Pattern URL_PATTERN = Pattern.compile("^(https?://).+", Pattern.CASE_INSENSITIVE);
    private static final Map<String, Integer> VERSION_REMINDER_INTERVALS = Map.of(
            "guild_chat", 20,
            "guild_raid_announcement", 5,
            "guild_bank_event", 10,
            "guild_storage_snapshot", 10,
            "guild_storage_reward", 10,
            "guild_war_submission", 5);

    private static ConnectionManager instance;
    private static final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "seq-reconnect");
        t.setDaemon(true);
        return t;
    });

    @Getter
    private boolean authenticated = false;

    @Getter
    private boolean authFailed = false;

    @Getter
    private boolean notInGuild = false;

    @Getter
    private Instant connectedSince;

    private Consumer<List<String>> connectedUsersCallback;
    private volatile long nextAllowedAuthAttemptAtMs;
    private volatile int authAttempt;
    private volatile long nextPrivilegedSendAtMs;
    private volatile boolean connectInProgress;
    private volatile boolean userInitiatedConnectFlow;
    private volatile boolean pendingDiscordLinkRequest;
    private final Deque<GuildWarSubmission> pendingGuildWarSubmissions = new ConcurrentLinkedDeque<>();

    // Reconnect state
    private static boolean autoReconnect = true;
    private static int reconnectAttempt = 0;
    private static ScheduledFuture<?> reconnectTask;
    private static boolean autoConnectSuppressedByManualDisconnect;

    private enum AuthFlow {
        CONNECT,
        LINK
    }

    // Callbacks for new message types
    private static Consumer<DiscordChatMessage> discordChatHandler;
    private static Consumer<PartyFinderUpdateMessage> partyFinderUpdateHandler;
    private static Consumer<PartyFinderInviteMessage> partyFinderInviteHandler;
    private static Consumer<PartyFinderStaleWarningMessage> partyFinderStaleWarningHandler;
    private static final Map<String, Integer> versionRejectionCounts = new ConcurrentHashMap<>();
    private volatile AuthFlow pendingAuthFlow = AuthFlow.CONNECT;

    public static ConnectionManager getInstance() {
        if (instance == null) {
            instance = new ConnectionManager();
        }
        return instance;
    }

    private ConnectionManager() {
        super(URI.create(BuildConfig.WS_URL));
    }

    public static void disconnectForBlockedServer() {
        WynncraftServerPolicy.Scope serverScope = WynncraftServerPolicy.currentScope();
        if (serverScope != WynncraftServerPolicy.Scope.BLOCKED) {
            return;
        }

        autoReconnect = false;
        cancelReconnect();

        ConnectionManager current = instance;
        if (current == null) {
            return;
        }

        if (!current.hasConnectionState()) {
            instance = null;
            return;
        }

        boolean shouldNotify = current.isOpen() || current.connectInProgress;
        current.disconnectInternal(false);
        if (shouldNotify) {
            current.notify(WynncraftServerPolicy.MAIN_SERVER_ONLY_MESSAGE);
        }
    }

    // ── Connect / Disconnect ──

    @Override
    public void connect() {
        connectInternal(AuthFlow.CONNECT, false);
    }

    public void connectManually() {
        connectInternal(AuthFlow.CONNECT, true);
    }

    public void link() {
        connectInternal(AuthFlow.LINK, false);
    }

    public void linkManually() {
        connectInternal(AuthFlow.LINK, true);
    }

    private void connectInternal(AuthFlow authFlow, boolean userInitiated) {
        if (userInitiated) {
            autoConnectSuppressedByManualDisconnect = false;
        }
        WynncraftServerPolicy.Scope serverScope = WynncraftServerPolicy.currentScope();
        if (serverScope != WynncraftServerPolicy.Scope.MAIN) {
            pendingDiscordLinkRequest = false;
            connectInProgress = false;
            finishConnectFlow();
            if (serverScope == WynncraftServerPolicy.Scope.BLOCKED) {
                autoReconnect = false;
                cancelReconnect();
                if (userInitiated) {
                    notify(WynncraftServerPolicy.MAIN_SERVER_ONLY_MESSAGE);
                }
                SeqClient.LOGGER.info("[WebSocket] Blocking {} outside main Wynncraft host", authFlow);
            } else {
                if (userInitiated) {
                    notify("Waiting until Wynncraft server transfer finishes.");
                }
                SeqClient.LOGGER.info("[WebSocket] Delaying {} until Wynncraft host is confirmed", authFlow);
            }
            return;
        }

        pendingAuthFlow = authFlow;
        pendingDiscordLinkRequest = authFlow == AuthFlow.LINK;
        userInitiatedConnectFlow = userInitiated;
        autoReconnect = true;
        SeqClient.LOGGER.info(
                "[WebSocket] connectInternal() called flow={} open={} authenticated={} autoReconnect={} configuredUrl={} clientUri={}",
                authFlow,
                isOpen(),
                authenticated,
                autoReconnect,
                BuildConfig.WS_URL,
                getURI());
        if (isOpen()) {
            if (authFlow == AuthFlow.LINK) {
                if (authenticated && !authFailed && !notInGuild) {
                    notifyConnectionStatus("Starting Discord OAuth link flow...");
                    requestDiscordLink(true);
                    finishConnectFlow();
                } else {
                    notifyConnectionStatus("Refreshing backend authentication...");
                    refreshAuthentication();
                }
            } else {
                notifyConnectionStatus("Already connected/connecting.");
                finishConnectFlow();
            }
            return;
        }
        if (connectInProgress) {
            notifyConnectionStatus("Already connected/connecting.");
            return;
        }

        if (authFlow == AuthFlow.LINK) {
            notifyConnectionStatus("Authenticating your Minecraft account on " + BuildConfig.ENVIRONMENT + "...");
        } else {
            notifyConnectionStatus("Connecting to " + BuildConfig.ENVIRONMENT + "...");
        }
        prepareAuthenticatedConnection(authFlow == AuthFlow.LINK);
    }

    public void disconnect() {
        disconnectInternal(false);
    }

    public void disconnectManually() {
        disconnectInternal(true);
    }

    private void disconnectInternal(boolean userInitiated) {
        if (userInitiated) {
            autoConnectSuppressedByManualDisconnect = true;
        }
        boolean open = isOpen();
        boolean hadConnectionState = hasConnectionState();
        boolean hadAutoReconnect = autoReconnect;
        if (!hadConnectionState && !hadAutoReconnect) {
            pendingDiscordLinkRequest = false;
            connectInProgress = false;
            connectedSince = null;
            instance = null;
            if (userInitiated) {
                notify("Not connected");
            }
            return;
        }

        SeqClient.LOGGER.info(
                "[WebSocket] disconnect() called open={} authenticated={} autoReconnect={}",
                open,
                authenticated,
                autoReconnect);
        finishConnectFlow();
        autoReconnect = false;
        cancelReconnect();
        connectInProgress = false;
        if (!open) {
            authenticated = false;
            authFailed = false;
            notInGuild = false;
            connectedSince = null;
            pendingDiscordLinkRequest = false;
            instance = null;
            if (userInitiated) {
                notify("Not connected");
            }
            return;
        }
        close();
        authenticated = false;
        authFailed = false;
        notInGuild = false;
        connectedSince = null;
        if (userInitiated) {
            notify("Disconnected");
        }
    }

    // ── WebSocket lifecycle ──

    @Override
    public void onOpen(ServerHandshake handshake) {
        SeqClient.LOGGER.info(
                "[WebSocket] onOpen configuredUrl={} clientUri={} status={} message='{}'",
                BuildConfig.WS_URL,
                getURI(),
                handshake != null ? handshake.getHttpStatus() : -1,
                handshake != null ? handshake.getHttpStatusMessage() : "null");
        connectInProgress = false;
        reconnectAttempt = 0;
        authenticated = false;
        authFailed = false;
        notInGuild = false;
        connectedSince = null;
        autoReconnect = true;
        authAttempt = 0;
        nextAllowedAuthAttemptAtMs = 0;

        String username = SeqClient.getConfigManager().getMinecraftUsername();
        if (pendingAuthFlow == AuthFlow.LINK) {
            notifyConnectionStatus(
                    username != null && !username.isBlank()
                            ? "Connected websocket for " + username + ". Waiting for backend authentication..."
                            : "Connected websocket. Waiting for backend authentication...");
        } else {
            notifyConnectionStatus(
                    username != null && !username.isBlank()
                            ? "Connected websocket for " + username + ". Waiting for backend authentication..."
                            : "Connected websocket to " + BuildConfig.ENVIRONMENT + ". Waiting for backend authentication...");
        }
        finishConnectFlow();
    }

    @Override
    public void onMessage(String message) {
        SeqClient.LOGGER.debug("[WebSocket] onMessage raw={} chars", message != null ? message.length() : -1);
        handleMessage(message);
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        SeqClient.LOGGER.info(
                "[WebSocket] onClose code={} reason='{}' remote={} autoReconnect={}",
                code,
                reason,
                remote,
                autoReconnect);
        connectInProgress = false;
        authenticated = false;
        authFailed = false;
        notInGuild = false;
        connectedSince = null;
        instance = null;
        handleWebSocketAuthRejection(code, reason);
        boolean shouldReconnect = autoReconnect && shouldReconnectAfterClose(code, remote);
        if (shouldReconnect) {
            SeqClient.LOGGER.info(
                    "[WebSocket] Scheduling reconnect after close code={} remote={} reason='{}'", code, remote, reason);
            scheduleReconnect();
        } else {
            SeqClient.LOGGER.info(
                    "[WebSocket] Reconnect not scheduled (autoReconnect={} code={} remote={} reason='{}')",
                    autoReconnect,
                    code,
                    remote,
                    reason);
            finishConnectFlow();
        }
    }

    @Override
    public void onError(Exception ex) {
        SeqClient.LOGGER.error(
                "[WebSocket] onError open={} authenticated={} message={}",
                isOpen(),
                authenticated,
                ex != null ? ex.getMessage() : "null",
                ex);
        connectInProgress = false;
        authenticated = false;
        connectedSince = null;
        notifyConnectionFailure("Connection error: " + (ex != null ? ex.getMessage() : "unknown"), false);
        finishConnectFlow();
    }

    // ── Auto-reconnect ──

    private static void scheduleReconnect() {
        WynncraftServerPolicy.Scope serverScope = WynncraftServerPolicy.currentScope();
        if (serverScope == WynncraftServerPolicy.Scope.BLOCKED) {
            autoReconnect = false;
            cancelReconnect();
            SeqClient.LOGGER.info("[WebSocket] Auto reconnect suppressed outside main Wynncraft host");
            return;
        }
        if (reconnectAttempt >= MAX_AUTO_RECONNECT_ATTEMPTS) {
            autoReconnect = false;
            cancelReconnect();
            SeqClient.LOGGER.warn("[WebSocket] Auto reconnect exhausted after {} attempts", reconnectAttempt);
            notifyManualConnectRequired();
            return;
        }
        cancelReconnect();
        long delay = Math.min(RECONNECT_BASE_MS * (1L << reconnectAttempt), RECONNECT_CAP_MS);
        reconnectAttempt++;
        SeqClient.LOGGER.info("[WebSocket] Reconnecting in {}ms (attempt {})", delay, reconnectAttempt);
        reconnectTask = scheduler.schedule(
                () -> {
                    instance = null;
                    try {
                        WynncraftServerPolicy.Scope reconnectScope = WynncraftServerPolicy.currentScope();
                        if (reconnectScope == WynncraftServerPolicy.Scope.BLOCKED) {
                            autoReconnect = false;
                            cancelReconnect();
                            SeqClient.LOGGER.info("[WebSocket] Cancelled reconnect because current host is blocked");
                            return;
                        }
                        if (reconnectScope != WynncraftServerPolicy.Scope.MAIN) {
                            reconnectAttempt = Math.max(0, reconnectAttempt - 1);
                            SeqClient.LOGGER.info(
                                    "[WebSocket] Delaying reconnect attempt {} until Wynncraft host is confirmed",
                                    reconnectAttempt);
                            scheduleReconnect();
                            return;
                        }
                        SeqClient.LOGGER.info("[WebSocket] Running scheduled reconnect attempt {}", reconnectAttempt);
                        getInstance().connect();
                    } catch (Exception e) {
                        SeqClient.LOGGER.error("[WebSocket] Reconnect failed", e);
                        scheduleReconnect();
                    }
                },
                delay,
                TimeUnit.MILLISECONDS);
    }

    private static void cancelReconnect() {
        if (reconnectTask != null) {
            SeqClient.LOGGER.debug("[WebSocket] Cancelling pending reconnect task");
            reconnectTask.cancel(false);
            reconnectTask = null;
        }
    }

    private void notifyConnectionStatus(String message) {
        if (userInitiatedConnectFlow) {
            notify(message);
        }
    }

    private void notifyConnectionFailure(String message, boolean requiresManualIntervention) {
        if (requiresManualIntervention || userInitiatedConnectFlow) {
            notify(message);
        }
    }

    private void finishConnectFlow() {
        userInitiatedConnectFlow = false;
        pendingAuthFlow = AuthFlow.CONNECT;
        if (!isOpen()) {
            pendingDiscordLinkRequest = false;
        }
    }

    static boolean shouldReconnectAfterClose(int code, boolean remote) {
        if (remote) {
            return true;
        }
        // Local clean close (1000) is treated as intentional; do not auto-reconnect.
        // Handshake/protocol close failures (e.g. 1002 from HTTP 502) should retry.
        return code != 1000;
    }

    private boolean hasConnectionState() {
        return isOpen()
                || connectInProgress
                || authenticated
                || authFailed
                || notInGuild
                || connectedSince != null
                || reconnectTask != null
                || userInitiatedConnectFlow
                || pendingDiscordLinkRequest;
    }

    static boolean hasReconnectTask() {
        return reconnectTask != null;
    }

    public static boolean canAutoConnectNow() {
        ConnectionManager current = instance;
        return shouldAttemptAutomaticConnect(
                current != null && current.isOpen(),
                current != null && current.authenticated,
                current != null && current.connectInProgress,
                reconnectTask != null,
                autoConnectSuppressedByManualDisconnect);
    }

    public static boolean isAutoConnectSuppressedByManualDisconnect() {
        return autoConnectSuppressedByManualDisconnect;
    }

    static void resetForTest() {
        autoReconnect = true;
        reconnectAttempt = 0;
        autoConnectSuppressedByManualDisconnect = false;
        cancelReconnect();
        instance = null;
    }

    static boolean shouldAttemptAutomaticConnect(
            boolean socketOpen,
            boolean authenticated,
            boolean connectInProgress,
            boolean reconnectScheduled,
            boolean manualDisconnectSuppressed) {
        return !manualDisconnectSuppressed
                && !socketOpen
                && !authenticated
                && !connectInProgress
                && !reconnectScheduled;
    }

    private static void notifyManualConnectRequired() {
        Minecraft.getInstance().execute(() -> {
            var player = Minecraft.getInstance().player;
            if (player != null) {
                player.displayClientMessage(
                        java.util.Objects.requireNonNull(
                                NotificationAccessor.prefixed(
                                        "Could not reconnect automatically. Run /seq connect manually (or /seq link if needed).")),
                        false);
            }
        });
    }

    // ── Outgoing messages ──

    private void send(String type, JsonObject payload) {
        if (isPrivilegedType(type) && WynncraftServerPolicy.currentScope() != WynncraftServerPolicy.Scope.MAIN) {
            SeqClient.LOGGER.warn("[WebSocket] Dropping {} outside confirmed main Wynncraft host", type);
            return;
        }
        if (isPrivilegedType(type) && !canSendPrivileged(type)) {
            return;
        }
        sendPrepared(type, payload);
    }

    private void sendPrepared(String type, JsonObject payload) {
        if (payload == null) payload = new JsonObject();
        payload.addProperty("type", type);
        SeqClient.LOGGER.debug("[WebSocket] send type={} payload={}", type, truncate(payload.toString(), 512));
        send(GSON.toJson(payload));
    }

    private void prepareAuthenticatedConnection(boolean forceRefresh) {
        WynncraftServerPolicy.Scope initialScope = WynncraftServerPolicy.currentScope();
        if (initialScope != WynncraftServerPolicy.Scope.MAIN) {
            connectInProgress = false;
            if (initialScope == WynncraftServerPolicy.Scope.BLOCKED) {
                autoReconnect = false;
                cancelReconnect();
                notifyConnectionFailure(WynncraftServerPolicy.MAIN_SERVER_ONLY_MESSAGE, false);
            } else if (autoReconnect) {
                scheduleReconnect();
            }
            finishConnectFlow();
            return;
        }
        if (!forceRefresh && !canAttemptAuthNow()) {
            return;
        }
        connectInProgress = true;
        SeqClient.getAuthService()
                .ensureValidToken(forceRefresh)
                .whenComplete((token, throwable) -> Minecraft.getInstance().execute(() -> {
                    if (throwable != null) {
                        connectInProgress = false;
                        AuthException authException = unwrapAuthException(throwable);
                        authFailed = true;
                        authenticated = false;
                        registerAuthFailure();
                        SeqClient.LOGGER.warn(
                                "[WebSocket] Failed to obtain backend auth token code={} message={}",
                                authException.getStableCode(),
                                authException.getMessage(),
                                authException);
                        notifyConnectionFailure(authException.getMessage(), !authException.isRetryable());
                        finishConnectFlow();
                        if (authException.isRetryable() && autoReconnect) {
                            scheduleReconnect();
                        }
                        return;
                    }

                    try {
                        WynncraftServerPolicy.Scope currentScope = WynncraftServerPolicy.currentScope();
                        if (currentScope != WynncraftServerPolicy.Scope.MAIN) {
                            connectInProgress = false;
                            if (currentScope == WynncraftServerPolicy.Scope.BLOCKED) {
                                autoReconnect = false;
                                cancelReconnect();
                                notifyConnectionFailure(WynncraftServerPolicy.MAIN_SERVER_ONLY_MESSAGE, false);
                            } else if (autoReconnect) {
                                scheduleReconnect();
                            }
                            finishConnectFlow();
                            return;
                        }
                        configureHandshakeAuthorization(token);
                        super.connect();
                    } catch (Exception exception) {
                        connectInProgress = false;
                        SeqClient.LOGGER.error("Failed to connect", exception);
                        notifyConnectionFailure("Failed to connect: " + exception.getMessage(), false);
                        instance = null;
                        finishConnectFlow();
                        scheduleReconnect();
                    }
                }));
    }

    private void refreshAuthentication() {
        SeqClient.getAuthService().beginAuthentication().whenComplete((session, throwable) -> Minecraft.getInstance()
                .execute(() -> {
                    if (throwable != null) {
                        AuthException authException = unwrapAuthException(throwable);
                        SeqClient.LOGGER.warn(
                                "[WebSocket] Refresh authentication failed code={} message={}",
                                authException.getStableCode(),
                                authException.getMessage(),
                                authException);
                        notifyConnectionFailure(authException.getMessage(), !authException.isRetryable());
                        finishConnectFlow();
                        return;
                    }

                    notifyConnectionStatus(
                            session.minecraftUsername() != null
                                            && !session.minecraftUsername().isBlank()
                                    ? "Refreshed backend token for " + session.minecraftUsername()
                                    : "Refreshed backend token.");
                    if (pendingDiscordLinkRequest && isOpen() && authenticated && !authFailed && !notInGuild) {
                        requestDiscordLink(true);
                    }
                    finishConnectFlow();
                }));
    }

    private void configureHandshakeAuthorization(String token) {
        Map<String, String> headers = buildHandshakeHeaders(token);
        clearHeaders();
        headers.forEach(this::addHeader);
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

    static JsonObject buildLinkRequestPayload(boolean allowRelink) {
        JsonObject payload = new JsonObject();
        payload.addProperty("allow_relink", allowRelink);
        return payload;
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

    private AuthException unwrapAuthException(Throwable throwable) {
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

    private void handleWebSocketAuthRejection(int code, String reason) {
        String normalizedReason = reason == null ? "" : reason.toLowerCase(Locale.ROOT);
        boolean rejected = code == 1008
                || normalizedReason.contains("token")
                || normalizedReason.contains("expired")
                || normalizedReason.contains("unauthor")
                || normalizedReason.contains("forbidden")
                || normalizedReason.contains("auth");
        if (!rejected) {
            return;
        }

        authFailed = true;
        SeqClient.getAuthService()
                .invalidateSession(
                        normalizedReason.contains("expired")
                                ? AuthErrorCode.TOKEN_EXPIRED
                                : AuthErrorCode.WEBSOCKET_AUTH_REJECTED,
                        normalizedReason.contains("expired")
                                ? "Backend token expired. Re-authenticating."
                                : "Backend rejected websocket authentication. Re-authenticating.");
        notifyConnectionFailure(SeqClient.getAuthService().getLastError().getMessage(), false);
        registerAuthFailure();
    }

    public void requestConnectedUsers(Consumer<List<String>> callback) {
        SeqClient.LOGGER.info("[WebSocket] requestConnectedUsers open={} authenticated={}", isOpen(), authenticated);
        if (!isOpen() || !authenticated || authFailed || notInGuild || !WynncraftServerPolicy.isCurrentServerAllowed()) {
            callback.accept(List.of());
            return;
        }
        this.connectedUsersCallback = callback;
        send("get_connected", null);
    }

    public void sendGuildChat(String username, String nickname, String message, String avatarUrl) {
        if (!isOpen()) {
            SeqClient.LOGGER.warn("[ConnectionManager] sendGuildChat dropped: socket not open uri={}", getURI());
            return;
        }
        if (!authenticated) {
            SeqClient.LOGGER.warn("[ConnectionManager] sendGuildChat dropped: not authenticated uri={}", getURI());
            return;
        }
        if (authFailed || notInGuild) {
            SeqClient.LOGGER.warn(
                    "[ConnectionManager] sendGuildChat dropped: authFailed={} notInGuild={}", authFailed, notInGuild);
            return;
        }

        String cleanedMessage = message == null ? "" : message.trim();
        if (cleanedMessage.isEmpty()) {
            SeqClient.LOGGER.warn("[ConnectionManager] sendGuildChat dropped: empty message");
            return;
        }
        if (cleanedMessage.length() > MAX_GUILD_CHAT_MESSAGE_LENGTH) {
            SeqClient.LOGGER.warn(
                    "[ConnectionManager] sendGuildChat dropped: message too long={} max={}",
                    cleanedMessage.length(),
                    MAX_GUILD_CHAT_MESSAGE_LENGTH);
            notify("Guild chat message too long.");
            return;
        }
        String safeAvatarUrl = sanitizeAvatarUrl(avatarUrl);
        String safeReportedUsername = sanitizeMinecraftUsername(username);
        String safeNickname = sanitizeNickname(nickname);

        SeqClient.LOGGER.info(
                "[ConnectionManager] Sending guild_chat uri={} username='{}' nickname='{}' message='{}'",
                getURI(),
                safeReportedUsername,
                safeNickname,
                cleanedMessage);
        JsonObject msg = new JsonObject();
        if (safeReportedUsername != null) {
            msg.addProperty("username", safeReportedUsername);
        }
        if (safeNickname != null) {
            msg.addProperty("nickname", safeNickname);
        }
        msg.addProperty("message", cleanedMessage);
        if (safeAvatarUrl != null) msg.addProperty("avatar_url", safeAvatarUrl);
        send("guild_chat", msg);
    }

    public void sendRaidAnnouncement(
            List<String> usernames,
            String raidType,
            int aspectCount,
            int emeraldCount,
            double experienceCount,
            int srCount) {
        if (!authenticated || !isOpen()) {
            SeqClient.LOGGER.warn(
                    "[WebSocket] sendRaidAnnouncement dropped open={} authenticated={}", isOpen(), authenticated);
            return;
        }
        if (authFailed || notInGuild) {
            SeqClient.LOGGER.warn(
                    "[WebSocket] sendRaidAnnouncement dropped authFailed={} notInGuild={}", authFailed, notInGuild);
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
        JsonObject msg = new JsonObject();
        JsonArray names = new JsonArray();
        usernames.forEach(names::add);
        msg.add("usernames", names);
        msg.addProperty("raid_type", raidType);
        msg.addProperty("aspect_count", aspectCount);
        msg.addProperty("emerald_count", emeraldCount);
        msg.addProperty("experience_count", experienceCount);
        msg.addProperty("sr_count", srCount);
        send("guild_raid_announcement", msg);
    }

    public void sendGuildBankEvent(
            String action,
            String player,
            Integer quantity,
            String itemName,
            String charges,
            String accessTier,
            String rawMessage) {
        if (!authenticated || !isOpen()) {
            SeqClient.LOGGER.warn("[WebSocket] sendGuildBankEvent dropped open={} authenticated={}", isOpen(), authenticated);
            return;
        }
        if (authFailed || notInGuild) {
            SeqClient.LOGGER.warn(
                    "[WebSocket] sendGuildBankEvent dropped authFailed={} notInGuild={}", authFailed, notInGuild);
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
        if (!authenticated || !isOpen()) {
            SeqClient.LOGGER.warn(
                    "[WebSocket] sendGuildStorageSnapshot dropped open={} authenticated={}", isOpen(), authenticated);
            return;
        }
        if (authFailed || notInGuild) {
            SeqClient.LOGGER.warn(
                    "[WebSocket] sendGuildStorageSnapshot dropped authFailed={} notInGuild={}", authFailed, notInGuild);
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
        if (!authenticated || !isOpen()) {
            SeqClient.LOGGER.warn(
                    "[WebSocket] sendGuildStorageReward dropped open={} authenticated={}", isOpen(), authenticated);
            return;
        }
        if (authFailed || notInGuild) {
            SeqClient.LOGGER.warn(
                    "[WebSocket] sendGuildStorageReward dropped authFailed={} notInGuild={}", authFailed, notInGuild);
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

        WynncraftServerPolicy.Scope serverScope = WynncraftServerPolicy.currentScope();
        if (serverScope == WynncraftServerPolicy.Scope.BLOCKED) {
            SeqClient.LOGGER.warn("[WebSocket] sendGuildWarSubmission dropped outside main Wynncraft host");
            return false;
        }
        if (serverScope == WynncraftServerPolicy.Scope.UNKNOWN) {
            SeqClient.LOGGER.warn("[WebSocket] Queueing guild_war_submission until Wynncraft host is confirmed");
            pendingGuildWarSubmissions.addLast(submission);
            return true;
        }

        if (!authenticated || !isOpen()) {
            SeqClient.LOGGER.warn(
                    "[WebSocket] Queueing guild_war_submission until websocket is ready open={} authenticated={}",
                    isOpen(),
                    authenticated);
            pendingGuildWarSubmissions.addLast(submission);
            return true;
        }
        if (authFailed || notInGuild) {
            SeqClient.LOGGER.warn(
                    "[WebSocket] Queueing guild_war_submission until auth recovers authFailed={} notInGuild={}",
                    authFailed,
                    notInGuild);
            pendingGuildWarSubmissions.addLast(submission);
            return true;
        }

        return sendGuildWarSubmissionNow(submission, false);
    }

    public static void flushPendingOutbound() {
        if (instance == null) {
            return;
        }
        instance.flushPendingGuildWarSubmissions();
    }

    private void flushPendingGuildWarSubmissions() {
        if (pendingGuildWarSubmissions.isEmpty()
                || !isOpen()
                || !authenticated
                || authFailed
                || notInGuild
                || WynncraftServerPolicy.currentScope() != WynncraftServerPolicy.Scope.MAIN) {
            return;
        }

        GuildWarSubmission pending = pendingGuildWarSubmissions.peekFirst();
        if (pending == null) {
            return;
        }
        if (sendGuildWarSubmissionNow(pending, true)) {
            pendingGuildWarSubmissions.pollFirst();
        }
    }

    private boolean sendGuildWarSubmissionNow(GuildWarSubmission submission, boolean replay) {
        if (!canSendPrivileged("guild_war_submission")) {
            return false;
        }
        JsonObject payload = buildGuildWarSubmissionPayload(submission);
        SeqClient.LOGGER.info(
                replay
                        ? "[WebSocket] Replaying queued guild_war_submission territory='{}' warrers={} completedAt={} sr={}"
                        : "[WebSocket] Sending guild_war_submission territory='{}' warrers={} completedAt={} sr={}",
                submission.territory(),
                submission.warrers(),
                submission.completedAt(),
                submission.seasonRating());
        sendPrepared("guild_war_submission", payload);
        return true;
    }

    public void sendPartyClassUpdate(WynnClassType classType) {
        if (!authenticated || !isOpen() || classType == null) {
            SeqClient.LOGGER.warn(
                    "[WebSocket] sendPartyClassUpdate dropped open={} authenticated={} classType={}",
                    isOpen(),
                    authenticated,
                    classType);
            return;
        }
        if (authFailed || notInGuild) {
            SeqClient.LOGGER.warn(
                    "[WebSocket] sendPartyClassUpdate dropped authFailed={} notInGuild={}", authFailed, notInGuild);
            return;
        }
        SeqClient.LOGGER.info("[WebSocket] Sending party_class_update classType={}", classType);
        JsonObject msg = new JsonObject();
        msg.addProperty("class_type", classType.name());
        send("party_class_update", msg);
    }

    public boolean sendPartySyncSnapshot(boolean active, String leaderUsername, List<String> memberUsernames) {
        if (!authenticated || !isOpen()) {
            SeqClient.LOGGER.warn(
                    "[WebSocket] sendPartySyncSnapshot dropped open={} authenticated={}",
                    isOpen(),
                    authenticated);
            return false;
        }
        if (authFailed || notInGuild) {
            SeqClient.LOGGER.warn(
                    "[WebSocket] sendPartySyncSnapshot dropped authFailed={} notInGuild={}",
                    authFailed,
                    notInGuild);
            return false;
        }

        JsonObject msg = new JsonObject();
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
        SeqClient.LOGGER.info(
                "[WebSocket] Sending party_sync_snapshot active={} leader={} members={} usernames={}",
                active,
                leaderUsername,
                usernames.size(),
                memberUsernames);
        send("party_sync_snapshot", msg);
        return true;
    }

    public boolean sendPartySyncMemberRemoved(String username, String reason) {
        if (!authenticated || !isOpen()) {
            SeqClient.LOGGER.warn(
                    "[WebSocket] sendPartySyncMemberRemoved dropped open={} authenticated={}",
                    isOpen(),
                    authenticated);
            return false;
        }
        if (authFailed || notInGuild) {
            SeqClient.LOGGER.warn(
                    "[WebSocket] sendPartySyncMemberRemoved dropped authFailed={} notInGuild={}",
                    authFailed,
                    notInGuild);
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
        send("party_sync_member_removed", msg);
        return true;
    }

    public void sendLocalPartyClassUpdate() {
        WynnClassType classType = WynnClassCache.resolveLocalClassType();
        if (classType == null) {
            return;
        }
        sendPartyClassUpdate(classType);
    }

    // ── Incoming message handler ──

    private void handleMessage(String message) {
        try {
            JsonObject json = GSON.fromJson(message, JsonObject.class);
            if (json == null || !json.has("type")) {
                SeqClient.LOGGER.warn("[WebSocket] Dropping message without type: {}", truncate(message, 512));
                return;
            }
            String type = json.get("type").getAsString();
            SeqClient.LOGGER.info("[WebSocket] Received message type={} payload={}", type, truncate(message, 512));

            switch (type) {
                case "authenticated" -> {
                    authenticated = true;
                    authFailed = false;
                    notInGuild = false;
                    authAttempt = 0;
                    nextAllowedAuthAttemptAtMs = 0;
                    connectedSince = Instant.now();
                    autoReconnect = true;
                    if (json.has("discord_username")
                            && json.get("discord_username").isJsonPrimitive()) {
                        String discordUser = json.get("discord_username").getAsString();
                        storeDiscordUsername(discordUser);
                        notifyConnectionStatus("Connected as " + discordUser);
                    } else {
                        clearDiscordUsername();
                    }
                    if (pendingDiscordLinkRequest) {
                        requestDiscordLink(true);
                    }
                    flushPendingGuildWarSubmissions();
                    sendLocalPartyClassUpdate();
                }
                case "auth_challenge" -> handleAuthChallenge(json);
                case "auth_success" -> handleAuthSuccess(json);
                case "connected_users" -> {
                    List<String> users = new ArrayList<>();
                    json.getAsJsonArray("users").forEach(el -> users.add(el.getAsString()));
                    SeqClient.LOGGER.info(
                            "[WebSocket] connected_users received count={} callbackPresent={}",
                            users.size(),
                            connectedUsersCallback != null);
                    if (connectedUsersCallback != null) {
                        connectedUsersCallback.accept(users);
                        connectedUsersCallback = null;
                    } else {
                        SeqClient.LOGGER.warn("[WebSocket] connected_users had no callback listener");
                    }
                }
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
                    Minecraft.getInstance().execute(() -> GuildStorageTracker.getInstance().applyRemoteSnapshot(
                            emeraldCurrent, emeraldMax, aspectCurrent, aspectMax));
                }
                case "discord_chat" -> {
                    if (discordChatHandler != null) {
                        String username = json.get("username").getAsString();
                        String msg = json.get("message").getAsString();
                        SeqClient.LOGGER.info("[WebSocket] Dispatching discord_chat from {}", username);
                        discordChatHandler.accept(new DiscordChatMessage(username, msg));
                    } else {
                        SeqClient.LOGGER.warn("[WebSocket] Received discord_chat but handler is not registered");
                    }
                }
                case "party_finder_update" -> {
                    String action = json.has("action") && !json.get("action").isJsonNull()
                            ? json.get("action").getAsString()
                            : "unknown";
                    if (partyFinderUpdateHandler != null) {
                        JsonObject listingJson = json.getAsJsonObject("listing");
                        SeqClient.LOGGER.info(
                                "[WebSocket] Dispatching party_finder_update action={} hasListing={}",
                                action,
                                listingJson != null);
                        partyFinderUpdateHandler.accept(new PartyFinderUpdateMessage(action, listingJson));
                    } else {
                        SeqClient.LOGGER.warn("[WebSocket] Received party_finder_update but handler is not registered");
                    }
                }
                case "party_finder_invite" -> {
                    if (partyFinderInviteHandler != null) {
                        long listingId = json.get("listing_id").getAsLong();
                        String inviterUUID = json.get("inviter_uuid").getAsString();
                        String inviteToken = json.get("invite_token").getAsString();

                        JsonObject listingJson = null;
                        if (json.has("listing") && json.get("listing").isJsonObject()) {
                            listingJson = json.getAsJsonObject("listing");
                        }

                        SeqClient.LOGGER.info(
                                "[WebSocket] Dispatching party_finder_invite listingId={} inviterUUID={} tokenPresent={} hasListing={}",
                                listingId,
                                inviterUUID,
                                inviteToken != null && !inviteToken.isBlank(),
                                listingJson != null);

                        partyFinderInviteHandler.accept(
                                new PartyFinderInviteMessage(listingId, inviterUUID, inviteToken, listingJson));
                    } else {
                        SeqClient.LOGGER.warn("[WebSocket] Received party_finder_invite but handler is not registered");
                    }
                }
                case "party_finder_stale_warning" -> {
                    if (partyFinderStaleWarningHandler != null) {
                        String reason = extractPrimitiveString(json, "reason");
                        long listingId = json.get("listing_id").getAsLong();
                        Instant disbandAt = json.has("disband_at") && !json.get("disband_at").isJsonNull()
                                ? Instant.parse(json.get("disband_at").getAsString())
                                : null;
                        long minutesRemaining = json.has("minutes_remaining")
                                ? json.get("minutes_remaining").getAsLong()
                                : 0L;

                        SeqClient.LOGGER.info(
                                "[WebSocket] Dispatching party_finder_stale_warning reason={} listingId={} disbandAt={} minutesRemaining={}",
                                reason,
                                listingId,
                                disbandAt,
                                minutesRemaining);

                        partyFinderStaleWarningHandler.accept(
                                new PartyFinderStaleWarningMessage(reason, listingId, disbandAt, minutesRemaining));
                    } else {
                        SeqClient.LOGGER.warn(
                                "[WebSocket] Received party_finder_stale_warning but handler is not registered");
                    }
                }
                case "error" -> {
                    String error = extractBackendErrorMessage(json);
                    String backendCode = extractBackendErrorCode(json);
                    String minimumSafeVersion = extractMinimumSafeVersion(json);
                    String capability = extractCapability(json);
                    int status = extractStatusCode(json);
                    String normalized = error.toLowerCase(Locale.ROOT);
                    SeqClient.LOGGER.warn(
                            "[WebSocket] Backend error status={} code={} message={}", status, backendCode, error);

                    if ("mod_version_unsupported".equalsIgnoreCase(backendCode) || status == 426) {
                        autoReconnect = false;
                        maybeNotifyVersionRejection(capability, minimumSafeVersion, error);
                        return;
                    }

                    if (status == 400 || normalized.contains("invalid auth request")) {
                        authFailed = true;
                        authenticated = false;
                        registerAuthFailure();
                        notify("Invalid auth request. Check username/UUID/session, then run /seq link.");
                        return;
                    }

                    if (status == 401 || normalized.contains("invalid token") || normalized.contains("expired")) {
                        authFailed = true;
                        authenticated = false;
                        registerAuthFailure();
                        SeqClient.getAuthService()
                                .invalidateSession(
                                        normalized.contains("expired")
                                                ? AuthErrorCode.TOKEN_EXPIRED
                                                : AuthErrorCode.TOKEN_INVALID,
                                        normalized.contains("expired")
                                                ? "Backend token expired. Re-authentication required."
                                                : "Backend rejected the stored token. Re-authentication required.");
                        notify(SeqClient.getAuthService().getLastError().getMessage());
                        return;
                    }

                    if (status == 403 || normalized.contains("not in guild") || normalized.contains("guild")) {
                        notInGuild = true;
                        authFailed = true;
                        authenticated = false;
                        notify("Access denied: you are not in the guild.");
                        SeqClient.LOGGER.warn(
                                "[WebSocket] Guild error detected; disabling auto-reconnect and closing socket");
                        autoReconnect = false;
                        close();
                        return;
                    }

                    if (normalized.contains("already linked")) {
                        notify(error);
                        return;
                    }

                    if (isPartyFinderError(json, normalized)) {
                        SeqClient.getPartyFinderManager().pushUiError(error);
                        return;
                    }

                    if (normalized.contains("validation")) {
                        if ("Unknown backend error".equals(error)) {
                            notify("Request rejected by backend validation. Please check your input.");
                        } else {
                            notify(error);
                        }
                        return;
                    }

                    notify("Error: " + error);
                }
                default -> SeqClient.LOGGER.warn("[WebSocket] Unhandled incoming message type={}", type);
            }
        } catch (Exception e) {
            SeqClient.LOGGER.error("[WebSocket] Failed to handle message payload={}", truncate(message, 512), e);
        }
    }

    // ── Handler registration ──

    public static void onDiscordChat(Consumer<DiscordChatMessage> handler) {
        SeqClient.LOGGER.info("[WebSocket] Registering discord_chat handler present={}", handler != null);
        discordChatHandler = handler;
    }

    public static void onPartyFinderUpdate(Consumer<PartyFinderUpdateMessage> handler) {
        SeqClient.LOGGER.info("[WebSocket] Registering party_finder_update handler present={}", handler != null);
        partyFinderUpdateHandler = handler;
    }

    public static void onPartyFinderInvite(Consumer<PartyFinderInviteMessage> handler) {
        SeqClient.LOGGER.info("[WebSocket] Registering party_finder_invite handler present={}", handler != null);
        partyFinderInviteHandler = handler;
    }

    public static void onPartyFinderStaleWarning(Consumer<PartyFinderStaleWarningMessage> handler) {
        SeqClient.LOGGER.info("[WebSocket] Registering party_finder_stale_warning handler present={}", handler != null);
        partyFinderStaleWarningHandler = handler;
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

    // ── Utility ──

    public static boolean isConnected() {
        return instance != null && instance.isOpen() && instance.authenticated;
    }

    public boolean isDiscordLinked() {
        return hasDiscordUsername(getLinkedDiscordUsername());
    }

    public String getLinkedDiscordUsername() {
        return SeqClient.getConfigManager().getDiscordUsername();
    }

    public void unlinkLocally() {
        disconnectInternal(false);
        SeqClient.getAuthService().clearSession();
        clearDiscordUsername();
        notify("Authentication cleared.");
    }

    private boolean canAttemptAuthNow() {
        long now = System.currentTimeMillis();
        if (now >= nextAllowedAuthAttemptAtMs) {
            return true;
        }
        long waitMs = nextAllowedAuthAttemptAtMs - now;
        SeqClient.LOGGER.warn("[WebSocket] Auth attempt throttled waitMs={}", waitMs);
        notifyConnectionFailure("Auth throttled. Retrying in " + Math.max(1, waitMs / 1000) + "s.", false);
        return false;
    }

    private void registerAuthFailure() {
        long delay = Math.min(AUTH_BACKOFF_BASE_MS * (1L << Math.min(authAttempt, 5)), AUTH_BACKOFF_CAP_MS);
        authAttempt++;
        nextAllowedAuthAttemptAtMs = System.currentTimeMillis() + delay;
    }

    private boolean canSendPrivileged(String type) {
        if (!authenticated || authFailed || notInGuild) {
            SeqClient.LOGGER.warn(
                    "[WebSocket] Dropping {}: authenticated={} authFailed={} notInGuild={}",
                    type,
                    authenticated,
                    authFailed,
                    notInGuild);
            return false;
        }
        long now = System.currentTimeMillis();
        if (now < nextPrivilegedSendAtMs) {
            SeqClient.LOGGER.debug("[WebSocket] Throttled {} send", type);
            return false;
        }
        nextPrivilegedSendAtMs = now + PRIVILEGED_SEND_THROTTLE_MS;
        return true;
    }

    private static boolean isPrivilegedType(String type) {
        return "guild_chat".equals(type)
                || "guild_raid_announcement".equals(type)
                || "guild_bank_event".equals(type)
                || "guild_war_submission".equals(type)
                || "party_class_update".equals(type)
                || "party_sync_snapshot".equals(type)
                || "party_sync_member_removed".equals(type)
                || "link_request".equals(type)
                || "get_connected".equals(type);
    }

    private void requestDiscordLink(boolean allowRelink) {
        if (!isOpen() || !authenticated || authFailed || notInGuild) {
            return;
        }

        send("link_request", buildLinkRequestPayload(allowRelink));
        pendingDiscordLinkRequest = false;
    }

    private void handleAuthChallenge(JsonObject json) {
        String url = extractPrimitiveString(json, "url");
        String code = extractPrimitiveString(json, "code");
        if (url == null) {
            notify("Backend returned an invalid Discord OAuth link challenge.");
            return;
        }

        if (code != null) {
            notify("Discord link code: " + code);
        }

        notifyClickable("Click to link Discord", url);
        if (openBrowser(url)) {
            notify("Opened Discord OAuth in your browser.");
        } else {
            notify("Could not open browser automatically. Click the link shown in chat.");
        }
    }

    private void handleAuthSuccess(JsonObject json) {
        String token = extractPrimitiveString(json, "token");
        if (token == null) {
            notify("Discord link completed, but backend did not return a token.");
            return;
        }

        String minecraftUsername = extractPrimitiveString(json, "minecraft_username");
        String discordUsername = extractPrimitiveString(json, "discord_username");
        storeAuthSuccessSession(token, minecraftUsername);
        storeDiscordUsername(discordUsername);
        if (discordUsername != null) {
            notify("Discord account linked as " + discordUsername + ".");
        } else {
            notify("Discord account linked.");
        }
    }

    private void storeAuthSuccessSession(String token, String minecraftUsername) {
        StoredAuthSession current = SeqClient.getConfigManager().getStoredAuthSession();
        String minecraftUuid = current != null
                ? current.minecraftUuid()
                : SeqClient.getConfigManager().getMinecraftUuid();
        String resolvedUsername = minecraftUsername;
        if (resolvedUsername == null || resolvedUsername.isBlank()) {
            resolvedUsername = current != null
                    ? current.minecraftUsername()
                    : SeqClient.getConfigManager().getMinecraftUsername();
        }

        Instant expiresAt = extractJwtExpiration(token);
        SeqClient.getConfigManager()
                .setAuthSession(new StoredAuthSession(token, expiresAt, minecraftUuid, resolvedUsername));
    }

    private boolean openBrowser(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }

        if (!Desktop.isDesktopSupported()) {
            return false;
        }

        try {
            Desktop.getDesktop().browse(URI.create(url));
            return true;
        } catch (Exception exception) {
            SeqClient.LOGGER.warn("[WebSocket] Failed to open browser for Discord OAuth", exception);
            return false;
        }
    }

    private static Instant extractJwtExpiration(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }

        try {
            String[] parts = token.split("\\.");
            if (parts.length < 2) {
                return null;
            }

            String payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            JsonObject payload = GSON.fromJson(payloadJson, JsonObject.class);
            if (payload == null || !payload.has("exp") || !payload.get("exp").isJsonPrimitive()) {
                return null;
            }

            return Instant.ofEpochSecond(payload.get("exp").getAsLong());
        } catch (Exception ignored) {
            return null;
        }
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

    private void maybeNotifyVersionRejection(String capability, String minimumSafeVersion, String backendMessage) {
        String scope = capability == null || capability.isBlank() ? "general" : capability;
        int count = versionRejectionCounts.merge(scope, 1, Integer::sum);
        int interval = VERSION_REMINDER_INTERVALS.getOrDefault(scope, Integer.MAX_VALUE);
        if (count != 1 && (interval == Integer.MAX_VALUE || count % interval != 0)) {
            return;
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

        String feature = switch (scope) {
            case "guild_chat" -> "guild chat relays";
            case "guild_raid_announcement" -> "raid completion relays";
            case "guild_bank_event" -> "guild bank relays";
            case "guild_war_submission" -> "guild war tracking";
            default -> "some Sequoia features";
        };
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

    private static boolean isPartyFinderError(JsonObject json, String normalizedMessage) {
        if (normalizedMessage != null
                && (normalizedMessage.contains("party finder")
                        || normalizedMessage.contains("party_finder")
                        || normalizedMessage.contains("listing")
                        || normalizedMessage.contains("invite"))) {
            return true;
        }
        if (json == null) {
            return false;
        }
        if (json.has("context") && json.get("context").isJsonPrimitive()) {
            String context = json.get("context").getAsString().toLowerCase(Locale.ROOT);
            if (context.contains("party_finder") || context.contains("party finder")) {
                return true;
            }
        }
        if (json.has("request_type") && json.get("request_type").isJsonPrimitive()) {
            String requestType = json.get("request_type").getAsString().toLowerCase(Locale.ROOT);
            return requestType.startsWith("party_")
                    || requestType.contains("listing")
                    || requestType.contains("invite");
        }
        return false;
    }

    public String getEnvironment() {
        return BuildConfig.ENVIRONMENT;
    }

    public String getUptimeString() {
        if (connectedSince == null) return null;
        java.time.Duration dur = java.time.Duration.between(connectedSince, Instant.now());
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

    private void storeDiscordUsername(String discordUsername) {
        if (hasDiscordUsername(discordUsername)) {
            SeqClient.getConfigManager().setDiscordUsername(discordUsername);
        }
    }

    private void clearDiscordUsername() {
        SeqClient.getConfigManager().clearDiscordUsername();
    }

    // ── Message records ──

    public record DiscordChatMessage(String username, String message) {}

    public record PartyFinderUpdateMessage(String action, JsonObject listingJson) {}

    public record PartyFinderInviteMessage(
            long listingId, String inviterUUID, String inviteToken, JsonObject listingJson) {}

    public record PartyFinderStaleWarningMessage(
            String reason, long listingId, Instant disbandAt, long minutesRemaining) {}
}
