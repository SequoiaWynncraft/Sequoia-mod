package com.seqwawa.seq.ui;

import static com.seqwawa.seq.managers.ThemeManager.color;
import static com.seqwawa.seq.ui.PickerControlLayout.HEADER_HEIGHT;
import static com.seqwawa.seq.ui.PickerControlLayout.HEADER_LOCK_WIDTH;
import static com.seqwawa.seq.ui.PickerControlLayout.HEADER_RESOURCE_WIDTH;
import static com.seqwawa.seq.ui.PickerControlLayout.PADDING;
import static com.seqwawa.seq.ui.PickerControlLayout.SIDEBAR_WIDTH;
import static com.seqwawa.seq.ui.WarTerritoryPickerPolicy.ControlKind.CANCEL;
import static com.seqwawa.seq.ui.WarTerritoryPickerPolicy.ControlKind.CLEAR;
import static com.seqwawa.seq.ui.WarTerritoryPickerPolicy.ControlKind.LOCK_MAIN_MAP;
import static com.seqwawa.seq.ui.WarTerritoryPickerPolicy.ControlKind.NAME;
import static com.seqwawa.seq.ui.WarTerritoryPickerPolicy.ControlKind.RESOURCE_COLORS;
import static com.seqwawa.seq.ui.WarTerritoryPickerPolicy.ControlKind.SAVE;
import static com.seqwawa.seq.ui.WarTerritoryPickerPolicy.ControlKind.TEAM;
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
import com.seqwawa.seq.map.WorldMapBackgroundRenderer;
import com.seqwawa.seq.model.war.WarPlannerDrafts;
import com.seqwawa.seq.model.war.WarPlannerDrafts.ZoneDraft;
import com.seqwawa.seq.model.war.WarPlannerSnapshot;
import com.seqwawa.seq.model.war.WarPlannerSnapshot.Team;
import com.seqwawa.seq.model.war.WarPlannerSnapshot.Zone;
import com.seqwawa.seq.model.war.WarZoneSelection;
import com.seqwawa.seq.ui.PickerControlLayout.Bounds;
import com.seqwawa.seq.ui.WarTerritoryPickerPolicy.ControlKind;
import com.seqwawa.seq.ui.WarTerritoryPickerPolicy.ControlTarget;
import com.seqwawa.seq.ui.WarTerritoryPickerPolicy.TerritoryAccess;
import com.seqwawa.seq.utils.TextInputHelper;
import com.seqwawa.seq.utils.rendering.MinecraftUiRenderer;
import com.seqwawa.seq.utils.rendering.UiCanvas;
import com.seqwawa.seq.utils.rendering.UiRenderer;
import com.seqwawa.seq.ui.widget.ColorWidget;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

/** Focused multi-territory editor over the same background and viewport as {@code /seq map}. */
public final class WarTerritoryPickerScreen extends Screen {
    private static final double MIN_ZOOM = WorldMapBackgroundRenderer.MIN_PIXELS_PER_BLOCK;
    private static final double MAX_ZOOM = WorldMapBackgroundRenderer.MAX_PIXELS_PER_BLOCK;

    private final Screen parent;
    private final WarPlannerManager manager = SeqClient.getWarPlannerManager();
    private final Zone original;
    private final GuildTerritoryService territoryService = GuildTerritoryService.getInstance();
    private final GatheringMapImageService mapImageService = GatheringMapImageService.getInstance();
    private final WorldMapBackgroundRenderer mapBackground = new WorldMapBackgroundRenderer(mapImageService);

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
    private ControlTarget keyboardTarget;
    private boolean replaceZoneNameOnType;

    private float nvgMouseX;
    private float nvgMouseY;
    private double centerX = (MapCalibration.MIN_WORLD_X + MapCalibration.MAX_WORLD_X) / 2;
    private double centerZ = (MapCalibration.MIN_WORLD_Z + MapCalibration.MAX_WORLD_Z) / 2;
    private double pixelsPerBlock = 0.12;
    private boolean fitted;
    private boolean draggingMap;
    private boolean dragMoved;
    private GuildTerritory hoveredTerritory;
    private WarPlannerSnapshot cachedTerritorySnapshot;
    private TerritoryAccess cachedTerritoryAccess = TerritoryAccess.empty();
    private Map<String, WarPlannerSnapshot.TerritoryDetails> cachedTerritoryDetails = Map.of();

