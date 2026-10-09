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
import com.seqwawa.seq.model.war.WarCompositionRole;
import com.seqwawa.seq.model.war.WarPlannerDrafts.TeamMemberMoveDraft;
import com.seqwawa.seq.model.war.WarPlannerSnapshot;
import com.seqwawa.seq.model.war.WarPlannerSnapshot.RosterMember;
import com.seqwawa.seq.model.war.WarPlannerSnapshot.Team;
import com.seqwawa.seq.model.war.WarPlannerSnapshot.TeamMember;
import com.seqwawa.seq.model.war.WarPlannerSnapshot.SupportSlot;
import com.seqwawa.seq.utils.rendering.UiCanvas;
import com.seqwawa.seq.utils.rendering.UiRenderer;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Team grid, support slots, unassigned pool, scrolling and member moves. */
final class WarTeamsTab {
    private static final float PADDING = 12;
    private static final float HEADER_HEIGHT = SequoiaUiStyle.HEADER_HEIGHT;

    private static final float TEAM_MEMBER_ROW_STEP = 16;
    private static final float TEAM_ACTION_TOP = 8;
    private static final float TEAM_SELF_ACTION_WIDTH = 68;
    private static final float SUPPORT_PANEL_HEIGHT = 142;
    private static final float SUPPORT_ROWS_TOP = 35;
    private static final float SUPPORT_ROW_STEP = 26;
    private static final float SUPPORT_ROW_HEIGHT = 21;
    private static final float UNASSIGNED_POOL_TOP = 150;
    private static final float UNASSIGNED_ROW_HEIGHT = 24;
    private static final float BUTTON_HEIGHT = 22;
    private static final float COMPOSITION_ICON_SIZE = 12;
    private static final float COMPOSITION_ICON_GAP = 3;
    private static final float TEAM_CARD_GAP = 6;
    private static final float TEAM_GRID_BREAKPOINT = 900;
    private static final float TEAM_COMPACT_BREAKPOINT = 520;
    private static final float TEAM_COMPACT_SUPPORT_HEIGHT = 62;
    private static final float TEAM_SHORT_SUPPORT_HEIGHT = 28;
    private static final float TEAM_SHORT_CONTENT_HEIGHT = 180;
    private int scrollRows;
    private PendingDelete pendingDeleteTeam;
    private MemberDrag memberDrag;
    private int unassignedScrollRows;

