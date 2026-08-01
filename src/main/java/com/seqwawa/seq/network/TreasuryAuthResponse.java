package com.seqwawa.seq.network;

/** Signals that Mojang accepted the backend-provided server ID for this client session. */
public record TreasuryAuthResponse(String type, String nonce) {
    public static final String CHALLENGE_TYPE = "treasury_auth_challenge";
    public static final String TYPE = "treasury_auth_response";
    public static final String AUTHENTICATED_TYPE = "treasury_authenticated";

    public TreasuryAuthResponse(String nonce) {
        this(TYPE, nonce);
    }
}
