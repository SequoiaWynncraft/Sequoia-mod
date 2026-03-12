package org.sequoia.seq.network;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import lombok.Getter;
import net.minecraft.client.Minecraft;
import org.sequoia.seq.accessors.NotificationAccessor;
import org.sequoia.seq.client.SeqClient;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.sequoia.seq.model.WynnClassType;
import org.sequoia.seq.network.auth.AuthErrorCode;
import org.sequoia.seq.network.auth.AuthException;
import org.sequoia.seq.utils.WynnClassCache;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

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

    // Reconnect state
    private static boolean autoReconnect = true;
    private static int reconnectAttempt = 0;
    private static ScheduledFuture<?> reconnectTask;

    private enum AuthFlow {
        CONNECT,
        LINK
    }

    // Callbacks for new message types
    private static Consumer<DiscordChatMessage> discordChatHandler;
    private static Consumer<PartyFinderUpdateMessage> partyFinderUpdateHandler;
    private static Consumer<PartyFinderInviteMessage> partyFinderInviteHandler;
    private static Consumer<PartyFinderStaleWarningMessage> partyFinderStaleWarningHandler;
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
        pendingAuthFlow = authFlow;
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
                notifyConnectionStatus("Refreshing backend authentication...");
                refreshAuthentication();
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
        SeqClient.LOGGER.info(
                "[WebSocket] disconnect() called open={} authenticated={} autoReconnect={}",
                isOpen(),
                authenticated,
                autoReconnect);
        finishConnectFlow();
        autoReconnect = false;
        cancelReconnect();
        connectInProgress = false;
        if (!isOpen()) {
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
        authenticated = true;
        authFailed = false;
        notInGuild = false;
        connectedSince = Instant.now();
        autoReconnect = true;
        authAttempt = 0;
        nextAllowedAuthAttemptAtMs = 0;

        String username = SeqClient.getConfigManager().getMinecraftUsername();
        if (pendingAuthFlow == AuthFlow.LINK) {
            notifyConnectionStatus(
                    username != null && !username.isBlank()
                            ? "Authenticated Minecraft account: " + username
                            : "Authenticated Minecraft account.");
        } else {
            notifyConnectionStatus(
                    username != null && !username.isBlank()
                            ? "Connected as " + username
                            : "Connected to " + BuildConfig.ENVIRONMENT + ".");
        }
        finishConnectFlow();
        sendLocalPartyClassUpdate();
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
        notifyConnectionFailure("Connection error: " + (ex != null ? ex.getMessage() : "unknown"), false);
        finishConnectFlow();
    }

    // ── Auto-reconnect ──

    private static void scheduleReconnect() {
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
    }

    private static boolean shouldReconnectAfterClose(int code, boolean remote) {
        if (remote) {
            return true;
        }
        // Local clean close (1000) is treated as intentional; do not auto-reconnect.
        // Handshake/protocol close failures (e.g. 1002 from HTTP 502) should retry.
        return code != 1000;
    }

    private static void notifyManualConnectRequired() {
        Minecraft.getInstance().execute(() -> {
            var player = Minecraft.getInstance().player;
            if (player != null) {
                player.displayClientMessage(
                    java.util.Objects.requireNonNull(NotificationAccessor.prefixed(
                        "Could not reconnect automatically. Run /seq connect manually (or /seq link if needed).")),
                        false);
            }
        });
    }

    // ── Outgoing messages ──

    private void send(String type, JsonObject payload) {
        if (isPrivilegedType(type) && !canSendPrivileged(type)) {
            return;
        }
        if (payload == null) payload = new JsonObject();
        payload.addProperty("type", type);
        SeqClient.LOGGER.debug("[WebSocket] send type={} payload={}", type, truncate(payload.toString(), 512));
        send(GSON.toJson(payload));
    }

    private void prepareAuthenticatedConnection(boolean forceRefresh) {
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
                        finishConnectFlow();
                }));
    }

    private void configureHandshakeAuthorization(String token) {
        Map<String, String> headers = buildHandshakeHeaders(token);
        clearHeaders();
        headers.forEach(this::addHeader);
    }

    static Map<String, String> buildHandshakeHeaders(String token) {
        Map<String, String> headers = new LinkedHashMap<>();
        if (token != null && !token.isBlank()) {
            headers.put("Authorization", buildAuthorizationHeaderValue(token));
        }
        return headers;
    }

    static String buildAuthorizationHeaderValue(String token) {
        return "Bearer " + token.trim();
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
        if (!isOpen() || !authenticated || authFailed || notInGuild) {
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
            SeqClient.LOGGER.warn("[WebSocket] sendRaidAnnouncement dropped: invalid payload");
            return;
        }
        SeqClient.LOGGER.info(
                "[WebSocket] Sending guild_raid_announcement type={} usernames={}", raidType, usernames.size());
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
                        notifyConnectionStatus("Connected as " + discordUser);
                    }
                    sendLocalPartyClassUpdate();
                }
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
                    if (partyFinderUpdateHandler != null) {
                        String action = json.get("action").getAsString();
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
                        long listingId = json.get("listing_id").getAsLong();
                        Instant disbandAt = Instant.parse(json.get("disband_at").getAsString());
                        long minutesRemaining = json.get("minutes_remaining").getAsLong();

                        SeqClient.LOGGER.info(
                                "[WebSocket] Dispatching party_finder_stale_warning listingId={} disbandAt={} minutesRemaining={}",
                                listingId,
                                disbandAt,
                                minutesRemaining);

                        partyFinderStaleWarningHandler.accept(
                                new PartyFinderStaleWarningMessage(listingId, disbandAt, minutesRemaining));
                    } else {
                        SeqClient.LOGGER.warn(
                                "[WebSocket] Received party_finder_stale_warning but handler is not registered");
                    }
                }
                case "error" -> {
                    String error = json.has("message") && json.get("message").isJsonPrimitive()
                            ? json.get("message").getAsString()
                            : "Unknown backend error";
                    int status = extractStatusCode(json);
                    String normalized = error.toLowerCase(Locale.ROOT);
                    SeqClient.LOGGER.warn("[WebSocket] Backend error status={} message={}", status, error);

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

                    if (normalized.contains("validation")) {
                        notify("Request rejected by backend validation. Please check your input.");
                        return;
                    }

                    if (isPartyFinderError(json, normalized)) {
                        SeqClient.getPartyFinderManager().pushUiError(error);
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
                || "party_class_update".equals(type)
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

    // ── Message records ──

    public record DiscordChatMessage(String username, String message) {}

    public record PartyFinderUpdateMessage(String action, JsonObject listingJson) {}

    public record PartyFinderInviteMessage(
            long listingId, String inviterUUID, String inviteToken, JsonObject listingJson) {}

    public record PartyFinderStaleWarningMessage(long listingId, Instant disbandAt, long minutesRemaining) {}
}
