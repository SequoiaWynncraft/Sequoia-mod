package com.seqwawa.seq.managers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class RaidGambitRosterTrackerTest {
    @BeforeAll
    static void bootstrapRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void parsesZeroGambitsWhenTheSectionIsAbsent() {
        assertEquals(0, RaidGambitRosterTracker.parseGambitCount(List.of(
                Component.literal("❤ Max Health: 10560"),
                Component.literal("✔ Class Type: Dark Wizard"))));
    }

    @Test
    void countsOnlyGambitEntriesAfterTheHeader() {
        assertEquals(3, RaidGambitRosterTracker.parseGambitCount(List.of(
                Component.literal("Enabled Gambits:"),
                Component.literal("- Outworn Soldier's Gambit"),
                Component.literal("Your spells cost at least 20 mana"),
                Component.literal("- Ingenuous Mage's Gambit"),
                Component.literal("- Foreseen Swordsman's Gambit"),
                Component.literal("✔ Class Type: Dark Wizard"))));
    }

    @Test
    void resolvesRealUsernameFromAPlayerHeadNickname() {
        ItemStack stack = new ItemStack(Items.PLAYER_HEAD);
        Component nickname = Component.literal("Regret").withStyle(style -> style.withHoverEvent(
                new HoverEvent.ShowText(Component.literal("Regret's real name is reyzhia"))));
        stack.set(DataComponents.CUSTOM_NAME, nickname);
        stack.set(DataComponents.LORE, new ItemLore(List.of(
                Component.literal("Enabled Gambits:"),
                Component.literal("- Outworn Soldier's Gambit"))));

        var parsed = RaidGambitRosterTracker.parsePlayerSlot(stack).orElseThrow();

        assertEquals("reyzhia", parsed.username());
        assertEquals(1, parsed.gambitCount());
    }

    @Test
    void filtersLatestCountsByPartyWithoutDependingOnSlotOrder() {
        assertEquals(
                Map.of("Reporter", 1, "ThirdPlayer", 4),
                RaidGambitRosterTracker.filterForParty(
                        Map.of("ThirdPlayer", 4, "Reporter", 1),
                        List.of("Reporter", "SecondPlayer", "ThirdPlayer")));
    }

    @Test
    void ignoresPlaceholderItems() {
        assertTrue(RaidGambitRosterTracker.parsePlayerSlot(new ItemStack(Items.SNOW)).isEmpty());
    }

    @Test
    void formatsChangedGambitCountsAsOneCompactChatLine() {
        assertEquals(
                "FirstPlayer=0, Reporter=3, ThirdPlayer=1",
                RaidGambitRosterTracker.formatCounts(
                        Map.of("ThirdPlayer", 1, "Reporter", 3, "FirstPlayer", 0)));
    }
}
