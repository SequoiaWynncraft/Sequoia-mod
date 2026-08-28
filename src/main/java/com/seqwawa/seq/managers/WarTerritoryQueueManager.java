package com.seqwawa.seq.managers;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.seqwawa.seq.accessors.NotificationAccessor;
import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.model.war.WarTerritoryQueueFeed;
import com.seqwawa.seq.model.war.WarTerritoryQueueFeed.Participant;
import com.seqwawa.seq.model.war.WarTerritoryQueueFeed.TerritoryQueue;
import com.seqwawa.seq.network.ApiClient;
import com.seqwawa.seq.utils.PacketTextNormalizer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.network.chat.Component;

/** Owns the schema-v1 queue feed: live while available, one-shot when explicitly viewed. */
public final class WarTerritoryQueueManager {
    static final Duration POLL_INTERVAL = Duration.ofSeconds(5);
    static final int MAX_PENDING_OBSERVATIONS = 24;
    private static final int MAX_TIMED_OBSERVATION_RETRIES = 3;
    private static final int MAX_RECENT_OBSERVATIONS = 128;
    private static final int MAX_RECENT_QUEUE_SNAPSHOTS = 128;
    private static final int MISSED_WAR_BLAME_VARIANT_COUNT = 6;
    private static final Duration OBSERVATION_DEDUPE_WINDOW = Duration.ofSeconds(15);
    static final Duration MISSED_WAR_MIN_DELAY = Duration.ofSeconds(1);
    static final Duration MISSED_WAR_MAX_DELAY = Duration.ofSeconds(10);
    private static final Duration MISSED_WAR_CACHE_RETENTION = Duration.ofSeconds(30);
    private static final String NOBODY_LOGGED_IN_MESSAGE = "Nobody logged in for the war.";
    private static final Pattern MINECRAFT_USERNAME_PATTERN = Pattern.compile("^[A-Za-z0-9_]{3,16}$");
    private static final Pattern DEFENSE_MESSAGE_PATTERN = Pattern.compile(
            "^(.{1,128}?) defense is (Very Low|Low|Medium|High|Very High)$");
    private static final Pattern QUEUE_CONFIRMATION_PATTERN = Pattern.compile(
            "^The war for (?<territory>.{1,128}?) will start in "
                    + "(?:(?<minutes>\\d+) minutes?(?: and (?<seconds>\\d+) seconds?)?"
                    + "|(?<secondsOnly>\\d+) seconds?)\\.$",
            Pattern.CASE_INSENSITIVE);
    private static final Set<String> DEFENSE_RATINGS =
            Set.of("Very Low", "Low", "Medium", "High", "Very High");

    public enum State {
        INACTIVE,
        LOADING,
        READY,
        OFFLINE
    }

    public interface Gateway {
        CompletableFuture<WarTerritoryQueueFeed> fetch();

        CompletableFuture<WarTerritoryQueueFeed> observe(Observation observation);

        CompletableFuture<WarTerritoryQueueFeed> join(long queueId);

        CompletableFuture<WarTerritoryQueueFeed> leave(long queueId);
    }

    interface AvailabilityContext {
        boolean available();

        String playerUuid();

        default void refreshAvailability() {}
    }

    public record Observation(
            String minecraftUsername,
            String nickname,
            String territory,
            String defenseRating,
            Integer queueDurationSeconds) {
        public Observation(String minecraftUsername, String nickname, String territory, String defenseRating) {
            this(minecraftUsername, nickname, territory, defenseRating, null);
        }

        static Observation confirmation(String territory, int queueDurationSeconds) {
            return new Observation(null, null, territory, null, queueDurationSeconds);
        }

        public Observation {
            minecraftUsername = normalizeOptional(minecraftUsername);
            nickname = normalizeOptional(nickname);
            if (nickname != null && nickname.length() > 64) {
                throw new IllegalArgumentException("Nickname is too long.");
            }
            territory = normalizeRequired(territory, "Territory");
            if (territory.length() > 128) {
                throw new IllegalArgumentException("Territory is too long.");
            }
            defenseRating = normalizeOptional(defenseRating);
            if (queueDurationSeconds != null && (queueDurationSeconds < 1 || queueDurationSeconds > 3_600)) {
                throw new IllegalArgumentException("Queue duration must be between 1 and 3600 seconds.");
            }
            boolean provisional = minecraftUsername == null && nickname == null && defenseRating == null;
            if (provisional) {
                if (queueDurationSeconds == null) {
                    throw new IllegalArgumentException("A timer-only confirmation requires a queue duration.");
                }
            } else {
                if (minecraftUsername == null || !MINECRAFT_USERNAME_PATTERN.matcher(minecraftUsername).matches()) {
                    throw new IllegalArgumentException("A valid Minecraft username is required.");
                }
                if (defenseRating == null || !DEFENSE_RATINGS.contains(defenseRating)) {
                    throw new IllegalArgumentException("A valid defense rating is required.");
                }
            }
        }

        boolean provisional() {
            return minecraftUsername == null;
        }

        String dedupeKey() {
            if (provisional()) {
                return "confirmation\u0000" + territory.toLowerCase(Locale.ROOT);
            }
            return baseDedupeKey()
                    + '\u0000'
                    + (queueDurationSeconds == null ? "untimed" : "timed");
        }

        String timedDedupeKey() {
            if (provisional()) {
                return dedupeKey();
            }
            return baseDedupeKey() + "\u0000timed";
        }

        private String baseDedupeKey() {
            return minecraftUsername.toLowerCase(Locale.ROOT)
                    + '\u0000'
                    + territory.toLowerCase(Locale.ROOT)
                    + '\u0000'
                    + defenseRating;
        }
    }

