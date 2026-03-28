package org.sequoia.seq.managers;

import com.wynntils.core.WynntilsMod;
import com.wynntils.core.components.Models;
import com.wynntils.models.character.event.CharacterDeathEvent;
import com.wynntils.models.war.type.WarBattleInfo;
import com.wynntils.models.war.type.WarTowerState;
import com.wynntils.utils.type.RangedValue;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import org.sequoia.seq.client.SeqClient;
import org.sequoia.seq.model.GuildWarSubmission;
import org.sequoia.seq.network.ConnectionManager;
import org.sequoia.seq.utils.PacketTextNormalizer;

/**
 * Tracks active guild wars via Wynntils tower state and relays one structured
 * summary when the war completes, disappears, or the local player dies.
 */
public final class GuildWarTracker implements GuildWarTrackerHandle {
    private static final double TRACKING_RADIUS_SQ = 120 * 120;
    private static final Pattern VALID_USERNAME = Pattern.compile("^[a-zA-Z0-9_]{3,16}$");
    private static final Pattern TERRITORY_CAPTURED = Pattern.compile("(?i)Territory\\s+Captured");
    private static final Pattern CAPTURED_TERRITORY = Pattern.compile("(?i)Captured\\s+\"([^\"]+)\"");
    private static final Pattern SEASON_RATING = Pattern.compile("(?i)\\+\\s*(\\d+)\\s+Season(?:al)?\\s+Rating");

    private final WarInfoProvider warInfoProvider;
    private final PlayerContext playerContext;
    private final SubmissionPublisher submissionPublisher;
    private final BooleanSupplier trackingEnabled;
    private final LongSupplier clock;

    private WarContext activeContext;
    private String lastProcessedBattleId;
    private int lastProcessedStateHash;
    private boolean wynnDeathListenerRegistered;

    public GuildWarTracker() {
        this(
                () -> Models.GuildWarTower.getWarBattleInfo().orElse(null),
                new RuntimePlayerContext(),
                submission -> ConnectionManager.getInstance().sendGuildWarSubmission(submission),
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
            reset();
            return;
        }
        trackWarState();
    }

    public void onSystemChat(Component message) {
        if (message == null) {
            return;
        }

        String cleaned = PacketTextNormalizer.normalizeForParsing(message.getString());
        if (cleaned.isEmpty() || !TERRITORY_CAPTURED.matcher(cleaned).find()) {
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
        activeContext = null;
        lastProcessedBattleId = null;
        lastProcessedStateHash = 0;
    }

    private void trackWarState() {
        WarBattleInfo info = warInfoProvider.getCurrentWar();
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
                requestSubmission(info, activeContext, false);
            }
            return;
        }

        if (activeContext != null) {
            if (!activeContext.submissionSent) {
                requestSubmission(activeContext.info, activeContext, true);
            }
            reset();
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
        int seasonRating = context.seasonRating != null ? context.seasonRating : 0;
        SeqClient.LOGGER.info(
                "[GuildWarTracker] Submitting war territory='{}' warrers={} completed={} sr={}",
                summary.territory(),
                warrers,
                completed,
                seasonRating);

        GuildWarSubmission submission = new GuildWarSubmission(
                summary.territory(),
                localUuid,
                submittedAt,
                startTime,
                warrers,
                summary.stats(),
                seasonRating,
                completed);

        if (submissionPublisher.publish(submission)) {
            context.submissionSent = true;
            context.pendingSubmission = false;
            return;
        }
        SeqClient.LOGGER.warn(
                "[GuildWarTracker] Submission failed territory='{}' warrers={} completed={} sr={}",
                summary.territory(),
                warrers,
                completed,
                seasonRating);
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
    }

    interface PlayerContext {
        String localUsername();

        String localUuid();

        List<String> nearbyPlayerNames(double radiusSq);
    }

    interface SubmissionPublisher {
        boolean publish(GuildWarSubmission submission);
    }

    private record WarSummary(String territory, GuildWarSubmission.TowerStats stats) {}

    private static final class WarContext {
        private final String id;
        private WarBattleInfo info;
        private final long startEpochMs;
        private List<String> warrers;
        private WarTowerState lastKnownState;
        private Integer seasonRating;
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
    }
}
