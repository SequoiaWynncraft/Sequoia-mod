package com.seqwawa.seq.network;

/** Clients of a backend a test runs on this machine, for tests outside this package. */
public final class TestApiClients {

    private TestApiClients() {}

    /** A client of the backend at {@code baseUrl}, sending as if on Wynncraft's main server. */
    public static ApiClient at(String baseUrl) {
        return new ApiClient(baseUrl, () -> true);
    }
}
