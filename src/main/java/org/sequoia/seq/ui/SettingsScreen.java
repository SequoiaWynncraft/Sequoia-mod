package org.sequoia.seq.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;
import org.sequoia.seq.client.SeqClient;
import org.sequoia.seq.config.Setting;
import org.sequoia.seq.ui.widget.*;
import org.sequoia.seq.utils.rendering.nvg.NVGContext;
import org.sequoia.seq.utils.rendering.nvg.NVGWrapper;

import java.awt.*;
import java.util.*;
import java.util.List;

import static org.lwjgl.nanovg.NanoVG.*;

public class SettingsScreen extends Screen {
    // Layout
    private static final float SIDEBAR_WIDTH = 140;
    private static final float SIDEBAR_PADDING = 10;
    private static final float SIDEBAR_BUTTON_HEIGHT = 22;
    private static final float SIDEBAR_BUTTON_SPACING = 6;
    private static final float HEADER_HEIGHT = 30;
    private static final float CATEGORY_HEIGHT = 28;
    private static final float CATEGORY_SPACING = 6;
    private static final float PADDING = 8;
    private static final float SEARCH_BAR_HEIGHT = 18;
    private static final float SEARCH_BAR_WIDTH = 180;
    private static final float SEARCH_BAR_MARGIN = 8;

    // Font sizes
    private static final float TITLE_FONT_SIZE = 18;
    private static final float SIDEBAR_TITLE_SIZE = 16;
    private static final float SIDEBAR_BUTTON_SIZE = 12;
    private static final float CATEGORY_FONT_SIZE = 14;
    private static final float SEARCH_FONT_SIZE = 12;
    private static final float SCROLL_SPEED = 12;

    // Colors
    private static final Color BG_COLOR = new Color(10, 10, 16, 100);
    private static final Color SIDEBAR_COLOR = new Color(18, 18, 26, 200);
    private static final Color PANEL_COLOR = new Color(22, 22, 30, 100);
    private static final Color HEADER_COLOR = new Color(26, 26, 36, 110);
    private static final Color CATEGORY_COLOR = new Color(30, 30, 42, 110);
    private static final Color CATEGORY_HOVER = new Color(38, 38, 52, 120);
    private static final Color TITLE_COLOR = new Color(160, 130, 220, 255);
    private static final Color CATEGORY_TEXT = new Color(180, 180, 200, 255);
    private static final Color ARROW_COLOR = new Color(140, 140, 160, 255);
    private static final Color SCROLLBAR_TRACK = new Color(30, 30, 42, 255);
    private static final Color SCROLLBAR_THUMB = new Color(160, 130, 220, 150);
    private static final Color SETTING_BG = new Color(22, 22, 30, 100);
    private static final Color SETTING_BG_ALT = new Color(26, 26, 36, 100);
    private static final Color SIDEBAR_BUTTON_COLOR = new Color(30, 30, 42, 110);
    private static final Color SIDEBAR_BUTTON_HOVER = new Color(42, 42, 58, 120);
    private static final Color SIDEBAR_BUTTON_ACTIVE = new Color(80, 50, 140, 120);
    private static final Color TEXT_COLOR = new Color(255, 255, 255, 255);
    private static final Color DIVIDER_COLOR = new Color(40, 40, 55, 255);
    private static final Color SEARCH_BG = new Color(30, 30, 40, 255);
    private static final Color SEARCH_ACTIVE_BG = new Color(40, 40, 55, 255);
    private static final Color SEARCH_BORDER = new Color(130, 100, 200, 180);
    private static final Color SEARCH_PLACEHOLDER = new Color(100, 100, 120, 200);

    private static final String GITHUB_URL = "https://github.com/SequoiaWynncraft/sequoia-mod";

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

    public SettingsScreen(Screen parent) {
        super(Component.literal("Settings"));
        this.parent = parent;
        buildWidgets();
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
    }

    private SettingWidget<?> createWidget(Setting<?> setting) {
        if (setting instanceof Setting.BooleanSetting b)
            return new BooleanWidget(b);
        if (setting instanceof Setting.IntSetting i)
            return new SliderWidget(i);
        if (setting instanceof Setting.DoubleSetting d)
            return new SliderWidget(d);
        if (setting instanceof Setting.FloatSetting f)
            return new SliderWidget(f);
        if (setting instanceof Setting.EnumSetting<?> e)
            return new EnumWidget(e);
        if (setting instanceof Setting.StringSetting s)
            return new StringWidget(s);
        return null;
    }

