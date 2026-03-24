package org.sequoia.seq.utils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import net.minecraft.client.input.KeyEvent;

public final class TextInputHelper {
    private static final String[] CHARACTER_METHOD_NAMES = {"character", "chr", "typedChar", "codePoint"};

    private TextInputHelper() {}

    public static Character getTypedCharacter(KeyEvent keyEvent) {
        if (keyEvent == null) {
            return null;
        }

        for (String methodName : CHARACTER_METHOD_NAMES) {
            Character character = invokeCharacterMethod(keyEvent, methodName);
            if (character != null) {
                return character;
            }
        }
        return null;
    }

    public static boolean isPrintableCharacter(char character) {
        return !Character.isISOControl(character);
    }

    private static Character invokeCharacterMethod(KeyEvent keyEvent, String methodName) {
        try {
            Method method = keyEvent.getClass().getMethod(methodName);
            Object value = method.invoke(keyEvent);
            if (value instanceof Character character) {
                return character;
            }
            if (value instanceof Number number) {
                int codePoint = number.intValue();
                if (!Character.isValidCodePoint(codePoint)) {
                    return null;
                }
                return codePoint <= Character.MAX_VALUE ? (char) codePoint : null;
            }
            if (value instanceof String string && string.length() == 1) {
                return string.charAt(0);
            }
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException ignored) {
        }
        return null;
    }
}
