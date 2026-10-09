package com.seqwawa.seq.ui;

import static com.seqwawa.seq.ui.WarQueueMapOverlay.*;

import static com.seqwawa.seq.ui.WarMapSettings.*;

import static com.seqwawa.seq.ui.WarMapGeometry.*;

import static com.seqwawa.seq.managers.ThemeManager.color;
import static com.seqwawa.seq.ui.theme.UiColor.*;

import static com.seqwawa.seq.ui.WarPlannerDialogUi.*;
import static com.seqwawa.seq.ui.WarPingPicker.pingCandidates;

import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.managers.WarPlannerManager;
import com.seqwawa.seq.model.war.WarPlannerDrafts.ZonePlacementDraft;
import com.seqwawa.seq.model.war.WarPlannerSnapshot;
import com.seqwawa.seq.model.war.WarPlannerSnapshot.Zone;
import com.seqwawa.seq.model.war.WarPlannerSnapshot.ZoneCategory;
import com.seqwawa.seq.utils.rendering.UiCanvas;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/** Zone visibility, category folds, menus, deletion and drag placement. */
final class WarZoneSidebar {
    private static final float PADDING = 12;
    private static final float HEADER_HEIGHT = SequoiaUiStyle.HEADER_HEIGHT;

    private static final float WAR_MAP_ZONE_ROW_HEIGHT = 44;
    private static final float WAR_MAP_ZONE_ROW_STEP = 48;
    private static final float WAR_MAP_CATEGORY_ROW_HEIGHT = 26;
    private static final float WAR_MAP_CATEGORY_ROW_STEP = 30;
    private static final float WAR_MAP_SIDEBAR_CONTENT_TOP = 64;
    private static final float WAR_MAP_SIDEBAR_BOTTOM_PADDING = 6;
    static final int RESOURCE_FILL_ALPHA = 96;
    private PendingDelete pendingDeleteZone;
    private PendingDelete pendingDeleteZoneCategory;
    private final Set<Long> hiddenZoneIds = new java.util.HashSet<>();
    private final Set<Long> hiddenZoneCategoryIds = new java.util.HashSet<>();
    private final Set<Long> collapsedZoneCategoryIds = new java.util.HashSet<>();
    private ZoneDrag zoneDrag;
    private Long zoneActionsId;
    private WarMapButtonBounds zoneActionsBounds;
    private int scrollRows;

    private final WarPlannerManager manager;
    private final WarZonesTab.Actions actions;
    private final Runnable visibilityChanged;
    private float nvgMouseX, nvgMouseY;
    WarZoneSidebar(WarPlannerManager manager, WarZonesTab.Actions actions, Runnable visibilityChanged) {
        this(manager, actions, visibilityChanged, SeqClient.getConfigManager().hiddenWarPlannerZoneIds(),
                SeqClient.getConfigManager().hiddenWarPlannerZoneCategoryIds());
    }

    WarZoneSidebar(WarPlannerManager manager, WarZonesTab.Actions actions, Runnable visibilityChanged,
            Set<Long> hiddenZones, Set<Long> hiddenCategories) {
        this.manager = manager;
        this.actions = actions;
        this.visibilityChanged = visibilityChanged;
        hiddenZoneIds.addAll(hiddenZones);
        hiddenZoneCategoryIds.addAll(hiddenCategories);
    }

    List<Zone> visibleZones(WarPlannerSnapshot snapshot) { return visibleZones(snapshot.zones(), hiddenZoneIds, hiddenZoneCategoryIds); }
    boolean actionsOpen() { return zoneActionsId != null; }
    boolean closeActions() { boolean open = actionsOpen(); zoneActionsId = null; pendingDeleteZone = null; return open; }

