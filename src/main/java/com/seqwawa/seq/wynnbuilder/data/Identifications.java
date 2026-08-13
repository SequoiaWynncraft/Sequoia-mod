package com.seqwawa.seq.wynnbuilder.data;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The identification namespace shared by items, tomes and crafted results.
 *
 * <p>Rather than enumerate every rolled stat, structural fields are listed explicitly and anything
 * else numeric is treated as an identification. New stats introduced by a Wynncraft update then flow
 * through and aggregate correctly instead of being silently dropped.
 */
public final class Identifications {

    /** Fields that describe the item itself rather than a rolled bonus. */
    private static final Set<String> STRUCTURAL = Set.of(
            "name", "displayName", "category", "type", "tier", "lvl", "icon", "emblem", "drop",
            "elements", "slots", "atkSpd", "classReq", "lore", "fixID", "dropInfo", "restrict",
            "sets", "set", "majorIds", "quest", "id", "remapID", "allowCraftsman", "persistent",
            "armourMaterial", "averageDps", "alias", "skills", "ids", "consumableIDs", "posMods",
            "itemIDs", "droppedBy", "materials", "healthOrDamage", "durability",
            "strReq", "dexReq", "intReq", "defReq", "agiReq",
            "nDam", "eDam", "tDam", "wDam", "fDam", "aDam",
            "hp", "eDef", "tDef", "wDef", "fDef", "aDef");

    /** Stats where a lower value is better, so a negative roll is an improvement. */
    private static final Set<String> INVERTED = Set.of("spPct1", "spPct2", "spPct3", "spPct4",
            "spRaw1", "spRaw2", "spRaw3", "spRaw4");

    /** Display order and labels for the stat panel. */
    private static final Map<String, String> DISPLAY_NAMES = buildDisplayNames();

    private Identifications() {}

    public static boolean isStructural(String key) {
        return STRUCTURAL.contains(key);
    }

    /** Whether a key represents a rolled identification. */
    public static boolean isIdentification(String key) {
        return !isStructural(key);
    }

    /**
     * Whether a negative value of this stat is beneficial.
     *
     * <p>Spell cost stats are the notable case: {@code -20%} spell cost is a bonus, so both the roll
     * ranges and the colouring have to flip.
     */
    public static boolean isInverted(String key) {
        return INVERTED.contains(key);
    }

    /** Human-readable label, falling back to the raw key for stats we have no name for. */
    public static String displayName(String key) {
        String name = DISPLAY_NAMES.get(key);
        return name != null ? name : key;
    }

    /** Whether the stat is displayed with a percent sign. */
    public static boolean isPercentage(String key) {
        return key.endsWith("Pct");
    }

    /**
     * Whether a stat belongs in the identifications list.
     *
     * <p>Abilities contribute two very different kinds of stat. Some are ordinary identifications
     * that stack with gear, such as spell damage or mana regen. Others are inputs to the damage and
     * healing calculation aimed at one part of one spell — {@code damMult.MultiTotem:1.Tick Damage},
     * {@code healMult.Rebound:3.Heal Amount}, or the flat {@code eDamAddMin}/{@code eDamAddMax}
     * pairs. Those are namespaced or paired internals: summing them is meaningful to the damage
     * pipeline but listing them reads as noise, so they are kept in the totals and hidden here.
     */
    public static boolean isDisplayable(String key) {
        if (key == null || key.isEmpty()) {
            return false;
        }
        // Ability internals are namespaced by the spell and part they target.
        if (key.indexOf('.') >= 0 || key.indexOf(':') >= 0) {
            return false;
        }
        // Flat damage added to a spell is one half of a min/max pair used by the damage calculation.
        return !key.endsWith("DamAddMin") && !key.endsWith("DamAddMax");
    }

    /**
     * Broad categories used to break a long identification list into readable blocks.
     *
     * <p>Declaration order is the order they are shown.
     */
    public enum Group {
        SKILL_POINTS("Skill points"),
        OFFENCE("Offence"),
        DEFENCE("Defence"),
        MANA("Mana"),
        MOVEMENT("Movement"),
        UTILITY("Utility");

        private final String label;

