package com.seqwawa.seq.ui;

import static com.seqwawa.seq.managers.ThemeManager.color;
import static com.seqwawa.seq.ui.theme.UiColor.*;

import static com.seqwawa.seq.ui.WarPlannerDialogUi.*;
import static com.seqwawa.seq.ui.WarPingPicker.pingCandidates;

import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.managers.AssetManager;
import com.seqwawa.seq.map.GuildTerritory;
import com.seqwawa.seq.map.GuildTerritoryIndex;
import com.seqwawa.seq.map.MapCalibration;
import com.seqwawa.seq.map.MapBounds;
import com.seqwawa.seq.map.MapViewport;
import com.seqwawa.seq.model.war.WarPlannerSnapshot;
import com.seqwawa.seq.model.war.WarPlannerSnapshot.Zone;
import com.seqwawa.seq.utils.rendering.UiCanvas;
import java.awt.Color;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** War-map layout, territory geometry and stateless rendering. */
final class WarMapGeometry {
    private static final float PADDING = 12;
    private static final float HEADER_HEIGHT = SequoiaUiStyle.HEADER_HEIGHT;

    private static final float WAR_MAP_SIDEBAR_GAP = 8;

    private static final float HQ_ICON_WIDTH = 24;
    private static final float HQ_ICON_HEIGHT = 19.5f;

    static final int RESOURCE_FILL_ALPHA = 96;

    static void drawHqIcon(
            UiCanvas canvas,
            GuildTerritory territory,
            MapBounds coordinateBounds,
            float offsetX,
            float offsetY,
            float scale) {
        AssetManager.Asset asset = SeqClient.assetManager == null
                ? null
                : SeqClient.assetManager.getAsset("hq_icon");
        if (asset == null || asset.getImage() == null) return;
        float centerX = previewX(territory.centerX(), coordinateBounds, offsetX, scale);
        float centerY = previewY(territory.centerZ(), coordinateBounds, offsetY, scale);
        canvas.drawImage(
                asset.getImage(),
                centerX - HQ_ICON_WIDTH / 2,
                centerY - HQ_ICON_HEIGHT / 2,
                HQ_ICON_WIDTH,
                HQ_ICON_HEIGHT,
                1f);
    }

    static List<GuildTerritory> resolveTerritories(
            List<String> names, Map<String, GuildTerritory> territoriesByName) {
        return names.stream().map(territoriesByName::get).filter(java.util.Objects::nonNull).toList();
    }

    static List<GuildTerritory> visibleMapTerritories(
            List<GuildTerritory> territories, WarPlannerSnapshot snapshot, boolean locked) {
        if (snapshot == null) return List.copyOf(territories);
        return visibleMapTerritories(territories, snapshot.zones(), locked);
    }

    static List<GuildTerritory> visibleMapTerritories(
            List<GuildTerritory> territories, List<Zone> zones, boolean locked) {
        Set<String> visible = visibleTerritoryNames(
                zones,
                territories.stream().map(GuildTerritory::name).collect(java.util.stream.Collectors.toSet()),
                locked);
        return territories.stream()
                .filter(territory -> visible.contains(territory.name()))
                .toList();
    }

    static Set<String> visibleTerritoryNames(WarPlannerSnapshot snapshot, Set<String> territories, boolean locked) {
        if (snapshot == null) return Set.copyOf(territories);
        return visibleTerritoryNames(snapshot.zones(), territories, locked);
    }

