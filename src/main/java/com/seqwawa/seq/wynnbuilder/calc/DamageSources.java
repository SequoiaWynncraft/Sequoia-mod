package com.seqwawa.seq.wynnbuilder.calc;

import com.seqwawa.seq.wynnbuilder.atree.AbilityTreeEngine;
import com.seqwawa.seq.wynnbuilder.data.BuildEquipment;
import com.seqwawa.seq.wynnbuilder.data.CraftedItem;
import com.seqwawa.seq.wynnbuilder.data.EquipmentSlot;
import com.seqwawa.seq.wynnbuilder.data.Identifications;
import com.seqwawa.seq.wynnbuilder.data.WynnBuild;
import com.seqwawa.seq.wynnbuilder.data.WynnDataSet;
import com.seqwawa.seq.wynnbuilder.data.WynnItem;
import java.util.ArrayList;
import java.util.List;

/**
 * Every source of damage a build has: the melee attack, and each part of each spell the ability tree
 * defines.
 *
 * <p>Spells are assembled by the ability tree, so a build with no weapon or no selected abilities
 * simply has fewer sources rather than none.
 */
public final class DamageSources {

    private DamageSources() {}

    /**
     * One thing that deals damage.
     *
     * @param result the full calculation, so the breakdown can be shown on demand
     * @param multipliers the part's share of weapon damage per element, for the same reason
     */
    public record Source(
            String name, String detail, double perHit, double perSecond, boolean isTotal,
            DamageCalc.Result result, double[] multipliers,
            java.util.Map<String, Double> composition) {

        public Source(String name, String detail, double perHit, double perSecond, boolean isTotal) {
            this(name, detail, perHit, perSecond, isTotal, null, null, java.util.Map.of());
        }

        public Source(String name, String detail, double perHit, double perSecond, boolean isTotal,
                DamageCalc.Result result, double[] multipliers) {
            this(name, detail, perHit, perSecond, isTotal, result, multipliers, java.util.Map.of());
        }

        /** The combined multiplier across every element, which is how the site labels a part. */
        public double totalMultiplier() {
            if (multipliers == null) {
                return 0;
            }
            double total = 0;
            for (double multiplier : multipliers) {
                total += multiplier;
            }
            return total;
        }
    }

    /**
     * A spell, its real mana cost, and what it does.
     *
     * @param headline the number that represents the spell, named by the ability data itself
     */
    public record SpellGroup(
            String name, double cost, double castsPerSecond, double sustainedDps, double headline,
            List<Source> parts) {
        public SpellGroup {
            parts = List.copyOf(parts);
        }
    }

    /** Everything the build can do, ready to display. */
    public record Report(Source melee, List<SpellGroup> spells, String weaponName, String attackSpeed, String message) {
        public SpellGroup ungrouped() {
            return spells.isEmpty() ? null : spells.get(0);
        }

        public boolean isEmpty() {
            return melee == null && spells.isEmpty();
        }
    }

