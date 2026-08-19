package com.seqwawa.seq.managers;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.model.war.WarPlannerDrafts.TeamDraft;
import com.seqwawa.seq.model.war.WarPlannerDrafts.TeamMemberMoveDraft;
import com.seqwawa.seq.model.war.WarPlannerDrafts.SupportDraft;
import com.seqwawa.seq.model.war.WarPlannerDrafts.ZoneDraft;
import com.seqwawa.seq.model.war.WarPlannerDrafts.ZoneCategoryDraft;
import com.seqwawa.seq.model.war.WarPlannerDrafts.ZonePlacementDraft;
import com.seqwawa.seq.model.war.WarCompositionRole;
import com.seqwawa.seq.model.war.WarPlannerSnapshot;
import com.seqwawa.seq.model.war.WarPlannerSnapshot.RosterMember;
import com.seqwawa.seq.network.ApiClient;
import com.seqwawa.seq.network.ApiClient.ApiException;
import com.seqwawa.seq.network.WynncraftServerPolicy;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/** Owns the immutable war-planner snapshot and keeps all network work off the render thread. */
public final class WarPlannerManager {
    private static final long READY_POLL_MS = Duration.ofSeconds(45).toMillis();
    private static final long FORBIDDEN_RETRY_MS = Duration.ofMinutes(5).toMillis();
    private static final long MAX_BACKOFF_MS = Duration.ofMinutes(2).toMillis();

    public enum State {
        UNKNOWN,
        LOADING,
        READY,
        FORBIDDEN,
        OFFLINE
    }

    public interface Gateway {
        CompletableFuture<WarPlannerSnapshot> snapshot();

        CompletableFuture<WarPlannerSnapshot> setAvailability(int durationMinutes);

        CompletableFuture<WarPlannerSnapshot> clearAvailability();

        CompletableFuture<WarPlannerSnapshot> updateCompositionRoles(List<WarCompositionRole> roles);

        CompletableFuture<WarPlannerSnapshot> pingPlayer(String playerUuid);

        CompletableFuture<WarPlannerSnapshot> createTeam(TeamDraft draft);

        CompletableFuture<WarPlannerSnapshot> updateTeam(long id, TeamDraft draft);

        CompletableFuture<WarPlannerSnapshot> deleteTeam(long id, long version);

        CompletableFuture<WarPlannerSnapshot> moveTeamMember(String playerUuid, TeamMemberMoveDraft draft);

        CompletableFuture<WarPlannerSnapshot> joinTeam(long id);

        CompletableFuture<WarPlannerSnapshot> leaveTeam();

        CompletableFuture<WarPlannerSnapshot> updateSupport(SupportDraft draft);

        CompletableFuture<WarPlannerSnapshot> createZone(ZoneDraft draft);

        CompletableFuture<WarPlannerSnapshot> updateZone(long id, ZoneDraft draft);

        CompletableFuture<WarPlannerSnapshot> deleteZone(long id, long version);

        CompletableFuture<WarPlannerSnapshot> moveZone(long id, ZonePlacementDraft draft);

        CompletableFuture<WarPlannerSnapshot> createZoneCategory(ZoneCategoryDraft draft);

        CompletableFuture<WarPlannerSnapshot> updateZoneCategory(long id, ZoneCategoryDraft draft);

        CompletableFuture<WarPlannerSnapshot> deleteZoneCategory(long id, long version);

        CompletableFuture<WarPlannerSnapshot> setHqTerritory(String territory, long version);
    }

    public record ActionResult(boolean success, String code, String message) {
        public ActionResult(boolean success, String message) {
            this(success, null, message);
        }
    }

    record ApiErrorDetails(String code, String message) {}

    private final Gateway gateway;
    private final Clock clock;

    private volatile State state = State.UNKNOWN;
    private volatile WarPlannerSnapshot snapshot;
    private volatile String lastError;
    private volatile boolean mutating;
    private volatile long serverOffsetMillis;

    private CompletableFuture<?> inFlight;
    private long nextPollAtMillis;
    private int consecutiveFailures;
    private long generation;

    public WarPlannerManager() {
        this(new ApiGateway(ApiClient.getInstance()), Clock.systemUTC());
    }

