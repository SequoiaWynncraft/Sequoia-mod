package com.seqwawa.seq.session;

import com.seqwawa.seq.network.auth.MinecraftAuthService;
import com.seqwawa.seq.network.auth.StoredAuthSession;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/** Sessions for tests outside this package. */
public final class TestSessions {

    private TestSessions() {}

    /**
     * Sessions signed in as {@code signedIn}, which hand out its token without asking the backend.
     * Ending one stops what it owned and touches nothing else in the game.
     */
    public static SessionManager signedIn(StoredAuthSession signedIn, Executor gameThread) {
        MinecraftAuthService auth = new MinecraftAuthService(signedIn) {
            @Override
            public CompletableFuture<String> ensureValidToken(boolean forceRefresh) {
                StoredAuthSession current = getCurrentSession();
                return CompletableFuture.completedFuture(current == null ? null : current.token());
            }
        };
        return new SessionManager(auth, gameThread, (name, step) -> {
            if (name.endsWith(" stopped")) {
                step.run();
            }
        }, null);
    }
}
