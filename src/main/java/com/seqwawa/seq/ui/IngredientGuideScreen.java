package com.seqwawa.seq.ui;

import static com.seqwawa.seq.managers.ThemeManager.color;
import static com.seqwawa.seq.ui.theme.UiColor.ACCENT_DIVIDER;
import static com.seqwawa.seq.ui.theme.UiColor.ACCENT_PRIMARY_DARK;
import static com.seqwawa.seq.ui.theme.UiColor.ACCENT_PRIMARY;
import static com.seqwawa.seq.ui.theme.UiColor.ACCENT_PRIMARY_HOVER;
import static com.seqwawa.seq.ui.theme.UiColor.TEXT_DISABLED;
import static com.seqwawa.seq.ui.theme.UiColor.BACKGROUND_BODY_OPAQUE;
import static com.seqwawa.seq.ui.theme.UiColor.BACKGROUND_CONTENT;
import static com.seqwawa.seq.ui.theme.UiColor.BACKGROUND_CONTENT_FOCUSED;
import static com.seqwawa.seq.ui.theme.UiColor.CONTROL_BORDER;
import static com.seqwawa.seq.ui.theme.UiColor.CONTROL_INPUT;
import static com.seqwawa.seq.ui.theme.UiColor.CONTROL_INPUT_HOVER;
import static com.seqwawa.seq.ui.theme.UiColor.CONTROL_THUMB;
import static com.seqwawa.seq.ui.theme.UiColor.CONTROL_TRACK;
import static com.seqwawa.seq.ui.theme.UiColor.TEXT_MUTED;
import static com.seqwawa.seq.ui.theme.UiColor.TEXT_PRIMARY;
import static com.seqwawa.seq.ui.theme.UiColor.TEXT_SECONDARY;

import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.managers.IngredientGuideManager;
import com.seqwawa.seq.managers.IngredientGuideManager.SearchScope;
import com.seqwawa.seq.managers.IngredientGuideManager.SortDirection;
import com.seqwawa.seq.managers.IngredientGuideManager.SortKey;
import com.seqwawa.seq.managers.IngredientGuideSessionSettings;
import com.seqwawa.seq.managers.IngredientItemIconFactory;
import com.seqwawa.seq.map.IngredientFarmSpot;
import com.seqwawa.seq.map.IngredientFarmSpotCatalog;
import com.seqwawa.seq.map.IngredientFarmSpotDisplay;
import com.seqwawa.seq.map.IngredientFarmSpotDisplay.Entry;
import com.seqwawa.seq.map.MapFocus;
import com.seqwawa.seq.model.IngredientGuideEntry;
import com.seqwawa.seq.model.IngredientGuideEntry.CraftingModifiers;
import com.seqwawa.seq.model.IngredientGuideEntry.DropSource;
import com.seqwawa.seq.model.IngredientGuideEntry.Effect;
import com.seqwawa.seq.model.IngredientGuideEntry.Modifier;
import com.seqwawa.seq.model.IngredientGuideEntry.SpawnLocation;
import com.seqwawa.seq.render.MinecraftGuiOverlay;
import com.seqwawa.seq.utils.TextInputHelper;
import com.seqwawa.seq.utils.rendering.MinecraftUiRenderer;
import com.seqwawa.seq.utils.rendering.UiCanvas;
import com.seqwawa.seq.utils.rendering.UiRenderMetrics;
import com.seqwawa.seq.utils.rendering.UiRenderer;
import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

public final class IngredientGuideScreen extends Screen implements MinecraftGuiOverlay {
    private static final float OUTER_MARGIN = SequoiaUiStyle.CONTENT_PADDING;
    private static final float HEADER_HEIGHT = SequoiaUiStyle.HEADER_HEIGHT;
    private static final float SEARCH_HEIGHT = SequoiaUiStyle.HEADER_CONTROL_HEIGHT;
    private static final float SORT_ROW_HEIGHT = 22;
    private static final float SORT_ROW_GAP = 4;
    private static final float SORT_DIRECTION_WIDTH = 76;
    private static final float SORT_OPTION_HEIGHT = 22;
    private static final float ROW_HEIGHT = 52;
    private static final float LIST_ICON_SIZE = 28;
    private static final float FARM_SPOT_ROW_HEIGHT = 58;
    private static final float FARM_SPOT_ICON_SIZE = 24;
    private static final int FARM_SPOT_PREVIEW_LIMIT = 3;
    private static final long FARM_SPOT_PREVIEW_ROTATION_MS = 2_500;
    private static final float SCROLL_STEP = 34;
    private static final float SCROLLBAR_WIDTH = 3;
    private static final float SCROLLBAR_HIT_WIDTH = 9;
    private static final float MIN_SCROLLBAR_THUMB_HEIGHT = 20;
    private static final Color[] TIER_COLORS = {
        new Color(153, 153, 153),
        new Color(255, 247, 153),
        new Color(255, 255, 0),
        new Color(230, 77, 0)
    };

    private final Screen parent;
    private final IngredientGuideManager manager = IngredientGuideManager.getInstance();
    private final IngredientGuideSessionSettings sessionSettings = IngredientGuideSessionSettings.getInstance();
    private final List<LocationHitbox> locationHitboxes = new ArrayList<>();
    private ActionHitbox showAllMapHitbox;
    private ActionHitbox showFarmSpotMapHitbox;

    private long observedSnapshotVersion = -1;
    private String observedQuery = "";
    private List<IngredientGuideEntry> visibleIngredients = List.of();
    private IngredientGuideEntry selectedIngredient;
    private GuideCategory guideCategory = GuideCategory.INGREDIENTS;
    private IngredientFarmSpot selectedFarmSpot;
    private String searchQuery = "";
    private boolean searchFocused;
    private boolean searchQuerySelected;
    private SearchScope searchScope;
    private boolean searchScopeDropdownOpen;
    private SortKey primarySortKey;
    private SortDirection primarySortDirection;
    private SortKey secondarySortKey;
    private SortDirection secondarySortDirection;
    private SortDropdown openSortDropdown;
    private Bounds coveredBySortMenu;
    private float listScroll;
    private float detailScroll;
    private float maxListScroll;
    private float maxDetailScroll;
    private ScrollbarTarget draggedScrollbar;
    private float scrollbarDragStartY;
    private float scrollbarDragStartOffset;
    private float nvgMouseX;
    private float nvgMouseY;
    private final Map<String, CachedIngredientIcon> itemIconCache = new HashMap<>();
    private final List<IngredientIconOverlay> itemIconOverlays = new ArrayList<>();
    private long cachedFarmSpotIngredientSnapshotVersion = -1;
    private Map<String, IngredientGuideEntry> cachedFarmSpotIngredientsByName = Map.of();
    private String copyFeedback;
    private long copyFeedbackUntilMs;

    public IngredientGuideScreen(Screen parent) {
        super(Component.literal("Ingredient Guide"));
        this.parent = parent;
        IngredientGuideSessionSettings.SortOrder sortOrder = sessionSettings.sortOrder();
        primarySortKey = sortOrder.primaryKey();
        primarySortDirection = sortOrder.primaryDirection();
        secondarySortKey = sortOrder.secondaryKey();
        secondarySortDirection = sortOrder.secondaryDirection();
        searchScope = sessionSettings.searchScope();
        selectedFarmSpot = IngredientFarmSpotCatalog.all().isEmpty()
                ? null
                : IngredientFarmSpotCatalog.all().getFirst();
        manager.requestRefresh();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        nvgMouseX = MinecraftUiRenderer.mouseX(mouseX);
        nvgMouseY = MinecraftUiRenderer.mouseY(mouseY);
        refreshVisibleIngredients();
        UiRenderer.renderScreen(this, this::renderGuide);
    }

    private void renderGuide(UiCanvas canvas) {
        itemIconOverlays.clear();
        float screenWidth = canvas.metrics().width();
        float screenHeight = canvas.metrics().height();
        GuideLayout layout = currentLayout(screenWidth, screenHeight);
        float panelTop = layout.list().y();
        float panelHeight = layout.list().height();
        float listWidth = layout.list().width();
        float listX = layout.list().x();
        float detailX = layout.detail().x();
        float detailWidth = layout.detail().width();
        coveredBySortMenu = guideCategory == GuideCategory.INGREDIENTS
                ? sortMenuBounds(listX + 9, listWidth - 18, ingredientListLayout(panelTop, hasSecondarySort()))
                : null;

        SequoiaUiStyle.drawPanelFrame(canvas, layout.headerHeight());
        drawText(canvas, "Ingredients", screenWidth - OUTER_MARGIN, HEADER_HEIGHT / 2f, 18,
                color(ACCENT_PRIMARY_HOVER), UiCanvas.HorizontalAlign.RIGHT, UiCanvas.VerticalAlign.MIDDLE);
        SequoiaSidebarNavigation.render(canvas, SequoiaSidebarNavigation.Destination.INGREDIENTS, nvgMouseX, nvgMouseY);
        drawGuideCategoryControl(canvas, layout.category().x(), layout.category().y(), layout.category().width());
        renderHeaderSearch(canvas, layout);
        Bounds refresh = layout.refresh();
        boolean hovered = refresh.contains(nvgMouseX, nvgMouseY);
        canvas.fillRect(refresh.x(), refresh.y(), refresh.width(), refresh.height(),
                hovered ? color(ACCENT_PRIMARY_HOVER) : color(ACCENT_PRIMARY));
        drawText(canvas, "Refresh", refresh.x() + refresh.width() / 2, refresh.y() + refresh.height() / 2,
                12, color(TEXT_PRIMARY), UiCanvas.HorizontalAlign.CENTER, UiCanvas.VerticalAlign.MIDDLE);
        if (guideCategory == GuideCategory.INGREDIENTS) {
            renderIngredientList(canvas, listX, panelTop, listWidth, panelHeight);
            renderIngredientDetail(canvas, detailX, layout.detail().y(), detailWidth, layout.detail().height());
            coveredBySortMenu = null;
            drawOpenSortDropdown(canvas, listX + 9, listWidth - 18,
                    ingredientListLayout(panelTop, hasSecondarySort()));
        } else {
            renderFarmSpotList(canvas, listX, panelTop, listWidth, panelHeight);
            renderFarmSpotDetail(canvas, detailX, layout.detail().y(), detailWidth, layout.detail().height());
        }
        renderSearchScopeDropdown(canvas, layout);
    }

    private GuideLayout currentLayout(float width, float height) {
        return guideLayout(width, height, guideCategory == GuideCategory.INGREDIENTS);
    }

    static GuideLayout guideLayout(float width, float height) {
        return guideLayout(width, height, true);
    }