    record QueueConfirmation(String territory, int durationSeconds) {}

    public record ActionResult(boolean success, String code, String message) {}

    private final Gateway gateway;
    private final Clock clock;
    private final AvailabilityContext availabilityContext;
    private final Consumer<String> missedWarNotifier;
    private final BooleanSupplier missedWarMessagesEnabled;
    private final ArrayDeque<PendingObservation> pendingObservations = new ArrayDeque<>();
    private final LinkedHashMap<String, Instant> recentObservations = new LinkedHashMap<>();
    private final LinkedHashMap<Long, TerritoryQueue> recentQueueSnapshots = new LinkedHashMap<>();
    private final LinkedHashMap<Long, Instant> notifiedMissedWars = new LinkedHashMap<>();
    private final Map<Long, PendingMembershipMutation> pendingMembershipMutations = new LinkedHashMap<>();

    private volatile State state = State.INACTIVE;
    private volatile WarTerritoryQueueFeed feed = WarTerritoryQueueFeed.empty();
    private volatile String lastError;
    private volatile long serverOffsetMillis;
    private boolean active;
    private long generation;
    private long nextPollAtMillis;
    private long nextObservationAttemptAtMillis;
    private CompletableFuture<WarTerritoryQueueFeed> pollInFlight;
    private CompletableFuture<WarTerritoryQueueFeed> observationInFlight;
    private CompletableFuture<WarTerritoryQueueFeed> viewerFetchInFlight;
    private CompletableFuture<ActionResult> viewerRefreshInFlight;

    public WarTerritoryQueueManager() {
        this(
                new ApiGateway(ApiClient.getInstance()),
                Clock.systemUTC(),
                new RuntimeAvailabilityContext(),
                NotificationAccessor::notifyPlayer,
                () -> SeqClient.getWarQueueMissMessagesSetting() != null
                        && SeqClient.getWarQueueMissMessagesSetting().getValue());
    }

    WarTerritoryQueueManager(Gateway gateway, Clock clock, AvailabilityContext availabilityContext) {
        this(gateway, clock, availabilityContext, NotificationAccessor::notifyPlayer, () -> true);
    }

    WarTerritoryQueueManager(
            Gateway gateway,
            Clock clock,
            AvailabilityContext availabilityContext,
            Consumer<String> missedWarNotifier) {
        this(gateway, clock, availabilityContext, missedWarNotifier, () -> true);
    }

    WarTerritoryQueueManager(
            Gateway gateway,
            Clock clock,
            AvailabilityContext availabilityContext,
            Consumer<String> missedWarNotifier,
            BooleanSupplier missedWarMessagesEnabled) {
        this.gateway = java.util.Objects.requireNonNull(gateway, "gateway");
        this.clock = java.util.Objects.requireNonNull(clock, "clock");
        this.availabilityContext = java.util.Objects.requireNonNull(availabilityContext, "availabilityContext");
        this.missedWarNotifier = java.util.Objects.requireNonNull(missedWarNotifier, "missedWarNotifier");
        this.missedWarMessagesEnabled =
                java.util.Objects.requireNonNull(missedWarMessagesEnabled, "missedWarMessagesEnabled");
    }

    public State state() {
        return state;
    }

    public boolean isActive() {
        return active && isAvailabilityActive();
    }

    public WarTerritoryQueueFeed feed() {
        return feed;
    }

    public String lastError() {
        return lastError;
    }

    public Instant serverNow() {
        return clock.instant().plusMillis(serverOffsetMillis);
    }

    public String localPlayerUuid() {
        return playerUuid();
    }