    void clearActionMenu() { zoneActionsId = null; }
    void cancelDrag() { zoneDrag = null; }
    void resetInteractions() { zoneDrag = null; zoneActionsId = null; pendingDeleteZone = null; pendingDeleteZoneCategory = null; }
    void resetScroll() { scrollRows = 0; }
    void renderActions(UiCanvas canvas, float mx, float my) { nvgMouseX = mx; nvgMouseY = my; renderZoneActions(canvas); }
    boolean clickActions(float mx, float my) { return clickZoneActions(mx, my); }
    void scroll(WarPlannerSnapshot snapshot, WarMapLayout layout, int delta) {
        scrollRows = zoneSidebarScrollStart(scrollRows + delta, zoneSidebarEntries(snapshot, collapsedZoneCategoryIds),
                layout.mapHeight() - WAR_MAP_SIDEBAR_CONTENT_TOP - WAR_MAP_SIDEBAR_BOTTOM_PADDING);
    }
    boolean drag(float mx, float my) {
        if (zoneDrag != null) {

            if (!zoneDrag.active() && Math.hypot(mx - zoneDrag.startX(), my - zoneDrag.startY()) >= 4) {
                zoneDrag = new ZoneDrag(
                        zoneDrag.zoneId(), zoneDrag.zoneName(), zoneDrag.version(),
                        zoneDrag.startX(), zoneDrag.startY(), true);
            }
            return true;
        }

        return false;
    }
    boolean release(float mx, float my, float width, float height) {
        if (zoneDrag != null) {

            ZoneDrag completed = zoneDrag;
            zoneDrag = null;
            if (completed.active()) dropZone(completed, mx, my, width, height);
            return true;
        }

        return false;
    }

    void render(UiCanvas canvas, WarPlannerSnapshot snapshot, WarMapLayout layout, float mx, float my) {
        nvgMouseX = mx; nvgMouseY = my; zoneActionsBounds = null;
        float x = layout.sidebarX(), top = layout.mapY(), width = layout.sidebarWidth(), bottom = top + layout.mapHeight();
        canvas.fillRect(x, top, width, bottom - top, plannerBackground(color(BACKGROUND_CONTENT)));
        if (manager.canManage()) {
            float actionWidth = (width - 18) / 2;
            primaryButton(canvas, x + 6, top + 37, actionWidth, 20, "+ Category", actions.busy());
            primaryButton(canvas, x + 12 + actionWidth, top + 37, actionWidth, 20, "+ Zone", actions.busy());
        } else {
            text(canvas, "Click visibility controls", x + 10, top + 46, 8, color(TEXT_MUTED), false);
        }
        List<ZoneSidebarEntry> entries = zoneSidebarEntries(snapshot, collapsedZoneCategoryIds);
        if (entries.isEmpty()) {
            text(canvas, manager.canManage() ? "Create a zone to begin." : "No zones configured.",
                    x + 10, top + 50, 10, color(TEXT_MUTED), false);
            return;
        }
        float availableHeight = bottom
                - (top + WAR_MAP_SIDEBAR_CONTENT_TOP)
                - WAR_MAP_SIDEBAR_BOTTOM_PADDING;
        int start = zoneSidebarScrollStart(scrollRows, entries, availableHeight);
        float rowY = top + WAR_MAP_SIDEBAR_CONTENT_TOP;
        for (int index = start; index < entries.size(); index++) {
            ZoneSidebarEntry entry = entries.get(index);
            float rowHeight = entry.height();
            if (rowY + rowHeight > bottom - WAR_MAP_SIDEBAR_BOTTOM_PADDING && index > start) break;
            if (entry.categoryHeader()) {
                renderZoneCategoryRow(canvas, entry, x, rowY, width);
            } else {
                renderZoneRow(canvas, snapshot, entry.zone(), x, rowY, width);
            }
            rowY += entry.step();
        }
        if (zoneDrag != null && zoneDrag.active()) {
            canvas.fillRect(nvgMouseX + 8, nvgMouseY - 10, 100, 20, color(ACCENT_PRIMARY_DARK));
            text(canvas, truncate(zoneDrag.zoneName(), 15), nvgMouseX + 16, nvgMouseY,
                    10, color(TEXT_PRIMARY), false);
        }
    }

