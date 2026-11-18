package com.seqwawa.seq.session;

import com.seqwawa.seq.network.auth.NotSignedInException;
import com.seqwawa.seq.network.auth.SignIn;
import com.seqwawa.seq.network.auth.StoredAuthSession;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * One sign-in of one account, from the moment the previous session ended until this one
 * does. {@link SessionManager} always has a current one; while signed out it is a session
 * with no token yet.
 * <p>
 * It holds its own sign-in, so once it has ended its token refuses: work it started that is
 * still running gets no token, and cannot sign the player in again.
 */
public final class Session {

    /** Where a session's token comes from: the auth service, which renews it but never signs in for it. */
    @FunctionalInterface
    public interface Tokens {
        CompletableFuture<String> forSession(boolean forceRefresh, long sessionId);
    }

    private final SignIn signIn;
    private final Tokens tokens;
    private volatile boolean ended;

    Session(SignIn signIn, Tokens tokens) {
        this.signIn = Objects.requireNonNull(signIn, "signIn");
        this.tokens = Objects.requireNonNull(tokens, "tokens");
    }

    /** The session's id, as the auth service counts sessions. */
    public long id() {
        return signIn.id();
    }

    /** The session's token, expired or not, or null while it has not signed in or once it has ended. */
    public StoredAuthSession signedIn() {
        return ended ? null : signIn.token();
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

    void end() {
        ended = true;
    }
}
