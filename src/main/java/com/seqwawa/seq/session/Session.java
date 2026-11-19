package com.seqwawa.seq.session;

import com.seqwawa.seq.managers.GuildRaidProgressService;
import com.seqwawa.seq.managers.LeafQuestService;
import com.seqwawa.seq.managers.PartyFinderManager;
import com.seqwawa.seq.managers.PrincessRaidStatsManager;
import com.seqwawa.seq.managers.WarPlannerManager;
import com.seqwawa.seq.managers.WarTerritoryQueueManager;
import com.seqwawa.seq.network.ApiClient;
import com.seqwawa.seq.network.LeafEventStream;
import com.seqwawa.seq.network.auth.MinecraftAuthService;
import com.seqwawa.seq.network.auth.NotSignedInException;
import com.seqwawa.seq.network.auth.RaidProfilesSession;
import com.seqwawa.seq.network.auth.SignIn;
import com.seqwawa.seq.network.auth.StoredAuthSession;
import com.seqwawa.seq.ui.unlock.UnlockOverlay;
import com.seqwawa.seq.ui.unlock.UnlockPopup;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * One sign-in of one account, from the moment the previous session ended until this one does;
 * while signed out, one with no token yet. Once it has ended its token refuses, so work it started
 * cannot sign the player in again, and what it owned is stopped, so a late answer lands in an
 * object nobody reads.
 */
public final class Session {

    private final SignIn signIn;
    private final MinecraftAuthService auth;
    private final Executor gameThread;
    private final BiConsumer<String, Runnable> steps;
    /** After the previous session's last Leaf event when the player is the same, or null to start from now. */
    private final String resumeLeafAfter;
    private volatile boolean ended;
    /** What the session made, by name. Guarded by this, as is {@link #endActions}. */
    private final Map<String, Object> owned = new HashMap<>();
    private final List<Runnable> endActions = new ArrayList<>();

    Session(SignIn signIn, MinecraftAuthService auth, Executor gameThread, BiConsumer<String, Runnable> steps,
            String resumeLeafAfter) {
        this.signIn = signIn;
        this.auth = auth;
        this.gameThread = gameThread;
        this.steps = steps;
        this.resumeLeafAfter = resumeLeafAfter;
    }

    /** The session's id, as the auth service counts sessions. */
    public long id() {
        return signIn.id();
    }

    /** The token, expired or not, or null before the session signs in and once it has ended. */
    public StoredAuthSession signedIn() {
        return ended ? null : signIn.token();
    }

    public boolean hasToken() {
        StoredAuthSession token = signedIn();
        return token != null && token.hasToken();
    }

    /** A token for this session's work, renewed when due. It never signs in: without one it fails with {@link NotSignedInException}. */
    public CompletableFuture<String> token(boolean forceRefresh) {
        return ended
                ? CompletableFuture.failedFuture(new NotSignedInException())
                : auth.ensureSessionToken(forceRefresh, id());
    }

    public boolean isEnded() {
        return ended;
    }

    public LeafQuestService leafQuests() {
        return own("leafQuests", () -> LeafQuestService.forSession(this::token), LeafQuestService::reset);
    }

    public GuildRaidProgressService raidProgress() {
        return own("raidProgress", () -> GuildRaidProgressService.create(api()), GuildRaidProgressService::reset);
    }

    public PrincessRaidStatsManager princessStats() {
        return own("princessStats", () -> new PrincessRaidStatsManager(api()), PrincessRaidStatsManager::reset);
    }

    /** Its token speaks for this account, so a save after the session ended would land on their profile. */
    public RaidProfilesSession raidProfiles() {
        return own("raidProfiles", RaidProfilesSession::create, RaidProfilesSession::clear);
    }

    public LeafEventStream leafEvents() {
        return own("leafEvents", () -> new LeafEventStream(this::token, resumeLeafAfter, this::show, () -> {
            if (!ended) {
                leafQuests().markStale();
            }
        }), LeafEventStream::stop);
    }

    /** The popups this session's rewards brought. Once it has ended, nothing draws them. */
    public UnlockOverlay popups() {
        return own("popups", UnlockOverlay::create, UnlockOverlay::clear);
    }

    public WarPlannerManager warPlanner() {
        return own("warPlanner", () -> new WarPlannerManager(api(), this::hasToken), WarPlannerManager::reset);
    }

    /** The party finder: its listings, party and invites. Once the session ends, an invite it was still showing never does. */
    public PartyFinderManager partyFinder() {
        return own("partyFinder", () -> new PartyFinderManager(api()), PartyFinderManager::reset);
    }

    public WarTerritoryQueueManager warQueue() {
        return own("warQueue", () -> new WarTerritoryQueueManager(api(), this::warPlanner, this::hasToken),
                WarTerritoryQueueManager::reset);
    }

    /** The backend client for this session's own work: it carries this session's token, never the next one's. */
    private ApiClient api() {
        return ApiClient.getInstance().forSession(this);
    }

    /** Queues {@code popup} in this session's own popups, on the game thread, unless the session has ended by then. */
    void show(UnlockPopup popup) {
        gameThread.execute(() -> {
            if (!ended) {
                popups().enqueue(popup);
            }
        });
    }

    /** The last Leaf event this session saw, for the same player's next session to resume after. */
    synchronized String leafCursor() {
        return owned.get("leafEvents") instanceof LeafEventStream stream ? stream.cursor() : resumeLeafAfter;
    }

    /** Runs {@code action} when the session ends. */
    synchronized void onEnd(Runnable action) {
        if (!ended) {
            endActions.add(action);
        }
    }

    /** Stops {@code name}, such as the session's connection, with {@code stop} when the session ends; at once if it has. */
    public void whenEnded(String name, Runnable stop) {
        Runnable step = () -> steps.accept(name + " stopped", stop);
        synchronized (this) {
            if (!ended) {
                endActions.add(step);
                return;
            }
        }
        step.run();
    }

    /** Ends the session and stops what it owned. Game thread only. */
    void end() {
        List<Runnable> actions;
        synchronized (this) {
            if (ended) {
                return;
            }
            ended = true;
            actions = List.copyOf(endActions);
            endActions.clear();
        }
        actions.forEach(Runnable::run);
    }

    /** {@code name}'s object, made the first time it is asked for and stopped when the session ends. */
    @SuppressWarnings("unchecked")
    private synchronized <T> T own(String name, Supplier<T> make, Consumer<T> stop) {
        T made = (T) owned.get(name);
        if (made == null) {
            T fresh = make.get();
            owned.put(name, fresh);
            onEnd(() -> steps.accept(name + " stopped", () -> stop.accept(fresh)));
            made = fresh;
        }
        return made;
    }
}