    private void renderZoneCategoryRow(
            UiCanvas canvas, ZoneSidebarEntry entry, float x, float rowY, float width) {
        boolean displayed = !hiddenZoneCategoryIds.contains(entry.categoryId());
        boolean collapsed = containsCategory(collapsedZoneCategoryIds, entry.categoryId());
        canvas.fillRect(x + 6, rowY, width - 12, WAR_MAP_CATEGORY_ROW_HEIGHT,
                color(CONTROL_INPUT));
        float controlsWidth = manager.canManage() && entry.category() != null ? 74 : 42;
        text(canvas, collapsed ? "▶" : "▼", x + 15, rowY + WAR_MAP_CATEGORY_ROW_HEIGHT / 2, 8,
                color(TEXT_SECONDARY), true);
        text(canvas, truncate(entry.label(), availableCharacters(x + 25, x + width - controlsWidth - 8, 10, 22)),
                x + 25, rowY + WAR_MAP_CATEGORY_ROW_HEIGHT / 2, 10,
                color(displayed ? TEXT_PRIMARY : TEXT_MUTED), false);
        if (manager.canManage() && entry.category() != null) {
            button(canvas, x + width - 70, rowY + 3, 38, 20, displayed ? "Hide" : "Show", false, false);
            boolean confirming = pendingDeleteZoneCategory != null
                    && pendingDeleteZoneCategory.id() == entry.category().id();
            destructiveButton(canvas, x + width - 28, rowY + 3, 22, 20, confirming ? "?" : "X", confirming,
                    actions.busy());
        } else {
            button(canvas, x + width - 48, rowY + 3, 42, 20, displayed ? "Hide" : "Show", false, false);
        }
    }

    private void renderZoneRow(
            UiCanvas canvas, WarPlannerSnapshot snapshot, Zone zone, float x, float rowY, float width) {
        boolean categoryDisplayed = !containsCategory(hiddenZoneCategoryIds, zone.categoryId());
        boolean displayed = categoryDisplayed && !hiddenZoneIds.contains(zone.id());
        Color zoneColor = parseColor(zone.color(), color(ACCENT_PRIMARY));
        canvas.fillRect(x + 6, rowY, width - 12, WAR_MAP_ZONE_ROW_HEIGHT,
                plannerBackground(color(BACKGROUND_CONTENT_FOCUSED)));
        canvas.fillRect(x + 10, rowY + 6, 3, WAR_MAP_ZONE_ROW_HEIGHT - 12,
                displayed ? zoneColor : color(TEXT_DISABLED));
        float actionX = x + width - (manager.canManage() ? 84 : 54);
        text(canvas, truncate(zone.name(), availableCharacters(x + 20, actionX - 4, 11, 32)),
                x + 20, rowY + 12, 11, color(displayed ? TEXT_PRIMARY : TEXT_MUTED), false);
        String assigned = zone.assignedTeamIds().stream().map(id -> teamName(snapshot, id))
                .reduce((left, right) -> left + " + " + right).orElse("No teams");
        String detail = zone.territories().size() + " territories · " + assigned;
        text(canvas, truncate(detail, availableCharacters(x + 20, x + width - 12, 9, 45)),
                x + 20, rowY + 32, 9, color(TEXT_MUTED), false);
        button(canvas, actionX, rowY + 3, 44, 20, displayed ? "Hide" : "Show", false, !categoryDisplayed);
        if (manager.canManage()) {
            button(canvas, x + width - 36, rowY + 3, 28, 20, "...", false, actions.busy());
            if (java.util.Objects.equals(zoneActionsId, zone.id())) {
                float menuY = Math.min(rowY + 25, canvas.metrics().height() - 60);
                zoneActionsBounds = new WarMapButtonBounds(x + width - 120, menuY, 112, 52);
            }
        }
    }

