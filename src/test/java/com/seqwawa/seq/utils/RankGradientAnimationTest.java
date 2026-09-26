package com.seqwawa.seq.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seqwawa.seq.accessors.NotificationAccessor;
import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.config.Setting;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import org.junit.jupiter.api.Test;

class RankGradientAnimationTest {

    private static final ColorRamp GRADIENT = ColorRamp.of(List.of(0x000000, 0xFFFFFF));

    /** Long enough that a lone test glyph at {@code position} sits at that fraction of it. */
    private static final float AXIS_LENGTH = 100f;

    @Test
    void scrollsAGradientAlongItsRamp() {
        TextColor first = colorAt(GRADIENT, 0d);

        withAnimation(true, () -> {
            assertEquals(0x000000, shaded(first, 0d), "at rest the pill looks untouched");
            assertEquals(0xFFFFFF, shaded(first, 0.5d), "half a turn later the far stop has arrived");
            assertEquals(0x000000, shaded(first, 1d), "and a full turn is where it started");
        });
    }

    @Test
    void avoidsWynncraftShaderMarkersInStaticAndAnimatedSamples() {
        ColorRamp ramp = ColorRamp.of(List.of(0x25FF00, 0x72D400));
        TextColor staticCrossing = colorAt(ramp, 8d / 17d);
        TextColor animatedCrossing = colorAt(ramp, 0d);

        assertEquals(0x49EC00, staticCrossing.getValue());
        withAnimation(true, () -> assertEquals(0x49EC00, shaded(animatedCrossing, 4d / 17d)));
    }

    @Test
    void avoidsShaderMarkersWhenSettingsSelectAlternatePalettePaths() {
        ColorRamp dangerousGradient = ColorRamp.of(List.of(0x40EB00, 0x72D400));
        TextColor flattened = colorAt(dangerousGradient, 1d);
        TextColor switchedSolid = colorAt(
                ColorRamp.of(0x123456), ColorRamp.of(0x40EB00), 0d, RankGradientAnimation.Target.USERNAME, null);
        TextColor switchedGradient = colorAt(
                ColorRamp.of(0x123456), dangerousGradient, 0d, RankGradientAnimation.Target.USERNAME, null);

        withGradientSettings(
                false,
                true,
                false,
                false,
                () -> assertEquals(0x40EC00, resolved(flattened), "flattened gradient"));
        withGradientSettings(true, true, false, false, () -> withPerUserColors(false, () -> {
            assertEquals(0x40EC00, resolved(switchedSolid), "switched solid palette");
            assertEquals(0x40EC00, resolved(switchedGradient), "switched gradient palette");
        }));
    }

    @Test
    void movesEveryStopOfAPillTogether() {
        // Otherwise the gradient would stretch and squash rather than travel.
        RankGradientAnimation.Axis axis = axis(GRADIENT, RankGradientAnimation.Target.RANK_BADGE);
        TextColor start = axis.colorAt(0f, 0f, null);
        TextColor end = axis.colorAt(AXIS_LENGTH, 0f, null);

        withAnimation(true, () -> assertEquals(
                shaded(start, 0d), shaded(end, 0.5d), "the far stop reaches what the near one showed"));
    }

    @Test
    void movesAtTheSameSpeedInPixelsWhateverItIsPaintedOn() {
        // A pill and the longer name after it used to take the same time to come round,
        // which had the name's gradient race past the pill's.
        for (double length : new double[] {40d, 80d, 137d}) {
            double turn = 2 * length;
            assertEquals(
                    RankGradientAnimation.PIXELS_PER_SECOND,
                    RankGradientAnimation.phaseAt(GRADIENT, length, RankGradientAnimation.PIXELS_PER_SECOND) * turn,
                    1e-9,
                    "pixels travelled along " + length);
        }
    }

    @Test
    void travelsFasterOrSlowerWithTheSpeedSetting() {
        for (int percent : new int[] {100, 250, 50, 10, 500}) {
            double travelled = withAnimationSpeed(percent, () -> {
                double before = RankGradientAnimation.travelled(later(60_000L));
                return RankGradientAnimation.travelled(later(1000L)) - before;
            });
            assertEquals(
                    RankGradientAnimation.PIXELS_PER_SECOND * percent / 100d,
                    travelled,
                    1e-9,
                    "pixels travelled in a second at " + percent + "%");
        }
    }

