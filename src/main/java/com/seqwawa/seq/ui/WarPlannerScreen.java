package com.seqwawa.seq.ui;

import static com.seqwawa.seq.ui.WarQueueMapOverlay.*;

import static com.seqwawa.seq.ui.WarMapSettings.*;

import static com.seqwawa.seq.ui.WarMapGeometry.*;

import static com.seqwawa.seq.managers.ThemeManager.color;
import static com.seqwawa.seq.ui.theme.UiColor.*;

import static com.seqwawa.seq.ui.WarPlannerDialogUi.*;
import static com.seqwawa.seq.ui.WarPingPicker.pingCandidates;
import static com.seqwawa.seq.ui.WarZonesTab.*;

import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.managers.WarPlannerManager;
import com.seqwawa.seq.managers.WarTerritoryQueueManager;
import com.seqwawa.seq.model.war.WarCompositionRole;
import com.seqwawa.seq.model.war.WarPlannerSnapshot;
import com.seqwawa.seq.model.war.WarPlannerSnapshot.RosterMember;
import com.seqwawa.seq.model.war.WarPlannerSnapshot.Team;
import com.seqwawa.seq.model.war.WarPlannerSnapshot.Zone;
import com.seqwawa.seq.model.war.WarPlannerSnapshot.ZoneCategory;
import com.seqwawa.seq.utils.TextInputHelper;
import com.seqwawa.seq.ui.widget.SliderWidget;
import com.seqwawa.seq.utils.rendering.MinecraftUiRenderer;
import com.seqwawa.seq.utils.rendering.UiCanvas;
import com.seqwawa.seq.utils.rendering.UiRenderer;
import java.awt.Color;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

/** Seq-only war management overlay. Authorization is supplied by protected backend responses. */
public final class WarPlannerScreen extends Screen {
    private static final float PADDING = 12;
    private static final float HEADER_HEIGHT = SequoiaUiStyle.HEADER_HEIGHT;
    private static final float AVAILABILITY_HEIGHT = 48;
    private static final float ROW_HEIGHT = 38;

    private static final float BUTTON_HEIGHT = 22;

    // Resource colors stay steady at the peak opacity used by queue highlights.
    static final int RESOURCE_FILL_ALPHA = 96;

    private final WarTeamsTab teamsTab;
    private final WarZonesTab zones;
    private final Screen parent;
    private final WarPlannerManager manager;
    private final WarTeamEditor teamEditor;
    private final WarSupportEditor supportEditor;
    private final WarRoleEditor roleEditor;
    private final WarPingPicker warPingPicker;
    private Tab tab = Tab.ZONES;
    private float nvgMouseX;
    private float nvgMouseY;
    private int scrollRows;
    private String flashMessage;
    private String displayedMessage;
    private long displayedMessageAt;

    private java.util.concurrent.CompletableFuture<WarPlannerManager.ActionResult> availabilityUpdate;
    private boolean sectionDropdownOpen;
    private SliderWidget opacitySlider;
    public WarPlannerScreen(Screen parent) {
        super(Component.literal("War Planner"));
        this.parent = parent;
        this.manager = SeqClient.getWarPlannerManager();
        java.util.concurrent.Executor clientExecutor = task -> SeqClient.mc.execute(task);
        java.util.function.Consumer<String> feedback = message -> flashMessage = message;
        teamEditor = new WarTeamEditor(manager, clientExecutor, feedback);
        supportEditor = new WarSupportEditor(manager, clientExecutor, feedback);
        roleEditor = new WarRoleEditor(manager, clientExecutor, feedback);
        warPingPicker = new WarPingPicker(manager, clientExecutor, feedback);
        teamsTab = new WarTeamsTab(manager, new WarTeamsTab.Actions() {
            public void openTeam(Team team) { teamEditor.open(team); }
            public void openSupport(int slot) { supportEditor.open(slot); }
            public void result(java.util.concurrent.CompletableFuture<WarPlannerManager.ActionResult> result) { showResult(result); }
            public void feedback(String message) { flashMessage = message; }
            public boolean busy() { return showBusyControls(); }
        });
        zones = new WarZonesTab(manager, new WarZonesTab.Actions() {
            public void openZone(Zone zone, boolean inspect) { SeqClient.mc.setScreen(new WarTerritoryPickerScreen(WarPlannerScreen.this, zone, inspect)); }
            public void openCategory(ZoneCategory category) { SeqClient.mc.setScreen(new WarZoneCategoryEditorScreen(WarPlannerScreen.this, category)); }
            public void result(java.util.concurrent.CompletableFuture<WarPlannerManager.ActionResult> result) { showResult(result); }
            public void queueResult(java.util.concurrent.CompletableFuture<WarTerritoryQueueManager.ActionResult> result) { showQueueResult(result); }
            public boolean busy() { return showBusyControls(); }
            public String stateLabel() { return WarPlannerScreen.this.stateLabel(); }
            public Color stateColor() { return WarPlannerScreen.this.stateColor(); }
            public void feedback(String message) { flashMessage = message; }
        });
    }

