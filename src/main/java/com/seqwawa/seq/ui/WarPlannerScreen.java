package com.seqwawa.seq.ui;

import static com.seqwawa.seq.managers.ThemeManager.color;
import static com.seqwawa.seq.ui.theme.UiColor.*;

import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.managers.AssetManager;
import com.seqwawa.seq.managers.WarPlannerManager;
import com.seqwawa.seq.managers.WarTerritoryQueueManager;
import com.seqwawa.seq.map.GatheringMapImageService;
import com.seqwawa.seq.map.GuildTerritory;
import com.seqwawa.seq.map.GuildTerritoryIndex;
import com.seqwawa.seq.map.GuildTerritoryService;
import com.seqwawa.seq.map.MapCalibration;
import com.seqwawa.seq.map.MapBounds;
import com.seqwawa.seq.map.MapViewport;
import com.seqwawa.seq.map.TelemetryPlayerMapOverlay;
import com.seqwawa.seq.map.WorldMapBackgroundRenderer;
import com.seqwawa.seq.model.war.WarCompositionRole;
import com.seqwawa.seq.model.war.WarCompositionTargets;
import com.seqwawa.seq.model.war.WarPlannerDrafts.TeamDraft;
import com.seqwawa.seq.model.war.WarPlannerDrafts.TeamMemberDraft;
import com.seqwawa.seq.model.war.WarPlannerDrafts.TeamMemberMoveDraft;
import com.seqwawa.seq.model.war.WarPlannerDrafts.SupportDraft;
import com.seqwawa.seq.model.war.WarPlannerDrafts.SupportSlotDraft;
import com.seqwawa.seq.model.war.WarPlannerDrafts.ZonePlacementDraft;
import com.seqwawa.seq.model.war.WarPlannerSnapshot;
import com.seqwawa.seq.model.war.WarPlannerSnapshot.RosterMember;
import com.seqwawa.seq.model.war.WarPlannerSnapshot.Team;
import com.seqwawa.seq.model.war.WarPlannerSnapshot.TeamMember;
import com.seqwawa.seq.model.war.WarPlannerSnapshot.SupportSlot;
import com.seqwawa.seq.model.war.WarPlannerSnapshot.Zone;
import com.seqwawa.seq.model.war.WarPlannerSnapshot.ZoneCategory;
import com.seqwawa.seq.model.war.WarTeamType;
import com.seqwawa.seq.model.war.WarTerritoryQueueFeed.TerritoryQueue;
import com.seqwawa.seq.utils.TextInputHelper;
import com.seqwawa.seq.ui.widget.SliderWidget;
import com.seqwawa.seq.utils.rendering.MinecraftUiRenderer;
import com.seqwawa.seq.utils.rendering.UiCanvas;
import com.seqwawa.seq.utils.rendering.UiRenderer;
import java.awt.Color;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.ToDoubleFunction;
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
    private static final float TEAM_MEMBER_ROW_STEP = 16;
    private static final float TEAM_ACTION_TOP = 8;
    private static final float TEAM_SELF_ACTION_WIDTH = 68;
    private static final float SUPPORT_PANEL_HEIGHT = 142;
    private static final float SUPPORT_ROWS_TOP = 35;
    private static final float SUPPORT_ROW_STEP = 26;
    private static final float SUPPORT_ROW_HEIGHT = 21;
    private static final float UNASSIGNED_POOL_TOP = 150;
    private static final float UNASSIGNED_ROW_HEIGHT = 24;
    private static final float WAR_MAP_SIDEBAR_GAP = 8;
    private static final float WAR_MAP_ZONE_ROW_HEIGHT = 44;
    private static final float WAR_MAP_ZONE_ROW_STEP = 48;
    private static final float WAR_MAP_CATEGORY_ROW_HEIGHT = 26;
    private static final float WAR_MAP_CATEGORY_ROW_STEP = 30;
    private static final float WAR_MAP_SIDEBAR_CONTENT_TOP = 64;
    private static final float WAR_MAP_SIDEBAR_BOTTOM_PADDING = 6;
    private static final float BUTTON_HEIGHT = 22;
    private static final float COMPOSITION_ICON_SIZE = 12;
    private static final float COMPOSITION_ICON_GAP = 3;
    private static final float TEAM_EDITOR_SEARCH_HEIGHT = 22;
    private static final float TEAM_EDITOR_SEARCH_OFFSET = 90;
    private static final float TEAM_EDITOR_LIST_OFFSET = 118;
    private static final int TEAM_EDITOR_SEARCH_MAX_LENGTH = 64;
    private static final float HQ_ICON_WIDTH = 24;
    private static final float HQ_ICON_HEIGHT = 19.5f;
    private static final long WAR_QUEUE_DOUBLE_CLICK_MILLIS = 350;
    private static final float WAR_QUEUE_DOUBLE_CLICK_MOVE_TOLERANCE = 4;
    private static final float WAR_QUEUE_MAP_MAX_FONT_SIZE = 8;
    private static final float WAR_QUEUE_MAP_MIN_FONT_SIZE = 5;
    private static final long WAR_QUEUE_PULSE_PERIOD_MILLIS = 1_600;
    private static final int WAR_QUEUE_PULSE_MIN_ALPHA = 36;
    private static final int WAR_QUEUE_PULSE_MAX_ALPHA = 96;
    private static final float WAR_QUEUE_TOOLTIP_TEXT_SIZE = 9;
    private static final float WAR_QUEUE_TOOLTIP_LINE_HEIGHT = 13;
    private static final float TEAM_CARD_GAP = 6;
    private static final float TEAM_GRID_BREAKPOINT = 900;
    private static final float TEAM_COMPACT_BREAKPOINT = 520;
    private static final float TEAM_COMPACT_SUPPORT_HEIGHT = 62;
    private static final float TEAM_SHORT_SUPPORT_HEIGHT = 28;
    private static final float TEAM_SHORT_CONTENT_HEIGHT = 180;

    private final Screen parent;
    private final WarPlannerManager manager;
    private final WarTerritoryQueueManager queueManager;
    private final GuildTerritoryIndex territoryIndex;
    private final GatheringMapImageService mapImageService = GatheringMapImageService.getInstance();
    private final WorldMapBackgroundRenderer mapBackground = new WorldMapBackgroundRenderer(mapImageService);
    private final TelemetryPlayerMapOverlay telemetryPlayerOverlay = new TelemetryPlayerMapOverlay();
    private Tab tab = Tab.ZONES;
    private float nvgMouseX;
    private float nvgMouseY;
    private int scrollRows;
    private String flashMessage;
    private String displayedMessage;
    private long displayedMessageAt;

    private Long editingTeamId;
    private TeamEditorBase teamEditorBase;
    private boolean teamEditorOpen;
    private WarTeamType teamType = WarTeamType.VLOW_MUNCH;
    private WarCompositionTargets teamTargets = WarCompositionTargets.NONE;
    private boolean teamTypeMenuOpen;
    private final List<TeamMemberDraft> teamMembers = new ArrayList<>();
    private boolean teamEditorSaving;
    private int editorScrollRows;
    private String teamEditorSearchQuery = "";
    private boolean teamEditorSearchFocused;
    private PendingDelete pendingDeleteTeam;
    private PendingDelete pendingDeleteZone;
    private PendingDelete pendingDeleteZoneCategory;
    private Integer editingSupportSlot;
    private int supportEditorScrollRows;
    private boolean supportEditorSaving;
    private java.util.concurrent.CompletableFuture<WarPlannerManager.ActionResult> availabilityUpdate;
    private boolean sectionDropdownOpen;
    private boolean coloringDropdownOpen;
    private SliderWidget opacitySlider;
    private boolean draggingWarMap;
    private float mapPressX;
    private float mapPressY;
    private boolean mapPressMoved;
    private String selectedWarTerritory;
    private PendingWarQueueClick pendingWarQueueClick;
    private boolean warMapFitted;
    private double warMapCenterX;
    private double warMapCenterZ;
    private double warMapPixelsPerBlock;
    private float fittedWarMapWidth = -1;
    private float fittedWarMapHeight = -1;
    private Boolean fittedWarMapLocked;
    private final Set<Long> hiddenZoneIds = new java.util.HashSet<>();
    private final Set<Long> hiddenZoneCategoryIds = new java.util.HashSet<>();
    private final Set<Long> collapsedZoneCategoryIds = new java.util.HashSet<>();
    private GuildTerritory hoveredWarMapTerritory;
    private MemberDrag memberDrag;
    private ZoneDrag zoneDrag;
    private Long zoneActionsId;
    private WarMapButtonBounds zoneActionsBounds;
    private int unassignedScrollRows;
    private boolean roleEditorOpen;
    private boolean roleEditorSaving;
    private final EnumSet<WarCompositionRole> selectedCompositionRoles =
            EnumSet.noneOf(WarCompositionRole.class);
    private boolean warPingPickerOpen;
    private boolean warPingSending;
    private int warPingScrollRows;
    private String warPingSearchQuery = "";
    private boolean warPingSearchFocused;

    public WarPlannerScreen(Screen parent) {
        super(Component.literal("War Planner"));
        this.parent = parent;
        this.manager = SeqClient.getWarPlannerManager();
        this.queueManager = SeqClient.getWarTerritoryQueueManager();
        this.hiddenZoneIds.addAll(SeqClient.getConfigManager().hiddenWarPlannerZoneIds());
        this.hiddenZoneCategoryIds.addAll(
                SeqClient.getConfigManager().hiddenWarPlannerZoneCategoryIds());
        GuildTerritoryService.getInstance().loadBundledTerritories();
        this.territoryIndex = GuildTerritoryService.getInstance().index();
        mapImageService.requestLoad();
    }

    @Override
    public void tick() {
        if (tab == Tab.ZONES && warMapPlayersEnabled()) telemetryPlayerOverlay.tick();
        if (manager == null || manager.state() == WarPlannerManager.State.FORBIDDEN) {
            SeqClient.mc.setScreen(parent);
        } else if (warPingPickerOpen && !manager.canManage()) {
            closeWarPingPicker();
        }
    }

    /** Loads the large planner aggregate only after the player explicitly opens or refreshes this screen. */
    public void refreshPlanner() {
        if (manager != null) {
            showResult(manager.refreshNow());
        }
        refreshQueueViewer();
    }

    private void refreshQueueViewer() {
        if (queueManager == null) {
            return;
        }
        queueManager.refreshForViewer().whenComplete((result, error) -> {
            if (error == null && result != null && result.success()) {
                return;
            }
            SeqClient.mc.execute(() -> flashMessage = error != null
                    ? "War queue request failed."
                    : result == null ? "War queue request failed." : result.message());
        });
    }

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
        SequoiaUiStyle.drawPanelFrame(canvas, HEADER_HEIGHT);
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
            boolean rolesEditable = current != null
                    && current.discordRolesAvailable()
                    && caller != null
                    && caller.discordId() != null
                    && !caller.discordId().isBlank();
            HeaderControls header = headerControls(width);
            renderDropdown(canvas, header.section(), tab.label, sectionDropdownOpen);
            primaryButton(canvas, header.roles().x(), 6, header.roles().width(), 18, "My roles",
                    showBusyControls() || !rolesEditable);
            primaryButton(canvas, header.refresh().x(), 6, header.refresh().width(), 18, "Refresh",
                    manager.isRequestInFlight() && !availabilityUpdatePending());

            renderAvailability(canvas, width);
            renderSectionAction(canvas, width);
            renderContent(canvas, width, height);
            if (tab == Tab.ZONES) {
                renderZoneActions(canvas);
            }
            renderDropdownMenus(canvas, width, height);
            renderFeedback(canvas, width, height);

            if (warPingPickerOpen) {
                renderWarPingPicker(canvas, width, height);
            } else if (roleEditorOpen) {
                renderRoleEditor(canvas, width, height);
            } else if (teamEditorOpen) {
                renderTeamEditor(canvas, width, height);
            } else if (editingSupportSlot != null) {
                renderSupportEditor(canvas, width, height);
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
        float y = HEADER_HEIGHT;
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
            button(canvas, layout.buttonX(index), y + layout.y(), layout.buttonWidth(index), BUTTON_HEIGHT,
                    labels[index], index == 4 ? ButtonTone.DANGER : ButtonTone.PRIMARY, showBusyControls());
        }
    }

    private void renderSectionAction(UiCanvas canvas, float width) {
        if (tab == Tab.ZONES) return;
        text(canvas, stateLabel(), width - PADDING, contentTop() + 12, 10, stateColor(), UiCanvas.HorizontalAlign.RIGHT);
        if (!manager.canManage()) return;
        primaryButton(canvas, PADDING, contentTop(), 80, BUTTON_HEIGHT,
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
        float top = contentTop() + (tab == Tab.ZONES ? 0 : 28);
        float bottom = height - PADDING;
        canvas.scissor(0, top, width, Math.max(0, bottom - top));
        switch (tab) {
            case ROSTER -> renderRoster(canvas, snapshot, width, top, bottom);
            case TEAMS -> renderTeams(canvas, snapshot, width, top, bottom);
            case ZONES -> renderZones(canvas, snapshot, width, top, bottom);
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

    private void renderWarPingPicker(UiCanvas canvas, float width, float height) {
        WarPlannerSnapshot snapshot = manager.snapshot();
        if (snapshot == null || !manager.canManage()) return;
        float w = Math.min(460, width - PADDING * 2);
        float h = Math.min(390, height - 44);
        float x = (width - w) / 2;
        float y = (height - h) / 2;
        canvas.fillRect(0, 0, width, height, color(BACKGROUND_MODAL_OVERLAY));
        canvas.fillRect(x, y, w, h, plannerBackground(color(BACKGROUND_BODY_OPAQUE)));
        canvas.strokeRect(x, y, w, h, 1, color(CONTROL_BORDER));
        text(canvas, "War ping", x + 14, y + 21, 16, color(ACCENT_PRIMARY), false);
        text(canvas, "Notify an online, Discord-linked member in war chat.",
                x + 14, y + 39, 9, color(TEXT_MUTED), false);
        button(canvas, x + w - 34, y + 9, 24, BUTTON_HEIGHT, "×", true, warPingSending);

        float searchY = y + 52;
        boolean searchHovered = hit(nvgMouseX, nvgMouseY, x + 12, searchY, SequoiaUiStyle.searchWidth(w - 24), TEAM_EDITOR_SEARCH_HEIGHT);
        canvas.fillRect(x + 12, searchY, SequoiaUiStyle.searchWidth(w - 24), TEAM_EDITOR_SEARCH_HEIGHT,
                color(warPingSearchFocused || searchHovered ? CONTROL_INPUT_HOVER : CONTROL_INPUT));
        canvas.strokeRect(x + 12, searchY, SequoiaUiStyle.searchWidth(w - 24), TEAM_EDITOR_SEARCH_HEIGHT, 1,
                color(warPingSearchFocused ? CONTROL_BORDER : CONTROL_INPUT_SECONDARY));
        String searchText = warPingSearchQuery.isEmpty() ? "Search online members…" : warPingSearchQuery;
        if (warPingSearchFocused && (System.currentTimeMillis() / 500) % 2 == 0) searchText += "|";
        canvas.save();
        canvas.scissor(x + 18, searchY, Math.max(0, SequoiaUiStyle.searchWidth(w - 24) - 12), TEAM_EDITOR_SEARCH_HEIGHT);
        text(canvas, searchText, x + 18, searchY + TEAM_EDITOR_SEARCH_HEIGHT / 2, 10,
                color(warPingSearchQuery.isEmpty() ? TEXT_MUTED : TEXT_PRIMARY), false);
        canvas.restore();

        List<RosterMember> candidates = pingCandidates(snapshot, warPingSearchQuery);
        float listTop = searchY + 30;
        float listBottom = y + h - 12;
        canvas.scissor(x + 8, listTop, w - 16, Math.max(0, listBottom - listTop));
        int start = warPingScrollStart(warPingScrollRows, candidates.size());
        float rowY = listTop;
        if (candidates.isEmpty()) {
            text(canvas, warPingSearchQuery.isBlank()
                            ? "No eligible online members."
                            : "No eligible members match this search.",
                    x + 18, rowY + 14, 10, color(TEXT_MUTED), false);
        }
        for (int index = start;
                index < candidates.size() && warPingRowFullyVisible(rowY, listBottom);
                index++, rowY += 30) {
            RosterMember member = candidates.get(index);
            canvas.fillRect(x + 12, rowY + 2, w - 24, 25,
                    plannerBackground(color(BACKGROUND_CONTENT)));
            float actionX = x + w - 72;
            int nameCharacters = availableCharacters(x + 18, actionX - 8, 11, 24);
            text(canvas, truncate(member.displayName(), nameCharacters), x + 18, rowY + 14, 11,
                    color(TEXT_PRIMARY), false);
            button(canvas, actionX, rowY + 4, 54, BUTTON_HEIGHT, "Ping", false,
                    warPingSending || showBusyControls());
        }
        canvas.resetScissor();
    }

    private void renderTeams(UiCanvas canvas, WarPlannerSnapshot snapshot, float width, float top, float bottom) {
        TeamsLayout layout = teamsLayout(width, top, bottom, snapshot.teams().size());
        if (layout.compactAuxiliary()) {
            renderCompactSupportBoard(canvas, snapshot, layout);
        } else {
            renderSupportBoard(canvas, snapshot, layout.sidebarX(), top, layout.sidebarWidth(), bottom);
            if (manager.canManage()) {
                renderUnassignedPool(canvas, snapshot, layout.sidebarX(), top, layout.sidebarWidth(), bottom);
            }
        }
        if (snapshot.teams().isEmpty()) {
            String message = manager.canManage()
                    ? "No war teams yet. Use New team to create one."
                    : "No war teams yet · View only (manager access required).";
            text(canvas, message, layout.cardsX(), layout.cardsTop() + 22, 13, color(TEXT_MUTED), false);
            return;
        }
        RosterMember caller = snapshot.caller();
        List<TeamPlacement> placements = teamPlacements(
                snapshot.teams(), scrollRows, layout, manager.canManage(), caller != null);
        canvas.save();
        canvas.scissor(
                layout.cardsX(),
                layout.cardsTop(),
                layout.cardsWidth(),
                Math.max(0, layout.cardsBottom() - layout.cardsTop()));
        for (TeamPlacement placement : placements) {
            renderTeamCard(canvas, snapshot, caller, placement);
        }
        canvas.restore();
        if (placements.isEmpty()) {
            text(canvas, "Scroll to view teams", layout.cardsX(), layout.cardsTop() + 18,
                    10, color(TEXT_MUTED), false);
        }
        if (memberDrag != null && memberDrag.active()) {
            RosterMember member = rosterMember(snapshot, memberDrag.playerUuid());
            String label = member == null ? memberDrag.playerUuid() : member.displayName();
            canvas.fillRect(nvgMouseX + 8, nvgMouseY - 10, Math.max(74, label.length() * 6 + 16), 20,
                    color(ACCENT_PRIMARY_DARK));
            text(canvas, truncate(label, 22), nvgMouseX + 16, nvgMouseY, 10, color(TEXT_PRIMARY), false);
        }
    }

    private void renderTeamCard(
            UiCanvas canvas, WarPlannerSnapshot snapshot, RosterMember caller, TeamPlacement placement) {
        Team team = placement.team();
        TeamActionLayout actions = placement.actions();
        float x = placement.x();
        float y = placement.y();
        float width = placement.width();
        float visibleHeight = placement.visibleHeight();
        boolean ownTeam = caller != null && caller.teamId() != null && caller.teamId() == team.id();
        boolean dropTarget = memberDrag != null
                && memberDrag.active()
                && hit(nvgMouseX, nvgMouseY, x, y + 1, width, Math.max(0, visibleHeight - 2));
        canvas.fillRect(x, y + 1, width, Math.max(1, visibleHeight - 2),
                plannerBackground(color(dropTarget
                        ? CONTROL_INPUT_HOVER
                        : ownTeam ? ACCENT_PRIMARY_DARK : BACKGROUND_CONTENT)));
        String targetSuffix = team.compositionTargets().configured()
                ? " · " + compositionTargetStatus(snapshot, team)
                : "";
        String title = team.name() + (ownTeam ? " · Your team" : "") + " · " + team.members().size() + "/5"
                + targetSuffix;
        text(canvas, truncate(title, availableCharacters(x + 8, actions.titleRight(), 13, 32)),
                x + 8, y + 13, 13, color(TEXT_PRIMARY), false);

        List<TeamMember> members = team.members().stream()
                .sorted(Comparator.comparingInt(TeamMember::position))
                .toList();
        float memberY = y + actions.memberTop();
        for (int memberIndex = 0; memberIndex < members.size(); memberIndex++) {
            if (memberY + TEAM_MEMBER_ROW_STEP / 2 > y + visibleHeight) break;
            TeamMember member = members.get(memberIndex);
            RosterMember rosterMember = rosterMember(snapshot, member.playerUuid());
            String displayName = member.minecraftUsername() == null ? member.playerUuid() : member.minecraftUsername();
            List<WarCompositionRole> roles = teamMemberRoles(snapshot, member.playerUuid());
            float iconWidth = roles.size() * COMPOSITION_ICON_SIZE
                    + Math.max(0, roles.size() - 1) * COMPOSITION_ICON_GAP;
            float textX = x + 16;
            float rightEdge = memberIndex == 0 ? actions.firstMemberRight() : x + width - 8;
            String memberLabel = truncate(
                    displayName,
                    availableCharacters(textX, rightEdge - iconWidth - 4, 11, 24));
            float labelWidth = UiRenderer.measureText(
                            memberLabel, SeqClient.getFontManager().getSelectedFont(), 11)
                    .width();
            float rolesX = compactRoleX(textX, labelWidth, rightEdge, iconWidth);
            Color presenceColor = rosterMember != null && rosterMember.available()
                    ? color(CONTROL_SUCCESS)
                    : rosterMember != null && rosterMember.online() ? color(TEXT_SECONDARY) : color(TEXT_MUTED);
            canvas.fillCircle(x + 11, memberY, 2, presenceColor);
            text(canvas, memberLabel, textX, memberY, 11, presenceColor, false);
            renderCompositionIcons(canvas, roles, rolesX, memberY - COMPOSITION_ICON_SIZE / 2);
            memberY += TEAM_MEMBER_ROW_STEP;
        }
        if (manager.canManage() && placement.fullyShows(actions.managerY(), BUTTON_HEIGHT)) {
            button(canvas, actions.editX(), y + actions.managerY(), actions.editWidth(), BUTTON_HEIGHT,
                    "Edit", false, showBusyControls());
            boolean confirming = pendingDeleteTeam != null && pendingDeleteTeam.id() == team.id();
            destructiveButton(canvas, actions.deleteX(), y + actions.managerY(), actions.deleteWidth(), BUTTON_HEIGHT,
                    confirming ? "Confirm" : "Delete", confirming, showBusyControls());
        }
        if (caller != null && placement.fullyShows(actions.selfY(), BUTTON_HEIGHT)) {
            primaryButton(canvas, actions.selfX(), y + actions.selfY(), actions.selfWidth(), BUTTON_HEIGHT,
                    teamMembershipActionLabel(snapshot, team),
                    showBusyControls() || !canChangeOwnTeam(snapshot, team));
        }
    }

    private void renderCompactSupportBoard(
            UiCanvas canvas, WarPlannerSnapshot snapshot, TeamsLayout layout) {
        canvas.fillRect(
                layout.supportX(),
                layout.supportY(),
                layout.supportWidth(),
                layout.supportHeight(),
                plannerBackground(color(BACKGROUND_CONTENT)));
        boolean showHeader = layout.supportHeight() > TEAM_SHORT_SUPPORT_HEIGHT;
        if (showHeader) {
            text(canvas, "Shared support", layout.supportX() + 8, layout.supportY() + 10,
                    10, color(ACCENT_PRIMARY), false);
            if (manager.canManage()) {
                int unassigned = unassignedOnlineRoster(snapshot).size();
                text(canvas, unassigned + " unassigned",
                        layout.supportX() + layout.supportWidth() - 8, layout.supportY() + 10,
                        8, color(TEXT_MUTED), UiCanvas.HorizontalAlign.RIGHT);
            }
        }
        List<SupportPlacement> placements = supportPlacements(layout);
        String[] labels = {"Lead", "Eco 1", "Eco 2", "Eco 3"};
        String[] codes = {"LEAD", "ECO_1", "ECO_2", "ECO_3"};
        for (SupportPlacement placement : placements) {
            SupportSlot slot = snapshot.support().slots().stream()
                    .filter(candidate -> codes[placement.index()].equals(candidate.code()))
                    .findFirst()
                    .orElse(null);
            boolean hovered = manager.canManage()
                    && hit(nvgMouseX, nvgMouseY, placement.x(), placement.y(), placement.width(), placement.height());
            canvas.fillRect(placement.x(), placement.y(), placement.width(), placement.height(),
                    color(hovered ? CONTROL_INPUT_HOVER : CONTROL_INPUT));
            String name = slot == null
                    ? "Empty"
                    : slot.minecraftUsername() == null ? slot.playerUuid() : slot.minecraftUsername();
            String value = labels[placement.index()] + " · " + name;
            int maxCharacters = Math.max(2, (int) (placement.width() / 6));
            text(canvas, truncate(value, maxCharacters), placement.x() + 5,
                    placement.y() + placement.height() / 2, 9,
                    color(slot == null ? TEXT_MUTED : TEXT_PRIMARY), false);
        }
    }

    private void renderZones(UiCanvas canvas, WarPlannerSnapshot snapshot, float width, float top, float bottom) {
        zoneActionsBounds = null;
        WarMapLayout layout = warMapLayout(width, top, bottom);
        TerritoryQueue hoveredQueue = renderWarMap(canvas, snapshot, layout);
        renderWarMapSidebar(canvas, snapshot, layout.sidebarX(), layout.mapY(), layout.sidebarWidth(), bottom);
        renderWarMapControls(canvas, layout);
        var controls = warMapControls(layout, manager.canManage());
        if (hoveredQueue != null && !sectionDropdownOpen && !coloringDropdownOpen
                && !controls.panel().contains(nvgMouseX, nvgMouseY) && !controls.fit().contains(nvgMouseX, nvgMouseY)) {
            drawWarQueueTooltip(canvas, hoveredQueue, queueManager.serverNow(), layout);
        }
    }

    private TerritoryQueue renderWarMap(UiCanvas canvas, WarPlannerSnapshot snapshot, WarMapLayout layout) {
        float x = layout.mapX();
        float y = layout.mapY();
        float width = layout.mapWidth();
        float height = layout.mapHeight();
        canvas.fillRect(x, y, width, height, color(BACKGROUND_BODY_OPAQUE));
        hoveredWarMapTerritory = null;
        List<GuildTerritory> allMapTerritories = territoryIndex.territories();
        boolean locked = manager.canManage() && territoriesLocked();
        List<Zone> displayedZones = visibleZones(
                snapshot.zones(), hiddenZoneIds, hiddenZoneCategoryIds);
        Set<String> shownZoneTerritories = shownZoneTerritoryNames(displayedZones);
        List<GuildTerritory> coreTerritories = visibleMapTerritories(
                allMapTerritories, displayedZones, locked);
        if (coreTerritories.isEmpty()) {
            text(canvas, "Map unavailable", x + width / 2, y + height / 2, 9, color(TEXT_MUTED), true);
            return null;
        }
        Map<String, GuildTerritory> byName = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        allMapTerritories.forEach(territory -> byName.put(territory.name(), territory));
        List<WarQueueMapMarker> queueMarkers = warQueueMapMarkers(
                displayedWarQueues(), shownZoneTerritories, byName);
        Map<String, WarPlannerSnapshot.TerritoryDetails> details = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        snapshot.territoryDetails().forEach(detail -> details.put(detail.name(), detail));
        List<GuildTerritory> contextTerritories = locked
                ? oneHopContextTerritories(allMapTerritories, coreTerritories, details)
                : List.of();
        ArrayList<GuildTerritory> displayedTerritories = new ArrayList<>(coreTerritories);
        displayedTerritories.addAll(contextTerritories);
        MapViewport viewport = warMapViewport(layout, displayedTerritories, locked);
        Set<String> displayedNames = displayedTerritories.stream()
                .map(GuildTerritory::name)
                .map(name -> name.toLowerCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toSet());
        hoveredWarMapTerritory = territoryAt(
                territoryIndex, viewport, displayedNames, nvgMouseX, nvgMouseY);
        var mapControls = warMapControls(layout, manager.canManage());
        if (sectionDropdownOpen || coloringDropdownOpen || mapControls.panel().contains(nvgMouseX, nvgMouseY)
                || mapControls.fit().contains(nvgMouseX, nvgMouseY)) hoveredWarMapTerritory = null;
        MapBounds coordinateBounds = mapImageBounds();
        float scale = (float) viewport.pixelsPerBlock();
        float offsetX = viewport.worldToScreenX(coordinateBounds.minX());
        float offsetY = viewport.worldToScreenZ(coordinateBounds.minZ());
        mapBackground.render(canvas, viewport);
        canvas.scissor(x, y, width, height);
        boolean resourceColors = resourceColorsEnabled();
        long overlayTime = monotonicMillis();
        if (resourceColors) {
            drawPreviewResources(canvas, coreTerritories, details, coordinateBounds, offsetX, offsetY, scale,
                    warQueuePulseAlpha(overlayTime));
        } else {
            drawWarQueuePulses(canvas, queueMarkers, coordinateBounds, offsetX, offsetY, scale, overlayTime);
        }
        Set<String> emphasizedTerritories = new java.util.HashSet<>();
        if (selectedWarTerritory != null) emphasizedTerritories.add(selectedWarTerritory.toLowerCase(Locale.ROOT));
        if (hoveredWarMapTerritory != null) emphasizedTerritories.add(hoveredWarMapTerritory.name().toLowerCase(Locale.ROOT));
        drawPreviewConnections(
                canvas,
                coreTerritories,
                byName,
                details,
                displayedNames,
                coordinateBounds,
                offsetX,
                offsetY,
                scale,
                emphasizedTerritories,
                resourceColors);
        if (hoveredWarMapTerritory != null) {
            drawPreviewFill(
                    canvas,
                    hoveredWarMapTerritory,
                    coordinateBounds,
                    offsetX,
                    offsetY,
                    scale,
                    color(MAP_TERRITORY));
        }
        Color mapColor = color(TEXT_MUTED);
        drawPreviewOutlines(
                canvas,
                coreTerritories,
                coordinateBounds,
                offsetX,
                offsetY,
                scale,
                mapColor,
                .55f,
                0,
                resourceColors);
        if (!contextTerritories.isEmpty()) {
            drawPreviewOutlines(
                    canvas,
                    contextTerritories,
                    coordinateBounds,
                    offsetX,
                    offsetY,
                    scale,
                    mapColor,
                    .75f,
                    0,
                    resourceColors);
        }
        for (Zone zone : displayedZones) {
            Color zoneColor = parseColor(zone.color(), color(ACCENT_PRIMARY));
            drawPreviewOutlines(
                    canvas,
                    resolveTerritories(zone.territories(), byName),
                    coordinateBounds,
                    offsetX,
                    offsetY,
                    scale,
                    zoneColor,
                    resourceColors ? 1.8f : 1.2f,
                    resourceColors ? 1 : 0,
                    resourceColors);
        }
        for (String name : emphasizedTerritories) {
            GuildTerritory territory = byName.get(name);
            if (territory != null && displayedNames.contains(name)) {
                drawPreviewOutlines(canvas, List.of(territory), coordinateBounds, offsetX, offsetY, scale,
                        color(MAP_SELECTED_TERRITORY), 2, 0);
            }
        }
        GuildTerritory hqTerritory = snapshot.hqTerritory() == null ? null : byName.get(snapshot.hqTerritory());
        if (hqTerritory != null && displayedNames.contains(hqTerritory.name().toLowerCase(Locale.ROOT))) {
            Color hqColor = color(MAP_SELECTED_TERRITORY);
            drawPreviewOutlines(
                    canvas,
                    List.of(hqTerritory),
                    coordinateBounds,
                    offsetX,
                    offsetY,
                    scale,
                    hqColor,
                    2.6f,
                    2);
        }
        TerritoryQueue hoveredQueue = null;
        if (!queueMarkers.isEmpty()) {
            drawWarQueueLabels(
                    canvas,
                    queueMarkers,
                    coordinateBounds,
                    offsetX,
                    offsetY,
                    scale);
            if (hoveredWarMapTerritory != null) {
                hoveredQueue = warQueueForTerritory(queueMarkers, hoveredWarMapTerritory.name());
            }
        }
        if (hoveredWarMapTerritory != null && hoveredQueue == null) {
            drawTerritoryName(canvas, hoveredWarMapTerritory, coordinateBounds, offsetX, offsetY, scale, layout);
        }
        if (hqTerritory != null && displayedNames.contains(hqTerritory.name().toLowerCase(Locale.ROOT))) {
            drawHqIcon(canvas, hqTerritory, coordinateBounds, offsetX, offsetY, scale);
        }
        canvas.resetScissor();
        if (warMapPlayersEnabled()) telemetryPlayerOverlay.render(canvas, viewport, territoryIndex);
        return hoveredQueue;
    }

    private void renderWarMapControls(UiCanvas canvas, WarMapLayout layout) {
        WarMapControls controls = warMapControls(layout, manager.canManage());
        var center = controls.fit();
        primaryButton(canvas, center.x(), center.y(), center.width(), center.height(), "Center", false);
        if (layout.mapWidth() > 180) {
            text(canvas, stateLabel(), layout.mapX() + layout.mapWidth() - 8, center.y() + 11,
                    10, stateColor(), UiCanvas.HorizontalAlign.RIGHT);
        }
        var panel = controls.panel();
        canvas.fillRect(panel.x(), panel.y(), panel.width(), panel.height(), color(BACKGROUND_CONTENT));
        if (manager.canManage()) renderMapSwitch(canvas, controls.lock(), "Lock territories", "Lock", territoriesLocked());
        renderMapSwitch(canvas, controls.queues(), "Only show personal queues", "Mine", onlyMyWarQueuesEnabled());
        renderMapSwitch(canvas, controls.players(), "Display players", "Players", warMapPlayersEnabled());
        var slider = prepareOpacitySlider(controls);
        if (slider != null) slider.render(canvas, nvgMouseX, nvgMouseY);
        renderDropdown(canvas, controls.coloring(), resourceColorsEnabled() ? "Resources" : "War queues", coloringDropdownOpen);
    }

    private void renderMapSwitch(UiCanvas canvas, WarMapButtonBounds row, String label, String shortLabel, boolean on) {
        float switchWidth = row.width() < 150 ? 26 : 36;
        float switchX = row.x() + row.width() - switchWidth - 6;
        text(canvas, row.width() < 210 ? shortLabel : label, switchX - 6, row.y() + row.height() / 2,
                row.width() < 150 ? 8 : 10, color(TEXT_PRIMARY), UiCanvas.HorizontalAlign.RIGHT);
        canvas.fillRect(switchX, row.y() + 3, switchWidth, 18, color(on ? ACCENT_PRIMARY : ACCENT_SECONDARY));
        canvas.fillRect(switchX + (on ? switchWidth - 16 : 2), row.y() + 5, 14, 14, color(TEXT_PRIMARY));
    }

    private SliderWidget prepareOpacitySlider(WarMapControls controls) {
        if (opacitySlider == null && SeqClient.getWarPlannerBackgroundOpacitySetting() != null) {
            opacitySlider = new SliderWidget(SeqClient.getWarPlannerBackgroundOpacitySetting(), true) {
                @Override protected String getDisplayName() { return "Opacity %"; }
            };
        }
        if (opacitySlider != null) {
            var bounds = controls.opacity();
            opacitySlider.setPosition(bounds.x(), bounds.y(), bounds.width(), bounds.height());
        }
        return opacitySlider;
    }

    private void renderDropdown(UiCanvas canvas, WarMapButtonBounds bounds, String label, boolean open) {
        canvas.fillRect(bounds.x(), bounds.y(), bounds.width(), bounds.height(), color(CONTROL_INPUT));
        text(canvas, label, bounds.x() + 8, bounds.y() + bounds.height() / 2, 10, color(TEXT_PRIMARY), false);
        float x = bounds.x() + bounds.width() - 10, y = bounds.y() + bounds.height() / 2;
        canvas.strokeLine(x - 3, y + (open ? 2 : -2), x, y + (open ? -2 : 2), 1.5f, color(TEXT_SECONDARY));
        canvas.strokeLine(x, y + (open ? -2 : 2), x + 3, y + (open ? 2 : -2), 1.5f, color(TEXT_SECONDARY));
    }

    private void renderDropdownMenus(UiCanvas canvas, float width, float height) {
        if (sectionDropdownOpen) renderDropdownOptions(canvas, headerControls(width).section(),
                java.util.Arrays.stream(Tab.values()).map(candidate -> candidate.label).toList(), tab.ordinal());
        if (coloringDropdownOpen && tab == Tab.ZONES) renderDropdownOptions(canvas,
                warMapControls(warMapLayout(width, contentTop(), height - PADDING), manager.canManage()).coloring(),
                List.of("War queues", "Resources"), resourceColorsEnabled() ? 1 : 0);
    }

    private void renderDropdownOptions(UiCanvas canvas, WarMapButtonBounds trigger, List<String> labels, int selected) {
        for (int index = 0; index < labels.size(); index++) {
            var row = dropdownOption(trigger, index);
            canvas.fillRect(row.x(), row.y(), row.width(), row.height(), color(index == selected ? ACCENT_PRIMARY_DARK
                    : row.contains(nvgMouseX, nvgMouseY) ? CONTROL_INPUT_HOVER : BACKGROUND_POPUP));
            text(canvas, labels.get(index), row.x() + 8, row.y() + row.height() / 2, 10, color(TEXT_PRIMARY), false);
        }
    }


    private static void drawHqIcon(
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

    private List<GuildTerritory> warMapDisplayedTerritories(
            WarPlannerSnapshot snapshot, boolean locked) {
        List<GuildTerritory> allTerritories = territoryIndex.territories();
        List<GuildTerritory> coreTerritories = visibleMapTerritories(
                allTerritories,
                visibleZones(snapshot.zones(), hiddenZoneIds, hiddenZoneCategoryIds),
                locked);
        if (!locked) return coreTerritories;

        Map<String, WarPlannerSnapshot.TerritoryDetails> details =
                new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        snapshot.territoryDetails().forEach(detail -> details.put(detail.name(), detail));
        ArrayList<GuildTerritory> displayedTerritories = new ArrayList<>(coreTerritories);
        displayedTerritories.addAll(oneHopContextTerritories(allTerritories, coreTerritories, details));
        return List.copyOf(displayedTerritories);
    }

    private MapViewport warMapViewport(
            WarMapLayout layout, List<GuildTerritory> displayedTerritories, boolean locked) {
        MapBounds fitBounds = warMapFitBounds(displayedTerritories, locked);
        if (shouldRefitWarMap(
                warMapFitted,
                fittedWarMapWidth,
                fittedWarMapHeight,
                fittedWarMapLocked,
                layout,
                locked)) {
            resetWarMapViewport(layout, fitBounds, locked);
        }
        return currentWarMapViewport(layout);
    }

    private MapViewport currentWarMapViewport(WarMapLayout layout) {
        return new MapViewport(
                warMapCenterX,
                warMapCenterZ,
                warMapPixelsPerBlock,
                layout.mapX(),
                layout.mapY(),
                layout.mapWidth(),
                layout.mapHeight());
    }

    private void resetWarMapViewport(WarMapLayout layout, MapBounds bounds, boolean locked) {
        MapViewport fitted = fittedWarMapViewport(bounds, layout);
        applyWarMapViewport(fitted);
        pendingWarQueueClick = null;
        draggingWarMap = false;
        fittedWarMapWidth = layout.mapWidth();
        fittedWarMapHeight = layout.mapHeight();
        fittedWarMapLocked = locked;
        warMapFitted = true;
    }

    private void applyWarMapViewport(MapViewport viewport) {
        warMapCenterX = viewport.centerX();
        warMapCenterZ = viewport.centerZ();
        warMapPixelsPerBlock = viewport.pixelsPerBlock();
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

    private void renderWarMapSidebar(
            UiCanvas canvas, WarPlannerSnapshot snapshot, float x, float top, float width, float bottom) {
        canvas.fillRect(x, top, width, bottom - top, plannerBackground(color(BACKGROUND_CONTENT)));
        if (manager.canManage()) {
            float actionWidth = (width - 18) / 2;
            primaryButton(canvas, x + 6, top + 37, actionWidth, 20, "+ Category", showBusyControls());
            primaryButton(canvas, x + 12 + actionWidth, top + 37, actionWidth, 20, "+ Zone", showBusyControls());
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
                    showBusyControls());
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
            button(canvas, x + width - 36, rowY + 3, 28, 20, "...", false, showBusyControls());
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
        button(canvas, menu.x() + 2, menu.y() + 2, menu.width() - 4, 22, "Edit zone", false, showBusyControls());
        boolean confirming = pendingDeleteZone != null && pendingDeleteZone.id() == zoneActionsId;
        destructiveButton(canvas, menu.x() + 2, menu.y() + 28, menu.width() - 4, 22,
                confirming ? "Confirm delete" : "Delete zone", confirming, showBusyControls());
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
            SeqClient.mc.setScreen(new WarTerritoryPickerScreen(this, zone));
        } else if (pendingDeleteZone != null && pendingDeleteZone.id() == zone.id()) {
            showResult(manager.deleteZone(zone.id(), pendingDeleteZone.version()));
            pendingDeleteZone = null;
            zoneActionsId = null;
        } else {
            pendingDeleteZone = new PendingDelete(zone.id(), zone.version());
        }
        return true;
    }

    private static List<GuildTerritory> resolveTerritories(
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

    static Set<String> shownZoneTerritoryNames(List<Zone> displayedZones) {
        if (displayedZones == null || displayedZones.isEmpty()) return Set.of();
        return displayedZones.stream()
                .filter(java.util.Objects::nonNull)
                .flatMap(zone -> zone.territories().stream())
                .filter(name -> name != null && !name.isBlank())
                .map(name -> name.toLowerCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    static boolean isWarQueueDoubleClick(
            PendingWarQueueClick previous,
            long queueId,
            String territory,
            float mouseX,
            float mouseY,
            long clickedAtMillis) {
        if (previous == null || territory == null) return false;
        long elapsed = clickedAtMillis - previous.clickedAtMillis();
        return previous.queueId() == queueId
                && territory.equalsIgnoreCase(previous.territory())
                && elapsed >= 0
                && elapsed <= WAR_QUEUE_DOUBLE_CLICK_MILLIS
                && !warQueueClickMoved(previous, mouseX, mouseY);
    }

    static boolean warQueueClickMoved(PendingWarQueueClick click, float mouseX, float mouseY) {
        return click != null
                && Math.hypot(mouseX - click.mouseX(), mouseY - click.mouseY())
                        >= WAR_QUEUE_DOUBLE_CLICK_MOVE_TOLERANCE;
    }

    private static long monotonicMillis() {
        return System.nanoTime() / 1_000_000L;
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

    private static void drawPreviewResources(
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

    private static void drawPreviewFill(
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

    private static void drawWarQueuePulses(
            UiCanvas canvas,
            List<WarQueueMapMarker> markers,
            MapBounds fitted,
            float offsetX,
            float offsetY,
            float scale,
            long elapsedMillis) {
        for (WarQueueMapMarker marker : markers) {
            drawPreviewFill(
                    canvas,
                    marker.territory(),
                    fitted,
                    offsetX,
                    offsetY,
                    scale,
                    warQueuePulseColor(marker.queue(), elapsedMillis));
        }
    }

    static List<WarQueueMapMarker> warQueueMapMarkers(
            List<TerritoryQueue> queues,
            Set<String> shownZoneTerritories,
            Map<String, GuildTerritory> territoriesByName) {
        if (queues == null
                || queues.isEmpty()
                || shownZoneTerritories == null
                || shownZoneTerritories.isEmpty()
                || territoriesByName == null
                || territoriesByName.isEmpty()) {
            return List.of();
        }
        Set<String> shownTerritoryKeys = shownZoneTerritories.stream()
                .filter(java.util.Objects::nonNull)
                .map(String::trim)
                .filter(name -> !name.isEmpty())
                .map(name -> name.toLowerCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toSet());
        Map<String, GuildTerritory> territoriesByKey = new java.util.HashMap<>();
        territoriesByName.values().stream()
                .filter(java.util.Objects::nonNull)
                .forEach(territory -> territoriesByKey.putIfAbsent(
                        territory.name().toLowerCase(Locale.ROOT), territory));
        Set<String> markedTerritories = new java.util.HashSet<>();
        ArrayList<WarQueueMapMarker> markers = new ArrayList<>();
        for (TerritoryQueue queue : queues) {
            if (queue == null || queue.territory() == null || queue.territory().isBlank()) continue;
            String territoryKey = queue.territory().trim().toLowerCase(Locale.ROOT);
            GuildTerritory territory = territoriesByKey.get(territoryKey);
            if (!shownTerritoryKeys.contains(territoryKey)
                    || territory == null
                    || !markedTerritories.add(territoryKey)) {
                continue;
            }
            markers.add(new WarQueueMapMarker(queue, territory));
        }
        return List.copyOf(markers);
    }

    static TerritoryQueue warQueueForTerritory(List<WarQueueMapMarker> markers, String territoryName) {
        if (markers == null || territoryName == null || territoryName.isBlank()) return null;
        return markers.stream()
                .filter(marker -> marker.territory().name().equalsIgnoreCase(territoryName.trim()))
                .map(WarQueueMapMarker::queue)
                .findFirst()
                .orElse(null);
    }

    static String warQueuePulseDefense(TerritoryQueue queue) {
        if (queue == null) return null;
        WarTerritoryQueueHudRenderer.DefenseRatings defenses =
                WarTerritoryQueueHudRenderer.defenseRatings(
                        queue.queuedDefenseRating(), queue.reportedDefenseRating());
        return defenses.queued() == null ? defenses.reported() : defenses.queued();
    }

    static int warQueuePulseAlpha(long elapsedMillis) {
        long phaseMillis = Math.floorMod(elapsedMillis, WAR_QUEUE_PULSE_PERIOD_MILLIS);
        double phase = phaseMillis / (double) WAR_QUEUE_PULSE_PERIOD_MILLIS;
        double pulse = (1d - Math.cos(phase * Math.PI * 2d)) / 2d;
        return WAR_QUEUE_PULSE_MIN_ALPHA
                + (int) Math.round((WAR_QUEUE_PULSE_MAX_ALPHA - WAR_QUEUE_PULSE_MIN_ALPHA) * pulse);
    }

    static Color warQueuePulseColor(TerritoryQueue queue, long elapsedMillis) {
        Color defenseColor = WarTerritoryQueueHudRenderer.vanillaDefenseColor(warQueuePulseDefense(queue));
        if (defenseColor == null) {
            // Unknown defenses use the configured fallback unchanged; only vanilla palette markers pulse.
            return color(TEXT_SECONDARY);
        }
        return new Color(
                defenseColor.getRed(),
                defenseColor.getGreen(),
                defenseColor.getBlue(),
                warQueuePulseAlpha(elapsedMillis));
    }

    private static void drawTerritoryName(
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

    private static void drawWarQueueLabels(
            UiCanvas canvas,
            List<WarQueueMapMarker> markers,
            MapBounds fitted,
            float offsetX,
            float offsetY,
            float scale) {
        for (WarQueueMapMarker marker : markers) {
            drawWarQueueLabel(
                    canvas,
                    marker.territory(),
                    warQueueMapUsername(marker.queue()),
                    fitted,
                    offsetX,
                    offsetY,
                    scale);
        }
    }

    private static void drawWarQueueLabel(
            UiCanvas canvas,
            GuildTerritory territory,
            String displayName,
            MapBounds fitted,
            float offsetX,
            float offsetY,
            float scale) {
        if (displayName == null || displayName.isBlank()) return;
        WarQueueLabelBounds available = warQueueLabelBounds(
                territory, fitted, offsetX, offsetY, scale, Float.MAX_VALUE, Float.MAX_VALUE);
        if (available == null) return;

        float fontSize = Math.min(WAR_QUEUE_MAP_MAX_FONT_SIZE, available.height() - 4);
        if (fontSize < WAR_QUEUE_MAP_MIN_FONT_SIZE) return;
        String font = SeqClient.getFontManager().getSelectedFont();
        float maxTextWidth = available.width() - 6;
        while (fontSize > WAR_QUEUE_MAP_MIN_FONT_SIZE
                && UiRenderer.measureText(displayName, font, fontSize).width() > maxTextWidth) {
            fontSize = Math.max(WAR_QUEUE_MAP_MIN_FONT_SIZE, fontSize - .5f);
        }
        float fittedFontSize = fontSize;
        String label = fitWarQueueText(
                displayName,
                maxTextWidth,
                value -> UiRenderer.measureText(value, font, fittedFontSize).width());
        if (label.isBlank()) return;

        float textWidth = UiRenderer.measureText(label, font, fontSize).width();
        WarQueueLabelBounds labelBounds = warQueueLabelBounds(
                territory, fitted, offsetX, offsetY, scale, textWidth + 6, fontSize + 4);
        if (labelBounds == null) return;
        canvas.fillRect(
                labelBounds.x(),
                labelBounds.y(),
                labelBounds.width(),
                labelBounds.height(),
                color(BACKGROUND_POPUP));
        text(
                canvas,
                label,
                labelBounds.x() + labelBounds.width() / 2,
                labelBounds.y() + labelBounds.height() / 2,
                fontSize,
                color(TEXT_PRIMARY),
                true);
    }

    static String warQueueMapUsername(TerritoryQueue queue) {
        if (queue == null || queue.minecraftUsername() == null || queue.minecraftUsername().isBlank()) {
            return "Unknown";
        }
        return queue.minecraftUsername();
    }

    static String fitWarQueueText(
            String value, float maxWidth, ToDoubleFunction<String> widthMeasurer) {
        if (value == null || value.isBlank() || maxWidth <= 0 || widthMeasurer == null) return "";
        String normalized = value.trim();
        if (widthMeasurer.applyAsDouble(normalized) <= maxWidth) return normalized;
        String ellipsis = "…";
        if (widthMeasurer.applyAsDouble(ellipsis) > maxWidth) return "";
        int low = 0;
        int high = normalized.length();
        while (low < high) {
            int midpoint = (low + high + 1) / 2;
            String candidate = normalized.substring(0, midpoint).stripTrailing() + ellipsis;
            if (widthMeasurer.applyAsDouble(candidate) <= maxWidth) {
                low = midpoint;
            } else {
                high = midpoint - 1;
            }
        }
        return normalized.substring(0, low).stripTrailing() + ellipsis;
    }

    static List<String> wrapWarQueueText(
            String value, float maxWidth, ToDoubleFunction<String> widthMeasurer) {
        if (value == null || value.isBlank() || maxWidth <= 0 || widthMeasurer == null) return List.of();
        ArrayList<String> lines = new ArrayList<>();
        String remaining = value.trim();
        while (!remaining.isEmpty()) {
            if (widthMeasurer.applyAsDouble(remaining) <= maxWidth) {
                lines.add(remaining);
                break;
            }
            int low = 0;
            int high = remaining.length();
            while (low < high) {
                int midpoint = (low + high + 1) / 2;
                if (widthMeasurer.applyAsDouble(remaining.substring(0, midpoint)) <= maxWidth) {
                    low = midpoint;
                } else {
                    high = midpoint - 1;
                }
            }
            if (low == 0) {
                lines.add(remaining.substring(0, 1));
                remaining = remaining.substring(1).stripLeading();
                continue;
            }
            int whitespace = -1;
            for (int index = low - 1; index >= 0; index--) {
                if (Character.isWhitespace(remaining.charAt(index))) {
                    whitespace = index;
                    break;
                }
            }
            int split = whitespace > 0 ? whitespace : low;
            lines.add(remaining.substring(0, split).stripTrailing());
            remaining = remaining.substring(split).stripLeading();
        }
        return List.copyOf(lines);
    }

    static WarQueueLabelBounds warQueueLabelBounds(
            GuildTerritory territory,
            MapBounds fitted,
            float offsetX,
            float offsetY,
            float scale,
            float desiredWidth,
            float desiredHeight) {
        if (territory == null || fitted == null || scale <= 0 || desiredWidth <= 0 || desiredHeight <= 0) {
            return null;
        }
        MapBounds bounds = territory.bounds();
        float territoryX = previewX(bounds.minX(), fitted, offsetX, scale);
        float territoryY = previewY(bounds.minZ(), fitted, offsetY, scale);
        float territoryWidth = (float) ((bounds.maxX() - bounds.minX()) * scale);
        float territoryHeight = (float) ((bounds.maxZ() - bounds.minZ()) * scale);
        float availableWidth = territoryWidth - 4;
        float availableHeight = territoryHeight - 4;
        if (availableWidth <= 0 || availableHeight <= 0) return null;
        float width = Math.min(desiredWidth, availableWidth);
        float height = Math.min(desiredHeight, availableHeight);
        return new WarQueueLabelBounds(
                territoryX + (territoryWidth - width) / 2,
                territoryY + (territoryHeight - height) / 2,
                width,
                height);
    }

    static List<String> warQueueTooltipLines(TerritoryQueue queue, Instant now) {
        if (queue == null) return List.of();
        String defenses = WarTerritoryQueueHudRenderer.formatDefenses(
                queue.queuedDefenseRating(), queue.reportedDefenseRating());
        if (defenses.startsWith("(") && defenses.endsWith(")")) {
            defenses = defenses.substring(1, defenses.length() - 1);
        }
        String age = WarTerritoryQueueHudRenderer.formatAge(queue.queuedAt(), now);
        String participantNames = queue.participants().stream()
                .map(participant -> participant.minecraftUsername())
                .filter(name -> name != null && !name.isBlank())
                .collect(java.util.stream.Collectors.joining(", "));
        ArrayList<String> lines = new ArrayList<>();
        lines.add(queue.displayName());
        lines.add(queue.territory() + (defenses.isBlank() ? " · Defense unknown" : " · Defense " + defenses));
        lines.add((age.isBlank() ? "Queued time unknown" : "Queued " + age)
                + " · "
                + WarTerritoryQueueHudRenderer.formatCountdown(queue.expiresAt(), now)
                + " remaining");
        lines.add("Party " + WarTerritoryQueueHudRenderer.participantLabel(queue.participantCount())
                + (participantNames.isBlank() ? "" : " · " + participantNames));
        return List.copyOf(lines);
    }

    static String warQueueActionHint(TerritoryQueue queue, String playerUuid) {
        if (queue == null) return "";
        if (samePlayer(playerUuid, queue.queuedBy())) {
            return "You own this queue · owner remains joined";
        }
        if (queue.hasParticipant(playerUuid)) {
            return "Double-click to leave";
        }
        return queue.full() ? "Queue full" : "Double-click to join";
    }

    private void drawWarQueueTooltip(
            UiCanvas canvas, TerritoryQueue queue, Instant now, WarMapLayout layout) {
        ArrayList<String> lines = new ArrayList<>(warQueueTooltipLines(queue, now));
        if (lines.isEmpty()) return;
        String actionHint = warQueueActionHint(queue, queueManager.localPlayerUuid());
        if (!actionHint.isBlank()) lines.add(actionHint);
        String font = SeqClient.getFontManager().getSelectedFont();
        float contentLeft = PADDING;
        float contentRight = layout.sidebarX() + layout.sidebarWidth();
        float maximumWidth = Math.max(1, contentRight - contentLeft);
        float measuredWidth = 0;
        for (String line : lines) {
            measuredWidth = Math.max(
                    measuredWidth,
                    UiRenderer.measureText(line, font, WAR_QUEUE_TOOLTIP_TEXT_SIZE).width());
        }
        float tooltipWidth = Math.min(maximumWidth, Math.max(170, measuredWidth + 16));
        ArrayList<WarQueueTooltipLine> wrappedLines = new ArrayList<>();
        for (int index = 0; index < lines.size(); index++) {
            for (String line : wrapWarQueueText(
                    lines.get(index),
                    tooltipWidth - 16,
                    value -> UiRenderer.measureText(value, font, WAR_QUEUE_TOOLTIP_TEXT_SIZE).width())) {
                wrappedLines.add(new WarQueueTooltipLine(line, index));
            }
        }
        float tooltipHeight = 10 + wrappedLines.size() * WAR_QUEUE_TOOLTIP_LINE_HEIGHT;
        float tooltipX = tooltipCoordinate(nvgMouseX, tooltipWidth, contentLeft, contentRight);
        float tooltipY = tooltipCoordinate(
                nvgMouseY,
                tooltipHeight,
                layout.mapY() + 4,
                layout.mapY() + layout.mapHeight() - 4);
        canvas.fillRect(
                tooltipX,
                tooltipY,
                tooltipWidth,
                tooltipHeight,
                plannerBackground(color(BACKGROUND_POPUP)));
        canvas.strokeRect(tooltipX, tooltipY, tooltipWidth, tooltipHeight, 1, color(CONTROL_BORDER));
        for (int index = 0; index < wrappedLines.size(); index++) {
            WarQueueTooltipLine line = wrappedLines.get(index);
            text(
                    canvas,
                    line.text(),
                    tooltipX + 8,
                    tooltipY + 8 + index * WAR_QUEUE_TOOLTIP_LINE_HEIGHT,
                    WAR_QUEUE_TOOLTIP_TEXT_SIZE,
                    color(line.detailIndex() == 0
                            ? TEXT_PRIMARY
                            : line.detailIndex() == 1 ? TEXT_SECONDARY : TEXT_MUTED),
                    false);
        }
    }

    static float tooltipCoordinate(float pointer, float size, float minimum, float maximum) {
        if (maximum <= minimum || size >= maximum - minimum) return minimum;
        float afterPointer = pointer + 12;
        if (afterPointer + size <= maximum) return Math.max(minimum, afterPointer);
        return Math.max(minimum, Math.min(pointer - size - 12, maximum - size));
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

    private static void drawPreviewOutlines(
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

    private static void drawPreviewOutlines(
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

    private static void drawPreviewConnections(
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

    private void renderSupportBoard(
            UiCanvas canvas, WarPlannerSnapshot snapshot, float x, float top, float panelWidth, float bottom) {
        canvas.fillRect(
                x,
                top + 2,
                panelWidth,
                Math.min(bottom - top - 4, SUPPORT_PANEL_HEIGHT),
                plannerBackground(color(BACKGROUND_CONTENT)));
        text(canvas, "Shared support", x + 10, top + 17, 13, color(ACCENT_PRIMARY), false);
        text(canvas, manager.canManage()
                        ? "Click a slot · may join any team"
                        : "Lead and economy assignments",
                x + 10, top + 29, 9, color(TEXT_MUTED), false);
        String[] codes = {"LEAD", "ECO_1", "ECO_2", "ECO_3"};
        for (int index = 0; index < codes.length; index++) {
            float y = top + SUPPORT_ROWS_TOP + index * SUPPORT_ROW_STEP;
            String code = codes[index];
            SupportSlot slot = snapshot.support().slots().stream()
                    .filter(candidate -> code.equals(candidate.code()))
                    .findFirst()
                    .orElse(null);
            boolean hovered = manager.canManage()
                    && hit(nvgMouseX, nvgMouseY, x + 7, y, panelWidth - 14, SUPPORT_ROW_HEIGHT);
            canvas.fillRect(x + 7, y, panelWidth - 14, SUPPORT_ROW_HEIGHT,
                    color(hovered ? CONTROL_INPUT_HOVER : CONTROL_INPUT));
            text(canvas, index == 0 ? "Lead" : "Eco " + index, x + 13, y + 11, 10, color(TEXT_MUTED), false);
            String name = slot == null ? "Empty" : slot.minecraftUsername() == null ? slot.playerUuid() : slot.minecraftUsername();
            text(canvas, truncate(name, 16), x + 60, y + 11, 10,
                    color(slot == null ? TEXT_MUTED : TEXT_PRIMARY), false);
        }
    }

    private void renderUnassignedPool(
            UiCanvas canvas, WarPlannerSnapshot snapshot, float x, float top, float panelWidth, float bottom) {
        float poolY = top + UNASSIGNED_POOL_TOP;
        if (poolY + 34 >= bottom) return;
        boolean dropTarget = memberDrag != null
                && memberDrag.active()
                && hit(nvgMouseX, nvgMouseY, x, poolY, panelWidth, bottom - poolY);
        canvas.fillRect(x, poolY, panelWidth, bottom - poolY,
                plannerBackground(color(dropTarget ? CONTROL_INPUT_HOVER : BACKGROUND_CONTENT)));
        text(canvas, "Unassigned", x + 10, poolY + 16, 12, color(ACCENT_PRIMARY), false);
        text(canvas, "Drag online players into a team", x + 10, poolY + 29, 9, color(TEXT_MUTED), false);
        List<RosterMember> members = unassignedOnlineRoster(snapshot);
        float rowsTop = poolY + 36;
        int visibleRows = Math.max(0, (int) ((bottom - rowsTop - 4) / UNASSIGNED_ROW_HEIGHT));
        int start = Math.min(unassignedScrollRows, Math.max(0, members.size() - 1));
        for (int index = start; index < members.size() && index - start < visibleRows; index++) {
            float rowY = rowsTop + (index - start) * UNASSIGNED_ROW_HEIGHT;
            RosterMember member = members.get(index);
            boolean hovered = memberDrag == null && hit(nvgMouseX, nvgMouseY, x + 6, rowY, panelWidth - 12, UNASSIGNED_ROW_HEIGHT - 2);
            canvas.fillRect(x + 6, rowY, panelWidth - 12, UNASSIGNED_ROW_HEIGHT - 2,
                    color(hovered ? CONTROL_INPUT_HOVER : CONTROL_INPUT));
            text(canvas, truncate(member.displayName(), 19), x + 12, rowY + 11, 11, color(TEXT_SECONDARY), false);
            renderCompositionIcons(canvas, member.compositionRoles(), x + panelWidth - 54, rowY + 5);
        }
        if (members.size() > visibleRows && visibleRows > 0) {
            text(canvas, (start + 1) + "–" + Math.min(members.size(), start + visibleRows) + "/" + members.size(),
                    x + panelWidth - 34, poolY + 16, 8, color(TEXT_MUTED), true);
        }
    }

    private void renderSupportEditor(UiCanvas canvas, float width, float height) {
        WarPlannerSnapshot snapshot = manager.snapshot();
        if (snapshot == null || editingSupportSlot == null) return;
        String label = editingSupportSlot == 0 ? "Lead" : "Eco " + editingSupportSlot;
        float w = Math.min(430, width - PADDING * 2);
        float h = Math.min(390, height - 44);
        float x = (width - w) / 2;
        float y = (height - h) / 2;
        canvas.fillRect(0, 0, width, height, color(BACKGROUND_MODAL_OVERLAY));
        canvas.fillRect(x, y, w, h, plannerBackground(color(BACKGROUND_BODY_OPAQUE)));
        canvas.strokeRect(x, y, w, h, 1, color(CONTROL_BORDER));
        text(canvas, "Assign shared " + label, x + 14, y + 21, 16, color(ACCENT_PRIMARY), false);
        text(canvas, "Support members can also belong to any party.", x + 14, y + 39, 10, color(TEXT_MUTED), false);
        button(canvas, x + w - 34, y + 9, 24, BUTTON_HEIGHT, "×", true, supportEditorSaving);

        List<RosterMember> candidates = supportCandidates(snapshot, editingSupportSlot);
        float listTop = y + 54;
        float listBottom = y + h - 42;
        canvas.scissor(x + 8, listTop, w - 16, listBottom - listTop);
        int start = Math.min(supportEditorScrollRows, Math.max(0, candidates.size() - 1));
        float rowY = listTop;
        String selectedUuid = supportSlot(snapshot, editingSupportSlot) == null
                ? null : supportSlot(snapshot, editingSupportSlot).playerUuid();
        for (int index = start; index < candidates.size() && rowY + 30 <= listBottom; index++, rowY += 30) {
            RosterMember candidate = candidates.get(index);
            boolean selected = samePlayer(selectedUuid, candidate.playerUuid());
            canvas.fillRect(x + 12, rowY + 2, w - 24, 25,
                    color(selected ? ACCENT_PRIMARY_DARK : BACKGROUND_CONTENT));
            text(canvas, truncate(candidate.displayName(), 28), x + 18, rowY + 14, 11, color(TEXT_PRIMARY), false);
            text(canvas, candidate.online() ? "Online" : "Offline · currently assigned", x + w - 150, rowY + 14,
                    9, color(candidate.online() ? CONTROL_SUCCESS : TEXT_MUTED), false);
        }
        canvas.resetScissor();
        button(canvas, x + 12, y + h - 32, 72, BUTTON_HEIGHT, "Clear slot", true, supportEditorSaving);
        button(canvas, x + w - 80, y + h - 32, 68, BUTTON_HEIGHT, "Cancel", false, supportEditorSaving);
    }

    private void renderRoleEditor(UiCanvas canvas, float width, float height) {
        float w = Math.min(420, width - PADDING * 2);
        float h = 176;
        float x = (width - w) / 2;
        float y = (height - h) / 2;
        canvas.fillRect(0, 0, width, height, color(BACKGROUND_MODAL_OVERLAY));
        canvas.fillRect(x, y, w, h, plannerBackground(color(BACKGROUND_BODY_OPAQUE)));
        canvas.strokeRect(x, y, w, h, 1, color(CONTROL_BORDER));
        text(canvas, "Your Discord war roles", x + 14, y + 22, 16, color(ACCENT_PRIMARY), false);
        text(canvas, "Only Solo, DPS, and Tank are changed. Other Discord roles stay untouched.",
                x + 14, y + 43, 9, color(TEXT_MUTED), false);

        float optionY = y + 62;
        float optionGap = 8;
        float optionWidth = (w - 28 - optionGap * 2) / 3;
        WarCompositionRole[] roles = WarCompositionRole.values();
        for (int index = 0; index < roles.length; index++) {
            WarCompositionRole role = roles[index];
            float optionX = x + 14 + index * (optionWidth + optionGap);
            boolean selected = selectedCompositionRoles.contains(role);
            canvas.fillRect(optionX, optionY, optionWidth, 42,
                    color(selected ? ACCENT_PRIMARY_DARK : BACKGROUND_CONTENT));
            canvas.strokeRect(optionX, optionY, optionWidth, 42, 1,
                    color(selected ? ACCENT_PRIMARY : CONTROL_BORDER));
            renderCompositionIcons(canvas, List.of(role), optionX + 10, optionY + 8);
            text(canvas, (selected ? "✓ " : "") + role.label(), optionX + 30, optionY + 15, 11,
                    color(selected ? TEXT_PRIMARY : TEXT_SECONDARY), false);
            text(canvas, selected ? "Selected" : "Not selected", optionX + 10, optionY + 32, 8,
                    color(TEXT_MUTED), false);
        }
        button(canvas, x + w - 154, y + h - 34, 66, BUTTON_HEIGHT, "Cancel", false, roleEditorSaving);
        primaryButton(canvas, x + w - 80, y + h - 34, 66, BUTTON_HEIGHT,
                roleEditorSaving ? "Saving…" : "Save", roleEditorSaving);
    }

    private void renderTeamEditor(UiCanvas canvas, float width, float height) {
        WarPlannerSnapshot snapshot = manager.snapshot();
        if (snapshot == null) {
            return;
        }
        float w = teamEditorWidth(width);
        float x = (width - w) / 2;
        float y = 46;
        float h = height - 70;
        canvas.fillRect(0, 0, width, height, color(BACKGROUND_MODAL_OVERLAY));
        canvas.fillRect(x, y, w, h, plannerBackground(color(BACKGROUND_BODY_OPAQUE)));
        canvas.strokeRect(x, y, w, h, 1, color(CONTROL_BORDER));
        text(canvas, editingTeamId == null ? "Create war team" : "Edit war team", x + 12, y + 20, 16,
                color(ACCENT_PRIMARY), false);
        button(canvas, x + w - 34, y + 8, 24, BUTTON_HEIGHT, "×", true, teamEditorSaving);

        float fieldY = y + 34;
        boolean typeHovered = hit(nvgMouseX, nvgMouseY, x + 12, fieldY, w - 24, 24);
        canvas.fillRect(x + 12, fieldY, w - 24, 24, color(typeHovered ? CONTROL_INPUT_HOVER : CONTROL_INPUT));
        canvas.strokeRect(x + 12, fieldY, w - 24, 24, 1, color(CONTROL_BORDER));
        text(canvas, "Type", x + 18, fieldY + 12, 9, color(TEXT_MUTED), false);
        text(canvas, teamType.label(), x + 54, fieldY + 12, 12, color(TEXT_PRIMARY), false);
        String automaticName = automaticTeamName(snapshot, teamType, editingTeamId);
        text(canvas, "Creates " + automaticName, x + w - 188, fieldY + 12, 9, color(TEXT_MUTED), false);
        text(canvas, teamTypeMenuOpen ? "▲" : "▼", x + w - 24, fieldY + 12, 8, color(TEXT_SECONDARY), true);
        renderCompositionTargetControls(canvas, x, fieldY + 32, w);
        text(canvas, "Targets warn about missing capabilities; they do not block saving.", x + 12, fieldY + 64, 9,
                color(TEXT_MUTED), false);

        if (flashMessage != null && !flashMessage.isBlank()) {
            text(canvas, truncate(flashMessage, 58), x + 12, fieldY + 78, 9, color(CONTROL_WARNING), false);
        }
        float searchY = fieldY + TEAM_EDITOR_SEARCH_OFFSET;
        boolean searchHovered = hit(
                nvgMouseX, nvgMouseY, x + 12, searchY, SequoiaUiStyle.searchWidth(w - 24), TEAM_EDITOR_SEARCH_HEIGHT);
        canvas.fillRect(
                x + 12,
                searchY,
                SequoiaUiStyle.searchWidth(w - 24),
                TEAM_EDITOR_SEARCH_HEIGHT,
                color(teamEditorSearchFocused || searchHovered ? CONTROL_INPUT_HOVER : CONTROL_INPUT));
        canvas.strokeRect(
                x + 12,
                searchY,
                SequoiaUiStyle.searchWidth(w - 24),
                TEAM_EDITOR_SEARCH_HEIGHT,
                1,
                color(teamEditorSearchFocused ? CONTROL_BORDER : CONTROL_INPUT_SECONDARY));
        String searchText = teamEditorSearchQuery.isEmpty() ? "Search all players…" : teamEditorSearchQuery;
        if (teamEditorSearchFocused && (System.currentTimeMillis() / 500) % 2 == 0) {
            searchText += "|";
        }
        canvas.save();
        canvas.scissor(x + 18, searchY, Math.max(0, SequoiaUiStyle.searchWidth(w - 24) - 12), TEAM_EDITOR_SEARCH_HEIGHT);
        text(
                canvas,
                searchText,
                x + 18,
                searchY + TEAM_EDITOR_SEARCH_HEIGHT / 2,
                10,
                color(teamEditorSearchQuery.isEmpty() ? TEXT_MUTED : TEXT_PRIMARY),
                false);
        canvas.restore();

        List<RosterMember> eligible = teamEditorRoster(snapshot, teamEditorSearchQuery);
        float listTop = fieldY + TEAM_EDITOR_LIST_OFFSET;
        float listBottom = y + h - 42;
        canvas.scissor(x + 8, listTop, w - 16, Math.max(0, listBottom - listTop));
        int start = Math.min(editorScrollRows, Math.max(0, eligible.size() - 1));
        float rowY = listTop;
        if (eligible.isEmpty()) {
            text(canvas, "No players match this search.", x + 18, rowY + 14, 10, color(TEXT_MUTED), false);
        }
        for (int index = start; index < eligible.size() && rowY + 28 <= listBottom; index++, rowY += 28) {
            RosterMember member = eligible.get(index);
            TeamMemberDraft selected = teamMember(member.playerUuid());
            canvas.fillRect(x + 12, rowY + 2, w - 24, 24,
                    color(selected == null ? BACKGROUND_CONTENT : ACCENT_PRIMARY_DARK));
            String memberLabel = member.displayName() + (member.online() ? "" : " · Offline");
            text(canvas, truncate(memberLabel, w >= 360 ? 24 : 14), x + 18, rowY + 14, 11,
                    color(TEXT_PRIMARY), false);
            float dutyX = x + w - 92;
            float rolesX = Math.max(x + 104, dutyX - 52);
            renderCompositionIcons(canvas, member.compositionRoles(), rolesX, rowY + 8);
            String assignment = selected != null
                    ? "In party"
                    : member.teamId() != null ? "Move here" : "Add";
            text(canvas, assignment, x + w - 92, rowY + 14, 10,
                    color(selected == null ? TEXT_MUTED : TEXT_PRIMARY), false);
        }
        canvas.resetScissor();
        text(canvas, teamMembers.size() + "/5 slots", x + 12, y + h - 22, 11, color(TEXT_MUTED), false);
        button(canvas, x + w - 148, y + h - 32, 64, BUTTON_HEIGHT, "Cancel", false, teamEditorSaving);
        primaryButton(canvas, x + w - 78, y + h - 32, 66, BUTTON_HEIGHT,
                teamEditorSaving ? "Saving…" : "Save", teamEditorSaving);
        if (teamTypeMenuOpen) {
            renderTeamTypeMenu(canvas, snapshot, x + 12, fieldY + 25, w - 24);
        }
    }

    private void renderCompositionTargetControls(UiCanvas canvas, float x, float y, float width) {
        text(canvas, "Comp", x + 12, y + 11, 9, color(TEXT_MUTED), false);
        float controlWidth = Math.min(92, Math.max(70, (width - 68) / 3));
        for (int index = 0; index < WarCompositionRole.values().length; index++) {
            WarCompositionRole role = WarCompositionRole.values()[index];
            float controlX = x + 55 + index * controlWidth;
            renderCompositionIcons(canvas, List.of(role), controlX, y + 5);
            button(canvas, controlX + 17, y, 18, BUTTON_HEIGHT, "−", false, teamEditorSaving);
            text(canvas, Integer.toString(teamTargets.target(role)), controlX + 43, y + 11, 10,
                    color(TEXT_PRIMARY), true);
            button(canvas, controlX + 51, y, 18, BUTTON_HEIGHT, "+", false, teamEditorSaving);
        }
    }

    private void renderTeamTypeMenu(
            UiCanvas canvas, WarPlannerSnapshot snapshot, float x, float y, float menuWidth) {
        List<WarTeamType> options = WarTeamType.editableValues();
        for (int index = 0; index < options.size(); index++) {
            WarTeamType option = options.get(index);
            float optionY = y + index * 24;
            boolean selectable = teamTypeSelectable(snapshot, option, editingTeamId);
            boolean hovered = selectable && hit(nvgMouseX, nvgMouseY, x, optionY, menuWidth, 23);
            canvas.fillRect(x, optionY, menuWidth, 23,
                    color(hovered || option == teamType ? CONTROL_INPUT_HOVER : BACKGROUND_BODY_OPAQUE));
            canvas.strokeRect(x, optionY, menuWidth, 23, 1, color(CONTROL_BORDER));
            text(canvas, option.label(), x + 8, optionY + 12, 11,
                    color(selectable ? TEXT_PRIMARY : TEXT_MUTED), false);
            String preview = option == WarTeamType.HQ && !selectable
                    ? "Already assigned"
                    : automaticTeamName(snapshot, option, editingTeamId);
            text(canvas, preview, x + menuWidth - 110, optionY + 12, 9, color(TEXT_MUTED), false);
        }
    }

    @Override
    public boolean mouseClicked(@NotNull MouseButtonEvent click, boolean outsideScreen) {
        if (!plannerModalOpen() && click.button() != 0) {
            float mx = MinecraftUiRenderer.mouseX(click.x()) - plannerViewport(MinecraftUiRenderer.screenWidth()).x();
            float my = MinecraftUiRenderer.mouseY(click.y());
            if (sectionDropdownOpen || coloringDropdownOpen) return true;
            if (tab == Tab.ZONES) {
                var layout = warMapLayout(plannerViewport(MinecraftUiRenderer.screenWidth()).width(), contentTop(),
                        MinecraftUiRenderer.screenHeight() - PADDING);
                var controls = warMapControls(layout, manager.canManage());
                if (controls.panel().contains(mx, my) || controls.fit().contains(mx, my) || controls.coloring().contains(mx, my)) return true;
            }
        }
        if ((warPingPickerOpen || zoneActionsId != null) && click.button() != 0) return true;
        if (click.button() == 1) {
            PlannerViewport viewport = plannerViewport(MinecraftUiRenderer.screenWidth());
            float mx = MinecraftUiRenderer.mouseX(click.x()) - viewport.x();
            float my = MinecraftUiRenderer.mouseY(click.y());
            WarPlannerSnapshot snapshot = manager == null ? null : manager.snapshot();
            if (!warPingPickerOpen
                    && !roleEditorOpen
                    && !teamEditorOpen
                    && editingSupportSlot == null
                    && tab == Tab.ZONES
                    && manager != null
                    && manager.canManage()
                    && snapshot != null) {
                float height = MinecraftUiRenderer.screenHeight();
                if (rightClickZoneName(snapshot, mx, my, viewport.width(), height)
                        || rightClickWarMapTerritory(snapshot, mx, my, viewport.width(), height)) {
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

        if (warPingPickerOpen) {
            return clickWarPingPicker(mx, my, width, height);
        }
        if (roleEditorOpen) {
            return clickRoleEditor(mx, my, width, height);
        }
        if (teamEditorOpen) {
            return clickTeamEditor(mx, my, width, height);
        }
        if (editingSupportSlot != null) {
            return clickSupportEditor(mx, my, width, height);
        }
        if (clickPlannerDropdown(mx, my, width, height)) return true;
        if (clickZoneActions(mx, my)) return true;
        if (SequoiaSidebarNavigation.click(mx + viewport.x(), my, height,
                SequoiaSidebarNavigation.Destination.WAR, parent)) return true;
        HeaderControls header = headerControls(width);
        if (tab == Tab.ZONES) {
            var display = warMapControls(warMapLayout(width, contentTop(), height - PADDING), manager.canManage());
            var slider = prepareOpacitySlider(display);
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
            beginRoleEdit();
            return true;
        }
        AvailabilityLayout availability = availabilityLayout(width);
        float availabilityY = HEADER_HEIGHT + availability.y();
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

        if (manager.canManage() && tab != Tab.ZONES && hit(mx, my, PADDING, contentTop(), 80, BUTTON_HEIGHT)) {
            if (tab == Tab.ROSTER && !manager.isMutating() && !pingCandidates(manager.snapshot(), "").isEmpty()) {
                beginWarPingPicker();
            } else if (tab == Tab.TEAMS && !manager.isMutating()) {
                beginTeamEdit(null);
            }
            return true;
        }
        return clickContent(mx, my, width, height) || super.mouseClicked(click, outsideScreen);
    }

    private boolean plannerModalOpen() {
        return warPingPickerOpen || roleEditorOpen || teamEditorOpen || editingSupportSlot != null;
    }

    private boolean clickPlannerDropdown(float mx, float my, float width, float height) {
        var section = headerControls(width).section();
        var coloring = warMapControls(warMapLayout(width, contentTop(), height - PADDING), manager.canManage()).coloring();
        if (sectionDropdownOpen) {
            sectionDropdownOpen = false;
            for (int index = 0; index < Tab.values().length; index++) {
                if (dropdownOption(section, index).contains(mx, my)) {
                    tab = Tab.values()[index];
                    scrollRows = 0;
                    draggingWarMap = false;
                    pendingWarQueueClick = null;
                    pendingDeleteTeam = null;
                    pendingDeleteZone = null;
                    pendingDeleteZoneCategory = null;
                    zoneActionsId = null;
                    memberDrag = null;
                    zoneDrag = null;
                    if (opacitySlider != null) opacitySlider.onHidden();
                    break;
                }
            }
            return true;
        }
        if (coloringDropdownOpen) {
            coloringDropdownOpen = false;
            for (int index = 0; index < 2; index++) {
                if (dropdownOption(coloring, index).contains(mx, my)) {
                    var setting = SeqClient.getWarPlannerResourceColorsSetting();
                    if (setting != null) {
                        setting.setValue(index == 1);
                        SeqClient.getConfigManager().save();
                    }
                    break;
                }
            }
            return true;
        }
        if (section.contains(mx, my)) {
            sectionDropdownOpen = true;
        } else if (tab == Tab.ZONES && coloring.contains(mx, my)) {
            coloringDropdownOpen = true;
        } else {
            return false;
        }
        draggingWarMap = false;
        pendingWarQueueClick = null;
        zoneActionsId = null;
        if (opacitySlider != null) opacitySlider.onHidden();
        return true;
    }

    private boolean rightClickZoneName(
            WarPlannerSnapshot snapshot, float mx, float my, float width, float height) {
        float top = contentTop();
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
            SeqClient.mc.setScreen(new WarZoneCategoryEditorScreen(this, entry.category()));
            return true;
        }
        if (!hit(mx, my, layout.sidebarX() + 18, placement.y() + 3, layout.sidebarWidth() - 30, 22)) return false;
        SeqClient.mc.setScreen(new WarTerritoryPickerScreen(this, entry.zone(), true));
        return true;
    }

    private boolean rightClickWarMapTerritory(
            WarPlannerSnapshot snapshot, float mx, float my, float width, float height) {
        WarMapLayout layout = warMapLayout(width, contentTop(), height - PADDING);
        if (!layout.containsMap(mx, my)) return false;
        boolean locked = manager.canManage() && territoriesLocked();
        List<GuildTerritory> displayedTerritories = warMapDisplayedTerritories(snapshot, locked);
        Set<String> displayedNames = displayedTerritories.stream()
                .map(GuildTerritory::name)
                .map(name -> name.toLowerCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toSet());
        GuildTerritory territory = territoryAt(
                territoryIndex, warMapViewport(layout, displayedTerritories, locked), displayedNames, mx, my);
        if (territory == null) return false;
        if (!manager.isMutating()) {
            String nextHq = territory.name().equalsIgnoreCase(snapshot.hqTerritory()) ? null : territory.name();
            showResult(manager.setHqTerritory(nextHq, snapshot.mapVersion()));
        }
        return true;
    }

    private boolean clickContent(float mx, float my, float width, float height) {
        WarPlannerSnapshot snapshot = manager.snapshot();
        if (snapshot == null || my < contentTop() + (tab == Tab.ZONES ? 0 : 28) || my > height - PADDING) {
            return false;
        }
        TeamsLayout teamsLayout = tab == Tab.TEAMS
                ? teamsLayout(width, contentTop() + 28, height - PADDING, snapshot.teams().size())
                : null;
        if (tab == Tab.TEAMS && manager.canManage()) {
            for (SupportPlacement support : supportPlacements(teamsLayout)) {
                if (hit(mx, my, support.x(), support.y(), support.width(), support.height())) {
                    editingSupportSlot = support.index();
                    supportEditorScrollRows = 0;
                    return true;
                }
            }
            if (!manager.isMutating()) {
                MemberDrag candidate = teamMemberDragAt(snapshot, mx, my, width, height);
                if (candidate == null) {
                    candidate = unassignedMemberDragAt(snapshot, mx, my, width, height);
                }
                if (candidate != null) {
                    memberDrag = new MemberDrag(
                            candidate.playerUuid(),
                            candidate.sourceTeamId(),
                            candidate.sourceVersion(),
                            mx,
                            my,
                            false);
                    return true;
                }
            }
        }
        if (tab == Tab.ZONES) {
            return clickZoneContent(snapshot, mx, my, width);
        }
        if (tab == Tab.ROSTER) return false;
        if (tab == Tab.TEAMS) {
            TeamPlacement placement = teamPlacementAt(snapshot, mx, my, width, height);
            if (placement == null) return false;
            Team team = placement.team();
            float rowY = placement.y();
            RosterMember caller = snapshot.caller();
            TeamActionLayout actions = placement.actions();
            if (caller != null
                    && placement.fullyShows(actions.selfY(), BUTTON_HEIGHT)
                    && hit(mx, my, actions.selfX(), rowY + actions.selfY(), actions.selfWidth(), BUTTON_HEIGHT)) {
                if (canChangeOwnTeam(snapshot, team) && !manager.isMutating()) {
                    boolean ownTeam = caller.teamId() != null && caller.teamId() == team.id();
                    showResult(ownTeam ? manager.leaveTeam() : manager.joinTeam(team.id()));
                }
                return true;
            }
            if (manager.canManage()
                    && placement.fullyShows(actions.managerY(), BUTTON_HEIGHT)
                    && hit(mx, my, actions.editX(), rowY + actions.managerY(), actions.editWidth(), BUTTON_HEIGHT)) {
                beginTeamEdit(team);
                return true;
            }
            if (manager.canManage()
                    && placement.fullyShows(actions.managerY(), BUTTON_HEIGHT)
                    && hit(mx, my, actions.deleteX(), rowY + actions.managerY(), actions.deleteWidth(), BUTTON_HEIGHT)) {
                if (pendingDeleteTeam != null && pendingDeleteTeam.id() == team.id()) {
                    showResult(manager.deleteTeam(team.id(), pendingDeleteTeam.version()));
                    pendingDeleteTeam = null;
                } else {
                    pendingDeleteTeam = new PendingDelete(team.id(), team.version());
                }
                return true;
            }
        }
        return false;
    }

    private boolean clickZoneContent(WarPlannerSnapshot snapshot, float mx, float my, float width) {
        float top = contentTop();
        WarMapLayout layout = warMapLayout(width, top, MinecraftUiRenderer.screenHeight() - PADDING);
        top = layout.mapY();
        boolean locked = manager.canManage() && territoriesLocked();
        WarMapControls controls = warMapControls(layout, manager.canManage());
        if (controls.fit().contains(mx, my)) {
            pendingWarQueueClick = null;
            resetWarMapViewport(
                    layout,
                    warMapFitBounds(warMapDisplayedTerritories(snapshot, locked), locked),
                    locked);
            return true;
        }
        if (manager.canManage() && controls.lock().contains(mx, my)) {
            var setting = SeqClient.getWarPlannerLockTerritoriesSetting();
            if (setting != null) {
                setting.setValue(!territoriesLocked());
                SeqClient.getConfigManager().save();
            }
            warMapFitted = false;
            pendingWarQueueClick = null;
            draggingWarMap = false;
            return true;
        }
        if (controls.queues().contains(mx, my)) {
            var setting = SeqClient.getWarQueueHudOnlyOwnedOrJoinedSetting();
            if (setting != null) {
                setting.setValue(!onlyMyWarQueuesEnabled());
                SeqClient.getConfigManager().save();
            }
            pendingWarQueueClick = null;
            draggingWarMap = false;
            return true;
        }
        if (controls.players().contains(mx, my)) {
            var setting = SeqClient.getWarPlannerShowPlayersSetting();
            if (setting != null) {
                setting.setValue(!warMapPlayersEnabled());
                SeqClient.getConfigManager().save();
                if (!setting.getValue()) {
                    UiRenderer.renderResource(canvas -> telemetryPlayerOverlay.close());
                }
            }
            pendingWarQueueClick = null;
            draggingWarMap = false;
            return true;
        }
        if (controls.panel().contains(mx, my)) return true;
        if (layout.containsMap(mx, my)) {
            TerritoryQueue queue = warQueueAtMapPoint(snapshot, layout, locked, mx, my);
            long clickedAt = monotonicMillis();
            if (queue != null
                    && isWarQueueDoubleClick(
                            pendingWarQueueClick, queue.id(), queue.territory(), mx, my, clickedAt)) {
                pendingWarQueueClick = null;
                draggingWarMap = false;
                showQueueResult(queueManager.toggleQueueMembership(queue.id()));
                return true;
            }
            pendingWarQueueClick = queue == null
                    ? null
                    : new PendingWarQueueClick(queue.id(), queue.territory(), mx, my, clickedAt);
            draggingWarMap = true;
            mapPressX = mx;
            mapPressY = my;
            mapPressMoved = false;
            return true;
        }
        float sidebarWidth = layout.sidebarWidth();
        float sidebarX = layout.sidebarX();
        if (manager.canManage()) {
            float headerActionWidth = (sidebarWidth - 18) / 2;
            if (hit(mx, my, sidebarX + 6, top + 37, headerActionWidth, 20)) {
                SeqClient.mc.setScreen(new WarZoneCategoryEditorScreen(this, null));
                return true;
            }
            if (hit(mx, my, sidebarX + 12 + headerActionWidth, top + 37, headerActionWidth, 20)) {
                SeqClient.mc.setScreen(new WarTerritoryPickerScreen(this, null));
                return true;
            }
        }
        ZoneSidebarPlacement placement = zoneSidebarPlacementAt(
                snapshot,
                collapsedZoneCategoryIds,
                scrollRows,
                top,
                MinecraftUiRenderer.screenHeight() - PADDING,
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
                    showResult(manager.deleteZoneCategory(
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

    private TerritoryQueue warQueueAtMapPoint(
            WarPlannerSnapshot snapshot, WarMapLayout layout, boolean locked, float mouseX, float mouseY) {
        if (queueManager == null) return null;
        List<Zone> displayedZones = visibleZones(
                snapshot.zones(), hiddenZoneIds, hiddenZoneCategoryIds);
        Set<String> shownZoneTerritories = shownZoneTerritoryNames(displayedZones);
        if (shownZoneTerritories.isEmpty()) return null;

        List<GuildTerritory> displayedTerritories = warMapDisplayedTerritories(snapshot, locked);
        GuildTerritory territory = territoryAt(
                territoryIndex,
                warMapViewport(layout, displayedTerritories, locked),
                shownZoneTerritories,
                mouseX,
                mouseY);
        if (territory == null) return null;
        return displayedWarQueues().stream()
                .filter(queue -> territory.name().equalsIgnoreCase(queue.territory()))
                .findFirst()
                .orElse(null);
    }

    private List<TerritoryQueue> displayedWarQueues() {
        return queueManager == null
                ? List.of()
                : warQueuesForMap(
                        queueManager.activeQueues(),
                        queueManager.localPlayerUuid(),
                        onlyMyWarQueuesEnabled());
    }

    static List<TerritoryQueue> warQueuesForMap(
            List<TerritoryQueue> queues, String localPlayerUuid, boolean onlyOwnedOrJoined) {
        return WarTerritoryQueueHudRenderer.displayedQueues(
                queues, localPlayerUuid, onlyOwnedOrJoined, Integer.MAX_VALUE);
    }

    static boolean onlyMyWarQueuesEnabled() {
        return WarTerritoryQueueHudRenderer.onlyOwnedOrJoined(
                SeqClient.getWarQueueHudOnlyOwnedOrJoinedSetting());
    }

    private void toggleZoneDisplay(long zoneId) {
        boolean hidden = !hiddenZoneIds.remove(zoneId);
        if (hidden) hiddenZoneIds.add(zoneId);
        pendingWarQueueClick = null;
        SeqClient.getConfigManager().setWarPlannerZoneHidden(zoneId, hidden);
    }

    private void toggleZoneCategoryDisplay(Long categoryId) {
        boolean hidden = !hiddenZoneCategoryIds.remove(categoryId);
        if (hidden) hiddenZoneCategoryIds.add(categoryId);
        pendingWarQueueClick = null;
        SeqClient.getConfigManager().setWarPlannerZoneCategoryHidden(categoryId, hidden);
    }

    private void toggleZoneCategoryFold(Long categoryId) {
        if (!collapsedZoneCategoryIds.remove(categoryId)) collapsedZoneCategoryIds.add(categoryId);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent click, double deltaX, double deltaY) {
        if (warPingPickerOpen) return true;
        if (tab == Tab.ZONES && opacitySlider != null && click.button() == 0 && !plannerModalOpen()
                && opacitySlider.mouseDragged(MinecraftUiRenderer.mouseX(click.x())
                        - plannerViewport(MinecraftUiRenderer.screenWidth()).x(), MinecraftUiRenderer.mouseY(click.y()))) {
            return true;
        }
        if (draggingWarMap && click.button() == 0) {
            pendingWarQueueClick = null;
            PlannerViewport plannerViewport = plannerViewport(MinecraftUiRenderer.screenWidth());
            WarMapLayout layout = warMapLayout(
                    plannerViewport.width(), contentTop(), MinecraftUiRenderer.screenHeight() - PADDING);
            float mx = MinecraftUiRenderer.mouseX(click.x()) - plannerViewport.x();
            float my = MinecraftUiRenderer.mouseY(click.y());
            if (Math.hypot(mx - mapPressX, my - mapPressY) >= 4) mapPressMoved = true;
            applyWarMapViewport(currentWarMapViewport(layout).panByScreenDelta(
                    MinecraftUiRenderer.mouseDelta(deltaX),
                    MinecraftUiRenderer.mouseDelta(deltaY)));
            return true;
        }
        if (memberDrag != null && click.button() == 0) {
            PlannerViewport viewport = plannerViewport(MinecraftUiRenderer.screenWidth());
            float mx = MinecraftUiRenderer.mouseX(click.x()) - viewport.x();
            float my = MinecraftUiRenderer.mouseY(click.y());
            if (!memberDrag.active()
                    && Math.hypot(mx - memberDrag.startX(), my - memberDrag.startY()) >= 4) {
                memberDrag = new MemberDrag(
                        memberDrag.playerUuid(),
                        memberDrag.sourceTeamId(),
                        memberDrag.sourceVersion(),
                        memberDrag.startX(),
                        memberDrag.startY(),
                        true);
            }
            return true;
        }
        if (zoneDrag != null && click.button() == 0) {
            PlannerViewport viewport = plannerViewport(MinecraftUiRenderer.screenWidth());
            float mx = MinecraftUiRenderer.mouseX(click.x()) - viewport.x();
            float my = MinecraftUiRenderer.mouseY(click.y());
            if (!zoneDrag.active() && Math.hypot(mx - zoneDrag.startX(), my - zoneDrag.startY()) >= 4) {
                zoneDrag = new ZoneDrag(
                        zoneDrag.zoneId(), zoneDrag.zoneName(), zoneDrag.version(),
                        zoneDrag.startX(), zoneDrag.startY(), true);
            }
            return true;
        }
        return super.mouseDragged(click, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(@NotNull MouseButtonEvent click) {
        if (warPingPickerOpen) return true;
        if (opacitySlider != null && opacitySlider.mouseReleased(
                MinecraftUiRenderer.mouseX(click.x()) - plannerViewport(MinecraftUiRenderer.screenWidth()).x(),
                MinecraftUiRenderer.mouseY(click.y()), click.button())) {
            SeqClient.getConfigManager().save();
            return true;
        }
        if (click.button() == 0 && draggingWarMap) {
            draggingWarMap = false;
            if (!mapPressMoved) {
                var planner = plannerViewport(MinecraftUiRenderer.screenWidth());
                var layout = warMapLayout(planner.width(), contentTop(), MinecraftUiRenderer.screenHeight() - PADDING);
                var snapshot = manager.snapshot();
                if (snapshot != null) {
                    boolean locked = manager.canManage() && territoriesLocked();
                    var territories = warMapDisplayedTerritories(snapshot, locked);
                    var names = territories.stream().map(territory -> territory.name().toLowerCase(Locale.ROOT))
                            .collect(java.util.stream.Collectors.toSet());
                    var selected = territoryAt(territoryIndex, warMapViewport(layout, territories, locked), names,
                            MinecraftUiRenderer.mouseX(click.x()) - planner.x(), MinecraftUiRenderer.mouseY(click.y()));
                    selectedWarTerritory = selected == null ? null : selected.name();
                }
            }
            return true;
        }
        if (click.button() == 0 && memberDrag != null) {
            PlannerViewport viewport = plannerViewport(MinecraftUiRenderer.screenWidth());
            float mx = MinecraftUiRenderer.mouseX(click.x()) - viewport.x();
            float my = MinecraftUiRenderer.mouseY(click.y());
            MemberDrag completed = memberDrag;
            memberDrag = null;
            if (completed.active()) {
                dropTeamMember(completed, mx, my, viewport.width(), MinecraftUiRenderer.screenHeight());
            }
            return true;
        }
        if (click.button() == 0 && zoneDrag != null) {
            PlannerViewport viewport = plannerViewport(MinecraftUiRenderer.screenWidth());
            float mx = MinecraftUiRenderer.mouseX(click.x()) - viewport.x();
            float my = MinecraftUiRenderer.mouseY(click.y());
            ZoneDrag completed = zoneDrag;
            zoneDrag = null;
            if (completed.active()) dropZone(completed, mx, my, viewport.width(), MinecraftUiRenderer.screenHeight());
            return true;
        }
        return super.mouseReleased(click);
    }

    private void dropZone(ZoneDrag drag, float mx, float my, float width, float height) {
        WarPlannerSnapshot snapshot = manager.snapshot();
        if (snapshot == null || manager.isMutating()) return;
        WarMapLayout layout = warMapLayout(width, contentTop(), height - PADDING);
        if (!layout.containsSidebar(mx, my)) return;
        ZoneSidebarPlacement placement = zoneSidebarPlacementAt(
                snapshot, collapsedZoneCategoryIds, scrollRows, layout.mapY(), height - PADDING, my);
        if (placement == null) return;
        ZoneDropTarget target = zoneDropTarget(snapshot, placement, my, drag.zoneId());
        if (target == null) return;
        showResult(manager.moveZone(
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

    private boolean clickTeamEditor(float mx, float my, float width, float height) {
        WarPlannerSnapshot snapshot = manager.snapshot();
        if (snapshot == null) return true;
        if (teamEditorSaving) return true;
        float w = teamEditorWidth(width);
        float x = (width - w) / 2;
        float y = 46;
        float h = height - 70;
        if (hit(mx, my, x + w - 34, y + 8, 24, BUTTON_HEIGHT)
                || hit(mx, my, x + w - 148, y + h - 32, 64, BUTTON_HEIGHT)) {
            closeTeamEditor();
            return true;
        }
        float fieldY = y + 34;
        float searchY = fieldY + TEAM_EDITOR_SEARCH_OFFSET;
        boolean searchClicked = hit(mx, my, x + 12, searchY, SequoiaUiStyle.searchWidth(w - 24), TEAM_EDITOR_SEARCH_HEIGHT);
        if (!searchClicked || teamTypeMenuOpen) {
            teamEditorSearchFocused = false;
        }
        if (hit(mx, my, x + 12, fieldY, w - 24, 24)) {
            teamTypeMenuOpen = !teamTypeMenuOpen;
            return true;
        }
        if (teamTypeMenuOpen) {
            List<WarTeamType> options = WarTeamType.editableValues();
            for (int index = 0; index < options.size(); index++) {
                float optionY = fieldY + 25 + index * 24;
                if (!hit(mx, my, x + 12, optionY, w - 24, 23)) continue;
                WarTeamType option = options.get(index);
                if (teamTypeSelectable(snapshot, option, editingTeamId)) {
                    teamType = option;
                    flashMessage = null;
                } else {
                    flashMessage = "Only one HQ Team can exist.";
                }
                teamTypeMenuOpen = false;
                return true;
            }
            teamTypeMenuOpen = false;
        }
        if (hit(mx, my, x + w - 78, y + h - 32, 66, BUTTON_HEIGHT)) {
            saveTeam();
            return true;
        }
        float targetY = fieldY + 32;
        float controlWidth = Math.min(92, Math.max(70, (w - 68) / 3));
        for (int index = 0; index < WarCompositionRole.values().length; index++) {
            WarCompositionRole role = WarCompositionRole.values()[index];
            float controlX = x + 55 + index * controlWidth;
            if (hit(mx, my, controlX + 17, targetY, 18, BUTTON_HEIGHT)) {
                teamTargets = teamTargets.with(role, Math.max(0, teamTargets.target(role) - 1));
                return true;
            }
            if (hit(mx, my, controlX + 51, targetY, 18, BUTTON_HEIGHT)) {
                teamTargets = teamTargets.with(role, Math.min(5, teamTargets.target(role) + 1));
                return true;
            }
        }
        if (searchClicked) {
            teamEditorSearchFocused = true;
            return true;
        }

        float listTop = fieldY + TEAM_EDITOR_LIST_OFFSET;
        float listBottom = y + h - 42;
        if (my >= listTop && my <= listBottom) {
            List<RosterMember> eligible = teamEditorRoster(snapshot, teamEditorSearchQuery);
            int row = editorScrollRows + (int) ((my - listTop) / 28);
            if (row >= 0 && row < eligible.size()) {
                cycleTeamMember(eligible.get(row));
            }
            return true;
        }
        return true;
    }

    private boolean clickWarPingPicker(float mx, float my, float width, float height) {
        WarPlannerSnapshot snapshot = manager.snapshot();
        if (snapshot == null || !manager.canManage()) {
            closeWarPingPicker();
            return true;
        }
        if (warPingSending) return true;
        float w = Math.min(460, width - PADDING * 2);
        float h = Math.min(390, height - 44);
        float x = (width - w) / 2;
        float y = (height - h) / 2;
        if (hit(mx, my, x + w - 34, y + 9, 24, BUTTON_HEIGHT)) {
            closeWarPingPicker();
            return true;
        }
        float searchY = y + 52;
        if (hit(mx, my, x + 12, searchY, SequoiaUiStyle.searchWidth(w - 24), TEAM_EDITOR_SEARCH_HEIGHT)) {
            warPingSearchFocused = true;
            return true;
        }
        warPingSearchFocused = false;
        float listTop = searchY + 30;
        float listBottom = y + h - 12;
        if (my >= listTop && my <= listBottom) {
            List<RosterMember> candidates = pingCandidates(snapshot, warPingSearchQuery);
            int start = warPingScrollStart(warPingScrollRows, candidates.size());
            int row = start + (int) ((my - listTop) / 30);
            float rowY = listTop + (row - start) * 30;
            if (row >= 0
                    && row < candidates.size()
                    && warPingRowFullyVisible(rowY, listBottom)
                    && hit(mx, my, x + w - 72, rowY + 4, 54, BUTTON_HEIGHT)) {
                sendWarPing(candidates.get(row));
            }
        }
        return true;
    }

    private boolean clickSupportEditor(float mx, float my, float width, float height) {
        WarPlannerSnapshot snapshot = manager.snapshot();
        if (snapshot == null || editingSupportSlot == null || supportEditorSaving) return true;
        float w = Math.min(430, width - PADDING * 2);
        float h = Math.min(390, height - 44);
        float x = (width - w) / 2;
        float y = (height - h) / 2;
        if (hit(mx, my, x + w - 34, y + 9, 24, BUTTON_HEIGHT)
                || hit(mx, my, x + w - 80, y + h - 32, 68, BUTTON_HEIGHT)) {
            closeSupportEditor();
            return true;
        }
        if (hit(mx, my, x + 12, y + h - 32, 72, BUTTON_HEIGHT)) {
            saveSupportSlot(editingSupportSlot, null);
            return true;
        }
        float listTop = y + 54;
        float listBottom = y + h - 42;
        if (my >= listTop && my <= listBottom) {
            List<RosterMember> candidates = supportCandidates(snapshot, editingSupportSlot);
            int row = supportEditorScrollRows + (int) ((my - listTop) / 30);
            if (row >= 0 && row < candidates.size()) {
                saveSupportSlot(editingSupportSlot, candidates.get(row).playerUuid());
            }
        }
        return true;
    }

    private boolean clickRoleEditor(float mx, float my, float width, float height) {
        if (roleEditorSaving) return true;
        float w = Math.min(420, width - PADDING * 2);
        float h = 176;
        float x = (width - w) / 2;
        float y = (height - h) / 2;
        if (hit(mx, my, x + w - 154, y + h - 34, 66, BUTTON_HEIGHT)) {
            closeRoleEditor();
            return true;
        }
        if (hit(mx, my, x + w - 80, y + h - 34, 66, BUTTON_HEIGHT)) {
            saveCompositionRoles();
            return true;
        }
        float optionY = y + 62;
        float optionGap = 8;
        float optionWidth = (w - 28 - optionGap * 2) / 3;
        WarCompositionRole[] roles = WarCompositionRole.values();
        for (int index = 0; index < roles.length; index++) {
            float optionX = x + 14 + index * (optionWidth + optionGap);
            if (hit(mx, my, optionX, optionY, optionWidth, 42)) {
                WarCompositionRole role = roles[index];
                if (!selectedCompositionRoles.remove(role)) selectedCompositionRoles.add(role);
                return true;
            }
        }
        return true;
    }

    @Override
    public boolean keyPressed(@NotNull KeyEvent keyEvent) {
        if (!plannerModalOpen() && (sectionDropdownOpen || coloringDropdownOpen)) {
            if (keyEvent.key() == GLFW.GLFW_KEY_ESCAPE) {
                sectionDropdownOpen = false;
                coloringDropdownOpen = false;
            }
            return true;
        }
        if (!plannerModalOpen() && tab == Tab.ZONES && opacitySlider != null && opacitySlider.keyPressed(keyEvent)) {
            SeqClient.getConfigManager().save();
            return true;
        }
        if (keyEvent.key() == GLFW.GLFW_KEY_ESCAPE && zoneActionsId != null) {
            zoneActionsId = null;
            pendingDeleteZone = null;
            return true;
        }
        if (warPingPickerOpen) {
            int key = keyEvent.key();
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                closeWarPingPicker();
                return true;
            }
            if (warPingSearchFocused) {
                if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                    warPingSearchFocused = false;
                    return true;
                }
                if (key == GLFW.GLFW_KEY_BACKSPACE) {
                    if (!warPingSearchQuery.isEmpty()) {
                        warPingSearchQuery = warPingSearchQuery.substring(0, warPingSearchQuery.length() - 1);
                        warPingScrollRows = 0;
                    }
                    return true;
                }
                return true;
            }
        }
        if (teamEditorOpen && teamEditorSearchFocused) {
            int key = keyEvent.key();
            if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                teamEditorSearchFocused = false;
                return true;
            }
            if (key == GLFW.GLFW_KEY_BACKSPACE) {
                if (!teamEditorSearchQuery.isEmpty()) {
                    teamEditorSearchQuery = teamEditorSearchQuery.substring(0, teamEditorSearchQuery.length() - 1);
                    editorScrollRows = 0;
                }
                return true;
            }
            return true;
        }
        return super.keyPressed(keyEvent);
    }

    @Override
    public boolean charTyped(@NotNull CharacterEvent characterEvent) {
        if (!plannerModalOpen() && tab == Tab.ZONES && opacitySlider != null && opacitySlider.charTyped(characterEvent)) return true;
        if (warPingPickerOpen && warPingSearchFocused) {
            String typedText = TextInputHelper.getTypedText(characterEvent);
            if (typedText != null
                    && warPingSearchQuery.length() + typedText.length() <= TEAM_EDITOR_SEARCH_MAX_LENGTH) {
                warPingSearchQuery += typedText;
                warPingScrollRows = 0;
            }
            return true;
        }
        if (teamEditorOpen && teamEditorSearchFocused) {
            String typedText = TextInputHelper.getTypedText(characterEvent);
            if (typedText != null
                    && teamEditorSearchQuery.length() + typedText.length() <= TEAM_EDITOR_SEARCH_MAX_LENGTH) {
                teamEditorSearchQuery += typedText;
                editorScrollRows = 0;
            }
            return true;
        }
        return super.charTyped(characterEvent);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!plannerModalOpen() && (sectionDropdownOpen || coloringDropdownOpen)) return true;
        zoneActionsId = null;
        pendingDeleteZone = null;
        if (scrollY == 0) return true;
        int delta = scrollY > 0 ? -1 : 1;
        WarPlannerSnapshot snapshot = manager.snapshot();
        if (snapshot == null) return true;
        if (warPingPickerOpen) {
            warPingScrollRows = clampRows(
                    warPingScrollRows + delta, pingCandidates(snapshot, warPingSearchQuery).size());
        } else if (roleEditorOpen) {
            return true;
        } else if (teamEditorOpen) {
            editorScrollRows = clampRows(
                    editorScrollRows + delta, teamEditorRoster(snapshot, teamEditorSearchQuery).size());
        } else if (editingSupportSlot != null) {
            supportEditorScrollRows = clampRows(
                    supportEditorScrollRows + delta, supportCandidates(snapshot, editingSupportSlot).size());
        } else {
            PlannerViewport viewport = plannerViewport(MinecraftUiRenderer.screenWidth());
            float localMouseX = MinecraftUiRenderer.mouseX(mouseX) - viewport.x();
            float localMouseY = MinecraftUiRenderer.mouseY(mouseY);
            float scrollTop = contentTop() + (tab == Tab.ZONES ? 0 : 28);
            if (!hit(localMouseX, localMouseY, PADDING, scrollTop,
                    viewport.width() - PADDING * 2, MinecraftUiRenderer.screenHeight() - scrollTop - PADDING)) {
                return true;
            }
            TeamsLayout teamsLayout = tab == Tab.TEAMS
                    ? teamsLayout(
                            viewport.width(),
                            contentTop() + 28,
                            MinecraftUiRenderer.screenHeight() - PADDING,
                            snapshot.teams().size())
                    : null;
            if (teamsLayout != null && manager.canManage() && !teamsLayout.compactAuxiliary()) {
                float poolY = contentTop() + 28 + UNASSIGNED_POOL_TOP;
                float contentBottom = MinecraftUiRenderer.screenHeight() - PADDING;
                if (poolY + 34 < contentBottom && hit(
                        localMouseX,
                        localMouseY,
                        teamsLayout.sidebarX(),
                        poolY,
                        teamsLayout.sidebarWidth(),
                        contentBottom - poolY)) {
                    unassignedScrollRows = clampRows(
                            unassignedScrollRows + delta, unassignedOnlineRoster(snapshot).size());
                    return true;
                }
            }
            if (tab == Tab.ZONES) {
                WarMapLayout layout = warMapLayout(
                        viewport.width(), contentTop(), MinecraftUiRenderer.screenHeight() - PADDING);
                if (warMapControls(layout, manager.canManage()).panel().contains(localMouseX, localMouseY)) return true;
                if (layout.containsMap(localMouseX, localMouseY)) {
                    pendingWarQueueClick = null;
                    boolean locked = manager.canManage() && territoriesLocked();
                    MapViewport before = warMapViewport(
                            layout, warMapDisplayedTerritories(snapshot, locked), locked);
                    double zoomFactor = scrollY > 0
                            ? MapViewport.SCROLL_ZOOM_FACTOR
                            : 1 / MapViewport.SCROLL_ZOOM_FACTOR;
                    applyWarMapViewport(before.zoomAt(localMouseX, localMouseY, zoomFactor));
                    return true;
                }
                if (layout.containsSidebar(localMouseX, localMouseY)) {
                    List<ZoneSidebarEntry> entries = zoneSidebarEntries(snapshot, collapsedZoneCategoryIds);
                    float availableHeight = layout.mapHeight()
                            - WAR_MAP_SIDEBAR_CONTENT_TOP
                            - WAR_MAP_SIDEBAR_BOTTOM_PADDING;
                    scrollRows = zoneSidebarScrollStart(scrollRows + delta, entries, availableHeight);
                }
                return true;
            }
            int size = switch (tab) {
                case ROSTER -> snapshot.visibleRoster().size();
                case TEAMS -> snapshot.teams().size();
                case ZONES -> 0;
            };
            scrollRows = tab == Tab.TEAMS
                    ? teamScrollStart(scrollRows + delta, size, teamsLayout == null ? 1 : teamsLayout.columns())
                    : clampRows(scrollRows + delta, size);
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
        UiRenderer.renderResource(canvas -> {
            mapBackground.close();
            telemetryPlayerOverlay.close();
        });
        super.removed();
    }

    private void beginTeamEdit(Team team) {
        closeTeamEditor();
        teamEditorOpen = true;
        flashMessage = null;
        editingTeamId = team == null ? null : team.id();
        teamEditorBase = team == null ? null : TeamEditorBase.from(team);
        teamType = team == null
                ? defaultTeamType(manager.snapshot())
                : team.teamType();
        teamTargets = team == null ? WarCompositionTargets.NONE : team.compositionTargets();
        if (team == null) {
            RosterMember caller = manager.snapshot() == null ? null : manager.snapshot().caller();
            RosterMember initialLeader = caller != null && caller.online() && caller.teamId() == null
                    ? caller
                    : manager.snapshot() == null ? null : manager.snapshot().roster().stream()
                            .filter(member -> member.online() && member.teamId() == null)
                            .findFirst()
                            .orElse(null);
            if (initialLeader != null) {
                teamMembers.add(new TeamMemberDraft(initialLeader.playerUuid()));
            }
        } else {
            WarPlannerSnapshot snapshot = manager.snapshot();
            int staleMembers = 0;
            for (TeamMember member : team.members().stream()
                    .sorted(Comparator.comparingInt(TeamMember::position))
                    .toList()) {
                boolean stillInRoster = snapshot != null && snapshot.roster().stream()
                        .anyMatch(rosterMember -> rosterMember.playerUuid().equalsIgnoreCase(member.playerUuid()));
                if (stillInRoster) {
                    teamMembers.add(new TeamMemberDraft(member.playerUuid()));
                } else {
                    staleMembers++;
                }
            }
            if (staleMembers > 0) {
                flashMessage = staleMembers + " former member" + (staleMembers == 1 ? " was" : "s were")
                        + " removed from this draft. Save to apply.";
            } else if (!team.teamType().editable()) {
                flashMessage = "Choose a supported team type before saving this legacy team.";
            }
        }
    }

    private void closeTeamEditor() {
        teamEditorOpen = false;
        editingTeamId = null;
        teamEditorBase = null;
        teamType = WarTeamType.VLOW_MUNCH;
        teamTargets = WarCompositionTargets.NONE;
        teamTypeMenuOpen = false;
        teamMembers.clear();
        editorScrollRows = 0;
        teamEditorSearchQuery = "";
        teamEditorSearchFocused = false;
        teamEditorSaving = false;
    }

    private void saveTeam() {
        WarPlannerSnapshot snapshot = manager.snapshot();
        if (snapshot == null) return;
        try {
            if (editingTeamId != null) {
                Team current = snapshot.team(editingTeamId);
                if (teamEditorBase == null || !teamEditorBase.matches(current)) {
                    flashMessage = "This team changed while you were editing it. Close and reopen the editor.";
                    return;
                }
            }
            Long version = teamEditorBase == null ? null : teamEditorBase.version();
            TeamDraft draft = new TeamDraft(teamType, version, teamTargets, teamMembers);
            Long id = editingTeamId;
            teamEditorSaving = true;
            manager.saveTeam(id, draft).whenComplete((result, error) -> SeqClient.mc.execute(() -> {
                teamEditorSaving = false;
                if (error != null || result == null || !result.success()) {
                    flashMessage = error != null
                            ? "War planner request failed."
                            : result == null ? "No response from the war planner." : result.message();
                    return;
                }
                flashMessage = result.message();
                closeTeamEditor();
            }));
        } catch (IllegalArgumentException exception) {
            flashMessage = exception.getMessage();
        }
    }

    private void cycleTeamMember(RosterMember member) {
        TeamMemberDraft current = teamMember(member.playerUuid());
        if (current == null) {
            if (teamMembers.size() >= 5) {
                flashMessage = "A war team can contain at most five people.";
                return;
            }
            teamMembers.add(new TeamMemberDraft(member.playerUuid()));
            return;
        }
        teamMembers.remove(current);
    }

    private void saveSupportSlot(int slotIndex, String playerUuid) {
        WarPlannerSnapshot snapshot = manager.snapshot();
        if (snapshot == null || snapshot.support() == null) return;
        String[] codes = {"LEAD", "ECO_1", "ECO_2", "ECO_3"};
        String code = codes[slotIndex];
        List<SupportSlotDraft> slots = new ArrayList<>();
        for (SupportSlot slot : snapshot.support().slots()) {
            if (!code.equals(slot.code()) && !samePlayer(slot.playerUuid(), playerUuid)) {
                slots.add(new SupportSlotDraft(slot.code(), slot.playerUuid()));
            }
        }
        if (playerUuid != null) slots.add(new SupportSlotDraft(code, playerUuid));
        supportEditorSaving = true;
        manager.saveSupport(new SupportDraft(snapshot.support().version(), slots)).whenComplete((result, error) ->
                SeqClient.mc.execute(() -> {
                    supportEditorSaving = false;
                    if (error != null || result == null || !result.success()) {
                        flashMessage = error != null ? "War planner request failed." : result == null ? "No response." : result.message();
                        return;
                    }
                    flashMessage = result.message();
                    closeSupportEditor();
                }));
    }

    private static SupportSlot supportSlot(WarPlannerSnapshot snapshot, int slotIndex) {
        String[] codes = {"LEAD", "ECO_1", "ECO_2", "ECO_3"};
        return snapshot.support().slots().stream()
                .filter(slot -> codes[slotIndex].equals(slot.code()))
                .findFirst()
                .orElse(null);
    }

    private static List<RosterMember> supportCandidates(WarPlannerSnapshot snapshot, int slotIndex) {
        SupportSlot current = supportSlot(snapshot, slotIndex);
        Set<String> assignedElsewhere = snapshot.support().slots().stream()
                .filter(slot -> current == null || !samePlayer(slot.playerUuid(), current.playerUuid()))
                .map(slot -> slot.playerUuid().toLowerCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toSet());
        return snapshot.roster().stream()
                .filter(member -> member.online() || (current != null && samePlayer(member.playerUuid(), current.playerUuid())))
                .filter(member -> !assignedElsewhere.contains(member.playerUuid().toLowerCase(Locale.ROOT)))
                .sorted(Comparator.comparing(RosterMember::displayName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private void closeSupportEditor() {
        editingSupportSlot = null;
        supportEditorScrollRows = 0;
        supportEditorSaving = false;
    }

    private void beginWarPingPicker() {
        if (!manager.canManage() || manager.isMutating()) return;
        if (opacitySlider != null) opacitySlider.onHidden();
        draggingWarMap = false;
        memberDrag = null;
        zoneDrag = null;
        warPingPickerOpen = true;
        warPingSending = false;
        warPingScrollRows = 0;
        warPingSearchQuery = "";
        warPingSearchFocused = true;
        flashMessage = null;
    }

    private void closeWarPingPicker() {
        warPingPickerOpen = false;
        warPingSending = false;
        warPingScrollRows = 0;
        warPingSearchQuery = "";
        warPingSearchFocused = false;
    }

    private void sendWarPing(RosterMember member) {
        if (!canPingPlayer(manager.snapshot(), member) || manager.isMutating()) return;
        warPingSending = true;
        manager.pingPlayer(member.playerUuid()).whenComplete((result, error) -> SeqClient.mc.execute(() -> {
            warPingSending = false;
            if (error != null || result == null || !result.success()) {
                flashMessage = error != null
                        ? "War ping failed."
                        : result == null ? "No response from the war planner." : result.message();
                return;
            }
            closeWarPingPicker();
            flashMessage = result.message();
        }));
    }

    private void beginRoleEdit() {
        WarPlannerSnapshot snapshot = manager.snapshot();
        RosterMember caller = snapshot == null ? null : snapshot.caller();
        if (snapshot == null
                || !snapshot.discordRolesAvailable()
                || caller == null
                || caller.discordId() == null
                || caller.discordId().isBlank()
                || manager.isMutating()) {
            flashMessage = caller != null && (caller.discordId() == null || caller.discordId().isBlank())
                    ? "Link and verify your Discord account before editing war roles."
                    : "Discord roles are temporarily unavailable.";
            return;
        }
        selectedCompositionRoles.clear();
        selectedCompositionRoles.addAll(caller.compositionRoles());
        roleEditorOpen = true;
        roleEditorSaving = false;
        flashMessage = null;
    }

    private void closeRoleEditor() {
        roleEditorOpen = false;
        roleEditorSaving = false;
        selectedCompositionRoles.clear();
    }

    private void saveCompositionRoles() {
        roleEditorSaving = true;
        manager.updateCompositionRoles(WarCompositionRole.ordered(List.copyOf(selectedCompositionRoles)))
                .whenComplete((result, error) -> SeqClient.mc.execute(() -> {
                    roleEditorSaving = false;
                    if (error != null || result == null || !result.success()) {
                        flashMessage = error != null
                                ? "War planner request failed."
                                : result == null ? "No response." : result.message();
                        return;
                    }
                    flashMessage = result.message();
                    closeRoleEditor();
                }));
    }

    private TeamMemberDraft teamMember(String playerUuid) {
        return teamMembers.stream()
                .filter(member -> member.playerUuid().equalsIgnoreCase(playerUuid))
                .findFirst()
                .orElse(null);
    }

    static List<RosterMember> teamEditorRoster(WarPlannerSnapshot snapshot, String searchQuery) {
        if (snapshot == null) {
            return List.of();
        }
        String query = searchQuery == null ? "" : searchQuery.strip().toLowerCase(Locale.ROOT);
        return snapshot.roster().stream()
                .filter(member -> query.isEmpty() || teamEditorSearchText(member).contains(query))
                .sorted(Comparator.<RosterMember>comparingInt(member -> member.online() ? 0 : 1)
                        .thenComparing(Comparator.comparingInt(
                                        (RosterMember member) -> member.compositionRoles().size())
                                .reversed())
                        .thenComparing(RosterMember::displayName, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(RosterMember::displayName)
                        .thenComparing(
                                member -> member.playerUuid() == null ? "" : member.playerUuid(),
                                String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private static String teamEditorSearchText(RosterMember member) {
        return String.join(
                        "\n",
                        member.displayName(),
                        member.minecraftUsername() == null ? "" : member.minecraftUsername(),
                        member.discordUsername() == null ? "" : member.discordUsername(),
                        member.playerUuid() == null ? "" : member.playerUuid())
                .toLowerCase(Locale.ROOT);
    }

    private MemberDrag teamMemberDragAt(
            WarPlannerSnapshot snapshot, float mouseX, float mouseY, float width, float height) {
        TeamsLayout layout = teamsLayout(width, contentTop() + 28, height - PADDING, snapshot.teams().size());
        List<TeamPlacement> placements = teamPlacements(
                snapshot.teams(), scrollRows, layout, manager.canManage(), snapshot.caller() != null);
        for (TeamPlacement placement : placements) {
            if (!placement.contains(mouseX, mouseY)) continue;
            Team team = placement.team();
            TeamActionLayout actions = placement.actions();
            List<TeamMember> members = team.members().stream()
                    .sorted(Comparator.comparingInt(TeamMember::position))
                    .toList();
            for (int memberIndex = 0; memberIndex < members.size(); memberIndex++) {
                float relativeY = actions.memberTop() + memberIndex * TEAM_MEMBER_ROW_STEP;
                if (!placement.fullyShows(relativeY - 6, TEAM_MEMBER_ROW_STEP)) continue;
                float memberY = placement.y() + relativeY;
                float memberRight = memberIndex == 0
                        ? actions.firstMemberRight()
                        : placement.x() + placement.width() - 8;
                float memberX = placement.x() + 8;
                if (hit(mouseX, mouseY, memberX, memberY - 6, Math.max(0, memberRight - memberX), TEAM_MEMBER_ROW_STEP)) {
                    return new MemberDrag(
                            members.get(memberIndex).playerUuid(),
                            team.id(),
                            team.version(),
                            mouseX,
                            mouseY,
                            false);
                }
            }
        }
        return null;
    }

    private MemberDrag unassignedMemberDragAt(
            WarPlannerSnapshot snapshot, float mouseX, float mouseY, float width, float height) {
        TeamsLayout layout = teamsLayout(width, contentTop() + 28, height - PADDING, snapshot.teams().size());
        if (layout.compactAuxiliary()) return null;
        float rowsTop = contentTop() + 28 + UNASSIGNED_POOL_TOP + 36;
        float bottom = height - PADDING;
        if (!hit(
                mouseX,
                mouseY,
                layout.sidebarX() + 6,
                rowsTop,
                layout.sidebarWidth() - 12,
                Math.max(0, bottom - rowsTop))) {
            return null;
        }
        int row = unassignedScrollRows + (int) ((mouseY - rowsTop) / UNASSIGNED_ROW_HEIGHT);
        List<RosterMember> members = unassignedOnlineRoster(snapshot);
        if (row < 0 || row >= members.size()) return null;
        return new MemberDrag(members.get(row).playerUuid(), null, null, mouseX, mouseY, false);
    }

    private void dropTeamMember(MemberDrag drag, float mouseX, float mouseY, float width, float height) {
        WarPlannerSnapshot snapshot = manager.snapshot();
        if (snapshot == null || manager.isMutating()) return;
        Team target = teamAt(snapshot, mouseX, mouseY, width, height);
        if (target != null) {
            if (drag.sourceTeamId() != null && drag.sourceTeamId() == target.id()) return;
            if (target.members().size() >= 5) {
                flashMessage = target.name() + " is full.";
                return;
            }
            moveTeamMember(drag, target);
            return;
        }

        TeamsLayout layout = teamsLayout(width, contentTop() + 28, height - PADDING, snapshot.teams().size());
        if (layout.compactAuxiliary()) return;
        float poolY = contentTop() + 28 + UNASSIGNED_POOL_TOP;
        float contentBottom = height - PADDING;
        if (drag.sourceTeamId() == null
                || poolY + 34 >= contentBottom
                || !hit(
                        mouseX,
                        mouseY,
                        layout.sidebarX(),
                        poolY,
                        layout.sidebarWidth(),
                        contentBottom - poolY)) {
            return;
        }
        moveTeamMember(drag, null);
    }

    private void moveTeamMember(MemberDrag drag, Team target) {
        try {
            TeamMemberMoveDraft draft = teamMemberMoveDraft(
                    drag.sourceTeamId(), drag.sourceVersion(), target);
            showResult(manager.moveTeamMember(drag.playerUuid(), draft));
        } catch (IllegalArgumentException exception) {
            flashMessage = exception.getMessage();
        }
    }

    private Team teamAt(
            WarPlannerSnapshot snapshot, float mouseX, float mouseY, float width, float height) {
        TeamPlacement placement = teamPlacementAt(snapshot, mouseX, mouseY, width, height);
        return placement == null ? null : placement.team();
    }

    private TeamPlacement teamPlacementAt(
            WarPlannerSnapshot snapshot, float mouseX, float mouseY, float width, float height) {
        TeamsLayout layout = teamsLayout(width, contentTop() + 28, height - PADDING, snapshot.teams().size());
        for (TeamPlacement placement : teamPlacements(
                snapshot.teams(), scrollRows, layout, manager.canManage(), snapshot.caller() != null)) {
            if (placement.contains(mouseX, mouseY)) return placement;
        }
        return null;
    }

    static List<RosterMember> unassignedOnlineRoster(WarPlannerSnapshot snapshot) {
        return snapshot.roster().stream()
                .filter(RosterMember::online)
                .filter(member -> member.teamId() == null)
                .sorted(Comparator.comparing(RosterMember::displayName, String.CASE_INSENSITIVE_ORDER))
                .toList();
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
            return "Live · roles unavailable";
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

    private static float renderCompositionIcons(
            UiCanvas canvas, List<WarCompositionRole> compositionRoles, float x, float y) {
        float nextX = x;
        for (WarCompositionRole role : WarCompositionRole.ordered(compositionRoles)) {
            AssetManager.Asset asset = SeqClient.assetManager == null
                    ? null
                    : SeqClient.assetManager.getAsset(role.assetKey());
            if (asset != null && asset.getImage() != null) {
                canvas.drawImage(asset.getImage(), nextX, y, COMPOSITION_ICON_SIZE, COMPOSITION_ICON_SIZE, 1f);
            } else {
                canvas.fillRect(nextX, y, COMPOSITION_ICON_SIZE, COMPOSITION_ICON_SIZE,
                        color(CONTROL_INPUT));
                text(canvas, role.name().substring(0, 1), nextX + COMPOSITION_ICON_SIZE / 2,
                        y + COMPOSITION_ICON_SIZE / 2, 8, color(TEXT_SECONDARY), true);
            }
            nextX += COMPOSITION_ICON_SIZE + COMPOSITION_ICON_GAP;
        }
        return nextX;
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

    private static boolean samePlayer(String left, String right) {
        return left != null && right != null && left.equalsIgnoreCase(right);
    }

    private static int availableCharacters(float left, float right, float fontSize, int maximum) {
        float approximateCharacterWidth = fontSize * .55f;
        return Math.max(1, Math.min(maximum, (int) ((right - left) / approximateCharacterWidth)));
    }

    private static RosterMember rosterMember(WarPlannerSnapshot snapshot, String playerUuid) {
        return snapshot.roster().stream()
                .filter(member -> samePlayer(member.playerUuid(), playerUuid))
                .findFirst()
                .orElse(null);
    }

    static List<WarCompositionRole> teamMemberRoles(WarPlannerSnapshot snapshot, String playerUuid) {
        RosterMember member = rosterMember(snapshot, playerUuid);
        return member == null ? List.of() : member.compositionRoles();
    }

    static int teamCompositionCount(WarPlannerSnapshot snapshot, Team team, WarCompositionRole role) {
        return (int) team.members().stream()
                .filter(member -> teamMemberRoles(snapshot, member.playerUuid()).contains(role))
                .count();
    }

    static String compositionTargetStatus(WarPlannerSnapshot snapshot, Team team) {
        if (!team.compositionTargets().configured()) return "No comp target";
        ArrayList<String> shortages = new ArrayList<>();
        for (WarCompositionRole role : WarCompositionRole.values()) {
            int missing = team.compositionTargets().target(role) - teamCompositionCount(snapshot, team, role);
            if (missing > 0) shortages.add(role.name().substring(0, 1) + missing);
        }
        return shortages.isEmpty() ? "Comp ready" : "Need " + String.join("/", shortages);
    }

    static boolean canChangeOwnTeam(WarPlannerSnapshot snapshot, Team team) {
        RosterMember caller = snapshot == null ? null : snapshot.caller();
        if (caller == null || team == null) return false;
        if (caller.teamId() != null && caller.teamId() == team.id()) return true;
        return team.members().size() < 5;
    }

    static String teamMembershipActionLabel(WarPlannerSnapshot snapshot, Team team) {
        RosterMember caller = snapshot == null ? null : snapshot.caller();
        if (caller == null || team == null) return "Join";
        if (caller.teamId() != null && caller.teamId() == team.id()) return "Leave";
        return caller.teamId() == null ? "Join" : "Switch";
    }

    static float teamSelfActionX(float cardsRight, boolean canManage) {
        return cardsRight - (canManage ? 204 : 72);
    }

    static TeamActionLayout teamActionLayout(float cardsRight, boolean canManage, boolean hasCaller) {
        return teamActionLayout(PADDING, cardsRight, canManage, hasCaller);
    }

    static TeamActionLayout teamActionLayout(
            float cardX, float cardRight, boolean canManage, boolean hasCaller) {
        float firstWideAction = hasCaller
                ? teamSelfActionX(cardRight, canManage)
                : canManage ? cardRight - 132 : cardRight;
        boolean compact = (canManage || hasCaller) && firstWideAction - (cardX + 8) < 110;
        if (!compact) {
            return new TeamActionLayout(
                    cardRight - 132,
                    canManage ? 52 : 0,
                    cardRight - 74,
                    canManage ? 70 : 0,
                    teamSelfActionX(cardRight, canManage),
                    hasCaller ? TEAM_SELF_ACTION_WIDTH : 0,
                    TEAM_ACTION_TOP,
                    TEAM_ACTION_TOP,
                    31,
                    Math.max(cardX + 8, firstWideAction - 6),
                    Math.max(cardX + 8, firstWideAction - 6),
                    0);
        }

        float innerLeft = cardX + 4;
        float innerRight = Math.max(innerLeft + 1, cardRight - 4);
        float availableWidth = innerRight - innerLeft;
        float managerGap = 4;
        float managerWidth = Math.max(1, (availableWidth - managerGap) / 2);
        float managerY = 28;
        float selfY = canManage ? 54 : 28;
        float memberTop = canManage && hasCaller ? 84 : 58;
        return new TeamActionLayout(
                innerLeft,
                canManage ? managerWidth : 0,
                innerLeft + managerWidth + managerGap,
                canManage ? managerWidth : 0,
                innerLeft,
                hasCaller ? availableWidth : 0,
                managerY,
                selfY,
                memberTop,
                cardRight - 6,
                cardRight - 8,
                memberTop - 31);
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

    static boolean canPingPlayer(WarPlannerSnapshot snapshot, RosterMember member) {
        return snapshot != null
                && snapshot.self() != null
                && snapshot.self().canManage()
                && snapshot.self().playerUuid() != null
                && !snapshot.self().playerUuid().isBlank()
                && member != null
                && member.online()
                && member.playerUuid() != null
                && !member.playerUuid().isBlank()
                && member.discordId() != null
                && !member.discordId().isBlank()
                && !samePlayer(snapshot.self().playerUuid(), member.playerUuid());
    }

    static List<RosterMember> pingCandidates(WarPlannerSnapshot snapshot, String searchQuery) {
        if (snapshot == null) return List.of();
        String query = searchQuery == null ? "" : searchQuery.strip().toLowerCase(Locale.ROOT);
        return snapshot.visibleRoster().stream()
                .filter(member -> canPingPlayer(snapshot, member))
                .filter(member -> query.isEmpty() || teamEditorSearchText(member).contains(query))
                .sorted(Comparator.comparing(RosterMember::displayName, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(RosterMember::displayName)
                        .thenComparing(member -> member.playerUuid().toLowerCase(Locale.ROOT)))
                .toList();
    }

    static boolean warPingRowFullyVisible(float rowY, float listBottom) {
        return rowY + 30 <= listBottom;
    }

    static int warPingScrollStart(int requested, int candidateCount) {
        return clampRows(requested, candidateCount);
    }

    static PlannerViewport plannerViewport(float screenWidth) {
        return new PlannerViewport(SequoiaSidebarNavigation.WIDTH,
                Math.max(1, screenWidth - SequoiaSidebarNavigation.WIDTH));
    }

    static float teamSidebarWidth(float width) {
        return Math.min(360, Math.max(220, width * .30f));
    }

    static TeamsLayout teamsLayout(float width, float top, float bottom, int teamCount) {
        float contentHeight = Math.max(0, bottom - top);
        boolean compactAuxiliary = width < TEAM_COMPACT_BREAKPOINT || contentHeight < TEAM_SHORT_CONTENT_HEIGHT;
        if (compactAuxiliary) {
            float desiredSupportHeight = contentHeight < 140
                    ? TEAM_SHORT_SUPPORT_HEIGHT
                    : TEAM_COMPACT_SUPPORT_HEIGHT;
            float supportHeight = Math.max(
                    0,
                    Math.min(desiredSupportHeight, contentHeight - 34 - TEAM_CARD_GAP));
            float cardsTop = top + supportHeight + (supportHeight > 0 ? TEAM_CARD_GAP : 0);
            return new TeamsLayout(
                    PADDING,
                    cardsTop,
                    Math.max(1, width - PADDING * 2),
                    Math.max(cardsTop, bottom),
                    1,
                    true,
                    PADDING,
                    top + 2,
                    Math.max(1, width - PADDING * 2),
                    supportHeight);
        }

        float sidebarWidth = teamSidebarWidth(width);
        float sidebarX = width - PADDING - sidebarWidth;
        float cardsWidth = Math.max(1, sidebarX - PADDING - TEAM_CARD_GAP);
        int columns = width >= TEAM_GRID_BREAKPOINT && teamCount > 1 ? 2 : 1;
        return new TeamsLayout(
                PADDING,
                top,
                cardsWidth,
                Math.max(top, bottom),
                columns,
                false,
                sidebarX,
                top,
                sidebarWidth,
                Math.min(contentHeight, SUPPORT_PANEL_HEIGHT));
    }

    static List<TeamPlacement> teamPlacements(
            List<Team> teams,
            int requestedStart,
            TeamsLayout layout,
            boolean canManage,
            boolean hasCaller) {
        if (teams == null || teams.isEmpty() || layout == null || layout.cardsTop() >= layout.cardsBottom()) {
            return List.of();
        }
        int columns = Math.max(1, Math.min(layout.columns(), teams.size()));
        int start = teamScrollStart(requestedStart, teams.size(), columns);
        float columnGap = columns > 1 ? TEAM_CARD_GAP : 0;
        float cardWidth = Math.max(1, (layout.cardsWidth() - columnGap * (columns - 1)) / columns);
        float[] columnBottoms = new float[columns];
        java.util.Arrays.fill(columnBottoms, layout.cardsTop());
        ArrayList<TeamPlacement> placements = new ArrayList<>();
        for (int index = start; index < teams.size(); index++) {
            int column = 0;
            for (int candidate = 1; candidate < columns; candidate++) {
                if (columnBottoms[candidate] < columnBottoms[column]) column = candidate;
            }
            float y = columnBottoms[column];
            if (y >= layout.cardsBottom()) break;
            float x = layout.cardsX() + column * (cardWidth + columnGap);
            Team team = teams.get(index);
            TeamActionLayout actions = teamActionLayout(x, x + cardWidth, canManage, hasCaller);
            float height = teamCardHeight(team.members().size(), actions);
            float visibleHeight = Math.max(0, Math.min(height, layout.cardsBottom() - y));
            if (visibleHeight <= 0) break;
            placements.add(new TeamPlacement(team, index, x, y, cardWidth, height, visibleHeight, actions));
            columnBottoms[column] = y + height + TEAM_CARD_GAP;
        }
        return List.copyOf(placements);
    }

    static int teamScrollStart(int requestedStart, int teamCount, int columns) {
        int minimumVisible = Math.max(1, columns);
        int maximumStart = Math.max(0, teamCount - minimumVisible);
        return Math.max(0, Math.min(requestedStart, maximumStart));
    }

    static List<SupportPlacement> supportPlacements(TeamsLayout layout) {
        if (layout == null || layout.supportHeight() < 10 || layout.supportWidth() <= 0) return List.of();
        ArrayList<SupportPlacement> placements = new ArrayList<>(4);
        if (!layout.compactAuxiliary()) {
            for (int index = 0; index < 4; index++) {
                placements.add(new SupportPlacement(
                        index,
                        layout.supportX() + 7,
                        layout.supportY() + SUPPORT_ROWS_TOP + index * SUPPORT_ROW_STEP,
                        layout.supportWidth() - 14,
                        SUPPORT_ROW_HEIGHT));
            }
            return List.copyOf(placements);
        }

        boolean twoRows = layout.supportHeight() > TEAM_SHORT_SUPPORT_HEIGHT;
        int columns = twoRows ? 2 : 4;
        int rows = twoRows ? 2 : 1;
        float topInset = twoRows ? 17 : 3;
        float gap = 4;
        float availableWidth = layout.supportWidth() - 12;
        float chipWidth = Math.max(1, (availableWidth - gap * (columns - 1)) / columns);
        float availableHeight = layout.supportHeight() - topInset - 3;
        float chipHeight = Math.max(1, (availableHeight - gap * (rows - 1)) / rows);
        for (int index = 0; index < 4; index++) {
            int row = index / columns;
            int column = index % columns;
            placements.add(new SupportPlacement(
                    index,
                    layout.supportX() + 6 + column * (chipWidth + gap),
                    layout.supportY() + topInset + row * (chipHeight + gap),
                    chipWidth,
                    chipHeight));
        }
        return List.copyOf(placements);
    }

    static float teamEditorWidth(float width) {
        return Math.max(1, Math.min(560, width - PADDING * 2));
    }

    static AvailabilityLayout availabilityLayout(float width) {
        if (width >= 520) return new AvailabilityLayout(Math.max(155, width - 360), 13, 58, 76, 6, false);
        float gap = 4;
        float buttonWidth = Math.max(1, (width - PADDING * 2 - gap * 4) / 5);
        return new AvailabilityLayout(PADDING, 23, buttonWidth, buttonWidth, gap, true);
    }

    static int backgroundOpacityPercent() {
        return SeqClient.getWarPlannerBackgroundOpacitySetting() == null
                ? 100
                : SeqClient.getWarPlannerBackgroundOpacitySetting().getValue();
    }

    static boolean resourceColorsEnabled() {
        return SeqClient.getWarPlannerResourceColorsSetting() != null
                && SeqClient.getWarPlannerResourceColorsSetting().getValue();
    }

    static boolean warMapPlayersEnabled() {
        return SeqClient.getWarPlannerShowPlayersSetting() == null
                || SeqClient.getWarPlannerShowPlayersSetting().getValue();
    }

    static boolean territoriesLocked() {
        return SeqClient.getWarPlannerLockTerritoriesSetting() != null
                && SeqClient.getWarPlannerLockTerritoriesSetting().getValue();
    }

    // This is an explicit user opacity control, not a hard-coded replacement for theme alpha.
    static Color plannerBackground(Color source) {
        int alpha = opacityAlpha(source.getAlpha(), backgroundOpacityPercent());
        return new Color(source.getRed(), source.getGreen(), source.getBlue(), alpha);
    }

    static int opacityAlpha(int sourceAlpha, int opacityPercent) {
        return Math.round(Math.max(0, Math.min(255, sourceAlpha))
                * Math.max(0, Math.min(100, opacityPercent))
                / 100f);
    }

    static boolean shouldBlurBackground(int opacityPercent) {
        return opacityPercent >= 100;
    }

    static WarTeamType defaultTeamType(WarPlannerSnapshot snapshot) {
        return teamTypeSelectable(snapshot, WarTeamType.HQ, null) ? WarTeamType.HQ : WarTeamType.VLOW_MUNCH;
    }

    static boolean teamTypeSelectable(WarPlannerSnapshot snapshot, WarTeamType teamType, Long editingTeamId) {
        if (teamType == null || !teamType.editable()) return false;
        if (teamType != WarTeamType.HQ || snapshot == null) return true;
        return snapshot.teams().stream()
                .filter(team -> editingTeamId == null || team.id() != editingTeamId)
                .noneMatch(team -> team.teamType() == WarTeamType.HQ);
    }

    static String automaticTeamName(WarPlannerSnapshot snapshot, WarTeamType teamType, Long editingTeamId) {
        if (snapshot != null && editingTeamId != null) {
            Team editing = snapshot.team(editingTeamId);
            if (editing != null && editing.teamType() == teamType) {
                return editing.name();
            }
        }
        if (teamType == null || !teamType.editable()) return "Unsupported legacy team";
        if (teamType == WarTeamType.HQ) return "HQ Team";
        Set<String> names = snapshot == null
                ? Set.of()
                : snapshot.teams().stream()
                        .filter(team -> editingTeamId == null || team.id() != editingTeamId)
                        .map(Team::name)
                        .filter(java.util.Objects::nonNull)
                        .map(name -> name.toLowerCase(Locale.ROOT))
                        .collect(java.util.stream.Collectors.toSet());
        for (int number = 1; number < Integer.MAX_VALUE; number++) {
            String candidate = teamType.namePrefix() + number;
            if (!names.contains(candidate.toLowerCase(Locale.ROOT))) {
                return candidate;
            }
        }
        return teamType.label();
    }

    static TeamMemberMoveDraft teamMemberMoveDraft(
            Long sourceTeamId, Long sourceVersion, Team target) {
        return new TeamMemberMoveDraft(
                sourceTeamId,
                sourceVersion,
                target == null ? null : target.id(),
                target == null ? null : target.version());
    }

    static float teamCardHeight(int memberCount) {
        return Math.max(80, 36 + Math.max(0, Math.min(5, memberCount)) * TEAM_MEMBER_ROW_STEP);
    }

    static float teamCardHeight(int memberCount, TeamActionLayout actions) {
        return teamCardHeight(memberCount) + (actions == null ? 0 : actions.extraHeight());
    }

    static float compactRoleX(float textX, float textWidth, float rightEdge, float iconWidth) {
        return Math.min(textX + textWidth + 4, rightEdge - iconWidth);
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

    static HeaderControls headerControls(float width) {
        float sectionWidth = width < 420 ? 68 : 116;
        float actionWidth = width < 420 ? 50 : 64;
        var section = new WarMapButtonBounds(PADDING, 6, sectionWidth, 18);
        var roles = new WarMapButtonBounds(section.x() + sectionWidth + 6, 6, actionWidth, 18);
        var refresh = new WarMapButtonBounds(roles.x() + actionWidth + 6, 6, actionWidth, 18);
        return new HeaderControls(section, roles, refresh);
    }

    static WarMapButtonBounds dropdownOption(WarMapButtonBounds trigger, int index) {
        return new WarMapButtonBounds(trigger.x(), trigger.y() + trigger.height() + index * 22, trigger.width(), 22);
    }

    static WarMapControls warMapControls(WarMapLayout layout, boolean canManage) {
        float panelWidth = Math.min(240, Math.max(1, layout.mapWidth() - 16));
        float panelHeight = (canManage ? 3 : 2) * 24 + 44;
        float x = layout.mapX() + layout.mapWidth() - panelWidth - 8;
        float y = Math.max(layout.mapY() + 34, layout.mapY() + layout.mapHeight() - panelHeight - 8);
        var panel = new WarMapButtonBounds(x, y, panelWidth, panelHeight);
        var lock = new WarMapButtonBounds(x, y, panelWidth, canManage ? 24 : 0);
        var queues = new WarMapButtonBounds(x, y + (canManage ? 24 : 0), panelWidth, 24);
        var players = new WarMapButtonBounds(x, queues.y() + 24, panelWidth, 24);
        var opacity = new WarMapButtonBounds(x, players.y() + 24, panelWidth, 40);
        var coloring = new WarMapButtonBounds(layout.sidebarX() + 6, layout.mapY() + 2, layout.sidebarWidth() - 12, 22);
        var fit = new WarMapButtonBounds(layout.mapX() + 8, layout.mapY() + 8, Math.min(64, layout.mapWidth() - 16), 22);
        return new WarMapControls(fit, queues, players, lock, panel, opacity, coloring);
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

    private static float previewX(double worldX, MapBounds fitted, float offsetX, float scale) {
        return offsetX + (float) ((worldX - fitted.minX()) * scale);
    }

    private static float previewY(double worldZ, MapBounds fitted, float offsetY, float scale) {
        return offsetY + (float) ((worldZ - fitted.minZ()) * scale);
    }

    private static String teamName(WarPlannerSnapshot snapshot, Long id) {
        Team team = snapshot.team(id);
        return team == null ? "Team #" + id : team.name();
    }

    static String formatDuration(Duration duration) {
        long seconds = Math.max(0, duration.getSeconds());
        long totalMinutes = Math.max(1, (seconds + 59) / 60);
        long hours = totalMinutes / 60;
        long minutes = totalMinutes % 60;
        if (hours == 0) return minutes + "m";
        return minutes == 0 ? hours + "h" : hours + "h " + minutes + "m";
    }

    private static Color parseColor(String value, Color fallback) {
        try {
            return Color.decode(value == null ? "" : value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static float contentTop() {
        return HEADER_HEIGHT + AVAILABILITY_HEIGHT + 8;
    }

    private static int clampRows(int value, int size) {
        return Math.max(0, Math.min(value, Math.max(0, size - 1)));
    }

    private enum ButtonTone { SECONDARY, PRIMARY, DANGER, QUIET_DANGER }

    private void primaryButton(UiCanvas canvas, float x, float y, float width, float height, String label, boolean disabled) {
        button(canvas, x, y, width, height, label, ButtonTone.PRIMARY, disabled);
    }

    private void destructiveButton(UiCanvas canvas, float x, float y, float width, float height,
            String label, boolean confirming, boolean disabled) {
        button(canvas, x, y, width, height, label, confirming ? ButtonTone.DANGER : ButtonTone.QUIET_DANGER, disabled);
    }

    private void button(UiCanvas canvas, float x, float y, float width, float height, String label, boolean danger, boolean disabled) {
        button(canvas, x, y, width, height, label, danger ? ButtonTone.DANGER : ButtonTone.SECONDARY, disabled);
    }

    private void button(UiCanvas canvas, float x, float y, float width, float height, String label, ButtonTone tone, boolean disabled) {
        boolean hovered = !disabled && hit(nvgMouseX, nvgMouseY, x, y, width, height);
        Color background = disabled ? color(ACCENT_DISABLED) : switch (tone) {
            case PRIMARY -> color(hovered ? ACCENT_PRIMARY_HOVER : ACCENT_PRIMARY);
            case DANGER -> color(hovered ? CONTROL_DANGER_HOVER : CONTROL_DANGER);
            case QUIET_DANGER -> color(hovered ? CONTROL_DANGER_HOVER : CONTROL_INPUT);
            case SECONDARY -> color(hovered ? CONTROL_INPUT_HOVER : CONTROL_INPUT);
        };
        canvas.fillRect(x, y, width, height, background);
        text(canvas, label, x + width / 2, y + height / 2, 10,
                color(disabled ? TEXT_DISABLED : tone == ButtonTone.QUIET_DANGER && !hovered ? CONTROL_DANGER_HOVER : TEXT_PRIMARY), true);
    }

    private static void text(UiCanvas canvas, String value, float x, float y, float size, Color textColor, boolean centered) {
        text(canvas, value, x, y, size, textColor,
                centered ? UiCanvas.HorizontalAlign.CENTER : UiCanvas.HorizontalAlign.LEFT);
    }

    private static void text(
            UiCanvas canvas,
            String value,
            float x,
            float y,
            float size,
            Color textColor,
            UiCanvas.HorizontalAlign alignment) {
        canvas.drawText(value == null ? "" : value, x, y, new UiCanvas.TextStyle(
                SeqClient.getFontManager().getSelectedFont(), size, textColor,
                alignment,
                UiCanvas.VerticalAlign.MIDDLE));
    }

    private static boolean hit(float mx, float my, float x, float y, float width, float height) {
        return mx >= x && mx <= x + width && my >= y && my <= y + height;
    }

    private static String truncate(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, Math.max(0, max - 1)) + "…";
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

    record TeamActionLayout(
            float editX,
            float editWidth,
            float deleteX,
            float deleteWidth,
            float selfX,
            float selfWidth,
            float managerY,
            float selfY,
            float memberTop,
            float titleRight,
            float firstMemberRight,
            float extraHeight) {}

    record TeamsLayout(
            float cardsX,
            float cardsTop,
            float cardsWidth,
            float cardsBottom,
            int columns,
            boolean compactAuxiliary,
            float supportX,
            float supportY,
            float supportWidth,
            float supportHeight) {
        float sidebarX() {
            return supportX;
        }

        float sidebarWidth() {
            return supportWidth;
        }
    }

    record TeamPlacement(
            Team team,
            int index,
            float x,
            float y,
            float width,
            float height,
            float visibleHeight,
            TeamActionLayout actions) {
        boolean contains(float mouseX, float mouseY) {
            return hit(mouseX, mouseY, x, y, width, visibleHeight);
        }

        boolean fullyShows(float relativeY, float itemHeight) {
            return relativeY >= 0 && itemHeight >= 0 && relativeY + itemHeight <= visibleHeight;
        }
    }

    record SupportPlacement(int index, float x, float y, float width, float height) {}

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

    record HeaderControls(WarMapButtonBounds section, WarMapButtonBounds roles, WarMapButtonBounds refresh) {}

    record WarMapControls(WarMapButtonBounds fit, WarMapButtonBounds queues, WarMapButtonBounds players,
            WarMapButtonBounds lock, WarMapButtonBounds panel, WarMapButtonBounds opacity, WarMapButtonBounds coloring) {}

    record WarQueueLabelBounds(float x, float y, float width, float height) {}

    record WarQueueMapMarker(TerritoryQueue queue, GuildTerritory territory) {}

    private record WarQueueTooltipLine(String text, int detailIndex) {}

    record TeamEditorBase(long teamId, Long version, WarTeamType teamType, List<String> memberUuids) {
        TeamEditorBase {
            memberUuids = List.copyOf(memberUuids);
        }

        static TeamEditorBase from(Team team) {
            return new TeamEditorBase(
                    team.id(),
                    team.version(),
                    team.teamType(),
                    orderedMemberUuids(team));
        }

        boolean matches(Team team) {
            return team != null
                    && team.id() == teamId
                    && java.util.Objects.equals(team.version(), version)
                    && team.teamType() == teamType
                    && orderedMemberUuids(team).equals(memberUuids);
        }

        private static List<String> orderedMemberUuids(Team team) {
            return team.members().stream()
                    .sorted(Comparator.comparingInt(TeamMember::position))
                    .map(TeamMember::playerUuid)
                    .toList();
        }
    }

    private record PendingDelete(long id, Long version) {}

    record PendingWarQueueClick(
            long queueId, String territory, float mouseX, float mouseY, long clickedAtMillis) {}

    private record MemberDrag(
            String playerUuid,
            Long sourceTeamId,
            Long sourceVersion,
            float startX,
            float startY,
            boolean active) {}

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

}
