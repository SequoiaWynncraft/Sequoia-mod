package com.seqwawa.seq.managers;

import com.seqwawa.seq.model.GuildWarQueueSubmission;
import com.seqwawa.seq.model.WarStatusUpdate;
import com.seqwawa.seq.model.WarTowerUpdate;
import com.seqwawa.seq.model.WynnClassType;
import com.wynntils.core.WynntilsMod;
import com.wynntils.core.components.Models;
import com.wynntils.models.character.event.CharacterDeathEvent;
import com.wynntils.models.war.type.WarBattleInfo;
import com.wynntils.models.war.type.WarTowerState;
import com.wynntils.utils.type.RangedValue;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.bus.api.SubscribeEvent;
import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.model.GuildWarSubmission;
import com.seqwawa.seq.network.ConnectionManager;
import com.seqwawa.seq.utils.PacketTextNormalizer;
import com.seqwawa.seq.utils.WynnClassCache;

/**
 * Tracks active guild-war lifecycle via Wynntils, reads live tower metrics from
 * vanilla boss-event packets, and relays one structured legacy summary when the
 * war completes, disappears, or the local player dies.
 */
public final class GuildWarTracker implements GuildWarTrackerHandle {
    private static final double TRACKING_RADIUS_SQ = 120 * 120;
    private static final Pattern VALID_USERNAME = Pattern.compile("^[a-zA-Z0-9_]{3,16}$");
    private static final Pattern TERRITORY_CAPTURED = Pattern.compile("(?i)Territory\\s+Captured");
    private static final Pattern CAPTURED_TERRITORY = Pattern.compile("(?i)Captured\\s+\"([^\"]+)\"");
    private static final Pattern SEASON_RATING = Pattern.compile("(?i)\\+\\s*(\\d+)\\s+Season(?:al)?\\s+Rating");
    private static final Pattern QUEUE_NAME = Pattern.compile("Attacking: (.+)");
    private static final Pattern QUEUE_DEFENSE = Pattern.compile(
            "Territory Defences: (Very Low|Low|Medium|High|Very High)");
    private static final Set<String> DEFENSE_LEVELS = Set.of("Very Low", "Low", "Medium", "High", "Very High");
    private static final long QUEUE_ATTEMPT_TIMEOUT_MS = 15_000L;
    static final long STATUS_HEARTBEAT_MS = 7_000L;
    static final long TOWER_HEARTBEAT_MS = 4_000L;
    private static final long LIVE_SEND_RETRY_MS = 1_000L;

    private final WarInfoProvider warInfoProvider;
    private final PlayerContext playerContext;
    private final SubmissionPublisher submissionPublisher;
    private final BooleanSupplier trackingEnabled;
    private final LongSupplier clock;

    private WarContext activeContext;
    private String lastProcessedBattleId;
    private int lastProcessedStateHash;
    private boolean wynnDeathListenerRegistered;
    private PendingQueueAttempt pendingQueueAttempt;
    private PresenceKey observedPresenceKey;
    private boolean warModeObserved;
    private boolean removalPending;
    private WynnClassType lastObservedClass;
    private long nextStatusHeartbeatAtMillis;
    private long nextStatusAttemptAtMillis;
    private String observedTowerTerritory;
    private String observedTowerBattleId;
    private long nextTowerHeartbeatAtMillis;
    private boolean publisherWasReady;

    public GuildWarTracker() {
        this(
                new RuntimeWarInfoProvider(),
                new RuntimePlayerContext(),
                new SubmissionPublisher() {
                    @Override
                    public boolean publishWar(GuildWarSubmission submission) {
                        return ConnectionManager.getInstance().sendGuildWarSubmission(submission);
                    }

                    @Override
                    public boolean publishQueue(GuildWarQueueSubmission submission) {
                        return ConnectionManager.getInstance().sendGuildWarQueue(submission);
                    }

                    @Override
                    public boolean liveTelemetryReady() {
                        return ConnectionManager.isLiveWarTelemetryReady();
                    }

                    @Override
                    public boolean publishWarStatus(WarStatusUpdate update) {
                        return ConnectionManager.getInstance().sendWarStatus(update);
                    }

                    @Override
                    public boolean publishWarTowerUpdate(WarTowerUpdate update) {
                        return ConnectionManager.getInstance().sendWarTowerUpdate(update);
                    }
                },
                () -> SeqClient.getTrackGuildWarsSetting() == null
                        || SeqClient.getTrackGuildWarsSetting().getValue(),
                System::currentTimeMillis,
                false);
    }

