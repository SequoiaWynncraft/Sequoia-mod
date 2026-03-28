package org.sequoia.seq.managers;

import java.util.concurrent.atomic.AtomicBoolean;
import net.fabricmc.loader.api.FabricLoader;
import org.sequoia.seq.client.SeqClient;

/**
 * Creates the Wynntils-backed guild war tracker only when the optional
 * dependency is available.
 */
public final class GuildWarTrackers {
    private static final AtomicBoolean WARNED_UNAVAILABLE = new AtomicBoolean(false);

    private GuildWarTrackers() {}

    public static GuildWarTrackerHandle createIfAvailable() {
        if (!FabricLoader.getInstance().isModLoaded("wynntils")) {
            warnOnce("Wynntils not found; guild war tracking is disabled.", null);
            return null;
        }

        try {
            Object tracker = Class.forName("org.sequoia.seq.managers.GuildWarTracker")
                    .getDeclaredConstructor()
                    .newInstance();
            return (GuildWarTrackerHandle) tracker;
        } catch (Throwable throwable) {
            warnOnce("Wynntils guild war tracker unavailable; guild war tracking is disabled.", throwable);
            return null;
        }
    }

    private static void warnOnce(String message, Throwable throwable) {
        if (!WARNED_UNAVAILABLE.compareAndSet(false, true)) {
            return;
        }

        if (throwable == null) {
            SeqClient.LOGGER.warn(message);
        } else {
            SeqClient.LOGGER.warn("{} Cause: {}", message, throwable.toString());
        }
    }
}
