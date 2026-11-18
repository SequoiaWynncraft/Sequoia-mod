package com.seqwawa.seq.network.auth;

/**
 * Work that needs the player's Sequoia session found none: they signed out, or
 * the session it began under was cleared. Nothing signs them in again until they
 * connect.
 */
public final class NotSignedInException extends IllegalStateException {

    public NotSignedInException() {
        super("There is no Sequoia session; connect to sign in.");
    }
}
