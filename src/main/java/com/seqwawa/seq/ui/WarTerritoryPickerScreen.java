package com.seqwawa.seq.ui;

import static com.seqwawa.seq.managers.ThemeManager.color;
import static com.seqwawa.seq.ui.theme.UiColor.*;

import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.config.Setting;
import com.seqwawa.seq.managers.WarPlannerManager;
import com.seqwawa.seq.map.GatheringMapImageService;
import com.seqwawa.seq.map.GuildTerritory;
import com.seqwawa.seq.map.GuildTerritoryIndex;
import com.seqwawa.seq.map.GuildTerritoryService;
import com.seqwawa.seq.map.MapBounds;
import com.seqwawa.seq.map.MapCalibration;
import com.seqwawa.seq.map.MapViewport;
import com.seqwawa.seq.model.war.WarPlannerDrafts;
import com.seqwawa.seq.model.war.WarPlannerDrafts.ZoneDraft;
import com.seqwawa.seq.model.war.WarPlannerSnapshot;
import com.seqwawa.seq.model.war.WarPlannerSnapshot.Team;
import com.seqwawa.seq.model.war.WarPlannerSnapshot.Zone;
import com.seqwawa.seq.model.war.WarZoneSelection;
import com.seqwawa.seq.utils.TextInputHelper;
import com.seqwawa.seq.utils.rendering.MinecraftUiRenderer;
import com.seqwawa.seq.utils.rendering.UiCanvas;
import com.seqwawa.seq.utils.rendering.UiImage;
import com.seqwawa.seq.utils.rendering.UiRenderer;
import com.seqwawa.seq.ui.widget.ColorWidget;
import java.awt.Color;
import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

/** Focused multi-territory editor; it reuses the established map calibration, image and territory index. */
public final class WarTerritoryPickerScreen extends Screen {
    private static final float SIDEBAR_WIDTH = 236;
    private static final float HEADER_HEIGHT = 34;
    private static final float PADDING = 10;
    private static final float FIELD_HEIGHT = 24;
    private static final float BUTTON_HEIGHT = 23;
    private static final float HEADER_RESOURCE_WIDTH = 100;
    private static final float HEADER_LOCK_WIDTH = 110;
    private static final double MIN_ZOOM = 0.06;
    private static final double MAX_ZOOM = 1.8;

    private final Screen parent;
    private final WarPlannerManager manager = SeqClient.getWarPlannerManager();
    private final Zone original;
    private final boolean readOnly;
    private final boolean overview;
    private final GuildTerritoryService territoryService = GuildTerritoryService.getInstance();
    private final GatheringMapImageService mapImageService = GatheringMapImageService.getInstance();

    private GuildTerritoryIndex territoryIndex;
    private WarZoneSelection selection;
    private String zoneName;
    private final Setting.ColorSetting zoneColorSetting;
    private final ColorWidget zoneColorWidget;
    private final Set<Long> assignedTeamIds = new HashSet<>();
    private Focus focus = Focus.NONE;
    private String message;
    private boolean saving;
    private int teamScrollRows;
    private int overviewScrollRows;

    private float nvgMouseX;
    private float nvgMouseY;
    private double centerX = (MapCalibration.MIN_WORLD_X + MapCalibration.MAX_WORLD_X) / 2;
    private double centerZ = (MapCalibration.MIN_WORLD_Z + MapCalibration.MAX_WORLD_Z) / 2;
    private double pixelsPerBlock = 0.12;
    private boolean fitted;
    private boolean draggingMap;
    private boolean dragMoved;
    private GuildTerritory hoveredTerritory;
    private UiImage mapImage;
    private long loadedImageVersion = -1;
    public WarTerritoryPickerScreen(Screen parent, Zone original) {
        this(parent, original, false, false);
    }

    public WarTerritoryPickerScreen(Screen parent, Zone original, boolean readOnly) {
        this(parent, original, readOnly, false);
    }

    public static WarTerritoryPickerScreen overview(Screen parent) {
        return new WarTerritoryPickerScreen(parent, null, true, true);
    }

    private WarTerritoryPickerScreen(Screen parent, Zone original, boolean readOnly, boolean overview) {
        super(Component.literal("War territory zone"));
        this.parent = parent;
        this.original = original;
        this.readOnly = readOnly;
        this.overview = overview;
        this.zoneName = overview ? "All zones" : original == null ? "New zone" : original.name();
        Color initialColor = parseColor(original == null ? "#55B8C5" : original.color(), new Color(0x55B8C5));
        this.zoneColorSetting = new Setting.ColorSetting("zone_color", "war_planner", initialColor.getRGB());
        this.zoneColorSetting.setPresentation("Zone color", null, null);
        this.zoneColorWidget = new ColorWidget(zoneColorSetting, null);
        if (original != null) assignedTeamIds.addAll(original.assignedTeamIds());
        this.selection = WarZoneSelection.of(overview
                ? overviewTerritoryNames(manager == null ? null : manager.snapshot())
                : original == null ? List.of() : original.territories());
        territoryService.loadBundledTerritories();
        territoryIndex = territoryService.index();
        mapImageService.requestLoad();
    }

