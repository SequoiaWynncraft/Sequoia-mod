package com.seqwawa.seq.ui;

import static com.seqwawa.seq.managers.ThemeManager.color;
import static com.seqwawa.seq.ui.theme.UiColor.*;

import java.awt.Color;
import com.seqwawa.seq.LightRoomTnaRange.LightRoom;
import com.seqwawa.seq.halcyon.HalcyonRingRenderer;
import com.seqwawa.seq.managers.PrincessMode;
import com.seqwawa.seq.radiance.PingRenderer;
import com.seqwawa.seq.ui.widget.BooleanWidget;
import com.seqwawa.seq.ui.widget.ChoiceWidget;
import com.seqwawa.seq.ui.widget.ColorWidget;
import com.seqwawa.seq.ui.widget.EnumWidget;
import com.seqwawa.seq.ui.widget.SettingWidget;
import com.seqwawa.seq.ui.widget.SliderWidget;
import com.seqwawa.seq.ui.widget.StringWidget;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;
import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.config.Setting;
import com.seqwawa.seq.utils.TextInputHelper;
import com.seqwawa.seq.utils.rendering.MinecraftUiRenderer;
import com.seqwawa.seq.utils.rendering.UiCanvas;
import com.seqwawa.seq.utils.rendering.UiRenderer;

import java.util.*;
import java.util.List;

public class SettingsScreen extends Screen {
    // Layout
    private static final float SIDEBAR_WIDTH = 140;
    private static final float SIDEBAR_PADDING = 10;
    private static final float SIDEBAR_BUTTON_HEIGHT = 22;
    private static final float SIDEBAR_BUTTON_SPACING = 6;
    private static final float HEADER_HEIGHT = 30;
    private static final float CATEGORY_HEIGHT = 28;
    private static final float SECTION_HEIGHT = 22;
    private static final float CATEGORY_SPACING = 6;
    private static final float PADDING = 8;
    private static final float SEARCH_BAR_HEIGHT = 18;
    private static final float SEARCH_BAR_WIDTH = 180;
    private static final float SEARCH_BAR_MARGIN = 8;
    private static final float THEME_EDITOR_BUTTON_WIDTH = 94;
    private static final float PRINCESS_PROMPT_HEIGHT = 24;

    // Font sizes
    private static final float TITLE_FONT_SIZE = 18;
    private static final float SIDEBAR_TITLE_SIZE = 16;
    private static final float SIDEBAR_BUTTON_SIZE = 12;
    private static final float CATEGORY_FONT_SIZE = 14;
    private static final float SECTION_FONT_SIZE = 11;
    private static final float SEARCH_FONT_SIZE = 12;
    private static final float SCROLL_SPEED = 12;

    private final Screen parent;
    private final LinkedHashMap<String, List<SettingWidget<?>>> categories = new LinkedHashMap<>();
    private final Set<String> collapsedCategories = new HashSet<>();
    private float scrollOffset = 0;
    private float maxScroll = 0;
    private float nvgMouseX, nvgMouseY;
    private boolean scrollbarDragging = false;
    private float scrollbarDragStart = 0;
    private float scrollOffsetDragStart = 0;

    // Search
    private boolean searchFocused = false;
    private String searchQuery = "";
    private int searchCursorBlink = 0;
    private final PrincessSidebarPrompt princessPrompt =
            new PrincessSidebarPrompt(new Random(), System.currentTimeMillis());

    public SettingsScreen(Screen parent) {
        super(Component.literal("Settings"));
        this.parent = parent;
        buildWidgets();
    }

    @Override
    public void removed() {
        deactivateColorPreviews();
        LightRoom.setColorPreviewActive(false);
        HalcyonRingRenderer.setColorPreviewActive(false);
        PingRenderer.setColorPreviewActive(false);
        SeqClient.getConfigManager().save();
        super.removed();
    }

    private void buildWidgets() {
        Map<String, List<SettingWidget<?>>> temp = new LinkedHashMap<>();

        for (Setting<?> setting : SeqClient.getConfigManager().getSettings()) {
            String category = setting.getCategory();
            SettingWidget<?> widget = createWidget(setting);
            if (widget != null) {
                temp.computeIfAbsent(category, k -> new ArrayList<>()).add(widget);
            }
        }

        categories.clear();
        categories.putAll(temp);
        collapsedCategories.clear();
        collapsedCategories.addAll(categories.keySet());
    }