    static GuideLayout guideLayout(float width, float height, boolean ingredientControls) {
        // Reserve every header control in both modes so category switches never move the toolbar.
        float x = SequoiaSidebarNavigation.WIDTH + OUTER_MARGIN;
        float availableWidth = Math.max(0, width - x - OUTER_MARGIN);
        // Reserve the title on the first row, then wrap controls like Party Finder.
        float firstRight = Math.max(x, width - OUTER_MARGIN - 126);
        float scopeWidth = Math.min(108, availableWidth / 2);
        float searchWidth = SequoiaUiStyle.searchWidth(availableWidth - scopeWidth);
        float combinedWidth = searchWidth + scopeWidth;
        float rowY = x + combinedWidth <= firstRight ? 6 : 6 + SEARCH_HEIGHT + 6;
        float rowRight = rowY == 6 ? firstRight : width - OUTER_MARGIN;
        Bounds search = new Bounds(x, rowY, searchWidth, SEARCH_HEIGHT);
        Bounds scope = new Bounds(x + searchWidth, rowY, scopeWidth, SEARCH_HEIGHT);
        float nextX = x + combinedWidth + 6;
        Bounds[] controls = new Bounds[2];
        float[] widths = {208, 64};
        for (int index = 0; index < widths.length; index++) {
            float controlWidth = Math.min(widths[index], availableWidth);
            if (nextX + controlWidth > rowRight) {
                rowY += SEARCH_HEIGHT + 6;
                nextX = x;
                rowRight = width - OUTER_MARGIN;
            }
            controls[index] = new Bounds(nextX, rowY, controlWidth, SEARCH_HEIGHT);
            nextX += controlWidth + 6;
        }
        Bounds category = controls[0];
        Bounds refresh = controls[1];
        float headerHeight = Math.max(HEADER_HEIGHT, rowY + SEARCH_HEIGHT + OUTER_MARGIN);
        float top = headerHeight + OUTER_MARGIN;
        top = Math.min(top, Math.max(0, height - OUTER_MARGIN));
        float availableHeight = Math.max(0, height - top - OUTER_MARGIN);
        Bounds list;
        Bounds detail;
        if (availableWidth >= 500) {
            float listWidth = clamp(availableWidth * 0.35f, 220, 355);
            list = new Bounds(x, top, listWidth, availableHeight);
            detail = new Bounds(x + listWidth + 10, top, availableWidth - listWidth - 10, availableHeight);
        } else {
            float gap = Math.min(10, availableHeight);
            float listHeight = (availableHeight - gap) * 0.55f;
            list = new Bounds(x, top, availableWidth, listHeight);
            detail = new Bounds(x, top + listHeight + gap, availableWidth, availableHeight - listHeight - gap);
        }
        return new GuideLayout(list, detail, category, refresh, search, scope, headerHeight);
    }

    record Bounds(float x, float y, float width, float height) {
        boolean contains(float px, float py) {
            return px >= x && px < x + width && py >= y && py < y + height;
        }
    }

    record GuideLayout(Bounds list, Bounds detail, Bounds category, Bounds refresh, Bounds search, Bounds scope, float headerHeight) {}

    private void renderFarmSpotList(UiCanvas canvas, float x, float y, float width, float height) {
        List<IngredientFarmSpot> spots = IngredientFarmSpotCatalog.all();
        drawText(canvas, spots.size() + " mob totem spots", x + 11, y + 17, 11, color(TEXT_MUTED),
                UiCanvas.HorizontalAlign.LEFT, UiCanvas.VerticalAlign.MIDDLE);
        float rowsTop = y + 32;
        float rowsHeight = Math.max(0, height - 40);
        maxListScroll = Math.max(0, spots.size() * FARM_SPOT_ROW_HEIGHT - rowsHeight);
        listScroll = clamp(listScroll, 0, maxListScroll);
        long previewRotationTimeMs = System.currentTimeMillis();
        canvas.scissor(x, rowsTop, width, rowsHeight);
        try {
            for (int index = 0; index < spots.size(); index++) {
                IngredientFarmSpot spot = spots.get(index);
                List<Entry> previews = farmSpotIngredientPreviews(spot);
                float rowY = rowsTop + index * FARM_SPOT_ROW_HEIGHT - listScroll;
                boolean selected = spot.equals(selectedFarmSpot);
                boolean hovered = contains(
                        nvgMouseX, nvgMouseY, x + 6, rowY + 2, width - 12, FARM_SPOT_ROW_HEIGHT - 4);
                canvas.fillRect(x, rowY, width - 6, FARM_SPOT_ROW_HEIGHT - 6,
                        selected || hovered ? color(BACKGROUND_CONTENT_FOCUSED) : color(BACKGROUND_CONTENT));
                int visiblePreviewCount = farmSpotVisiblePreviewCount(previews.size());
                float previewWidth = visiblePreviewCount == 0
                        ? 0
                        : visiblePreviewCount * FARM_SPOT_ICON_SIZE + (visiblePreviewCount - 1) * 3 + 8;
                float textWidth = Math.max(1, width - 32 - previewWidth);
                drawText(canvas, ellipsize(spot.name(), textWidth, 14), x + 14, rowY + 16, 14,
                        color(TEXT_PRIMARY), UiCanvas.HorizontalAlign.LEFT, UiCanvas.VerticalAlign.MIDDLE);
                drawText(canvas, spot.coordinates(), x + 14, rowY + 33, 10, color(TEXT_MUTED),
                        UiCanvas.HorizontalAlign.LEFT, UiCanvas.VerticalAlign.MIDDLE);
                int labelIndex = farmSpotLabelIndex(previews.size(), previewRotationTimeMs);
                if (labelIndex >= 0) {
                    Entry label = previews.get(labelIndex);
                    Color labelColor = label.ingredient() == null
                            ? color(TEXT_SECONDARY)
                            : tierColor(label.ingredient().tier());
                    drawText(canvas, label.name(), x + 14, rowY + 48, 9, labelColor,
                            UiCanvas.HorizontalAlign.LEFT, UiCanvas.VerticalAlign.MIDDLE);
                }
                float previewX = x + width - 13
                        - visiblePreviewCount * FARM_SPOT_ICON_SIZE
                        - Math.max(0, visiblePreviewCount - 1) * 3;
                float previewY = rowY + (FARM_SPOT_ROW_HEIGHT - FARM_SPOT_ICON_SIZE) / 2f;
                for (int previewSlot = 0; previewSlot < visiblePreviewCount; previewSlot++) {
                    int previewIndex =
                            farmSpotPreviewIndex(previews.size(), previewSlot, previewRotationTimeMs);
                    drawFarmSpotIngredientPreview(
                            canvas,
                            previews.get(previewIndex),
                            previewX + previewSlot * (FARM_SPOT_ICON_SIZE + 3),
                            previewY,
                            FARM_SPOT_ICON_SIZE,
                            rowsTop,
                            rowsTop + rowsHeight);
                }
            }
        } finally {
            canvas.resetScissor();
        }
        drawScrollbar(
                canvas,
                x + width - 5,
                rowsTop,
                rowsHeight,
                listScroll,
                maxListScroll,
                ScrollbarTarget.INGREDIENT_LIST);
    }

    static int farmSpotVisiblePreviewCount(int previewCount) {
        return Math.min(FARM_SPOT_PREVIEW_LIMIT, Math.max(0, previewCount));
    }

    static int farmSpotPreviewIndex(int previewCount, int previewSlot, long timeMs) {
        int visibleCount = farmSpotVisiblePreviewCount(previewCount);
        if (previewSlot < 0 || previewSlot >= visibleCount) {
            return -1;
        }
        int startIndex = previewCount > FARM_SPOT_PREVIEW_LIMIT
                ? (int) Math.floorMod(timeMs / FARM_SPOT_PREVIEW_ROTATION_MS, previewCount)
                : 0;
        return (startIndex + previewSlot) % previewCount;
    }

    static int farmSpotLabelIndex(int ingredientCount, long timeMs) {
        if (ingredientCount <= 0) {
            return -1;
        }
        return ingredientCount == 1
                ? 0
                : (int) Math.floorMod(timeMs / FARM_SPOT_PREVIEW_ROTATION_MS, ingredientCount);
    }

