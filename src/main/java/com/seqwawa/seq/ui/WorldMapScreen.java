package com.seqwawa.seq.ui;

import static com.seqwawa.seq.managers.ThemeManager.color;
import static com.seqwawa.seq.ui.theme.UiColor.*;

import static com.seqwawa.seq.ui.WorldMapUi.*;

import com.mojang.authlib.GameProfile;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;
import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.map.GatheringMapImageService;
import com.seqwawa.seq.map.IngredientFarmSpot;
import com.seqwawa.seq.map.MapCalibration;
import com.seqwawa.seq.map.MapBounds;
import com.seqwawa.seq.map.MapDisplayMode;
import com.seqwawa.seq.map.MapFocus;
import com.seqwawa.seq.map.MapViewport;
import com.seqwawa.seq.map.MapPlayerHeadRenderer;
import com.seqwawa.seq.map.WorldEventService;
import com.seqwawa.seq.map.WorldMapSettings;
import com.seqwawa.seq.map.WorldMapBackgroundRenderer;
import com.seqwawa.seq.managers.IngredientGuideManager;
import com.seqwawa.seq.render.MinecraftGuiOverlay;
import com.seqwawa.seq.utils.TextInputHelper;
import com.seqwawa.seq.utils.rendering.MinecraftUiRenderer;
import com.seqwawa.seq.utils.rendering.UiCanvas;
import com.seqwawa.seq.utils.rendering.UiRenderMetrics;
import com.seqwawa.seq.utils.rendering.UiRenderer;
import net.minecraft.world.item.ItemStack;

public class WorldMapScreen extends Screen implements MinecraftGuiOverlay {
    private static final float SIDEBAR_WIDTH = 230;
    private static final float INSIGHTS_SIDEBAR_WIDTH = 250;
    private static final float INSIGHTS_RAIL_WIDTH = 28;
    private static final float PADDING = 12;
    private static final float BUTTON_HEIGHT = 24;

    private static final float SIDEBAR_HEADER_HEIGHT = 44;

    private static final long CENTER_PLAYER_WARNING_DURATION_MS = 6_767;

    private static final float WORLD_EVENT_DETAIL_HEIGHT = 122;

    private static final double MIN_PIXELS_PER_BLOCK = MapViewport.MIN_PIXELS_PER_BLOCK;
    private static final double MAX_PIXELS_PER_BLOCK = MapViewport.MAX_PIXELS_PER_BLOCK;

    private static final double CONTEXT_FOCUS_MAX_PIXELS_PER_BLOCK = 0.75;

    private final GatheringMapImageService mapImageService = GatheringMapImageService.getInstance();
    private final WorldMapBackgroundRenderer mapBackground = new WorldMapBackgroundRenderer(mapImageService);
    private final MapPlayerHeadRenderer playerHeads = new MapPlayerHeadRenderer();
    private final WorldMapSettings mapSettings = WorldMapSettings.getInstance();
    private final WorldEventService worldEventService = WorldEventService.getInstance();
    private final WorldEventMapMode eventMode = new WorldEventMapMode(worldEventService::snapshot,
            worldEventService::status, mapSettings, new WorldEventMapMode.Tracking() {
                public Set<String> ids() { return SeqClient.getConfigManager().trackedWorldEventIds(); }
                public void setTracked(String id, boolean tracked) { SeqClient.getConfigManager().setWorldEventTracked(id, tracked); }
            });

    private double centerX = (MapCalibration.MIN_WORLD_X + MapCalibration.MAX_WORLD_X) / 2.0;
    private double centerZ = (MapCalibration.MIN_WORLD_Z + MapCalibration.MAX_WORLD_Z) / 2.0;
    private double pixelsPerBlock = 0.08;
    private boolean initializedViewport;
    private boolean draggingMap;
    private boolean mapDragMoved;
    private boolean clearIngredientSelectionOnRelease;
    private boolean insightsSidebarOpen;
    private long centerPlayerWarningUntilMs;

