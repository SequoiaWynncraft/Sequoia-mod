package com.seqwawa.seq.wynnbuilder.atree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Sliders, switches and property references, the parts of the tree that drive numbers.
 *
 * <p>Fixtures follow the upstream schema, including the shapes that broke the builder in game, but
 * are hand-written: the WynnBuilder repository is GPL-3 while this mod is MIT.
 */
class AbilityTreeEngineTest {

    /** A corruption slider scaled by a property, as the Fallen archetype's is. */
    private static final String WARRIOR_JSON =
            """
            {"Warrior": [
              {"id": 0, "display_name": "Bash", "parents": [], "cost": 0, "properties": {"hits": 1},
               "effects": [{"type": "replace_spell", "name": "Bash", "base_spell": 1, "parts": [
                 {"name": "Single Hit", "multipliers": [100, 0, 0, 0, 0, 0]},
                 {"name": "Total Damage", "hits": {"Single Hit": "0.hits"}}]}]},
              {"id": 1, "display_name": "Grasp", "parents": [0], "cost": 1, "properties": {"raw_per_corrupt": 2},
               "effects": [{"type": "stat_scaling", "slider": true, "slider_name": "Corrupted", "slider_max": 100,
                 "slider_step": 1, "output": {"type": "stat", "name": "damRaw"}, "max": 120,
                 "scaling": ["1.raw_per_corrupt"]}]},
              {"id": 2, "display_name": "Blow", "parents": [1], "cost": 1, "base_abil": 1,
               "properties": {"max_damage_bonus": 55},
               "effects": [{"type": "stat_scaling", "slider": true, "slider_name": "Corrupted",
                 "output": {"type": "stat", "name": "damMult.Enraged"}, "max": "1.max_damage_bonus",
                 "scaling": [1.5]}]},
              {"id": 3, "display_name": "Unbound", "parents": [1], "cost": 1, "base_abil": 1,
               "properties": {"raw_per_corrupt": 0.5}, "effects": []},
              {"id": 4, "display_name": "Pact", "parents": [0], "cost": 1, "properties": {"damage_boost": 30},
               "effects": [{"type": "raw_stat", "toggle": "Activate Pact", "bonuses": [
                 {"type": "stat", "name": "damMult.Pact", "value": "4.damage_boost"},
                 {"type": "prop", "abil": 0, "name": "hits", "mult": true, "value": 3}]}]},
              {"id": 5, "display_name": "Dangling", "parents": [0], "cost": 1, "properties": {},
               "effects": [{"type": "stat_scaling", "slider": true, "slider_name": "Dangling", "slider_max": 10,
                 "output": {"type": "stat", "name": "sdRaw"}, "scaling": ["99.nowhere"], "max": "nonsense"}]}
            ]}
            """;

    /**
     * Puppets: a slider several abilities extend, feeding a spell's hit count.
     *
     * <p>"Doubled" adjusts the slider and is listed before the ability that defines it, so the
     * adjustment has to wait for the definition.
     */
    private static final String SHAMAN_JSON =
            """
            {"Shaman": [
              {"id": 0, "display_name": "Totem", "parents": [], "cost": 0, "properties": {}, "effects": []},
              {"id": 3, "display_name": "Doubled", "parents": [0], "cost": 1, "properties": {},
               "effects": [{"type": "stat_scaling", "slider": true, "slider_name": "Active Puppets",
                 "behavior": "modify", "slider_max_mult": 2}]},
              {"id": 1, "display_name": "Puppets", "parents": [0], "cost": 1, "properties": {"num_puppets": 0},
               "effects": [
                 {"type": "replace_spell", "name": "Puppets", "base_spell": 5, "parts": [
                   {"name": "Puppet Hit", "multipliers": [20, 0, 0, 0, 0, 0]},
                   {"name": "Puppet DPS", "hits": {"Puppet Hit": "1.num_puppets"}}]},
                 {"type": "stat_scaling", "slider": true, "slider_name": "Active Puppets", "slider_max": 3,
                  "slider_default": 3, "output": [{"type": "prop", "abil": 1, "name": "num_puppets"}],
                  "scaling": [1]}]},
              {"id": 2, "display_name": "More Puppets", "parents": [1], "cost": 1, "base_abil": 1,
               "properties": {},
               "effects": [{"type": "stat_scaling", "slider": true, "slider_name": "Active Puppets",
                 "slider_max": 2, "slider_default": 2,
                 "output": [{"type": "prop", "abil": 1, "name": "num_puppets"}], "scaling": [0]}]},
              {"id": 4, "display_name": "Echo", "parents": [0], "cost": 1, "properties": {},
               "effects": [
                 {"type": "stat_scaling", "slider": true, "slider_name": "Copies", "slider_max": 3,
                  "output": [{"type": "stat", "name": "damMult.EchoA"}, {"type": "stat", "name": "damMult.EchoB"}],
                  "scaling": [75]},
                 {"type": "stat_scaling", "slider": true, "slider_name": "Stacks", "slider_max": 10,
                  "multiplicative": true, "output": {"type": "stat", "name": "damMult.Stacks"}, "scaling": [10]}]}
            ]}
            """;

