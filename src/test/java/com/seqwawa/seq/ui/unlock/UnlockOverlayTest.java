package com.seqwawa.seq.ui.unlock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seqwawa.seq.ui.unlock.UnlockPopup.Kind;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class UnlockOverlayTest {

    private final AtomicLong now = new AtomicLong(10_000);
    private final List<String> started = new ArrayList<>();
    private final AtomicBoolean fighting = new AtomicBoolean();
    private final UnlockOverlay overlay = new UnlockOverlay(now::get, popup -> started.add(popup.title()), fighting::get);

    private static UnlockPopup popup(Kind kind, String title) {
        return new UnlockPopup(kind, null, title, null, 100, 100, "gold", null);
    }

    private List<String> showing() {
        return overlay.advance(now.get()).stream().map(playing -> playing.popup().title()).toList();
    }

    @Test
    void cardsAndCeremoniesTakeTurnsInTheMiddle() {
        overlay.enqueue(popup(Kind.CARD, "daily done"));
        overlay.enqueue(popup(Kind.CEREMONY, "diamond"));

        assertEquals(List.of("daily done"), showing());
        now.addAndGet(UnlockAnimation.CARD_MS);
        assertEquals(List.of("diamond"), showing());
        now.addAndGet(UnlockAnimation.CEREMONY_MS);
        assertEquals(List.of(), showing());
        assertEquals(List.of("daily done", "diamond"), started);
    }

    @Test
    void toastsStackBesideTheMiddleThreeAtATime() {
        for (int i = 1; i <= 4; i++) overlay.enqueue(popup(Kind.TOAST, "toast " + i));
        overlay.enqueue(popup(Kind.CARD, "daily done"));

        assertEquals(List.of("toast 1", "toast 2", "toast 3", "daily done"), showing());
        now.addAndGet(UnlockAnimation.TOAST_MS);
        assertEquals(List.of("toast 4", "daily done"), showing());
    }

    @Test
    void aPopupsClockStartsWhenItIsFirstShown() {
        overlay.enqueue(popup(Kind.CARD, "waited behind a menu"));
        now.addAndGet(60_000);

        assertEquals(List.of("waited behind a menu"), showing());
        assertEquals(List.of("waited behind a menu"), started);
    }

    @Test
    void aFloodKeepsOnlyTheNewestFewWaiting() {
        for (int i = 1; i <= UnlockOverlay.MAX_WAITING + 3; i++) overlay.enqueue(popup(Kind.CARD, "card " + i));

        assertEquals(List.of("card 4"), showing());
    }

    @Test
    void isIdleOnlyWhenNothingShowsOrWaits() {
        assertTrue(overlay.isIdle());
        overlay.enqueue(popup(Kind.TOAST, "toast"));
        assertFalse(overlay.isIdle());
        showing();
        now.addAndGet(UnlockAnimation.TOAST_MS);
        showing();
        assertTrue(overlay.isIdle());
    }

    @Test
    void theCeremonyFitsShortScreensWithSmallerArt() {
        // 360 UI pixels tall (1280x720 at GUI scale 2): 3-pixel art, all of the group on screen.
        float tall = UnlockOverlay.ceremonyCentre(360, 3);
        assertTrue(tall - 17 * 3 >= 0 && tall + UnlockOverlay.groupBelow() * 3 <= 360);
        // 205 tall: 2-pixel art, still all on screen.
        float short_ = UnlockOverlay.ceremonyCentre(205, 2);
        assertTrue(short_ - 17 * 2 >= 0 && short_ + UnlockOverlay.groupBelow() * 2 <= 205);
        assertEquals(List.of(0, 1, 2, 3, 2), List.of(0, 1, 2, 4, 3).stream()
                .map(scale -> UnlockOverlay.medalScaleFor(scale, 2)).toList());
    }

    @Test
    void ceremoniesWaitOutRaidsAndWarsWhileCardsAndToastsStillPlay() {
        fighting.set(true);
        overlay.enqueue(popup(Kind.CEREMONY, "diamond"));
        overlay.enqueue(popup(Kind.CARD, "daily done"));
        overlay.enqueue(popup(Kind.TOAST, "+330"));

        assertEquals(List.of("+330", "daily done"), showing());
        now.addAndGet(UnlockAnimation.CARD_MS);
        assertEquals(List.of(), showing());
        now.addAndGet(60_000);
        assertEquals(List.of(), showing());

        fighting.set(false);
        assertEquals(List.of("diamond"), showing());
    }

    @Test
    void aCeremonyCutShortByAFightPlaysAgainFromTheStartAfterwards() {
        overlay.enqueue(popup(Kind.CEREMONY, "diamond"));
        assertEquals(List.of("diamond"), showing());
        now.addAndGet(2_000);

        fighting.set(true);
        assertEquals(List.of(), showing());
        now.addAndGet(30_000);
        fighting.set(false);

        assertEquals(List.of("diamond"), showing());
        assertEquals(List.of("diamond", "diamond"), started);
        now.addAndGet(UnlockAnimation.CEREMONY_MS - 1);
        assertEquals(List.of("diamond"), showing());
    }

    @Test
    void toastsMoveUnderTheCardWhenTheScreenIsTooNarrowForBoth() {
        assertEquals(8f, UnlockOverlay.toastTop(960));
        assertEquals(120f, UnlockOverlay.toastTop(568));
    }

    @Test
    void bannerTailsHaveAVNotchInTheMiddle() {
        // 14 rows: two pixels deep at rows 6 and 7, one pixel at rows 5 and 8, none elsewhere.
        assertTrue(UnlockOverlay.inNotch(6, 14, 1));
        assertFalse(UnlockOverlay.inNotch(6, 14, 2));
        assertTrue(UnlockOverlay.inNotch(5, 14, 0));
        assertFalse(UnlockOverlay.inNotch(5, 14, 1));
        assertFalse(UnlockOverlay.inNotch(0, 14, 0));
    }

    @Test
    void aboutOneToastInThreeWearsAVineAndTheSameRewardAlwaysLooksTheSame() {
        int withVine = 0;
        for (int i = 0; i < 300; i++) {
            UnlockPopup toast = new UnlockPopup(Kind.TOAST, null, "+" + i, "War for Territory " + i, i, i, null, null);
            if (UnlockOverlay.hasVine(toast)) withVine++;
            assertEquals(UnlockOverlay.hasVine(toast), UnlockOverlay.hasVine(toast));
        }
        assertTrue(withVine > 70 && withVine < 130, "vines on " + withVine + " of 300");
    }

    @Test
    void aToastWithAVineMovesDownSoItsVineKeepsClearOfTheToastAbove() {
        UnlockPopup vined = null, plain = null;
        for (int i = 0; vined == null || plain == null; i++) {
            UnlockPopup toast = new UnlockPopup(Kind.TOAST, null, "+" + i, "War for Territory " + i, i, i, null, null);
            if (UnlockOverlay.hasVine(toast)) vined = toast;
            else plain = toast;
        }
        assertEquals(UnlockOverlay.VINE_REACH * UnlockOverlay.SMALL_PIXEL, UnlockOverlay.roomAboveToast(vined, false));
        assertEquals(0f, UnlockOverlay.roomAboveToast(vined, true), "the top toast's vine has the screen's margin");
        assertEquals(0f, UnlockOverlay.roomAboveToast(plain, false));
    }

    @Test
    void framesSitOnTheArtPixelGrid() {
        assertEquals(12f, UnlockOverlay.snap(12.9f, 3));
        assertEquals(14f, UnlockOverlay.snap(13.1f, 2));
    }
}
