package com.seqwawa.seq.integrations;

import com.seqwawa.seq.managers.ItemScaleService;
import com.seqwawa.seq.managers.ItemScaleTooltip;
import com.seqwawa.seq.managers.ThemeManager;
import com.seqwawa.seq.model.ItemScale;
import com.seqwawa.seq.ui.theme.UiColor;
import com.wynntils.core.components.Managers;
import com.wynntils.core.components.Models;
import com.wynntils.features.tooltips.ItemStatInfoFeature;
import com.wynntils.handlers.tooltip.impl.identifiable.IdentifiableTooltipBuilder;
import com.wynntils.handlers.tooltip.type.TooltipIdentificationDecorator;
import com.wynntils.handlers.tooltip.type.TooltipStyle;
import com.wynntils.models.gear.type.ItemWeightSource;
import com.wynntils.models.items.properties.IdentifiableItemProperty;
import com.wynntils.models.stats.StatCalculator;
import com.wynntils.models.stats.type.StatActualValue;
import com.wynntils.models.stats.type.StatListOrdering;
import com.wynntils.models.stats.type.StatPossibleValues;
import com.wynntils.models.stats.type.StatType;
import com.wynntils.utils.wynn.ColorScaleUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

@SuppressWarnings("rawtypes")
public final class WynntilsItemScaleAccess {
    // The decorator only hands back a Component, so the weight rides along as a style insertion
    // and is picked back up once the whole tooltip has been built.
    private static final String WEIGHT_MARKER = "seq_scale:";

    private static final FontDescription DEFAULT_FONT =
            new FontDescription.Resource(Identifier.withDefaultNamespace("default"));
    private static final int TITLE_SEARCH_DEPTH = 8;

    private WynntilsItemScaleAccess() {}

    public static void decorate(ItemStack stack, List<Component> lines) {
        Optional<IdentifiableItemProperty> identifiable =
                Models.Item.asWynnItemProperty(stack, IdentifiableItemProperty.class);
        if (identifiable.isEmpty()) {
            return;
        }

        IdentifiableItemProperty<?, ?> item = identifiable.get();
        if (item.getItemInstance().isEmpty()) {
            return;
        }

        ItemScale scale = ItemScaleTooltip.showItemScale()
                ? ItemScaleService.getInstance().scaleFor(item.getName())
                : null;

        List<Component> rebuilt = buildTooltip(stack, item, scale);
        if (rebuilt == null || rebuilt.isEmpty()) {
            return;
        }

        Map<String, Float> rolls = rolls(item);
        List<Component> expanded = expandWeightLines(rebuilt);
        appendOverallPercentage(expanded, item, rolls);

        if (scale != null) {
            Float value = scale.score(rolls);
            if (value != null) {
                insertScaleBlock(expanded, value);
            }
        }

        lines.clear();
        lines.addAll(expanded);
    }

    private static List<Component> buildTooltip(ItemStack stack, IdentifiableItemProperty<?, ?> item, ItemScale scale) {
        IdentifiableTooltipBuilder builder = IdentifiableTooltipBuilder.fromParsedItemStack(stack, item);
        if (builder == null) {
            return null;
        }

        TooltipStyle style = new TooltipStyle(StatListOrdering.DEFAULT, true, true, false, false);
        return builder.getTooltipLines(
                Models.Character.getClassType(), style, decorator(scale), ItemWeightSource.NONE, null);
    }

    private static TooltipIdentificationDecorator decorator(ItemScale scale) {
        return (actualValue, possibleValues, style) -> {
            float percentage = StatCalculator.getPercentage(actualValue, possibleValues);
            if (!Float.isFinite(percentage)) {
                return Component.empty();
            }

            MutableComponent suffix = Component.literal(" ").append(percentageComponent(percentage));
            Double weight = scale == null || !ItemScaleTooltip.showStatWeights()
                    ? null
                    : scale.weights().get(actualValue.statType().getApiName());
            if (weight != null) {
                suffix.setStyle(suffix.getStyle()
                        .withInsertion(WEIGHT_MARKER + String.format(Locale.ROOT, "%.1f", weight)));
            }
            return suffix;
        };
    }

    private static List<Component> expandWeightLines(List<Component> lines) {
        List<Component> expanded = new ArrayList<>(lines.size() + 8);
        for (Component line : lines) {
            expanded.add(line);
            String weight = findWeightMarker(line);
            if (weight != null) {
                expanded.add(Component.literal("   ↳ Scale : ")
                        .withStyle(defaultFont(style(UiColor.TEXT_DISABLED)))
                        .append(Component.literal(weight + "%").withStyle(style(UiColor.ACCENT_PRIMARY))));
            }
        }
        return expanded;
    }