    GuildWarTracker(
            WarInfoProvider warInfoProvider,
            PlayerContext playerContext,
            SubmissionPublisher submissionPublisher,
            BooleanSupplier trackingEnabled,
            LongSupplier clock,
            boolean registerDeathListener) {
        this.warInfoProvider = Objects.requireNonNull(warInfoProvider, "warInfoProvider");
        this.playerContext = Objects.requireNonNull(playerContext, "playerContext");
        this.submissionPublisher = Objects.requireNonNull(submissionPublisher, "submissionPublisher");
        this.trackingEnabled = Objects.requireNonNull(trackingEnabled, "trackingEnabled");
        this.clock = Objects.requireNonNull(clock, "clock");
        if (registerDeathListener) {
            ensureDeathListenerRegistered();
        }
    }

    public void tick() {
        ensureDeathListenerRegistered();
        if (!trackingEnabled.getAsBoolean()) {
            trackDisabledTelemetry();
            return;
        }
        WarBattleInfo info = warInfoProvider.getCurrentWar();
        trackWarState(info);
        trackLiveTelemetry(info);
    }

    public void onSystemChat(Component message) {
        if (message == null) {
            return;
        }

        String cleaned = PacketTextNormalizer.normalizeForParsing(message.getString());
        if (cleaned.isEmpty()) {
            return;
        }

        attemptQueueConfirmation(cleaned);
        attemptTerritoryCapture(cleaned);
    }

    private void attemptQueueConfirmation(String cleaned) {
        if (pendingQueueAttempt == null) {
            return;
        }
        if (!trackingEnabled.getAsBoolean()) {
            pendingQueueAttempt = null;
            return;
        }
        long now = clock.getAsLong();
        if (pendingQueueAttempt.expiresAtEpochMs() < now) {
            pendingQueueAttempt = null;
            return;
        }
        WarTerritoryQueueManager.QueueConfirmation confirmation =
                WarTerritoryQueueManager.parseQueueConfirmation(cleaned).orElse(null);
        if (confirmation == null
                || !pendingQueueAttempt.territory().equalsIgnoreCase(confirmation.territory())) {
            return;
        }

        PendingQueueAttempt confirmed = pendingQueueAttempt;
        pendingQueueAttempt = null;
        int queueMinutes = Math.max(1, (confirmation.durationSeconds() + 59) / 60);
        submitQueue(new QueueAttemptInfo(confirmed.territory(), confirmed.rating(), queueMinutes));
    }

    private void attemptTerritoryCapture(String cleaned) {
        if (!TERRITORY_CAPTURED.matcher(cleaned).find()) {
            return;
        }

        if (!trackingEnabled.getAsBoolean()) {
            SeqClient.LOGGER.warn("[GuildWarTracker] Ignoring completion chat because track_guild_wars is disabled");
            return;
        }

        Integer sr = parseSeasonRating(cleaned);
        if (sr == null) {
            return;
        }

        String territory = parseCapturedTerritory(cleaned);
        if (activeContext == null) {
            SeqClient.LOGGER.warn(
                    "[GuildWarTracker] Ignoring completion chat territory='{}' sr={} because no active war context exists",
                    territory != null ? territory : "unknown",
                    sr);
            return;
        }
        if (activeContext.submissionSent) {
            return;
        }
        if (territory != null && !territoryMatches(activeContext, territory)) {
            SeqClient.LOGGER.warn(
                    "[GuildWarTracker] Ignoring completion chat territory='{}' because active war is '{}'",
                    territory,
                    activeContext.info != null ? activeContext.info.getTerritory() : "unknown");
            return;
        }

        activeContext.seasonRating = sr;
        activeContext.completedFromChat = true;
        activeContext.completedAtEpochMs = clock.getAsLong();
        SeqClient.LOGGER.info(
                "[GuildWarTracker] Captured completion chat territory='{}' sr={}",
                activeContext.info != null ? activeContext.info.getTerritory() : territory,
                sr);
        if (activeContext.pendingSubmission) {
            requestSubmission(activeContext.info, activeContext, false);
        }
    }