    @Test
    void changingTheSpeedDoesNotMakeTheGradientJump() {
        long now = later(60_000L);
        double atOldSpeed = withAnimationSpeed(100, () -> RankGradientAnimation.travelled(now));
        double atNewSpeed = withAnimationSpeed(400, () -> RankGradientAnimation.travelled(now));
        double aSecondLater = withAnimationSpeed(400, () -> RankGradientAnimation.travelled(later(1000L)));

        assertEquals(atOldSpeed, atNewSpeed, 1e-9, "the gradient stays where it was");
        assertEquals(
                4 * RankGradientAnimation.PIXELS_PER_SECOND, aSecondLater - atNewSpeed, 1e-9, "and then goes faster");
    }

    @Test
    void neverRunsBackwards() {
        long now = later(60_000L);
        double travelled = RankGradientAnimation.travelled(now);

        assertEquals(travelled, RankGradientAnimation.travelled(now - 5000L), 1e-9);
    }

    @Test
    void holdsStillWhileTheSettingIsOff() {
        TextColor stop = colorAt(GRADIENT, 0d);

        withAnimation(false, () -> {
            assertSame(stop, RankGradientAnimation.resolve(stop));
            assertTrue(Double.isNaN(RankGradientAnimation.shade(stop, 0.5d).phase()), "a still gradient");
            assertEquals(0x000000, shaded(stop, 0.5d));
        });
    }

    @Test
    void controlsBadgeAndUsernameAnimationIndependently() {
        TextColor badge = colorAt(GRADIENT, 0d, RankGradientAnimation.Target.RANK_BADGE);
        TextColor username = colorAt(GRADIENT, 0d, RankGradientAnimation.Target.USERNAME);

        withGradientSettings(true, true, false, true, () -> {
            assertEquals(0x000000, shaded(badge, 0.5d), "the badge stays static");
            assertEquals(0xFFFFFF, shaded(username, 0.5d), "the username moves independently");
        });
    }

    @Test
    void controlsBadgeAndUsernameGradientsIndependently() {
        TextColor badge = colorAt(GRADIENT, 1d, RankGradientAnimation.Target.RANK_BADGE);
        TextColor username = colorAt(GRADIENT, 1d, RankGradientAnimation.Target.USERNAME);

        withGradientSettings(true, false, false, false, () -> {
            assertSame(badge, RankGradientAnimation.resolve(badge), "the pill keeps its complete gradient");
            assertNotNull(RankGradientAnimation.shade(badge, 0d));
            assertEquals(0x000000, resolved(username), "the username flattens independently");
            assertNull(RankGradientAnimation.shade(username, 0d), "and is drawn flat");
        });
        withGradientSettings(false, true, false, false, () -> {
            assertEquals(0x000000, resolved(badge), "the pill flattens independently");
            assertNull(RankGradientAnimation.shade(badge, 0d));
            assertSame(username, RankGradientAnimation.resolve(username), "the username keeps its gradient");
        });
    }

    @Test
    void readsAPillAndItsNameAsOneGradientWhenBothShowTheirs() {
        RankGradientAnimation.Axis pill = RankGradientAnimation.axis(
                GRADIENT, GRADIENT, RankGradientAnimation.Target.RANK_BADGE, 40f);
        RankGradientAnimation.Axis name = RankGradientAnimation.axis(
                GRADIENT, GRADIENT, RankGradientAnimation.Target.USERNAME, 60f);
        TextColor pillStart = pill.colorAt(0f, 0f, null);
        TextColor nameStart = name.colorAt(0f, 0f, null);
        TextColor nameEnd = name.colorAt(60f, 0f, null);
        RankGradientAnimation.join(pill, name, 10f);

        withGradientSettings(true, true, false, false, () -> {
            assertEquals(0x000000, shaded(pillStart, 0d), "the pill starts the gradient");
            assertEquals(GRADIENT.sample(50d / 110d), shaded(nameStart, 0d), "the name carries on from it");
            assertEquals(0xFFFFFF, shaded(nameEnd, 0d), "and finishes it");
            assertEquals(GRADIENT.sample(50d / 110d), resolved(nameStart), "its flat colour agrees");
        });
        withGradientSettings(true, false, false, false, () -> assertEquals(
                0x000000, shaded(pillStart, 0d), "with the name flat, the pill keeps a gradient of its own"));
        withGradientSettings(true, false, false, false, () -> {
            RankGradientAnimation.Shade alone = RankGradientAnimation.shade(pillStart, 0d);
            assertEquals(0xFFFFFF, alone.rgbAt(40f), "spread over the pill alone");
        });
    }

