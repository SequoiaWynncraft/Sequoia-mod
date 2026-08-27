package com.seqwawa.seq.managers;

import com.seqwawa.seq.client.SeqClient;
import java.util.concurrent.atomic.AtomicBoolean;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Chooses the richest available guild-war tracker while keeping live telemetry
 * available without optional dependencies.
 */
public final class GuildWarTrackers {
    private static final AtomicBoolean WARNED_UNAVAILABLE = new AtomicBoolean(false);

    private GuildWarTrackers() {}

    public static GuildWarTrackerHandle create() {
        if (!FabricLoader.getInstance().isModLoaded("wynntils")) {
            SeqClient.LOGGER.info(
                    "[GuildWarTracker] Wynntils not found; vanilla live war telemetry enabled,"
                            + " legacy lifecycle tracking unavailable.");
            return new VanillaGuildWarTelemetryTracker();
        }

        try {
            Object tracker = Class.forName("com.seqwawa.seq.managers.GuildWarTracker")
                    .getDeclaredConstructor()
                    .newInstance();
            SeqClient.LOGGER.info("[GuildWarTracker] Wynntils detected; guild war tracking enabled.");
            return (GuildWarTrackerHandle) tracker;
        } catch (Throwable throwable) {
            warnOnce(
                    "Wynntils guild war tracker unavailable; falling back to vanilla live war telemetry.",
                    throwable);
            return new VanillaGuildWarTelemetryTracker();
        }
    }

    private static void warnOnce(String message, Throwable throwable) {
        if (!WARNED_UNAVAILABLE.compareAndSet(false, true)) {
            return;
        }

        SeqClient.LOGGER.warn("{} Cause: {}", message, throwable.toString());
    }
}
