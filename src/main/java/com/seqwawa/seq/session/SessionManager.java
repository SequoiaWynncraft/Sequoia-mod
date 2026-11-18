package com.seqwawa.seq.session;

import com.seqwawa.seq.client.ClientThread;
import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.network.auth.AuthErrorCode;
import com.seqwawa.seq.network.auth.MinecraftAuthService;
import com.seqwawa.seq.network.auth.RaidProfilesSession;
import com.seqwawa.seq.network.auth.StoredAuthSession;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * Notices when the player switches Minecraft account, and is the one way a session ends:
 * {@link #end(EndReason)}. Before it, each way out (an account switch, /seq logout,
 * Clear session, a rejected token) reset its own hand-picked list in its own class;
 * now each reason's list is written out here, in the order it runs.
 */
public final class SessionManager {

    /**
     * What ending a session can do. Each step is one call the game already made before
     * this class existed; {@link LiveSessionSteps} makes them, and a test records them.
     */
    public interface Steps {
        /** Hands pending guild reward bursts to the socket, so it runs while the previous account's is open. */
        void resetGuildStorage();

        void resetConnectionForAccountChange();

        /** Ends the sign-in without signing out, so the next account signs in as usual. */
        void clearSignIn();

        /** Ends the sign-in; nothing signs the player in again until they connect. */
        void signOut();

        /** Ends the sign-in the backend refused; the connection signs in again by itself. */
        void invalidateSignIn(AuthErrorCode code, String message);

        void disconnect();

        void dropPendingWarEvents();

        void forgetDiscordName();

        void tellPlayer(String message);

        void forgetPartyClassBroadcast();

        void resetPartyFinder();

        void resetWynnPartySync();

        void resetRaidPartySnapshot();

        void resetGuildPresence();

        void resetGuildMembership();

        void resetNicknames();

        void resetWarTracking();
    }

    private static volatile SessionManager instance;

    private final Steps steps;
    private final MinecraftAuthService auth;
    private final Session.Services services;
    /** Where the game-side steps of an end that arrives from another thread run. */
    private final Executor gameThread;
    /** The account seen on the last tick. Game thread only. */
    private UUID lastSeenAccount;
    /** Replaced on the game thread only; any thread may read it. */
    private volatile Session current;

    SessionManager(Steps steps, MinecraftAuthService auth, Session.Services services, Executor gameThread) {
        this.steps = Objects.requireNonNull(steps, "steps");
        this.auth = Objects.requireNonNull(auth, "auth");
        this.services = Objects.requireNonNull(services, "services");
        this.gameThread = Objects.requireNonNull(gameThread, "gameThread");
        this.current = newSession(null);
        // A board that found no session can read Leaf now, without waiting out its minute.
        auth.whenSignedIn(this::signedIn);
    }

    public static SessionManager getInstance() {
        SessionManager manager = instance;
        if (manager != null) {
            return manager;
        }
        synchronized (SessionManager.class) {
            if (instance == null) {
                SessionManager created = new SessionManager(
                        new LiveSessionSteps(),
                        MinecraftAuthService.getInstance(),
                        new LiveSessionServices(ClientThread.executor()),
                        ClientThread.executor());
                RaidProfilesSession.findCurrentWith(() -> created.current().raidProfiles());
                instance = created;
            }
            return instance;
        }
    }

    private Session newSession(String resumeLeafAfter) {
        return new Session(auth.currentSignIn(), auth::ensureSessionToken, services, resumeLeafAfter);
    }

    /** A sign-in completed, on the auth thread: the session it was for can read Leaf at once. */
    private void signedIn(long sessionId) {
        Session session = current;
        if (session.id() == sessionId) {
            session.leafQuests().markStale();
        }
    }

    /** The current session: the signed-in one, or a signed-out one waiting to sign in. */
    public Session current() {
        return current;
    }

