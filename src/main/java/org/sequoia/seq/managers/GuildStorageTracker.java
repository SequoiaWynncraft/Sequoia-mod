package org.sequoia.seq.managers;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import org.sequoia.seq.accessors.NotificationAccessor;
import org.sequoia.seq.client.SeqClient;
import org.sequoia.seq.config.Setting;
import org.sequoia.seq.network.ConnectionManager;
import org.sequoia.seq.utils.PacketTextNormalizer;

/**
 * Tracks guild reward storage snapshots from the open guild menu and keeps
 * emerald/aspect totals updated locally via raid announcements after seed.
 */
public final class GuildStorageTracker implements NotificationAccessor {
    static final long REWARD_BURST_IDLE_GAP_MS = 2_000L;
    static final long REWARD_BURST_MAX_WINDOW_MS = 30_000L;
    private static final Pattern EMERALDS_PATTERN =
            Pattern.compile("(?i)^emeralds:\\s*([\\d, ]+)\\s*/\\s*([\\d, ]+)$");
    private static final Pattern ASPECTS_PATTERN =
            Pattern.compile("(?i)^aspects:\\s*([\\d, ]+)\\s*/\\s*([\\d, ]+)$");
    private static final Pattern REWARD_GRANT_PATTERN = Pattern.compile(
            "^(?<sender>.+?)\\s+rewarded\\s+(?:(?<emeraldAmount>[\\d, ]+)\\s+Emeralds?|(?<aspectAmount>an?|[\\d, ]+)\\s+Aspects?)\\s+to\\s+(?<recipient>.+)$",
            Pattern.CASE_INSENSITIVE);
    private static final String REWARDS_UNAVAILABLE = "rewards are unavailable";

    private static GuildStorageTracker instance;

    private final Consumer<StorageSnapshot> snapshotPublisher;
    private final Consumer<RewardBurst> rewardPublisher;
    private final Consumer<String> notificationSender;
    private final BooleanSupplier trackingEnabled;
    private final IntSupplier emeraldThresholdPercent;
    private final IntSupplier aspectThresholdPercent;
    private final LongSupplier clockMsSupplier;

    private StorageSnapshot currentSnapshot;
    private StorageSnapshot lastPublishedSnapshot;
    private final Map<RewardBurstKey, RewardBurstAccumulator> pendingRewardBursts = new LinkedHashMap<>();

    public static synchronized GuildStorageTracker getInstance() {
        if (instance == null) {
            instance = new GuildStorageTracker(
                    snapshot -> ConnectionManager.getInstance().sendGuildStorageSnapshot(
                            snapshot.emeralds().current(),
                            snapshot.emeralds().max(),
                            snapshot.aspects().current(),
                            snapshot.aspects().max()),
                    reward -> ConnectionManager.getInstance().sendGuildStorageReward(
                            reward.senderUsername(),
                            reward.recipientUsername(),
                            reward.resourceType().wireValue(),
                            reward.amount(),
                            reward.count(),
                            reward.windowStartedAt()),
                    NotificationAccessor::notifyPlayer,
                    () -> {
                        Setting.BooleanSetting setting = SeqClient.getTrackGuildStorageSetting();
                        return setting == null || setting.getValue();
                    },
                    () -> {
                        Setting.IntSetting setting = SeqClient.getGuildStorageEmeraldNotifyValueSetting();
                        return setting == null ? 100 : setting.getValue();
                    },
                    () -> {
                        Setting.IntSetting setting = SeqClient.getGuildStorageAspectNotifyValueSetting();
                        return setting == null ? 100 : setting.getValue();
                    },
                    System::currentTimeMillis);
        }
        return instance;
    }

    GuildStorageTracker(
            Consumer<StorageSnapshot> snapshotPublisher,
            Consumer<RewardBurst> rewardPublisher,
            Consumer<String> notificationSender,
            BooleanSupplier trackingEnabled,
            IntSupplier emeraldThresholdPercent,
            IntSupplier aspectThresholdPercent,
            LongSupplier clockMsSupplier) {
        this.snapshotPublisher = Objects.requireNonNull(snapshotPublisher, "snapshotPublisher");
        this.rewardPublisher = Objects.requireNonNull(rewardPublisher, "rewardPublisher");
        this.notificationSender = Objects.requireNonNull(notificationSender, "notificationSender");
        this.trackingEnabled = Objects.requireNonNull(trackingEnabled, "trackingEnabled");
        this.emeraldThresholdPercent = Objects.requireNonNull(emeraldThresholdPercent, "emeraldThresholdPercent");
        this.aspectThresholdPercent = Objects.requireNonNull(aspectThresholdPercent, "aspectThresholdPercent");
        this.clockMsSupplier = Objects.requireNonNull(clockMsSupplier, "clockMsSupplier");
    }