    @SubscribeEvent
    public void onCharacterDeathEvent(CharacterDeathEvent event) {
        onCharacterDeath();
    }

    void onCharacterDeath() {
        if (!trackingEnabled.getAsBoolean() || activeContext == null || activeContext.submissionSent) {
            return;
        }
        requestSubmission(activeContext.info, activeContext, true);
    }

    public void reset() {
        warInfoProvider.resetTowerMetrics();
        clearActiveWarContext();
        pendingQueueAttempt = null;
        observedPresenceKey = null;
        warModeObserved = false;
        removalPending = false;
        lastObservedClass = null;
        nextStatusHeartbeatAtMillis = 0L;
        nextStatusAttemptAtMillis = 0L;
        observedTowerTerritory = null;
        observedTowerBattleId = null;
        nextTowerHeartbeatAtMillis = 0L;
        publisherWasReady = false;
    }

    private void clearActiveWarContext() {
        activeContext = null;
        lastProcessedBattleId = null;
        lastProcessedStateHash = 0;
    }

    @Override
    public void onSlotClick(String screenName, ItemStack item) {
        if (!trackingEnabled.getAsBoolean()) {
            return;
        }
        Matcher mName = QUEUE_NAME.matcher(screenName);
        if (!mName.find()) {
            return;
        }
        String itemName = PacketTextNormalizer.normalizeForParsing(item.getHoverName().getString());
        if (!itemName.startsWith("Attack")) {
            return;
        }
        String territoryName = mName.group(1).trim();
        String defense = null;

        for (Component component : item.getTooltipLines(Item.TooltipContext.EMPTY, Minecraft.getInstance().player, TooltipFlag.NORMAL)) {
            String lineContent = PacketTextNormalizer.normalizeForParsing(component.getString());
            Matcher mDefense = QUEUE_DEFENSE.matcher(lineContent);
            if (mDefense.find()) {
                defense = mDefense.group(1);
            }
        }

        if (defense == null) {
            SeqClient.LOGGER.warn("[GuildWarTracker] Failed to parse queue item tooltip, territory='{}'", territoryName);
            return;
        }

        if (!DEFENSE_LEVELS.contains(defense)) {
            SeqClient.LOGGER.warn("[GuildWarTracker] Invalid queue defense, territory='{}' defense='{}'", territoryName, defense);
            return;
        }

        rememberQueueAttempt(territoryName, defense);
    }

    void rememberQueueAttempt(String territory, String defense) {
        String normalizedTerritory = trimToNull(territory);
        String normalizedDefense = trimToNull(defense);
        if (normalizedTerritory == null || !DEFENSE_LEVELS.contains(normalizedDefense)) {
            return;
        }
        long now = clock.getAsLong();
        pendingQueueAttempt = new PendingQueueAttempt(
                normalizedTerritory, normalizedDefense, now + QUEUE_ATTEMPT_TIMEOUT_MS);
    }

