package com.seqwawa.seq.scroll;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class CraftedScrollRangeVisualiserClientTest {
    private static final FontDescription SCROLL_FONT =
            new FontDescription.Resource(Identifier.withDefaultNamespace("tooltip/emblem/sprite"));

    @BeforeAll
    static void bootstrapRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void detectsCraftedScrollGlyph() {
        ItemStack stack = new ItemStack(Items.PAPER);
        stack.set(
                DataComponents.LORE,
                new ItemLore(List.of(Component.literal("\uE032").withStyle(Style.EMPTY.withFont(SCROLL_FONT)))));

        assertTrue(CraftedScrollRangeVisualiserClient.isCraftedScroll(stack));
    }

    @Test
    void ignoresScrollNameAndGlyphInWrongFont() {
        ItemStack namedScroll = new ItemStack(Items.PAPER);
        namedScroll.set(DataComponents.CUSTOM_NAME, Component.literal("Detlas Teleportation Scroll"));
        assertFalse(CraftedScrollRangeVisualiserClient.isCraftedScroll(namedScroll));

        ItemStack wrongFont = new ItemStack(Items.PAPER);
        wrongFont.set(DataComponents.LORE, new ItemLore(List.of(Component.literal("\uE032"))));
        assertFalse(CraftedScrollRangeVisualiserClient.isCraftedScroll(wrongFont));
    }
}