    public void tick() {
        if (!trackingEnabled.getAsBoolean()) {
            return;
        }

        if (!ConnectionManager.isConnected()) {
            lastPublishedSnapshot = null;
        }

        flushReadyRewardBursts(false);

        StorageSnapshot observed = observeCurrentSnapshot();
        if (observed == null) {
            return;
        }

        applyObservedSnapshot(observed);
    }

    public void onSystemChat(Component message) {
        if (!trackingEnabled.getAsBoolean() || message == null) {
            return;
        }
        if (SeqClient.mc != null && !SeqClient.mc.isSameThread()) {
            return;
        }

        RaidTracker.ParsedRaidCompletion completion = RaidTracker.parseRaidCompletion(message);
        if (completion != null) {
            applyStorageDelta(completion.emeralds(), completion.aspects());
            return;
        }

        RewardGrant rewardGrant = parseRewardGrant(message);
        if (rewardGrant == null) {
            return;
        }

        applyStorageDelta(rewardGrant.emeraldDelta(), rewardGrant.aspectDelta());
        recordRewardGrant(rewardGrant);
    }

    public void reset() {
        flushReadyRewardBursts(true);
        currentSnapshot = null;
        lastPublishedSnapshot = null;
        pendingRewardBursts.clear();
    }

    void applyObservedSnapshot(StorageSnapshot snapshot) {
        applySnapshot(snapshot, true);
    }

    public void applyRemoteSnapshot(long emeraldCurrent, long emeraldMax, long aspectCurrent, long aspectMax) {
        applySnapshot(
                new StorageSnapshot(
                        new ResourceSnapshot(emeraldCurrent, emeraldMax),
                        new ResourceSnapshot(aspectCurrent, aspectMax)),
                false);
    }

    private void applySnapshot(StorageSnapshot snapshot, boolean publishUpdate) {
        if (snapshot == null) {
            return;
        }

        StorageSnapshot previous = currentSnapshot;
        currentSnapshot = snapshot;

        if (previous != null && !previous.equals(snapshot)) {
            maybeNotifyThresholdCrossing(previous, snapshot, ResourceType.EMERALDS, emeraldThresholdPercent.getAsInt());
            maybeNotifyThresholdCrossing(previous, snapshot, ResourceType.ASPECTS, aspectThresholdPercent.getAsInt());
        }

        if (publishUpdate) {
            publishSnapshotIfNeeded(snapshot);
        }
    }

    void applyRaidRewards(long emeraldDelta, long aspectDelta) {
        applyStorageDelta(emeraldDelta, aspectDelta);
    }

    void applyStorageDelta(long emeraldDelta, long aspectDelta) {
        if (currentSnapshot == null) {
            return;
        }
        if (emeraldDelta == 0 && aspectDelta == 0) {
            return;
        }

        StorageSnapshot next = new StorageSnapshot(
                currentSnapshot.emeralds().add(emeraldDelta),
                currentSnapshot.aspects().add(aspectDelta));
        StorageSnapshot previous = currentSnapshot;
        currentSnapshot = next;

        maybeNotifyThresholdCrossing(previous, next, ResourceType.EMERALDS, emeraldThresholdPercent.getAsInt());
        maybeNotifyThresholdCrossing(previous, next, ResourceType.ASPECTS, aspectThresholdPercent.getAsInt());
    }

    StorageSnapshot currentSnapshot() {
        return currentSnapshot;
    }

    static StorageSnapshot observeCurrentSnapshot() {
        if (SeqClient.mc == null) {
            return null;
        }
        Screen screen = SeqClient.mc.screen;
        if (!(screen instanceof AbstractContainerScreen<?> containerScreen)) {
            return null;
        }
        return extractSnapshot(containerScreen.getMenu()).orElse(null);
    }

    static Optional<StorageSnapshot> extractSnapshot(AbstractContainerMenu menu) {
        if (menu == null) {
            return Optional.empty();
        }

        for (Slot slot : menu.slots) {
            if (slot == null || !slot.hasItem()) {
                continue;
            }
            StorageSnapshot snapshot = parseSnapshot(slot.getItem());
            if (snapshot != null) {
                return Optional.of(snapshot);
            }
        }

        return Optional.empty();
    }

    static StorageSnapshot parseSnapshot(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }

        ItemLore lore = stack.get(DataComponents.LORE);
        if (lore == null || lore.lines().isEmpty()) {
            return null;
        }

