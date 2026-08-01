package com.seqwawa.seq.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import net.minecraft.client.input.CharacterEvent;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

class TextInputHelperTest {
    @Test
    void usesTranslatedCharacterInsteadOfPhysicalKeyCode() {
        assertEquals("a", TextInputHelper.getTypedText(new CharacterEvent('a', 0)));
        assertEquals("q", TextInputHelper.getTypedText(new CharacterEvent('q', 0)));
        assertEquals("é", TextInputHelper.getTypedText(new CharacterEvent('é', 0)));
    }

    @Test
    void ignoresShortcutCharactersButAllowsAltGrText() {
        assertNull(TextInputHelper.getTypedText(new CharacterEvent('v', GLFW.GLFW_MOD_CONTROL)));
        assertNull(TextInputHelper.getTypedText(new CharacterEvent('v', GLFW.GLFW_MOD_SUPER)));
        assertEquals(
                "@",
                TextInputHelper.getTypedText(
                        new CharacterEvent('@', GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_ALT)));
    }
}