    private SettingWidget<?> createWidget(Setting<?> setting) {
        if (setting instanceof Setting.BooleanSetting b)
            return new BooleanWidget(b);
        if (setting instanceof Setting.ColorSetting c)
            return createColorWidget(c);
        if (setting instanceof Setting.IntSetting i)
            return new SliderWidget(i, i == SeqClient.getUiSizePercentSetting());
        if (setting instanceof Setting.DoubleSetting d)
            return new SliderWidget(d);
        if (setting instanceof Setting.FloatSetting f)
            return new SliderWidget(f);
        if (setting instanceof Setting.ChoiceSetting c)
            return new ChoiceWidget(c);
        if (setting instanceof Setting.EnumSetting<?> e)
            return new EnumWidget(e);
        if (setting instanceof Setting.StringSetting s)
            return new StringWidget(s);
        return null;
    }

    private ColorWidget createColorWidget(Setting.ColorSetting setting) {
        if (setting == SeqClient.getLightRoomRingColorSetting()) {
            return new ColorWidget(setting, LightRoom::setColorPreviewActive);
        }
        if (setting == SeqClient.getHalcyonRingColorSetting()) {
            return new ColorWidget(setting, HalcyonRingRenderer::setColorPreviewActive);
        }
        if (setting == SeqClient.getRadianceMarkerColorSetting()) {
            return new ColorWidget(setting, PingRenderer::setColorPreviewActive);
        }
        return new ColorWidget(setting, null);
    }

    private void deactivateColorPreviews() {
        for (List<SettingWidget<?>> widgets : categories.values()) {
            for (SettingWidget<?> widget : widgets) {
                if (widget instanceof ColorWidget colorWidget) {
                    colorWidget.deactivatePreview();
                }
            }
        }
    }

    private boolean matchesSearch(Setting<?> setting, String categoryName) {
        if (searchQuery.isEmpty())
            return true;
        String query = searchQuery.toLowerCase();
        String settingName = setting.getName();
        String displaySettingName = setting.getDisplayName() == null
                ? SettingWidget.toDisplayName(settingName).toLowerCase()
                : setting.getDisplayName().toLowerCase();
        String displayCategoryName = SettingWidget.toDisplayName(categoryName).toLowerCase();
        return settingName.toLowerCase().contains(query)
                || categoryName.toLowerCase().contains(query)
                || displaySettingName.contains(query)
                || displayCategoryName.contains(query)
                || containsIgnoreCase(setting.getDescription(), query)
                || containsIgnoreCase(setting.getSection(), query);
    }

