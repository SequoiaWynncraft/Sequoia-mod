package org.sequoia.seq.network.auth;

public enum AuthState {
    IDLE,
    REQUESTING_CHALLENGE,
    JOINING_MINECRAFT_SESSION,
    COMPLETING_BACKEND_AUTH,
    AUTHENTICATED,
    FAILED
}
