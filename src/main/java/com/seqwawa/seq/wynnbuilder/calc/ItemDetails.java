package com.seqwawa.seq.wynnbuilder.calc;

import com.seqwawa.seq.wynnbuilder.data.CraftedItem;
import com.seqwawa.seq.wynnbuilder.data.Identifications;
import com.seqwawa.seq.wynnbuilder.data.WynnDataSet;
import com.seqwawa.seq.wynnbuilder.data.WynnIngredient;
import com.seqwawa.seq.wynnbuilder.data.WynnItem;
import com.seqwawa.seq.wynnbuilder.data.WynnRecipe;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Builds the readable stat lines for a single item or craft.
 *
 * <p>Kept out of the screens so what an item shows can be tested, and so the builder and any future
 * tooltip render the same thing.
 */
public final class ItemDetails {

    private ItemDetails() {}

    /** One line of an item breakdown. */
    public record Line(String label, String value, Kind kind, int signedValue, boolean inverted) {
        public static Line heading(String label) {
            return new Line(label, "", Kind.HEADING, 0, false);
        }

        public static Line plain(String label, String value) {
            return new Line(label, value, Kind.PLAIN, 0, false);
        }

        public static Line stat(String label, String value, int signedValue, boolean inverted) {
            return new Line(label, value, Kind.STAT, signedValue, inverted);
        }
    }

    /** How a line should be presented. */
    public enum Kind {
        HEADING,
        PLAIN,
        STAT
    }

    /**
     * Describes a regular item: base facts, requirements, then every identification as a range.
     *
     * <p>Fixed items show a single value; everything else shows the span a real drop can roll, with
     * the ends ordered numerically so they read naturally.
     */
    public static List<Line> forItem(WynnItem item) {
        List<Line> lines = new ArrayList<>();
        if (item == null) {
            return lines;
        }
        lines.add(Line.plain(item.tier().label() + " " + capitalise(item.type()), "Lv. " + item.level()));

        if (item.baseHealth() != 0) {
            lines.add(Line.plain("Health", String.valueOf(item.baseHealth())));
        }
        for (int i = 0; i < Identifications.DEFENCE_KEYS.size(); i++) {
            String key = Identifications.DEFENCE_KEYS.get(i);
            int value = item.baseDefences().getOrDefault(key, 0);
            if (value != 0) {
                String element = Identifications.elementLabel(Identifications.ELEMENT_PREFIXES.get(i + 1));
                lines.add(Line.stat(element + " defence", formatSigned(value), value, false));
            }
        }
        for (Map.Entry<String, int[]> entry : item.damages().entrySet()) {
            int[] range = entry.getValue();
            if (range[0] == 0 && range[1] == 0) {
                continue;
            }
            String element = Identifications.elementLabel(entry.getKey().substring(0, 1));
            lines.add(Line.plain(element + " damage", range[0] + "-" + range[1]));
        }
        if (item.isWeapon()) {
            lines.add(Line.plain("Attack speed", capitalise(item.attackSpeed())));
        }
        if (item.powderSlots() > 0) {
            lines.add(Line.plain("Powder slots", String.valueOf(item.powderSlots())));
        }

        boolean anyRequirement = item.requirements().values().stream().anyMatch(value -> value != 0);
        if (anyRequirement) {
            lines.add(Line.heading("Requirements"));
            for (int i = 0; i < Identifications.REQUIREMENT_KEYS.size(); i++) {
                int value = item.requirements().getOrDefault(Identifications.REQUIREMENT_KEYS.get(i), 0);
                if (value != 0) {
                    lines.add(Line.plain(Identifications.SKILL_POINT_ORDER_NAMES.get(i), String.valueOf(value)));
                }
            }
        }

        Map<String, IdentificationRolls.Range> ranges = IdentificationRolls.ranges(item);
        if (!ranges.isEmpty()) {
            lines.add(Line.heading(item.fixedIds() ? "Identifications (fixed)" : "Identifications (min - max)"));
            ranges.entrySet().stream()
                    .filter(entry -> Identifications.isDisplayable(entry.getKey()))
                    .sorted(java.util.Comparator.comparing(
                            entry -> Identifications.displayName(entry.getKey()),
                            String.CASE_INSENSITIVE_ORDER))
                    .forEach(entry -> {
                        IdentificationRolls.Range range = entry.getValue();
                        boolean percentage = Identifications.isPercentage(entry.getKey());
                        String suffix = percentage ? "%" : "";
                        String text = range.isFixed()
                                ? formatSigned(range.best()) + suffix
                                : formatSigned(range.lower()) + " to " + formatSigned(range.upper()) + suffix;
                        lines.add(Line.stat(
                                Identifications.displayName(entry.getKey()),
                                text,
                                range.best(),
                                Identifications.isInverted(entry.getKey())));
                    });
        }

        if (!item.majorIds().isEmpty()) {
            lines.add(Line.heading("Major IDs"));
            for (String majorId : item.majorIds()) {
                lines.add(Line.plain(majorId, ""));
            }
        }
        return lines;
    }

