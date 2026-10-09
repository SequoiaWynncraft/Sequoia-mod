package com.seqwawa.seq.ui;

import static com.seqwawa.seq.managers.ThemeManager.color;
import static com.seqwawa.seq.ui.theme.UiColor.*;

import static com.seqwawa.seq.ui.WorldMapUi.*;

import com.mojang.authlib.GameProfile;
import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.world.entity.player.PlayerSkin;
import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.map.IngredientFarmSpot;
import com.seqwawa.seq.map.IngredientFarmSpotCatalog;
import com.seqwawa.seq.map.IngredientFarmSpotDisplay;
import com.seqwawa.seq.map.IngredientFarmSpotDisplay.Entry;
import com.seqwawa.seq.map.IngredientMapSelection;
import com.seqwawa.seq.map.IngredientMapCategory;
import com.seqwawa.seq.map.IngredientWaypointManager;
import com.seqwawa.seq.map.IngredientWaypointManager.DetailLine;
import com.seqwawa.seq.map.IngredientWaypointManager.Kind;
import com.seqwawa.seq.map.IngredientWaypointManager.Waypoint;
import com.seqwawa.seq.map.IngredientWaypointManager.WaypointIcon;
import com.seqwawa.seq.map.MapBounds;
import com.seqwawa.seq.map.MapDisplayMode;
import com.seqwawa.seq.map.MapFocus;
import com.seqwawa.seq.map.MapViewport;
import com.seqwawa.seq.map.WorldMapSettings;
import com.seqwawa.seq.managers.IngredientGuideManager;
import com.seqwawa.seq.managers.IngredientItemIconFactory;
import com.seqwawa.seq.model.IngredientGuideEntry;
import com.seqwawa.seq.utils.rendering.UiCanvas;
import com.seqwawa.seq.utils.rendering.UiRenderMetrics;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Ingredient map mode owns category, multi-selection, sidebar and waypoint actions. */
final class IngredientMapMode {
    private static final float SIDEBAR_WIDTH = 230;

    private static final float PADDING = 12;
    private static final float BUTTON_HEIGHT = 24;

    private static final float SIDEBAR_PANEL_TOP = 92;

    private static final double MIN_PIXELS_PER_BLOCK = MapViewport.MIN_PIXELS_PER_BLOCK;
    private static final double MAX_PIXELS_PER_BLOCK = MapViewport.MAX_PIXELS_PER_BLOCK;

    private static final float INGREDIENT_FARM_SPOT_CARD_GAP = 6;
    private static final float INGREDIENT_FARM_SPOT_CARD_PADDING = 8;
    private static final float INGREDIENT_FARM_SPOT_ICON_SIZE = 17;
    private static final float INGREDIENT_FARM_SPOT_ICON_GAP = 3;
    private static final float INGREDIENT_OPTION_HEIGHT = 18;
    private static final float INGREDIENT_CHECKBOX_SIZE = 12;
    private final MapFocus mapFocus;
    private final ItemStack mapFocusIcon;
    private final Supplier<PlayerSkin> mapFocusSkinLookup;
    private final List<FocusIconOverlay> focusIconOverlays = new ArrayList<>();
    private final IngredientMapInsights insights = new IngredientMapInsights(this::ingredientFarmSpotEntries, this::cachedIngredientIcon, overlay -> focusIconOverlays.add(overlay));
    private final IngredientMapSelection ingredientMapSelection = new IngredientMapSelection();
    private MapFocus.Marker hoveredFocusMarker;
    private MapFocus.Marker selectedFocusMarker;
    private IngredientMapCategory ingredientMapCategory = IngredientMapCategory.SPAWNS;
    private IngredientFarmSpot hoveredIngredientFarmSpot;
    private IngredientFarmSpot selectedIngredientFarmSpot;
    private final Map<String, MapIngredientIcon> ingredientIconCache = new HashMap<>();
    private Map<String, IngredientGuideEntry> cachedIngredientsByName = Map.of();
    private long cachedIngredientSnapshotVersion = -1;