    private void renderZoneActions(UiCanvas canvas) {
        if (zoneActionsId == null || zoneActionsBounds == null || !manager.canManage()) return;
        var menu = zoneActionsBounds;
        canvas.fillRect(menu.x(), menu.y(), menu.width(), menu.height(), color(BACKGROUND_BODY_OPAQUE));
        button(canvas, menu.x() + 2, menu.y() + 2, menu.width() - 4, 22, "Edit zone", false, actions.busy());
        boolean confirming = pendingDeleteZone != null && pendingDeleteZone.id() == zoneActionsId;
        destructiveButton(canvas, menu.x() + 2, menu.y() + 28, menu.width() - 4, 22,
                confirming ? "Confirm delete" : "Delete zone", confirming, actions.busy());
        canvas.strokeRect(menu.x(), menu.y(), menu.width(), menu.height(), 1, color(ACCENT_DIVIDER));
    }

    private boolean clickZoneActions(float mx, float my) {
        if (zoneActionsId == null) return false;
        Zone zone = manager.snapshot() == null ? null : manager.snapshot().zones().stream()
                .filter(candidate -> candidate.id() == zoneActionsId).findFirst().orElse(null);
        var menu = zoneActionsBounds;
        if (!manager.canManage() || zone == null || menu == null || !menu.contains(mx, my)) {
            zoneActionsId = null;
            pendingDeleteZone = null;
            return true;
        }
        if (manager.isMutating()) return true;
        if (my < menu.y() + 26) {
            zoneActionsId = null;
            actions.openZone(zone, false);
        } else if (pendingDeleteZone != null && pendingDeleteZone.id() == zone.id()) {
            actions.result(manager.deleteZone(zone.id(), pendingDeleteZone.version()));
            pendingDeleteZone = null;
            zoneActionsId = null;
        } else {
            pendingDeleteZone = new PendingDelete(zone.id(), zone.version());
        }
        return true;
    }

    static List<Zone> visibleZones(List<Zone> zones, Set<Long> hiddenZoneIds) {
        return visibleZones(zones, hiddenZoneIds, Set.of());
    }

    static List<Zone> visibleZones(
            List<Zone> zones, Set<Long> hiddenZoneIds, Set<Long> hiddenZoneCategoryIds) {
        if (zones == null || zones.isEmpty()) return List.of();
        Set<Long> hiddenZones = hiddenZoneIds == null ? Set.of() : hiddenZoneIds;
        Set<Long> hiddenCategories = hiddenZoneCategoryIds == null ? Set.of() : hiddenZoneCategoryIds;
        return zones.stream()
                .filter(zone -> !hiddenZones.contains(zone.id()))
                .filter(zone -> !containsCategory(hiddenCategories, zone.categoryId()))
                .toList();
    }

    private static boolean containsCategory(Set<Long> categoryIds, Long categoryId) {
        if (categoryIds == null || categoryIds.isEmpty()) return false;
        return categoryId == null
                ? categoryIds.stream().anyMatch(java.util.Objects::isNull)
                : categoryIds.contains(categoryId);
    }

    static List<ZoneSidebarEntry> zoneSidebarEntries(WarPlannerSnapshot snapshot) {
        return zoneSidebarEntries(snapshot, Set.of());
    }

    static List<ZoneSidebarEntry> zoneSidebarEntries(
            WarPlannerSnapshot snapshot, Set<Long> collapsedCategoryIds) {
        if (snapshot == null) return List.of();
        Set<Long> collapsed = collapsedCategoryIds == null ? Set.of() : collapsedCategoryIds;
        ArrayList<ZoneSidebarEntry> entries = new ArrayList<>();
        List<ZoneCategory> categories = snapshot.zoneCategories().stream()
                .sorted(Comparator.comparingInt(ZoneCategory::position).thenComparingLong(ZoneCategory::id))
                .toList();
        for (ZoneCategory category : categories) {
            entries.add(ZoneSidebarEntry.category(category));
            if (!containsCategory(collapsed, category.id())) {
                snapshot.zones().stream()
                        .filter(zone -> java.util.Objects.equals(zone.categoryId(), category.id()))
                        .sorted(Comparator.comparingInt(Zone::position).thenComparingLong(Zone::id))
                        .map(zone -> ZoneSidebarEntry.zone(category.id(), zone))
                        .forEach(entries::add);
            }
        }
        List<Zone> uncategorized = snapshot.zones().stream()
                .filter(zone -> zone.categoryId() == null
                        || categories.stream().noneMatch(category -> category.id() == zone.categoryId()))
                .sorted(Comparator.comparingInt(Zone::position).thenComparingLong(Zone::id))
                .toList();
        if (!uncategorized.isEmpty()) {
            entries.add(ZoneSidebarEntry.uncategorized());
            if (!containsCategory(collapsed, null)) {
                uncategorized.stream().map(zone -> ZoneSidebarEntry.zone(null, zone)).forEach(entries::add);
            }
        }
        return List.copyOf(entries);
    }

