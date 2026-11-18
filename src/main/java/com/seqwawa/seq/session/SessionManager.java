package com.seqwawa.seq.session;

import com.seqwawa.seq.client.ClientThread;
import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.network.auth.AuthErrorCode;
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
        /** The Minecraft account the current sign-in speaks for, as the backend said, or null. */
        String signedInAccount();

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

        /** Its token speaks for the previous account, so a save would land on their profile. */
        void clearRaidProfilesSignIn();

        void resetGuildRaidProgress();

        void resetLeafQuests();

        /** Stops the Leaf stream and forgets where it was: the next account's starts from now. */
        void resetLeafEvents();

        /** Stops the Leaf stream, dropping what it had yet to show, but the next session resumes from it. */
        void endLeafEventsKeepingCursor();

        void clearPopups();

        void resetWarTracking();

        /** The war planner and the war queue. */
        void resetWarPlanning();

        /** The war planner alone, as /seq logout did. */
        void resetWarPlanner();

        void resetPrincessStats();
    }

    private static SessionManager instance;

    private final Steps steps;
    /** Where the game-side steps of an end that arrives from another thread run. */
    private final Executor gameThread;
    /** The account seen on the last tick. Game thread only. */
    private UUID lastSeenAccount;

    SessionManager(Steps steps, Executor gameThread) {
        this.steps = Objects.requireNonNull(steps, "steps");
        this.gameThread = Objects.requireNonNull(gameThread, "gameThread");
    }

    public static synchronized SessionManager getInstance() {
        if (instance == null) {
            instance = new SessionManager(new LiveSessionSteps(), ClientThread.executor());
        }
        return instance;
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
        boolean keepSignIn = signInSpeaksFor(account, steps.signedInAccount());
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

    /** Ends the session, running the steps {@code reason} calls for, in order. */
    public void end(EndReason reason) {
        switch (reason) {
            case EndReason.AccountSwitch accountSwitch -> endForAccountSwitch(accountSwitch.keepSignIn());
            case EndReason.SignOut ignored -> {
                signOut();
                steps.resetWarPlanner();
            }
            case EndReason.ClearSession ignored -> {
                steps.dropPendingWarEvents();
                signOut();
                steps.forgetDiscordName();
                steps.tellPlayer("Backend session cleared. Run /seq connect to sign in again.");
            }
            case EndReason.TokenRejected rejected -> {
                // At once, on the socket's thread: no request may use or renew the refused token.
                steps.invalidateSignIn(rejected.code(), rejected.message());
                gameThread.execute(this::endSessionState);
            }
        }
    }

    /**
     * Everything that belonged to the sign-in, for a session that ends while the account
     * stays: what was read with its token, what was waiting to show, and the raid-profile
     * sign-in. The Leaf stream resumes from where it was, since the events are the same
     * player's. The connection reconnects by itself, so it is not touched here.
     */
    private void endSessionState() {
        steps.resetPartyFinder();
        steps.clearRaidProfilesSignIn();
        steps.resetGuildRaidProgress();
        steps.resetLeafQuests();
        steps.endLeafEventsKeepingCursor();
        steps.clearPopups();
        steps.resetWarPlanning();
        steps.resetPrincessStats();
    }

    private void endForAccountSwitch(boolean keepSignIn) {
        steps.resetGuildStorage();
        if (!keepSignIn) {
            steps.resetConnectionForAccountChange();
            steps.clearSignIn();
        }
        steps.forgetPartyClassBroadcast();
        steps.resetPartyFinder();
        steps.resetWynnPartySync();
        steps.resetRaidPartySnapshot();
        steps.resetGuildPresence();
        steps.resetGuildMembership();
        steps.resetNicknames();
        steps.clearRaidProfilesSignIn();
        steps.resetGuildRaidProgress();
        steps.resetLeafQuests();
        steps.resetLeafEvents();
        steps.clearPopups();
        steps.resetWarTracking();
        steps.resetWarPlanning();
        steps.resetPrincessStats();
    }

    /**
     * Marked signed out before the socket closes, so a reconnect already running cannot
     * sign in again in between.
     */
    private void signOut() {
        steps.signOut();
        steps.disconnect();
        steps.resetPartyFinder();
        steps.clearRaidProfilesSignIn();
    }
}
