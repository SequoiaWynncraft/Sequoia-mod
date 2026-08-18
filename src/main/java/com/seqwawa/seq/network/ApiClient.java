package com.seqwawa.seq.network;

import com.google.gson.*;
import com.google.gson.annotations.SerializedName;
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
import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.model.Activity;
import com.seqwawa.seq.model.CreateInviteResponse;
import com.seqwawa.seq.model.Listing;
import com.seqwawa.seq.model.PartyJoinPolicy;
import com.seqwawa.seq.model.PartyRegion;
import com.seqwawa.seq.model.PartyRole;
import com.seqwawa.seq.model.RankProfilesResponse;
import com.seqwawa.seq.model.WynnClassType;
import com.seqwawa.seq.model.war.WarCompositionRole;
import com.seqwawa.seq.model.war.WarPlannerDrafts.TeamDraft;
import com.seqwawa.seq.model.war.WarPlannerDrafts.TeamMemberDraft;
import com.seqwawa.seq.model.war.WarPlannerDrafts.SupportDraft;
import com.seqwawa.seq.model.war.WarPlannerDrafts.ZoneDraft;
import com.seqwawa.seq.model.war.WarPlannerSnapshot;
import com.seqwawa.seq.network.auth.MinecraftAuthChallengeResponse;
import com.seqwawa.seq.network.auth.MinecraftAuthCompleteRequest;
import com.seqwawa.seq.network.auth.MinecraftAuthCompleteResponse;
import com.seqwawa.seq.utils.WynnClassCache;

/**
 * REST client for the Sequoia backend API.
 * All calls return CompletableFuture and run off the render thread.
 */
public class ApiClient {

    private static final String MAIN_SERVER_ONLY_ERROR =
            "{\"error\":\"main_server_only\",\"message\":\""
                    + WynncraftServerPolicy.MAIN_SERVER_ONLY_MESSAGE
                    + "\"}";
    private static final String DEFAULT_ASPECT_REQUEST_REASON = "No reason provided.";

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

    public CompletableFuture<Listing> createListing(
            List<Long> activityIds,
            PartyRegion region,
            PartyRole role,
            String note,
            String world,
            PartyJoinPolicy joinPolicy,
            int reservedSlots) {
        JsonObject body = buildCreateListingPayload(
                activityIds, region, role, note, world, joinPolicy, reservedSlots);
        return post("/party-finder/listings", body, Listing.class);
    }

