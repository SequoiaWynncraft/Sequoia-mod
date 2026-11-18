package com.seqwawa.seq.ui;

import static com.seqwawa.seq.managers.ThemeManager.color;
import static com.seqwawa.seq.ui.theme.UiColor.*;

import java.awt.Color;
import com.seqwawa.seq.LightRoomTnaRange.LightRoom;
import com.seqwawa.seq.halcyon.HalcyonRingRenderer;
import com.seqwawa.seq.managers.PrincessMode;
import com.seqwawa.seq.radiance.PingRenderer;
import com.seqwawa.seq.scroll.CraftedScrollRangeVisualiserClient;
import com.seqwawa.seq.ui.widget.BooleanWidget;
import com.seqwawa.seq.ui.widget.ChoiceWidget;
import com.seqwawa.seq.ui.widget.ColorWidget;
import com.seqwawa.seq.ui.widget.EnumWidget;
import com.seqwawa.seq.ui.widget.SelectionWidget;
import com.seqwawa.seq.ui.widget.SettingWidget;
import com.seqwawa.seq.ui.widget.SliderWidget;
import com.seqwawa.seq.ui.widget.StringWidget;
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

import java.util.*;
import java.util.List;

public class SettingsScreen extends SequoiaPanelScreen {
    // Layout
    private static final float SIDEBAR_WIDTH = SequoiaSidebarNavigation.WIDTH;
    private static final float SIDEBAR_PADDING = SequoiaSidebarNavigation.PADDING;
    private static final float SIDEBAR_BUTTON_SPACING = SequoiaSidebarNavigation.BUTTON_SPACING;
    private static final float HEADER_HEIGHT = 30;
    private static final float CATEGORY_HEIGHT = 28;
    private static final float SECTION_HEIGHT = 22;
    private static final float CATEGORY_SPACING = 6;
    private static final float PADDING = 8;
    private static final float SEARCH_BAR_HEIGHT = 18;
    private static final float SEARCH_BAR_MARGIN = 8;
    private static final float THEME_EDITOR_BUTTON_WIDTH = 94;
    private static final float HUD_EDITOR_BUTTON_WIDTH = 76;
    private static final float HEADER_BUTTON_GAP = 6;
    private static final float PRINCESS_PROMPT_HEIGHT = 24;

    // Font sizes
    private static final float TITLE_FONT_SIZE = 18;
    private static final float CATEGORY_FONT_SIZE = 14;
    private static final float SECTION_FONT_SIZE = 11;
    private static final float SEARCH_FONT_SIZE = 12;
    private static final float SCROLL_SPEED = 12;

    private final LinkedHashMap<String, List<SettingWidget<?>>> categories = new LinkedHashMap<>();
    private final Set<String> collapsedCategories = new HashSet<>();
    private final SettingsSectionExpansion sectionExpansion = new SettingsSectionExpansion();
    private final ScrollPane scroll = new ScrollPane();
    private final TextField search = new TextField();
    private final PrincessSidebarPrompt princessPrompt =
            new PrincessSidebarPrompt(new Random(), System.currentTimeMillis());

    public SettingsScreen(Screen parent) {
        super(Component.literal("Settings"), parent, SequoiaSidebarNavigation.Destination.SETTINGS);
        buildWidgets();
        if (PrincessMode.isEnabled() && SeqClient.getPrincessRaidStatsManager() != null) {
            SeqClient.getPrincessRaidStatsManager().refresh();
        }
    }

    @Override
    public void removed() {
        for (var widgets : categories.values()) widgets.forEach(SettingWidget::onHidden);
        deactivateColorPreviews();
        LightRoom.setColorPreviewActive(false);
        HalcyonRingRenderer.setColorPreviewActive(false);
        PingRenderer.setColorPreviewActive(false);
        CraftedScrollRangeVisualiserClient.setColorPreviewActive(false);
        SeqClient.getConfigManager().save();
        super.removed();
    }

