package com.seqwawa.seq.ui;

import static com.seqwawa.seq.managers.ThemeManager.color;
import static com.seqwawa.seq.ui.theme.UiColor.*;

import static com.seqwawa.seq.ui.WorldMapUi.*;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import com.seqwawa.seq.map.IngredientFarmSpot;
import com.seqwawa.seq.map.IngredientFarmSpotDisplay.Entry;
import com.seqwawa.seq.map.IngredientMapCategory;
import com.seqwawa.seq.map.MapFocus;
import com.seqwawa.seq.model.IngredientGuideEntry;
import com.seqwawa.seq.utils.rendering.UiCanvas;

import com.seqwawa.seq.ui.IngredientMapMode.MapIngredientIcon;
import com.seqwawa.seq.ui.IngredientMapMode.FocusIconOverlay;

/** Ingredient detail cards and their independent scroll position. */
final class IngredientMapInsights {

    private static final float PADDING = 12, INSIGHTS_SIDEBAR_WIDTH = 250, SIDEBAR_HEADER_HEIGHT = 44, SIDEBAR_SCROLL_STEP = 28;
    private float ingredientTotemInsightsScroll;
    private IngredientMapMode.View view;
    private WorldMapFrame frame;
    private final java.util.function.Function<IngredientFarmSpot, List<Entry>> entries;
    private final java.util.function.Function<IngredientGuideEntry, MapIngredientIcon> icons;
    private final java.util.function.Consumer<FocusIconOverlay> overlays;
    IngredientMapInsights(java.util.function.Function<IngredientFarmSpot, List<Entry>> entries,
            java.util.function.Function<IngredientGuideEntry, MapIngredientIcon> icons,
            java.util.function.Consumer<FocusIconOverlay> overlays) {
        this.entries = entries;
        this.icons = icons;
        this.overlays = overlays;
    }
    void resetScroll() { ingredientTotemInsightsScroll = 0; }
    void scroll(IngredientMapMode.View view, WorldMapFrame frame, double delta) {
        this.view = view;
        if (view.category() == IngredientMapCategory.TOTEM_SPOTS && frame.mouseY() >= SIDEBAR_HEADER_HEIGHT && frame.mouseY() <= frame.height()) {
            ingredientTotemInsightsScroll = (float) Math.max(0, Math.min(ingredientTotemInsightsScroll - delta * SIDEBAR_SCROLL_STEP, ingredientTotemInsightsMaxScroll(frame.height())));
        }
    }
    private void renderIngredientInsights(UiCanvas canvas, float x) {
        float contentX = x + PADDING;
        float contentWidth = INSIGHTS_SIDEBAR_WIDTH - PADDING * 2;
        drawInsightsSectionTitle(canvas, contentX, 60, "Ingredient");
        if (!(view.focus() != null && !view.focus().markers().isEmpty())) {
            drawFittedText(
                    canvas,
                    contentX,
                    84,
                    11,
                    "No ingredient selected. Open one from the Ingredient Guide.",
                    color(MAP_SUBTEXT),
                    contentWidth,
                    TextAlignment.LEFT);
            return;
        }
        drawFittedText(canvas, contentX, 84, 13, view.focus().title(), color(MAP_TEXT), contentWidth, TextAlignment.LEFT);
        drawInsightRow(canvas, contentX, 106, contentWidth, "Spawn locations", String.valueOf(view.focus().markers().size()));
        long sourceCount = view.focus().markers().stream().map(MapFocus.Marker::source).distinct().count();
        drawInsightRow(canvas, contentX, 122, contentWidth, "Mob sources", String.valueOf(sourceCount));
        if (view.selectedSpawn() != null) {
            drawInsightsSectionTitle(
                    canvas,
                    contentX,
                    154,
                    "Selected Spawns (" + view.spawnCount() + ")");
            drawFittedText(
                    canvas,
                    contentX,
                    178,
                    12,
                    view.selectedSpawn().source(),
                    color(MAP_TEXT),
                    contentWidth,
                    TextAlignment.LEFT);
            drawFittedText(
                    canvas,
                    contentX,
                    196,
                    11,
                    view.selectedSpawn().coordinates(),
                    color(MAP_SUBTEXT),
                    contentWidth,
                    TextAlignment.LEFT);
        }
    }

