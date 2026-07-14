package com.seqwawa.seq.utils.rendering;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class UiRenderQueueTest {
    @Test
    void keepsOnlyWorkOwnedByTheCurrentScreen() {
        UiRenderQueue<String> queue = new UiRenderQueue<>();
        Object currentScreen = new Object();

        queue.submitScreen(new Object(), "stale");
        queue.submitScreen(currentScreen, "current");

        assertEquals(List.of("current"), queue.drain(currentScreen));
    }

    @Test
    void rendersHudWorkOnlyWhenNoScreenIsOpen() {
        UiRenderQueue<String> queue = new UiRenderQueue<>();
        queue.submitHud("hidden");

        assertEquals(List.of(), queue.drain(new Object()));

        queue.submitHud("visible");
        assertEquals(List.of("visible"), queue.drain(null));
    }

    @Test
    void resourceWorkAlwaysRunsAndPreservesQueueOrder() {
        UiRenderQueue<String> queue = new UiRenderQueue<>();
        Object currentScreen = new Object();

        queue.submitResource("first");
        queue.submitScreen(currentScreen, "second");
        queue.submitResource("third");

        assertEquals(List.of("first", "second", "third"), queue.drain(currentScreen));
        assertEquals(List.of(), queue.drain(currentScreen));
    }
}
