package com.seqwawa.seq.network.auth;

/**
 * One session's sign-in: its id and, once signed in, its token, which ending the session drops.
 * The token lives in memory only. The auth service changes it under its own lock; anyone may read it.
 */
public final class SignIn {

    private final long id;
    private volatile StoredAuthSession token;

    SignIn(long id, StoredAuthSession token) {
        this.id = id;
        this.token = token;
    }

    public long id() {
        return id;
    }

    /** The token, expired or not, or null before the session signs in and once it ends. */
    public StoredAuthSession token() {
        return token;
    }

    void setToken(StoredAuthSession token) {
        this.token = token;
    }
}