    record View(MapFocus focus, MapFocus.Marker selectedSpawn, IngredientFarmSpot selectedSpot, int spawnCount, IngredientMapCategory category) {}
    private View view() { return new View(mapFocus, selectedFocusMarker, selectedIngredientFarmSpot, ingredientMapSelection.spawnCount(), ingredientMapCategory); }
    void renderInsights(UiCanvas canvas, WorldMapFrame frame, float x) {
        insights.render(canvas, view(), frame, x);
    }
    interface Actions {
        void center(IngredientFarmSpot spot);
        void openGuide();
        void copy(String coordinates);
    }
    private final Actions actions;
    private final WorldMapSettings mapSettings;
    private final IngredientGuideManager ingredientGuideManager = IngredientGuideManager.getInstance();
    private final MapSidebar sidebar = new MapSidebar();
    private WorldMapFrame frame;
    IngredientMapMode(MapFocus mapFocus, ItemStack mapFocusIcon, GameProfile mapFocusSkinProfile,
            IngredientFarmSpot farmSpot, WorldMapSettings settings, Actions actions) {
        this.mapSettings = settings;
        this.actions = actions;
        this.mapFocus = mapFocus;
        this.mapFocusIcon = mapFocusIcon == null ? ItemStack.EMPTY : mapFocusIcon.copy();
        this.mapFocusSkinLookup = mapFocusSkinProfile == null
                ? null
                : SeqClient.mc.getSkinManager().createLookup(mapFocusSkinProfile, false);
        this.selectedFocusMarker = mapFocus == null ? null : mapFocus.selectedMarker();
        this.selectedIngredientFarmSpot = farmSpot;
        if (selectedFocusMarker != null) {
            ingredientMapSelection.toggleSpawn(selectedFocusMarker.id());
        }
        if (selectedIngredientFarmSpot != null) {
            ingredientMapSelection.toggleTotem(selectedIngredientFarmSpot.id());
        }
        this.ingredientMapCategory =
                farmSpot == null ? IngredientMapCategory.SPAWNS : IngredientMapCategory.TOTEM_SPOTS;

    }
    void deactivate() { sidebar.reset(); clearHover(); focusIconOverlays.clear(); }
    void beginFrame(WorldMapFrame frame) { this.frame = frame; clearHover(); focusIconOverlays.clear(); }
    void clearHover() { hoveredFocusMarker = null; hoveredIngredientFarmSpot = null; }
    IngredientFarmSpot selectedSpot() { return selectedIngredientFarmSpot; }
    MapBounds focusBounds() { return mapFocus.bounds(); }
    boolean clickMap(WorldMapFrame frame) {
        this.frame = frame;
        boolean selected = false;
        if (hoveredIngredientFarmSpot != null) { toggleIngredientFarmSpotSelection(hoveredIngredientFarmSpot); selected = true; }
        else if (hoveredFocusMarker != null) { toggleFocusMarkerSelection(hoveredFocusMarker); selected = true; }
        clearHover();
        return selected;
    }
    boolean copyHovered(WorldMapFrame frame) {
        if (!frame.viewport().isInsideScreen(frame.mouseX(), frame.mouseY())) return false;
        if (hoveredIngredientFarmSpot != null) actions.copy(hoveredIngredientFarmSpot.coordinates());
        else if (hoveredFocusMarker != null) actions.copy(hoveredFocusMarker.coordinates());
        else return false;
        return true;
    }
    void renderMap(UiCanvas canvas, MapViewport viewport) {
        if (ingredientMapCategory == IngredientMapCategory.TOTEM_SPOTS) renderIngredientFarmSpotMarkers(canvas, viewport);
        else renderMapFocus(canvas, viewport);
    }
    private void drawButton(UiCanvas canvas, float x, float y, float w, float h, String label, boolean active) {
        MapSidebar.button(canvas, x, y, w, h, label, active, frame);
    }
    private static boolean isHovered(float mx, float my, float x, float y, float w, float h) { return MapSidebar.hit(mx, my, x, y, w, h); }
    private static double markerDistance(double x, double y) { return Math.max(Math.abs(x), Math.abs(y)); }

    boolean scroll(WorldMapFrame frame, double delta, boolean insightsOpen, float insightsX) {
        this.frame = frame;
        if (frame.mouseX() >= 0 && frame.mouseX() <= SIDEBAR_WIDTH) {
            if (frame.mouseY() >= SIDEBAR_PANEL_TOP && frame.mouseY() <= frame.height()) sidebar.scroll(delta, frame.height());
            return true;
        }
        if (insightsOpen && frame.mouseX() >= insightsX && frame.mouseX() <= frame.width()) {
            insights.scroll(view(), frame, delta);
            return true;
        }
        return false;
    }

    static Color ingredientRadiusFillColor(Color markerColor) {
        return new Color(markerColor.getRed(), markerColor.getGreen(), markerColor.getBlue(),
                Math.round(markerColor.getAlpha() * 0.35f));
    }

    private void renderMapFocus(UiCanvas canvas, MapViewport viewport) {
        if (!hasMapFocus()) {
            return;
        }

        MapBounds visibleBounds = viewport.visibleBounds();
        if (!frame.dragging() && viewport.isInsideScreen(frame.mouseX(), frame.mouseY())) {
            double closestDistance = 12;
            for (MapFocus.Marker marker : mapFocus.markers()) {
                if (!visibleBounds.contains(marker.x(), marker.z())) {
                    continue;
                }
                double distance = markerDistance(
                        frame.mouseX() - viewport.worldToScreenX(marker.x()),
                        frame.mouseY() - viewport.worldToScreenZ(marker.z()));
                if (distance <= closestDistance) {
                    hoveredFocusMarker = marker;
                    closestDistance = distance;
                }
            }
        }

        canvas.scissor(viewport.screenX(), viewport.screenY(), viewport.screenWidth(), viewport.screenHeight());
        for (MapFocus.Marker marker : mapFocus.markers()) {
            if (!visibleBounds.contains(marker.x(), marker.z())) {
                continue;
            }
            float x = viewport.worldToScreenX(marker.x());
            float y = viewport.worldToScreenZ(marker.z());
            float areaRadius = (float) (marker.radius() * viewport.pixelsPerBlock());
            boolean selected = ingredientMapSelection.isSpawnSelected(marker.id());
            boolean hovered = marker.equals(hoveredFocusMarker);
            Color markerColor = selected ? color(MAP_SELECTED_TERRITORY) : color(ACCENT_PRIMARY);
            if (areaRadius >= 4) {
                drawCircle(canvas, x, y, areaRadius, ingredientRadiusFillColor(markerColor));
                drawCircleOutline(canvas, x, y, areaRadius, selected ? 1.5f : 1, markerColor);
            }
            if (mapFocusIcon.isEmpty()) {
                float markerRadius = selected || hovered ? 4 : 3;
                drawSquareMarker(canvas, x, y, markerRadius + 1.5f, color(BACKGROUND_MODAL_OVERLAY));
                drawSquareMarker(canvas, x, y, markerRadius, markerColor);
            } else {
                float iconSize = selected || hovered ? 22 : 18;
                float outlineRadius = iconSize / 2f + 1;
                drawSquareMarker(canvas, x, y, outlineRadius, color(BACKGROUND_MODAL_OVERLAY));
                if (selected || hovered) {
                    drawSquareMarkerOutline(canvas, x, y, outlineRadius, 1, markerColor);
                }
                focusIconOverlays.add(new FocusIconOverlay(
                        x - iconSize / 2f,
                        y - iconSize / 2f,
                        iconSize,
                        mapFocusIcon,
                        mapFocusSkinLookup));
            }
        }
        canvas.resetScissor();

        renderMapFocusBanner(canvas, viewport);
        if (hoveredFocusMarker != null) {
            renderMapFocusTooltip(canvas, hoveredFocusMarker);
        }
    }