    private void buildWidgets() {
        Map<String, List<SettingWidget<?>>> temp = new LinkedHashMap<>();

        for (Setting<?> setting : SeqClient.getConfigManager().getSettings()) {
            String category = setting.getPresentationCategory();
            SettingWidget<?> widget = createWidget(setting);
            if (widget != null) {
                temp.computeIfAbsent(category, k -> new ArrayList<>()).add(widget);
            }
        }

        categories.clear();
        sortedCategoryNames(temp.keySet()).forEach(
                category -> categories.put(category, groupWidgetsBySection(temp.get(category))));
        collapsedCategories.clear();
        collapsedCategories.addAll(categories.keySet());
    }

    /** Keeps each section contiguous while preserving section and control registration order. */
    static List<SettingWidget<?>> groupWidgetsBySection(List<SettingWidget<?>> widgets) {
        Map<String, List<SettingWidget<?>>> sections = new LinkedHashMap<>();
        for (SettingWidget<?> widget : widgets) {
            sections.computeIfAbsent(widget.getSetting().getSection(), key -> new ArrayList<>()).add(widget);
        }
        return sections.values().stream().flatMap(List::stream).toList();
    }

    static List<String> sortedCategoryNames(Collection<String> categoryNames) {
        if (categoryNames == null || categoryNames.isEmpty()) {
            return List.of();
        }
        return categoryNames.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(
                                (String category) -> SettingWidget.toDisplayName(category),
                                String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private SettingWidget<?> createWidget(Setting<?> setting) {
        if (setting instanceof Setting.BooleanSetting b)
            return new BooleanWidget(b);
        if (setting instanceof Setting.ColorSetting c)
            return createColorWidget(c);
        if (setting instanceof Setting.IntSetting i)
            return new SliderWidget(i);
        if (setting instanceof Setting.DoubleSetting d)
            return new SliderWidget(d);
        if (setting instanceof Setting.FloatSetting f)
            return new SliderWidget(f);
        if (setting instanceof Setting.ChoiceSetting c)
            return new ChoiceWidget(c);
        if (setting instanceof Setting.EnumSetting<?> e)
            return new EnumWidget<>(e);
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
        if (setting == SeqClient.getCraftedScrollRangeColorSetting()) {
            return new ColorWidget(setting, CraftedScrollRangeVisualiserClient::setColorPreviewActive);
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
        if (search.text().isEmpty())
            return true;
        String query = search.text().toLowerCase();
        String settingName = setting.getName();
        String displaySettingName = setting.getDisplayName() == null
                ? SettingWidget.toDisplayName(settingName).toLowerCase()
                : setting.getDisplayName().toLowerCase();
        String displayCategoryName = SettingWidget.toDisplayName(categoryName).toLowerCase();
        String persistedCategoryName = setting.getCategory().toLowerCase();
        String displayPersistedCategoryName = SettingWidget.toDisplayName(setting.getCategory()).toLowerCase();
        return settingName.toLowerCase().contains(query)
                || categoryName.toLowerCase().contains(query)
                || displaySettingName.contains(query)
                || displayCategoryName.contains(query)
                || persistedCategoryName.contains(query)
                || displayPersistedCategoryName.contains(query)
                || containsIgnoreCase(setting.getDescription(), query)
                || containsIgnoreCase(setting.getSection(), query);
    }

    private static boolean containsIgnoreCase(String value, String lowercaseQuery) {
        return value != null && value.toLowerCase().contains(lowercaseQuery);
    }

    /** A sidebar button opens its screen in front of Settings, so Esc there comes back here. */
    @Override
    protected Screen sidebarParent() {
        return this;
    }

    /** The profile card where it leaves the Princess mode controls their room, and the prompt's place above it. */
    @Override
    protected SequoiaSidebarNavigation.Foot sidebarFoot(float screenHeight) {
        return new SequoiaSidebarNavigation.Foot(
                settingsSidebarReserved(screenHeight, SequoiaSidebarNavigation.destinations().size(),
                        sidebarFloor(screenHeight), princessPromptAllowed()),
                profileShown(screenHeight));
    }

    @Override
    protected void beforeFrame() {
        if (!princessPromptAllowed() && PrincessMode.isEnabled()) {
            PrincessMode.setEnabled(false);
        }
        deactivateHiddenWidgets();
    }

    @Override
    protected void draw(UiCanvas canvas) {
        float screenWidth = canvas.metrics().width();
        float screenHeight = canvas.metrics().height();
        String fontName = SeqClient.getFontManager().getSelectedFont();

        // Fill entire screen
        canvas.fillRect(0, 0, screenWidth, screenHeight, color(BACKGROUND_OVERLAY));

        // === Left Sidebar (full height) ===
        drawSidebar(canvas);
        float btnX = SIDEBAR_PADDING;
        float btnW = SIDEBAR_WIDTH - SIDEBAR_PADDING * 2;
        var sidebarLayout = settingsSidebarLayout(screenHeight, SequoiaSidebarNavigation.destinations().size());

        float floor = sidebarFloor(screenHeight);
        boolean princessPromptFits = princessPromptFits(floor, sidebarLayout.bottom());
        if (princessLeaderboardVisible()) {
            float leaderboardY = sidebarLayout.bottom() + SIDEBAR_BUTTON_SPACING;
            float leaderboardHeight = princessLeaderboardHeight(floor, sidebarLayout.bottom(), princessPromptFits);
            if (leaderboardHeight >= PrincessLeaderboardPanel.MIN_HEIGHT) {
                PrincessLeaderboardPanel.render(
                        canvas,
                        fontName,
                        btnX,
                        leaderboardY,
                        btnW,
                        leaderboardHeight,
                        SeqClient.getPrincessRaidStatsManager().snapshot());
            }
        }

        if (princessPromptFits) {
            renderPrincessPrompt(canvas, fontName, screenHeight, System.currentTimeMillis());
        }

        // === Main Content Panel (fills rest of screen) ===
        float panelX = SIDEBAR_WIDTH;
        float panelY = 0;
        float panelWidth = screenWidth - SIDEBAR_WIDTH;
        float panelHeight = screenHeight;

        canvas.fillRect(panelX, panelY, panelWidth, panelHeight, color(BACKGROUND_BODY));

        // Header bar
        canvas.fillRect(panelX, panelY, panelWidth, HEADER_HEIGHT, color(BACKGROUND_HEADER));

        // Search bar (top left of header), then the editor buttons beside it
        UiBounds searchBounds = searchBounds(panelWidth);
        search.draw(canvas, searchBounds, fontName, SEARCH_FONT_SIZE, "Search...");
        drawHeaderButton(canvas, fontName, themeEditorButton(searchBounds), "Theme editor");
        drawHeaderButton(canvas, fontName, hudEditorButton(searchBounds), "HUD layout");

        // Title (right side of header)
        drawText(canvas, fontName, TITLE_FONT_SIZE, color(ACCENT_PRIMARY_HOVER), UiCanvas.HorizontalAlign.RIGHT,
                panelX + panelWidth - SEARCH_BAR_MARGIN, panelY + HEADER_HEIGHT / 2f, "Settings");

        // Content area with scissor
        float contentX = panelX;
        float contentY = panelY + HEADER_HEIGHT;
        float contentWidth = panelWidth;
        float contentHeight = panelHeight - HEADER_HEIGHT;
        UiBounds content = new UiBounds(contentX, contentY, contentWidth, contentHeight);

        canvas.save();
        canvas.scissor(contentX, contentY, contentWidth, contentHeight);

        float cursorY = contentY - scroll.offset() + PADDING;
        float widgetWidth = contentWidth - PADDING * 2 - 6;

        int settingIndex = 0;
        for (Map.Entry<String, List<SettingWidget<?>>> entry : categories.entrySet()) {
            String category = entry.getKey();
            List<SettingWidget<?>> filtered = shownWidgets(category, entry.getValue());
            if (filtered.isEmpty()) {
                continue;
            }
            boolean collapsed = isCategoryCollapsed(category);

            // Category header
            UiBounds header = new UiBounds(contentX, cursorY, contentWidth, CATEGORY_HEIGHT);
            boolean catHovered = header.contains(pointer) && content.contains(pointer);
            header.fill(canvas, catHovered ? color(BACKGROUND_CONTENT_FOCUSED) : color(BACKGROUND_CONTENT));

            // Arrow
            drawText(canvas, fontName, 12, color(ACCENT_PRIMARY_HOVER), UiCanvas.HorizontalAlign.CENTER,
                    contentX + PADDING + 14, cursorY + CATEGORY_HEIGHT / 2f, collapsed ? "+" : "-");

            // Category name
            String displayName = SettingWidget.toDisplayName(category);
            drawText(canvas, fontName, CATEGORY_FONT_SIZE, color(ACCENT_PRIMARY_HOVER), UiCanvas.HorizontalAlign.LEFT,
                    contentX + PADDING + 26, cursorY + CATEGORY_HEIGHT / 2f, displayName);

            cursorY += CATEGORY_HEIGHT;

            // Settings under this category
            if (!collapsed) {
                String currentSection = null;
                for (SettingWidget<?> widget : filtered) {
                    String section = widget.getSetting().getSection();
                    if (section != null && !section.equals(currentSection)) {
                        UiBounds sectionRow = new UiBounds(contentX, cursorY, contentWidth, SECTION_HEIGHT);
                        boolean sectionHovered = sectionRow.contains(pointer) && content.contains(pointer);
                        sectionRow.fill(canvas,
                                sectionHovered ? color(BACKGROUND_CONTENT_FOCUSED) : color(BACKGROUND_CONTENT));
                        drawText(canvas, fontName, SECTION_FONT_SIZE, color(TEXT_SECONDARY),
                                UiCanvas.HorizontalAlign.CENTER, contentX + PADDING + 14,
                                cursorY + SECTION_HEIGHT / 2f,
                                isSectionCollapsed(category, section) ? "+" : "-");
                        drawText(
                                canvas,
                                fontName,
                                SECTION_FONT_SIZE,
                                color(TEXT_SECONDARY),
                                UiCanvas.HorizontalAlign.LEFT,
                                contentX + PADDING + 26,
                                cursorY + SECTION_HEIGHT / 2f,
                                section);
                        cursorY += SECTION_HEIGHT;
                    }
                    currentSection = section;
                    if (isSectionCollapsed(category, section)) {
                        continue;
                    }
                    Color bg = (settingIndex % 2 == 0) ? color(BACKGROUND_BODY) : color(BACKGROUND_CONTENT_FOCUSED);
                    canvas.fillRect(contentX, cursorY, contentWidth, widget.getHeight(), bg);

                    widget.setPosition(contentX + PADDING, cursorY, widgetWidth, widget.getHeight());
                    if (widget instanceof SelectionWidget<?> selection) selection.setViewport(contentY, contentY + contentHeight);
                    widget.render(canvas, pointer.x(), pointer.y());
                    cursorY += widget.getHeight();
                    settingIndex++;
                }
            }

            cursorY += CATEGORY_SPACING;
        }

        scroll.setMax(cursorY + scroll.offset() - contentY - contentHeight);

        canvas.restore();

        scroll.draw(canvas, scrollTrack(content));
        SelectionWidget<?> dropdown = openDropdown();
        if (dropdown != null) dropdown.renderOverlay(canvas, pointer.x(), pointer.y());
    }

    private void drawHeaderButton(UiCanvas canvas, String fontName, UiBounds button, String label) {
        button.fill(canvas, button.contains(pointer) ? color(ACCENT_PRIMARY_HOVER) : color(ACCENT_PRIMARY));
        drawText(canvas, fontName, 11, color(TEXT_PRIMARY), UiCanvas.HorizontalAlign.CENTER,
                button.centerX(), button.centerY(), label);
    }

    private static UiBounds searchBounds(float panelWidth) {
        return new UiBounds(SIDEBAR_WIDTH + SEARCH_BAR_MARGIN, (HEADER_HEIGHT - SEARCH_BAR_HEIGHT) / 2f,
                SequoiaUiStyle.searchWidth(panelWidth - SEARCH_BAR_MARGIN * 2), SEARCH_BAR_HEIGHT);
    }

    private static UiBounds themeEditorButton(UiBounds search) {
        return new UiBounds(search.right() + SEARCH_BAR_MARGIN, search.y(), THEME_EDITOR_BUTTON_WIDTH, search.height());
    }

    private static UiBounds hudEditorButton(UiBounds search) {
        UiBounds themeEditor = themeEditorButton(search);
        return new UiBounds(themeEditor.right() + HEADER_BUTTON_GAP, search.y(),
                HUD_EDITOR_BUTTON_WIDTH, search.height());
    }

    /** The scrollbar along the content's right edge. */
    private static UiBounds scrollTrack(UiBounds content) {
        return new UiBounds(content.right() - 5, content.y(), 4, content.height());
    }

    /** A category's settings that show: the visible ones, and while searching, those that match. */
    private List<SettingWidget<?>> shownWidgets(String category, List<SettingWidget<?>> widgets) {
        List<SettingWidget<?>> visible = visibleWidgets(widgets);
        if (search.text().isEmpty()) return visible;
        List<SettingWidget<?>> filtered = new ArrayList<>();
        for (SettingWidget<?> w : visible) {
            if (matchesSearch(w.getSetting(), category)) {
                filtered.add(w);
            }
        }
        return filtered;
    }

    private SelectionWidget<?> openDropdown() {
        for (var widgets : categories.values()) {
            for (var widget : widgets) {
                if (widget instanceof SelectionWidget<?> selection && selection.isOpen()) {
                    if (isWidgetInteractive(widget)) return selection;
                    selection.onHidden();
                }
            }
        }
        return null;
    }

    private void renderPrincessPrompt(UiCanvas canvas, String fontName, float screenHeight, long nowMs) {
        if (!princessPromptAllowed()) {
            return;
        }

        float progress = princessPrompt.slideProgress(nowMs);
        if (progress <= 0f) {
            return;
        }

        float floor = sidebarFloor(screenHeight);
        float x = SIDEBAR_PADDING;
        float width = SIDEBAR_WIDTH - SIDEBAR_PADDING * 2;
        float y = princessPromptY(floor, progress);
        boolean hovered = overPrincessPrompt(pointer, floor, progress);
        // It slides up from behind the profile card, so nothing of it shows below the card's top.
        canvas.save();
        canvas.scissor(0, 0, SIDEBAR_WIDTH, floor);
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
        canvas.restore();
    }

    /** Where the sidebar's free space ends: the profile card's top while the card shows, else the screen's bottom. */
    private static float sidebarFloor(float screenHeight) {
        return profileShown(screenHeight) ? screenHeight - SidebarProfile.reserved(screenHeight) : screenHeight;
    }

    /** Whether Settings shows the profile card: where it fits, and leaves the Princess mode controls their room. */
    private static boolean profileShown(float screenHeight) {
        float card = SidebarProfile.reserved(screenHeight);
        return card > 0 && profileLeavesPrincessRoom(screenHeight, SequoiaSidebarNavigation.destinations().size(),
                screenHeight - card, princessPromptAllowed(), princessLeaderboardVisible());
    }

    /**
     * Whether the profile card, its top at {@code cardFloor}, leaves room for the
     * Princess mode prompt and, while the mode is on, its leaderboard. The card is
     * a shortcut and they are settings, so where one of them fits without the card
     * and not with it, Settings leaves the card out.
     */
    static boolean profileLeavesPrincessRoom(float screenHeight, int rows, float cardFloor, boolean promptAllowed,
            boolean leaderboardVisible) {
        if (!promptAllowed) return true;
        float withoutBottom = settingsSidebarLayout(screenHeight, rows, screenHeight, true).bottom();
        float withBottom = settingsSidebarLayout(screenHeight, rows, cardFloor, true).bottom();
        boolean promptWithout = princessPromptFits(screenHeight, withoutBottom);
        boolean promptWith = princessPromptFits(cardFloor, withBottom);
        if (promptWithout && !promptWith) return false;
        if (!leaderboardVisible) return true;
        boolean boardWithout = princessLeaderboardHeight(screenHeight, withoutBottom, promptWithout)
                >= PrincessLeaderboardPanel.MIN_HEIGHT;
        boolean boardWith = princessLeaderboardHeight(cardFloor, withBottom, promptWith)
                >= PrincessLeaderboardPanel.MIN_HEIGHT;
        return boardWith || !boardWithout;
    }

    /** How tall the leaderboard can be between the last button and the prompt, or the floor when there is no prompt. */
    static float princessLeaderboardHeight(float floor, float navigationBottom, boolean promptFits) {
        float top = navigationBottom + SIDEBAR_BUTTON_SPACING;
        float bottom = promptFits ? princessPromptY(floor, 1f) : floor - SIDEBAR_PADDING;
        return Math.min(PrincessLeaderboardPanel.HEIGHT, bottom - SIDEBAR_BUTTON_SPACING - top);
    }

    private static SequoiaSidebarNavigation.SidebarLayout settingsSidebarLayout(float screenHeight, int rows) {
        return settingsSidebarLayout(screenHeight, rows, sidebarFloor(screenHeight), princessPromptAllowed());
    }

    /**
     * The sidebar's buttons, packed to leave the Princess mode prompt its place
     * above {@code floor} when the prompt may show, so neither covers the other.
     * Where even the packed buttons leave no room for it, the prompt stays hidden
     * and the buttons take the space back.
     */
    static SequoiaSidebarNavigation.SidebarLayout settingsSidebarLayout(
            float screenHeight, int rows, float floor, boolean promptAllowed) {
        return SequoiaSidebarNavigation.sidebarLayout(screenHeight, rows, SequoiaSidebarNavigation.BUTTON_HEIGHT,
                SIDEBAR_BUTTON_SPACING, settingsSidebarReserved(screenHeight, rows, floor, promptAllowed));
    }

    /** How much of the sidebar's foot {@link #settingsSidebarLayout} keeps clear of buttons. */
    static float settingsSidebarReserved(float screenHeight, int rows, float floor, boolean promptAllowed) {
        float below = screenHeight - floor;
        if (!promptAllowed) return below;
        // The layout keeps its own bottom margin under the last button, which is the gap above the prompt.
        float withPrompt = below + SIDEBAR_PADDING + PRINCESS_PROMPT_HEIGHT;
        var layout = SequoiaSidebarNavigation.sidebarLayout(
                screenHeight, rows, SequoiaSidebarNavigation.BUTTON_HEIGHT, SIDEBAR_BUTTON_SPACING, withPrompt);
        return princessPromptFits(floor, layout.bottom()) ? withPrompt : below;
    }

    /** The prompt's top as it slides up out of {@code floor}, the sidebar's foot or the profile card's top. */
    private static float princessPromptY(float floor, float progress) {
        return floor - (SIDEBAR_PADDING + PRINCESS_PROMPT_HEIGHT) * progress;
    }

    static boolean princessPromptFits(float floor, float navigationBottom) {
        return princessPromptY(floor, 1f) >= navigationBottom + SIDEBAR_BUTTON_SPACING;
    }

    /** Whether the point is over the part of the prompt that shows: above the profile card, while it slides in. */
    private static boolean overPrincessPrompt(UiPointer at, float floor, float progress) {
        return progress > 0f && at.y() < floor && new UiBounds(SIDEBAR_PADDING, princessPromptY(floor, progress),
                SIDEBAR_WIDTH - SIDEBAR_PADDING * 2, PRINCESS_PROMPT_HEIGHT).contains(at);
    }

    private static boolean princessPromptAllowed() {
        return SeqClient.getEasterEggsSetting() != null && SeqClient.getEasterEggsSetting().getValue();
    }

    private static boolean princessLeaderboardVisible() {
        return PrincessMode.isEnabled() && SeqClient.getPrincessRaidStatsManager() != null;
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

        UiPointer at = UiPointer.at(click);
        float mx = at.x();
        float my = at.y();

        SelectionWidget<?> dropdown = openDropdown();
        if (dropdown != null && dropdown.mouseClicked(mx, my, click.button())) return true;

        float screenWidth = MinecraftUiRenderer.screenWidth();
        float screenHeight = MinecraftUiRenderer.screenHeight();
        float panelX = SIDEBAR_WIDTH;
        float panelWidth = screenWidth - SIDEBAR_WIDTH;
        float panelHeight = screenHeight;
        float contentY = HEADER_HEIGHT;
        float contentHeight = panelHeight - HEADER_HEIGHT;

        if (click.button() == 0) {
            if (clickSidebar(at)) return true;

            float floor = sidebarFloor(screenHeight);
            var sidebarLayout = settingsSidebarLayout(screenHeight, SequoiaSidebarNavigation.destinations().size());
            if (princessPromptAllowed() && princessPromptFits(floor, sidebarLayout.bottom())) {
                float progress = princessPrompt.slideProgress(System.currentTimeMillis());
                if (overPrincessPrompt(at, floor, progress)) {
                    boolean enabled = PrincessMode.toggle();
                    if (enabled && SeqClient.getPrincessRaidStatsManager() != null) {
                        SeqClient.getPrincessRaidStatsManager().refresh();
                    }
                    return true;
                }
            }

            UiBounds searchBounds = searchBounds(panelWidth);
            if (searchBounds.contains(at)) {
                search.focus();
                return true;
            }
            search.blur();

            if (themeEditorButton(searchBounds).contains(at)) {
                SeqClient.mc.setScreen(new ThemeEditorScreen(this));
                return true;
            }
            if (hudEditorButton(searchBounds).contains(at)) {
                SeqClient.mc.setScreen(new WarQueueHudEditorScreen(this));
                return true;
            }

            // The scrollbar's hit area is two pixels wider than the bar on each side.
            UiBounds content = new UiBounds(panelX, contentY, panelWidth, contentHeight);
            if (scroll.startDrag(at, scrollTrack(content).inset(-2, 0))) {
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
        float cursorY = contentY - scroll.offset() + PADDING;

        for (Map.Entry<String, List<SettingWidget<?>>> entry : categories.entrySet()) {
            String category = entry.getKey();
            List<SettingWidget<?>> filtered = shownWidgets(category, entry.getValue());
            if (filtered.isEmpty()) {
                continue;
            }
            boolean collapsed = isCategoryCollapsed(category);

            // Category header click
            if (new UiBounds(panelX, cursorY, contentWidth, CATEGORY_HEIGHT).contains(at)) {
                if (click.button() == 0) {
                    if (search.text().isEmpty()) {
                        if (collapsed) {
                            collapsedCategories.remove(category);
                        } else {
                            collapsedCategories.add(category);
                        }
                        deactivateHiddenWidgets();
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
                        if (new UiBounds(panelX, cursorY, contentWidth, SECTION_HEIGHT).contains(at)) {
                            if (click.button() == 0) {
                                if (search.text().isEmpty()) {
                                    sectionExpansion.toggle(category, section);
                                    deactivateHiddenWidgets();
                                }
                                return true;
                            }
                            return super.mouseClicked(click, outsideScreen);
                        }
                        cursorY += SECTION_HEIGHT;
                    }
                    currentSection = section;
                    if (isSectionCollapsed(category, section)) {
                        continue;
                    }
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
        scroll.release();
        UiPointer at = UiPointer.at(click);

        for (List<SettingWidget<?>> widgets : categories.values()) {
            for (SettingWidget<?> widget : widgets) {
                widget.mouseReleased(at.x(), at.y(), click.button());
            }
        }
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent click, double deltaX, double deltaY) {
        UiPointer at = UiPointer.at(click);
        UiBounds content = new UiBounds(SIDEBAR_WIDTH, HEADER_HEIGHT,
                MinecraftUiRenderer.screenWidth() - SIDEBAR_WIDTH, MinecraftUiRenderer.screenHeight() - HEADER_HEIGHT);
        if (scroll.canScroll() && scroll.drag(at, scrollTrack(content))) {
            return true;
        }

        for (List<SettingWidget<?>> widgets : categories.values()) {
            for (SettingWidget<?> widget : interactiveWidgets(widgets)) {
                if (widget.mouseDragged(at.x(), at.y()))
                    return true;
            }
        }
        return super.mouseDragged(click, deltaX, deltaY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        SelectionWidget<?> dropdown = openDropdown();
        if (dropdown != null && dropdown.scroll(scrollY)) return true;
        scroll.scrollBy(-(float) scrollY * SCROLL_SPEED);
        return true;
    }

    @Override
    public boolean keyPressed(@NotNull KeyEvent keyEvent) {
        SelectionWidget<?> dropdown = openDropdown();
        if (dropdown != null && dropdown.keyPressed(keyEvent)) return true;
        // Search bar input
        if (search.focused()) {
            int keyCode = keyEvent.key();
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                search.blur();
            } else if (keyCode == GLFW.GLFW_KEY_BACKSPACE && search.backspace()) {
                scroll.reset();
            }
            return true;
        }

        for (List<SettingWidget<?>> widgets : categories.values()) {
            for (SettingWidget<?> widget : interactiveWidgets(widgets)) {
                if (widget.keyPressed(keyEvent))
                    return true;
            }
        }
        return super.keyPressed(keyEvent);
    }

    @Override
    public boolean charTyped(@NotNull CharacterEvent characterEvent) {
        if (openDropdown() != null) return true;
        if (search.focused()) {
            String typedText = TextInputHelper.getTypedText(characterEvent);
            if (typedText != null) {
                search.type(typedText);
                scroll.reset();
            }
            return true;
        }

        for (List<SettingWidget<?>> widgets : categories.values()) {
            for (SettingWidget<?> widget : interactiveWidgets(widgets)) {
                if (widget.charTyped(characterEvent)) {
                    return true;
                }
            }
        }
        return super.charTyped(characterEvent);
    }

    private boolean isCategoryCollapsed(String category) {
        return search.text().isEmpty() && collapsedCategories.contains(category);
    }

    private boolean isSectionCollapsed(String category, String section) {
        return sectionExpansion.isCollapsed(category, section, !search.text().isEmpty());
    }

    private boolean isWidgetInteractive(SettingWidget<?> widget) {
        Setting<?> setting = widget.getSetting();
        String category = setting.getPresentationCategory();
        return setting.isVisible()
                && !isCategoryCollapsed(category)
                && !isSectionCollapsed(category, setting.getSection())
                && matchesSearch(setting, category);
    }

    private List<SettingWidget<?>> interactiveWidgets(List<SettingWidget<?>> widgets) {
        return widgets.stream().filter(this::isWidgetInteractive).toList();
    }

    private void deactivateHiddenWidgets() {
        for (List<SettingWidget<?>> widgets : categories.values()) {
            for (SettingWidget<?> widget : widgets) {
                if (!isWidgetInteractive(widget)) {
                    widget.onHidden();
                }
            }
        }
    }

    private List<SettingWidget<?>> visibleWidgets(List<SettingWidget<?>> widgets) {
        return widgets.stream()
                .filter(widget -> widget.getSetting().isVisible())
                .toList();
    }

}
