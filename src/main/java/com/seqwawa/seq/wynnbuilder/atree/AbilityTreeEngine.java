package com.seqwawa.seq.wynnbuilder.atree;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.seqwawa.seq.wynnbuilder.data.Identifications;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
 *
 * <p>Almost any number in an effect may instead be a reference such as {@code "25.raw_per_corrupt"}:
 * the {@code raw_per_corrupt} property of ability 25, after its upgrades and any slider or switch
 * have moved it. Upstream resolves these wherever it reads a number, so every number here goes
 * through {@link Evaluator#number}. Reading one as a plain number either throws or, worse, quietly
 * yields the ability ID.
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
     * A value the player sets, such as hits landed, that an ability scales from.
     *
     * @param defaultValue what the slider reads until the player moves it; Puppet Master, for one,
     *     starts with its puppets already out
     */
    public record Slider(String name, int maximum, int defaultValue) {
        public Slider(String name, int maximum) {
            this(name, maximum, 0);
        }

        /** The value in effect: the player's pick if they made one, kept within the slider's range. */
        public int valueFrom(Map<String, Integer> chosen) {
            Integer picked = chosen == null ? null : chosen.get(name);
            int value = picked == null ? defaultValue : picked;
            return Math.max(0, Math.min(value, maximum));
        }
    }

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
     * @param type {@code damage}, {@code heal} or {@code total}, decided the way upstream decides
     *     it: by whether the part carries multipliers, a healing power, or hit counts
     * @param multipliers damage share per element in n/e/t/w/f/a order, as percentages
     * @param hits for a total part, how many times each named part lands
     * @param display false for an intermediate step the website does not list
     * @param tickRounding whether a total's hit counts snap to the game's twenty ticks a second
     * @param ignoredMults damage multipliers, by name, that do not apply to this part
     */
    public record Part(
            String name,
            String type,
            double[] multipliers,
            Map<String, Double> hits,
            boolean useStrength,
            double power,
            boolean display,
            boolean tickRounding,
            List<String> ignoredMults) {
        public Part {
            multipliers = multipliers.clone();
            hits = Map.copyOf(hits);
            ignoredMults = List.copyOf(ignoredMults);
        }
    }

    /** The ID upstream gives the basic attack, which the tree reshapes like any spell. */
    public static final int MELEE_ABILITY_ID = 999;
    /** A placeholder elemental mastery upgrades attach to. */
    private static final int ELEMENTAL_MASTERY_ID = 998;

    /**
     * Evaluates the active selection.
     *
     * @param sliderValues values the player picked for sliders, keyed by slider name; a slider
     *     missing here reads its default
     * @param enabledToggles toggles the player has switched on
     */
    public static Evaluation evaluate(
            AbilityTreeState state, Map<String, Integer> sliderValues, Set<String> enabledToggles) {
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
            Set<String> enabledToggles,
            Map<String, Integer> buildStats) {
        return evaluate(state, sliderValues, enabledToggles, buildStats, List.of());
    }

    /**
     * Evaluates the active selection together with abilities granted from outside the tree.
     *
     * <p>Major identifications describe what they do as abilities in the tree's own vocabulary:
     * they extend a tree ability, add sliders and switches, and replace spells. Upstream merges them
     * into the tree before evaluating it, so they go through exactly the same path here.
     *
     * @param grantedAbilities ability definitions from major identifications, each carrying the
     *     class it belongs to and the tree abilities it depends on
     */
    public static Evaluation evaluate(
            AbilityTreeState state,
            Map<String, Integer> sliderValues,
            Set<String> enabledToggles,
            Map<String, Integer> buildStats,
            List<JsonObject> grantedAbilities) {
        if (state == null || state.tree() == null || state.tree().isEmpty()) {
            return Evaluation.empty();
        }
        Evaluator evaluator = new Evaluator(
                activeAbilities(state, grantedAbilities),
                sliderValues == null ? Map.of() : sliderValues,
                enabledToggles == null ? Set.of() : enabledToggles,
                buildStats == null ? Map.of() : buildStats);
        return evaluator.evaluate();
    }

    /**
     * The abilities in effect: the class's built-in ones, the selected tree nodes, then whatever
     * aspects and major identifications graft on.
     *
     * <p>A granted ability applies only to its own class, only once everything it depends on is
     * selected, and, when it extends an ability, only while that ability is in effect.
     */
    private static List<AbilityNode> activeAbilities(AbilityTreeState state, List<JsonObject> grantedAbilities) {
        List<AbilityNode> active = new ArrayList<>(defaultAbilities(state.tree().playerClass()));
        Set<Integer> inEffect = new HashSet<>();
        active.forEach(node -> inEffect.add(node.id()));
        for (AbilityNode node : state.tree().nodes()) {
            if (state.isActive(node.id())) {
                active.add(node);
                inEffect.add(node.id());
            }
        }
        if (grantedAbilities == null) {
            return active;
        }
        // Negative IDs cannot collide with the tree, whose IDs start at zero.
        int syntheticId = -1;
        for (JsonObject ability : grantedAbilities) {
            if (ability == null) {
                continue;
            }
            String abilityClass = string(ability, "class");
            if (abilityClass == null
                    || !("Any".equalsIgnoreCase(abilityClass)
                            || abilityClass.equalsIgnoreCase(state.tree().playerClass()))) {
                continue;
            }
            AbilityNode node = AbilityNode.parseDetached(ability, syntheticId--);
            if (node.baseAbility() != null && !inEffect.contains(node.baseAbility())) {
                continue;
            }
            if (node.dependencyIds().stream().allMatch(inEffect::contains)) {
                active.add(node);
            }
        }
        return active;
    }

    /**
     * The abilities every class has without spending a point.
     *
     * <p>Upstream models the basic attack as spell 0 of an ability with ID {@value #MELEE_ABILITY_ID},
     * so the tree can reshape it like any other spell: Spear Proficiency adds to its multiplier,
     * Bloodied Armory adds a Blood Swipe part. Computing the attack apart from the tree loses all of
     * that. Ability {@value #ELEMENTAL_MASTERY_ID} is an empty anchor elemental mastery upgrades extend.
     */
    private static List<AbilityNode> defaultAbilities(String playerClass) {
        String melee = switch (playerClass == null ? "" : playerClass) {
            case "Mage" -> meleeAbility("Mage", "{\"range\": 12}", "Wand Melee", "Melee",
                    "[{\"name\": \"Melee\", \"multipliers\": [100, 0, 0, 0, 0, 0]}]");
            case "Warrior" -> meleeAbility("Warrior", "{\"range\": 4}", "Melee", "Melee",
                    "[{\"name\": \"Melee\", \"multipliers\": [100, 0, 0, 0, 0, 0]}]");
            case "Archer" -> meleeAbility("Archer", "{\"range\": 9}", "Bow Shot", "Single Shot",
                    "[{\"name\": \"Single Shot\", \"multipliers\": [100, 0, 0, 0, 0, 0]}]");
            case "Assassin" -> meleeAbility("Assassin", "{\"range\": 3}", "Melee", "Melee",
                    "[{\"name\": \"Melee\", \"multipliers\": [100, 0, 0, 0, 0, 0]}]");
            case "Shaman" -> meleeAbility("Shaman", "{\"range\": 32.25, \"speed\": 0}", "Relik Melee", "Total",
                    "[{\"name\": \"Single Beam\", \"multipliers\": [33, 0, 0, 0, 0, 0]},"
                            + " {\"name\": \"Total\", \"hits\": {\"Single Beam\": 3}}]");
            default -> null;
        };
        List<AbilityNode> defaults = new ArrayList<>();
        if (melee != null) {
            defaults.add(AbilityNode.parseDetached(
                    com.google.gson.JsonParser.parseString(melee).getAsJsonObject(), MELEE_ABILITY_ID));
        }
        defaults.add(AbilityNode.parseDetached(
                com.google.gson.JsonParser.parseString(
                        "{\"display_name\": \"Elemental Mastery\", \"properties\": {}, \"effects\": []}")
                        .getAsJsonObject(),
                ELEMENTAL_MASTERY_ID));
        return defaults;
    }

    private static String meleeAbility(
            String playerClass, String properties, String spellName, String display, String parts) {
        return "{\"display_name\": \"" + playerClass + " Melee\", \"properties\": " + properties + ","
                + " \"effects\": [{\"type\": \"replace_spell\", \"name\": \"" + spellName + "\","
                + " \"base_spell\": 0, \"scaling\": \"melee\", \"use_atkspd\": false,"
                + " \"display\": \"" + display + "\", \"parts\": " + parts + "}]}";
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
        Set<String> names = new java.util.LinkedHashSet<>();
        for (AbilityNode.Effect effect : node.effects()) {
            String toggle = string(effect.raw(), "toggle");
            if (toggle != null) {
                names.add(toggle);
            }
        }
        return names.size();
    }

    private static boolean isSlider(AbilityNode.Effect effect) {
        return "stat_scaling".equals(effect.type()) && bool(effect.raw(), "slider", false);
    }

    /** Snaps floating-point noise such as 2.9999999 back to the integer it stands for, as upstream does. */
    private static double roundNear(double value) {
        double nearest = Math.round(value);
        return Math.abs(value - nearest) < 1e-8 ? nearest : value;
    }

    /**
     * When an effect lands.
     *
     * <p>Every effect is applied twice, each time keeping only the outputs of one kind. Property
     * rewrites all land first, so a stat that reads a property sees it after every slider and switch
     * has moved it. That is the order upstream resolves them in.
     */
    private enum Phase {
        PROPERTY,
        STAT;

        static Phase of(String bonusType) {
            if ("prop".equals(bonusType)) {
                return PROPERTY;
            }
            return "stat".equals(bonusType) ? STAT : null;
        }
    }

    /** The mutable working state of one evaluation. */
    private static final class Evaluator {
        private final List<AbilityNode> active;
        /** Abilities in effect that others can name: the tree's, plus the built-in melee and mastery. */
        private final Map<Integer, AbilityNode> byId = new LinkedHashMap<>();
        private final Map<String, Integer> sliderValues;
        private final Set<String> enabledToggles;
        private final Map<String, Integer> buildStats;

        private final Map<String, Integer> stats = new LinkedHashMap<>();
        private final List<String> toggles = new ArrayList<>();
        private final Map<String, Slider> sliders = new LinkedHashMap<>();
        /** Property rewrites keyed {@code "<abilityId>.<property>"}, each {@code {addend, multiplier}}. */
        private final Map<String, double[]> modifiers = new LinkedHashMap<>();

        Evaluator(
                List<AbilityNode> active,
                Map<String, Integer> sliderValues,
                Set<String> enabledToggles,
                Map<String, Integer> buildStats) {
            this.active = active;
            this.sliderValues = sliderValues;
            this.enabledToggles = enabledToggles;
            this.buildStats = buildStats;
            for (AbilityNode node : active) {
                if (node.id() >= 0) {
                    byId.put(node.id(), node);
                }
            }
        }

        Evaluation evaluate() {
            registerSliders();
            applyEffects(Phase.PROPERTY);
            applyEffects(Phase.STAT);
            return new Evaluation(stats, collectSpells(), toggles, new ArrayList<>(sliders.values()));
        }

        // -------------------------------------------------------------- sliders

        /** A slider under construction; upstream accumulates these across every ability that touches it. */
        private static final class SliderDraft {
            double maximum;
            double defaultValue;
            double maximumMultiplier = 1;
            boolean overwritten;
        }

        /**
         * Builds the slider set the way upstream does.
         *
         * <p>One ability defines a slider and others extend it: each adds its own maximum and default
         * on top, so taking More Puppets raises how many puppets the slider allows. An effect marked
         * {@code modify} only adjusts a slider that something else defines, so it waits until that
         * definition has been seen, wherever each sits in the tree.
         */
        private void registerSliders() {
            Map<String, SliderDraft> drafts = new LinkedHashMap<>();
            List<JsonObject> pending = new ArrayList<>();
            for (AbilityNode node : active) {
                for (AbilityNode.Effect effect : node.effects()) {
                    if (isSlider(effect)) {
                        pending.add(effect.raw());
                    }
                }
            }
            while (!pending.isEmpty()) {
                List<JsonObject> deferred = new ArrayList<>();
                for (JsonObject effect : pending) {
                    String name = string(effect, "slider_name");
                    if (name == null) {
                        continue;
                    }
                    String behavior = stringOrDefault(effect, "behavior", "merge");
                    SliderDraft draft = drafts.get(name);
                    if (draft == null) {
                        if ("merge".equals(behavior)) {
                            draft = new SliderDraft();
                            draft.maximum = number(effect.get("slider_max"), 0);
                            draft.defaultValue = number(effect.get("slider_default"), 0);
                            drafts.put(name, draft);
                        } else {
                            deferred.add(effect);
                        }
                    } else if ("overwrite".equals(behavior)) {
                        if (effect.has("slider_max")) {
                            draft.maximum = number(effect.get("slider_max"), 0);
                        }
                        if (effect.has("slider_default")) {
                            draft.defaultValue = number(effect.get("slider_default"), 0);
                        }
                        draft.overwritten = true;
                    } else if (!draft.overwritten) {
                        draft.maximum += number(effect.get("slider_max"), 0);
                        draft.maximumMultiplier *= number(effect.get("slider_max_mult"), 1);
                        draft.defaultValue += number(effect.get("slider_default"), 0);
                    }
                }
                if (deferred.size() == pending.size()) {
                    // Adjustments to a slider nothing defines: upstream drops them too.
                    break;
                }
                pending = deferred;
            }
            drafts.forEach((name, draft) -> sliders.put(name, new Slider(
                    name,
                    (int) Math.round(draft.maximum * draft.maximumMultiplier),
                    (int) Math.round(draft.defaultValue))));
        }

        // -------------------------------------------------------------- stat and property effects

        private void applyEffects(Phase phase) {
            for (AbilityNode node : active) {
                int togglesOnNode = toggleCount(node);
                for (AbilityNode.Effect effect : node.effects()) {
                    switch (effect.type()) {
                        case "raw_stat" -> applyRawStat(effect.raw(), togglesOnNode, phase);
                        case "stat_scaling" -> applyStatScaling(effect.raw(), phase);
                        default -> {
                            // Spells are assembled once every property has settled.
                        }
                    }
                }
            }
        }

        private void applyRawStat(JsonObject effect, int togglesOnNode, Phase phase) {
            String toggle = string(effect, "toggle");
            if (toggle != null && isPlayerControlled(toggle, togglesOnNode)) {
                if (!toggles.contains(toggle)) {
                    toggles.add(toggle);
                }
                // A toggled bonus only counts while the player has it switched on.
                if (!enabledToggles.contains(toggle)) {
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
                // Resolved only in its own phase, so a value naming a property reads it settled.
                if (Phase.of(string(bonus, "type")) == phase) {
                    applyBonus(bonus, number(bonus.get("value"), 0), phase);
                }
            }
        }

        /**
         * Applies a scaling effect, following upstream's arithmetic step for step.
         *
         * <p>A slider multiplies the value the player picks; otherwise the effect converts the build's
         * own totals, which only exist from the second pass on. Derived stats are rounded down and
         * kept non-negative, while slider-driven ones stay exact and may go negative, since a
         * fraction such as 0.01 per stack only means something unrounded.
         */
        private void applyStatScaling(JsonObject effect, Phase phase) {
            JsonElement output = effect.get("output");
            if (output == null || output.isJsonNull()) {
                // Some effects exist only to extend a slider.
                return;
            }
            boolean slider = bool(effect, "slider", false);
            Double base = slider ? sliderTotal(effect) : inputTotal(effect);
            if (base == null) {
                return;
            }
            double total = base + number(effect.get("base"), 0);
            if (bool(effect, "round", !slider)) {
                total = Math.floor(roundNear(total));
            }
            if (!slider && bool(effect, "positive", true) && total < 0) {
                total = 0;
            }
            if (effect.has("max")) {
                // A cap may be negative for a penalty, so it clamps towards zero from its own side.
                double cap = number(effect.get("max"), 0);
                if (cap > 0 && total > cap) {
                    total = cap;
                } else if (cap < 0 && total < cap) {
                    total = cap;
                }
            }
            if (output.isJsonArray()) {
                for (JsonElement target : output.getAsJsonArray()) {
                    if (target != null && target.isJsonObject()) {
                        applyBonus(target.getAsJsonObject(), total, phase);
                    }
                }
            } else if (output.isJsonObject()) {
                applyBonus(output.getAsJsonObject(), total, phase);
            }
        }

        /** The slider's value times the effect's scaling, or {@code null} when it does not apply. */
        private Double sliderTotal(JsonObject effect) {
            String name = string(effect, "slider_name");
            Slider slider = name == null ? null : sliders.get(name);
            if (slider == null) {
                return null;
            }
            int value = slider.valueFrom(sliderValues);
            double requirement = number(effect.get("requirement"), 0);
            if (requirement > value) {
                return null;
            }
            double steps = Math.floor(value - requirement);
            double scaling = number(firstElement(effect.get("scaling")), 0);
            if (bool(effect, "multiplicative", false)) {
                // Each step compounds on the last, as with Judrajim's successive hits.
                return (Math.pow((100 + scaling) / 100, steps) - 1) * 100;
            }
            return steps * scaling;
        }

        /** The sum of each input times its own scaling factor. */
        private Double inputTotal(JsonObject effect) {
            JsonElement inputs = effect.get("inputs");
            JsonElement scaling = effect.get("scaling");
            if (inputs == null || !inputs.isJsonArray() || scaling == null || !scaling.isJsonArray()) {
                return 0.0;
            }
            JsonArray inputArray = inputs.getAsJsonArray();
            JsonArray scalingArray = scaling.getAsJsonArray();
            double total = 0;
            for (int i = 0; i < inputArray.size() && i < scalingArray.size(); i++) {
                JsonElement element = inputArray.get(i);
                if (element == null || !element.isJsonObject()) {
                    continue;
                }
                JsonObject input = element.getAsJsonObject();
                String name = string(input, "name");
                if (name == null) {
                    continue;
                }
                double factor = number(scalingArray.get(i), 0);
                if ("prop".equals(string(input, "type"))) {
                    Integer ability = optionalInt(input.get("abil"));
                    double value = ability == null ? Double.NaN : property(ability + "." + name);
                    if (!Double.isNaN(value)) {
                        total += value * factor;
                    }
                } else {
                    total += buildStats.getOrDefault(Identifications.normalise(name), 0) * factor;
                }
            }
            return total;
        }

        /**
         * Sends a value to a stat or to an ability property, whichever the target names.
         *
         * <p>Only targets of the current phase are touched, which is what lets the two phases run
         * over the same effects without applying anything twice.
         */
        private void applyBonus(JsonObject target, double value, Phase phase) {
            String name = string(target, "name");
            if (name == null || Phase.of(string(target, "type")) != phase) {
                return;
            }
            if (phase == Phase.STAT) {
                // Stats are whole numbers here; a fractional multiplier rounds to the nearest point.
                int rounded = (int) Math.round(value);
                if (rounded != 0) {
                    stats.merge(Identifications.normalise(name), rounded, Integer::sum);
                }
                return;
            }
            Integer ability = optionalInt(target.get("abil"));
            // Like upstream, a property of an ability the build does not have is left alone.
            if (ability == null || !byId.containsKey(ability)) {
                return;
            }
            double[] modifier = modifiers.computeIfAbsent(ability + "." + name, key -> new double[] {0, 1});
            if (bool(target, "mult", false)) {
                modifier[1] *= value;
            } else {
                modifier[0] += value;
            }
        }

        // -------------------------------------------------------------- properties

        /**
         * Resolves {@code "<abilityId>.<property>"} against the abilities in effect.
         *
         * <p>Only active abilities are consulted: a property of an ability the build has not taken
         * should not feed its damage. Additions land before multiplications, which is how upstream
         * orders them.
         *
         * @return {@code NaN} when nothing in effect declares the property, so callers can tell an
         *     unknown reference from a genuine zero
         */
        double property(String reference) {
            int separator = reference.indexOf('.');
            if (separator <= 0) {
                return Double.NaN;
            }
            int abilityId;
            try {
                abilityId = Integer.parseInt(reference.substring(0, separator).trim());
            } catch (NumberFormatException ignored) {
                return Double.NaN;
            }
            String property = reference.substring(separator + 1);
            AbilityNode node = byId.get(abilityId);
            if (node == null) {
                return Double.NaN;
            }
            boolean declared = node.properties().containsKey(property);
            double value = node.properties().getOrDefault(property, 0.0);
            // An upgrade contributes to the ability it extends, and only to it: a node naming a base
            // ability adds its own properties to that ability's. Double Totem and Triple Totem each
            // add one to the Totem's num_totems, so taking both is what turns one totem into three.
            for (AbilityNode upgrade : active) {
                if (upgrade != node
                        && upgrade.baseAbility() != null
                        && upgrade.baseAbility() == abilityId
                        && upgrade.properties().containsKey(property)) {
                    value += upgrade.properties().get(property);
                    declared = true;
                }
            }
            // Effects rewrite properties on top: the same totem upgrades carry a bonus adding one to
            // the Aura's count as well, and Lifestream quarters Blood Sorrow's duration in exchange
            // for quadrupling its damage.
            double[] modifier = modifiers.get(abilityId + "." + property);
            if (modifier != null) {
                value = (value + modifier[0]) * modifier[1];
                declared = true;
            }
            return declared ? value : Double.NaN;
        }

        /**
         * Reads a number that may be written as a property reference.
         *
         * @return {@code fallback} when the element is missing, not numeric, or names a property
         *     nothing in effect declares
         */
        double number(JsonElement element, double fallback) {
            if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) {
                return fallback;
            }
            JsonPrimitive primitive = element.getAsJsonPrimitive();
            if (primitive.isNumber()) {
                return primitive.getAsDouble();
            }
            if (!primitive.isString()) {
                return fallback;
            }
            String text = primitive.getAsString().trim();
            try {
                return Double.parseDouble(text);
            } catch (NumberFormatException notAPlainNumber) {
                double resolved = property(text);
                return Double.isNaN(resolved) ? fallback : resolved;
            }
        }

        // -------------------------------------------------------------- spells

        /**
         * Assembles the spells, once every property has settled, following upstream's rules.
         *
         * <p>Spells are worked on as data and only turned into records at the end, because the
         * rules are about data: a later {@code replace_spell} overwrites only the keys it declares,
         * so a spell keeps its cost when an upgrade redefines just its parts. Two passes, since every
         * spell must be defined before anything adjusts it: an {@code add_spell_prop} declared on a
         * node that sits earlier in the tree than the definition of its target would otherwise find
         * nothing and be dropped.
         */
        private List<Spell> collectSpells() {
            Map<Integer, JsonObject> spells = new LinkedHashMap<>();
            for (AbilityNode node : active) {
                for (AbilityNode.Effect effect : node.effects()) {
                    if (!"replace_spell".equals(effect.type())) {
                        continue;
                    }
                    Integer baseSpell = optionalInt(effect.raw().get("base_spell"));
                    if (baseSpell == null) {
                        continue;
                    }
                    JsonObject existing = spells.get(baseSpell);
                    if (existing == null) {
                        spells.put(baseSpell, effect.raw().deepCopy());
                    } else {
                        effect.raw().entrySet().forEach(entry -> existing.add(entry.getKey(), entry.getValue().deepCopy()));
                    }
                }
            }
            for (AbilityNode node : active) {
                for (AbilityNode.Effect effect : node.effects()) {
                    if ("add_spell_prop".equals(effect.type())) {
                        applySpellProperty(effect.raw(), spells);
                    }
                }
            }
            List<Spell> result = new ArrayList<>(spells.size());
            spells.forEach((baseSpell, spell) -> result.add(toSpell(baseSpell, spell)));
            return result;
        }

        /**
         * Adjusts an already-defined spell.
         *
         * <p>A cost change and a new headline apply to the spell as a whole, with or without a target
         * part: most cost reductions name no part at all. With a target, the part's multipliers,
         * healing power or hit counts are added to, or replaced when the effect says
         * {@code overwrite}; a part that does not exist yet is created, under the target's name,
         * unless the effect only means to modify one.
         */
        private void applySpellProperty(JsonObject effect, Map<Integer, JsonObject> spells) {
            Integer baseSpell = optionalInt(effect.get("base_spell"));
            JsonObject spell = baseSpell == null ? null : spells.get(baseSpell);
            if (spell == null) {
                return;
            }
            if (spell.has("cost")) {
                spell.addProperty("cost", number(spell.get("cost"), 0) + number(effect.get("cost"), 0));
            }
            if (effect.has("display")) {
                spell.add("display", effect.get("display").deepCopy());
            }
            String target = string(effect, "target_part");
            if (target == null) {
                return;
            }
            String behavior = stringOrDefault(effect, "behavior", "merge");
            boolean overwrite = "overwrite".equals(behavior);
            JsonArray parts = spell.has("parts") && spell.get("parts").isJsonArray()
                    ? spell.getAsJsonArray("parts")
                    : new JsonArray();
            spell.add("parts", parts);

            for (JsonElement element : parts) {
                if (element == null || !element.isJsonObject() || !target.equals(string(element.getAsJsonObject(), "name"))) {
                    continue;
                }
                JsonObject part = element.getAsJsonObject();
                if (effect.has("multipliers") && effect.get("multipliers").isJsonArray()) {
                    JsonArray added = effect.getAsJsonArray("multipliers");
                    JsonArray current = part.has("multipliers") && part.get("multipliers").isJsonArray()
                            ? part.getAsJsonArray("multipliers")
                            : new JsonArray();
                    JsonArray merged = new JsonArray();
                    for (int i = 0; i < Math.max(added.size(), current.size()); i++) {
                        double previous = i < current.size() ? number(current.get(i), 0) : 0;
                        double change = i < added.size() ? number(added.get(i), 0) : 0;
                        merged.add(overwrite && i < added.size() ? change : previous + change);
                    }
                    part.add("multipliers", merged);
                } else if (effect.has("power")) {
                    double change = number(effect.get("power"), 0);
                    part.addProperty("power", overwrite ? change : number(part.get("power"), 0) + change);
                } else if (effect.has("hits") && effect.get("hits").isJsonObject()) {
                    JsonObject hits = part.has("hits") && part.get("hits").isJsonObject()
                            ? part.getAsJsonObject("hits")
                            : new JsonObject();
                    for (Map.Entry<String, JsonElement> entry : effect.getAsJsonObject("hits").entrySet()) {
                        double change = hitCount(entry.getValue());
                        double updated = overwrite || !hits.has(entry.getKey())
                                ? change
                                : hitCount(hits.get(entry.getKey())) + change;
                        hits.addProperty(entry.getKey(), updated);
                    }
                    part.add("hits", hits);
                }
                if (effect.has("hide")) {
                    part.addProperty("display", false);
                }
                if (effect.has("ignored_mults")) {
                    JsonArray ignored = part.has("ignored_mults") && part.get("ignored_mults").isJsonArray()
                            ? part.getAsJsonArray("ignored_mults")
                            : new JsonArray();
                    if (effect.get("ignored_mults").isJsonArray()) {
                        ignored.addAll(effect.getAsJsonArray("ignored_mults"));
                    }
                    part.add("ignored_mults", ignored);
                }
                return;
            }
            if ("merge".equals(behavior)) {
                JsonObject part = effect.deepCopy();
                part.addProperty("name", target);
                if (effect.has("hide")) {
                    part.addProperty("display", false);
                }
                parts.add(part);
            }
        }

        private Spell toSpell(int baseSpell, JsonObject spell) {
            List<Part> parts = new ArrayList<>();
            JsonElement partsElement = spell.get("parts");
            if (partsElement != null && partsElement.isJsonArray()) {
                for (JsonElement element : partsElement.getAsJsonArray()) {
                    if (element != null && element.isJsonObject()) {
                        parts.add(toPart(element.getAsJsonObject()));
                    }
                }
            }
            // Both default to the spell-like behaviour; only the entries that say otherwise differ.
            JsonElement scaling = spell.get("scaling");
            return new Spell(
                    baseSpell,
                    stringOrDefault(spell, "name", "Spell " + baseSpell),
                    (int) Math.round(number(spell.get("cost"), 0)),
                    stringOrDefault(spell, "display", ""),
                    scaling == null || !scaling.isJsonPrimitive() || "spell".equals(scaling.getAsString()),
                    bool(spell, "use_atkspd", true),
                    parts);
        }

        /**
         * Reads a hit count, which may be a plain number or a reference to an ability property.
         *
         * <p>References look like {@code "73.duration"}, meaning the {@code duration} property of
         * ability 73. Reading that as the number 73 multiplies the damage by roughly sixty, which is
         * how a spell ends up reporting millions. A reference that resolves to zero means zero, as
         * when the puppet slider is pulled all the way down.
         */
        private double hitCount(JsonElement element) {
            double resolved = number(element, Double.NaN);
            // An unresolved reference counts once rather than guessing.
            return Double.isNaN(resolved) ? 1 : resolved;
        }

        /** A part's kind follows from what it carries, checked in upstream's order. */
        private Part toPart(JsonObject object) {
            String type = object.has("multipliers") ? "damage" : object.has("power") ? "heal" : "total";
            double[] multipliers = new double[6];
            JsonElement multipliersElement = object.get("multipliers");
            if (multipliersElement != null && multipliersElement.isJsonArray()) {
                var array = multipliersElement.getAsJsonArray();
                for (int i = 0; i < multipliers.length && i < array.size(); i++) {
                    multipliers[i] = number(array.get(i), 0);
                }
            }
            Map<String, Double> hits = new LinkedHashMap<>();
            JsonElement hitsElement = object.get("hits");
            if (hitsElement != null && hitsElement.isJsonObject()) {
                for (Map.Entry<String, JsonElement> entry : hitsElement.getAsJsonObject().entrySet()) {
                    hits.put(entry.getKey(), hitCount(entry.getValue()));
                }
            }
            List<String> ignoredMults = new ArrayList<>();
            collectStrings(object.get("ignored_mults"), ignoredMults);
            return new Part(
                    stringOrDefault(object, "name", ""),
                    type,
                    multipliers,
                    hits,
                    bool(object, "use_str", true),
                    number(object.get("power"), 0),
                    bool(object, "display", true),
                    bool(object, "tick_rounding", false),
                    ignoredMults);
        }

        private static void collectStrings(JsonElement element, List<String> into) {
            if (element == null || element.isJsonNull()) {
                return;
            }
            if (element.isJsonArray()) {
                element.getAsJsonArray().forEach(child -> collectStrings(child, into));
            } else if (element.isJsonPrimitive()) {
                into.add(element.getAsString());
            }
        }
    }

    // ------------------------------------------------------------------ json helpers

    private static JsonElement firstElement(JsonElement element) {
        if (element == null || !element.isJsonArray() || element.getAsJsonArray().isEmpty()) {
            return null;
        }
        return element.getAsJsonArray().get(0);
    }

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

    private static boolean bool(JsonObject object, String key, boolean fallback) {
        JsonElement element = object.get(key);
        if (element == null || !element.isJsonPrimitive()) {
            return fallback;
        }
        JsonPrimitive primitive = element.getAsJsonPrimitive();
        return primitive.isBoolean() ? primitive.getAsBoolean() : fallback;
    }

    /** Reads an identifier, such as an ability or spell number; values go through {@link Evaluator#number}. */
    private static Integer optionalInt(JsonElement element) {
        if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) {
            return null;
        }
        JsonPrimitive primitive = element.getAsJsonPrimitive();
        if (primitive.isNumber()) {
            return (int) Math.round(primitive.getAsDouble());
        }
        try {
            return Integer.parseInt(primitive.getAsString().trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
