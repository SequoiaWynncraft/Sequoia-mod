package com.seqwawa.seq.wynnbuilder.calc;

import com.google.gson.JsonObject;
import com.seqwawa.seq.wynnbuilder.atree.AbilityTreeEngine;
import com.seqwawa.seq.wynnbuilder.atree.AbilityTreeState;
import com.seqwawa.seq.wynnbuilder.data.MajorIds;
import com.seqwawa.seq.wynnbuilder.data.WynnBuild;
import com.seqwawa.seq.wynnbuilder.data.WynnDataSet;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Runs a build through the whole pipeline and hands back both halves of the answer.
 *
 * <p>The order is not arbitrary and cannot be collapsed into one pass. Abilities can scale off the
 * build's own totals, so the totals have to exist before the abilities are evaluated; and major
 * identifications can rewrite a spell's properties, so they have to be known before the spells are
 * assembled rather than merged into the statistics afterwards. That gives: evaluate abilities
 * against nothing, compute provisional statistics, collect what the major IDs change, evaluate the
 * abilities again against real numbers, then compute the statistics that are actually shown. A third
 * pass would chase a fixed point for a bonus that is small by construction.
 *
 * <p>Extracted from the builder screen's session so the equipped-gear panel runs the identical
 * pipeline: two implementations of this would drift, and the damage numbers are the whole point of
 * both.
 */
public record BuildEvaluation(BuildStats stats, AbilityTreeEngine.Evaluation evaluation) {

    public static BuildEvaluation empty() {
        return new BuildEvaluation(null, AbilityTreeEngine.Evaluation.empty());
    }

    /**
     * Evaluates a build.
     *
     * @param treeState the selected abilities, or {@code null} when the class or tree is unknown
     * @param extraBonuses stats from outside the gear and the tree — raid buffs, powder specials,
     *     boosts other players provide, and mastery tomes
     * @param extraMajorIds major identifications that do not come from the gear itself
     * @param skillPointMultiplier scales the skill points gear grants, as Radiance does
     */
    public static BuildEvaluation compute(
            WynnBuild build,
            WynnDataSet data,
            IdentificationRolls.RollMode rollMode,
            AbilityTreeState treeState,
            Map<String, Integer> sliderValues,
            Set<String> toggles,
            Map<String, Integer> extraBonuses,
            List<String> extraMajorIds,
            double skillPointMultiplier) {

        if (build == null || data == null) {
            return empty();
        }
        Map<String, Integer> extras = extraBonuses == null ? Map.of() : extraBonuses;
        List<String> extraIds = extraMajorIds == null ? List.of() : extraMajorIds;

        AbilityTreeEngine.Evaluation firstPass =
                evaluate(treeState, sliderValues, toggles, Map.of(), Map.of());
        BuildStats provisional = BuildStats.compute(
                build, data, rollMode, merge(firstPass.statBonuses(), extras), skillPointMultiplier);

        List<JsonObject> majorIdAbilities = majorIdAbilities(data.majorIds(), provisional.majorIds(), extraIds);
        Map<String, double[]> propertyModifiers =
                AbilityTreeEngine.collectPropertyModifiers(majorIdAbilities);

        AbilityTreeEngine.Evaluation secondPass = evaluate(
                treeState, sliderValues, toggles, provisional.identifications(), propertyModifiers);

        Map<String, Integer> combined = merge(secondPass.statBonuses(), extras);
        // Major identifications carry ability effects of their own, and they read the totals the
        // earlier pass produced.
        AbilityTreeEngine.applyAbilityEffects(
                        majorIdAbilities, sliderValues, toggles, provisional.identifications())
                .forEach((key, value) -> combined.merge(key, value, Integer::sum));

        BuildStats stats = BuildStats.compute(build, data, rollMode, combined, skillPointMultiplier);
        return new BuildEvaluation(stats, secondPass);
    }

    /** The damage every source in the build deals, spells included. */
    public DamageSources.Report damage(WynnBuild build, WynnDataSet data) {
        if (stats == null) {
            return new DamageSources.Report(null, List.of(), null, null, "No build to measure");
        }
        return DamageSources.compute(build, data, stats, evaluation);
    }

    private static AbilityTreeEngine.Evaluation evaluate(
            AbilityTreeState treeState,
            Map<String, Integer> sliderValues,
            Set<String> toggles,
            Map<String, Integer> buildStats,
            Map<String, double[]> propertyModifiers) {

        if (treeState == null) {
            return AbilityTreeEngine.Evaluation.empty();
        }
        return AbilityTreeEngine.evaluate(
                treeState,
                sliderValues == null ? Map.of() : sliderValues,
                toggles == null ? Set.of() : toggles,
                buildStats,
                propertyModifiers);
    }

    /** The ability definitions every major identification on the build contributes. */
    private static List<JsonObject> majorIdAbilities(
            MajorIds majorIds, List<String> fromGear, List<String> fromElsewhere) {

        List<JsonObject> abilities = new ArrayList<>();
        if (majorIds == null) {
            return abilities;
        }
        List<String> names = new ArrayList<>(fromGear);
        names.addAll(fromElsewhere);
        Set<String> seen = new HashSet<>();
        for (String name : names) {
            if (!seen.add(name)) {
                // A major ID granted twice still only applies once.
                continue;
            }
            MajorIds.Entry entry = majorIds.get(name);
            if (entry != null) {
                abilities.addAll(entry.abilities());
            }
        }
        return abilities;
    }

    private static Map<String, Integer> merge(Map<String, Integer> left, Map<String, Integer> right) {
        Map<String, Integer> combined = new LinkedHashMap<>(left);
        right.forEach((key, value) -> combined.merge(key, value, Integer::sum));
        return combined;
    }
}