    private void trackWarState(WarBattleInfo info) {
        if (info != null) {
            String battleId = buildBattleId(info);
            int stateHash = hashState(info.getCurrentState());
            if (activeContext != null
                    && battleId.equals(lastProcessedBattleId)
                    && stateHash == lastProcessedStateHash) {
                return;
            }

            lastProcessedBattleId = battleId;
            lastProcessedStateHash = stateHash;

            if (activeContext == null || !battleId.equals(activeContext.id)) {
                activeContext =
                        new WarContext(battleId, info, determineStartEpoch(info), collectCurrentWarrers());
                SeqClient.LOGGER.info(
                        "[GuildWarTracker] Tracking war territory='{}' warrers={}",
                        activeContext.info.getTerritory(),
                        activeContext.warrers);
            } else {
                activeContext.info = info;
            }

            activeContext.lastKnownState = info.getCurrentState();
            if (!activeContext.submissionSent && isTowerDestroyed(activeContext.lastKnownState)) {
                if (activeContext.completedAtEpochMs == null) {
                    activeContext.completedAtEpochMs = completionEpoch(activeContext.lastKnownState);
                }
                requestSubmission(info, activeContext, false);
            }
            return;
        }

        if (activeContext != null) {
            if (!activeContext.submissionSent) {
                requestSubmission(activeContext.info, activeContext, true);
            }
            clearActiveWarContext();
            pendingQueueAttempt = null;
        }
    }

    private void trackLiveTelemetry(WarBattleInfo info) {
        long now = clock.getAsLong();
        boolean ready = submissionPublisher.liveTelemetryReady();
        boolean reconnected = ready && !publisherWasReady;
        publisherWasReady = ready;
        String battleId = info == null ? null : buildBattleId(info);

        boolean warModeActive = playerContext.warModeActive() || info != null;
        WynnClassType classType = playerContext.localClassType();
        if (classType != null) {
            lastObservedClass = classType;
        }

        if (!warModeActive) {
            stopTowerTelemetry();
            if (warModeObserved || reconnected) {
                removalPending = true;
                observedPresenceKey = null;
                nextStatusHeartbeatAtMillis = 0L;
                nextStatusAttemptAtMillis = 0L;
            }
            warModeObserved = false;
            if (removalPending) {
                attemptRemoval(ready, now);
            }
            return;
        }

        warModeObserved = true;
        removalPending = false;

        WarStatusUpdate statusUpdate = statusUpdate(info, classType);
        if (statusUpdate != null) {
            PresenceKey presenceKey = PresenceKey.from(statusUpdate, battleId);
            if (!presenceKey.equals(observedPresenceKey)) {
                observedPresenceKey = presenceKey;
                nextStatusHeartbeatAtMillis = 0L;
                nextStatusAttemptAtMillis = 0L;
            }
            if (reconnected) {
                nextStatusHeartbeatAtMillis = 0L;
                nextStatusAttemptAtMillis = 0L;
            }
            if (ready
                    && now >= nextStatusHeartbeatAtMillis
                    && now >= nextStatusAttemptAtMillis) {
                if (publishWarStatus(statusUpdate)) {
                    nextStatusHeartbeatAtMillis = now + STATUS_HEARTBEAT_MS;
                    nextStatusAttemptAtMillis = 0L;
                } else {
                    nextStatusAttemptAtMillis = now + LIVE_SEND_RETRY_MS;
                }
            }
        }

        trackTowerTelemetry(info, battleId, ready, reconnected, now);
    }

    private WarStatusUpdate statusUpdate(WarBattleInfo info, WynnClassType classType) {
        if (classType == null) {
            return null;
        }
        if (info != null) {
            String territory = trimToNull(info.getTerritory());
            return territory == null ? null : WarStatusUpdate.war(classType, territory);
        }
        WorldPosition position = playerContext.worldPosition();
        return position == null ? null : WarStatusUpdate.world(classType, position.x(), position.z());
    }

    private void attemptRemoval(boolean ready, long now) {
        if (!ready || now < nextStatusAttemptAtMillis) {
            return;
        }
        WynnClassType classType = playerContext.localClassType();
        if (classType == null) {
            classType = lastObservedClass;
        }
        WarStatusUpdate removal = classType == null ? WarStatusUpdate.remove() : WarStatusUpdate.remove(classType);
        if (publishWarStatus(removal)) {
            removalPending = false;
            nextStatusHeartbeatAtMillis = 0L;
            nextStatusAttemptAtMillis = 0L;
        } else {
            nextStatusAttemptAtMillis = now + LIVE_SEND_RETRY_MS;
        }
    }

