package com.seqwawa.seq.integrations;

import com.wynntils.core.components.Models;
import com.wynntils.models.items.WynnItem;
import com.wynntils.models.items.properties.CraftedItemProperty;
import com.wynntils.models.items.properties.DurableItemProperty;
import com.wynntils.models.items.properties.GearTierItemProperty;
import com.wynntils.models.items.properties.GearTypeItemProperty;
import com.wynntils.models.items.properties.IdentifiableItemProperty;
import com.wynntils.models.items.properties.LeveledItemProperty;
import com.wynntils.models.items.properties.NamedItemProperty;
import com.wynntils.models.items.properties.PowderedItemProperty;
import com.wynntils.models.items.properties.RerollableItemProperty;
import com.wynntils.models.items.properties.ShinyItemProperty;
import com.wynntils.models.stats.StatCalculator;
import com.wynntils.models.stats.type.StatActualValue;
import com.wynntils.models.stats.type.StatPossibleValues;
import com.wynntils.utils.EncodedByteBuffer;
import com.wynntils.utils.type.CappedValue;
import com.wynntils.utils.type.ErrorOr;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.model.ChatItemPreview;

public final class WynntilsItemPreviewAccess {
    private static final String WYNNTILS_MOD_ID = "wynntils";
    private static final int MAX_ITEM_PREVIEWS = 10;
    private static final int MAX_STAT_LINES = 10;

    private WynntilsItemPreviewAccess() {}

    public static Result extract(String message) {
        if (message == null || message.isBlank()) {
            return new Result(message, List.of());
        }

        try {
            if (!FabricLoader.getInstance().isModLoaded(WYNNTILS_MOD_ID)) {
                return new Result(message, List.of());
            }

            Matcher matcher = Models.ItemEncoding.getEncodedDataPattern().matcher(message);
            StringBuffer cleaned = new StringBuffer();
            Map<String, ChatItemPreview> previews = new LinkedHashMap<>();

            while (matcher.find()) {
                Optional<ChatItemPreview> preview = decodePreview(matcher.group("data"), matcher.group("name"));
                if (preview.isPresent()) {
                    previews.putIfAbsent(previewKey(preview.get()), preview.get());
                    matcher.appendReplacement(cleaned, Matcher.quoteReplacement("[" + preview.get().name() + "]"));
                } else {
                    matcher.appendReplacement(cleaned, Matcher.quoteReplacement(matcher.group()));
                }
            }
            matcher.appendTail(cleaned);

            return new Result(cleaned.toString(), previews.values().stream().limit(MAX_ITEM_PREVIEWS).toList());
        } catch (LinkageError | RuntimeException e) {
            SeqClient.LOGGER.debug("[Wynntils] Item preview extraction failed", e);
            return new Result(message, List.of());
        }
    }

    private static Optional<ChatItemPreview> decodePreview(String encodedData, String itemName) {
        EncodedByteBuffer encodedByteBuffer = EncodedByteBuffer.fromUtf16String(encodedData);
        ErrorOr<WynnItem> decoded = Models.ItemEncoding.decodeItem(encodedByteBuffer, itemName);
        if (decoded.hasError()) {
            return Optional.empty();
        }

        WynnItem item = decoded.getValue();
        String name = item instanceof NamedItemProperty namedItem ? namedItem.getName() : item.getClass().getSimpleName();
        Optional<com.wynntils.models.stats.type.ShinyStat> shinyStat = shinyStat(item);
        if (shinyStat.isPresent()) {
            name = "Shiny " + name;
        }
        String subtitle = subtitle(item);
        Integer color = color(item);
        List<String> attributes = attributes(item);
        List<String> statLines = statLines(item);
        List<ChatItemPreview.StatRoll> statRolls = statRolls(item);
        ChatItemPreview.ShinyStat shinyStatPreview =
                shinyStat.map(WynntilsItemPreviewAccess::shinyStatPreview).orElse(null);
        return Optional.of(new ChatItemPreview(
                name, subtitle, color, attributes, statLines, statRolls, shinyStatPreview));
    }

