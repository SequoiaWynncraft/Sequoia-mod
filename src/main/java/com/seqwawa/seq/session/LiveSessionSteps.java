package com.seqwawa.seq.session;

import com.seqwawa.seq.accessors.NotificationAccessor;
import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.managers.GuildPresenceManager;
import com.seqwawa.seq.managers.MinecraftWarTowerTracker;
import com.seqwawa.seq.managers.NicknameResolverCache;
import com.seqwawa.seq.managers.RaidPartySnapshotTracker;
import com.seqwawa.seq.managers.SequoiaGuildMembership;
import com.seqwawa.seq.network.ConnectionManager;
import com.seqwawa.seq.network.auth.AuthErrorCode;
import com.seqwawa.seq.network.auth.MinecraftAuthService;
import com.seqwawa.seq.network.auth.SignIn;

/** The steps a session's end takes in the game, each the call the game made for it before. */
final class LiveSessionSteps implements SessionManager.Steps {

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
    public SignIn invalidateSignIn(String token, AuthErrorCode code, String message) {
        return SeqClient.getAuthService().invalidateSession(token, code, message);
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
        SeqClient.getConfigManager().clearDiscordUsername(SeqClient.currentMinecraftProfileId());
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
    public void resetWarTracking() {
        if (SeqClient.getGuildWarTracker() != null) {
            SeqClient.getGuildWarTracker().reset();
        } else {
            MinecraftWarTowerTracker.getInstance().reset();
        }
    }
}
