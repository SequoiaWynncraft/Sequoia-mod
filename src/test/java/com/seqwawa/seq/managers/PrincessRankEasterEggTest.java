package com.seqwawa.seq.managers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PrincessRankEasterEggTest {

    @Test
    void usesPrincessForTheLocalPlayerWhileTheModeIsEnabled() {
        assertEquals("PRINCESS", label("ArcLeRetour", "arcleretour", true));
    }

    @Test
    void keepsTheRealRankWhileTheModeIsDisabled() {
        assertEquals("SAPLING", label("ArcLeRetour", "ArcLeRetour", false));
    }

    @Test
    void keepsOtherPlayersRealRanks() {
        assertEquals("SAPLING", label("Pat_Crafter07", "ArcLeRetour", true));
    }

    @Test
    void identifiesTheLocalSpeakerCaseInsensitively() {
        assertTrue(PrincessRankEasterEgg.isLocalSpeaker("ReYZhiA", "reyzhia"));
        assertFalse(PrincessRankEasterEgg.isLocalSpeaker("SomeoneElse", "reyzhia"));
    }

    private static String label(String speaker, String localPlayer, boolean modeEnabled) {
        return PrincessRankEasterEgg.pillLabel("SAPLING", speaker, localPlayer, modeEnabled);
    }
}
