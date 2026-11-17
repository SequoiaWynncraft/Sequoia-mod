package com.seqwawa.seq.managers;

import com.wynntils.core.components.Models;

/** Wynntils' own raid and war state. Only loaded when Wynntils is installed. */
final class WynntilsCombat {

    private WynntilsCombat() {}

    static boolean inRaidOrWar() {
        return Models.Raid.getCurrentRaid() != null || Models.War.isWarActive();
    }
}