    private static boolean containsIgnoreCase(String value, String lowercaseQuery) {
        return value != null && value.toLowerCase().contains(lowercaseQuery);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (!princessPromptAllowed() && PrincessMode.isEnabled()) {
            PrincessMode.setEnabled(false);
        }
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        nvgMouseX = MinecraftUiRenderer.mouseX(mouseX);
        nvgMouseY = MinecraftUiRenderer.mouseY(mouseY);

        UiRenderer.renderScreen(this, canvas -> {
            float screenWidth = canvas.metrics().width();
            float screenHeight = canvas.metrics().height();
            String fontName = SeqClient.getFontManager().getSelectedFont();

            // Fill entire screen
            canvas.fillRect(0, 0, screenWidth, screenHeight, color(BACKGROUND_OVERLAY));

            // === Left Sidebar (full height) ===
            canvas.fillRect(0, 0, SIDEBAR_WIDTH, screenHeight, color(BACKGROUND_SIDEBAR));

            // Sidebar title
            drawText(canvas, fontName, SIDEBAR_TITLE_SIZE, color(ACCENT_PRIMARY), UiCanvas.HorizontalAlign.CENTER,
                    SIDEBAR_WIDTH / 2f, 22, "Sequoia");

            // Divider under title
            canvas.fillRect(SIDEBAR_PADDING, 40, SIDEBAR_WIDTH - SIDEBAR_PADDING * 2, 1, color(ACCENT_DIVIDER));

            // Sidebar buttons
            float btnX = SIDEBAR_PADDING;
            float btnW = SIDEBAR_WIDTH - SIDEBAR_PADDING * 2;
            float btnStartY = 50;

            float step = SIDEBAR_BUTTON_HEIGHT + SIDEBAR_BUTTON_SPACING;
            var destinations = SequoiaSidebarNavigation.destinations();
            for (int row = 0; row < destinations.size(); row++) {
                var destination = destinations.get(row);
                drawSidebarButton(
                        canvas,
                        fontName,
                        btnX,
                        btnStartY + step * row,
                        btnW,
                        destination.label(),
                        destination == SequoiaSidebarNavigation.Destination.SETTINGS);
            }

            renderPrincessPrompt(canvas, fontName, screenHeight, System.currentTimeMillis());

            // === Main Content Panel (fills rest of screen) ===
            float panelX = SIDEBAR_WIDTH;
            float panelY = 0;
            float panelWidth = screenWidth - SIDEBAR_WIDTH;
            float panelHeight = screenHeight;

            canvas.fillRect(panelX, panelY, panelWidth, panelHeight, color(BACKGROUND_BODY));

            // Header bar
            canvas.fillRect(panelX, panelY, panelWidth, HEADER_HEIGHT, color(BACKGROUND_HEADER));

            // Search bar (top left of header)
            searchCursorBlink++;
            float searchX = panelX + SEARCH_BAR_MARGIN;
            float searchY = panelY + (HEADER_HEIGHT - SEARCH_BAR_HEIGHT) / 2f;

            Color searchBg = searchFocused ? color(CONTROL_INPUT_HOVER) : color(CONTROL_INPUT);
            canvas.fillRect(searchX, searchY, SEARCH_BAR_WIDTH, SEARCH_BAR_HEIGHT, searchBg);
            if (searchFocused) {
                canvas.strokeRect(searchX, searchY, SEARCH_BAR_WIDTH, SEARCH_BAR_HEIGHT, 1,
                        color(CONTROL_BORDER));
            }

            canvas.save();
            canvas.scissor(searchX, searchY, SEARCH_BAR_WIDTH, SEARCH_BAR_HEIGHT);

            if (searchQuery.isEmpty() && !searchFocused) {
                drawText(canvas, fontName, SEARCH_FONT_SIZE, color(TEXT_DISABLED), UiCanvas.HorizontalAlign.LEFT,
                        searchX + 6, searchY + SEARCH_BAR_HEIGHT / 2f, "Search...");
            } else {
                drawText(canvas, fontName, SEARCH_FONT_SIZE, color(TEXT_PRIMARY), UiCanvas.HorizontalAlign.LEFT,
                        searchX + 6, searchY + SEARCH_BAR_HEIGHT / 2f, searchQuery);
            }

            canvas.restore();

            // Draw search cursor separately
            if (searchFocused && (searchCursorBlink / 1000) % 2 == 0) {
                float textW = searchQuery.isEmpty()
                        ? 0
                        : UiRenderer.measureText(searchQuery, fontName, SEARCH_FONT_SIZE).width();
                float cursorDrawX = searchX + 6 + textW + 1;
                canvas.fillRect(cursorDrawX, searchY + 3, 1, SEARCH_BAR_HEIGHT - 6, color(TEXT_PRIMARY));
            }

            // Title (right side of header)
            float themeEditorX = searchX + SEARCH_BAR_WIDTH + SEARCH_BAR_MARGIN;
            boolean themeEditorHovered = isHovered(
                    nvgMouseX,
                    nvgMouseY,
                    themeEditorX,
                    searchY,
                    THEME_EDITOR_BUTTON_WIDTH,
                    SEARCH_BAR_HEIGHT);
            canvas.fillRect(
                    themeEditorX,
                    searchY,
                    THEME_EDITOR_BUTTON_WIDTH,
                    SEARCH_BAR_HEIGHT,
                    themeEditorHovered ? color(CONTROL_INPUT_HOVER) : color(CONTROL_INPUT_SECONDARY));
            drawText(
                    canvas,
                    fontName,
                    11,
                    color(TEXT_PRIMARY),
                    UiCanvas.HorizontalAlign.CENTER,
                    themeEditorX + THEME_EDITOR_BUTTON_WIDTH / 2f,
                    searchY + SEARCH_BAR_HEIGHT / 2f,
                    "Theme editor");

            // Title (right side of header)
            drawText(canvas, fontName, TITLE_FONT_SIZE, color(ACCENT_PRIMARY), UiCanvas.HorizontalAlign.RIGHT,
                    panelX + panelWidth - SEARCH_BAR_MARGIN, panelY + HEADER_HEIGHT / 2f, "Settings");

            // Content area with scissor
            float contentX = panelX;
            float contentY = panelY + HEADER_HEIGHT;
            float contentWidth = panelWidth;
            float contentHeight = panelHeight - HEADER_HEIGHT;

            canvas.save();
            canvas.scissor(contentX, contentY, contentWidth, contentHeight);

            float cursorY = contentY - scrollOffset + PADDING;
            float widgetWidth = contentWidth - PADDING * 2 - 6;

            int settingIndex = 0;
            for (Map.Entry<String, List<SettingWidget<?>>> entry : categories.entrySet()) {
                String category = entry.getKey();
                List<SettingWidget<?>> widgets = visibleWidgets(entry.getValue());
                if (widgets.isEmpty()) {
                    continue;
                }
                boolean collapsed = isCategoryCollapsed(category);

                // Filter widgets by search
                List<SettingWidget<?>> filtered = widgets;
                if (!searchQuery.isEmpty()) {
                    filtered = new ArrayList<>();
                    for (SettingWidget<?> w : widgets) {
                        if (matchesSearch(w.getSetting(), category)) {
                            filtered.add(w);
                        }
                    }
                    if (filtered.isEmpty())
                        continue;
                }

                // Category header
                boolean catHovered = isHovered(nvgMouseX, nvgMouseY, contentX, cursorY, contentWidth, CATEGORY_HEIGHT)
                        && nvgMouseY >= contentY && nvgMouseY <= contentY + contentHeight;
                canvas.fillRect(contentX, cursorY, contentWidth, CATEGORY_HEIGHT,
                        catHovered ? color(BACKGROUND_CONTENT_FOCUSED) : color(BACKGROUND_CONTENT));

                // Arrow
                drawText(canvas, fontName, 12, color(ACCENT_SECONDARY), UiCanvas.HorizontalAlign.CENTER,
                        contentX + PADDING + 14, cursorY + CATEGORY_HEIGHT / 2f, collapsed ? "+" : "-");

                // Category name
                String displayName = SettingWidget.toDisplayName(category);
                drawText(canvas, fontName, CATEGORY_FONT_SIZE, color(TEXT_MUTED), UiCanvas.HorizontalAlign.LEFT,
                        contentX + PADDING + 26, cursorY + CATEGORY_HEIGHT / 2f, displayName);

                cursorY += CATEGORY_HEIGHT;

                // Settings under this category
                if (!collapsed) {
                    String currentSection = null;
                    for (SettingWidget<?> widget : filtered) {
                        String section = widget.getSetting().getSection();
                        if (section != null && !section.equals(currentSection)) {
                            canvas.fillRect(contentX, cursorY, contentWidth, SECTION_HEIGHT, color(BACKGROUND_CONTENT, 170));
                            drawText(
                                    canvas,
                                    fontName,
                                    SECTION_FONT_SIZE,
                                    color(ACCENT_SECONDARY),
                                    UiCanvas.HorizontalAlign.LEFT,
                                    contentX + PADDING + 8,
                                    cursorY + SECTION_HEIGHT / 2f,
                                    section);
                            cursorY += SECTION_HEIGHT;
                        }
                        currentSection = section;
                        Color bg = (settingIndex % 2 == 0) ? color(BACKGROUND_BODY) : color(BACKGROUND_CONTENT_FOCUSED, 100);
                        canvas.fillRect(contentX, cursorY, contentWidth, widget.getHeight(), bg);

                        widget.setPosition(contentX + PADDING, cursorY, widgetWidth, widget.getHeight());
                        widget.render(canvas, nvgMouseX, nvgMouseY);
                        cursorY += widget.getHeight();
                        settingIndex++;
                    }
                }

                cursorY += CATEGORY_SPACING;
            }

            maxScroll = Math.max(0, cursorY + scrollOffset - contentY - contentHeight);

            canvas.restore();

            // Scrollbar
            if (maxScroll > 0) {
                float scrollbarX = panelX + panelWidth - 5;
                float scrollbarHeight = contentHeight;
                canvas.fillRect(scrollbarX, contentY, 4, scrollbarHeight, color(CONTROL_TRACK));

                float thumbRatio = contentHeight / (contentHeight + maxScroll);
                float thumbHeight = Math.max(20, scrollbarHeight * thumbRatio);
                float thumbY = contentY + (scrollOffset / maxScroll) * (scrollbarHeight - thumbHeight);
                canvas.fillRect(scrollbarX, thumbY, 4, thumbHeight, color(CONTROL_THUMB));
            }
        });
    }

