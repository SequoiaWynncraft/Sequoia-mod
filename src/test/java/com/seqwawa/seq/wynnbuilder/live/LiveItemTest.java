package com.seqwawa.seq.wynnbuilder.live;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seqwawa.seq.wynnbuilder.data.EquipmentSlot;
import com.seqwawa.seq.wynnbuilder.data.WynnItem;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LiveItemTest {

    @Test
    @DisplayName("a stat that hurts is judged by what it means, not by its sign")
    void harmfulRollsRespectInvertedStats() {
        LiveItem item = item(
                new LiveItem.Roll("sdPct", "Spell Damage %", -12, -12, 100),
                new LiveItem.Roll("mdPct", "Melee Damage %", 30, 40, 60),
                // A spell cost reduction is stored negative, so a positive one is the drawback.
                new LiveItem.Roll("spRaw1", "Spell 1 Cost", 2, -1, 10),
                new LiveItem.Roll("spRaw2", "Spell 2 Cost", -3, -4, 80));

        List<String> harmful = item.harmfulRolls().stream().map(LiveItem.Roll::key).toList();
        assertEquals(List.of("sdPct", "spRaw1"), harmful);
    }

    @Test
    @DisplayName("a stat with no room left is not called weak")
    void weakestRollsSkipMaxedStats() {
        LiveItem item = item(
                new LiveItem.Roll("sdPct", "Spell Damage %", 40, 40, 100),
                new LiveItem.Roll("mdPct", "Melee Damage %", 10, 50, 20),
                new LiveItem.Roll("hprRaw", "Health Regen", 30, 60, 55));

        List<LiveItem.Roll> weakest = item.weakestRolls(3);
        assertEquals(2, weakest.size());
        assertEquals("mdPct", weakest.get(0).key());
        assertEquals("hprRaw", weakest.get(1).key());
        assertFalse(weakest.get(0).percentage() > weakest.get(1).percentage());
    }

    @Test
    @DisplayName("the ceiling is a separate item, so the two can be measured against each other")
    void bestEquipmentUsesTheCeiling() {
        WynnItem actual = wynnItem(Map.of("sdPct", 10));
        WynnItem best = wynnItem(Map.of("sdPct", 40));
        LiveItem item = new LiveItem(
                EquipmentSlot.RING1, "Ring", "Rare", false, actual, best, 25f, List.of(), List.of());

        assertTrue(item.toEquipment() instanceof com.seqwawa.seq.wynnbuilder.data.BuildEquipment.Live);
        assertEquals(10, ((com.seqwawa.seq.wynnbuilder.data.BuildEquipment.Live) item.toEquipment())
                .item().identifications().get("sdPct"));
        assertEquals(40, ((com.seqwawa.seq.wynnbuilder.data.BuildEquipment.Live) item.toBestEquipment())
                .item().identifications().get("sdPct"));
    }

    private static LiveItem item(LiveItem.Roll... rolls) {
        WynnItem stub = wynnItem(Map.of());
        return new LiveItem(
                EquipmentSlot.RING1, "Ring", "Rare", false, stub, stub, 50f, List.of(rolls), List.of());
    }

    static WynnItem wynnItem(Map<String, Integer> identifications) {
        return new WynnItem(
                1, "Ring", "Ring", "accessory", "ring", WynnItem.Tier.RARE, 100, null, "NORMAL", 0,
                Map.of(), Map.of(), Map.of(), 0, identifications, List.of(), null, true, null, null);
    }
}
