package com.seqwawa.seq.ui;

import static com.seqwawa.seq.managers.ThemeManager.color;
import static com.seqwawa.seq.ui.theme.UiColor.*;
import static com.seqwawa.seq.ui.WarPlannerDialogUi.*;

import com.seqwawa.seq.managers.WarPlannerManager;
import com.seqwawa.seq.model.war.WarCompositionRole;
import com.seqwawa.seq.model.war.WarCompositionTargets;
import com.seqwawa.seq.model.war.WarPlannerDrafts.TeamDraft;
import com.seqwawa.seq.model.war.WarPlannerDrafts.TeamMemberDraft;
import com.seqwawa.seq.model.war.WarPlannerSnapshot;
import com.seqwawa.seq.model.war.WarPlannerSnapshot.RosterMember;
import com.seqwawa.seq.model.war.WarPlannerSnapshot.Team;
import com.seqwawa.seq.model.war.WarPlannerSnapshot.TeamMember;
import com.seqwawa.seq.model.war.WarTeamType;
import com.seqwawa.seq.utils.rendering.UiCanvas;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import org.lwjgl.glfw.GLFW;

/** Owns a war-planner dialog, including its draft and in-flight request session. */
final class WarTeamEditor {
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

    private final WarPlannerManager manager;
    private final Executor clientExecutor;
    private final Consumer<String> feedback;
    private long generation;
    private String flashMessage;

    WarTeamEditor(WarPlannerManager manager, Executor clientExecutor, Consumer<String> feedback) {
        this.manager = manager;
        this.clientExecutor = clientExecutor;
        this.feedback = feedback;
    }

    private void setMessage(String message) {
        flashMessage = message;
        feedback.accept(message);
    }

    boolean isOpen() { return teamEditorOpen; }

