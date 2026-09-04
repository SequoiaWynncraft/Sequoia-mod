package com.seqwawa.seq.integrations;

import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.wynnbuilder.data.EquipmentSlot;
import com.seqwawa.seq.wynnbuilder.data.WynnItem;
import com.seqwawa.seq.wynnbuilder.live.LiveItem;
import com.wynntils.core.components.Models;
import com.wynntils.models.character.type.ClassType;
import com.wynntils.models.elements.type.Element;
import com.wynntils.models.elements.type.Powder;
import com.wynntils.models.elements.type.Skill;
import com.wynntils.models.gear.type.GearAttackSpeed;
import com.wynntils.models.gear.type.GearInfo;
import com.wynntils.models.gear.type.GearRequirements;
import com.wynntils.models.gear.type.GearTier;
import com.wynntils.models.gear.type.GearType;
import com.wynntils.models.items.items.game.CraftedGearItem;
import com.wynntils.models.items.items.game.GearItem;
import com.wynntils.models.stats.type.DamageType;
import com.wynntils.models.wynnitem.parsing.WynnItemParseResult;
import com.wynntils.models.wynnitem.parsing.WynnItemParser;
import com.wynntils.utils.type.Pair;
import com.wynntils.utils.type.RangedValue;
import com.wynntils.utils.wynn.InventoryUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.ToIntFunction;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Reads the nine pieces the player is wearing, resolved to the numbers the calculator needs.
 *
 * <p>This is the trick {@link WynntilsItemPreviewAccess} plays on chat messages, pointed at the
 * inventory instead: Wynntils has already turned each stack into a typed item carrying its real
 * rolls, so nothing here decodes anything. What it adds is the tooltip's own damage, health and
 * defence figures, read through {@link WynnItemParser}, because those already have the powders
 * applied. That matters more than it looks — Wynncraft never prints a powder's tier and Wynntils
 * assumes six, so deriving damage from an item's base stats would silently mis-state every powdered
 * weapon. Taking the printed numbers instead means the tier never has to be known.
 */
public final class WynntilsEquipmentAccess {
    private static final String WYNNTILS_MOD_ID = "wynntils";

    /** Wynncraft damage lines, keyed the way the damage pipeline expects them. */
    private static final Map<DamageType, String> DAMAGE_KEYS = Map.of(
            DamageType.NEUTRAL, "nDam",
            DamageType.EARTH, "eDam",
            DamageType.THUNDER, "tDam",
            DamageType.WATER, "wDam",
            DamageType.FIRE, "fDam",
            DamageType.AIR, "aDam");

    private static final Map<Element, String> DEFENCE_KEYS = Map.of(
            Element.EARTH, "eDef",
            Element.THUNDER, "tDef",
            Element.WATER, "wDef",
            Element.FIRE, "fDef",
            Element.AIR, "aDef");

    private static final Map<Element, String> REQUIREMENT_KEYS = Map.of(
            Element.EARTH, "strReq",
            Element.THUNDER, "dexReq",
            Element.WATER, "intReq",
            Element.FIRE, "defReq",
            Element.AIR, "agiReq");

    private WynntilsEquipmentAccess() {}

    /** What the player is wearing, plus anything that could not be read. */
    public record Loadout(Map<EquipmentSlot, LiveItem> items, int level, List<String> notes) {
        public Loadout {
            items = Collections.unmodifiableMap(new EnumMap<>(items));
            notes = List.copyOf(notes);
        }

        public static Loadout empty() {
            return new Loadout(new EnumMap<>(EquipmentSlot.class), 0, List.of());
        }

        public boolean isEmpty() {
            return items.isEmpty();
        }

        public LiveItem item(EquipmentSlot slot) {
            return items.get(slot);
        }
    }

    public static boolean isAvailable() {
        return FabricLoader.getInstance().isModLoaded(WYNNTILS_MOD_ID) && Minecraft.getInstance().player != null;
    }