    static Set<String> visibleTerritoryNames(List<Zone> zones, Set<String> territories, boolean locked) {
        if (!locked) return Set.copyOf(territories);
        Set<String> zoned = zones.stream()
                .flatMap(zone -> zone.territories().stream())
                .map(name -> name.toLowerCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toSet());
        return territories.stream()
                .filter(name -> zoned.contains(name.toLowerCase(Locale.ROOT)))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    static Set<String> shownZoneTerritoryNames(List<Zone> displayedZones) {
        if (displayedZones == null || displayedZones.isEmpty()) return Set.of();
        return displayedZones.stream()
                .filter(java.util.Objects::nonNull)
                .flatMap(zone -> zone.territories().stream())
                .filter(name -> name != null && !name.isBlank())
                .map(name -> name.toLowerCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    static List<GuildTerritory> oneHopContextTerritories(
            List<GuildTerritory> allTerritories,
            List<GuildTerritory> coreTerritories,
            Map<String, WarPlannerSnapshot.TerritoryDetails> details) {
        Set<String> coreNames = coreTerritories.stream()
                .map(GuildTerritory::name)
                .map(name -> name.toLowerCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toSet());
        Set<String> contextNames = new java.util.HashSet<>();
        for (GuildTerritory territory : coreTerritories) {
            WarPlannerSnapshot.TerritoryDetails detail = details.get(territory.name());
            if (detail == null) continue;
            detail.connections().stream()
                    .map(name -> name.toLowerCase(Locale.ROOT))
                    .filter(name -> !coreNames.contains(name))
                    .forEach(contextNames::add);
        }
        return allTerritories.stream()
                .filter(territory -> contextNames.contains(territory.name().toLowerCase(Locale.ROOT)))
                .toList();
    }

    static boolean shouldRefitWarMap(
            boolean fitted,
            float fittedWidth,
            float fittedHeight,
            Boolean fittedLocked,
            WarMapLayout layout,
            boolean locked) {
        return !fitted
                || layout == null
                || fittedWidth != layout.mapWidth()
                || fittedHeight != layout.mapHeight()
                || fittedLocked == null
                || fittedLocked != locked;
    }

    static MapViewport fittedWarMapViewport(MapBounds bounds, WarMapLayout layout) {
        double scale = MapViewport.fitPixelsPerBlock(bounds, layout.mapWidth() - 10, layout.mapHeight() - 10, 1);
        return new MapViewport(
                (bounds.minX() + bounds.maxX()) / 2,
                (bounds.minZ() + bounds.maxZ()) / 2,
                scale,
                layout.mapX(),
                layout.mapY(),
                layout.mapWidth(),
                layout.mapHeight());
    }

    static float warMapSidebarWidth(float width) {
        return Math.min(220, Math.max(150, width * .25f));
    }

    static WarMapLayout warMapLayout(float width, float top, float bottom) {
        float sidebarWidth = warMapSidebarWidth(width);
        float sidebarX = width - PADDING - sidebarWidth;
        return new WarMapLayout(
                PADDING,
                top,
                Math.max(1, sidebarX - PADDING - WAR_MAP_SIDEBAR_GAP),
                Math.max(1, bottom - top),
                sidebarX,
                sidebarWidth);
    }

    static WarMapControls warMapControls(WarMapLayout layout, boolean canManage) {
        float panelWidth = Math.min(240, Math.max(1, layout.mapWidth() - 16));
        float panelHeight = (canManage ? 3 : 2) * 24 + 8;
        float x = layout.mapX() + layout.mapWidth() - panelWidth - 8;
        float y = Math.max(layout.mapY() + 34, layout.mapY() + layout.mapHeight() - panelHeight - 8);
        var panel = new WarMapButtonBounds(x, y, panelWidth, panelHeight);
        var lock = new WarMapButtonBounds(x, y, panelWidth, canManage ? 24 : 0);
        var queues = new WarMapButtonBounds(x, y + (canManage ? 24 : 0), panelWidth, 24);
        var players = new WarMapButtonBounds(x, queues.y() + 24, panelWidth, 24);
        var coloring = new WarMapButtonBounds(layout.sidebarX() + 6, layout.mapY() + 2, layout.sidebarWidth() - 12, 22);
        var fit = new WarMapButtonBounds(layout.mapX() + 8, layout.mapY() + 8, Math.min(44, layout.mapWidth() - 16), 22);
        return new WarMapControls(fit, queues, players, lock, panel, coloring);
    }

    static MapBounds fittedBounds(List<GuildTerritory> territories) {
        double minX = territories.stream().map(GuildTerritory::bounds).mapToDouble(MapBounds::minX).min().orElse(0);
        double minZ = territories.stream().map(GuildTerritory::bounds).mapToDouble(MapBounds::minZ).min().orElse(0);
        double maxX = territories.stream().map(GuildTerritory::bounds).mapToDouble(MapBounds::maxX).max().orElse(1);
        double maxZ = territories.stream().map(GuildTerritory::bounds).mapToDouble(MapBounds::maxZ).max().orElse(1);
        return new MapBounds(minX, minZ, maxX, maxZ);
    }

    static MapBounds zonePreviewBounds(List<GuildTerritory> selectedTerritories) {
        if (selectedTerritories == null || selectedTerritories.isEmpty()) return mapImageBounds();
        MapBounds selected = fittedBounds(selectedTerritories);
        double paddingX = Math.max(180, (selected.maxX() - selected.minX()) * .18);
        double paddingZ = Math.max(180, (selected.maxZ() - selected.minZ()) * .18);
        MapBounds map = mapImageBounds();
        return new MapBounds(
                Math.max(map.minX(), selected.minX() - paddingX),
                Math.max(map.minZ(), selected.minZ() - paddingZ),
                Math.min(map.maxX(), selected.maxX() + paddingX),
                Math.min(map.maxZ(), selected.maxZ() + paddingZ));
    }

    static MapBounds warMapFitBounds(List<GuildTerritory> displayedTerritories, boolean locked) {
        return locked ? zonePreviewBounds(displayedTerritories) : mapImageBounds();
    }

    static MapBounds mapImageBounds() {
        return MapCalibration.fullBounds();
    }

    static float previewX(double worldX, MapBounds fitted, float offsetX, float scale) {
        return offsetX + (float) ((worldX - fitted.minX()) * scale);
    }

    static float previewY(double worldZ, MapBounds fitted, float offsetY, float scale) {
        return offsetY + (float) ((worldZ - fitted.minZ()) * scale);
    }

    static GuildTerritory territoryAt(
            GuildTerritoryIndex territoryIndex,
            MapViewport viewport,
            Set<String> displayedNames,
            float mouseX,
            float mouseY) {
        if (territoryIndex == null || viewport == null || !viewport.isInsideScreen(mouseX, mouseY)) return null;
        GuildTerritory territory = territoryIndex.territoryAt(
                viewport.screenToWorldX(mouseX), viewport.screenToWorldZ(mouseY));
        return territory != null
                        && displayedNames != null
                        && displayedNames.contains(territory.name().toLowerCase(Locale.ROOT))
                ? territory
                : null;
    }

    static void drawPreviewResources(
            UiCanvas canvas,
            List<GuildTerritory> territories,
            Map<String, WarPlannerSnapshot.TerritoryDetails> details,
            MapBounds fitted,
            float offsetX,
            float offsetY,
            float scale,
            int alpha) {
        for (GuildTerritory territory : territories) {
            MapBounds bounds = territory.bounds();
            float territoryX = previewX(bounds.minX(), fitted, offsetX, scale);
            float territoryY = previewY(bounds.minZ(), fitted, offsetY, scale);
            float territoryWidth = Math.max(2, (float) ((bounds.maxX() - bounds.minX()) * scale));
            float territoryHeight = Math.max(2, (float) ((bounds.maxZ() - bounds.minZ()) * scale));
            WarTerritoryPickerScreen.renderResourceFill(
                    canvas, territoryX, territoryY, territoryWidth, territoryHeight, details.get(territory.name()), alpha);
        }
    }

    static void drawPreviewFill(
            UiCanvas canvas,
            GuildTerritory territory,
            MapBounds fitted,
            float offsetX,
            float offsetY,
            float scale,
            Color fill) {
        MapBounds bounds = territory.bounds();
        float territoryX = previewX(bounds.minX(), fitted, offsetX, scale);
        float territoryY = previewY(bounds.minZ(), fitted, offsetY, scale);
        float territoryWidth = Math.max(2, (float) ((bounds.maxX() - bounds.minX()) * scale));
        float territoryHeight = Math.max(2, (float) ((bounds.maxZ() - bounds.minZ()) * scale));
        canvas.fillRect(territoryX, territoryY, territoryWidth, territoryHeight, fill);
    }

    static void drawTerritoryName(
            UiCanvas canvas,
            GuildTerritory territory,
            MapBounds fitted,
            float offsetX,
            float offsetY,
            float scale,
            WarMapLayout layout) {
        float labelWidth = Math.max(1,
                Math.min(layout.mapWidth() - 12, Math.max(64, territory.name().length() * 6 + 14)));
        float centerX = previewX(territory.centerX(), fitted, offsetX, scale);
        float centerY = previewY(territory.centerZ(), fitted, offsetY, scale);
        float labelX = Math.max(layout.mapX() + 6,
                Math.min(centerX - labelWidth / 2, layout.mapX() + layout.mapWidth() - labelWidth - 6));
        float labelY = Math.max(layout.mapY() + 34,
                Math.min(centerY - 11, layout.mapY() + layout.mapHeight() - 25));
        canvas.fillRect(labelX, labelY, labelWidth, 20, color(BACKGROUND_POPUP));
        text(canvas, truncate(territory.name(), 34), labelX + labelWidth / 2, labelY + 10,
                9, color(MAP_TEXT), true);
    }

    static void drawPreviewOutlines(
            UiCanvas canvas,
            List<GuildTerritory> territories,
            MapBounds fitted,
            float offsetX,
            float offsetY,
            float scale,
            Color stroke,
            float strokeWidth,
            float outset) {
        drawPreviewOutlines(canvas, territories, fitted, offsetX, offsetY, scale, stroke, strokeWidth, outset, false);
    }

    static void drawPreviewOutlines(
            UiCanvas canvas, List<GuildTerritory> territories, MapBounds fitted,
            float offsetX, float offsetY, float scale, Color stroke,
            float strokeWidth, float outset, boolean resourceColors) {
        for (GuildTerritory territory : territories) {
            MapBounds bounds = territory.bounds();
            float territoryX = previewX(bounds.minX(), fitted, offsetX, scale) - outset;
            float territoryY = previewY(bounds.minZ(), fitted, offsetY, scale) - outset;
            float territoryWidth = Math.max(2, (float) ((bounds.maxX() - bounds.minX()) * scale)) + outset * 2;
            float territoryHeight = Math.max(2, (float) ((bounds.maxZ() - bounds.minZ()) * scale)) + outset * 2;
            float weight = territoryOutlineWeight(Math.min(territoryWidth, territoryHeight), strokeWidth, resourceColors);
            if (weight > 0) canvas.strokeRect(territoryX, territoryY, territoryWidth, territoryHeight, weight, stroke);
        }
    }

    static float territoryOutlineWeight(float projectedSize, float requestedWidth) {
        return territoryOutlineWeight(projectedSize, requestedWidth, false);
    }

    static float territoryOutlineWeight(float projectedSize, float requestedWidth, boolean resourceColors) {
        if (resourceColors) return requestedWidth;
        if (requestedWidth >= 2) return requestedWidth;
        if (projectedSize < 5) return 0;
        return Math.min(requestedWidth, .5f + Math.min(1, projectedSize / 36) * .6f);
    }

    static boolean warConnectionVisible(float scale, boolean emphasized) {
        return warConnectionVisible(scale, emphasized, false);
    }

    static boolean warConnectionVisible(float scale, boolean emphasized, boolean resourceColors) {
        return resourceColors || emphasized || scale >= .22f;
    }

    static void drawPreviewConnections(
            UiCanvas canvas,
            List<GuildTerritory> territories,
            Map<String, GuildTerritory> territoriesByName,
            Map<String, WarPlannerSnapshot.TerritoryDetails> details,
            Set<String> displayedNames,
            MapBounds fitted,
            float offsetX,
            float offsetY,
            float scale,
            Set<String> emphasizedTerritories,
            boolean resourceColors) {
        Set<String> drawnConnections = new java.util.HashSet<>();
        Color foreground = color(TEXT_PRIMARY);
        for (GuildTerritory territory : territories) {
            WarPlannerSnapshot.TerritoryDetails detail = details.get(territory.name());
            if (detail == null) continue;
            for (String linkedName : detail.connections()) {
                GuildTerritory linked = territoriesByName.get(linkedName);
                if (linked == null || !displayedNames.contains(linked.name().toLowerCase(Locale.ROOT))) continue;
                String key = territory.name().compareToIgnoreCase(linkedName) < 0
                        ? territory.name() + "\n" + linkedName : linkedName + "\n" + territory.name();
                if (!drawnConnections.add(key)) continue;
                boolean emphasized = emphasizedTerritories.contains(territory.name().toLowerCase(Locale.ROOT))
                        || emphasizedTerritories.contains(linked.name().toLowerCase(Locale.ROOT));
                if (!warConnectionVisible(scale, emphasized, resourceColors)) continue;
                float startX = previewX(territory.centerX(), fitted, offsetX, scale);
                float startY = previewY(territory.centerZ(), fitted, offsetY, scale);
                float endX = previewX(linked.centerX(), fitted, offsetX, scale);
                float endY = previewY(linked.centerZ(), fitted, offsetY, scale);
                if (resourceColors) {
                    canvas.strokeLine(startX, startY, endX, endY, 1.6f, color(BACKGROUND_BODY_OPAQUE));
                }
                canvas.strokeLine(startX, startY, endX, endY, emphasized ? 1.2f : resourceColors ? .75f : .55f,
                        emphasized ? color(MAP_SELECTED_TERRITORY) : foreground);
            }
        }
    }

    record WarMapLayout(
            float mapX, float mapY, float mapWidth, float mapHeight, float sidebarX, float sidebarWidth) {
        boolean containsMap(float x, float y) {
            return hit(x, y, mapX, mapY, mapWidth, mapHeight);
        }

        boolean containsSidebar(float x, float y) {
            return hit(x, y, sidebarX, mapY, sidebarWidth, mapHeight);
        }
    }

    record WarMapButtonBounds(float x, float y, float width, float height) {
        boolean visible() {
            return width > 0 && height > 0;
        }

        boolean contains(float pointX, float pointY) {
            return visible() && hit(pointX, pointY, x, y, width, height);
        }
    }

    record WarMapControls(WarMapButtonBounds fit, WarMapButtonBounds queues, WarMapButtonBounds players,
            WarMapButtonBounds lock, WarMapButtonBounds panel, WarMapButtonBounds coloring) {}
    static float headerHeight(float width) {
        return width >= 660 ? SequoiaUiStyle.HEADER_HEIGHT : 56;
    }

    static float contentTop(float width) {
        return headerHeight(width) + 48 + 8;
    }
}