    /**
     * Builds the report.
     *
     * @param evaluation the ability tree's spells, which may be empty
     */
    public static Report compute(
            WynnBuild build,
            WynnDataSet data,
            BuildStats stats,
            AbilityTreeEngine.Evaluation evaluation) {

        DamageCalc.Weapon weapon = weaponOf(build, data, stats);
        if (weapon == null) {
            return new Report(null, List.of(), null, null, "Equip a weapon to see damage");
        }

        String weaponName = weaponName(build, data);
        double critChance = DamageCalc.critChance(stats);

        DamageCalc.Result meleeHit = DamageCalc.meleeHit(stats, weapon);
        Source melee = new Source(
                "Melee",
                "per hit",
                meleeHit.expected(critChance),
                DamageCalc.meleeDps(stats, weapon),
                false);

        List<SpellGroup> spells = new ArrayList<>();
        for (AbilityTreeEngine.Spell spell : evaluation.spells()) {
            List<Source> parts = new ArrayList<>();
            // Damage parts only; totals are kept apart so one cannot feed another.
            java.util.Map<String, Double> byName = new java.util.LinkedHashMap<>();
            java.util.Map<String, Double> totalsByName = new java.util.LinkedHashMap<>();
            double damageSum = 0;
            for (AbilityTreeEngine.Part part : spell.parts()) {
                String partId = DamageMultipliers.partId(spell.baseSpell(), part.name());

                if ("heal".equals(part.type())) {
                    double healed = SpellCalc.heal(stats, part.power(), partId);
                    parts.add(new Source(part.name(), "heal", healed, 0, false));
                    continue;
                }
                if ("total".equals(part.type())) {
                    // A total restates earlier parts, each counted as many times as it lands. Parts
                    // are declared in dependency order, so a total may legitimately build on another
                    // total that came before it.
                    double summed = 0;
                    for (java.util.Map.Entry<String, Double> hit : part.hits().entrySet()) {
                        summed += byName.getOrDefault(hit.getKey(), 0.0) * hit.getValue();
                    }
                    parts.add(new Source(part.name(), "total", summed, 0, false, null, null,
                            java.util.Map.copyOf(part.hits())));
                    byName.put(part.name(), summed);
                    totalsByName.put(part.name(), summed);
                    continue;
                }
                // A spell says how it scales and whether the weapon's speed multiplies it. A relik's
                // own swing scales as melee and ignores the speed multiplier, since its attack rate
                // already accounts for how often it lands.
                DamageCalc.Result result = DamageCalc.calculate(
                        stats,
                        weapon,
                        part.multipliers(),
                        spell.spellScaling(),
                        !spell.useAttackSpeed(),
                        part.useStrength(),
                        partId);
                double expected = result.expected(critChance);
                String name = part.name().isEmpty() ? spell.name() : part.name();
                byName.put(name, expected);
                damageSum += expected;
                parts.add(new Source(name, "", expected, 0, false, result, part.multipliers()));
            }
            if (!parts.isEmpty()) {
                // Spells 1-4 have a mana cost that item and skill point modifiers change.
                double cost = spell.baseSpell() >= 1 && spell.baseSpell() <= 4
                        ? SpellCalc.cost(stats, spell.baseSpell(), spell.cost())
                        : spell.cost();
                double casts = SpellCalc.castsPerSecond(stats, cost);

                // The ability data names the part that represents the spell; without one, the sum of
                // its damage parts is the honest headline. Inventing an extra total on top of a
                // declared one double counts.
                Double headline = totalsByName.get(spell.display());
                if (headline == null) {
                    headline = byName.get(spell.display());
                }
                if (headline == null && !totalsByName.isEmpty()) {
                    headline = totalsByName.values().stream().mapToDouble(Double::doubleValue).max().orElse(0);
                }
                if (headline == null) {
                    headline = damageSum;
                }
                spells.add(new SpellGroup(spell.name(), cost, casts, casts * headline, headline, parts));
            }
        }

        String message = spells.isEmpty()
                ? "Select abilities in the ability tree to see spell damage"
                : "";
        return new Report(melee, spells, weaponName, weapon.effectiveAttackSpeed(), message);
    }

    /** The weapon's damage profile, whether it is a dropped item or a craft. */
    private static DamageCalc.Weapon weaponOf(WynnBuild build, WynnDataSet data, BuildStats stats) {
        BuildEquipment equipment = build.equipment(EquipmentSlot.WEAPON);

        WynnItem item = BuildStats.resolveItem(equipment, data);
        if (item != null) {
            if (!item.isWeapon()) {
                return null;
            }
            int[][] damages = new int[DamageCalc.ELEMENTS][2];
            for (int i = 0; i < Identifications.DAMAGE_KEYS.size(); i++) {
                int[] range = item.damages().get(Identifications.DAMAGE_KEYS.get(i));
                damages[i] = range == null ? new int[] {0, 0} : new int[] {range[0], range[1]};
            }
            var powders = build.powders(EquipmentSlot.WEAPON);
            return new DamageCalc.Weapon(
                    PowderCalc.applyToWeaponDamage(damages, powders),
                    item.attackSpeed(),
                    powders,
                    stats.identification("atkTier"));
        }

        if (equipment instanceof BuildEquipment.Crafted crafted) {
            CraftCalc.Result result = CraftCalc.compute(crafted.craft(), data);
            if (!result.isWeapon()) {
                return null;
            }
            int[][] damages = new int[DamageCalc.ELEMENTS][2];
            damages[0] = new int[] {result.neutralDamage()[0], result.neutralDamage()[1]};
            var craftPowders = build.powders(EquipmentSlot.WEAPON);
            return new DamageCalc.Weapon(
                    PowderCalc.applyToWeaponDamage(damages, craftPowders),
                    craftedAttackSpeed(crafted.craft()),
                    craftPowders,
                    stats.identification("atkTier"));
        }
        return null;
    }

    private static String craftedAttackSpeed(CraftedItem craft) {
        return switch (craft.attackSpeed()) {
            case SLOW -> "SLOW";
            case NORMAL -> "NORMAL";
            case FAST -> "FAST";
        };
    }

    private static String weaponName(WynnBuild build, WynnDataSet data) {
        BuildEquipment equipment = build.equipment(EquipmentSlot.WEAPON);
        WynnItem item = BuildStats.resolveItem(equipment, data);
        if (item != null) {
            return item.displayName();
        }
        return equipment instanceof BuildEquipment.Crafted ? "Crafted weapon" : null;
    }
}