    private void renderFarmSpotDetail(UiCanvas canvas, float x, float y, float width, float height) {
        canvas.fillRect(x, y, width, height, color(BACKGROUND_CONTENT_FOCUSED));
        locationHitboxes.clear();
        showAllMapHitbox = null;
        showFarmSpotMapHitbox = null;
        if (selectedFarmSpot == null) {
            maxDetailScroll = 0;
            detailScroll = 0;
            drawText(canvas, "Select a mob totem farming spot", x + width / 2f, y + height / 2f, 14,
                    color(TEXT_MUTED), UiCanvas.HorizontalAlign.CENTER, UiCanvas.VerticalAlign.MIDDLE);
            return;
        }

        float viewportTop = y + 8;
        float viewportHeight = Math.max(0, height - 16);
        float viewportBottom = viewportTop + viewportHeight;
        detailScroll = clamp(detailScroll, 0, maxDetailScroll);
        float contentX = x + 16;
        float contentWidth = Math.max(1, width - 32);
        float cursorY = y + 24 - detailScroll;
        canvas.scissor(x, viewportTop, width, viewportHeight);
        try {
            drawText(canvas, ellipsize(selectedFarmSpot.name(), contentWidth, 20), contentX, cursorY, 20,
                    color(TEXT_PRIMARY), UiCanvas.HorizontalAlign.LEFT, UiCanvas.VerticalAlign.MIDDLE);
            cursorY += 30;
            drawText(canvas, selectedFarmSpot.coordinates(), contentX, cursorY, 12, color(ACCENT_PRIMARY),
                    UiCanvas.HorizontalAlign.LEFT, UiCanvas.VerticalAlign.MIDDLE);
            if (selectedFarmSpot.radius() > 0) {
                drawText(canvas, selectedFarmSpot.radius() + " blocks radius", contentX + contentWidth, cursorY, 10,
                        color(TEXT_MUTED), UiCanvas.HorizontalAlign.RIGHT, UiCanvas.VerticalAlign.MIDDLE);
            }
            cursorY += 26;
            float mapButtonWidth = Math.min(150, contentWidth);
            drawButton(canvas, contentX, cursorY, mapButtonWidth, 24, "Show on map");
            if (cursorY + 24 >= viewportTop && cursorY <= viewportBottom) {
                showFarmSpotMapHitbox = new ActionHitbox(contentX, cursorY, mapButtonWidth, 24);
            }
            cursorY += 48;

            drawText(canvas, "INGREDIENTS", contentX, cursorY, 11, color(ACCENT_PRIMARY),
                    UiCanvas.HorizontalAlign.LEFT, UiCanvas.VerticalAlign.MIDDLE);
            cursorY += 14;
            for (Entry preview : farmSpotIngredientPreviews(selectedFarmSpot)) {
                canvas.fillRect(contentX, cursorY, contentWidth, 46, color(BACKGROUND_CONTENT));
                drawFarmSpotIngredientPreview(
                        canvas,
                        preview,
                        contentX + 6,
                        cursorY + 11,
                        24,
                        viewportTop,
                        viewportBottom);
                drawText(
                        canvas,
                        preview.name(),
                        contentX + 38,
                        cursorY + 15,
                        12,
                        preview.ingredient() == null
                                ? color(TEXT_SECONDARY)
                                : tierColor(preview.ingredient().tier()),
                        UiCanvas.HorizontalAlign.LEFT,
                        UiCanvas.VerticalAlign.MIDDLE);
                drawText(
                        canvas,
                        preview.ingredient() == null ? "" : preview.metadata(),
                        contentX + 38,
                        cursorY + 33,
                        9,
                        color(TEXT_MUTED),
                        UiCanvas.HorizontalAlign.LEFT,
                        UiCanvas.VerticalAlign.MIDDLE);
                cursorY += 52;
            }
            cursorY += 14;
            drawText(canvas, "MOBS", contentX, cursorY, 11, color(ACCENT_PRIMARY),
                    UiCanvas.HorizontalAlign.LEFT, UiCanvas.VerticalAlign.MIDDLE);
            cursorY += 21;
            String mobs = selectedFarmSpot.mobs().isEmpty()
                    ? "No mobs listed"
                    : String.join(", ", selectedFarmSpot.mobs());
            drawText(canvas, mobs, contentX, cursorY, 12, color(TEXT_SECONDARY),
                    UiCanvas.HorizontalAlign.LEFT, UiCanvas.VerticalAlign.MIDDLE);
            if (!selectedFarmSpot.notes().isBlank()) {
                cursorY += 32;
                drawText(canvas, "NOTES", contentX, cursorY, 11, color(ACCENT_PRIMARY),
                        UiCanvas.HorizontalAlign.LEFT, UiCanvas.VerticalAlign.MIDDLE);
                cursorY += 21;
                drawText(canvas, selectedFarmSpot.notes(), contentX, cursorY, 11, color(TEXT_MUTED),
                        UiCanvas.HorizontalAlign.LEFT, UiCanvas.VerticalAlign.MIDDLE);
            }
        } finally {
            canvas.resetScissor();
        }

        float contentBottom = cursorY + detailScroll + 16;
        maxDetailScroll = Math.max(0, contentBottom - viewportBottom);
        detailScroll = clamp(detailScroll, 0, maxDetailScroll);
        drawScrollbar(
                canvas,
                x + width - 5,
                viewportTop,
                viewportHeight,
                detailScroll,
                maxDetailScroll,
                ScrollbarTarget.INGREDIENT_DETAIL);
    }

    private void renderHeaderSearch(UiCanvas canvas, GuideLayout layout) {
        boolean enabled = guideCategory == GuideCategory.INGREDIENTS;
        Bounds search = layout.search();
        Bounds scope = layout.scope();
        float totalWidth = search.width() + scope.width();
        boolean active = enabled && (searchFocused || searchScopeDropdownOpen);
        canvas.fillRect(search.x(), search.y(), totalWidth, search.height(), color(CONTROL_INPUT));
        if (enabled && (searchScopeDropdownOpen || scope.contains(nvgMouseX, nvgMouseY))) {
            canvas.fillRect(scope.x(), scope.y(), scope.width(), scope.height(), color(CONTROL_INPUT_HOVER));
        }
        canvas.strokeRect(search.x(), search.y(), totalWidth, search.height(), 1,
                active ? color(CONTROL_BORDER) : color(ACCENT_DIVIDER));
        canvas.fillRect(scope.x(), scope.y() + 3, 1, scope.height() - 6, color(ACCENT_DIVIDER));
        String value = searchQuery.isEmpty() ? "Search..." : searchQuery;
        String visible = ellipsize(value, Math.max(0, search.width() - 12), 12);
        if (enabled && searchQuerySelected && !searchQuery.isEmpty()) {
            float selectionWidth = UiRenderer.measureText(visible, SeqClient.getFontManager().getSelectedFont(), 12).width();
            canvas.fillRect(search.x() + 4, search.y() + 2, Math.max(0, Math.min(selectionWidth + 4, search.width() - 8)),
                    search.height() - 4, color(ACCENT_PRIMARY));
        }
        drawText(canvas, visible, search.x() + 6, search.y() + search.height() / 2, 12,
                !enabled || searchQuery.isEmpty() ? color(TEXT_DISABLED) : color(TEXT_PRIMARY),
                UiCanvas.HorizontalAlign.LEFT, UiCanvas.VerticalAlign.MIDDLE);
        drawText(canvas, ellipsize(searchScope.label(), Math.max(0, scope.width() - 24), 12),
                scope.x() + 7, scope.y() + scope.height() / 2, 12,
                enabled ? color(TEXT_SECONDARY) : color(TEXT_DISABLED),
                UiCanvas.HorizontalAlign.LEFT, UiCanvas.VerticalAlign.MIDDLE);
        drawText(canvas, searchScopeDropdownOpen ? "^" : "v", scope.x() + scope.width() - 7,
                scope.y() + scope.height() / 2, 10, enabled ? color(TEXT_SECONDARY) : color(TEXT_DISABLED),
                UiCanvas.HorizontalAlign.RIGHT, UiCanvas.VerticalAlign.MIDDLE);
    }

    static Bounds searchScopeMenuBounds(GuideLayout layout) {
        Bounds scope = layout.scope();
        return new Bounds(scope.x(), scope.y() + scope.height(), scope.width(),
                SearchScope.values().length * SORT_OPTION_HEIGHT);
    }

    private void renderSearchScopeDropdown(UiCanvas canvas, GuideLayout layout) {
        if (!searchScopeDropdownOpen || guideCategory != GuideCategory.INGREDIENTS) return;
        Bounds menu = searchScopeMenuBounds(layout);
        canvas.fillRect(menu.x(), menu.y(), menu.width(), menu.height(), color(BACKGROUND_BODY_OPAQUE));
        itemIconOverlays.removeIf(icon -> icon.x() < menu.x() + menu.width() && icon.x() + icon.size() > menu.x()
                && icon.y() < menu.y() + menu.height() && icon.y() + icon.size() > menu.y());
        for (SearchScope option : SearchScope.values()) {
            float y = menu.y() + option.ordinal() * SORT_OPTION_HEIGHT;
            boolean selected = option == searchScope;
            boolean hovered = contains(nvgMouseX, nvgMouseY, menu.x(), y, menu.width(), SORT_OPTION_HEIGHT);
            canvas.fillRect(menu.x(), y, menu.width(), SORT_OPTION_HEIGHT,
                    selected ? color(BACKGROUND_CONTENT_FOCUSED)
                            : hovered ? color(CONTROL_INPUT_HOVER) : color(CONTROL_INPUT));
            drawText(canvas, option.label(), menu.x() + 7, y + SORT_OPTION_HEIGHT / 2, 12,
                    selected ? color(ACCENT_PRIMARY_HOVER) : color(TEXT_SECONDARY),
                    UiCanvas.HorizontalAlign.LEFT, UiCanvas.VerticalAlign.MIDDLE);
        }
        canvas.strokeRect(menu.x(), menu.y(), menu.width(), menu.height(), 1, color(ACCENT_DIVIDER));
    }

    private boolean handleSearchScopeClick(float mouseX, float mouseY, GuideLayout layout) {
        if (guideCategory != GuideCategory.INGREDIENTS) return false;
        if (layout.scope().contains(mouseX, mouseY)) {
            searchScopeDropdownOpen = !searchScopeDropdownOpen;
            searchFocused = false;
            searchQuerySelected = false;
            openSortDropdown = null;
            return true;
        }
        if (!searchScopeDropdownOpen) return false;
        Bounds menu = searchScopeMenuBounds(layout);
        searchScopeDropdownOpen = false;
        if (!menu.contains(mouseX, mouseY)) return false;
        searchScope = SearchScope.values()[(int) ((mouseY - menu.y()) / SORT_OPTION_HEIGHT)];
        sessionSettings.setSearchScope(searchScope);
        resortVisibleIngredients();
        searchFocused = true;
        searchQuerySelected = false;
        return true;
    }

    private void renderIngredientList(UiCanvas canvas, float x, float y, float width, float height) {
        IngredientListLayout layout = ingredientListLayout(y, hasSecondarySort());
        drawSortRow(
                canvas,
                x + 9,
                layout.primarySortY(),
                width - 18,
                primarySortKey,
                primarySortDirection);
        if (hasSecondarySort()) {
            drawSortRow(
                    canvas,
                    x + 9,
                    layout.secondarySortY(),
                    width - 18,
                    secondarySortKey,
                    secondarySortDirection);
        }

        float summaryY = layout.summaryY();
        drawText(canvas, visibleIngredients.size() + " ingredients", x + 11, summaryY, 10, color(TEXT_MUTED),
                UiCanvas.HorizontalAlign.LEFT, UiCanvas.VerticalAlign.MIDDLE);

        float rowsTop = layout.rowsTop();
        float rowsHeight = Math.max(0, y + height - rowsTop - 8);
        maxListScroll = Math.max(0, visibleIngredients.size() * ROW_HEIGHT - rowsHeight);
        listScroll = clamp(listScroll, 0, maxListScroll);
        canvas.scissor(x, rowsTop, width, rowsHeight);
        try {
            int first = Math.max(0, (int) (listScroll / ROW_HEIGHT));
            int visibleCount = (int) Math.ceil(rowsHeight / ROW_HEIGHT) + 1;
            int end = Math.min(visibleIngredients.size(), first + visibleCount);
            for (int index = first; index < end; index++) {
                IngredientGuideEntry ingredient = visibleIngredients.get(index);
                float rowY = rowsTop + index * ROW_HEIGHT - listScroll;
                boolean selected = ingredient.equals(selectedIngredient);
                boolean hovered = contains(nvgMouseX, nvgMouseY, x + 6, rowY + 2, width - 12, ROW_HEIGHT - 4);
                canvas.fillRect(x, rowY, width - 6, ROW_HEIGHT - 6,
                        selected || hovered ? color(BACKGROUND_CONTENT_FOCUSED) : color(BACKGROUND_CONTENT));
                canvas.fillRect(x + 13, rowY + ROW_HEIGHT / 2f - 4, 8, 8, tierColor(ingredient.tier()));
                drawText(canvas, ellipsize(ingredient.displayName(), width - 78, 14), x + 29, rowY + 16, 14,
                        color(TEXT_PRIMARY), UiCanvas.HorizontalAlign.LEFT, UiCanvas.VerticalAlign.MIDDLE);
                String subtitle = "Lv. " + ingredient.level() + "  •  " + tierLabel(ingredient.tier());
                drawText(canvas, subtitle, x + 29, rowY + 36, 12, color(TEXT_MUTED),
                        UiCanvas.HorizontalAlign.LEFT, UiCanvas.VerticalAlign.MIDDLE);
                CachedIngredientIcon icon = cachedItemIcon(ingredient);
                float iconX = x + width - LIST_ICON_SIZE - 13;
                float iconY = rowY + (ROW_HEIGHT - LIST_ICON_SIZE) / 2f;
                if (!icon.stack().isEmpty()) {
                    if (iconY >= rowsTop && iconY + LIST_ICON_SIZE <= rowsTop + rowsHeight) {
                        itemIconOverlays.add(new IngredientIconOverlay(
                                icon, iconX, iconY, LIST_ICON_SIZE));
                    }
                } else {
                    drawText(
                            canvas,
                            "✦",
                            iconX + LIST_ICON_SIZE / 2f,
                            iconY + LIST_ICON_SIZE / 2f,
                            16,
                            tierColor(ingredient.tier()),
                            UiCanvas.HorizontalAlign.CENTER,
                            UiCanvas.VerticalAlign.MIDDLE);
                }
            }
        } finally {
            canvas.resetScissor();
        }
        drawScrollbar(
                canvas,
                x + width - 5,
                rowsTop,
                rowsHeight,
                listScroll,
                maxListScroll,
                ScrollbarTarget.INGREDIENT_LIST);
    }

