package com.seqwawa.seq.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void avoidsWynncraftShaderMarkersInStaticAndAnimatedSamples() {
        ColorRamp ramp = ColorRamp.of(List.of(0x25FF00, 0x72D400));
        TextColor staticCrossing = RankGradientAnimation.colorAt(ramp, 8d / 17d);
        TextColor animatedCrossing = RankGradientAnimation.colorAt(ramp, 0d);

        assertEquals(0x49EC00, staticCrossing.getValue());
        withAnimation(true, () -> assertEquals(0x49EC00, animated(animatedCrossing, 4d / 17d)));
    }

    @Test
    void avoidsShaderMarkersWhenSettingsSelectAlternatePalettePaths() {
        ColorRamp dangerousGradient = ColorRamp.of(List.of(0x40EB00, 0x72D400));
        TextColor flattened = RankGradientAnimation.colorAt(dangerousGradient, 1d);
        TextColor switchedSolid = RankGradientAnimation.colorAt(
                ColorRamp.of(0x123456),
                ColorRamp.of(0x40EB00),
                0d,
                RankGradientAnimation.Target.USERNAME,
                null);
        TextColor switchedGradient = RankGradientAnimation.colorAt(
                ColorRamp.of(0x123456),
                dangerousGradient,
                0d,
                RankGradientAnimation.Target.USERNAME,
                null);

        withGradientSettings(
                false,
                true,
                false,
                false,
                () -> assertEquals(0x40EC00, animated(flattened, 0d), "flattened gradient"));
        withGradientSettings(true, true, false, false, () -> withPerUserColors(false, () -> {
            assertEquals(0x40EC00, animated(switchedSolid, 0d), "switched solid palette");
            assertEquals(0x40EC00, animated(switchedGradient, 0d), "switched gradient palette");
        }));
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

        withGradientSettings(true, true, false, true, () -> {
            assertSame(badge, RankGradientAnimation.animate(badge, 0.5d), "the badge stays static");
            assertEquals(0xFFFFFF, animated(username, 0.5d), "the username moves independently");
        });
    }

    @Test
    void controlsBadgeAndUsernameGradientsIndependently() {
        TextColor badge = RankGradientAnimation.colorAt(GRADIENT, 1d, RankGradientAnimation.Target.RANK_BADGE);
        TextColor username = RankGradientAnimation.colorAt(GRADIENT, 1d, RankGradientAnimation.Target.USERNAME);

        withGradientSettings(true, false, false, false, () -> {
            assertSame(badge, RankGradientAnimation.animate(badge, 0.5d), "the pill keeps its complete gradient");
            assertEquals(0x000000, animated(username, 0.5d), "the username flattens independently");
        });
        withGradientSettings(false, true, false, false, () -> {
            assertEquals(0x000000, animated(badge, 0.5d), "the pill flattens independently");
            assertSame(
                    username,
                    RankGradientAnimation.animate(username, 0.5d),
                    "the username keeps its complete gradient");
        });
    }

    @Test
    void restoresEachTargetsBaseColorImmediatelyWhenRoleColoringIsDisabled() {
        TextColor pillBase = TextColor.fromRgb(0x40EB00);
        TextColor usernameBase = TextColor.fromRgb(0xFFFFFF);
        TextColor badge = RankGradientAnimation.colorAt(
                ColorRamp.of(0x4CB4FA), 0d, RankGradientAnimation.Target.RANK_BADGE, pillBase);
        TextColor username = RankGradientAnimation.colorAt(
                GRADIENT, 1d, RankGradientAnimation.Target.USERNAME, usernameBase);

        withColorSettings(false, true, () -> {
            assertSame(pillBase, RankGradientAnimation.animate(badge, 0.5d));
            assertSame(username, RankGradientAnimation.animate(username, 0.5d));
        });
        withColorSettings(true, false, () -> {
            assertSame(badge, RankGradientAnimation.animate(badge, 0.5d));
            assertSame(usernameBase, RankGradientAnimation.animate(username, 0.5d));
        });
    }

    @Test
    void restoresAnInheritedUsernameColorAsNull() {
        TextColor username = RankGradientAnimation.colorAt(
                ColorRamp.of(0x4CB4FA), 0d, RankGradientAnimation.Target.USERNAME, null);

        withColorSettings(true, false, () -> assertNull(RankGradientAnimation.animate(username, 0.5d)));
    }

    @Test
    void switchesExistingDecorationsBetweenIndividualAndRolePalettes() {
        ColorRamp individual = ColorRamp.of(List.of(0x000000, 0xFFFFFF));
        ColorRamp role = ColorRamp.of(0x4CB4FA);
        TextColor color = RankGradientAnimation.colorAt(
                individual, role, 1d, RankGradientAnimation.Target.USERNAME, TextColor.fromRgb(0xFFFFFF));

        withPerUserColors(true, () -> assertSame(color, RankGradientAnimation.animate(color, 0.5d)));
        withPerUserColors(
                false, () -> assertEquals(0x4CB4FA, RankGradientAnimation.animate(color, 0.5d).getValue()));
        withPerUserColors(true, () -> assertSame(color, RankGradientAnimation.animate(color, 0.5d)));
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
        TextColor intentionalShaderColor = TextColor.fromRgb(0x40EB00);

        withAnimation(true, () -> {
            assertSame(chatColor, RankGradientAnimation.animate(chatColor, 0.5d));
            assertSame(
                    intentionalShaderColor,
                    RankGradientAnimation.animate(intentionalShaderColor, 0.5d),
                    "unregistered Wynncraft shader text remains intentional");
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

    @Test
    void publishesAWholePillOnceEvenWhenTheRegistryIsFull() {
        RankGradientAnimation.batchRegistrations(() -> {
            for (int index = 0; index < 4096; index++) {
                RankGradientAnimation.colorAt(GRADIENT, index / 4095d);
            }
            return null;
        });
        assertEquals(4096, RankGradientAnimation.rememberedStopCount());

        long publicationsBefore = RankGradientAnimation.publicationCount();
        NotificationAccessor.wynnPill("Upper Strategist", GRADIENT, TextColor.fromRgb(0xFFFFFF), null);

        assertEquals(
                publicationsBefore + 1,
                RankGradientAnimation.publicationCount(),
                "all glyph stops should share one copy-on-write publication");
        assertEquals(4096, RankGradientAnimation.rememberedStopCount(), "the registry remains bounded");
    }

    @Test
    void rejectsAPinNestedInsideAnEvictableBatch() {
        assertThrows(
                IllegalStateException.class,
                () -> RankGradientAnimation.batchRegistrations(
                        () -> RankGradientAnimation.pin(() -> RankGradientAnimation.colorAt(GRADIENT, 0d))));
    }

    @Test
    void releasesSeveralPinnedDecorationsWithOnePublication() {
        RankGradientAnimation.Pinned<TextColor> first =
                RankGradientAnimation.pin(() -> RankGradientAnimation.colorAt(GRADIENT, 0d));
        RankGradientAnimation.Pinned<TextColor> second =
                RankGradientAnimation.pin(() -> RankGradientAnimation.colorAt(GRADIENT, 1d));
        long publicationsBefore = RankGradientAnimation.publicationCount();

        RankGradientAnimation.releaseAll(List.of(first.colors(), second.colors()));

        assertEquals(publicationsBefore + 1, RankGradientAnimation.publicationCount());
        assertFalse(RankGradientAnimation.isDecorationColor(first.value()));
        assertFalse(RankGradientAnimation.isDecorationColor(second.value()));
    }

    @Test
    void recognisesMarkedFixedDecorationColors() {
        TextColor fixed = RankGradientAnimation.markDecorationColor(TextColor.fromRgb(0x1F2126));

        assertTrue(RankGradientAnimation.isDecorationColor(fixed));
        assertSame(fixed, RankGradientAnimation.animate(fixed, 0.5d));
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
