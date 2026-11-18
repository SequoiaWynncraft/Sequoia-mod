package com.seqwawa.seq.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class TimedBannerTest {
    private long now = 0;
    private final TimedBanner banner = new TimedBanner(() -> now);

    @Test
    void showsItsMessageUntilItsTimeIsUp() {
        now = 1_000;
        banner.show("Copied 120, 64, -300", 2_000);

        now = 2_999;
        assertEquals("Copied 120, 64, -300", banner.message());
        assertNotNull(banner.message());
        now = 3_000;
        assertNull(banner.message());
        assertNull(banner.message());
    }

    @Test
    void showsNothingBeforeItIsGivenAMessageEvenWhileTheClockIsNearZero() {
        assertNull(banner.message());
    }

    @Test
    void aNewMessageTakesThePlaceOfTheOldAndStartsItsOwnTime() {
        banner.show("Invited Salted", 3_500);
        now = 3_000;
        banner.show("Salted is already in a party", 3_500);

        now = 6_000;
        assertEquals("Salted is already in a party", banner.message());
        now = 6_500;
        assertNull(banner.message());
    }

    @Test
    void aBlankMessageLeavesTheBannerAsItWas() {
        banner.show("Invited Salted", 3_500);
        banner.show(" ", 3_500);
        banner.show(null, 3_500);

        assertEquals("Invited Salted", banner.message());
    }
}