    static int zoneSidebarScrollStart(int requested, List<ZoneSidebarEntry> entries, float availableHeight) {
        if (entries == null || entries.isEmpty()) return 0;
        int latestUsefulStart = entries.size() - 1;
        float tailHeight = entries.get(latestUsefulStart).height();
        for (int index = latestUsefulStart - 1; index >= 0; index--) {
            float candidateHeight = entries.get(index).step() + tailHeight;
            if (candidateHeight > availableHeight) break;
            tailHeight = candidateHeight;
            latestUsefulStart = index;
        }
        return Math.max(0, Math.min(requested, latestUsefulStart));
    }

    private static ZoneSidebarPlacement zoneSidebarPlacementAt(
            WarPlannerSnapshot snapshot,
            Set<Long> collapsedCategoryIds,
            int requestedScroll,
            float top,
            float bottom,
            float my) {
        List<ZoneSidebarEntry> entries = zoneSidebarEntries(snapshot, collapsedCategoryIds);
        float availableHeight = bottom
                - (top + WAR_MAP_SIDEBAR_CONTENT_TOP)
                - WAR_MAP_SIDEBAR_BOTTOM_PADDING;
        int start = zoneSidebarScrollStart(requestedScroll, entries, availableHeight);
        float rowY = top + WAR_MAP_SIDEBAR_CONTENT_TOP;
        for (int index = start; index < entries.size(); index++) {
            ZoneSidebarEntry entry = entries.get(index);
            if (rowY + entry.height() > bottom - WAR_MAP_SIDEBAR_BOTTOM_PADDING && index > start) break;
            if (my >= rowY && my <= rowY + entry.height()) {
                return new ZoneSidebarPlacement(entry, rowY, index);
            }
            rowY += entry.step();
        }
        return null;
    }

    boolean rightClickZoneName(
            WarPlannerSnapshot snapshot, float mx, float my, float width, float height) {
        float top = contentTop(width);
        WarMapLayout layout = warMapLayout(width, top, height - PADDING);
        top = layout.mapY();
        ZoneSidebarPlacement placement = zoneSidebarPlacementAt(
                snapshot, collapsedZoneCategoryIds, scrollRows, top, height - PADDING, my);
        if (placement == null) return false;
        ZoneSidebarEntry entry = placement.entry();
        if (entry.categoryHeader()) {
            if (!manager.canManage()
                    || entry.category() == null
                    || !hit(mx, my, layout.sidebarX() + 10, placement.y(), layout.sidebarWidth() - 84,
                            WAR_MAP_CATEGORY_ROW_HEIGHT)) {
                return false;
            }
            actions.openCategory(entry.category());
            return true;
        }
        if (!hit(mx, my, layout.sidebarX() + 18, placement.y() + 3, layout.sidebarWidth() - 30, 22)) return false;
        actions.openZone(entry.zone(), true);
        return true;
    }

    private void toggleZoneDisplay(long zoneId) {
        boolean hidden = !hiddenZoneIds.remove(zoneId);
        if (hidden) hiddenZoneIds.add(zoneId);
        visibilityChanged.run();
        SeqClient.getConfigManager().setWarPlannerZoneHidden(zoneId, hidden);
    }

