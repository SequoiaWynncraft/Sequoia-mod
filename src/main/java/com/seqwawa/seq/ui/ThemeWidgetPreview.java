package com.seqwawa.seq.ui;

import static com.seqwawa.seq.managers.ThemeManager.color;
import static com.seqwawa.seq.ui.theme.UiColor.*;

import com.seqwawa.seq.config.Setting;
import com.seqwawa.seq.ui.theme.UiColor;
import com.seqwawa.seq.ui.widget.*;
import com.seqwawa.seq.utils.rendering.UiCanvas;
import java.util.List;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;

/** Interactive examples backed only by local, unregistered settings. */
final class ThemeWidgetPreview {
    private static final float GAP = 12;
    private static final float TOP = SequoiaUiStyle.HEADER_HEIGHT + 8;
    private final List<SettingWidget<?>> widgets = createWidgets();
    private SettingWidget<?> focused;
    private float scroll;
    private float maxScroll;
    private Layout visibleLayout;

    private enum SampleMode { CASUAL, GRIND, ANY }

    private static List<SettingWidget<?>> createWidgets() {
        var disabledToggle = new Setting.BooleanSetting("disabled_toggle", "preview", true);
        disabledToggle.setEnabledCondition(() -> false);
        var disabledChoice = new Setting.ChoiceSetting("disabled_menu", "preview", "Unavailable", List.of("Unavailable"), v -> {});
        disabledChoice.setEnabledCondition(() -> false);
        return List.of(
                new BooleanWidget(new Setting.BooleanSetting("toggle_on", "preview", true)),
                new BooleanWidget(new Setting.BooleanSetting("toggle_off", "preview", false)),
                new BooleanWidget(disabledToggle),
                new ChoiceWidget(new Setting.ChoiceSetting("dropdown", "preview", "Tank", List.of("DPS", "Healer", "Tank", "Other"), v -> {})),
                new EnumWidget<>(new Setting.EnumSetting<>("mode", "preview", SampleMode.CASUAL, SampleMode.class)),
                new ChoiceWidget(disabledChoice),
                new SliderWidget(new Setting.IntSetting("slider", "preview", 65, 0, 100), true),
                new SliderWidget(new Setting.DoubleSetting("decimal_slider", "preview", .5, 0, 1, .05)),
                new StringWidget(new Setting.StringSetting("text_field", "preview", "Sample text")),
                new ColorWidget(new Setting.ColorSetting("color_picker", "preview", 0xA082DC), null));
    }

    void render(UiCanvas canvas, float mouseX, float mouseY) {
        Layout layout = layout(canvas.metrics().width(), canvas.metrics().height());
        visibleLayout = layout;
        maxScroll = Math.max(0, layout.contentHeight() - layout.viewportHeight());
        scroll = Math.max(0, Math.min(scroll, maxScroll));
        float y = TOP - scroll;
        canvas.save();
        canvas.scissor(layout.x(), TOP, layout.width(), layout.viewportHeight());
        float controlsY = y;
        panel(canvas, layout.x(), controlsY, layout.columnWidth(), controlsHeight(), "Settings controls");
        float rowY = controlsY + 30;
        for (int i = 0; i < widgets.size(); i++) {
            SettingWidget<?> widget = widgets.get(i);
            float rowHeight = widget.getHeight();
            canvas.fillRect(layout.x() + 4, rowY, layout.columnWidth() - 8, rowHeight,
                    color(i % 2 == 0 ? BACKGROUND_BODY : BACKGROUND_CONTENT_FOCUSED));
            widget.setPosition(layout.x() + 4, rowY, layout.columnWidth() - 8, rowHeight);
            if (widget instanceof SelectionWidget<?> selection) selection.setViewport(TOP, canvas.metrics().height() - 8);
            if (rowY + rowHeight > TOP && rowY < canvas.metrics().height() - 8) {
                widget.render(canvas, mouseX, mouseY);
            } else widget.onHidden();
            rowY += rowHeight;
        }
        float samplesX = layout.twoColumns() ? layout.x() + layout.columnWidth() + GAP : layout.x();
        float samplesY = layout.twoColumns() ? controlsY : controlsY + controlsHeight() + GAP;
        renderSamples(canvas, samplesX, samplesY, layout.columnWidth(), mouseX, mouseY);
        canvas.restore();
        if (maxScroll > 0) {
            float height = layout.viewportHeight();
            float thumb = Math.max(20, height * height / layout.contentHeight());
            canvas.fillRect(canvas.metrics().width() - 5, TOP, 3, height, color(CONTROL_TRACK));
            canvas.fillRect(canvas.metrics().width() - 5, TOP + (height - thumb) * scroll / maxScroll,
                    3, thumb, color(CONTROL_THUMB));
        }
        SelectionWidget<?> dropdown = openDropdown();
        if (dropdown != null) dropdown.renderOverlay(canvas, mouseX, mouseY);
    }