    private void renderIngredientFarmSpotMarkers(UiCanvas canvas, MapViewport viewport) {
        List<IngredientFarmSpot> spots = IngredientFarmSpotCatalog.all();
        MapBounds visibleBounds = viewport.visibleBounds();
        if (!frame.dragging() && viewport.isInsideScreen(frame.mouseX(), frame.mouseY())) {
            double closestDistance = 12;
            for (IngredientFarmSpot spot : spots) {
                if (!visibleBounds.contains(spot.x(), spot.z())) {
                    continue;
                }
                double distance = markerDistance(
                        frame.mouseX() - viewport.worldToScreenX(spot.x()),
                        frame.mouseY() - viewport.worldToScreenZ(spot.z()));
                if (distance <= closestDistance) {
                    hoveredIngredientFarmSpot = spot;
                    closestDistance = distance;
                }
            }
        }

        canvas.scissor(viewport.screenX(), viewport.screenY(), viewport.screenWidth(), viewport.screenHeight());
        for (IngredientFarmSpot spot : spots) {
            if (!visibleBounds.contains(spot.x(), spot.z())) {
                continue;
            }
            float x = viewport.worldToScreenX(spot.x());
            float y = viewport.worldToScreenZ(spot.z());
            boolean selected = ingredientMapSelection.isTotemSelected(spot.id());
            boolean hovered = spot.equals(hoveredIngredientFarmSpot);
            Color markerColor = selected ? color(MAP_SELECTED_TERRITORY) : color(ACCENT_PRIMARY);
            float areaRadius = (float) (spot.radius() * viewport.pixelsPerBlock());
            if (areaRadius >= 4) {
                drawCircle(canvas, x, y, areaRadius, ingredientRadiusFillColor(markerColor));
                drawCircleOutline(canvas, x, y, areaRadius, selected ? 1.5f : 1, markerColor);
            }
            drawTotemMarker(canvas, x, y, selected || hovered ? 22 : 18,
                    markerColor, selected || hovered);
        }
        canvas.resetScissor();

        if (hoveredIngredientFarmSpot != null) {
            renderIngredientFarmSpotTooltip(canvas, hoveredIngredientFarmSpot);
        }
    }

    private void renderIngredientFarmSpotTooltip(UiCanvas canvas, IngredientFarmSpot spot) {
        String ingredients = ingredientFarmSpotEntries(spot).stream()
                .map(Entry::name)
                .reduce((first, second) -> first + ", " + second)
                .orElse("");
        String subtitle = spot.coordinates() + " · " + ingredients;
        float x = frame.tooltipX(250);
        float y = Math.max(58, frame.mouseY() + 12);
        canvas.fillRect(x, y, 250, 42, color(MAP_SIDEBAR));
        canvas.strokeRect(x, y, 250, 42, 1, color(MAP_BORDER));
        drawFittedText(canvas, x + 8, y + 15, 12, spot.name(), color(MAP_TEXT), 234, TextAlignment.LEFT);
        drawFittedText(canvas, x + 8, y + 31, 10, subtitle, color(MAP_SUBTEXT), 234, TextAlignment.LEFT);
    }

    private void renderMapFocusBanner(UiCanvas canvas, MapViewport viewport) {
        String title = mapFocus.title();
        String subtitle = mapFocus.markers().size() + (mapFocus.markers().size() == 1
                ? " spawn location"
                : " spawn locations");
        float width = Math.min(260, Math.max(170, textWidth(title, 12) + 32));
        float x = viewport.screenX() + (viewport.screenWidth() - width) / 2f;
        float y = viewport.screenY() + 10;
        canvas.fillRoundedRect(x, y, width, 42, 6, color(MAP_SIDEBAR));
        canvas.strokeRect(x, y, width, 42, 1, color(MAP_BORDER));
        drawFittedText(canvas, x + 10, y + 14, 12, title, color(MAP_TEXT), width - 20, TextAlignment.LEFT);
        drawFittedText(canvas, x + 10, y + 30, 10, subtitle, color(MAP_SUBTEXT), width - 20, TextAlignment.LEFT);
    }

    private void renderMapFocusTooltip(UiCanvas canvas, MapFocus.Marker marker) {
        String subtitle = marker.source() + " · " + marker.coordinates();
        float x = frame.tooltipX(230);
        float y = Math.max(58, frame.mouseY() + 12);
        canvas.fillRect(x, y, 230, 42, color(MAP_SIDEBAR));
        canvas.strokeRect(x, y, 230, 42, 1, color(MAP_BORDER));
        drawFittedText(canvas, x + 8, y + 15, 12, marker.label(), color(MAP_TEXT), 214, TextAlignment.LEFT);
        drawFittedText(canvas, x + 8, y + 31, 10, subtitle, color(MAP_SUBTEXT), 214, TextAlignment.LEFT);
    }