    @Test
    void movesAJoinedGradientAsOneWhenEitherHalfIsAnimated() {
        RankGradientAnimation.Axis pill = RankGradientAnimation.axis(
                GRADIENT, GRADIENT, RankGradientAnimation.Target.RANK_BADGE, 40f);
        RankGradientAnimation.Axis name = RankGradientAnimation.axis(
                GRADIENT, GRADIENT, RankGradientAnimation.Target.USERNAME, 60f);
        TextColor pillGlyph = pill.colorAt(0f, 0f, null);
        TextColor nameGlyph = name.colorAt(0f, 0f, null);
        RankGradientAnimation.join(pill, name, 10f);

        withGradientSettings(true, true, true, false, () -> {
            assertFalse(Double.isNaN(RankGradientAnimation.shade(pillGlyph, 0.25d).phase()));
            assertFalse(Double.isNaN(RankGradientAnimation.shade(nameGlyph, 0.25d).phase()), "the name moves too");
            assertEquals(
                    RankGradientAnimation.shade(pillGlyph, 0.25d).length(),
                    RankGradientAnimation.shade(nameGlyph, 0.25d).length(),
                    "along one axis, so at one speed");
        });
    }

    @Test
    void restoresEachTargetsBaseColorImmediatelyWhenRoleColoringIsDisabled() {
        TextColor pillBase = TextColor.fromRgb(0x40EB00);
        TextColor usernameBase = TextColor.fromRgb(0xFFFFFF);
        TextColor badge = colorAt(ColorRamp.of(0x4CB4FA), 0d, RankGradientAnimation.Target.RANK_BADGE, pillBase);
        TextColor username = colorAt(GRADIENT, 1d, RankGradientAnimation.Target.USERNAME, usernameBase);

        withColorSettings(false, true, () -> {
            assertSame(pillBase, RankGradientAnimation.resolve(badge));
            assertSame(username, RankGradientAnimation.resolve(username));
        });
        withColorSettings(true, false, () -> {
            assertSame(badge, RankGradientAnimation.resolve(badge));
            assertSame(usernameBase, RankGradientAnimation.resolve(username));
            assertNull(RankGradientAnimation.shade(username, 0d), "an uncoloured name is drawn flat");
        });
    }

    @Test
    void restoresAnInheritedUsernameColorAsNull() {
        TextColor username = colorAt(ColorRamp.of(0x4CB4FA), 0d, RankGradientAnimation.Target.USERNAME, null);

        withColorSettings(true, false, () -> assertNull(RankGradientAnimation.resolve(username)));
    }

    @Test
    void switchesExistingDecorationsBetweenIndividualAndRolePalettes() {
        ColorRamp individual = ColorRamp.of(List.of(0x000000, 0xFFFFFF));
        ColorRamp role = ColorRamp.of(0x4CB4FA);
        TextColor color = colorAt(individual, role, 1d, RankGradientAnimation.Target.USERNAME, TextColor.fromRgb(0xFFFFFF));

        withPerUserColors(true, () -> assertSame(color, RankGradientAnimation.resolve(color)));
        withPerUserColors(false, () -> assertEquals(0x4CB4FA, resolved(color)));
        withPerUserColors(true, () -> assertSame(color, RankGradientAnimation.resolve(color)));
    }

    @Test
    void leavesSolidRolesAlone() {
        TextColor solid = colorAt(ColorRamp.of(0x4CB4FA), 0d);

        withAnimation(true, () -> {
            assertSame(solid, RankGradientAnimation.resolve(solid));
            assertNull(RankGradientAnimation.shade(solid, 0.5d), "one colour has nothing to grade or move");
        });
    }

    @Test
    void leavesEveryOtherColourInTheGameAlone() {
        // The lookups run on every glyph drawn, so ordinary text must come straight back.
        TextColor chatColor = TextColor.fromRgb(0x55FFFF);
        TextColor intentionalShaderColor = TextColor.fromRgb(0x40EB00);

        withAnimation(true, () -> {
            assertSame(chatColor, RankGradientAnimation.resolve(chatColor));
            assertNull(RankGradientAnimation.shade(chatColor, 0.5d));
            assertSame(
                    intentionalShaderColor,
                    RankGradientAnimation.resolve(intentionalShaderColor),
                    "unregistered Wynncraft shader text remains intentional");
            assertNull(RankGradientAnimation.resolve(null), "an unstyled glyph has no colour to change");
        });
    }