    private void trackDisabledTelemetry() {
        long now = clock.getAsLong();
        boolean ready = submissionPublisher.liveTelemetryReady();
        boolean reconnected = ready && !publisherWasReady;
        publisherWasReady = ready;

        if (warModeObserved || observedPresenceKey != null || reconnected) {
            removalPending = true;
            nextStatusAttemptAtMillis = 0L;
        }
        warModeObserved = false;
        observedPresenceKey = null;
        nextStatusHeartbeatAtMillis = 0L;
        stopTowerTelemetry();
        clearActiveWarContext();
        pendingQueueAttempt = null;

        if (removalPending) {
            attemptRemoval(ready, now);
        }
    }

    private void trackTowerTelemetry(
            WarBattleInfo info, String battleId, boolean ready, boolean reconnected, long now) {
        String territory = info == null ? null : trimToNull(info.getTerritory());
        if (territory == null) {
            stopTowerTelemetry();
            return;
        }
        if (!territory.equalsIgnoreCase(observedTowerTerritory)
                || !Objects.equals(battleId, observedTowerBattleId)) {
            observedTowerTerritory = territory;
            observedTowerBattleId = battleId;
            nextTowerHeartbeatAtMillis = 0L;
        }
        if (reconnected) {
            nextTowerHeartbeatAtMillis = 0L;
        }
        if (!ready || now < nextTowerHeartbeatAtMillis) {
            return;
        }

        WarTowerUpdate update = warInfoProvider.towerUpdate(info);
        if (update == null) {
            nextTowerHeartbeatAtMillis = now + LIVE_SEND_RETRY_MS;
            return;
        }
        if (publishWarTowerUpdate(update)) {
            nextTowerHeartbeatAtMillis = now + TOWER_HEARTBEAT_MS;
        } else {
            nextTowerHeartbeatAtMillis = now + LIVE_SEND_RETRY_MS;
        }
    }

    private void stopTowerTelemetry() {
        observedTowerTerritory = null;
        observedTowerBattleId = null;
        nextTowerHeartbeatAtMillis = 0L;
    }

    private boolean publishWarStatus(WarStatusUpdate update) {
        try {
            return submissionPublisher.publishWarStatus(update);
        } catch (RuntimeException exception) {
            SeqClient.LOGGER.debug("Live war status publisher failed; retrying on a later tick", exception);
            return false;
        }
    }

    private boolean publishWarTowerUpdate(WarTowerUpdate update) {
        try {
            return submissionPublisher.publishWarTowerUpdate(update);
        } catch (RuntimeException exception) {
            SeqClient.LOGGER.debug("Live war tower publisher failed; retrying on a later tick", exception);
            return false;
        }
    }

    private void ensureDeathListenerRegistered() {
        if (wynnDeathListenerRegistered) {
            return;
        }
        try {
            WynntilsMod.registerEventListener(this);
            wynnDeathListenerRegistered = true;
            SeqClient.LOGGER.info("[GuildWarTracker] Registered Wynntils death listener.");
        } catch (Throwable throwable) {
            SeqClient.LOGGER.debug(
                    "[GuildWarTracker] Wynntils death listener not ready yet: {}",
                    throwable.toString());
        }
    }

    private void requestSubmission(WarBattleInfo info, WarContext context, boolean force) {
        if (info == null || context == null || context.submissionSent) {
            return;
        }
        if (!force && context.seasonRating == null) {
            context.pendingSubmission = true;
            SeqClient.LOGGER.info(
                    "[GuildWarTracker] War territory='{}' completed but SR not seen yet; waiting for capture chat",
                    context.info != null ? context.info.getTerritory() : "unknown");
            return;
        }
        submitWar(info, context);
    }