        return parseSnapshotLines(lore.lines().stream().map(Component::getString).toList());
    }

    static StorageSnapshot parseSnapshotLines(List<String> rawLines) {
        if (rawLines == null || rawLines.isEmpty()) {
            return null;
        }

        ResourceSnapshot emeralds = null;
        ResourceSnapshot aspects = null;

        for (String rawLine : rawLines) {
            String line = normalizeLine(rawLine);
            if (line.isEmpty()) {
                continue;
            }
            if (line.toLowerCase().contains(REWARDS_UNAVAILABLE)) {
                return null;
            }

            Matcher emeraldMatcher = EMERALDS_PATTERN.matcher(line);
            if (emeraldMatcher.matches()) {
                emeralds = new ResourceSnapshot(parseNumber(emeraldMatcher.group(1)), parseNumber(emeraldMatcher.group(2)));
                continue;
            }

            Matcher aspectMatcher = ASPECTS_PATTERN.matcher(line);
            if (aspectMatcher.matches()) {
                aspects = new ResourceSnapshot(parseNumber(aspectMatcher.group(1)), parseNumber(aspectMatcher.group(2)));
            }
        }

        if (emeralds == null || aspects == null || emeralds.max() <= 0 || aspects.max() <= 0) {
            return null;
        }

        return new StorageSnapshot(emeralds, aspects);
    }

    static RewardGrant parseRewardGrant(Component message) {
        if (message == null) {
            return null;
        }

        PacketNameResolver resolver = PacketNameResolver.from(message);
        String normalized = resolver.text();
        if (!normalized.toLowerCase(Locale.ROOT).contains(" rewarded ")
                || !normalized.toLowerCase(Locale.ROOT).contains(" to ")) {
            return null;
        }

        Matcher matcher = REWARD_GRANT_PATTERN.matcher(normalized);
        if (!matcher.matches()) {
            return null;
        }

        String senderDisplayed = matcher.group("sender");
        String recipientDisplayed = matcher.group("recipient");
        String senderUsername = resolver.resolveUsername(senderDisplayed, matcher.start("sender"), matcher.end("sender"));
        String recipientUsername =
                resolver.resolveUsername(recipientDisplayed, matcher.start("recipient"), matcher.end("recipient"));
        if (senderUsername == null || recipientUsername == null) {
            return null;
        }

        String emeraldAmount = matcher.group("emeraldAmount");
        if (emeraldAmount != null) {
            return new RewardGrant(senderUsername, recipientUsername, ResourceType.EMERALDS, parseNumber(emeraldAmount));
        }

        String aspectAmount = matcher.group("aspectAmount");
        long amount = parseAspectAmount(aspectAmount);
        if (amount <= 0) {
            return null;
        }
        return new RewardGrant(senderUsername, recipientUsername, ResourceType.ASPECTS, amount);
    }

    private void publishSnapshotIfNeeded(StorageSnapshot snapshot) {
        if (!ConnectionManager.isConnected()) {
            return;
        }
        if (snapshot.equals(lastPublishedSnapshot)) {
            return;
        }

        snapshotPublisher.accept(snapshot);
        lastPublishedSnapshot = snapshot;
    }

    private void recordRewardGrant(RewardGrant rewardGrant) {
        long now = clockMsSupplier.getAsLong();
        RewardBurstKey key = new RewardBurstKey(
                rewardGrant.senderUsername(), rewardGrant.recipientUsername(), rewardGrant.resourceType(), rewardGrant.amount());
        RewardBurstAccumulator existing = pendingRewardBursts.get(key);
        if (existing == null) {
            pendingRewardBursts.put(key, new RewardBurstAccumulator(key, now));
            return;
        }

        if (existing.shouldStartNewBurst(now)) {
            publishRewardBurst(existing);
            pendingRewardBursts.put(key, new RewardBurstAccumulator(key, now));
            return;
        }

        existing.increment(now);
    }

    private void flushReadyRewardBursts(boolean force) {
        if (pendingRewardBursts.isEmpty()) {
            return;
        }

        long now = clockMsSupplier.getAsLong();
        List<RewardBurstKey> keysToFlush = new ArrayList<>();
        for (Map.Entry<RewardBurstKey, RewardBurstAccumulator> entry : pendingRewardBursts.entrySet()) {
            if (force || entry.getValue().isReadyToFlush(now)) {
                keysToFlush.add(entry.getKey());
            }
        }

        for (RewardBurstKey key : keysToFlush) {
            RewardBurstAccumulator burst = pendingRewardBursts.remove(key);
            if (burst != null) {
                publishRewardBurst(burst);
            }
        }
    }

    private void publishRewardBurst(RewardBurstAccumulator burst) {
        rewardPublisher.accept(burst.toRewardBurst());
    }

    private void maybeNotifyThresholdCrossing(
            StorageSnapshot previous, StorageSnapshot current, ResourceType resourceType, int thresholdPercent) {
        if (thresholdPercent < 0) {
            return;
        }

        ResourceSnapshot previousResource = previous.resourceFor(resourceType);
        ResourceSnapshot currentResource = current.resourceFor(resourceType);
        if (previousResource == null || currentResource == null || previousResource.max() <= 0 || currentResource.max() <= 0) {
            return;
        }

        boolean wasAboveThreshold = previousResource.fractionFilled() >= (thresholdPercent / 100.0);
        boolean isAboveThreshold = currentResource.fractionFilled() >= (thresholdPercent / 100.0);
        if (wasAboveThreshold || !isAboveThreshold) {
            return;
        }

        notificationSender.accept("%s storage reached %d%% (%d/%d)."
                .formatted(
                        resourceType.displayName(),
                        thresholdPercent,
                        currentResource.current(),
                        currentResource.max()));
    }

    private static String normalizeLine(String rawLine) {
        return PacketTextNormalizer.normalizeForParsing(rawLine);
    }

    private static long parseNumber(String rawValue) {
        return Long.parseLong(rawValue.replace(",", "").replace(" ", "").trim());
    }

    private static long parseAspectAmount(String rawValue) {
        if (rawValue == null) {
            return 0;
        }
        String trimmed = rawValue.trim().toLowerCase(Locale.ROOT);
        if (trimmed.equals("a") || trimmed.equals("an")) {
            return 1;
        }
        return parseNumber(rawValue);
    }

    enum ResourceType {
        EMERALDS("Emeralds", "emeralds"),
        ASPECTS("Aspects", "aspects");

        private final String displayName;
        private final String wireValue;

        ResourceType(String displayName, String wireValue) {
            this.displayName = displayName;
            this.wireValue = wireValue;
        }

        String displayName() {
            return displayName;
        }

        String wireValue() {
            return wireValue;
        }
    }

    record ResourceSnapshot(long current, long max) {
        ResourceSnapshot add(long delta) {
            return new ResourceSnapshot(current + delta, max);
        }

        double fractionFilled() {
            if (max <= 0) {
                return 0;
            }
            return (double) current / (double) max;
        }
    }

    record StorageSnapshot(ResourceSnapshot emeralds, ResourceSnapshot aspects) {
        ResourceSnapshot resourceFor(ResourceType resourceType) {
            return switch (resourceType) {
                case EMERALDS -> emeralds;
                case ASPECTS -> aspects;
            };
        }
    }

    record RewardGrant(String senderUsername, String recipientUsername, ResourceType resourceType, long amount) {
        long emeraldDelta() {
            return resourceType == ResourceType.EMERALDS ? -amount : 0;
        }

        long aspectDelta() {
            return resourceType == ResourceType.ASPECTS ? -amount : 0;
        }
    }

    private record RewardBurstKey(String senderUsername, String recipientUsername, ResourceType resourceType, long amount) {}

    record RewardBurst(
            String senderUsername,
            String recipientUsername,
            ResourceType resourceType,
            long amount,
            int count,
            Instant windowStartedAt) {}

    private static final class RewardBurstAccumulator {
        private final RewardBurstKey key;
        private final long windowStartedAtMs;
        private long lastSeenAtMs;
        private int count;

        private RewardBurstAccumulator(RewardBurstKey key, long now) {
            this.key = key;
            this.windowStartedAtMs = now;
            this.lastSeenAtMs = now;
            this.count = 1;
        }

        private boolean shouldStartNewBurst(long now) {
            return now - lastSeenAtMs > REWARD_BURST_IDLE_GAP_MS || now - windowStartedAtMs >= REWARD_BURST_MAX_WINDOW_MS;
        }

        private boolean isReadyToFlush(long now) {
            return now - lastSeenAtMs > REWARD_BURST_IDLE_GAP_MS || now - windowStartedAtMs >= REWARD_BURST_MAX_WINDOW_MS;
        }

        private void increment(long now) {
            lastSeenAtMs = now;
            count++;
        }

        private RewardBurst toRewardBurst() {
            return new RewardBurst(
                    key.senderUsername(),
                    key.recipientUsername(),
                    key.resourceType(),
                    key.amount(),
                    count,
                    Instant.ofEpochMilli(windowStartedAtMs));
        }
    }
}
