package com.seqwawa.seq.session;

import com.seqwawa.seq.network.auth.AuthErrorCode;
import java.util.UUID;

/** Why a session ends. Each reason runs its own steps; see {@link SessionManager#end(EndReason)}. */
public sealed interface EndReason {

    /** /seq logout. */
    EndReason SIGN_OUT = new SignOut();

    /** Clear session, on the connection screen. */
    EndReason CLEAR_SESSION = new ClearSession();

    /**
     * The player switched Minecraft account. {@code keepSignIn} when the sign-in already
     * speaks for the new account, as the Treasury operator's does when they switch back
     * from the shared account: the connection and token stay, everything else goes.
     */
    record AccountSwitch(UUID from, UUID to, boolean keepSignIn) implements EndReason {}

    /** The player signed out: nothing signs them in again until they connect. */
    record SignOut() implements EndReason {}

    /** The player cleared the session: a sign-out that also forgets the linked Discord name. */
    record ClearSession() implements EndReason {}

    /**
     * The backend refused {@code token}, the one a socket signed in with; the connection signs in
     * again by itself. Only the sign-in that still holds it ends.
     */
    record TokenRejected(String token, AuthErrorCode code, String message) implements EndReason {
        /** Leaves the token out of logs. */
        @Override
        public String toString() {
            return "TokenRejected[code=" + code + ", message=" + message + "]";
        }
    }
}
