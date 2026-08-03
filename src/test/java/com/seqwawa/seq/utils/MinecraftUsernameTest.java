package com.seqwawa.seq.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class MinecraftUsernameTest {
    @ParameterizedTest
    @MethodSource("rawUsernameCases")
    void validatesRawUsernamesWithoutTrimming(String value, boolean expected) {
        assertEquals(expected, MinecraftUsername.isValid(value));
    }

    private static Stream<Arguments> rawUsernameCases() {
        return Stream.of(
                Arguments.of(null, false),
                Arguments.of("", false),
                Arguments.of("ab", false),
                Arguments.of("abc", true),
                Arguments.of("Player_123", true),
                Arguments.of("abcdefghijklmnop", true),
                Arguments.of("abcdefghijklmnopq", false),
                Arguments.of(" Player_123 ", false),
                Arguments.of("Player-123", false),
                Arguments.of("Pláyer", false),
                Arguments.of("玩家名", false));
    }

    @ParameterizedTest
    @MethodSource("normalizedUsernameCases")
    void trimsThenValidatesWhilePreservingCase(String value, String expected) {
        assertEquals(expected, MinecraftUsername.normalize(value));
    }

    private static Stream<Arguments> normalizedUsernameCases() {
        return Stream.of(
                Arguments.of(null, null),
                Arguments.of("", null),
                Arguments.of("  Player_123\t", "Player_123"),
                Arguments.of(" ab ", null),
                Arguments.of(" Player Name ", null),
                Arguments.of("\u2003Player_123\u2003", null),
                Arguments.of("abcdefghijklmnop", "abcdefghijklmnop"));
    }
}