    private void toggleZoneCategoryDisplay(Long categoryId) {
        boolean hidden = !hiddenZoneCategoryIds.remove(categoryId);
        if (hidden) hiddenZoneCategoryIds.add(categoryId);
        visibilityChanged.run();
        SeqClient.getConfigManager().setWarPlannerZoneCategoryHidden(categoryId, hidden);
    }

    private void toggleZoneCategoryFold(Long categoryId) {
        if (!collapsedZoneCategoryIds.remove(categoryId)) collapsedZoneCategoryIds.add(categoryId);
    }

    private void dropZone(ZoneDrag drag, float mx, float my, float width, float height) {
        WarPlannerSnapshot snapshot = manager.snapshot();
        if (snapshot == null || manager.isMutating()) return;
        WarMapLayout layout = warMapLayout(width, contentTop(width), height - PADDING);
        if (!layout.containsSidebar(mx, my)) return;
        ZoneSidebarPlacement placement = zoneSidebarPlacementAt(
                snapshot, collapsedZoneCategoryIds, scrollRows, layout.mapY(), height - PADDING, my);
        if (placement == null) return;
        ZoneDropTarget target = zoneDropTarget(snapshot, placement, my, drag.zoneId());
        if (target == null) return;
        actions.result(manager.moveZone(
                drag.zoneId(), new ZonePlacementDraft(target.categoryId(), target.position(), drag.version())));
    }

    static ZoneDropTarget zoneDropTarget(
            WarPlannerSnapshot snapshot, ZoneSidebarPlacement placement, float mouseY, long draggedZoneId) {
        if (snapshot == null || placement == null) return null;
        ZoneSidebarEntry entry = placement.entry();
        if (entry.categoryHeader()) return new ZoneDropTarget(entry.categoryId(), 0);
        if (entry.zone().id() == draggedZoneId) return null;
        List<Zone> targetZones = snapshot.zones().stream()
                .filter(zone -> zone.id() != draggedZoneId)
                .filter(zone -> java.util.Objects.equals(zone.categoryId(), entry.categoryId()))
                .sorted(Comparator.comparingInt(Zone::position).thenComparingLong(Zone::id))
                .toList();
        int targetIndex = targetZones.indexOf(entry.zone());
        if (targetIndex < 0) return null;
        int position = mouseY < placement.y() + placement.entry().height() / 2
                ? targetIndex
                : targetIndex + 1;
        return new ZoneDropTarget(entry.categoryId(), position);
    }

    record ZoneSidebarEntry(Long categoryId, ZoneCategory category, Zone zone, String label) {
        static ZoneSidebarEntry category(ZoneCategory category) {
            return new ZoneSidebarEntry(category.id(), category, null, category.name());
        }

        static ZoneSidebarEntry uncategorized() {
            return new ZoneSidebarEntry(null, null, null, "Uncategorized");
        }

        static ZoneSidebarEntry zone(Long categoryId, Zone zone) {
            return new ZoneSidebarEntry(categoryId, null, zone, zone.name());
        }

        boolean categoryHeader() {
            return zone == null;
        }

        float height() {
            return categoryHeader() ? WAR_MAP_CATEGORY_ROW_HEIGHT : WAR_MAP_ZONE_ROW_HEIGHT;
        }

        float step() {
            return categoryHeader() ? WAR_MAP_CATEGORY_ROW_STEP : WAR_MAP_ZONE_ROW_STEP;
        }
    }

    record ZoneSidebarPlacement(ZoneSidebarEntry entry, float y, int index) {}

    record ZoneDropTarget(Long categoryId, int position) {}