    private static String previewKey(ChatItemPreview preview) {
        return String.join(
                "\u001F",
                normalizePreviewPart(preview.name()),
                normalizePreviewPart(preview.subtitle()),
                normalizePreviewPart(preview.attributes()),
                normalizePreviewPart(preview.statLines()),
                normalizeShinyStat(preview.shinyStat()));
    }

    private static String normalizePreviewPart(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizePreviewPart(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        return values.stream().map(WynntilsItemPreviewAccess::normalizePreviewPart).toList().toString();
    }

    private static String normalizeShinyStat(ChatItemPreview.ShinyStat shinyStat) {
        if (shinyStat == null) {
            return "";
        }
        return String.join(
                "\u001F",
                normalizePreviewPart(shinyStat.key()),
                normalizePreviewPart(shinyStat.displayName()),
                String.valueOf(shinyStat.value()),
                String.valueOf(shinyStat.rerolls()));
    }

    private static Optional<com.wynntils.models.stats.type.ShinyStat> shinyStat(WynnItem item) {
        if (item instanceof ShinyItemProperty shinyItem) {
            return shinyItem.getShinyStat();
        }
        return Optional.empty();
    }

    private static ChatItemPreview.ShinyStat shinyStatPreview(com.wynntils.models.stats.type.ShinyStat shinyStat) {
        return new ChatItemPreview.ShinyStat(
                shinyStat.statType().key(),
                shinyStat.statType().displayName(),
                shinyStat.value(),
                shinyStat.shinyRerolls());
    }

    private static String subtitle(WynnItem item) {
        List<String> parts = new ArrayList<>();
        if (item instanceof GearTierItemProperty tierItem) {
            parts.add(formatEnumName(tierItem.getGearTier()));
        }
        if (item instanceof GearTypeItemProperty typeItem) {
            parts.add(formatEnumName(typeItem.getGearType()));
        } else {
            parts.add(item.getClass().getSimpleName());
        }
        if (item instanceof LeveledItemProperty leveledItem && leveledItem.getLevel() > 0) {
            parts.add("Lv. " + leveledItem.getLevel());
        }
        return String.join(" • ", parts);
    }

    private static Integer color(WynnItem item) {
        if (!(item instanceof GearTierItemProperty tierItem)) {
            return null;
        }
        ChatFormatting formatting = tierItem.getGearTier().getChatFormatting();
        return formatting.getColor();
    }

    private static List<String> attributes(WynnItem item) {
        List<String> attributes = new ArrayList<>();
        if (item instanceof RerollableItemProperty rerollableItem && rerollableItem.getRerollCount() > 0) {
            attributes.add(rerollableItem.getRerollCount() + " rerolls");
        }
        if (item instanceof IdentifiableItemProperty<?, ?> identifiableItem && identifiableItem.hasOverallValue()) {
            attributes.add(String.format(Locale.ROOT, "%.1f%%", identifiableItem.getOverallPercentage()));
        }
        if (item instanceof DurableItemProperty durableItem) {
            CappedValue durability = durableItem.getDurability();
            if (durability != null && durability.max() > 0) {
                attributes.add("Durability " + durability.current() + "/" + durability.max());
            }
        }
        if (item instanceof PowderedItemProperty powderedItem && !powderedItem.getPowders().isEmpty()) {
            attributes.add("Powders: " + powderedItem.getPowders().stream()
                    .map(Object::toString)
                    .limit(5)
                    .reduce((left, right) -> left + ", " + right)
                    .orElse(""));
        }
        return List.copyOf(attributes);
    }

    private static List<String> statLines(WynnItem item) {
        List<StatActualValue> identifications;
        List<StatPossibleValues> possibleValues;
        if (item instanceof IdentifiableItemProperty<?, ?> identifiableItem) {
            identifications = identifiableItem.getIdentifications();
            possibleValues = identifiableItem.getPossibleValues();
        } else if (item instanceof CraftedItemProperty craftedItem) {
            identifications = craftedItem.getIdentifications();
            possibleValues = craftedItem.getPossibleValues();
        } else {
            return List.of();
        }

        Map<Object, StatPossibleValues> possibleValuesByType = possibleValuesByType(possibleValues);
        return identifications.stream()
                .limit(MAX_STAT_LINES)
                .map(stat -> formatStatLine(stat, possibleValuesByType.get(stat.statType())))
                .toList();
    }

    private static Map<Object, StatPossibleValues> possibleValuesByType(List<StatPossibleValues> possibleValues) {
        if (possibleValues == null || possibleValues.isEmpty()) {
            return Map.of();
        }

        Map<Object, StatPossibleValues> byType = new HashMap<>();
        for (StatPossibleValues possibleValue : possibleValues) {
            if (possibleValue != null && possibleValue.statType() != null) {
                byType.putIfAbsent(possibleValue.statType(), possibleValue);
            }
        }
        return byType;
    }

    private static String formatStatLine(StatActualValue stat, StatPossibleValues possibleValue) {
        String sign = stat.value() > 0 ? "+" : "";
        String stars = stat.stars() > 0 ? " " + "✦".repeat(stat.stars()) : "";
        String percentage = formatRollPercentage(stat, possibleValue);
        return sign + stat.value() + " " + stat.statType().getDisplayName() + stars + percentage;
    }

    private static String formatRollPercentage(StatActualValue stat, StatPossibleValues possibleValue) {
        if (stat == null || possibleValue == null) {
            return "";
        }

        try {
            float percentage = StatCalculator.getPercentage(stat, possibleValue);
            if (!Float.isFinite(percentage)) {
                return "";
            }
            return " [" + formatPercentage(percentage) + "]";
        } catch (RuntimeException e) {
            SeqClient.LOGGER.debug("[Wynntils] Failed to calculate stat roll percentage", e);
            return "";
        }
    }

    private static String formatPercentage(float percentage) {
        float rounded = Math.round(percentage * 10.0f) / 10.0f;
        if (Math.abs(rounded - Math.round(rounded)) < 0.05f) {
            return String.format(Locale.ROOT, "%.0f%%", rounded);
        }
        return String.format(Locale.ROOT, "%.1f%%", rounded);
    }

    private static List<ChatItemPreview.StatRoll> statRolls(WynnItem item) {
        List<StatActualValue> identifications;
        List<StatPossibleValues> possibleValues;
        if (item instanceof IdentifiableItemProperty<?, ?> identifiableItem) {
            identifications = identifiableItem.getIdentifications();
            possibleValues = identifiableItem.getPossibleValues();
        } else if (item instanceof CraftedItemProperty craftedItem) {
            identifications = craftedItem.getIdentifications();
            possibleValues = craftedItem.getPossibleValues();
        } else {
            return List.of();
        }

        Map<Object, StatPossibleValues> possibleValuesByType = possibleValuesByType(possibleValues);
        return identifications.stream()
                .map(stat -> statRoll(stat, possibleValuesByType.get(stat.statType())))
                .flatMap(Optional::stream)
                .toList();
    }

    private static Optional<ChatItemPreview.StatRoll> statRoll(
            StatActualValue stat, StatPossibleValues possibleValue) {
        if (stat == null || stat.statType() == null || possibleValue == null) {
            return Optional.empty();
        }

        try {
            float percentage = StatCalculator.getPercentage(stat, possibleValue);
            if (!Float.isFinite(percentage)) {
                return Optional.empty();
            }
            return Optional.of(new ChatItemPreview.StatRoll(
                    stat.statType().getApiName(),
                    stat.statType().getKey(),
                    stat.statType().getDisplayName(),
                    stat.value(),
                    percentage));
        } catch (RuntimeException e) {
            SeqClient.LOGGER.debug("[Wynntils] Failed to calculate structured stat roll percentage", e);
            return Optional.empty();
        }
    }

    private static String formatEnumName(Object value) {
        if (value == null) {
            return "";
        }
        String raw = value.toString().replace('_', ' ').toLowerCase(Locale.ROOT);
        StringBuilder formatted = new StringBuilder(raw.length());
        boolean capitalize = true;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            formatted.append(capitalize ? Character.toUpperCase(c) : c);
            capitalize = Character.isWhitespace(c);
        }
        return formatted.toString();
    }

    public record Result(String message, List<ChatItemPreview> previews) {}
}
