package org.sequoia.seq.integrations;

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
import com.wynntils.models.stats.type.StatActualValue;
import com.wynntils.utils.EncodedByteBuffer;
import com.wynntils.utils.type.CappedValue;
import com.wynntils.utils.type.ErrorOr;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import org.sequoia.seq.client.SeqClient;
import org.sequoia.seq.model.ChatItemPreview;

public final class WynntilsItemPreviewAccess {
    private static final String WYNNTILS_MOD_ID = "wynntils";
    private static final int MAX_ITEM_PREVIEWS = 5;
    private static final int MAX_STAT_LINES = 6;

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
            List<ChatItemPreview> previews = new ArrayList<>();

            while (matcher.find()) {
                Optional<ChatItemPreview> preview = previews.size() < MAX_ITEM_PREVIEWS
                        ? decodePreview(matcher.group("data"), matcher.group("name"))
                        : Optional.empty();
                if (preview.isPresent()) {
                    previews.add(preview.get());
                    matcher.appendReplacement(cleaned, Matcher.quoteReplacement("[" + preview.get().name() + "]"));
                } else {
                    matcher.appendReplacement(cleaned, Matcher.quoteReplacement(matcher.group()));
                }
            }
            matcher.appendTail(cleaned);

            return new Result(cleaned.toString(), List.copyOf(previews));
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
        String subtitle = subtitle(item);
        Integer color = color(item);
        List<String> attributes = attributes(item);
        List<String> statLines = statLines(item);
        return Optional.of(new ChatItemPreview(name, subtitle, color, attributes, statLines));
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
        if (item instanceof IdentifiableItemProperty<?, ?> identifiableItem) {
            identifications = identifiableItem.getIdentifications();
        } else if (item instanceof CraftedItemProperty craftedItem) {
            identifications = craftedItem.getIdentifications();
        } else {
            return List.of();
        }

        return identifications.stream()
                .limit(MAX_STAT_LINES)
                .map(WynntilsItemPreviewAccess::formatStatLine)
                .toList();
    }

    private static String formatStatLine(StatActualValue stat) {
        String sign = stat.value() > 0 ? "+" : "";
        String stars = stat.stars() > 0 ? " " + "✦".repeat(stat.stars()) : "";
        return sign + stat.value() + " " + stat.statType().getDisplayName() + stars;
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