    private boolean matchesSearch(String settingName, String categoryName) {
        if (searchQuery.isEmpty())
            return true;
        String query = searchQuery.toLowerCase();
        String displaySettingName = SettingWidget.toDisplayName(settingName).toLowerCase();
        String displayCategoryName = SettingWidget.toDisplayName(categoryName).toLowerCase();
        return settingName.toLowerCase().contains(query)
                || categoryName.toLowerCase().contains(query)
                || displaySettingName.contains(query)
                || displayCategoryName.contains(query);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        double guiScale = SeqClient.mc.getWindow().getGuiScale();
        nvgMouseX = (float) (mouseX * guiScale / 2.0);
        nvgMouseY = (float) (mouseY * guiScale / 2.0);

        NVGContext.renderDeferred(nvg -> {
            float screenWidth = SeqClient.mc.getWindow().getWidth() / 2f;
            float screenHeight = SeqClient.mc.getWindow().getHeight() / 2f;
            String fontName = SeqClient.getFontManager().getSelectedFont();

            // Fill entire screen
            NVGWrapper.drawRect(nvg, 0, 0, screenWidth, screenHeight, BG_COLOR);

            // === Left Sidebar (full height) ===
            NVGWrapper.drawRect(nvg, 0, 0, SIDEBAR_WIDTH, screenHeight, SIDEBAR_COLOR);

            // Sidebar title
            nvgFontFace(nvg, fontName);
            nvgFontSize(nvg, SIDEBAR_TITLE_SIZE);
            nvgTextAlign(nvg, NVG_ALIGN_CENTER | NVG_ALIGN_MIDDLE);
            var sTitleCol = NVGContext.nvgColor(TITLE_COLOR);
            nvgFillColor(nvg, sTitleCol);
            nvgText(nvg, SIDEBAR_WIDTH / 2f, 22, "Sequoia");
            sTitleCol.free();

            // Divider under title
            NVGWrapper.drawRect(nvg, SIDEBAR_PADDING, 40, SIDEBAR_WIDTH - SIDEBAR_PADDING * 2, 1, DIVIDER_COLOR);

            // Sidebar buttons
            float btnX = SIDEBAR_PADDING;
            float btnW = SIDEBAR_WIDTH - SIDEBAR_PADDING * 2;
            float btnStartY = 50;

            drawSidebarButton(nvg, fontName, btnX, btnStartY, btnW, "Partyfinder", false);
            drawSidebarButton(nvg, fontName, btnX, btnStartY + (SIDEBAR_BUTTON_HEIGHT + SIDEBAR_BUTTON_SPACING), btnW,
                    "Settings", true);
            drawSidebarButton(nvg, fontName, btnX, btnStartY + (SIDEBAR_BUTTON_HEIGHT + SIDEBAR_BUTTON_SPACING) * 2,
                    btnW, "Github", false);

            // === Main Content Panel (fills rest of screen) ===
            float panelX = SIDEBAR_WIDTH;
            float panelY = 0;
            float panelWidth = screenWidth - SIDEBAR_WIDTH;
            float panelHeight = screenHeight;

            NVGWrapper.drawRect(nvg, panelX, panelY, panelWidth, panelHeight, PANEL_COLOR);

            // Header bar
            NVGWrapper.drawRect(nvg, panelX, panelY, panelWidth, HEADER_HEIGHT, HEADER_COLOR);

            // Search bar (top left of header)
            searchCursorBlink++;
            float searchX = panelX + SEARCH_BAR_MARGIN;
            float searchY = panelY + (HEADER_HEIGHT - SEARCH_BAR_HEIGHT) / 2f;

            Color searchBg = searchFocused ? SEARCH_ACTIVE_BG : SEARCH_BG;
            NVGWrapper.drawRect(nvg, searchX, searchY, SEARCH_BAR_WIDTH, SEARCH_BAR_HEIGHT, searchBg);
            if (searchFocused) {
                NVGWrapper.drawRectOutline(nvg, searchX, searchY, SEARCH_BAR_WIDTH, SEARCH_BAR_HEIGHT, 1,
                        SEARCH_BORDER);
            }

            nvgFontFace(nvg, fontName);
            nvgFontSize(nvg, SEARCH_FONT_SIZE);
            nvgTextAlign(nvg, NVG_ALIGN_LEFT | NVG_ALIGN_MIDDLE);

            nvgSave(nvg);
            nvgScissor(nvg, searchX, searchY, SEARCH_BAR_WIDTH, SEARCH_BAR_HEIGHT);

            if (searchQuery.isEmpty() && !searchFocused) {
                var phCol = NVGContext.nvgColor(SEARCH_PLACEHOLDER);
                nvgFillColor(nvg, phCol);
                nvgText(nvg, searchX + 6, searchY + SEARCH_BAR_HEIGHT / 2f, "Search...");
                phCol.free();
            } else {
                var searchTextCol = NVGContext.nvgColor(TEXT_COLOR);
                nvgFillColor(nvg, searchTextCol);
                nvgText(nvg, searchX + 6, searchY + SEARCH_BAR_HEIGHT / 2f, searchQuery);
                searchTextCol.free();
            }

            nvgRestore(nvg);

            // Draw search cursor separately
            if (searchFocused && (searchCursorBlink / 1000) % 2 == 0) {
                float[] bounds = new float[4];
                nvgFontFace(nvg, fontName);
                nvgFontSize(nvg, SEARCH_FONT_SIZE);
                float textW = searchQuery.isEmpty() ? 0 : nvgTextBounds(nvg, 0, 0, searchQuery, bounds);
                float cursorDrawX = searchX + 6 + textW + 1;
                NVGWrapper.drawRect(nvg, cursorDrawX, searchY + 3, 1, SEARCH_BAR_HEIGHT - 6, TEXT_COLOR);
            }

            // Title (right side of header)
            nvgFontFace(nvg, fontName);
            nvgFontSize(nvg, TITLE_FONT_SIZE);
            nvgTextAlign(nvg, NVG_ALIGN_RIGHT | NVG_ALIGN_MIDDLE);
            var titleCol = NVGContext.nvgColor(TITLE_COLOR);
            nvgFillColor(nvg, titleCol);
            nvgText(nvg, panelX + panelWidth - SEARCH_BAR_MARGIN, panelY + HEADER_HEIGHT / 2f, "Settings");
            titleCol.free();

            // Content area with scissor
            float contentX = panelX;
            float contentY = panelY + HEADER_HEIGHT;
            float contentWidth = panelWidth;
            float contentHeight = panelHeight - HEADER_HEIGHT;

            nvgSave(nvg);
            nvgScissor(nvg, contentX, contentY, contentWidth, contentHeight);

            float cursorY = contentY - scrollOffset + PADDING;
            float widgetWidth = contentWidth - PADDING * 2 - 6;

            int settingIndex = 0;
            for (Map.Entry<String, List<SettingWidget<?>>> entry : categories.entrySet()) {
                String category = entry.getKey();
                List<SettingWidget<?>> widgets = entry.getValue();
                boolean collapsed = collapsedCategories.contains(category);

                // Filter widgets by search
                List<SettingWidget<?>> filtered = widgets;
                if (!searchQuery.isEmpty()) {
                    filtered = new ArrayList<>();
                    for (SettingWidget<?> w : widgets) {
                        if (matchesSearch(w.getSetting().getName(), category)) {
                            filtered.add(w);
                        }
                    }
                    if (filtered.isEmpty())
                        continue;
                }

                // Category header
                boolean catHovered = isHovered(nvgMouseX, nvgMouseY, contentX, cursorY, contentWidth, CATEGORY_HEIGHT)
                        && nvgMouseY >= contentY && nvgMouseY <= contentY + contentHeight;
                NVGWrapper.drawRect(nvg, contentX, cursorY, contentWidth, CATEGORY_HEIGHT,
                        catHovered ? CATEGORY_HOVER : CATEGORY_COLOR);

                // Arrow
                nvgFontFace(nvg, fontName);
                nvgFontSize(nvg, 12);
                nvgTextAlign(nvg, NVG_ALIGN_CENTER | NVG_ALIGN_MIDDLE);
                var catArrowCol = NVGContext.nvgColor(ARROW_COLOR);
                nvgFillColor(nvg, catArrowCol);
                nvgText(nvg, contentX + PADDING + 14, cursorY + CATEGORY_HEIGHT / 2f, collapsed ? "+" : "-");
                catArrowCol.free();

                // Category name
                nvgFontFace(nvg, fontName);
                nvgFontSize(nvg, CATEGORY_FONT_SIZE);
                nvgTextAlign(nvg, NVG_ALIGN_LEFT | NVG_ALIGN_MIDDLE);
                var catTextCol = NVGContext.nvgColor(CATEGORY_TEXT);
                nvgFillColor(nvg, catTextCol);
                String displayName = SettingWidget.toDisplayName(category);
                nvgText(nvg, contentX + PADDING + 26, cursorY + CATEGORY_HEIGHT / 2f, displayName);
                catTextCol.free();

                cursorY += CATEGORY_HEIGHT;

                // Settings under this category
                if (!collapsed) {
                    for (SettingWidget<?> widget : filtered) {
                        Color bg = (settingIndex % 2 == 0) ? SETTING_BG : SETTING_BG_ALT;
                        NVGWrapper.drawRect(nvg, contentX, cursorY, contentWidth, widget.getHeight(), bg);

                        widget.setPosition(contentX + PADDING, cursorY, widgetWidth, widget.getHeight());
                        widget.render(nvg, nvgMouseX, nvgMouseY);
                        cursorY += widget.getHeight();
                        settingIndex++;
                    }
                }

                cursorY += CATEGORY_SPACING;
            }

            maxScroll = Math.max(0, cursorY + scrollOffset - contentY - contentHeight);

            nvgRestore(nvg);

            // Scrollbar
            if (maxScroll > 0) {
                float scrollbarX = panelX + panelWidth - 5;
                float scrollbarHeight = contentHeight;
                NVGWrapper.drawRect(nvg, scrollbarX, contentY, 4, scrollbarHeight, SCROLLBAR_TRACK);

                float thumbRatio = contentHeight / (contentHeight + maxScroll);
                float thumbHeight = Math.max(20, scrollbarHeight * thumbRatio);
                float thumbY = contentY + (scrollOffset / maxScroll) * (scrollbarHeight - thumbHeight);
                NVGWrapper.drawRect(nvg, scrollbarX, thumbY, 4, thumbHeight, SCROLLBAR_THUMB);
            }
        });
    }

