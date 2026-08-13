package com.seqwawa.seq.wynnbuilder.atree;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.seqwawa.seq.wynnbuilder.data.Identifications;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Evaluates the selected abilities into stat bonuses and spell definitions.
 *
 * <p>The whole tree uses only four effect kinds:
 *
 * <ul>
 *   <li>{@code raw_stat} adds flat bonuses, sometimes behind a toggle the player controls.
 *   <li>{@code stat_scaling} converts one stat into another, often driven by a slider.
 *   <li>{@code replace_spell} defines or redefines a spell and its damage parts.
 *   <li>{@code add_spell_prop} adjusts an existing spell part.
 * </ul>
 */
public final class AbilityTreeEngine {

    private AbilityTreeEngine() {}

    /** Everything the tree contributes to a build. */
    public record Evaluation(
            Map<String, Integer> statBonuses,
            List<Spell> spells,
            List<String> toggles,
            List<Slider> sliders) {
        public Evaluation {
            statBonuses = Map.copyOf(statBonuses);
            spells = List.copyOf(spells);
            toggles = List.copyOf(toggles);
            sliders = List.copyOf(sliders);
        }

        public static Evaluation empty() {
            return new Evaluation(Map.of(), List.of(), List.of(), List.of());
        }
    }

    /**
     * Property rewrites an ability set carries, keyed {@code "<abilityId>.<property>"}.
     *
     * <p>Each entry is {@code {addend, multiplier}}, applied in that order. These come from major
     * identifications, whose whole point is often to trade one property of a spell against another.
     */
    public static Map<String, double[]> collectPropertyModifiers(List<JsonObject> abilities) {
        Map<String, double[]> modifiers = new LinkedHashMap<>();
        for (JsonObject ability : abilities) {
            JsonElement effects = ability.get("effects");
            if (effects == null || !effects.isJsonArray()) {
                continue;
            }
            for (JsonElement element : effects.getAsJsonArray()) {
                if (element == null || !element.isJsonObject()) {
                    continue;
                }
                JsonElement bonuses = element.getAsJsonObject().get("bonuses");
                if (bonuses == null || !bonuses.isJsonArray()) {
                    continue;
                }
                for (JsonElement entry : bonuses.getAsJsonArray()) {
                    if (entry == null || !entry.isJsonObject()) {
                        continue;
                    }
                    JsonObject bonus = entry.getAsJsonObject();
                    if (!"prop".equals(string(bonus, "type"))) {
                        continue;
                    }
                    Integer abil = optionalInt(bonus.get("abil"));
                    String name = string(bonus, "name");
                    JsonElement value = bonus.get("value");
                    if (abil == null || name == null || value == null || !value.isJsonPrimitive()) {
                        continue;
                    }
                    JsonElement mult = bonus.get("mult");
                    boolean multiplicative = mult != null && mult.isJsonPrimitive() && mult.getAsBoolean();
                    double[] modifier =
                            modifiers.computeIfAbsent(abil + "." + name, key -> new double[] {0, 1});
                    if (multiplicative) {
                        modifier[1] *= value.getAsDouble();
                    } else {
                        modifier[0] += value.getAsDouble();
                    }
                }
            }
        }
        return modifiers;
    }

    /** A value the player sets, such as hits landed, that an ability scales from. */
    public record Slider(String name, int maximum) {}

    /** A spell after every replacement and adjustment has been applied. */
    /**
     * A spell the ability tree defines.
     *
     * @param spellScaling whether the spell scales off spell damage or off melee damage; a relik's
     *     own swing is a spell here, and it scales as melee
     * @param useAttackSpeed whether the weapon's speed multiplies the damage, which a melee swing
     *     deliberately does not do since its rate already accounts for it
     */
    public record Spell(
            int baseSpell,
            String name,
            int cost,
            String display,
            boolean spellScaling,
            boolean useAttackSpeed,
            List<Part> parts) {
        public Spell {
            parts = List.copyOf(parts);
        }
    }

    /**
     * One component of a spell.
     *
     * @param multipliers damage share per element in n/e/t/w/f/a order, as percentages
     * @param hits for a total part, how many times each named part lands
     */
    public record Part(
            String name,
            String type,
            double[] multipliers,
            Map<String, Double> hits,
            boolean useStrength,
            double power) {
        public Part {
            multipliers = multipliers.clone();
            hits = Map.copyOf(hits);
        }
    }

