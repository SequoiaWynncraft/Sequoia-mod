package com.seqwawa.seq.network.auth;

/**
 * One session's sign-in to the backend: the session's id and, once signed in, its token.
 * <p>
 * Each session has its own, so ending one leaves its token with it: a request still holding
 * an ended session's sign-in gets nothing, and a sign-in that finishes for it is never
 * stored. The token stays in memory only, never in the config file.
 * <p>
 * The auth service changes it under its own lock; anyone may read it.
 */
public final class SignIn {

    private final long id;
    private volatile StoredAuthSession token;

    SignIn(long id, StoredAuthSession token) {
        this.id = id;
        this.token = token;
    }

    /** The session's id: the auth service counts sessions, and this is the one it was at. */
    public long id() {
        return id;
    }

    /** The token, whether or not it has expired, or null before the session signs in. */
    public StoredAuthSession token() {
        return token;
    }

    void setToken(StoredAuthSession token) {
        this.token = token;
    }
}