    private MapDisplayMode displayMode = MapDisplayMode.GATHERING;
    private boolean mapModeDropdownOpen;
    private final GatheringMapMode gatheringMode = new GatheringMapMode(mapSettings, new GatheringMapMode.Actions() {
        public void center(double x, double z, double scale) { centerX = x; centerZ = z; pixelsPerBlock = scale; }
        public void startDragging() { draggingMap = true; }
        public void copy(String coordinates) { copyToClipboard(coordinates); }
        public void renderPlayer(UiCanvas canvas, MapViewport viewport) { WorldMapScreen.this.renderPlayer(canvas, viewport); }
    });
    private final IngredientMapMode ingredientMode;
    private final Screen parent;
    private float nvgMouseX;
    private float nvgMouseY;

    public WorldMapScreen(Screen parent) {
        this(parent, null, ItemStack.EMPTY, null);
    }

    public WorldMapScreen(Screen parent, MapFocus mapFocus) {
        this(parent, mapFocus, ItemStack.EMPTY, null);
    }

    public WorldMapScreen(Screen parent, MapFocus mapFocus, ItemStack mapFocusIcon) {
        this(parent, mapFocus, mapFocusIcon, null);
    }

    public WorldMapScreen(
            Screen parent,
            MapFocus mapFocus,
            ItemStack mapFocusIcon,
            GameProfile mapFocusSkinProfile) {
        this(parent, mapFocus, mapFocusIcon, mapFocusSkinProfile, null);
    }

    public WorldMapScreen(Screen parent, IngredientFarmSpot farmSpot) {
        this(parent, null, ItemStack.EMPTY, null, farmSpot);
    }

    private WorldMapScreen(
            Screen parent,
            MapFocus mapFocus,
            ItemStack mapFocusIcon,
            GameProfile mapFocusSkinProfile,
            IngredientFarmSpot farmSpot) {
        super(Component.literal("Sequoia Map"));
        this.parent = parent;
        ingredientMode = new IngredientMapMode(mapFocus, mapFocusIcon, mapFocusSkinProfile, farmSpot,
                mapSettings, new IngredientMapMode.Actions() {
                    public void center(IngredientFarmSpot spot) { centerOnIngredientFarmSpot(spot); }
                    public void openGuide() { SeqClient.mc.setScreen(new IngredientGuideScreen(WorldMapScreen.this)); }
                    public void copy(String coordinates) { copyToClipboard(coordinates); }
                });
        displayMode = (mapFocus == null || mapFocus.markers().isEmpty()) && farmSpot == null
                ? mapSettings.displayMode()
                : MapDisplayMode.INGREDIENTS;
        insightsSidebarOpen = mapSettings.insightsSidebarOpen();
        mapImageService.requestLoad();
        IngredientGuideManager.getInstance().requestRefresh();
        SeqClient.getWorldEventManager().requestMapRefresh();
    }

    @Override
    public void removed() {
        gatheringMode.close();
        UiRenderer.renderResource(canvas -> {
            mapBackground.close();
            playerHeads.close();
        });
        super.removed();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        nvgMouseX = MinecraftUiRenderer.mouseX(mouseX);
        nvgMouseY = MinecraftUiRenderer.mouseY(mouseY);

        float screenWidth = uiScreenWidth();
        float screenHeight = uiScreenHeight();
        float mapX = SIDEBAR_WIDTH;
        float mapY = 0;
        float mapW = Math.max(1, screenWidth - SIDEBAR_WIDTH - insightsSidebarInset());
        float mapH = Math.max(1, screenHeight);

        if (!initializedViewport) {
            initializedViewport = true;
            if (ingredientMode.selectedSpot() != null) {
                centerOnIngredientFarmSpot(ingredientMode.selectedSpot());
            } else if (ingredientMode.hasMapFocus()) {
                fitMapFocus(mapW, mapH);
            } else {
                fitFullMap(mapW, mapH);
            }
        }

        if (displayMode == MapDisplayMode.GATHERING) {
            gatheringMode.refresh(mapFrame(nvgMouseX, nvgMouseY));
        } else if (displayMode == MapDisplayMode.WORLD_EVENTS) {
            eventMode.refresh();
        }
        MapViewport viewport = new MapViewport(centerX, centerZ, pixelsPerBlock, mapX, mapY, mapW, mapH);
        UiRenderer.renderScreen(this, canvas -> {
            renderNvg(canvas, viewport);
            drawMapModeControl(canvas);
        });
    }

