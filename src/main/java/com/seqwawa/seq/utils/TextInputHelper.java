package com.seqwawa.seq.utils;

import net.minecraft.client.input.CharacterEvent;
import org.lwjgl.glfw.GLFW;

public final class TextInputHelper {
    private TextInputHelper() {}

    public static String getTypedText(CharacterEvent characterEvent) {
        if (characterEvent == null
                || isShortcutModified(characterEvent)
                || !characterEvent.isAllowedChatCharacter()) {
            return null;
        }
        String typedText = characterEvent.codepointAsString();
        return typedText.isEmpty() ? null : typedText;
    }

    private static boolean isShortcutModified(CharacterEvent characterEvent) {
        int modifiers = characterEvent.modifiers();
        boolean superDown = (modifiers & GLFW.GLFW_MOD_SUPER) != 0;
        boolean controlDown = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
        boolean altDown = (modifiers & GLFW.GLFW_MOD_ALT) != 0;
        return superDown || controlDown && !altDown;
    }
}
