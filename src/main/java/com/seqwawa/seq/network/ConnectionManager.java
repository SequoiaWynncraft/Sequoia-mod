package com.seqwawa.seq.network;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.net.URI;
import java.time.Instant;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import com.seqwawa.seq.model.GuildWarQueueSubmission;
import lombok.Getter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import com.seqwawa.seq.accessors.NotificationAccessor;
import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.config.ConfigManager;
import com.seqwawa.seq.model.ChatItemPreview;
import com.seqwawa.seq.managers.GuildStorageTracker;
import com.seqwawa.seq.managers.TreasuryOutManager;
import com.seqwawa.seq.model.BombShareType;
import com.seqwawa.seq.model.GuildWarSubmission;
import com.seqwawa.seq.model.WynnClassType;
import com.seqwawa.seq.network.auth.AuthErrorCode;
import com.seqwawa.seq.network.auth.AuthException;
import com.seqwawa.seq.utils.MinecraftUsername;
import com.seqwawa.seq.utils.WynnClassCache;

public class ConnectionManager extends WebSocketClient implements NotificationAccessor {

    private static final Gson GSON = new Gson();
    private static final long RECONNECT_BASE_MS = 1_000;
    private static final long RECONNECT_CAP_MS = 60_000;
    private static final int MAX_AUTO_RECONNECT_ATTEMPTS = 5;
    private static final int MAX_GUILD_CHAT_MESSAGE_LENGTH = 512;
    private static final long AUTH_BACKOFF_BASE_MS = 2_000;
    private static final long AUTH_BACKOFF_CAP_MS = 60_000;
    private static final long PRIVILEGED_SEND_THROTTLE_MS = 50;
    private static final Pattern URL_PATTERN = Pattern.compile("^(https?://).+", Pattern.CASE_INSENSITIVE);
    private static final Map<String, Integer> VERSION_REMINDER_INTERVALS = Map.of(
            "bomb_share_request", 5,
            "bomb_share_submit", 5,
            "treasury_out", 1,
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
    private static final ExecutorService treasuryAuthExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "seq-treasury-auth");
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
    private volatile boolean membershipProbePending;
    private volatile boolean memberFeaturesDisabled;
    private volatile long nextAllowedAuthAttemptAtMs;
    private volatile int authAttempt;
    private volatile long nextPrivilegedSendAtMs;
    private volatile boolean connectInProgress;
    private volatile boolean userInitiatedConnectFlow;
    private volatile boolean treasuryOnlyConnection;
    private final TreasurySessionAuthenticator treasurySessionAuthenticator;
    private final IncomingMessageRouter incomingMessageRouter;
    private final Deque<GuildWarSubmission> pendingGuildWarSubmissions = new ConcurrentLinkedDeque<>();

    // Reconnect state
    private static boolean autoReconnect = true;
    private static int reconnectAttempt = 0;
    private static ScheduledFuture<?> reconnectTask;
    private static boolean autoConnectSuppressedByManualDisconnect;

    // Callbacks for new message types
    private static Consumer<DiscordChatMessage> discordChatHandler;
    private static Consumer<PartyFinderUpdateMessage> partyFinderUpdateHandler;
    private static Consumer<PartyFinderInviteMessage> partyFinderInviteHandler;
    private static Consumer<PartyFinderStaleWarningMessage> partyFinderStaleWarningHandler;
    private static Consumer<BombSharePromptMessage> bombSharePromptHandler;
    private static Consumer<BombShareResultMessage> bombShareResultHandler;
    private static Consumer<TreasuryOutRecordedMessage> treasuryOutRecordedHandler;
    private static Predicate<TreasuryOutErrorMessage> treasuryOutErrorHandler;
    private static final Map<String, Integer> versionRejectionCounts = new ConcurrentHashMap<>();
    private final Map<String, BombSharePromptMessage> pendingBombSharePrompts = new ConcurrentHashMap<>();

    public static ConnectionManager getInstance() {
        if (instance == null) {
            instance = new ConnectionManager();
        }
        return instance;
    }

    private ConnectionManager() {
        super(URI.create(BuildConfig.WS_URL));
        treasurySessionAuthenticator = new TreasurySessionAuthenticator(
                ConnectionManager::joinActiveMinecraftSession,
                treasuryAuthExecutor,
                this::sendTreasuryAuthResponse,
                this::handleTreasuryAuthFailure);
        incomingMessageRouter = new IncomingMessageRouter(this);
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
        connectInternal(false, false);
    }

    public void connectManually() {
        connectInternal(true, true);
    }