    void renderSidebar(UiCanvas canvas, WorldMapFrame frame, String centerLabel) {
        this.frame = frame;
        float screenHeight = frame.height();
        IngredientSidebarLayout layout = ingredientSidebarLayout();
        sidebar.contentHeight(ingredientSidebarContentHeight(layout), screenHeight);
        sidebar.renderBase(canvas, frame, MapDisplayMode.INGREDIENTS, centerLabel);

        float contentWidth = SIDEBAR_WIDTH - PADDING * 2;
        canvas.scissor(0, SIDEBAR_PANEL_TOP, SIDEBAR_WIDTH, Math.max(0, screenHeight - SIDEBAR_PANEL_TOP));
        try {
            drawIngredientMapCategoryControl(canvas, sidebar.y(layout.titleY()));
            renderIngredientWaypointActions(canvas, layout, contentWidth);
            if (ingredientMapCategory == IngredientMapCategory.TOTEM_SPOTS) {
                renderIngredientFarmSpotSidebar(canvas, layout, contentWidth);
            } else {
                renderIngredientSpawnSidebar(canvas, layout, contentWidth);
            }
        } finally {
            canvas.resetScissor();
        }
        sidebar.renderScrollbar(canvas, screenHeight);
    }

    private void renderIngredientSpawnSidebar(
            UiCanvas canvas, IngredientSidebarLayout layout, float contentWidth) {
        drawButton(
                canvas,
                PADDING,
                sidebar.y(layout.guideY()),
                contentWidth,
                BUTTON_HEIGHT,
                "Choose Ingredient",
                false);
        if (!hasMapFocus()) {
            drawFittedText(
                    canvas,
                    PADDING,
                    sidebar.y(layout.ingredientY()),
                    11,
                    "Choose an ingredient to display all of its published spawn locations.",
                    color(MAP_SUBTEXT),
                    contentWidth,
                    TextAlignment.LEFT);
            return;
        }

        drawFittedText(
                canvas,
                PADDING,
                sidebar.y(layout.ingredientY()),
                13,
                mapFocus.title(),
                color(MAP_TEXT),
                contentWidth,
                TextAlignment.LEFT);
        long sourceCount = mapFocus.markers().stream().map(MapFocus.Marker::source).distinct().count();
        drawText(
                canvas,
                PADDING,
                sidebar.y(layout.summaryY()),
                10,
                mapFocus.markers().size() + " locations · " + sourceCount + " mobs",
                color(MAP_SUBTEXT),
                TextAlignment.LEFT);

        int selectedSpawnCount = ingredientMapSelection.spawnCount();
        drawText(
                canvas,
                PADDING,
                sidebar.y(layout.selectedTitleY()),
                12,
                "Selected Spawns (" + selectedSpawnCount + ")",
                color(MAP_TITLE),
                TextAlignment.LEFT);
        if (selectedFocusMarker == null) {
            drawText(
                    canvas,
                    PADDING,
                    sidebar.y(layout.selectedDetailY()),
                    10,
                    "Click markers to select multiple",
                    color(MAP_SUBTEXT),
                    TextAlignment.LEFT);
        } else {
            drawFittedText(
                    canvas,
                    PADDING,
                    sidebar.y(layout.selectedDetailY()),
                    11,
                    selectedFocusMarker.source(),
                    color(MAP_TEXT),
                    contentWidth,
                    TextAlignment.LEFT);
            drawText(
                    canvas,
                    PADDING,
                    sidebar.y(layout.selectedDetailY() + 18),
                    10,
                    selectedFocusMarker.coordinates(),
                    color(MAP_SUBTEXT),
                    TextAlignment.LEFT);
            drawButton(
                    canvas,
                    PADDING,
                    sidebar.y(layout.copyY()),
                    contentWidth,
                    BUTTON_HEIGHT,
                    "Copy Selected Coordinates",
                    false);
        }
    }

    private void renderIngredientWaypointActions(
            UiCanvas canvas, IngredientSidebarLayout layout, float contentWidth) {
        int selectedCount = ingredientMapSelection.size();
        int waypointCount = IngredientWaypointManager.getInstance().size();
        drawButton(
                canvas,
                PADDING,
                sidebar.y(layout.renderWaypointsY()),
                contentWidth,
                BUTTON_HEIGHT,
                "Render Selected (" + selectedCount + ")",
                selectedCount > 0);
        boolean showRadii = mapSettings.showIngredientWaypointRadii();
        drawIngredientCheckbox(
                canvas,
                PADDING,
                sidebar.y(layout.radiusToggleY()),
                contentWidth,
                "Show spot radius in game",
                showRadii);
        boolean colorByProximity = mapSettings.colorIngredientWaypointRadiiByProximity();
        drawIngredientCheckbox(
                canvas,
                PADDING,
                sidebar.y(layout.radiusColorToggleY()),
                contentWidth,
                "Color radius by proximity",
                colorByProximity);
        drawButton(
                canvas,
                PADDING,
                sidebar.y(layout.clearWaypointsY()),
                contentWidth,
                BUTTON_HEIGHT,
                "Clear Waypoints" + (waypointCount > 0 ? " (" + waypointCount + ")" : ""),
                waypointCount > 0);
    }

