package org.sequoia.seq.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
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
    private static final float PANEL_WIDTH = 300;
    private static final float HEADER_HEIGHT = 30;
    private static final float CATEGORY_HEIGHT = 28;
    private static final float PADDING = 8;
    private static final float BACK_BUTTON_SIZE = 20;
    private static final float TITLE_FONT_SIZE = 18;
    private static final float CATEGORY_FONT_SIZE = 14;
    private static final float SCROLL_SPEED = 12;

    private static final Color BG_COLOR = new Color(0, 0, 0, 140);
    private static final Color PANEL_COLOR = new Color(20, 20, 28, 230);
    private static final Color HEADER_COLOR = new Color(30, 30, 40, 255);
    private static final Color CATEGORY_COLOR = new Color(35, 35, 48, 220);
    private static final Color CATEGORY_HOVER = new Color(45, 45, 60, 230);
    private static final Color TITLE_COLOR = new Color(100, 220, 130, 255);
    private static final Color CATEGORY_TEXT = new Color(180, 180, 200, 255);
    private static final Color ARROW_COLOR = new Color(140, 140, 160, 255);
    private static final Color BACK_HOVER = new Color(60, 60, 80, 200);
    private static final Color SCROLLBAR_TRACK = new Color(40, 40, 55, 150);
    private static final Color SCROLLBAR_THUMB = new Color(100, 220, 130, 150);
    private static final Color SETTING_BG = new Color(28, 28, 38, 200);
    private static final Color SETTING_BG_ALT = new Color(32, 32, 44, 200);

    private final Screen parent;
    private final LinkedHashMap<String, List<SettingWidget<?>>> categories = new LinkedHashMap<>();
    private final Set<String> collapsedCategories = new HashSet<>();
    private float scrollOffset = 0;
    private float maxScroll = 0;
    private float nvgMouseX, nvgMouseY;
    private boolean scrollbarDragging = false;
    private float scrollbarDragStart = 0;
    private float scrollOffsetDragStart = 0;

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
        if (setting instanceof Setting.BooleanSetting b) return new BooleanWidget(b);
        if (setting instanceof Setting.IntSetting i) return new SliderWidget(i);
        if (setting instanceof Setting.DoubleSetting d) return new SliderWidget(d);
        if (setting instanceof Setting.FloatSetting f) return new SliderWidget(f);
        if (setting instanceof Setting.EnumSetting<?> e) return new EnumWidget(e);
        return null;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        double guiScale = SeqClient.mc.getWindow().getGuiScale();
        nvgMouseX = (float) (mouseX * guiScale / 2.0);
        nvgMouseY = (float) (mouseY * guiScale / 2.0);

        NVGContext.render(nvg -> {
            float screenWidth = SeqClient.mc.getWindow().getWidth() / 2f;
            float screenHeight = SeqClient.mc.getWindow().getHeight() / 2f;

            // Background
            NVGWrapper.drawRect(nvg, 0, 0, screenWidth, screenHeight, BG_COLOR);

            // Panel
            float panelX = (screenWidth - PANEL_WIDTH) / 2f;
            float panelY = screenHeight * 0.08f;
            float panelHeight = screenHeight * 0.84f;

            NVGWrapper.drawRoundedRect(nvg, panelX, panelY, PANEL_WIDTH, panelHeight, 8, PANEL_COLOR);

            // Header
            NVGWrapper.drawRoundedRect(nvg, panelX, panelY, PANEL_WIDTH, HEADER_HEIGHT, 8, HEADER_COLOR);
            // Cover bottom corners of header rounded rect
            NVGWrapper.drawRect(nvg, panelX, panelY + HEADER_HEIGHT - 8, PANEL_WIDTH, 8, HEADER_COLOR);

            String fontName = SeqClient.getFontManager().getSelectedFont();

            // Back button
            float backX = panelX + 6;
            float backY = panelY + (HEADER_HEIGHT - BACK_BUTTON_SIZE) / 2f;
            boolean backHovered = isHovered(nvgMouseX, nvgMouseY, backX, backY, BACK_BUTTON_SIZE, BACK_BUTTON_SIZE);
            if (backHovered) {
                NVGWrapper.drawRoundedRect(nvg, backX, backY, BACK_BUTTON_SIZE, BACK_BUTTON_SIZE, 4, BACK_HOVER);
            }
            // Draw back arrow "<"
            nvgFontFace(nvg, fontName);
            nvgFontSize(nvg, 16);
            nvgTextAlign(nvg, NVG_ALIGN_CENTER | NVG_ALIGN_MIDDLE);
            var arrowCol = NVGContext.nvgColor(ARROW_COLOR);
            nvgFillColor(nvg, arrowCol);
            nvgText(nvg, backX + BACK_BUTTON_SIZE / 2f, backY + BACK_BUTTON_SIZE / 2f, "<");
            arrowCol.free();

            // Title
            nvgFontFace(nvg, fontName);
            nvgFontSize(nvg, TITLE_FONT_SIZE);
            nvgTextAlign(nvg, NVG_ALIGN_CENTER | NVG_ALIGN_MIDDLE);
            var titleCol = NVGContext.nvgColor(TITLE_COLOR);
            nvgFillColor(nvg, titleCol);
            nvgText(nvg, panelX + PANEL_WIDTH / 2f, panelY + HEADER_HEIGHT / 2f, "Settings");
            titleCol.free();

            // Content area with scissor
            float contentX = panelX;
            float contentY = panelY + HEADER_HEIGHT;
            float contentWidth = PANEL_WIDTH;
            float contentHeight = panelHeight - HEADER_HEIGHT;

            nvgSave(nvg);
            nvgScissor(nvg, contentX, contentY, contentWidth, contentHeight);

            float cursorY = contentY - scrollOffset + PADDING;
            float widgetWidth = contentWidth - PADDING * 2 - 6; // 6 for scrollbar

            int settingIndex = 0;
            for (Map.Entry<String, List<SettingWidget<?>>> entry : categories.entrySet()) {
                String category = entry.getKey();
                List<SettingWidget<?>> widgets = entry.getValue();
                boolean collapsed = collapsedCategories.contains(category);

                // Category header
                boolean catHovered = isHovered(nvgMouseX, nvgMouseY, contentX + PADDING, cursorY, widgetWidth, CATEGORY_HEIGHT)
                        && nvgMouseY >= contentY && nvgMouseY <= contentY + contentHeight;
                NVGWrapper.drawRoundedRect(nvg, contentX + PADDING, cursorY, widgetWidth, CATEGORY_HEIGHT, 4,
                        catHovered ? CATEGORY_HOVER : CATEGORY_COLOR);

                // Arrow
                nvgFontFace(nvg, fontName);
                nvgFontSize(nvg, 12);
                nvgTextAlign(nvg, NVG_ALIGN_CENTER | NVG_ALIGN_MIDDLE);
                var catArrowCol = NVGContext.nvgColor(ARROW_COLOR);
                nvgFillColor(nvg, catArrowCol);
                nvgText(nvg, contentX + PADDING + 14, cursorY + CATEGORY_HEIGHT / 2f, collapsed ? ">" : "v");
                catArrowCol.free();

                // Category name
                nvgFontFace(nvg, fontName);
                nvgFontSize(nvg, CATEGORY_FONT_SIZE);
                nvgTextAlign(nvg, NVG_ALIGN_LEFT | NVG_ALIGN_MIDDLE);
                var catTextCol = NVGContext.nvgColor(CATEGORY_TEXT);
                nvgFillColor(nvg, catTextCol);
                String displayName = category.substring(0, 1).toUpperCase() + category.substring(1);
                nvgText(nvg, contentX + PADDING + 26, cursorY + CATEGORY_HEIGHT / 2f, displayName);
                catTextCol.free();

                cursorY += CATEGORY_HEIGHT + 2;

                // Settings under this category
                if (!collapsed) {
                    for (SettingWidget<?> widget : widgets) {
                        Color bg = (settingIndex % 2 == 0) ? SETTING_BG : SETTING_BG_ALT;
                        NVGWrapper.drawRoundedRect(nvg, contentX + PADDING, cursorY, widgetWidth, widget.getHeight(), 3, bg);

                        widget.setPosition(contentX + PADDING, cursorY, widgetWidth, widget.getHeight());
                        widget.render(nvg, nvgMouseX, nvgMouseY);
                        cursorY += widget.getHeight() + 1;
                        settingIndex++;
                    }
                }

                cursorY += PADDING;
            }

            maxScroll = Math.max(0, cursorY + scrollOffset - contentY - contentHeight);

            nvgRestore(nvg);

            // Scrollbar
            if (maxScroll > 0) {
                float scrollbarX = panelX + PANEL_WIDTH - 6;
                float scrollbarHeight = contentHeight;
                NVGWrapper.drawRect(nvg, scrollbarX, contentY, 4, scrollbarHeight, SCROLLBAR_TRACK);

                float thumbRatio = contentHeight / (contentHeight + maxScroll);
                float thumbHeight = Math.max(20, scrollbarHeight * thumbRatio);
                float thumbY = contentY + (scrollOffset / maxScroll) * (scrollbarHeight - thumbHeight);
                NVGWrapper.drawRoundedRect(nvg, scrollbarX, thumbY, 4, thumbHeight, 2, SCROLLBAR_THUMB);
            }
        });
    }

    @Override
    public boolean mouseClicked(@NotNull MouseButtonEvent click, boolean outsideScreen) {
        if (click.button() == 0) {
            double guiScale = SeqClient.mc.getWindow().getGuiScale();
            float mx = (float) (click.x() * guiScale / 2.0);
            float my = (float) (click.y() * guiScale / 2.0);

            float screenWidth = SeqClient.mc.getWindow().getWidth() / 2f;
            float screenHeight = SeqClient.mc.getWindow().getHeight() / 2f;
            float panelX = (screenWidth - PANEL_WIDTH) / 2f;
            float panelY = screenHeight * 0.08f;
            float panelHeight = screenHeight * 0.84f;
            float contentY = panelY + HEADER_HEIGHT;
            float contentHeight = panelHeight - HEADER_HEIGHT;

            // Back button
            float backX = panelX + 6;
            float backY = panelY + (HEADER_HEIGHT - BACK_BUTTON_SIZE) / 2f;
            if (isHovered(mx, my, backX, backY, BACK_BUTTON_SIZE, BACK_BUTTON_SIZE)) {
                SeqClient.mc.setScreen(parent);
                return true;
            }

            // Scrollbar drag
            if (maxScroll > 0) {
                float scrollbarX = panelX + PANEL_WIDTH - 6;
                if (isHovered(mx, my, scrollbarX - 2, contentY, 8, contentHeight)) {
                    scrollbarDragging = true;
                    scrollbarDragStart = my;
                    scrollOffsetDragStart = scrollOffset;
                    return true;
                }
            }

            // Only process clicks in content area
            if (my < contentY || my > contentY + contentHeight) {
                return super.mouseClicked(click, outsideScreen);
            }

            // Category headers and widgets
            float widgetWidth = PANEL_WIDTH - PADDING * 2 - 6;
            float cursorY = contentY - scrollOffset + PADDING;

            for (Map.Entry<String, List<SettingWidget<?>>> entry : categories.entrySet()) {
                String category = entry.getKey();
                List<SettingWidget<?>> widgets = entry.getValue();
                boolean collapsed = collapsedCategories.contains(category);

                // Category header click
                if (isHovered(mx, my, panelX + PADDING, cursorY, widgetWidth, CATEGORY_HEIGHT)) {
                    if (collapsed) {
                        collapsedCategories.remove(category);
                    } else {
                        collapsedCategories.add(category);
                    }
                    return true;
                }
                cursorY += CATEGORY_HEIGHT + 2;

                if (!collapsed) {
                    for (SettingWidget<?> widget : widgets) {
                        widget.setPosition(panelX + PADDING, cursorY, widgetWidth, widget.getHeight());
                        if (widget.mouseClicked(mx, my, click.button())) {
                            return true;
                        }
                        cursorY += widget.getHeight() + 1;
                    }
                }
                cursorY += PADDING;
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
            float panelY = screenHeight * 0.08f;
            float panelHeight = screenHeight * 0.84f;
            float contentHeight = panelHeight - HEADER_HEIGHT;
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
                if (widget.mouseDragged(mx, my)) return true;
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
        for (List<SettingWidget<?>> widgets : categories.values()) {
            for (SettingWidget<?> widget : widgets) {
                if (widget.keyPressed(keyEvent)) return true;
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

    @Override
    public void renderBackground(@NotNull GuiGraphics guiGraphics, int i, int j, float f) {}
}