    private void renderIngredientDetail(UiCanvas canvas, float x, float y, float width, float height) {
        canvas.fillRect(x, y, width, height, color(BACKGROUND_CONTENT_FOCUSED));
        locationHitboxes.clear();
        showAllMapHitbox = null;
        showFarmSpotMapHitbox = null;
        if (selectedIngredient == null) {
            String message = "Select an ingredient";
            maxDetailScroll = 0;
            detailScroll = 0;
            drawText(canvas, ellipsize(message, Math.max(0, width - 24), 14), x + width / 2f, y + height / 2f, 14, color(TEXT_MUTED),
                    UiCanvas.HorizontalAlign.CENTER, UiCanvas.VerticalAlign.MIDDLE);
            return;
        }

        float contentX = x + 16;
        float contentWidth = Math.max(1, width - 32);
        float contentTop = y + 14;
        float viewportHeight = Math.max(0, height - 28);
        canvas.scissor(x + 1, y + 1, Math.max(0, width - 2), Math.max(0, height - 2));
        float cursorY = contentTop - detailScroll;
        try {
            CachedIngredientIcon icon = cachedItemIcon(selectedIngredient);
            canvas.fillRect(contentX, cursorY, 58, 58, color(BACKGROUND_CONTENT));
            if (!icon.stack().isEmpty()) {
                float iconX = contentX + 5;
                float iconY = cursorY + 5;
                float iconSize = 48;
                if (iconY >= y + 1 && iconY + iconSize <= y + height - 1) {
                    itemIconOverlays.add(new IngredientIconOverlay(icon, iconX, iconY, iconSize));
                }
            } else {
                drawText(canvas, "✦", contentX + 29, cursorY + 29, 23, tierColor(selectedIngredient.tier()),
                        UiCanvas.HorizontalAlign.CENTER, UiCanvas.VerticalAlign.MIDDLE);
            }

            float titleX = contentX + 70;
            drawText(canvas, ellipsize(selectedIngredient.displayName(), contentWidth - 70, 20),
                    titleX, cursorY + 14, 20, color(TEXT_PRIMARY),
                    UiCanvas.HorizontalAlign.LEFT, UiCanvas.VerticalAlign.MIDDLE);
            drawText(canvas, tierLabel(selectedIngredient.tier()) + "  •  Combat level " + selectedIngredient.level(),
                    titleX, cursorY + 37, 11, tierColor(selectedIngredient.tier()),
                    UiCanvas.HorizontalAlign.LEFT, UiCanvas.VerticalAlign.MIDDLE);
            String professions = selectedIngredient.skills().isEmpty()
                    ? "No crafting professions listed"
                    : selectedIngredient.skills().stream().map(IngredientGuideScreen::titleCase).reduce((a, b) -> a + ", " + b).orElse("");
            drawText(canvas, ellipsize(professions, contentWidth - 70, 10), titleX, cursorY + 53, 10,
                    color(TEXT_MUTED), UiCanvas.HorizontalAlign.LEFT, UiCanvas.VerticalAlign.MIDDLE);
            cursorY += 74;
            cursorY = renderIngredientEffects(canvas, contentX, cursorY, contentWidth);

            int locationCount = selectedIngredient.dropSources().stream()
                    .mapToInt(source -> source.locations().size())
                    .sum();
            canvas.strokeLine(contentX, cursorY, contentX + contentWidth, cursorY, 1, color(ACCENT_PRIMARY_DARK));
            cursorY += 18;
            drawText(canvas, "DROP SOURCES", contentX, cursorY, 11, color(ACCENT_PRIMARY),
                    UiCanvas.HorizontalAlign.LEFT, UiCanvas.VerticalAlign.MIDDLE);
            float showAllWidth = 104;
            if (locationCount > 0) {
                float showAllX = contentX + contentWidth - showAllWidth;
                float showAllY = cursorY - 11;
                drawButton(canvas, showAllX, showAllY, showAllWidth, 22, "Show all on map");
                showAllMapHitbox = new ActionHitbox(showAllX, showAllY, showAllWidth, 22);
            }
            drawText(canvas,
                    selectedIngredient.dropSources().size() + " mobs  •  " + locationCount + " spawn locations",
                    contentX + contentWidth - (locationCount > 0 ? showAllWidth + 8 : 0), cursorY, 10, color(TEXT_MUTED),
                    UiCanvas.HorizontalAlign.RIGHT, UiCanvas.VerticalAlign.MIDDLE);
            cursorY += 18;

            if (selectedIngredient.dropSources().isEmpty()) {
                canvas.fillRect(contentX, cursorY, contentWidth, 46, color(BACKGROUND_CONTENT));
                drawText(canvas, "No known drop sources.", contentX + 12, cursorY + 23,
                        11, color(TEXT_MUTED), UiCanvas.HorizontalAlign.LEFT, UiCanvas.VerticalAlign.MIDDLE);
                cursorY += 54;
            } else {
                for (int sourceIndex = 0; sourceIndex < selectedIngredient.dropSources().size(); sourceIndex++) {
                    DropSource source = selectedIngredient.dropSources().get(sourceIndex);
                    float cardHeight = source.locations().isEmpty() ? 52 : 34 + source.locations().size() * 25;
                    canvas.fillRect(contentX, cursorY, contentWidth, cardHeight, color(BACKGROUND_CONTENT));
                    drawText(canvas, ellipsize(source.name(), contentWidth - 24, 13), contentX + 11, cursorY + 16, 13,
                            color(TEXT_SECONDARY), UiCanvas.HorizontalAlign.LEFT, UiCanvas.VerticalAlign.MIDDLE);
                    if (source.locations().isEmpty()) {
                        drawText(canvas, "Spawn coordinates unavailable", contentX + 11, cursorY + 35, 10,
                                color(TEXT_MUTED), UiCanvas.HorizontalAlign.LEFT, UiCanvas.VerticalAlign.MIDDLE);
                    } else {
                        float locationY = cursorY + 29;
                        for (int locationIndex = 0; locationIndex < source.locations().size(); locationIndex++) {
                            SpawnLocation location = source.locations().get(locationIndex);
                            boolean hovered = contains(
                                    nvgMouseX,
                                    nvgMouseY,
                                    contentX + 8,
                                    locationY,
                                    contentWidth - 16,
                                    21);
                            canvas.fillRect(contentX + 8, locationY, contentWidth - 16, 21, hovered ? color(CONTROL_INPUT_HOVER) : color(CONTROL_INPUT));
                            String radius = location.radius() > 0
                                    ? "  •  " + location.radius() + " blocks radius"
                                    : "";
                            drawText(canvas, location.coordinates() + radius, contentX + 16, locationY + 10.5f, 10,
                                    color(TEXT_SECONDARY), UiCanvas.HorizontalAlign.LEFT, UiCanvas.VerticalAlign.MIDDLE);
                            drawText(canvas, "MAP", contentX + contentWidth - 52, locationY + 10.5f, 9,
                                    hovered ? color(ACCENT_PRIMARY_HOVER) : color(TEXT_MUTED),
                                    UiCanvas.HorizontalAlign.RIGHT, UiCanvas.VerticalAlign.MIDDLE);
                            drawText(canvas, "COPY", contentX + contentWidth - 16, locationY + 10.5f, 9,
                                    hovered ? color(ACCENT_PRIMARY_HOVER) : color(TEXT_MUTED),
                                    UiCanvas.HorizontalAlign.RIGHT, UiCanvas.VerticalAlign.MIDDLE);
                            if (locationY + 21 >= y && locationY <= y + height) {
                                locationHitboxes.add(new LocationHitbox(
                                        contentX + contentWidth - 78,
                                        contentX + contentWidth - 43,
                                        locationY,
                                        35,
                                        21,
                                        markerId(sourceIndex, locationIndex),
                                        location));
                            }
                            locationY += 25;
                        }
                    }
                    cursorY += cardHeight + 8;
                }
            }
            if (copyFeedback != null && System.currentTimeMillis() < copyFeedbackUntilMs) {
                drawText(canvas, copyFeedback, contentX, cursorY + 7, 10, color(ACCENT_PRIMARY),
                        UiCanvas.HorizontalAlign.LEFT, UiCanvas.VerticalAlign.MIDDLE);
                cursorY += 18;
            }
        } finally {
            canvas.resetScissor();
        }
        float contentHeight = cursorY + detailScroll - contentTop;
        maxDetailScroll = Math.max(0, contentHeight - viewportHeight);
        detailScroll = clamp(detailScroll, 0, maxDetailScroll);
        drawScrollbar(
                canvas,
                x + width - 5,
                y + 8,
                height - 16,
                detailScroll,
                maxDetailScroll,
                ScrollbarTarget.INGREDIENT_DETAIL);
    }