    private void renderNvg(UiCanvas canvas, MapViewport viewport) {
        ingredientMode.beginFrame(mapFrame(nvgMouseX, nvgMouseY));
        renderMapBackground(canvas, viewport);
        if (displayMode == MapDisplayMode.WORLD_EVENTS) {
            eventMode.renderMap(canvas, mapFrame(nvgMouseX, nvgMouseY));
            renderPlayer(canvas, viewport);
            renderSidebar(canvas);
            renderInsightsSidebar(canvas);
            return;
        }
        if (displayMode == MapDisplayMode.INGREDIENTS) {
            ingredientMode.renderMap(canvas, viewport);
            renderPlayer(canvas, viewport);
            renderSidebar(canvas);
            renderInsightsSidebar(canvas);
            return;
        }

        gatheringMode.renderMap(canvas, mapFrame(nvgMouseX, nvgMouseY));
        renderSidebar(canvas);
        renderInsightsSidebar(canvas);
    }

    @Override
    public void renderMinecraftGuiOverlay(GuiGraphics graphics, UiRenderMetrics metrics) {
        if (displayMode == MapDisplayMode.INGREDIENTS) ingredientMode.renderMinecraftGuiOverlay(graphics, metrics);
    }

    private void renderMapBackground(UiCanvas canvas, MapViewport viewport) {
        mapBackground.render(canvas, viewport);
        mapImageService.unavailableMessage()
                .ifPresent(message -> renderMapUnavailable(canvas, viewport, message));
    }

    private void renderMapUnavailable(UiCanvas canvas, MapViewport viewport, String message) {
        float panelWidth = Math.min(430, Math.max(220, viewport.screenWidth() - 64));
        float contentWidth = panelWidth - 32;
        List<String> lines = wrapText(message, contentWidth, 11);
        float panelHeight = 48 + lines.size() * 16;
        float x = viewport.screenX() + (viewport.screenWidth() - panelWidth) / 2;
        float y = viewport.screenY() + (viewport.screenHeight() - panelHeight) / 2;

        canvas.fillRoundedRect(x, y, panelWidth, panelHeight, 6, color(STATUS_DANGER_BACKGROUND));
        canvas.strokeRect(x, y, panelWidth, panelHeight, 1, color(STATUS_DANGER_BORDER));
        drawText(
                canvas,
                x + panelWidth / 2,
                y + 18,
                14,
                "Map unavailable",
                color(TEXT_PRIMARY),
                TextAlignment.CENTER);
        for (int index = 0; index < lines.size(); index++) {
            drawText(
                    canvas,
                    x + 16,
                    y + 42 + index * 16,
                    11,
                    lines.get(index),
                    color(TEXT_SECONDARY),
                    TextAlignment.LEFT);
        }
    }

    // Explicit cluster-hull-only alpha override requested for the map (35%, rounded to 8-bit alpha).

    private void renderPlayer(UiCanvas canvas, MapViewport viewport) {
        playerHeads.renderLocalPlayer(canvas, viewport);
    }

    private void renderSidebar(UiCanvas canvas) {
        WorldMapFrame frame = mapFrame(nvgMouseX, nvgMouseY);
        if (displayMode == MapDisplayMode.INGREDIENTS) ingredientMode.renderSidebar(canvas, frame, centerPlayerButtonLabel());
        else if (displayMode == MapDisplayMode.WORLD_EVENTS) eventMode.renderSidebar(canvas, frame, centerPlayerButtonLabel());
        else gatheringMode.renderSidebar(canvas, frame, centerPlayerButtonLabel());
    }