    private void renderSamples(UiCanvas canvas, float x, float y, float width, float mouseX, float mouseY) {
        panel(canvas, x, y, width, 96, "Buttons");
        float buttonWidth = (width - 24) / 2;
        sampleButton(canvas, x + 8, y + 30, buttonWidth, "Primary", ACCENT_PRIMARY, ACCENT_PRIMARY_HOVER, false, TEXT_PRIMARY, mouseX, mouseY);
        sampleButton(canvas, x + 16 + buttonWidth, y + 30, buttonWidth, "Secondary", CONTROL_INPUT, CONTROL_INPUT_HOVER, false, TEXT_PRIMARY, mouseX, mouseY);
        sampleButton(canvas, x + 8, y + 58, buttonWidth, "Danger", CONTROL_DANGER, CONTROL_DANGER_HOVER, false, TEXT_PRIMARY, mouseX, mouseY);
        sampleButton(canvas, x + 16 + buttonWidth, y + 58, buttonWidth, "Disabled", ACCENT_DISABLED, ACCENT_DISABLED, true, TEXT_PRIMARY, mouseX, mouseY);
        y += 108;
        panel(canvas, x, y, width, 110, "Text and dividers");
        UiColor[] textColors = {TEXT_PRIMARY, TEXT_SECONDARY, TEXT_MUTED, TEXT_DISABLED};
        String[] textLabels = {"Primary text", "Secondary text", "Muted text", "Disabled text"};
        for (int i = 0; i < textColors.length; i++) label(canvas, x + 8, y + 35 + i * 17, width - 16, textLabels[i], textColors[i]);
        canvas.fillRect(x + 8, y + 101, width - 16, 1, color(ACCENT_DIVIDER));
        y += 122;
        panel(canvas, x, y, width, 94, "Status and achievement colors");
        String[] statuses = {"Success", "Warning", "Danger"};
        UiColor[] backgrounds = {STATUS_SUCCESS_BACKGROUND, STATUS_WARNING_BACKGROUND, STATUS_DANGER_BACKGROUND};
        UiColor[] borders = {STATUS_SUCCESS_BORDER, STATUS_WARNING_BORDER, STATUS_DANGER_BORDER};
        float badgeWidth = (width - 32) / 3;
        for (int i = 0; i < statuses.length; i++) {
            float bx = x + 8 + i * (badgeWidth + 8);
            canvas.fillRect(bx, y + 30, badgeWidth, 20, color(backgrounds[i]));
            canvas.strokeRect(bx, y + 30, badgeWidth, 20, 1, color(borders[i]));
            label(canvas, bx + 5, y + 40, badgeWidth - 10, statuses[i], TEXT_PRIMARY);
        }
        UiColor[] tiers = {ACHIEVEMENT_BRONZE, ACHIEVEMENT_SILVER, ACHIEVEMENT_GOLD, ACHIEVEMENT_PLATINUM,
                ACHIEVEMENT_DIAMOND, ACHIEVEMENT_OBSIDIAN, ACHIEVEMENT_MYTHRIL};
        float tierWidth = (width - 16) / tiers.length;
        for (int i = 0; i < tiers.length; i++) {
            float bx = x + 8 + i * tierWidth;
            canvas.strokeRect(bx + 2, y + 62, tierWidth - 6, 18, 2, color(tiers[i]));
        }
        y += 106;
        panel(canvas, x, y, width, 110, "Map controls and markers");
        canvas.fillRect(x + 8, y + 30, width - 16, 70, color(MAP_SIDEBAR));
        float mapWidth = (width - 32) / 3;
        sampleButton(canvas, x + 12, y + 34, mapWidth, "Map", MAP_CONTROL, MAP_CONTROL_HOVER, false, MAP_TEXT, mouseX, mouseY);
        sampleButton(canvas, x + 16 + mapWidth, y + 34, mapWidth, "Active", MAP_CONTROL_ACTIVE, MAP_CONTROL_HOVER, false, MAP_TEXT, mouseX, mouseY);
        sampleButton(canvas, x + 20 + mapWidth * 2, y + 34, mapWidth, "Inactive", MAP_CONTROL_INACTIVE, MAP_CONTROL_HOVER, false, MAP_TEXT, mouseX, mouseY);
        UiColor[] markers = {MAP_PLAYER, MAP_TERRITORY, MAP_SELECTED_TERRITORY, MAP_WORLD_EVENT, MAP_TRACKED_WORLD_EVENT, MAP_TOTEM};
        for (int i = 0; i < markers.length; i++) canvas.fillRect(x + 16 + i * (width - 40) / markers.length, y + 74, 12, 12, color(markers[i]));
    }