    /**
     * Evaluates the active selection.
     *
     * @param sliderValues values chosen for slider-driven scaling effects, keyed by slider name
     * @param enabledToggles toggles the player has switched on
     */
    public static Evaluation evaluate(
            AbilityTreeState state, Map<String, Integer> sliderValues, java.util.Set<String> enabledToggles) {
        return evaluate(state, sliderValues, enabledToggles, Map.of());
    }

    /**
     * Evaluates the active selection, resolving effects that scale off the build's own stats.
     *
     * @param buildStats the totals from a previous pass, so an effect reading "per point of
     *     intelligence" has something to read; pass an empty map on the first pass
     */
    public static Evaluation evaluate(
            AbilityTreeState state,
            Map<String, Integer> sliderValues,
            java.util.Set<String> enabledToggles,
            Map<String, Integer> buildStats) {
        return evaluate(state, sliderValues, enabledToggles, buildStats, Map.of());
    }

    /**
     * Evaluates the active selection, with property rewrites from major identifications applied.
     *
     * @param propertyModifiers from {@link #collectPropertyModifiers}
     */
    public static Evaluation evaluate(
            AbilityTreeState state,
            Map<String, Integer> sliderValues,
            java.util.Set<String> enabledToggles,
            Map<String, Integer> buildStats,
            Map<String, double[]> propertyModifiers) {
        if (state == null || state.tree() == null || state.tree().isEmpty()) {
            return Evaluation.empty();
        }
        Map<String, Integer> stats = new LinkedHashMap<>();
        Map<Integer, Spell> spells = new LinkedHashMap<>();
        List<String> toggles = new ArrayList<>();
        Map<String, Integer> sliderMaximums = new LinkedHashMap<>();
        java.util.function.ToDoubleFunction<String> properties = propertyResolver(state, propertyModifiers);

        // Two passes over the active abilities. Spells must all be defined before anything adjusts
        // them: an "add_spell_prop" declared on a node that happens to sit earlier in the tree than
        // the "replace_spell" defining its target would otherwise find nothing and be dropped.
        for (AbilityNode node : state.tree().nodes()) {
            if (!state.isActive(node.id())) {
                continue;
            }
            for (AbilityNode.Effect effect : node.effects()) {
                switch (effect.type()) {
                    case "raw_stat" -> applyRawStat(effect.raw(), stats, toggles, enabledToggles, toggleCount(node));
                    case "stat_scaling" ->
                            applyStatScaling(effect.raw(), stats, sliderValues, buildStats, sliderMaximums);
                    case "replace_spell" -> {
                        Spell spell = parseSpell(effect.raw(), properties);
                        if (spell != null) {
                            spells.put(spell.baseSpell(), spell);
                        }
                    }
                    default -> {
                        // Adjustments wait for the second pass.
                    }
                }
            }
        }
        for (AbilityNode node : state.tree().nodes()) {
            if (!state.isActive(node.id())) {
                continue;
            }
            for (AbilityNode.Effect effect : node.effects()) {
                if ("add_spell_prop".equals(effect.type())) {
                    applySpellProperty(effect.raw(), spells, properties);
                }
            }
        }

        List<Slider> sliders = new ArrayList<>();
        sliderMaximums.forEach((name, maximum) -> sliders.add(new Slider(name, maximum)));
        return new Evaluation(stats, new ArrayList<>(spells.values()), toggles, sliders);
    }