        Group(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    private static final Set<String> DEFENCE_STATS =
            Set.of("hpBonus", "hprPct", "hprRaw", "thorns", "ref", "healPct", "ls");
    private static final Set<String> MANA_STATS = Set.of("mr", "ms", "maxMana");
    private static final Set<String> MOVEMENT_STATS = Set.of("spd", "sprint", "sprintReg", "jh");
    private static final Set<String> UTILITY_STATS =
            Set.of("xpb", "lb", "eSteal", "gXp", "gSpd", "kb", "slowEnemy", "weakenEnemy");

    /**
     * The unprefixed offensive stats.
     *
     * <p>Listed explicitly because the element-prefixed forms are capitalised ({@code eDamPct},
     * {@code tSdRaw}) while the base forms are not ({@code damPct}, {@code sdRaw}), so a single
     * substring rule cannot catch both.
     */
    private static final Set<String> OFFENCE_STATS = Set.of(
            "sdPct", "sdRaw", "mdPct", "mdRaw", "damPct", "damRaw",
            "atkTier", "critDamPct", "poison", "expd", "mainAttackRange");

    /** Which block a stat belongs to. Anything unrecognised falls into {@link Group#UTILITY}. */
    public static Group group(String key) {
        if (key == null) {
            return Group.UTILITY;
        }
        if (SKILL_POINT_KEYS.contains(key)) {
            return Group.SKILL_POINTS;
        }
        if (MANA_STATS.contains(key) || key.startsWith("spPct") || key.startsWith("spRaw")) {
            return Group.MANA;
        }
        if (MOVEMENT_STATS.contains(key)) {
            return Group.MOVEMENT;
        }
        // Checked before the damage rules so "eDefPct" is not mistaken for "eDamPct".
        if (DEFENCE_STATS.contains(key) || key.endsWith("DefPct")) {
            return Group.DEFENCE;
        }
        if (UTILITY_STATS.contains(key)) {
            return Group.UTILITY;
        }
        if (OFFENCE_STATS.contains(key)) {
            return Group.OFFENCE;
        }
        // The element-prefixed damage forms: eDamPct, tSdRaw, aMdRaw and friends.
        if (key.contains("Dam") || key.contains("Sd") || key.contains("Md")) {
            return Group.OFFENCE;
        }
        return Group.UTILITY;
    }

    /** The five skill point keys in encoding order. */
    public static final java.util.List<String> SKILL_POINT_KEYS = java.util.List.of("str", "dex", "int", "def", "agi");

    /** Display names for the skill points, in the same order. */
    public static final java.util.List<String> SKILL_POINT_ORDER_NAMES =
            java.util.List.of("Strength", "Dexterity", "Intelligence", "Defence", "Agility");

    /** The five requirement keys in encoding order. */
    public static final java.util.List<String> REQUIREMENT_KEYS =
            java.util.List.of("strReq", "dexReq", "intReq", "defReq", "agiReq");

    /** Damage keys in the order used by the damage pipeline: neutral first, then e/t/w/f/a. */
    public static final java.util.List<String> DAMAGE_KEYS = java.util.List.of("nDam", "eDam", "tDam", "wDam", "fDam", "aDam");

    /** Elemental defence keys in e/t/w/f/a order. */
    public static final java.util.List<String> DEFENCE_KEYS = java.util.List.of("eDef", "tDef", "wDef", "fDef", "aDef");

    /** Element prefixes in the canonical order, with neutral first. */
    public static final java.util.List<String> ELEMENT_PREFIXES = java.util.List.of("n", "e", "t", "w", "f", "a");

    private static Map<String, String> buildDisplayNames() {
        Map<String, String> names = new LinkedHashMap<>();
        names.put("str", "Strength");
        names.put("dex", "Dexterity");
        names.put("int", "Intelligence");
        names.put("def", "Defence");
        names.put("agi", "Agility");
        names.put("hprPct", "Health Regen %");
        names.put("hprRaw", "Health Regen");
        names.put("hpBonus", "Health Bonus");
        names.put("mr", "Mana Regen");
        names.put("ms", "Mana Steal");
        names.put("ls", "Life Steal");
        names.put("sdPct", "Spell Damage %");
        names.put("sdRaw", "Raw Spell Damage");
        names.put("mdPct", "Melee Damage %");
        names.put("mdRaw", "Raw Melee Damage");
        names.put("damPct", "Damage %");
        names.put("damRaw", "Raw Damage");
        names.put("spd", "Walk Speed %");
        names.put("maxMana", "Mana Bonus");
        names.put("ref", "Reflection");
        names.put("thorns", "Thorns");
        names.put("poison", "Poison");
        names.put("expd", "Exploding");
        names.put("kb", "Knockback");
        names.put("sprint", "Sprint");
        names.put("sprintReg", "Sprint Regen");
        names.put("jh", "Jump Height");
        names.put("eSteal", "Stealing");
        names.put("slowEnemy", "Slowness");
        names.put("weakenEnemy", "Weakness");
        names.put("healPct", "Healing Efficiency");
        names.put("critDamPct", "Critical Damage");
        names.put("atkTier", "Attack Speed");
        names.put("xpb", "XP Bonus");
        names.put("lb", "Loot Bonus");
        names.put("mainAttackRange", "Attack Range");
        // Gathering stats only ever come from ingredients, so they are easy to miss when naming.
        names.put("gXp", "Gathering XP %");
        names.put("gSpd", "Gathering Speed %");
        for (int i = 1; i <= 4; i++) {
            names.put("spPct" + i, "Spell " + i + " Cost %");
            names.put("spRaw" + i, "Spell " + i + " Cost");
        }
        Map<String, String> elements = new LinkedHashMap<>();
        elements.put("e", "Earth");
        elements.put("t", "Thunder");
        elements.put("w", "Water");
        elements.put("f", "Fire");
        elements.put("a", "Air");
        elements.put("n", "Neutral");
        elements.put("r", "Rainbow");
        for (Map.Entry<String, String> element : elements.entrySet()) {
            String prefix = element.getKey();
            String label = element.getValue();
            names.put(prefix + "DamPct", label + " Damage %");
            names.put(prefix + "DamRaw", "Raw " + label + " Damage");
            names.put(prefix + "DefPct", label + " Defence %");
            names.put(prefix + "SdPct", label + " Spell Damage %");
            names.put(prefix + "SdRaw", "Raw " + label + " Spell Damage");
            names.put(prefix + "MdPct", label + " Melee Damage %");
            names.put(prefix + "MdRaw", "Raw " + label + " Melee Damage");
        }
        return Map.copyOf(names);
    }

    /** Normalises a key read from the data, tolerating the odd upstream typo. */
    public static String normalise(String key) {
        if (key == null) {
            return null;
        }
        // Present in the shipped data for a single item.
        if ("DEXETERITY".equals(key)) {
            return "dex";
        }
        return key;
    }

    public static String elementLabel(String prefix) {
        return switch (prefix.toLowerCase(Locale.ROOT)) {
            case "n" -> "Neutral";
            case "e" -> "Earth";
            case "t" -> "Thunder";
            case "w" -> "Water";
            case "f" -> "Fire";
            case "a" -> "Air";
            case "r" -> "Rainbow";
            default -> prefix;
        };
    }
}