    private void connectInternal(boolean userInitiated, boolean forceTokenRefresh) {
        if (userInitiated) {
            autoConnectSuppressedByManualDisconnect = false;
        }
        WynncraftServerPolicy.Scope serverScope = WynncraftServerPolicy.currentScope();
        if (serverScope != WynncraftServerPolicy.Scope.MAIN) {
            connectInProgress = false;
            finishConnectFlow();
            if (serverScope == WynncraftServerPolicy.Scope.BLOCKED) {
                autoReconnect = false;
                cancelReconnect();
                if (userInitiated) {
                    notify(WynncraftServerPolicy.MAIN_SERVER_ONLY_MESSAGE);
                }
                SeqClient.LOGGER.info("[WebSocket] Blocking connection outside main Wynncraft host");
            } else {
                if (userInitiated) {
                    notify("Waiting until Wynncraft server transfer finishes.");
                }
                SeqClient.LOGGER.info("[WebSocket] Delaying connection until Wynncraft host is confirmed");
            }
            return;
        }

        userInitiatedConnectFlow = userInitiated;
        autoReconnect = true;
        SeqClient.LOGGER.info(
                "[WebSocket] connectInternal() called open={} authenticated={} autoReconnect={} configuredUrl={} clientUri={}",
                isOpen(),
                authenticated,
                autoReconnect,
                BuildConfig.WS_URL,
                getURI());
        if (isOpen()) {
            notifyConnectionStatus("Already connected/connecting.");
            finishConnectFlow();
            return;
        }
        if (connectInProgress) {
            notifyConnectionStatus("Already connected/connecting.");
            return;
        }

        notifyConnectionStatus("Connecting to " + BuildConfig.ENVIRONMENT + "...");
        if (shouldUseTreasuryOnlyConnection(currentMinecraftUsername())) {
            prepareTreasuryOnlyConnection();
        } else {
            prepareAuthenticatedConnection(forceTokenRefresh);
        }
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
            connectInProgress = false;
            treasurySessionAuthenticator.reset();
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
            membershipProbePending = false;
            memberFeaturesDisabled = false;
            treasurySessionAuthenticator.reset();
            connectedSince = null;
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
        membershipProbePending = false;
        memberFeaturesDisabled = false;
        treasurySessionAuthenticator.reset();
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
        membershipProbePending = false;
        memberFeaturesDisabled = false;
        treasurySessionAuthenticator.reset();
        connectedSince = null;
        autoReconnect = true;
        authAttempt = 0;
        nextAllowedAuthAttemptAtMs = 0;

        if (treasuryOnlyConnection) {
            notifyConnectionStatus("Connected for Treasury OUT. Verifying the cinfrascitizen Minecraft session...");
        } else {
            String username = SeqClient.getConfigManager().getMinecraftUsername();
            notifyConnectionStatus(
                    username != null && !username.isBlank()
                            ? "Connected websocket for " + username + ". Waiting for backend authentication..."
                            : "Connected websocket to " + BuildConfig.ENVIRONMENT
                                    + ". Waiting for backend authentication...");
        }
        if (!treasuryOnlyConnection) {
            finishConnectFlow();
        }
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
        membershipProbePending = false;
        memberFeaturesDisabled = false;
        treasurySessionAuthenticator.reset();
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
        treasurySessionAuthenticator.reset();
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
                || userInitiatedConnectFlow;
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
                                        "Could not reconnect automatically. Run /seq connect manually.")),
                        false);
            }
        });
    }

    // ── Outgoing messages ──

    private boolean send(String type, JsonObject payload) {
        if (isServerScopedType(type) && WynncraftServerPolicy.currentScope() != WynncraftServerPolicy.Scope.MAIN) {
            SeqClient.LOGGER.warn("[WebSocket] Dropping {} outside confirmed main Wynncraft host", type);
            return false;
        }
        if (isAuthenticatedOutboundType(type) && !canSendAuthenticated(type)) {
            return false;
        }
        if (isThrottleLimitedType(type) && !canSendThrottleLimited(type)) {
            return false;
        }
        sendPrepared(type, payload);
        return true;
    }

    private void sendPrepared(String type, JsonObject payload) {
        if (payload == null) payload = new JsonObject();
        payload.addProperty("type", type);
        if (TreasuryOutRequest.TYPE.equals(type)) {
            SeqClient.LOGGER.debug(
                    "[WebSocket] send type={} requestId={}",
                    type,
                    IncomingMessageParser.primitiveString(payload, "request_id"));
        } else {
            SeqClient.LOGGER.debug("[WebSocket] send type={} payload={}", type, truncate(payload.toString(), 512));
        }
        send(GSON.toJson(payload));
    }

    private void prepareAuthenticatedConnection(boolean forceTokenRefresh) {
        treasuryOnlyConnection = false;
        treasurySessionAuthenticator.reset();
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
        if (!canAttemptAuthNow()) {
            return;
        }
        connectInProgress = true;
        SeqClient.getAuthService()
                .ensureValidToken(forceTokenRefresh)
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

    private void prepareTreasuryOnlyConnection() {
        WynncraftServerPolicy.Scope scope = WynncraftServerPolicy.currentScope();
        if (scope != WynncraftServerPolicy.Scope.MAIN) {
            connectInProgress = false;
            finishConnectFlow();
            if (scope == WynncraftServerPolicy.Scope.BLOCKED) {
                autoReconnect = false;
                cancelReconnect();
                notifyConnectionFailure(WynncraftServerPolicy.MAIN_SERVER_ONLY_MESSAGE, false);
            } else if (autoReconnect) {
                scheduleReconnect();
            }
            return;
        }

        connectInProgress = true;
        treasuryOnlyConnection = true;
        treasurySessionAuthenticator.reset();
        try {
            configureHandshakeAuthorization(null);
            super.connect();
        } catch (Exception exception) {
            connectInProgress = false;
            SeqClient.LOGGER.error("Failed to connect Treasury OUT websocket", exception);
            notifyConnectionFailure("Failed to connect: " + exception.getMessage(), false);
            instance = null;
            finishConnectFlow();
            scheduleReconnect();
        }
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

    static JsonObject buildGuildWarSubmissionPayload(GuildWarSubmission submission) {
        return OutboundPayloadFactory.guildWarSubmission(submission);
    }

    static JsonObject buildBombShareRequestPayload(String canonicalKey, List<BombShareType> requestedTypes) {
        return OutboundPayloadFactory.bombShareRequest(canonicalKey, requestedTypes);
    }

    static JsonObject buildBombShareSubmitPayload(String requestId, List<String> worlds) {
        return OutboundPayloadFactory.bombShareSubmit(requestId, worlds);
    }

    static JsonObject serializeTreasuryOutRequest(TreasuryOutRequest request) {
        return OutboundPayloadFactory.treasuryOut(request);
    }

    static JsonObject serializeTreasuryAuthResponse(TreasuryAuthResponse response) {
        return OutboundPayloadFactory.treasuryAuth(response);
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
        if (!isOpen()
                || !authenticated
                || authFailed
                || notInGuild
                || memberFeaturesDisabled
                || !WynncraftServerPolicy.isCurrentServerAllowed()) {
            callback.accept(List.of());
            return;
        }
        this.connectedUsersCallback = callback;
        send("get_connected", null);
    }

    public boolean sendBombShareRequest(String canonicalKey, List<BombShareType> requestedTypes) {
        if (!isOpen() || !authenticated || authFailed || notInGuild) {
            SeqClient.LOGGER.warn(
                    "[WebSocket] sendBombShareRequest dropped open={} authenticated={} authFailed={} notInGuild={}",
                    isOpen(),
                    authenticated,
                    authFailed,
                    notInGuild);
            return false;
        }
        if (canonicalKey == null || canonicalKey.isBlank() || requestedTypes == null || requestedTypes.isEmpty()) {
            SeqClient.LOGGER.warn("[WebSocket] sendBombShareRequest dropped invalid payload canonicalKey={}", canonicalKey);
            return false;
        }

        send("bomb_share_request", buildBombShareRequestPayload(canonicalKey, requestedTypes));
        return true;
    }

    public boolean sendBombShareSubmit(String requestId, List<String> worlds) {
        if (!isOpen() || !authenticated || authFailed || notInGuild) {
            SeqClient.LOGGER.warn(
                    "[WebSocket] sendBombShareSubmit dropped open={} authenticated={} authFailed={} notInGuild={}",
                    isOpen(),
                    authenticated,
                    authFailed,
                    notInGuild);
            return false;
        }
        if (requestId == null || requestId.isBlank() || worlds == null || worlds.isEmpty()) {
            SeqClient.LOGGER.warn("[WebSocket] sendBombShareSubmit dropped invalid payload requestId={}", requestId);
            return false;
        }

        send("bomb_share_submit", buildBombShareSubmitPayload(requestId, worlds));
        return true;
    }

    public boolean sendTreasuryOut(TreasuryOutRequest request) {
        if (!TreasuryOutManager.isTreasuryMinecraftAccount(currentMinecraftUsername())) {
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
        if (!isOpen() || !treasurySessionAuthenticator.isAuthenticated()) {
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
        send("guild_chat", OutboundPayloadFactory.guildChat(
                safeReportedUsername, safeNickname, cleanedMessage, safeAvatarUrl, itemPreviews));
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

        send("guild_alliance_update", OutboundPayloadFactory.guildAllianceUpdate(safeAction, safeGuildName));
        return true;
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
        return OutboundPayloadFactory.guildAllianceSnapshot(guildNames);
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
        send("guild_raid_announcement", OutboundPayloadFactory.raidAnnouncement(
                usernames, raidType, aspectCount, emeraldCount, experienceCount, srCount));
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

        SeqClient.LOGGER.info(
                "[WebSocket] Sending guild_bank_event action={} player='{}' item='{}'",
                action,
                player,
                itemName);
        send("guild_bank_event", OutboundPayloadFactory.guildBankEvent(
                action, player, quantity, itemName, charges, accessTier, rawMessage));
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

        SeqClient.LOGGER.info(
                "[WebSocket] Sending guild_storage_snapshot emerald={}/{} aspect={}/{}",
                emeraldCurrent,
                emeraldMax,
                aspectCurrent,
                aspectMax);
        send("guild_storage_snapshot", OutboundPayloadFactory.guildStorageSnapshot(
                emeraldCurrent, emeraldMax, aspectCurrent, aspectMax));
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

        SeqClient.LOGGER.info(
                "[WebSocket] Sending guild_storage_reward sender='{}' recipient='{}' resource='{}' amount={} count={} windowStartedAt={}",
                safeSender,
                safeRecipient,
                normalizedResourceType,
                amount,
                count,
                windowStartedAt);
        send("guild_storage_reward", OutboundPayloadFactory.guildStorageReward(
                safeSender, safeRecipient, normalizedResourceType, amount, count, windowStartedAt));
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
            if (!MinecraftUsername.isValid(warrer)) {
                SeqClient.LOGGER.warn("[WebSocket] sendGuildWarSubmission dropped invalid warrer={}", warrer);
                return false;
            }
        }

        WynncraftServerPolicy.Scope serverScope = WynncraftServerPolicy.currentScope();
        if (serverScope == WynncraftServerPolicy.Scope.BLOCKED) {
            SeqClient.LOGGER.warn("[WebSocket] sendGuildWarSubmission dropped outside main Wynncraft host");
            return false;
        }
        if (memberFeaturesDisabled) {
            SeqClient.LOGGER.debug("[WebSocket] Guild war submission disabled for non-member session");
            return true;
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

    public boolean sendGuildWarQueue(GuildWarQueueSubmission submission) {
        if (submission == null
                || submission.territory() == null
                || submission.territory().isBlank()
                || submission.submittedBy() == null
                || submission.submittedBy().isBlank()
                || submission.submittedAt() == null
                || submission.submittedAt().isBlank()
                || submission.defenseRating() == null
                || submission.defenseRating().isBlank()) {
            SeqClient.LOGGER.warn("[WebSocket] sendGuildWarQueue dropped: invalid payload");
            return false;
        }

        WynncraftServerPolicy.Scope serverScope = WynncraftServerPolicy.currentScope();
        if (serverScope == WynncraftServerPolicy.Scope.BLOCKED) {
            SeqClient.LOGGER.warn("[WebSocket] sendGuildWarQueue dropped outside main Wynncraft host");
            return false;
        }
        if (memberFeaturesDisabled) {
            SeqClient.LOGGER.debug("[WebSocket] Guild war queue submission disabled for non-member session");
            return true;
        }
        if (serverScope == WynncraftServerPolicy.Scope.UNKNOWN) {
            SeqClient.LOGGER.warn("[WebSocket] Queueing guild_war_queue until Wynncraft host is confirmed");
            return false;
        }

        if (!authenticated || !isOpen()) {
            SeqClient.LOGGER.warn(
                    "[WebSocket] sendGuildWarQueue dropped open={} authenticated={}", isOpen(), authenticated);
            return false;
        }
        if (authFailed || notInGuild) {
            SeqClient.LOGGER.warn(
                    "[WebSocket] sendGuildWarQueue dropped authFailed={} notInGuild={}", authFailed, notInGuild);
            return false;
        }

        send("guild_war_queue", OutboundPayloadFactory.guildWarQueue(submission));

        return true;
    }

    public static void flushPendingOutbound() {
        if (instance == null) {
            return;
        }
        instance.flushPendingGuildWarSubmissions();
    }

    public static void resetForAccountChange() {
        ConnectionManager current = instance;
        if (current == null) {
            return;
        }

        current.pendingGuildWarSubmissions.clear();
        current.pendingBombSharePrompts.clear();
        current.disconnectInternal(false);
    }

    private void flushPendingGuildWarSubmissions() {
        if (pendingGuildWarSubmissions.isEmpty()
                || !isOpen()
                || !authenticated
                || authFailed
                || notInGuild
                || memberFeaturesDisabled
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
        if (memberFeaturesDisabled) {
            return true;
        }
        if (!canSendAuthenticated("guild_war_submission") || !canSendThrottleLimited("guild_war_submission")) {
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
        send("party_class_update", OutboundPayloadFactory.partyClassUpdate(classType));
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

        JsonObject payload = OutboundPayloadFactory.partySyncSnapshot(active, leaderUsername, memberUsernames);
        SeqClient.LOGGER.info(
                "[WebSocket] Sending party_sync_snapshot active={} leader={} members={} usernames={}",
                active,
                leaderUsername,
                payload.getAsJsonArray("member_usernames").size(),
                memberUsernames);
        return send("party_sync_snapshot", payload);
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

        SeqClient.LOGGER.info("[WebSocket] Sending party_sync_member_removed username={} reason={}", username, reason);
        return send("party_sync_member_removed", OutboundPayloadFactory.partySyncMemberRemoved(username, reason));
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
            IncomingMessageParser.IncomingMessage incoming = IncomingMessageParser.parse(message);
            if (incoming == null) {
                SeqClient.LOGGER.warn("[WebSocket] Dropping message without type: {}", truncate(message, 512));
                return;
            }
            String type = incoming.type();
            JsonObject json = incoming.payload();
            if ("treasury_out_recorded".equals(type)
                    || TreasuryAuthResponse.CHALLENGE_TYPE.equals(type)
                    || TreasuryAuthResponse.AUTHENTICATED_TYPE.equals(type)) {
                SeqClient.LOGGER.debug(
                        "[WebSocket] Received message type={} requestId={}",
                        type,
                        IncomingMessageParser.primitiveString(json, "request_id"));
            } else {
                SeqClient.LOGGER.info("[WebSocket] Received message type={} payload={}", type, truncate(message, 512));
            }

            incomingMessageRouter.route(incoming);
        } catch (Exception e) {
            SeqClient.LOGGER.error("[WebSocket] Failed to handle message payload={}", truncate(message, 512), e);
        }
    }

    void handleTreasuryAuthChallenge(JsonObject payload) {
        if (!treasuryOnlyConnection) {
            SeqClient.LOGGER.warn("[TreasuryAuth] Ignoring challenge on an authenticated WebSocket");
            return;
        }
        String nonce = IncomingMessageParser.primitiveString(payload, "nonce");
        if (!treasurySessionAuthenticator.handleChallenge(nonce)) {
            SeqClient.LOGGER.warn("[TreasuryAuth] Ignoring invalid or unexpected challenge");
        }
    }

    void handleTreasuryAuthenticated(JsonObject payload) {
        String nonce = IncomingMessageParser.primitiveString(payload, "nonce");
        if (!treasuryOnlyConnection || !treasurySessionAuthenticator.confirm(nonce)) {
            SeqClient.LOGGER.warn("[TreasuryAuth] Ignoring uncorrelated authentication confirmation");
            return;
        }
        authFailed = false;
        connectedSince = Instant.now();
        autoReconnect = true;
        notifyConnectionStatus("Treasury OUT identity verified as cinfrascitizen.");
        finishConnectFlow();
        SeqClient.LOGGER.info("[TreasuryAuth] Minecraft session proof accepted by backend");
    }

    void handleAuthenticated(JsonObject payload) {
        if (treasuryOnlyConnection) {
            SeqClient.LOGGER.warn("[TreasuryAuth] Ignoring bearer-authenticated response on Treasury-only WebSocket");
            return;
        }
        authenticated = true;
        authFailed = false;
        notInGuild = false;
        memberFeaturesDisabled = false;
        membershipProbePending = true;
        authAttempt = 0;
        nextAllowedAuthAttemptAtMs = 0;
        connectedSince = Instant.now();
        autoReconnect = true;
        String discordUser = IncomingMessageParser.authenticatedDiscordUsername(payload);
        if (discordUser != null) {
            storeDiscordUsername(discordUser);
            notifyConnectionStatus("Connected as " + discordUser);
        } else {
            clearDiscordUsername();
        }
        sendPrepared("get_connected", null);
        flushPendingGuildWarSubmissions();
        sendLocalPartyClassUpdate();
    }

    void handleConnectedUsers(JsonObject payload) {
        boolean wasMembershipProbe = membershipProbePending;
        membershipProbePending = false;
        memberFeaturesDisabled = false;
        List<String> users = IncomingMessageParser.connectedUsers(payload);
        SeqClient.LOGGER.info(
                "[WebSocket] connected_users received count={} callbackPresent={}",
                users.size(),
                connectedUsersCallback != null);
        if (connectedUsersCallback != null) {
            connectedUsersCallback.accept(users);
            connectedUsersCallback = null;
        } else if (!wasMembershipProbe) {
            SeqClient.LOGGER.warn("[WebSocket] connected_users had no callback listener");
        }
    }

    void handleBombSharePrompt(JsonObject payload) {
        BombSharePromptMessage prompt = IncomingMessageParser.bombSharePrompt(payload);
        if (prompt.requestId() != null) {
            pendingBombSharePrompts.put(prompt.requestId(), prompt);
        }
        if (bombSharePromptHandler != null) {
            bombSharePromptHandler.accept(prompt);
        } else {
            SeqClient.LOGGER.warn("[WebSocket] Received bomb_share_prompt but handler is not registered");
        }
    }

    void handleBombShareResult(JsonObject payload) {
        BombShareResultMessage result = IncomingMessageParser.bombShareResult(payload);
        if (result.requestId() != null) {
            pendingBombSharePrompts.remove(result.requestId());
        }
        if (bombShareResultHandler != null) {
            bombShareResultHandler.accept(result);
        } else {
            SeqClient.LOGGER.warn("[WebSocket] Received bomb_share_result but handler is not registered");
        }
    }

    void handleTreasuryOutRecorded(JsonObject payload) {
        TreasuryOutRecordedMessage recorded = IncomingMessageParser.treasuryOutRecorded(payload);
        if (treasuryOutRecordedHandler != null) {
            treasuryOutRecordedHandler.accept(recorded);
        } else {
            SeqClient.LOGGER.warn("[WebSocket] Received treasury_out_recorded but handler is not registered");
        }
    }

    void handleGuildStorageSnapshot(JsonObject payload) {
        IncomingMessageParser.GuildStorageSnapshot snapshot = IncomingMessageParser.guildStorageSnapshot(payload);
        SeqClient.LOGGER.info(
                "[WebSocket] Applying guild_storage_snapshot emerald={}/{} aspect={}/{}",
                snapshot.emeraldCurrent(),
                snapshot.emeraldMax(),
                snapshot.aspectCurrent(),
                snapshot.aspectMax());
        Minecraft.getInstance().execute(() -> GuildStorageTracker.getInstance().applyRemoteSnapshot(
                snapshot.emeraldCurrent(), snapshot.emeraldMax(), snapshot.aspectCurrent(), snapshot.aspectMax()));
    }

    void handleDiscordChat(JsonObject payload) {
        if (discordChatHandler == null) {
            SeqClient.LOGGER.warn("[WebSocket] Received discord_chat but handler is not registered");
            return;
        }
        DiscordChatMessage discordChat = IncomingMessageParser.discordChat(payload);
        String username = discordChat.username();
        ConfigManager configManager = SeqClient.getConfigManager();
        if (configManager != null && shouldIgnoreDiscordChatSender(username, configManager.ignoredBridgeUsers())) {
            SeqClient.LOGGER.debug("[WebSocket] Ignoring discord_chat from {}", username);
            return;
        }
        SeqClient.LOGGER.info("[WebSocket] Dispatching discord_chat from {}", username);
        discordChatHandler.accept(discordChat);
    }

    void handlePartyFinderUpdate(JsonObject payload) {
        String action = IncomingMessageParser.partyFinderAction(payload);
        if (partyFinderUpdateHandler == null) {
            SeqClient.LOGGER.warn("[WebSocket] Received party_finder_update but handler is not registered");
            return;
        }
        PartyFinderUpdateMessage update = IncomingMessageParser.partyFinderUpdate(payload, action);
        SeqClient.LOGGER.info(
                "[WebSocket] Dispatching party_finder_update action={} hasListing={}",
                action,
                update.listingJson() != null);
        partyFinderUpdateHandler.accept(update);
    }

    void handlePartyFinderInvite(JsonObject payload) {
        if (partyFinderInviteHandler == null) {
            SeqClient.LOGGER.warn("[WebSocket] Received party_finder_invite but handler is not registered");
            return;
        }
        PartyFinderInviteMessage invite = IncomingMessageParser.partyFinderInvite(payload);
        SeqClient.LOGGER.info(
                "[WebSocket] Dispatching party_finder_invite listingId={} inviterUUID={} tokenPresent={} hasListing={}",
                invite.listingId(),
                invite.inviterUUID(),
                invite.inviteToken() != null && !invite.inviteToken().isBlank(),
                invite.listingJson() != null);
        partyFinderInviteHandler.accept(invite);
    }

    void handlePartyFinderStaleWarning(JsonObject payload) {
        if (partyFinderStaleWarningHandler == null) {
            SeqClient.LOGGER.warn("[WebSocket] Received party_finder_stale_warning but handler is not registered");
            return;
        }
        PartyFinderStaleWarningMessage warning = IncomingMessageParser.partyFinderStaleWarning(payload);
        SeqClient.LOGGER.info(
                "[WebSocket] Dispatching party_finder_stale_warning reason={} listingId={} disbandAt={} minutesRemaining={}",
                warning.reason(),
                warning.listingId(),
                warning.disbandAt(),
                warning.minutesRemaining());
        partyFinderStaleWarningHandler.accept(warning);
    }

    void handleBackendError(JsonObject payload) {
        IncomingMessageParser.BackendError backendError = IncomingMessageParser.backendError(payload);
        String error = backendError.message();
        String backendCode = backendError.code();
        String requestId = backendError.requestId();
        String minimumSafeVersion = backendError.minimumSafeVersion();
        String capability = backendError.capability();
        int status = backendError.status();
        String normalized = error.toLowerCase(Locale.ROOT);

        if (treasuryOnlyConnection && requestId == null && isTreasuryAuthenticationError(backendCode)) {
            treasurySessionAuthenticator.reset();
            authFailed = true;
            autoReconnect = false;
            notifyConnectionFailure("Treasury identity verification failed: " + error, true);
            finishConnectFlow();
            closeTreasuryAuthFailure();
            return;
        }

        if (requestId != null
                && treasuryOutErrorHandler != null
                && treasuryOutErrorHandler.test(new TreasuryOutErrorMessage(requestId, backendCode, error))) {
            if ("token_invalid".equalsIgnoreCase(backendCode)) {
                authFailed = true;
                authenticated = false;
                registerAuthFailure();
                SeqClient.getAuthService()
                        .invalidateSession(
                                AuthErrorCode.TOKEN_INVALID,
                                "Backend rejected the stored token. Re-authentication required.");
            } else if ("mod_version_unsupported".equalsIgnoreCase(backendCode)) {
                autoReconnect = false;
            }
            return;
        }

        if (isSilentGuildChatMembershipReject(backendCode, capability, normalized)) {
            SeqClient.LOGGER.info("[WebSocket] Guild chat relay rejected because sender is not in guild");
            return;
        }

        if (isSessionMembershipReject(backendCode, capability, normalized)) {
            disableMemberFeaturesForSession();
            return;
        }

        SeqClient.LOGGER.warn("[WebSocket] Backend error status={} code={} message={}", status, backendCode, error);

        if ("mod_version_unsupported".equalsIgnoreCase(backendCode) || status == 426) {
            autoReconnect = false;
            maybeNotifyVersionRejection(capability, minimumSafeVersion, error);
            return;
        }

        if (status == 400 || normalized.contains("invalid auth request")) {
            authFailed = true;
            authenticated = false;
            registerAuthFailure();
            notify("Invalid auth request. Run /seq connect to start a fresh backend session.");
            return;
        }

        if (status == 401 || normalized.contains("invalid token") || normalized.contains("expired")) {
            authFailed = true;
            authenticated = false;
            registerAuthFailure();
            SeqClient.getAuthService()
                    .invalidateSession(
                            normalized.contains("expired") ? AuthErrorCode.TOKEN_EXPIRED : AuthErrorCode.TOKEN_INVALID,
                            normalized.contains("expired")
                                    ? "Backend token expired. Re-authentication required."
                                    : "Backend rejected the stored token. Re-authentication required.");
            notify(SeqClient.getAuthService().getLastError().getMessage());
            return;
        }

        if (isPartyFinderError(backendError, normalized)) {
            SeqClient.getPartyFinderManager().pushUiError(error);
            return;
        }

        if (isCapabilityAuthorizationReject(status, capability)) {
            SeqClient.LOGGER.info(
                    "[WebSocket] Capability authorization rejected capability={} message={}", capability, error);
            notify(formatCapabilityAccessDenied(capability, error));
            return;
        }

        if (status == 403 || normalized.contains("not in guild") || normalized.contains("guild")) {
            notInGuild = true;
            authFailed = true;
            authenticated = false;
            notify("Access denied by backend authorization.");
            SeqClient.LOGGER.warn("[WebSocket] Guild error detected; disabling auto-reconnect and closing socket");
            autoReconnect = false;
            close();
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

    void handleUnhandledIncomingMessage(String type) {
        SeqClient.LOGGER.warn("[WebSocket] Unhandled incoming message type={}", type);
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

    public static void onBombSharePrompt(Consumer<BombSharePromptMessage> handler) {
        SeqClient.LOGGER.info("[WebSocket] Registering bomb_share_prompt handler present={}", handler != null);
        bombSharePromptHandler = handler;
    }

    public static void onBombShareResult(Consumer<BombShareResultMessage> handler) {
        SeqClient.LOGGER.info("[WebSocket] Registering bomb_share_result handler present={}", handler != null);
        bombShareResultHandler = handler;
    }

    public static void onTreasuryOutRecorded(Consumer<TreasuryOutRecordedMessage> handler) {
        SeqClient.LOGGER.info("[WebSocket] Registering treasury_out_recorded handler present={}", handler != null);
        treasuryOutRecordedHandler = handler;
    }

    public static void onTreasuryOutError(Predicate<TreasuryOutErrorMessage> handler) {
        SeqClient.LOGGER.info("[WebSocket] Registering treasury error handler present={}", handler != null);
        treasuryOutErrorHandler = handler;
    }

    public Optional<BombSharePromptMessage> getPendingBombSharePrompt(String requestId) {
        if (requestId == null || requestId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(pendingBombSharePrompts.get(requestId));
    }

    public Optional<BombSharePromptMessage> removePendingBombSharePrompt(String requestId) {
        if (requestId == null || requestId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(pendingBombSharePrompts.remove(requestId));
    }

    public boolean hasPendingBombSharePrompt(String requestId) {
        return requestId != null && !requestId.isBlank() && pendingBombSharePrompts.containsKey(requestId);
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

    public static boolean isTreasuryOutConnected() {
        ConnectionManager current = instance;
        return current != null
                && treasuryConnectionReady(
                        current.isOpen(),
                        current.treasuryOnlyConnection,
                        current.treasurySessionAuthenticator.isAuthenticated());
    }

    static boolean treasuryConnectionReady(boolean open, boolean treasuryOnly, boolean sessionAuthenticated) {
        return open && treasuryOnly && sessionAuthenticated;
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
        notify("Backend session cleared.");
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

    private boolean canSendAuthenticated(String type) {
        if (!authenticated || authFailed || notInGuild) {
            SeqClient.LOGGER.warn(
                    "[WebSocket] Dropping {}: authenticated={} authFailed={} notInGuild={}",
                    type,
                    authenticated,
                    authFailed,
                    notInGuild);
            return false;
        }
        if (memberFeaturesDisabled && isSequoiaMemberOnlyType(type)) {
            SeqClient.LOGGER.debug("[WebSocket] Dropping disabled Sequoia-only feature type={}", type);
            return false;
        }
        return true;
    }

    private void disableMemberFeaturesForSession() {
        membershipProbePending = false;
        pendingGuildWarSubmissions.clear();
        if (memberFeaturesDisabled) {
            SeqClient.LOGGER.debug("[WebSocket] Additional non-member feature rejection suppressed");
            return;
        }

        memberFeaturesDisabled = true;
        SeqClient.LOGGER.info("[WebSocket] Non-member session detected; Sequoia-only features are disabled");
        notify("You are not a Sequoia member. Sequoia-only features are disabled for this session.");
    }

    private boolean canSendThrottleLimited(String type) {
        long now = System.currentTimeMillis();
        if (now < nextPrivilegedSendAtMs) {
            SeqClient.LOGGER.debug("[WebSocket] Throttled {} send", type);
            return false;
        }
        nextPrivilegedSendAtMs = now + PRIVILEGED_SEND_THROTTLE_MS;
        return true;
    }

    static boolean isAuthenticatedOutboundType(String type) {
        return isServerScopedType(type) && !TreasuryOutRequest.TYPE.equals(type);
    }

    static boolean shouldUseTreasuryOnlyConnection(String activeMinecraftUsername) {
        return TreasuryOutManager.isTreasuryMinecraftAccount(activeMinecraftUsername);
    }

    private static boolean isTreasuryAuthenticationError(String code) {
        return "treasury_auth_failed".equalsIgnoreCase(code)
                || "treasury_auth_timeout".equalsIgnoreCase(code);
    }

    private static void joinActiveMinecraftSession(String nonce) throws Exception {
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
                .joinServer(user.getProfileId(), user.getAccessToken(), nonce);
    }

    private void sendTreasuryAuthResponse(String nonce) {
        if (instance != this || !treasuryOnlyConnection || !isOpen()) {
            treasurySessionAuthenticator.reset();
            return;
        }
        sendPrepared(TreasuryAuthResponse.TYPE, serializeTreasuryAuthResponse(new TreasuryAuthResponse(nonce)));
        SeqClient.LOGGER.debug("[TreasuryAuth] Minecraft session joined; response sent to backend");
    }

    private void handleTreasuryAuthFailure(Throwable failure) {
        authFailed = true;
        autoReconnect = false;
        String message = failure == null || failure.getMessage() == null || failure.getMessage().isBlank()
                ? "Minecraft session verification failed."
                : failure.getMessage();
        SeqClient.LOGGER.warn("[TreasuryAuth] Minecraft session proof failed: {}", message);
        notifyConnectionFailure("Treasury identity verification failed. Restart Minecraft and try again.", true);
        finishConnectFlow();
        closeTreasuryAuthFailure();
    }

    private void closeTreasuryAuthFailure() {
        if (isOpen()) {
            close(1008, "Treasury authentication failed");
        }
    }

    private static String currentMinecraftUsername() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.getUser() == null ? null : minecraft.getUser().getName();
    }

    static boolean isSequoiaMemberOnlyType(String type) {
        return "bomb_share_request".equals(type)
                || "bomb_share_submit".equals(type)
                || "guild_chat".equals(type)
                || "guild_alliance_update".equals(type)
                || "guild_alliance_snapshot".equals(type)
                || "guild_raid_announcement".equals(type)
                || "guild_bank_event".equals(type)
                || "guild_storage_snapshot".equals(type)
                || "guild_storage_reward".equals(type)
                || "guild_war_submission".equals(type)
                || "party_sync_snapshot".equals(type)
                || "party_sync_member_removed".equals(type)
                || "get_connected".equals(type);
    }

    static boolean isServerScopedType(String type) {
        return "bomb_share_request".equals(type)
                || "bomb_share_submit".equals(type)
                || "treasury_out".equals(type)
                || "guild_chat".equals(type)
                || "guild_alliance_update".equals(type)
                || "guild_alliance_snapshot".equals(type)
                || "guild_raid_announcement".equals(type)
                || "guild_bank_event".equals(type)
                || "guild_storage_snapshot".equals(type)
                || "guild_storage_reward".equals(type)
                || "guild_war_submission".equals(type)
                || "party_class_update".equals(type)
                || "party_sync_snapshot".equals(type)
                || "party_sync_member_removed".equals(type)
                || "get_connected".equals(type);
    }

    static boolean isThrottleLimitedType(String type) {
        return "bomb_share_request".equals(type)
                || "bomb_share_submit".equals(type)
                || "treasury_out".equals(type)
                || "guild_chat".equals(type)
                || "guild_alliance_update".equals(type)
                || "guild_raid_announcement".equals(type)
                || "guild_bank_event".equals(type)
                || "guild_war_submission".equals(type)
                || "party_class_update".equals(type)
                || "party_sync_snapshot".equals(type)
                || "party_sync_member_removed".equals(type)
                || "get_connected".equals(type);
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
        return MinecraftUsername.normalize(username);
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

    static boolean isSilentGuildChatMembershipReject(String backendCode, String capability, String normalizedMessage) {
        return "not_in_guild".equalsIgnoreCase(backendCode)
                && "guild_chat".equalsIgnoreCase(capability)
                && normalizedMessage != null
                && normalizedMessage.contains("guild");
    }

    static boolean isSessionMembershipReject(String backendCode, String capability, String normalizedMessage) {
        return "not_in_guild".equalsIgnoreCase(backendCode)
                && (capability == null || capability.isBlank())
                && normalizedMessage != null
                && normalizedMessage.contains("sequoia")
                && normalizedMessage.contains("member");
    }

    private static boolean isCapabilityAuthorizationReject(int status, String capability) {
        return status == 403 && capability != null && !capability.isBlank();
    }

    private static String formatCapabilityAccessDenied(String capability, String backendMessage) {
        String feature = capability.replace('_', ' ');
        if (backendMessage != null && !backendMessage.isBlank() && !"Unknown backend error".equals(backendMessage)) {
            return feature + " unavailable: " + backendMessage;
        }
        return feature + " unavailable for this account.";
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
            case "bomb_share_request", "bomb_share_submit" -> "bomb share relays";
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

    private static boolean isPartyFinderError(
            IncomingMessageParser.BackendError backendError, String normalizedMessage) {
        if (normalizedMessage != null
                && (normalizedMessage.contains("party finder")
                        || normalizedMessage.contains("party_finder")
                        || normalizedMessage.contains("listing")
                        || normalizedMessage.contains("invite"))) {
            return true;
        }
        if (backendError == null) {
            return false;
        }
        if (backendError.context() != null) {
            String context = backendError.context().toLowerCase(Locale.ROOT);
            if (context.contains("party_finder") || context.contains("party finder")) {
                return true;
            }
        }
        if (backendError.requestType() != null) {
            String requestType = backendError.requestType().toLowerCase(Locale.ROOT);
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