    @Override
    public void tick() {
        if (manager == null || !manager.isAuthorized() || (!readOnly && !manager.canManage())) {
            SeqClient.mc.setScreen(parent);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        nvgMouseX = MinecraftUiRenderer.mouseX(mouseX);
        nvgMouseY = MinecraftUiRenderer.mouseY(mouseY);
        UiRenderer.renderScreen(this, this::renderPicker);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (WarPlannerScreen.shouldBlurBackground(WarPlannerScreen.backgroundOpacityPercent())) {
            super.renderBackground(graphics, mouseX, mouseY, partialTick);
        }
    }

    private void renderPicker(UiCanvas canvas) {
        float width = canvas.metrics().width();
        float height = canvas.metrics().height();
        if (!fitted) {
            InitialViewport initial = initialViewport(
                    territoryIndex,
                    selection.names(),
                    overview || readOnly && original != null,
                    Math.max(1, width - SIDEBAR_WIDTH),
                    Math.max(1, height - HEADER_HEIGHT));
            centerX = initial.centerX();
            centerZ = initial.centerZ();
            pixelsPerBlock = initial.pixelsPerBlock();
            fitted = true;
        }
        canvas.fillRect(0, 0, width, height, WarPlannerScreen.plannerBackground(color(BACKGROUND_BODY_OPAQUE)));
        renderMap(canvas, viewport(width, height));
        renderSidebar(canvas, height);
        canvas.fillRect(
                SIDEBAR_WIDTH,
                0,
                width - SIDEBAR_WIDTH,
                HEADER_HEIGHT,
                WarPlannerScreen.plannerBackground(color(MAP_HEADER)));
        HeaderControls controls = headerControls(width, manager.canManage());
        if (controls.resourceX() > SIDEBAR_WIDTH + 150) {
            text(
                    canvas,
                    overview
                            ? "All territory zones"
                            : original == null
                            ? "Create territory zone"
                            : readOnly ? "View territory zone" : "Edit territory zone",
                    SIDEBAR_WIDTH + 12,
                    HEADER_HEIGHT / 2,
                    15,
                    color(MAP_TITLE),
                    false);
        }
        button(
                canvas,
                controls.resourceX(),
                5,
                controls.resourceWidth(),
                BUTTON_HEIGHT,
                controls.resourceWidth() < HEADER_RESOURCE_WIDTH
                        ? WarPlannerScreen.resourceColorsEnabled() ? "Res ✓" : "Res"
                        : WarPlannerScreen.resourceColorsEnabled() ? "Resources ✓" : "Resources",
                false,
                false);
        if (manager.canManage()) {
            button(
                    canvas,
                    controls.lockX(),
                    5,
                    controls.lockWidth(),
                    BUTTON_HEIGHT,
                    controls.lockWidth() < HEADER_LOCK_WIDTH
                            ? WarPlannerScreen.territoriesLocked() ? "Unlock" : "Lock"
                            : WarPlannerScreen.territoriesLocked() ? "Unlock territories" : "Lock territories",
                    false,
                    false);
        }
    }

    private void renderMap(UiCanvas canvas, MapViewport viewport) {
        canvas.fillRect(viewport.screenX(), viewport.screenY(), viewport.screenWidth(), viewport.screenHeight(),
                WarPlannerScreen.plannerBackground(color(BACKGROUND_BODY)));
        UiImage image = mapImage();
        canvas.scissor(viewport.screenX(), viewport.screenY(), viewport.screenWidth(), viewport.screenHeight());
        if (image != null) {
            float x = viewport.worldToScreenX(MapCalibration.MIN_WORLD_X);
            float y = viewport.worldToScreenZ(MapCalibration.MIN_WORLD_Z);
            float w = viewport.worldToScreenX(MapCalibration.MAX_WORLD_X) - x;
            float h = viewport.worldToScreenZ(MapCalibration.MAX_WORLD_Z) - y;
            canvas.drawImage(image, x, y, w, h, WarPlannerScreen.backgroundOpacityPercent() / 100f);
        }
        canvas.fillRect(viewport.screenX(), viewport.screenY(), viewport.screenWidth(), viewport.screenHeight(),
                WarPlannerScreen.plannerBackground(color(MAP_TINT)));

        Set<String> allowed = allowedTerritories();
        hoveredTerritory = !draggingMap && viewport.isInsideScreen(nvgMouseX, nvgMouseY)
                ? territoryIndex.territoryAt(viewport.screenToWorldX(nvgMouseX), viewport.screenToWorldZ(nvgMouseY))
                : null;
        if (hoveredTerritory != null && !allowed.contains(hoveredTerritory.name())) {
            hoveredTerritory = null;
        }
        MapBounds visible = viewport.visibleBounds();
        Color selectedColor = new Color(zoneColorSetting.getValue());
        Map<String, WarPlannerSnapshot.TerritoryDetails> details = territoryDetails();
        WarPlannerSnapshot snapshot = manager.snapshot();
        Map<String, Zone> zonesByTerritory = overview ? zonesByTerritory(snapshot) : Map.of();
        for (GuildTerritory territory : territoryIndex.territories()) {
            if (!allowed.contains(territory.name()) || !intersects(visible, territory.bounds())) continue;
            MapBounds bounds = territory.bounds();
            float x = viewport.worldToScreenX(bounds.minX());
            float y = viewport.worldToScreenZ(bounds.minZ());
            float w = viewport.worldToScreenX(bounds.maxX()) - x;
            float h = viewport.worldToScreenZ(bounds.maxZ()) - y;
            boolean selected = selection.contains(territory.name());
            boolean hovered = territory.equals(hoveredTerritory);
            if (WarPlannerScreen.resourceColorsEnabled()) {
                renderResourceFill(canvas, x, y, w, h, details.get(territory.name()));
            }
            if (hovered) {
                canvas.fillRect(x, y, w, h, alpha(color(MAP_TERRITORY), 42));
            }
            Zone overviewZone = zonesByTerritory.get(territory.name().toLowerCase(Locale.ROOT));
            if (overviewZone != null) {
                Color zoneColor = parseColor(overviewZone.color(), color(ACCENT_PRIMARY));
                canvas.strokeRect(x - 1, y - 1, w + 2, h + 2, 2.2f, alpha(zoneColor, 255));
            } else if (selected) {
                canvas.strokeRect(x - 1, y - 1, w + 2, h + 2, 2.2f, alpha(selectedColor, 255));
            } else {
                canvas.strokeRect(x, y, w, h, hovered ? 1.7f : .7f,
                        alpha(color(MAP_TERRITORY), hovered ? 245 : 100));
            }
        }
        drawConnections(canvas, viewport, details, allowed);
        canvas.resetScissor();
        if (hoveredTerritory != null) {
            WarPlannerSnapshot.TerritoryDetails detail = details.get(hoveredTerritory.name());
            Zone hoveredZone = zonesByTerritory.get(hoveredTerritory.name().toLowerCase(Locale.ROOT));
            String resourceText = detail == null || detail.resources().isEmpty()
                    ? "Base emerald income" : String.join(" · ", detail.resources());
            float tooltipWidth = Math.min(260, Math.max(150, 14 + resourceText.length() * 6));
            float tooltipHeight = hoveredZone == null ? 36 : 49;
            canvas.fillRoundedRect(
                    nvgMouseX + 10,
                    nvgMouseY + 10,
                    tooltipWidth,
                    tooltipHeight,
                    4,
                    WarPlannerScreen.plannerBackground(color(BACKGROUND_POPUP)));
            text(canvas, hoveredTerritory.name(), nvgMouseX + 17, nvgMouseY + 21, 10, color(TEXT_PRIMARY), false);
            text(canvas, resourceText, nvgMouseX + 17, nvgMouseY + 36, 9, color(TEXT_MUTED), false);
            if (hoveredZone != null) {
                text(canvas, hoveredZone.name(), nvgMouseX + 17, nvgMouseY + 48, 9,
                        parseColor(hoveredZone.color(), color(ACCENT_PRIMARY)), false);
            }
        }
    }

    private void renderSidebar(UiCanvas canvas, float height) {
        canvas.fillRect(0, 0, SIDEBAR_WIDTH, height, WarPlannerScreen.plannerBackground(color(MAP_SIDEBAR)));
        if (overview) {
            renderOverviewSidebar(canvas, height);
            return;
        }
        text(canvas, "Territory zone", PADDING, 20, 17, color(ACCENT_PRIMARY), false);
        float y = 46;
        label(canvas, "Name", y);
        field(canvas, zoneName, y + 14, focus == Focus.NAME);
        SidebarLayout layout = sidebarLayout(height);
        if (readOnly) {
            label(canvas, "Zone color", layout.colorY() + 2);
            field(canvas, zoneColorSetting.getHexValue(), layout.colorY() + 16, false);
            canvas.fillRect(
                    SIDEBAR_WIDTH - 38,
                    layout.colorY() + 21,
                    18,
                    14,
                    new Color(zoneColorSetting.getValue()));
        } else {
            positionColorWidget(layout.colorY());
            zoneColorWidget.render(canvas, nvgMouseX, nvgMouseY);
        }
        y = layout.teamsY();
        label(canvas, "Assigned teams · scroll", y);
        WarPlannerSnapshot snapshot = manager.snapshot();
        int visibleTeamRows = layout.visibleTeamRows();
        if (snapshot != null) {
            int shown = 0;
            int start = Math.min(teamScrollRows, Math.max(0, snapshot.teams().size() - 1));
            for (int index = start; index < snapshot.teams().size() && shown < visibleTeamRows; index++) {
                Team team = snapshot.teams().get(index);
                float teamY = y + 14 + shown * 24;
                button(canvas, PADDING, teamY, SIDEBAR_WIDTH - PADDING * 2, 21,
                        (assignedTeamIds.contains(team.id()) ? "✓ " : "+ ") + team.name(), false, saving || readOnly);
                shown++;
            }
            if (snapshot.teams().size() > visibleTeamRows) {
                text(canvas, (start + 1) + "–" + Math.min(start + visibleTeamRows, snapshot.teams().size()) + " of "
                                + snapshot.teams().size(),
                        PADDING, y + 18 + visibleTeamRows * 24, 8, color(MAP_SUBTEXT), false);
            }
        }
        y = layout.selectionY();
        label(canvas, "Selection", y);
        text(canvas, selection.names().size() + " territories", PADDING, y + 22, 13, color(TEXT_PRIMARY), false);
        long unknown = selection.names().stream().filter(name -> territoryIndex.territory(name) == null).count();
        if (unknown > 0) {
            text(canvas, unknown + " saved names are not in this map version", PADDING, y + 39, 9,
                    color(CONTROL_WARNING), false);
        }
        if (!readOnly) {
            button(canvas, PADDING, y + 45, 68, BUTTON_HEIGHT, "Clear", true, saving);
        }

        if (message != null && !message.isBlank()) {
            text(canvas, truncate(message, 34), PADDING, height - 74, 10, color(CONTROL_WARNING), false);
        }
        button(canvas, PADDING, height - 38, 78, BUTTON_HEIGHT, readOnly ? "Back" : "Cancel", false, false);
        if (!readOnly) {
            button(canvas, SIDEBAR_WIDTH - PADDING - 92, height - 38, 92, BUTTON_HEIGHT,
                    saving ? "Saving…" : "Save zone", false, saving);
        }
    }

    private void renderOverviewSidebar(UiCanvas canvas, float height) {
        text(canvas, "Zone overview", PADDING, 20, 17, color(ACCENT_PRIMARY), false);
        text(canvas, "All territory groups on one map", PADDING, 42, 9, color(MAP_SUBTEXT), false);
        WarPlannerSnapshot snapshot = manager.snapshot();
        List<Zone> zones = snapshot == null ? List.of() : snapshot.zones();
        int visibleRows = overviewVisibleRows(height);
        int start = Math.min(overviewScrollRows, Math.max(0, zones.size() - 1));
        float y = 66;
        for (int index = start; index < zones.size() && index < start + visibleRows; index++, y += 30) {
            Zone zone = zones.get(index);
            Color zoneColor = parseColor(zone.color(), color(ACCENT_PRIMARY));
            canvas.fillRoundedRect(PADDING, y, SIDEBAR_WIDTH - PADDING * 2, 24, 3,
                    WarPlannerScreen.plannerBackground(color(BACKGROUND_CONTENT)));
            canvas.fillRect(PADDING + 5, y + 4, 5, 16, zoneColor);
            text(canvas, truncate(zone.name(), 22), PADDING + 17, y + 9, 11, color(TEXT_PRIMARY), false);
            text(canvas, zone.territories().size() + " terrs", PADDING + 17, y + 19, 8, color(MAP_SUBTEXT), false);
        }
        if (zones.size() > visibleRows) {
            text(canvas, (start + 1) + "–" + Math.min(start + visibleRows, zones.size()) + " of " + zones.size()
                            + " · scroll",
                    PADDING, height - 58, 8, color(MAP_SUBTEXT), false);
        }
        button(canvas, PADDING, height - 38, 78, BUTTON_HEIGHT, "Back", false, false);
    }

    @Override
    public boolean mouseClicked(@NotNull MouseButtonEvent click, boolean outsideScreen) {
        float mx = MinecraftUiRenderer.mouseX(click.x());
        float my = MinecraftUiRenderer.mouseY(click.y());
        float width = MinecraftUiRenderer.screenWidth();
        float height = MinecraftUiRenderer.screenHeight();
        HeaderControls headerControls = headerControls(width, manager.canManage());
        if (click.button() == 0
                && hit(mx, my, headerControls.resourceX(), 5, headerControls.resourceWidth(), BUTTON_HEIGHT)) {
            SeqClient.getWarPlannerResourceColorsSetting()
                    .setValue(!WarPlannerScreen.resourceColorsEnabled());
            SeqClient.getConfigManager().save();
            return true;
        }
        if (click.button() == 0
                && manager.canManage()
                && hit(mx, my, headerControls.lockX(), 5, headerControls.lockWidth(), BUTTON_HEIGHT)) {
            SeqClient.getWarPlannerLockTerritoriesSetting()
                    .setValue(!WarPlannerScreen.territoriesLocked());
            SeqClient.getConfigManager().save();
            return true;
        }
        if (mx < SIDEBAR_WIDTH) {
            SidebarLayout layout = sidebarLayout(height);
            if (!readOnly) {
                positionColorWidget(layout.colorY());
                if (zoneColorWidget.mouseClicked(mx, my, click.button())) {
                    focus = Focus.NONE;
                    return true;
                }
            }
            if (click.button() != 0) return true;
            if (hit(mx, my, PADDING, height - 38, 78, BUTTON_HEIGHT)) {
                onClose();
                return true;
            }
            if (readOnly) return true;
            if (hit(mx, my, PADDING, 60, SIDEBAR_WIDTH - PADDING * 2, FIELD_HEIGHT)) {
                focus = Focus.NAME;
                return true;
            }
            WarPlannerSnapshot snapshot = manager.snapshot();
            if (snapshot != null) {
                int start = Math.min(teamScrollRows, Math.max(0, snapshot.teams().size() - 1));
                int visibleTeamRows = layout.visibleTeamRows();
                for (int shown = 0; shown < visibleTeamRows && start + shown < snapshot.teams().size(); shown++) {
                    Team team = snapshot.teams().get(start + shown);
                    if (hit(
                            mx,
                            my,
                            PADDING,
                            layout.teamsY() + 14 + shown * 24,
                            SIDEBAR_WIDTH - PADDING * 2,
                            21)) {
                        if (!assignedTeamIds.add(team.id())) assignedTeamIds.remove(team.id());
                        return true;
                    }
                }
            }
            if (hit(mx, my, PADDING, layout.selectionY() + 45, 68, BUTTON_HEIGHT)) {
                selection = new WarZoneSelection(Set.of());
                return true;
            }
            if (hit(mx, my, SIDEBAR_WIDTH - PADDING - 92, height - 38, 92, BUTTON_HEIGHT)) {
                save();
                return true;
            }
            focus = Focus.NONE;
            return true;
        }
        if (click.button() != 0) return super.mouseClicked(click, outsideScreen);
        if (viewport(width, height).isInsideScreen(mx, my)) {
            focus = Focus.NONE;
            draggingMap = true;
            dragMoved = false;
            return true;
        }
        return super.mouseClicked(click, outsideScreen);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent click, double deltaX, double deltaY) {
        if (!readOnly) {
            positionColorWidget(sidebarLayout(MinecraftUiRenderer.screenHeight()).colorY());
            if (zoneColorWidget.mouseDragged(
                    MinecraftUiRenderer.mouseX(click.x()), MinecraftUiRenderer.mouseY(click.y()))) {
                return true;
            }
        }
        if (draggingMap && click.button() == 0) {
            double dx = MinecraftUiRenderer.mouseDelta(deltaX);
            double dy = MinecraftUiRenderer.mouseDelta(deltaY);
            if (Math.abs(dx) + Math.abs(dy) > .5) dragMoved = true;
            centerX -= dx / pixelsPerBlock;
            centerZ -= dy / pixelsPerBlock;
            return true;
        }
        return super.mouseDragged(click, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(@NotNull MouseButtonEvent click) {
        if (!readOnly) {
            positionColorWidget(sidebarLayout(MinecraftUiRenderer.screenHeight()).colorY());
            if (zoneColorWidget.mouseReleased(
                    MinecraftUiRenderer.mouseX(click.x()), MinecraftUiRenderer.mouseY(click.y()), click.button())) {
                return true;
            }
        }
        if (click.button() == 0 && draggingMap) {
            draggingMap = false;
            float mx = MinecraftUiRenderer.mouseX(click.x());
            float my = MinecraftUiRenderer.mouseY(click.y());
            MapViewport viewport = viewport(MinecraftUiRenderer.screenWidth(), MinecraftUiRenderer.screenHeight());
            if (!readOnly && !dragMoved && viewport.isInsideScreen(mx, my)) {
                GuildTerritory territory = territoryIndex.territoryAt(
                        viewport.screenToWorldX(mx), viewport.screenToWorldZ(my));
                if (territory != null && allowedTerritories().contains(territory.name())) {
                    selection = selection.toggle(territory.name());
                }
            }
            return true;
        }
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        float mx = MinecraftUiRenderer.mouseX(mouseX);
        float my = MinecraftUiRenderer.mouseY(mouseY);
        if (overview && mx < SIDEBAR_WIDTH) {
            WarPlannerSnapshot snapshot = manager.snapshot();
            int size = snapshot == null ? 0 : snapshot.zones().size();
            int visibleRows = overviewVisibleRows(MinecraftUiRenderer.screenHeight());
            int delta = scrollY > 0 ? -1 : 1;
            overviewScrollRows = Math.max(0, Math.min(overviewScrollRows + delta, Math.max(0, size - visibleRows)));
            return true;
        }
        SidebarLayout layout = sidebarLayout(MinecraftUiRenderer.screenHeight());
        int visibleTeamRows = layout.visibleTeamRows();
        if (mx < SIDEBAR_WIDTH
                && my >= layout.teamsY()
                && my <= layout.teamsY() + 20 + visibleTeamRows * 24) {
            WarPlannerSnapshot snapshot = manager.snapshot();
            int size = snapshot == null ? 0 : snapshot.teams().size();
            int delta = scrollY > 0 ? -1 : 1;
            teamScrollRows = Math.max(0, Math.min(teamScrollRows + delta, Math.max(0, size - visibleTeamRows)));
            return true;
        }
        MapViewport before = viewport(MinecraftUiRenderer.screenWidth(), MinecraftUiRenderer.screenHeight());
        if (!before.isInsideScreen(mx, my)) return true;
        double worldX = before.screenToWorldX(mx);
        double worldZ = before.screenToWorldZ(my);
        double factor = scrollY > 0 ? 1.15 : 1 / 1.15;
        pixelsPerBlock = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, pixelsPerBlock * factor));
        MapViewport after = viewport(MinecraftUiRenderer.screenWidth(), MinecraftUiRenderer.screenHeight());
        centerX = worldX - (mx - (after.screenX() + after.screenWidth() / 2)) / pixelsPerBlock;
        centerZ = worldZ - (my - (after.screenY() + after.screenHeight() / 2)) / pixelsPerBlock;
        return true;
    }

    @Override
    public boolean keyPressed(@NotNull KeyEvent event) {
        if (!readOnly && zoneColorWidget.keyPressed(event)) {
            return true;
        }
        if (focus != Focus.NONE) {
            if (event.key() == GLFW.GLFW_KEY_ESCAPE || event.key() == GLFW.GLFW_KEY_ENTER
                    || event.key() == GLFW.GLFW_KEY_KP_ENTER) {
                focus = Focus.NONE;
                return true;
            }
            if (event.key() == GLFW.GLFW_KEY_BACKSPACE) {
                if (focus == Focus.NAME && !zoneName.isEmpty()) zoneName = zoneName.substring(0, zoneName.length() - 1);
                return true;
            }
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(@NotNull CharacterEvent event) {
        if (!readOnly && zoneColorWidget.charTyped(event)) {
            return true;
        }
        if (focus != Focus.NONE) {
            String typed = TextInputHelper.getTypedText(event);
            if (typed != null && typed.length() == 1 && !Character.isISOControl(typed.charAt(0))) {
                if (focus == Focus.NAME && zoneName.length() < 64) zoneName += typed;
            }
            return true;
        }
        return super.charTyped(event);
    }

    @Override
    public void onClose() {
        SeqClient.mc.setScreen(parent);
    }

    @Override
    public void removed() {
        UiRenderer.renderResource(canvas -> {
            if (mapImage != null) {
                UiRenderer.deleteImage(mapImage);
                mapImage = null;
            }
        });
        super.removed();
    }

    private void save() {
        if (saving) return;
        if (manager == null || !manager.isAuthorized() || !manager.canManage()) {
            message = "War planner management access is no longer available.";
            SeqClient.mc.setScreen(parent);
            return;
        }
        try {
            String normalizedColor = WarPlannerDrafts.normalizeColor(zoneColorSetting.getHexValue());
            ZoneDraft draft = new ZoneDraft(
                    zoneName,
                    normalizedColor,
                    List.copyOf(assignedTeamIds),
                    original == null ? null : original.version(),
                    selection.sortedNames().stream().filter(allowedTerritories()::contains).toList());
            saving = true;
            manager.saveZone(original == null ? null : original.id(), draft).whenComplete((result, error) ->
                    SeqClient.mc.execute(() -> {
                        saving = false;
                        if (error != null || result == null || !result.success()) {
                            message = error != null ? "War planner request failed." : result == null ? "No response." : result.message();
                            return;
                        }
                        SeqClient.mc.setScreen(parent);
                    }));
        } catch (IllegalArgumentException exception) {
            message = exception.getMessage();
        }
    }

    private Set<String> allowedTerritories() {
        WarPlannerSnapshot snapshot = manager.snapshot();
        if (snapshot == null) return Set.of();
        if (overview) {
            return WarPlannerScreen.visibleTerritoryNames(
                    snapshot,
                    new HashSet<>(snapshot.territories()),
                    manager.canManage() && WarPlannerScreen.territoriesLocked());
        }
        Set<String> visibleTerritories = new HashSet<>(WarPlannerScreen.visibleTerritoryNames(
                snapshot,
                new HashSet<>(snapshot.territories()),
                manager.canManage() && WarPlannerScreen.territoriesLocked()));
        visibleTerritories.addAll(selection.names());
        Set<String> allowed = new HashSet<>(snapshot.territories());
        allowed.retainAll(visibleTerritories);
        if (readOnly) return allowed;
        for (Zone zone : snapshot.zones()) {
            if (original == null || zone.id() != original.id()) {
                allowed.removeAll(zone.territories());
            }
        }
        return allowed;
    }

    static List<String> overviewTerritoryNames(WarPlannerSnapshot snapshot) {
        if (snapshot == null) return List.of();
        return snapshot.zones().stream().flatMap(zone -> zone.territories().stream()).distinct().toList();
    }

    static Map<String, Zone> zonesByTerritory(WarPlannerSnapshot snapshot) {
        if (snapshot == null) return Map.of();
        Map<String, Zone> result = new HashMap<>();
        for (Zone zone : snapshot.zones()) {
            for (String territory : zone.territories()) {
                result.put(territory.toLowerCase(Locale.ROOT), zone);
            }
        }
        return Map.copyOf(result);
    }

    private Map<String, WarPlannerSnapshot.TerritoryDetails> territoryDetails() {
        WarPlannerSnapshot snapshot = manager.snapshot();
        if (snapshot == null) return Map.of();
        Map<String, WarPlannerSnapshot.TerritoryDetails> result = new HashMap<>();
        snapshot.territoryDetails().forEach(detail -> result.put(detail.name(), detail));
        return result;
    }

    private static float centerScreenX(MapViewport viewport, GuildTerritory territory) {
        return viewport.worldToScreenX((territory.bounds().minX() + territory.bounds().maxX()) / 2);
    }

    private static float centerScreenZ(MapViewport viewport, GuildTerritory territory) {
        return viewport.worldToScreenZ((territory.bounds().minZ() + territory.bounds().maxZ()) / 2);
    }

    private void drawConnections(
            UiCanvas canvas,
            MapViewport viewport,
            Map<String, WarPlannerSnapshot.TerritoryDetails> details,
            Set<String> visibleTerritories) {
        Set<String> drawnConnections = new HashSet<>();
        for (GuildTerritory territory : territoryIndex.territories()) {
            if (!visibleTerritories.contains(territory.name())) continue;
            WarPlannerSnapshot.TerritoryDetails detail = details.get(territory.name());
            if (detail == null) continue;
            for (String linkedName : detail.connections()) {
                GuildTerritory linked = territoryIndex.territory(linkedName);
                if (linked == null || !visibleTerritories.contains(linked.name())) continue;
                String key = territory.name().compareToIgnoreCase(linkedName) < 0
                        ? territory.name() + "\n" + linkedName : linkedName + "\n" + territory.name();
                if (!drawnConnections.add(key)) continue;
                float startX = centerScreenX(viewport, territory);
                float startY = centerScreenZ(viewport, territory);
                float endX = centerScreenX(viewport, linked);
                float endY = centerScreenZ(viewport, linked);
                canvas.strokeLine(startX, startY, endX, endY, 2.2f, alpha(color(BACKGROUND_BODY_OPAQUE), 220));
                canvas.strokeLine(startX, startY, endX, endY, 1.05f, alpha(color(MAP_TERRITORY), 245));
            }
        }
    }

    static void renderResourceFill(
            UiCanvas canvas, float x, float y, float width, float height, WarPlannerSnapshot.TerritoryDetails detail) {
        if (detail == null || detail.resources().isEmpty()) return;
        List<Color> colors = resourceDisplayColors(detail.resources());
        float sliceWidth = width / colors.size();
        for (int index = 0; index < colors.size(); index++) {
            canvas.fillRect(x + index * sliceWidth, y, index == colors.size() - 1 ? width - index * sliceWidth : sliceWidth,
                    height, alpha(colors.get(index), 115));
        }
    }

    static List<Color> resourceDisplayColors(List<String> resources) {
        List<String> distinct = resources == null ? List.of() : resources.stream().distinct().toList();
        long materialCount = distinct.stream().filter(resource -> !"EMERALD".equals(resource)).count();
        if (materialCount >= 4) {
            return List.of(new Color(156, 85, 210));
        }
        return distinct.stream().map(WarTerritoryPickerScreen::resourceColor).toList();
    }

    static Color resourceColor(String resource) {
        return switch (resource == null ? "" : resource.toUpperCase(Locale.ROOT)) {
            case "EMERALD" -> new Color(52, 211, 99);
            case "ORE" -> new Color(176, 190, 197);
            case "WOOD" -> new Color(166, 113, 70);
            case "FISH" -> new Color(64, 196, 255);
            case "CROP" -> new Color(255, 213, 79);
            default -> new Color(144, 164, 174);
        };
    }

    private MapViewport viewport(float width, float height) {
        return new MapViewport(centerX, centerZ, pixelsPerBlock, SIDEBAR_WIDTH, HEADER_HEIGHT,
                Math.max(1, width - SIDEBAR_WIDTH), Math.max(1, height - HEADER_HEIGHT));
    }

    static HeaderControls headerControls(float width, boolean canManage) {
        boolean compact = width < 500;
        float resourceWidth = compact ? 70 : HEADER_RESOURCE_WIDTH;
        float lockWidth = compact ? 86 : HEADER_LOCK_WIDTH;
        float right = width - PADDING;
        float lockX = canManage ? right - lockWidth : right;
        float resourceRight = canManage ? lockX - 6 : right;
        return new HeaderControls(resourceRight - resourceWidth, resourceWidth, lockX, lockWidth);
    }

    static int visibleTeamRows(float height) {
        return visibleTeamRows(height, 42);
    }

    static int visibleTeamRows(float height, float colorWidgetHeight) {
        return Math.max(1, Math.min(4, (int) ((height - 318 - Math.max(0, colorWidgetHeight - 42)) / 24)));
    }

    static int overviewVisibleRows(float height) {
        return Math.max(1, (int) ((height - 130) / 30));
    }

    private SidebarLayout sidebarLayout(float height) {
        float colorY = 94;
        float colorHeight = readOnly ? 42 : zoneColorWidget.getHeight();
        float teamsY = colorY + colorHeight + 10;
        int teamRows = visibleTeamRows(height, colorHeight);
        return new SidebarLayout(colorY, teamsY, teamRows, teamsY + 20 + teamRows * 24);
    }

    private void positionColorWidget(float y) {
        zoneColorWidget.setPosition(2, y, SIDEBAR_WIDTH - 4, zoneColorWidget.getHeight());
    }

    static InitialViewport initialViewport(
            GuildTerritoryIndex territoryIndex,
            Set<String> selectedNames,
            boolean focusSelection,
            float viewportWidth,
            float viewportHeight) {
        List<GuildTerritory> selected = selectedNames == null
                ? List.of()
                : selectedNames.stream()
                        .map(territoryIndex::territory)
                        .filter(java.util.Objects::nonNull)
                        .toList();
        MapBounds bounds = focusSelection && !selected.isEmpty()
                ? WarPlannerScreen.zonePreviewBounds(selected)
                : MapCalibration.fullBounds();
        double centerX = (bounds.minX() + bounds.maxX()) / 2;
        double centerZ = (bounds.minZ() + bounds.maxZ()) / 2;
        double fitX = Math.max(1, viewportWidth) / Math.max(1, bounds.maxX() - bounds.minX());
        double fitZ = Math.max(1, viewportHeight) / Math.max(1, bounds.maxZ() - bounds.minZ());
        double pixelsPerBlock = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, Math.min(fitX, fitZ)));
        return new InitialViewport(centerX, centerZ, pixelsPerBlock);
    }

    private UiImage mapImage() {
        long version = mapImageService.version();
        if (mapImage != null && loadedImageVersion == version) return mapImage;
        if (mapImage != null) UiRenderer.deleteImage(mapImage);
        mapImage = null;
        loadedImageVersion = version;
        try {
            byte[] bytes = mapImageService.imageBytes();
            if (bytes.length > 0) mapImage = UiRenderer.createImage(ByteBuffer.wrap(bytes), true);
        } catch (RuntimeException exception) {
            SeqClient.LOGGER.warn("[WarPlanner] Could not load territory-picker map image.", exception);
        }
        return mapImage;
    }

    private void label(UiCanvas canvas, String value, float y) {
        text(canvas, value, PADDING, y, 10, color(MAP_SUBTEXT), false);
    }

    private void field(UiCanvas canvas, String value, float y, boolean focused) {
        canvas.fillRect(PADDING, y, SIDEBAR_WIDTH - PADDING * 2, FIELD_HEIGHT,
                color(focused ? CONTROL_INPUT_HOVER : CONTROL_INPUT));
        canvas.strokeRect(PADDING, y, SIDEBAR_WIDTH - PADDING * 2, FIELD_HEIGHT, 1, color(CONTROL_BORDER));
        text(canvas, value, PADDING + 7, y + FIELD_HEIGHT / 2, 11, color(TEXT_PRIMARY), false);
    }

    private void button(UiCanvas canvas, float x, float y, float width, float height, String label, boolean danger, boolean disabled) {
        boolean hovered = !disabled && hit(nvgMouseX, nvgMouseY, x, y, width, height);
        Color background = disabled ? color(ACCENT_DISABLED)
                : danger ? color(hovered ? CONTROL_DANGER_HOVER : CONTROL_DANGER)
                : color(hovered ? MAP_CONTROL_HOVER : MAP_CONTROL);
        canvas.fillRoundedRect(x, y, width, height, 4, background);
        text(canvas, truncate(label, 27), x + width / 2, y + height / 2, 10,
                color(disabled ? TEXT_DISABLED : TEXT_PRIMARY), true);
    }

    private static void text(UiCanvas canvas, String value, float x, float y, float size, Color textColor, boolean centered) {
        canvas.drawText(value == null ? "" : value, x, y, new UiCanvas.TextStyle(
                SeqClient.getFontManager().getSelectedFont(), size, textColor,
                centered ? UiCanvas.HorizontalAlign.CENTER : UiCanvas.HorizontalAlign.LEFT,
                UiCanvas.VerticalAlign.MIDDLE));
    }

    private static Color parseColor(String value, Color fallback) {
        try {
            return Color.decode(value == null ? "" : value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static Color alpha(Color source, int alpha) {
        return new Color(source.getRed(), source.getGreen(), source.getBlue(), alpha);
    }

    private static boolean intersects(MapBounds left, MapBounds right) {
        return left.maxX() >= right.minX() && left.minX() <= right.maxX()
                && left.maxZ() >= right.minZ() && left.minZ() <= right.maxZ();
    }

    private static boolean hit(float mx, float my, float x, float y, float width, float height) {
        return mx >= x && mx <= x + width && my >= y && my <= y + height;
    }

    private static String truncate(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, Math.max(0, max - 1)) + "…";
    }

    private enum Focus {
        NONE,
        NAME
    }

    record InitialViewport(double centerX, double centerZ, double pixelsPerBlock) {}

    record HeaderControls(float resourceX, float resourceWidth, float lockX, float lockWidth) {}

    private record SidebarLayout(float colorY, float teamsY, int visibleTeamRows, float selectionY) {}
}
