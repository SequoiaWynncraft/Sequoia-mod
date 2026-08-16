package com.seqwawa.seq.ui;

import static com.seqwawa.seq.managers.ThemeManager.color;
import static com.seqwawa.seq.ui.theme.UiColor.*;

import com.seqwawa.seq.client.SeqClient;
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
import java.awt.Color;
import java.nio.ByteBuffer;
import java.util.HashSet;
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
    private static final double MIN_ZOOM = 0.06;
    private static final double MAX_ZOOM = 1.8;

    private final Screen parent;
    private final WarPlannerManager manager = SeqClient.getWarPlannerManager();
    private final Zone original;
    private final GuildTerritoryService territoryService = GuildTerritoryService.getInstance();
    private final GatheringMapImageService mapImageService = GatheringMapImageService.getInstance();

    private GuildTerritoryIndex territoryIndex;
    private WarZoneSelection selection;
    private String zoneName;
    private String zoneColor;
    private Long assignedTeamId;
    private Focus focus = Focus.NONE;
    private String message;
    private boolean saving;

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
        super(Component.literal("War territory zone"));
        this.parent = parent;
        this.original = original;
        this.zoneName = original == null ? "New zone" : original.name();
        this.zoneColor = original == null ? "#55B8C5" : original.color();
        this.assignedTeamId = original == null ? null : original.assignedTeamId();
        this.selection = WarZoneSelection.of(original == null ? List.of() : original.territories());
        territoryService.loadBundledTerritories();
        territoryIndex = territoryService.index();
        mapImageService.requestLoad();
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

    private void renderPicker(UiCanvas canvas) {
        float width = canvas.metrics().width();
        float height = canvas.metrics().height();
        if (!fitted) {
            double fitX = Math.max(1, width - SIDEBAR_WIDTH) / (MapCalibration.MAX_WORLD_X - MapCalibration.MIN_WORLD_X);
            double fitZ = Math.max(1, height - HEADER_HEIGHT) / (MapCalibration.MAX_WORLD_Z - MapCalibration.MIN_WORLD_Z);
            pixelsPerBlock = Math.max(MIN_ZOOM, Math.min(fitX, fitZ));
            fitted = true;
        }
        canvas.fillRect(0, 0, width, height, color(BACKGROUND_BODY_OPAQUE));
        renderMap(canvas, viewport(width, height));
        renderSidebar(canvas, height);
        canvas.fillRect(SIDEBAR_WIDTH, 0, width - SIDEBAR_WIDTH, HEADER_HEIGHT, color(MAP_HEADER));
        text(canvas, original == null ? "Create territory zone" : "Edit territory zone",
                SIDEBAR_WIDTH + 12, HEADER_HEIGHT / 2, 15, color(MAP_TITLE), false);
        text(canvas, "Scroll to zoom · drag to pan · click a territory to toggle",
                width - 330, HEADER_HEIGHT / 2, 10, color(MAP_SUBTEXT), false);
    }

    private void renderMap(UiCanvas canvas, MapViewport viewport) {
        canvas.fillRect(viewport.screenX(), viewport.screenY(), viewport.screenWidth(), viewport.screenHeight(),
                color(BACKGROUND_BODY));
        UiImage image = mapImage();
        canvas.scissor(viewport.screenX(), viewport.screenY(), viewport.screenWidth(), viewport.screenHeight());
        if (image != null) {
            float x = viewport.worldToScreenX(MapCalibration.MIN_WORLD_X);
            float y = viewport.worldToScreenZ(MapCalibration.MIN_WORLD_Z);
            float w = viewport.worldToScreenX(MapCalibration.MAX_WORLD_X) - x;
            float h = viewport.worldToScreenZ(MapCalibration.MAX_WORLD_Z) - y;
            canvas.drawImage(image, x, y, w, h, 1f);
        }
        canvas.fillRect(viewport.screenX(), viewport.screenY(), viewport.screenWidth(), viewport.screenHeight(),
                color(MAP_TINT));

        Set<String> allowed = allowedTerritories();
        hoveredTerritory = !draggingMap && viewport.isInsideScreen(nvgMouseX, nvgMouseY)
                ? territoryIndex.territoryAt(viewport.screenToWorldX(nvgMouseX), viewport.screenToWorldZ(nvgMouseY))
                : null;
        if (hoveredTerritory != null && !allowed.contains(hoveredTerritory.name())) {
            hoveredTerritory = null;
        }
        MapBounds visible = viewport.visibleBounds();
        Color selectedColor = parseColor(zoneColor, color(MAP_SELECTED_TERRITORY));
        for (GuildTerritory territory : territoryIndex.territories()) {
            if (!allowed.contains(territory.name()) || !intersects(visible, territory.bounds())) continue;
            MapBounds bounds = territory.bounds();
            float x = viewport.worldToScreenX(bounds.minX());
            float y = viewport.worldToScreenZ(bounds.minZ());
            float w = viewport.worldToScreenX(bounds.maxX()) - x;
            float h = viewport.worldToScreenZ(bounds.maxZ()) - y;
            boolean selected = selection.contains(territory.name());
            boolean hovered = territory.equals(hoveredTerritory);
            Color outline = selected ? selectedColor : color(MAP_TERRITORY);
            if (selected || hovered) {
                canvas.fillRect(x, y, w, h, alpha(outline, selected ? 72 : 34));
            }
            canvas.strokeRect(x, y, w, h, selected || hovered ? 1.7f : .7f,
                    alpha(outline, selected || hovered ? 245 : 100));
        }
        canvas.resetScissor();
        if (hoveredTerritory != null) {
            float tooltipWidth = Math.min(220, 14 + hoveredTerritory.name().length() * 7);
            canvas.fillRoundedRect(nvgMouseX + 10, nvgMouseY + 10, tooltipWidth, 22, 4, color(BACKGROUND_POPUP));
            text(canvas, hoveredTerritory.name(), nvgMouseX + 17, nvgMouseY + 21, 10, color(TEXT_PRIMARY), false);
        }
    }

    private void renderSidebar(UiCanvas canvas, float height) {
        canvas.fillRect(0, 0, SIDEBAR_WIDTH, height, color(MAP_SIDEBAR));
        text(canvas, "Territory zone", PADDING, 20, 17, color(ACCENT_PRIMARY), false);
        float y = 46;
        label(canvas, "Name", y);
        field(canvas, zoneName, y + 14, focus == Focus.NAME);
        y += 52;
        label(canvas, "Color (#RRGGBB)", y);
        field(canvas, zoneColor, y + 14, focus == Focus.COLOR);
        canvas.fillRect(SIDEBAR_WIDTH - 38, y + 19, 18, 14, parseColor(zoneColor, color(ACCENT_PRIMARY)));
        y += 52;
        label(canvas, "Assigned team", y);
        String assignment = assignedTeamId == null ? "Unassigned" : assignedTeamName();
        button(canvas, PADDING, y + 14, SIDEBAR_WIDTH - PADDING * 2, BUTTON_HEIGHT, assignment, false, saving);
        y += 52;
        label(canvas, "Selection", y);
        text(canvas, selection.names().size() + " territories", PADDING, y + 22, 13, color(TEXT_PRIMARY), false);
        long unknown = selection.names().stream().filter(name -> territoryIndex.territory(name) == null).count();
        if (unknown > 0) {
            text(canvas, unknown + " saved names are not in this map version", PADDING, y + 39, 9,
                    color(CONTROL_WARNING), false);
        }
        button(canvas, PADDING, y + 52, 68, BUTTON_HEIGHT, "Clear", true, saving);

        if (message != null && !message.isBlank()) {
            text(canvas, truncate(message, 34), PADDING, height - 74, 10, color(CONTROL_WARNING), false);
        }
        button(canvas, PADDING, height - 38, 78, BUTTON_HEIGHT, "Cancel", false, false);
        button(canvas, SIDEBAR_WIDTH - PADDING - 92, height - 38, 92, BUTTON_HEIGHT,
                saving ? "Saving…" : "Save zone", false, saving);
    }

    @Override
    public boolean mouseClicked(@NotNull MouseButtonEvent click, boolean outsideScreen) {
        if (click.button() != 0) return super.mouseClicked(click, outsideScreen);
        float mx = MinecraftUiRenderer.mouseX(click.x());
        float my = MinecraftUiRenderer.mouseY(click.y());
        float width = MinecraftUiRenderer.screenWidth();
        float height = MinecraftUiRenderer.screenHeight();
        if (mx < SIDEBAR_WIDTH) {
            if (hit(mx, my, PADDING, 60, SIDEBAR_WIDTH - PADDING * 2, FIELD_HEIGHT)) {
                focus = Focus.NAME;
                return true;
            }
            if (hit(mx, my, PADDING, 112, SIDEBAR_WIDTH - PADDING * 2, FIELD_HEIGHT)) {
                focus = Focus.COLOR;
                return true;
            }
            if (hit(mx, my, PADDING, 164, SIDEBAR_WIDTH - PADDING * 2, BUTTON_HEIGHT)) {
                cycleAssignedTeam();
                focus = Focus.NONE;
                return true;
            }
            if (hit(mx, my, PADDING, 254, 68, BUTTON_HEIGHT)) {
                selection = new WarZoneSelection(Set.of());
                return true;
            }
            if (hit(mx, my, PADDING, height - 38, 78, BUTTON_HEIGHT)) {
                onClose();
                return true;
            }
            if (hit(mx, my, SIDEBAR_WIDTH - PADDING - 92, height - 38, 92, BUTTON_HEIGHT)) {
                save();
                return true;
            }
            focus = Focus.NONE;
            return true;
        }
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
        if (click.button() == 0 && draggingMap) {
            draggingMap = false;
            float mx = MinecraftUiRenderer.mouseX(click.x());
            float my = MinecraftUiRenderer.mouseY(click.y());
            MapViewport viewport = viewport(MinecraftUiRenderer.screenWidth(), MinecraftUiRenderer.screenHeight());
            if (!dragMoved && viewport.isInsideScreen(mx, my)) {
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
        if (focus != Focus.NONE) {
            if (event.key() == GLFW.GLFW_KEY_ESCAPE || event.key() == GLFW.GLFW_KEY_ENTER
                    || event.key() == GLFW.GLFW_KEY_KP_ENTER) {
                focus = Focus.NONE;
                return true;
            }
            if (event.key() == GLFW.GLFW_KEY_BACKSPACE) {
                if (focus == Focus.NAME && !zoneName.isEmpty()) zoneName = zoneName.substring(0, zoneName.length() - 1);
                if (focus == Focus.COLOR && !zoneColor.isEmpty()) zoneColor = zoneColor.substring(0, zoneColor.length() - 1);
                return true;
            }
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(@NotNull CharacterEvent event) {
        if (focus != Focus.NONE) {
            String typed = TextInputHelper.getTypedText(event);
            if (typed != null && typed.length() == 1 && !Character.isISOControl(typed.charAt(0))) {
                if (focus == Focus.NAME && zoneName.length() < 64) zoneName += typed;
                if (focus == Focus.COLOR && zoneColor.length() < 7
                        && (typed.charAt(0) == '#' || Character.digit(typed.charAt(0), 16) >= 0)) {
                    zoneColor += typed.toUpperCase(Locale.ROOT);
                }
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
            String normalizedColor = WarPlannerDrafts.normalizeColor(zoneColor);
            ZoneDraft draft = new ZoneDraft(
                    zoneName,
                    normalizedColor,
                    assignedTeamId,
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

    private void cycleAssignedTeam() {
        WarPlannerSnapshot snapshot = manager.snapshot();
        if (snapshot == null || snapshot.teams().isEmpty()) {
            assignedTeamId = null;
            return;
        }
        List<Team> teams = snapshot.teams();
        if (assignedTeamId == null) {
            assignedTeamId = teams.get(0).id();
            return;
        }
        for (int index = 0; index < teams.size(); index++) {
            if (teams.get(index).id() == assignedTeamId) {
                assignedTeamId = index + 1 < teams.size() ? teams.get(index + 1).id() : null;
                return;
            }
        }
        assignedTeamId = null;
    }

    private String assignedTeamName() {
        WarPlannerSnapshot snapshot = manager.snapshot();
        Team team = snapshot == null ? null : snapshot.team(assignedTeamId);
        return team == null ? "Team #" + assignedTeamId : team.name();
    }

    private Set<String> allowedTerritories() {
        WarPlannerSnapshot snapshot = manager.snapshot();
        if (snapshot == null) return Set.of();
        Set<String> allowed = new HashSet<>(snapshot.territories());
        for (Zone zone : snapshot.zones()) {
            if (original == null || zone.id() != original.id()) {
                allowed.removeAll(zone.territories());
            }
        }
        return allowed;
    }

    private MapViewport viewport(float width, float height) {
        return new MapViewport(centerX, centerZ, pixelsPerBlock, SIDEBAR_WIDTH, HEADER_HEIGHT,
                Math.max(1, width - SIDEBAR_WIDTH), Math.max(1, height - HEADER_HEIGHT));
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
        NAME,
        COLOR
    }
}