    private void drawSidebarButton(
            UiCanvas canvas, String fontName, float x, float y, float w, String label, boolean active) {
        boolean hovered = isHovered(nvgMouseX, nvgMouseY, x, y, w, SIDEBAR_BUTTON_HEIGHT);

        Color bgColor = active ? color(ACCENT_PRIMARY_DARK_HOVER, 120) : (hovered ? color(BACKGROUND_CONTENT_FOCUSED) : color(BACKGROUND_CONTENT));
        canvas.fillRect(x, y, w, SIDEBAR_BUTTON_HEIGHT, bgColor);
        drawText(canvas, fontName, SIDEBAR_BUTTON_SIZE, color(TEXT_PRIMARY), UiCanvas.HorizontalAlign.CENTER,
                x + w / 2f, y + SIDEBAR_BUTTON_HEIGHT / 2f, label);
    }

    private void renderPrincessPrompt(UiCanvas canvas, String fontName, float screenHeight, long nowMs) {
        if (!princessPromptAllowed()) {
            return;
        }

        float progress = princessPrompt.slideProgress(nowMs);
        if (progress <= 0f) {
            return;
        }

        float x = SIDEBAR_PADDING;
        float width = SIDEBAR_WIDTH - SIDEBAR_PADDING * 2;
        float y = princessPromptY(screenHeight, progress);
        boolean hovered = isHovered(nvgMouseX, nvgMouseY, x, y, width, PRINCESS_PROMPT_HEIGHT);
        canvas.fillRect(
                x,
                y,
                width,
                PRINCESS_PROMPT_HEIGHT,
                hovered ? color(CONTROL_INPUT_HOVER) : color(BACKGROUND_CONTENT));

        float checkboxSize = 12;
        float checkboxX = x + 7;
        float checkboxY = y + (PRINCESS_PROMPT_HEIGHT - checkboxSize) / 2f;
        canvas.fillRect(
                checkboxX,
                checkboxY,
                checkboxSize,
                checkboxSize,
                PrincessMode.isEnabled() ? color(ACCENT_PRIMARY) : color(CONTROL_INPUT_SECONDARY));
        if (PrincessMode.isEnabled()) {
            drawText(
                    canvas,
                    fontName,
                    10,
                    color(TEXT_PRIMARY),
                    UiCanvas.HorizontalAlign.CENTER,
                    checkboxX + checkboxSize / 2f,
                    checkboxY + checkboxSize / 2f,
                    "✓");
        }
        drawText(
                canvas,
                fontName,
                11,
                color(TEXT_PRIMARY),
                UiCanvas.HorizontalAlign.LEFT,
                checkboxX + checkboxSize + 7,
                y + PRINCESS_PROMPT_HEIGHT / 2f,
                "Princess mode");
    }

