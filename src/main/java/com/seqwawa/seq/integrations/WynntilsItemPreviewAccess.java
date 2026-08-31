package com.seqwawa.seq.integrations;

import com.wynntils.core.components.Models;
import com.wynntils.models.items.WynnItem;
import com.wynntils.models.items.items.game.CharmItem;
import com.wynntils.models.items.items.game.CraftedConsumableItem;
import com.wynntils.models.items.items.game.CraftedGearItem;
import com.wynntils.models.items.items.game.GearItem;
import com.wynntils.models.items.items.game.MountItem;
import com.wynntils.models.items.items.game.TomeItem;
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
import com.wynntils.models.stats.type.FixedStats;
import com.wynntils.models.stats.type.StatActualValue;
import com.wynntils.models.stats.type.StatPossibleValues;
import com.wynntils.models.gear.type.GearRequirements;
import com.wynntils.models.mount.type.MountInfo;
import com.wynntils.models.mount.type.MountStat;
import com.wynntils.utils.EncodedByteBuffer;
import com.wynntils.utils.type.CappedValue;
import com.wynntils.utils.type.ErrorOr;
import com.wynntils.utils.type.Pair;
import com.wynntils.utils.type.RangedValue;
import java.lang.reflect.InvocationTargetException;
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
        ErrorOr<WynnItem> decoded = decodeChatItem(encodedByteBuffer, itemName);
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
                name, subtitle, color, attributes, statLines, statRolls, shinyStatPreview, sections(item)));
    }

    private static ErrorOr<WynnItem> decodeChatItem(EncodedByteBuffer encodedByteBuffer, String itemName) {
        try {
            return Models.ItemEncoding.decodeItemWithTrustedName(encodedByteBuffer, itemName);
        } catch (NoSuchMethodError ignored) {
            return Models.ItemEncoding.decodeItem(encodedByteBuffer, itemName);
        }
    }

    private static String previewKey(ChatItemPreview preview) {
        return String.join(
                "\u001F",
                normalizePreviewPart(preview.name()),
                normalizePreviewPart(preview.subtitle()),
                normalizePreviewPart(preview.attributes()),
                normalizePreviewPart(preview.statLines()),
                normalizeShinyStat(preview.shinyStat()),
                normalizeSections(preview.sections()));
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

    private static String normalizeSections(List<ChatItemPreview.Section> sections) {
        if (sections == null || sections.isEmpty()) {
            return "";
        }
        return sections.stream()
                .map(section -> normalizePreviewPart(section.title()) + ":" + normalizePreviewPart(section.lines()))
                .toList()
                .toString();
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
        } else if (item instanceof CraftedConsumableItem consumableItem) {
            parts.add(formatEnumName(consumableItem.getConsumableType()));
        } else if (item instanceof MountItem mountItem) {
            parts.add(formatEnumName(mountItem.getMountType()) + (mountItem.isSummonItem() ? " Summon" : " Mount"));
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
        String unit = stat.statType().getUnit().getDisplayName();
        String perfect = perfectRollMarker(stat);
        String percentage = formatRollPercentage(stat, possibleValue);
        return sign + stat.value() + unit + " " + stat.statType().getDisplayName() + perfect + percentage;
    }

    private static String perfectRollMarker(StatActualValue stat) {
        try {
            Object perfect = stat.getClass().getMethod("perfectInternalRoll").invoke(stat);
            return Boolean.TRUE.equals(perfect) ? " ✦" : "";
        } catch (NoSuchMethodException e) {
            try {
                Object stars = stat.getClass().getMethod("stars").invoke(stat);
                return stars instanceof Integer count && count > 0 ? " " + "✦".repeat(count) : "";
            } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException ignored) {
                return "";
            }
        } catch (IllegalAccessException | InvocationTargetException e) {
            return "";
        }
    }

    static List<ChatItemPreview.Section> sections(WynnItem item) {
        List<ChatItemPreview.Section> sections = new ArrayList<>();
        if (item instanceof GearItem gearItem) {
            FixedStats fixedStats = gearItem.getItemInfo().fixedStats();
            addSection(sections, "Base Stats", baseStatLines(
                    fixedStats.averageDps(),
                    fixedStats.healthBuff(),
                    fixedStats.attackSpeed().map(speed -> speed.getName()).orElse(null),
                    fixedStats.damages(),
                    fixedStats.defences()));
            fixedStats.majorIds().ifPresent(majorId -> addSection(
                    sections,
                    "Major ID: " + majorId.name(),
                    splitLines(majorId.lore().getStringWithoutFormatting())));
            addSection(sections, "Requirements", requirementLines(gearItem.getItemInfo().requirements()));
        } else if (item instanceof CraftedGearItem craftedGear) {
            addSection(sections, "Base Stats", baseStatLines(
                    craftedGear.getDps(),
                    craftedGear.getHealth(),
                    craftedGear.getAttackSpeed().map(speed -> speed.getName()).orElse(null),
                    craftedGear.getDamages(),
                    craftedGear.getDefences()));
            addSection(sections, "Requirements", requirementLines(craftedGear.getRequirements()));
        } else if (item instanceof CraftedConsumableItem consumable) {
            addSection(sections, "Consumable", consumableLines(consumable));
            addSection(sections, "Effects", consumableEffectLines(consumable));
        } else if (item instanceof MountItem mount) {
            addSection(sections, "Mount", mountLines(mount));
            addSection(sections, "Mount Stats", mountStatLines(mount.getMountInfo()));
        } else if (item instanceof TomeItem tome) {
            List<String> requirements = new ArrayList<>();
            requirements.add("Combat Level: " + tome.getItemInfo().requirements().level());
            if (tome.getItemInfo().requirements().tomeSeeking()) {
                requirements.add("Requires Tome Seeking");
            }
            addSection(sections, "Requirements", requirements);
        } else if (item instanceof CharmItem charm) {
            List<String> requirements = new ArrayList<>();
            requirements.add("Combat Level: " + charm.getItemInfo().requirements().level());
            RangedValue workingLevels = charm.getItemInfo().requirements().workingLevelRange();
            if (workingLevels != null && !workingLevels.equals(RangedValue.NONE)) {
                requirements.add("Effective Levels: " + formatRange(workingLevels));
            }
            addSection(sections, "Requirements", requirements);
        }
        return List.copyOf(sections);
    }

    private static List<String> baseStatLines(
            int averageDps,
            int health,
            String attackSpeed,
            List<? extends Pair<?, RangedValue>> damages,
            List<? extends Pair<?, Integer>> defences) {
        List<String> lines = new ArrayList<>();
        if (attackSpeed != null && !attackSpeed.isBlank()) {
            lines.add("Attack Speed: " + attackSpeed);
        }
        if (averageDps > 0) {
            lines.add("Average DPS: " + averageDps);
        }
        if (health != 0) {
            lines.add("Health: " + signed(health));
        }
        if (damages != null) {
            damages.stream()
                    .filter(pair -> pair != null && pair.a() != null && pair.b() != null)
                    .map(pair -> displayName(pair.a()) + " Damage: " + formatRange(pair.b()))
                    .forEach(lines::add);
        }
        if (defences != null) {
            defences.stream()
                    .filter(pair -> pair != null && pair.a() != null && pair.b() != null && pair.b() != 0)
                    .map(pair -> displayName(pair.a()) + " Defence: " + signed(pair.b()))
                    .forEach(lines::add);
        }
        return lines;
    }

    private static List<String> requirementLines(GearRequirements requirements) {
        if (requirements == null) {
            return List.of();
        }
        List<String> lines = new ArrayList<>();
        if (requirements.level() > 0) {
            lines.add("Combat Level: " + requirements.level());
        }
        requirements.classType()
                .filter(classType -> !"NONE".equals(classType.name()))
                .ifPresent(classType -> lines.add("Class: " + classType.getName()));
        if (requirements.skills() != null) {
            requirements.skills().stream()
                    .filter(pair -> pair != null && pair.a() != null && pair.b() != null && pair.b() > 0)
                    .map(pair -> pair.a().getDisplayName() + ": " + pair.b())
                    .forEach(lines::add);
        }
        requirements.quest().filter(quest -> !quest.isBlank()).ifPresent(quest -> lines.add("Quest: " + quest));
        return lines;
    }

    private static List<String> consumableLines(CraftedConsumableItem item) {
        List<String> lines = new ArrayList<>();
        CappedValue uses = item.getUses();
        if (uses != null && uses.max() > 0) {
            lines.add("Uses: " + uses.current() + "/" + uses.max());
        }
        return lines;
    }

    private static List<String> consumableEffectLines(CraftedConsumableItem item) {
        Map<String, String> lines = new LinkedHashMap<>();
        item.getNamedEffects().stream().filter(effect -> effect != null && effect.type() != null).forEach(effect -> {
            String label = formatEnumName(effect.type());
            String suffix = switch (effect.type().name()) {
                case "HEAL" -> " Health";
                case "MANA" -> " Mana";
                case "DURATION" -> "s";
                default -> "";
            };
            String value = "DURATION".equals(effect.type().name())
                    ? String.valueOf(effect.value())
                    : signed(effect.value());
            lines.putIfAbsent(label.toLowerCase(Locale.ROOT), label + ": " + value + suffix);
        });
        item.getEffects().stream().filter(effect -> effect != null && effect.type() != null).forEach(effect -> {
            String label = effect.type().trim();
            if (!label.isBlank()) {
                lines.putIfAbsent(label.toLowerCase(Locale.ROOT), label + ": " + signed(effect.value()));
            }
        });
        return List.copyOf(lines.values());
    }

    private static List<String> mountLines(MountItem item) {
        MountInfo info = item.getMountInfo();
        List<String> lines = new ArrayList<>();
        lines.add("Type: " + formatEnumName(item.getMountType()));
        lines.add("Form: " + (item.isSummonItem() ? "Summon Item" : "Mount Item"));
        if (info == null) {
            return lines;
        }
        if (info.potential() > 0) {
            lines.add("Potential: " + info.potential());
        }
        if (info.primaryColorInfo() != null) {
            lines.add("Primary Color: " + info.primaryColorInfo().displayName());
        }
        if (info.secondaryColorInfo() != null) {
            lines.add("Secondary Color: " + info.secondaryColorInfo().displayName());
        }
        CappedValue energy = info.currentEnergy();
        if (energy != null && energy.max() > 0) {
            lines.add("Energy: " + energy.current() + "/" + energy.max());
        }
        return lines;
    }

    private static List<String> mountStatLines(MountInfo info) {
        if (info == null || info.stats() == null || info.stats().isEmpty()) {
            return List.of();
        }
        List<String> lines = new ArrayList<>();
        for (MountStat stat : MountStat.values()) {
            CappedValue value = info.stats().get(stat);
            if (value == null) {
                continue;
            }
            StringBuilder line = new StringBuilder(stat.getName())
                    .append(": ")
                    .append(value.current())
                    .append('/')
                    .append(value.max());
            Integer maximum = info.maxStats() == null ? null : info.maxStats().get(stat);
            if (maximum != null && maximum > 0) {
                line.append(" • Max: ");
                if (info.estimatedMaxStats()) {
                    line.append('~');
                }
                line.append(maximum);
            }
            lines.add(line.toString());
        }
        return lines;
    }

    private static void addSection(
            List<ChatItemPreview.Section> sections, String title, List<String> rawLines) {
        List<String> lines = rawLines == null
                ? List.of()
                : rawLines.stream().filter(line -> line != null && !line.isBlank()).toList();
        if (!lines.isEmpty()) {
            sections.add(new ChatItemPreview.Section(title, lines));
        }
    }

    private static List<String> splitLines(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return value.lines().map(String::trim).filter(line -> !line.isBlank()).toList();
    }

    private static String signed(int value) {
        return value > 0 ? "+" + value : String.valueOf(value);
    }

    private static String formatRange(RangedValue range) {
        return range.isFixed() ? String.valueOf(range.low()) : range.low() + "–" + range.high();
    }

    private static String displayName(Object value) {
        try {
            Object displayName = value.getClass().getMethod("getDisplayName").invoke(value);
            if (displayName != null && !displayName.toString().isBlank()) {
                return displayName.toString().trim();
            }
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException ignored) {
            // Fall through to enum/object formatting.
        }
        return formatEnumName(value);
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