    /**
     * Applies a set of raw ability definitions, as major identifications supply them.
     *
     * <p>Major IDs modify abilities with the same effect vocabulary as the tree, so they run through
     * the same handlers rather than being special-cased one by one.
     *
     * @param abilities objects each carrying an {@code effects} array
     */
    public static Map<String, Integer> applyAbilityEffects(
            List<com.google.gson.JsonObject> abilities,
            Map<String, Integer> sliderValues,
            java.util.Set<String> enabledToggles,
            Map<String, Integer> buildStats) {

        Map<String, Integer> stats = new LinkedHashMap<>();
        List<String> toggles = new ArrayList<>();
        Map<String, Integer> sliderMaximums = new LinkedHashMap<>();
        for (com.google.gson.JsonObject ability : abilities) {
            JsonElement effects = ability.get("effects");
            if (effects == null || !effects.isJsonArray()) {
                continue;
            }
            for (JsonElement element : effects.getAsJsonArray()) {
                if (element == null || !element.isJsonObject()) {
                    continue;
                }
                JsonObject effect = element.getAsJsonObject();
                JsonElement type = effect.get("type");
                if (type == null || !type.isJsonPrimitive()) {
                    continue;
                }
                switch (type.getAsString()) {
                    case "raw_stat" -> applyRawStat(effect, stats, toggles, enabledToggles, Integer.MAX_VALUE);
                    case "stat_scaling" ->
                            applyStatScaling(effect, stats, sliderValues, buildStats, sliderMaximums);
                    default -> {
                        // Spell replacements from major IDs need the spell set, which the tree owns.
                    }
                }
            }
        }
        return stats;
    }

    /**
     * Resolves {@code "<abilityId>.<property>"} references against the tree.
     *
     * <p>Only active abilities are consulted: a property of an ability the build has not taken
     * should not feed its damage.
     */
    private static java.util.function.ToDoubleFunction<String> propertyResolver(
            AbilityTreeState state, Map<String, double[]> propertyModifiers) {
        return reference -> {
            int separator = reference.indexOf('.');
            if (separator <= 0 || state == null || state.tree() == null) {
                return 0;
            }
            try {
                int abilityId = Integer.parseInt(reference.substring(0, separator));
                String property = reference.substring(separator + 1);
                AbilityNode node = state.tree().node(abilityId);
                if (node == null || !state.isActive(abilityId)) {
                    return 0;
                }
                double value = node.properties().getOrDefault(property, 0.0);
                // An upgrade contributes to the ability it extends: a node naming a base ability
                // adds its own properties to that ability's. Double Totem and Triple Totem each add
                // one to the Totem's num_totems, so taking both is what turns one totem into three
                // and triples the spell's damage.
                for (AbilityNode upgrade : state.tree().nodes()) {
                    if (upgrade.baseAbility() == null
                            || !state.isActive(upgrade.id())
                            || !upgrade.properties().containsKey(property)) {
                        continue;
                    }
                    if (upgrade.baseAbility() == abilityId) {
                        value += upgrade.properties().get(property);
                        continue;
                    }
                    // Some quantities are the build's, not one spell's. How many totems are standing
                    // is the same number whether the Totem is ticking or the Aura is riding them, so
                    // an upgrade reaches every ability that counts the same thing — recognised by
                    // the ability it extends tracking that property too.
                    AbilityNode extended = state.tree().node(upgrade.baseAbility());
                    if (extended != null
                            && extended.properties().containsKey(property)
                            && node.properties().containsKey(property)) {
                        value += upgrade.properties().get(property);
                    }
                }
                // A major identification can rewrite a property outright: Lifestream quarters Blood
                // Sorrow's duration in exchange for quadrupling its damage. Additions land before
                // the scaling, which is how upstream orders them.
                double[] modifier = propertyModifiers.get(abilityId + "." + property);
                if (modifier != null) {
                    value = (value + modifier[0]) * modifier[1];
                }
                return value;
            } catch (NumberFormatException ignored) {
                return 0;
            }
        };
    }

    /**
     * Decides whether a toggle is the player's to switch or simply part of taking the ability.
     *
     * <p>Most toggles describe a passive that is always in effect once the node is unlocked, so
     * offering a switch for them only invites a build that reads lower than it plays. Two kinds do
     * belong to the player: an active skill, which the data names with an {@code "Activate "}
     * prefix, and a choice between alternatives, which shows up as a node declaring more than one
     * toggle — the three mystic masks, or the two forms of Divine Intervention. Those are mutually
     * exclusive, so switching them on together would stack bonuses the game never stacks.
     */
    private static boolean isPlayerControlled(String toggle, int togglesOnNode) {
        return toggle.startsWith("Activate ") || togglesOnNode > 1;
    }

