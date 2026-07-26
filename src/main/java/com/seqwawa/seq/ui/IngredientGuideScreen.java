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
import com.seqwawa.seq.managers.IngredientItemIconFactory;
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
    private static final float ROW_HEIGHT = 43;
    private static final float SCROLL_STEP = 34;
    private static final float PANEL_RADIUS = 7;
    private static final Color[] TIER_COLORS = {
        new Color(150, 150, 165),
        new Color(240, 240, 245),
        new Color(245, 195, 72),
        new Color(226, 94, 94)
    };

    private final Screen parent;
    private final IngredientGuideManager manager = IngredientGuideManager.getInstance();
    private final List<LocationHitbox> locationHitboxes = new ArrayList<>();
    private ActionHitbox showAllMapHitbox;

    private long observedSnapshotVersion = -1;
    private String observedQuery = "";
    private List<IngredientGuideEntry> visibleIngredients = List.of();
    private IngredientGuideEntry selectedIngredient;
    private String searchQuery = "";
    private boolean searchFocused;
    private float listScroll;
    private float detailScroll;
    private float maxListScroll;
    private float maxDetailScroll;
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
        drawText(canvas, manager.status(), screenWidth - 92, HEADER_HEIGHT / 2f, 11, color(TEXT_MUTED),
                UiCanvas.HorizontalAlign.RIGHT, UiCanvas.VerticalAlign.MIDDLE);
        drawButton(canvas, screenWidth - 82, 9, 68, 24, manager.isLoading() ? "Loading" : "Refresh");

        renderIngredientList(canvas, listX, panelTop, listWidth, panelHeight);
        renderIngredientDetail(canvas, detailX, panelTop, detailWidth, panelHeight);
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

        float summaryY = y + 9 + SEARCH_HEIGHT + 14;
        drawText(canvas, visibleIngredients.size() + " ingredients", x + 11, summaryY, 10, color(TEXT_MUTED),
                UiCanvas.HorizontalAlign.LEFT, UiCanvas.VerticalAlign.MIDDLE);

        float rowsTop = summaryY + 12;
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
        drawScrollbar(canvas, x + width - 5, rowsTop, rowsHeight, listScroll, maxListScroll);
    }

    private void renderIngredientDetail(UiCanvas canvas, float x, float y, float width, float height) {
        canvas.fillRoundedRect(x, y, width, height, PANEL_RADIUS, color(BACKGROUND_BODY_OPAQUE, 245));
        locationHitboxes.clear();
        showAllMapHitbox = null;
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
                    selectedIngredient.dropSources().size() + " mobs  •  " + locationCount + " public spawn locations",
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
                        drawText(canvas, "Public spawn coordinates unavailable", contentX + 11, cursorY + 35, 10,
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
        drawScrollbar(canvas, x + width - 5, y + 8, height - 16, detailScroll, maxDetailScroll);
    }

    private void refreshVisibleIngredients() {
        IngredientGuideManager.Snapshot snapshot = manager.snapshot();
        if (snapshot.version() == observedSnapshotVersion && searchQuery.equals(observedQuery)) {
            return;
        }
        String selectedName = selectedIngredient == null ? null : selectedIngredient.internalName();
        visibleIngredients = IngredientGuideManager.filter(snapshot.ingredients(), searchQuery);
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

        if (contains(mx, my, screenWidth - 82, 9, 68, 24)) {
            manager.requestRefresh(true);
            return true;
        }
        if (contains(mx, my, OUTER_MARGIN + 9, panelTop + 9, listWidth - 18, SEARCH_HEIGHT)) {
            searchFocused = true;
            return true;
        }
        searchFocused = false;

        float summaryY = panelTop + 9 + SEARCH_HEIGHT + 14;
        float rowsTop = summaryY + 12;
        float rowsHeight = Math.max(0, panelTop + panelHeight - rowsTop - 8);
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
        if (key == GLFW.GLFW_KEY_SLASH) {
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

    private void drawScrollbar(
            UiCanvas canvas, float x, float y, float height, float scroll, float maxScroll) {
        if (maxScroll <= 0 || height <= 0) {
            return;
        }
        canvas.fillRoundedRect(x, y, 3, height, 1.5f, color(CONTROL_TRACK));
        float thumbHeight = Math.max(20, height * height / (height + maxScroll));
        float thumbY = y + (height - thumbHeight) * (scroll / maxScroll);
        canvas.fillRoundedRect(x, thumbY, 3, thumbHeight, 1.5f, color(CONTROL_THUMB));
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
}
