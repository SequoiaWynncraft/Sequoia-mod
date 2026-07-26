package com.seqwawa.seq.managers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

class GazDeathMessageEffectTest {
    @Test
    void titleAndSubtitleNameTargetIgnInDarkRed() {
        Component title = GazDeathMessageEffect.title();
        Component subtitle = GazDeathMessageEffect.subtitleFor("Cela41");

        assertEquals("Gaz has issued a death message", title.getString());
        assertEquals("to Cela41", subtitle.getString());
        assertEquals(ChatFormatting.DARK_RED.getColor(), title.getStyle().getColor().getValue());
        assertEquals(ChatFormatting.DARK_RED.getColor(), subtitle.getStyle().getColor().getValue());
        assertTrue(title.getStyle().isBold());
        assertTrue(subtitle.getStyle().isBold());
    }
}