    private void renderIngredientFarmSpotSidebar(
            UiCanvas canvas, IngredientSidebarLayout layout, float contentWidth) {
        drawText(
                canvas,
                PADDING,
                sidebar.y(layout.guideY()),
                13,
                "All Mob Totem Spots",
                color(MAP_TITLE),
                TextAlignment.LEFT);
        List<IngredientFarmSpot> spots = IngredientFarmSpotCatalog.all();
        float listY = ingredientFarmSpotListY(layout);
        float rowY = sidebar.y(listY);
        long previewRotationTimeMs = System.currentTimeMillis();
        if (spots.isEmpty()) {
            drawFittedText(
                    canvas,
                    PADDING,
                    rowY,
                    10,
                    "No farming spots have been added yet.",
                    color(MAP_SUBTEXT),
                    contentWidth,
                    TextAlignment.LEFT);
            return;
        }

        for (IngredientFarmSpot spot : spots) {
            List<Entry> ingredients = ingredientFarmSpotEntries(spot);
            float cardHeight = ingredientFarmSpotCardHeight(spot, ingredients, contentWidth);
            boolean selected = ingredientMapSelection.isTotemSelected(spot.id());
            boolean hovered = isHovered(
                            frame.mouseX(),
                            frame.mouseY(),
                            0,
                            SIDEBAR_PANEL_TOP,
                            SIDEBAR_WIDTH,
                            Math.max(0, frame.height() - SIDEBAR_PANEL_TOP))
                    && isHovered(
                            frame.mouseX(),
                            frame.mouseY(),
                            PADDING,
                            rowY,
                            contentWidth,
                            cardHeight);
            canvas.fillRect(
                    PADDING,
                    rowY,
                    contentWidth,
                    cardHeight,
                    selected
                            ? color(MAP_CONTROL_ACTIVE)
                            : hovered ? color(MAP_CONTROL_HOVER) : color(MAP_CONTROL));
            float textY = drawWrappedText(
                    canvas,
                    PADDING + INGREDIENT_FARM_SPOT_CARD_PADDING,
                    rowY + 12,
                    11,
                    spot.name(),
                    color(MAP_TEXT),
                    contentWidth - INGREDIENT_FARM_SPOT_CARD_PADDING * 2,
                    13);
            textY = drawWrappedText(
                    canvas,
                    PADDING + INGREDIENT_FARM_SPOT_CARD_PADDING,
                    textY,
                    10,
                    spot.coordinates(),
                    color(MAP_SUBTEXT),
                    contentWidth - INGREDIENT_FARM_SPOT_CARD_PADDING * 2,
                    12);
            renderIngredientFarmSpotTextures(
                    canvas,
                    ingredients,
                    PADDING + INGREDIENT_FARM_SPOT_CARD_PADDING,
                    textY + 2,
                    contentWidth - INGREDIENT_FARM_SPOT_CARD_PADDING * 2,
                    SIDEBAR_PANEL_TOP,
                    frame.height(),
                    previewRotationTimeMs);
            rowY += cardHeight + INGREDIENT_FARM_SPOT_CARD_GAP;
        }
    }

    private void renderIngredientFarmSpotTextures(
            UiCanvas canvas,
            List<Entry> ingredients,
            float x,
            float y,
            float width,
            float viewportTop,
            float viewportBottom,
            long previewRotationTimeMs) {
        int visibleIconCount = IngredientGuideScreen.farmSpotVisiblePreviewCount(ingredients.size());
        float iconsWidth = visibleIconCount == 0
                ? 0
                : visibleIconCount * INGREDIENT_FARM_SPOT_ICON_SIZE
                        + (visibleIconCount - 1) * INGREDIENT_FARM_SPOT_ICON_GAP;
        float iconsX = x + width - iconsWidth;
        int labelIndex = IngredientGuideScreen.farmSpotLabelIndex(ingredients.size(), previewRotationTimeMs);
        if (labelIndex >= 0) {
            Entry label = ingredients.get(labelIndex);
            drawWrappedText(
                    canvas,
                    x,
                    y + INGREDIENT_FARM_SPOT_ICON_SIZE / 2f,
                    9,
                    label.name(),
                    new Color(label.tierColor(), true),
                    Math.max(1, iconsX - x - INGREDIENT_FARM_SPOT_ICON_GAP),
                    10);
        }
        for (int previewSlot = 0; previewSlot < visibleIconCount; previewSlot++) {
            int previewIndex = IngredientGuideScreen.farmSpotPreviewIndex(
                    ingredients.size(), previewSlot, previewRotationTimeMs);
            Entry preview = ingredients.get(previewIndex);
            IngredientGuideEntry ingredient = preview.ingredient();
            float iconX =
                    iconsX + previewSlot * (INGREDIENT_FARM_SPOT_ICON_SIZE + INGREDIENT_FARM_SPOT_ICON_GAP);
            if (ingredient == null) {
                drawText(
                        canvas,
                        iconX + INGREDIENT_FARM_SPOT_ICON_SIZE / 2f,
                        y + INGREDIENT_FARM_SPOT_ICON_SIZE / 2f,
                        10,
                        "✦",
                        new Color(preview.tierColor(), true),
                        TextAlignment.CENTER);
                continue;
            }
            MapIngredientIcon icon = cachedIngredientIcon(ingredient);
            if (icon.stack().isEmpty()) {
                drawText(
                        canvas,
                        iconX + INGREDIENT_FARM_SPOT_ICON_SIZE / 2f,
                        y + INGREDIENT_FARM_SPOT_ICON_SIZE / 2f,
                        10,
                        "✦",
                        new Color(preview.tierColor(), true),
                        TextAlignment.CENTER);
            } else if (y >= viewportTop && y + INGREDIENT_FARM_SPOT_ICON_SIZE <= viewportBottom) {
                focusIconOverlays.add(new FocusIconOverlay(
                        iconX,
                        y,
                        INGREDIENT_FARM_SPOT_ICON_SIZE,
                        icon.stack(),
                        icon.skinLookup()));
            }
        }
    }

    private List<Entry> ingredientFarmSpotEntries(IngredientFarmSpot spot) {
        refreshIngredientEntryLookup();
        return IngredientFarmSpotDisplay.resolve(
                spot, name -> cachedIngredientsByName.get(name.toLowerCase(Locale.ROOT)));
    }