    static JsonObject buildCreateListingPayload(
            List<Long> activityIds,
            PartyRegion region,
            PartyRole role,
            String note,
            String world,
            PartyJoinPolicy joinPolicy,
            int reservedSlots) {
        if (activityIds == null || activityIds.isEmpty()) {
            throw new IllegalArgumentException("activityIds must not be empty");
        }
        if (reservedSlots < 0) {
            throw new IllegalArgumentException("reservedSlots must not be negative");
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
        body.addProperty("region", region.name());
        body.addProperty("role", role.name());
        body.addProperty(
                "joinPolicy", (joinPolicy != null ? joinPolicy : PartyJoinPolicy.OPEN).name());
        body.addProperty("reservedSlots", reservedSlots);
        if (note != null && !note.isBlank()) body.addProperty("note", note);
        if (world != null && !world.isBlank()) body.addProperty("world", world);
        return body;
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

    public CompletableFuture<CreateInviteResponse> createInvite(long listingId, UUID targetUUID) {
        JsonObject body = new JsonObject();
        body.addProperty("targetUUID", targetUUID.toString());

        return post("/party-finder/listings/" + listingId + "/invite", body, CreateInviteResponse.class);
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

    public CompletableFuture<Listing> extendListing(long id) {
        return post("/party-finder/listings/" + id + "/extend", null, Listing.class);
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

    public CompletableFuture<RankProfilesResponse> getRecognizedRankProfiles() {
        return get(authBaseUrl, "/v1/rank-profiles?scope=recognized", RankProfilesResponse.class, false);
    }

    // ── War Planner ──

    public CompletableFuture<WarPlannerSnapshot> getWarPlannerSnapshot() {
        return get("/war-planner/snapshot", WarPlannerSnapshot.class);
    }

    public CompletableFuture<WarPlannerSnapshot> setWarPlannerAvailability(int durationMinutes) {
        return put(
                "/war-planner/availability/me",
                buildWarAvailabilityPayload(durationMinutes),
                WarPlannerSnapshot.class);
    }

    public CompletableFuture<WarPlannerSnapshot> clearWarPlannerAvailability() {
        return deleteTyped("/war-planner/availability/me", WarPlannerSnapshot.class);
    }

    public CompletableFuture<WarPlannerSnapshot> updateWarPlannerCompositionRoles(
            List<WarCompositionRole> roles) {
        return put(
                "/war-planner/composition-roles/me",
                buildWarCompositionRolesPayload(roles),
                WarPlannerSnapshot.class);
    }

    public CompletableFuture<WarPlannerSnapshot> pingWarPlannerPlayer(String playerUuid) {
        if (playerUuid == null || playerUuid.isBlank()) {
            throw new IllegalArgumentException("Player UUID is required.");
        }
        return post("/war-planner/players/" + playerUuid + "/ping", new JsonObject(), WarPlannerSnapshot.class);
    }

    public CompletableFuture<WarPlannerSnapshot> createWarPlannerTeam(TeamDraft draft) {
        return post("/war-planner/teams", buildWarTeamPayload(draft, false), WarPlannerSnapshot.class);
    }

    public CompletableFuture<WarPlannerSnapshot> updateWarPlannerTeam(long id, TeamDraft draft) {
        return put(
                "/war-planner/teams/" + id,
                buildWarTeamPayload(draft, true),
                WarPlannerSnapshot.class);
    }

    public CompletableFuture<WarPlannerSnapshot> deleteWarPlannerTeam(long id) {
        return deleteTyped("/war-planner/teams/" + id, WarPlannerSnapshot.class);
    }

    public CompletableFuture<WarPlannerSnapshot> joinWarPlannerTeam(long id) {
        return put("/war-planner/teams/" + id + "/members/me", new JsonObject(), WarPlannerSnapshot.class);
    }

    public CompletableFuture<WarPlannerSnapshot> leaveWarPlannerTeam() {
        return deleteTyped("/war-planner/teams/members/me", WarPlannerSnapshot.class);
    }

    public CompletableFuture<WarPlannerSnapshot> updateWarPlannerSupport(SupportDraft draft) {
        return put("/war-planner/support", buildWarSupportPayload(draft), WarPlannerSnapshot.class);
    }

    static JsonObject buildWarSupportPayload(SupportDraft draft) {
        if (draft == null) {
            throw new IllegalArgumentException("Support draft is required.");
        }
        JsonObject body = new JsonObject();
        body.addProperty("version", draft.version());
        JsonArray slots = new JsonArray();
        draft.slots().forEach(slot -> {
            JsonObject item = new JsonObject();
            item.addProperty("code", slot.code());
            item.addProperty("player_uuid", slot.playerUuid());
            slots.add(item);
        });
        body.add("slots", slots);
        return body;
    }

    public CompletableFuture<WarPlannerSnapshot> createWarPlannerZone(ZoneDraft draft) {
        return post("/war-planner/zones", buildWarZonePayload(draft, false), WarPlannerSnapshot.class);
    }

    public CompletableFuture<WarPlannerSnapshot> updateWarPlannerZone(long id, ZoneDraft draft) {
        return put(
                "/war-planner/zones/" + id,
                buildWarZonePayload(draft, true),
                WarPlannerSnapshot.class);
    }

    public CompletableFuture<WarPlannerSnapshot> deleteWarPlannerZone(long id) {
        return deleteTyped("/war-planner/zones/" + id, WarPlannerSnapshot.class);
    }

    static JsonObject buildWarAvailabilityPayload(int durationMinutes) {
        if (durationMinutes < 1 || durationMinutes > 1440) {
            throw new IllegalArgumentException("Availability duration must be between 1 and 1440 minutes.");
        }
        JsonObject body = new JsonObject();
        body.addProperty("duration_minutes", durationMinutes);
        return body;
    }

    static JsonObject buildWarCompositionRolesPayload(List<WarCompositionRole> roles) {
        if (roles == null || roles.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("Composition roles are required.");
        }
        JsonObject body = new JsonObject();
        JsonArray roleValues = new JsonArray();
        WarCompositionRole.ordered(roles).forEach(role -> roleValues.add(role.name()));
        body.add("roles", roleValues);
        return body;
    }

    static JsonObject buildWarTeamPayload(TeamDraft draft, boolean includeVersion) {
        if (draft == null) {
            throw new IllegalArgumentException("Team draft is required.");
        }
        JsonObject body = new JsonObject();
        body.addProperty("team_type", draft.teamType().name());
        JsonObject targets = new JsonObject();
        targets.addProperty("solo", draft.compositionTargets().solo());
        targets.addProperty("dps", draft.compositionTargets().dps());
        targets.addProperty("tank", draft.compositionTargets().tank());
        body.add("composition_targets", targets);
        if (includeVersion) {
            if (draft.version() == null || draft.version() <= 0) {
                throw new IllegalArgumentException("Team version is required for updates.");
            }
            body.addProperty("version", draft.version());
        }
        JsonArray members = new JsonArray();
        for (TeamMemberDraft member : draft.members()) {
            JsonObject item = new JsonObject();
            item.addProperty("player_uuid", member.playerUuid());
            members.add(item);
        }
        body.add("members", members);
        return body;
    }

    static JsonObject buildWarZonePayload(ZoneDraft draft, boolean includeVersion) {
        if (draft == null) {
            throw new IllegalArgumentException("Zone draft is required.");
        }
        JsonObject body = new JsonObject();
        body.addProperty("name", draft.name());
        body.addProperty("color", draft.color());
        JsonArray assignedTeamIds = new JsonArray();
        draft.assignedTeamIds().forEach(assignedTeamIds::add);
        body.add("assigned_team_ids", assignedTeamIds);
        if (includeVersion) {
            if (draft.version() == null || draft.version() <= 0) {
                throw new IllegalArgumentException("Zone version is required for updates.");
            }
            body.addProperty("version", draft.version());
        }
        JsonArray territories = new JsonArray();
        draft.territories().forEach(territories::add);
        body.add("territories", territories);
        return body;
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
            long id,
            List<Long> activityIds,
            PartyRegion region,
            String note,
            String world,
            PartyJoinPolicy joinPolicy) {
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
        body.addProperty("region", region.name());
        body.addProperty(
                "joinPolicy", (joinPolicy != null ? joinPolicy : PartyJoinPolicy.OPEN).name());
        if (note != null) {
            body.addProperty("note", note);
        }
        if (world != null && !world.isBlank()) {
            body.addProperty("world", world);
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

    // ── Reward Queue ──

    public CompletableFuture<RewardQueueFirstResponse> getFirstRewardQueueEntry(String type) {
        String encodedType = URLEncoder.encode(type, StandardCharsets.UTF_8);
        return get("/reward-queue/first?type=" + encodedType, RewardQueueFirstResponse.class);
    }

    public CompletableFuture<Void> completeRewardQueueEntry(long requestId) {
        return post("/reward-queue/" + requestId + "/complete", null, Void.class);
    }

    public CompletableFuture<Void> createRewardQueueRequest(String type, String reason) {
        return post("/reward-queue/requests", buildRewardQueueRequestPayload(type, reason), Void.class);
    }

    static JsonObject buildRewardQueueRequestPayload(String type, String reason) {
        String normalizedType = normalizeRewardQueueRequestType(type);
        String normalizedReason = reason == null ? "" : reason.trim();
        if ("aspect".equals(normalizedType) && normalizedReason.isBlank()) {
            normalizedReason = DEFAULT_ASPECT_REQUEST_REASON;
        }
        if (normalizedReason.isBlank()) {
            throw new IllegalArgumentException("Reason for request is required.");
        }

        JsonObject body = new JsonObject();
        body.addProperty("type", normalizedType);
        body.addProperty("reason", normalizedReason);
        return body;
    }

    private static String normalizeRewardQueueRequestType(String type) {
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("Request type is required.");
        }

        String normalized = type.trim().toLowerCase(Locale.ROOT);
        if (!"aspect".equals(normalized) && !"tome".equals(normalized)) {
            throw new IllegalArgumentException("Unknown request type: " + type);
        }
        return normalized;
    }

    // ── Minecraft Auth ──

    public CompletableFuture<MinecraftAuthChallengeResponse> requestMinecraftAuthChallenge() {
        return postWithFallback(
                authRequestBaseUrls(), "/auth/minecraft/challenge", null, MinecraftAuthChallengeResponse.class, false);
    }

    public CompletableFuture<MinecraftAuthCompleteResponse> completeMinecraftAuthentication(
            MinecraftAuthCompleteRequest request) {
        JsonObject body = new JsonObject();
        body.addProperty("challenge_id", request.challengeId());
        body.addProperty("username", request.username());
        return postWithFallback(
                authRequestBaseUrls(), "/auth/minecraft/complete", body, MinecraftAuthCompleteResponse.class, false);
    }

    // ── HTTP helpers ──

    private <T> CompletableFuture<T> get(String path, java.lang.reflect.Type type) {
        return get(baseUrl, path, type, true);
    }

    private <T> CompletableFuture<T> get(
            String resolvedBaseUrl, String path, java.lang.reflect.Type type, boolean includeAuthHeader) {
        HttpRequest request = newRequest(resolvedBaseUrl, path, includeAuthHeader).GET().build();
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

    private <T> CompletableFuture<T> put(String path, JsonObject body, java.lang.reflect.Type type) {
        HttpRequest request = newRequest(baseUrl, path, true)
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
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
        builder.header(ClientVersion.MOD_VERSION_HEADER, ClientVersion.resolveInstalledVersion());
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
        if (!WynncraftServerPolicy.isCurrentServerAllowed()) {
            return CompletableFuture.failedFuture(mainServerOnlyException());
        }
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

    static ApiException mainServerOnlyException() {
        return new ApiException(403, MAIN_SERVER_ONLY_ERROR);
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

    public record RewardQueueFirstResponse(String type, RewardQueueEntry entry) {}

    public record RewardQueueEntry(
            @SerializedName("request_id") long requestId,
            String type,
            @SerializedName("discord_account_id") long discordAccountId,
            @SerializedName("discord_id") String discordId,
            @SerializedName("minecraft_username") String minecraftUsername,
            @SerializedName("guild_rank") String guildRank,
            @SerializedName("created_at") Instant createdAt) {}
}