    private void renderIngredientTotemInsights(
            UiCanvas canvas, float contentX, float contentWidth, float overlayOffsetY) {
        if (view.selectedSpot() == null) {
            canvas.fillRoundedRect(contentX, 60, contentWidth, 78, 5, color(MAP_HEADER));
            canvas.strokeRect(contentX, 60, contentWidth, 78, 1, color(MAP_BORDER));
            drawText(
                    canvas,
                    contentX + 10,
                    81,
                    11,
                    "Select a totem spot",
                    color(MAP_TEXT),
                    TextAlignment.LEFT);
            drawWrappedText(
                    canvas,
                    contentX + 10,
                    103,
                    9,
                    "Click a sidebar entry or map marker to inspect its ingredients.",
                    color(MAP_SUBTEXT),
                    contentWidth - 20,
                    11);
            return;
        }

        List<Entry> ingredients = entries.apply(view.selectedSpot());
        drawInsightsSectionTitle(canvas, contentX, 60, "Selected Spot");
        float spotCardY = 72;
        float spotCardHeight = selectedTotemSpotCardHeight(contentWidth);
        canvas.fillRoundedRect(contentX, spotCardY, contentWidth, spotCardHeight, 5, color(MAP_HEADER));
        canvas.strokeRect(contentX, spotCardY, contentWidth, spotCardHeight, 1, color(MAP_BORDER));
        float spotTextY = drawWrappedText(
                canvas,
                contentX + 9,
                spotCardY + 14,
                12,
                view.selectedSpot().name(),
                color(MAP_TEXT),
                contentWidth - 18,
                14);
        spotTextY = drawWrappedText(
                canvas,
                contentX + 9,
                spotTextY + 1,
                10,
                view.selectedSpot().coordinates()
                        + (view.selectedSpot().radius() > 0
                                ? " · radius " + view.selectedSpot().radius()
                                : ""),
                color(MAP_SUBTEXT),
                contentWidth - 18,
                12);
        drawWrappedText(
                canvas,
                contentX + 9,
                spotTextY + 1,
                9,
                ingredientTierSummary(ingredients),
                color(MAP_SUBTEXT),
                contentWidth - 18,
                11);

        float ingredientsTitleY = spotCardY + spotCardHeight + 15;
        drawInsightsSectionTitle(canvas, contentX, ingredientsTitleY, "Ingredients");
        float ingredientY = ingredientsTitleY + 14;
        float screenHeight = frame.height();
        for (Entry ingredient : ingredients) {
            float cardHeight = renderIngredientInsightCard(
                    canvas,
                    ingredient,
                    contentX,
                    ingredientY,
                    contentWidth,
                    screenHeight,
                    overlayOffsetY);
            ingredientY += cardHeight + 6;
        }

        float insightY = ingredientY + 4;
        insightY = drawWrappedText(
                canvas,
                contentX,
                insightY,
                9,
                ingredientProfessionSummary(ingredients),
                color(MAP_SUBTEXT),
                contentWidth,
                11);
        String mobs = view.selectedSpot().mobs().isEmpty()
                ? "Mobs: not catalogued"
                : "Mobs: " + String.join(", ", view.selectedSpot().mobs());
        insightY = drawWrappedText(
                canvas,
                contentX,
                insightY + 2,
                9,
                mobs,
                color(MAP_SUBTEXT),
                contentWidth,
                11);
        if (!view.selectedSpot().notes().isBlank()) {
            drawWrappedText(
                    canvas,
                    contentX,
                    insightY + 2,
                    9,
                    "Note: " + view.selectedSpot().notes(),
                    color(MAP_SUBTEXT),
                    contentWidth,
                    11);
        }
    }

