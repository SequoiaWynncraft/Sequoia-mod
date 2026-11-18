package com.seqwawa.seq.session;

import com.seqwawa.seq.managers.GuildRaidProgressService;
import com.seqwawa.seq.managers.LeafQuestService;
import com.seqwawa.seq.managers.PrincessRaidStatsManager;
import com.seqwawa.seq.managers.WarPlannerManager;
import com.seqwawa.seq.managers.WarTerritoryQueueManager;
import com.seqwawa.seq.network.LeafEventStream;
import com.seqwawa.seq.network.auth.RaidProfilesSession;
import com.seqwawa.seq.ui.unlock.UnlockOverlay;
import com.seqwawa.seq.ui.unlock.UnlockPopup;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/** A session's objects as the game makes them, each stopped when its session ends. */
final class LiveSessionServices implements Session.Services {

    /** Where the Leaf stream hands its popups: the game thread, where the queue lives. */
    private final Executor gameThread;

    LiveSessionServices(Executor gameThread) {
        this.gameThread = Objects.requireNonNull(gameThread, "gameThread");
    }

    @Override
    public LeafQuestService leafQuests(Session session) {
        LeafQuestService quests = LeafQuestService.forSession(session.id(), session::token);
        session.onEnd(quests::reset);
        return quests;
    }

    @Override
    public GuildRaidProgressService raidProgress(Session session) {
        GuildRaidProgressService progress = GuildRaidProgressService.create();
        session.onEnd(progress::reset);
        return progress;
    }

    @Override
    public PrincessRaidStatsManager princessStats(Session session) {
        PrincessRaidStatsManager stats = new PrincessRaidStatsManager();
        session.onEnd(stats::reset);
        return stats;
    }

    @Override
    public RaidProfilesSession raidProfiles(Session session) {
        // Its token speaks for this session's account, so a save after it ended would land on their profile.
        RaidProfilesSession raidProfiles = RaidProfilesSession.create();
        session.onEnd(raidProfiles::clear);
        return raidProfiles;
    }

    @Override
    public LeafEventStream leafEvents(Session session, String resumeAfter) {
        LeafEventStream stream = new LeafEventStream(
                session::token,
                resumeAfter,
                intoQueueOf(session, gameThread),
                () -> {
                    if (!session.isEnded()) {
                        session.leafQuests().markStale();
                    }
                });
        session.onEnd(stream::stop);
        return stream;
    }

    @Override
    public UnlockOverlay popups(Session session) {
        UnlockOverlay popups = UnlockOverlay.create();
        session.onEnd(popups::clear);
        return popups;
    }

    @Override
    public WarPlannerManager warPlanner(Session session) {
        WarPlannerManager planner = WarPlannerManager.forSession(session::hasToken);
        session.onEnd(planner::reset);
        return planner;
    }

    @Override
    public WarTerritoryQueueManager warQueue(Session session) {
        WarTerritoryQueueManager queue = WarTerritoryQueueManager.forSession(session::warPlanner, session::hasToken);
        session.onEnd(queue::reset);
        return queue;
    }

    /**
     * Hands a popup to {@code session}'s own queue, on the game thread. Once that session has
     * ended nothing draws its queue, so a popup that arrives late never reaches the next account.
     */
    static Consumer<UnlockPopup> intoQueueOf(Session session, Executor gameThread) {
        return popup -> gameThread.execute(() -> session.popups().enqueue(popup));
    }
}
