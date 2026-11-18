package com.seqwawa.seq.session;

import com.seqwawa.seq.accessors.NotificationAccessor;
import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.config.ConfigManager;
import com.seqwawa.seq.managers.GuildPresenceManager;
import com.seqwawa.seq.managers.GuildRaidProgressService;
import com.seqwawa.seq.managers.LeafQuestService;
import com.seqwawa.seq.managers.MinecraftWarTowerTracker;
import com.seqwawa.seq.managers.NicknameResolverCache;
import com.seqwawa.seq.managers.RaidPartySnapshotTracker;
import com.seqwawa.seq.managers.SequoiaGuildMembership;
import com.seqwawa.seq.network.ConnectionManager;
import com.seqwawa.seq.network.LeafEventStream;
import com.seqwawa.seq.network.auth.AuthErrorCode;
import com.seqwawa.seq.network.auth.MinecraftAuthService;
import com.seqwawa.seq.network.auth.RaidProfilesSession;
import com.seqwawa.seq.network.auth.StoredAuthSession;
import com.seqwawa.seq.ui.unlock.UnlockOverlay;

/** The steps a session's end takes in the game, each the call the game made for it before. */
final class LiveSessionSteps implements SessionManager.Steps {

    @Override
    public String signedInAccount() {
        ConfigManager config = SeqClient.getConfigManager();
        StoredAuthSession stored = config == null ? null : config.getStoredAuthSession();
        return stored == null ? null : stored.minecraftUuid();
    }

    @Override
    public void resetGuildStorage() {
        if (SeqClient.getGuildStorageTracker() != null) {
            SeqClient.getGuildStorageTracker().reset();
        }
    }

    @Override
    public void resetConnectionForAccountChange() {
        ConnectionManager.resetForAccountChange();
    }

    @Override
    public void clearSignIn() {
        MinecraftAuthService auth = SeqClient.getAuthService();
        if (auth != null) {
            auth.clearSession();
        }
    }

    @Override
    public void signOut() {
        MinecraftAuthService auth = SeqClient.getAuthService();
        if (auth != null) {
            auth.signOut();
        }
    }

    @Override
    public void invalidateSignIn(AuthErrorCode code, String message) {
        SeqClient.getAuthService().invalidateSession(code, message);
    }

    @Override
    public void disconnect() {
        ConnectionManager.getInstance().disconnectForSessionEnd();
    }

    @Override
    public void dropPendingWarEvents() {
        ConnectionManager.dropPendingWarEvents();
    }

    @Override
    public void forgetDiscordName() {
        SeqClient.getConfigManager().clearDiscordUsername();
    }

    @Override
    public void tellPlayer(String message) {
        NotificationAccessor.notifyPlayer(message);
    }

    @Override
    public void forgetPartyClassBroadcast() {
        SeqClient.forgetPartyClassBroadcast();
    }

    @Override
    public void resetPartyFinder() {
        if (SeqClient.getPartyFinderManager() != null) {
            SeqClient.getPartyFinderManager().reset();
        }
    }

    @Override
    public void resetWynnPartySync() {
        if (SeqClient.getWynnPartySyncManager() != null) {
            SeqClient.getWynnPartySyncManager().reset();
        }
    }

    @Override
    public void resetRaidPartySnapshot() {
        RaidPartySnapshotTracker.reset();
    }

    @Override
    public void resetGuildPresence() {
        GuildPresenceManager.getInstance().reset();
    }

    @Override
    public void resetGuildMembership() {
        SequoiaGuildMembership.getInstance().reset();
    }

    @Override
    public void resetNicknames() {
        NicknameResolverCache.reset();
    }

    @Override
    public void clearRaidProfilesSignIn() {
        RaidProfilesSession.getInstance().clear();
    }

    @Override
    public void resetGuildRaidProgress() {
        GuildRaidProgressService.getInstance().reset();
    }

    @Override
    public void resetLeafQuests() {
        LeafQuestService.getInstance().reset();
    }

    @Override
    public void resetLeafEvents() {
        LeafEventStream.getInstance().reset();
    }

    @Override
    public void endLeafEventsKeepingCursor() {
        LeafEventStream.getInstance().endSessionKeepingCursor();
    }

    @Override
    public void clearPopups() {
        UnlockOverlay.getInstance().clear();
    }

    @Override
    public void resetWarTracking() {
        if (SeqClient.getGuildWarTracker() != null) {
            SeqClient.getGuildWarTracker().reset();
        } else {
            MinecraftWarTowerTracker.getInstance().reset();
        }
    }

    @Override
    public void resetWarPlanning() {
        resetWarPlanner();
        if (SeqClient.getWarTerritoryQueueManager() != null) {
            SeqClient.getWarTerritoryQueueManager().reset();
        }
    }

    @Override
    public void resetWarPlanner() {
        if (SeqClient.getWarPlannerManager() != null) {
            SeqClient.getWarPlannerManager().reset();
        }
    }

    @Override
    public void resetPrincessStats() {
        if (SeqClient.getPrincessRaidStatsManager() != null) {
            SeqClient.getPrincessRaidStatsManager().reset();
        }
    }
}