    private void renderInsightsSidebar(UiCanvas canvas) {
        float screenWidth = uiScreenWidth();
        float screenHeight = uiScreenHeight();
        if (!insightsSidebarOpen) {
            drawText(
                    canvas,
                    screenWidth - INSIGHTS_RAIL_WIDTH / 2f,
                    10 + BUTTON_HEIGHT / 2f,
                    16,
                    "<",
                    color(MAP_TEXT),
                    TextAlignment.CENTER);
            return;
        }

        float x = screenWidth - INSIGHTS_SIDEBAR_WIDTH;
        canvas.fillRect(x, 0, INSIGHTS_SIDEBAR_WIDTH, screenHeight, color(MAP_SIDEBAR));
        canvas.fillRect(x, 0, INSIGHTS_SIDEBAR_WIDTH, SIDEBAR_HEADER_HEIGHT, color(MAP_HEADER));
        drawText(canvas, x + PADDING, 22, 16, "Insights", color(MAP_TITLE), TextAlignment.LEFT);
        drawButton(canvas, x + INSIGHTS_SIDEBAR_WIDTH - PADDING - 24, 10, 24, BUTTON_HEIGHT, ">", false);
        canvas.scissor(x, SIDEBAR_HEADER_HEIGHT, INSIGHTS_SIDEBAR_WIDTH, Math.max(0, screenHeight - SIDEBAR_HEADER_HEIGHT));
        if (displayMode == MapDisplayMode.WORLD_EVENTS) {
            eventMode.renderInsights(canvas, mapFrame(nvgMouseX, nvgMouseY), x, 60, 142);
        } else if (displayMode == MapDisplayMode.INGREDIENTS) {
            ingredientMode.renderInsights(canvas, mapFrame(nvgMouseX, nvgMouseY), x);
        } else {
            gatheringMode.renderInsights(canvas, mapFrame(nvgMouseX, nvgMouseY), x);
        }
        canvas.resetScissor();
    }

    private WorldMapModeDropdownLayout mapModeDropdownLayout() {
        MapViewport viewport = mapViewport(uiScreenWidth(), uiScreenHeight());
        return WorldMapModeDropdownLayout.fit(viewport.screenX(), viewport.screenWidth());
    }

    private void drawMapModeControl(UiCanvas canvas) {
        var layout = mapModeDropdownLayout();
        canvas.save();
        canvas.scissor(layout.x(), layout.y(), layout.width(),
                layout.rowHeight() * (MapDisplayMode.values().length + 1));
        DropdownMenu.trigger(canvas, layout.x(), layout.y(), layout.width(), layout.rowHeight(),
                displayMode.label(), mapModeDropdownOpen, true, nvgMouseX, nvgMouseY);
        if (mapModeDropdownOpen) {
            DropdownMenu.list(canvas, layout.x(), layout.y() + layout.rowHeight(), layout.width(), layout.rowHeight(),
                    java.util.Arrays.stream(MapDisplayMode.values()).map(MapDisplayMode::label).toList(),
                    i -> MapDisplayMode.values()[i] == displayMode, 0, MapDisplayMode.values().length, nvgMouseX, nvgMouseY);
        }
        canvas.restore();
        canvas.save();
        canvas.scissor(layout.closeX(), layout.y(), layout.width(), layout.rowHeight());
        boolean closeHovered = isHovered(nvgMouseX, nvgMouseY,
                layout.closeX(), layout.y(), layout.width(), layout.rowHeight());
        canvas.fillRect(layout.closeX(), layout.y(), layout.width(), layout.rowHeight(),
                closeHovered ? color(CONTROL_DANGER_HOVER) : color(CONTROL_DANGER));
        canvas.strokeRect(layout.closeX(), layout.y(), layout.width(), layout.rowHeight(), 1, color(MAP_BORDER));
        drawText(canvas, layout.closeX() + layout.width() / 2f, layout.y() + layout.rowHeight() / 2f,
                12, "Close Map", color(MAP_TEXT), TextAlignment.CENTER);
        canvas.restore();
    }

