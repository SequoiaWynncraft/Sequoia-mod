package com.seqwawa.seq.wynnbuilder.live;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seqwawa.seq.wynnbuilder.data.Identifications;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StatKeysTest {

    @Test
    @DisplayName("derives the damage family from its parts")
    void damageFamily() {
        assertEquals("sdPct", StatKeys.key("DAMAGE_SPELL_ALL_PERCENT", 0));
        assertEquals("sdRaw", StatKeys.key("DAMAGE_SPELL_ALL_RAW", 0));
        assertEquals("mdPct", StatKeys.key("DAMAGE_MAIN_ATTACK_ALL_PERCENT", 0));
        assertEquals("damRaw", StatKeys.key("DAMAGE_ANY_ALL_RAW", 0));
        assertEquals("fMdPct", StatKeys.key("DAMAGE_MAIN_ATTACK_FIRE_PERCENT", 0));
        assertEquals("rSdRaw", StatKeys.key("DAMAGE_SPELL_RAINBOW_RAW", 0));
        assertEquals("nDamPct", StatKeys.key("DAMAGE_ANY_NEUTRAL_PERCENT", 0));
        assertEquals("critDamPct", StatKeys.key("CRITICAL_DAMAGE_BONUS", 0));
    }

    @Test
    @DisplayName("reads the attack type from the right, so MAIN_ATTACK survives its own underscore")
    void attackTypeWithUnderscore() {
        assertEquals("eMdRaw", StatKeys.key("DAMAGE_MAIN_ATTACK_EARTH_RAW", 0));
    }

    @Test
    @DisplayName("every damage combination lands on a stat the calculator names")
    void damageFamilyIsFullyKnown() {
        List<String> attackTypes = List.of("ANY", "SPELL", "MAIN_ATTACK");
        List<String> elements = List.of("ALL", "NEUTRAL", "EARTH", "THUNDER", "WATER", "FIRE", "AIR", "RAINBOW");
        List<String> units = List.of("PERCENT", "RAW");

        for (String attackType : attackTypes) {
            for (String element : elements) {
                for (String unit : units) {
                    String wynntilsKey = "DAMAGE_" + attackType + "_" + element + "_" + unit;
                    String key = StatKeys.key(wynntilsKey, 0);
                    assertNotEquals(null, key, wynntilsKey + " has no counterpart");
                    // An unnamed key is one the calculator does not model, which would aggregate
                    // silently into nothing rather than fail.
                    assertNotEquals(key, Identifications.displayName(key), wynntilsKey + " maps to unknown " + key);
                }
            }
        }
    }

    @Test
    @DisplayName("elemental defences map to their percentage stat")
    void defences() {
        assertEquals("wDefPct", StatKeys.key("DEFENCE_WATER", 0));
        assertEquals("aDefPct", StatKeys.key("DEFENCE_AIR", 0));
        assertEquals("rDefPct", StatKeys.key("DEFENCE_ELEMENTAL", 0));
    }

    @Test
    @DisplayName("skills and miscellaneous stats")
    void skillsAndMisc() {
        assertEquals("int", StatKeys.key("SKILL_INTELLIGENCE", 0));
        assertEquals("def", StatKeys.key("SKILL_DEFENCE", 0));
        assertEquals("mr", StatKeys.key("MISC_MANA_REGEN", 0));
        assertEquals("hpBonus", StatKeys.key("MISC_HEALTH", 0));
        assertEquals("atkTier", StatKeys.key("MISC_ATTACK_SPEED", 0));
    }

    @Test
    @DisplayName("spell costs are numbered by the caller, not by the key")
    void spellCosts() {
        assertEquals("spRaw1", StatKeys.key("SPELL_ARROW_STORM_COST_RAW", 1));
        assertEquals("spPct3", StatKeys.key("SPELL_HEAL_COST_PERCENT", 3));
        // Without a spell number there is nothing to name, so the stat is dropped rather than
        // guessed onto the first spell.
        assertNull(StatKeys.key("SPELL_HEAL_COST_PERCENT", 0));
        assertNull(StatKeys.key("SPELL_HEAL_COST_PERCENT", 5));
    }

    @Test
    @DisplayName("only spell costs carry Wynntils' sign flip")
    void negation() {
        assertTrue(StatKeys.isNegated("SPELL_BASH_COST_RAW"));
        assertFalse(StatKeys.isNegated("DAMAGE_SPELL_ALL_PERCENT"));
        assertFalse(StatKeys.isNegated("MISC_MANA_REGEN"));
        assertFalse(StatKeys.isNegated(null));
    }

    @Test
    @DisplayName("stats the calculator has no notion of are dropped, not guessed")
    void unmapped() {
        assertNull(StatKeys.key("DAMAGE_TO_MOBS", 0));
        assertNull(StatKeys.key("DAMAGE_FROM_MOBS", 0));
        assertNull(StatKeys.key("DEFENCE_TO_MOBS", 0));
        assertNull(StatKeys.key("MISC_DUNGEON_XP", 0));
        assertNull(StatKeys.key("SOMETHING_NEW", 0));
        assertNull(StatKeys.key(null, 0));
        assertNull(StatKeys.key("", 0));
    }
}