    private static void panel(UiCanvas canvas, float x, float y, float width, float height, String title) {
        canvas.fillRect(x, y, width, height, color(BACKGROUND_CONTENT));
        label(canvas, x + 8, y + 14, width - 16, title, ACCENT_PRIMARY_HOVER);
        canvas.fillRect(x + 8, y + 25, width - 16, 1, color(ACCENT_PRIMARY_DARK));
    }

    private static void label(UiCanvas canvas, float x, float y, float width, String text, UiColor tint) {
        DropdownMenu.label(canvas, x, y, width, text, color(tint));
    }

    private static void sampleButton(UiCanvas canvas, float x, float y, float width, String text,
            UiColor normal, UiColor hover, boolean disabled, UiColor textColor, float mouseX, float mouseY) {
        canvas.fillRect(x, y, width, 20, color(!disabled && DropdownMenu.contains(mouseX, mouseY, x, y, width, 20) ? hover : normal));
        label(canvas, x + 8, y + 10, width - 16, text, disabled ? TEXT_DISABLED : textColor);
    }

    private float controlsHeight() {
        return 38 + (float) widgets.stream().mapToDouble(SettingWidget::getHeight).sum();
    }

    Layout layout(float width, float height) {
        float x = SequoiaSidebarNavigation.WIDTH + 8;
        float available = Math.max(1, width - x - 12);
        boolean twoColumns = available >= 660;
        float columnWidth = twoColumns ? (available - GAP) / 2 : available;
        float contentHeight = twoColumns ? Math.max(controlsHeight(), 446) : controlsHeight() + GAP + 446;
        return new Layout(x, available, columnWidth, twoColumns, Math.max(0, height - TOP - 8), contentHeight);
    }

    boolean mouseClicked(float mouseX, float mouseY, int button) {
        SelectionWidget<?> dropdown = openDropdown();
        if (dropdown != null) return dropdown.mouseClicked(mouseX, mouseY, button);
        if (visibleLayout == null || !DropdownMenu.contains(mouseX, mouseY, visibleLayout.x(), TOP,
                visibleLayout.width(), visibleLayout.viewportHeight())) return false;
        for (SettingWidget<?> widget : widgets) {
            // The widget owns its exact hit area; blur other samples before focusing it.
            if (widget.mouseClicked(mouseX, mouseY, button)) {
                if (focused != null && focused != widget) focused.onHidden();
                focused = widget;
                return true;
            }
        }
        if (focused != null) focused.onHidden();
        focused = null;
        return false;
    }

    boolean mouseDragged(float x, float y) { return focused != null && focused.mouseDragged(x, y); }
    void mouseReleased(float x, float y, int button) { if (focused != null) focused.mouseReleased(x, y, button); }
    boolean keyPressed(KeyEvent event) { return focused != null && focused.keyPressed(event); }
    boolean charTyped(CharacterEvent event) { return focused != null && focused.charTyped(event); }

    void scroll(double amount) {
        SelectionWidget<?> dropdown = openDropdown();
        if (dropdown != null) { dropdown.scroll(amount); return; }
        onHidden();
        scroll = Math.max(0, Math.min(maxScroll, scroll - (float) amount * 24));
    }

    void onHidden() {
        widgets.forEach(SettingWidget::onHidden);
        focused = null;
    }

    private SelectionWidget<?> openDropdown() {
        for (SettingWidget<?> widget : widgets) if (widget instanceof SelectionWidget<?> selection && selection.isOpen()) return selection;
        return null;
    }

    record Layout(float x, float width, float columnWidth, boolean twoColumns, float viewportHeight, float contentHeight) {}
}