    private float renderIngredientEffects(
            UiCanvas canvas, float contentX, float cursorY, float contentWidth) {
        List<EffectLine> statLines = selectedIngredient.effects().stream()
                .map(effect -> new EffectLine(
                        effectDisplayName(effect.apiName()),
                        formatEffectRange(effect)))
                .toList();
        List<EffectLine> modifierLines = craftingModifierLines(selectedIngredient.craftingModifiers());
        int effectCount = statLines.size() + modifierLines.size();

        canvas.strokeLine(contentX, cursorY, contentX + contentWidth, cursorY, 1, color(ACCENT_PRIMARY_DARK));
        cursorY += 18;
        drawText(canvas, "EFFECTS", contentX, cursorY, 11, color(ACCENT_PRIMARY),
                UiCanvas.HorizontalAlign.LEFT, UiCanvas.VerticalAlign.MIDDLE);
        drawText(canvas,
                effectCount + (effectCount == 1 ? " effect" : " effects"),
                contentX + contentWidth,
                cursorY,
                10,
                color(TEXT_MUTED),
                UiCanvas.HorizontalAlign.RIGHT,
                UiCanvas.VerticalAlign.MIDDLE);
        cursorY += 18;

        if (effectCount == 0) {
            canvas.fillRect(contentX, cursorY, contentWidth, 42, color(BACKGROUND_CONTENT));
            drawText(canvas,
                    "No crafting effects.",
                    contentX + 11,
                    cursorY + 21,
                    11,
                    color(TEXT_MUTED),
                    UiCanvas.HorizontalAlign.LEFT,
                    UiCanvas.VerticalAlign.MIDDLE);
            return cursorY + 52;
        }
        if (!modifierLines.isEmpty()) {
            cursorY = renderEffectGroup(canvas, contentX, cursorY, contentWidth, modifierLines);
        }
        if (!statLines.isEmpty()) {
            if (!modifierLines.isEmpty()) {
                cursorY += 10;
            }
            cursorY = renderEffectGroup(canvas, contentX, cursorY, contentWidth, statLines);
        }
        return cursorY + 10;
    }

    private float renderEffectGroup(
            UiCanvas canvas,
            float x,
            float y,
            float width,
            List<EffectLine> lines) {
        float rowHeight = 22;
        float cardHeight = 8 + lines.size() * rowHeight;
        canvas.fillRect(x, y, width, cardHeight, color(BACKGROUND_CONTENT));
        for (int index = 0; index < lines.size(); index++) {
            EffectLine line = lines.get(index);
            float rowY = y + 4 + index * rowHeight;
            if (index > 0) {
                canvas.strokeLine(x + 9, rowY, x + width - 9, rowY, 1, color(ACCENT_PRIMARY_DARK));
            }
            float valueWidth = UiRenderer.measureText(
                            line.value(),
                            SeqClient.getFontManager().getSelectedFont(),
                            10)
                    .width();
            String label = ellipsize(line.label() + ":", Math.max(1, width - valueWidth - 26), 10);
            drawText(canvas,
                    label,
                    x + 10,
                    rowY + rowHeight / 2f,
                    10,
                    color(TEXT_SECONDARY),
                    UiCanvas.HorizontalAlign.LEFT,
                    UiCanvas.VerticalAlign.MIDDLE);
            float labelWidth = UiRenderer.measureText(
                            label,
                            SeqClient.getFontManager().getSelectedFont(),
                            10)
                    .width();
            drawText(canvas,
                    line.value(),
                    x + 10 + labelWidth + 6,
                    rowY + rowHeight / 2f,
                    10,
                    color(TEXT_PRIMARY),
                    UiCanvas.HorizontalAlign.LEFT,
                    UiCanvas.VerticalAlign.MIDDLE);
        }
        return y + cardHeight;
    }

    private static List<EffectLine> craftingModifierLines(CraftingModifiers modifiers) {
        List<EffectLine> lines = new ArrayList<>();
        if (modifiers.duration() != 0) {
            lines.add(new EffectLine(
                    "Consumable duration",
                    formatSigned(modifiers.duration()) + " sec"));
        }
        if (modifiers.charges() != 0) {
            lines.add(new EffectLine(
                    "Consumable charges",
                    formatSigned(modifiers.charges())));
        }
        if (modifiers.durability() != 0) {
            lines.add(new EffectLine(
                    "Item durability",
                    formatSigned(modifiers.durability())));
        }
        for (Modifier requirement : modifiers.requirements()) {
            lines.add(new EffectLine(
                    effectDisplayName(requirement.apiName()),
                    formatSigned(requirement.value())));
        }
        for (Modifier position : modifiers.positions()) {
            lines.add(new EffectLine(
                    "Effectiveness: " + positionLabel(position.apiName()),
                    formatSigned(position.value()) + "%"));
        }
        return List.copyOf(lines);
    }

    private static String formatEffectRange(Effect effect) {
        String unit = effectUnit(effect.apiName());
        if (effect.min() == effect.max()) {
            return formatSigned(effect.min()) + unit;
        }
        return formatSigned(effect.min()) + " to " + formatSigned(effect.max()) + unit;
    }

    private static String effectUnit(String apiName) {
        return switch (apiName) {
            case "lifeSteal", "manaSteal", "poison" -> "/3s";
            case "healthRegenRaw", "manaRegen" -> "/5s";
            case "rawAttackSpeed" -> " tier";
            case "jumpHeight", "mainAttackRange" -> "";
            default -> apiName.startsWith("raw") || apiName.endsWith("Raw") ? "" : "%";
        };
    }