    /**
     * An upgrade that adds a totem twice over: once to the Totem it extends, through its own
     * properties, and once to the Aura, through a bonus.
     */
    private static final String TOTEM_JSON =
            """
            {"Shaman": [
              {"id": 0, "display_name": "Totem", "parents": [], "cost": 0, "properties": {"num_totems": 1},
               "effects": [{"type": "replace_spell", "name": "Totem", "base_spell": 1, "parts": [
                 {"name": "Tick", "multipliers": [6, 0, 0, 0, 0, 6]},
                 {"name": "Tick DPS", "hits": {"Tick": "0.num_totems"}}]}]},
              {"id": 1, "display_name": "Aura", "parents": [0], "cost": 1, "properties": {"num_totems": 1},
               "effects": [{"type": "replace_spell", "name": "Aura", "base_spell": 3, "parts": [
                 {"name": "Single Wave", "multipliers": [150, 0, 0, 30, 0, 0]},
                 {"name": "First Wave", "hits": {"Single Wave": "1.num_totems"}}]}]},
              {"id": 2, "display_name": "Double Totem", "parents": [1], "cost": 1, "base_abil": 0,
               "properties": {"num_totems": 1},
               "effects": [{"type": "raw_stat", "bonuses": [{"type": "prop", "abil": 1, "name": "num_totems", "value": 1}]}]}
            ]}
            """;

    /** A major identification's ability, extending Puppets once More Puppets is taken. */
    private static final String GRANTED_JSON =
            """
            {"class": "Shaman", "base_abil": 1, "dependencies": [2], "properties": {"remnants": 0},
             "effects": [
               {"type": "stat_scaling", "slider": true, "slider_name": "Active Remnants", "slider_max": 5,
                "output": [{"type": "prop", "abil": 1, "name": "remnants"}], "scaling": [1]},
               {"type": "raw_stat", "toggle": "Activate Whip",
                "bonuses": [{"type": "stat", "name": "damMult.Whip", "value": "1.remnants"}]}]}
            """;

    private static AbilityTreeState select(String json, String playerClass, int... ids) {
        AbilityTree tree = AbilityTree.parseAll(json).get(playerClass);
        assertNotNull(tree);
        AbilityTreeState state = new AbilityTreeState(tree);
        for (int id : ids) {
            assertTrue(state.toggle(id), "fixture node " + id + " should be selectable");
        }
        return state;
    }

    private static AbilityTreeEngine.Slider slider(AbilityTreeEngine.Evaluation evaluation, String name) {
        return evaluation.sliders().stream().filter(slider -> slider.name().equals(name)).findFirst().orElse(null);
    }

