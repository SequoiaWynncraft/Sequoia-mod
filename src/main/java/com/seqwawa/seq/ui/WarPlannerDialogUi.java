package com.seqwawa.seq.ui;

import static com.seqwawa.seq.managers.ThemeManager.color;
import static com.seqwawa.seq.ui.theme.UiColor.*;

import com.seqwawa.seq.model.war.WarCompositionRole;
import com.seqwawa.seq.model.war.WarPlannerSnapshot;
import com.seqwawa.seq.model.war.WarPlannerSnapshot.Team;

import com.seqwawa.seq.model.war.WarPlannerSnapshot.RosterMember;
import com.seqwawa.seq.utils.rendering.UiCanvas;
import java.util.List;
import java.util.Locale;

import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.managers.AssetManager;
import java.awt.Color;

final class WarPlannerDialogUi {
    static final float PADDING = 12;
    static final float BUTTON_HEIGHT = 22;
    static final float TEAM_EDITOR_SEARCH_HEIGHT = 22;
    static final float TEAM_EDITOR_SEARCH_OFFSET = 90;
    static final float TEAM_EDITOR_LIST_OFFSET = 118;
    static final int TEAM_EDITOR_SEARCH_MAX_LENGTH = 64;
    private static final float COMPOSITION_ICON_SIZE = 12;
    private static final float COMPOSITION_ICON_GAP = 3;

    private WarPlannerDialogUi() {}

    static int backgroundOpacityPercent() {
        return SeqClient.getWarPlannerBackgroundOpacitySetting() == null
                ? 100
                : SeqClient.getWarPlannerBackgroundOpacitySetting().getValue();
    }

    static Color plannerBackground(Color source) {
        int alpha = opacityAlpha(source.getAlpha(), backgroundOpacityPercent());
        return new Color(source.getRed(), source.getGreen(), source.getBlue(), alpha);
    }

    static int opacityAlpha(int sourceAlpha, int opacityPercent) {
        return Math.round(Math.max(0, Math.min(255, sourceAlpha))
                * Math.max(0, Math.min(100, opacityPercent))
                / 100f);
    }

    static boolean shouldBlurBackground(int opacityPercent) {
        return opacityPercent >= 100;
    }

    static void button(UiCanvas canvas, float mx, float my, float x, float y, float width, float height,
            String label, boolean danger, boolean disabled) {
        button(canvas, mx, my, new UiBounds(x, y, width, height), label, danger, false, disabled);
    }

    static void button(UiCanvas canvas, float mx, float my, UiBounds bounds,
            String label, boolean danger, boolean disabled) {
        button(canvas, mx, my, bounds, label, danger, false, disabled);
    }

    static void primaryButton(UiCanvas canvas, float mx, float my, UiBounds bounds,
            String label, boolean disabled) {
        button(canvas, mx, my, bounds, label, false, true, disabled);
    }

    static void primaryButton(UiCanvas canvas, float mx, float my, float x, float y, float width, float height,
            String label, boolean disabled) {
        button(canvas, mx, my, new UiBounds(x, y, width, height), label, false, true, disabled);
    }

    static void button(UiCanvas canvas, float mx, float my, UiBounds bounds,
            String label, boolean danger, boolean primary, boolean disabled) {
        boolean hovered = !disabled && bounds.contains(mx, my);
        Color background = disabled ? color(ACCENT_DISABLED)
                : primary ? color(hovered ? ACCENT_PRIMARY_HOVER : ACCENT_PRIMARY)
                : danger ? color(hovered ? CONTROL_DANGER_HOVER : CONTROL_DANGER)
                : color(hovered ? CONTROL_INPUT_HOVER : CONTROL_INPUT);
        canvas.fillRect(bounds.x(), bounds.y(), bounds.width(), bounds.height(), background);
        text(canvas, label, bounds.x() + bounds.width() / 2, bounds.y() + bounds.height() / 2, 10,
                color(disabled ? TEXT_DISABLED : TEXT_PRIMARY), true);
    }

    static boolean hit(float mx, float my, float x, float y, float width, float height) {
        return new UiBounds(x, y, width, height).contains(mx, my);
    }