    /**
     * Describes a crafted item: its recipe, materials, ingredients, and the resulting stat ranges.
     *
     * <p>A craft has no fixed identifications — every stat is a span determined by the ingredients
     * and their positions — so the whole breakdown is presented as ranges.
     */
    public static List<Line> forCraft(CraftedItem craft, WynnDataSet data) {
        List<Line> lines = new ArrayList<>();
        if (craft == null || data == null) {
            return lines;
        }
        WynnRecipe recipe = data.recipe(craft.recipeId());
        CraftCalc.Result result = CraftCalc.compute(craft, data);

        lines.add(Line.plain("Crafted " + (result.type().isEmpty() ? "item" : result.type()),
                "Lv. " + result.levelMin() + "-" + result.levelMax()));
        if (recipe != null) {
            lines.add(Line.plain("Profession", capitalise(recipe.profession())));
        }
        lines.add(Line.plain("Materials", "Tier " + craft.materialTier1() + " and tier " + craft.materialTier2()));

        if (result.isWeapon()) {
            lines.add(Line.plain("Attack speed", craft.attackSpeed().label()));
            lines.add(Line.plain("Neutral damage", result.neutralDamage()[0] + "-" + result.neutralDamage()[1]));
        } else if (result.isArmour()) {
            lines.add(Line.plain("Health", result.healthOrDamage()[0] + "-" + result.healthOrDamage()[1]));
        }
        if (result.isConsumable()) {
            lines.add(Line.plain("Duration", result.duration()[0] + "-" + result.duration()[1] + "s"));
            lines.add(Line.plain("Charges", String.valueOf(result.charges())));
        } else {
            lines.add(Line.plain("Durability", result.durability()[0] + "-" + result.durability()[1]));
        }

        // The ingredient grid belongs to the crafter, which is one click away, so it is deliberately
        // not repeated here.
        boolean anyRequirement = result.requirements().values().stream().anyMatch(value -> value != 0);
        if (anyRequirement) {
            lines.add(Line.heading("Requirements"));
            for (int i = 0; i < Identifications.REQUIREMENT_KEYS.size(); i++) {
                int value = result.requirements().getOrDefault(Identifications.REQUIREMENT_KEYS.get(i), 0);
                if (value != 0) {
                    lines.add(Line.plain(Identifications.SKILL_POINT_ORDER_NAMES.get(i), String.valueOf(value)));
                }
            }
        }

        if (!result.identificationRanges().isEmpty()) {
            lines.add(Line.heading("Identifications (min - max)"));
            result.identificationRanges().entrySet().stream()
                    .filter(entry -> Identifications.isDisplayable(entry.getKey()))
                    .sorted(java.util.Comparator.comparing(
                            entry -> Identifications.displayName(entry.getKey()),
                            String.CASE_INSENSITIVE_ORDER))
                    .forEach(entry -> {
                        int[] range = entry.getValue();
                        boolean percentage = Identifications.isPercentage(entry.getKey());
                        String suffix = percentage ? "%" : "";
                        String text = range[0] == range[1]
                                ? formatSigned(range[0]) + suffix
                                : formatSigned(range[0]) + " to " + formatSigned(range[1]) + suffix;
                        lines.add(Line.stat(
                                Identifications.displayName(entry.getKey()),
                                text,
                                range[1],
                                Identifications.isInverted(entry.getKey())));
                    });
        }

        for (String warning : result.warnings()) {
            lines.add(Line.stat(warning, "", -1, false));
        }
        return lines;
    }

    private static String formatSigned(int value) {
        return value > 0 ? "+" + value : String.valueOf(value);
    }

    private static String capitalise(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        return Character.toUpperCase(text.charAt(0)) + text.substring(1).toLowerCase(java.util.Locale.ROOT);
    }
}
