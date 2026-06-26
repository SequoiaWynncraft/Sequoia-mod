package com.seqwawa.seq.managers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.EnumSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.seqwawa.seq.model.BombShareType;

class BombShareManagerTest {

    @Test
    void resolveRequestSupportsAliasesAndBundleExpansion() {
        BombShareManager.ResolvedRequest resolved = BombShareManager.resolveRequest("prof dxp lc");

        assertNotNull(resolved);
        assertEquals(
                List.of(
                        BombShareType.COMBAT_XP,
                        BombShareType.PROFESSION_XP,
                        BombShareType.PROFESSION_SPEED,
                        BombShareType.LOOT_CHEST),
                resolved.requestedTypes());
        assertEquals(
                "combat_xp+profession_xp+profession_speed+loot_chest",
                resolved.canonicalKey());
    }

    @Test
    void resolveRequestRejectsUnknownSelectors() {
        assertNull(BombShareManager.resolveRequest("mystery"));
    }

    @Test
    void buildDisplayLabelUsesProfessionShortcutAndUnionLabel() {
        assertEquals(
                "profession",
                BombShareManager.buildDisplayLabel(
                        EnumSet.of(BombShareType.PROFESSION_XP, BombShareType.PROFESSION_SPEED)));
        assertEquals(
                "combat xp + loot chest",
                BombShareManager.buildDisplayLabel(
                        EnumSet.of(BombShareType.COMBAT_XP, BombShareType.LOOT_CHEST)));
    }

    @Test
    void suggestionsHideAlreadyCompletedTokensAndContinueUnionFlow() {
        assertEquals(List.of("loot", "lc"), BombShareManager.suggestionsFor("dxp prof l"));
        assertEquals(
                List.of("dxp", "prof", "profxp", "profspeed", "loot", "lc"),
                BombShareManager.suggestionsFor(""));
        assertEquals(
                List.of("dxp", "profxp", "profspeed", "loot", "lc"),
                BombShareManager.suggestionsFor("prof "));
    }

    @Test
    void compareWorldNamesSortsByNumericSuffixWhenPossible() {
        List<String> worlds = new java.util.ArrayList<>(List.of("WC12", "WC2", "WC1"));
        worlds.sort(BombShareManager::compareWorldNames);
        assertEquals(List.of("WC1", "WC2", "WC12"), worlds);
    }
}
