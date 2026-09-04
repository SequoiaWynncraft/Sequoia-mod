package com.seqwawa.seq.wynnbuilder.live;

import java.util.Locale;
import java.util.Map;

/**
 * Translates Wynntils stat identifiers into the identification keys the calculator speaks.
 *
 * <p>The two namespaces describe the same stats under different names: Wynntils keys are structured
 * enum names such as {@code DAMAGE_SPELL_FIRE_PERCENT}, while WynnBuilder's data files use the
 * short forms {@code fSdPct}, {@code mr} or {@code spRaw2} that {@link
 * com.seqwawa.seq.wynnbuilder.data.Identifications} indexes.
 *
 * <p>The systematic families — damage, defence, skills and spell costs — are derived rather than
 * tabulated, so a new element or attack type Wynncraft introduces maps itself. Only the
 * miscellaneous stats, which have no shared structure, need a table.
 *
 * <p>Works on the key string alone so it can be tested without a running game. The one thing a
 * string cannot carry is which of the four spells a cost belongs to, because Wynntils names those
 * keys after the class-specific spell ({@code SPELL_ARROW_STORM_COST_RAW}); the caller reads the
 * number off the stat type and passes it in.
 */
public final class StatKeys {

    /** Damage and defence element names, in Wynntils spelling, to the calculator's prefixes. */
    private static final Map<String, String> ELEMENT_PREFIXES = Map.of(
            "ALL", "",
            "NEUTRAL", "n",
            "EARTH", "e",
            "THUNDER", "t",
            "WATER", "w",
            "FIRE", "f",
            "AIR", "a",
            "RAINBOW", "r");

    /** How each attack type spells its damage stat, unprefixed and prefixed. */
    private static final Map<String, String[]> ATTACK_TYPES = Map.of(
            "ANY", new String[] {"dam", "Dam"},
            "SPELL", new String[] {"sd", "Sd"},
            "MAIN_ATTACK", new String[] {"md", "Md"});

    private static final Map<String, String> SKILLS = Map.of(
            "STRENGTH", "str",
            "DEXTERITY", "dex",
            "INTELLIGENCE", "int",
            "DEFENCE", "def",
            "AGILITY", "agi");

    /**
     * Stats with no family to derive them from.
     *
     * <p>Entries absent here are deliberate: Wynncraft stats the calculator has no notion of, such
     * as the gathering and dungeon experience bonuses, contribute to nothing it computes.
     */
    private static final Map<String, String> MISC = Map.ofEntries(
            Map.entry("HEALTH", "hpBonus"),
            Map.entry("HEALTH_REGEN_PERCENT", "hprPct"),
            Map.entry("HEALTH_REGEN_RAW", "hprRaw"),
            Map.entry("HEALING_EFFICIENCY", "healPct"),
            Map.entry("LIFE_STEAL", "ls"),
            Map.entry("MANA_REGEN", "mr"),
            Map.entry("MANA_STEAL", "ms"),
            Map.entry("MAX_MANA_RAW", "maxMana"),
            Map.entry("WALK_SPEED", "spd"),
            Map.entry("SPRINT", "sprint"),
            Map.entry("SPRINT_REGEN", "sprintReg"),
            Map.entry("JUMP_HEIGHT", "jh"),
            Map.entry("ATTACK_SPEED", "atkTier"),
            Map.entry("MAIN_ATTACK_RANGE", "mainAttackRange"),
            Map.entry("REFLECTION", "ref"),
            Map.entry("THORNS", "thorns"),
            Map.entry("EXPLODING", "expd"),
            Map.entry("POISON", "poison"),
            Map.entry("KNOCKBACK", "kb"),
            Map.entry("SLOW_ENEMY", "slowEnemy"),
            Map.entry("WEAKEN_ENEMY", "weakenEnemy"),
            Map.entry("STEALING", "eSteal"),
            Map.entry("COMBAT_EXPERIENCE", "xpb"),
            Map.entry("LOOT", "lb"),
            Map.entry("GATHERING_EXPERIENCE", "gXp"),
            Map.entry("GATHERING_SPEED", "gSpd"));

