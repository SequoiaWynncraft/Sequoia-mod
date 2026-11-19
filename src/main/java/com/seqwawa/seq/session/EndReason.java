package com.seqwawa.seq.session;

import com.seqwawa.seq.network.auth.AuthErrorCode;

/** Why a session ends. Each reason runs its own steps; see {@link SessionManager#end(EndReason)}. */
public sealed interface EndReason {

    EndReason SIGN_OUT = new SignOut();

    EndReason CLEAR_SESSION = new ClearSession();

    /**
     * The player switched Minecraft account. {@code keepSignIn} when the sign-in already speaks for
     * the new account, as the Treasury operator's does when they switch back from the shared one.
     */
    record AccountSwitch(boolean keepSignIn) implements EndReason {}

    /** /seq logout: nothing signs the player in again until they connect. */
    record SignOut() implements EndReason {}

    /** Clear session, on the connection screen: a sign-out that also forgets the linked Discord name. */
    record ClearSession() implements EndReason {}

    /** The backend refused {@code token}, the one a socket signed in with. Only the sign-in still holding it ends. */
    record TokenRejected(String token, AuthErrorCode code, String message) implements EndReason {
        /** Leaves the token out of logs. */
        @Override
        public String toString() {
            return "TokenRejected[code=" + code + ", message=" + message + "]";
        }
    }
}
