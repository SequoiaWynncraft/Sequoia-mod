package com.seqwawa.seq.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class WarAvailabilityEditorScreenTest {
    @Test
    void parsesSupportedHourSuffixesCaseInsensitively() {
        assertEquals(60, WarAvailabilityEditorScreen.parseDurationMinutes("1h"));
        assertEquals(60, WarAvailabilityEditorScreen.parseDurationMinutes("1hr"));
        assertEquals(60, WarAvailabilityEditorScreen.parseDurationMinutes("1H"));
        assertEquals(60, WarAvailabilityEditorScreen.parseDurationMinutes("1HR"));
        assertEquals(60, WarAvailabilityEditorScreen.parseDurationMinutes("1hrs"));
        assertEquals(60, WarAvailabilityEditorScreen.parseDurationMinutes("1HRS"));
    }

    @Test
    void parsesSupportedMinuteSuffixesCaseInsensitively() {
        assertEquals(1, WarAvailabilityEditorScreen.parseDurationMinutes("1m"));
        assertEquals(1, WarAvailabilityEditorScreen.parseDurationMinutes("1min"));
        assertEquals(1, WarAvailabilityEditorScreen.parseDurationMinutes("1M"));
        assertEquals(1, WarAvailabilityEditorScreen.parseDurationMinutes("1MIN"));
        assertEquals(90, WarAvailabilityEditorScreen.parseDurationMinutes(" 90 mins "));
    }

    @Test
    void parsesCombinedHourAndMinuteDurations() {
        assertEquals(90, WarAvailabilityEditorScreen.parseDurationMinutes("1hr 30min"));
        assertEquals(90, WarAvailabilityEditorScreen.parseDurationMinutes("1HR 30MIN"));
        assertEquals(90, WarAvailabilityEditorScreen.parseDurationMinutes("1h30m"));
        assertEquals(150, WarAvailabilityEditorScreen.parseDurationMinutes("2hrs 30mins"));
    }

    @Test
    void rejectsMissingUnitsAndDurationsAboveOneDay() {
        assertEquals(1440, WarAvailabilityEditorScreen.parseDurationMinutes("24h"));
        assertEquals(1440, WarAvailabilityEditorScreen.parseDurationMinutes("1440m"));
        assertThrows(IllegalArgumentException.class, () -> WarAvailabilityEditorScreen.parseDurationMinutes("60"));
        assertThrows(IllegalArgumentException.class, () -> WarAvailabilityEditorScreen.parseDurationMinutes("25h"));
        assertThrows(IllegalArgumentException.class, () -> WarAvailabilityEditorScreen.parseDurationMinutes("1441m"));
        assertThrows(IllegalArgumentException.class, () -> WarAvailabilityEditorScreen.parseDurationMinutes("1h 60m"));
    }
}
