package com.seqwawa.seq.ui;

import static com.seqwawa.seq.managers.ThemeManager.color;
import static com.seqwawa.seq.ui.theme.UiColor.*;
import static com.seqwawa.seq.ui.WorldMapUi.*;
import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.map.MapDisplayMode;
import com.seqwawa.seq.map.WorldMapSettings;
import com.seqwawa.seq.map.WorldMapSidebarPanel;
import com.seqwawa.seq.utils.rendering.UiCanvas;
import com.seqwawa.seq.utils.rendering.UiRenderer;

/** Scroll position and common drawing for one mode's left sidebar. */
final class MapSidebar {
    static final float BUTTON_HEIGHT = 24;
    static final float INPUT_HEIGHT = 24;
    static final float PANEL_HEADER_HEIGHT = 28;
    static final float PANEL_TOP = 92;
    static final float HEADER_HEIGHT = 44;
    static final float SCROLL_STEP = 28;
    private static final float PANEL_LABEL_WIDTH = 116;
    private static final float PANEL_SUMMARY_WIDTH = 50;
    private float scroll;
    private float contentHeight;

    float scroll() { return scroll; }
    float y(float contentY) { return contentY - scroll; }
    void reset() { scroll = 0; }
    void contentHeight(float height, float screenHeight) { contentHeight = height; clamp(screenHeight); }
    void scroll(double delta, float screenHeight) { scroll -= (float) delta * SCROLL_STEP; clamp(screenHeight); }
    private void clamp(float height) { scroll = Math.max(0, Math.min(scroll, maxScroll(height))); }
    private float maxScroll(float height) { return Math.max(0, contentHeight - height); }

    static boolean hit(float mx, float my, float x, float y, float w, float h) {
        return new UiBounds(x, y, w, h).contains(mx, my);
    }
    static void button(UiCanvas canvas, float x, float y, float w, float h, String label,
            boolean active, WorldMapFrame frame) {
        UiBounds bounds = new UiBounds(x, y, w, h);
        bounds.fill(canvas, color(active ? MAP_CONTROL_ACTIVE : bounds.contains(frame.mouseX(), frame.mouseY()) ? MAP_CONTROL_HOVER : MAP_CONTROL));
        bounds.stroke(canvas, 1, color(MAP_BORDER));
        drawText(canvas, x + w / 2, y + h / 2, 12, label, color(MAP_TEXT), TextAlignment.CENTER);
    }
    void renderBase(UiCanvas canvas, WorldMapFrame frame, MapDisplayMode mode, String centerLabel) {
        canvas.fillRect(0, 0, SIDEBAR_WIDTH, frame.height(), color(MAP_SIDEBAR));
        canvas.fillRect(0, 0, SIDEBAR_WIDTH, HEADER_HEIGHT, color(MAP_HEADER));
        String font = SeqClient.getFontManager().getSelectedFont();
        String title = mode.mapTitle();
        float width = UiRenderer.measureText(title, font, 18).width();
        float size = width > 0 ? Math.min(18, 18 * (SIDEBAR_WIDTH - 2 * PADDING - 30) / width) : 18;
        SequoiaUiStyle.drawSidebarTitle(canvas, font, SIDEBAR_WIDTH, title, size, color(MAP_TITLE));
        button(canvas, PADDING, 58, SIDEBAR_WIDTH - 2 * PADDING, BUTTON_HEIGHT, centerLabel, false, frame);
    }

    void panelHeader(
            UiCanvas canvas,
            float y,
            String label,
            String summary,
            WorldMapSidebarPanel panel, WorldMapFrame frame, WorldMapSettings settings) {
        boolean expanded = settings.sidebarPanelExpanded(panel);
        boolean hovered = hit(
                frame.mouseX(),
                frame.mouseY(),
                PADDING,
                y,
                SIDEBAR_WIDTH - PADDING * 2,
                PANEL_HEADER_HEIGHT);
        canvas.fillRect(PADDING,
                y,
                SIDEBAR_WIDTH - PADDING * 2,
                PANEL_HEADER_HEIGHT,
                hovered ? color(MAP_CONTROL_HOVER)
                        : panel == WorldMapSidebarPanel.TOTEM_SOLVER && expanded
                                ? color(MAP_CONTROL_ACTIVE) : color(MAP_CONTROL_INACTIVE));
        canvas.strokeRect(PADDING,
                y,
                SIDEBAR_WIDTH - PADDING * 2,
                PANEL_HEADER_HEIGHT,
                1,
                color(MAP_BORDER));
        drawText(
                canvas,
                PADDING + 10,
                y + PANEL_HEADER_HEIGHT / 2f,
                12,
                expanded ? "v" : ">",
                color(MAP_SUBTEXT),
                TextAlignment.CENTER);
        drawFittedText(
                canvas,
                PADDING + 22,
                y + PANEL_HEADER_HEIGHT / 2f,
                12,
                label,
                color(MAP_TEXT),
                PANEL_LABEL_WIDTH,
                TextAlignment.LEFT);
        drawFittedText(
                canvas,
                SIDEBAR_WIDTH - PADDING - 8,
                y + PANEL_HEADER_HEIGHT / 2f,
                10,
                summary,
                color(MAP_SUBTEXT),
                PANEL_SUMMARY_WIDTH,
                TextAlignment.RIGHT);
    }

    void renderScrollbar(UiCanvas canvas, float screenHeight) {
        float viewportHeight = Math.max(0, screenHeight - PANEL_TOP);
        float maxScroll = maxScroll(screenHeight);
        if (maxScroll <= 0 || viewportHeight <= 0) {
            return;
        }
        float trackX = SIDEBAR_WIDTH - 5;
        float trackY = PANEL_TOP + 4;
        float trackHeight = viewportHeight - 8;
        float scrollableContentHeight = Math.max(viewportHeight, contentHeight - PANEL_TOP);
        float thumbHeight = Math.max(24, trackHeight * (viewportHeight / scrollableContentHeight));
        float thumbY = trackY + (trackHeight - thumbHeight) * (scroll / maxScroll);
        canvas.fillRect(trackX, trackY, 3, trackHeight, color(CONTROL_TRACK));
        canvas.fillRect(trackX, thumbY, 3, thumbHeight, color(CONTROL_THUMB));
    }

    static void searchInput(UiCanvas canvas, float y, boolean dropdownOpen,
            boolean inputFocused, String search, String unfocusedValue, WorldMapFrame frame) {
        float width = SequoiaUiStyle.searchWidth(SIDEBAR_WIDTH - PADDING * 2);
        String value = inputFocused ? search : unfocusedValue;
        String displayValue = value == null || value.isBlank() ? "Search..." : value;
        DropdownMenu.trigger(canvas, PADDING, y, width, INPUT_HEIGHT, displayValue,
                dropdownOpen, true, frame.mouseX(), frame.mouseY());
        if (inputFocused) {
            float cursorX = PADDING + 8 + Math.min(textWidth(search, DropdownMenu.FONT_SIZE), Math.max(0, width - 30));
            drawText(canvas, cursorX, y + INPUT_HEIGHT / 2f, DropdownMenu.FONT_SIZE, "|", color(TEXT_PRIMARY), TextAlignment.LEFT);
        }
    }
}