    private boolean clickMapModeDropdown(float mx, float my) {
        var layout = mapModeDropdownLayout();
        if (layout.contains(mx, my, false)) {
            mapModeDropdownOpen = !mapModeDropdownOpen;
            closeSearchDropdowns();
            draggingMap = false;
            return true;
        }
        if (mapModeDropdownOpen) {
            int option = layout.optionAt(mx, my);
            mapModeDropdownOpen = false;
            if (option >= 0) {
                setDisplayMode(MapDisplayMode.values()[option]);
            }
            // Dismissing the menu must not select or drag the map underneath it.
            return true;
        }
        return false;
    }

    private void drawButton(UiCanvas canvas, float x, float y, float w, float h, String label, boolean active) {
        boolean hovered = isHovered(nvgMouseX, nvgMouseY, x, y, w, h);
        canvas.fillRect(x, y, w, h, active ? color(MAP_CONTROL_ACTIVE) : hovered ? color(MAP_CONTROL_HOVER) : color(MAP_CONTROL));
        canvas.strokeRect(x, y, w, h, 1, color(MAP_BORDER));
        drawText(canvas, x + w / 2f, y + h / 2f, 12, label, color(MAP_TEXT), TextAlignment.CENTER);
    }

    private String centerPlayerButtonLabel() {
        return System.currentTimeMillis() < centerPlayerWarningUntilMs ? "Leave housing bum !" : "Center Player";
    }

    private boolean copyHoveredCoordinates(float mx, float my, float screenWidth, float screenHeight) {
        MapViewport viewport = mapViewport(screenWidth, screenHeight);
        if (displayMode == MapDisplayMode.INGREDIENTS) return ingredientMode.copyHovered(mapFrame(mx, my));
        float insightsX = insightsSidebarX(screenWidth);
        if (displayMode == MapDisplayMode.WORLD_EVENTS) {
            if (insightsSidebarOpen
                    && eventMode.selected() != null
                    && isHovered(
                            mx,
                            my,
                            insightsX + PADDING,
                            142 + 4,
                            INSIGHTS_SIDEBAR_WIDTH - PADDING * 2,
                            WORLD_EVENT_DETAIL_HEIGHT)) {
                copyToClipboard(WorldEventMapMode.worldEventCoordinates(eventMode.selected().locations().getFirst()));
                return true;
            }
            if (viewport.isInsideScreen(mx, my)
                    && eventMode.hovered() != null
                    && eventMode.hoveredLocationIndex() >= 0) {
                copyToClipboard(WorldEventMapMode.worldEventCoordinates(
                        eventMode.hovered().locations().get(eventMode.hoveredLocationIndex())));
                return true;
            }
            return false;
        }
        return gatheringMode.copyHoveredCoordinates(mapFrame(mx, my), insightsSidebarOpen, insightsSidebarX(screenWidth));
    }

    private void copyToClipboard(String text) {
        SeqClient.mc.keyboardHandler.setClipboard(text);
    }

    private void fitFullMap(float mapW, float mapH) {
        pixelsPerBlock = MapViewport.fitPixelsPerBlock(
                MapCalibration.fullBounds(), mapW, mapH, MapViewport.FULL_MAP_FIT_SCALE);
    }

    private void fitMapFocus(float mapW, float mapH) {
        MapBounds bounds = ingredientMode.focusBounds();
        centerX = (bounds.minX() + bounds.maxX()) / 2.0;
        centerZ = (bounds.minZ() + bounds.maxZ()) / 2.0;
        double spanX = Math.max(80, bounds.maxX() - bounds.minX());
        double spanZ = Math.max(80, bounds.maxZ() - bounds.minZ());
        double xScale = Math.max(1, mapW - 80) / spanX;
        double zScale = Math.max(1, mapH - 100) / spanZ;
        pixelsPerBlock = clamp(
                Math.min(Math.min(xScale, zScale) * 0.9, CONTEXT_FOCUS_MAX_PIXELS_PER_BLOCK),
                MIN_PIXELS_PER_BLOCK,
                MAX_PIXELS_PER_BLOCK);
    }