    private float renderIngredientInsightCard(
            UiCanvas canvas,
            Entry ingredient,
            float x,
            float y,
            float width,
            float screenHeight,
            float overlayOffsetY) {
        Color tierColor = new Color(ingredient.tierColor(), true);
        float cardHeight = ingredientInsightCardHeight(ingredient, width);
        canvas.fillRoundedRect(x, y, width, cardHeight, 5, color(MAP_CONTROL));
        canvas.strokeRect(x, y, width, cardHeight, 1, color(MAP_BORDER));
        canvas.fillRect(x, y, 3, cardHeight, tierColor);
        renderIngredientInsightIcon(
                canvas,
                ingredient,
                x + 8,
                y + 7,
                28,
                screenHeight,
                overlayOffsetY);
        float textY = drawWrappedText(
                canvas,
                x + 43,
                y + 11,
                11,
                ingredient.name(),
                tierColor,
                width - 51,
                12);
        drawWrappedText(
                canvas,
                x + 43,
                textY + 1,
                9,
                ingredient.metadata(),
                color(MAP_SUBTEXT),
                width - 51,
                10);
        return cardHeight;
    }

    private void renderIngredientInsightIcon(
            UiCanvas canvas,
            Entry ingredient,
            float x,
            float y,
            float size,
            float screenHeight,
            float overlayOffsetY) {
        if (ingredient.ingredient() == null) {
            drawText(
                    canvas,
                    x + size / 2f,
                    y + size / 2f,
                    13,
                    "✦",
                    new Color(ingredient.tierColor(), true),
                    TextAlignment.CENTER);
            return;
        }
        MapIngredientIcon icon = icons.apply(ingredient.ingredient());
        if (icon.stack().isEmpty()) {
            drawText(
                    canvas,
                    x + size / 2f,
                    y + size / 2f,
                    13,
                    "✦",
                    new Color(ingredient.tierColor(), true),
                    TextAlignment.CENTER);
        } else {
            float renderedY = y + overlayOffsetY;
            if (renderedY < SIDEBAR_HEADER_HEIGHT || renderedY + size > screenHeight) {
                return;
            }
            overlays.accept(new FocusIconOverlay(
                    x,
                    renderedY,
                    size,
                    icon.stack(),
                    icon.skinLookup()));
        }
    }

    private static String ingredientTierSummary(List<Entry> ingredients) {
        int[] tierCounts = new int[4];
        int unknownCount = 0;
        for (Entry ingredient : ingredients) {
            if (ingredient.ingredient() == null) {
                unknownCount++;
                continue;
            }
            int tier = ingredient.ingredient().tier();
            tierCounts[Math.max(0, Math.min(tierCounts.length - 1, tier))]++;
        }
        List<String> summary = new ArrayList<>();
        for (int tier = 0; tier < tierCounts.length; tier++) {
            if (tierCounts[tier] > 0) {
                summary.add("T" + tier + " " + tierCounts[tier]);
            }
        }
        if (unknownCount > 0) {
            summary.add("? " + unknownCount);
        }
        return String.join(" · ", summary);
    }

    private static String ingredientProfessionSummary(List<Entry> ingredients) {
        Set<String> professions = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (Entry ingredient : ingredients) {
            if (ingredient.ingredient() == null) {
                continue;
            }
            ingredient.ingredient().skills().stream()
                    .map(WorldMapUi::displayEnumValue)
                    .forEach(professions::add);
        }
        return professions.isEmpty()
                ? "Professions unavailable"
                : "Professions: " + String.join(", ", professions);
    }

    private float selectedTotemSpotCardHeight(float contentWidth) {
        float textWidth = contentWidth - 18;
        int nameLines = wrappedLineCount(view.selectedSpot().name(), textWidth, 12);
        String coordinates = view.selectedSpot().coordinates()
                + (view.selectedSpot().radius() > 0
                        ? " · radius " + view.selectedSpot().radius()
                        : "");
        int coordinateLines = wrappedLineCount(coordinates, textWidth, 10);
        int tierLines = wrappedLineCount(
                ingredientTierSummary(entries.apply(view.selectedSpot())),
                textWidth,
                9);
        return 20 + nameLines * 14 + coordinateLines * 12 + tierLines * 11;
    }