    private void submitWar(WarBattleInfo info, WarContext context) {
        WarSummary summary = buildSummary(info);
        if (summary == null) {
            return;
        }

        String localUuid = trimToNull(playerContext.localUuid());
        if (localUuid == null) {
            return;
        }

        List<String> warrers = sanitizeWarrers(context.warrers);
        if (warrers.isEmpty()) {
            warrers = sanitizeWarrers(collectCurrentWarrers());
        }

        String localUsername = trimToNull(playerContext.localUsername());
        if (warrers.isEmpty() && isValidUsername(localUsername)) {
            warrers = List.of(localUsername);
        }
        if (warrers.isEmpty()) {
            return;
        }

        long submittedAtMillis = clock.getAsLong();
        String submittedAt = toRfc3339(submittedAtMillis);
        String startTime = toRfc3339(context.startEpochMs > 0 ? context.startEpochMs : submittedAtMillis);
        WarTowerState completionState = context.lastKnownState != null ? context.lastKnownState : info.getCurrentState();
        boolean completed = context.completedFromChat || isTowerDestroyed(completionState);
        String completedAt = context.completedAtEpochMs != null ? toRfc3339(context.completedAtEpochMs) : null;
        int seasonRating = context.seasonRating != null ? context.seasonRating : 0;
        SeqClient.LOGGER.info(
                "[GuildWarTracker] Submitting war territory='{}' warrers={} completedAt={} sr={}",
                summary.territory(),
                warrers,
                completedAt,
                seasonRating);

        GuildWarSubmission submission = new GuildWarSubmission(
                summary.territory(),
                localUuid,
                submittedAt,
                startTime,
                warrers,
                summary.stats(),
                seasonRating,
                completed ? completedAt : null);

        if (submissionPublisher.publishWar(submission)) {
            context.submissionSent = true;
            context.pendingSubmission = false;
            return;
        }
        SeqClient.LOGGER.warn(
                "[GuildWarTracker] Submission failed territory='{}' warrers={} completedAt={} sr={}",
                summary.territory(),
                warrers,
                completedAt,
                seasonRating);
    }

    private void submitQueue(QueueAttemptInfo info) {
        String localUuid = trimToNull(playerContext.localUuid());
        if (localUuid == null) {
            return;
        }

        long submittedAtMillis = clock.getAsLong();
        String submittedAt = toRfc3339(submittedAtMillis);

        SeqClient.LOGGER.info(
                "[GuildWarTracker] Submitting queue territory='{}' defense='{}' timer={}m",
                info.territory(),
                info.rating(),
                info.queueMinutes()
        );

        GuildWarQueueSubmission submission = new GuildWarQueueSubmission(
                info.territory(),
                localUuid,
                submittedAt,
                info.rating(),
                info.queueMinutes()
        );

        if (submissionPublisher.publishQueue(submission)) {
            return;
        }

        SeqClient.LOGGER.warn(
                "[GuildWarTracker] Submission failed queue territory='{}' defense='{}' timer={}m",
                info.territory(),
                info.rating(),
                info.queueMinutes()
        );
    }

    private WarSummary buildSummary(WarBattleInfo info) {
        if (info == null) {
            return null;
        }
        WarTowerState initialState = info.getInitialState();
        WarTowerState currentState = info.getCurrentState();
        if (initialState == null || currentState == null) {
            return null;
        }
        String territory = trimToNull(info.getTerritory());
        if (territory == null) {
            territory = "Unknown Territory";
        }
        return new WarSummary(territory, toStats(initialState));
    }

    private GuildWarSubmission.TowerStats toStats(WarTowerState state) {
        if (state == null) {
            return new GuildWarSubmission.TowerStats(0, 0, 0, 0, 0);
        }
        RangedValue damage = state.damage();
        long low = damage != null ? damage.low() : 0;
        long high = damage != null ? damage.high() : 0;
        return new GuildWarSubmission.TowerStats(low, high, state.attackSpeed(), state.health(), state.defense());
    }

