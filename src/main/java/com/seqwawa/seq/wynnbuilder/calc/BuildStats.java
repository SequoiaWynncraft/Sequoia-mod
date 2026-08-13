package com.seqwawa.seq.wynnbuilder.calc;

import com.seqwawa.seq.wynnbuilder.data.BuildEquipment;
import com.seqwawa.seq.wynnbuilder.data.EquipmentSlot;
import com.seqwawa.seq.wynnbuilder.data.Identifications;
import com.seqwawa.seq.wynnbuilder.data.Powder;
import com.seqwawa.seq.wynnbuilder.data.WynnBuild;
import com.seqwawa.seq.wynnbuilder.data.WynnDataSet;
import com.seqwawa.seq.wynnbuilder.data.WynnItem;
import com.seqwawa.seq.wynnbuilder.data.WynnSet;
import com.seqwawa.seq.wynnbuilder.data.WynnTome;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Aggregated statistics for a build: identifications, health, defences and skill points.
 *
 * <p>Everything the build contributes is summed here — items, tomes, powders and the ability tree's
 * flat bonuses — and the skill point allocation is solved so requirements can be checked.
 */
public record BuildStats(
        Map<String, Integer> identifications,
        Map<String, Integer> activeSets,
        int[] skillPointTotals,
        int[] assignedSkillPoints,
        int assignedTotal,
        int availableSkillPoints,
        boolean skillPointsValid,
        int health,
        int[] elementalDefences,
        List<String> majorIds,
        List<String> problems,
        Map<EquipmentSlot, WynnItem> resolvedItems) {

    public BuildStats {
        identifications = Map.copyOf(identifications);
        activeSets = Map.copyOf(activeSets);
        skillPointTotals = skillPointTotals.clone();
        assignedSkillPoints = assignedSkillPoints.clone();
        elementalDefences = elementalDefences.clone();
        majorIds = List.copyOf(majorIds);
        problems = List.copyOf(problems);
        resolvedItems = Map.copyOf(resolvedItems);
    }

    /** A stat's aggregated value, or zero when the build does not have it. */
    public int identification(String key) {
        return identifications.getOrDefault(key, 0);
    }

    /**
     * Effective health: how much raw damage the build absorbs once defences are accounted for.
     *
     * <p>Agility gives a dodge chance and defence a damage reduction, so both scale health rather
     * than adding to it.
     */
    public int effectiveHealth() {
        // Defence reduces the damage taken; agility instead avoids it entirely some of the time, so
        // the two combine as a weighted average rather than multiplying.
        double defencePercent = SkillPoints.percentage(3, skillPointTotals[3]);
        double dodgePercent = SkillPoints.percentage(4, skillPointTotals[4]);
        double reduction = DamageMultipliers.defence(identifications);
        double denominator = (dodgePercent * 0 + (1 - dodgePercent) * (1 - defencePercent)) * reduction;
        if (denominator <= 0.0001) {
            return Integer.MAX_VALUE;
        }
        return (int) Math.round(health / denominator);
    }

    /** Effective health assuming nothing is dodged, which is the figure to plan around. */
    public int effectiveHealthWithoutDodge() {
        double defencePercent = SkillPoints.percentage(3, skillPointTotals[3]);
        double denominator = (1 - defencePercent) * DamageMultipliers.defence(identifications);
        if (denominator <= 0.0001) {
            return Integer.MAX_VALUE;
        }
        return (int) Math.round(health / denominator);
    }

    /** Whether every equipped item's requirements are satisfied. */
    public boolean isValid() {
        return problems.isEmpty();
    }

    /** Builds the statistics for a build against a data set. */
    public static BuildStats compute(
            WynnBuild build,
            WynnDataSet data,
            IdentificationRolls.RollMode rollMode,
            Map<String, Integer> abilityTreeBonuses) {
        return compute(build, data, rollMode, abilityTreeBonuses, 1.0);
    }

    /**
     * Builds the statistics, scaling the skill points items grant.
     *
     * @param itemSkillPointMultiplier applied to skill points granted by gear, which is how Radiance
     *     and its siblings work: they do not add points, they amplify what the gear already gives
     */
    public static BuildStats compute(
            WynnBuild build,
            WynnDataSet data,
            IdentificationRolls.RollMode rollMode,
            Map<String, Integer> abilityTreeBonuses,
            double itemSkillPointMultiplier) {

        Map<String, Integer> totals = new LinkedHashMap<>();
        List<String> majorIds = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        Map<EquipmentSlot, WynnItem> resolved = new EnumMap<>(EquipmentSlot.class);

        int health = SkillPoints.levelToBaseHealth(build.level());
        int[] defences = new int[5];

        List<SkillPoints.Item> skillPointItems = new ArrayList<>();
        SkillPoints.Item weaponSkillPoints = SkillPoints.Item.empty();
        Map<String, Integer> setCounts = new LinkedHashMap<>();

        for (EquipmentSlot slot : EquipmentSlot.encodingOrder()) {
            BuildEquipment equipment = build.equipment(slot);
            WynnItem item = equipment instanceof BuildEquipment.Normal normal ? data.item(normal.itemId()) : null;
            int powderCapacity = 0;

            if (item != null) {
                resolved.put(slot, item);
                powderCapacity = item.powderSlots();
                health += item.baseHealth();
                addAll(totals, IdentificationRolls.resolve(item, rollMode));
                majorIds.addAll(item.majorIds());
                for (int i = 0; i < Identifications.DEFENCE_KEYS.size(); i++) {
                    defences[i] += item.baseDefences().getOrDefault(Identifications.DEFENCE_KEYS.get(i), 0);
                }
                if (item.level() > build.level()) {
                    problems.add(item.displayName() + " requires level " + item.level());
                }
                if (item.setName() != null) {
                    setCounts.merge(item.setName(), 1, Integer::sum);
                }
            }

            // A crafted piece is real gear: it carries health, identifications and requirements just
            // like a dropped item, and leaving it out silently understates the whole build.
            CraftCalc.Result craftResult = null;
            if (equipment instanceof BuildEquipment.Crafted crafted) {
                craftResult = CraftCalc.compute(crafted.craft(), data);
                powderCapacity = craftResult.powderSlots();
                if (craftResult.isArmour()) {
                    health += pick(craftResult.healthOrDamage(), rollMode, false);
                }
                for (Map.Entry<String, int[]> entry : craftResult.identificationRanges().entrySet()) {
                    int value = pick(entry.getValue(), rollMode, Identifications.isInverted(entry.getKey()));
                    if (value != 0) {
                        totals.merge(entry.getKey(), value, Integer::sum);
                    }
                }
                if (craftResult.levelMin() > build.level()) {
                    problems.add("Crafted " + craftResult.type() + " requires level " + craftResult.levelMin());
                }
            }

            SkillPoints.Item skillPointItem =
                    toSkillPointItem(item, craftResult, equipment, itemSkillPointMultiplier);
            if (slot == EquipmentSlot.WEAPON) {
                weaponSkillPoints = skillPointItem;
            } else {
                skillPointItems.add(skillPointItem);
            }

            // Armour powders add health and shift defences; the weapon's are handled by the damage
            // pipeline instead, since there they convert damage rather than defend.
            List<Powder> powders = build.powders(slot);
            if (!powders.isEmpty() && slot.isArmour()) {
                PowderCalc.ArmourEffect effect = PowderCalc.applyToArmour(powders);
                health += effect.health();
                for (int i = 0; i < defences.length; i++) {
                    defences[i] += effect.defences()[i];
                }
            }
            if (!powders.isEmpty() && powders.size() > powderCapacity) {
                String name = item != null ? item.displayName() : slot.label();
                problems.add(name + " has more powders than it has slots");
            }
        }

        for (int i = 0; i < build.tomeIds().size(); i++) {
            Integer tomeId = build.tomeIds().get(i);
            if (tomeId == null) {
                continue;
            }
            WynnTome tome = data.tome(tomeId);
            if (tome != null) {
                addAll(totals, tome.identifications());
            }
        }

        // Set bonuses depend on how many pieces of each set ended up equipped, so they are applied
        // once the whole loadout is known.
        Map<String, Integer> activeSets = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : setCounts.entrySet()) {
            WynnSet set = data.set(entry.getKey());
            if (set == null) {
                continue;
            }
            int count = entry.getValue();
            if (set.isIllegalAt(count)) {
                problems.add("The " + set.name() + " set cannot be worn with " + count + " pieces");
                continue;
            }
            Map<String, Integer> bonus = set.bonusFor(count);
            if (!bonus.isEmpty()) {
                activeSets.put(set.name(), count);
                addAll(totals, bonus);
            }
        }

        if (abilityTreeBonuses != null) {
            addAll(totals, abilityTreeBonuses);
        }

        SkillPoints.Allocation allocation = SkillPoints.allocate(skillPointItems, weaponSkillPoints);

        int[] assigned = allocation.assigned().clone();
        int[] skillTotals = allocation.totals().clone();

        // Item and tome skill point bonuses land in the identification totals too, and count towards
        // the totals used by the damage and defence curves.
        if (itemSkillPointMultiplier != 1.0) {
            // Scale the granted points in the totals too, so the skill point panel and the damage
            // curves read the same numbers.
            for (String key : Identifications.SKILL_POINT_KEYS) {
                Integer granted = totals.get(key);
                if (granted != null) {
                    totals.put(key, (int) Math.floor(granted * itemSkillPointMultiplier));
                }
            }
        }

        for (int i = 0; i < SkillPoints.TYPES; i++) {
            String key = Identifications.SKILL_POINT_KEYS.get(i);
            int bonus = totals.getOrDefault(key, 0);
            int itemBonus = bonus - sumItemSkillPoints(skillPointItems, weaponSkillPoints, i);
            if (itemBonus != 0) {
                skillTotals[i] += itemBonus;
            }
        }

        // A manual skill point value is the element's *final total*, gear bonuses included, not the
        // number of points spent on it: that is the number the site puts in its skill box and the
        // number a link carries. Reading it as an assignment would count the gear's bonus twice and
        // inflate the build past its point budget. The spend is what is left once the gear's share
        // is taken back out, which is why the solver's own assignment is the baseline here.
        for (int i = 0; i < SkillPoints.TYPES; i++) {
            Integer manual = build.assignedSkillPoint(i);
            if (manual != null) {
                assigned[i] += manual - skillTotals[i];
                skillTotals[i] = manual;
            }
        }

        int assignedTotal = 0;
        for (int value : assigned) {
            assignedTotal += value;
        }
        int available = SkillPoints.levelToSkillPoints(build.level());
        boolean skillPointsValid = allocation.valid() && assignedTotal <= available;
        if (assignedTotal > available) {
            problems.add("Needs " + assignedTotal + " skill points but level " + build.level()
                    + " only grants " + available);
        }

        health += totals.getOrDefault("hpBonus", 0);

        // Elemental defence percentages scale the flat defences.
        for (int i = 0; i < defences.length; i++) {
            String percentKey = Identifications.ELEMENT_PREFIXES.get(i + 1) + "DefPct";
            int percent = totals.getOrDefault(percentKey, 0);
            if (percent != 0) {
                defences[i] = (int) Math.round(defences[i] * (1 + percent / 100.0));
            }
        }

        return new BuildStats(
                totals,
                activeSets,
                skillTotals,
                assigned,
                assignedTotal,
                available,
                skillPointsValid,
                Math.max(1, health),
                defences,
                majorIds,
                problems,
                resolved);
    }

    private static int sumItemSkillPoints(List<SkillPoints.Item> items, SkillPoints.Item weapon, int index) {
        int total = weapon.bonuses()[index];
        for (SkillPoints.Item item : items) {
            total += item.bonuses()[index];
        }
        return total;
    }

    /**
     * Reduces a slot to what skill point allocation needs: requirements and granted points.
     *
     * <p>A crafted piece is flagged so the solver knows its own bonuses cannot pay for its own
     * requirements, which is how the game behaves.
     */
    private static SkillPoints.Item toSkillPointItem(
            WynnItem item, CraftCalc.Result craft, BuildEquipment equipment, double skillPointMultiplier) {

        int[] requirements = new int[SkillPoints.TYPES];
        int[] bonuses = new int[SkillPoints.TYPES];

        if (item != null) {
            for (int i = 0; i < SkillPoints.TYPES; i++) {
                requirements[i] = item.requirements().getOrDefault(Identifications.REQUIREMENT_KEYS.get(i), 0);
                int granted = item.identifications().getOrDefault(Identifications.SKILL_POINT_KEYS.get(i), 0);
                bonuses[i] = (int) Math.floor(granted * skillPointMultiplier);
            }
        } else if (craft != null) {
            for (int i = 0; i < SkillPoints.TYPES; i++) {
                requirements[i] = craft.requirements().getOrDefault(Identifications.REQUIREMENT_KEYS.get(i), 0);
                int[] range = craft.identificationRanges().get(Identifications.SKILL_POINT_KEYS.get(i));
                // Skill points a craft grants are taken at their best value, as the game rolls them
                // once and the builder shows the achievable build.
                bonuses[i] = range == null ? 0 : Math.max(range[0], range[1]);
            }
        }
        return new SkillPoints.Item(requirements, bonuses, equipment instanceof BuildEquipment.Crafted);
    }

    /**
     * Picks one end of a crafted range for the chosen roll mode.
     *
     * <p>Crafted ranges are stored in numeric order, so which end is the good one depends on whether
     * a higher value helps: for a spell cost reduction the lower number is the better roll.
     */
    /**
     * The value a crafted identification contributes.
     *
     * <p>Always the best end of the range, whatever roll mode the player is viewing. A dropped item
     * rolls once and randomly, so showing its average is honest; a craft's range comes from its
     * ingredients, and the build being described is the one worth crafting. Upstream reads crafted
     * damage the same way, and it is what makes this build's strength and dexterity land on the
     * values the site's own crit ratio implies.
     */
    private static int pick(int[] range, IdentificationRolls.RollMode mode, boolean inverted) {
        return inverted ? range[0] : range[1];
    }

    private static void addAll(Map<String, Integer> totals, Map<String, Integer> values) {
        for (Map.Entry<String, Integer> entry : values.entrySet()) {
            totals.merge(entry.getKey(), entry.getValue(), Integer::sum);
        }
    }
}