    private static float princessPromptY(float screenHeight, float progress) {
        float visibleY = screenHeight - SIDEBAR_PADDING - PRINCESS_PROMPT_HEIGHT;
        return screenHeight + (visibleY - screenHeight) * progress;
    }

    private static boolean princessPromptAllowed() {
        return SeqClient.getEasterEggsSetting() != null && SeqClient.getEasterEggsSetting().getValue();
    }

    private static void drawText(
            UiCanvas canvas,
            String font,
            float size,
            Color color,
            UiCanvas.HorizontalAlign horizontalAlign,
            float x,
            float y,
            String text) {
        canvas.drawText(text, x, y, new UiCanvas.TextStyle(
                font, size, color, horizontalAlign, UiCanvas.VerticalAlign.MIDDLE));
    }

    @Override
    public boolean mouseClicked(@NotNull MouseButtonEvent click, boolean outsideScreen) {
        if (click.button() != 0 && click.button() != 1) {
            return super.mouseClicked(click, outsideScreen);
        }

        float mx = MinecraftUiRenderer.mouseX(click.x());
        float my = MinecraftUiRenderer.mouseY(click.y());

        float screenWidth = MinecraftUiRenderer.screenWidth();
        float screenHeight = MinecraftUiRenderer.screenHeight();
        float panelX = SIDEBAR_WIDTH;
        float panelWidth = screenWidth - SIDEBAR_WIDTH;
        float panelHeight = screenHeight;
        float contentY = HEADER_HEIGHT;
        float contentHeight = panelHeight - HEADER_HEIGHT;

        if (click.button() == 0) {

            // Sidebar button clicks
            float btnX = SIDEBAR_PADDING;
            float btnW = SIDEBAR_WIDTH - SIDEBAR_PADDING * 2;
            float btnStartY = 50;

            if (princessPromptAllowed()) {
                long nowMs = System.currentTimeMillis();
                float progress = princessPrompt.slideProgress(nowMs);
                float promptY = princessPromptY(screenHeight, progress);
                if (progress > 0f && isHovered(mx, my, btnX, promptY, btnW, PRINCESS_PROMPT_HEIGHT)) {
                    PrincessMode.toggle();
                    return true;
                }
            }

            float step = SIDEBAR_BUTTON_HEIGHT + SIDEBAR_BUTTON_SPACING;
            var destinations = SequoiaSidebarNavigation.destinations();
            for (int row = 0; row < destinations.size(); row++) {
                if (!isHovered(mx, my, btnX, btnStartY + step * row, btnW, SIDEBAR_BUTTON_HEIGHT)) {
                    continue;
                }
                var destination = destinations.get(row);
                if (destination != SequoiaSidebarNavigation.Destination.SETTINGS) {
                    SequoiaSidebarNavigation.open(destination, this);
                }
                return true;
            }

            // Search bar click
            float searchX = panelX + SEARCH_BAR_MARGIN;
            float searchY = (HEADER_HEIGHT - SEARCH_BAR_HEIGHT) / 2f;

            if (isHovered(mx, my, searchX, searchY, SEARCH_BAR_WIDTH, SEARCH_BAR_HEIGHT)) {
                searchFocused = true;
                searchCursorBlink = 0;
                return true;
            } else if (searchFocused) {
                searchFocused = false;
            }

            float themeEditorX = searchX + SEARCH_BAR_WIDTH + SEARCH_BAR_MARGIN;
            if (isHovered(mx, my, themeEditorX, searchY, THEME_EDITOR_BUTTON_WIDTH, SEARCH_BAR_HEIGHT)) {
                SeqClient.mc.setScreen(new ThemeEditorScreen(this));
                return true;
            }

            // Main panel calculations
            // Scrollbar drag
            if (maxScroll > 0) {
                float scrollbarX = panelX + panelWidth - 5;
                if (isHovered(mx, my, scrollbarX - 2, contentY, 8, contentHeight)) {
                    scrollbarDragging = true;
                    scrollbarDragStart = my;
                    scrollOffsetDragStart = scrollOffset;
                    return true;
                }
            }
        }

        // Only process clicks in content area
        if (mx < panelX || my < contentY || my > contentY + contentHeight) {
            return super.mouseClicked(click, outsideScreen);
        }

        // Category headers and widgets
        float contentWidth = panelWidth;
        float widgetWidth = contentWidth - PADDING * 2 - 6;
        float cursorY = contentY - scrollOffset + PADDING;

        for (Map.Entry<String, List<SettingWidget<?>>> entry : categories.entrySet()) {
            String category = entry.getKey();
            List<SettingWidget<?>> widgets = visibleWidgets(entry.getValue());
            if (widgets.isEmpty()) {
                continue;
            }
            boolean collapsed = isCategoryCollapsed(category);

            // Filter widgets by search
            List<SettingWidget<?>> filtered = widgets;
            if (!searchQuery.isEmpty()) {
                filtered = new ArrayList<>();
                for (SettingWidget<?> w : widgets) {
                    if (matchesSearch(w.getSetting(), category)) {
                        filtered.add(w);
                    }
                }
                if (filtered.isEmpty())
                    continue;
            }

            // Category header click
            if (isHovered(mx, my, panelX, cursorY, contentWidth, CATEGORY_HEIGHT)) {
                if (click.button() == 0) {
                    if (collapsed) {
                        collapsedCategories.remove(category);
                    } else {
                        collapsedCategories.add(category);
                    }
                    return true;
                }
                return super.mouseClicked(click, outsideScreen);
            }
            cursorY += CATEGORY_HEIGHT;

            if (!collapsed) {
                String currentSection = null;
                for (SettingWidget<?> widget : filtered) {
                    String section = widget.getSetting().getSection();
                    if (section != null && !section.equals(currentSection)) {
                        cursorY += SECTION_HEIGHT;
                    }
                    currentSection = section;
                    widget.setPosition(panelX + PADDING, cursorY, widgetWidth, widget.getHeight());
                    if (widget.mouseClicked(mx, my, click.button())) {
                        return true;
                    }
                    cursorY += widget.getHeight();
                }
            }

            cursorY += CATEGORY_SPACING;
        }
        return super.mouseClicked(click, outsideScreen);
    }

