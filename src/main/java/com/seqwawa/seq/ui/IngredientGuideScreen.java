package com.seqwawa.seq.ui;

import static com.seqwawa.seq.managers.ThemeManager.color;
import static com.seqwawa.seq.ui.theme.UiColor.ACCENT_DIVIDER;
import static com.seqwawa.seq.ui.theme.UiColor.ACCENT_PRIMARY;
import static com.seqwawa.seq.ui.theme.UiColor.ACCENT_PRIMARY_HOVER;
import static com.seqwawa.seq.ui.theme.UiColor.BACKGROUND_BODY_OPAQUE;
import static com.seqwawa.seq.ui.theme.UiColor.BACKGROUND_CONTENT;
import static com.seqwawa.seq.ui.theme.UiColor.BACKGROUND_CONTENT_FOCUSED;
import static com.seqwawa.seq.ui.theme.UiColor.BACKGROUND_HEADER;
import static com.seqwawa.seq.ui.theme.UiColor.BACKGROUND_MODAL_OVERLAY;
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
import com.seqwawa.seq.managers.IngredientGuideManager.SortDirection;
import com.seqwawa.seq.managers.IngredientGuideManager.SortKey;
import com.seqwawa.seq.managers.IngredientGuideSessionSettings;
import com.seqwawa.seq.managers.IngredientItemIconFactory;
import com.seqwawa.seq.map.IngredientFarmSpot;
import com.seqwawa.seq.map.IngredientFarmSpotCatalog;
import com.seqwawa.seq.map.MapFocus;
import com.seqwawa.seq.model.IngredientGuideEntry;
import com.seqwawa.seq.model.IngredientGuideEntry.DropSource;
import com.seqwawa.seq.model.IngredientGuideEntry.SpawnLocation;
import com.seqwawa.seq.render.MinecraftGuiOverlay;
import com.seqwawa.seq.utils.TextInputHelper;
import com.seqwawa.seq.utils.rendering.MinecraftUiRenderer;
import com.seqwawa.seq.utils.rendering.UiCanvas;
import com.seqwawa.seq.utils.rendering.UiRenderMetrics;
import com.seqwawa.seq.utils.rendering.UiRenderer;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