    @Test
    void aPillBuiltForAGradientRoleCarriesStopsThatMove() {
        // The colours have to survive from the built component through to rendering by
        // identity alone; anything that copied them by value would silently stop moving.
        List<TextColor> backgrounds = pillBackgroundColors(NotificationAccessor.gradientPill(
                        "AB", GRADIENT, GRADIENT, TextColor.fromRgb(0x1F2126), null, null)
                .component());

        withAnimation(true, () -> assertNotEquals(
                shaded(backgrounds.getFirst(), 0d),
                shaded(backgrounds.getFirst(), 0.5d),
                "the pill's first block has moved"));
    }

    @Test
    void registersAWholePillOnceEvenWhenTheRegistryIsFull() {
        RankGradientAnimation.batchRegistrations(() -> {
            RankGradientAnimation.Axis filler = axis(GRADIENT, RankGradientAnimation.Target.RANK_BADGE);
            for (int index = 0; index < RankGradientAnimation.MAX_REMEMBERED_STOPS; index++) {
                filler.colorAt(index % AXIS_LENGTH, 0f, null);
            }
            return null;
        });
        assertEquals(RankGradientAnimation.MAX_REMEMBERED_STOPS, RankGradientAnimation.rememberedStopCount());

        long registrationsBefore = RankGradientAnimation.publicationCount();
        NotificationAccessor.gradientPill("Upper Strategist", GRADIENT, GRADIENT, TextColor.fromRgb(0xFFFFFF), null, null);

        assertEquals(
                registrationsBefore + 1,
                RankGradientAnimation.publicationCount(),
                "all glyph stops should be registered together");
        assertEquals(
                RankGradientAnimation.MAX_REMEMBERED_STOPS,
                RankGradientAnimation.rememberedStopCount(),
                "the registry remains bounded");
    }

    @Test
    void rejectsAPinNestedInsideAnEvictableBatch() {
        assertThrows(
                IllegalStateException.class,
                () -> RankGradientAnimation.batchRegistrations(
                        () -> RankGradientAnimation.pin(() -> colorAt(GRADIENT, 0d))));
    }

    @Test
    void releasesSeveralPinnedDecorationsAtOnce() {
        RankGradientAnimation.Pinned<TextColor> first = RankGradientAnimation.pin(() -> colorAt(GRADIENT, 0d));
        RankGradientAnimation.Pinned<TextColor> second = RankGradientAnimation.pin(() -> colorAt(GRADIENT, 1d));
        long registrationsBefore = RankGradientAnimation.publicationCount();

        RankGradientAnimation.releaseAll(List.of(first.colors(), second.colors()));

        assertEquals(registrationsBefore + 1, RankGradientAnimation.publicationCount());
        assertFalse(RankGradientAnimation.isDecorationColor(first.value()));
        assertFalse(RankGradientAnimation.isDecorationColor(second.value()));
    }

    @Test
    void recognisesMarkedFixedDecorationColors() {
        TextColor fixed = RankGradientAnimation.markDecorationColor(TextColor.fromRgb(0x1F2126));

        assertTrue(RankGradientAnimation.isDecorationColor(fixed));
        assertSame(fixed, RankGradientAnimation.resolve(fixed));
        assertNull(RankGradientAnimation.shade(fixed, 0.5d), "a fixed colour is not graded");
    }

    /** A lone glyph at {@code position} along an axis painted with {@code ramp}. */
    private static TextColor colorAt(ColorRamp ramp, double position) {
        return colorAt(ramp, position, RankGradientAnimation.Target.RANK_BADGE);
    }

    private static TextColor colorAt(ColorRamp ramp, double position, RankGradientAnimation.Target target) {
        return colorAt(ramp, position, target, null);
    }

    private static TextColor colorAt(
            ColorRamp ramp, double position, RankGradientAnimation.Target target, TextColor baseColor) {
        return colorAt(ramp, ramp, position, target, baseColor);
    }

    private static TextColor colorAt(
            ColorRamp displayRamp,
            ColorRamp roleRamp,
            double position,
            RankGradientAnimation.Target target,
            TextColor baseColor) {
        return RankGradientAnimation.axis(displayRamp, roleRamp, target, AXIS_LENGTH)
                .colorAt((float) (position * AXIS_LENGTH), 0f, baseColor);
    }

