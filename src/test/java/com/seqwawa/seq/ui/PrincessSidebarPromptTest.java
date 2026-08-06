package com.seqwawa.seq.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Random;
import org.junit.jupiter.api.Test;

class PrincessSidebarPromptTest {

    @Test
    void cyclesFromHiddenToVisibleAndBackWithSlidingTransitions() {
        PrincessSidebarPrompt prompt = new PrincessSidebarPrompt(new Random(42), 1_000);

        assertEquals(PrincessSidebarPrompt.Phase.HIDDEN, prompt.phase());
        assertEquals(0f, prompt.slideProgress(1_000));

        long enterAt = prompt.phaseEndsAtMs();
        assertEquals(0f, prompt.slideProgress(enterAt));
        assertEquals(PrincessSidebarPrompt.Phase.ENTERING, prompt.phase());
        assertEquals(0.75f, prompt.slideProgress(enterAt + PrincessSidebarPrompt.SLIDE_DURATION_MS / 2), 0.001f);

        long visibleAt = enterAt + PrincessSidebarPrompt.SLIDE_DURATION_MS;
        assertEquals(1f, prompt.slideProgress(visibleAt));
        assertEquals(PrincessSidebarPrompt.Phase.VISIBLE, prompt.phase());

        long exitAt = prompt.phaseEndsAtMs();
        assertEquals(1f, prompt.slideProgress(exitAt));
        assertEquals(PrincessSidebarPrompt.Phase.EXITING, prompt.phase());
        assertEquals(0.25f, prompt.slideProgress(exitAt + PrincessSidebarPrompt.SLIDE_DURATION_MS / 2), 0.001f);

        long hiddenAt = exitAt + PrincessSidebarPrompt.SLIDE_DURATION_MS;
        assertEquals(0f, prompt.slideProgress(hiddenAt));
        assertEquals(PrincessSidebarPrompt.Phase.HIDDEN, prompt.phase());
    }
}