    private StatKeys() {}

    /**
     * The calculator's key for a Wynntils stat, or {@code null} when it has no counterpart.
     *
     * @param spellNumber the spell a cost stat belongs to, 1 to 4; ignored for every other stat
     */
    public static String key(String wynntilsKey, int spellNumber) {
        if (wynntilsKey == null || wynntilsKey.isEmpty()) {
            return null;
        }
        String key = wynntilsKey.toUpperCase(Locale.ROOT);

        // Checked ahead of the damage family: both start with DAMAGE_ but neither is one.
        if (key.equals("DAMAGE_TO_MOBS") || key.equals("DAMAGE_FROM_MOBS") || key.equals("DEFENCE_TO_MOBS")) {
            return null;
        }
        if (key.equals("CRITICAL_DAMAGE_BONUS")) {
            return "critDamPct";
        }
        if (key.startsWith("DAMAGE_")) {
            return damageKey(key.substring("DAMAGE_".length()));
        }
        if (key.equals("DEFENCE_ELEMENTAL")) {
            return "rDefPct";
        }
        if (key.startsWith("DEFENCE_")) {
            String prefix = ELEMENT_PREFIXES.get(key.substring("DEFENCE_".length()));
            return prefix == null || prefix.isEmpty() ? null : prefix + "DefPct";
        }
        if (key.startsWith("SKILL_")) {
            return SKILLS.get(key.substring("SKILL_".length()));
        }
        if (key.startsWith("SPELL_") && key.contains("_COST_")) {
            if (spellNumber < 1 || spellNumber > 4) {
                return null;
            }
            return (key.endsWith("_RAW") ? "spRaw" : "spPct") + spellNumber;
        }
        if (key.startsWith("MISC_")) {
            return MISC.get(key.substring("MISC_".length()));
        }
        return null;
    }

    /**
     * Whether Wynntils stores this stat with the opposite sign to the calculator.
     *
     * <p>Only spell costs differ. Wynntils negates them while parsing so that a larger number is
     * always the better roll, which is convenient for its own comparisons; the calculator instead
     * keeps the game's own sign, where a cost reduction reads as the negative number the tooltip
     * shows. Missing the flip turns every cost reduction into a cost increase.
     */
    public static boolean isNegated(String wynntilsKey) {
        if (wynntilsKey == null) {
            return false;
        }
        String key = wynntilsKey.toUpperCase(Locale.ROOT);
        return key.startsWith("SPELL_") && key.contains("_COST_");
    }

    /**
     * Parses the {@code <attack type>_<element>_<unit>} tail of a damage key.
     *
     * <p>Read from the right, because the attack type is the only part that can itself contain an
     * underscore ({@code MAIN_ATTACK}).
     */
    private static String damageKey(String tail) {
        int unitSeparator = tail.lastIndexOf('_');
        if (unitSeparator < 0) {
            return null;
        }
        String unit = tail.substring(unitSeparator + 1);
        int elementSeparator = tail.lastIndexOf('_', unitSeparator - 1);
        if (elementSeparator < 0) {
            return null;
        }
        String element = tail.substring(elementSeparator + 1, unitSeparator);
        String attackType = tail.substring(0, elementSeparator);

        String prefix = ELEMENT_PREFIXES.get(element);
        String[] forms = ATTACK_TYPES.get(attackType);
        if (prefix == null || forms == null) {
            return null;
        }
        String suffix = switch (unit) {
            case "PERCENT" -> "Pct";
            case "RAW" -> "Raw";
            default -> null;
        };
        if (suffix == null) {
            return null;
        }
        // Unprefixed forms are lower case (damPct, sdRaw); prefixed ones capitalise (eDamPct).
        return prefix + (prefix.isEmpty() ? forms[0] : forms[1]) + suffix;
    }
}
