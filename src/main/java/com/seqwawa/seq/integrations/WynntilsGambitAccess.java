package com.seqwawa.seq.integrations;

import com.seqwawa.seq.client.SeqClient;
import com.wynntils.core.components.Models;
import java.util.OptionalInt;
import net.fabricmc.loader.api.FabricLoader;

/** Reads the local player's selected raid gambits from Wynntils when available. */
public final class WynntilsGambitAccess {
    private static final String WYNNTILS_MOD_ID = "wynntils";
    private static final int MAX_GAMBIT_COUNT = 4;

    private WynntilsGambitAccess() {}

    public static OptionalInt currentGambitCount() {
        if (!FabricLoader.getInstance().isModLoaded(WYNNTILS_MOD_ID)) {
            return OptionalInt.empty();
        }

        try {
            return validatedCount(Models.Gambit.getActiveGambits().size());
        } catch (LinkageError | RuntimeException exception) {
            SeqClient.LOGGER.debug("[Wynntils] Could not read active raid gambits", exception);
            return OptionalInt.empty();
        }
    }

    static OptionalInt validatedCount(int count) {
        return count >= 0 && count <= MAX_GAMBIT_COUNT ? OptionalInt.of(count) : OptionalInt.empty();
    }
}
