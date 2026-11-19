package com.seqwawa.seq.managers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.collect.ImmutableMultimap;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.ResolvableProfile;
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
    void takesThePlayerFromTheHeadsNameWhenItsProfileOnlyCarriesTheSkin() {
        // Wynncraft's raid menu draws each player's head from a skin-only profile with no name, so the
        // player is the one the head is named for, as Wynntils reads it too.
        ItemStack stack = new ItemStack(Items.PLAYER_HEAD);
        PropertyMap skin = new PropertyMap(ImmutableMultimap.of("textures", new Property("textures", "e30=")));
        stack.set(DataComponents.PROFILE, ResolvableProfile.createResolved(
                new GameProfile(UUID.fromString("10000000-0000-0000-0000-000000000009"), "", skin)));
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("PlayerOne"));
        stack.set(DataComponents.LORE, new ItemLore(List.of(
                Component.literal("Enabled Gambits:"),
                Component.literal("- Outworn Soldier's Gambit"),
                Component.literal("- Ingenuous Mage's Gambit"))));

        var parsed = RaidGambitRosterTracker.parsePlayerSlot(stack).orElseThrow();

        assertEquals("PlayerOne", parsed.username());
        assertEquals(2, parsed.gambitCount());

        // A ranked player's head starts with the rank's glyph and is coloured.
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("\uE022 ")
                .append(Component.literal("PlayerTwo").withColor(0x55FFFF)));
        assertEquals("PlayerTwo", RaidGambitRosterTracker.parsePlayerSlot(stack).orElseThrow().username());
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

}