    private void centerOnIngredientFarmSpot(IngredientFarmSpot spot) {
        centerX = spot.x();
        centerZ = spot.z();
        pixelsPerBlock = Math.max(pixelsPerBlock, 0.35);
    }

    private boolean centerOnPlayer() {
        if (SeqClient.mc.player == null) {
            return false;
        }
        double playerX = SeqClient.mc.player.getX();
        double playerZ = SeqClient.mc.player.getZ();
        if (!MapCalibration.fullBounds().contains(playerX, playerZ)) {
            return false;
        }
        centerX = playerX;
        centerZ = playerZ;
        pixelsPerBlock = Math.max(pixelsPerBlock, 0.18);
        return true;
    }

    @Override
    public void onClose() {
        SeqClient.mc.setScreen(parent == null || parent instanceof net.minecraft.client.gui.screens.ChatScreen
                ? new SequoiaScreen() : parent);
    }

    @Override
    public boolean mouseClicked(@NotNull MouseButtonEvent click, boolean outsideScreen) {
        float mx = scaledMouseX(click.x());
        float my = scaledMouseY(click.y());
        float screenWidth = uiScreenWidth();
        float screenHeight = uiScreenHeight();

        if (mapModeDropdownLayout().containsClose(mx, my)) {
            if (click.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                onClose();
            }
            return true;
        }
        if (click.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT && clickMapModeDropdown(mx, my)) {
            return true;
        }
        if (mapModeDropdownLayout().contains(mx, my, mapModeDropdownOpen)) {
            return true;
        }
        if (click.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            if (copyHoveredCoordinates(mx, my, screenWidth, screenHeight)) {
                return true;
            }
            return super.mouseClicked(click, outsideScreen);
        }
        if (click.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return super.mouseClicked(click, outsideScreen);
        }
        mapDragMoved = false;
        clearIngredientSelectionOnRelease = false;

        if (mx >= 0 && mx <= SIDEBAR_WIDTH && my < SIDEBAR_HEADER_HEIGHT) {
            return true;
        }
        if (mouseClickedInsights(mx, my, screenWidth, screenHeight)) {
            return true;
        }
        MapViewport focusedViewport = mapViewport(screenWidth, screenHeight);
        if (displayMode == MapDisplayMode.INGREDIENTS) {
            if (isHovered(mx, my, PADDING, 58, SIDEBAR_WIDTH - PADDING * 2, BUTTON_HEIGHT)) {
                if (!centerOnPlayer()) centerPlayerWarningUntilMs = System.currentTimeMillis() + CENTER_PLAYER_WARNING_DURATION_MS;
                return true;
            }
            if (focusedViewport.isInsideScreen(mx, my)) {
                clearIngredientSelectionOnRelease = !ingredientMode.clickMap(mapFrame(mx, my));
                draggingMap = true;
                closeSearchDropdowns();
                return true;
            }
            if (ingredientMode.clickSidebar(mapFrame(mx, my))) return true;
            return super.mouseClicked(click, outsideScreen);
        }
        if (displayMode == MapDisplayMode.WORLD_EVENTS) {
            if (mouseClickedWorldEvents(mx, my, screenWidth, screenHeight)) {
                return true;
            }
            return super.mouseClicked(click, outsideScreen);
        }
        if (isHovered(mx, my, PADDING, 58, SIDEBAR_WIDTH - PADDING * 2, BUTTON_HEIGHT)) {
            if (!centerOnPlayer()) centerPlayerWarningUntilMs = System.currentTimeMillis() + CENTER_PLAYER_WARNING_DURATION_MS;
            return true;
        }
        return gatheringMode.click(mapFrame(mx, my)) || super.mouseClicked(click, outsideScreen);
    }

