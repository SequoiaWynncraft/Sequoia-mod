package com.seqwawa.seq.network.auth;

import com.seqwawa.seq.accessors.NotificationAccessor;
import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.network.ApiClient;
import com.seqwawa.seq.network.BuildConfig;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * A sign-in to the backend that serves raid profiles, kept apart from the main one.
 * <p>
 * Raid profiles can live on a different backend than the rest of the mod (see
 * {@code raid_profiles_environment} in build.gradle), and each backend signs its
 * tokens with its own secret: a production token is refused by staging as invalid.
 * So when the two differ, this runs the same Minecraft challenge flow a second time
 * against the raid-profiles backend and keeps that token here.
 * <p>
 * The token stays in memory only. It never replaces the main session in the config
 * file, so chat, the party finder and every other feature keep using production.
 */
public final class RaidProfilesSession {

    private static final Duration REFRESH_SKEW = Duration.ofSeconds(30);

    /**
     * How long a failed sign-in is remembered before trying again. The panel refreshes
     * on open, on Refresh and after a save, and each attempt is a full challenge plus a
     * Mojang session join, so an unlinked account should not repeat that every click.
     */
    private static final Duration FAILURE_COOLDOWN = Duration.ofSeconds(20);

    private static RaidProfilesSession instance;

    private final String apiBaseUrl;

    private volatile StoredAuthSession session;
    private volatile CompletableFuture<String> inFlight;
    private volatile AuthException lastFailure;
    private volatile Instant lastFailureAt = Instant.EPOCH;
    private volatile boolean linkHintShown;

    RaidProfilesSession(String apiBaseUrl) {
        this.apiBaseUrl = apiBaseUrl;
    }

    public static synchronized RaidProfilesSession getInstance() {
        if (instance == null) {
            instance = new RaidProfilesSession(BuildConfig.RAID_PROFILES_API_URL);
        }
        return instance;
    }

    /**
     * A valid token for the raid-profiles backend, signing in when there is none or it
     * is about to expire. {@code force} discards the current one, which is what a 401
     * from that backend calls for.
     */
    public synchronized CompletableFuture<String> ensureToken(boolean force) {
        Instant now = Instant.now();
        StoredAuthSession current = session;
        if (!force && current != null && !current.expiresWithin(REFRESH_SKEW, now)) {
            return CompletableFuture.completedFuture(current.token());
        }
        if (!force && lastFailure != null && now.isBefore(lastFailureAt.plus(FAILURE_COOLDOWN))) {
            return CompletableFuture.failedFuture(lastFailure);
        }
        if (inFlight != null && !inFlight.isDone()) {
            return inFlight;
        }
        if (force) {
            session = null;
        }

        CompletableFuture<String> attempt = MinecraftAuthService.getInstance()
                .authenticateAgainst(apiBaseUrl)
                .thenApply(signedIn -> {
                    session = signedIn;
                    lastFailure = null;
                    SeqClient.LOGGER.info(
                            "[RaidProfiles] Signed in to {} as '{}' until {}",
                            ApiClient.resolveAuthBaseUrl(apiBaseUrl),
                            signedIn.minecraftUsername(),
                            signedIn.expiresAt());
                    return signedIn.token();
                })
                .whenComplete((token, throwable) -> {
                    if (throwable != null) {
                        onFailure(throwable);
                    }
                });
        inFlight = attempt;
        return attempt;
    }

    private void onFailure(Throwable throwable) {
        Throwable cause = throwable;
        while (cause instanceof CompletionException && cause.getCause() != null) {
            cause = cause.getCause();
        }
        AuthException failure = cause instanceof AuthException authException
                ? authException
                : new AuthException(AuthErrorCode.NETWORK_FAILURE, String.valueOf(cause.getMessage()), true, cause);
        lastFailure = failure;
        lastFailureAt = Instant.now();
        SeqClient.LOGGER.warn(
                "[RaidProfiles] Sign-in to {} failed code={} message={}",
                ApiClient.resolveAuthBaseUrl(apiBaseUrl),
                failure.getStableCode(),
                failure.getMessage());

        if (failure.getCode() == AuthErrorCode.ACCOUNT_NOT_LINKED && !linkHintShown) {
            // The backend's own text names the Discord bot, which links the production
            // database, not this one. The website flow links whichever backend serves it.
            linkHintShown = true;
            new NotificationAccessor() {}.notifyClickable(
                    "Raid profiles use " + hostOf(apiBaseUrl)
                            + ", where your account is not linked yet. Click to link it there, then press Refresh.",
                    linkUrlFor(apiBaseUrl));
        }
    }

    /** The last sign-in failure, or null. */
    public AuthException lastFailure() {
        return lastFailure;
    }

    public void clear() {
        session = null;
        lastFailure = null;
        lastFailureAt = Instant.EPOCH;
    }

    /**
     * The website sign-in that links a Discord and Wynncraft account on the backend at
     * {@code apiBaseUrl}. It lands on the same link call the /link command makes, and
     * the return address has to be on that backend's own host to be accepted.
     */
    public static String linkUrlFor(String apiBaseUrl) {
        String site = ApiClient.resolveAuthBaseUrl(apiBaseUrl);
        return site + "/auth/web/start?return_to=" + site + "/";
    }

    public static String hostOf(String apiBaseUrl) {
        String site = ApiClient.resolveAuthBaseUrl(apiBaseUrl);
        int scheme = site.indexOf("://");
        return scheme < 0 ? site : site.substring(scheme + 3);
    }
}