    private long determineStartEpoch(WarBattleInfo info) {
        WarTowerState initial = info.getInitialState();
        return initial != null && initial.timestamp() > 0 ? initial.timestamp() : clock.getAsLong();
    }

    private String buildBattleId(WarBattleInfo info) {
        WarTowerState initial = info.getInitialState();
        long timestamp = initial != null ? initial.timestamp() : clock.getAsLong();
        String territory = trimToNull(info.getTerritory());
        return (territory == null ? "unknown" : territory) + ":" + timestamp;
    }

    private List<String> collectCurrentWarrers() {
        LinkedHashSet<String> uniqueNames = new LinkedHashSet<>();

        String localUsername = trimToNull(playerContext.localUsername());
        if (isValidUsername(localUsername)) {
            uniqueNames.add(localUsername);
        }

        for (String name : playerContext.nearbyPlayerNames(TRACKING_RADIUS_SQ)) {
            if (isValidUsername(name)) {
                uniqueNames.add(name.trim());
            }
        }

        return uniqueNames.isEmpty() ? Collections.emptyList() : new ArrayList<>(uniqueNames);
    }

    private List<String> sanitizeWarrers(List<String> warrers) {
        if (warrers == null || warrers.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String warrer : warrers) {
            if (isValidUsername(warrer)) {
                unique.add(warrer.trim());
            }
        }
        return List.copyOf(unique);
    }

    private int hashState(WarTowerState state) {
        if (state == null) {
            return 0;
        }
        long damageLow = state.damage() == null ? 0 : state.damage().low();
        long damageHigh = state.damage() == null ? 0 : state.damage().high();
        int hash = Long.hashCode(damageLow);
        hash = 31 * hash + Long.hashCode(damageHigh);
        hash = 31 * hash + Double.hashCode(state.attackSpeed());
        hash = 31 * hash + Long.hashCode(state.health());
        hash = 31 * hash + Double.hashCode(state.defense());
        hash = 31 * hash + Long.hashCode(state.timestamp());
        return hash;
    }

    private boolean isTowerDestroyed(WarTowerState state) {
        return state != null && state.health() <= 0;
    }

    private long completionEpoch(WarTowerState state) {
        if (state != null && state.timestamp() > 0) {
            return state.timestamp();
        }
        return clock.getAsLong();
    }

    private boolean isValidUsername(String name) {
        return trimToNull(name) != null && VALID_USERNAME.matcher(name.trim()).matches();
    }

