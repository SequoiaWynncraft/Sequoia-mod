package org.sequoia.seq.network;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.sequoia.seq.client.SeqClient;
import org.sequoia.seq.model.*;
import org.sequoia.seq.network.auth.MinecraftAuthChallengeResponse;
import org.sequoia.seq.network.auth.MinecraftAuthCompleteRequest;
import org.sequoia.seq.network.auth.MinecraftAuthCompleteResponse;
import org.sequoia.seq.utils.WynnClassCache;

/**
 * REST client for the Sequoia backend API.
 * All calls return CompletableFuture and run off the render thread.
 */
public class ApiClient {

    private static ApiClient instance;

    private final HttpClient httpClient;
    private final ExecutorService executor;
    private final Gson gson;
    private final String baseUrl;
    private final String authBaseUrl;

    public static ApiClient getInstance() {
        if (instance == null) {
            instance = new ApiClient();
        }
        return instance;
    }

    private ApiClient() {
        this.executor = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "seq-api-client");
            t.setDaemon(true);
            return t;
        });
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .executor(executor)
                .build();
        this.gson = new GsonBuilder()
                .registerTypeAdapter(Instant.class, (JsonDeserializer<Instant>)
                        (json, type, ctx) -> Instant.parse(json.getAsString()))
                .registerTypeAdapter(
                        Instant.class, (JsonSerializer<Instant>) (src, type, ctx) -> new JsonPrimitive(src.toString()))
                .create();
        this.baseUrl = BuildConfig.API_URL;
            this.authBaseUrl = resolveAuthBaseUrl(BuildConfig.API_URL);
    }

    // ── Party Finder: Activities ──

    public CompletableFuture<List<Activity>> getActivities() {
        return get("/party-finder/activities", new TypeToken<List<Activity>>() {}.getType());
    }

    // ── Party Finder: Listings ──

    public CompletableFuture<List<Listing>> getListings(Long activityId, PartyRegion region) {
        StringBuilder path = new StringBuilder("/party-finder/listings");
        String sep = "?";
        if (activityId != null) {
            path.append(sep).append("activityId=").append(activityId);
            sep = "&";
        }
        if (region != null) {
            path.append(sep).append("region=").append(region.name());
        }
        return get(path.toString(), new TypeToken<List<Listing>>() {}.getType());
    }

    public CompletableFuture<Listing> getListing(long id) {
        return get("/party-finder/listings/" + id, Listing.class);
    }

    public CompletableFuture<Listing> createListing(
            List<Long> activityIds, PartyMode mode, boolean strict, PartyRegion region, PartyRole role, String note) {
        if (activityIds == null || activityIds.isEmpty()) {
            throw new IllegalArgumentException("activityIds must not be empty");
        }
        JsonObject body = new JsonObject();
        JsonArray activityIdsJson = new JsonArray();
        for (Long activityId : activityIds) {
            if (activityId != null) {
                activityIdsJson.add(activityId);
            }
        }
        if (activityIdsJson.size() == 0) {
            throw new IllegalArgumentException("activityIds must contain at least one non-null value");
        }
        body.add("activityIds", activityIdsJson);
        if (activityIdsJson.size() > 0) {
            body.addProperty("activityId", activityIdsJson.get(0).getAsLong());
        }
        body.addProperty("mode", mode.name());
        body.addProperty("strict", strict);
        body.addProperty("region", region.name());
        body.addProperty("role", role.name());
        if (note != null && !note.isBlank()) body.addProperty("note", note);
        return post("/party-finder/listings", body, Listing.class);
    }

    public CompletableFuture<Listing> joinListing(long id, PartyRole role) {
        return joinListing(id, role, null);
    }

    public CompletableFuture<Listing> joinListing(long id, PartyRole role, String inviteToken) {
        JsonObject body = new JsonObject();
        body.addProperty("role", role.name());
        WynnClassType classType = WynnClassCache.resolveLocalClassType();
        if (classType != null) {
            body.addProperty("classType", classType.name());
        }

        String path = "/party-finder/listings/" + id + "/join";
        if (inviteToken != null && !inviteToken.isBlank()) {
            path += "?inviteToken=" + URLEncoder.encode(inviteToken, StandardCharsets.UTF_8);
        }

        return post(path, body, Listing.class);
    }

    public CompletableFuture<Void> createInvite(long listingId, UUID targetUUID) {
        JsonObject body = new JsonObject();
        body.addProperty("targetUUID", targetUUID.toString());

        return post("/party-finder/listings/" + listingId + "/invite", body, Void.class);
    }

    public CompletableFuture<Listing> revokeInvite(long listingId, String inviteToken) {
        String encodedToken = URLEncoder.encode(inviteToken, StandardCharsets.UTF_8);
        return deleteTyped("/party-finder/listings/" + listingId + "/invite/" + encodedToken, Listing.class);
    }

    public CompletableFuture<Listing> leaveListing(long id) {
        return post("/party-finder/listings/" + id + "/leave", null, Listing.class);
    }

    public CompletableFuture<Listing> closeListing(long id) {
        return post("/party-finder/listings/" + id + "/close", null, Listing.class);
    }

    public CompletableFuture<Listing> reopenListing(long id) {
        return post("/party-finder/listings/" + id + "/reopen", null, Listing.class);
    }

    public CompletableFuture<Listing> disbandListing(long id) {
        return deleteTyped("/party-finder/listings/" + id, Listing.class);
    }

    public CompletableFuture<Listing> kickMember(long listingId, UUID targetUUID) {
        return deleteTyped("/party-finder/listings/" + listingId + "/members/" + targetUUID, Listing.class);
    }

    public CompletableFuture<Listing> changeMyRole(PartyRole role) {
        JsonObject body = new JsonObject();
        body.addProperty("role", role.name());
        return patch("/party-finder/members/me/role", body, Listing.class);
    }

    public CompletableFuture<Listing> reassignRole(long listingId, UUID targetUUID, PartyRole role) {
        JsonObject body = new JsonObject();
        body.addProperty("role", role.name());
        return patch("/party-finder/listings/" + listingId + "/members/" + targetUUID + "/role", body, Listing.class);
    }

    public CompletableFuture<Listing> transferLeadership(long listingId, UUID targetUUID) {
        JsonObject body = new JsonObject();
        body.addProperty("targetUUID", targetUUID.toString());
        return post("/party-finder/listings/" + listingId + "/transfer", body, Listing.class);
    }

    public CompletableFuture<Listing> updateListing(
            long id, List<Long> activityIds, PartyMode mode, boolean strict, PartyRegion region, String note) {
        if (activityIds == null || activityIds.isEmpty()) {
            throw new IllegalArgumentException("activityIds must not be empty");
        }

        JsonObject body = new JsonObject();
        JsonArray activityIdsJson = new JsonArray();
        for (Long activityId : activityIds) {
            if (activityId != null) {
                activityIdsJson.add(activityId);
            }
        }
        if (activityIdsJson.size() == 0) {
            throw new IllegalArgumentException("activityIds must contain at least one non-null value");
        }

        body.add("activityIds", activityIdsJson);
        body.addProperty("mode", mode.name());
        body.addProperty("strict", strict);
        body.addProperty("region", region.name());
        if (note != null) {
            body.addProperty("note", note);
        }

        return post("/party-finder/listings/" + id + "/update", body, Listing.class);
    }

    public CompletableFuture<Listing> reserveSlots(long listingId, Integer count) {
        JsonObject body = new JsonObject();
        if (count != null) {
            body.addProperty("count", count);
        }

        return post("/party-finder/listings/" + listingId + "/reserve", body, Listing.class);
    }

    public CompletableFuture<Listing> unreserveSlots(long listingId, Integer count) {
        JsonObject body = new JsonObject();
        if (count != null) {
            body.addProperty("count", count);
        }

        return post("/party-finder/listings/" + listingId + "/unreserve", body, Listing.class);
    }

    // ── Minecraft Auth ──

    public CompletableFuture<MinecraftAuthChallengeResponse> requestMinecraftAuthChallenge() {
        return postWithFallback(
            authRequestBaseUrls(),
            "/auth/minecraft/challenge",
            null,
            MinecraftAuthChallengeResponse.class,
            false);
    }

    public CompletableFuture<MinecraftAuthCompleteResponse> completeMinecraftAuthentication(
            MinecraftAuthCompleteRequest requestBody) {
        JsonObject body = new JsonObject();
        body.addProperty("challenge_id", requestBody.challengeId());
        body.addProperty("username", requestBody.username());
        return postWithFallback(
            authRequestBaseUrls(),
            "/auth/minecraft/complete",
            body,
            MinecraftAuthCompleteResponse.class,
            false);
    }

    // ── HTTP helpers ──

    private <T> CompletableFuture<T> get(String path, java.lang.reflect.Type type) {
        HttpRequest request = newRequest(baseUrl, path, true).GET().build();
        return sendAsync(request, type);
    }

    private <T> CompletableFuture<T> post(String path, JsonObject body, java.lang.reflect.Type type) {
        return post(baseUrl, path, body, type, true);
    }

    private <T> CompletableFuture<T> post(
            String path, JsonObject body, java.lang.reflect.Type type, boolean includeAuthHeader) {
        return post(baseUrl, path, body, type, includeAuthHeader);
    }

    private <T> CompletableFuture<T> post(
            String resolvedBaseUrl,
            String path,
            JsonObject body,
            java.lang.reflect.Type type,
            boolean includeAuthHeader) {
        HttpRequest.Builder builder =
                newRequest(resolvedBaseUrl, path, includeAuthHeader).header("Content-Type", "application/json");
        if (body != null) {
            builder.POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body)));
        } else {
            builder.POST(HttpRequest.BodyPublishers.noBody());
        }
        return sendAsync(builder.build(), type);
    }

    private <T> CompletableFuture<T> postWithFallback(
            List<String> baseUrls,
            String path,
            JsonObject body,
            java.lang.reflect.Type type,
            boolean includeAuthHeader) {
        return postWithFallback(baseUrls, 0, path, body, type, includeAuthHeader);
    }

    @SuppressWarnings("unchecked")
    private <T> CompletableFuture<T> postWithFallback(
            List<String> baseUrls,
            int index,
            String path,
            JsonObject body,
            java.lang.reflect.Type type,
            boolean includeAuthHeader) {
        String resolvedBaseUrl = baseUrls.get(index);
        CompletableFuture<T> resultFuture = new CompletableFuture<>();
        post(resolvedBaseUrl, path, body, type, includeAuthHeader).whenComplete((result, throwable) -> {
            if (throwable == null) {
                resultFuture.complete((T) result);
                return;
            }

            Throwable cause = throwable;
            while (cause instanceof java.util.concurrent.CompletionException && cause.getCause() != null) {
                cause = cause.getCause();
            }

            boolean hasFallback = index + 1 < baseUrls.size();
            if (hasFallback && cause instanceof ApiException apiException && shouldRetryAuthAtAlternateBase(apiException)) {
                SeqClient.LOGGER.warn(
                        "[Api] Auth route {}{} returned status={} body={}; retrying alternate base URL",
                        resolvedBaseUrl,
                        path,
                        apiException.getStatusCode(),
                        apiException.getResponseBody());
                postWithFallback(baseUrls, index + 1, path, body, type, includeAuthHeader)
                        .whenComplete((fallbackResult, fallbackThrowable) -> {
                            if (fallbackThrowable != null) {
                                resultFuture.completeExceptionally(fallbackThrowable);
                                return;
                            }
                            resultFuture.complete((T) fallbackResult);
                        });
                return;
            }

            resultFuture.completeExceptionally(cause);
        });
        return resultFuture;
    }

    private <T> CompletableFuture<T> patch(String path, JsonObject body, java.lang.reflect.Type type) {
        HttpRequest request = newRequest(baseUrl, path, true)
                .header("Content-Type", "application/json")
                .method("PATCH", HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
                .build();
        return sendAsync(request, type);
    }

    private <T> CompletableFuture<T> deleteTyped(String path, java.lang.reflect.Type type) {
        HttpRequest request = newRequest(baseUrl, path, true).DELETE().build();
        return sendAsync(request, type);
    }

    private HttpRequest.Builder newRequest(String path) {
        return newRequest(baseUrl, path, true);
    }

    private HttpRequest.Builder newRequest(String path, boolean includeAuthHeader) {
        return newRequest(baseUrl, path, includeAuthHeader);
    }

    private HttpRequest.Builder newRequest(String resolvedBaseUrl, String path, boolean includeAuthHeader) {
        HttpRequest.Builder builder =
                HttpRequest.newBuilder().uri(URI.create(resolvedBaseUrl + path)).timeout(Duration.ofSeconds(15));
        String token = SeqClient.getConfigManager().getToken();
        if (includeAuthHeader && token != null && !token.isBlank()) {
            builder.header("Authorization", "Bearer " + token);
        }
        return builder;
    }

    static String resolveAuthBaseUrl(String apiBaseUrl) {
        if (apiBaseUrl == null || apiBaseUrl.isBlank()) {
            return apiBaseUrl;
        }

        String normalized = apiBaseUrl.endsWith("/")
                ? apiBaseUrl.substring(0, apiBaseUrl.length() - 1)
                : apiBaseUrl;
        if (normalized.endsWith("/api")) {
            return normalized.substring(0, normalized.length() - 4);
        }
        return normalized;
    }

    static boolean shouldRetryAuthAtAlternateBase(ApiException exception) {
        if (exception == null) {
            return false;
        }

        int status = exception.getStatusCode();
        String body = exception.getResponseBody();
        String normalized = body == null ? "" : body.toLowerCase(Locale.ROOT);

        if (status == 404 || status == 405) {
            return true;
        }

        return status == 401
                && (normalized.contains("missing bearer token")
                        || normalized.contains("token_invalid")
                        || normalized.contains("token expired")
                        || normalized.contains("token_expired"));
    }

    private List<String> authRequestBaseUrls() {
        List<String> baseUrls = new ArrayList<>();
        if (authBaseUrl != null && !authBaseUrl.isBlank()) {
            baseUrls.add(authBaseUrl);
        }
        if (baseUrl != null && !baseUrl.isBlank() && !baseUrl.equals(authBaseUrl)) {
            baseUrls.add(baseUrl);
        }
        return baseUrls;
    }

    private <T> CompletableFuture<T> sendAsync(HttpRequest request, java.lang.reflect.Type type) {
        return httpClient
                .sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(resp -> {
                    if (resp.statusCode() >= 400) {
                        throw new ApiException(resp.statusCode(), resp.body());
                    }
                    if (type == Void.class || resp.body().isBlank()) return gson.fromJson("null", type);
                    return gson.fromJson(resp.body(), type);
                });
    }

    // ── Exception ──

    public static class ApiException extends RuntimeException {

        private final int statusCode;
        private final String responseBody;

        public ApiException(int statusCode, String responseBody) {
            super("API error " + statusCode + ": " + responseBody);
            this.statusCode = statusCode;
            this.responseBody = responseBody;
        }

        public int getStatusCode() {
            return statusCode;
        }

        public String getResponseBody() {
            return responseBody;
        }
    }
}
