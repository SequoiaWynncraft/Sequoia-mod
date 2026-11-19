package com.seqwawa.seq.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TextFieldTest {

    @Test
    void typingAddsToTheEndAndBackspaceTakesTheLastCharacterOff() {
        TextField field = new TextField();
        field.focus();

        assertTrue(field.type("ha"));
        assertTrue(field.type("lo"));
        assertEquals("halo", field.text());
        assertTrue(field.backspace());
        assertEquals("hal", field.text());
    }

    @Test
    void nothingToTypeOrDeleteLeavesTheTextAlone() {
        TextField field = new TextField();
        field.focus();

        assertFalse(field.backspace());
        assertFalse(field.type(""));
        assertFalse(field.type(null));
        assertEquals("", field.text());
    }

    @Test
    void aFieldWithoutFocusIgnoresTypingAndBackspace() {
        TextField field = new TextField();
        field.focus();
        field.type("Notes");
        field.blur();

        assertFalse(field.type("x"));
        assertFalse(field.backspace());
        assertEquals("Notes", field.text());
    }

    @Test
    void focusComesAndGoes() {
        TextField field = new TextField();
        assertFalse(field.focused());

        field.focus();
        assertTrue(field.focused());
        field.blur();
        assertFalse(field.focused());
    }

    @Test
    void theCaretShowsForAThousandFramesThenHidesForAThousand() {
        assertTrue(TextField.caretShown(1));
        assertTrue(TextField.caretShown(999));
        assertFalse(TextField.caretShown(1_000));
        assertFalse(TextField.caretShown(1_999));
        assertTrue(TextField.caretShown(2_000));
    }

    @Test
    void theCaretSitsAPixelAfterTheTextButStaysInsideTheField() {
        UiBounds field = new UiBounds(148, 6, 140, 18);

        assertEquals(148 + 6 + 40 + 1, TextField.caretX(field, 40));
        assertEquals(field.right() - 1, TextField.caretX(field, 400));
    }
}