    private static String effectDisplayName(String apiName) {
        if (apiName == null || apiName.isBlank()) {
            return "Unknown effect";
        }
        String special = switch (apiName) {
            case "combatExperience" -> "Combat XP Bonus";
            case "gatherXpBonus" -> "Gathering XP Bonus";
            case "gatherSpeed" -> "Gathering Speed";
            default -> null;
        };
        if (special != null) {
            return special;
        }
        String normalized = apiName;
        if (normalized.startsWith("raw")
                && normalized.length() > 3
                && Character.isUpperCase(normalized.charAt(3))) {
            normalized = normalized.substring(3);
        }
        if (normalized.endsWith("Raw") && normalized.length() > 3) {
            normalized = normalized.substring(0, normalized.length() - 3);
        }
        normalized = normalized
                .replaceAll("([a-z0-9])([A-Z])", "$1 $2")
                .replace('_', ' ');
        String[] words = normalized.split("\\s+");
        StringBuilder displayName = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            if (!displayName.isEmpty()) {
                displayName.append(' ');
            }
            displayName.append(word.equalsIgnoreCase("xp")
                    ? "XP"
                    : Character.toUpperCase(word.charAt(0)) + word.substring(1));
        }
        return displayName.toString();
    }

    private static String positionLabel(String apiName) {
        return switch (apiName) {
            case "left" -> "left";
            case "right" -> "right";
            case "above" -> "above";
            case "under" -> "under";
            case "touching" -> "touching";
            case "notTouching" -> "not touching";
            default -> effectDisplayName(apiName).toLowerCase(Locale.ROOT);
        };
    }

    private static String formatSigned(int value) {
        return value > 0 ? "+" + value : Integer.toString(value);
    }

    private void refreshVisibleIngredients() {
        IngredientGuideManager.Snapshot snapshot = manager.snapshot();
        if (snapshot.version() == observedSnapshotVersion && searchQuery.equals(observedQuery)) {
            return;
        }
        String selectedName = selectedIngredient == null ? null : selectedIngredient.internalName();
        visibleIngredients = sortedFilteredIngredients(snapshot);
        selectedIngredient = visibleIngredients.stream()
                .filter(ingredient -> ingredient.internalName().equals(selectedName))
                .findFirst()
                .orElse(visibleIngredients.isEmpty() ? null : visibleIngredients.getFirst());
        observedSnapshotVersion = snapshot.version();
        observedQuery = searchQuery;
        listScroll = 0;
        detailScroll = 0;
    }

    private List<Entry> farmSpotIngredientPreviews(IngredientFarmSpot spot) {
        refreshFarmSpotIngredientLookup();
        return IngredientFarmSpotDisplay.resolve(
                spot,
                name -> cachedFarmSpotIngredientsByName.get(name.toLowerCase(Locale.ROOT)));
    }

    private void refreshFarmSpotIngredientLookup() {
        IngredientGuideManager.Snapshot snapshot = manager.snapshot();
        if (snapshot.version() == cachedFarmSpotIngredientSnapshotVersion) {
            return;
        }
        Map<String, IngredientGuideEntry> ingredientsByName = new HashMap<>();
        for (IngredientGuideEntry ingredient : snapshot.ingredients()) {
            ingredientsByName.put(ingredient.displayName().toLowerCase(Locale.ROOT), ingredient);
            ingredientsByName.put(ingredient.internalName().toLowerCase(Locale.ROOT), ingredient);
        }
        cachedFarmSpotIngredientsByName = Map.copyOf(ingredientsByName);
        cachedFarmSpotIngredientSnapshotVersion = snapshot.version();
    }

    private void drawFarmSpotIngredientPreview(
            UiCanvas canvas,
            Entry preview,
            float x,
            float y,
            float size,
            float viewportTop,
            float viewportBottom) {
        if (preview.ingredient() == null) {
            drawText(canvas, "✦", x + size / 2f, y + size / 2f, size * 0.58f, color(TEXT_MUTED),
                    UiCanvas.HorizontalAlign.CENTER, UiCanvas.VerticalAlign.MIDDLE);
            return;
        }
        CachedIngredientIcon icon = cachedItemIcon(preview.ingredient());
        if (!icon.stack().isEmpty()) {
            if (y >= viewportTop && y + size <= viewportBottom) {
                itemIconOverlays.add(new IngredientIconOverlay(icon, x, y, size));
            }
            return;
        }
        drawText(canvas, "✦", x + size / 2f, y + size / 2f, size * 0.58f,
                tierColor(preview.ingredient().tier()),
                UiCanvas.HorizontalAlign.CENTER, UiCanvas.VerticalAlign.MIDDLE);
    }

    private CachedIngredientIcon cachedItemIcon(IngredientGuideEntry ingredient) {
        return itemIconCache.computeIfAbsent(ingredient.icon().cacheKey(), ignored -> {
            ItemStack stack = IngredientItemIconFactory.create(ingredient.icon());
            var skinProfile = IngredientItemIconFactory.skinProfile(ingredient.icon());
            Supplier<PlayerSkin> skinLookup = skinProfile == null
                    ? null
                    : SeqClient.mc.getSkinManager().createLookup(skinProfile, false);
            return new CachedIngredientIcon(stack, skinLookup);
        });
    }

    @Override
    public void renderMinecraftGuiOverlay(GuiGraphics guiGraphics, UiRenderMetrics metrics) {
        if (itemIconOverlays.isEmpty()) {
            return;
        }
        float guiUnitsPerUiUnit = metrics.pixelRatio() / (float) metrics.minecraftGuiScale();
        for (IngredientIconOverlay overlay : itemIconOverlays) {
            float itemScale = overlay.size() * guiUnitsPerUiUnit / 16f;
            guiGraphics.pose().pushMatrix();
            try {
                guiGraphics.pose().translate(
                        overlay.x() * guiUnitsPerUiUnit,
                        overlay.y() * guiUnitsPerUiUnit);
                guiGraphics.pose().scale(itemScale, itemScale);
                if (overlay.icon().skinLookup() != null) {
                    PlayerFaceRenderer.draw(guiGraphics, overlay.icon().skinLookup().get(), 0, 0, 16);
                } else {
                    guiGraphics.renderItem(overlay.icon().stack(), 0, 0);
                }
            } finally {
                guiGraphics.pose().popMatrix();
            }
        }
    }

    @Override
    public boolean mouseClicked(@NotNull MouseButtonEvent click, boolean outsideScreen) {
        if (click.button() != 0) {
            return super.mouseClicked(click, outsideScreen);
        }
        float mx = MinecraftUiRenderer.mouseX(click.x());
        float my = MinecraftUiRenderer.mouseY(click.y());
        float screenWidth = MinecraftUiRenderer.screenWidth();
        float screenHeight = MinecraftUiRenderer.screenHeight();
        if (SequoiaSidebarNavigation.click(mx, my, screenHeight,
                SequoiaSidebarNavigation.Destination.INGREDIENTS, parent)) return true;
        GuideLayout guide = currentLayout(screenWidth, screenHeight);
        float panelTop = guide.list().y();
        float panelHeight = guide.list().height();
        float listWidth = guide.list().width();
        float listX = guide.list().x();
        float detailX = guide.detail().x();
        float detailWidth = guide.detail().width();

        if (handleSearchScopeClick(mx, my, guide)) return true;
        if (guide.category().contains(mx, my)) {
            GuideCategory nextCategory = mx < guide.category().x() + guide.category().width() / 2
                    ? GuideCategory.INGREDIENTS
                    : GuideCategory.TOTEM_SPOTS;
            if (nextCategory != guideCategory) {
                guideCategory = nextCategory;
                searchScopeDropdownOpen = false;
                searchFocused = false;
                searchQuerySelected = false;
                listScroll = 0;
                detailScroll = 0;
                draggedScrollbar = null;
                openSortDropdown = null;
            }
            return true;
        }
        if (guide.refresh().contains(mx, my)) {
            openSortDropdown = null;
            manager.requestRefresh(true);
            return true;
        }

        if (guideCategory == GuideCategory.TOTEM_SPOTS) {
            searchFocused = false;
            float rowsTop = panelTop + 32;
            float rowsHeight = Math.max(0, panelHeight - 40);
            if (startScrollbarDrag(
                    ScrollbarTarget.INGREDIENT_LIST,
                    scrollbarGeometry(
                            listX + listWidth - 5,
                            rowsTop,
                            rowsHeight,
                            listScroll,
                            maxListScroll),
                    mx,
                    my,
                    listScroll)) {
                return true;
            }
            if (contains(mx, my, listX, rowsTop, listWidth, rowsHeight)) {
                int index = (int) ((my - rowsTop + listScroll) / FARM_SPOT_ROW_HEIGHT);
                List<IngredientFarmSpot> spots = IngredientFarmSpotCatalog.all();
                if (index >= 0 && index < spots.size()) {
                    IngredientFarmSpot nextSpot = spots.get(index);
                    if (!nextSpot.equals(selectedFarmSpot)) {
                        selectedFarmSpot = nextSpot;
                        detailScroll = 0;
                    }
                    return true;
                }
            }
            if (startScrollbarDrag(ScrollbarTarget.INGREDIENT_DETAIL,
                    scrollbarGeometry(detailX + detailWidth - 5, guide.detail().y() + 8,
                            Math.max(0, guide.detail().height() - 16), detailScroll, maxDetailScroll),
                    mx, my, detailScroll)) return true;
            if (!guide.detail().contains(mx, my)) return true;
            if (showFarmSpotMapHitbox != null && showFarmSpotMapHitbox.contains(mx, my)) {
                openFarmSpotMap();
                return true;
            }
            return super.mouseClicked(click, outsideScreen);
        }

        if (guide.search().contains(mx, my)) {
            openSortDropdown = null;
            searchFocused = true;
            searchQuerySelected = false;
            return true;
        }
        searchFocused = false;
        searchQuerySelected = false;

        IngredientListLayout layout = ingredientListLayout(panelTop, hasSecondarySort());
        float sortX = listX + 9;
        float sortWidth = listWidth - 18;
        if (handleOpenSortDropdownClick(mx, my, sortX, sortWidth, layout)) {
            return true;
        }
        float rowsTop = layout.rowsTop();
        float rowsHeight = Math.max(0, panelTop + panelHeight - rowsTop - 8);
        if (startScrollbarDrag(
                ScrollbarTarget.INGREDIENT_LIST,
                scrollbarGeometry(
                        listX + listWidth - 5,
                        rowsTop,
                        rowsHeight,
                        listScroll,
                        maxListScroll),
                mx,
                my,
                listScroll)) {
            return true;
        }
        if (startScrollbarDrag(
                ScrollbarTarget.INGREDIENT_DETAIL,
                scrollbarGeometry(
                        detailX + detailWidth - 5,
                        guide.detail().y() + 8,
                        Math.max(0, guide.detail().height() - 16),
                        detailScroll,
                        maxDetailScroll),
                mx,
                my,
                detailScroll)) {
            return true;
        }

        if (contains(mx, my, sortX, layout.primarySortY(), sortWidth, SORT_ROW_HEIGHT)) {
            if (mx >= sortX + sortWidth - SORT_DIRECTION_WIDTH) {
                primarySortDirection = primarySortDirection.toggled();
                openSortDropdown = null;
            } else {
                openSortDropdown = SortDropdown.PRIMARY;
                return true;
            }
            saveSortSettings();
            resortVisibleIngredients();
            return true;
        }
        if (hasSecondarySort()
                && contains(mx, my, sortX, layout.secondarySortY(), sortWidth, SORT_ROW_HEIGHT)) {
            if (mx >= sortX + sortWidth - SORT_DIRECTION_WIDTH) {
                secondarySortDirection = secondarySortDirection.toggled();
                openSortDropdown = null;
            } else {
                openSortDropdown = SortDropdown.SECONDARY;
                return true;
            }
            saveSortSettings();
            resortVisibleIngredients();
            return true;
        }

        if (contains(mx, my, listX, rowsTop, listWidth, rowsHeight)) {
            int index = (int) ((my - rowsTop + listScroll) / ROW_HEIGHT);
            if (index >= 0 && index < visibleIngredients.size()) {
                selectedIngredient = visibleIngredients.get(index);
                detailScroll = 0;
                return true;
            }
        }
        if (!guide.detail().contains(mx, my)) return true;
        for (LocationHitbox hitbox : locationHitboxes) {
            if (hitbox.containsMap(mx, my)) {
                openIngredientMap(hitbox.markerId());
                return true;
            }
            if (hitbox.containsCopy(mx, my)) {
                SeqClient.mc.keyboardHandler.setClipboard(hitbox.location().coordinates());
                copyFeedback = "Copied " + hitbox.location().coordinates();
                copyFeedbackUntilMs = System.currentTimeMillis() + 2_000L;
                return true;
            }
        }
        if (showAllMapHitbox != null && showAllMapHitbox.contains(mx, my)) {
            openIngredientMap(null);
            return true;
        }
        return super.mouseClicked(click, outsideScreen);
    }

    @Override
    public boolean mouseReleased(@NotNull MouseButtonEvent click) {
        boolean wasDragging = draggedScrollbar != null;
        draggedScrollbar = null;
        if (wasDragging) {
            return true;
        }
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent click, double deltaX, double deltaY) {
        if (draggedScrollbar == null) {
            return super.mouseDragged(click, deltaX, deltaY);
        }

        float screenWidth = MinecraftUiRenderer.screenWidth();
        float screenHeight = MinecraftUiRenderer.screenHeight();
        GuideLayout guide = currentLayout(screenWidth, screenHeight);
        float panelTop = guide.list().y();
        float panelHeight = guide.list().height();
        IngredientListLayout listLayout = ingredientListLayout(panelTop, hasSecondarySort());
        float trackHeight;
        float maxScroll;
        if (draggedScrollbar == ScrollbarTarget.INGREDIENT_LIST) {
            trackHeight = guideCategory == GuideCategory.TOTEM_SPOTS
                    ? Math.max(0, panelHeight - 40)
                    : Math.max(0, panelTop + panelHeight - listLayout.rowsTop() - 8);
            maxScroll = maxListScroll;
        } else {
            trackHeight = Math.max(0, guide.detail().height() - 16);
            maxScroll = maxDetailScroll;
        }

        float thumbHeight = scrollbarThumbHeight(trackHeight, maxScroll);
        float scrollRange = trackHeight - thumbHeight;
        if (maxScroll <= 0 || scrollRange <= 0) {
            return true;
        }

        float mouseY = MinecraftUiRenderer.mouseY(click.y());
        float nextScroll = scrollbarDragStartOffset
                + ((mouseY - scrollbarDragStartY) / scrollRange) * maxScroll;
        if (draggedScrollbar == ScrollbarTarget.INGREDIENT_LIST) {
            listScroll = clamp(nextScroll, 0, maxListScroll);
        } else {
            detailScroll = clamp(nextScroll, 0, maxDetailScroll);
        }
        return true;
    }

    private void openIngredientMap(String selectedMarkerId) {
        if (selectedIngredient == null) {
            return;
        }
        List<MapFocus.Marker> markers = new ArrayList<>();
        for (int sourceIndex = 0; sourceIndex < selectedIngredient.dropSources().size(); sourceIndex++) {
            DropSource source = selectedIngredient.dropSources().get(sourceIndex);
            for (int locationIndex = 0; locationIndex < source.locations().size(); locationIndex++) {
                SpawnLocation location = source.locations().get(locationIndex);
                markers.add(new MapFocus.Marker(
                        markerId(sourceIndex, locationIndex),
                        selectedIngredient.displayName(),
                        source.name(),
                        location.x(),
                        location.y(),
                        location.z(),
                        location.radius()));
            }
        }
        if (!markers.isEmpty()) {
            SeqClient.mc.setScreen(new WorldMapScreen(
                    this,
                    new MapFocus(selectedIngredient.displayName(), markers, selectedMarkerId),
                    IngredientItemIconFactory.create(selectedIngredient.icon()),
                    IngredientItemIconFactory.skinProfile(selectedIngredient.icon())));
        }
    }

    private void openFarmSpotMap() {
        if (selectedFarmSpot != null) {
            SeqClient.mc.setScreen(new WorldMapScreen(this, selectedFarmSpot));
        }
    }

    private static String markerId(int sourceIndex, int locationIndex) {
        return sourceIndex + ":" + locationIndex;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (searchScopeDropdownOpen) return true;
        float mx = MinecraftUiRenderer.mouseX(mouseX);
        float my = MinecraftUiRenderer.mouseY(mouseY);
        GuideLayout guide = currentLayout(MinecraftUiRenderer.screenWidth(), MinecraftUiRenderer.screenHeight());
        if (guide.list().contains(mx, my)) {
            listScroll = clamp(listScroll - (float) scrollY * SCROLL_STEP, 0, maxListScroll);
        } else if (guide.detail().contains(mx, my)) {
            detailScroll = clamp(detailScroll - (float) scrollY * SCROLL_STEP, 0, maxDetailScroll);
        } else {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        return true;
    }

    @Override
    public boolean keyPressed(@NotNull KeyEvent keyEvent) {
        int key = keyEvent.key();
        if (key == GLFW.GLFW_KEY_ESCAPE && searchScopeDropdownOpen) {
            searchScopeDropdownOpen = false;
            return true;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE && openSortDropdown != null) {
            openSortDropdown = null;
            return true;
        }
        if (searchFocused) {
            if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                searchFocused = false;
                searchQuerySelected = false;
                return true;
            }
            boolean shortcutModifier =
                    (keyEvent.modifiers() & (GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_SUPER)) != 0;
            if (shortcutModifier && key == GLFW.GLFW_KEY_A) {
                searchQuerySelected = !searchQuery.isEmpty();
                return true;
            }
            if (key == GLFW.GLFW_KEY_BACKSPACE) {
                if (searchQuerySelected) {
                    searchQuery = "";
                } else if (!searchQuery.isEmpty()) {
                    searchQuery = searchQuery.substring(0, searchQuery.length() - 1);
                }
                searchQuerySelected = false;
                return true;
            }
            if (shortcutModifier && key == GLFW.GLFW_KEY_V) {
                String clipboard = SeqClient.mc.keyboardHandler.getClipboard();
                if (clipboard != null) {
                    String pastedText = clipboard.replaceAll("\\p{Cntrl}", "");
                    searchQuery = searchQuerySelected ? pastedText : searchQuery + pastedText;
                    if (searchQuery.length() > 80) {
                        searchQuery = searchQuery.substring(0, 80);
                    }
                }
                searchQuerySelected = false;
                return true;
            }
            return true;
        }
        if (guideCategory == GuideCategory.INGREDIENTS && key == GLFW.GLFW_KEY_SLASH) {
            searchScopeDropdownOpen = false;
            searchFocused = true;
            searchQuerySelected = false;
            return true;
        }
        return super.keyPressed(keyEvent);
    }

    @Override
    public boolean charTyped(@NotNull CharacterEvent characterEvent) {
        if (!searchFocused) {
            return super.charTyped(characterEvent);
        }
        String typedText = TextInputHelper.getTypedText(characterEvent);
        if (typedText != null) {
            if (searchQuerySelected) {
                searchQuery = "";
            }
            if (searchQuery.length() + typedText.length() <= 80) {
                searchQuery += typedText;
            }
            searchQuerySelected = false;
        }
        return true;
    }

    @Override
    public void onClose() {
        SeqClient.mc.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void drawButton(UiCanvas canvas, float x, float y, float width, float height, String label) {
        boolean hovered = contains(nvgMouseX, nvgMouseY, x, y, width, height);
        canvas.fillRect(x, y, width, height, hovered ? color(CONTROL_INPUT_HOVER) : color(CONTROL_INPUT));
        canvas.strokeRect(x, y, width, height, 1, color(ACCENT_DIVIDER));
        drawText(canvas, label, x + width / 2f, y + height / 2f, 12,
                hovered ? color(ACCENT_PRIMARY_HOVER) : color(TEXT_SECONDARY),
                UiCanvas.HorizontalAlign.CENTER, UiCanvas.VerticalAlign.MIDDLE);
    }

    private void drawGuideCategoryControl(UiCanvas canvas, float x, float y, float width) {
        float segmentWidth = width / 2f;
        for (int index = 0; index < GuideCategory.values().length; index++) {
            GuideCategory category = GuideCategory.values()[index];
            float segmentX = x + index * segmentWidth;
            boolean active = guideCategory == category;
            boolean hovered = contains(nvgMouseX, nvgMouseY, segmentX, y, segmentWidth, SEARCH_HEIGHT);
            canvas.fillRect(
                    segmentX,
                    y,
                    segmentWidth,
                    SEARCH_HEIGHT,
                    active
                            ? color(ACCENT_PRIMARY_DARK)
                            : hovered ? color(CONTROL_INPUT_HOVER) : color(CONTROL_INPUT));
            canvas.strokeRect(segmentX, y, segmentWidth, SEARCH_HEIGHT, 1, color(ACCENT_DIVIDER));
            drawText(
                    canvas,
                    category.label(),
                    segmentX + segmentWidth / 2f,
                    y + SEARCH_HEIGHT / 2,
                    12,
                    color(TEXT_PRIMARY),
                    UiCanvas.HorizontalAlign.CENTER,
                    UiCanvas.VerticalAlign.MIDDLE);
        }
    }

    private void drawSortRow(
            UiCanvas canvas,
            float x,
            float y,
            float width,
            SortKey key,
            SortDirection direction) {
        float keyWidth = Math.max(1, width - SORT_DIRECTION_WIDTH);
        boolean keyHovered = contains(nvgMouseX, nvgMouseY, x, y, keyWidth, SORT_ROW_HEIGHT);
        boolean directionHovered =
                contains(nvgMouseX, nvgMouseY, x + keyWidth, y, SORT_DIRECTION_WIDTH, SORT_ROW_HEIGHT);
        canvas.fillRect(x, y, width, SORT_ROW_HEIGHT, keyHovered || directionHovered ? color(CONTROL_INPUT_HOVER) : color(CONTROL_INPUT));
        canvas.strokeRect(x, y, width, SORT_ROW_HEIGHT, 1, color(ACCENT_DIVIDER));
        canvas.strokeLine(
                x + keyWidth,
                y + 3,
                x + keyWidth,
                y + SORT_ROW_HEIGHT - 3,
                1,
                color(ACCENT_PRIMARY_DARK));
        drawText(
                canvas,
                key.label(),
                x + 8,
                y + SORT_ROW_HEIGHT / 2f,
                10,
                keyHovered ? color(ACCENT_PRIMARY_HOVER) : color(TEXT_SECONDARY),
                UiCanvas.HorizontalAlign.LEFT,
                UiCanvas.VerticalAlign.MIDDLE);
        drawText(
                canvas,
                "v",
                x + keyWidth - 8,
                y + SORT_ROW_HEIGHT / 2f,
                9,
                keyHovered ? color(ACCENT_PRIMARY_HOVER) : color(TEXT_MUTED),
                UiCanvas.HorizontalAlign.RIGHT,
                UiCanvas.VerticalAlign.MIDDLE);
        drawText(
                canvas,
                direction.symbol() + " " + direction.label(),
                x + width - 7,
                y + SORT_ROW_HEIGHT / 2f,
                9,
                directionHovered ? color(ACCENT_PRIMARY_HOVER) : color(TEXT_MUTED),
                UiCanvas.HorizontalAlign.RIGHT,
                UiCanvas.VerticalAlign.MIDDLE);
    }

    private Bounds sortMenuBounds(float x, float width, IngredientListLayout layout) {
        if (openSortDropdown == null || (openSortDropdown == SortDropdown.SECONDARY && !hasSecondarySort())) return null;
        float anchor = openSortDropdown == SortDropdown.PRIMARY ? layout.primarySortY() : layout.secondarySortY();
        return new Bounds(x, anchor + SORT_ROW_HEIGHT + 2, Math.max(1, width - SORT_DIRECTION_WIDTH),
                sortOptions(openSortDropdown).size() * SORT_OPTION_HEIGHT);
    }

    private void drawOpenSortDropdown(
            UiCanvas canvas, float x, float width, IngredientListLayout layout) {
        if (openSortDropdown == null
                || (openSortDropdown == SortDropdown.SECONDARY && !hasSecondarySort())) {
            return;
        }
        float menuWidth = Math.max(1, width - SORT_DIRECTION_WIDTH);
        float anchorY = openSortDropdown == SortDropdown.PRIMARY
                ? layout.primarySortY()
                : layout.secondarySortY();
        float menuY = anchorY + SORT_ROW_HEIGHT + 2;
        List<SortKey> options = sortOptions(openSortDropdown);
        SortKey selectedKey = openSortDropdown == SortDropdown.PRIMARY ? primarySortKey : secondarySortKey;
        float menuBottom = menuY + options.size() * SORT_OPTION_HEIGHT;
        itemIconOverlays.removeIf(icon -> icon.x() < x + menuWidth && icon.x() + icon.size() > x
                && icon.y() < menuBottom && icon.y() + icon.size() > menuY);
        for (int index = 0; index < options.size(); index++) {
            SortKey option = options.get(index);
            float optionY = menuY + index * SORT_OPTION_HEIGHT;
            boolean selected = option == selectedKey;
            boolean hovered = contains(nvgMouseX, nvgMouseY, x, optionY, menuWidth, SORT_OPTION_HEIGHT);
            canvas.fillRect(
                    x,
                    optionY,
                    menuWidth,
                    SORT_OPTION_HEIGHT,
                    selected
                            ? color(BACKGROUND_CONTENT_FOCUSED)
                            : hovered ? color(CONTROL_INPUT_HOVER) : color(CONTROL_INPUT));
            canvas.strokeRect(x, optionY, menuWidth, SORT_OPTION_HEIGHT, 1, color(ACCENT_DIVIDER));
            drawText(
                    canvas,
                    option.label(),
                    x + 8,
                    optionY + SORT_OPTION_HEIGHT / 2f,
                    10,
                    selected ? color(ACCENT_PRIMARY) : color(TEXT_SECONDARY),
                    UiCanvas.HorizontalAlign.LEFT,
                    UiCanvas.VerticalAlign.MIDDLE);
        }
    }

    private boolean handleOpenSortDropdownClick(
            float mouseX,
            float mouseY,
            float x,
            float width,
            IngredientListLayout layout) {
        if (openSortDropdown == null) {
            return false;
        }
        if (openSortDropdown == SortDropdown.SECONDARY && !hasSecondarySort()) {
            openSortDropdown = null;
            return false;
        }
        float menuWidth = Math.max(1, width - SORT_DIRECTION_WIDTH);
        float anchorY = openSortDropdown == SortDropdown.PRIMARY
                ? layout.primarySortY()
                : layout.secondarySortY();
        if (contains(mouseX, mouseY, x, anchorY, menuWidth, SORT_ROW_HEIGHT)) {
            openSortDropdown = null;
            return true;
        }
        List<SortKey> options = sortOptions(openSortDropdown);
        float menuY = anchorY + SORT_ROW_HEIGHT + 2;
        float menuHeight = options.size() * SORT_OPTION_HEIGHT;
        if (!contains(mouseX, mouseY, x, menuY, menuWidth, menuHeight)) {
            openSortDropdown = null;
            return false;
        }
        int optionIndex = (int) ((mouseY - menuY) / SORT_OPTION_HEIGHT);
        if (optionIndex < 0 || optionIndex >= options.size()) {
            return true;
        }
        applySortSelection(openSortDropdown, options.get(optionIndex));
        openSortDropdown = null;
        saveSortSettings();
        resortVisibleIngredients();
        return true;
    }

    private void applySortSelection(SortDropdown dropdown, SortKey selectedKey) {
        if (dropdown == SortDropdown.SECONDARY) {
            secondarySortKey = selectedKey;
            return;
        }
        SortKey previousPrimary = primarySortKey;
        primarySortKey = selectedKey;
        if (primarySortKey == secondarySortKey && primarySortKey != SortKey.ALPHABETICAL) {
            secondarySortKey = previousPrimary == primarySortKey
                    ? firstSortKeyOtherThan(primarySortKey)
                    : previousPrimary;
        }
    }

    private List<SortKey> sortOptions(SortDropdown dropdown) {
        List<SortKey> options = new ArrayList<>();
        for (SortKey key : SortKey.values()) {
            if (dropdown == SortDropdown.PRIMARY || key != primarySortKey) {
                options.add(key);
            }
        }
        return options;
    }

    private static SortKey firstSortKeyOtherThan(SortKey excluded) {
        for (SortKey key : SortKey.values()) {
            if (key != excluded) {
                return key;
            }
        }
        return excluded;
    }

    private boolean hasSecondarySort() {
        return primarySortKey != SortKey.ALPHABETICAL;
    }

    private List<IngredientGuideEntry> sortedFilteredIngredients(IngredientGuideManager.Snapshot snapshot) {
        return IngredientGuideManager.sort(
                IngredientGuideManager.filter(snapshot.ingredients(), searchQuery, searchScope),
                primarySortKey,
                primarySortDirection,
                secondarySortKey,
                secondarySortDirection);
    }

    private void saveSortSettings() {
        sessionSettings.setSortOrder(
                primarySortKey,
                primarySortDirection,
                secondarySortKey,
                secondarySortDirection);
    }

    private void resortVisibleIngredients() {
        String selectedName = selectedIngredient == null ? null : selectedIngredient.internalName();
        visibleIngredients = sortedFilteredIngredients(manager.snapshot());
        selectedIngredient = visibleIngredients.stream()
                .filter(ingredient -> ingredient.internalName().equals(selectedName))
                .findFirst()
                .orElse(visibleIngredients.isEmpty() ? null : visibleIngredients.getFirst());
        listScroll = 0;
    }

    private static IngredientListLayout ingredientListLayout(float panelY, boolean showSecondarySort) {
        float primarySortY = panelY;
        float secondarySortY = primarySortY + SORT_ROW_HEIGHT + SORT_ROW_GAP;
        float finalSortY = showSecondarySort ? secondarySortY : primarySortY;
        float summaryY = finalSortY + SORT_ROW_HEIGHT + 11;
        return new IngredientListLayout(primarySortY, secondarySortY, summaryY, summaryY + 12);
    }

    private void drawScrollbar(
            UiCanvas canvas,
            float x,
            float y,
            float height,
            float scroll,
            float maxScroll,
            ScrollbarTarget target) {
        ScrollbarGeometry geometry = scrollbarGeometry(x, y, height, scroll, maxScroll);
        if (geometry == null) {
            return;
        }
        boolean interactive = target == draggedScrollbar || geometry.containsTrack(nvgMouseX, nvgMouseY);
        float visualWidth = interactive ? 5 : SCROLLBAR_WIDTH;
        float visualX = geometry.x() - (visualWidth - SCROLLBAR_WIDTH) / 2f;
        canvas.fillRect(visualX, geometry.y(), visualWidth, geometry.height(), color(CONTROL_TRACK));
        canvas.fillRect(visualX, geometry.thumbY(), visualWidth, geometry.thumbHeight(), interactive ? color(ACCENT_PRIMARY_HOVER) : color(CONTROL_THUMB));
    }

    private boolean startScrollbarDrag(
            ScrollbarTarget target,
            ScrollbarGeometry geometry,
            float mouseX,
            float mouseY,
            float currentScroll) {
        if (geometry == null || !geometry.containsTrack(mouseX, mouseY)) {
            return false;
        }
        draggedScrollbar = target;
        scrollbarDragStartY = mouseY;
        scrollbarDragStartOffset = currentScroll;
        return true;
    }

    private static ScrollbarGeometry scrollbarGeometry(
            float x, float y, float height, float scroll, float maxScroll) {
        if (maxScroll <= 0 || height <= 0) {
            return null;
        }
        float thumbHeight = scrollbarThumbHeight(height, maxScroll);
        float thumbY = y + (height - thumbHeight) * (clamp(scroll, 0, maxScroll) / maxScroll);
        return new ScrollbarGeometry(x, y, height, thumbY, thumbHeight);
    }

    private static float scrollbarThumbHeight(float trackHeight, float maxScroll) {
        if (trackHeight <= 0) {
            return 0;
        }
        if (maxScroll <= 0) {
            return trackHeight;
        }
        return Math.min(
                trackHeight,
                Math.max(MIN_SCROLLBAR_THUMB_HEIGHT, trackHeight * trackHeight / (trackHeight + maxScroll)));
    }

    private void drawText(
            UiCanvas canvas,
            String text,
            float x,
            float y,
            float size,
            Color textColor,
            UiCanvas.HorizontalAlign horizontalAlign,
            UiCanvas.VerticalAlign verticalAlign) {
        // Keep the theme's popup alpha while preventing covered labels from bleeding through it.
        if (coveredBySortMenu != null) {
            float textWidth = UiRenderer.measureText(text, SeqClient.getFontManager().getSelectedFont(), size).width();
            float left = x - switch (horizontalAlign) {
                case CENTER -> textWidth / 2;
                case RIGHT -> textWidth;
                default -> 0;
            };
            float top = y - switch (verticalAlign) {
                case TOP -> 0;
                case MIDDLE -> size / 2;
                default -> size;
            };
            if (left < coveredBySortMenu.x() + coveredBySortMenu.width() && left + textWidth > coveredBySortMenu.x()
                    && top < coveredBySortMenu.y() + coveredBySortMenu.height() && top + size > coveredBySortMenu.y()) return;
        }
        canvas.drawText(text, x, y, new UiCanvas.TextStyle(
                SeqClient.getFontManager().getSelectedFont(),
                size,
                textColor,
                horizontalAlign,
                verticalAlign));
    }

    private static String ellipsize(String text, float maxWidth, float fontSize) {
        if (text == null || text.isEmpty() || maxWidth <= 0) {
            return "";
        }
        String font = SeqClient.getFontManager().getSelectedFont();
        if (UiRenderer.measureText(text, font, fontSize).width() <= maxWidth) {
            return text;
        }
        String suffix = "…";
        int low = 0;
        int high = text.length();
        while (low < high) {
            int mid = (low + high + 1) / 2;
            String candidate = text.substring(0, mid) + suffix;
            if (UiRenderer.measureText(candidate, font, fontSize).width() <= maxWidth) {
                low = mid;
            } else {
                high = mid - 1;
            }
        }
        return text.substring(0, low) + suffix;
    }

    private static Color tierColor(int tier) {
        return TIER_COLORS[Math.max(0, Math.min(TIER_COLORS.length - 1, tier))];
    }

    private static String tierLabel(int tier) {
        return "Tier " + Math.max(0, Math.min(3, tier));
    }

    private static String titleCase(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = value.replace('_', ' ').toLowerCase(Locale.ROOT);
        return Character.toUpperCase(normalized.charAt(0)) + normalized.substring(1);
    }

    private static boolean contains(float px, float py, float x, float y, float width, float height) {
        return px >= x && px <= x + width && py >= y && py <= y + height;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private record CachedIngredientIcon(ItemStack stack, Supplier<PlayerSkin> skinLookup) {}

    private record IngredientIconOverlay(
            CachedIngredientIcon icon, float x, float y, float size) {}

    private record EffectLine(String label, String value) {}

    private record LocationHitbox(
            float mapX,
            float copyX,
            float y,
            float actionWidth,
            float height,
            String markerId,
            SpawnLocation location) {
        private boolean containsMap(float px, float py) {
            return IngredientGuideScreen.contains(px, py, mapX, y, actionWidth, height);
        }

        private boolean containsCopy(float px, float py) {
            return IngredientGuideScreen.contains(px, py, copyX, y, actionWidth, height);
        }
    }

    private record ActionHitbox(float x, float y, float width, float height) {
        private boolean contains(float px, float py) {
            return IngredientGuideScreen.contains(px, py, x, y, width, height);
        }
    }

    private record IngredientListLayout(
            float primarySortY, float secondarySortY, float summaryY, float rowsTop) {}

    private enum GuideCategory {
        INGREDIENTS("Ingredients"),
        TOTEM_SPOTS("Totem Spots");

        private final String label;

        GuideCategory(String label) {
            this.label = label;
        }

        private String label() {
            return label;
        }
    }

    private enum ScrollbarTarget {
        INGREDIENT_LIST,
        INGREDIENT_DETAIL
    }

    private enum SortDropdown {
        PRIMARY,
        SECONDARY
    }

    private record ScrollbarGeometry(float x, float y, float height, float thumbY, float thumbHeight) {
        private boolean containsTrack(float mouseX, float mouseY) {
            float hitX = x - (SCROLLBAR_HIT_WIDTH - SCROLLBAR_WIDTH) / 2f;
            return IngredientGuideScreen.contains(
                    mouseX,
                    mouseY,
                    hitX,
                    y,
                    SCROLLBAR_HIT_WIDTH,
                    height);
        }
    }
}
