package com.seqwawa.seq.integrations;

import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.model.PowderSpecialReading;
import com.wynntils.core.components.Models;
import com.wynntils.models.characterstats.type.PowderSpecialInfo;
import com.wynntils.models.elements.type.Powder;
import java.util.Optional;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Reads how far the local player's powder special has charged.
 *
 * <p>Wynncraft only publishes this for the player themselves, encoded in the action
 * bar; no other player's charge is on the wire. Wynntils already decodes that
 * encoding, so this reads its result rather than re-implementing the parse.
 *
 * <p>Every element is reported, not just Fire, because "holding a Water weapon" and
 * "cannot read the bar at all" have to stay distinguishable — see
 * {@code CourageCharge}.
 */
public final class WynntilsPowderSpecialAccess {
    private static final String WYNNTILS_MOD_ID = "wynntils";

    private WynntilsPowderSpecialAccess() {}

    /** The current powder special, or empty when Wynncraft is not publishing one. */
    public static Optional<PowderSpecialReading> read() {
        try {
            if (!FabricLoader.getInstance().isModLoaded(WYNNTILS_MOD_ID)) {
                return Optional.empty();
            }

            Optional<PowderSpecialInfo> info = Models.CharacterStats.getPowderSpecialInfo();
            return info.map(special ->
                    new PowderSpecialReading(special.powder() == Powder.FIRE, special.charge()));
        } catch (LinkageError | RuntimeException e) {
            SeqClient.LOGGER.debug("[Wynntils] Could not read the powder special charge", e);
            return Optional.empty();
        }
    }
}
