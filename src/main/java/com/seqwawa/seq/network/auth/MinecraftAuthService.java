package com.seqwawa.seq.network.auth;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.mojang.authlib.exceptions.AuthenticationException;
import com.mojang.authlib.minecraft.MinecraftSessionService;
import lombok.Getter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.server.Services;
import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.network.ApiClient;
import com.seqwawa.seq.network.BuildConfig;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MinecraftAuthService {

    private static final Duration TOKEN_REFRESH_SKEW = Duration.ofSeconds(30);
    private static final Gson GSON = new Gson();

    private static MinecraftAuthService instance;

    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "seq-minecraft-auth");
        t.setDaemon(true);
        return t;
    });

    @Getter
    private volatile AuthState state = AuthState.IDLE;

    @Getter
    private volatile AuthException lastError;

    private volatile CompletableFuture<StoredAuthSession> inFlightAuthentication;

    public static synchronized MinecraftAuthService getInstance() {
        if (instance == null) {
            instance = new MinecraftAuthService();
        }
        return instance;
    }

    public CompletableFuture<String> ensureValidToken(boolean forceRefresh) {
        StoredAuthSession current = getCurrentSession();
        if (!forceRefresh && current != null && !current.expiresWithin(TOKEN_REFRESH_SKEW, Instant.now())) {
            return CompletableFuture.completedFuture(current.token());
        }
        return beginAuthentication().thenApply(StoredAuthSession::token);
    }

    public CompletableFuture<StoredAuthSession> beginAuthentication() {
        try {
            ensureSecureTransport();
        } catch (AuthException exception) {
            setState(AuthState.FAILED, exception);
            return CompletableFuture.failedFuture(exception);
        }

        synchronized (this) {
            if (inFlightAuthentication != null && !inFlightAuthentication.isDone()) {
                return inFlightAuthentication;
            }

            setState(AuthState.REQUESTING_CHALLENGE, null);
            CompletableFuture<StoredAuthSession> future = ApiClient.getInstance()
                    .requestMinecraftAuthChallenge()
                    .thenApply(MinecraftAuthService::validateChallenge)
                    .thenCompose(this::authenticateMinecraftSession)
                    .thenApply(this::storeSession)
                    .handle((session, throwable) -> {
                        if (throwable != null) {
                            throw new CompletionException(mapException(throwable));
                        }
                        return session;
                    })
                    .whenComplete((session, throwable) -> {
                        synchronized (MinecraftAuthService.this) {
                            inFlightAuthentication = null;
                        }

                        if (throwable != null) {
                            AuthException authException = mapException(throwable);
                            setState(AuthState.FAILED, authException);
                            SeqClient.LOGGER.warn(
                                    "[Auth] Minecraft authentication failed code={} message={}",
                                    authException.getStableCode(),
                                    authException.getMessage(),
                                    authException);
                            return;
                        }

                        setState(AuthState.AUTHENTICATED, null);
                        SeqClient.LOGGER.info(
                                "[Auth] Authenticated Minecraft account username='{}' expiresAt={}",
                                session.minecraftUsername(),
                                session.expiresAt());
                    });
            inFlightAuthentication = future;
            return future;
        }
    }

    static MinecraftAuthChallengeResponse validateChallenge(MinecraftAuthChallengeResponse response) {
        if (response == null
                || response.challengeId() == null
                || response.challengeId().isBlank()
                || response.serverId() == null
                || !response.serverId().matches("[0-9a-f]{40}")
                || response.expiresAt() == null) {
            throw new AuthException(
                    AuthErrorCode.MALFORMED_RESPONSE,
                    "Backend returned a malformed Minecraft auth challenge.");
        }
        if (!response.expiresAt().isAfter(Instant.now())) {
            throw new AuthException(
                    AuthErrorCode.CHALLENGE_EXPIRED, "Backend returned an already expired Minecraft auth challenge.");
        }
        return response;
    }

    private CompletableFuture<MinecraftAuthCompleteResponse> authenticateMinecraftSession(
            MinecraftAuthChallengeResponse challenge) {
        return CompletableFuture
                .supplyAsync(
                        () -> {
                            setState(AuthState.JOINING_MINECRAFT_SESSION, null);
                            User user = requireLoggedInUser();
                            try {
                                resolveSessionService().joinServer(
                                        user.getProfileId(), user.getAccessToken(), challenge.serverId());
                                return user.getName();
                            } catch (AuthenticationException exception) {
                                throw new AuthException(
                                        AuthErrorCode.SESSION_JOIN_FAILED,
                                        "Minecraft session verification failed. Restart Minecraft and try again.",
                                        true,
                                        exception);
                            }
                        },
                        executor)
                .thenCompose(username -> completeAuthentication(challenge.challengeId(), username));
    }

    private CompletableFuture<MinecraftAuthCompleteResponse> completeAuthentication(String challengeId, String username) {
        setState(AuthState.COMPLETING_BACKEND_AUTH, null);
        return ApiClient.getInstance()
                .completeMinecraftAuthentication(new MinecraftAuthCompleteRequest(challengeId, username));
    }

    private User requireLoggedInUser() {
        Minecraft minecraft = resolveMinecraftClient();
        if (minecraft == null || minecraft.getUser() == null) {
            throw new AuthException(AuthErrorCode.PLAYER_NOT_LOGGED_IN, "Minecraft account is not logged in.");
        }
        User user = minecraft.getUser();
        if (user.getProfileId() == null
                || user.getName() == null
                || user.getName().isBlank()
                || user.getAccessToken() == null
                || user.getAccessToken().isBlank()) {
            throw new AuthException(
                    AuthErrorCode.PLAYER_NOT_LOGGED_IN,
                    "Minecraft account is missing launcher session credentials.");
        }
        return user;
    }

    private MinecraftSessionService resolveSessionService() {
        Minecraft minecraft = resolveMinecraftClient();
        Services services = minecraft == null ? null : minecraft.services();
        MinecraftSessionService sessionService = services == null ? null : services.sessionService();
        if (sessionService == null) {
            throw new AuthException(
                    AuthErrorCode.MINECRAFT_SESSION_INVALID,
                    "Minecraft session service is unavailable. Restart Minecraft and try again.",
                    true);
        }
        return sessionService;
    }

    private Minecraft resolveMinecraftClient() {
        return SeqClient.mc != null ? SeqClient.mc : Minecraft.getInstance();
    }

    public String getCurrentToken() {
        StoredAuthSession session = getCurrentSession();
        if (session == null || session.isExpired(Instant.now())) {
            return null;
        }
        return session.token();
    }

    public StoredAuthSession getCurrentSession() {
        return SeqClient.getConfigManager().getStoredAuthSession();
    }

    public boolean isTokenExpired() {
        StoredAuthSession session = getCurrentSession();
        return session == null || session.isExpired(Instant.now());
    }

    public void clearSession() {
        SeqClient.getConfigManager().clearAuthSession();
        setState(AuthState.IDLE, null);
    }

    public void invalidateSession(AuthErrorCode code, String message) {
        SeqClient.getConfigManager().clearAuthSession();
        setState(AuthState.FAILED, new AuthException(code, message, true));
    }

    public void clearSessionIfNotActiveProfile(UUID activeProfileId) {
        StoredAuthSession session = getCurrentSession();
        if (activeProfileId == null || session == null || session.minecraftUuid() == null) {
            return;
        }
        try {
            if (!UUID.fromString(session.minecraftUuid()).equals(activeProfileId)) {
                clearSession();
            }
        } catch (IllegalArgumentException exception) {
            clearSession();
        }
    }

    static StoredAuthSession toStoredSession(MinecraftAuthCompleteResponse response) {
        if (response == null
                || response.token() == null
                || response.token().isBlank()
                || response.expiresAt() == null
                || response.user() == null
                || response.user().minecraftUuid() == null
                || response.user().minecraftUuid().isBlank()
                || response.user().minecraftUsername() == null
                || response.user().minecraftUsername().isBlank()) {
            throw new AuthException(
                    AuthErrorCode.MALFORMED_RESPONSE,
                    "Backend returned a malformed Minecraft auth completion response.");
        }

        if (!response.expiresAt().isAfter(Instant.now())) {
            throw new AuthException(AuthErrorCode.TOKEN_EXPIRED, "Backend returned an already expired backend token.");
        }

        try {
            UUID.fromString(response.user().minecraftUuid());
        } catch (Exception exception) {
            throw new AuthException(
                    AuthErrorCode.MALFORMED_RESPONSE,
                    "Backend returned an invalid authoritative Minecraft UUID.",
                    false,
                    exception);
        }

        return new StoredAuthSession(
                response.token(),
                response.expiresAt(),
                response.user().minecraftUuid(),
                response.user().minecraftUsername());
    }

    private MinecraftAuthCompleteResponse unwrapCompleteResponse(MinecraftAuthCompleteResponse response) {
        if (response == null) {
            throw new AuthException(
                    AuthErrorCode.MALFORMED_RESPONSE, "Backend did not return a Minecraft auth completion response.");
        }
        return response;
    }

    private StoredAuthSession storeSession(MinecraftAuthCompleteResponse response) {
        StoredAuthSession session = toStoredSession(unwrapCompleteResponse(response));
        SeqClient.getConfigManager().setAuthSession(session);
        return session;
    }

    private void ensureSecureTransport() {
        if (!BuildConfig.API_URL.startsWith("https://") || !BuildConfig.WS_URL.startsWith("wss://")) {
            throw new AuthException(
                    AuthErrorCode.TRANSPORT_INSECURE, "Refusing Minecraft authentication over insecure transport.");
        }
    }

    private void setState(AuthState state, AuthException error) {
        this.state = state;
        this.lastError = error;
    }

    private AuthException mapException(Throwable throwable) {
        Throwable cause = throwable;
        while (cause instanceof CompletionException && cause.getCause() != null) {
            cause = cause.getCause();
        }
        if (cause instanceof AuthException authException) {
            return authException;
        }
        if (cause instanceof ApiClient.ApiException apiException) {
            return mapApiException(apiException);
        }
        return new AuthException(
                AuthErrorCode.NETWORK_FAILURE,
                cause != null && cause.getMessage() != null ? cause.getMessage() : "Authentication request failed.",
                true,
                cause);
    }

    private AuthException mapApiException(ApiClient.ApiException exception) {
        JsonObject json = parseJsonSafely(exception.getResponseBody());
        String backendCode = readString(json, "code");
        if (backendCode == null) {
            backendCode = readString(json, "error_code");
        }
        String message = readString(json, "message");
        int status = exception.getStatusCode();
        AuthErrorCode code = mapApiErrorCode(status, backendCode, message);
        String resolvedMessage = message;
        if (resolvedMessage == null || resolvedMessage.isBlank()) {
            resolvedMessage = defaultMessageFor(code, status);
        }
        boolean retryable = code == AuthErrorCode.RATE_LIMITED
                || code == AuthErrorCode.BACKEND_UNAVAILABLE
                || code == AuthErrorCode.NETWORK_FAILURE;
        return new AuthException(code, resolvedMessage, retryable, exception);
    }

    private static AuthErrorCode mapApiErrorCode(int status, String backendCode, String message) {
        AuthErrorCode parsed = AuthErrorCode.fromCode(backendCode);
        if (parsed != AuthErrorCode.UNKNOWN) {
            return parsed;
        }

        String normalized = message == null ? "" : message.toLowerCase(Locale.ROOT);
        if ("not_linked".equalsIgnoreCase(backendCode)) {
            return AuthErrorCode.ACCOUNT_NOT_LINKED;
        }
        if ("minecraft_session_invalid".equalsIgnoreCase(backendCode)) {
            return AuthErrorCode.MINECRAFT_SESSION_INVALID;
        }
        if (status == 429 || normalized.contains("rate")) {
            return AuthErrorCode.RATE_LIMITED;
        }
        if (status == 503 || status == 502 || status == 504) {
            return AuthErrorCode.BACKEND_UNAVAILABLE;
        }
        if (status == 401 && normalized.contains("token")) {
            return AuthErrorCode.TOKEN_INVALID;
        }
        if (status == 401 || status == 403) {
            return AuthErrorCode.BACKEND_VERIFICATION_FAILED;
        }
        if (status == 410 || normalized.contains("expired")) {
            return normalized.contains("challenge") ? AuthErrorCode.CHALLENGE_EXPIRED : AuthErrorCode.TOKEN_EXPIRED;
        }
        if (normalized.contains("challenge") && normalized.contains("used")) {
            return AuthErrorCode.CHALLENGE_USED;
        }
        return AuthErrorCode.UNKNOWN;
    }

    private static String defaultMessageFor(AuthErrorCode code, int status) {
        return switch (code) {
            case ACCOUNT_NOT_LINKED -> "No linked Sequoia account was found. Use /link with the Sierra bot in Discord, then reconnect.";
            case CHALLENGE_EXPIRED -> "Minecraft authentication challenge expired before it could be completed.";
            case CHALLENGE_USED -> "Minecraft authentication challenge was already used.";
            case MINECRAFT_SESSION_INVALID -> "Minecraft session verification failed. Restart Minecraft and try again.";
            case SESSION_JOIN_FAILED -> "Minecraft session join failed. Restart Minecraft and try again.";
            case TOKEN_INVALID -> "Stored backend token is invalid. Re-authentication is required.";
            case TOKEN_EXPIRED -> "Stored backend token expired. Re-authentication is required.";
            case RATE_LIMITED -> "Authentication was rate limited. Please wait and try again.";
            case TRANSPORT_INSECURE -> "Refusing authentication over insecure transport.";
            case BACKEND_UNAVAILABLE -> "Backend is unavailable right now.";
            case BACKEND_VERIFICATION_FAILED -> "Backend could not complete Minecraft authentication.";
            case WEBSOCKET_AUTH_REJECTED -> "Websocket authentication was rejected by the backend.";
            case PLAYER_NOT_LOGGED_IN -> "Minecraft account is not logged in.";
            case NETWORK_FAILURE -> "Network request failed during authentication.";
            case MALFORMED_RESPONSE -> "Backend returned an invalid authentication response.";
            case UNKNOWN -> "Authentication failed with backend status " + status + ".";
        };
    }

    private static JsonObject parseJsonSafely(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            return GSON.fromJson(body, JsonObject.class);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String readString(JsonObject json, String key) {
        if (json == null || key == null || !json.has(key) || !json.get(key).isJsonPrimitive()) {
            return null;
        }
        try {
            return json.get(key).getAsString();
        } catch (Exception ignored) {
            return null;
        }
    }

}