    /**
     * The stacks occupying each equipment slot, in slot order.
     *
     * <p>Kept separate from {@link #read} so a caller can tell whether anything changed without
     * paying for the parse, which walks every tooltip line of every piece.
     */
    public static List<ItemStack> equippedStacks() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return List.of();
        }
        List<ItemStack> accessories = InventoryUtils.getAccessories(player);
        List<ItemStack> stacks = new ArrayList<>(EquipmentSlot.encodingOrder().size());
        for (EquipmentSlot slot : EquipmentSlot.encodingOrder()) {
            stacks.add(stackFor(player, slot, accessories));
        }
        return stacks;
    }

    /**
     * Reads the whole loadout.
     *
     * @param itemIdByName resolves a dropped item's WynnBuilder ID from its name so an equipped
     *     build can still be written as a link; may return a negative value when unknown
     */
    public static Loadout read(ToIntFunction<String> itemIdByName) {
        if (!isAvailable()) {
            return Loadout.empty();
        }
        try {
            LocalPlayer player = Minecraft.getInstance().player;
            List<ItemStack> accessories = InventoryUtils.getAccessories(player);
            Map<EquipmentSlot, LiveItem> items = new EnumMap<>(EquipmentSlot.class);
            List<String> notes = new ArrayList<>();

            for (EquipmentSlot slot : EquipmentSlot.encodingOrder()) {
                ItemStack stack = stackFor(player, slot, accessories);
                if (stack == null || stack.isEmpty()) {
                    continue;
                }
                Optional<LiveItem> item = read(slot, stack, itemIdByName);
                if (item.isPresent()) {
                    items.put(slot, item.get());
                } else if (slot != EquipmentSlot.WEAPON) {
                    // The weapon slot is allowed to hold anything, so a miss there is not a fault.
                    notes.add(slot.label() + " could not be read");
                }
            }
            return new Loadout(items, Models.CharacterStats.getLevel(), notes);
        } catch (LinkageError | RuntimeException e) {
            SeqClient.LOGGER.debug("[Wynntils] Equipment could not be read", e);
            return Loadout.empty();
        }
    }

    private static ItemStack stackFor(LocalPlayer player, EquipmentSlot slot, List<ItemStack> accessories) {
        return switch (slot) {
            case HELMET -> player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD);
            case CHESTPLATE -> player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST);
            case LEGGINGS -> player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.LEGS);
            case BOOTS -> player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.FEET);
            case RING1 -> accessory(accessories, 0);
            case RING2 -> accessory(accessories, 1);
            case BRACELET -> accessory(accessories, 2);
            case NECKLACE -> accessory(accessories, 3);
            case WEAPON -> weaponStack(player);
        };
    }

    /**
     * Accessories live in four fixed inventory slots, which Wynntils already knows about.
     *
     * <p>The list is fetched once per read rather than once per slot: this runs on every frame the
     * panel is up, and four throwaway lists a frame is four too many.
     */
    private static ItemStack accessory(List<ItemStack> accessories, int index) {
        return index < accessories.size() ? accessories.get(index) : ItemStack.EMPTY;
    }

    /**
     * The weapon the class actually fights with.
     *
     * <p>Wynncraft keeps it in the first hotbar slot, but the player can be holding anything at the
     * moment the inventory opens, so the hotbar is searched for a piece the current class can wield
     * rather than trusting the main hand.
     */
    private static ItemStack weaponStack(LocalPlayer player) {
        ClassType classType = Models.Character.getClassType();
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            GearType type = gearType(stack);
            if (type == null) {
                continue;
            }
            // Before Wynntils knows the class — early on a world join — any weapon will do; the
            // weapon type then settles the class rather than the other way round.
            if (classType == null ? isWeapon(type) : type.isValidWeapon(classType)) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    private static GearType gearType(ItemStack stack) {
        Optional<GearItem> gear = Models.Item.asWynnItem(stack, GearItem.class);
        if (gear.isPresent()) {
            return gear.get().getGearType();
        }
        return Models.Item.asWynnItem(stack, CraftedGearItem.class)
                .map(CraftedGearItem::getGearType)
                .orElse(null);
    }

    private static Optional<LiveItem> read(
            EquipmentSlot slot, ItemStack stack, ToIntFunction<String> itemIdByName) {
        Optional<CraftedGearItem> crafted = Models.Item.asWynnItem(stack, CraftedGearItem.class);
        if (crafted.isPresent()) {
            return Optional.of(readCrafted(slot, crafted.get()));
        }
        return Models.Item.asWynnItem(stack, GearItem.class)
                .filter(gear -> !gear.isUnidentified())
                .map(gear -> readGear(slot, stack, gear, itemIdByName));
    }

    // ------------------------------------------------------------------ dropped gear

    private static LiveItem readGear(
            EquipmentSlot slot, ItemStack stack, GearItem gear, ToIntFunction<String> itemIdByName) {

        GearInfo info = gear.getItemInfo();
        // The tooltip's own damage, health and defences, which already carry the powders.
        WynnItemParseResult printed = WynnItemParser.parseItemStack(stack, info.getVariableStatsMap());

        WynntilsStatRolls.Rolls rolls =
                WynntilsStatRolls.read(gear.getIdentifications(), gear.getPossibleValues());
        int itemId = itemIdByName == null ? -1 : itemIdByName.applyAsInt(info.name());
        String setName = gear.getSetInfo().map(set -> set.name()).orElse(null);
        List<String> majorIds =
                info.fixedStats().majorIds().map(majorId -> List.of(majorId.name())).orElseGet(List::of);

        WynnItem actual = printedItem(itemId, info.name(), gear.getGearType(), gear.getGearTier(),
                gear.getLevel(), printed, rolls.actual(), majorIds, setName, gear.getPowderSlots());
        WynnItem best = printedItem(itemId, info.name(), gear.getGearType(), gear.getGearTier(),
                gear.getLevel(), printed, rolls.best(), majorIds, setName, gear.getPowderSlots());

        Float quality = gear.hasOverallValue() ? gear.getOverallPercentage() : null;
        return new LiveItem(slot, info.name(), tier(gear.getGearTier()).label(), false, actual, best,
                quality, rolls.detail(), powderLabels(gear.getPowders()));
    }

    // ------------------------------------------------------------------ crafted gear

    /**
     * Reads a player-made piece.
     *
     * <p>A craft prints its damage, health and defences through Wynntils already, so unlike dropped
     * gear nothing has to be re-read from the tooltip. Its ceiling is the top of each
     * ingredient-derived range, which is what a perfect craft of the same recipe would give.
     */
    private static LiveItem readCrafted(EquipmentSlot slot, CraftedGearItem crafted) {
        WynntilsStatRolls.Rolls rolls =
                WynntilsStatRolls.read(crafted.getIdentifications(), crafted.getPossibleValues());

        Map<String, int[]> damages = new LinkedHashMap<>();
        for (Pair<DamageType, RangedValue> damage : crafted.getDamages()) {
            String key = DAMAGE_KEYS.get(damage.key());
            if (key != null) {
                damages.put(key, new int[] {damage.value().low(), damage.value().high()});
            }
        }
        Map<String, Integer> defences = new LinkedHashMap<>();
        for (Pair<Element, Integer> defence : crafted.getDefences()) {
            String key = DEFENCE_KEYS.get(defence.key());
            if (key != null) {
                defences.merge(key, defence.value(), Integer::sum);
            }
        }
        String attackSpeed = crafted.getAttackSpeed().map(GearAttackSpeed::name).orElse("NORMAL");
        Map<String, Integer> requirements = requirements(crafted.getRequirements());

        WynnItem actual = item(-1, crafted.getName(), crafted.getGearType(), GearTier.CRAFTED,
                crafted.getLevel(), damages, defences, crafted.getHealth(), requirements, attackSpeed,
                rolls.actual(), List.of(), null, crafted.getPowderSlots());
        WynnItem best = item(-1, crafted.getName(), crafted.getGearType(), GearTier.CRAFTED,
                crafted.getLevel(), damages, defences, crafted.getHealth(), requirements, attackSpeed,
                rolls.best(), List.of(), null, crafted.getPowderSlots());

        return new LiveItem(slot, crafted.getName(), "Crafted", true, actual, best,
                null, rolls.detail(), powderLabels(crafted.getPowders()));
    }

    // ------------------------------------------------------------------ shared

    /** Builds the calculator's item from a dropped piece's printed stats. */
    private static WynnItem printedItem(
            int id, String name, GearType type, GearTier tier, int level, WynnItemParseResult printed,
            Map<String, Integer> identifications, List<String> majorIds, String setName, int powderSlots) {

        Map<String, int[]> damages = new LinkedHashMap<>();
        for (Pair<DamageType, RangedValue> damage : printed.damages()) {
            String key = DAMAGE_KEYS.get(damage.key());
            if (key != null) {
                damages.put(key, new int[] {damage.value().low(), damage.value().high()});
            }
        }
        Map<String, Integer> defences = new LinkedHashMap<>();
        for (Pair<Element, Integer> defence : printed.defences()) {
            String key = DEFENCE_KEYS.get(defence.key());
            if (key != null) {
                defences.merge(key, defence.value(), Integer::sum);
            }
        }
        String attackSpeed = printed.attackSpeed() == null ? "NORMAL" : printed.attackSpeed().name();
        return item(id, name, type, tier, level, damages, defences, printed.health(),
                requirements(printed.requirements()), attackSpeed, identifications, majorIds, setName, powderSlots);
    }

    private static WynnItem item(
            int id, String name, GearType type, GearTier tier, int level,
            Map<String, int[]> damages, Map<String, Integer> defences, int health,
            Map<String, Integer> requirements, String attackSpeed,
            Map<String, Integer> identifications, List<String> majorIds, String setName, int powderSlots) {

        return new WynnItem(
                id,
                name,
                name,
                category(type),
                type == null ? "" : type.name().toLowerCase(Locale.ROOT),
                tier(tier),
                level,
                null,
                attackSpeed,
                powderSlots,
                requirements,
                damages,
                defences,
                health,
                identifications,
                majorIds,
                setName,
                // The roll already happened. Marking the identifications fixed is what stops the
                // calculator re-rolling numbers that are no longer hypothetical.
                true,
                null,
                null);
    }

    private static Map<String, Integer> requirements(GearRequirements requirements) {
        Map<String, Integer> byKey = new LinkedHashMap<>();
        if (requirements == null) {
            return byKey;
        }
        for (Pair<Skill, Integer> skill : requirements.skills()) {
            String key = REQUIREMENT_KEYS.get(skill.key().getAssociatedElement());
            if (key != null) {
                byKey.merge(key, skill.value(), Integer::sum);
            }
        }
        return byKey;
    }

    private static boolean isWeapon(GearType type) {
        return "weapon".equals(category(type));
    }

    private static String category(GearType type) {
        if (type == null) {
            return "";
        }
        return switch (type) {
            case SPEAR, WAND, DAGGER, BOW, RELIK, WEAPON -> "weapon";
            case HELMET, CHESTPLATE, LEGGINGS, BOOTS -> "armor";
            default -> "accessory";
        };
    }

    private static WynnItem.Tier tier(GearTier tier) {
        return tier == null ? WynnItem.Tier.NORMAL : WynnItem.Tier.parse(tier.name());
    }

    private static List<String> powderLabels(List<Powder> powders) {
        if (powders == null || powders.isEmpty()) {
            return List.of();
        }
        return powders.stream().map(powder -> String.valueOf(powder.getSymbol())).toList();
    }
}