    private static RankGradientAnimation.Axis axis(ColorRamp ramp, RankGradientAnimation.Target target) {
        return RankGradientAnimation.axis(ramp, ramp, target, AXIS_LENGTH);
    }

    private static int resolved(TextColor color) {
        return RankGradientAnimation.resolve(color).getValue();
    }

    /** The colour at a glyph's origin, graded along its gradient at {@code phase}. */
    private static int shaded(TextColor color, double phase) {
        return RankGradientAnimation.shade(color, phase).rgbAt(0f);
    }

    private static List<TextColor> pillBackgroundColors(Component pill) {
        return ComponentTextEditor.flatten(pill).stream()
                .filter(fragment -> fragment.text().indexOf(WynnPillGlyphs.BACKGROUND) >= 0)
                .map(fragment -> fragment.style().getColor())
                .toList();
    }

    /**
     * Moments on the animation clock later than any it has seen, each later than the
     * last, so tests can move it forward whatever order they run in.
     */
    private static long lastMoment = System.nanoTime() / 1_000_000L + 1_000_000_000L;

    private static synchronized long later(long millis) {
        lastMoment += millis;
        return lastMoment;
    }

    private static <T> T withAnimationSpeed(int percent, Supplier<T> body) {
        Setting.IntSetting previous = SeqClient.gradientAnimationSpeedSetting;
        try {
            SeqClient.gradientAnimationSpeedSetting =
                    new Setting.IntSetting("gradient_animation_speed", "chat", percent, 10, 500, 5);
            return body.get();
        } finally {
            SeqClient.gradientAnimationSpeedSetting = previous;
        }
    }

    private static void withAnimation(boolean enabled, Runnable body) {
        withGradientSettings(true, true, enabled, false, body);
    }

    private static void withColorSettings(boolean pills, boolean usernames, Runnable body) {
        Setting.BooleanSetting previousPills = SeqClient.colorRankPillsSetting;
        Setting.BooleanSetting previousUsernames = SeqClient.colorUsernamesSetting;
        try {
            SeqClient.colorRankPillsSetting = new Setting.BooleanSetting("color_rank_pills", "chat", pills);
            SeqClient.colorUsernamesSetting = new Setting.BooleanSetting("color_usernames", "chat", usernames);
            body.run();
        } finally {
            SeqClient.colorRankPillsSetting = previousPills;
            SeqClient.colorUsernamesSetting = previousUsernames;
        }
    }

    private static void withPerUserColors(boolean enabled, Runnable body) {
        Setting.BooleanSetting previous = SeqClient.usePerUserColorsSetting;
        try {
            SeqClient.usePerUserColorsSetting =
                    new Setting.BooleanSetting("use_per_user_colors", "chat", enabled);
            body.run();
        } finally {
            SeqClient.usePerUserColorsSetting = previous;
        }
    }

    private static void withGradientSettings(
            boolean pillGradients,
            boolean usernameGradients,
            boolean pillAnimation,
            boolean usernameAnimation,
            Runnable body) {
        Setting.BooleanSetting previousPillGradients = SeqClient.showRankPillGradientsSetting;
        Setting.BooleanSetting previousUsernameGradients = SeqClient.showUsernameGradientsSetting;
        Setting.BooleanSetting previousPillAnimation = SeqClient.animateRankGradientsSetting;
        Setting.BooleanSetting previousUsernameAnimation = SeqClient.animateUsernameGradientsSetting;
        try {
            SeqClient.showRankPillGradientsSetting =
                    new Setting.BooleanSetting("show_rank_pill_gradients", "chat", pillGradients);
            SeqClient.showUsernameGradientsSetting =
                    new Setting.BooleanSetting("show_username_gradients", "chat", usernameGradients);
            SeqClient.animateRankGradientsSetting =
                    new Setting.BooleanSetting("animate_rank_gradients", "chat", pillAnimation);
            SeqClient.animateUsernameGradientsSetting =
                    new Setting.BooleanSetting("animate_username_gradients", "chat", usernameAnimation);
            body.run();
        } finally {
            SeqClient.showRankPillGradientsSetting = previousPillGradients;
            SeqClient.showUsernameGradientsSetting = previousUsernameGradients;
            SeqClient.animateRankGradientsSetting = previousPillAnimation;
            SeqClient.animateUsernameGradientsSetting = previousUsernameAnimation;
        }
    }
}
