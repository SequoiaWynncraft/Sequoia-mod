package com.seqwawa.seq.ui;

import static com.seqwawa.seq.managers.ThemeManager.color;
import static com.seqwawa.seq.ui.theme.UiColor.*;

import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.managers.AssetManager;
import com.seqwawa.seq.managers.WarPlannerManager;
import com.seqwawa.seq.map.GatheringMapImageService;
import com.seqwawa.seq.map.GuildTerritory;
import com.seqwawa.seq.map.GuildTerritoryIndex;
import com.seqwawa.seq.map.GuildTerritoryService;
import com.seqwawa.seq.map.MapCalibration;
import com.seqwawa.seq.map.MapBounds;
import com.seqwawa.seq.map.MapViewport;
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
import com.seqwawa.seq.utils.rendering.MinecraftUiRenderer;
import com.seqwawa.seq.utils.rendering.UiCanvas;
import com.seqwawa.seq.utils.rendering.UiImage;
import com.seqwawa.seq.utils.rendering.UiRenderer;
import java.awt.Color;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

/** Seq-only war management overlay. Authorization is supplied solely by the protected snapshot. */
public final class WarPlannerScreen extends Screen {
    private static final float PADDING = 12;
    private static final float HEADER_HEIGHT = 38;
    private static final float AVAILABILITY_HEIGHT = 48;
    private static final float TAB_HEIGHT = 24;
    private static final float ROW_HEIGHT = 38;
    private static final float TEAM_MEMBER_ROW_STEP = 11;
    private static final float TEAM_ACTION_TOP = 8;
    private static final float TEAM_SELF_ACTION_WIDTH = 68;
    private static final float SUPPORT_PANEL_HEIGHT = 142;
    private static final float SUPPORT_ROWS_TOP = 35;
    private static final float SUPPORT_ROW_STEP = 26;
    private static final float SUPPORT_ROW_HEIGHT = 21;
    private static final float UNASSIGNED_POOL_TOP = 150;
    private static final float UNASSIGNED_ROW_HEIGHT = 20;
    private static final float WAR_MAP_SIDEBAR_GAP = 8;
    private static final float WAR_MAP_ZONE_ROW_HEIGHT = 62;
    private static final float WAR_MAP_ZONE_ROW_STEP = 66;
    private static final float WAR_MAP_CATEGORY_ROW_HEIGHT = 26;
    private static final float WAR_MAP_CATEGORY_ROW_STEP = 30;
    private static final float WAR_MAP_SIDEBAR_CONTENT_TOP = 34;
    private static final float WAR_MAP_SIDEBAR_BOTTOM_PADDING = 6;
    private static final double WAR_MAP_MIN_ZOOM = .015;
    private static final double WAR_MAP_MAX_ZOOM = 1.8;
    private static final float BUTTON_HEIGHT = 22;
    private static final float MANAGER_ACTION_WIDTH = 92;
    private static final float MAX_ROSTER_WIDTH = 680;
    private static final float MAX_TEAMS_WIDTH = 780;
    private static final float MAX_ZONES_WIDTH = 900;
    private static final float COMPOSITION_ICON_SIZE = 12;
    private static final float COMPOSITION_ICON_GAP = 3;
    private static final float HQ_ICON_WIDTH = 24;
    private static final float HQ_ICON_HEIGHT = 19.5f;
    private static final float DISPLAY_CONTROL_GAP = 6;
    private static final float OPACITY_CONTROL_WIDTH = 132;
    private static final float RESOURCE_CONTROL_WIDTH = 104;
    private static final float LOCK_CONTROL_WIDTH = 110;

    private final Screen parent;
    private final WarPlannerManager manager;
    private final GuildTerritoryIndex territoryIndex;
    private final GatheringMapImageService mapImageService = GatheringMapImageService.getInstance();
    private UiImage zonePreviewMapImage;
    private long loadedMapImageVersion = -1;
    private Tab tab = Tab.ROSTER;
    private float nvgMouseX;
    private float nvgMouseY;
    private int scrollRows;
    private String flashMessage;

    private Long editingTeamId;
    private TeamEditorBase teamEditorBase;
    private boolean teamEditorOpen;
    private WarTeamType teamType = WarTeamType.VLOW_MUNCH;
    private WarCompositionTargets teamTargets = WarCompositionTargets.NONE;
    private boolean teamTypeMenuOpen;
    private final List<TeamMemberDraft> teamMembers = new ArrayList<>();
    private boolean teamEditorSaving;
    private int editorScrollRows;
    private PendingDelete pendingDeleteTeam;
    private PendingDelete pendingDeleteZone;
    private PendingDelete pendingDeleteZoneCategory;
    private Integer editingSupportSlot;
    private int supportEditorScrollRows;
    private boolean supportEditorSaving;
    private boolean draggingBackgroundOpacity;
    private boolean draggingWarMap;
    private boolean warMapFitted;
    private double warMapCenterX;
    private double warMapCenterZ;
    private double warMapPixelsPerBlock;
    private float fittedWarMapWidth = -1;
    private float fittedWarMapHeight = -1;
    private final Set<Long> hiddenZoneIds = new java.util.HashSet<>();
    private final Set<Long> hiddenZoneCategoryIds = new java.util.HashSet<>();
    private final Set<Long> collapsedZoneCategoryIds = new java.util.HashSet<>();
    private GuildTerritory hoveredWarMapTerritory;
    private MemberDrag memberDrag;
    private ZoneDrag zoneDrag;
    private int unassignedScrollRows;
    private boolean roleEditorOpen;
    private boolean roleEditorSaving;
    private final EnumSet<WarCompositionRole> selectedCompositionRoles =
            EnumSet.noneOf(WarCompositionRole.class);

    public WarPlannerScreen(Screen parent) {
        super(Component.literal("War Planner"));
        this.parent = parent;
        this.manager = SeqClient.getWarPlannerManager();
        GuildTerritoryService.getInstance().loadBundledTerritories();
        this.territoryIndex = GuildTerritoryService.getInstance().index();
        mapImageService.requestLoad();
    }