    /** How many distinct toggles a node offers, which tells a choice apart from a passive. */
    private static int toggleCount(AbilityNode node) {
        java.util.Set<String> names = new java.util.LinkedHashSet<>();
        for (AbilityNode.Effect effect : node.effects()) {
            String toggle = string(effect.raw(), "toggle");
            if (toggle != null) {
                names.add(toggle);
            }
        }
        return names.size();
    }

    private static void applyRawStat(
            JsonObject effect,
            Map<String, Integer> stats,
            List<String> toggles,
            java.util.Set<String> enabledToggles,
            int togglesOnNode) {

        String toggle = string(effect, "toggle");
        if (toggle != null && isPlayerControlled(toggle, togglesOnNode)) {
            if (!toggles.contains(toggle)) {
                toggles.add(toggle);
            }
            // A toggled bonus only counts while the player has it switched on.
            if (enabledToggles == null || !enabledToggles.contains(toggle)) {
                return;
            }
        }
        JsonElement bonuses = effect.get("bonuses");
        if (bonuses == null || !bonuses.isJsonArray()) {
            return;
        }
        for (JsonElement element : bonuses.getAsJsonArray()) {
            if (element == null || !element.isJsonObject()) {
                continue;
            }
            JsonObject bonus = element.getAsJsonObject();
            if (!"stat".equals(string(bonus, "type"))) {
                // Non-stat bonuses (spell properties and the like) are handled elsewhere.
                continue;
            }
            String name = string(bonus, "name");
            Integer value = optionalInt(bonus.get("value"));
            if (name != null && value != null) {
                stats.merge(Identifications.normalise(name), value, Integer::sum);
            }
        }
    }

    /**
     * Applies a scaling effect.
     *
     * <p>Slider-driven effects scale linearly with the value the player picks, capped by the
     * effect's own maximum. Effects scaling from another stat are skipped here because they depend
     * on totals that are only known once every item is aggregated.
     */
    private static void applyStatScaling(
            JsonObject effect,
            Map<String, Integer> stats,
            Map<String, Integer> sliderValues,
            Map<String, Integer> buildStats,
            Map<String, Integer> sliderMaximums) {

        JsonElement output = effect.get("output");
        if (output == null || !output.isJsonObject()) {
            return;
        }
        JsonObject outputObject = output.getAsJsonObject();
        if (!"stat".equals(string(outputObject, "type"))) {
            return;
        }
        String name = string(outputObject, "name");
        if (name == null) {
            return;
        }

        boolean slider = effect.has("slider") && effect.get("slider").isJsonPrimitive()
                && effect.get("slider").getAsBoolean();
        if (!slider) {
            applyInputScaling(effect, stats, name, buildStats);
            return;
        }
        String sliderName = string(effect, "slider_name");
        if (sliderName == null) {
            return;
        }
        int declaredMax = effect.has("slider_max") ? optionalIntOrDefault(effect.get("slider_max"), 1) : 1;
        // Offered to the player even at zero, otherwise the control never appears.
        sliderMaximums.merge(sliderName, declaredMax, Math::max);
        int value = sliderValues == null ? 0 : sliderValues.getOrDefault(sliderName, 0);
        if (value <= 0) {
            return;
        }
        int maximum = declaredMax;
        int clamped = Math.min(value, maximum);

        double scaling = 1;
        JsonElement scalingElement = effect.get("scaling");
        if (scalingElement != null && scalingElement.isJsonArray() && !scalingElement.getAsJsonArray().isEmpty()) {
            JsonElement first = scalingElement.getAsJsonArray().get(0);
            if (first != null && first.isJsonPrimitive()) {
                scaling = first.getAsDouble();
            }
        }
        int total = (int) Math.round(scaling * clamped);
        if (effect.has("max")) {
            Integer cap = optionalInt(effect.get("max"));
            if (cap != null) {
                total = Math.min(total, cap);
            }
        }
        if (total != 0) {
            stats.merge(Identifications.normalise(name), total, Integer::sum);
        }
    }

