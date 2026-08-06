package com.seqwawa.seq.managers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PrincessRankEasterEggTest {

    @Test
    void unlocksPrincessForTheLocalPlayerWithAllThreeSecretColors() {
        assertEquals(
                "PRINCESS",
                label("ArcLeRetour", "arcleretour", true, 0xFF5DD6, 0xFF5DD6, 0xFF5DD6));
    }

    @Test
    void keepsTheRealRankWhenAnyColorDoesNotMatch() {
        assertEquals(
                "SAPLING",
                label("ArcLeRetour", "ArcLeRetour", true, 0xFF5DD6, 0xFF5DD6, 0xFF5DD5));
    }

    @Test
    void keepsOtherPlayersRealRanks() {
        assertEquals(
                "SAPLING",
                label("Pat_Crafter07", "ArcLeRetour", true, 0xFF5DD6, 0xFF5DD6, 0xFF5DD6));
    }

    @Test
    void respectsTheEasterEggSetting() {
        assertEquals(
                "SAPLING",
                label("ArcLeRetour", "ArcLeRetour", false, 0xFF5DD6, 0xFF5DD6, 0xFF5DD6));
    }

    @Test
    void identifiesTheLocalSpeakerCaseInsensitively() {
        assertTrue(PrincessRankEasterEgg.isLocalSpeaker("ReYZhiA", "reyzhia"));
        assertFalse(PrincessRankEasterEgg.isLocalSpeaker("SomeoneElse", "reyzhia"));
    }

    private static String label(
            String speaker,
            String localPlayer,
            boolean easterEggsEnabled,
            int halcyon,
            int radiance,
            int light) {
        return PrincessRankEasterEgg.pillLabel(
                "SAPLING", speaker, localPlayer, easterEggsEnabled, halcyon, radiance, light);
    }
}