    public WarTerritoryPickerScreen(Screen parent, Zone original) {
        this(parent, original, false);
    }

    WarTerritoryPickerScreen(Screen parent, Zone original, boolean focusName) {
        super(Component.literal("War map zone"));
        this.parent = parent;
        this.original = original;
        this.zoneName = original == null ? "New zone" : original.name();
        Color initialColor = parseColor(original == null ? "#55B8C5" : original.color(), new Color(0x55B8C5));
        this.zoneColorSetting = new Setting.ColorSetting("zone_color", "war_planner", initialColor.getRGB());
        this.zoneColorSetting.setPresentation("Zone color", null, null);
        this.zoneColorWidget = new ColorWidget(zoneColorSetting, null);
        if (original != null) assignedTeamIds.addAll(original.assignedTeamIds());
        this.selection = WarZoneSelection.of(original == null ? List.of() : original.territories());
        territoryService.loadBundledTerritories();
        territoryIndex = territoryService.index();
        mapImageService.requestLoad();
        if (focusName) {
            focus = Focus.NAME;
            keyboardTarget = ControlTarget.named(NAME);
            replaceZoneNameOnType = true;
        }
    }

    @Override
    public void tick() {
        if (manager == null || !manager.isAuthorized() || !manager.canManage()) {
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
        PickerControlLayout layout = controlLayout(width, height);
        if (!fitted) {
            InitialViewport initial = initialViewport(
                    territoryIndex,
                    selection.names(),
                    false,
                    layout.map().width(),
                    layout.map().height());
            centerX = initial.centerX();
            centerZ = initial.centerZ();
            pixelsPerBlock = initial.pixelsPerBlock();
            fitted = true;
        }
        renderMap(canvas, viewport(layout.map()));
        renderSidebar(canvas, layout);
        canvas.fillRect(
                layout.header().x(),
                layout.header().y(),
                layout.header().width(),
                layout.header().height(),
                WarPlannerScreen.plannerBackground(color(MAP_HEADER)));
        if (layout.headerTitleVisible()) {
            text(
                    canvas,
                    original == null
                            ? "Create war map zone"
                            : "Edit war map zone",
                    SIDEBAR_WIDTH + 12,
                    HEADER_HEIGHT / 2,
                    15,
                    color(MAP_TITLE),
                    false);
        }
        button(
                canvas,
                layout.resourceToggle(),
                layout.resourceToggle().width() < HEADER_RESOURCE_WIDTH
                        ? WarPlannerScreen.resourceColorsEnabled() ? "Res ✓" : "Res"
                        : WarPlannerScreen.resourceColorsEnabled() ? "Resources ✓" : "Resources",
                false,
                false,
                isKeyboardTarget(RESOURCE_COLORS));
        if (canManage()) {
            button(
                    canvas,
                    layout.lockToggle(),
                    layout.lockToggle().width() < HEADER_LOCK_WIDTH
                            ? WarPlannerScreen.territoriesLocked() ? "Map ✓" : "Lock map"
                            : WarPlannerScreen.territoriesLocked() ? "Main map locked ✓" : "Lock main map",
                    false,
                    false,
                    isKeyboardTarget(LOCK_MAIN_MAP));
        }
    }

    private void renderMap(UiCanvas canvas, MapViewport viewport) {
        mapBackground.render(canvas, viewport, WarPlannerScreen.backgroundOpacityPercent() / 100f);
        canvas.scissor(viewport.screenX(), viewport.screenY(), viewport.screenWidth(), viewport.screenHeight());

        TerritoryAccess access = territoryAccess();
        hoveredTerritory = !draggingMap && viewport.isInsideScreen(nvgMouseX, nvgMouseY)
                ? territoryIndex.territoryAt(viewport.screenToWorldX(nvgMouseX), viewport.screenToWorldZ(nvgMouseY))
                : null;
        if (hoveredTerritory != null && !access.isVisible(hoveredTerritory.name())) {
            hoveredTerritory = null;
        }
        MapBounds visible = viewport.visibleBounds();
        Color selectedColor = new Color(zoneColorSetting.getValue());
        Map<String, WarPlannerSnapshot.TerritoryDetails> details = territoryDetails();
        if (WarPlannerScreen.resourceColorsEnabled()) {
            for (GuildTerritory territory : territoryIndex.territories()) {
                if (!access.isVisible(territory.name()) || !intersects(visible, territory.bounds())) continue;
                MapBounds bounds = territory.bounds();
                float x = viewport.worldToScreenX(bounds.minX());
                float y = viewport.worldToScreenZ(bounds.minZ());
                float w = viewport.worldToScreenX(bounds.maxX()) - x;
                float h = viewport.worldToScreenZ(bounds.maxZ()) - y;
                renderResourceFill(canvas, x, y, w, h, details.get(territory.name()));
            }
        }
        drawConnections(canvas, viewport, details, access);
        for (GuildTerritory territory : territoryIndex.territories()) {
            if (!access.isVisible(territory.name()) || !intersects(visible, territory.bounds())) continue;
            MapBounds bounds = territory.bounds();
            float x = viewport.worldToScreenX(bounds.minX());
            float y = viewport.worldToScreenZ(bounds.minZ());
            float w = viewport.worldToScreenX(bounds.maxX()) - x;
            float h = viewport.worldToScreenZ(bounds.maxZ()) - y;
            boolean selected = selection.contains(territory.name());
            boolean hovered = territory.equals(hoveredTerritory);
            Zone unavailableOwner = access.unavailableOwner(territory.name());
            if (unavailableOwner != null) {
                canvas.fillRect(x, y, w, h, alpha(color(BACKGROUND_BODY_OPAQUE), 135));
            }
            if (hovered) {
                canvas.fillRect(x, y, w, h, alpha(color(MAP_TERRITORY), 42));
            }
            if (unavailableOwner != null) {
                canvas.strokeRect(x, y, w, h, hovered ? 1.7f : 1f,
                        alpha(color(CONTROL_WARNING), hovered ? 245 : 170));
            } else if (selected) {
                canvas.strokeRect(x - 1, y - 1, w + 2, h + 2, 2.2f, alpha(selectedColor, 255));
            } else {
                canvas.strokeRect(x, y, w, h, hovered ? 1.7f : .7f,
                        alpha(color(MAP_TERRITORY), hovered ? 245 : 100));
            }
        }
        canvas.resetScissor();
        if (hoveredTerritory != null) {
            WarPlannerSnapshot.TerritoryDetails detail = details.get(hoveredTerritory.name());
            Zone unavailableOwner = access.unavailableOwner(hoveredTerritory.name());
            String resourceText = detail == null || detail.resources().isEmpty()
                    ? "Base emerald income" : String.join(" · ", detail.resources());
            String actionText = unavailableOwner == null
                    ? selection.contains(hoveredTerritory.name()) ? "Click to remove" : "Click to select"
                    : "Unavailable · assigned to " + unavailableOwner.name();
            float tooltipWidth = Math.min(280,
                    Math.max(150, 14 + Math.max(resourceText.length(), actionText.length()) * 6));
            canvas.fillRoundedRect(
                    nvgMouseX + 10,
                    nvgMouseY + 10,
                    tooltipWidth,
                    49,
                    4,
                    WarPlannerScreen.plannerBackground(color(BACKGROUND_POPUP)));
            text(canvas, hoveredTerritory.name(), nvgMouseX + 17, nvgMouseY + 21, 10, color(TEXT_PRIMARY), false);
            text(canvas, resourceText, nvgMouseX + 17, nvgMouseY + 36, 9, color(TEXT_MUTED), false);
            text(canvas, actionText, nvgMouseX + 17, nvgMouseY + 48, 9,
                    color(unavailableOwner == null ? MAP_SUBTEXT : CONTROL_WARNING), false);
        }
    }

    private void renderSidebar(UiCanvas canvas, PickerControlLayout layout) {
        float height = layout.sidebar().height();
        canvas.fillRect(
                layout.sidebar().x(),
                layout.sidebar().y(),
                layout.sidebar().width(),
                layout.sidebar().height(),
                WarPlannerScreen.plannerBackground(color(MAP_SIDEBAR)));
        text(canvas, "War map zone", PADDING, 20, 17, color(ACCENT_PRIMARY), false);
        label(canvas, "Name", layout.nameField().y() - 14);
        field(canvas, layout.nameField(), zoneName, focus == Focus.NAME || isKeyboardTarget(NAME), saving);
        positionColorWidget(layout.colorWidget());
        zoneColorWidget.render(canvas, nvgMouseX, nvgMouseY);
        if (saving) {
            canvas.fillRect(
                    layout.colorWidget().x(),
                    layout.colorWidget().y(),
                    layout.colorWidget().width(),
                    layout.colorWidget().height(),
                    alpha(color(ACCENT_DISABLED), 95));
        }
        label(canvas, "Party assignments · click to toggle", layout.teamsLabelY());
        WarPlannerSnapshot snapshot = plannerSnapshot();
        int visibleTeamRows = layout.visibleTeamRows();
        if (snapshot != null) {
            int shown = 0;
            int start = WarTerritoryPickerPolicy.scrollStart(
                    teamScrollRows, snapshot.teams().size(), visibleTeamRows);
            for (int index = start; index < snapshot.teams().size() && shown < visibleTeamRows; index++) {
                Team team = snapshot.teams().get(index);
                Bounds teamBounds = layout.teamRow(shown);
                boolean assigned = assignedTeamIds.contains(team.id());
                boolean hovered = !saving && teamBounds.contains(nvgMouseX, nvgMouseY);
                boolean keyboardFocused = isKeyboardTeam(index);
                Color background = color(assigned ? STATUS_SUCCESS_BACKGROUND : STATUS_DANGER_BACKGROUND);
                Color border = color(keyboardFocused
                        ? ACCENT_PRIMARY
                        : assigned ? STATUS_SUCCESS_BORDER : STATUS_DANGER_BORDER);
                canvas.fillRoundedRect(
                        teamBounds.x(), teamBounds.y(), teamBounds.width(), teamBounds.height(), 3,
                        hovered ? brighten(background, 18) : background);
                canvas.strokeRect(
                        teamBounds.x(), teamBounds.y(), teamBounds.width(), teamBounds.height(),
                        keyboardFocused ? 2 : 1, border);
                text(canvas, teamAssignmentLabel(team.name(), assigned),
                        teamBounds.x() + 7, teamBounds.centerY(), 10,
                        color(saving ? TEXT_DISABLED : TEXT_PRIMARY), false);
                shown++;
            }
            if (snapshot.teams().size() > visibleTeamRows) {
                text(canvas, (start + 1) + "–" + Math.min(start + visibleTeamRows, snapshot.teams().size()) + " of "
                                + snapshot.teams().size(),
                        PADDING, layout.selectionLabelY() - 2, 8, color(MAP_SUBTEXT), false);
            }
        }
        TerritoryAccess access = territoryAccess();
        int unavailableCount = access.visibleNames().size() - access.selectableNames().size();
        label(canvas, unavailableCount == 0
                ? "Selection"
                : "Selection · " + unavailableCount + " assigned elsewhere", layout.selectionLabelY());
        text(canvas, selection.names().size() + " territories",
                PADDING, layout.selectionLabelY() + 22, 13, color(TEXT_PRIMARY), false);
        long unknown = selection.names().stream().filter(name -> territoryIndex.territory(name) == null).count();
        if (unknown > 0) {
            text(canvas, unknown + " saved names are not in this map version",
                    PADDING, layout.selectionLabelY() + 39, 9,
                    color(CONTROL_WARNING), false);
        }
        button(canvas, layout.clear(), "Clear", true, saving, isKeyboardTarget(CLEAR));

        if (message != null && !message.isBlank()) {
            text(canvas, truncate(message, 34), PADDING, height - 74, 10, color(CONTROL_WARNING), false);
        }
        button(canvas, layout.cancel(), "Cancel", false, false, isKeyboardTarget(CANCEL));
        button(canvas, layout.save(),
                saving ? "Saving…" : "Save zone", false, saving, isKeyboardTarget(SAVE));
    }

    @Override
    public boolean mouseClicked(@NotNull MouseButtonEvent click, boolean outsideScreen) {
        float mx = MinecraftUiRenderer.mouseX(click.x());
        float my = MinecraftUiRenderer.mouseY(click.y());
        float width = MinecraftUiRenderer.screenWidth();
        float height = MinecraftUiRenderer.screenHeight();
        PickerControlLayout layout = controlLayout(width, height);
        if (click.button() == 0 && layout.resourceToggle().contains(mx, my)) {
            focus = Focus.NONE;
            setKeyboardTarget(ControlTarget.named(RESOURCE_COLORS));
            toggleResourceColors();
            return true;
        }
        if (click.button() == 0
                && canManage()
                && layout.lockToggle().contains(mx, my)) {
            focus = Focus.NONE;
            setKeyboardTarget(ControlTarget.named(LOCK_MAIN_MAP));
            toggleMainMapLock();
            return true;
        }
        if (mx < layout.sidebar().width()) {
            positionColorWidget(layout.colorWidget());
            if (!saving && zoneColorWidget.mouseClicked(mx, my, click.button())) {
                focus = Focus.NONE;
                keyboardTarget = null;
                return true;
            }
            if (click.button() != 0) return true;
            if (layout.cancel().contains(mx, my)) {
                setKeyboardTarget(ControlTarget.named(CANCEL));
                onClose();
                return true;
            }
            if (!saving && layout.nameField().contains(mx, my)) {
                setKeyboardTarget(ControlTarget.named(NAME));
                focus = Focus.NAME;
                replaceZoneNameOnType = false;
                return true;
            }
            WarPlannerSnapshot snapshot = plannerSnapshot();
            if (!saving && snapshot != null) {
                int visibleTeamRows = layout.visibleTeamRows();
                int start = WarTerritoryPickerPolicy.scrollStart(
                        teamScrollRows, snapshot.teams().size(), visibleTeamRows);
                for (int shown = 0; shown < visibleTeamRows && start + shown < snapshot.teams().size(); shown++) {
                    int teamIndex = start + shown;
                    Team team = snapshot.teams().get(teamIndex);
                    if (layout.teamRow(shown).contains(mx, my)) {
                        setKeyboardTarget(ControlTarget.team(teamIndex));
                        if (!assignedTeamIds.add(team.id())) assignedTeamIds.remove(team.id());
                        return true;
                    }
                }
            }
            if (!saving && layout.clear().contains(mx, my)) {
                setKeyboardTarget(ControlTarget.named(CLEAR));
                selection = new WarZoneSelection(Set.of());
                return true;
            }
            if (!saving && layout.save().contains(mx, my)) {
                setKeyboardTarget(ControlTarget.named(SAVE));
                save();
                return true;
            }
            focus = Focus.NONE;
            keyboardTarget = null;
            return true;
        }
        if (click.button() != 0) return super.mouseClicked(click, outsideScreen);
        if (viewport(layout.map()).isInsideScreen(mx, my)) {
            focus = Focus.NONE;
            keyboardTarget = null;
            draggingMap = true;
            dragMoved = false;
            return true;
        }
        return super.mouseClicked(click, outsideScreen);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent click, double deltaX, double deltaY) {
        PickerControlLayout layout = currentControlLayout();
        positionColorWidget(layout.colorWidget());
        if (!saving && zoneColorWidget.mouseDragged(
                MinecraftUiRenderer.mouseX(click.x()), MinecraftUiRenderer.mouseY(click.y()))) {
            return true;
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
        PickerControlLayout layout = currentControlLayout();
        positionColorWidget(layout.colorWidget());
        if (!saving && zoneColorWidget.mouseReleased(
                MinecraftUiRenderer.mouseX(click.x()), MinecraftUiRenderer.mouseY(click.y()), click.button())) {
            return true;
        }
        if (click.button() == 0 && draggingMap) {
            draggingMap = false;
            float mx = MinecraftUiRenderer.mouseX(click.x());
            float my = MinecraftUiRenderer.mouseY(click.y());
            MapViewport viewport = viewport(layout.map());
            if (!saving && !dragMoved && viewport.isInsideScreen(mx, my)) {
                GuildTerritory territory = territoryIndex.territoryAt(
                        viewport.screenToWorldX(mx), viewport.screenToWorldZ(my));
                if (territory != null && territoryAccess().isSelectable(territory.name())) {
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
        PickerControlLayout layout = currentControlLayout();
        int visibleTeamRows = layout.visibleTeamRows();
        if (layout.teamScroll().contains(mx, my)) {
            WarPlannerSnapshot snapshot = plannerSnapshot();
            int size = snapshot == null ? 0 : snapshot.teams().size();
            int delta = scrollY > 0 ? -1 : 1;
            teamScrollRows = WarTerritoryPickerPolicy.scrollStart(
                    teamScrollRows + delta, size, visibleTeamRows);
            return true;
        }
        MapViewport before = viewport(layout.map());
        if (!before.isInsideScreen(mx, my)) return true;
        double worldX = before.screenToWorldX(mx);
        double worldZ = before.screenToWorldZ(my);
        double factor = scrollY > 0 ? 1.15 : 1 / 1.15;
        pixelsPerBlock = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, pixelsPerBlock * factor));
        MapViewport after = viewport(layout.map());
        centerX = worldX - (mx - (after.screenX() + after.screenWidth() / 2)) / pixelsPerBlock;
        centerZ = worldZ - (my - (after.screenY() + after.screenHeight() / 2)) / pixelsPerBlock;
        return true;
    }

    @Override
    public boolean keyPressed(@NotNull KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_TAB) {
            if (!saving) {
                positionColorWidget(currentControlLayout().colorWidget());
                zoneColorWidget.mouseClicked(-1, -1, 0);
            }
            focus = Focus.NONE;
            WarPlannerSnapshot snapshot = plannerSnapshot();
            int teamCount = snapshot == null ? 0 : snapshot.teams().size();
            boolean backwards = (event.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0;
            setKeyboardTarget(WarTerritoryPickerPolicy.nextKeyboardTarget(
                    keyboardTarget, backwards, canManage(), teamCount, saving));
            return true;
        }
        if (!saving && zoneColorWidget.keyPressed(event)) {
            return true;
        }
        if (!saving && focus != Focus.NONE) {
            if (event.key() == GLFW.GLFW_KEY_ESCAPE || event.key() == GLFW.GLFW_KEY_ENTER
                    || event.key() == GLFW.GLFW_KEY_KP_ENTER) {
                focus = Focus.NONE;
                return true;
            }
            if (event.key() == GLFW.GLFW_KEY_BACKSPACE) {
                if (focus == Focus.NAME && replaceZoneNameOnType) {
                    zoneName = "";
                    replaceZoneNameOnType = false;
                } else if (focus == Focus.NAME && !zoneName.isEmpty()) {
                    zoneName = zoneName.substring(0, zoneName.length() - 1);
                }
                return true;
            }
            return true;
        }
        if (WarTerritoryPickerPolicy.isActivationKey(event.key()) && activateKeyboardTarget()) {
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(@NotNull CharacterEvent event) {
        if (!saving && zoneColorWidget.charTyped(event)) {
            return true;
        }
        if (!saving && focus != Focus.NONE) {
            String typed = TextInputHelper.getTypedText(event);
            if (typed != null && typed.length() == 1 && !Character.isISOControl(typed.charAt(0))) {
                if (focus == Focus.NAME && replaceZoneNameOnType) {
                    zoneName = "";
                    replaceZoneNameOnType = false;
                }
                if (focus == Focus.NAME && zoneName.length() < 64) zoneName += typed;
            }
            return true;
        }
        return super.charTyped(event);
    }

    private boolean activateKeyboardTarget() {
        if (keyboardTarget == null) return false;
        ControlKind kind = keyboardTarget.kind();
        if (kind == RESOURCE_COLORS) {
            toggleResourceColors();
            return true;
        }
        if (kind == LOCK_MAIN_MAP) {
            if (canManage()) toggleMainMapLock();
            return true;
        }
        if (kind == CANCEL) {
            onClose();
            return true;
        }
        if (saving) return true;
        if (kind == NAME) {
            focus = Focus.NAME;
            return true;
        }
        if (kind == TEAM) {
            WarPlannerSnapshot snapshot = plannerSnapshot();
            int index = keyboardTarget.teamIndex();
            if (snapshot != null && index >= 0 && index < snapshot.teams().size()) {
                long teamId = snapshot.teams().get(index).id();
                if (!assignedTeamIds.add(teamId)) assignedTeamIds.remove(teamId);
            }
            return true;
        }
        if (kind == CLEAR) {
            selection = new WarZoneSelection(Set.of());
            return true;
        }
        if (kind == SAVE) {
            save();
            return true;
        }
        return false;
    }

    private void setKeyboardTarget(ControlTarget target) {
        keyboardTarget = target;
        if (target == null || target.kind() != TEAM) return;
        WarPlannerSnapshot snapshot = plannerSnapshot();
        if (snapshot == null || snapshot.teams().isEmpty()) return;
        int visibleRows = currentControlLayout().visibleTeamRows();
        if (target.teamIndex() < teamScrollRows) {
            teamScrollRows = target.teamIndex();
        } else if (target.teamIndex() >= teamScrollRows + visibleRows) {
            teamScrollRows = target.teamIndex() - visibleRows + 1;
        }
        teamScrollRows = WarTerritoryPickerPolicy.scrollStart(
                teamScrollRows, snapshot.teams().size(), visibleRows);
    }

    private boolean isKeyboardTarget(ControlKind kind) {
        return keyboardTarget != null && keyboardTarget.kind() == kind;
    }

    private boolean isKeyboardTeam(int teamIndex) {
        return isKeyboardTarget(TEAM) && keyboardTarget.teamIndex() == teamIndex;
    }

    private void toggleResourceColors() {
        SeqClient.getWarPlannerResourceColorsSetting().setValue(!WarPlannerScreen.resourceColorsEnabled());
        SeqClient.getConfigManager().save();
    }

    private void toggleMainMapLock() {
        SeqClient.getWarPlannerLockTerritoriesSetting().setValue(!WarPlannerScreen.territoriesLocked());
        SeqClient.getConfigManager().save();
    }

    @Override
    public void onClose() {
        SeqClient.mc.setScreen(parent);
    }

    @Override
    public void removed() {
        UiRenderer.renderResource(canvas -> mapBackground.close());
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
            TerritoryAccess access = territoryAccess();
            List<String> unavailableSelections = selection.sortedNames().stream()
                    .filter(access::isVisible)
                    .filter(territory -> !access.isSelectable(territory))
                    .toList();
            if (!unavailableSelections.isEmpty()) {
                throw new IllegalArgumentException(
                        "Some selected territories now belong to another zone. Review the dimmed areas.");
            }
            ZoneDraft draft = new ZoneDraft(
                    zoneName,
                    normalizedColor,
                    List.copyOf(assignedTeamIds),
                    original == null ? null : original.version(),
                    selection.sortedNames().stream().filter(access::isSelectable).toList());
            saving = true;
            focus = Focus.NONE;
            setKeyboardTarget(ControlTarget.named(CANCEL));
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

    private TerritoryAccess territoryAccess() {
        refreshTerritoryCache();
        return cachedTerritoryAccess;
    }

    private Map<String, WarPlannerSnapshot.TerritoryDetails> territoryDetails() {
        refreshTerritoryCache();
        return cachedTerritoryDetails;
    }

    private void refreshTerritoryCache() {
        WarPlannerSnapshot snapshot = plannerSnapshot();
        if (snapshot == cachedTerritorySnapshot) return;
        cachedTerritorySnapshot = snapshot;
        cachedTerritoryAccess = WarTerritoryPickerPolicy.territoryAccess(
                snapshot, original == null ? null : original.id());
        if (snapshot == null) {
            cachedTerritoryDetails = Map.of();
            return;
        }
        TreeMap<String, WarPlannerSnapshot.TerritoryDetails> details =
                new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        snapshot.territoryDetails().forEach(detail -> details.put(detail.name(), detail));
        cachedTerritoryDetails = Collections.unmodifiableMap(details);
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
            TerritoryAccess access) {
        Set<String> drawnConnections = new HashSet<>();
        for (GuildTerritory territory : territoryIndex.territories()) {
            if (!access.isVisible(territory.name())) continue;
            WarPlannerSnapshot.TerritoryDetails detail = details.get(territory.name());
            if (detail == null) continue;
            for (String linkedName : detail.connections()) {
                GuildTerritory linked = territoryIndex.territory(linkedName);
                if (linked == null || !access.isVisible(linked.name())) continue;
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

    private MapViewport viewport(Bounds mapBounds) {
        return new MapViewport(
                centerX,
                centerZ,
                pixelsPerBlock,
                mapBounds.x(),
                mapBounds.y(),
                mapBounds.width(),
                mapBounds.height());
    }

    private PickerControlLayout currentControlLayout() {
        return controlLayout(MinecraftUiRenderer.screenWidth(), MinecraftUiRenderer.screenHeight());
    }

    private PickerControlLayout controlLayout(float width, float height) {
        PickerControlLayout layout = PickerControlLayout.create(
                width, height, zoneColorWidget.getHeight(), canManage());
        if (layout.controlsOverlapFooter()) {
            zoneColorWidget.collapse();
            layout = PickerControlLayout.create(width, height, zoneColorWidget.getHeight(), canManage());
        }
        return layout;
    }

    private WarPlannerSnapshot plannerSnapshot() {
        return manager == null ? null : manager.snapshot();
    }

    private boolean canManage() {
        return manager != null && manager.canManage();
    }

    static int visibleTeamRows(float height) {
        return PickerControlLayout.visibleTeamRows(height, 42);
    }

    static int visibleTeamRows(float height, float colorWidgetHeight) {
        return PickerControlLayout.visibleTeamRows(height, colorWidgetHeight);
    }

    private void positionColorWidget(Bounds bounds) {
        zoneColorWidget.setPosition(bounds.x(), bounds.y(), bounds.width(), bounds.height());
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
        double fitScale = focusSelection && !selected.isEmpty()
                ? 1
                : WorldMapBackgroundRenderer.FULL_MAP_FIT_SCALE;
        double pixelsPerBlock = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, Math.min(fitX, fitZ) * fitScale));
        return new InitialViewport(centerX, centerZ, pixelsPerBlock);
    }

    private void label(UiCanvas canvas, String value, float y) {
        text(canvas, value, PADDING, y, 10, color(MAP_SUBTEXT), false);
    }

    private void field(UiCanvas canvas, Bounds bounds, String value, boolean focused, boolean disabled) {
        canvas.fillRect(bounds.x(), bounds.y(), bounds.width(), bounds.height(),
                color(disabled ? ACCENT_DISABLED : focused ? CONTROL_INPUT_HOVER : CONTROL_INPUT));
        canvas.strokeRect(bounds.x(), bounds.y(), bounds.width(), bounds.height(), 1, color(CONTROL_BORDER));
        text(canvas, value, bounds.x() + 7, bounds.centerY(), 11,
                color(disabled ? TEXT_DISABLED : TEXT_PRIMARY), false);
    }

    private void button(
            UiCanvas canvas,
            Bounds bounds,
            String label,
            boolean danger,
            boolean disabled,
            boolean focused) {
        boolean hovered = !disabled && bounds.contains(nvgMouseX, nvgMouseY);
        Color background = disabled ? color(ACCENT_DISABLED)
                : danger ? color(hovered ? CONTROL_DANGER_HOVER : CONTROL_DANGER)
                : color(hovered ? MAP_CONTROL_HOVER : MAP_CONTROL);
        canvas.fillRoundedRect(bounds.x(), bounds.y(), bounds.width(), bounds.height(), 4, background);
        if (focused && !disabled) {
            canvas.strokeRect(bounds.x(), bounds.y(), bounds.width(), bounds.height(), 2, color(ACCENT_PRIMARY));
        }
        text(canvas, truncate(label, 27), bounds.centerX(), bounds.centerY(), 10,
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

    private static Color brighten(Color source, int amount) {
        return new Color(
                Math.min(255, source.getRed() + amount),
                Math.min(255, source.getGreen() + amount),
                Math.min(255, source.getBlue() + amount),
                source.getAlpha());
    }

    static String teamAssignmentLabel(String teamName, boolean assigned) {
        return (assigned ? "Assigned · " : "Unassigned · ") + teamName;
    }

    private static boolean intersects(MapBounds left, MapBounds right) {
        return left.maxX() >= right.minX() && left.minX() <= right.maxX()
                && left.maxZ() >= right.minZ() && left.minZ() <= right.maxZ();
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

}
