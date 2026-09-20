package com.seqwawa.seq.managers;

import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.integrations.WynntilsGuildRankAccess;
import com.seqwawa.seq.model.AchievementAnnouncementClaim;
import com.seqwawa.seq.network.ApiClient;
import com.seqwawa.seq.network.ConnectionManager;
import com.seqwawa.seq.network.WynncraftServerPolicy;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;

/** Checks only this player's achievements. No announcement is broadcast to other mods. */
public final class AchievementAnnouncementManager {
    static final long CHECK_INTERVAL_MS = 30_000;
    static final long COMMAND_INTERVAL_MS = 3_000;
    private static AchievementAnnouncementManager instance;

    private final Supplier<CompletableFuture<AchievementAnnouncementClaim>> claim;
    private final BooleanSupplier ready;
    private final Supplier<UUID> identity;
    private final LongSupplier clock;
    private final Consumer<Runnable> clientThread;
    private final Consumer<String> sendCommand;
    private boolean active;
    private boolean loading;
    private UUID player;
    private UUID lastAnnouncement;
    private int generation;
    private long nextCheck;

    AchievementAnnouncementManager(
            Supplier<CompletableFuture<AchievementAnnouncementClaim>> claim,
            BooleanSupplier ready,
            Supplier<UUID> identity,
            LongSupplier clock,
            Consumer<Runnable> clientThread,
            Consumer<String> sendCommand) {
        this.claim = claim;
        this.ready = ready;
        this.identity = identity;
        this.clock = clock;
        this.clientThread = clientThread;
        this.sendCommand = sendCommand;
    }

    public static synchronized AchievementAnnouncementManager getInstance() {
        if (instance == null) {
            instance = new AchievementAnnouncementManager(
                    () -> ApiClient.getInstance().claimAchievementAnnouncement(),
                    AchievementAnnouncementManager::canAnnounce,
                    () -> Minecraft.getInstance().getUser().getProfileId(),
                    System::currentTimeMillis,
                    action -> Minecraft.getInstance().execute(action),
                    command -> Minecraft.getInstance().player.connection.sendCommand(command));
        }
        return instance;
    }

    private static boolean canAnnounce() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null || client.getUser() == null
                || !ConnectionManager.isConnected()
                || WynncraftServerPolicy.currentScope() != WynncraftServerPolicy.Scope.MAIN) {
            return false;
        }
        var session = SeqClient.getConfigManager().getStoredAuthSession();
        if (session == null || !client.getUser().getProfileId().toString().equalsIgnoreCase(session.minecraftUuid())) {
            return false;
        }
        return ChatManager.shouldRelayForGuild(WynntilsGuildRankAccess.guildMembership("Sequoia"));
    }

    public void tick() {
        boolean online = ready.getAsBoolean();
        UUID current = online ? identity.get() : null;
        if (active != online || !Objects.equals(player, current)) {
            generation++;
            loading = false;
            nextCheck = 0;
            active = online;
            player = current;
        }
        long now = clock.getAsLong();
        if (!online || loading || now < nextCheck) {
            return;
        }
        loading = true;
        nextCheck = now + CHECK_INTERVAL_MS;
        int startedFor = generation;
        UUID requestedPlayer = current;
        try {
            claim.get().whenComplete((result, failure) -> clientThread.accept(
                    () -> accept(startedFor, requestedPlayer, result, failure)));
        } catch (RuntimeException failure) {
            accept(startedFor, requestedPlayer, null, failure);
        }
    }

    private void accept(int startedFor, UUID requestedPlayer, AchievementAnnouncementClaim result, Throwable failure) {
        if (startedFor != generation) {
            return;
        }
        loading = false;
        if (failure != null) {
            SeqClient.LOGGER.debug("[Achievements] Announcement check failed", failure);
            return;
        }
        if (!ready.getAsBoolean() || !Objects.equals(requestedPlayer, identity.get())
                || result == null || result.announcement() == null) {
            return;
        }
        var announcement = result.announcement();
        if (announcement.id() == null || announcement.id().equals(lastAnnouncement)
                || !validMessage(announcement.message())) {
            return;
        }
        lastAnnouncement = announcement.id();
        sendCommand.accept("g " + announcement.message());
        nextCheck = clock.getAsLong() + COMMAND_INTERVAL_MS;
    }

    static boolean validMessage(String message) {
        return message != null && !message.isBlank() && message.length() <= 240
                && message.chars().noneMatch(c -> Character.isISOControl(c) || c == '§');
    }
}