    private static Integer parseSeasonRating(String cleaned) {
        if (cleaned == null) {
            return null;
        }
        var matcher = SEASON_RATING.matcher(cleaned);
        if (!matcher.find()) {
            return null;
        }
        try {
            return Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static String parseCapturedTerritory(String cleaned) {
        if (cleaned == null) {
            return null;
        }
        var matcher = CAPTURED_TERRITORY.matcher(cleaned);
        if (!matcher.find()) {
            return null;
        }
        return trimToNull(matcher.group(1));
    }

    private static boolean territoryMatches(WarContext context, String territory) {
        if (context == null || territory == null) {
            return false;
        }
        String expected = context.info != null ? trimToNull(context.info.getTerritory()) : null;
        return expected == null || expected.equalsIgnoreCase(territory.trim());
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String toRfc3339(long epochMillis) {
        return DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(epochMillis));
    }

    interface WarInfoProvider {
        WarBattleInfo getCurrentWar();

        default WarTowerUpdate towerUpdate(WarBattleInfo info) {
            return null;
        }

        default void resetTowerMetrics() {}
    }

    interface PlayerContext {
        String localUsername();

        String localUuid();

        List<String> nearbyPlayerNames(double radiusSq);

        default boolean warModeActive() {
            return false;
        }

        default WynnClassType localClassType() {
            return null;
        }

        default WorldPosition worldPosition() {
            return null;
        }
    }

    interface SubmissionPublisher {
        boolean publishWar(GuildWarSubmission submission);
        boolean publishQueue(GuildWarQueueSubmission submission);

        default boolean liveTelemetryReady() {
            return true;
        }

        default boolean publishWarStatus(WarStatusUpdate update) {
            return false;
        }

        default boolean publishWarTowerUpdate(WarTowerUpdate update) {
            return false;
        }
    }

    private record QueueAttemptInfo(String territory, String rating, int queueMinutes) {}

    private record PendingQueueAttempt(String territory, String rating, long expiresAtEpochMs) {}

    private record WarSummary(String territory, GuildWarSubmission.TowerStats stats) {}

    record WorldPosition(int x, int z) {}

    private record PresenceKey(
            WarStatusUpdate.Status status, WynnClassType classType, String territory, String battleId) {
        private static PresenceKey from(WarStatusUpdate update, String battleId) {
            return new PresenceKey(update.status(), update.classType(), update.territory(), battleId);
        }
    }

    private static final class WarContext {
        private final String id;
        private WarBattleInfo info;
        private final long startEpochMs;
        private List<String> warrers;
        private WarTowerState lastKnownState;
        private Integer seasonRating;
        private Long completedAtEpochMs;
        private boolean pendingSubmission;
        private boolean completedFromChat;
        private boolean submissionSent;

        private WarContext(String id, WarBattleInfo info, long startEpochMs, List<String> warrers) {
            this.id = id;
            this.info = info;
            this.startEpochMs = startEpochMs;
            this.warrers = warrers == null ? new ArrayList<>() : new ArrayList<>(warrers);
        }
    }

    private static final class RuntimePlayerContext implements PlayerContext {
        @Override
        public String localUsername() {
            return Minecraft.getInstance().player != null ? Minecraft.getInstance().player.getName().getString() : null;
        }

        @Override
        public String localUuid() {
            return Minecraft.getInstance().player != null
                    ? Minecraft.getInstance().player.getUUID().toString()
                    : null;
        }

        @Override
        public List<String> nearbyPlayerNames(double radiusSq) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player == null || minecraft.level == null) {
                return List.of();
            }

            LinkedHashSet<String> uniqueNames = new LinkedHashSet<>();
            for (Player other : minecraft.level.players()) {
                if (other == null || other == minecraft.player) {
                    continue;
                }
                if (minecraft.player.distanceToSqr(other) > radiusSq) {
                    continue;
                }
                String name = other.getGameProfile() != null
                        ? other.getName().getString()
                        : other.getName().getString();
                if (trimToNull(name) != null) {
                    uniqueNames.add(name.trim());
                }
            }

            return uniqueNames.isEmpty() ? List.of() : List.copyOf(uniqueNames);
        }

        @Override
        public boolean warModeActive() {
            WarPlannerManager manager = SeqClient.getWarPlannerManager();
            if (manager == null) {
                return false;
            }
            Duration remaining = manager.ownAvailabilityRemaining();
            return remaining != null && !remaining.isZero() && !remaining.isNegative();
        }

        @Override
        public WynnClassType localClassType() {
            if (!Models.Character.hasCharacter()) {
                return null;
            }
            var classType = Models.Character.getClassType();
            return classType == null ? null : WynnClassCache.parseClassType(classType.name());
        }

        @Override
        public WorldPosition worldPosition() {
            Player player = Minecraft.getInstance().player;
            return player == null ? null : new WorldPosition(player.getBlockX(), player.getBlockZ());
        }
    }

    private static final class RuntimeWarInfoProvider implements WarInfoProvider {
        @Override
        public WarBattleInfo getCurrentWar() {
            return Models.GuildWarTower.getWarBattleInfo().orElse(null);
        }

        @Override
        public WarTowerUpdate towerUpdate(WarBattleInfo info) {
            String territory = info == null ? null : trimToNull(info.getTerritory());
            return MinecraftWarTowerTracker.getInstance().snapshot(territory);
        }

        @Override
        public void resetTowerMetrics() {
            MinecraftWarTowerTracker.getInstance().reset();
        }
    }
}
