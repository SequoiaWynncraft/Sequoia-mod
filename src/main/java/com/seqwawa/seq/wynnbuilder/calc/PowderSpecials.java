package com.seqwawa.seq.wynnbuilder.calc;

import com.seqwawa.seq.wynnbuilder.data.Powder.PowderElement;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Powder specials: the effects that trigger once enough powders of one element are applied.
 *
 * <p>Each element has an active special on a weapon and a passive one on armour. Both scale with the
 * tier of the powders that triggered them, so a level is chosen rather than a simple on or off.
 */
public final class PowderSpecials {

    public static final int MAX_LEVEL = 7;

    private PowderSpecials() {}

    /** Whether a special comes from a weapon or from armour. */
    public enum Kind {
        ACTIVE("Active"),
        PASSIVE("Passive");

        private final String label;

        Kind(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    /**
     * One special.
     *
     * @param boostStat the stat the passive form grants, or {@code null} for an active special whose
     *     effect is a spell rather than a stat
     * @param boostPerLevel the value of {@code boostStat} at each level, indexed from level one
     */
    public record Special(
            PowderElement element, Kind kind, String name, String description,
            String boostStat, double[] boostPerLevel) {

        public Special {
            boostPerLevel = boostPerLevel.clone();
        }

        /** The stat value at a level, or zero when the special grants no stat. */
        public int valueAt(int level) {
            if (boostStat == null || level < 1) {
                return 0;
            }
            int index = Math.min(level, boostPerLevel.length) - 1;
            return (int) Math.round(boostPerLevel[index]);
        }
    }

    private static final List<Special> ALL = List.of(
            new Special(PowderElement.EARTH, Kind.ACTIVE, "Quake",
                    "Deals area damage around you", null, new double[] {240, 280, 320, 360, 400, 440, 480}),
            new Special(PowderElement.THUNDER, Kind.ACTIVE, "Chain Lightning",
                    "Bounces between nearby enemies", null, new double[] {200, 225, 250, 275, 300, 325, 350}),
            new Special(PowderElement.WATER, Kind.ACTIVE, "Curse",
                    "Marks an enemy to take more damage", "damPct",
                    new double[] {10, 12.5, 15, 17.5, 20, 22.5, 25}),
            new Special(PowderElement.FIRE, Kind.ACTIVE, "Courage",
                    "Raises your damage for a few seconds", "damPct",
                    new double[] {10, 12.5, 15, 17.5, 20, 22.5, 25}),
            new Special(PowderElement.AIR, Kind.ACTIVE, "Wind Prison",
                    "Holds enemies and raises damage against them", "damPct",
                    new double[] {100, 125, 150, 175, 200, 225, 250}),

            new Special(PowderElement.EARTH, Kind.PASSIVE, "Rage",
                    "Damage rises as your health falls", "mdPct",
                    new double[] {0.4, 0.5, 0.6, 0.7, 0.8, 0.9, 1.0}),
            new Special(PowderElement.THUNDER, Kind.PASSIVE, "Kill Streak",
                    "Damage rises after a kill", "damPct",
                    new double[] {6, 7.5, 9, 10.5, 12, 13.5, 15}),
            new Special(PowderElement.WATER, Kind.PASSIVE, "Concentration",
                    "Damage rises with mana spent", "sdPct",
                    new double[] {0.05, 0.075, 0.1, 0.125, 0.15, 0.175, 0.2}),
            new Special(PowderElement.FIRE, Kind.PASSIVE, "Endurance",
                    "Damage rises as you take hits", "mdPct",
                    new double[] {2, 3, 4, 5, 6, 7, 8}),
            new Special(PowderElement.AIR, Kind.PASSIVE, "Dodge",
                    "Damage rises while enemies are near", "damPct",
                    new double[] {2, 3, 4, 5, 6, 7, 8}));

    public static List<Special> all() {
        return ALL;
    }

    /** The specials of one element, active first. */
    public static List<Special> forElement(PowderElement element) {
        return ALL.stream().filter(special -> special.element() == element).toList();
    }

    public static Special byName(String name) {
        return ALL.stream().filter(special -> special.name().equals(name)).findFirst().orElse(null);
    }

    /**
     * The stats granted by a set of chosen special levels.
     *
     * <p>Specials whose effect is a spell rather than a stat contribute nothing here; they show in
     * the list so the player can see they are available.
     */
    public static Map<String, Integer> statsFor(Map<String, Integer> levels) {
        Map<String, Integer> stats = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : levels.entrySet()) {
            Special special = byName(entry.getKey());
            if (special == null || special.boostStat() == null || entry.getValue() < 1) {
                continue;
            }
            int value = special.valueAt(entry.getValue());
            if (value != 0) {
                stats.merge(special.boostStat(), value, Integer::sum);
            }
        }
        return stats;
    }
}
