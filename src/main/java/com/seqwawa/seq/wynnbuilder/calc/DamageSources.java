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

        Source melee = null;
        List<SpellGroup> spells = new ArrayList<>();
        for (AbilityTreeEngine.Spell spell : evaluation.spells()) {
            SpellGroup group = evaluate(spell, stats, weapon, critChance);
            if (group == null) {
                continue;
            }
            if (spell.baseSpell() == 0) {
                // The basic attack is spell 0, which the tree reshapes like any other: Spear
                // Proficiency raises its multiplier, Bloodied Armory adds swipes to it. Its headline is
                // one attack, and the swing rate turns that into damage per second.
                melee = new Source(group.name(), "per hit", group.headline(),
                        group.headline() * DamageCalc.attacksPerSecond(weapon.effectiveAttackSpeed()), false);
                continue;
            }
            spells.add(group);
        }
        if (melee == null) {
            // No ability tree to shape the attack, as when its data is unavailable: a plain swing.
            DamageCalc.Result meleeHit = DamageCalc.meleeHit(stats, weapon);
            melee = new Source("Melee", "per hit", meleeHit.expected(critChance),
                    DamageCalc.meleeDps(stats, weapon), false);
        }

        String message = spells.isEmpty()
                ? "Select abilities in the ability tree to see spell damage"
                : "";
        return new Report(melee, spells, weaponName, weapon.effectiveAttackSpeed(), message);
    }

    /** What one part works out to: expected damage or healing, and the calculation behind a hit. */
    private record Evaluated(boolean heal, double amount, DamageCalc.Result result) {}

    /**
     * Works out every part of a spell the way upstream does.
     *
     * <p>A total names the parts it adds up, and those are looked up by name wherever they sit in the
     * spell, a total included. A total of heals is itself a heal. Parts the data hides are still
     * worked out, since totals build on them, but are not listed.
     */
    private static SpellGroup evaluate(
            AbilityTreeEngine.Spell spell, BuildStats stats, DamageCalc.Weapon weapon, double critChance) {
        java.util.Map<String, AbilityTreeEngine.Part> byName = new java.util.LinkedHashMap<>();
        for (AbilityTreeEngine.Part part : spell.parts()) {
            byName.put(part.name(), part);
        }
        java.util.Map<String, Evaluated> done = new java.util.HashMap<>();
        java.util.function.Function<String, Evaluated> lookup = new java.util.function.Function<>() {
            private final java.util.Set<String> visiting = new java.util.HashSet<>();

            @Override
            public Evaluated apply(String name) {
                Evaluated known = done.get(name);
                AbilityTreeEngine.Part part = byName.get(name);
                // A total that names itself, directly or not, would recurse forever.
                if (known != null || part == null || !visiting.add(name)) {
                    return known;
                }
                Evaluated result = evaluatePart(spell, part, this, stats, weapon, critChance);
                visiting.remove(name);
                done.put(name, result);
                return result;
            }
        };

        List<Source> parts = new ArrayList<>();
        double damageSum = 0;
        double largestTotal = -1;
        for (AbilityTreeEngine.Part part : spell.parts()) {
            Evaluated result = lookup.apply(part.name());
            if (result == null) {
                continue;
            }
            boolean total = "total".equals(part.type());
            if (!result.heal() && !total) {
                damageSum += result.amount();
            }
            if (!result.heal() && total) {
                largestTotal = Math.max(largestTotal, result.amount());
            }
            if (!part.display()) {
                continue;
            }
            String name = part.name().isEmpty() ? spell.name() : part.name();
            if (total) {
                parts.add(new Source(name, result.heal() ? "heal" : "total", result.amount(), 0, false, null, null,
                        java.util.Map.copyOf(part.hits())));
            } else if (result.heal()) {
                parts.add(new Source(name, "heal", result.amount(), 0, false));
            } else {
                parts.add(new Source(name, "", result.amount(), 0, false, result.result(), part.multipliers()));
            }
        }
        if (parts.isEmpty()) {
            return null;
        }
        // Spells 1-4 have a mana cost that item and skill point modifiers change.
        double cost = spell.baseSpell() >= 1 && spell.baseSpell() <= 4
                ? SpellCalc.cost(stats, spell.baseSpell(), spell.cost())
                : spell.cost();
        double casts = SpellCalc.castsPerSecond(stats, cost);

        // The ability data names the part that represents the spell; without one, the largest total,
        // or failing that the sum of its damage parts, is the honest headline. Inventing an extra
        // total on top of a declared one double counts.
        Evaluated displayed = done.get(spell.display());
        double headline = displayed != null && !displayed.heal()
                ? displayed.amount()
                : largestTotal >= 0 ? largestTotal : damageSum;
        return new SpellGroup(spell.name(), cost, casts, casts * headline, headline, parts);
    }

    private static Evaluated evaluatePart(
            AbilityTreeEngine.Spell spell,
            AbilityTreeEngine.Part part,
            java.util.function.Function<String, Evaluated> lookup,
            BuildStats stats,
            DamageCalc.Weapon weapon,
            double critChance) {
        String partId = DamageMultipliers.partId(spell.baseSpell(), part.name());
        switch (part.type()) {
            case "damage" -> {
                // A spell says how it scales and whether the weapon's speed multiplies it. The basic
                // attack scales as melee and ignores the speed multiplier, since its attack rate
                // already accounts for how often it lands.
                DamageCalc.Result result = DamageCalc.calculate(
                        stats,
                        weapon,
                        part.multipliers(),
                        spell.spellScaling(),
                        !spell.useAttackSpeed(),
                        part.useStrength(),
                        partId,
                        part.ignoredMults());
                return new Evaluated(false, result.expected(critChance), result);
            }
            case "heal" -> {
                return new Evaluated(true, SpellCalc.heal(stats, part.power(), partId), null);
            }
            default -> {
                Boolean heal = null;
                double amount = 0;
                for (java.util.Map.Entry<String, Double> hit : part.hits().entrySet()) {
                    Evaluated counted = lookup.apply(hit.getKey());
                    // Upstream refuses to add damage to healing; skipping the odd one out is kinder.
                    if (counted == null || (heal != null && heal != counted.heal())) {
                        continue;
                    }
                    heal = counted.heal();
                    double hits = part.tickRounding() ? tickRounded(hit.getValue()) : hit.getValue();
                    amount += counted.amount() * hits;
                }
                return new Evaluated(heal != null && heal, amount, null);
            }
        }
    }

    /**
     * Snaps a rate to what the game can deliver: effects land on ticks, twenty a second, so the gap
     * between two hits is a whole number of ticks. Three hits a second really lands every six ticks,
     * which is 3.33 a second.
     */
    private static double tickRounded(double hitsPerSecond) {
        double ticksBetweenHits = Math.floor(1.0 / hitsPerSecond * 20);
        return ticksBetweenHits <= 0 ? hitsPerSecond : 1.0 / (ticksBetweenHits * 0.05);
    }

    /** The weapon's damage profile, whether it is a dropped item or a craft. */
    private static DamageCalc.Weapon weaponOf(WynnBuild build, WynnDataSet data, BuildStats stats) {
        BuildEquipment equipment = build.equipment(EquipmentSlot.WEAPON);

        if (equipment instanceof BuildEquipment.Normal normal) {
            WynnItem item = data.item(normal.itemId());
            if (item == null || !item.isWeapon()) {
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
        if (equipment instanceof BuildEquipment.Normal normal) {
            WynnItem item = data.item(normal.itemId());
            return item == null ? "Unknown weapon" : item.displayName();
        }
        return equipment instanceof BuildEquipment.Crafted ? "Crafted weapon" : null;
    }
}