    private static AbilityTreeEngine.Part part(AbilityTreeEngine.Evaluation evaluation, int spell, String name) {
        return evaluation.spells().stream()
                .filter(candidate -> candidate.baseSpell() == spell)
                .flatMap(candidate -> candidate.parts().stream())
                .filter(candidate -> candidate.name().equals(name))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void sliderScalingCanNameAProperty() {
        // "1.raw_per_corrupt" used to be read as a plain number, which threw on every frame once
        // the slider moved off zero and left the builder blank.
        AbilityTreeState state = select(WARRIOR_JSON, "Warrior", 1);

        var evaluation = AbilityTreeEngine.evaluate(state, Map.of("Corrupted", 50), Set.of());

        assertEquals(100, evaluation.statBonuses().get("damRaw"));
        assertEquals(100, slider(evaluation, "Corrupted").maximum());
    }

    @Test
    void upgradesRaiseTheReferencedPropertyAndTheCapStillHolds() {
        AbilityTreeState state = select(WARRIOR_JSON, "Warrior", 1, 3);

        var evaluation = AbilityTreeEngine.evaluate(state, Map.of("Corrupted", 50), Set.of());

        // 2.5 per percent would be 125, over the effect's own cap.
        assertEquals(120, evaluation.statBonuses().get("damRaw"));
    }

    @Test
    void aCapCanNameAProperty() {
        AbilityTreeState state = select(WARRIOR_JSON, "Warrior", 1, 2);

        var evaluation = AbilityTreeEngine.evaluate(state, Map.of("Corrupted", 50), Set.of());

        // 75 from the slider, held to the 55 the upgrade declares rather than to the ability ID.
        assertEquals(55, evaluation.statBonuses().get("damMult.Enraged"));
    }

    @Test
    void aSwitchedBonusCanNameAPropertyAndRewriteOne() {
        AbilityTreeState state = select(WARRIOR_JSON, "Warrior", 4);

        var off = AbilityTreeEngine.evaluate(state, Map.of(), Set.of());
        assertNull(off.statBonuses().get("damMult.Pact"));
        assertEquals(1.0, part(off, 1, "Total Damage").hits().get("Single Hit"));

        var on = AbilityTreeEngine.evaluate(state, Map.of(), Set.of("Activate Pact"));
        assertEquals(30, on.statBonuses().get("damMult.Pact"), "the property's value, not the ability ID");
        assertEquals(3.0, part(on, 1, "Total Damage").hits().get("Single Hit"));
    }

    @Test
    void anUnresolvableReferenceContributesNothingInsteadOfThrowing() {
        AbilityTreeState state = select(WARRIOR_JSON, "Warrior", 5);

        var evaluation = AbilityTreeEngine.evaluate(state, Map.of("Dangling", 10), Set.of());

        assertNull(evaluation.statBonuses().get("sdRaw"));
    }

    @Test
    void sliderRangeAndDefaultAccumulateAcrossAbilities() {
        AbilityTreeState state = select(SHAMAN_JSON, "Shaman", 1, 2);

        var puppets = slider(AbilityTreeEngine.evaluate(state, Map.of(), Set.of()), "Active Puppets");

        assertEquals(5, puppets.maximum());
        assertEquals(5, puppets.defaultValue());
    }

    @Test
    void anAdjustmentWaitsForTheSliderItModifies() {
        AbilityTreeState state = select(SHAMAN_JSON, "Shaman", 3, 1, 2);

        var puppets = slider(AbilityTreeEngine.evaluate(state, Map.of(), Set.of()), "Active Puppets");

        assertEquals(10, puppets.maximum(), "doubled after both abilities added their share");
    }

    @Test
    void anAdjustmentAloneCreatesNoSlider() {
        AbilityTreeState state = select(SHAMAN_JSON, "Shaman", 3);

        assertTrue(AbilityTreeEngine.evaluate(state, Map.of(), Set.of()).sliders().isEmpty());
    }

    @Test
    void aSliderCanDriveASpellThroughAProperty() {
        AbilityTreeState state = select(SHAMAN_JSON, "Shaman", 1, 2);

        var byDefault = AbilityTreeEngine.evaluate(state, Map.of(), Set.of());
        assertEquals(5.0, part(byDefault, 5, "Puppet DPS").hits().get("Puppet Hit"));

        var none = AbilityTreeEngine.evaluate(state, Map.of("Active Puppets", 0), Set.of());
        assertEquals(0.0, part(none, 5, "Puppet DPS").hits().get("Puppet Hit"), "zero puppets deal nothing");

        var tooMany = AbilityTreeEngine.evaluate(state, Map.of("Active Puppets", 40), Set.of());
        assertEquals(5.0, part(tooMany, 5, "Puppet DPS").hits().get("Puppet Hit"), "held to the slider's range");
    }

    @Test
    void everyTargetOfAnOutputListIsApplied() {
        AbilityTreeState state = select(SHAMAN_JSON, "Shaman", 4);

        var evaluation = AbilityTreeEngine.evaluate(state, Map.of("Copies", 2), Set.of());

        assertEquals(150, evaluation.statBonuses().get("damMult.EchoA"));
        assertEquals(150, evaluation.statBonuses().get("damMult.EchoB"));
    }

    @Test
    void aMultiplicativeSliderCompounds() {
        AbilityTreeState state = select(SHAMAN_JSON, "Shaman", 4);

        var evaluation = AbilityTreeEngine.evaluate(state, Map.of("Stacks", 2), Set.of());

        // 1.1 squared, not 10 twice.
        assertEquals(21, evaluation.statBonuses().get("damMult.Stacks"));
    }

    @Test
    void anUpgradeReachesOtherAbilitiesOnlyThroughItsBonuses() {
        AbilityTreeState state = select(TOTEM_JSON, "Shaman", 1, 2);

        var evaluation = AbilityTreeEngine.evaluate(state, Map.of(), Set.of());

        assertEquals(2.0, part(evaluation, 1, "Tick DPS").hits().get("Tick"), "merged into the Totem");
        assertEquals(2.0, part(evaluation, 3, "First Wave").hits().get("Single Wave"),
                "one from the Aura, one from the bonus, not a third from the shared property name");
    }

    @Test
    void grantedAbilitiesJoinTheTreeOnceTheirDependenciesAreTaken() {
        JsonObject granted = JsonParser.parseString(GRANTED_JSON).getAsJsonObject();

        var withoutDependency = AbilityTreeEngine.evaluate(
                select(SHAMAN_JSON, "Shaman", 1), Map.of(), Set.of(), Map.of(), List.of(granted));
        assertNull(slider(withoutDependency, "Active Remnants"));
        assertFalse(withoutDependency.toggles().contains("Activate Whip"));

        var evaluation = AbilityTreeEngine.evaluate(
                select(SHAMAN_JSON, "Shaman", 1, 2),
                Map.of("Active Remnants", 4),
                Set.of("Activate Whip"),
                Map.of(),
                List.of(granted));
        assertNotNull(slider(evaluation, "Active Remnants"));
        assertEquals(4, evaluation.statBonuses().get("damMult.Whip"));
    }

    @Test
    void grantedAbilitiesOfAnotherClassAreIgnored() {
        JsonObject granted = JsonParser.parseString(GRANTED_JSON).getAsJsonObject();
        granted.addProperty("class", "Mage");

        var evaluation = AbilityTreeEngine.evaluate(
                select(SHAMAN_JSON, "Shaman", 1, 2), Map.of(), Set.of(), Map.of(), List.of(granted));

        assertNull(slider(evaluation, "Active Remnants"));
    }
}
