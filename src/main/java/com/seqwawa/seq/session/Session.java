package com.seqwawa.seq.session;

import com.seqwawa.seq.managers.GuildRaidProgressService;
import com.seqwawa.seq.managers.LeafQuestService;
import com.seqwawa.seq.managers.PrincessRaidStatsManager;
import com.seqwawa.seq.managers.WarPlannerManager;
import com.seqwawa.seq.managers.WarTerritoryQueueManager;
import com.seqwawa.seq.network.LeafEventStream;
import com.seqwawa.seq.network.auth.NotSignedInException;
import com.seqwawa.seq.network.auth.RaidProfilesSession;
import com.seqwawa.seq.network.auth.SignIn;
import com.seqwawa.seq.network.auth.StoredAuthSession;
import com.seqwawa.seq.ui.unlock.UnlockOverlay;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * One sign-in of one account, from the moment the previous session ended until this one
 * does. {@link SessionManager} always has a current one; while signed out it is a session
 * with no token yet.
 * <p>
 * It holds its own sign-in, so once it has ended its token refuses: work it started that is
 * still running gets no token, and cannot sign the player in again. It also owns what the
 * account read with that sign-in. Each is made the first time it is asked for, and stopped
 * when the session ends, so an answer that arrives later lands in an object nobody reads.
 */
public final class Session {

    /** Where a session's token comes from: the auth service, which renews it but never signs in for it. */
    @FunctionalInterface
    public interface Tokens {
        CompletableFuture<String> forSession(boolean forceRefresh, long sessionId);
    }

    /**
     * Makes a session's own objects. Each registers, with {@link #onEnd(Runnable)}, how to
     * stop what it has running when the session ends.
     */
    interface Services {
        LeafQuestService leafQuests(Session session);

        GuildRaidProgressService raidProgress(Session session);

        PrincessRaidStatsManager princessStats(Session session);

        RaidProfilesSession raidProfiles(Session session);

        /** Resumes after {@code resumeAfter}, the last event the previous session's stream saw, or from now. */
        LeafEventStream leafEvents(Session session, String resumeAfter);

        UnlockOverlay popups(Session session);

        WarPlannerManager warPlanner(Session session);

        /** Reads the account's availability and queues from the session's own {@link #warPlanner()}. */
        WarTerritoryQueueManager warQueue(Session session);
    }

    private final SignIn signIn;
    private final Tokens tokens;
    private final Services services;
    /** Where this session's Leaf stream starts: after the previous one's last event when the account is the same. */
    private final String resumeLeafAfter;
    private volatile boolean ended;
    /** Guarded by this. */
    private final List<Runnable> endActions = new ArrayList<>();
    private LeafQuestService leafQuests;
    private GuildRaidProgressService raidProgress;
    private PrincessRaidStatsManager princessStats;
    private RaidProfilesSession raidProfiles;
    private LeafEventStream leafEvents;
    private UnlockOverlay popups;
    private WarPlannerManager warPlanner;
    private WarTerritoryQueueManager warQueue;

    Session(SignIn signIn, Tokens tokens, Services services, String resumeLeafAfter) {
        this.signIn = Objects.requireNonNull(signIn, "signIn");
        this.tokens = Objects.requireNonNull(tokens, "tokens");
        this.services = Objects.requireNonNull(services, "services");
        this.resumeLeafAfter = resumeLeafAfter;
    }

    /** The session's id, as the auth service counts sessions. */
    public long id() {
        return signIn.id();
    }

    /** The session's token, expired or not, or null while it has not signed in or once it has ended. */
    public StoredAuthSession signedIn() {
        return ended ? null : signIn.token();
    }

    /** Whether the session has a token to read with. */
    public boolean hasToken() {
        StoredAuthSession token = signedIn();
        return token != null && token.hasToken();
    }

    /**
     * A token for work that belongs to this session, renewed when it has to be. It never
     * starts a sign-in: with no token, or once the session has ended, it fails with
     * {@link NotSignedInException}.
     */
    public CompletableFuture<String> token(boolean forceRefresh) {
        if (ended) {
            return CompletableFuture.failedFuture(new NotSignedInException());
        }
        return tokens.forSession(forceRefresh, id());
    }

    /** Whether a later session has replaced this one. */
    public boolean isEnded() {
        return ended;
    }

    /** The member's Leaf and quests, read with this session's token. */
    public synchronized LeafQuestService leafQuests() {
        if (leafQuests == null) {
            leafQuests = services.leafQuests(this);
        }
        return leafQuests;
    }

    /** The guild's raid progress, as this session's connection reads it. */
    public synchronized GuildRaidProgressService raidProgress() {
        if (raidProgress == null) {
            raidProgress = services.raidProgress(this);
        }
        return raidProgress;
    }

    /** The account's Princess raid count and the leaderboard. */
    public synchronized PrincessRaidStatsManager princessStats() {
        if (princessStats == null) {
            princessStats = services.princessStats(this);
        }
        return princessStats;
    }

    /** The account's sign-in to the raid-profiles backend, when that is a different one. */
    public synchronized RaidProfilesSession raidProfiles() {
        if (raidProfiles == null) {
            raidProfiles = services.raidProfiles(this);
        }
        return raidProfiles;
    }

    /** Leaf's event stream, read with this session's token, which hands its popups to {@link #popups()}. */
    public synchronized LeafEventStream leafEvents() {
        if (leafEvents == null) {
            leafEvents = services.leafEvents(this, resumeLeafAfter);
        }
        return leafEvents;
    }

    /** The popups this session's rewards brought. Once it has ended, nothing draws them. */
    public synchronized UnlockOverlay popups() {
        if (popups == null) {
            popups = services.popups(this);
        }
        return popups;
    }

    /** The guild's war plan, as this account sees and may change it. */
    public synchronized WarPlannerManager warPlanner() {
        if (warPlanner == null) {
            warPlanner = services.warPlanner(this);
        }
        return warPlanner;
    }

    /** The territory queues, with the account's own availability and joins. */
    public synchronized WarTerritoryQueueManager warQueue() {
        if (warQueue == null) {
            warQueue = services.warQueue(this);
        }
        return warQueue;
    }

    /** The last Leaf event this session saw, for a next session of the same player to resume after. */
    synchronized String leafCursor() {
        return leafEvents == null ? resumeLeafAfter : leafEvents.cursor();
    }

    /**
     * Runs {@code action} when the session ends, on the game thread: it stops what one of the
     * session's objects has running, so nothing it finishes later is shown or sent.
     */
    synchronized void onEnd(Runnable action) {
        if (!ended) {
            endActions.add(Objects.requireNonNull(action, "action"));
        }
    }

    /** Ends the session and stops what its objects had running. Game thread only. */
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
}