    /**
     * Called every client tick with the account Minecraft is signed in as. Ends the session
     * when it differs from the last one seen, and says so; the tick skips the rest of its
     * work then. Game thread only.
     */
    public boolean checkAccount(UUID account, String username) {
        if (account == null) {
            return false;
        }
        if (lastSeenAccount == null) {
            lastSeenAccount = account;
            return false;
        }
        if (lastSeenAccount.equals(account)) {
            return false;
        }
        StoredAuthSession signedIn = auth.currentSignIn().token();
        boolean keepSignIn = signInSpeaksFor(account, signedIn == null ? null : signedIn.minecraftUuid());
        SeqClient.LOGGER.info(
                "[Seq] Active Minecraft account changed {} -> {} username={} preserveOperatorSession={}",
                lastSeenAccount,
                account,
                username,
                keepSignIn);
        UUID previous = lastSeenAccount;
        lastSeenAccount = account;
        end(new EndReason.AccountSwitch(previous, account, keepSignIn));
        return true;
    }

    /**
     * Whether the sign-in already belongs to {@code account}: the backend names the account
     * a token speaks for, and the Treasury operator's names them even while they play the
     * shared account. Switching back to them keeps it.
     */
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

    /**
     * Ends the current session, running the steps {@code reason} calls for, in order, and
     * starts the next one. Game thread only, except for a rejected token, which arrives on
     * the socket's thread: its sign-in ends at once and the rest follows on the game thread.
     */
    public void end(EndReason reason) {
        switch (reason) {
            case EndReason.AccountSwitch accountSwitch -> endForAccountSwitch(accountSwitch.keepSignIn());
            case EndReason.SignOut ignored -> signOut();
            case EndReason.ClearSession ignored -> {
                steps.dropPendingWarEvents();
                signOut();
                steps.forgetDiscordName();
                steps.tellPlayer("Backend session cleared. Run /seq connect to sign in again.");
            }
            case EndReason.TokenRejected rejected -> {
                Session refused = current;
                // At once, on the socket's thread: no request may use or renew the refused token.
                steps.invalidateSignIn(rejected.code(), rejected.message());
                gameThread.execute(() -> {
                    // Another end may have got there first; the session it started is not this one's to end.
                    if (current == refused) {
                        startNextSession(true);
                    }
                    endSessionState();
                });
            }
        }
    }

    /**
     * Ends the current session, which stops everything it owned: what it read with its
     * token, its raid-profile sign-in, its Leaf stream and popups, and its war planner and
     * queues. Then starts the next
     * one with the auth service's current sign-in. {@code samePlayer} when the account
     * stays: its Leaf stream then resumes from the last event the ended one saw, so nothing
     * that happened in between is missed.
     */
    private void startNextSession(boolean samePlayer) {
        Session ended = current;
        // Stopped first, so its stream's place no longer moves.
        ended.end();
        current = newSession(samePlayer ? ended.leafCursor() : null);
    }

    /**
     * The rest of what belonged to the sign-in, for a session that ends while the account
     * stays: the party it was in. The connection reconnects by itself, so it is not touched here.
     */
    private void endSessionState() {
        steps.resetPartyFinder();
    }

    private void endForAccountSwitch(boolean keepSignIn) {
        steps.resetGuildStorage();
        if (!keepSignIn) {
            steps.resetConnectionForAccountChange();
            steps.clearSignIn();
        }
        // Kept or not, the sign-in is the new account's now, and so is the session.
        startNextSession(false);
        steps.forgetPartyClassBroadcast();
        steps.resetPartyFinder();
        steps.resetWynnPartySync();
        steps.resetRaidPartySnapshot();
        steps.resetGuildPresence();
        steps.resetGuildMembership();
        steps.resetNicknames();
        steps.resetWarTracking();
    }

    /**
     * Marked signed out before the socket closes, so a reconnect already running cannot
     * sign in again in between.
     */
    private void signOut() {
        steps.signOut();
        startNextSession(true);
        steps.disconnect();
        steps.resetPartyFinder();
    }
}
