package com.seqwawa.seq.session;

import com.seqwawa.seq.managers.GuildRaidProgressService;
import com.seqwawa.seq.managers.LeafQuestService;
import com.seqwawa.seq.managers.PrincessRaidStatsManager;
import com.seqwawa.seq.network.auth.RaidProfilesSession;

/** A session's objects as the game makes them, each stopped when its session ends. */
final class LiveSessionServices implements Session.Services {

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
}
