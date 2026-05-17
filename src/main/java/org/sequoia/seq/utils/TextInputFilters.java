package org.sequoia.seq.utils;

public final class TextInputFilters {
    private TextInputFilters() {}

    public static boolean isMinecraftUsernameCharacter(char character) {
        return (character >= 'a' && character <= 'z')
                || (character >= 'A' && character <= 'Z')
                || (character >= '0' && character <= '9')
                || character == '_';
    }
}
