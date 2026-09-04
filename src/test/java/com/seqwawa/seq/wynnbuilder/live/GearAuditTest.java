package com.seqwawa.seq.wynnbuilder.live;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seqwawa.seq.integrations.WynntilsEquipmentAccess;
import com.seqwawa.seq.wynnbuilder.data.EquipmentSlot;
import com.seqwawa.seq.wynnbuilder.data.WynnDataFile;
import com.seqwawa.seq.wynnbuilder.data.WynnDataSet;
import com.seqwawa.seq.wynnbuilder.data.WynnItem;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The audit's promise is that it measures rather than judges, so these check the measurement rather
 * than the wording: a piece that rolled badly on a stat the build actually uses has to come out
 * ahead of one that rolled badly on a stat the build ignores.
 */
class GearAuditTest {

    private static final String ITEMS =
            """
            {"items": [
              {"name": "Blade", "displayName": "Blade", "category": "weapon", "type": "dagger",
               "tier": "Legendary", "lvl": 100, "id": 1, "atkSpd": "FAST", "fixID": true,
               "nDam": "50-90"}
            ]}
            """;

    private static WynnDataSet data() {
        Map<WynnDataFile, String> contents = new EnumMap<>(WynnDataFile.class);
        contents.put(WynnDataFile.ITEMS, ITEMS);
        return WynnDataSet.parse("test", contents);
    }

    @Test
    @DisplayName("names the piece whose roll costs the most damage, not the one with the worst percentage")
    void ranksByDamageLostRatherThanRollQuality() {
        // The bracelet rolled far worse as a percentage, but on a stat that changes nothing here.
        LiveItem goodRing = ring(EquipmentSlot.RING1, "Lucky Ring", "mdPct", 40, 40, 100f);
        LiveItem badRing = ring(EquipmentSlot.RING2, "Sad Ring", "mdPct", 8, 40, 20f);
        LiveItem bracelet = ring(EquipmentSlot.BRACELET, "Shiny Bracelet", "xpb", 4, 80, 5f);

        GearAudit.Result result = GearAudit.run(data(), snapshot(goodRing, badRing, bracelet));

        assertNotNull(result.worst());
        assertEquals(EquipmentSlot.RING2, result.worst().slot());
        assertEquals("Sad Ring", result.worst().itemName());
        assertTrue(result.worst().headroom() > 0, "a bad roll on a used stat has to cost damage");

        GearAudit.Finding maxed = find(result, EquipmentSlot.RING1);
        assertEquals(0, maxed.headroom(), 1e-6, "a maxed roll has nothing left to recover");
        assertTrue(maxed.contribution() > 0, "but it is still worth something");

        GearAudit.Finding irrelevant = find(result, EquipmentSlot.BRACELET);
        assertEquals(0, irrelevant.headroom(), 1e-6, "an unused stat cannot cost damage however it rolled");
    }

    @Test
    @DisplayName("a craft's negative ingredient is treated as removable, a dropped item's drawback is not")
    void craftedDrawbacksCountAgainstTheRecipe() {
        // Both carry the same drawback at the same roll; only the craft can be remade without it.
        LiveItem craft = new LiveItem(
                EquipmentSlot.RING1, "Crafted Ring", "Crafted", true,
                item(Map.of("mdPct", -30)), item(Map.of("mdPct", -30)), null,
                List.of(new LiveItem.Roll("mdPct", "Melee Damage %", -30, -30, 100)), List.of());
        LiveItem dropped = new LiveItem(
                EquipmentSlot.RING2, "Cursed Ring", "Mythic", false,
                item(Map.of("mdPct", -30)), item(Map.of("mdPct", -30)), 100f,
                List.of(new LiveItem.Roll("mdPct", "Melee Damage %", -30, -30, 100)), List.of());

        GearAudit.Result result = GearAudit.run(data(), snapshot(craft, dropped));

        GearAudit.Finding craftFinding = find(result, EquipmentSlot.RING1);
        GearAudit.Finding droppedFinding = find(result, EquipmentSlot.RING2);

        assertTrue(craftFinding.hasHarmfulRolls());
        assertTrue(craftFinding.headroom() > 0, "recrafting can drop the ingredient that hurts");
        assertEquals(0, droppedFinding.headroom(), 1e-6, "a printed drawback is part of the item");
        assertEquals(EquipmentSlot.RING1, result.worst().slot());
        assertTrue(craftFinding.advice().contains("recraft"));
    }

    @Test
    @DisplayName("says so rather than guessing when there is no weapon to measure with")
    void withoutAWeaponThereIsNothingToMeasure() {
        GearAudit.Result result = GearAudit.run(
                data(), snapshot(false, ring(EquipmentSlot.RING1, "Ring", "mdPct", 8, 40, 20f)));

        assertTrue(result.isEmpty());
        assertTrue(result.notes().stream().anyMatch(note -> note.toLowerCase().contains("weapon")));
    }

    // ------------------------------------------------------------------ fixtures

    private static GearAudit.Finding find(GearAudit.Result result, EquipmentSlot slot) {
        return result.findings().stream()
                .filter(finding -> finding.slot() == slot)
                .findFirst()
                .orElseThrow(() -> new AssertionError("no finding for " + slot));
    }

    private static EquippedBuild.Snapshot snapshot(LiveItem... items) {
        return snapshot(true, items);
    }

    private static EquippedBuild.Snapshot snapshot(boolean withWeapon, LiveItem... items) {
        Map<EquipmentSlot, LiveItem> equipped = new EnumMap<>(EquipmentSlot.class);
        if (withWeapon) {
            equipped.put(EquipmentSlot.WEAPON, weapon());
        }
        for (LiveItem item : items) {
            equipped.put(item.slot(), item);
        }
        WynntilsEquipmentAccess.Loadout loadout =
                new WynntilsEquipmentAccess.Loadout(equipped, 106, List.of());
        return EquippedBuild.assemble(data(), loadout, null, null, null);
    }

    private static LiveItem weapon() {
        WynnItem blade = new WynnItem(
                1, "Blade", "Blade", "weapon", "dagger", WynnItem.Tier.LEGENDARY, 100, null, "FAST", 0,
                Map.of(), Map.of("nDam", new int[] {50, 90}), Map.of(), 0, Map.of(),
                List.of(), null, true, null, null);
        return new LiveItem(
                EquipmentSlot.WEAPON, "Blade", "Legendary", false, blade, blade, 100f, List.of(), List.of());
    }

    private static LiveItem ring(EquipmentSlot slot, String name, String key, int actual, int best, float roll) {
        return new LiveItem(
                slot,
                name,
                "Rare",
                false,
                item(Map.of(key, actual)),
                item(Map.of(key, best)),
                roll,
                List.of(new LiveItem.Roll(key, key, actual, best, roll)),
                List.of());
    }

    private static WynnItem item(Map<String, Integer> identifications) {
        return new WynnItem(
                2, "Ring", "Ring", "accessory", "ring", WynnItem.Tier.RARE, 100, null, "NORMAL", 0,
                Map.of(), Map.of(), Map.of(), 0, identifications, List.of(), null, true, null, null);
    }
}
