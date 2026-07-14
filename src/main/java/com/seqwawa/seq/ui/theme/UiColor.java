package com.seqwawa.seq.ui.theme;

import java.awt.Color;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public enum UiColor {
    BACKGROUND_OVERLAY("background_overlay", rgba(10, 10, 16, 100), true),
    BACKGROUND_MODAL_OVERLAY("background_modal_overlay", rgba(0, 0, 0, 160)),
    BACKGROUND_SIDEBAR("background_sidebar", rgba(18, 18, 26, 200), true),
    BACKGROUND_BODY("background_body", rgba(22, 22, 30, 100), true),
    BACKGROUND_BODY_OPAQUE("background_body_opaque", rgba(20, 20, 30, 255), true),
    BACKGROUND_HEADER("background_header", rgba(26, 26, 36, 110), true),
    BACKGROUND_CONTENT("background_content", rgba(30, 30, 42, 110), true),
    BACKGROUND_CONTENT_FOCUSED("background_content_focused", rgba(26, 26, 36, 120), true),
    BACKGROUND_POPUP("background_popup", rgba(40, 40, 55, 240)),

    ACCENT_PRIMARY("accent_main_light", rgba(160, 130, 220, 255), true),
    ACCENT_PRIMARY_HOVER("accent_main_light_hover", rgba(190, 160, 250, 255), true),
    ACCENT_PRIMARY_DARK("accent_main_dark", rgba(60, 30, 120, 255), true),
    ACCENT_PRIMARY_DARK_HOVER("accent_main_dark_hover", rgba(80, 50, 140, 255), true),
    ACCENT_DISABLED("accent_main_inactive", rgba(60, 60, 70, 255), true),
    ACCENT_SECONDARY("accent_alt_light", rgba(80, 80, 100, 255), true),
    ACCENT_DIVIDER("accent_alt_dark", rgba(40, 40, 55, 255), true),

    TEXT_PRIMARY("text_primary", rgba(255, 255, 255, 255), true),
    TEXT_SECONDARY("text_pleasant", rgba(220, 220, 230, 255), true),
    TEXT_MUTED("text_faint", rgba(160, 160, 180, 255), true),
    TEXT_DISABLED("text_inactive", rgba(120, 120, 130, 255), true),

    CONTROL_INPUT("element_input_primary", rgba(35, 35, 48, 255), true),
    CONTROL_INPUT_SECONDARY("element_input_secondary", rgba(80, 80, 100, 255), true),
    CONTROL_INPUT_HOVER("element_input_hover", rgba(55, 55, 75, 255), true),
    CONTROL_BORDER("control_border", rgba(130, 100, 200, 180)),
    CONTROL_TRACK("element_scrollbar_track", rgba(30, 30, 42, 255), true),
    CONTROL_THUMB("element_scrollbar_thumb", rgba(160, 130, 220, 255), true),
    CONTROL_DANGER("element_danger_primary", rgba(200, 60, 60, 200), true),
    CONTROL_DANGER_HOVER("element_danger_hover", rgba(220, 80, 80, 220), true),
    CONTROL_WARNING("element_warning_primary", rgba(220, 176, 88, 255), true),
    CONTROL_SUCCESS("element_good_primary", rgba(88, 196, 122, 255), true),

    STATUS_SUCCESS_BACKGROUND("status_success_background", rgba(56, 140, 88, 220)),
    STATUS_SUCCESS_BORDER("status_success_border", rgba(88, 196, 122, 255)),
    STATUS_WARNING_BACKGROUND("status_warning_background", rgba(148, 108, 44, 220)),
    STATUS_WARNING_BORDER("status_warning_border", rgba(220, 176, 88, 255)),
    STATUS_DANGER_BACKGROUND("status_danger_background", rgba(160, 64, 72, 220)),
    STATUS_DANGER_BORDER("status_danger_border", rgba(226, 108, 118, 255)),

    MAP_SIDEBAR("map_sidebar", rgba(18, 18, 24, 235)),
    MAP_TINT("map_tint", rgba(4, 7, 10, 32)),
    MAP_HEADER("map_header", rgba(28, 28, 38, 230)),
    MAP_CONTROL("map_control", rgba(42, 42, 54, 220)),
    MAP_CONTROL_INACTIVE("map_control_inactive", rgba(33, 33, 44, 235)),
    MAP_CONTROL_HOVER("map_control_hover", rgba(62, 62, 82, 235)),
    MAP_CONTROL_ACTIVE("map_control_active", rgba(92, 74, 138, 235)),
    MAP_BORDER("map_border", rgba(92, 92, 115, 180)),
    MAP_TEXT("map_text", rgba(240, 240, 245, 255)),
    MAP_SUBTEXT("map_subtext", rgba(175, 175, 190, 255)),
    MAP_TITLE("map_title", rgba(170, 145, 230, 255)),
    MAP_PLAYER("map_player", rgba(255, 255, 255, 255)),
    MAP_SELECTED_CLUSTER("map_selected_cluster", rgba(235, 58, 58, 255)),
    MAP_TERRITORY("map_territory", rgba(75, 194, 205, 175)),
    MAP_TERRITORY_HOVER_TEXT("map_territory_hover_text", rgba(185, 247, 250, 255)),
    MAP_SELECTED_TERRITORY("map_selected_territory", rgba(255, 204, 82, 235)),
    MAP_WORLD_EVENT("map_world_event", rgba(62, 190, 218, 245)),
    MAP_TRACKED_WORLD_EVENT("map_tracked_world_event", rgba(255, 194, 72, 250));

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