    public List<TerritoryQueue> activeQueues() {
        Instant now = serverNow();
        return feed.queues().stream()
                .filter(queue -> queue.id() > 0)
                .filter(queue -> !queue.territory().isBlank())
                .filter(queue -> queue.expiresAt() != null && !queue.isExpired(now))
                .sorted(Comparator.comparing(
                                TerritoryQueue::expiresAt,
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(TerritoryQueue::territory, String.CASE_INSENSITIVE_ORDER)
                        .thenComparingLong(TerritoryQueue::id))
                .toList();
    }

    public Optional<TerritoryQueue> queueForTerritory(String territory) {
        if (territory == null || territory.isBlank()) {
            return Optional.empty();
        }
        return activeQueues().stream()
                .filter(queue -> territory.equalsIgnoreCase(queue.territory()))
                .findFirst();
    }

    public void tick() {
        if (!isAvailabilityActive()) {
            synchronized (this) {
                if (playerUuid() == null) {
                    resetLocked();
                } else if (active) {
                    suspendLivePollingLocked();
                }
            }
            return;
        }

        synchronized (this) {
            if (!active) {
                active = true;
                state = State.LOADING;
                nextPollAtMillis = 0L;
            }
            cleanupRecentObservations(clock.instant());
            cleanupRecentQueueSnapshots(adjustedServerNow());
            if (pollInFlight == null && clock.millis() >= nextPollAtMillis) {
                startPoll();
            }
            if (observationInFlight == null
                    && !pendingObservations.isEmpty()
                    && clock.millis() >= nextObservationAttemptAtMillis) {
                startObservation();
            }
        }
    }

    /** Fetches one queue snapshot for the explicitly opened war map without enabling live polling. */
    public synchronized CompletableFuture<ActionResult> refreshForViewer() {
        if (playerUuid() == null) {
            return completedFailure(
                    "player_identity_unavailable", "Your war queue identity is unavailable; try again shortly.");
        }
        if (isAvailabilityActive()) {
            tick();
            return CompletableFuture.completedFuture(new ActionResult(true, null, ""));
        }
        if (viewerRefreshInFlight != null) {
            return viewerRefreshInFlight;
        }

        final CompletableFuture<WarTerritoryQueueFeed> request;
        try {
            request = gateway.fetch();
        } catch (RuntimeException exception) {
            ErrorDetails details = errorDetails(exception);
            return completedFailure(details.code(), details.message());
        }
        state = feed.queues().isEmpty() ? State.LOADING : State.READY;
        CompletableFuture<ActionResult> result = new CompletableFuture<>();
        viewerFetchInFlight = request;
        viewerRefreshInFlight = result;
        long requestGeneration = generation;
        request.whenComplete((received, error) ->
                completeViewerRefresh(requestGeneration, request, result, received, error));
        return result;
    }

    public boolean onSystemChat(Component message) {
        if (!isAvailabilityActive()) {
            return false;
        }
        if (isNobodyLoggedInMessage(message)) {
            notifyMissedWar();
            return false;
        }
        Optional<QueueConfirmation> confirmation = parseQueueConfirmation(message);
        if (confirmation.isPresent()) {
            QueueConfirmation parsed = confirmation.get();
            Instant expiresAt = clock.instant().plusSeconds(parsed.durationSeconds());
            enqueueObservation(new PendingObservation(
                    Observation.confirmation(parsed.territory(), parsed.durationSeconds()), expiresAt, 0));
            return false;
        }
        Optional<Observation> parsed = parseGuildObservation(message);
        return parsed.isPresent() && enqueueObservation(PendingObservation.untimed(parsed.get()));
    }

    static boolean isNobodyLoggedInMessage(Component message) {
        if (message == null) {
            return false;
        }
        return isNobodyLoggedInMessage(PacketTextNormalizer.normalizeForParsing(message.getString()));
    }

    static boolean isNobodyLoggedInMessage(String normalizedMessage) {
        return normalizedMessage != null && NOBODY_LOGGED_IN_MESSAGE.equals(normalizedMessage.trim());
    }

    static Optional<QueueConfirmation> parseQueueConfirmation(Component message) {
        if (message == null) {
            return Optional.empty();
        }
        return parseQueueConfirmation(PacketTextNormalizer.normalizeForParsing(message.getString()));
    }

    static Optional<QueueConfirmation> parseQueueConfirmation(String normalizedMessage) {
        if (normalizedMessage == null || normalizedMessage.isBlank()) {
            return Optional.empty();
        }
        Matcher matcher = QUEUE_CONFIRMATION_PATTERN.matcher(normalizedMessage.trim());
        if (!matcher.matches()) {
            return Optional.empty();
        }
        try {
            long minutes = parseOptionalLong(matcher.group("minutes"));
            long seconds = parseOptionalLong(matcher.group("seconds"))
                    + parseOptionalLong(matcher.group("secondsOnly"));
            long durationSeconds = Math.addExact(Math.multiplyExact(minutes, 60L), seconds);
            if (durationSeconds < 1 || durationSeconds > 3_600) {
                return Optional.empty();
            }
            return Optional.of(new QueueConfirmation(matcher.group("territory").trim(), (int) durationSeconds));
        } catch (ArithmeticException | NumberFormatException ignored) {
            return Optional.empty();
        }
    }

    static Optional<Observation> parseGuildObservation(Component message) {
        if (!ChatManager.hasLeadingGuildChatColor(message)) {
            return Optional.empty();
        }
        ChatManager.ParsedMessage parsed = ChatManager.parseGuildMessage(message);
        if (parsed == null) {
            return Optional.empty();
        }
        String normalizedContent = PacketTextNormalizer.normalizeForParsing(parsed.message());
        Matcher matcher = DEFENSE_MESSAGE_PATTERN.matcher(normalizedContent);
        if (!matcher.matches()) {
            return Optional.empty();
        }
        try {
            return Optional.of(new Observation(
                    parsed.username(), parsed.nickname(), matcher.group(1), matcher.group(2)));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    public synchronized CompletableFuture<ActionResult> joinQueue(long queueId) {
        if (queueId <= 0) {
            return completedFailure("territory_queue_not_found", "That territory queue no longer exists.");
        }

        TerritoryQueue queue = activeQueues().stream()
                .filter(candidate -> candidate.id() == queueId)
                .findFirst()
                .orElse(null);
        if (queue == null) {
            return completedFailure("territory_queue_not_found", "That territory queue no longer exists.");
        }
        String playerUuid = availabilityContext.playerUuid();
        if (queue.hasParticipant(playerUuid)) {
            return CompletableFuture.completedFuture(
                    new ActionResult(true, null, "You already joined that territory queue."));
        }
        if (queue.full()) {
            return completedFailure("territory_queue_full", "That territory queue already has five participants.");
        }

        return startMembershipMutation(queueId, MembershipAction.JOIN);
    }

    /**
     * Toggles the authenticated player's membership using the latest backend feed.
     * The queue owner remains in the reserved position-zero slot and is therefore a
     * local no-op rather than a destructive queue cancellation.
     */
    public synchronized CompletableFuture<ActionResult> toggleQueueMembership(long queueId) {
        if (queueId <= 0) {
            return completedFailure("territory_queue_not_found", "That territory queue no longer exists.");
        }

        TerritoryQueue queue = activeQueues().stream()
                .filter(candidate -> candidate.id() == queueId)
                .findFirst()
                .orElse(null);
        if (queue == null) {
            return completedFailure("territory_queue_not_found", "That territory queue no longer exists.");
        }

        PendingMembershipMutation existing = pendingMembershipMutations.get(queueId);
        if (existing != null) {
            return existing.result();
        }

        String playerUuid = availabilityContext.playerUuid();
        if (playerUuid == null || playerUuid.isBlank()) {
            return completedFailure(
                    "player_identity_unavailable", "Your war queue identity is unavailable; try again shortly.");
        }
        if (playerUuid.equalsIgnoreCase(queue.queuedBy())) {
            return CompletableFuture.completedFuture(
                    new ActionResult(true, null, "You queued this territory and remain its owner."));
        }

        MembershipAction action = queue.hasParticipant(playerUuid)
                ? MembershipAction.LEAVE
                : MembershipAction.JOIN;
        return startMembershipMutation(queueId, action);
    }

    private CompletableFuture<ActionResult> startMembershipMutation(long queueId, MembershipAction action) {
        PendingMembershipMutation existing = pendingMembershipMutations.get(queueId);
        if (existing != null) {
            return existing.result();
        }

        final CompletableFuture<WarTerritoryQueueFeed> request;
        try {
            request = action == MembershipAction.JOIN ? gateway.join(queueId) : gateway.leave(queueId);
        } catch (RuntimeException exception) {
            ErrorDetails details = errorDetails(exception);
            return completedFailure(details.code(), details.message());
        }

        CompletableFuture<ActionResult> result = new CompletableFuture<>();
        PendingMembershipMutation pending = new PendingMembershipMutation(action, request, result);
        pendingMembershipMutations.put(queueId, pending);
        long requestGeneration = generation;
        request.whenComplete((received, error) ->
                completeMembershipMutation(queueId, requestGeneration, pending, received, error));
        return result;
    }

    public synchronized void reset() {
        resetLocked();
    }

    int pendingObservationCount() {
        synchronized (this) {
            return pendingObservations.size() + (observationInFlight == null ? 0 : 1);
        }
    }

    private synchronized boolean enqueueObservation(PendingObservation pending) {
        if (!isAvailabilityActive()) {
            resetLocked();
            return false;
        }
        Observation observation = pending.observation();
        Instant now = clock.instant();
        cleanupRecentObservations(now);
        String key = observation.dedupeKey();
        Instant observedAt = recentObservations.get(key);
        if (observedAt != null && observedAt.plus(OBSERVATION_DEDUPE_WINDOW).isAfter(now)) {
            return false;
        }
        if (observation.queueDurationSeconds() == null) {
            Instant timedAt = recentObservations.get(observation.timedDedupeKey());
            if (timedAt != null && timedAt.plus(OBSERVATION_DEDUPE_WINDOW).isAfter(now)) {
                return false;
            }
        }
        if (pending.timed()) {
            pendingObservations.removeIf(candidate -> candidate.timed() && candidate.sameQueue(pending));
        }
        if (pendingObservations.size() >= MAX_PENDING_OBSERVATIONS && pending.timed()) {
            PendingObservation displaced = pendingObservations.removeLast();
            SeqClient.LOGGER.warn(
                    "[WarTerritoryQueue] Prioritizing confirmed timer territory='{}'; dropping queued territory='{}'",
                    observation.territory(),
                    displaced.observation().territory());
        } else if (pendingObservations.size() >= MAX_PENDING_OBSERVATIONS) {
            SeqClient.LOGGER.warn(
                    "[WarTerritoryQueue] Observation queue is full; dropping territory='{}' reporter='{}'",
                    observation.territory(),
                    observation.minecraftUsername());
            return false;
        }

        recentObservations.put(key, now);
        trimRecentObservations();
        if (pending.timed()) {
            pendingObservations.addFirst(pending);
            nextObservationAttemptAtMillis = 0L;
        } else {
            pendingObservations.addLast(pending);
        }
        if (active && observationInFlight == null && clock.millis() >= nextObservationAttemptAtMillis) {
            startObservation();
        }
        return true;
    }

    private void startPoll() {
        long requestGeneration = generation;
        final CompletableFuture<WarTerritoryQueueFeed> request;
        try {
            request = gateway.fetch();
        } catch (RuntimeException exception) {
            state = State.OFFLINE;
            lastError = safeMessage(exception);
            nextPollAtMillis = clock.millis() + POLL_INTERVAL.toMillis();
            return;
        }
        pollInFlight = request;
        nextPollAtMillis = clock.millis() + POLL_INTERVAL.toMillis();
        request.whenComplete((received, error) ->
                completePoll(requestGeneration, request, received, error));
    }

    private synchronized void completePoll(
            long requestGeneration,
            CompletableFuture<WarTerritoryQueueFeed> request,
            WarTerritoryQueueFeed received,
            Throwable error) {
        if (pollInFlight == request) {
            pollInFlight = null;
        }
        if (requestGeneration != generation || !active) {
            return;
        }
        if (error != null) {
            ErrorDetails details = errorDetails(error);
            if ("war_availability_required".equals(details.code())) {
                resetLocked();
                return;
            }
            lastError = details.message();
            state = feed.queues().isEmpty() ? State.OFFLINE : State.READY;
            return;
        }
        if (!applyFeed(received)) {
            feed = WarTerritoryQueueFeed.empty();
            state = State.OFFLINE;
            lastError = received == null
                    ? "The backend returned an empty territory queue feed."
                    : "Unsupported territory queue schema " + received.schemaVersion() + ".";
        }
    }

    private void startObservation() {
        PendingObservation pending;
        Observation observation;
        do {
            if (pendingObservations.isEmpty()) {
                return;
            }
            pending = pendingObservations.removeFirst();
            observation = pending.forDispatch(clock.instant());
        } while (observation == null);

        long requestGeneration = generation;
        final CompletableFuture<WarTerritoryQueueFeed> request;
        try {
            request = gateway.observe(observation);
        } catch (RuntimeException exception) {
            lastError = safeMessage(exception);
            scheduleTimedObservationRetry(pending, exception);
            return;
        }
        nextObservationAttemptAtMillis = 0L;
        observationInFlight = request;
        PendingObservation submitted = pending;
        Observation submittedObservation = observation;
        request.whenComplete((received, error) ->
                completeObservation(
                        requestGeneration, request, submitted, submittedObservation, received, error));
    }

    private synchronized void completeObservation(
            long requestGeneration,
            CompletableFuture<WarTerritoryQueueFeed> request,
            PendingObservation pending,
            Observation observation,
            WarTerritoryQueueFeed received,
            Throwable error) {
        if (observationInFlight == request) {
            observationInFlight = null;
        }
        if (requestGeneration != generation || !active) {
            return;
        }
        if (error != null) {
            ErrorDetails details = errorDetails(error);
            if ("war_availability_required".equals(details.code())) {
                resetLocked();
                return;
            }
            lastError = details.message();
            boolean retrying = scheduleTimedObservationRetry(pending, error);
            SeqClient.LOGGER.warn(
                    "[WarTerritoryQueue] Observation failed territory='{}' reporter='{}' code='{}' retrying={} message='{}'",
                    observation.territory(),
                    observation.minecraftUsername(),
                    details.code(),
                    retrying,
                    details.message());
            return;
        }
        nextObservationAttemptAtMillis = 0L;
        if (!applyFeed(received)) {
            lastError = "The backend returned an unsupported territory queue feed.";
        }
    }

    private boolean scheduleTimedObservationRetry(PendingObservation pending, Throwable error) {
        if (!pending.timed()
                || pending.retryCount() >= MAX_TIMED_OBSERVATION_RETRIES
                || !isRetriableObservationFailure(error)) {
            return false;
        }
        Instant retryAt = clock.instant().plus(POLL_INTERVAL);
        if (!pending.queueExpiresAt().isAfter(retryAt)) {
            return false;
        }
        boolean newerTimedObservation = pendingObservations.stream()
                .anyMatch(candidate -> candidate.timed() && candidate.sameQueue(pending));
        if (newerTimedObservation) {
            return false;
        }
        if (pendingObservations.size() >= MAX_PENDING_OBSERVATIONS) {
            pendingObservations.removeLast();
        }
        pendingObservations.addFirst(pending.nextRetry());
        nextObservationAttemptAtMillis = clock.millis() + POLL_INTERVAL.toMillis();
        return true;
    }

    private synchronized void completeMembershipMutation(
            long queueId,
            long requestGeneration,
            PendingMembershipMutation pending,
            WarTerritoryQueueFeed received,
            Throwable error) {
        if (pendingMembershipMutations.get(queueId) != pending) {
            return;
        }
        pendingMembershipMutations.remove(queueId);
        if (requestGeneration != generation) {
            pending.result().complete(new ActionResult(
                    false, "queue_context_changed", "The war queue context changed before the update completed."));
            return;
        }
        if (error != null) {
            ErrorDetails details = errorDetails(error);
            if ("territory_queue_not_found".equals(details.code())
                    || "territory_queue_full".equals(details.code())) {
                nextPollAtMillis = 0L;
            }
            if ("war_availability_required".equals(details.code())) {
                resetLocked();
            }
            pending.result().complete(new ActionResult(false, details.code(), details.message()));
            return;
        }
        if (!applyFeed(received)) {
            pending.result().complete(new ActionResult(
                    false, "invalid_queue_feed", "The backend returned an unsupported territory queue feed."));
            return;
        }
        String message = pending.action() == MembershipAction.JOIN
                ? "Joined territory queue."
                : "Left territory queue.";
        if (pending.action() == MembershipAction.JOIN) {
            availabilityContext.refreshAvailability();
        }
        pending.result().complete(new ActionResult(true, null, message));
    }

    private synchronized void completeViewerRefresh(
            long requestGeneration,
            CompletableFuture<WarTerritoryQueueFeed> request,
            CompletableFuture<ActionResult> result,
            WarTerritoryQueueFeed received,
            Throwable error) {
        if (viewerFetchInFlight == request) {
            viewerFetchInFlight = null;
        }
        if (viewerRefreshInFlight == result) {
            viewerRefreshInFlight = null;
        }
        if (requestGeneration != generation) {
            result.complete(new ActionResult(false, "queue_context_changed", "The war queue context changed."));
            return;
        }
        if (error != null) {
            ErrorDetails details = errorDetails(error);
            lastError = details.message();
            state = feed.queues().isEmpty() ? State.OFFLINE : State.READY;
            result.complete(new ActionResult(false, details.code(), details.message()));
            return;
        }
        if (!applyFeed(received)) {
            result.complete(new ActionResult(
                    false, "invalid_queue_feed", "The backend returned an unsupported territory queue feed."));
            return;
        }
        result.complete(new ActionResult(true, null, ""));
    }

    private boolean applyFeed(WarTerritoryQueueFeed received) {
        if (received == null || !received.isSupported()) {
            return false;
        }
        WarTerritoryQueueFeed current = feed;
        if (shouldApplyFeed(received, current)) {
            feed = received;
            if (received.serverTime() != null) {
                serverOffsetMillis = Duration.between(clock.instant(), received.serverTime()).toMillis();
            }
            reconcileRecentQueueSnapshots(received, adjustedServerNow());
        }
        state = State.READY;
        lastError = null;
        nextPollAtMillis = clock.millis() + POLL_INTERVAL.toMillis();
        return true;
    }

    private static boolean shouldApplyFeed(WarTerritoryQueueFeed received, WarTerritoryQueueFeed current) {
        if (current == null || received.revision() > current.revision()) {
            return true;
        }
        if (received.revision() < current.revision()) {
            return false;
        }
        Instant receivedAt = received.serverTime();
        Instant currentAt = current.serverTime();
        return receivedAt == null || currentAt == null || !receivedAt.isBefore(currentAt);
    }

    private synchronized void notifyMissedWar() {
        if (!active) {
            return;
        }

        Instant now = adjustedServerNow();
        cleanupRecentQueueSnapshots(now);
        List<TerritoryQueue> candidates = recentQueueSnapshots.values().stream()
                .filter(queue -> !notifiedMissedWars.containsKey(queue.id()))
                .filter(queue -> isWithinMissedWarWindow(queue.expiresAt(), now))
                .sorted(Comparator.comparing(TerritoryQueue::expiresAt).thenComparingLong(TerritoryQueue::id))
                .toList();
        if (candidates.isEmpty()) {
            SeqClient.LOGGER.debug("[WarTerritoryQueue] Missed-war message had no recent queue candidate");
            return;
        }

        TerritoryQueue matched = candidates.size() == 1 ? candidates.getFirst() : uniqueLocalCandidate(candidates);
        if (matched == null) {
            SeqClient.LOGGER.debug(
                    "[WarTerritoryQueue] Missed-war message was ambiguous across {} recent queues",
                    candidates.size());
            return;
        }

        recentQueueSnapshots.remove(matched.id());
        notifiedMissedWars.put(matched.id(), now);
        trimRecentQueueState();
        try {
            if (missedWarMessagesEnabled.getAsBoolean()) {
                missedWarNotifier.accept(formatMissedWarBlame(matched));
            }
        } catch (RuntimeException exception) {
            SeqClient.LOGGER.warn(
                    "[WarTerritoryQueue] Could not display missed-war notification for territory='{}'",
                    matched.territory(),
                    exception);
        }
    }

    private TerritoryQueue uniqueLocalCandidate(List<TerritoryQueue> candidates) {
        String localPlayerUuid;
        try {
            localPlayerUuid = availabilityContext.playerUuid();
        } catch (RuntimeException ignored) {
            return null;
        }
        List<TerritoryQueue> localCandidates = candidates.stream()
                .filter(queue -> belongsToPlayer(queue, localPlayerUuid))
                .toList();
        return localCandidates.size() == 1 ? localCandidates.getFirst() : null;
    }

    private static boolean belongsToPlayer(TerritoryQueue queue, String playerUuid) {
        if (queue == null || playerUuid == null || playerUuid.isBlank()) {
            return false;
        }
        return playerUuid.equalsIgnoreCase(queue.queuedBy()) || queue.hasParticipant(playerUuid);
    }

    static boolean isWithinMissedWarWindow(Instant expiresAt, Instant now) {
        if (expiresAt == null || now == null) {
            return false;
        }
        Instant earliest = expiresAt.plus(MISSED_WAR_MIN_DELAY);
        Instant latest = expiresAt.plus(MISSED_WAR_MAX_DELAY);
        return !now.isBefore(earliest) && !now.isAfter(latest);
    }

    static String formatMissedWarBlame(TerritoryQueue queue) {
        String territory = queue == null ? null : normalizeOptional(queue.territory());
        if (territory == null) {
            territory = "Unknown territory";
        }
        List<String> usernames = blamedUsernames(queue);
        String players = naturalLanguageList(usernames);
        int variant = queue == null
                ? 0
                : (int) Math.floorMod(queue.id(), (long) MISSED_WAR_BLAME_VARIANT_COUNT);
        return switch (variant) {
            case 1 -> territory + " called. " + players + " sent it straight to voicemail.";
            case 2 -> "The war at " + territory + " began exactly as " + players + " planned: without them.";
            case 3 -> players + " chose peace. Unfortunately, they queued a war at " + territory + ".";
            case 4 -> "Breaking: "
                    + players
                    + " successfully avoided the war they queued for at "
                    + territory
                    + ".";
            case 5 -> "Nobody entered "
                    + territory
                    + ". "
                    + players
                    + (usernames.size() == 1 ? " was" : " were")
                    + " last seen fighting the login button.";
            default -> territory
                    + " started with nobody inside. Blame "
                    + players
                    + ", apparently they queued for moral support.";
        };
    }

    static List<String> blamedUsernames(TerritoryQueue queue) {
        if (queue == null) {
            return List.of("Unknown");
        }

        LinkedHashMap<String, String> names = new LinkedHashMap<>();
        List<Participant> participants = queue.participants().stream()
                .sorted(Comparator.comparingInt(Participant::position))
                .toList();
        boolean hasOwnerPosition = participants.stream().anyMatch(participant -> participant.position() == 0);
        if (queue.provisional()) {
            addBlameName(names, null);
        } else if (!hasOwnerPosition) {
            addBlameName(names, queue.minecraftUsername());
        }

        for (Participant participant : participants) {
            String username = participant.minecraftUsername();
            if (username == null && participant.position() == 0) {
                username = queue.minecraftUsername();
            }
            addBlameName(names, username);
        }
        if (names.isEmpty()) {
            addBlameName(names, queue.minecraftUsername());
        }
        return List.copyOf(names.values());
    }

    private static void addBlameName(Map<String, String> names, String username) {
        String displayName = normalizeOptional(username);
        if (displayName == null) {
            displayName = "Unknown";
        }
        names.putIfAbsent(displayName.toLowerCase(Locale.ROOT), displayName);
    }

    private static String naturalLanguageList(List<String> values) {
        if (values.size() == 1) {
            return values.getFirst();
        }
        if (values.size() == 2) {
            return values.get(0) + " and " + values.get(1);
        }
        return String.join(", ", values.subList(0, values.size() - 1))
                + ", and "
                + values.getLast();
    }

    private void reconcileRecentQueueSnapshots(WarTerritoryQueueFeed received, Instant now) {
        Set<Long> liveQueueIds = new java.util.HashSet<>();
        for (TerritoryQueue queue : received.queues()) {
            if (isCacheableQueue(queue)) {
                liveQueueIds.add(queue.id());
            }
        }

        Iterator<Map.Entry<Long, TerritoryQueue>> iterator = recentQueueSnapshots.entrySet().iterator();
        while (iterator.hasNext()) {
            TerritoryQueue cached = iterator.next().getValue();
            boolean missing = !liveQueueIds.contains(cached.id());
            if (missing
                    && (now.isBefore(cached.expiresAt())
                            || wasReplacedBeforeExpiry(cached, received.queues()))) {
                iterator.remove();
            }
        }
        for (TerritoryQueue queue : received.queues()) {
            if (isCacheableQueue(queue) && !notifiedMissedWars.containsKey(queue.id())) {
                recentQueueSnapshots.put(queue.id(), queue);
            }
        }
        cleanupRecentQueueSnapshots(now);
        trimRecentQueueState();
    }

    private static boolean wasReplacedBeforeExpiry(TerritoryQueue cached, List<TerritoryQueue> liveQueues) {
        return liveQueues.stream()
                .filter(WarTerritoryQueueManager::isCacheableQueue)
                .filter(queue -> queue.id() != cached.id())
                .filter(queue -> queue.territory().equalsIgnoreCase(cached.territory()))
                .anyMatch(queue -> queue.queuedAt() != null && queue.queuedAt().isBefore(cached.expiresAt()));
    }

    private static boolean isCacheableQueue(TerritoryQueue queue) {
        return queue != null && queue.id() > 0 && !queue.territory().isBlank() && queue.expiresAt() != null;
    }

    private void cleanupRecentQueueSnapshots(Instant now) {
        recentQueueSnapshots.entrySet().removeIf(entry -> {
            Instant expiresAt = entry.getValue().expiresAt();
            return expiresAt == null || !expiresAt.plus(MISSED_WAR_CACHE_RETENTION).isAfter(now);
        });
        notifiedMissedWars.entrySet().removeIf(
                entry -> !entry.getValue().plus(MISSED_WAR_CACHE_RETENTION).isAfter(now));
    }

    private void trimRecentQueueState() {
        trimOldestEntries(recentQueueSnapshots, MAX_RECENT_QUEUE_SNAPSHOTS);
        trimOldestEntries(notifiedMissedWars, MAX_RECENT_QUEUE_SNAPSHOTS);
    }

    private static <K, V> void trimOldestEntries(LinkedHashMap<K, V> entries, int maximumSize) {
        while (entries.size() > maximumSize) {
            Iterator<K> iterator = entries.keySet().iterator();
            iterator.next();
            iterator.remove();
        }
    }

    private Instant adjustedServerNow() {
        return clock.instant().plusMillis(serverOffsetMillis);
    }

    private void resetLocked() {
        if (!active
                && state == State.INACTIVE
                && feed.queues().isEmpty()
                && pendingObservations.isEmpty()
                && recentQueueSnapshots.isEmpty()
                && notifiedMissedWars.isEmpty()
                && pendingMembershipMutations.isEmpty()
                && pollInFlight == null
                && observationInFlight == null
                && viewerFetchInFlight == null) {
            return;
        }
        generation++;
        active = false;
        state = State.INACTIVE;
        feed = WarTerritoryQueueFeed.empty();
        lastError = null;
        serverOffsetMillis = 0L;
        nextPollAtMillis = 0L;
        nextObservationAttemptAtMillis = 0L;
        pendingObservations.clear();
        recentObservations.clear();
        recentQueueSnapshots.clear();
        notifiedMissedWars.clear();
        if (pollInFlight != null) {
            pollInFlight.cancel(true);
            pollInFlight = null;
        }
        if (observationInFlight != null) {
            observationInFlight.cancel(true);
            observationInFlight = null;
        }
        if (viewerFetchInFlight != null) {
            viewerFetchInFlight.cancel(true);
            viewerFetchInFlight = null;
        }
        if (viewerRefreshInFlight != null) {
            viewerRefreshInFlight.complete(
                    new ActionResult(false, "queue_context_changed", "The war queue context changed."));
            viewerRefreshInFlight = null;
        }
        for (PendingMembershipMutation pending : new ArrayList<>(pendingMembershipMutations.values())) {
            pending.result().complete(new ActionResult(
                    false, "queue_context_changed", "The war queue context changed before the update completed."));
            pending.request().cancel(true);
        }
        pendingMembershipMutations.clear();
    }

    private void suspendLivePollingLocked() {
        active = false;
        state = feed.queues().isEmpty() ? State.INACTIVE : State.READY;
        nextPollAtMillis = 0L;
        nextObservationAttemptAtMillis = 0L;
        pendingObservations.clear();
        recentObservations.clear();
        recentQueueSnapshots.clear();
        notifiedMissedWars.clear();
        if (pollInFlight != null) {
            pollInFlight.cancel(true);
            pollInFlight = null;
        }
        if (observationInFlight != null) {
            observationInFlight.cancel(true);
            observationInFlight = null;
        }
    }

    private void cleanupRecentObservations(Instant now) {
        Iterator<Map.Entry<String, Instant>> iterator = recentObservations.entrySet().iterator();
        while (iterator.hasNext()) {
            Instant observedAt = iterator.next().getValue();
            if (!observedAt.plus(OBSERVATION_DEDUPE_WINDOW).isAfter(now)) {
                iterator.remove();
            }
        }
    }

    private void trimRecentObservations() {
        while (recentObservations.size() > MAX_RECENT_OBSERVATIONS) {
            Iterator<String> iterator = recentObservations.keySet().iterator();
            iterator.next();
            iterator.remove();
        }
    }

    private boolean isAvailabilityActive() {
        try {
            return availabilityContext.available();
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private String playerUuid() {
        try {
            String playerUuid = availabilityContext.playerUuid();
            return playerUuid == null || playerUuid.isBlank() ? null : playerUuid;
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static CompletableFuture<ActionResult> completedFailure(String code, String message) {
        return CompletableFuture.completedFuture(new ActionResult(false, code, message));
    }

    private static ErrorDetails errorDetails(Throwable throwable) {
        Throwable cause = unwrap(throwable);
        if (cause instanceof ApiClient.ApiException apiException) {
            String code = "territory_queue_request_failed";
            String message = "Territory queue request failed.";
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
                message = safeMessage(apiException);
            }
            return new ErrorDetails(code, message);
        }
        return new ErrorDetails("territory_queue_request_failed", safeMessage(cause));
    }

    private static Throwable unwrap(Throwable throwable) {
        Throwable current = throwable;
        while (current != null
                && (current instanceof CompletionException || current instanceof ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current == null ? new IllegalStateException("Unknown territory queue failure") : current;
    }

    private static boolean isRetriableObservationFailure(Throwable throwable) {
        Throwable cause = unwrap(throwable);
        if (cause instanceof ApiClient.ApiException apiException) {
            int status = apiException.getStatusCode();
            return status == 408 || status == 429 || status >= 500;
        }
        return true;
    }

    private static String safeMessage(Throwable throwable) {
        if (throwable == null || throwable.getMessage() == null || throwable.getMessage().isBlank()) {
            return "Territory queue request failed.";
        }
        return throwable.getMessage();
    }

    private static String normalizeRequired(String value, String label) {
        String normalized = normalizeOptional(value);
        if (normalized == null) {
            throw new IllegalArgumentException(label + " is required.");
        }
        return normalized;
    }

    private static String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private static long parseOptionalLong(String value) {
        return value == null ? 0L : Long.parseLong(value);
    }

    private static String territoryKey(String territory) {
        return territory.trim().toLowerCase(Locale.ROOT);
    }

    private enum MembershipAction {
        JOIN,
        LEAVE
    }

    private record PendingMembershipMutation(
            MembershipAction action,
            CompletableFuture<WarTerritoryQueueFeed> request,
            CompletableFuture<ActionResult> result) {}

    private record PendingObservation(Observation observation, Instant queueExpiresAt, int retryCount) {
        static PendingObservation untimed(Observation observation) {
            return new PendingObservation(observation, null, 0);
        }

        boolean timed() {
            return queueExpiresAt != null;
        }

        boolean sameQueue(PendingObservation other) {
            return other != null
                    && observation.territory().equalsIgnoreCase(other.observation.territory())
                    && (observation.provisional()
                            || other.observation.provisional()
                            || observation.minecraftUsername().equalsIgnoreCase(other.observation.minecraftUsername()));
        }

        Observation forDispatch(Instant now) {
            if (!timed()) {
                return observation;
            }
            long remainingMillis = Duration.between(now, queueExpiresAt).toMillis();
            if (remainingMillis <= 0L) {
                return null;
            }
            int remainingSeconds = (int) Math.max(1L, Math.min(3_600L, (remainingMillis + 999L) / 1_000L));
            return new Observation(
                    observation.minecraftUsername(),
                    observation.nickname(),
                    observation.territory(),
                    observation.defenseRating(),
                    remainingSeconds);
        }

        PendingObservation nextRetry() {
            return new PendingObservation(observation, queueExpiresAt, retryCount + 1);
        }
    }

    private record ErrorDetails(String code, String message) {}

    private record ApiGateway(ApiClient api) implements Gateway {
        @Override
        public CompletableFuture<WarTerritoryQueueFeed> fetch() {
            return api.getWarTerritoryQueues();
        }

        @Override
        public CompletableFuture<WarTerritoryQueueFeed> observe(Observation observation) {
            if (observation.provisional()) {
                return api.submitWarTerritoryQueueConfirmation(
                        observation.territory(), observation.queueDurationSeconds());
            }
            return api.submitWarTerritoryQueueObservation(
                    observation.minecraftUsername(),
                    observation.nickname(),
                    observation.territory(),
                    observation.defenseRating(),
                    observation.queueDurationSeconds());
        }

        @Override
        public CompletableFuture<WarTerritoryQueueFeed> join(long queueId) {
            return api.joinWarTerritoryQueue(queueId);
        }

        @Override
        public CompletableFuture<WarTerritoryQueueFeed> leave(long queueId) {
            return api.leaveWarTerritoryQueue(queueId);
        }
    }

    private static final class RuntimeAvailabilityContext implements AvailabilityContext {
        @Override
        public boolean available() {
            WarPlannerManager manager = SeqClient.getWarPlannerManager();
            if (manager == null) {
                return false;
            }
            Duration remaining = manager.ownAvailabilityRemaining();
            return !remaining.isZero() && !remaining.isNegative();
        }

        @Override
        public String playerUuid() {
            WarPlannerManager manager = SeqClient.getWarPlannerManager();
            return manager == null ? null : manager.playerUuid();
        }

        @Override
        public void refreshAvailability() {
            WarPlannerManager manager = SeqClient.getWarPlannerManager();
            if (manager != null) {
                manager.refreshNow();
            }
        }
    }
}
