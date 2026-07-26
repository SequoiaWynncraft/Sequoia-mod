package com.seqwawa.seq.ui.theme;

import java.awt.Color;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public enum UiColor {
    BACKGROUND_OVERLAY("background.overlay", rgba(10, 10, 16, 100), true),
    BACKGROUND_MODAL_OVERLAY("background.modal_overlay", rgba(0, 0, 0, 160)),
    BACKGROUND_SIDEBAR("background.sidebar", rgba(18, 18, 26, 200), true),
    BACKGROUND_BODY("background.body", rgba(22, 22, 30, 100), true),
    BACKGROUND_BODY_OPAQUE("background.body_opaque", rgba(20, 20, 30, 255), true),
    BACKGROUND_HEADER("background.header", rgba(26, 26, 36, 110), true),
    BACKGROUND_CONTENT("background.content", rgba(30, 30, 42, 110), true),
    BACKGROUND_CONTENT_FOCUSED("background.content_focused", rgba(26, 26, 36, 120), true),
    BACKGROUND_POPUP("background.popup", rgba(40, 40, 55, 240)),

    ACCENT_PRIMARY("accent.primary", rgba(160, 130, 220, 255), true),
    ACCENT_PRIMARY_HOVER("accent.primary_hover", rgba(190, 160, 250, 255), true),
    ACCENT_PRIMARY_DARK("accent.primary_dark", rgba(60, 30, 120, 255), true),
    ACCENT_PRIMARY_DARK_HOVER("accent.primary_dark_hover", rgba(80, 50, 140, 255), true),
    ACCENT_DISABLED("accent.disabled", rgba(60, 60, 70, 255), true),
    ACCENT_SECONDARY("accent.secondary", rgba(80, 80, 100, 255), true),
    ACCENT_DIVIDER("accent.divider", rgba(40, 40, 55, 255), true),

    TEXT_PRIMARY("text.primary", rgba(255, 255, 255, 255), true),
    TEXT_SECONDARY("text.secondary", rgba(220, 220, 230, 255), true),
    TEXT_MUTED("text.muted", rgba(160, 160, 180, 255), true),
    TEXT_DISABLED("text.disabled", rgba(120, 120, 130, 255), true),

    CONTROL_INPUT("control.input", rgba(35, 35, 48, 255), true),
    CONTROL_INPUT_SECONDARY("control.input_secondary", rgba(80, 80, 100, 255), true),
    CONTROL_INPUT_HOVER("control.input_hover", rgba(55, 55, 75, 255), true),
    CONTROL_BORDER("control.border", rgba(130, 100, 200, 180)),
    CONTROL_TRACK("control.scrollbar_track", rgba(30, 30, 42, 255), true),
    CONTROL_THUMB("control.scrollbar_thumb", rgba(160, 130, 220, 255), true),
    CONTROL_DANGER("control.danger", rgba(200, 60, 60, 200), true),
    CONTROL_DANGER_HOVER("control.danger_hover", rgba(220, 80, 80, 220), true),
    CONTROL_WARNING("control.warning", rgba(220, 176, 88, 255), true),
    CONTROL_SUCCESS("control.success", rgba(88, 196, 122, 255), true),

    STATUS_SUCCESS_BACKGROUND("status.success_background", rgba(56, 140, 88, 220)),
    STATUS_SUCCESS_BORDER("status.success_border", rgba(88, 196, 122, 255)),
    STATUS_WARNING_BACKGROUND("status.warning_background", rgba(148, 108, 44, 220)),
    STATUS_WARNING_BORDER("status.warning_border", rgba(220, 176, 88, 255)),
    STATUS_DANGER_BACKGROUND("status.danger_background", rgba(160, 64, 72, 220)),
    STATUS_DANGER_BORDER("status.danger_border", rgba(226, 108, 118, 255)),

    MAP_SIDEBAR("map.sidebar", rgba(18, 18, 24, 235)),
    MAP_TINT("map.tint", rgba(4, 7, 10, 32)),
    MAP_HEADER("map.header", rgba(28, 28, 38, 230)),
    MAP_CONTROL("map.control", rgba(42, 42, 54, 220)),
    MAP_CONTROL_INACTIVE("map.control_inactive", rgba(33, 33, 44, 235)),
    MAP_CONTROL_HOVER("map.control_hover", rgba(62, 62, 82, 235)),
    MAP_CONTROL_ACTIVE("map.control_active", rgba(92, 74, 138, 235)),
    MAP_BORDER("map.border", rgba(92, 92, 115, 180)),
    MAP_TEXT("map.text", rgba(240, 240, 245, 255)),
    MAP_SUBTEXT("map.subtext", rgba(175, 175, 190, 255)),
    MAP_TITLE("map.title", rgba(170, 145, 230, 255)),
    MAP_PLAYER("map.player", rgba(255, 255, 255, 255)),
    MAP_SELECTED_CLUSTER("map.selected_cluster", rgba(235, 58, 58, 255)),
    MAP_TERRITORY("map.territory", rgba(75, 194, 205, 175)),
    MAP_TERRITORY_HOVER_TEXT("map.territory_hover_text", rgba(185, 247, 250, 255)),
    MAP_SELECTED_TERRITORY("map.selected_territory", rgba(255, 204, 82, 235)),
    MAP_WORLD_EVENT("map.world_event", rgba(62, 190, 218, 245)),
    MAP_TRACKED_WORLD_EVENT("map.tracked_world_event", rgba(255, 194, 72, 250)),
    MAP_TOTEM("map.totem", rgba(255, 194, 72, 245)),
    MAP_TOTEM_MUTED("map.totem_muted", rgba(190, 150, 76, 130)),
    MAP_TOTEM_RANGE("map.totem_range", rgba(74, 220, 235, 235)),
    MAP_TOTEM_REACH("map.totem_reach", rgba(126, 232, 242, 175));

    private static final Map<String, UiColor> BY_KEY = Arrays.stream(values())
            .collect(Collectors.toUnmodifiableMap(UiColor::key, Function.identity()));

    private final String key;
    private final Color fallback;
    private final boolean required;

    UiColor(String key, Color fallback) {
        this(key, fallback, false);
    }

    UiColor(String key, Color fallback, boolean required) {
        this.key = key;
        this.fallback = fallback;
        this.required = required;
    }

    public String key() {
        return key;
    }

    public Color fallback() {
        return fallback;
    }

    public boolean required() {
        return required;
    }

    public static UiColor fromKey(String key) {
        return BY_KEY.get(key);
    }

    private static Color rgba(int red, int green, int blue, int alpha) {
        return new Color(red, green, blue, alpha);
    }
}