    private static String findWeightMarker(Component component) {
        String insertion = component.getStyle().getInsertion();
        if (insertion != null && insertion.startsWith(WEIGHT_MARKER)) {
            return insertion.substring(WEIGHT_MARKER.length());
        }
        for (Component sibling : component.getSiblings()) {
            String found = findWeightMarker(sibling);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static void appendOverallPercentage(
            List<Component> lines, IdentifiableItemProperty<?, ?> item, Map<String, Float> rolls) {
        float overall = overallPercentage(item, rolls);
        if (!Float.isFinite(overall)) {
            return;
        }

        int index = titleIndex(lines, item.getName());
        if (index < 0) {
            return;
        }

        MutableComponent suffix = Component.literal(" ")
                .append(percentageComponent(overall))
                .withStyle(defaultFont(Style.EMPTY));
        lines.set(index, Component.empty().append(lines.get(index)).append(suffix));
    }

    private static float overallPercentage(IdentifiableItemProperty<?, ?> item, Map<String, Float> rolls) {
        if (item.hasOverallValue()) {
            float overall = item.getOverallPercentage();
            if (Float.isFinite(overall)) {
                return overall;
            }
        }
        if (rolls.isEmpty()) {
            return Float.NaN;
        }

        float total = 0.0f;
        for (float roll : rolls.values()) {
            total += roll;
        }
        return total / rolls.size();
    }

    private static void insertScaleBlock(List<Component> lines, float value) {
        List<Component> block = List.of(
                Component.empty(),
                Component.literal("Seqcale").withStyle(defaultFont(style(UiColor.ACCENT_PRIMARY))),
                Component.literal("  Main ")
                        .withStyle(defaultFont(style(UiColor.TEXT_MUTED)))
                        .append(percentageComponent(value)));

        lines.addAll(blockIndex(lines), block);
    }

    private static int blockIndex(List<Component> lines) {
        for (int i = 1; i < Math.min(lines.size(), TITLE_SEARCH_DEPTH); i++) {
            if (lines.get(i).getString().isBlank()) {
                return i;
            }
        }
        return Math.min(1, lines.size());
    }

    private static int titleIndex(List<Component> lines, String itemName) {
        int match = -1;
        int limit = Math.min(lines.size(), TITLE_SEARCH_DEPTH);
        for (int i = 0; i < limit; i++) {
            if (itemName != null && lines.get(i).getString().contains(itemName)) {
                match = i;
            }
        }
        if (match >= 0) {
            return match;
        }

        for (int i = 0; i < lines.size(); i++) {
            if (!lines.get(i).getString().isBlank()) {
                return i;
            }
        }
        return -1;
    }

    private static Map<String, Float> rolls(IdentifiableItemProperty<?, ?> item) {
        Map<StatType, StatPossibleValues> possibleByType = new HashMap<>();
        for (StatPossibleValues values : item.getPossibleValues()) {
            if (values != null && values.statType() != null) {
                possibleByType.putIfAbsent(values.statType(), values);
            }
        }

        Map<String, Float> rolls = new HashMap<>();
        for (StatActualValue actual : item.getIdentifications()) {
            StatPossibleValues values = possibleByType.get(actual.statType());
            if (values == null) {
                continue;
            }
            float percentage = StatCalculator.getPercentage(actual, values);
            if (Float.isFinite(percentage)) {
                rolls.put(actual.statType().getApiName(), percentage);
            }
        }
        return rolls;
    }

    private static MutableComponent percentageComponent(float percentage) {
        try {
            ItemStatInfoFeature feature = Managers.Feature.getFeatureInstance(ItemStatInfoFeature.class);
            if (feature != null) {
                return ColorScaleUtils.getPercentageTextComponent(
                        feature.getColorMap(), percentage, feature.colorLerp.get(), feature.decimalPlaces.get());
            }
        } catch (LinkageError | RuntimeException ignored) {
        }
        return Component.literal(String.format(Locale.ROOT, "[%.1f%%]", percentage))
                .withStyle(Style.EMPTY.withColor(fallbackRollColor(percentage)));
    }

    private static int fallbackRollColor(float percentage) {
        if (percentage >= 100.0f) {
            return 0xFF55FF;
        }
        if (percentage >= 95.0f) {
            return 0x55FFFF;
        }
        if (percentage >= 80.0f) {
            return 0x55FF55;
        }
        if (percentage >= 60.0f) {
            return 0xFFFF55;
        }
        if (percentage >= 30.0f) {
            return 0xFFAA00;
        }
        return 0xFF5555;
    }

    private static Style defaultFont(Style style) {
        return style.withFont(DEFAULT_FONT).withBold(false).withItalic(false).withObfuscated(false);
    }

    private static Style style(UiColor token) {
        return Style.EMPTY.withColor(ThemeManager.color(token).getRGB() & 0xFFFFFF);
    }
}
