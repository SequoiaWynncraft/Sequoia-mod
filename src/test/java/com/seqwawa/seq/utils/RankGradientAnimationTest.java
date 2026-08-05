package com.seqwawa.seq.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.seqwawa.seq.accessors.NotificationAccessor;
import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.config.Setting;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import org.junit.jupiter.api.Test;

class RankGradientAnimationTest {

    private static final ColorRamp GRADIENT = ColorRamp.of(List.of(0x000000, 0xFFFFFF));

    @Test
    void scrollsAGradientStopAlongItsRamp() {
        TextColor first = RankGradientAnimation.colorAt(GRADIENT, 0d);

        withAnimation(true, () -> {
            assertEquals(0x000000, animated(first, 0d), "at rest the pill looks untouched");
            assertEquals(0xFFFFFF, animated(first, 0.5d), "half a turn later the far stop has arrived");
            assertEquals(0x000000, animated(first, 1d), "and a full turn is where it started");
        });
    }

    @Test
    void movesEveryStopOfAPillTogether() {
        // Otherwise the gradient would stretch and squash rather than travel.
        TextColor start = RankGradientAnimation.colorAt(GRADIENT, 0d);
        TextColor end = RankGradientAnimation.colorAt(GRADIENT, 1d);

        withAnimation(true, () -> assertEquals(
                animated(start, 0d), animated(end, 0.5d), "the far stop reaches what the near one showed"));
    }

    @Test
    void holdsStillWhileTheSettingIsOff() {
        TextColor stop = RankGradientAnimation.colorAt(GRADIENT, 0d);

        withAnimation(false, () -> assertSame(stop, RankGradientAnimation.animate(stop, 0.5d)));
    }

    @Test
    void controlsBadgeAndUsernameAnimationIndependently() {
        TextColor badge = RankGradientAnimation.colorAt(GRADIENT, 0d, RankGradientAnimation.Target.RANK_BADGE);
        TextColor username = RankGradientAnimation.colorAt(GRADIENT, 0d, RankGradientAnimation.Target.USERNAME);

        withGradientSettings(true, false, true, () -> {
            assertSame(badge, RankGradientAnimation.animate(badge, 0.5d), "the badge stays static");
            assertEquals(0xFFFFFF, animated(username, 0.5d), "the username moves independently");
        });
    }

    @Test
    void masterGradientToggleFlattensExistingDecorationsToTheirPrimaryColour() {
        TextColor farStop = RankGradientAnimation.colorAt(GRADIENT, 1d, RankGradientAnimation.Target.USERNAME);

        withGradientSettings(false, true, true, () -> assertEquals(
                0x000000,
                animated(farStop, 0.5d),
                "a line already in chat should react without being rebuilt"));
    }

    @Test
    void leavesSolidRolesAlone() {
        // One colour scrolled is that same colour back again, so it is never remembered.
        TextColor solid = RankGradientAnimation.colorAt(ColorRamp.of(0x4CB4FA), 0d);

        withAnimation(true, () -> assertSame(solid, RankGradientAnimation.animate(solid, 0.5d)));
    }

    @Test
    void leavesEveryOtherColourInTheGameAlone() {
        // The lookup runs on every glyph drawn, so ordinary text must come straight back.
        TextColor chatColor = TextColor.fromRgb(0x55FFFF);

        withAnimation(true, () -> {
            assertSame(chatColor, RankGradientAnimation.animate(chatColor, 0.5d));
            assertNull(RankGradientAnimation.animate(null, 0.5d), "an unstyled glyph has no colour to move");
        });
    }

    @Test
    void aPillBuiltForAGradientRoleCarriesStopsThatMove() {
        // The colours have to survive from the built component through to rendering by
        // identity alone; anything that copied them by value would silently stop moving.
        List<TextColor> backgrounds =
                pillBackgroundColors(NotificationAccessor.wynnPill("AB", GRADIENT, TextColor.fromRgb(0x1F2126), null));

        withAnimation(true, () -> assertEquals(
                0xFFFFFF, animated(backgrounds.getFirst(), 0.5d), "the pill's first block has moved"));
    }

    private static int animated(TextColor color, double phase) {
        return RankGradientAnimation.animate(color, phase).getValue();
    }

    private static List<TextColor> pillBackgroundColors(Component pill) {
        return ComponentTextEditor.flatten(pill).stream()
                .filter(fragment -> fragment.text().indexOf(WynnPillGlyphs.BACKGROUND) >= 0)
                .map(fragment -> fragment.style().getColor())
                .toList();
    }

    private static void withAnimation(boolean enabled, Runnable body) {
        withGradientSettings(true, enabled, false, body);
    }

    private static void withGradientSettings(
            boolean gradients, boolean badges, boolean usernames, Runnable body) {
        Setting.BooleanSetting previousGradients = SeqClient.showRankGradientsSetting;
        Setting.BooleanSetting previous = SeqClient.animateRankGradientsSetting;
        Setting.BooleanSetting previousUsernames = SeqClient.animateUsernameGradientsSetting;
        try {
            SeqClient.showRankGradientsSetting =
                    new Setting.BooleanSetting("show_rank_gradients", "chat", gradients);
            SeqClient.animateRankGradientsSetting =
                    new Setting.BooleanSetting("animate_rank_gradients", "chat", badges);
            SeqClient.animateUsernameGradientsSetting =
                    new Setting.BooleanSetting("animate_username_gradients", "chat", usernames);
            body.run();
        } finally {
            SeqClient.showRankGradientsSetting = previousGradients;
            SeqClient.animateRankGradientsSetting = previous;
            SeqClient.animateUsernameGradientsSetting = previousUsernames;
        }
    }
}