public final class IngredientGuideScreen extends Screen implements MinecraftGuiOverlay {
    private static final float OUTER_MARGIN = 14;
    private static final float HEADER_HEIGHT = 42;
    private static final float SEARCH_HEIGHT = 28;
    private static final float SORT_ROW_HEIGHT = 22;
    private static final float SORT_ROW_GAP = 4;
    private static final float SORT_DIRECTION_WIDTH = 76;
    private static final float SORT_OPTION_HEIGHT = 22;
    private static final float ROW_HEIGHT = 43;
    private static final float FARM_SPOT_ROW_HEIGHT = 58;
    private static final float SCROLL_STEP = 34;
    private static final float SCROLLBAR_WIDTH = 3;
    private static final float SCROLLBAR_HIT_WIDTH = 9;
    private static final float MIN_SCROLLBAR_THUMB_HEIGHT = 20;
    private static final float PANEL_RADIUS = 7;
    private static final Color[] TIER_COLORS = {
        new Color(150, 150, 165),
        new Color(240, 240, 245),
        new Color(245, 195, 72),
        new Color(226, 94, 94)
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
    private SortKey primarySortKey;
    private SortDirection primarySortDirection;
    private SortKey secondarySortKey;
    private SortDirection secondarySortDirection;
    private SortDropdown openSortDropdown;
    private float listScroll;
    private float detailScroll;
    private float maxListScroll;
    private float maxDetailScroll;
    private ScrollbarTarget draggedScrollbar;
    private float scrollbarDragStartY;
    private float scrollbarDragStartOffset;
    private float nvgMouseX;
    private float nvgMouseY;
    private String itemIconKey;
    private ItemStack itemIconStack = ItemStack.EMPTY;
    private Supplier<PlayerSkin> itemSkinLookup;
    private boolean itemIconOverlayVisible;
    private float itemIconOverlayX;
    private float itemIconOverlayY;
    private float itemIconOverlaySize;
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
        itemIconOverlayVisible = false;
        float screenWidth = canvas.metrics().width();
        float screenHeight = canvas.metrics().height();
        float panelTop = HEADER_HEIGHT + OUTER_MARGIN;
        float panelHeight = Math.max(120, screenHeight - panelTop - OUTER_MARGIN);
        float listWidth = clamp(screenWidth * 0.35f, 245, 355);
        float listX = OUTER_MARGIN;
        float detailX = listX + listWidth + 10;
        float detailWidth = Math.max(150, screenWidth - detailX - OUTER_MARGIN);

        canvas.fillRect(0, 0, screenWidth, screenHeight, color(BACKGROUND_MODAL_OVERLAY, 205));
        canvas.fillRect(0, 0, screenWidth, HEADER_HEIGHT, color(BACKGROUND_HEADER, 245));
        drawText(canvas, "Ingredient Guide", OUTER_MARGIN, HEADER_HEIGHT / 2f, 20, color(ACCENT_PRIMARY),
                UiCanvas.HorizontalAlign.LEFT, UiCanvas.VerticalAlign.MIDDLE);
        drawGuideCategoryControl(canvas, screenWidth / 2f - 112, 9, 224);
        if (guideCategory == GuideCategory.INGREDIENTS) {
            drawText(canvas, manager.status(), screenWidth - 92, HEADER_HEIGHT / 2f, 11, color(TEXT_MUTED),
                    UiCanvas.HorizontalAlign.RIGHT, UiCanvas.VerticalAlign.MIDDLE);
            drawButton(canvas, screenWidth - 82, 9, 68, 24, manager.isLoading() ? "Loading" : "Refresh");
            renderIngredientList(canvas, listX, panelTop, listWidth, panelHeight);
            renderIngredientDetail(canvas, detailX, panelTop, detailWidth, panelHeight);
        } else {
            renderFarmSpotList(canvas, listX, panelTop, listWidth, panelHeight);
            renderFarmSpotDetail(canvas, detailX, panelTop, detailWidth, panelHeight);
        }
    }