    @Override
    public void tick() {
        if (tab == Tab.ZONES) zones.tick();
        if (manager == null || manager.state() == WarPlannerManager.State.FORBIDDEN) {
            SeqClient.mc.setScreen(parent);
        } else if (warPingPicker.isOpen() && !manager.canManage()) {
            warPingPicker.close();
        }
    }

    /** Loads the large planner aggregate only after the player explicitly opens or refreshes this screen. */
    public void refreshPlanner() {
        if (manager != null) {
            showResult(manager.refreshNow());
        }
        refreshQueueViewer();
    }

    private void refreshQueueViewer() { zones.refreshQueueViewer(); }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        nvgMouseX = MinecraftUiRenderer.mouseX(mouseX);
        nvgMouseY = MinecraftUiRenderer.mouseY(mouseY);
        UiRenderer.renderScreen(this, this::renderPlanner);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (shouldBlurBackground(backgroundOpacityPercent())) {
            super.renderBackground(graphics, mouseX, mouseY, partialTick);
        }
    }

    void renderBehindDialog(UiCanvas canvas) {
        float savedX = nvgMouseX;
        float savedY = nvgMouseY;
        nvgMouseX = Float.NEGATIVE_INFINITY;
        nvgMouseY = Float.NEGATIVE_INFINITY;
        try {
            renderPlanner(canvas);
        } finally {
            nvgMouseX = savedX;
            nvgMouseY = savedY;
        }
    }

    private void renderPlanner(UiCanvas canvas) {
        float screenWidth = canvas.metrics().width();
        float height = canvas.metrics().height();
        PlannerViewport viewport = plannerViewport(screenWidth);
        SequoiaUiStyle.drawPanelFrame(canvas, headerHeight(viewport.width()));
        SequoiaSidebarNavigation.render(canvas, SequoiaSidebarNavigation.Destination.WAR, nvgMouseX, nvgMouseY);
        float screenMouseX = nvgMouseX;
        nvgMouseX -= viewport.x();
        canvas.save();
        canvas.translate(viewport.x(), 0);
        try {
            float width = viewport.width();
            text(canvas, "War Planner", width - PADDING, HEADER_HEIGHT / 2, width < 420 ? 10 : 18,
                    color(ACCENT_PRIMARY_HOVER), UiCanvas.HorizontalAlign.RIGHT);
            WarPlannerSnapshot current = manager.snapshot();
            RosterMember caller = current == null ? null : current.caller();
            boolean rolesEditable = caller != null;
            HeaderControls header = headerControls(width);
            renderDropdown(canvas, header.section(), tab.label, sectionDropdownOpen);
            primaryButton(canvas, header.roles().x(), 6, header.roles().width(), 18, "My roles",
                    showBusyControls() || !rolesEditable);
            primaryButton(canvas, header.refresh().x(), 6, header.refresh().width(), 18, "Refresh",
                    manager.isRequestInFlight() && !availabilityUpdatePending());

            if (tab == Tab.ZONES) {
                var slider = prepareOpacitySlider(width);
                if (slider != null) slider.render(canvas, nvgMouseX, nvgMouseY);
            }
            renderAvailability(canvas, width);
            renderSectionAction(canvas, width);
            renderContent(canvas, width, height);
            if (tab == Tab.ZONES) {
                zones.renderActions(canvas, nvgMouseX, nvgMouseY);
            }
            renderDropdownMenus(canvas, width, height);
            renderFeedback(canvas, width, height);

            if (warPingPicker.isOpen()) {
                warPingPicker.render(canvas, width, height, nvgMouseX, nvgMouseY);
            } else if (roleEditor.isOpen()) {
                roleEditor.render(canvas, width, height, nvgMouseX, nvgMouseY);
            } else if (teamEditor.isOpen()) {
                teamEditor.render(canvas, width, height, nvgMouseX, nvgMouseY);
            } else if (supportEditor.isOpen()) {
                supportEditor.render(canvas, width, height, nvgMouseX, nvgMouseY);
            }
        } finally {
            canvas.restore();
            nvgMouseX = screenMouseX;
        }
    }

    private void renderFeedback(UiCanvas canvas, float width, float height) {
        String message = manager.lastError() != null ? manager.lastError() : flashMessage;
        if (!java.util.Objects.equals(message, displayedMessage)) {
            displayedMessage = message;
            displayedMessageAt = monotonicMillis();
        }
        if (message == null || message.isBlank()
                || (manager.lastError() == null && monotonicMillis() - displayedMessageAt > 4_000)) return;
        float messageWidth = Math.min(width - PADDING * 2,
                UiRenderer.measureText(message, SeqClient.getFontManager().getSelectedFont(), 10).width() + 16);
        canvas.fillRect(PADDING, height - 32, messageWidth, 24, color(BACKGROUND_POPUP));
        text(canvas, truncate(message, availableCharacters(0, messageWidth - 16, 10, 90)),
                PADDING + 8, height - 20, 10, color(TEXT_SECONDARY), false);
    }

    private void renderAvailability(UiCanvas canvas, float width) {
        float y = headerHeight(width);
        canvas.fillRect(0, y, width, AVAILABILITY_HEIGHT, plannerBackground(color(BACKGROUND_CONTENT)));
        WarPlannerSnapshot snapshot = manager.snapshot();
        RosterMember caller = snapshot == null ? null : snapshot.caller();
        Duration remaining = manager.ownAvailabilityRemaining();
        String status = caller != null && caller.available() && !remaining.isZero()
                ? "Available for " + formatDuration(remaining)
                : "Unavailable";
        String roleLabel = caller == null ? "No role" : compositionLabel(caller.compositionRoles());
        AvailabilityLayout layout = availabilityLayout(width);
        if (layout.compact()) {
            text(canvas, truncate(status + " · " + roleLabel, availableCharacters(PADDING, width - PADDING, 10, 48)),
                    PADDING, y + 9, 10, color(remaining.isZero() ? TEXT_SECONDARY : CONTROL_SUCCESS), false);
        } else {
            text(canvas, "Your status", PADDING, y + 13, 10, color(TEXT_MUTED), false);
            int statusCharacters = availableCharacters(PADDING, layout.x() - 10, 14, 42);
            text(canvas, truncate(status + " · " + roleLabel, statusCharacters), PADDING, y + 31, 14,
                    color(remaining.isZero() ? TEXT_SECONDARY : CONTROL_SUCCESS), false);
        }

        String[] labels = layout.compact()
                ? new String[] {"30m", "1h", "2h", "Custom", "Off"}
                : new String[] {"30 min", "1 hour", "2 hours", "Custom", "Unavailable"};
        for (int index = 0; index < labels.length; index++) {
            tonedButton(canvas, nvgMouseX, nvgMouseY, layout.buttonX(index), y + layout.y(), layout.buttonWidth(index), BUTTON_HEIGHT,
                    labels[index], index == 4 ? ButtonTone.DANGER : ButtonTone.PRIMARY, showBusyControls());
        }
    }

    private void renderSectionAction(UiCanvas canvas, float width) {
        if (tab == Tab.ZONES) return;
        text(canvas, stateLabel(), width - PADDING, contentTop(width) + 12, 10, stateColor(), UiCanvas.HorizontalAlign.RIGHT);
        if (!manager.canManage()) return;
        primaryButton(canvas, PADDING, contentTop(width), 80, BUTTON_HEIGHT,
                tab == Tab.ROSTER ? "War ping" : "New team",
                showBusyControls() || (tab == Tab.ROSTER && pingCandidates(manager.snapshot(), "").isEmpty()));
    }

    private void renderContent(UiCanvas canvas, float width, float height) {
        WarPlannerSnapshot snapshot = manager.snapshot();
        if (snapshot == null) {
            text(canvas, manager.state() == WarPlannerManager.State.LOADING ? "Loading…" : "No planner data",
                    width / 2, 160, 14, color(TEXT_MUTED), true);
            return;
        }
        float top = contentTop(width) + (tab == Tab.ZONES ? 0 : 28);
        float bottom = height - PADDING;
        canvas.scissor(0, top, width, Math.max(0, bottom - top));
        switch (tab) {
            case ROSTER -> renderRoster(canvas, snapshot, width, top, bottom);
            case TEAMS -> teamsTab.render(canvas, snapshot, width, top, bottom, nvgMouseX, nvgMouseY);
            case ZONES -> zones.render(canvas, snapshot, width, top, bottom, nvgMouseX, nvgMouseY, sectionDropdownOpen);
        }
        canvas.resetScissor();
    }

    private void renderRoster(UiCanvas canvas, WarPlannerSnapshot snapshot, float width, float top, float bottom) {
        List<RosterMember> roster = sortedWarRoster(snapshot);
        if (roster.isEmpty()) {
            text(canvas, "No Sequoia members are online.", PADDING, top + 22, 13, color(TEXT_MUTED), false);
            return;
        }
        int start = Math.min(scrollRows, Math.max(0, roster.size() - 1));
        float y = top;
        for (int index = start; index < roster.size() && y + ROW_HEIGHT <= bottom; index++, y += ROW_HEIGHT) {
            RosterMember member = roster.get(index);
            boolean caller = snapshot.self() != null
                    && snapshot.self().playerUuid() != null
                    && snapshot.self().playerUuid().equalsIgnoreCase(member.playerUuid());
            canvas.fillRect(PADDING, y + 2, width - PADDING * 2, ROW_HEIGHT - 4,
                    plannerBackground(color(
                            caller
                                    ? ACCENT_PRIMARY_DARK
                                    : index % 2 == 0 ? BACKGROUND_CONTENT : BACKGROUND_CONTENT_FOCUSED)));
            text(canvas, truncate(member.displayName() + (caller ? " · You" : ""), 22),
                    PADDING + 8, y + 13, 13, color(TEXT_PRIMARY), false);
            float detailX = renderCompositionIcons(canvas, member.compositionRoles(), PADDING + 8, y + 20);
            float statusX = width - 128;
            int detailCharacters = availableCharacters(detailX, statusX - 6, 10, 28);
            text(canvas, truncate(compositionLabel(member.compositionRoles()), detailCharacters),
                    detailX + iconTextGap(member.compositionRoles()), y + 28, 10, color(TEXT_MUTED), false);
            String assignment = member.teamId() == null ? "No team" : teamName(snapshot, member.teamId());
            text(canvas, truncate(assignment, 18), statusX, y + 13, 11, color(TEXT_SECONDARY), false);
            Duration remaining = member.available()
                    ? WarPlannerManager.remainingUntil(member.availableUntil(), manager.serverNow())
                    : Duration.ZERO;
            text(canvas, remaining.isZero() ? "Unavailable" : formatDuration(remaining), statusX, y + 28, 10,
                    color(remaining.isZero() ? TEXT_MUTED : CONTROL_SUCCESS), false);
        }
    }

    private SliderWidget prepareOpacitySlider(float width) {
        if (opacitySlider == null && SeqClient.getWarPlannerBackgroundOpacitySetting() != null) {
            opacitySlider = new SliderWidget(SeqClient.getWarPlannerBackgroundOpacitySetting(), "Opacity %");
        }
        if (opacitySlider != null) {
            var bounds = headerControls(width).opacity();
            opacitySlider.setPosition(bounds.x(), bounds.y(), bounds.width(), bounds.height());
        }
        return opacitySlider;
    }

    private void renderDropdown(UiCanvas canvas, WarMapButtonBounds bounds, String label, boolean open) {
        DropdownMenu.trigger(canvas, bounds.x(), bounds.y(), bounds.width(), bounds.height(), label, open, true,
                nvgMouseX, nvgMouseY);
    }

    private void renderDropdownMenus(UiCanvas canvas, float width, float height) {
        if (sectionDropdownOpen) renderDropdownOptions(canvas, headerControls(width).section(),
                java.util.Arrays.stream(Tab.values()).map(candidate -> candidate.label).toList(), tab.ordinal());
        if (tab == Tab.ZONES) zones.renderDropdown(canvas, width, height, nvgMouseX, nvgMouseY);
    }

    private void renderDropdownOptions(UiCanvas canvas, WarMapButtonBounds trigger, List<String> labels, int selected) {
        DropdownMenu.list(canvas, trigger.x(), trigger.y() + trigger.height(), trigger.width(), DropdownMenu.ROW_HEIGHT,
                labels, i -> i == selected, 0, labels.size(), nvgMouseX, nvgMouseY);
    }

    @Override
    public boolean mouseClicked(@NotNull MouseButtonEvent click, boolean outsideScreen) {
        if (!plannerModalOpen() && click.button() != 0) {
            float mx = MinecraftUiRenderer.mouseX(click.x()) - plannerViewport(MinecraftUiRenderer.screenWidth()).x();
            float my = MinecraftUiRenderer.mouseY(click.y());
            if (sectionDropdownOpen || zones.dropdownOpen()) return true;
            if (tab == Tab.ZONES) {
                float width = plannerViewport(MinecraftUiRenderer.screenWidth()).width();
                var layout = warMapLayout(width, contentTop(width), MinecraftUiRenderer.screenHeight() - PADDING);
                var controls = warMapControls(layout, manager.canManage());
                if (controls.panel().contains(mx, my) || controls.fit().contains(mx, my) || controls.coloring().contains(mx, my)) return true;
            }
        }
        if ((warPingPicker.isOpen() || zones.actionsOpen()) && click.button() != 0) return true;
        if (click.button() == 1) {
            PlannerViewport viewport = plannerViewport(MinecraftUiRenderer.screenWidth());
            float mx = MinecraftUiRenderer.mouseX(click.x()) - viewport.x();
            float my = MinecraftUiRenderer.mouseY(click.y());
            WarPlannerSnapshot snapshot = manager == null ? null : manager.snapshot();
            if (!warPingPicker.isOpen()
                    && !roleEditor.isOpen()
                    && !teamEditor.isOpen()
                    && !supportEditor.isOpen()
                    && tab == Tab.ZONES
                    && manager != null
                    && manager.canManage()
                    && snapshot != null) {
                float height = MinecraftUiRenderer.screenHeight();
                if (zones.rightClickZoneName(snapshot, mx, my, viewport.width(), height)
                        || zones.rightClickWarMapTerritory(snapshot, mx, my, viewport.width(), height)) {
                    return true;
                }
            }
            return super.mouseClicked(click, outsideScreen);
        }
        if (click.button() != 0) {
            return super.mouseClicked(click, outsideScreen);
        }
        PlannerViewport viewport = plannerViewport(MinecraftUiRenderer.screenWidth());
        float mx = MinecraftUiRenderer.mouseX(click.x()) - viewport.x();
        float my = MinecraftUiRenderer.mouseY(click.y());
        float width = viewport.width();
        float height = MinecraftUiRenderer.screenHeight();

        if (warPingPicker.isOpen()) {
            return warPingPicker.click(mx, my, width, height);
        }
        if (roleEditor.isOpen()) {
            return roleEditor.click(mx, my, width, height);
        }
        if (teamEditor.isOpen()) {
            return teamEditor.click(mx, my, width, height);
        }
        if (supportEditor.isOpen()) {
            return supportEditor.click(mx, my, width, height);
        }
        if (clickPlannerDropdown(mx, my, width, height)) return true;
        if (zones.clickActions(mx, my)) return true;
        if (SequoiaSidebarNavigation.click(mx + viewport.x(), my, height,
                SequoiaSidebarNavigation.Destination.WAR, parent)) return true;
        HeaderControls header = headerControls(width);
        if (tab == Tab.ZONES) {
            var slider = prepareOpacitySlider(width);
            int opacityBefore = backgroundOpacityPercent();
            boolean sliderClicked = slider != null && slider.mouseClicked(mx, my, 0);
            if (opacityBefore != backgroundOpacityPercent()) SeqClient.getConfigManager().save();
            if (sliderClicked) return true;
        }
        if (header.refresh().contains(mx, my)) {
            if (!manager.isRequestInFlight()) showResult(manager.refreshNow());
            return true;
        }
        if (header.roles().contains(mx, my)) {
            roleEditor.open();
            return true;
        }
        AvailabilityLayout availability = availabilityLayout(width);
        float availabilityY = headerHeight(width) + availability.y();
        if (hit(mx, my, availability.buttonX(0), availabilityY, availability.buttonWidth(0), BUTTON_HEIGHT)) {
            return setAvailability(30);
        }
        if (hit(mx, my, availability.buttonX(1), availabilityY, availability.buttonWidth(1), BUTTON_HEIGHT)) {
            return setAvailability(60);
        }
        if (hit(mx, my, availability.buttonX(2), availabilityY, availability.buttonWidth(2), BUTTON_HEIGHT)) {
            return setAvailability(120);
        }
        if (hit(mx, my, availability.buttonX(3), availabilityY, availability.buttonWidth(3), BUTTON_HEIGHT)) {
            SeqClient.mc.setScreen(new WarAvailabilityEditorScreen(this));
            return true;
        }
        if (hit(mx, my, availability.buttonX(4), availabilityY, availability.buttonWidth(4), BUTTON_HEIGHT)) {
            return setAvailability(0);
        }

        if (manager.canManage() && tab != Tab.ZONES && hit(mx, my, PADDING, contentTop(width), 80, BUTTON_HEIGHT)) {
            if (tab == Tab.ROSTER && !manager.isMutating() && !pingCandidates(manager.snapshot(), "").isEmpty()) {
                if (opacitySlider != null) opacitySlider.onHidden();
                zones.cancelMapDrag();
                teamsTab.cancelDrag();
                zones.cancelZoneDrag();
                warPingPicker.open();
            } else if (tab == Tab.TEAMS && !manager.isMutating()) {
                teamEditor.open(null);
            }
            return true;
        }
        return clickContent(mx, my, width, height) || super.mouseClicked(click, outsideScreen);
    }

    private boolean plannerModalOpen() {
        return warPingPicker.isOpen() || roleEditor.isOpen() || teamEditor.isOpen() || supportEditor.isOpen();
    }

    private boolean clickPlannerDropdown(float mx, float my, float width, float height) {
        var section = headerControls(width).section();
        if (sectionDropdownOpen) {
            sectionDropdownOpen = false;
            int index = DropdownMenu.optionAt(mx, my, section.x(), section.y() + section.height(), section.width(),
                    DropdownMenu.ROW_HEIGHT, Tab.values().length, 0);
            if (index >= 0) {
                tab = Tab.values()[index];
                zones.reset();
                scrollRows = 0;
                teamsTab.reset();
                if (opacitySlider != null) opacitySlider.onHidden();
            }
            return true;
        }
        if (tab == Tab.ZONES && zones.clickDropdown(mx, my, width, height)) {
            if (opacitySlider != null) opacitySlider.onHidden();
            return true;
        }
        if (section.contains(mx, my)) {
            sectionDropdownOpen = true;

        } else {
            return false;
        }
        zones.cancelMapDrag();
        zones.clearQueueClick();
        zones.clearActionMenu();
        if (opacitySlider != null) opacitySlider.onHidden();
        return true;
    }

    private boolean clickContent(float mx, float my, float width, float height) {
        WarPlannerSnapshot snapshot = manager.snapshot();
        if (snapshot == null || my < contentTop(width) + (tab == Tab.ZONES ? 0 : 28) || my > height - PADDING) return false;
        if (tab == Tab.TEAMS) return teamsTab.click(mx, my, width, height);
        if (tab == Tab.ZONES) return zones.click(snapshot, mx, my, width, height);
        return false;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent click, double deltaX, double deltaY) {
        if (warPingPicker.isOpen()) return true;
        if (tab == Tab.ZONES && opacitySlider != null && click.button() == 0 && !plannerModalOpen()
                && opacitySlider.mouseDragged(MinecraftUiRenderer.mouseX(click.x())
                        - plannerViewport(MinecraftUiRenderer.screenWidth()).x(), MinecraftUiRenderer.mouseY(click.y()))) {
            return true;
        }
        if (tab == Tab.ZONES && click.button() == 0 && zones.drag(
                MinecraftUiRenderer.mouseX(click.x()) - plannerViewport(MinecraftUiRenderer.screenWidth()).x(),
                MinecraftUiRenderer.mouseY(click.y()), plannerViewport(MinecraftUiRenderer.screenWidth()).width(),
                MinecraftUiRenderer.screenHeight(), MinecraftUiRenderer.mouseDelta(deltaX), MinecraftUiRenderer.mouseDelta(deltaY))) return true;
        if (tab == Tab.TEAMS && click.button() == 0 && teamsTab.drag(
                MinecraftUiRenderer.mouseX(click.x()) - plannerViewport(MinecraftUiRenderer.screenWidth()).x(),
                MinecraftUiRenderer.mouseY(click.y()))) return true;
        return super.mouseDragged(click, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(@NotNull MouseButtonEvent click) {
        if (warPingPicker.isOpen()) return true;
        if (opacitySlider != null && opacitySlider.mouseReleased(
                MinecraftUiRenderer.mouseX(click.x()) - plannerViewport(MinecraftUiRenderer.screenWidth()).x(),
                MinecraftUiRenderer.mouseY(click.y()), click.button())) {
            SeqClient.getConfigManager().save();
            return true;
        }
        if (tab == Tab.ZONES && click.button() == 0 && zones.release(
                MinecraftUiRenderer.mouseX(click.x()) - plannerViewport(MinecraftUiRenderer.screenWidth()).x(),
                MinecraftUiRenderer.mouseY(click.y()), plannerViewport(MinecraftUiRenderer.screenWidth()).width(), MinecraftUiRenderer.screenHeight())) return true;
        if (tab == Tab.TEAMS && click.button() == 0 && teamsTab.release(
                MinecraftUiRenderer.mouseX(click.x()) - plannerViewport(MinecraftUiRenderer.screenWidth()).x(),
                MinecraftUiRenderer.mouseY(click.y()), plannerViewport(MinecraftUiRenderer.screenWidth()).width(), MinecraftUiRenderer.screenHeight())) return true;
        return super.mouseReleased(click);
    }

    @Override
    public boolean keyPressed(@NotNull KeyEvent keyEvent) {
        if (teamEditor.keyPressed(keyEvent.key())) return true;

        if (!plannerModalOpen() && (sectionDropdownOpen || zones.dropdownOpen())) {
            if (keyEvent.key() == GLFW.GLFW_KEY_ESCAPE) {
                sectionDropdownOpen = false;
                zones.closeDropdown();
            }
            return true;
        }
        if (!plannerModalOpen() && tab == Tab.ZONES && opacitySlider != null && opacitySlider.keyPressed(keyEvent)) {
            SeqClient.getConfigManager().save();
            return true;
        }
        if (keyEvent.key() == GLFW.GLFW_KEY_ESCAPE && zones.closeActions()) return true;
        if (warPingPicker.keyPressed(keyEvent.key())) return true;
        return super.keyPressed(keyEvent);
    }

    @Override
    public boolean charTyped(@NotNull CharacterEvent characterEvent) {
        if (!plannerModalOpen() && tab == Tab.ZONES && opacitySlider != null && opacitySlider.charTyped(characterEvent)) return true;
        String typedText = TextInputHelper.getTypedText(characterEvent);
        if (warPingPicker.charTyped(typedText) || teamEditor.charTyped(typedText)) return true;
        return super.charTyped(characterEvent);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!plannerModalOpen() && (sectionDropdownOpen || zones.dropdownOpen())) return true;
        zones.closeActions();
        if (scrollY == 0) return true;
        int delta = scrollY > 0 ? -1 : 1;
        WarPlannerSnapshot snapshot = manager.snapshot();
        if (snapshot == null) return true;
        if (warPingPicker.isOpen()) {
            warPingPicker.scroll(delta);
        } else if (roleEditor.isOpen()) {
            return true;
        } else if (teamEditor.isOpen()) {
            teamEditor.scroll(delta);
        } else if (supportEditor.isOpen()) {
            supportEditor.scroll(delta);
        } else {
            PlannerViewport viewport = plannerViewport(MinecraftUiRenderer.screenWidth());
            float localMouseX = MinecraftUiRenderer.mouseX(mouseX) - viewport.x();
            float localMouseY = MinecraftUiRenderer.mouseY(mouseY);
            float scrollTop = contentTop(viewport.width()) + (tab == Tab.ZONES ? 0 : 28);
            if (!hit(localMouseX, localMouseY, PADDING, scrollTop,
                    viewport.width() - PADDING * 2, MinecraftUiRenderer.screenHeight() - scrollTop - PADDING)) {
                return true;
            }
            if (tab == Tab.TEAMS) { teamsTab.scroll(snapshot, localMouseX, localMouseY, viewport.width(), MinecraftUiRenderer.screenHeight(), delta); return true; }
            if (tab == Tab.ZONES) return zones.scroll(snapshot, localMouseX, localMouseY, viewport.width(), MinecraftUiRenderer.screenHeight(), scrollY);
            scrollRows = clampRows(scrollRows + delta, snapshot.visibleRoster().size());
        }
        return true;
    }

    @Override
    public void onClose() {
        SeqClient.mc.setScreen(parent);
    }

    @Override
    public void removed() {
        if (opacitySlider != null) {
            opacitySlider.onHidden();
            SeqClient.getConfigManager().save();
        }
        teamEditor.close();
        supportEditor.close();
        roleEditor.close();
        warPingPicker.close();
        zones.closeResources();
        super.removed();
    }

    private boolean availabilityUpdatePending() {
        return availabilityUpdate != null && !availabilityUpdate.isDone();
    }

    private boolean showBusyControls() {
        // Availability updates keep the existing button colors; request guards still prevent concurrent edits.
        return manager.isMutating() && !availabilityUpdatePending();
    }

    private boolean setAvailability(int minutes) {
        if (manager.isRequestInFlight()) return true;
        availabilityUpdate = minutes == 0 ? manager.clearAvailability() : manager.setAvailability(minutes);
        showResult(availabilityUpdate);
        return true;
    }

    private void showResult(java.util.concurrent.CompletableFuture<WarPlannerManager.ActionResult> future) {
        future.whenComplete((result, error) -> SeqClient.mc.execute(() -> flashMessage = error != null
                ? "War planner request failed."
                : result == null || result.success() ? null : result.message()));
    }

    private void showQueueResult(
            java.util.concurrent.CompletableFuture<WarTerritoryQueueManager.ActionResult> future) {
        future.whenComplete((result, error) -> SeqClient.mc.execute(() -> flashMessage = error != null
                ? "War queue request failed."
                : result == null ? "No response from the war queue." : result.success() ? null : result.message()));
    }

    private String stateLabel() {
        WarPlannerSnapshot current = manager.snapshot();
        if (manager.state() == WarPlannerManager.State.READY
                && current != null
                && !current.discordRolesAvailable()) {
            return "Live · Discord unavailable";
        }
        return switch (manager.state()) {
            case UNKNOWN -> "Waiting";
            case LOADING -> "Loading…";
            case READY -> manager.isMutating() ? "Saving…" : manager.canManage() ? "Live" : "Live · view only";
            case FORBIDDEN -> "Unavailable";
            case OFFLINE -> manager.canManage() ? "Offline · cached" : "Offline · view only";
        };
    }

    private Color stateColor() {
        WarPlannerSnapshot current = manager.snapshot();
        if (manager.state() == WarPlannerManager.State.READY
                && current != null
                && !current.discordRolesAvailable()) {
            return color(CONTROL_WARNING);
        }
        return switch (manager.state()) {
            case READY -> color(CONTROL_SUCCESS);
            case OFFLINE -> color(CONTROL_WARNING);
            default -> color(TEXT_MUTED);
        };
    }

    private static float iconTextGap(List<WarCompositionRole> roles) {
        return roles == null || roles.isEmpty() ? 0 : 2;
    }

    private static String compositionLabel(List<WarCompositionRole> roles) {
        return WarCompositionRole.ordered(roles).stream()
                .map(WarCompositionRole::label)
                .reduce((left, right) -> left + "/" + right)
                .orElse("No role");
    }

    static List<RosterMember> sortedWarRoster(WarPlannerSnapshot snapshot) {
        return snapshot.visibleRoster().stream()
                .sorted(Comparator.comparing(RosterMember::available)
                        .reversed()
                        .thenComparing(
                                Comparator.comparingInt((RosterMember member) -> member.compositionRoles().size())
                                        .reversed())
                        .thenComparing(RosterMember::displayName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    static PlannerViewport plannerViewport(float screenWidth) {
        return new PlannerViewport(SequoiaSidebarNavigation.WIDTH,
                Math.max(1, screenWidth - SequoiaSidebarNavigation.WIDTH));
    }

    static AvailabilityLayout availabilityLayout(float width) {
        if (width >= 520) return new AvailabilityLayout(Math.max(155, width - 360), 13, 58, 76, 6, false);
        float gap = 4;
        float buttonWidth = Math.max(1, (width - PADDING * 2 - gap * 4) / 5);
        return new AvailabilityLayout(PADDING, 23, buttonWidth, buttonWidth, gap, true);
    }

    // This is an explicit user opacity control, not a hard-coded replacement for theme alpha.

    static HeaderControls headerControls(float width) {
        float sectionWidth = width < 420 ? 68 : 116;
        float actionWidth = width < 420 ? 50 : 64;
        var section = new WarMapButtonBounds(PADDING, 6, sectionWidth, 18);
        var roles = new WarMapButtonBounds(section.x() + sectionWidth + 6, 6, actionWidth, 18);
        var refresh = new WarMapButtonBounds(roles.x() + actionWidth + 6, 6, actionWidth, 18);
        var opacity = width >= 660
                ? new WarMapButtonBounds(refresh.x() + actionWidth + 12, 6, 240, 18)
                : new WarMapButtonBounds(PADDING, 32, 240, 18);
        return new HeaderControls(section, roles, refresh, opacity);
    }

    static String formatDuration(Duration duration) {
        long seconds = Math.max(0, duration.getSeconds());
        long totalMinutes = Math.max(1, (seconds + 59) / 60);
        long hours = totalMinutes / 60;
        long minutes = totalMinutes % 60;
        if (hours == 0) return minutes + "m";
        return minutes == 0 ? hours + "h" : hours + "h " + minutes + "m";
    }

    private void primaryButton(UiCanvas canvas, float x, float y, float width, float height, String label, boolean disabled) {
        tonedButton(canvas, nvgMouseX, nvgMouseY, x, y, width, height, label, ButtonTone.PRIMARY, disabled);
    }

    private void button(UiCanvas canvas, float x, float y, float width, float height, String label, boolean danger, boolean disabled) {
        tonedButton(canvas, nvgMouseX, nvgMouseY, x, y, width, height, label, danger ? ButtonTone.DANGER : ButtonTone.SECONDARY, disabled);
    }

    private static boolean hit(float mx, float my, float x, float y, float width, float height) {
        return mx >= x && mx <= x + width && my >= y && my <= y + height;
    }

    private enum Tab {
        ROSTER("Roster"),
        TEAMS("Teams"),
        ZONES("War Map");

        private final String label;

        Tab(String label) {
            this.label = label;
        }
    }

    record PlannerViewport(float x, float width) {}

    record AvailabilityLayout(
            float x, float y, float regularButtonWidth, float actionButtonWidth, float gap, boolean compact) {
        float buttonX(int index) {
            int regularButtonsBefore = Math.min(index, 3);
            int actionButtonsBefore = Math.max(0, index - 3);
            return x
                    + regularButtonsBefore * regularButtonWidth
                    + actionButtonsBefore * actionButtonWidth
                    + index * gap;
        }

        float buttonWidth(int index) {
            return index >= 3 ? actionButtonWidth : regularButtonWidth;
        }
    }

    record HeaderControls(WarMapButtonBounds section, WarMapButtonBounds roles, WarMapButtonBounds refresh, WarMapButtonBounds opacity) {}

}
