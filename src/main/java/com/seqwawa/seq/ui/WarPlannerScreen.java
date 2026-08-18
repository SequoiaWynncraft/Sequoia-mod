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
import com.seqwawa.seq.model.war.WarCompositionRole;
import com.seqwawa.seq.model.war.WarPlannerDrafts.TeamDraft;
import com.seqwawa.seq.model.war.WarPlannerDrafts.TeamMemberDraft;
import com.seqwawa.seq.model.war.WarPlannerDrafts.SupportDraft;
import com.seqwawa.seq.model.war.WarPlannerDrafts.SupportSlotDraft;
import com.seqwawa.seq.model.war.WarPlannerSnapshot;
import com.seqwawa.seq.model.war.WarPlannerSnapshot.RosterMember;
import com.seqwawa.seq.model.war.WarPlannerSnapshot.Team;
import com.seqwawa.seq.model.war.WarPlannerSnapshot.TeamMember;
import com.seqwawa.seq.model.war.WarPlannerSnapshot.SupportSlot;
import com.seqwawa.seq.model.war.WarPlannerSnapshot.Zone;
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
    private static final float TEAM_CARD_HEIGHT = 88;
    private static final float TEAM_CARD_STEP = 92;
    private static final float TEAM_MEMBER_ROW_STEP = 11;
    private static final float TEAM_ACTION_TOP = 8;
    private static final float TEAM_SELF_ACTION_WIDTH = 68;
    private static final float ZONE_CARD_HEIGHT = 132;
    private static final float ZONE_CARD_GAP = 8;
    private static final float ZONE_OVERVIEW_BAR_HEIGHT = 34;
    private static final float BUTTON_HEIGHT = 22;
    private static final float MANAGER_ACTION_WIDTH = 92;
    private static final float MAX_CONTENT_WIDTH = 900;
    private static final float COMPOSITION_ICON_SIZE = 12;
    private static final float COMPOSITION_ICON_GAP = 3;
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
    private boolean teamEditorOpen;
    private WarTeamType teamType = WarTeamType.VLOW_MUNCH;
    private boolean teamTypeMenuOpen;
    private final List<TeamMemberDraft> teamMembers = new ArrayList<>();
    private boolean teamEditorSaving;
    private int editorScrollRows;
    private Long pendingDeleteTeamId;
    private Long pendingDeleteZoneId;
    private Integer editingSupportSlot;
    private int supportEditorScrollRows;
    private boolean supportEditorSaving;
    private boolean draggingBackgroundOpacity;
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
        PlannerViewport viewport = plannerViewport(screenWidth);
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
        text(canvas, "Your status", PADDING, y + 13, 10, color(TEXT_MUTED), false);
        String roleLabel = caller == null ? "No composition role" : compositionLabel(caller.compositionRoles());
        text(canvas, truncate(status + " · " + roleLabel, 42), PADDING, y + 31, 14,
                color(remaining.isZero() ? TEXT_SECONDARY : CONTROL_SUCCESS), false);

        float x = Math.max(155, width - 360);
        button(canvas, x, y + 13, 58, BUTTON_HEIGHT, "30 min", false, manager.isMutating());
        button(canvas, x + 64, y + 13, 58, BUTTON_HEIGHT, "1 hour", false, manager.isMutating());
        button(canvas, x + 128, y + 13, 58, BUTTON_HEIGHT, "2 hours", false, manager.isMutating());
        button(canvas, x + 192, y + 13, 76, BUTTON_HEIGHT, "Unavailable", true, manager.isMutating());
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
        if (manager.canManage() && tab != Tab.ROSTER) {
            button(canvas, width - 92, y + 1, 80, BUTTON_HEIGHT,
                    tab == Tab.TEAMS ? "New team" : "New zone", false, manager.isMutating());
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
        float supportWidth = Math.min(214, Math.max(176, width * .28f));
        float cardsRight = width - supportWidth - PADDING * 2;
        float cardWidth = cardsRight - PADDING;
        renderSupportBoard(canvas, snapshot, cardsRight + PADDING, top, supportWidth, bottom);
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
        for (int index = start; index < snapshot.teams().size() && y + TEAM_CARD_HEIGHT <= bottom;
                index++, y += TEAM_CARD_STEP) {
            Team team = snapshot.teams().get(index);
            boolean ownTeam = caller != null && caller.teamId() != null && caller.teamId() == team.id();
            canvas.fillRoundedRect(PADDING, y + 1, cardWidth, TEAM_CARD_HEIGHT - 2, 4,
                    plannerBackground(color(ownTeam ? ACCENT_PRIMARY_DARK : BACKGROUND_CONTENT)));
            float selfActionX = teamSelfActionX(cardsRight, manager.canManage());
            float actionsLeft = caller != null ? selfActionX : manager.canManage() ? cardsRight - 132 : cardsRight;
            String title = team.name() + (ownTeam ? " · Your team" : "") + " · " + team.members().size() + "/5";
            text(canvas, truncate(title, availableCharacters(PADDING + 8, actionsLeft - 6, 13, 32)),
                    PADDING + 8, y + 13, 13, color(TEXT_PRIMARY), false);
            List<TeamMember> members = team.members().stream()
                    .sorted(Comparator.comparingInt(TeamMember::position))
                    .toList();
            float memberY = y + 31;
            for (TeamMember member : members) {
                String displayName = member.minecraftUsername() == null ? member.playerUuid() : member.minecraftUsername();
                List<WarCompositionRole> roles = teamMemberRoles(snapshot, member.playerUuid());
                float iconWidth = roles.size() * COMPOSITION_ICON_SIZE
                        + Math.max(0, roles.size() - 1) * COMPOSITION_ICON_GAP;
                float textX = PADDING + 12;
                float rightEdge = cardsRight - 8;
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
                button(canvas, cardsRight - 132, y + TEAM_ACTION_TOP, 52, BUTTON_HEIGHT, "Edit", false, manager.isMutating());
                boolean confirming = pendingDeleteTeamId != null && pendingDeleteTeamId == team.id();
                button(canvas, cardsRight - 74, y + TEAM_ACTION_TOP, 70, BUTTON_HEIGHT,
                        confirming ? "Confirm" : "Delete", true, manager.isMutating());
            }
            if (caller != null) {
                button(canvas, selfActionX, y + TEAM_ACTION_TOP, TEAM_SELF_ACTION_WIDTH, BUTTON_HEIGHT,
                        teamMembershipActionLabel(snapshot, team), false,
                        manager.isMutating() || !canChangeOwnTeam(snapshot, team));
            }
        }
    }

    private void renderZones(UiCanvas canvas, WarPlannerSnapshot snapshot, float width, float top, float bottom) {
        if (snapshot.zones().isEmpty()) {
            String message = manager.canManage()
                    ? "No territory zones yet. Use New zone to create one."
                    : "No territory zones yet · View only (manager access required).";
            text(canvas, message, PADDING, top + 22, 13, color(TEXT_MUTED), false);
            return;
        }
        boolean overviewAvailable = zoneOverviewAvailable(snapshot, manager.canManage(), territoriesLocked());
        float gridTop = zoneGridTop(top, overviewAvailable);
        if (overviewAvailable) {
            canvas.fillRoundedRect(PADDING, top + 3, width - PADDING * 2, 27, 4,
                    plannerBackground(color(BACKGROUND_CONTENT)));
            text(canvas, width >= 560 ? "Territories locked · compare every zone on one map" : "Locked zones",
                    PADDING + 8, top + 16, 10,
                    color(TEXT_SECONDARY), false);
            button(canvas, width - 140, top + 5, 128, BUTTON_HEIGHT, "Open map overview", false, false);
        }
        int columns = zoneGridColumns(width);
        int start = Math.min(scrollRows * columns, Math.max(0, snapshot.zones().size() - 1));
        float cardWidth = zoneCardWidth(width, columns);
        int visibleIndex = 0;
        for (int index = start; index < snapshot.zones().size(); index++, visibleIndex++) {
            int row = visibleIndex / columns;
            int column = visibleIndex % columns;
            float x = PADDING + column * (cardWidth + ZONE_CARD_GAP);
            float y = gridTop + row * (ZONE_CARD_HEIGHT + ZONE_CARD_GAP);
            if (y + ZONE_CARD_HEIGHT > bottom) {
                break;
            }
            renderZoneCard(canvas, snapshot, snapshot.zones().get(index), x, y, cardWidth);
        }
    }

    private void renderZoneCard(
            UiCanvas canvas, WarPlannerSnapshot snapshot, Zone zone, float x, float y, float cardWidth) {
        Color zoneColor = parseColor(zone.color(), color(ACCENT_PRIMARY));
        canvas.fillRoundedRect(x, y, cardWidth, ZONE_CARD_HEIGHT, 5, plannerBackground(color(BACKGROUND_CONTENT)));
        canvas.strokeRect(x, y, cardWidth, ZONE_CARD_HEIGHT, 1, new Color(
                zoneColor.getRed(), zoneColor.getGreen(), zoneColor.getBlue(), 150));
        canvas.fillRect(x + 7, y + 8, 6, 22, zoneColor);

        float previewX = x + 18;
        float previewY = y + 34;
        float previewWidth = Math.min(178, Math.max(112, cardWidth * .43f));
        float previewHeight = ZONE_CARD_HEIGHT - 44;
        renderZonePreview(canvas, snapshot, zone, previewX, previewY, previewWidth, previewHeight, zoneColor);

        float detailsX = previewX + previewWidth + 10;
        float detailsWidth = Math.max(1, x + cardWidth - detailsX - 8);
        text(canvas, truncate(zone.name(), Math.max(8, (int) (detailsWidth / 7))),
                detailsX, y + 16, 13, color(TEXT_PRIMARY), false);
        String assigned = zone.assignedTeamIds().isEmpty()
                ? "Unassigned"
                : zone.assignedTeamIds().stream()
                        .map(id -> teamName(snapshot, id))
                        .reduce((left, right) -> left + " + " + right)
                        .orElse("Unassigned");
        text(canvas, truncate(assigned, Math.max(8, (int) (detailsWidth / 6))),
                detailsX, y + 39, 10, color(TEXT_SECONDARY), false);
        text(canvas, zone.territories().size() + " territories", detailsX, y + 56, 10, color(TEXT_MUTED), false);
        text(canvas, "Click map to inspect", detailsX, y + 73, 9, color(TEXT_MUTED), false);
        if (manager.canManage()) {
            float actionsY = y + ZONE_CARD_HEIGHT - BUTTON_HEIGHT - 8;
            button(canvas, detailsX, actionsY, 52, BUTTON_HEIGHT, "Edit", false, manager.isMutating());
            boolean confirming = pendingDeleteZoneId != null && pendingDeleteZoneId == zone.id();
            button(canvas, x + cardWidth - 78, actionsY, 70, BUTTON_HEIGHT,
                    confirming ? "Confirm" : "Delete", true, manager.isMutating());
        }
    }

    private void renderZonePreview(
            UiCanvas canvas,
            WarPlannerSnapshot snapshot,
            Zone zone,
            float x,
            float y,
            float width,
            float height,
            Color zoneColor) {
        canvas.fillRoundedRect(x, y, width, height, 3, plannerBackground(color(CONTROL_INPUT)));
        List<GuildTerritory> allMapTerritories = territoryIndex.territories();
        List<GuildTerritory> mapTerritories = visibleMapTerritories(
                allMapTerritories, snapshot, manager.canManage() && territoriesLocked());
        if (mapTerritories.isEmpty()) {
            text(canvas, "Map unavailable", x + width / 2, y + height / 2, 9, color(TEXT_MUTED), true);
            return;
        }
        Map<String, GuildTerritory> byName = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        allMapTerritories.forEach(territory -> byName.put(territory.name(), territory));
        List<GuildTerritory> selectedTerritories = resolveTerritories(zone.territories(), byName);
        MapBounds fitted = zonePreviewBounds(selectedTerritories);
        float scale = (float) Math.min(
                (width - 10) / Math.max(1, fitted.maxX() - fitted.minX()),
                (height - 10) / Math.max(1, fitted.maxZ() - fitted.minZ()));
        float contentWidth = (float) ((fitted.maxX() - fitted.minX()) * scale);
        float contentHeight = (float) ((fitted.maxZ() - fitted.minZ()) * scale);
        float offsetX = x + (width - contentWidth) / 2;
        float offsetY = y + (height - contentHeight) / 2;
        canvas.scissor(x, y, width, height);
        UiImage mapImage = zonePreviewMapImage();
        if (mapImage != null) {
            MapBounds imageBounds = mapImageBounds();
            float mapX = previewX(imageBounds.minX(), fitted, offsetX, scale);
            float mapY = previewY(imageBounds.minZ(), fitted, offsetY, scale);
            float mapWidth = (float) ((imageBounds.maxX() - imageBounds.minX()) * scale);
            float mapHeight = (float) ((imageBounds.maxZ() - imageBounds.minZ()) * scale);
            canvas.drawImage(mapImage, mapX, mapY, mapWidth, mapHeight, .9f * backgroundOpacityPercent() / 100f);
            Color tint = color(BACKGROUND_BODY_OPAQUE);
            canvas.fillRect(x, y, width, height,
                    plannerBackground(new Color(tint.getRed(), tint.getGreen(), tint.getBlue(), 24)));
        }
        Map<String, WarPlannerSnapshot.TerritoryDetails> details = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        snapshot.territoryDetails().forEach(detail -> details.put(detail.name(), detail));
        if (resourceColorsEnabled()) {
            drawPreviewResources(canvas, mapTerritories, details, fitted, offsetX, offsetY, scale);
        }
        Color mapColor = color(TEXT_MUTED);
        drawPreviewOutlines(
                canvas,
                mapTerritories,
                fitted,
                offsetX,
                offsetY,
                scale,
                new Color(mapColor.getRed(), mapColor.getGreen(), mapColor.getBlue(), 72),
                .55f,
                0);
        for (Zone otherZone : snapshot.zones()) {
            if (otherZone.id() == zone.id()) continue;
            Color otherColor = parseColor(otherZone.color(), color(ACCENT_PRIMARY));
            drawPreviewOutlines(
                    canvas,
                    resolveTerritories(otherZone.territories(), byName),
                    fitted,
                    offsetX,
                    offsetY,
                    scale,
                    new Color(otherColor.getRed(), otherColor.getGreen(), otherColor.getBlue(), 185),
                    1.1f,
                    .5f);
        }
        drawPreviewOutlines(
                canvas,
                selectedTerritories,
                fitted,
                offsetX,
                offsetY,
                scale,
                zoneColor,
                1.8f,
                1);
        drawPreviewConnections(canvas, mapTerritories, byName, details, fitted, offsetX, offsetY, scale);
        canvas.resetScissor();
    }

    private static List<GuildTerritory> resolveTerritories(
            List<String> names, Map<String, GuildTerritory> territoriesByName) {
        return names.stream().map(territoriesByName::get).filter(java.util.Objects::nonNull).toList();
    }

    static List<GuildTerritory> visibleMapTerritories(
            List<GuildTerritory> territories, WarPlannerSnapshot snapshot, boolean locked) {
        Set<String> visible = visibleTerritoryNames(
                snapshot,
                territories.stream().map(GuildTerritory::name).collect(java.util.stream.Collectors.toSet()),
                locked);
        return territories.stream()
                .filter(territory -> visible.contains(territory.name()))
                .toList();
    }

    static Set<String> visibleTerritoryNames(WarPlannerSnapshot snapshot, Set<String> territories, boolean locked) {
        if (!locked || snapshot == null) return Set.copyOf(territories);
        Set<String> zoned = snapshot.zones().stream()
                .flatMap(zone -> zone.territories().stream())
                .map(name -> name.toLowerCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toSet());
        return territories.stream()
                .filter(name -> zoned.contains(name.toLowerCase(Locale.ROOT)))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
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
                if (linked == null) continue;
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
                Math.min(bottom - top - 4, 168),
                5,
                plannerBackground(color(BACKGROUND_CONTENT)));
        text(canvas, "Shared support", x + 10, top + 17, 13, color(ACCENT_PRIMARY), false);
        text(canvas, "Click to cycle · may join party", x + 10, top + 31, 9, color(TEXT_MUTED), false);
        String[] codes = {"LEAD", "ECO_1", "ECO_2", "ECO_3"};
        for (int index = 0; index < codes.length; index++) {
            float y = top + 43 + index * 28;
            String code = codes[index];
            SupportSlot slot = snapshot.support().slots().stream()
                    .filter(candidate -> code.equals(candidate.code()))
                    .findFirst()
                    .orElse(null);
            boolean hovered = manager.canManage() && hit(nvgMouseX, nvgMouseY, x + 7, y, panelWidth - 14, 23);
            canvas.fillRoundedRect(x + 7, y, panelWidth - 14, 23, 3,
                    color(hovered ? CONTROL_INPUT_HOVER : CONTROL_INPUT));
            text(canvas, index == 0 ? "Lead" : "Eco " + index, x + 13, y + 12, 10, color(TEXT_MUTED), false);
            String name = slot == null ? "Empty" : slot.minecraftUsername() == null ? slot.playerUuid() : slot.minecraftUsername();
            text(canvas, truncate(name, 16), x + 60, y + 12, 10,
                    color(slot == null ? TEXT_MUTED : TEXT_PRIMARY), false);
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
        float x = Math.max(PADDING, width * .12f);
        float y = 46;
        float w = width - x * 2;
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
        text(canvas, "Click a player to add/remove. Capabilities: Solo wand · DPS relik · Tank spear", x + 12, fieldY + 38, 10,
                color(TEXT_MUTED), false);

        if (flashMessage != null && !flashMessage.isBlank()) {
            text(canvas, truncate(flashMessage, 58), x + 12, fieldY + 53, 9, color(CONTROL_WARNING), false);
        }
        List<RosterMember> eligible = editableRoster(snapshot);
        float listTop = fieldY + 64;
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

    private void renderTeamTypeMenu(
            UiCanvas canvas, WarPlannerSnapshot snapshot, float x, float y, float menuWidth) {
        for (int index = 0; index < WarTeamType.values().length; index++) {
            WarTeamType option = WarTeamType.values()[index];
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
        if (click.button() != 0) {
            return super.mouseClicked(click, outsideScreen);
        }
        PlannerViewport viewport = plannerViewport(MinecraftUiRenderer.screenWidth());
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
        float availabilityY = HEADER_HEIGHT + 13;
        float quickX = Math.max(155, width - 360);
        if (hit(mx, my, quickX, availabilityY, 58, BUTTON_HEIGHT)) return setAvailability(30);
        if (hit(mx, my, quickX + 64, availabilityY, 58, BUTTON_HEIGHT)) return setAvailability(60);
        if (hit(mx, my, quickX + 128, availabilityY, 58, BUTTON_HEIGHT)) return setAvailability(120);
        if (hit(mx, my, quickX + 192, availabilityY, 76, BUTTON_HEIGHT)) {
            showResult(manager.clearAvailability());
            return true;
        }

        float tabsY = HEADER_HEIGHT + AVAILABILITY_HEIGHT;
        float tabWidth = tabWidth(width, manager.canManage());
        for (int index = 0; index < Tab.values().length; index++) {
            if (hit(mx, my, PADDING + tabWidth * index, tabsY, tabWidth - 4, TAB_HEIGHT)) {
                tab = Tab.values()[index];
                scrollRows = 0;
                pendingDeleteTeamId = null;
                pendingDeleteZoneId = null;
                return true;
            }
        }
        if (manager.canManage() && tab != Tab.ROSTER && hit(mx, my, width - 92, tabsY + 1, 80, BUTTON_HEIGHT)) {
            if (tab == Tab.TEAMS) {
                beginTeamEdit(null);
            } else {
                SeqClient.mc.setScreen(new WarTerritoryPickerScreen(this, null));
            }
            return true;
        }
        return clickContent(mx, my, width, height) || super.mouseClicked(click, outsideScreen);
    }

    private boolean clickContent(float mx, float my, float width, float height) {
        WarPlannerSnapshot snapshot = manager.snapshot();
        if (snapshot == null || my < contentTop() || my > height - 42) {
            return false;
        }
        if (tab == Tab.TEAMS && manager.canManage()) {
            float supportWidth = Math.min(214, Math.max(176, width * .28f));
            float supportX = width - supportWidth - PADDING;
            for (int index = 0; index < 4; index++) {
                if (hit(mx, my, supportX + 7, contentTop() + 43 + index * 28, supportWidth - 14, 23)) {
                    editingSupportSlot = index;
                    supportEditorScrollRows = 0;
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
        float supportWidth = Math.min(214, Math.max(176, width * .28f));
        float cardsRight = width - supportWidth - PADDING * 2;
        float itemHeight = tab == Tab.TEAMS
                ? TEAM_CARD_STEP
                : ROW_HEIGHT;
        int row = scrollRows + Math.max(0, (int) ((my - contentTop()) / itemHeight));
        float rowY = contentTop() + (row - scrollRows) * itemHeight;
        if (tab == Tab.TEAMS && row < snapshot.teams().size()) {
            Team team = snapshot.teams().get(row);
            RosterMember caller = snapshot.caller();
            float selfActionX = teamSelfActionX(cardsRight, manager.canManage());
            if (caller != null
                    && hit(mx, my, selfActionX, rowY + TEAM_ACTION_TOP, TEAM_SELF_ACTION_WIDTH, BUTTON_HEIGHT)) {
                if (canChangeOwnTeam(snapshot, team) && !manager.isMutating()) {
                    boolean ownTeam = caller.teamId() != null && caller.teamId() == team.id();
                    showResult(ownTeam ? manager.leaveTeam() : manager.joinTeam(team.id()));
                }
                return true;
            }
            if (manager.canManage()
                    && hit(mx, my, cardsRight - 132, rowY + TEAM_ACTION_TOP, 52, BUTTON_HEIGHT)) {
                beginTeamEdit(team);
                return true;
            }
            if (manager.canManage()
                    && hit(mx, my, cardsRight - 74, rowY + TEAM_ACTION_TOP, 70, BUTTON_HEIGHT)) {
                if (pendingDeleteTeamId != null && pendingDeleteTeamId == team.id()) {
                    showResult(manager.deleteTeam(team.id()));
                    pendingDeleteTeamId = null;
                } else {
                    pendingDeleteTeamId = team.id();
                }
                return true;
            }
        }
        return false;
    }

    private boolean clickZoneContent(WarPlannerSnapshot snapshot, float mx, float my, float width) {
        boolean overviewAvailable = zoneOverviewAvailable(snapshot, manager.canManage(), territoriesLocked());
        float gridTop = zoneGridTop(contentTop(), overviewAvailable);
        if (overviewAvailable && hit(mx, my, width - 140, contentTop() + 5, 128, BUTTON_HEIGHT)) {
            SeqClient.mc.setScreen(WarTerritoryPickerScreen.overview(this));
            return true;
        }
        if (my < gridTop) return false;
        int columns = zoneGridColumns(width);
        float cardWidth = zoneCardWidth(width, columns);
        int visibleRow = (int) ((my - gridTop) / (ZONE_CARD_HEIGHT + ZONE_CARD_GAP));
        int column = (int) ((mx - PADDING) / (cardWidth + ZONE_CARD_GAP));
        if (visibleRow < 0 || column < 0 || column >= columns) return false;
        float cardX = PADDING + column * (cardWidth + ZONE_CARD_GAP);
        float cardY = gridTop + visibleRow * (ZONE_CARD_HEIGHT + ZONE_CARD_GAP);
        if (!hit(mx, my, cardX, cardY, cardWidth, ZONE_CARD_HEIGHT)) return false;
        int index = (scrollRows + visibleRow) * columns + column;
        if (index < 0 || index >= snapshot.zones().size()) return false;
        Zone zone = snapshot.zones().get(index);
        if (manager.canManage()) {
            float previewWidth = Math.min(178, Math.max(112, cardWidth * .43f));
            float detailsX = cardX + 18 + previewWidth + 10;
            float actionsY = cardY + ZONE_CARD_HEIGHT - BUTTON_HEIGHT - 8;
            if (hit(mx, my, detailsX, actionsY, 52, BUTTON_HEIGHT)) {
                SeqClient.mc.setScreen(new WarTerritoryPickerScreen(this, zone));
                return true;
            }
            if (hit(mx, my, cardX + cardWidth - 78, actionsY, 70, BUTTON_HEIGHT)) {
                if (pendingDeleteZoneId != null && pendingDeleteZoneId == zone.id()) {
                    showResult(manager.deleteZone(zone.id()));
                    pendingDeleteZoneId = null;
                } else {
                    pendingDeleteZoneId = zone.id();
                }
                return true;
            }
        }
        SeqClient.mc.setScreen(new WarTerritoryPickerScreen(this, zone, true));
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent click, double deltaX, double deltaY) {
        if (draggingBackgroundOpacity && click.button() == 0) {
            PlannerViewport viewport = plannerViewport(MinecraftUiRenderer.screenWidth());
            float mx = MinecraftUiRenderer.mouseX(click.x()) - viewport.x();
            updateBackgroundOpacity(mx, displayControls(viewport.width(), manager.canManage()));
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
        return super.mouseReleased(click);
    }

    private boolean clickTeamEditor(float mx, float my, float width, float height) {
        WarPlannerSnapshot snapshot = manager.snapshot();
        if (snapshot == null) return true;
        if (teamEditorSaving) return true;
        float x = Math.max(PADDING, width * .12f);
        float y = 46;
        float w = width - x * 2;
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
            for (int index = 0; index < WarTeamType.values().length; index++) {
                float optionY = fieldY + 25 + index * 24;
                if (!hit(mx, my, x + 12, optionY, w - 24, 23)) continue;
                WarTeamType option = WarTeamType.values()[index];
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
        float listTop = fieldY + 64;
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
            int size = switch (tab) {
                case ROSTER -> snapshot.visibleRoster().size();
                case TEAMS -> snapshot.teams().size();
                case ZONES -> zoneGridRows(
                        snapshot.zones().size(), plannerViewport(MinecraftUiRenderer.screenWidth()).width());
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
        teamType = team == null
                ? defaultTeamType(manager.snapshot())
                : WarTeamType.fromTeamName(team.name());
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
            }
        }
    }

    private void closeTeamEditor() {
        teamEditorOpen = false;
        editingTeamId = null;
        teamType = WarTeamType.VLOW_MUNCH;
        teamTypeMenuOpen = false;
        teamMembers.clear();
        editorScrollRows = 0;
        teamEditorSaving = false;
    }

    private void saveTeam() {
        WarPlannerSnapshot snapshot = manager.snapshot();
        if (snapshot == null) return;
        try {
            Long version = editingTeamId == null ? null : snapshot.teams().stream()
                    .filter(team -> team.id() == editingTeamId)
                    .map(Team::version)
                    .findFirst()
                    .orElse(null);
            TeamDraft draft = new TeamDraft(teamType, version, teamMembers);
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

    static PlannerViewport plannerViewport(float screenWidth) {
        float width = Math.max(1, Math.min(MAX_CONTENT_WIDTH, screenWidth));
        return new PlannerViewport(Math.max(0, (screenWidth - width) / 2), width);
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
        if (teamType != WarTeamType.HQ || snapshot == null) return true;
        return snapshot.teams().stream()
                .filter(team -> editingTeamId == null || team.id() != editingTeamId)
                .noneMatch(team -> WarTeamType.fromTeamName(team.name()) == WarTeamType.HQ);
    }

    static String automaticTeamName(WarPlannerSnapshot snapshot, WarTeamType teamType, Long editingTeamId) {
        if (snapshot != null && editingTeamId != null) {
            Team editing = snapshot.team(editingTeamId);
            if (editing != null && WarTeamType.fromTeamName(editing.name()) == teamType) {
                return editing.name();
            }
        }
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

    static float teamCardHeight() {
        return TEAM_CARD_HEIGHT;
    }

    static float teamMemberRowStep() {
        return TEAM_MEMBER_ROW_STEP;
    }

    static float compactRoleX(float textX, float textWidth, float rightEdge, float iconWidth) {
        return Math.min(textX + textWidth + 4, rightEdge - iconWidth);
    }

    static int zoneGridColumns(float width) {
        return width >= 720 ? 2 : 1;
    }

    static int zoneGridRows(int zoneCount, float width) {
        int columns = zoneGridColumns(width);
        return Math.max(0, (zoneCount + columns - 1) / columns);
    }

    static boolean zoneOverviewAvailable(WarPlannerSnapshot snapshot, boolean canManage, boolean locked) {
        return snapshot != null && canManage && locked && !snapshot.zones().isEmpty();
    }

    static float zoneGridTop(float contentTop, boolean overviewAvailable) {
        return contentTop + (overviewAvailable ? ZONE_OVERVIEW_BAR_HEIGHT : 0);
    }

    private static float zoneCardWidth(float width, int columns) {
        return (width - PADDING * 2 - ZONE_CARD_GAP * (columns - 1)) / columns;
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

    private static String formatDuration(Duration duration) {
        long seconds = Math.max(0, duration.getSeconds());
        long hours = seconds / 3600;
        long minutes = (seconds % 3600 + 59) / 60;
        return hours > 0 ? hours + "h " + minutes + "m" : Math.max(1, minutes) + "m";
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
        ZONES("Zones");

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
}