    /**
     * Applies an effect that converts one of the build's own stats into another.
     *
     * <p>Each input contributes {@code value * scaling}, and the result is capped when the effect
     * declares a maximum. Because it reads the build's totals, the caller has to have computed them
     * once already; on the first pass there is nothing to read and the effect contributes nothing.
     */
    private static void applyInputScaling(
            JsonObject effect, Map<String, Integer> stats, String outputName, Map<String, Integer> buildStats) {

        JsonElement inputs = effect.get("inputs");
        if (inputs == null || !inputs.isJsonArray() || buildStats.isEmpty()) {
            return;
        }
        JsonElement scalingElement = effect.get("scaling");
        double total = 0;
        int index = 0;
        for (JsonElement element : inputs.getAsJsonArray()) {
            if (element == null || !element.isJsonObject()) {
                index++;
                continue;
            }
            JsonObject input = element.getAsJsonObject();
            String inputName = string(input, "name");
            if (inputName != null) {
                double scaling = 1;
                if (scalingElement != null && scalingElement.isJsonArray()
                        && index < scalingElement.getAsJsonArray().size()) {
                    JsonElement value = scalingElement.getAsJsonArray().get(index);
                    if (value != null && value.isJsonPrimitive()) {
                        scaling = value.getAsDouble();
                    }
                }
                total += buildStats.getOrDefault(Identifications.normalise(inputName), 0) * scaling;
            }
            index++;
        }
        int result = (int) Math.round(total);
        if (effect.has("max")) {
            Integer cap = optionalInt(effect.get("max"));
            if (cap != null && cap != 0) {
                // A cap may be negative for a penalty, so clamp towards zero from the right side.
                result = cap > 0 ? Math.min(result, cap) : Math.max(result, cap);
            }
        }
        if (result != 0) {
            stats.merge(Identifications.normalise(outputName), result, Integer::sum);
        }
    }

    private static Spell parseSpell(
            JsonObject effect, java.util.function.ToDoubleFunction<String> properties) {
        Integer baseSpell = optionalInt(effect.get("base_spell"));
        if (baseSpell == null) {
            return null;
        }
        List<Part> parts = new ArrayList<>();
        JsonElement partsElement = effect.get("parts");
        if (partsElement != null && partsElement.isJsonArray()) {
            for (JsonElement element : partsElement.getAsJsonArray()) {
                if (element != null && element.isJsonObject()) {
                    parts.add(parsePart(element.getAsJsonObject(), properties));
                }
            }
        }
        // Both default to the spell-like behaviour; only the entries that say otherwise differ.
        JsonElement scaling = effect.get("scaling");
        JsonElement useAttackSpeed = effect.get("use_atkspd");
        return new Spell(
                baseSpell,
                stringOrDefault(effect, "name", "Spell " + baseSpell),
                optionalIntOrDefault(effect.get("cost"), 0),
                stringOrDefault(effect, "display", ""),
                scaling == null || !scaling.isJsonPrimitive() || "spell".equals(scaling.getAsString()),
                useAttackSpeed == null || !useAttackSpeed.isJsonPrimitive() || useAttackSpeed.getAsBoolean(),
                parts);
    }

    /**
     * Reads a hit count, which may be a plain number or a reference to an ability property.
     *
     * <p>References look like {@code "73.duration"}, meaning the {@code duration} property of
     * ability 73. Reading that as the number 73 multiplies the damage by roughly sixty, which is how
     * a spell ends up reporting millions.
     */
    private static double hitCount(JsonElement element, java.util.function.ToDoubleFunction<String> properties) {
        if (element == null || !element.isJsonPrimitive()) {
            return 1;
        }
        try {
            return element.getAsDouble();
        } catch (RuntimeException ignored) {
            String reference = element.getAsString();
            double resolved = properties == null ? 0 : properties.applyAsDouble(reference);
            // An unresolved reference counts once rather than guessing.
            return resolved > 0 ? resolved : 1;
        }
    }