    private void renderFarmSpotList(UiCanvas canvas, float x, float y, float width, float height) {
        canvas.fillRoundedRect(x, y, width, height, PANEL_RADIUS, color(BACKGROUND_BODY_OPAQUE, 245));
        List<IngredientFarmSpot> spots = IngredientFarmSpotCatalog.all();
        drawText(canvas, spots.size() + " mob totem spots", x + 11, y + 17, 11, color(TEXT_MUTED),
                UiCanvas.HorizontalAlign.LEFT, UiCanvas.VerticalAlign.MIDDLE);
        float rowsTop = y + 32;
        float rowsHeight = Math.max(0, height - 40);
        maxListScroll = Math.max(0, spots.size() * FARM_SPOT_ROW_HEIGHT - rowsHeight);
        listScroll = clamp(listScroll, 0, maxListScroll);
        canvas.scissor(x, rowsTop, width, rowsHeight);
        try {
            for (int index = 0; index < spots.size(); index++) {
                IngredientFarmSpot spot = spots.get(index);
                float rowY = rowsTop + index * FARM_SPOT_ROW_HEIGHT - listScroll;
                boolean selected = spot.equals(selectedFarmSpot);
                boolean hovered = contains(
                        nvgMouseX, nvgMouseY, x + 6, rowY + 2, width - 12, FARM_SPOT_ROW_HEIGHT - 4);
                if (selected || hovered) {
                    canvas.fillRoundedRect(
                            x + 6,
                            rowY + 2,
                            width - 12,
                            FARM_SPOT_ROW_HEIGHT - 4,
                            5,
                            selected ? color(BACKGROUND_CONTENT_FOCUSED, 245) : color(CONTROL_INPUT_HOVER, 210));
                }
                drawText(canvas, ellipsize(spot.name(), width - 32, 12), x + 14, rowY + 16, 12,
                        color(TEXT_PRIMARY), UiCanvas.HorizontalAlign.LEFT, UiCanvas.VerticalAlign.MIDDLE);
                drawText(canvas, spot.coordinates(), x + 14, rowY + 33, 10, color(TEXT_MUTED),
                        UiCanvas.HorizontalAlign.LEFT, UiCanvas.VerticalAlign.MIDDLE);
                String targets = String.join(", ", spot.ingredients());
                drawText(canvas, ellipsize(targets, width - 32, 9), x + 14, rowY + 48, 9, color(TEXT_SECONDARY),
                        UiCanvas.HorizontalAlign.LEFT, UiCanvas.VerticalAlign.MIDDLE);
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

    private void renderFarmSpotDetail(UiCanvas canvas, float x, float y, float width, float height) {
        canvas.fillRoundedRect(x, y, width, height, PANEL_RADIUS, color(BACKGROUND_BODY_OPAQUE, 245));
        locationHitboxes.clear();
        showAllMapHitbox = null;
        showFarmSpotMapHitbox = null;
        maxDetailScroll = 0;
        detailScroll = 0;
        if (selectedFarmSpot == null) {
            drawText(canvas, "Select a mob totem farming spot", x + width / 2f, y + height / 2f, 14,
                    color(TEXT_MUTED), UiCanvas.HorizontalAlign.CENTER, UiCanvas.VerticalAlign.MIDDLE);
            return;
        }

        float contentX = x + 16;
        float contentWidth = Math.max(1, width - 32);
        float cursorY = y + 24;
        drawText(canvas, ellipsize(selectedFarmSpot.name(), contentWidth, 20), contentX, cursorY, 20,
                color(TEXT_PRIMARY), UiCanvas.HorizontalAlign.LEFT, UiCanvas.VerticalAlign.MIDDLE);
        cursorY += 30;
        drawText(canvas, selectedFarmSpot.coordinates(), contentX, cursorY, 12, color(ACCENT_PRIMARY),
                UiCanvas.HorizontalAlign.LEFT, UiCanvas.VerticalAlign.MIDDLE);
        if (selectedFarmSpot.radius() > 0) {
            drawText(canvas, "Radius " + selectedFarmSpot.radius(), contentX + contentWidth, cursorY, 10,
                    color(TEXT_MUTED), UiCanvas.HorizontalAlign.RIGHT, UiCanvas.VerticalAlign.MIDDLE);
        }
        cursorY += 26;
        drawButton(canvas, contentX, cursorY, Math.min(150, contentWidth), 24, "Show on map");
        showFarmSpotMapHitbox = new ActionHitbox(contentX, cursorY, Math.min(150, contentWidth), 24);
        cursorY += 48;

        drawText(canvas, "INGREDIENTS", contentX, cursorY, 11, color(ACCENT_PRIMARY),
                UiCanvas.HorizontalAlign.LEFT, UiCanvas.VerticalAlign.MIDDLE);
        cursorY += 21;
        drawText(canvas, String.join(", ", selectedFarmSpot.ingredients()), contentX, cursorY, 12,
                color(TEXT_SECONDARY), UiCanvas.HorizontalAlign.LEFT, UiCanvas.VerticalAlign.MIDDLE);
        cursorY += 32;
        drawText(canvas, "MOBS", contentX, cursorY, 11, color(ACCENT_PRIMARY),
                UiCanvas.HorizontalAlign.LEFT, UiCanvas.VerticalAlign.MIDDLE);
        cursorY += 21;
        String mobs = selectedFarmSpot.mobs().isEmpty()
                ? "Mob names not catalogued yet"
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
    }

    private void renderIngredientList(UiCanvas canvas, float x, float y, float width, float height) {
        canvas.fillRoundedRect(x, y, width, height, PANEL_RADIUS, color(BACKGROUND_BODY_OPAQUE, 245));
        Color searchColor = searchFocused ? color(BACKGROUND_CONTENT_FOCUSED, 255) : color(CONTROL_INPUT, 255);
        canvas.fillRoundedRect(x + 9, y + 9, width - 18, SEARCH_HEIGHT, 5, searchColor);
        canvas.strokeRect(x + 9, y + 9, width - 18, SEARCH_HEIGHT, 1,
                searchFocused ? color(CONTROL_BORDER) : color(ACCENT_DIVIDER));
        String searchText = searchQuery.isEmpty() ? "Search ingredient, mob, profession..." : searchQuery;
        drawText(canvas, searchText, x + 18, y + 9 + SEARCH_HEIGHT / 2f, 12,
                searchQuery.isEmpty() ? color(TEXT_MUTED) : color(TEXT_PRIMARY),
                UiCanvas.HorizontalAlign.LEFT, UiCanvas.VerticalAlign.MIDDLE);

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
                if (selected || hovered) {
                    canvas.fillRoundedRect(
                            x + 6,
                            rowY + 2,
                            width - 12,
                            ROW_HEIGHT - 4,
                            5,
                            selected ? color(BACKGROUND_CONTENT_FOCUSED, 245) : color(CONTROL_INPUT_HOVER, 210));
                }
                canvas.fillCircle(x + 17, rowY + ROW_HEIGHT / 2f, 4, tierColor(ingredient.tier()));
                drawText(canvas, ellipsize(ingredient.displayName(), width - 64, 12), x + 29, rowY + 14, 12,
                        color(TEXT_PRIMARY), UiCanvas.HorizontalAlign.LEFT, UiCanvas.VerticalAlign.MIDDLE);
                String subtitle = "Lv. " + ingredient.level() + "  •  " + tierLabel(ingredient.tier());
                drawText(canvas, subtitle, x + 29, rowY + 30, 10, color(TEXT_MUTED),
                        UiCanvas.HorizontalAlign.LEFT, UiCanvas.VerticalAlign.MIDDLE);
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
        drawOpenSortDropdown(canvas, x + 9, width - 18, layout);
    }

    private void renderIngredientDetail(UiCanvas canvas, float x, float y, float width, float height) {
        canvas.fillRoundedRect(x, y, width, height, PANEL_RADIUS, color(BACKGROUND_BODY_OPAQUE, 245));
        locationHitboxes.clear();
        showAllMapHitbox = null;
        showFarmSpotMapHitbox = null;
        if (selectedIngredient == null) {
            String message = manager.isLoading() ? "Loading ingredient data..." : "Select an ingredient";
            drawText(canvas, message, x + width / 2f, y + height / 2f, 14, color(TEXT_MUTED),
                    UiCanvas.HorizontalAlign.CENTER, UiCanvas.VerticalAlign.MIDDLE);
            return;
        }

        float contentX = x + 16;
        float contentWidth = Math.max(1, width - 32);
        float contentTop = y + 14;
        float viewportHeight = height - 28;
        canvas.scissor(x + 1, y + 1, width - 2, height - 2);
        float cursorY = contentTop - detailScroll;
        try {
            ItemStack iconStack = selectedItemIcon(selectedIngredient);
            canvas.fillRoundedRect(contentX, cursorY, 58, 58, 6, color(BACKGROUND_CONTENT, 230));
            if (!iconStack.isEmpty()) {
                itemIconOverlayX = contentX + 5;
                itemIconOverlayY = cursorY + 5;
                itemIconOverlaySize = 48;
                itemIconOverlayVisible = itemIconOverlayY >= y + 1
                        && itemIconOverlayY + itemIconOverlaySize <= y + height - 1;
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

            int locationCount = selectedIngredient.dropSources().stream()
                    .mapToInt(source -> source.locations().size())
                    .sum();
            canvas.strokeLine(contentX, cursorY, contentX + contentWidth, cursorY, 1, color(ACCENT_DIVIDER));
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
                canvas.fillRoundedRect(contentX, cursorY, contentWidth, 46, 5, color(BACKGROUND_CONTENT, 220));
                drawText(canvas, "No mob drop source is published by the Wynncraft API.", contentX + 12, cursorY + 23,
                        11, color(TEXT_MUTED), UiCanvas.HorizontalAlign.LEFT, UiCanvas.VerticalAlign.MIDDLE);
                cursorY += 54;
            } else {
                for (int sourceIndex = 0; sourceIndex < selectedIngredient.dropSources().size(); sourceIndex++) {
                    DropSource source = selectedIngredient.dropSources().get(sourceIndex);
                    float cardHeight = source.locations().isEmpty() ? 52 : 34 + source.locations().size() * 25;
                    canvas.fillRoundedRect(contentX, cursorY, contentWidth, cardHeight, 5, color(BACKGROUND_CONTENT, 220));
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
                            canvas.fillRoundedRect(
                                    contentX + 8,
                                    locationY,
                                    contentWidth - 16,
                                    21,
                                    4,
                                    hovered ? color(CONTROL_INPUT_HOVER, 245) : color(CONTROL_INPUT, 225));
                            String radius = location.radius() > 0 ? "  •  radius " + location.radius() : "";
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

    private ItemStack selectedItemIcon(IngredientGuideEntry ingredient) {
        String selectedKey = ingredient.icon().cacheKey();
        if (!selectedKey.equals(itemIconKey)) {
            itemIconKey = selectedKey;
            itemIconStack = IngredientItemIconFactory.create(ingredient.icon());
            var skinProfile = IngredientItemIconFactory.skinProfile(ingredient.icon());
            itemSkinLookup = skinProfile == null
                    ? null
                    : SeqClient.mc.getSkinManager().createLookup(skinProfile, false);
        }
        return itemIconStack;
    }

    @Override
    public void renderMinecraftGuiOverlay(GuiGraphics guiGraphics, UiRenderMetrics metrics) {
        if (!itemIconOverlayVisible || itemIconStack.isEmpty()) {
            return;
        }
        float guiUnitsPerUiUnit = metrics.pixelRatio() / (float) metrics.minecraftGuiScale();
        float itemScale = itemIconOverlaySize * guiUnitsPerUiUnit / 16f;
        guiGraphics.pose().pushMatrix();
        try {
            guiGraphics.pose().translate(
                    itemIconOverlayX * guiUnitsPerUiUnit,
                    itemIconOverlayY * guiUnitsPerUiUnit);
            guiGraphics.pose().scale(itemScale, itemScale);
            if (itemSkinLookup != null) {
                PlayerFaceRenderer.draw(guiGraphics, itemSkinLookup.get(), 0, 0, 16);
            } else {
                guiGraphics.renderItem(itemIconStack, 0, 0);
            }
        } finally {
            guiGraphics.pose().popMatrix();
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
        float panelTop = HEADER_HEIGHT + OUTER_MARGIN;
        float panelHeight = Math.max(120, screenHeight - panelTop - OUTER_MARGIN);
        float listWidth = clamp(screenWidth * 0.35f, 245, 355);
        float detailX = OUTER_MARGIN + listWidth + 10;
        float detailWidth = Math.max(150, screenWidth - detailX - OUTER_MARGIN);

        float categoryX = screenWidth / 2f - 112;
        if (contains(mx, my, categoryX, 9, 224, 24)) {
            GuideCategory nextCategory = mx < categoryX + 112
                    ? GuideCategory.INGREDIENTS
                    : GuideCategory.TOTEM_SPOTS;
            if (nextCategory != guideCategory) {
                guideCategory = nextCategory;
                searchFocused = false;
                listScroll = 0;
                detailScroll = 0;
                draggedScrollbar = null;
                openSortDropdown = null;
            }
            return true;
        }
        if (guideCategory == GuideCategory.INGREDIENTS
                && contains(mx, my, screenWidth - 82, 9, 68, 24)) {
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
                            OUTER_MARGIN + listWidth - 5,
                            rowsTop,
                            rowsHeight,
                            listScroll,
                            maxListScroll),
                    mx,
                    my,
                    listScroll)) {
                return true;
            }
            if (contains(mx, my, OUTER_MARGIN, rowsTop, listWidth, rowsHeight)) {
                int index = (int) ((my - rowsTop + listScroll) / FARM_SPOT_ROW_HEIGHT);
                List<IngredientFarmSpot> spots = IngredientFarmSpotCatalog.all();
                if (index >= 0 && index < spots.size()) {
                    selectedFarmSpot = spots.get(index);
                    return true;
                }
            }
            if (showFarmSpotMapHitbox != null && showFarmSpotMapHitbox.contains(mx, my)) {
                openFarmSpotMap();
                return true;
            }
            return super.mouseClicked(click, outsideScreen);
        }

        if (contains(mx, my, OUTER_MARGIN + 9, panelTop + 9, listWidth - 18, SEARCH_HEIGHT)) {
            searchFocused = true;
            openSortDropdown = null;
            return true;
        }
        searchFocused = false;

        IngredientListLayout layout = ingredientListLayout(panelTop, hasSecondarySort());
        float sortX = OUTER_MARGIN + 9;
        float sortWidth = listWidth - 18;
        if (handleOpenSortDropdownClick(mx, my, sortX, sortWidth, layout)) {
            return true;
        }
        float rowsTop = layout.rowsTop();
        float rowsHeight = Math.max(0, panelTop + panelHeight - rowsTop - 8);
        if (startScrollbarDrag(
                ScrollbarTarget.INGREDIENT_LIST,
                scrollbarGeometry(
                        OUTER_MARGIN + listWidth - 5,
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
                        panelTop + 8,
                        panelHeight - 16,
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

        if (contains(mx, my, OUTER_MARGIN, rowsTop, listWidth, rowsHeight)) {
            int index = (int) ((my - rowsTop + listScroll) / ROW_HEIGHT);
            if (index >= 0 && index < visibleIngredients.size()) {
                selectedIngredient = visibleIngredients.get(index);
                detailScroll = 0;
                return true;
            }
        }
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
        float panelTop = HEADER_HEIGHT + OUTER_MARGIN;
        float panelHeight = Math.max(120, screenHeight - panelTop - OUTER_MARGIN);
        float listWidth = clamp(screenWidth * 0.35f, 245, 355);
        IngredientListLayout listLayout = ingredientListLayout(panelTop, hasSecondarySort());
        float trackHeight;
        float maxScroll;
        if (draggedScrollbar == ScrollbarTarget.INGREDIENT_LIST) {
            trackHeight = guideCategory == GuideCategory.TOTEM_SPOTS
                    ? Math.max(0, panelHeight - 40)
                    : Math.max(0, panelTop + panelHeight - listLayout.rowsTop() - 8);
            maxScroll = maxListScroll;
        } else {
            trackHeight = panelHeight - 16;
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
        float mx = MinecraftUiRenderer.mouseX(mouseX);
        float screenWidth = MinecraftUiRenderer.screenWidth();
        float listWidth = clamp(screenWidth * 0.35f, 245, 355);
        if (mx <= OUTER_MARGIN + listWidth) {
            listScroll = clamp(listScroll - (float) scrollY * SCROLL_STEP, 0, maxListScroll);
        } else {
            detailScroll = clamp(detailScroll - (float) scrollY * SCROLL_STEP, 0, maxDetailScroll);
        }
        return true;
    }

    @Override
    public boolean keyPressed(@NotNull KeyEvent keyEvent) {
        int key = keyEvent.key();
        if (key == GLFW.GLFW_KEY_ESCAPE && openSortDropdown != null) {
            openSortDropdown = null;
            return true;
        }
        if (searchFocused) {
            if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                searchFocused = false;
                return true;
            }
            if (key == GLFW.GLFW_KEY_BACKSPACE) {
                if (!searchQuery.isEmpty()) {
                    searchQuery = searchQuery.substring(0, searchQuery.length() - 1);
                }
                return true;
            }
            if ((keyEvent.modifiers() & GLFW.GLFW_MOD_CONTROL) != 0 && key == GLFW.GLFW_KEY_V) {
                String clipboard = SeqClient.mc.keyboardHandler.getClipboard();
                if (clipboard != null) {
                    searchQuery += clipboard.replaceAll("\\p{Cntrl}", "");
                }
                return true;
            }
            Character character = TextInputHelper.getTypedCharacter(keyEvent);
            if (character != null && TextInputHelper.isPrintableCharacter(character) && searchQuery.length() < 80) {
                searchQuery += character;
                return true;
            }
            return true;
        }
        if (guideCategory == GuideCategory.INGREDIENTS && key == GLFW.GLFW_KEY_SLASH) {
            searchFocused = true;
            return true;
        }
        return super.keyPressed(keyEvent);
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
        canvas.fillRoundedRect(x, y, width, height, 5,
                hovered ? color(CONTROL_INPUT_HOVER, 245) : color(CONTROL_INPUT, 235));
        canvas.strokeRect(x, y, width, height, 1, color(ACCENT_DIVIDER));
        drawText(canvas, label, x + width / 2f, y + height / 2f, 10,
                hovered ? color(ACCENT_PRIMARY_HOVER) : color(TEXT_SECONDARY),
                UiCanvas.HorizontalAlign.CENTER, UiCanvas.VerticalAlign.MIDDLE);
    }

    private void drawGuideCategoryControl(UiCanvas canvas, float x, float y, float width) {
        float segmentWidth = width / 2f;
        for (int index = 0; index < GuideCategory.values().length; index++) {
            GuideCategory category = GuideCategory.values()[index];
            float segmentX = x + index * segmentWidth;
            boolean active = guideCategory == category;
            boolean hovered = contains(nvgMouseX, nvgMouseY, segmentX, y, segmentWidth, 24);
            canvas.fillRect(
                    segmentX,
                    y,
                    segmentWidth,
                    24,
                    active
                            ? color(BACKGROUND_CONTENT_FOCUSED, 255)
                            : hovered ? color(CONTROL_INPUT_HOVER, 245) : color(CONTROL_INPUT, 235));
            canvas.strokeRect(segmentX, y, segmentWidth, 24, 1, color(ACCENT_DIVIDER));
            drawText(
                    canvas,
                    category.label(),
                    segmentX + segmentWidth / 2f,
                    y + 12,
                    10,
                    active ? color(ACCENT_PRIMARY) : color(TEXT_SECONDARY),
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
        canvas.fillRoundedRect(
                x,
                y,
                width,
                SORT_ROW_HEIGHT,
                4,
                keyHovered || directionHovered ? color(CONTROL_INPUT_HOVER, 240) : color(CONTROL_INPUT, 225));
        canvas.strokeRect(x, y, width, SORT_ROW_HEIGHT, 1, color(ACCENT_DIVIDER));
        canvas.strokeLine(
                x + keyWidth,
                y + 3,
                x + keyWidth,
                y + SORT_ROW_HEIGHT - 3,
                1,
                color(ACCENT_DIVIDER));
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
                            ? color(BACKGROUND_CONTENT_FOCUSED, 255)
                            : hovered ? color(CONTROL_INPUT_HOVER, 255) : color(CONTROL_INPUT, 250));
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
                IngredientGuideManager.filter(snapshot.ingredients(), searchQuery),
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
        float primarySortY = panelY + 9 + SEARCH_HEIGHT + 8;
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
        canvas.fillRoundedRect(
                visualX,
                geometry.y(),
                visualWidth,
                geometry.height(),
                visualWidth / 2f,
                color(CONTROL_TRACK));
        canvas.fillRoundedRect(
                visualX,
                geometry.thumbY(),
                visualWidth,
                geometry.thumbHeight(),
                visualWidth / 2f,
                interactive ? color(ACCENT_PRIMARY_HOVER) : color(CONTROL_THUMB));
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

    private static void drawText(
            UiCanvas canvas,
            String text,
            float x,
            float y,
            float size,
            Color textColor,
            UiCanvas.HorizontalAlign horizontalAlign,
            UiCanvas.VerticalAlign verticalAlign) {
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