    private boolean mouseClickedInsights(float mx, float my, float screenWidth, float screenHeight) {
        if (!insightsSidebarOpen) {
            if (isHovered(
                    mx,
                    my,
                    screenWidth - INSIGHTS_RAIL_WIDTH,
                    10,
                    INSIGHTS_RAIL_WIDTH,
                    BUTTON_HEIGHT)) {
                setInsightsSidebarOpen(true);
                return true;
            }
            return false;
        }

        float x = insightsSidebarX(screenWidth);
        if (mx < x || mx > screenWidth) {
            return false;
        }
        if (isHovered(
                mx,
                my,
                x + INSIGHTS_SIDEBAR_WIDTH - PADDING - 24,
                10,
                24,
                BUTTON_HEIGHT)) {
            setInsightsSidebarOpen(false);
            return true;
        }

        if (displayMode == MapDisplayMode.WORLD_EVENTS) { eventMode.clickTrackingButton(mapFrame(mx, my), x, 142); return true; }
        if (displayMode == MapDisplayMode.INGREDIENTS) return true;
        return gatheringMode.clickInsights(mapFrame(mx, my), x);
    }

    private void setInsightsSidebarOpen(boolean open) {
        if (insightsSidebarOpen == open) {
            return;
        }
        insightsSidebarOpen = open;
        mapSettings.setInsightsSidebarOpen(open);
        draggingMap = false;
    }

    private boolean mouseClickedWorldEvents(float mx, float my, float screenWidth, float screenHeight) {
        if (isHovered(mx, my, PADDING, 58, SIDEBAR_WIDTH - PADDING * 2, BUTTON_HEIGHT)) {
            if (!centerOnPlayer()) centerPlayerWarningUntilMs = System.currentTimeMillis() + CENTER_PLAYER_WARNING_DURATION_MS;
            return true;
        }
        WorldMapFrame frame = mapFrame(mx, my);
        if (eventMode.clickSidebar(frame)) return true;
        if (!frame.viewport().isInsideScreen(mx, my)) return false;
        eventMode.selectHovered();
        draggingMap = true;
        closeSearchDropdowns();
        return true;
    }