    private void drawSidebarButton(long nvg, String fontName, float x, float y, float w, String label, boolean active) {
        boolean hovered = isHovered(nvgMouseX, nvgMouseY, x, y, w, SIDEBAR_BUTTON_HEIGHT);

        Color bgColor = active ? SIDEBAR_BUTTON_ACTIVE : (hovered ? SIDEBAR_BUTTON_HOVER : SIDEBAR_BUTTON_COLOR);
        NVGWrapper.drawRect(nvg, x, y, w, SIDEBAR_BUTTON_HEIGHT, bgColor);

        nvgFontFace(nvg, fontName);
        nvgFontSize(nvg, SIDEBAR_BUTTON_SIZE);
        nvgTextAlign(nvg, NVG_ALIGN_CENTER | NVG_ALIGN_MIDDLE);
        var textCol = NVGContext.nvgColor(TEXT_COLOR);
        nvgFillColor(nvg, textCol);
        nvgText(nvg, x + w / 2f, y + SIDEBAR_BUTTON_HEIGHT / 2f, label);
        textCol.free();
    }

    @Override
    public boolean mouseClicked(@NotNull MouseButtonEvent click, boolean outsideScreen) {
        if (click.button() == 0) {
            double guiScale = SeqClient.mc.getWindow().getGuiScale();
            float mx = (float) (click.x() * guiScale / 2.0);
            float my = (float) (click.y() * guiScale / 2.0);

            float screenWidth = SeqClient.mc.getWindow().getWidth() / 2f;
            float screenHeight = SeqClient.mc.getWindow().getHeight() / 2f;

            // Sidebar button clicks
            float btnX = SIDEBAR_PADDING;
            float btnW = SIDEBAR_WIDTH - SIDEBAR_PADDING * 2;
            float btnStartY = 50;

            // Partyfinder
            if (isHovered(mx, my, btnX, btnStartY, btnW, SIDEBAR_BUTTON_HEIGHT)) {
                SeqClient.mc.setScreen(new PartyFinderScreen(this));
                return true;
            }
            // Settings (already here)
            if (isHovered(mx, my, btnX, btnStartY + (SIDEBAR_BUTTON_HEIGHT + SIDEBAR_BUTTON_SPACING), btnW,
                    SIDEBAR_BUTTON_HEIGHT)) {
                return true;
            }
            // Github
            if (isHovered(mx, my, btnX, btnStartY + (SIDEBAR_BUTTON_HEIGHT + SIDEBAR_BUTTON_SPACING) * 2, btnW,
                    SIDEBAR_BUTTON_HEIGHT)) {
                try {
                    java.net.URI uri = java.net.URI.create(GITHUB_URL);
                    java.awt.Desktop.getDesktop().browse(uri);
                } catch (Exception ignored) {
                }
                return true;
            }

            // Search bar click
            float panelX = SIDEBAR_WIDTH;
            float searchX = panelX + SEARCH_BAR_MARGIN;
            float searchY = (HEADER_HEIGHT - SEARCH_BAR_HEIGHT) / 2f;

            if (isHovered(mx, my, searchX, searchY, SEARCH_BAR_WIDTH, SEARCH_BAR_HEIGHT)) {
                searchFocused = true;
                searchCursorBlink = 0;
                return true;
            } else if (searchFocused) {
                searchFocused = false;
            }

            // Main panel calculations
            float panelWidth = screenWidth - SIDEBAR_WIDTH;
            float panelHeight = screenHeight;
            float contentY = HEADER_HEIGHT;
            float contentHeight = panelHeight - HEADER_HEIGHT;

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
                List<SettingWidget<?>> widgets = entry.getValue();
                boolean collapsed = collapsedCategories.contains(category);

                // Filter widgets by search
                List<SettingWidget<?>> filtered = widgets;
                if (!searchQuery.isEmpty()) {
                    filtered = new ArrayList<>();
                    for (SettingWidget<?> w : widgets) {
                        if (matchesSearch(w.getSetting().getName(), category)) {
                            filtered.add(w);
                        }
                    }
                    if (filtered.isEmpty())
                        continue;
                }

                // Category header click
                if (isHovered(mx, my, panelX, cursorY, contentWidth, CATEGORY_HEIGHT)) {
                    if (collapsed) {
                        collapsedCategories.remove(category);
                    } else {
                        collapsedCategories.add(category);
                    }
                    return true;
                }
                cursorY += CATEGORY_HEIGHT;

                if (!collapsed) {
                    for (SettingWidget<?> widget : filtered) {
                        widget.setPosition(panelX + PADDING, cursorY, widgetWidth, widget.getHeight());
                        if (widget.mouseClicked(mx, my, click.button())) {
                            return true;
                        }
                        cursorY += widget.getHeight();
                    }
                }

                cursorY += CATEGORY_SPACING;
            }
        }
        return super.mouseClicked(click, outsideScreen);
    }

    @Override
    public boolean mouseReleased(@NotNull MouseButtonEvent click) {
        scrollbarDragging = false;
        double guiScale = SeqClient.mc.getWindow().getGuiScale();
        float mx = (float) (click.x() * guiScale / 2.0);
        float my = (float) (click.y() * guiScale / 2.0);

        for (List<SettingWidget<?>> widgets : categories.values()) {
            for (SettingWidget<?> widget : widgets) {
                widget.mouseReleased(mx, my, click.button());
            }
        }
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent click, double deltaX, double deltaY) {
        double guiScale = SeqClient.mc.getWindow().getGuiScale();
        float mx = (float) (click.x() * guiScale / 2.0);
        float my = (float) (click.y() * guiScale / 2.0);

        if (scrollbarDragging && maxScroll > 0) {
            float screenHeight = SeqClient.mc.getWindow().getHeight() / 2f;
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
            if (keyCode >= GLFW.GLFW_KEY_A && keyCode <= GLFW.GLFW_KEY_Z) {
                boolean shift = (keyEvent.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0;
                char letter = (char) ('a' + (keyCode - GLFW.GLFW_KEY_A));
                searchQuery += shift ? Character.toUpperCase(letter) : letter;
                scrollOffset = 0;
                return true;
            }
            if (keyCode >= GLFW.GLFW_KEY_0 && keyCode <= GLFW.GLFW_KEY_9) {
                searchQuery += (char) ('0' + (keyCode - GLFW.GLFW_KEY_0));
                scrollOffset = 0;
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_SPACE) {
                searchQuery += ' ';
                scrollOffset = 0;
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

    private boolean isHovered(float mx, float my, float bx, float by, float bw, float bh) {
        return mx >= bx && mx <= bx + bw && my >= by && my <= by + bh;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

}
