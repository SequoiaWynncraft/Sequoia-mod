package com.seqwawa.seq.managers;

import java.util.concurrent.atomic.AtomicBoolean;
import net.fabricmc.loader.api.FabricLoader;
import com.seqwawa.seq.client.SeqClient;

public final class SeqBadgeNametagRenderers {
    private static final AtomicBoolean WARNED_UNAVAILABLE = new AtomicBoolean(false);

    private SeqBadgeNametagRenderers() {}

    public static SeqBadgeNametagRendererHandle createIfAvailable() {
        if (!FabricLoader.getInstance().isModLoaded("wynntils")) {
            warnOnce("Wynntils not found; Seq leaderboard nametag badges are disabled.", null);
            return null;
        }

        try {
            Object renderer = Class.forName("com.seqwawa.seq.managers.WynntilsSeqBadgeNametagRenderer")
                    .getDeclaredConstructor()
                    .newInstance();
            SeqClient.LOGGER.info("[LeaderboardBadges] Wynntils detected; nametag badge renderer enabled.");
            return (SeqBadgeNametagRendererHandle) renderer;
        } catch (Throwable throwable) {
            warnOnce("Wynntils nametag badge renderer unavailable; Seq leaderboard badges are disabled.", throwable);
            return null;
        }
    }

    private static void warnOnce(String message, Throwable throwable) {
        if (!WARNED_UNAVAILABLE.compareAndSet(false, true)) {
            return;
        }

        if (throwable == null) {
            SeqClient.LOGGER.info(message);
        } else {
            SeqClient.LOGGER.warn("{} Cause: {}", message, throwable.toString());
        }
    }
}