    @Override
    public void tick() {
        if (manager == null || manager.state() == WarPlannerManager.State.FORBIDDEN || manager.snapshot() == null) {
            SeqClient.mc.setScreen(parent);
        }
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

    private void renderPlanner(UiCanvas canvas) {
        float screenWidth = canvas.metrics().width();
        float height = canvas.metrics().height();
        PlannerViewport viewport = activePlannerViewport(screenWidth);
        canvas.fillRect(0, 0, screenWidth, height, plannerBackground(color(BACKGROUND_BODY_OPAQUE)));
        canvas.fillRect(0, 0, screenWidth, HEADER_HEIGHT, plannerBackground(color(BACKGROUND_HEADER)));
        float screenMouseX = nvgMouseX;
        nvgMouseX -= viewport.x();
        canvas.save();
        canvas.translate(viewport.x(), 0);
        try {
            float width = viewport.width();
            text(canvas, "War Planner", PADDING, HEADER_HEIGHT / 2, 19, color(ACCENT_PRIMARY), false);
            if (width >= 520) {
                text(canvas, stateLabel(), width - 300, HEADER_HEIGHT / 2, 11, stateColor(), false);
            }
            WarPlannerSnapshot current = manager.snapshot();
            RosterMember caller = current == null ? null : current.caller();
            boolean rolesEditable = current != null
                    && current.discordRolesAvailable()
                    && caller != null
                    && caller.discordId() != null
                    && !caller.discordId().isBlank();
            button(canvas, width - 158, 8, 70, BUTTON_HEIGHT, "My roles", false,
                    manager.isMutating() || !rolesEditable);
            button(canvas, width - 82, 8, 70, BUTTON_HEIGHT, "Refresh", false, manager.isMutating());

            renderAvailability(canvas, width);
            renderTabs(canvas, width);
            renderContent(canvas, width, height);
            DisplayControls controls = renderDisplayControls(canvas, width, height);

            if (flashMessage != null && !flashMessage.isBlank()) {
                boolean stacked = controls.left() < PADDING + 100;
                float messageY = stacked ? height - 64 : height - 34;
                float messageWidth = stacked
                        ? Math.min(width - PADDING * 2, 480)
                        : Math.max(80, Math.min(480, controls.left() - PADDING - DISPLAY_CONTROL_GAP));
                canvas.fillRoundedRect(PADDING, messageY, messageWidth, 24, 5,
                        plannerBackground(color(BACKGROUND_POPUP)));
                text(canvas, truncate(flashMessage, 68), PADDING + 8, messageY + 12, 11,
                        color(manager.lastError() == null ? TEXT_SECONDARY : CONTROL_WARNING), false);
            } else if (manager.lastError() != null) {
                boolean stacked = controls.left() < PADDING + 100;
                float messageY = stacked ? height - 64 : height - 34;
                float messageWidth = stacked
                        ? Math.min(width - PADDING * 2, 480)
                        : Math.max(80, Math.min(480, controls.left() - PADDING - DISPLAY_CONTROL_GAP));
                canvas.fillRoundedRect(PADDING, messageY, messageWidth, 24, 5,
                        plannerBackground(color(STATUS_WARNING_BACKGROUND)));
                text(canvas, truncate(manager.lastError(), 68), PADDING + 8, messageY + 12, 11,
                        color(TEXT_PRIMARY), false);
            }

            if (roleEditorOpen) {
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

    private void renderAvailability(UiCanvas canvas, float width) {
        float y = HEADER_HEIGHT;
        canvas.fillRect(0, y, width, AVAILABILITY_HEIGHT, plannerBackground(color(BACKGROUND_CONTENT)));
        WarPlannerSnapshot snapshot = manager.snapshot();
        RosterMember caller = snapshot == null ? null : snapshot.caller();
        Duration remaining = manager.ownAvailabilityRemaining();
        String status = caller != null && caller.available() && !remaining.isZero()
                ? "Available for " + formatDuration(remaining)
                : "Unavailable";
        String roleLabel = caller == null ? "No composition role" : compositionLabel(caller.compositionRoles());
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
                    labels[index], index == 4, manager.isMutating());
        }
    }

    private DisplayControls renderDisplayControls(UiCanvas canvas, float width, float height) {
        DisplayControls controls = displayControls(width, manager.canManage());
        float y = height - 34;
        canvas.fillRoundedRect(
                controls.opacityX(), y, controls.opacityWidth(), 24, 4, plannerBackground(color(BACKGROUND_CONTENT)));
        int opacity = backgroundOpacityPercent();
        float labelWidth = controls.opacityWidth() < OPACITY_CONTROL_WIDTH ? 45 : 65;
        text(
                canvas,
                (controls.opacityWidth() < OPACITY_CONTROL_WIDTH ? "BG " : "Opacity ") + opacity + "%",
                controls.opacityX() + 5,
                y + 12,
                9,
                color(TEXT_SECONDARY),
                false);
        float trackX = controls.opacityX() + labelWidth;
        float trackY = y + 10;
        float trackWidth = controls.opacityWidth() - labelWidth - 7;
        canvas.fillRect(trackX, trackY, trackWidth, 4, color(CONTROL_INPUT_SECONDARY));
        float fillWidth = trackWidth * opacity / 100f;
        canvas.fillRect(trackX, trackY, fillWidth, 4, color(ACCENT_PRIMARY));
        canvas.fillCircle(trackX + fillWidth, trackY + 2, 4, color(TEXT_PRIMARY));

        button(
                canvas,
                controls.resourceX(),
                y + 1,
                controls.resourceWidth(),
                BUTTON_HEIGHT,
                controls.resourceWidth() < RESOURCE_CONTROL_WIDTH
                        ? resourceColorsEnabled() ? "Res ✓" : "Resources"
                        : resourceColorsEnabled() ? "Resources ✓" : "Resource colors",
                false,
                false);
        if (manager.canManage()) {
            button(
                    canvas,
                    controls.lockX(),
                    y + 1,
                    controls.lockWidth(),
                    BUTTON_HEIGHT,
                    controls.lockWidth() < LOCK_CONTROL_WIDTH
                            ? territoriesLocked() ? "Locked ✓" : "Lock terrs"
                            : territoriesLocked() ? "Territories locked" : "Lock territories",
                    false,
                    false);
        }
        return controls;
    }

    private void renderTabs(UiCanvas canvas, float width) {
        float y = HEADER_HEIGHT + AVAILABILITY_HEIGHT;
        float tabWidth = tabWidth(width, manager.canManage());
        int index = 0;
        for (Tab candidate : Tab.values()) {
            float x = PADDING + tabWidth * index++;
            canvas.fillRect(x, y, tabWidth - 4, TAB_HEIGHT,
                    plannerBackground(color(candidate == tab ? ACCENT_PRIMARY_DARK : CONTROL_INPUT)));
            text(canvas, candidate.label, x + (tabWidth - 4) / 2, y + TAB_HEIGHT / 2, 12,
                    color(TEXT_PRIMARY), true);
        }
        if (manager.canManage() && tab == Tab.TEAMS) {
            button(canvas, width - 92, y + 1, 80, BUTTON_HEIGHT,
                    "New team", false, manager.isMutating());
        }
    }

    private void renderContent(UiCanvas canvas, float width, float height) {
        WarPlannerSnapshot snapshot = manager.snapshot();
        if (snapshot == null) {
            text(canvas, manager.state() == WarPlannerManager.State.LOADING ? "Loading…" : "No planner data",
                    width / 2, 160, 14, color(TEXT_MUTED), true);
            return;
        }
        float top = contentTop();
        float bottom = height - 42;
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
            float pingX = rosterPingButtonX(width);
            float statusX = pingX - 116;
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
            button(canvas, pingX, y + 8, 100, BUTTON_HEIGHT, "Ping war chat", false,
                    manager.isMutating() || !canPingPlayer(snapshot, member));
        }
    }

    private void renderTeams(UiCanvas canvas, WarPlannerSnapshot snapshot, float width, float top, float bottom) {
        float supportWidth = teamSidebarWidth(width);
        float cardsRight = width - supportWidth - PADDING * 2;
        float cardWidth = cardsRight - PADDING;
        renderSupportBoard(canvas, snapshot, cardsRight + PADDING, top, supportWidth, bottom);
        if (manager.canManage()) {
            renderUnassignedPool(canvas, snapshot, cardsRight + PADDING, top, supportWidth, bottom);
        }
        if (snapshot.teams().isEmpty()) {
            String message = manager.canManage()
                    ? "No war teams yet. Use New team to create one."
                    : "No war teams yet · View only (manager access required).";
            text(canvas, message, PADDING, top + 22, 13, color(TEXT_MUTED), false);
            return;
        }
        int start = Math.min(scrollRows, Math.max(0, snapshot.teams().size() - 1));
        float y = top;
        RosterMember caller = snapshot.caller();
        TeamActionLayout actions = teamActionLayout(cardsRight, manager.canManage(), caller != null);
        for (int index = start; index < snapshot.teams().size(); index++) {
            Team team = snapshot.teams().get(index);
            float cardHeight = teamCardHeight(team.members().size(), actions);
            if (y + cardHeight > bottom) break;
            boolean ownTeam = caller != null && caller.teamId() != null && caller.teamId() == team.id();
            boolean dropTarget = memberDrag != null
                    && memberDrag.active()
                    && hit(nvgMouseX, nvgMouseY, PADDING, y + 1, cardWidth, cardHeight - 2);
            canvas.fillRoundedRect(PADDING, y + 1, cardWidth, cardHeight - 2, 4,
                    plannerBackground(color(dropTarget
                            ? CONTROL_INPUT_HOVER
                            : ownTeam ? ACCENT_PRIMARY_DARK : BACKGROUND_CONTENT)));
            String targetSuffix = team.compositionTargets().configured()
                    ? " · " + compositionTargetStatus(snapshot, team)
                    : "";
            String title = team.name() + (ownTeam ? " · Your team" : "") + " · " + team.members().size() + "/5"
                    + targetSuffix;
            text(canvas, truncate(title, availableCharacters(PADDING + 8, actions.titleRight(), 13, 32)),
                    PADDING + 8, y + 13, 13, color(TEXT_PRIMARY), false);
            List<TeamMember> members = team.members().stream()
                    .sorted(Comparator.comparingInt(TeamMember::position))
                    .toList();
            float memberY = y + actions.memberTop();
            for (int memberIndex = 0; memberIndex < members.size(); memberIndex++) {
                TeamMember member = members.get(memberIndex);
                String displayName = member.minecraftUsername() == null ? member.playerUuid() : member.minecraftUsername();
                List<WarCompositionRole> roles = teamMemberRoles(snapshot, member.playerUuid());
                float iconWidth = roles.size() * COMPOSITION_ICON_SIZE
                        + Math.max(0, roles.size() - 1) * COMPOSITION_ICON_GAP;
                float textX = PADDING + 12;
                float rightEdge = memberIndex == 0 ? actions.firstMemberRight() : cardsRight - 8;
                String memberLabel = truncate(
                        displayName,
                        availableCharacters(textX, rightEdge - iconWidth - 4, 10, 24));
                float labelWidth = UiRenderer.measureText(
                                memberLabel, SeqClient.getFontManager().getSelectedFont(), 10)
                        .width();
                float rolesX = compactRoleX(textX, labelWidth, rightEdge, iconWidth);
                text(canvas, memberLabel, textX, memberY, 10, color(TEXT_SECONDARY), false);
                renderCompositionIcons(canvas, roles, rolesX, memberY - COMPOSITION_ICON_SIZE / 2);
                memberY += TEAM_MEMBER_ROW_STEP;
            }
            if (manager.canManage()) {
                button(canvas, actions.editX(), y + actions.managerY(), actions.editWidth(), BUTTON_HEIGHT,
                        "Edit", false, manager.isMutating());
                boolean confirming = pendingDeleteTeam != null && pendingDeleteTeam.id() == team.id();
                button(canvas, actions.deleteX(), y + actions.managerY(), actions.deleteWidth(), BUTTON_HEIGHT,
                        confirming ? "Confirm" : "Delete", true, manager.isMutating());
            }
            if (caller != null) {
                button(canvas, actions.selfX(), y + actions.selfY(), actions.selfWidth(), BUTTON_HEIGHT,
                        teamMembershipActionLabel(snapshot, team), false,
                        manager.isMutating() || !canChangeOwnTeam(snapshot, team));
            }
            y += cardHeight + 4;
        }
        if (memberDrag != null && memberDrag.active()) {
            RosterMember member = rosterMember(snapshot, memberDrag.playerUuid());
            String label = member == null ? memberDrag.playerUuid() : member.displayName();
            canvas.fillRoundedRect(nvgMouseX + 8, nvgMouseY - 10, Math.max(74, label.length() * 6 + 16), 20, 4,
                    color(ACCENT_PRIMARY_DARK));
            text(canvas, truncate(label, 22), nvgMouseX + 16, nvgMouseY, 10, color(TEXT_PRIMARY), false);
        }
    }

    private void renderZones(UiCanvas canvas, WarPlannerSnapshot snapshot, float width, float top, float bottom) {
        WarMapLayout layout = warMapLayout(width, top, bottom);
        renderWarMap(canvas, snapshot, layout);
        renderWarMapSidebar(canvas, snapshot, layout.sidebarX(), top, layout.sidebarWidth(), bottom);
    }

    private void renderWarMap(UiCanvas canvas, WarPlannerSnapshot snapshot, WarMapLayout layout) {
        float x = layout.mapX();
        float y = layout.mapY();
        float width = layout.mapWidth();
        float height = layout.mapHeight();
        hoveredWarMapTerritory = null;
        canvas.fillRoundedRect(x, y, width, height, 3, plannerBackground(color(CONTROL_INPUT)));
        List<GuildTerritory> allMapTerritories = territoryIndex.territories();
        boolean locked = manager.canManage() && territoriesLocked();
        List<Zone> displayedZones = visibleZones(
                snapshot.zones(), hiddenZoneIds, hiddenZoneCategoryIds);
        List<GuildTerritory> coreTerritories = visibleMapTerritories(
                allMapTerritories, displayedZones, locked);
        if (coreTerritories.isEmpty()) {
            text(canvas, "Map unavailable", x + width / 2, y + height / 2, 9, color(TEXT_MUTED), true);
            return;
        }
        Map<String, GuildTerritory> byName = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        allMapTerritories.forEach(territory -> byName.put(territory.name(), territory));
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
        MapBounds coordinateBounds = mapImageBounds();
        float scale = (float) viewport.pixelsPerBlock();
        float offsetX = viewport.worldToScreenX(coordinateBounds.minX());
        float offsetY = viewport.worldToScreenZ(coordinateBounds.minZ());
        canvas.scissor(x, y, width, height);
        UiImage mapImage = zonePreviewMapImage();
        if (mapImage != null) {
            MapBounds imageBounds = mapImageBounds();
            float mapX = previewX(imageBounds.minX(), coordinateBounds, offsetX, scale);
            float mapY = previewY(imageBounds.minZ(), coordinateBounds, offsetY, scale);
            float mapWidth = (float) ((imageBounds.maxX() - imageBounds.minX()) * scale);
            float mapHeight = (float) ((imageBounds.maxZ() - imageBounds.minZ()) * scale);
            canvas.drawImage(mapImage, mapX, mapY, mapWidth, mapHeight, .9f * backgroundOpacityPercent() / 100f);
            Color tint = color(BACKGROUND_BODY_OPAQUE);
            canvas.fillRect(x, y, width, height,
                    plannerBackground(new Color(tint.getRed(), tint.getGreen(), tint.getBlue(), 24)));
        }
        if (resourceColorsEnabled()) {
            drawPreviewResources(canvas, coreTerritories, details, coordinateBounds, offsetX, offsetY, scale);
        }
        drawPreviewConnections(
                canvas,
                coreTerritories,
                byName,
                details,
                displayedNames,
                coordinateBounds,
                offsetX,
                offsetY,
                scale);
        if (hoveredWarMapTerritory != null) {
            drawPreviewFill(
                    canvas,
                    hoveredWarMapTerritory,
                    coordinateBounds,
                    offsetX,
                    offsetY,
                    scale,
                    new Color(91, 195, 255, 82));
        }
        Color mapColor = color(TEXT_MUTED);
        drawPreviewOutlines(
                canvas,
                coreTerritories,
                coordinateBounds,
                offsetX,
                offsetY,
                scale,
                new Color(mapColor.getRed(), mapColor.getGreen(), mapColor.getBlue(), 72),
                .55f,
                0);
        if (!contextTerritories.isEmpty()) {
            drawPreviewOutlines(
                    canvas,
                    contextTerritories,
                    coordinateBounds,
                    offsetX,
                    offsetY,
                    scale,
                    new Color(mapColor.getRed(), mapColor.getGreen(), mapColor.getBlue(), 155),
                    .75f,
                    0);
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
                    new Color(zoneColor.getRed(), zoneColor.getGreen(), zoneColor.getBlue(), 235),
                    1.8f,
                    1);
        }
        GuildTerritory hqTerritory = snapshot.hqTerritory() == null ? null : byName.get(snapshot.hqTerritory());
        if (hqTerritory != null && displayedNames.contains(hqTerritory.name().toLowerCase(Locale.ROOT))) {
            Color hqColor = new Color(255, 205, 74, 255);
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
        if (hoveredWarMapTerritory != null) {
            drawTerritoryName(canvas, hoveredWarMapTerritory, coordinateBounds, offsetX, offsetY, scale, layout);
        }
        if (hqTerritory != null && displayedNames.contains(hqTerritory.name().toLowerCase(Locale.ROOT))) {
            drawHqIcon(canvas, hqTerritory, coordinateBounds, offsetX, offsetY, scale);
        }
        canvas.resetScissor();
        if (!locked) {
            button(canvas, layout.mapX() + 6, layout.mapY() + 6, 44, BUTTON_HEIGHT, "Fit", false, false);
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

    private MapViewport warMapViewport(
            WarMapLayout layout, List<GuildTerritory> displayedTerritories, boolean locked) {
        if (locked) {
            return fittedWarMapViewport(zonePreviewBounds(displayedTerritories), layout);
        }
        if (!warMapFitted
                || fittedWarMapWidth != layout.mapWidth()
                || fittedWarMapHeight != layout.mapHeight()) {
            MapViewport fitted = fittedWarMapViewport(mapImageBounds(), layout);
            warMapCenterX = fitted.centerX();
            warMapCenterZ = fitted.centerZ();
            warMapPixelsPerBlock = fitted.pixelsPerBlock();
            fittedWarMapWidth = layout.mapWidth();
            fittedWarMapHeight = layout.mapHeight();
            warMapFitted = true;
        }
        return new MapViewport(
                warMapCenterX,
                warMapCenterZ,
                warMapPixelsPerBlock,
                layout.mapX(),
                layout.mapY(),
                layout.mapWidth(),
                layout.mapHeight());
    }

    private void resetWarMapViewport(WarMapLayout layout) {
        MapViewport fitted = fittedWarMapViewport(mapImageBounds(), layout);
        warMapCenterX = fitted.centerX();
        warMapCenterZ = fitted.centerZ();
        warMapPixelsPerBlock = fitted.pixelsPerBlock();
        fittedWarMapWidth = layout.mapWidth();
        fittedWarMapHeight = layout.mapHeight();
        warMapFitted = true;
    }

    static MapViewport fittedWarMapViewport(MapBounds bounds, WarMapLayout layout) {
        double scale = Math.max(WAR_MAP_MIN_ZOOM, Math.min(WAR_MAP_MAX_ZOOM, Math.min(
                (layout.mapWidth() - 10) / Math.max(1, bounds.maxX() - bounds.minX()),
                (layout.mapHeight() - 10) / Math.max(1, bounds.maxZ() - bounds.minZ()))));
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
        canvas.fillRoundedRect(x, top, width, bottom - top, 5, plannerBackground(color(BACKGROUND_CONTENT)));
        if (manager.canManage()) {
            float actionWidth = (width - 18) / 2;
            button(canvas, x + 6, top + 7, actionWidth, 20, "+ Category", false, manager.isMutating());
            button(canvas, x + 12 + actionWidth, top + 7, actionWidth, 20, "+ Zone", false, manager.isMutating());
        } else {
            text(canvas, "Click visibility controls", x + 10, top + 16, 8, color(TEXT_MUTED), false);
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
            canvas.fillRoundedRect(nvgMouseX + 8, nvgMouseY - 10, 100, 20, 4, color(ACCENT_PRIMARY_DARK));
            text(canvas, truncate(zoneDrag.zoneName(), 15), nvgMouseX + 16, nvgMouseY,
                    10, color(TEXT_PRIMARY), false);
        }
    }

    private void renderZoneCategoryRow(
            UiCanvas canvas, ZoneSidebarEntry entry, float x, float rowY, float width) {
        boolean displayed = !hiddenZoneCategoryIds.contains(entry.categoryId());
        boolean collapsed = containsCategory(collapsedZoneCategoryIds, entry.categoryId());
        canvas.fillRoundedRect(x + 6, rowY, width - 12, WAR_MAP_CATEGORY_ROW_HEIGHT, 4,
                plannerBackground(color(CONTROL_INPUT)));
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
            button(canvas, x + width - 28, rowY + 3, 22, 20, confirming ? "?" : "X", true,
                    manager.isMutating());
        } else {
            button(canvas, x + width - 48, rowY + 3, 42, 20, displayed ? "Hide" : "Show", false, false);
        }
    }

    private void renderZoneRow(
            UiCanvas canvas, WarPlannerSnapshot snapshot, Zone zone, float x, float rowY, float width) {
        boolean categoryDisplayed = !containsCategory(hiddenZoneCategoryIds, zone.categoryId());
        boolean displayed = categoryDisplayed && !hiddenZoneIds.contains(zone.id());
        Color zoneColor = parseColor(zone.color(), color(ACCENT_PRIMARY));
        canvas.fillRoundedRect(x + 6, rowY, width - 12, WAR_MAP_ZONE_ROW_HEIGHT, 4,
                plannerBackground(color(BACKGROUND_CONTENT_FOCUSED)));
        canvas.fillRect(x + 11, rowY + 7, 5, 24, displayed ? zoneColor : alpha(zoneColor, 70));
        text(canvas, truncate(zone.name(), 22), x + 22, rowY + 13, 11,
                color(displayed ? TEXT_PRIMARY : TEXT_MUTED), false);
        String assigned = zone.assignedTeamIds().isEmpty()
                ? "No parties"
                : zone.assignedTeamIds().stream()
                        .map(id -> teamName(snapshot, id))
                        .reduce((left, right) -> left + " + " + right)
                        .orElse("No parties");
        text(canvas, truncate(zone.territories().size() + " terrs · " + assigned, 29),
                x + 22, rowY + 29, 9, color(TEXT_MUTED), false);
        if (manager.canManage()) {
            float actionWidth = (width - 32) / 3;
            button(canvas, x + 12, rowY + 35, actionWidth, BUTTON_HEIGHT, "Edit", false, manager.isMutating());
            button(canvas, x + 16 + actionWidth, rowY + 35, actionWidth, BUTTON_HEIGHT,
                    categoryDisplayed ? displayed ? "Hide" : "Show" : "Group off", false, !categoryDisplayed);
            boolean confirming = pendingDeleteZone != null && pendingDeleteZone.id() == zone.id();
            button(canvas, x + 20 + actionWidth * 2, rowY + 35, actionWidth, BUTTON_HEIGHT,
                    confirming ? "Sure?" : "Delete", true, manager.isMutating());
        } else {
            button(canvas, x + 22, rowY + 35, 56, BUTTON_HEIGHT,
                    categoryDisplayed ? displayed ? "Hide" : "Show" : "Group off", false, !categoryDisplayed);
        }
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
            float scale) {
        for (GuildTerritory territory : territories) {
            MapBounds bounds = territory.bounds();
            float territoryX = previewX(bounds.minX(), fitted, offsetX, scale);
            float territoryY = previewY(bounds.minZ(), fitted, offsetY, scale);
            float territoryWidth = Math.max(2, (float) ((bounds.maxX() - bounds.minX()) * scale));
            float territoryHeight = Math.max(2, (float) ((bounds.maxZ() - bounds.minZ()) * scale));
            WarTerritoryPickerScreen.renderResourceFill(
                    canvas, territoryX, territoryY, territoryWidth, territoryHeight, details.get(territory.name()));
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
        canvas.fillRoundedRect(labelX, labelY, labelWidth, 20, 4, new Color(22, 76, 105, 225));
        text(canvas, truncate(territory.name(), 34), labelX + labelWidth / 2, labelY + 10,
                9, new Color(190, 232, 255), true);
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
        for (GuildTerritory territory : territories) {
            MapBounds bounds = territory.bounds();
            float territoryX = previewX(bounds.minX(), fitted, offsetX, scale) - outset;
            float territoryY = previewY(bounds.minZ(), fitted, offsetY, scale) - outset;
            float territoryWidth = Math.max(2, (float) ((bounds.maxX() - bounds.minX()) * scale)) + outset * 2;
            float territoryHeight = Math.max(2, (float) ((bounds.maxZ() - bounds.minZ()) * scale)) + outset * 2;
            canvas.strokeRect(territoryX, territoryY, territoryWidth, territoryHeight, strokeWidth, stroke);
        }
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
            float scale) {
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
                float startX = previewX(territory.centerX(), fitted, offsetX, scale);
                float startY = previewY(territory.centerZ(), fitted, offsetY, scale);
                float endX = previewX(linked.centerX(), fitted, offsetX, scale);
                float endY = previewY(linked.centerZ(), fitted, offsetY, scale);
                canvas.strokeLine(startX, startY, endX, endY, 1.6f, alpha(color(BACKGROUND_BODY_OPAQUE), 210));
                canvas.strokeLine(startX, startY, endX, endY, .75f, alpha(foreground, 235));
            }
        }
    }

    private void renderSupportBoard(
            UiCanvas canvas, WarPlannerSnapshot snapshot, float x, float top, float panelWidth, float bottom) {
        canvas.fillRoundedRect(
                x,
                top + 2,
                panelWidth,
                Math.min(bottom - top - 4, SUPPORT_PANEL_HEIGHT),
                5,
                plannerBackground(color(BACKGROUND_CONTENT)));
        text(canvas, "Shared support", x + 10, top + 17, 13, color(ACCENT_PRIMARY), false);
        text(canvas, "Click a slot · may join party", x + 10, top + 29, 9, color(TEXT_MUTED), false);
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
            canvas.fillRoundedRect(x + 7, y, panelWidth - 14, SUPPORT_ROW_HEIGHT, 3,
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
        canvas.fillRoundedRect(x, poolY, panelWidth, bottom - poolY, 5,
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
            boolean hovered = memberDrag == null && hit(nvgMouseX, nvgMouseY, x + 6, rowY, panelWidth - 12, 18);
            canvas.fillRoundedRect(x + 6, rowY, panelWidth - 12, 18, 3,
                    plannerBackground(color(hovered ? CONTROL_INPUT_HOVER : CONTROL_INPUT)));
            text(canvas, truncate(member.displayName(), 19), x + 12, rowY + 9, 10, color(TEXT_SECONDARY), false);
            renderCompositionIcons(canvas, member.compositionRoles(), x + panelWidth - 54, rowY + 3);
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
        canvas.fillRect(0, 0, width, height, plannerBackground(color(BACKGROUND_MODAL_OVERLAY)));
        canvas.fillRoundedRect(x, y, w, h, 7, plannerBackground(color(BACKGROUND_BODY_OPAQUE)));
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
            canvas.fillRoundedRect(x + 12, rowY + 2, w - 24, 25, 3,
                    plannerBackground(color(selected ? ACCENT_PRIMARY_DARK : BACKGROUND_CONTENT)));
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
        canvas.fillRect(0, 0, width, height, plannerBackground(color(BACKGROUND_MODAL_OVERLAY)));
        canvas.fillRoundedRect(x, y, w, h, 7, plannerBackground(color(BACKGROUND_BODY_OPAQUE)));
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
            canvas.fillRoundedRect(optionX, optionY, optionWidth, 42, 4,
                    plannerBackground(color(selected ? ACCENT_PRIMARY_DARK : BACKGROUND_CONTENT)));
            canvas.strokeRect(optionX, optionY, optionWidth, 42, 1,
                    color(selected ? ACCENT_PRIMARY : CONTROL_BORDER));
            renderCompositionIcons(canvas, List.of(role), optionX + 10, optionY + 8);
            text(canvas, (selected ? "✓ " : "") + role.label(), optionX + 30, optionY + 15, 11,
                    color(selected ? TEXT_PRIMARY : TEXT_SECONDARY), false);
            text(canvas, selected ? "Selected" : "Not selected", optionX + 10, optionY + 32, 8,
                    color(TEXT_MUTED), false);
        }
        button(canvas, x + w - 154, y + h - 34, 66, BUTTON_HEIGHT, "Cancel", false, roleEditorSaving);
        button(canvas, x + w - 80, y + h - 34, 66, BUTTON_HEIGHT,
                roleEditorSaving ? "Saving…" : "Save", false, roleEditorSaving);
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
        canvas.fillRect(0, 0, width, height, plannerBackground(color(BACKGROUND_MODAL_OVERLAY)));
        canvas.fillRoundedRect(x, y, w, h, 7, plannerBackground(color(BACKGROUND_BODY_OPAQUE)));
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
        List<RosterMember> eligible = editableRoster(snapshot);
        float listTop = fieldY + 90;
        float listBottom = y + h - 42;
        canvas.scissor(x + 8, listTop, w - 16, Math.max(0, listBottom - listTop));
        int start = Math.min(editorScrollRows, Math.max(0, eligible.size() - 1));
        float rowY = listTop;
        for (int index = start; index < eligible.size() && rowY + 28 <= listBottom; index++, rowY += 28) {
            RosterMember member = eligible.get(index);
            TeamMemberDraft selected = teamMember(member.playerUuid());
            canvas.fillRect(x + 12, rowY + 2, w - 24, 24,
                    plannerBackground(color(selected == null ? BACKGROUND_CONTENT : ACCENT_PRIMARY_DARK)));
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
        button(canvas, x + w - 78, y + h - 32, 66, BUTTON_HEIGHT,
                teamEditorSaving ? "Saving…" : "Save", false, teamEditorSaving);
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
        if (click.button() == 1) {
            PlannerViewport viewport = activePlannerViewport(MinecraftUiRenderer.screenWidth());
            float mx = MinecraftUiRenderer.mouseX(click.x()) - viewport.x();
            float my = MinecraftUiRenderer.mouseY(click.y());
            WarPlannerSnapshot snapshot = manager == null ? null : manager.snapshot();
            if (!roleEditorOpen
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
        PlannerViewport viewport = activePlannerViewport(MinecraftUiRenderer.screenWidth());
        float mx = MinecraftUiRenderer.mouseX(click.x()) - viewport.x();
        float my = MinecraftUiRenderer.mouseY(click.y());
        float width = viewport.width();
        float height = MinecraftUiRenderer.screenHeight();

        if (roleEditorOpen) {
            return clickRoleEditor(mx, my, width, height);
        }
        if (teamEditorOpen) {
            return clickTeamEditor(mx, my, width, height);
        }
        if (editingSupportSlot != null) {
            return clickSupportEditor(mx, my, width, height);
        }
        DisplayControls controls = displayControls(width, manager.canManage());
        float controlsY = height - 34;
        if (hit(mx, my, controls.resourceX(), controlsY + 1, controls.resourceWidth(), BUTTON_HEIGHT)) {
            SeqClient.getWarPlannerResourceColorsSetting()
                    .setValue(!resourceColorsEnabled());
            SeqClient.getConfigManager().save();
            return true;
        }
        if (manager.canManage()
                && hit(mx, my, controls.lockX(), controlsY + 1, controls.lockWidth(), BUTTON_HEIGHT)) {
            SeqClient.getWarPlannerLockTerritoriesSetting()
                    .setValue(!territoriesLocked());
            warMapFitted = false;
            draggingWarMap = false;
            SeqClient.getConfigManager().save();
            return true;
        }
        if (hit(
                mx,
                my,
                controls.opacityX() + (controls.opacityWidth() < OPACITY_CONTROL_WIDTH ? 40 : 58),
                controlsY,
                controls.opacityWidth() - (controls.opacityWidth() < OPACITY_CONTROL_WIDTH ? 40 : 58),
                24)) {
            draggingBackgroundOpacity = true;
            updateBackgroundOpacity(mx, controls);
            return true;
        }
        if (hit(mx, my, width - 82, 8, 70, BUTTON_HEIGHT)) {
            showResult(manager.refreshNow());
            return true;
        }
        if (hit(mx, my, width - 158, 8, 70, BUTTON_HEIGHT)) {
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
            showResult(manager.clearAvailability());
            return true;
        }

        float tabsY = HEADER_HEIGHT + AVAILABILITY_HEIGHT;
        float tabWidth = tabWidth(width, manager.canManage());
        for (int index = 0; index < Tab.values().length; index++) {
            if (hit(mx, my, PADDING + tabWidth * index, tabsY, tabWidth - 4, TAB_HEIGHT)) {
                tab = Tab.values()[index];
                scrollRows = 0;
                pendingDeleteTeam = null;
                pendingDeleteZone = null;
                pendingDeleteZoneCategory = null;
                return true;
            }
        }
        if (manager.canManage()
                && tab == Tab.TEAMS
                && hit(mx, my, width - 92, tabsY + 1, 80, BUTTON_HEIGHT)) {
            beginTeamEdit(null);
            return true;
        }
        return clickContent(mx, my, width, height) || super.mouseClicked(click, outsideScreen);
    }

    private boolean rightClickZoneName(
            WarPlannerSnapshot snapshot, float mx, float my, float width, float height) {
        float top = contentTop();
        WarMapLayout layout = warMapLayout(width, top, height - 42);
        ZoneSidebarPlacement placement = zoneSidebarPlacementAt(
                snapshot, collapsedZoneCategoryIds, scrollRows, top, height - 42, my);
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
        WarMapLayout layout = warMapLayout(width, contentTop(), height - 42);
        if (!layout.containsMap(mx, my)) return false;
        boolean locked = manager.canManage() && territoriesLocked();
        List<GuildTerritory> allTerritories = territoryIndex.territories();
        List<GuildTerritory> coreTerritories = visibleMapTerritories(
                allTerritories,
                visibleZones(snapshot.zones(), hiddenZoneIds, hiddenZoneCategoryIds),
                locked);
        Map<String, WarPlannerSnapshot.TerritoryDetails> details = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        snapshot.territoryDetails().forEach(detail -> details.put(detail.name(), detail));
        ArrayList<GuildTerritory> displayedTerritories = new ArrayList<>(coreTerritories);
        if (locked) {
            displayedTerritories.addAll(oneHopContextTerritories(allTerritories, coreTerritories, details));
        }
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
        if (snapshot == null || my < contentTop() || my > height - 42) {
            return false;
        }
        if (tab == Tab.TEAMS && manager.canManage()) {
            float supportWidth = teamSidebarWidth(width);
            float supportX = width - supportWidth - PADDING;
            for (int index = 0; index < 4; index++) {
                if (hit(
                        mx,
                        my,
                        supportX + 7,
                        contentTop() + SUPPORT_ROWS_TOP + index * SUPPORT_ROW_STEP,
                        supportWidth - 14,
                        SUPPORT_ROW_HEIGHT)) {
                    editingSupportSlot = index;
                    supportEditorScrollRows = 0;
                    return true;
                }
            }
            if (!manager.isMutating()) {
                MemberDrag candidate = teamMemberDragAt(snapshot, mx, my, width);
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
        if (tab == Tab.ROSTER) {
            int row = scrollRows + Math.max(0, (int) ((my - contentTop()) / ROW_HEIGHT));
            List<RosterMember> roster = sortedWarRoster(snapshot);
            if (row < roster.size()) {
                RosterMember member = roster.get(row);
                float rowY = contentTop() + (row - scrollRows) * ROW_HEIGHT;
                if (hit(mx, my, rosterPingButtonX(width), rowY + 8, 100, BUTTON_HEIGHT)
                        && canPingPlayer(snapshot, member)
                        && !manager.isMutating()) {
                    showResult(manager.pingPlayer(member.playerUuid()));
                    return true;
                }
            }
            return false;
        }
        if (tab == Tab.TEAMS) {
            TeamPlacement placement = teamPlacementAt(snapshot, my, width);
            if (placement == null) return false;
            float supportWidth = teamSidebarWidth(width);
            float cardsRight = width - supportWidth - PADDING * 2;
            Team team = placement.team();
            float rowY = placement.y();
            RosterMember caller = snapshot.caller();
            TeamActionLayout actions = teamActionLayout(cardsRight, manager.canManage(), caller != null);
            if (caller != null
                    && hit(mx, my, actions.selfX(), rowY + actions.selfY(), actions.selfWidth(), BUTTON_HEIGHT)) {
                if (canChangeOwnTeam(snapshot, team) && !manager.isMutating()) {
                    boolean ownTeam = caller.teamId() != null && caller.teamId() == team.id();
                    showResult(ownTeam ? manager.leaveTeam() : manager.joinTeam(team.id()));
                }
                return true;
            }
            if (manager.canManage()
                    && hit(mx, my, actions.editX(), rowY + actions.managerY(), actions.editWidth(), BUTTON_HEIGHT)) {
                beginTeamEdit(team);
                return true;
            }
            if (manager.canManage()
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
        WarMapLayout layout = warMapLayout(width, top, MinecraftUiRenderer.screenHeight() - 42);
        boolean locked = manager.canManage() && territoriesLocked();
        if (!locked && hit(mx, my, layout.mapX() + 6, layout.mapY() + 6, 44, BUTTON_HEIGHT)) {
            resetWarMapViewport(layout);
            return true;
        }
        if (layout.containsMap(mx, my)) {
            if (!locked) draggingWarMap = true;
            return true;
        }
        float sidebarWidth = layout.sidebarWidth();
        float sidebarX = layout.sidebarX();
        if (manager.canManage()) {
            float headerActionWidth = (sidebarWidth - 18) / 2;
            if (hit(mx, my, sidebarX + 6, top + 7, headerActionWidth, 20)) {
                SeqClient.mc.setScreen(new WarZoneCategoryEditorScreen(this, null));
                return true;
            }
            if (hit(mx, my, sidebarX + 12 + headerActionWidth, top + 7, headerActionWidth, 20)) {
                SeqClient.mc.setScreen(new WarTerritoryPickerScreen(this, null));
                return true;
            }
        }
        ZoneSidebarPlacement placement = zoneSidebarPlacementAt(
                snapshot,
                collapsedZoneCategoryIds,
                scrollRows,
                top,
                MinecraftUiRenderer.screenHeight() - 42,
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
        if (!manager.canManage()) {
            if (hit(mx, my, sidebarX + 22, rowY + 35, 56, BUTTON_HEIGHT)) {
                if (!containsCategory(hiddenZoneCategoryIds, zone.categoryId())) toggleZoneDisplay(zone.id());
                return true;
            }
            return false;
        }
        float actionWidth = (sidebarWidth - 32) / 3;
        if (hit(mx, my, sidebarX + 12, rowY + 35, actionWidth, BUTTON_HEIGHT)) {
            SeqClient.mc.setScreen(new WarTerritoryPickerScreen(this, zone));
            return true;
        }
        if (hit(mx, my, sidebarX + 16 + actionWidth, rowY + 35, actionWidth, BUTTON_HEIGHT)) {
            if (!containsCategory(hiddenZoneCategoryIds, zone.categoryId())) toggleZoneDisplay(zone.id());
            return true;
        }
        if (hit(mx, my, sidebarX + 20 + actionWidth * 2, rowY + 35, actionWidth, BUTTON_HEIGHT)) {
            if (pendingDeleteZone != null && pendingDeleteZone.id() == zone.id()) {
                showResult(manager.deleteZone(zone.id(), pendingDeleteZone.version()));
                pendingDeleteZone = null;
            } else {
                pendingDeleteZone = new PendingDelete(zone.id(), zone.version());
            }
            return true;
        }
        if (!manager.isMutating() && hit(mx, my, sidebarX + 10, rowY + 2, sidebarWidth - 20, 30)) {
            zoneDrag = new ZoneDrag(zone.id(), zone.name(), zone.version(), mx, my, false);
            return true;
        }
        return true;
    }

    private void toggleZoneDisplay(long zoneId) {
        if (!hiddenZoneIds.remove(zoneId)) hiddenZoneIds.add(zoneId);
    }

    private void toggleZoneCategoryDisplay(Long categoryId) {
        if (!hiddenZoneCategoryIds.remove(categoryId)) hiddenZoneCategoryIds.add(categoryId);
    }

    private void toggleZoneCategoryFold(Long categoryId) {
        if (!collapsedZoneCategoryIds.remove(categoryId)) collapsedZoneCategoryIds.add(categoryId);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent click, double deltaX, double deltaY) {
        if (draggingBackgroundOpacity && click.button() == 0) {
            PlannerViewport viewport = activePlannerViewport(MinecraftUiRenderer.screenWidth());
            float mx = MinecraftUiRenderer.mouseX(click.x()) - viewport.x();
            updateBackgroundOpacity(mx, displayControls(viewport.width(), manager.canManage()));
            return true;
        }
        if (draggingWarMap && click.button() == 0) {
            warMapCenterX -= MinecraftUiRenderer.mouseDelta(deltaX) / warMapPixelsPerBlock;
            warMapCenterZ -= MinecraftUiRenderer.mouseDelta(deltaY) / warMapPixelsPerBlock;
            return true;
        }
        if (memberDrag != null && click.button() == 0) {
            PlannerViewport viewport = activePlannerViewport(MinecraftUiRenderer.screenWidth());
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
            PlannerViewport viewport = activePlannerViewport(MinecraftUiRenderer.screenWidth());
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
        if (click.button() == 0 && draggingBackgroundOpacity) {
            draggingBackgroundOpacity = false;
            SeqClient.getConfigManager().save();
            return true;
        }
        if (click.button() == 0 && draggingWarMap) {
            draggingWarMap = false;
            return true;
        }
        if (click.button() == 0 && memberDrag != null) {
            PlannerViewport viewport = activePlannerViewport(MinecraftUiRenderer.screenWidth());
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
            PlannerViewport viewport = activePlannerViewport(MinecraftUiRenderer.screenWidth());
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
        WarMapLayout layout = warMapLayout(width, contentTop(), height - 42);
        if (!layout.containsSidebar(mx, my)) return;
        ZoneSidebarPlacement placement = zoneSidebarPlacementAt(
                snapshot, collapsedZoneCategoryIds, scrollRows, contentTop(), height - 42, my);
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
        float listTop = fieldY + 90;
        float listBottom = y + h - 42;
        if (my >= listTop && my <= listBottom) {
            List<RosterMember> eligible = editableRoster(snapshot);
            int row = editorScrollRows + (int) ((my - listTop) / 28);
            if (row >= 0 && row < eligible.size()) {
                cycleTeamMember(eligible.get(row));
            }
            return true;
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
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int delta = scrollY > 0 ? -1 : 1;
        WarPlannerSnapshot snapshot = manager.snapshot();
        if (snapshot == null) return true;
        if (roleEditorOpen) {
            return true;
        } else if (teamEditorOpen) {
            editorScrollRows = clampRows(editorScrollRows + delta, editableRoster(snapshot).size());
        } else if (editingSupportSlot != null) {
            supportEditorScrollRows = clampRows(
                    supportEditorScrollRows + delta, supportCandidates(snapshot, editingSupportSlot).size());
        } else {
            PlannerViewport viewport = activePlannerViewport(MinecraftUiRenderer.screenWidth());
            float localMouseX = MinecraftUiRenderer.mouseX(mouseX) - viewport.x();
            float localMouseY = MinecraftUiRenderer.mouseY(mouseY);
            float supportWidth = teamSidebarWidth(viewport.width());
            float supportX = viewport.width() - supportWidth - PADDING;
            if (tab == Tab.TEAMS
                    && manager.canManage()
                    && localMouseX >= supportX
                    && localMouseY >= contentTop() + UNASSIGNED_POOL_TOP) {
                unassignedScrollRows = clampRows(
                        unassignedScrollRows + delta, unassignedOnlineRoster(snapshot).size());
                return true;
            }
            if (tab == Tab.ZONES) {
                WarMapLayout layout = warMapLayout(
                        viewport.width(), contentTop(), MinecraftUiRenderer.screenHeight() - 42);
                if (layout.containsMap(localMouseX, localMouseY)) {
                    if (manager.canManage() && territoriesLocked()) return true;
                    MapViewport before = warMapViewport(layout, territoryIndex.territories(), false);
                    double anchorX = before.screenToWorldX(localMouseX);
                    double anchorZ = before.screenToWorldZ(localMouseY);
                    double zoomFactor = scrollY > 0 ? 1.15 : 1 / 1.15;
                    warMapPixelsPerBlock = Math.max(
                            WAR_MAP_MIN_ZOOM, Math.min(WAR_MAP_MAX_ZOOM, warMapPixelsPerBlock * zoomFactor));
                    warMapCenterX = anchorX
                            - (localMouseX - (layout.mapX() + layout.mapWidth() / 2)) / warMapPixelsPerBlock;
                    warMapCenterZ = anchorZ
                            - (localMouseY - (layout.mapY() + layout.mapHeight() / 2)) / warMapPixelsPerBlock;
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
            scrollRows = clampRows(scrollRows + delta, size);
        }
        return true;
    }

    @Override
    public void onClose() {
        SeqClient.mc.setScreen(parent);
    }

    @Override
    public void removed() {
        UiRenderer.renderResource(canvas -> {
            if (zonePreviewMapImage != null) {
                UiRenderer.deleteImage(zonePreviewMapImage);
                zonePreviewMapImage = null;
            }
        });
        super.removed();
    }

    private UiImage zonePreviewMapImage() {
        long version = mapImageService.version();
        if (zonePreviewMapImage != null && loadedMapImageVersion == version) return zonePreviewMapImage;
        if (zonePreviewMapImage != null) UiRenderer.deleteImage(zonePreviewMapImage);
        zonePreviewMapImage = null;
        loadedMapImageVersion = version;
        try {
            byte[] bytes = mapImageService.imageBytes();
            if (bytes.length > 0) {
                zonePreviewMapImage = UiRenderer.createImage(ByteBuffer.wrap(bytes), true);
            }
        } catch (RuntimeException exception) {
            SeqClient.LOGGER.warn("[WarPlanner] Could not load zone-preview map image.", exception);
        }
        return zonePreviewMapImage;
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

    private List<RosterMember> editableRoster(WarPlannerSnapshot snapshot) {
        return snapshot.teamCandidates(editingTeamId).stream()
                .sorted(Comparator.comparing(RosterMember::displayName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private MemberDrag teamMemberDragAt(
            WarPlannerSnapshot snapshot, float mouseX, float mouseY, float width) {
        float supportWidth = teamSidebarWidth(width);
        float cardsRight = width - supportWidth - PADDING * 2;
        if (mouseX < PADDING || mouseX > cardsRight - 6) return null;
        int start = Math.min(scrollRows, Math.max(0, snapshot.teams().size() - 1));
        float teamY = contentTop();
        RosterMember caller = snapshot.caller();
        TeamActionLayout actions = teamActionLayout(cardsRight, manager.canManage(), caller != null);
        for (int index = start; index < snapshot.teams().size(); index++) {
            Team team = snapshot.teams().get(index);
            List<TeamMember> members = team.members().stream()
                    .sorted(Comparator.comparingInt(TeamMember::position))
                    .toList();
            for (int memberIndex = 0; memberIndex < members.size(); memberIndex++) {
                float memberY = teamY + actions.memberTop() + memberIndex * TEAM_MEMBER_ROW_STEP;
                float memberRight = memberIndex == 0 ? actions.firstMemberRight() : cardsRight - 6;
                if (hit(mouseX, mouseY, PADDING + 8, memberY - 6, memberRight - PADDING - 8, 11)) {
                    return new MemberDrag(
                            members.get(memberIndex).playerUuid(),
                            team.id(),
                            team.version(),
                            mouseX,
                            mouseY,
                            false);
                }
            }
            teamY += teamCardHeight(team.members().size(), actions) + 4;
        }
        return null;
    }

    private MemberDrag unassignedMemberDragAt(
            WarPlannerSnapshot snapshot, float mouseX, float mouseY, float width, float height) {
        float supportWidth = teamSidebarWidth(width);
        float supportX = width - supportWidth - PADDING;
        float rowsTop = contentTop() + UNASSIGNED_POOL_TOP + 36;
        float bottom = height - 42;
        if (!hit(mouseX, mouseY, supportX + 6, rowsTop, supportWidth - 12, Math.max(0, bottom - rowsTop))) {
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
        Team target = teamAt(snapshot, mouseX, mouseY, width);
        if (target != null) {
            if (drag.sourceTeamId() != null && drag.sourceTeamId() == target.id()) return;
            if (target.members().size() >= 5) {
                flashMessage = target.name() + " is full.";
                return;
            }
            moveTeamMember(drag, target);
            return;
        }

        float supportWidth = teamSidebarWidth(width);
        float supportX = width - supportWidth - PADDING;
        float poolY = contentTop() + UNASSIGNED_POOL_TOP;
        if (drag.sourceTeamId() == null
                || !hit(mouseX, mouseY, supportX, poolY, supportWidth, Math.max(0, height - 42 - poolY))) {
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

    private Team teamAt(WarPlannerSnapshot snapshot, float mouseX, float mouseY, float width) {
        float supportWidth = teamSidebarWidth(width);
        float cardsRight = width - supportWidth - PADDING * 2;
        if (mouseX < PADDING || mouseX > cardsRight - PADDING || mouseY < contentTop()) return null;
        TeamPlacement placement = teamPlacementAt(snapshot, mouseY, width);
        return placement != null
                        && hit(mouseX, mouseY, PADDING, placement.y() + 1,
                                cardsRight - PADDING, placement.height() - 2)
                ? placement.team()
                : null;
    }

    private TeamPlacement teamPlacementAt(WarPlannerSnapshot snapshot, float mouseY, float width) {
        int start = Math.min(scrollRows, Math.max(0, snapshot.teams().size() - 1));
        float y = contentTop();
        float supportWidth = teamSidebarWidth(width);
        float cardsRight = width - supportWidth - PADDING * 2;
        TeamActionLayout actions = teamActionLayout(cardsRight, manager.canManage(), snapshot.caller() != null);
        for (int index = start; index < snapshot.teams().size(); index++) {
            Team team = snapshot.teams().get(index);
            float height = teamCardHeight(team.members().size(), actions);
            if (mouseY >= y && mouseY <= y + height) return new TeamPlacement(team, y, height);
            y += height + 4;
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

    private boolean setAvailability(int minutes) {
        showResult(manager.setAvailability(minutes));
        return true;
    }

    private void showResult(java.util.concurrent.CompletableFuture<WarPlannerManager.ActionResult> future) {
        future.whenComplete((result, error) -> SeqClient.mc.execute(() -> flashMessage = error != null
                ? "War planner request failed."
                : result == null ? null : result.message()));
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
                canvas.fillRoundedRect(nextX, y, COMPOSITION_ICON_SIZE, COMPOSITION_ICON_SIZE, 2,
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
                .orElse("No composition role");
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
        float firstWideAction = hasCaller
                ? teamSelfActionX(cardsRight, canManage)
                : canManage ? cardsRight - 132 : cardsRight;
        boolean compact = firstWideAction < PADDING + 4;
        if (!compact) {
            return new TeamActionLayout(
                    cardsRight - 132,
                    canManage ? 52 : 0,
                    cardsRight - 74,
                    canManage ? 70 : 0,
                    teamSelfActionX(cardsRight, canManage),
                    hasCaller ? TEAM_SELF_ACTION_WIDTH : 0,
                    TEAM_ACTION_TOP,
                    TEAM_ACTION_TOP,
                    31,
                    Math.max(PADDING + 8, firstWideAction - 6),
                    Math.max(PADDING + 8, firstWideAction - 6),
                    0);
        }

        float innerLeft = PADDING + 4;
        float innerRight = Math.max(innerLeft + 1, cardsRight - 4);
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
                cardsRight - 6,
                cardsRight - 8,
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
                && member != null
                && member.discordId() != null
                && !member.discordId().isBlank()
                && !samePlayer(snapshot.self().playerUuid(), member.playerUuid());
    }

    static float rosterPingButtonX(float width) {
        return width - 112;
    }

    private PlannerViewport activePlannerViewport(float screenWidth) {
        float maximum = switch (tab) {
            case ROSTER -> MAX_ROSTER_WIDTH;
            case TEAMS -> MAX_TEAMS_WIDTH;
            case ZONES -> MAX_ZONES_WIDTH;
        };
        return plannerViewport(screenWidth, maximum);
    }

    static PlannerViewport plannerViewport(float screenWidth) {
        return plannerViewport(screenWidth, MAX_ZONES_WIDTH);
    }

    static PlannerViewport plannerViewport(float screenWidth, float maximumWidth) {
        float width = Math.max(1, Math.min(maximumWidth, screenWidth));
        return new PlannerViewport(Math.max(0, (screenWidth - width) / 2), width);
    }

    static float teamSidebarWidth(float width) {
        return Math.min(214, Math.max(176, width * .28f));
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

    static DisplayControls displayControls(float width, boolean canManage) {
        boolean compact = width < 420;
        float opacityWidth = compact ? 100 : OPACITY_CONTROL_WIDTH;
        float resourceWidth = compact ? 82 : RESOURCE_CONTROL_WIDTH;
        float lockWidth = compact ? 94 : LOCK_CONTROL_WIDTH;
        float right = width - PADDING;
        float lockX = canManage ? right - lockWidth : right;
        float resourceRight = canManage ? lockX - DISPLAY_CONTROL_GAP : right;
        float resourceX = resourceRight - resourceWidth;
        float opacityX = resourceX - DISPLAY_CONTROL_GAP - opacityWidth;
        return new DisplayControls(opacityX, opacityWidth, resourceX, resourceWidth, lockX, lockWidth);
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

    static boolean territoriesLocked() {
        return SeqClient.getWarPlannerLockTerritoriesSetting() != null
                && SeqClient.getWarPlannerLockTerritoriesSetting().getValue();
    }

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

    private static void updateBackgroundOpacity(float mouseX, DisplayControls controls) {
        if (SeqClient.getWarPlannerBackgroundOpacitySetting() == null) return;
        SeqClient.getWarPlannerBackgroundOpacitySetting().setValue(opacityPercentForMouse(mouseX, controls));
    }

    static int opacityPercentForMouse(float mouseX, DisplayControls controls) {
        float labelWidth = controls.opacityWidth() < OPACITY_CONTROL_WIDTH ? 45 : 65;
        float trackX = controls.opacityX() + labelWidth;
        float trackWidth = controls.opacityWidth() - labelWidth - 7;
        float ratio = Math.max(0, Math.min(1, (mouseX - trackX) / trackWidth));
        return Math.round(ratio * 100);
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
        return Math.max(48, 34 + Math.max(0, Math.min(5, memberCount)) * TEAM_MEMBER_ROW_STEP);
    }

    static float teamCardHeight(int memberCount, TeamActionLayout actions) {
        return teamCardHeight(memberCount) + (actions == null ? 0 : actions.extraHeight());
    }

    static float teamMemberRowStep() {
        return TEAM_MEMBER_ROW_STEP;
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

    static int warMapVisibleZoneRows(float height) {
        return Math.max(1, (int) ((height - 56) / WAR_MAP_ZONE_ROW_STEP));
    }

    static int warMapScrollStart(int requested, int zoneCount, int visibleRows) {
        return Math.max(0, Math.min(requested, Math.max(0, zoneCount - Math.max(1, visibleRows))));
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

    static MapBounds mapImageBounds() {
        return MapCalibration.fullBounds();
    }

    private static float previewX(double worldX, MapBounds fitted, float offsetX, float scale) {
        return offsetX + (float) ((worldX - fitted.minX()) * scale);
    }

    private static float previewY(double worldZ, MapBounds fitted, float offsetY, float scale) {
        return offsetY + (float) ((worldZ - fitted.minZ()) * scale);
    }

    static float tabWidth(float width, boolean reserveManagerAction) {
        float reservedWidth = reserveManagerAction ? MANAGER_ACTION_WIDTH : 0;
        return Math.max(1, Math.min(120, (width - PADDING * 2 - reservedWidth) / Tab.values().length));
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

    private static Color alpha(Color source, int alpha) {
        return new Color(source.getRed(), source.getGreen(), source.getBlue(), alpha);
    }

    private static float contentTop() {
        return HEADER_HEIGHT + AVAILABILITY_HEIGHT + TAB_HEIGHT + 8;
    }

    private static int clampRows(int value, int size) {
        return Math.max(0, Math.min(value, Math.max(0, size - 1)));
    }

    private void button(UiCanvas canvas, float x, float y, float width, float height, String label, boolean danger, boolean disabled) {
        boolean hovered = !disabled && hit(nvgMouseX, nvgMouseY, x, y, width, height);
        Color background = disabled
                ? color(ACCENT_DISABLED)
                : danger
                        ? color(hovered ? CONTROL_DANGER_HOVER : CONTROL_DANGER)
                        : color(hovered ? CONTROL_INPUT_HOVER : CONTROL_INPUT);
        canvas.fillRoundedRect(x, y, width, height, 4, background);
        text(canvas, label, x + width / 2, y + height / 2, 10,
                color(disabled ? TEXT_DISABLED : TEXT_PRIMARY), true);
    }

    private static void text(UiCanvas canvas, String value, float x, float y, float size, Color textColor, boolean centered) {
        canvas.drawText(value == null ? "" : value, x, y, new UiCanvas.TextStyle(
                SeqClient.getFontManager().getSelectedFont(), size, textColor,
                centered ? UiCanvas.HorizontalAlign.CENTER : UiCanvas.HorizontalAlign.LEFT,
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

    record DisplayControls(
            float opacityX,
            float opacityWidth,
            float resourceX,
            float resourceWidth,
            float lockX,
            float lockWidth) {
        float left() {
            return opacityX;
        }
    }

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

    record WarMapLayout(
            float mapX, float mapY, float mapWidth, float mapHeight, float sidebarX, float sidebarWidth) {
        boolean containsMap(float x, float y) {
            return hit(x, y, mapX, mapY, mapWidth, mapHeight);
        }

        boolean containsSidebar(float x, float y) {
            return hit(x, y, sidebarX, mapY, sidebarWidth, mapHeight);
        }
    }

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

    private record TeamPlacement(Team team, float y, float height) {}
}
