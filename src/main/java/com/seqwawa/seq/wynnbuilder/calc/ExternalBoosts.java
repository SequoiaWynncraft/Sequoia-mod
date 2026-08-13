package com.seqwawa.seq.wynnbuilder.calc;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Boosts a build receives from other players rather than from its own gear or abilities.
 *
 * <p>These are what a party brings: a Shaman's totem, a Warrior's war scream, a Mage's judgement.
 * They belong on the buff panel because a raid build is planned around having them.
 *
 * <p>The damage boosts deliberately do <em>not</em> stack: only the strongest applies, matching the
 * game. The defensive and strength parts do add up.
 */
public final class ExternalBoosts {

    private ExternalBoosts() {}

    /** One boost the player can switch on. */
    public record Boost(String id, String label, String detail) {}

    /** Damage boost each one grants; the largest enabled value is the one that counts. */
    private static final Map<String, Double> DAMAGE_BOOST = Map.of(
            "totem", 0.20,
            "warscream", 0.0,
            "emboldeningcry", 0.0,
            "fortitude", 0.40,
            "hauntingfanatic", 0.0,
            "hauntinglunatic", 0.0);

    /** Boosts that scale the skill points items grant, rather than damage. */
    private static final Map<String, Double> SKILL_POINT_BOOST = Map.of(
            "radiance", 0.15,
            "divinehonor", 0.05,
            "shine", 0.05);

    private static final List<Boost> ALL = List.of(
            new Boost("radiance", "Radiance", "+15% item skill points"),
            new Boost("divinehonor", "Divine Honor", "+5% item skill points"),
            new Boost("shine", "Shine", "+5% item skill points"),
            new Boost("judgement", "Judgement", "+20% damage and defence"),
            new Boost("warscream", "War Scream", "+20% defence"),
            new Boost("emboldeningcry", "Emboldening Cry", "+5% defence, +8% strength damage"),
            new Boost("totem", "Vengeful Spirit", "+20% damage"),
            new Boost("fortitude", "Fortitude", "+40% damage"),
            new Boost("hauntinglunatic", "Lunatic Haunt", "+15% enemy weaken"),
            new Boost("hauntingfanatic", "Fanatic Haunt", "+15% vulnerability"));

    public static List<Boost> all() {
        return ALL;
    }

    /**
     * The stats the enabled boosts contribute.
     *
     * <p>Damage boosts take the maximum rather than the sum, which is why they cannot simply be
     * merged like the rest.
     */
    public static Map<String, Integer> statsFor(Set<String> enabled) {
        Map<String, Integer> stats = new LinkedHashMap<>();
        if (enabled == null || enabled.isEmpty()) {
            return stats;
        }
        double damage = 0;
        double defence = 0;
        double strength = 0;
        double vulnerability = 0;
        double weaken = 0;

        for (String id : enabled) {
            Double boost = DAMAGE_BOOST.get(id);
            if (boost != null) {
                damage = Math.max(damage, boost);
            }
            switch (id) {
                case "warscream" -> defence += 0.20;
                case "emboldeningcry" -> {
                    defence += 0.05;
                    strength += 0.08;
                }
                case "hauntingfanatic" -> vulnerability += 0.15;
                case "hauntinglunatic" -> weaken += 0.15;
                default -> {
                    // Skill point boosts are applied separately.
                }
            }
        }

        put(stats, "damMult.Potion", damage * 100);
        put(stats, "damMult.Strength", strength * 100);
        put(stats, "damMult.Vulnerability", vulnerability * 100);
        put(stats, "defMult.Potion", defence * 100);
        put(stats, "defMult.AbilityWeaken", weaken * 100);

        if (enabled.contains("judgement")) {
            stats.merge("damMult.Judgement", 20, Integer::sum);
            stats.merge("defMult.Judgement", 20, Integer::sum);
        }
        return stats;
    }

    /**
     * The multiplier applied to skill points granted by items.
     *
     * <p>Judgement overrides the others outright rather than adding to them.
     */
    public static double skillPointMultiplier(Set<String> enabled) {
        if (enabled == null || enabled.isEmpty()) {
            return 1.0;
        }
        if (enabled.contains("judgement")) {
            return 1.4;
        }
        double boost = 1.0;
        for (Map.Entry<String, Double> entry : SKILL_POINT_BOOST.entrySet()) {
            if (enabled.contains(entry.getKey())) {
                boost += entry.getValue();
            }
        }
        return boost;
    }

    private static void put(Map<String, Integer> stats, String key, double value) {
        int rounded = (int) Math.round(value);
        if (rounded != 0) {
            stats.merge(key, rounded, Integer::sum);
        }
    }
}