    private static float ingredientInsightCardHeight(Entry ingredient, float width) {
        float textWidth = width - 51;
        int nameLines = wrappedLineCount(ingredient.name(), textWidth, 11);
        int metadataLines = wrappedLineCount(ingredient.metadata(), textWidth, 9);
        return Math.max(43, 17 + nameLines * 12 + metadataLines * 10);
    }

    private float ingredientTotemInsightsMaxScroll(float screenHeight) {
        return Math.max(0, ingredientTotemInsightsContentBottom() + PADDING - screenHeight);
    }

    private float ingredientTotemInsightsContentBottom() {
        if (view.selectedSpot() == null) {
            return 138;
        }
        float contentWidth = INSIGHTS_SIDEBAR_WIDTH - PADDING * 2;
        List<Entry> ingredients = entries.apply(view.selectedSpot());
        float spotCardHeight = selectedTotemSpotCardHeight(contentWidth);
        float contentBottom = 72 + spotCardHeight + 29;
        for (Entry ingredient : ingredients) {
            contentBottom += ingredientInsightCardHeight(ingredient, contentWidth) + 6;
        }
        contentBottom += 4;
        contentBottom += wrappedLineCount(ingredientProfessionSummary(ingredients), contentWidth, 9) * 11;
        String mobs = view.selectedSpot().mobs().isEmpty()
                ? "Mobs: not catalogued"
                : "Mobs: " + String.join(", ", view.selectedSpot().mobs());
        contentBottom += 2 + wrappedLineCount(mobs, contentWidth, 9) * 11;
        if (!view.selectedSpot().notes().isBlank()) {
            contentBottom += 2
                    + wrappedLineCount(
                                    "Note: " + view.selectedSpot().notes(),
                                    contentWidth,
                                    9)
                            * 11;
        }
        return contentBottom;
    }

    private void renderIngredientTotemInsightsScrollbar(
            UiCanvas canvas, float x, float screenHeight, float maxScroll) {
        if (maxScroll <= 0) {
            return;
        }
        float trackY = SIDEBAR_HEADER_HEIGHT + 4;
        float trackHeight = Math.max(0, screenHeight - trackY - 4);
        float viewportHeight = Math.max(1, screenHeight - SIDEBAR_HEADER_HEIGHT);
        float contentHeight = Math.max(viewportHeight, ingredientTotemInsightsContentBottom() - SIDEBAR_HEADER_HEIGHT);
        float thumbHeight = Math.max(24, trackHeight * viewportHeight / contentHeight);
        float thumbY = trackY + (trackHeight - thumbHeight) * (ingredientTotemInsightsScroll / maxScroll);
        float trackX = x + INSIGHTS_SIDEBAR_WIDTH - 5;
        canvas.fillRect(trackX, trackY, 3, trackHeight, color(CONTROL_TRACK));
        canvas.fillRect(trackX, thumbY, 3, thumbHeight, color(CONTROL_THUMB));
    }

    void render(UiCanvas canvas, IngredientMapMode.View view, WorldMapFrame frame, float x) {
        this.frame = frame; this.view = view;
        if (view.category() == IngredientMapCategory.TOTEM_SPOTS) {
            float maxScroll = ingredientTotemInsightsMaxScroll(frame.height());
            ingredientTotemInsightsScroll = (float) Math.max(0, Math.min(ingredientTotemInsightsScroll, maxScroll));
            canvas.save();
            canvas.translate(0, -ingredientTotemInsightsScroll);
            renderIngredientTotemInsights(canvas, x + PADDING, INSIGHTS_SIDEBAR_WIDTH - PADDING * 2, -ingredientTotemInsightsScroll);
            canvas.restore();
            renderIngredientTotemInsightsScrollbar(canvas, x, frame.height(), maxScroll);
        } else renderIngredientInsights(canvas, x);
    }
}
