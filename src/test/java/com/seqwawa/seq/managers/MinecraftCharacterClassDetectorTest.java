package com.seqwawa.seq.managers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seqwawa.seq.model.WynnClassType;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.Util;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class MinecraftCharacterClassDetectorTest {

    @BeforeAll
    static void bootstrapRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @AfterEach
    void resetSingleton() {
        MinecraftCharacterClassDetector.getInstance().reset();
    }

    @Test
    void parsesExactCharacterInfoAndSelectionCardClassLines() {
        assertEquals(
                WynnClassType.MAGE,
                MinecraftCharacterClassDetector.parseClass(stackWithLore("§7Class: §fDark Wizard")));
        assertEquals(
                WynnClassType.SHAMAN,
                MinecraftCharacterClassDetector.parseClass(stackWithLore("§7- Class: §fSkyseer")));
        assertEquals(
                WynnClassType.ASSASSIN,
                MinecraftCharacterClassDetector.parseClass(stackWithLore("Class: Ninja")));
    }

    @Test
    void rejectsRequirementsTypeLabelsAndIncidentalClassText() {
        assertNull(MinecraftCharacterClassDetector.parseClass(stackWithLore(
                "Class Req: 103",
                "Class Type: Archer",
                "Recommended Class: Mage",
                "- Class Type: Shaman")));
    }

    @Test
    void observesOnlyAnExplicitCharacterSelectionClass() {
        MinecraftCharacterClassDetector detector = MinecraftCharacterClassDetector.getInstance();
        String selectionTitle = new String(Character.toChars(0xCFFD5)) + '\uE01F';

        detector.observeCharacterSelection(selectionTitle, stackWithLore("Class Type: Warrior"));
        assertNull(detector.currentClass());

        detector.observeCharacterSelection("Character Info", stackWithLore("- Class: Mage"));
        assertNull(detector.currentClass());

        detector.observeCharacterSelection(selectionTitle, stackWithLore("- Class: Knight"));
        assertEquals(WynnClassType.WARRIOR, detector.currentClass());
    }

    @Test
    void bindsASelectionClassToTheFirstIdAndClearsItWhenTheIdChanges() {
        MinecraftCharacterClassDetector detector = MinecraftCharacterClassDetector.getInstance();
        String selectionTitle = new String(Character.toChars(0xCFFD5)) + '\uE01F';
        detector.observeCharacterSelection(selectionTitle, stackWithLore("- Class: Hunter"));

        detector.observeCharacterId("a1b2c3d4", 1_000L);
        assertEquals(WynnClassType.ARCHER, detector.currentClass());

        detector.observeCharacterId("e5f6a7b8", 2_000L);
        assertNull(detector.currentClass());
    }

    @Test
    void recognizesOnlyAnExactLowercaseEightCharacterIdLoreLine() {
        assertEquals("a1b2c3d4", MinecraftCharacterClassDetector.characterId(stackWithLore("§8a1b2c3d4")));
        assertNull(MinecraftCharacterClassDetector.characterId(stackWithLore("A1b2c3d4")));
        assertNull(MinecraftCharacterClassDetector.characterId(stackWithLore("a1b2c3d")));
        assertNull(MinecraftCharacterClassDetector.characterId(stackWithLore("id: a1b2c3d4")));
    }

    @Test
    void matchesOnlyTheExactPrivateUseCharacterInfoTitle() {
        String exactTitle = new String(Character.toChars(0xCFFDC)) + '\uE003';

        assertTrue(MinecraftCharacterClassDetector.isCharacterInfoTitle(Component.literal(exactTitle)));
        assertFalse(MinecraftCharacterClassDetector.isCharacterInfoTitle(Component.literal(exactTitle + " ")));
        assertFalse(MinecraftCharacterClassDetector.isCharacterInfoTitle(Component.literal("Character Info")));
        assertFalse(MinecraftCharacterClassDetector.isCharacterInfoTitle(null));
    }

    @Test
    void pendingCharacterInfoResponseIsConsumedOnceAndIgnoresAStaleCharacter() {
        MinecraftCharacterClassDetector detector = MinecraftCharacterClassDetector.getInstance();
        long now = Util.getMillis();
        String exactTitle = new String(Character.toChars(0xCFFDC)) + '\uE003';

        detector.observeCharacterId("a1b2c3d4", now);
        detector.beginQuery(now);
        assertTrue(detector.onCharacterInfoOpened(17, Component.literal(exactTitle)));

        detector.observeCharacterId("e5f6a7b8", now + 1L);
        assertTrue(detector.onCharacterInfoContents(17, characterInfoItems("Class: Mage")));
        assertNull(detector.currentClass());
        assertFalse(detector.onCharacterInfoContents(17, characterInfoItems("Class: Mage")));
        assertTrue(detector.onCharacterInfoClosed(17));
        assertFalse(detector.onCharacterInfoClosed(17));
    }

    @Test
    void matchingCharacterInfoResponseStoresTheExactClass() {
        MinecraftCharacterClassDetector detector = MinecraftCharacterClassDetector.getInstance();
        long now = Util.getMillis();
        String exactTitle = new String(Character.toChars(0xCFFDC)) + '\uE003';

        detector.observeCharacterId("a1b2c3d4", now);
        detector.beginQuery(now);
        assertTrue(detector.onCharacterInfoOpened(23, Component.literal(exactTitle)));
        assertTrue(detector.onCharacterInfoContents(23, characterInfoItems("Class: Dark Wizard")));

        assertEquals(WynnClassType.MAGE, detector.currentClass());
    }

    @Test
    void characterInfoQueryUsesTheEstablishedInventorySlotClickPacket() {
        ArrayList<ItemStack> menuItems = new ArrayList<>();
        for (int index = 0; index < 44; index++) {
            menuItems.add(ItemStack.EMPTY);
        }
        menuItems.set(43, stackWithLore("a1b2c3d4"));

        ServerboundContainerClickPacket packet = MinecraftCharacterClassDetector.createCharacterInfoClickPacket(
                menuItems, component -> component.hashCode());

        assertEquals(0, packet.containerId());
        assertEquals(0, packet.stateId());
        assertEquals(43, packet.slotNum());
        assertEquals(0, packet.buttonNum());
        assertEquals(ClickType.PICKUP, packet.clickType());
        assertEquals(1, packet.changedSlots().size());
        assertTrue(packet.changedSlots().containsKey(43));
    }

    private static List<ItemStack> characterInfoItems(String classLore) {
        ArrayList<ItemStack> items = new ArrayList<>();
        for (int index = 0; index < 8; index++) {
            items.add(ItemStack.EMPTY);
        }
        items.set(7, stackWithLore(classLore));
        return items;
    }

    private static ItemStack stackWithLore(String... lines) {
        ItemStack stack = new ItemStack(Items.COMPASS);
        stack.set(
                DataComponents.LORE,
                new ItemLore(List.of(lines).stream().map(line -> (Component) Component.literal(line)).toList()));
        return stack;
    }
}