    private static float ingredientFarmSpotCardHeight(
            IngredientFarmSpot spot, List<Entry> ingredients, float width) {
        float innerWidth = width - INGREDIENT_FARM_SPOT_CARD_PADDING * 2;
        int titleLines = wrappedLineCount(spot.name(), innerWidth, 11);
        int coordinateLines = wrappedLineCount(spot.coordinates(), innerWidth, 10);
        int visibleIconCount = IngredientGuideScreen.farmSpotVisiblePreviewCount(ingredients.size());
        float iconsWidth = visibleIconCount == 0
                ? 0
                : visibleIconCount * INGREDIENT_FARM_SPOT_ICON_SIZE
                        + (visibleIconCount - 1) * INGREDIENT_FARM_SPOT_ICON_GAP;
        float labelWidth = Math.max(1, innerWidth - iconsWidth - INGREDIENT_FARM_SPOT_ICON_GAP);
        int labelLines = ingredients.stream()
                .mapToInt(ingredient -> wrappedLineCount(ingredient.name(), labelWidth, 9))
                .max()
                .orElse(0);
        float previewHeight = Math.max(
                visibleIconCount == 0 ? 0 : INGREDIENT_FARM_SPOT_ICON_SIZE,
                labelLines * 10f);
        float topPadding = 12;
        float previewGap = 2;
        float bottomPadding = 8;
        return topPadding
                + titleLines * 13
                + coordinateLines * 12
                + previewGap
                + previewHeight
                + bottomPadding;
    }

    private void refreshIngredientEntryLookup() {
        IngredientGuideManager.Snapshot snapshot = ingredientGuideManager.snapshot();
        if (snapshot.version() == cachedIngredientSnapshotVersion) {
            return;
        }
        Map<String, IngredientGuideEntry> ingredientsByName = new HashMap<>();
        for (IngredientGuideEntry ingredient : snapshot.ingredients()) {
            ingredientsByName.put(ingredient.displayName().toLowerCase(Locale.ROOT), ingredient);
            ingredientsByName.put(ingredient.internalName().toLowerCase(Locale.ROOT), ingredient);
        }
        cachedIngredientsByName = Map.copyOf(ingredientsByName);
        cachedIngredientSnapshotVersion = snapshot.version();
    }

    private MapIngredientIcon cachedIngredientIcon(IngredientGuideEntry ingredient) {
        return ingredientIconCache.computeIfAbsent(ingredient.icon().cacheKey(), ignored -> {
            ItemStack stack = IngredientItemIconFactory.create(ingredient.icon());
            GameProfile skinProfile = IngredientItemIconFactory.skinProfile(ingredient.icon());
            Supplier<PlayerSkin> skinLookup = skinProfile == null
                    ? null
                    : SeqClient.mc.getSkinManager().createLookup(skinProfile, false);
            return new MapIngredientIcon(stack, skinLookup);
        });
    }

    private void drawIngredientMapCategoryControl(UiCanvas canvas, float y) {
        float width = SIDEBAR_WIDTH - PADDING * 2;
        float segmentWidth = width / IngredientMapCategory.values().length;
        for (int index = 0; index < IngredientMapCategory.values().length; index++) {
            IngredientMapCategory category = IngredientMapCategory.values()[index];
            float x = PADDING + index * segmentWidth;
            boolean active = ingredientMapCategory == category;
            boolean hovered = isHovered(frame.mouseX(), frame.mouseY(), x, y, segmentWidth, BUTTON_HEIGHT);
            canvas.fillRect(
                    x,
                    y,
                    segmentWidth,
                    BUTTON_HEIGHT,
                    active ? color(MAP_CONTROL_ACTIVE) : hovered ? color(MAP_CONTROL_HOVER) : color(MAP_CONTROL));
            canvas.strokeRect(x, y, segmentWidth, BUTTON_HEIGHT, 1, color(MAP_BORDER));
            drawText(
                    canvas,
                    x + segmentWidth / 2f,
                    y + BUTTON_HEIGHT / 2f,
                    10,
                    category.label(),
                    color(MAP_TEXT),
                    TextAlignment.CENTER);
        }
    }

    private void drawIngredientCheckbox(
            UiCanvas canvas, float x, float y, float width, String label, boolean checked) {
        float checkboxY = y + (INGREDIENT_OPTION_HEIGHT - INGREDIENT_CHECKBOX_SIZE) / 2f;
        boolean hovered = isHovered(
                frame.mouseX(),
                frame.mouseY(),
                x,
                checkboxY,
                INGREDIENT_CHECKBOX_SIZE,
                INGREDIENT_CHECKBOX_SIZE);
        canvas.fillRect(
                x,
                checkboxY,
                INGREDIENT_CHECKBOX_SIZE,
                INGREDIENT_CHECKBOX_SIZE,
                checked
                        ? color(MAP_CONTROL_ACTIVE)
                        : hovered ? color(MAP_CONTROL_HOVER) : color(MAP_CONTROL));
        canvas.strokeRect(
                x,
                checkboxY,
                INGREDIENT_CHECKBOX_SIZE,
                INGREDIENT_CHECKBOX_SIZE,
                1,
                color(MAP_BORDER));
        if (checked) {
            float inset = 3;
            canvas.fillRect(
                    x + inset,
                    checkboxY + inset,
                    INGREDIENT_CHECKBOX_SIZE - inset * 2,
                    INGREDIENT_CHECKBOX_SIZE - inset * 2,
                    color(ACCENT_PRIMARY));
        }
        drawFittedText(
                canvas,
                x + INGREDIENT_CHECKBOX_SIZE + 7,
                y + INGREDIENT_OPTION_HEIGHT / 2f,
                10,
                label,
                color(MAP_SUBTEXT),
                Math.max(1, width - INGREDIENT_CHECKBOX_SIZE - 7),
                TextAlignment.LEFT);
    }

    boolean hasMapFocus() {
        return mapFocus != null && !mapFocus.markers().isEmpty();
    }

    private void selectIngredientFarmSpot(IngredientFarmSpot spot) {
        if (spot == null) {
            return;
        }
        insights.resetScroll();
        ingredientMapCategory = IngredientMapCategory.TOTEM_SPOTS;
        clearSelections();
        ingredientMapSelection.toggleTotem(spot.id());
        selectedIngredientFarmSpot = spot;
        actions.center(spot);
    }

