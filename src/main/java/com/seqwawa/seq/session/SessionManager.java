package com.seqwawa.seq.session;

import com.seqwawa.seq.accessors.NotificationAccessor;
import com.seqwawa.seq.client.ClientThread;
import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.managers.GuildPresenceManager;
import com.seqwawa.seq.managers.MinecraftWarTowerTracker;
import com.seqwawa.seq.managers.NicknameResolverCache;
import com.seqwawa.seq.managers.RaidPartySnapshotTracker;
import com.seqwawa.seq.managers.SequoiaGuildMembership;
import com.seqwawa.seq.network.ConnectionManager;
import com.seqwawa.seq.network.auth.MinecraftAuthService;
import com.seqwawa.seq.network.auth.StoredAuthSession;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;

/** Notices when the player switches Minecraft account, and is the one way a session ends: {@link #end(EndReason)}. */
public final class SessionManager {

    private static volatile SessionManager instance;

    private final MinecraftAuthService auth;
    private final Executor gameThread;
    /** Runs each named step of ending a session. The game runs it; a test writes down the name instead. */
    private final BiConsumer<String, Runnable> steps;
    /** The account seen on the last tick. Game thread only. */
    private UUID lastSeenAccount;
    /** Replaced on the game thread only; any thread may read it. */
    private volatile Session current;

    SessionManager(MinecraftAuthService auth, Executor gameThread, BiConsumer<String, Runnable> steps,
            String resumeLeafAfter) {
        this.auth = auth;
        this.gameThread = gameThread;
        this.steps = steps;
        this.current = newSession(resumeLeafAfter);
        // A board that found no session can read Leaf now, without waiting out its minute.
        auth.whenSignedIn(sessionId -> {
            Session session = current;
            if (session.id() == sessionId) {
                session.leafQuests().markStale();
            }
        });
    }

    public static SessionManager getInstance() {
        SessionManager manager = instance;
        if (manager == null) {
            synchronized (SessionManager.class) {
                if (instance == null) {
                    instance = new SessionManager(
                            MinecraftAuthService.getInstance(), ClientThread.executor(), (name, step) -> step.run(), null);
                }
                manager = instance;
            }
        }
        return manager;
    }

    private Session newSession(String resumeLeafAfter) {
        return new Session(auth.currentSignIn(), auth, gameThread, steps, resumeLeafAfter);
    }

    /** The signed-in session, or a signed-out one waiting to sign in. */
    public Session current() {
        return current;
    }

    /**
     * Called every tick with the account Minecraft is signed in as. Ends the session when it
     * differs from the last one seen, and says so: the tick skips the rest of its work then.
     */
    public boolean checkAccount(UUID account, String username) {
        if (account == null || account.equals(lastSeenAccount)) {
            return false;
        }
        if (lastSeenAccount == null) {
            lastSeenAccount = account;
            return false;
        }
        StoredAuthSession signedIn = auth.getCurrentSession();
        boolean keepSignIn = signInSpeaksFor(account, signedIn == null ? null : signedIn.minecraftUuid());
        SeqClient.LOGGER.info(
                "[Seq] Active Minecraft account changed {} -> {} username={} preserveOperatorSession={}",
                lastSeenAccount,
                account,
                username,
                keepSignIn);
        lastSeenAccount = account;
        end(new EndReason.AccountSwitch(keepSignIn));
        return true;
    }

    /** The Treasury operator's sign-in names them even while they play the shared account; switching back keeps it. */
    static boolean signInSpeaksFor(UUID account, String signedInAccount) {
        if (signedInAccount == null || signedInAccount.isBlank() || account == null) {
            return false;
        }
        try {
            return account.equals(UUID.fromString(signedInAccount));
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    /** Ends the current session with the steps {@code reason} calls for, in order, and starts the next. Game thread only. */
    public void end(EndReason reason) {
        switch (reason) {
            case EndReason.AccountSwitch accountSwitch -> {
                // Hands pending guild reward bursts to the socket, so it runs while the previous account's is open.
                step("resetGuildStorage", () -> {
                    if (SeqClient.getGuildStorageTracker() != null) {
                        SeqClient.getGuildStorageTracker().reset();
                    }
                });
                if (accountSwitch.keepSignIn()) {
                    // A fresh sign-in with the same token: what the ended session started never reaches the next.
                    auth.carrySignInOver();
                } else {
                    step("resetConnectionForAccountChange", ConnectionManager::resetForAccountChange);
                    auth.clearSession();
                }
                startNextSession(false);
                step("forgetPartyClassBroadcast", SeqClient::forgetPartyClassBroadcast);
                resetPartyFinder();
                step("resetWynnPartySync", () -> {
                    if (SeqClient.getWynnPartySyncManager() != null) {
                        SeqClient.getWynnPartySyncManager().reset();
                    }
                });
                step("resetRaidPartySnapshot", RaidPartySnapshotTracker::reset);
                step("resetGuildPresence", () -> GuildPresenceManager.getInstance().reset());
                step("resetGuildMembership", () -> SequoiaGuildMembership.getInstance().reset());
                step("resetNicknames", NicknameResolverCache::reset);
                step("resetWarTracking", () -> {
                    if (SeqClient.getGuildWarTracker() != null) {
                        SeqClient.getGuildWarTracker().reset();
                    } else {
                        MinecraftWarTowerTracker.getInstance().reset();
                    }
                });
            }
            case EndReason.SignOut ignored -> signOut();
            case EndReason.ClearSession ignored -> {
                step("dropPendingWarEvents", ConnectionManager::dropPendingWarEvents);
                signOut();
                step("forgetDiscordName", () -> SeqClient.getConfigManager()
                        .clearDiscordUsername(SeqClient.currentMinecraftProfileId()));
                String cleared = "Backend session cleared. Run /seq connect to sign in again.";
                step("tellPlayer: " + cleared, () -> NotificationAccessor.notifyPlayer(cleared));
            }
            case EndReason.TokenRejected rejected -> {
                // An old socket's refusal that lands after its sign-in ended has nothing left to end.
                if (auth.invalidateSession(rejected.token(), rejected.code(), rejected.message())) {
                    // The connection signs in again by itself, so it is left alone.
                    startNextSession(true);
                    resetPartyFinder();
                }
            }
        }
    }

    /** Signed out before the socket closes, so a reconnect already running cannot sign in again in between. */
    private void signOut() {
        auth.signOut();
        startNextSession(true);
        step("disconnect", () -> ConnectionManager.getInstance().disconnectForSessionEnd());
        resetPartyFinder();
    }

    /**
     * Ends the current session, stopping what it owned, and starts the next with the auth service's
     * current sign-in. {@code samePlayer}: its Leaf stream resumes after the ended one's last event.
     */
    private void startNextSession(boolean samePlayer) {
        Session ended = current;
        // Stopped first, so its stream's place no longer moves.
        ended.end();
        current = newSession(samePlayer ? ended.leafCursor() : null);
    }

    private void resetPartyFinder() {
        step("resetPartyFinder", () -> {
            if (SeqClient.getPartyFinderManager() != null) {
                SeqClient.getPartyFinderManager().reset();
            }
        });
    }

    private void step(String name, Runnable live) {
        steps.accept(name, live);
    }
}
