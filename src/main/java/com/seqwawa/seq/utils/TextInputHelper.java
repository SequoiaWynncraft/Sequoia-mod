package com.seqwawa.seq.utils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.glfw.GLFW;

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
        return keyCodeCharacter(keyEvent);
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

    private static Character keyCodeCharacter(KeyEvent keyEvent) {
        int keyCode = keyEvent.key();
        boolean shifted = (keyEvent.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0;

        if (keyCode >= GLFW.GLFW_KEY_A && keyCode <= GLFW.GLFW_KEY_Z) {
            char base = shifted ? 'A' : 'a';
            return (char) (base + (keyCode - GLFW.GLFW_KEY_A));
        }
        if (keyCode >= GLFW.GLFW_KEY_0 && keyCode <= GLFW.GLFW_KEY_9) {
            return (char) ('0' + (keyCode - GLFW.GLFW_KEY_0));
        }
        if (keyCode >= GLFW.GLFW_KEY_KP_0 && keyCode <= GLFW.GLFW_KEY_KP_9) {
            return (char) ('0' + (keyCode - GLFW.GLFW_KEY_KP_0));
        }

        return switch (keyCode) {
            case GLFW.GLFW_KEY_SPACE -> ' ';
            case GLFW.GLFW_KEY_MINUS, GLFW.GLFW_KEY_KP_SUBTRACT -> '-';
            case GLFW.GLFW_KEY_APOSTROPHE -> '\'';
            case GLFW.GLFW_KEY_PERIOD, GLFW.GLFW_KEY_KP_DECIMAL -> '.';
            case GLFW.GLFW_KEY_COMMA -> ',';
            case GLFW.GLFW_KEY_SLASH -> '/';
            case GLFW.GLFW_KEY_SEMICOLON -> ';';
            case GLFW.GLFW_KEY_EQUAL -> '=';
            default -> null;
        };
    }
}
