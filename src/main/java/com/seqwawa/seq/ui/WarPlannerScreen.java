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
    private static final float TEAM_CARD_GAP = 6;
    private static final float TEAM_MEMBER_ROW_HEIGHT = 17;
    private static final float TEAM_ACTION_TOP = 4;
    private static final float TEAM_MEMBER_FIRST_BASELINE = 36;
    private static final float ZONE_CARD_HEIGHT = 132;
    private static final float ZONE_CARD_GAP = 8;
    private static final float BUTTON_HEIGHT = 22;
    private static final float MANAGER_ACTION_WIDTH = 92;
    private static final float COMPOSITION_ICON_SIZE = 12;
    private static final float COMPOSITION_ICON_GAP = 3;

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

    private void renderPlanner(UiCanvas canvas) {
        float width = canvas.metrics().width();
        float height = canvas.metrics().height();
        canvas.fillRect(0, 0, width, height, color(BACKGROUND_BODY_OPAQUE));
        canvas.fillRect(0, 0, width, HEADER_HEIGHT, color(BACKGROUND_HEADER));
        text(canvas, "War Planner", PADDING, HEADER_HEIGHT / 2, 19, color(ACCENT_PRIMARY), false);
        text(canvas, stateLabel(), Math.max(132, width - 240), HEADER_HEIGHT / 2, 11, stateColor(), false);
        button(canvas, width - 82, 8, 70, BUTTON_HEIGHT, "Refresh", false, manager.isMutating());

        renderAvailability(canvas, width);
        renderTabs(canvas, width);
        renderContent(canvas, width, height);

        if (flashMessage != null && !flashMessage.isBlank()) {
            canvas.fillRoundedRect(PADDING, height - 34, Math.min(width - PADDING * 2, 480), 24, 5,
                    color(BACKGROUND_POPUP));
            text(canvas, truncate(flashMessage, 68), PADDING + 8, height - 22, 11,
                    color(manager.lastError() == null ? TEXT_SECONDARY : CONTROL_WARNING), false);
        } else if (manager.lastError() != null) {
            canvas.fillRoundedRect(PADDING, height - 34, Math.min(width - PADDING * 2, 480), 24, 5,
                    color(STATUS_WARNING_BACKGROUND));
            text(canvas, truncate(manager.lastError(), 68), PADDING + 8, height - 22, 11,
                    color(TEXT_PRIMARY), false);
        }

        if (teamEditorOpen) {
            renderTeamEditor(canvas, width, height);
        } else if (editingSupportSlot != null) {
            renderSupportEditor(canvas, width, height);
        }
    }

    private void renderAvailability(UiCanvas canvas, float width) {
        float y = HEADER_HEIGHT;
        canvas.fillRect(0, y, width, AVAILABILITY_HEIGHT, color(BACKGROUND_CONTENT));
        WarPlannerSnapshot snapshot = manager.snapshot();
        RosterMember caller = snapshot == null ? null : snapshot.caller();
        Duration remaining = manager.ownAvailabilityRemaining();
        String status = caller != null && caller.available() && !remaining.isZero()
                ? "Available for " + formatDuration(remaining)
                : "Unavailable";
        text(canvas, "Your status", PADDING, y + 13, 10, color(TEXT_MUTED), false);
        text(canvas, status, PADDING, y + 31, 14,
                color(remaining.isZero() ? TEXT_SECONDARY : CONTROL_SUCCESS), false);

        float x = Math.max(155, width - 360);
        button(canvas, x, y + 13, 58, BUTTON_HEIGHT, "30 min", false, manager.isMutating());
        button(canvas, x + 64, y + 13, 58, BUTTON_HEIGHT, "1 hour", false, manager.isMutating());
        button(canvas, x + 128, y + 13, 58, BUTTON_HEIGHT, "2 hours", false, manager.isMutating());
        button(canvas, x + 192, y + 13, 76, BUTTON_HEIGHT, "Unavailable", true, manager.isMutating());
    }

    private void renderTabs(UiCanvas canvas, float width) {
        float y = HEADER_HEIGHT + AVAILABILITY_HEIGHT;
        float tabWidth = tabWidth(width, manager.canManage());
        int index = 0;
        for (Tab candidate : Tab.values()) {
            float x = PADDING + tabWidth * index++;
            canvas.fillRect(x, y, tabWidth - 4, TAB_HEIGHT,
                    color(candidate == tab ? ACCENT_PRIMARY_DARK : CONTROL_INPUT));
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
        List<RosterMember> roster = snapshot.onlineRoster().stream()
                .sorted(Comparator.comparing(RosterMember::displayName, String.CASE_INSENSITIVE_ORDER))
                .toList();
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
                    color(caller ? ACCENT_PRIMARY_DARK : index % 2 == 0 ? BACKGROUND_CONTENT : BACKGROUND_CONTENT_FOCUSED));
            text(canvas, truncate(member.displayName() + (caller ? " · You" : ""), 22),
                    PADDING + 8, y + 13, 13, color(TEXT_PRIMARY), false);
            String discord = member.discordUsername() == null ? "Discord unlinked" : "@" + member.discordUsername();
            float detailX = renderCompositionIcons(canvas, member.compositionRoles(), PADDING + 8, y + 20);
            int detailCharacters = availableCharacters(detailX, width - 190, 10, 44);
            text(canvas, truncate(compositionLabel(member.compositionRoles()) + " · " + discord, detailCharacters),
                    detailX + iconTextGap(member.compositionRoles()), y + 28, 10, color(TEXT_MUTED), false);
            String assignment = member.teamId() == null ? "No team" : teamName(snapshot, member.teamId());
            text(canvas, truncate(assignment, 28), width - 184, y + 13, 11, color(TEXT_SECONDARY), false);
            Duration remaining = member.available()
                    ? WarPlannerManager.remainingUntil(member.availableUntil(), manager.serverNow())
                    : Duration.ZERO;
            text(canvas, remaining.isZero() ? "Unavailable" : formatDuration(remaining), width - 184, y + 28, 10,
                    color(remaining.isZero() ? TEXT_MUTED : CONTROL_SUCCESS), false);
        }
    }

    private void renderTeams(UiCanvas canvas, WarPlannerSnapshot snapshot, float width, float top, float bottom) {
        float supportWidth = Math.min(214, Math.max(176, width * .28f));
        float cardsRight = width - supportWidth - PADDING * 2;
        float cardWidth = cardsRight - PADDING;
        int memberColumns = teamMemberGridColumns(cardWidth);
        float cardHeight = teamCardHeight(cardWidth);
        float cardStep = cardHeight + TEAM_CARD_GAP;
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
        for (int index = start; index < snapshot.teams().size() && y + cardHeight <= bottom;
                index++, y += cardStep) {
            Team team = snapshot.teams().get(index);
            boolean ownTeam = caller != null && caller.teamId() != null && caller.teamId() == team.id();
            canvas.fillRoundedRect(PADDING, y + 1, cardWidth, cardHeight - 2, 4,
                    color(ownTeam ? ACCENT_PRIMARY_DARK : BACKGROUND_CONTENT));
            float actionsLeft = manager.canManage() ? cardsRight - 104 : cardsRight;
            String title = team.name() + (ownTeam ? " · Your team" : "") + " · " + team.members().size() + "/5";
            text(canvas, truncate(title, availableCharacters(PADDING + 8, actionsLeft - 6, 13, 32)),
                    PADDING + 8, y + 13, 13, color(TEXT_PRIMARY), false);
            List<TeamMember> members = team.members().stream()
                    .sorted(Comparator.comparingInt(TeamMember::position))
                    .toList();
            float memberGap = 4;
            float memberAreaWidth = cardWidth - 14;
            float memberWidth = (memberAreaWidth - memberGap * (memberColumns - 1)) / memberColumns;
            for (int memberIndex = 0; memberIndex < members.size(); memberIndex++) {
                TeamMember member = members.get(memberIndex);
                int row = memberIndex / memberColumns;
                int column = memberIndex % memberColumns;
                float memberX = PADDING + 7 + column * (memberWidth + memberGap);
                float memberY = y + TEAM_MEMBER_FIRST_BASELINE + row * TEAM_MEMBER_ROW_HEIGHT;
                canvas.fillRoundedRect(memberX, memberY - 7, memberWidth, 15, 3, color(CONTROL_INPUT));
                String displayName = member.minecraftUsername() == null ? member.playerUuid() : member.minecraftUsername();
                List<WarCompositionRole> roles = teamMemberRoles(snapshot, member.playerUuid());
                float iconWidth = roles.size() * (COMPOSITION_ICON_SIZE + COMPOSITION_ICON_GAP);
                float rolesX = memberX + memberWidth - iconWidth - 3;
                String offlineSuffix = isOnline(snapshot, member.playerUuid()) ? "" : " · off";
                text(canvas,
                        truncate(displayName + offlineSuffix,
                                availableCharacters(memberX + 5, rolesX - 4, 10, 18)),
                        memberX + 5,
                        memberY,
                        10,
                        color(TEXT_SECONDARY),
                        false);
                renderCompositionIcons(canvas, roles, rolesX, memberY - COMPOSITION_ICON_SIZE / 2);
            }
            if (manager.canManage()) {
                button(canvas, cardsRight - 104, y + TEAM_ACTION_TOP, 42, BUTTON_HEIGHT, "Edit", false, manager.isMutating());
                boolean confirming = pendingDeleteTeamId != null && pendingDeleteTeamId == team.id();
                button(canvas, cardsRight - 58, y + TEAM_ACTION_TOP, 54, BUTTON_HEIGHT,
                        confirming ? "Confirm" : "Delete", true, manager.isMutating());
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
        int columns = zoneGridColumns(width);
        int start = Math.min(scrollRows * columns, Math.max(0, snapshot.zones().size() - 1));
        float cardWidth = zoneCardWidth(width, columns);
        int visibleIndex = 0;
        for (int index = start; index < snapshot.zones().size(); index++, visibleIndex++) {
            int row = visibleIndex / columns;
            int column = visibleIndex % columns;
            float x = PADDING + column * (cardWidth + ZONE_CARD_GAP);
            float y = top + row * (ZONE_CARD_HEIGHT + ZONE_CARD_GAP);
            if (y + ZONE_CARD_HEIGHT > bottom) {
                break;
            }
            renderZoneCard(canvas, snapshot, snapshot.zones().get(index), x, y, cardWidth);
        }
    }

    private void renderZoneCard(
            UiCanvas canvas, WarPlannerSnapshot snapshot, Zone zone, float x, float y, float cardWidth) {
        Color zoneColor = parseColor(zone.color(), color(ACCENT_PRIMARY));
        canvas.fillRoundedRect(x, y, cardWidth, ZONE_CARD_HEIGHT, 5, color(BACKGROUND_CONTENT));
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
        canvas.fillRoundedRect(x, y, width, height, 3, color(CONTROL_INPUT));
        List<GuildTerritory> mapTerritories = territoryIndex.territories();
        if (mapTerritories.isEmpty()) {
            text(canvas, "Map unavailable", x + width / 2, y + height / 2, 9, color(TEXT_MUTED), true);
            return;
        }
        Map<String, GuildTerritory> byName = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        mapTerritories.forEach(territory -> byName.put(territory.name(), territory));
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
            canvas.drawImage(mapImage, mapX, mapY, mapWidth, mapHeight, .72f);
            Color tint = color(BACKGROUND_BODY_OPAQUE);
            canvas.fillRect(x, y, width, height,
                    new Color(tint.getRed(), tint.getGreen(), tint.getBlue(), 58));
        }
        Map<String, WarPlannerSnapshot.TerritoryDetails> details = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        snapshot.territoryDetails().forEach(detail -> details.put(detail.name(), detail));
        Set<String> drawnConnections = new java.util.HashSet<>();
        Color connectionColor = color(CONTROL_BORDER);
        connectionColor = new Color(
                connectionColor.getRed(), connectionColor.getGreen(), connectionColor.getBlue(), 75);
        for (GuildTerritory territory : mapTerritories) {
            WarPlannerSnapshot.TerritoryDetails detail = details.get(territory.name());
            if (detail == null) continue;
            for (String linkedName : detail.connections()) {
                GuildTerritory linked = byName.get(linkedName);
                if (linked == null) continue;
                String key = territory.name().compareToIgnoreCase(linkedName) < 0
                        ? territory.name() + "\n" + linkedName : linkedName + "\n" + territory.name();
                if (!drawnConnections.add(key)) continue;
                canvas.strokeLine(
                        previewX(territory.centerX(), fitted, offsetX, scale),
                        previewY(territory.centerZ(), fitted, offsetY, scale),
                        previewX(linked.centerX(), fitted, offsetX, scale),
                        previewY(linked.centerZ(), fitted, offsetY, scale),
                        .45f,
                        connectionColor);
            }
        }
        Color mapColor = color(TEXT_MUTED);
        drawPreviewTerritories(
                canvas,
                mapTerritories,
                fitted,
                offsetX,
                offsetY,
                scale,
                new Color(mapColor.getRed(), mapColor.getGreen(), mapColor.getBlue(), 38),
                new Color(mapColor.getRed(), mapColor.getGreen(), mapColor.getBlue(), 85));
        for (Zone otherZone : snapshot.zones()) {
            if (otherZone.id() == zone.id()) continue;
            Color otherColor = parseColor(otherZone.color(), color(ACCENT_PRIMARY));
            drawPreviewTerritories(
                    canvas,
                    resolveTerritories(otherZone.territories(), byName),
                    fitted,
                    offsetX,
                    offsetY,
                    scale,
                    new Color(otherColor.getRed(), otherColor.getGreen(), otherColor.getBlue(), 55),
                    new Color(otherColor.getRed(), otherColor.getGreen(), otherColor.getBlue(), 90));
        }
        drawPreviewTerritories(
                canvas,
                selectedTerritories,
                fitted,
                offsetX,
                offsetY,
                scale,
                new Color(zoneColor.getRed(), zoneColor.getGreen(), zoneColor.getBlue(), 180),
                zoneColor);
        canvas.resetScissor();
    }

    private static List<GuildTerritory> resolveTerritories(
            List<String> names, Map<String, GuildTerritory> territoriesByName) {
        return names.stream().map(territoriesByName::get).filter(java.util.Objects::nonNull).toList();
    }

    private static void drawPreviewTerritories(
            UiCanvas canvas,
            List<GuildTerritory> territories,
            MapBounds fitted,
            float offsetX,
            float offsetY,
            float scale,
            Color fill,
            Color stroke) {
        for (GuildTerritory territory : territories) {
            MapBounds bounds = territory.bounds();
            float territoryX = previewX(bounds.minX(), fitted, offsetX, scale);
            float territoryY = previewY(bounds.minZ(), fitted, offsetY, scale);
            float territoryWidth = Math.max(2, (float) ((bounds.maxX() - bounds.minX()) * scale));
            float territoryHeight = Math.max(2, (float) ((bounds.maxZ() - bounds.minZ()) * scale));
            canvas.fillRect(territoryX, territoryY, territoryWidth, territoryHeight, fill);
            canvas.strokeRect(territoryX, territoryY, territoryWidth, territoryHeight, .55f, stroke);
        }
    }

    private void renderSupportBoard(
            UiCanvas canvas, WarPlannerSnapshot snapshot, float x, float top, float panelWidth, float bottom) {
        canvas.fillRoundedRect(x, top + 2, panelWidth, Math.min(bottom - top - 4, 168), 5, color(BACKGROUND_CONTENT));
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
        canvas.fillRect(0, 0, width, height, color(BACKGROUND_MODAL_OVERLAY));
        canvas.fillRoundedRect(x, y, w, h, 7, color(BACKGROUND_BODY_OPAQUE));
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
                    color(selected ? ACCENT_PRIMARY_DARK : BACKGROUND_CONTENT));
            text(canvas, truncate(candidate.displayName(), 28), x + 18, rowY + 14, 11, color(TEXT_PRIMARY), false);
            text(canvas, candidate.online() ? "Online" : "Offline · currently assigned", x + w - 150, rowY + 14,
                    9, color(candidate.online() ? CONTROL_SUCCESS : TEXT_MUTED), false);
        }
        canvas.resetScissor();
        button(canvas, x + 12, y + h - 32, 72, BUTTON_HEIGHT, "Clear slot", true, supportEditorSaving);
        button(canvas, x + w - 80, y + h - 32, 68, BUTTON_HEIGHT, "Cancel", false, supportEditorSaving);
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
        canvas.fillRect(0, 0, width, height, color(BACKGROUND_MODAL_OVERLAY));
        canvas.fillRoundedRect(x, y, w, h, 7, color(BACKGROUND_BODY_OPAQUE));
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
                    color(selected == null ? BACKGROUND_CONTENT : ACCENT_PRIMARY_DARK));
            String memberLabel = member.displayName() + (member.online() ? "" : " · Offline");
            text(canvas, truncate(memberLabel, w >= 360 ? 24 : 14), x + 18, rowY + 14, 11,
                    color(TEXT_PRIMARY), false);
            float dutyX = x + w - 92;
            float rolesX = Math.max(x + 104, dutyX - 52);
            renderCompositionIcons(canvas, member.compositionRoles(), rolesX, rowY + 8);
            text(canvas, selected == null ? "Off" : "In party", x + w - 92, rowY + 14, 10,
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
        float mx = MinecraftUiRenderer.mouseX(click.x());
        float my = MinecraftUiRenderer.mouseY(click.y());
        float width = MinecraftUiRenderer.screenWidth();
        float height = MinecraftUiRenderer.screenHeight();

        if (teamEditorOpen) {
            return clickTeamEditor(mx, my, width, height);
        }
        if (editingSupportSlot != null) {
            return clickSupportEditor(mx, my, width, height);
        }
        if (hit(mx, my, width - 82, 8, 70, BUTTON_HEIGHT)) {
            showResult(manager.refreshNow());
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
        float supportWidth = Math.min(214, Math.max(176, width * .28f));
        float cardsRight = width - supportWidth - PADDING * 2;
        float itemHeight = tab == Tab.TEAMS
                ? teamCardHeight(cardsRight - PADDING) + TEAM_CARD_GAP
                : ROW_HEIGHT;
        int row = scrollRows + Math.max(0, (int) ((my - contentTop()) / itemHeight));
        float rowY = contentTop() + (row - scrollRows) * itemHeight;
        if (tab == Tab.TEAMS && manager.canManage() && row < snapshot.teams().size()) {
            Team team = snapshot.teams().get(row);
            if (hit(mx, my, cardsRight - 104, rowY + TEAM_ACTION_TOP, 42, BUTTON_HEIGHT)) {
                beginTeamEdit(team);
                return true;
            }
            if (hit(mx, my, cardsRight - 58, rowY + TEAM_ACTION_TOP, 54, BUTTON_HEIGHT)) {
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
        int columns = zoneGridColumns(width);
        float cardWidth = zoneCardWidth(width, columns);
        int visibleRow = (int) ((my - contentTop()) / (ZONE_CARD_HEIGHT + ZONE_CARD_GAP));
        int column = (int) ((mx - PADDING) / (cardWidth + ZONE_CARD_GAP));
        if (visibleRow < 0 || column < 0 || column >= columns) return false;
        float cardX = PADDING + column * (cardWidth + ZONE_CARD_GAP);
        float cardY = contentTop() + visibleRow * (ZONE_CARD_HEIGHT + ZONE_CARD_GAP);
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

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int delta = scrollY > 0 ? -1 : 1;
        WarPlannerSnapshot snapshot = manager.snapshot();
        if (snapshot == null) return true;
        if (teamEditorOpen) {
            editorScrollRows = clampRows(editorScrollRows + delta, editableRoster(snapshot).size());
        } else if (editingSupportSlot != null) {
            supportEditorScrollRows = clampRows(
                    supportEditorScrollRows + delta, supportCandidates(snapshot, editingSupportSlot).size());
        } else {
            int size = switch (tab) {
                case ROSTER -> snapshot.onlineRoster().size();
                case TEAMS -> snapshot.teams().size();
                case ZONES -> zoneGridRows(snapshot.zones().size(), MinecraftUiRenderer.screenWidth());
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

    private static boolean isOnline(WarPlannerSnapshot snapshot, String playerUuid) {
        return snapshot.roster().stream()
                .filter(member -> samePlayer(member.playerUuid(), playerUuid))
                .map(RosterMember::online)
                .findFirst()
                .orElse(false);
    }

    private static RosterMember rosterMember(WarPlannerSnapshot snapshot, String playerUuid) {
        return snapshot.roster().stream()
                .filter(member -> samePlayer(member.playerUuid(), playerUuid))
                .findFirst()
                .orElse(null);
    }

    static List<WarCompositionRole> teamMemberRoles(WarPlannerSnapshot snapshot, String playerUuid) {
        RosterMember member = rosterMember(snapshot, playerUuid);
        return member == null ? List.of(WarCompositionRole.SOLO) : member.compositionRoles();
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

    static int teamMemberGridColumns(float cardWidth) {
        if (cardWidth >= 320) return 3;
        if (cardWidth >= 220) return 2;
        return 1;
    }

    static float teamCardHeight(float cardWidth) {
        int rows = (5 + teamMemberGridColumns(cardWidth) - 1) / teamMemberGridColumns(cardWidth);
        return Math.max(66, 49 + (rows - 1) * TEAM_MEMBER_ROW_HEIGHT);
    }

    static float teamActionBottomOffset() {
        return TEAM_ACTION_TOP + BUTTON_HEIGHT;
    }

    static float teamMemberContentTopOffset() {
        return TEAM_MEMBER_FIRST_BASELINE - COMPOSITION_ICON_SIZE / 2;
    }

    static int zoneGridColumns(float width) {
        return width >= 720 ? 2 : 1;
    }

    static int zoneGridRows(int zoneCount, float width) {
        int columns = zoneGridColumns(width);
        return Math.max(0, (zoneCount + columns - 1) / columns);
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
}