    private record ZoneDrag(long zoneId, String zoneName, Long version, float startX, float startY, boolean active) {}
    boolean click(WarPlannerSnapshot snapshot, float mx, float my, float width, float height) {
        WarMapLayout layout = warMapLayout(width, contentTop(width), height - PADDING);
        float top = layout.mapY();
        float sidebarWidth = layout.sidebarWidth();
        float sidebarX = layout.sidebarX();
        if (manager.canManage()) {
            float headerActionWidth = (sidebarWidth - 18) / 2;
            if (hit(mx, my, sidebarX + 6, top + 37, headerActionWidth, 20)) {
                actions.openCategory(null);
                return true;
            }
            if (hit(mx, my, sidebarX + 12 + headerActionWidth, top + 37, headerActionWidth, 20)) {
                actions.openZone(null, false);
                return true;
            }
        }
        ZoneSidebarPlacement placement = zoneSidebarPlacementAt(
                snapshot,
                collapsedZoneCategoryIds,
                scrollRows,
                top,
                height - PADDING,
                my);
        if (placement == null) return false;
        ZoneSidebarEntry entry = placement.entry();
        float rowY = placement.y();
        if (!hit(mx, my, sidebarX + 6, rowY, sidebarWidth - 12, entry.height())) return false;
        if (entry.categoryHeader()) {
            float toggleX = manager.canManage() && entry.category() != null
                    ? sidebarX + sidebarWidth - 70
                    : sidebarX + sidebarWidth - 48;
            float toggleWidth = manager.canManage() && entry.category() != null ? 38 : 42;
            if (hit(mx, my, toggleX, rowY + 3, toggleWidth, 20)) {
                toggleZoneCategoryDisplay(entry.categoryId());
                return true;
            }
            if (manager.canManage()
                    && entry.category() != null
                    && hit(mx, my, sidebarX + sidebarWidth - 28, rowY + 3, 22, 20)) {
                if (pendingDeleteZoneCategory != null
                        && pendingDeleteZoneCategory.id() == entry.category().id()) {
                    actions.result(manager.deleteZoneCategory(
                            entry.category().id(), pendingDeleteZoneCategory.version()));
                    pendingDeleteZoneCategory = null;
                } else {
                    pendingDeleteZoneCategory =
                            new PendingDelete(entry.category().id(), entry.category().version());
                }
                return true;
            }
            toggleZoneCategoryFold(entry.categoryId());
            return true;
        }
        Zone zone = entry.zone();
        float visibilityX = sidebarX + sidebarWidth - (manager.canManage() ? 84 : 54);
        if (hit(mx, my, visibilityX, rowY + 3, 44, 20)) {
            if (!containsCategory(hiddenZoneCategoryIds, zone.categoryId())) toggleZoneDisplay(zone.id());
            return true;
        }
        if (manager.canManage() && hit(mx, my, sidebarX + sidebarWidth - 36, rowY + 3, 28, 20)) {
            if (!manager.isMutating()) zoneActionsId = zone.id();
            return true;
        }
        if (manager.canManage() && !manager.isMutating() && hit(mx, my, sidebarX + 10, rowY + 2, sidebarWidth - 20, 40)) {
            zoneDrag = new ZoneDrag(zone.id(), zone.name(), zone.version(), mx, my, false);
            return true;
        }
        return true;
    }

    private void primaryButton(UiCanvas canvas, float x, float y, float width, float height, String label, boolean disabled) {
        tonedButton(canvas, nvgMouseX, nvgMouseY, x, y, width, height, label, ButtonTone.PRIMARY, disabled);
    }

    private void destructiveButton(UiCanvas canvas, float x, float y, float width, float height,
            String label, boolean confirming, boolean disabled) {
        tonedButton(canvas, nvgMouseX, nvgMouseY, x, y, width, height, label, confirming ? ButtonTone.DANGER : ButtonTone.QUIET_DANGER, disabled);
    }

    private void button(UiCanvas canvas, float x, float y, float width, float height, String label, boolean danger, boolean disabled) {
        tonedButton(canvas, nvgMouseX, nvgMouseY, x, y, width, height, label, danger ? ButtonTone.DANGER : ButtonTone.SECONDARY, disabled);
    }

    private static boolean hit(float mx, float my, float x, float y, float width, float height) {
        return mx >= x && mx <= x + width && my >= y && my <= y + height;
    }

    private record PendingDelete(long id, Long version) {}
}
