package com.seqwawa.seq.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.client.input.KeyEvent;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

class TextInputHelperTest {
    @Test
    void resolvesLettersFromKeyCodeWhenNoTypedCharacterIsPresent() {
        assertEquals('a', TextInputHelper.getTypedCharacter(new KeyEvent(GLFW.GLFW_KEY_A, 0, 0)));
        assertEquals(
                'A',
                TextInputHelper.getTypedCharacter(new KeyEvent(GLFW.GLFW_KEY_A, 0, GLFW.GLFW_MOD_SHIFT)));
    }

    @Test
    void resolvesNumbersAndCommonSearchCharactersFromKeyCode() {
        assertEquals('3', TextInputHelper.getTypedCharacter(new KeyEvent(GLFW.GLFW_KEY_3, 0, 0)));
        assertEquals(' ', TextInputHelper.getTypedCharacter(new KeyEvent(GLFW.GLFW_KEY_SPACE, 0, 0)));
        assertEquals('-', TextInputHelper.getTypedCharacter(new KeyEvent(GLFW.GLFW_KEY_MINUS, 0, 0)));
    }
}
