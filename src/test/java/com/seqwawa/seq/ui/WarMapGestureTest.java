package com.seqwawa.seq.ui;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class WarMapGestureTest {
    @Test
    void queueDoubleClickSurvivesReleaseButIsCancelledByAnyDragEvent() {
        var gesture = new WarMapGesture();
        assertFalse(gesture.press(7L, "Territory", 10, 20, 100));
        assertTrue(gesture.release());
        assertTrue(gesture.press(7L, "Territory", 10, 20, 200));
        assertFalse(gesture.dragging());
        assertFalse(gesture.press(7L, "Territory", 10, 20, 300));
        assertTrue(gesture.drag(11, 20));
        assertTrue(gesture.release(), "small movement still selects the territory");
        assertFalse(gesture.press(7L, "Territory", 10, 20, 400), "drag event clears queue history");
    }

    @Test
    void panDoesNotSelectAndTabResetCancelsPointerAndQueueHistory() {
        var gesture = new WarMapGesture();
        gesture.press(7L, "Territory", 10, 20, 100);
        gesture.drag(14, 20);
        assertFalse(gesture.release());
        gesture.press(7L, "Territory", 10, 20, 200);
        gesture.reset();
        assertFalse(gesture.dragging());
        assertFalse(gesture.release());
        assertFalse(gesture.press(7L, "Territory", 10, 20, 300));
    }
}