    private void toggleIngredientFarmSpotSelection(IngredientFarmSpot spot) {
        insights.resetScroll();
        boolean selected = ingredientMapSelection.toggleTotem(spot.id());
        selectedIngredientFarmSpot = selected
                ? spot
                : IngredientFarmSpotCatalog.all().stream()
                        .filter(candidate -> ingredientMapSelection.isTotemSelected(candidate.id()))
                        .reduce((first, second) -> second)
                        .orElse(null);
    }

    private void toggleFocusMarkerSelection(MapFocus.Marker marker) {
        boolean selected = ingredientMapSelection.toggleSpawn(marker.id());
        selectedFocusMarker = selected
                ? marker
                : mapFocus.markers().stream()
                        .filter(candidate -> ingredientMapSelection.isSpawnSelected(candidate.id()))
                        .reduce((first, second) -> second)
                        .orElse(null);
    }

    void clearSelections() {
        ingredientMapSelection.clear();
        selectedFocusMarker = null;
        selectedIngredientFarmSpot = null;
    }

    private void renderSelectedIngredientWaypoints() {
        if (ingredientMapSelection.isEmpty()) {
            return;
        }
        List<Waypoint> waypoints = new ArrayList<>();
        if (mapFocus != null) {
            for (MapFocus.Marker marker : mapFocus.markers()) {
                if (!ingredientMapSelection.isSpawnSelected(marker.id())) {
                    continue;
                }
                waypoints.add(new Waypoint(
                        "ingredient-spawn:" + mapFocus.title() + ":" + marker.id(),
                        Kind.INGREDIENT_SPAWN,
                        marker.label(),
                        marker.source(),
                        marker.x(),
                        marker.y(),
                        marker.z(),
                        marker.radius(),
                        WaypointIcon.of(mapFocusIcon, mapFocusSkinLookup)));
            }
        }
        for (IngredientFarmSpot spot : IngredientFarmSpotCatalog.all()) {
            if (!ingredientMapSelection.isTotemSelected(spot.id())) {
                continue;
            }
            List<Entry> ingredients = ingredientFarmSpotEntries(spot);
            waypoints.add(new Waypoint(
                    "ingredient-totem:" + spot.id(),
                    Kind.TOTEM_SPOT,
                    spot.name(),
                    ingredients.stream()
                            .map(ingredient -> new DetailLine(
                                    ingredient.displayLine(), ingredient.tierColor()))
                            .toList(),
                    spot.x(),
                    spot.y(),
                    spot.z(),
                    spot.radius(),
                    WaypointIcon.of(new ItemStack(Items.TOTEM_OF_UNDYING), null)));
        }
        IngredientWaypointManager.getInstance().replaceAll(waypoints);
    }

    private IngredientMapCategory ingredientMapCategoryAt(float mouseX) {
        float segmentWidth = (SIDEBAR_WIDTH - PADDING * 2) / IngredientMapCategory.values().length;
        int index = (int) ((mouseX - PADDING) / segmentWidth);
        return index >= 0 && index < IngredientMapCategory.values().length
                ? IngredientMapCategory.values()[index]
                : ingredientMapCategory;
    }

    private static float ingredientFarmSpotListY(IngredientSidebarLayout layout) {
        return layout.guideY() + 22;
    }

    private float ingredientFarmSpotContentHeight() {
        float contentWidth = SIDEBAR_WIDTH - PADDING * 2;
        List<IngredientFarmSpot> spots = IngredientFarmSpotCatalog.all();
        float height = 0;
        for (IngredientFarmSpot spot : spots) {
            height += ingredientFarmSpotCardHeight(
                            spot,
                            ingredientFarmSpotEntries(spot),
                            contentWidth)
                    + INGREDIENT_FARM_SPOT_CARD_GAP;
        }
        return spots.isEmpty() ? 0 : height - INGREDIENT_FARM_SPOT_CARD_GAP;
    }

    private float ingredientSidebarContentHeight(IngredientSidebarLayout layout) {
        if (ingredientMapCategory == IngredientMapCategory.TOTEM_SPOTS) {
            return ingredientFarmSpotListY(layout) + ingredientFarmSpotContentHeight() + PADDING;
        }
        return layout.copyY() + BUTTON_HEIGHT + PADDING;
    }

    private IngredientSidebarLayout ingredientSidebarLayout() {
        float centerY = 58;
        float titleY = centerY + BUTTON_HEIGHT + 18;
        float renderWaypointsY = titleY + BUTTON_HEIGHT + 10;
        float radiusToggleY = renderWaypointsY + BUTTON_HEIGHT + 6;
        float radiusColorToggleY = radiusToggleY + INGREDIENT_OPTION_HEIGHT + 2;
        float clearWaypointsY = radiusColorToggleY + INGREDIENT_OPTION_HEIGHT + 6;
        float guideY = clearWaypointsY + BUTTON_HEIGHT + 18;
        float ingredientY = guideY + BUTTON_HEIGHT + 20;
        float summaryY = ingredientY + 20;
        float selectedTitleY = summaryY + 30;
        float selectedDetailY = selectedTitleY + 24;
        float copyY = selectedDetailY + 44;
        return new IngredientSidebarLayout(
                centerY,
                titleY,
                renderWaypointsY,
                radiusToggleY,
                radiusColorToggleY,
                clearWaypointsY,
                guideY,
                ingredientY,
                summaryY,
                selectedTitleY,
                selectedDetailY,
                copyY);
    }

    record FocusIconOverlay(
            float x,
            float y,
            float size,
            ItemStack stack,
            Supplier<PlayerSkin> skinLookup) {}