    static String rosterSearchText(RosterMember member) {
        return String.join(
                        "\n",
                        member.displayName(),
                        member.minecraftUsername() == null ? "" : member.minecraftUsername(),
                        member.discordUsername() == null ? "" : member.discordUsername(),
                        member.playerUuid() == null ? "" : member.playerUuid())
                .toLowerCase(Locale.ROOT);
    }

    static float renderCompositionIcons(
            UiCanvas canvas, List<WarCompositionRole> compositionRoles, float x, float y) {
        float nextX = x;
        for (WarCompositionRole role : WarCompositionRole.ordered(compositionRoles)) {
            AssetManager.Asset asset = SeqClient.assetManager == null
                    ? null
                    : SeqClient.assetManager.getAsset(role.assetKey());
            if (asset != null && asset.getImage() != null) {
                canvas.drawImage(asset.getImage(), nextX, y, COMPOSITION_ICON_SIZE, COMPOSITION_ICON_SIZE, 1f);
            } else {
                canvas.fillRect(nextX, y, COMPOSITION_ICON_SIZE, COMPOSITION_ICON_SIZE,
                        color(CONTROL_INPUT));
                text(canvas, role.name().substring(0, 1), nextX + COMPOSITION_ICON_SIZE / 2,
                        y + COMPOSITION_ICON_SIZE / 2, 8, color(TEXT_SECONDARY), true);
            }
            nextX += COMPOSITION_ICON_SIZE + COMPOSITION_ICON_GAP;
        }
        return nextX;
    }

    static int availableCharacters(float left, float right, float fontSize, int maximum) {
        float approximateCharacterWidth = fontSize * .55f;
        return Math.max(1, Math.min(maximum, (int) ((right - left) / approximateCharacterWidth)));
    }

    static boolean samePlayer(String left, String right) {
        return left != null && right != null && left.equalsIgnoreCase(right);
    }

    static int clampRows(int value, int size) {
        return Math.max(0, Math.min(value, Math.max(0, size - 1)));
    }

    static String truncate(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, Math.max(0, max - 1)) + "…";
    }

    static void text(UiCanvas canvas, String value, float x, float y, float size, Color textColor, boolean centered) {
        text(canvas, value, x, y, size, textColor,
                centered ? UiCanvas.HorizontalAlign.CENTER : UiCanvas.HorizontalAlign.LEFT);
    }

    static void text(
            UiCanvas canvas,
            String value,
            float x,
            float y,
            float size,
            Color textColor,
            UiCanvas.HorizontalAlign alignment) {
        canvas.drawText(value == null ? "" : value, x, y, new UiCanvas.TextStyle(
                SeqClient.getFontManager().getSelectedFont(), size, textColor,
                alignment,
                UiCanvas.VerticalAlign.MIDDLE));
    }
    static long monotonicMillis() {
        return System.nanoTime() / 1_000_000L;
    }

    static String teamName(WarPlannerSnapshot snapshot, Long id) {
        Team team = snapshot.team(id);
        return team == null ? "Team #" + id : team.name();
    }

    static Color parseColor(String value, Color fallback) {
        try {
            return Color.decode(value == null ? "" : value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
    enum ButtonTone { SECONDARY, PRIMARY, DANGER, QUIET_DANGER }

    static void tonedButton(UiCanvas canvas, float nvgMouseX, float nvgMouseY, float x, float y, float width, float height, String label, ButtonTone tone, boolean disabled) {
        boolean hovered = !disabled && hit(nvgMouseX, nvgMouseY, x, y, width, height);
        Color background = disabled ? color(ACCENT_DISABLED) : switch (tone) {
            case PRIMARY -> color(hovered ? ACCENT_PRIMARY_HOVER : ACCENT_PRIMARY);
            case DANGER -> color(hovered ? CONTROL_DANGER_HOVER : CONTROL_DANGER);
            case QUIET_DANGER -> color(hovered ? CONTROL_DANGER_HOVER : CONTROL_INPUT);
            case SECONDARY -> color(hovered ? CONTROL_INPUT_HOVER : CONTROL_INPUT);
        };
        canvas.fillRect(x, y, width, height, background);
        text(canvas, label, x + width / 2, y + height / 2, 10,
                color(disabled ? TEXT_DISABLED : tone == ButtonTone.QUIET_DANGER && !hovered ? CONTROL_DANGER_HOVER : TEXT_PRIMARY), true);
    }
}