    interface Actions {
        void openTeam(Team team);
        void openSupport(int slot);
        void result(java.util.concurrent.CompletableFuture<WarPlannerManager.ActionResult> result);
        void feedback(String message);
        boolean busy();
    }
    private final WarPlannerManager manager;
    private final Actions host;
    private float nvgMouseX, nvgMouseY;
    WarTeamsTab(WarPlannerManager manager, Actions actions) { this.manager = manager; this.host = actions; }
    void cancelDrag() { memberDrag = null; }
    void reset() { scrollRows = 0; pendingDeleteTeam = null; cancelDrag(); }
    void render(UiCanvas canvas, WarPlannerSnapshot snapshot, float width, float top, float bottom, float mx, float my) {
        nvgMouseX = mx; nvgMouseY = my;
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
                    "Edit", false, host.busy());
            boolean confirming = pendingDeleteTeam != null && pendingDeleteTeam.id() == team.id();
            destructiveButton(canvas, actions.deleteX(), y + actions.managerY(), actions.deleteWidth(), BUTTON_HEIGHT,
                    confirming ? "Confirm" : "Delete", confirming, host.busy());
        }
        if (caller != null && placement.fullyShows(actions.selfY(), BUTTON_HEIGHT)) {
            primaryButton(canvas, actions.selfX(), y + actions.selfY(), actions.selfWidth(), BUTTON_HEIGHT,
                    teamMembershipActionLabel(snapshot, team),
                    host.busy() || !canChangeOwnTeam(snapshot, team));
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

    private MemberDrag teamMemberDragAt(
            WarPlannerSnapshot snapshot, float mouseX, float mouseY, float width, float height) {
        TeamsLayout layout = teamsLayout(width, contentTop(width) + 28, height - PADDING, snapshot.teams().size());
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
        TeamsLayout layout = teamsLayout(width, contentTop(width) + 28, height - PADDING, snapshot.teams().size());
        if (layout.compactAuxiliary()) return null;
        float rowsTop = contentTop(width) + 28 + UNASSIGNED_POOL_TOP + 36;
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
                host.feedback(target.name() + " is full.");
                return;
            }
            moveTeamMember(drag, target);
            return;
        }

        TeamsLayout layout = teamsLayout(width, contentTop(width) + 28, height - PADDING, snapshot.teams().size());
        if (layout.compactAuxiliary()) return;
        float poolY = contentTop(width) + 28 + UNASSIGNED_POOL_TOP;
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
            host.result(manager.moveTeamMember(drag.playerUuid(), draft));
        } catch (IllegalArgumentException exception) {
            host.feedback(exception.getMessage());
        }
    }

    private Team teamAt(
            WarPlannerSnapshot snapshot, float mouseX, float mouseY, float width, float height) {
        TeamPlacement placement = teamPlacementAt(snapshot, mouseX, mouseY, width, height);
        return placement == null ? null : placement.team();
    }

    private TeamPlacement teamPlacementAt(
            WarPlannerSnapshot snapshot, float mouseX, float mouseY, float width, float height) {
        TeamsLayout layout = teamsLayout(width, contentTop(width) + 28, height - PADDING, snapshot.teams().size());
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

    private record MemberDrag(
            String playerUuid,
            Long sourceTeamId,
            Long sourceVersion,
            float startX,
            float startY,
            boolean active) {}

    private record PendingDelete(long id, Long version) {}

    boolean click(float mx, float my, float width, float height) {
        WarPlannerSnapshot snapshot = manager.snapshot();
        if (snapshot == null || my < contentTop(width) + 28 || my > height - PADDING) {
            return false;
        }
        TeamsLayout teamsLayout = teamsLayout(width, contentTop(width) + 28, height - PADDING, snapshot.teams().size());
        if (manager.canManage()) {
            for (SupportPlacement support : supportPlacements(teamsLayout)) {
                if (hit(mx, my, support.x(), support.y(), support.width(), support.height())) {
                    host.openSupport(support.index());
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
        {
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
                    host.result(ownTeam ? manager.leaveTeam() : manager.joinTeam(team.id()));
                }
                return true;
            }
            if (manager.canManage()
                    && placement.fullyShows(actions.managerY(), BUTTON_HEIGHT)
                    && hit(mx, my, actions.editX(), rowY + actions.managerY(), actions.editWidth(), BUTTON_HEIGHT)) {
                host.openTeam(team);
                return true;
            }
            if (manager.canManage()
                    && placement.fullyShows(actions.managerY(), BUTTON_HEIGHT)
                    && hit(mx, my, actions.deleteX(), rowY + actions.managerY(), actions.deleteWidth(), BUTTON_HEIGHT)) {
                if (pendingDeleteTeam != null && pendingDeleteTeam.id() == team.id()) {
                    host.result(manager.deleteTeam(team.id(), pendingDeleteTeam.version()));
                    pendingDeleteTeam = null;
                } else {
                    pendingDeleteTeam = new PendingDelete(team.id(), team.version());
                }
                return true;
            }
        }
        return false;
    }
    boolean drag(float mx, float my) {
        if (memberDrag != null) {

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
        return false;
    }

    boolean release(float mx, float my, float width, float height) {
        if (memberDrag != null) {

            MemberDrag completed = memberDrag;
            memberDrag = null;
            if (completed.active()) {
                dropTeamMember(completed, mx, my, width, height);
            }
            return true;
        }
        return false;
    }

    void scroll(WarPlannerSnapshot snapshot, float mx, float my, float width, float height, int delta) {
        TeamsLayout layout = teamsLayout(width, contentTop(width) + 28, height - PADDING, snapshot.teams().size());
        if (manager.canManage() && !layout.compactAuxiliary()) {
            float poolY = contentTop(width) + 28 + UNASSIGNED_POOL_TOP;
            float contentBottom = height - PADDING;
            if (poolY + 34 < contentBottom && hit(mx, my, layout.sidebarX(), poolY,
                    layout.sidebarWidth(), contentBottom - poolY)) {
                unassignedScrollRows = clampRows(unassignedScrollRows + delta, unassignedOnlineRoster(snapshot).size());
                return;
            }
        }
        scrollRows = teamScrollStart(scrollRows + delta, snapshot.teams().size(), layout.columns());
    }

    private void button(UiCanvas canvas, float x, float y, float width, float height, String label, boolean danger, boolean disabled) {
        tonedButton(canvas, nvgMouseX, nvgMouseY, x, y, width, height, label, danger ? ButtonTone.DANGER : ButtonTone.SECONDARY, disabled);
    }

    private void primaryButton(UiCanvas canvas, float x, float y, float width, float height, String label, boolean disabled) {
        tonedButton(canvas, nvgMouseX, nvgMouseY, x, y, width, height, label, ButtonTone.PRIMARY, disabled);
    }

    private void destructiveButton(UiCanvas canvas, float x, float y, float width, float height,
            String label, boolean confirming, boolean disabled) {
        tonedButton(canvas, nvgMouseX, nvgMouseY, x, y, width, height, label, confirming ? ButtonTone.DANGER : ButtonTone.QUIET_DANGER, disabled);
    }

    private static boolean hit(float mx, float my, float x, float y, float width, float height) {
        return mx >= x && mx <= x + width && my >= y && my <= y + height;
    }
}