    @Override
    public boolean mouseReleased(@NotNull MouseButtonEvent click) {
        scrollbarDragging = false;
        float mx = MinecraftUiRenderer.mouseX(click.x());
        float my = MinecraftUiRenderer.mouseY(click.y());

        for (List<SettingWidget<?>> widgets : categories.values()) {
            for (SettingWidget<?> widget : widgets) {
                widget.mouseReleased(mx, my, click.button());
            }
        }
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent click, double deltaX, double deltaY) {
        float mx = MinecraftUiRenderer.mouseX(click.x());
        float my = MinecraftUiRenderer.mouseY(click.y());

        if (scrollbarDragging && maxScroll > 0) {
            float screenHeight = MinecraftUiRenderer.screenHeight();
            float contentHeight = screenHeight - HEADER_HEIGHT;
            float thumbRatio = contentHeight / (contentHeight + maxScroll);
            float thumbHeight = Math.max(20, contentHeight * thumbRatio);
            float scrollRange = contentHeight - thumbHeight;

            float delta = my - scrollbarDragStart;
            scrollOffset = scrollOffsetDragStart + (delta / scrollRange) * maxScroll;
            scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset));
            return true;
        }

        for (List<SettingWidget<?>> widgets : categories.values()) {
            for (SettingWidget<?> widget : widgets) {
                if (widget.mouseDragged(mx, my))
                    return true;
            }
        }
        return super.mouseDragged(click, deltaX, deltaY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scrollOffset -= (float) scrollY * SCROLL_SPEED;
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset));
        return true;
    }

    @Override
    public boolean keyPressed(@NotNull KeyEvent keyEvent) {
        // Search bar input
        if (searchFocused) {
            int keyCode = keyEvent.key();
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                searchFocused = false;
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                if (!searchQuery.isEmpty()) {
                    searchQuery = searchQuery.substring(0, searchQuery.length() - 1);
                    scrollOffset = 0;
                }
                return true;
            }
            return true;
        }

        for (List<SettingWidget<?>> widgets : categories.values()) {
            for (SettingWidget<?> widget : widgets) {
                if (widget.keyPressed(keyEvent))
                    return true;
            }
        }
        return super.keyPressed(keyEvent);
    }

    @Override
    public boolean charTyped(@NotNull CharacterEvent characterEvent) {
        if (searchFocused) {
            String typedText = TextInputHelper.getTypedText(characterEvent);
            if (typedText != null) {
                searchQuery += typedText;
                scrollOffset = 0;
            }
            return true;
        }

        for (List<SettingWidget<?>> widgets : categories.values()) {
            for (SettingWidget<?> widget : widgets) {
                if (widget.charTyped(characterEvent)) {
                    return true;
                }
            }
        }
        return super.charTyped(characterEvent);
    }

    private boolean isHovered(float mx, float my, float bx, float by, float bw, float bh) {
        return mx >= bx && mx <= bx + bw && my >= by && my <= by + bh;
    }

    private boolean isCategoryCollapsed(String category) {
        return searchQuery.isEmpty() && collapsedCategories.contains(category);
    }

    private List<SettingWidget<?>> visibleWidgets(List<SettingWidget<?>> widgets) {
        return widgets.stream()
                .filter(widget -> widget.getSetting().isVisible())
                .toList();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

}
