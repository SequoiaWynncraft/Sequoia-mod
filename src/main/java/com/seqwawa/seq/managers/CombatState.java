package com.seqwawa.seq.managers;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Whether the player is in a raid or a war, where anything covering the screen
 * could cost them the fight. Read once a client tick; drawing code reads the
 * last answer.
 */
public final class CombatState {

    private static volatile boolean fighting;

    private CombatState() {}

    public static boolean inCombat() {
        return fighting;
    }

    public static void tick() {
        fighting = readFighting();
    }

    private static boolean readFighting() {
        if (FabricLoader.getInstance().isModLoaded("wynntils")) {
            try {
                if (WynntilsCombat.inRaidOrWar()) return true;
            } catch (Throwable unavailable) {
                // An older or newer Wynntils without these models: fall back to what the game itself shows.
            }
        }
        // Without Wynntils: a raid's sidebar, or a war's sidebar or instance.
        return WynnPartyScoreboardReader.raidSidebarActive() || MinecraftWarWorldDetector.isWarInstance();
    }
}