    public WarPlannerManager(Gateway gateway, Clock clock) {
        this.gateway = Objects.requireNonNull(gateway, "gateway");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public State state() {
        return state;
    }

    public WarPlannerSnapshot snapshot() {
        return snapshot;
    }

    public String lastError() {
        return lastError;
    }

    public boolean isMutating() {
        return mutating;
    }

    /** Visibility deliberately depends on a successful authorized response, never local role inference. */
    public boolean isAuthorized() {
        return snapshot != null && state != State.FORBIDDEN;
    }

    public boolean canManage() {
        WarPlannerSnapshot current = snapshot;
        return isAuthorized() && current.self() != null && current.self().canManage();
    }

    public Instant serverNow() {
        return clock.instant().plusMillis(serverOffsetMillis);
    }

    public Duration ownAvailabilityRemaining() {
        WarPlannerSnapshot current = snapshot;
        RosterMember caller = current == null ? null : current.caller();
        return caller == null || !caller.available()
                ? Duration.ZERO
                : remainingUntil(caller.availableUntil(), serverNow());
    }

    public static Duration remainingUntil(Instant until, Instant now) {
        if (until == null || now == null || !until.isAfter(now)) {
            return Duration.ZERO;
        }
        return Duration.between(now, until);
    }

    public void tick() {
        boolean hasToken = SeqClient.getConfigManager() != null
                && SeqClient.getConfigManager().getToken() != null
                && !SeqClient.getConfigManager().getToken().isBlank();
        tick(WynncraftServerPolicy.isCurrentServerAllowed(), hasToken);
    }

    public synchronized void tick(boolean contextAllowed, boolean hasToken) {
        if (!contextAllowed || !hasToken) {
            reset();
            return;
        }
        if (inFlight != null || clock.millis() < nextPollAtMillis) {
            return;
        }
        startRefresh(false);
    }

    public synchronized CompletableFuture<ActionResult> refreshNow() {
        if (inFlight != null) {
            return CompletableFuture.completedFuture(new ActionResult(false, "A war planner request is already running."));
        }
        return startRefresh(true);
    }

    public CompletableFuture<ActionResult> setAvailability(int durationMinutes) {
        if (durationMinutes < 1 || durationMinutes > 1440) {
            return CompletableFuture.completedFuture(
                    new ActionResult(false, "Availability must be between 1 and 1440 minutes."));
        }
        return mutate(() -> gateway.setAvailability(durationMinutes), "Availability updated.");
    }

    public CompletableFuture<ActionResult> clearAvailability() {
        return mutate(gateway::clearAvailability, "Availability cleared.");
    }

    public CompletableFuture<ActionResult> updateCompositionRoles(List<WarCompositionRole> roles) {
        if (roles == null || roles.stream().anyMatch(Objects::isNull)) {
            return CompletableFuture.completedFuture(new ActionResult(false, "Choose valid war roles."));
        }
        return mutate(() -> gateway.updateCompositionRoles(roles), "Discord war roles updated.");
    }

    public CompletableFuture<ActionResult> pingPlayer(String playerUuid) {
        if (playerUuid == null || playerUuid.isBlank()) {
            return CompletableFuture.completedFuture(new ActionResult(false, "Choose a player to ping."));
        }
        return mutate(() -> gateway.pingPlayer(playerUuid), "Ping sent in war chat.");
    }

    public CompletableFuture<ActionResult> saveTeam(Long id, TeamDraft draft) {
        if (!canManage()) {
            return managementDenied();
        }
        return mutate(
                () -> id == null ? gateway.createTeam(draft) : gateway.updateTeam(id, draft),
                id == null ? "War team created." : "War team updated.");
    }

    public CompletableFuture<ActionResult> saveSupport(SupportDraft draft) {
        if (!canManage()) {
            return managementDenied();
        }
        return mutate(() -> gateway.updateSupport(draft), "Support board updated.");
    }

    public CompletableFuture<ActionResult> deleteTeam(long id, Long version) {
        if (!canManage()) {
            return managementDenied();
        }
        if (version == null || version <= 0) {
            return CompletableFuture.completedFuture(
                    new ActionResult(false, "invalid_version", "Reload the team before deleting it."));
        }
        return mutate(() -> gateway.deleteTeam(id, version), "War team deleted.");
    }

    public CompletableFuture<ActionResult> moveTeamMember(String playerUuid, TeamMemberMoveDraft draft) {
        if (!canManage()) {
            return managementDenied();
        }
        if (playerUuid == null || playerUuid.isBlank() || draft == null) {
            return CompletableFuture.completedFuture(
                    new ActionResult(false, "invalid_team_move", "Choose a player and destination team."));
        }
        return mutate(() -> gateway.moveTeamMember(playerUuid, draft), "War team assignment updated.");
    }

    public CompletableFuture<ActionResult> joinTeam(long id) {
        RosterMember caller = snapshot == null ? null : snapshot.caller();
        boolean switching = caller != null && caller.teamId() != null && caller.teamId() != id;
        return mutate(() -> gateway.joinTeam(id), switching ? "Switched war teams." : "Joined war team.");
    }

    public CompletableFuture<ActionResult> leaveTeam() {
        return mutate(gateway::leaveTeam, "Left war team.");
    }

    public CompletableFuture<ActionResult> saveZone(Long id, ZoneDraft draft) {
        if (!canManage()) {
            return managementDenied();
        }
        return mutate(
                () -> id == null ? gateway.createZone(draft) : gateway.updateZone(id, draft),
                id == null ? "Territory zone created." : "Territory zone updated.");
    }

    public CompletableFuture<ActionResult> deleteZone(long id, Long version) {
        if (!canManage()) {
            return managementDenied();
        }
        if (version == null || version <= 0) {
            return CompletableFuture.completedFuture(
                    new ActionResult(false, "invalid_version", "Reload the zone before deleting it."));
        }
        return mutate(() -> gateway.deleteZone(id, version), "Territory zone deleted.");
    }

    public CompletableFuture<ActionResult> moveZone(long id, ZonePlacementDraft draft) {
        if (!canManage()) return managementDenied();
        return mutate(() -> gateway.moveZone(id, draft), "Territory zone moved.");
    }

    public CompletableFuture<ActionResult> saveZoneCategory(Long id, ZoneCategoryDraft draft) {
        if (!canManage()) return managementDenied();
        return mutate(
                () -> id == null ? gateway.createZoneCategory(draft) : gateway.updateZoneCategory(id, draft),
                id == null ? "Zone category created." : "Zone category renamed.");
    }

    public CompletableFuture<ActionResult> deleteZoneCategory(long id, Long version) {
        if (!canManage()) return managementDenied();
        if (version == null || version <= 0) {
            return CompletableFuture.completedFuture(
                    new ActionResult(false, "invalid_version", "Reload the category before deleting it."));
        }
        return mutate(() -> gateway.deleteZoneCategory(id, version), "Zone category deleted.");
    }

    public CompletableFuture<ActionResult> setHqTerritory(String territory, long version) {
        if (!canManage()) {
            return managementDenied();
        }
        if (version <= 0) {
            return CompletableFuture.completedFuture(
                    new ActionResult(false, "invalid_version", "Refresh the planner before changing its HQ."));
        }
        return mutate(
                () -> gateway.setHqTerritory(territory, version),
                territory == null ? "HQ territory cleared." : territory + " marked as HQ.");
    }

    private static CompletableFuture<ActionResult> managementDenied() {
        return CompletableFuture.completedFuture(
                new ActionResult(
                        false,
                        "war_manager_required",
                        "You do not have permission to manage war teams or zones."));
    }

    public synchronized void reset() {
        if (state == State.UNKNOWN && snapshot == null && inFlight == null) {
            return;
        }
        generation++;
        if (inFlight != null) {
            inFlight.cancel(true);
        }
        inFlight = null;
        snapshot = null;
        state = State.UNKNOWN;
        lastError = null;
        mutating = false;
        consecutiveFailures = 0;
        nextPollAtMillis = 0;
        serverOffsetMillis = 0;
    }

    private CompletableFuture<ActionResult> startRefresh(boolean userInitiated) {
        long requestGeneration = generation;
        if (snapshot == null) {
            state = State.LOADING;
        }
        lastError = null;
        CompletableFuture<ActionResult> result = new CompletableFuture<>();
        CompletableFuture<WarPlannerSnapshot> request = gateway.snapshot();
        inFlight = request;
        request.whenComplete((received, error) -> completeRequest(
                requestGeneration, received, error, false, userInitiated ? "War planner refreshed." : null, result));
        return result;
    }

    private synchronized CompletableFuture<ActionResult> mutate(
            Supplier<CompletableFuture<WarPlannerSnapshot>> operation, String successMessage) {
        if (!isAuthorized()) {
            return CompletableFuture.completedFuture(new ActionResult(false, "War planner access has not been authorized."));
        }
        if (inFlight != null) {
            return CompletableFuture.completedFuture(new ActionResult(false, "Another war planner request is still running."));
        }
        final CompletableFuture<WarPlannerSnapshot> request;
        try {
            request = operation.get();
        } catch (RuntimeException exception) {
            return CompletableFuture.completedFuture(new ActionResult(false, exception.getMessage()));
        }
        mutating = true;
        lastError = null;
        long requestGeneration = generation;
        CompletableFuture<ActionResult> result = new CompletableFuture<>();
        inFlight = request;
        request.whenComplete((received, error) ->
                completeRequest(requestGeneration, received, error, true, successMessage, result));
        return result;
    }

    private synchronized void completeRequest(
            long requestGeneration,
            WarPlannerSnapshot received,
            Throwable error,
            boolean mutation,
            String successMessage,
            CompletableFuture<ActionResult> result) {
        if (requestGeneration != generation) {
            result.complete(new ActionResult(false, "The active Minecraft account changed."));
            return;
        }
        inFlight = null;
        mutating = false;

        Throwable cause = unwrap(error);
        if (cause == null && received != null && received.isSupported()) {
            snapshot = received;
            state = State.READY;
            lastError = null;
            consecutiveFailures = 0;
            nextPollAtMillis = clock.millis() + READY_POLL_MS;
            serverOffsetMillis = received.serverTime() == null
                    ? 0
                    : Duration.between(clock.instant(), received.serverTime()).toMillis();
            result.complete(new ActionResult(true, successMessage == null ? "" : successMessage));
            return;
        }

        boolean incompatibleSchema = cause == null && received != null && !received.isSupported();
        if (cause == null) {
            cause = received == null
                    ? new IllegalStateException("The backend returned an empty war planner response.")
                    : new IllegalStateException("Unsupported war planner schema " + received.schemaVersion() + ".");
        }
        int status = cause instanceof ApiException apiException ? apiException.getStatusCode() : 0;
        ApiErrorDetails apiError = apiError(cause);
        String code = apiError.code();
        String message = apiError.message();
        lastError = message;
        if (incompatibleSchema) {
            snapshot = null;
        }

        boolean managerRequired = mutation
                && snapshot != null
                && "war_manager_required".equals(code);
        boolean guildAuthorizationUnverifiable = "guild_roster_unavailable".equals(code);
        boolean clearsSensitiveData = status == 401
                || (status == 403 && !managerRequired)
                || status == 426
                || "not_in_guild".equals(code)
                || isAuthenticationError(code);
        boolean recoverableMutationError = mutation
                && snapshot != null
                && (status == 400 || status == 404 || status == 409 || status == 422 || status == 429);

        if (managerRequired) {
            snapshot = snapshot.withCanManage(false);
            state = State.READY;
            consecutiveFailures = 0;
            nextPollAtMillis = 0;
        } else if (guildAuthorizationUnverifiable) {
            snapshot = null;
            state = State.OFFLINE;
            consecutiveFailures++;
            long backoff = Math.min(MAX_BACKOFF_MS, 5_000L << Math.min(5, consecutiveFailures - 1));
            nextPollAtMillis = clock.millis() + backoff;
        } else if (clearsSensitiveData) {
            snapshot = null;
            state = State.FORBIDDEN;
            consecutiveFailures = 0;
            nextPollAtMillis = clock.millis() + FORBIDDEN_RETRY_MS;
        } else if (recoverableMutationError) {
            state = State.READY;
            consecutiveFailures = 0;
            if (status == 404 || status == 409) {
                nextPollAtMillis = 0;
            } else if (status == 429) {
                nextPollAtMillis = Math.max(nextPollAtMillis, clock.millis() + 5_000L);
            }
        } else {
            state = State.OFFLINE;
            consecutiveFailures++;
            long backoff = Math.min(MAX_BACKOFF_MS, 5_000L << Math.min(5, consecutiveFailures - 1));
            nextPollAtMillis = clock.millis() + backoff;
        }
        result.complete(new ActionResult(false, code, message));
    }

    static Throwable unwrap(Throwable throwable) {
        Throwable current = throwable;
        while (current != null
                && (current instanceof java.util.concurrent.CompletionException
                        || current instanceof java.util.concurrent.ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    static ApiErrorDetails apiError(Throwable throwable) {
        if (throwable instanceof ApiException apiException) {
            String code = null;
            String message = null;
            try {
                JsonObject body = JsonParser.parseString(apiException.getResponseBody()).getAsJsonObject();
                if (body.has("code") && !body.get("code").isJsonNull()) {
                    code = body.get("code").getAsString();
                } else if (body.has("error") && !body.get("error").isJsonNull()) {
                    code = body.get("error").getAsString();
                }
                if (body.has("message") && !body.get("message").isJsonNull()) {
                    message = body.get("message").getAsString();
                }
            } catch (RuntimeException ignored) {
                // Fall through to status-specific text.
            }
            if (message == null || message.isBlank()) {
                message = code == null || code.isBlank()
                        ? switch (apiException.getStatusCode()) {
                            case 400 -> "The war planner request was invalid.";
                            case 401 -> "Connect your Minecraft account to use the war planner.";
                            case 403 -> "The war planner is available to Sequoia members only.";
                            case 404 -> "That war planner item no longer exists.";
                            case 409 -> "The planner changed on the server. Refresh and try again.";
                            case 422 -> "The team or zone contains invalid data.";
                            case 426 -> "Update the Sequoia mod to use the war planner.";
                            case 429 -> "Too many requests. Please wait before trying again.";
                            default -> "War planner request failed (HTTP " + apiException.getStatusCode() + ").";
                        }
                        : code.replace('_', ' ');
            }
            return new ApiErrorDetails(code, message);
        }
        String message = throwable == null ? null : throwable.getMessage();
        return new ApiErrorDetails(
                null,
                message == null || message.isBlank() ? "Could not reach the war planner." : message);
    }

    static String userMessage(Throwable throwable) {
        return apiError(throwable).message();
    }

    private static boolean isAuthenticationError(String code) {
        if (code == null) return false;
        return code.equals("unauthorized")
                || code.equals("authentication_required")
                || code.equals("token_invalid")
                || code.equals("token_expired")
                || code.equals("invalid_token");
    }

    private record ApiGateway(ApiClient api) implements Gateway {
        @Override
        public CompletableFuture<WarPlannerSnapshot> snapshot() {
            return api.getWarPlannerSnapshot();
        }

        @Override
        public CompletableFuture<WarPlannerSnapshot> setAvailability(int durationMinutes) {
            return api.setWarPlannerAvailability(durationMinutes);
        }

        @Override
        public CompletableFuture<WarPlannerSnapshot> clearAvailability() {
            return api.clearWarPlannerAvailability();
        }

        @Override
        public CompletableFuture<WarPlannerSnapshot> updateCompositionRoles(List<WarCompositionRole> roles) {
            return api.updateWarPlannerCompositionRoles(roles);
        }

        @Override
        public CompletableFuture<WarPlannerSnapshot> pingPlayer(String playerUuid) {
            return api.pingWarPlannerPlayer(playerUuid);
        }

        @Override
        public CompletableFuture<WarPlannerSnapshot> createTeam(TeamDraft draft) {
            return api.createWarPlannerTeam(draft);
        }

        @Override
        public CompletableFuture<WarPlannerSnapshot> updateTeam(long id, TeamDraft draft) {
            return api.updateWarPlannerTeam(id, draft);
        }

        @Override
        public CompletableFuture<WarPlannerSnapshot> deleteTeam(long id, long version) {
            return api.deleteWarPlannerTeam(id, version);
        }

        @Override
        public CompletableFuture<WarPlannerSnapshot> moveTeamMember(
                String playerUuid, TeamMemberMoveDraft draft) {
            return api.moveWarPlannerTeamMember(playerUuid, draft);
        }

        @Override
        public CompletableFuture<WarPlannerSnapshot> joinTeam(long id) {
            return api.joinWarPlannerTeam(id);
        }

        @Override
        public CompletableFuture<WarPlannerSnapshot> leaveTeam() {
            return api.leaveWarPlannerTeam();
        }

        @Override
        public CompletableFuture<WarPlannerSnapshot> updateSupport(SupportDraft draft) {
            return api.updateWarPlannerSupport(draft);
        }

        @Override
        public CompletableFuture<WarPlannerSnapshot> createZone(ZoneDraft draft) {
            return api.createWarPlannerZone(draft);
        }

        @Override
        public CompletableFuture<WarPlannerSnapshot> updateZone(long id, ZoneDraft draft) {
            return api.updateWarPlannerZone(id, draft);
        }

        @Override
        public CompletableFuture<WarPlannerSnapshot> deleteZone(long id, long version) {
            return api.deleteWarPlannerZone(id, version);
        }

        @Override
        public CompletableFuture<WarPlannerSnapshot> moveZone(long id, ZonePlacementDraft draft) {
            return api.moveWarPlannerZone(id, draft);
        }

        @Override
        public CompletableFuture<WarPlannerSnapshot> createZoneCategory(ZoneCategoryDraft draft) {
            return api.createWarPlannerZoneCategory(draft);
        }

        @Override
        public CompletableFuture<WarPlannerSnapshot> updateZoneCategory(long id, ZoneCategoryDraft draft) {
            return api.updateWarPlannerZoneCategory(id, draft);
        }

        @Override
        public CompletableFuture<WarPlannerSnapshot> deleteZoneCategory(long id, long version) {
            return api.deleteWarPlannerZoneCategory(id, version);
        }

        @Override
        public CompletableFuture<WarPlannerSnapshot> setHqTerritory(String territory, long version) {
            return api.setWarPlannerHqTerritory(territory, version);
        }
    }
}