    void render(UiCanvas canvas, float width, float height, float nvgMouseX, float nvgMouseY) {
        WarPlannerSnapshot snapshot = manager.snapshot();
        if (snapshot == null) {
            return;
        }
        Layout layout = layout(width, height);
        float x = layout.modal().x(), y = layout.modal().y();
        float w = layout.modal().width(), h = layout.modal().height();
        canvas.fillRect(0, 0, width, height, color(BACKGROUND_MODAL_OVERLAY));
        canvas.fillRect(x, y, w, h, plannerBackground(color(BACKGROUND_BODY_OPAQUE)));
        canvas.strokeRect(x, y, w, h, 1, color(CONTROL_BORDER));
        text(canvas, editingTeamId == null ? "Create war team" : "Edit war team", x + 12, y + 20, 16,
                color(ACCENT_PRIMARY), false);
        button(canvas, nvgMouseX, nvgMouseY, layout.close(), "×", true, teamEditorSaving);

        float fieldY = y + 34;
        String automaticName = automaticTeamName(snapshot, teamType, editingTeamId);
        DropdownMenu.trigger(canvas, layout.type().x(), layout.type().y(), layout.type().width(), layout.type().height(),
                "Type: " + teamType.label() + " · Creates " + automaticName,
                teamTypeMenuOpen, !teamEditorSaving, nvgMouseX, nvgMouseY);
        renderCompositionTargetControls(canvas, layout, nvgMouseX, nvgMouseY);
        text(canvas, "Targets warn about missing capabilities; they do not block saving.", x + 12, fieldY + 64, 9,
                color(TEXT_MUTED), false);

        if (flashMessage != null && !flashMessage.isBlank()) {
            text(canvas, truncate(flashMessage, 58), x + 12, fieldY + 78, 9, color(CONTROL_WARNING), false);
        }
        float searchY = layout.search().y();
        boolean searchHovered = hit(
                nvgMouseX, nvgMouseY, layout.search().x(), layout.search().y(), layout.search().width(), layout.search().height());
        layout.search().fill(canvas,
                color(teamEditorSearchFocused || searchHovered ? CONTROL_INPUT_HOVER : CONTROL_INPUT));
        layout.search().stroke(canvas, 1,
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
        float listTop = layout.roster().area().y();
        float listBottom = layout.roster().area().y() + layout.roster().area().height();
        canvas.scissor(x + 8, listTop, w - 16, Math.max(0, listBottom - listTop));
        int start = Math.min(editorScrollRows, Math.max(0, eligible.size() - 1));
        if (eligible.isEmpty()) {
            text(canvas, "No players match this search.", x + 18, listTop + 14, 10, color(TEXT_MUTED), false);
        }
        for (int index = start; index < eligible.size() && layout.roster().fullyVisible(index - start); index++) {
            UiBounds rowBounds = layout.roster().row(index - start);
            float rowY = rowBounds.y() - 2;
            RosterMember member = eligible.get(index);
            TeamMemberDraft selected = teamMember(member.playerUuid());
            rowBounds.fill(canvas,
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
        button(canvas, nvgMouseX, nvgMouseY, layout.cancel(), "Cancel", false, teamEditorSaving);
        primaryButton(canvas, nvgMouseX, nvgMouseY, layout.save(),
                teamEditorSaving ? "Saving…" : "Save", teamEditorSaving);
        if (teamTypeMenuOpen) {
            renderTeamTypeMenu(canvas, snapshot, layout, nvgMouseX, nvgMouseY);
        }
    }

    private void renderCompositionTargetControls(UiCanvas canvas, Layout layout, float nvgMouseX, float nvgMouseY) {
        float x = layout.modal().x(), y = layout.type().y() + 32, width = layout.modal().width();
        text(canvas, "Comp", x + 12, y + 11, 9, color(TEXT_MUTED), false);
        float controlWidth = Math.min(92, Math.max(70, (width - 68) / 3));
        for (int index = 0; index < WarCompositionRole.values().length; index++) {
            WarCompositionRole role = WarCompositionRole.values()[index];
            float controlX = x + 55 + index * controlWidth;
            renderCompositionIcons(canvas, List.of(role), controlX, y + 5);
            button(canvas, nvgMouseX, nvgMouseY, layout.target(index, false), "−", false, teamEditorSaving);
            text(canvas, Integer.toString(teamTargets.target(role)), controlX + 43, y + 11, 10,
                    color(TEXT_PRIMARY), true);
            button(canvas, nvgMouseX, nvgMouseY, layout.target(index, true), "+", false, teamEditorSaving);
        }
    }

    private void renderTeamTypeMenu(
            UiCanvas canvas, WarPlannerSnapshot snapshot, Layout layout, float nvgMouseX, float nvgMouseY) {
        List<WarTeamType> options = WarTeamType.editableValues();
        for (int index = 0; index < options.size(); index++) {
            WarTeamType option = options.get(index);
            UiBounds optionBounds = layout.typeOption(index);
            boolean selectable = !teamEditorSaving && teamTypeSelectable(snapshot, option, editingTeamId);
            String preview = option == WarTeamType.HQ && !selectable
                    ? "Already assigned" : automaticTeamName(snapshot, option, editingTeamId);
            DropdownMenu.row(canvas, optionBounds.x(), optionBounds.y(), optionBounds.width(), optionBounds.height(),
                    option.label(), preview, option == teamType, optionBounds.contains(nvgMouseX, nvgMouseY), selectable);
        }
    }

    boolean click(float mx, float my, float width, float height) {
        WarPlannerSnapshot snapshot = manager.snapshot();
        if (snapshot == null) return true;
        if (teamEditorSaving) return true;
        Layout layout = layout(width, height);
        if (layout.close().contains(mx, my)
                || layout.cancel().contains(mx, my)) {
            close();
            return true;
        }
        boolean searchClicked = layout.search().contains(mx, my);
        if (!searchClicked || teamTypeMenuOpen) {
            teamEditorSearchFocused = false;
        }
        if (layout.type().contains(mx, my)) {
            teamTypeMenuOpen = !teamTypeMenuOpen;
            return true;
        }
        if (teamTypeMenuOpen) {
            List<WarTeamType> options = WarTeamType.editableValues();
            int index = DropdownMenu.optionAt(mx, my, layout.typeOption(0).x(), layout.typeOption(0).y(), layout.type().width(),
                    DropdownMenu.ROW_HEIGHT, options.size(), 0);
            teamTypeMenuOpen = false;
            if (index >= 0) {
                WarTeamType option = options.get(index);
                if (teamTypeSelectable(snapshot, option, editingTeamId)) {
                    teamType = option;
                    setMessage(null);
                } else {
                    setMessage("Only one HQ Team can exist.");
                }
            }
            return true;
        }
        if (layout.save().contains(mx, my)) {
            saveTeam();
            return true;
        }
        for (int index = 0; index < WarCompositionRole.values().length; index++) {
            WarCompositionRole role = WarCompositionRole.values()[index];
            if (layout.target(index, false).contains(mx, my)) {
                teamTargets = teamTargets.with(role, Math.max(0, teamTargets.target(role) - 1));
                return true;
            }
            if (layout.target(index, true).contains(mx, my)) {
                teamTargets = teamTargets.with(role, Math.min(5, teamTargets.target(role) + 1));
                return true;
            }
        }
        if (searchClicked) {
            teamEditorSearchFocused = true;
            return true;
        }

        List<RosterMember> eligible = teamEditorRoster(snapshot, teamEditorSearchQuery);
        int row = layout.roster().indexAt(mx, my,
                Math.min(editorScrollRows, Math.max(0, eligible.size() - 1)), eligible.size());
        if (row >= 0) cycleTeamMember(eligible.get(row));
        return true;
    }

    void open(Team team) {
        close();
        teamEditorOpen = true;
        setMessage(null);
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
                setMessage(staleMembers + " former member" + (staleMembers == 1 ? " was" : "s were")
                        + " removed from this draft. Save to apply.");
            } else if (!team.teamType().editable()) {
                setMessage("Choose a supported team type before saving this legacy team.");
            }
        }
    }

    void close() {
        generation++;
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
                    setMessage("This team changed while you were editing it. Close and reopen the editor.");
                    return;
                }
            }
            Long version = teamEditorBase == null ? null : teamEditorBase.version();
            TeamDraft draft = new TeamDraft(teamType, version, teamTargets, teamMembers);
            Long id = editingTeamId;
            long requestGeneration = generation;
            teamEditorSaving = true;
            manager.saveTeam(id, draft).whenComplete((result, error) -> clientExecutor.execute(() -> {
                if (requestGeneration != generation) return;
                teamEditorSaving = false;
                if (error != null || result == null || !result.success()) {
                    setMessage(error != null
                            ? "War planner request failed."
                            : result == null ? "No response from the war planner." : result.message());
                    return;
                }
                setMessage(result.message());
                close();
            }));
        } catch (IllegalArgumentException exception) {
            setMessage(exception.getMessage());
        }
    }

    private void cycleTeamMember(RosterMember member) {
        TeamMemberDraft current = teamMember(member.playerUuid());
        if (current == null) {
            if (teamMembers.size() >= 5) {
                setMessage("A war team can contain at most five people.");
                return;
            }
            teamMembers.add(new TeamMemberDraft(member.playerUuid()));
            return;
        }
        teamMembers.remove(current);
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
                .filter(member -> query.isEmpty() || rosterSearchText(member).contains(query))
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

    static float teamEditorWidth(float width) {
        return Math.max(1, Math.min(560, width - PADDING * 2));
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

    boolean keyPressed(int key) {
        if (teamTypeMenuOpen && key == GLFW.GLFW_KEY_ESCAPE) {
            teamTypeMenuOpen = false;
            return true;
        }
        if (!teamEditorOpen) return false;

        if (!teamEditorSearchFocused) return false;
        if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            teamEditorSearchFocused = false;
        } else if (key == GLFW.GLFW_KEY_BACKSPACE && !teamEditorSearchQuery.isEmpty()) {
            teamEditorSearchQuery = teamEditorSearchQuery.substring(0, teamEditorSearchQuery.length() - 1);
            editorScrollRows = 0;
        }
        return true;
    }

    boolean charTyped(String text) {
        if (!teamEditorOpen || !teamEditorSearchFocused) return false;
        if (text != null && teamEditorSearchQuery.length() + text.length() <= TEAM_EDITOR_SEARCH_MAX_LENGTH) {
            teamEditorSearchQuery += text;
            editorScrollRows = 0;
        }
        return true;
    }

    void scroll(int delta) {
        editorScrollRows = clampRows(editorScrollRows + delta, teamEditorRoster(manager.snapshot(), teamEditorSearchQuery).size());
    }

    static Layout layout(float width, float height) {
        float w = teamEditorWidth(width), h = Math.max(0, height - 70);
        float x = (width - w) / 2, y = 46;
        return new Layout(new UiBounds(x, y, w, h),
                new UiBounds(x + w - 34, y + 8, 24, BUTTON_HEIGHT),
                new UiBounds(x + w - 148, y + h - 32, 64, BUTTON_HEIGHT),
                new UiBounds(x + w - 78, y + h - 32, 66, BUTTON_HEIGHT),
                new UiBounds(x + 12, y + 34, w - 24, 24),
                new UiBounds(x + 12, y + 34 + TEAM_EDITOR_SEARCH_OFFSET,
                        SequoiaUiStyle.searchWidth(w - 24), TEAM_EDITOR_SEARCH_HEIGHT),
                new UiRowList(new UiBounds(x + 12, y + 34 + TEAM_EDITOR_LIST_OFFSET,
                        w - 24, Math.max(0, h - 76 - TEAM_EDITOR_LIST_OFFSET)), 28, 24));
    }

    record Layout(UiBounds modal, UiBounds close, UiBounds cancel, UiBounds save,
            UiBounds type, UiBounds search, UiRowList roster) {
        UiBounds target(int index, boolean increase) {
            float controlWidth = Math.min(92, Math.max(70, (modal.width() - 68) / 3));
            return new UiBounds(modal.x() + 55 + index * controlWidth + (increase ? 51 : 17),
                    type.y() + 32, 18, BUTTON_HEIGHT);
        }
        UiBounds typeOption(int index) {
            return new UiBounds(type.x(), type.y() + type.height() + index * DropdownMenu.ROW_HEIGHT,
                    type.width(), DropdownMenu.ROW_HEIGHT);
        }
    }
}
