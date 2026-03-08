package org.sequoia.seq.network.auth;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.mojang.authlib.exceptions.AuthenticationException;
import com.mojang.authlib.minecraft.MinecraftSessionService;
import lombok.Getter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.server.Services;
import org.sequoia.seq.client.SeqClient;
import org.sequoia.seq.network.ApiClient;
import org.sequoia.seq.network.BuildConfig;

import java.lang.reflect.Method;
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
                    .thenCompose(challenge -> performMinecraftSessionJoin(challenge)
                            .thenCompose(username -> completeAuthentication(challenge.challengeId(), username)))
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

    public CompletableFuture<MinecraftAuthCompleteResponse> completeAuthentication(
            String challengeId, String username) {
        setState(AuthState.COMPLETING_BACKEND_AUTH, null);
        return ApiClient.getInstance()
                .completeMinecraftAuthentication(new MinecraftAuthCompleteRequest(challengeId, username));
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

    static MinecraftAuthChallengeResponse validateChallenge(MinecraftAuthChallengeResponse response) {
        if (response == null
                || response.challengeId() == null
                || response.challengeId().isBlank()
                || response.serverId() == null
                || response.serverId().isBlank()
                || response.expiresAt() == null) {
            throw new AuthException(
                    AuthErrorCode.MALFORMED_RESPONSE, "Backend returned a malformed Minecraft auth challenge.");
        }
        if (!response.expiresAt().isAfter(Instant.now())) {
            throw new AuthException(
                    AuthErrorCode.CHALLENGE_EXPIRED, "Backend returned an already expired Minecraft auth challenge.");
        }
        return response;
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

    private CompletableFuture<String> performMinecraftSessionJoin(MinecraftAuthChallengeResponse challenge) {
        return CompletableFuture.supplyAsync(
                () -> {
                    setState(AuthState.JOINING_MINECRAFT_SESSION, null);

                    User user = Minecraft.getInstance().getUser();
                    String username = user.getName();
                    UUID profileId = user.getProfileId();
                    String accessToken = user.getAccessToken();
                    if (username == null
                            || username.isBlank()
                            || profileId == null
                            || accessToken == null
                            || accessToken.isBlank()) {
                        throw new AuthException(
                                AuthErrorCode.MINECRAFT_SESSION_INVALID,
                                "Minecraft session is missing profile or access-token data.");
                    }

                    try {
                        resolveSessionService(Minecraft.getInstance())
                                .joinServer(profileId, accessToken, challenge.serverId());
                        SeqClient.LOGGER.info(
                                "[Auth] Minecraft session join succeeded username='{}' challengeId={} expiresAt={}",
                                username,
                                challenge.challengeId(),
                                challenge.expiresAt());
                        return username;
                    } catch (AuthenticationException exception) {
                        throw new AuthException(
                                AuthErrorCode.SESSION_JOIN_FAILED,
                                "Minecraft session join failed. Re-log in to Minecraft and try again.",
                                false,
                                exception);
                    } catch (AuthException exception) {
                        throw exception;
                    } catch (Exception exception) {
                        throw new AuthException(
                                AuthErrorCode.MINECRAFT_SESSION_INVALID,
                                "Unable to access Minecraft session service.",
                                false,
                                exception);
                    }
                },
                executor);
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
        if (cause instanceof AuthenticationException authenticationException) {
            return new AuthException(
                    AuthErrorCode.SESSION_JOIN_FAILED,
                    "Minecraft session join failed. Re-log in to Minecraft and try again.",
                    false,
                    authenticationException);
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
        if (status == 429 || normalized.contains("rate")) {
            return AuthErrorCode.RATE_LIMITED;
        }
        if (status == 503 || status == 502 || status == 504) {
            return AuthErrorCode.BACKEND_UNAVAILABLE;
        }
        if (status == 401 && normalized.contains("session")) {
            return AuthErrorCode.MINECRAFT_SESSION_INVALID;
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
            case CHALLENGE_EXPIRED -> "Minecraft auth challenge expired before it could be completed.";
            case CHALLENGE_USED -> "Minecraft auth challenge was already used.";
            case MINECRAFT_SESSION_INVALID -> "Minecraft session is invalid. Re-log in and try again.";
            case TOKEN_INVALID -> "Stored backend token is invalid. Re-authentication is required.";
            case TOKEN_EXPIRED -> "Stored backend token expired. Re-authentication is required.";
            case RATE_LIMITED -> "Authentication was rate limited. Please wait and try again.";
            case TRANSPORT_INSECURE -> "Refusing authentication over insecure transport.";
            case BACKEND_UNAVAILABLE -> "Backend is unavailable right now.";
            case BACKEND_VERIFICATION_FAILED -> "Backend could not verify the Minecraft session.";
            case WEBSOCKET_AUTH_REJECTED -> "Websocket authentication was rejected by the backend.";
            case PLAYER_NOT_LOGGED_IN -> "Minecraft account is not logged in.";
            case SESSION_JOIN_FAILED -> "Minecraft session join failed. Re-log in and try again.";
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

    private static MinecraftSessionService resolveSessionService(Minecraft minecraft) {
        if (minecraft == null) {
            throw new AuthException(AuthErrorCode.MINECRAFT_SESSION_INVALID, "Minecraft client is not available.");
        }

        try {
            Services services = minecraft.services();
            if (services != null && services.sessionService() != null) {
                return services.sessionService();
            }
        } catch (Throwable ignored) {
        }

        for (String methodName : new String[] {"getMinecraftSessionService", "sessionService"}) {
            try {
                Method method = minecraft.getClass().getMethod(methodName);
                if (MinecraftSessionService.class.isAssignableFrom(method.getReturnType())) {
                    return (MinecraftSessionService) method.invoke(minecraft);
                }
            } catch (ReflectiveOperationException ignored) {
            }
        }

        for (String methodName : new String[] {"getApiServices", "apiServices"}) {
            try {
                Method apiServicesMethod = minecraft.getClass().getMethod(methodName);
                Object apiServices = apiServicesMethod.invoke(minecraft);
                if (apiServices == null) {
                    continue;
                }
                for (String sessionMethodName : new String[] {"sessionService", "getSessionService", "getMinecraftSessionService"}) {
                    try {
                        Method sessionServiceMethod = apiServices.getClass().getMethod(sessionMethodName);
                        if (MinecraftSessionService.class.isAssignableFrom(sessionServiceMethod.getReturnType())) {
                            return (MinecraftSessionService) sessionServiceMethod.invoke(apiServices);
                        }
                    } catch (ReflectiveOperationException ignored) {
                    }
                }
            } catch (ReflectiveOperationException ignored) {
            }
        }

        for (Method method : minecraft.getClass().getMethods()) {
            if (method.getParameterCount() == 0
                    && MinecraftSessionService.class.isAssignableFrom(method.getReturnType())) {
                try {
                    return (MinecraftSessionService) method.invoke(minecraft);
                } catch (ReflectiveOperationException ignored) {
                }
            }
        }

        throw new AuthException(
                AuthErrorCode.MINECRAFT_SESSION_INVALID,
                "Unable to resolve Minecraft session service from the client.");
    }
}