    record MapIngredientIcon(ItemStack stack, Supplier<PlayerSkin> skinLookup) {}

    private record IngredientSidebarLayout(
            float centerY,
            float titleY,
            float renderWaypointsY,
            float radiusToggleY,
            float radiusColorToggleY,
            float clearWaypointsY,
            float guideY,
            float ingredientY,
            float summaryY,
            float selectedTitleY,
            float selectedDetailY,
            float copyY) {}

    void renderMinecraftGuiOverlay(GuiGraphics guiGraphics, UiRenderMetrics metrics) {
        if (focusIconOverlays.isEmpty()) {
            return;
        }
        float guiUnitsPerUiUnit = metrics.pixelRatio() / (float) metrics.minecraftGuiScale();
        for (FocusIconOverlay overlay : focusIconOverlays) {
            float itemScale = overlay.size() * guiUnitsPerUiUnit / 16f;
            guiGraphics.pose().pushMatrix();
            try {
                guiGraphics.pose().translate(
                        overlay.x() * guiUnitsPerUiUnit,
                        overlay.y() * guiUnitsPerUiUnit);
                guiGraphics.pose().scale(itemScale, itemScale);
                if (overlay.skinLookup() != null) {
                    PlayerFaceRenderer.draw(guiGraphics, overlay.skinLookup().get(), 0, 0, 16);
                } else {
                    guiGraphics.renderItem(overlay.stack(), 0, 0);
                }
            } finally {
                guiGraphics.pose().popMatrix();
            }
        }
    }

    boolean clickSidebar(WorldMapFrame frame) {
        this.frame = frame;
        float mx = frame.mouseX(), my = frame.mouseY(), screenHeight = frame.height();
        IngredientSidebarLayout layout = ingredientSidebarLayout();
        float buttonWidth = SIDEBAR_WIDTH - PADDING * 2;
        boolean insideScrollablePanel = my >= SIDEBAR_PANEL_TOP && my <= screenHeight;
        if (insideScrollablePanel
                && isHovered(mx, my, PADDING, sidebar.y(layout.titleY()), buttonWidth, BUTTON_HEIGHT)) {
            IngredientMapCategory nextCategory = ingredientMapCategoryAt(mx);
            if (nextCategory != ingredientMapCategory) {
                ingredientMapCategory = nextCategory;
                insights.resetScroll();
                sidebar.reset();
            }
            hoveredFocusMarker = null;
            hoveredIngredientFarmSpot = null;
            return true;
        }
        if (insideScrollablePanel
                && isHovered(
                        mx,
                        my,
                        PADDING,
                        sidebar.y(layout.renderWaypointsY()),
                        buttonWidth,
                        BUTTON_HEIGHT)) {
            renderSelectedIngredientWaypoints();
            return true;
        }
        float radiusCheckboxY = sidebar.y(layout.radiusToggleY())
                + (INGREDIENT_OPTION_HEIGHT - INGREDIENT_CHECKBOX_SIZE) / 2f;
        if (insideScrollablePanel
                && isHovered(
                        mx,
                        my,
                        PADDING,
                        radiusCheckboxY,
                        INGREDIENT_CHECKBOX_SIZE,
                        INGREDIENT_CHECKBOX_SIZE)) {
            mapSettings.setShowIngredientWaypointRadii(!mapSettings.showIngredientWaypointRadii());
            return true;
        }
        float radiusColorCheckboxY = sidebar.y(layout.radiusColorToggleY())
                + (INGREDIENT_OPTION_HEIGHT - INGREDIENT_CHECKBOX_SIZE) / 2f;
        if (insideScrollablePanel
                && isHovered(
                        mx,
                        my,
                        PADDING,
                        radiusColorCheckboxY,
                        INGREDIENT_CHECKBOX_SIZE,
                        INGREDIENT_CHECKBOX_SIZE)) {
            mapSettings.setColorIngredientWaypointRadiiByProximity(
                    !mapSettings.colorIngredientWaypointRadiiByProximity());
            return true;
        }
        if (insideScrollablePanel
                && isHovered(
                        mx,
                        my,
                        PADDING,
                        sidebar.y(layout.clearWaypointsY()),
                        buttonWidth,
                        BUTTON_HEIGHT)) {
            IngredientWaypointManager.getInstance().clear();
            return true;
        }
        if (ingredientMapCategory == IngredientMapCategory.TOTEM_SPOTS) {
            if (insideScrollablePanel) {
                float rowY = sidebar.y(ingredientFarmSpotListY(layout));
                for (IngredientFarmSpot spot : IngredientFarmSpotCatalog.all()) {
                    float cardHeight = ingredientFarmSpotCardHeight(
                            spot,
                            ingredientFarmSpotEntries(spot),
                            buttonWidth);
                    if (isHovered(
                            mx,
                            my,
                            PADDING,
                            rowY,
                            buttonWidth,
                            cardHeight)) {
                        selectIngredientFarmSpot(spot);
                        return true;
                    }
                    rowY += cardHeight + INGREDIENT_FARM_SPOT_CARD_GAP;
                }
            }
        } else if (insideScrollablePanel
                && isHovered(mx, my, PADDING, sidebar.y(layout.guideY()), buttonWidth, BUTTON_HEIGHT)) {
            actions.openGuide();
            return true;
        }
        if (ingredientMapCategory == IngredientMapCategory.SPAWNS
                && selectedFocusMarker != null
                && insideScrollablePanel
                && isHovered(mx, my, PADDING, sidebar.y(layout.copyY()), buttonWidth, BUTTON_HEIGHT)) {
            actions.copy(selectedFocusMarker.coordinates());
            return true;
        }

        return mx >= 0 && mx <= SIDEBAR_WIDTH;
    }
}