    @Override
    public boolean mouseReleased(@NotNull MouseButtonEvent click) {
        boolean clearIngredientSelection = click.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT
                && draggingMap
                && clearIngredientSelectionOnRelease
                && !mapDragMoved;
        draggingMap = false;
        mapDragMoved = false;
        clearIngredientSelectionOnRelease = false;
        if (clearIngredientSelection) {
            ingredientMode.clearSelections();
            return true;
        }
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent click, double deltaX, double deltaY) {
        if (draggingMap) {
            mapDragMoved = true;
            clearIngredientSelectionOnRelease = false;
            centerX -= MinecraftUiRenderer.mouseDelta(deltaX) / pixelsPerBlock;
            centerZ -= MinecraftUiRenderer.mouseDelta(deltaY) / pixelsPerBlock;
            gatheringMode.clearHover();
            eventMode.clearHover();
            ingredientMode.clearHover();
            return true;
        }
        return super.mouseDragged(click, deltaX, deltaY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        float mx = scaledMouseX(mouseX);
        float my = scaledMouseY(mouseY);
        if (mapModeDropdownLayout().contains(mx, my, mapModeDropdownOpen)
                || mapModeDropdownLayout().containsClose(mx, my)) {
            return true;
        }
        if (displayMode == MapDisplayMode.WORLD_EVENTS && eventMode.scroll(mapFrame(mx, my), scrollY)) return true;
        if (displayMode == MapDisplayMode.INGREDIENTS && ingredientMode.scroll(mapFrame(mx, my), scrollY, insightsSidebarOpen, insightsSidebarX(uiScreenWidth()))) return true;
        if (displayMode == MapDisplayMode.GATHERING && gatheringMode.scroll(mapFrame(mx, my), scrollY)) return true;
        float screenWidth = uiScreenWidth();
        float screenHeight = uiScreenHeight();
        if (mx >= insightsSidebarX(screenWidth) && mx <= screenWidth) {
            return true;
        }
        MapViewport viewport = mapViewport(screenWidth, screenHeight);
        if (!viewport.isInsideScreen(mx, my)) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        double factor = scrollY > 0 ? MapViewport.SCROLL_ZOOM_FACTOR : 1 / MapViewport.SCROLL_ZOOM_FACTOR;
        MapViewport zoomed = viewport.zoomAt(mx, my, factor);
        centerX = zoomed.centerX();
        centerZ = zoomed.centerZ();
        pixelsPerBlock = zoomed.pixelsPerBlock();
        return true;
    }

    @Override
    public boolean keyPressed(@NotNull KeyEvent keyEvent) {
        int keyCode = keyEvent.key();
        if (mapModeDropdownOpen && keyCode == GLFW.GLFW_KEY_ESCAPE) {
            mapModeDropdownOpen = false;
            return true;
        }
        if (displayMode == MapDisplayMode.WORLD_EVENTS && eventMode.keyPressed(keyCode)) return true;
        if (displayMode == MapDisplayMode.GATHERING && gatheringMode.keyPressed(keyCode)) return true;
        return super.keyPressed(keyEvent);
    }

    @Override
    public boolean charTyped(@NotNull CharacterEvent characterEvent) {
        String typedText = searchText(characterEvent);
        if (displayMode == MapDisplayMode.WORLD_EVENTS && eventMode.charTyped(typedText)) return true;
        if (displayMode == MapDisplayMode.GATHERING && gatheringMode.charTyped(typedText)) return true;
        return super.charTyped(characterEvent);
    }

    private float scaledMouseX(double rawX) {
        return MinecraftUiRenderer.mouseX(rawX);
    }

    private float scaledMouseY(double rawY) {
        return MinecraftUiRenderer.mouseY(rawY);
    }

    private static float uiScreenWidth() {
        return MinecraftUiRenderer.screenWidth();
    }

    private static float uiScreenHeight() {
        return MinecraftUiRenderer.screenHeight();
    }

    private static boolean isHovered(float mx, float my, float x, float y, float w, float h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    private static String searchText(CharacterEvent characterEvent) {
        String typedText = TextInputHelper.getTypedText(characterEvent);
        return typedText == null ? null : typedText.toUpperCase(Locale.ROOT);
    }

    private void setDisplayMode(MapDisplayMode mode) {
        if (mode == null || mode == displayMode) {
            return;
        }
        eventMode.deactivate();
        ingredientMode.deactivate();
        displayMode = mode;
        mapSettings.setDisplayMode(mode);
        draggingMap = false;
        gatheringMode.deactivate();
        closeSearchDropdowns();
        if (mode == MapDisplayMode.WORLD_EVENTS) {
            SeqClient.getWorldEventManager().requestMapRefresh();
            eventMode.refresh();
        }
    }

    private void closeSearchDropdowns() {
        gatheringMode.closeSearchDropdowns();
        eventMode.closeSearch();
    }

    private float insightsSidebarInset() {
        return insightsSidebarOpen ? INSIGHTS_SIDEBAR_WIDTH : 0;
    }

    private float insightsSidebarX(float screenWidth) {
        return screenWidth - insightsSidebarInset();
    }

    private MapViewport mapViewport(float screenWidth, float screenHeight) {
        return new MapViewport(
                centerX,
                centerZ,
                pixelsPerBlock,
                SIDEBAR_WIDTH,
                0,
                Math.max(1, screenWidth - SIDEBAR_WIDTH - insightsSidebarInset()),
                screenHeight);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private WorldMapFrame mapFrame(float mouseX, float mouseY) {
        return new WorldMapFrame(uiScreenWidth(), uiScreenHeight(), mouseX, mouseY,
                mapViewport(uiScreenWidth(), uiScreenHeight()), draggingMap);
    }
}