    private static Part parsePart(JsonObject object, java.util.function.ToDoubleFunction<String> properties) {
        double[] multipliers = new double[6];
        JsonElement multipliersElement = object.get("multipliers");
        if (multipliersElement != null && multipliersElement.isJsonArray()) {
            var array = multipliersElement.getAsJsonArray();
            for (int i = 0; i < multipliers.length && i < array.size(); i++) {
                JsonElement value = array.get(i);
                if (value != null && value.isJsonPrimitive()) {
                    multipliers[i] = value.getAsDouble();
                }
            }
        }
        Map<String, Double> hits = new LinkedHashMap<>();
        JsonElement hitsElement = object.get("hits");
        if (hitsElement != null && hitsElement.isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : hitsElement.getAsJsonObject().entrySet()) {
                hits.put(entry.getKey(), hitCount(entry.getValue(), properties));
            }
        }
        boolean useStrength = !object.has("use_str")
                || !object.get("use_str").isJsonPrimitive()
                || object.get("use_str").getAsBoolean();
        // A part with a "power" field heals; one with "hits" totals other parts.
        double power = 0;
        String inferred = object.has("power") ? "heal" : object.has("hits") ? "total" : "damage";
        String type = stringOrDefault(object, "type", inferred);
        if (object.has("power") && object.get("power").isJsonPrimitive()) {
            power = object.get("power").getAsDouble();
            type = "heal";
        }
        return new Part(
                stringOrDefault(object, "name", ""),
                type,
                multipliers,
                hits,
                useStrength,
                power);
    }

    /** Adjusts a part of an already-defined spell, adding it when the part does not exist yet. */
    private static void applySpellProperty(
            JsonObject effect, Map<Integer, Spell> spells,
            java.util.function.ToDoubleFunction<String> properties) {
        Integer baseSpell = optionalInt(effect.get("base_spell"));
        if (baseSpell == null) {
            return;
        }
        Spell spell = spells.get(baseSpell);
        if (spell == null) {
            return;
        }
        String targetName = string(effect, "target_part");
        if (targetName == null) {
            return;
        }

        List<Part> updated = new ArrayList<>(spell.parts().size());
        boolean found = false;
        for (Part part : spell.parts()) {
            if (!targetName.equals(part.name())) {
                updated.add(part);
                continue;
            }
            found = true;
            double[] multipliers = part.multipliers().clone();
            JsonElement multipliersElement = effect.get("multipliers");
            if (multipliersElement != null && multipliersElement.isJsonArray()) {
                var array = multipliersElement.getAsJsonArray();
                for (int i = 0; i < multipliers.length && i < array.size(); i++) {
                    JsonElement value = array.get(i);
                    if (value != null && value.isJsonPrimitive()) {
                        multipliers[i] += value.getAsDouble();
                    }
                }
            }
            Map<String, Double> hits = new LinkedHashMap<>(part.hits());
            JsonElement hitsElement = effect.get("hits");
            if (hitsElement != null && hitsElement.isJsonObject()) {
                for (Map.Entry<String, JsonElement> entry : hitsElement.getAsJsonObject().entrySet()) {
                    hits.merge(entry.getKey(), hitCount(entry.getValue(), properties), Double::sum);
                }
            }
            updated.add(new Part(part.name(), part.type(), multipliers, hits, part.useStrength(), part.power()));
        }
        if (!found) {
            updated.add(parsePart(effect, properties));
        }
        int cost = spell.cost() + optionalIntOrDefault(effect.get("cost"), 0);
        spells.put(baseSpell, new Spell(spell.baseSpell(), spell.name(), cost, spell.display(),
                spell.spellScaling(), spell.useAttackSpeed(), updated));
    }

    // ------------------------------------------------------------------ json helpers

    private static String string(JsonObject object, String key) {
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) {
            return null;
        }
        String value = element.getAsString();
        return value.isEmpty() ? null : value;
    }

    private static String stringOrDefault(JsonObject object, String key, String fallback) {
        String value = string(object, key);
        return value == null ? fallback : value;
    }

    /**
     * Reads a numeric value, tolerating the {@code "9.resistance"} form the data uses for bonuses
     * that name the stat they scale.
     */
    private static Integer optionalInt(JsonElement element) {
        if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) {
            return null;
        }
        try {
            return (int) Math.round(element.getAsDouble());
        } catch (RuntimeException ignored) {
            String text = element.getAsString();
            int separator = text.indexOf('.');
            try {
                return Integer.parseInt(separator > 0 ? text.substring(0, separator) : text);
            } catch (NumberFormatException alsoIgnored) {
                return null;
            }
        }
    }

    private static int optionalIntOrDefault(JsonElement element, int fallback) {
        Integer value = optionalInt(element);
        return value == null ? fallback : value;
    }
}
