package com.seqwawa.seq.ui;

import static com.seqwawa.seq.managers.ThemeManager.color;
import static com.seqwawa.seq.ui.theme.UiColor.*;

import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.managers.AssetManager;
import com.seqwawa.seq.managers.WarPlannerManager;
import com.seqwawa.seq.model.war.WarCompositionRole;
import com.seqwawa.seq.model.war.WarPlannerDrafts.TeamDraft;
import com.seqwawa.seq.model.war.WarPlannerDrafts.TeamMemberDraft;
import com.seqwawa.seq.model.war.WarPlannerSnapshot;
import com.seqwawa.seq.model.war.WarPlannerSnapshot.RosterMember;
import com.seqwawa.seq.model.war.WarPlannerSnapshot.Team;
import com.seqwawa.seq.model.war.WarPlannerSnapshot.TeamMember;
import com.seqwawa.seq.model.war.WarPlannerSnapshot.Zone;
import com.seqwawa.seq.model.war.WarTeamRole;
import com.seqwawa.seq.utils.TextInputHelper;
import com.seqwawa.seq.utils.rendering.MinecraftUiRenderer;
import com.seqwawa.seq.utils.rendering.UiCanvas;
import com.seqwawa.seq.utils.rendering.UiRenderer;
import java.awt.Color;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

/** Seq-only war management overlay. Authorization is supplied solely by the protected snapshot. */
public final class WarPlannerScreen extends Screen {
    private static final float PADDING = 12;
    private static final float HEADER_HEIGHT = 38;
    private static final float AVAILABILITY_HEIGHT = 48;
    private static final float TAB_HEIGHT = 24;
    private static final float ROW_HEIGHT = 38;
    private static final float BUTTON_HEIGHT = 22;
    private static final float COMPOSITION_ICON_SIZE = 12;
    private static final float COMPOSITION_ICON_GAP = 3;

    private final Screen parent;
    private final WarPlannerManager manager;
    private Tab tab = Tab.ROSTER;
    private float nvgMouseX;
    private float nvgMouseY;
    private int scrollRows;
    private String flashMessage;

    private Long editingTeamId;
    private boolean teamEditorOpen;
    private String teamName = "";
    private final List<TeamMemberDraft> teamMembers = new ArrayList<>();
    private boolean teamNameFocused;
    private boolean teamEditorSaving;
    private int editorScrollRows;
    private Long pendingDeleteTeamId;
    private Long pendingDeleteZoneId;

    public WarPlannerScreen(Screen parent) {
        super(Component.literal("War Planner"));
        this.parent = parent;
        this.manager = SeqClient.getWarPlannerManager();
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
        float tabWidth = Math.min(120, (width - PADDING * 2) / 3f);
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
        List<RosterMember> roster = snapshot.roster().stream()
                .sorted(Comparator.comparing(RosterMember::displayName, String.CASE_INSENSITIVE_ORDER))
                .toList();
        if (roster.isEmpty()) {
            text(canvas,
                    snapshot.discordRolesAvailable() ? "No eligible war members." : "Discord roles are temporarily unavailable.",
                    PADDING, top + 22, 13, color(TEXT_MUTED), false);
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
            if (member.teamRole() != null) {
                assignment += " · " + roleLabel(member.teamRole());
            }
            text(canvas, truncate(assignment, 28), width - 184, y + 13, 11, color(TEXT_SECONDARY), false);
            Duration remaining = member.available()
                    ? WarPlannerManager.remainingUntil(member.availableUntil(), manager.serverNow())
                    : Duration.ZERO;
            text(canvas, remaining.isZero() ? "Unavailable" : formatDuration(remaining), width - 184, y + 28, 10,
                    color(remaining.isZero() ? TEXT_MUTED : CONTROL_SUCCESS), false);
        }
    }

    private void renderTeams(UiCanvas canvas, WarPlannerSnapshot snapshot, float width, float top, float bottom) {
        if (snapshot.teams().isEmpty()) {
            text(canvas, "No war teams yet.", PADDING, top + 22, 13, color(TEXT_MUTED), false);
            return;
        }
        int start = Math.min(scrollRows, Math.max(0, snapshot.teams().size() - 1));
        float y = top;
        RosterMember caller = snapshot.caller();
        for (int index = start; index < snapshot.teams().size() && y + ROW_HEIGHT <= bottom; index++, y += ROW_HEIGHT) {
            Team team = snapshot.teams().get(index);
            boolean ownTeam = caller != null && caller.teamId() != null && caller.teamId() == team.id();
            canvas.fillRect(PADDING, y + 2, width - PADDING * 2, ROW_HEIGHT - 4,
                    color(ownTeam ? ACCENT_PRIMARY_DARK : BACKGROUND_CONTENT));
            text(canvas, truncate(team.name() + (ownTeam ? " · Your team" : ""), 28),
                    PADDING + 8, y + 13, 13, color(TEXT_PRIMARY), false);
            List<WarCompositionRole> compositionRoles = teamCompositionRoles(snapshot, team);
            float summaryX = renderCompositionIcons(canvas, compositionRoles, PADDING + 8, y + 20);
            float summaryRight = manager.canManage() ? width - 148 : width - PADDING;
            int summaryCharacters = availableCharacters(summaryX, summaryRight, 10, 70);
            text(canvas, truncate(compositionLabel(compositionRoles) + " · " + teamSummary(team), summaryCharacters),
                    summaryX + iconTextGap(compositionRoles), y + 28, 10, color(TEXT_MUTED), false);
            if (manager.canManage()) {
                button(canvas, width - 140, y + 8, 52, BUTTON_HEIGHT, "Edit", false, manager.isMutating());
                boolean confirming = pendingDeleteTeamId != null && pendingDeleteTeamId == team.id();
                button(canvas, width - 82, y + 8, 70, BUTTON_HEIGHT,
                        confirming ? "Confirm" : "Delete", true, manager.isMutating());
            }
        }
    }

    private void renderZones(UiCanvas canvas, WarPlannerSnapshot snapshot, float width, float top, float bottom) {
        if (snapshot.zones().isEmpty()) {
            text(canvas, "No territory zones yet.", PADDING, top + 22, 13, color(TEXT_MUTED), false);
            return;
        }
        int start = Math.min(scrollRows, Math.max(0, snapshot.zones().size() - 1));
        float y = top;
        for (int index = start; index < snapshot.zones().size() && y + ROW_HEIGHT <= bottom; index++, y += ROW_HEIGHT) {
            Zone zone = snapshot.zones().get(index);
            canvas.fillRect(PADDING, y + 2, width - PADDING * 2, ROW_HEIGHT - 4, color(BACKGROUND_CONTENT));
            canvas.fillRect(PADDING + 7, y + 8, 8, 22, parseColor(zone.color(), color(ACCENT_PRIMARY)));
            text(canvas, truncate(zone.name(), 24), PADDING + 22, y + 13, 13, color(TEXT_PRIMARY), false);
            String assigned = zone.assignedTeamId() == null ? "Unassigned" : teamName(snapshot, zone.assignedTeamId());
            text(canvas, zone.territories().size() + " territories · " + assigned,
                    PADDING + 22, y + 28, 10, color(TEXT_MUTED), false);
            if (manager.canManage()) {
                button(canvas, width - 140, y + 8, 52, BUTTON_HEIGHT, "Edit", false, manager.isMutating());
                boolean confirming = pendingDeleteZoneId != null && pendingDeleteZoneId == zone.id();
                button(canvas, width - 82, y + 8, 70, BUTTON_HEIGHT,
                        confirming ? "Confirm" : "Delete", true, manager.isMutating());
            }
        }
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
        canvas.fillRect(x + 12, fieldY, w - 24, 24,
                color(teamNameFocused ? CONTROL_INPUT_HOVER : CONTROL_INPUT));
        canvas.strokeRect(x + 12, fieldY, w - 24, 24, 1, color(CONTROL_BORDER));
        text(canvas, teamName.isBlank() ? "Team name" : teamName, x + 18, fieldY + 12, 12,
                color(teamName.isBlank() ? TEXT_MUTED : TEXT_PRIMARY), false);
        text(canvas, "Click to cycle duty. Capabilities: Solo wand · DPS relik · Tank spear", x + 12, fieldY + 38, 10,
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
            text(canvas, truncate(member.displayName(), w >= 360 ? 24 : 14), x + 18, rowY + 14, 11,
                    color(TEXT_PRIMARY), false);
            float dutyX = x + w - 92;
            float rolesX = Math.max(x + 104, dutyX - 52);
            renderCompositionIcons(canvas, member.compositionRoles(), rolesX, rowY + 8);
            text(canvas, selected == null ? "Off" : roleLabel(selected.role()), x + w - 92, rowY + 14, 10,
                    color(selected == null ? TEXT_MUTED : TEXT_PRIMARY), false);
        }
        canvas.resetScissor();
        text(canvas, teamMembers.size() + "/5 slots", x + 12, y + h - 22, 11, color(TEXT_MUTED), false);
        button(canvas, x + w - 148, y + h - 32, 64, BUTTON_HEIGHT, "Cancel", false, teamEditorSaving);
        button(canvas, x + w - 78, y + h - 32, 66, BUTTON_HEIGHT,
                teamEditorSaving ? "Saving…" : "Save", false, teamEditorSaving);
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
        float tabWidth = Math.min(120, (width - PADDING * 2) / 3f);
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
        if (snapshot == null || !manager.canManage() || my < contentTop() || my > height - 42) {
            return false;
        }
        int row = scrollRows + Math.max(0, (int) ((my - contentTop()) / ROW_HEIGHT));
        float rowY = contentTop() + (row - scrollRows) * ROW_HEIGHT;
        if (tab == Tab.TEAMS && row < snapshot.teams().size()) {
            Team team = snapshot.teams().get(row);
            if (hit(mx, my, width - 140, rowY + 8, 52, BUTTON_HEIGHT)) {
                beginTeamEdit(team);
                return true;
            }
            if (hit(mx, my, width - 82, rowY + 8, 70, BUTTON_HEIGHT)) {
                if (pendingDeleteTeamId != null && pendingDeleteTeamId == team.id()) {
                    showResult(manager.deleteTeam(team.id()));
                    pendingDeleteTeamId = null;
                } else {
                    pendingDeleteTeamId = team.id();
                }
                return true;
            }
        }
        if (tab == Tab.ZONES && row < snapshot.zones().size()) {
            Zone zone = snapshot.zones().get(row);
            if (hit(mx, my, width - 140, rowY + 8, 52, BUTTON_HEIGHT)) {
                SeqClient.mc.setScreen(new WarTerritoryPickerScreen(this, zone));
                return true;
            }
            if (hit(mx, my, width - 82, rowY + 8, 70, BUTTON_HEIGHT)) {
                if (pendingDeleteZoneId != null && pendingDeleteZoneId == zone.id()) {
                    showResult(manager.deleteZone(zone.id()));
                    pendingDeleteZoneId = null;
                } else {
                    pendingDeleteZoneId = zone.id();
                }
                return true;
            }
        }
        return false;
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
            teamNameFocused = true;
            return true;
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
        teamNameFocused = false;
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int delta = scrollY > 0 ? -1 : 1;
        WarPlannerSnapshot snapshot = manager.snapshot();
        if (snapshot == null) return true;
        if (teamEditorOpen) {
            editorScrollRows = clampRows(editorScrollRows + delta, editableRoster(snapshot).size());
        } else {
            int size = switch (tab) {
                case ROSTER -> snapshot.roster().size();
                case TEAMS -> snapshot.teams().size();
                case ZONES -> snapshot.zones().size();
            };
            scrollRows = clampRows(scrollRows + delta, size);
        }
        return true;
    }

    @Override
    public boolean keyPressed(@NotNull KeyEvent event) {
        if (teamNameFocused) {
            if (event.key() == GLFW.GLFW_KEY_ESCAPE || event.key() == GLFW.GLFW_KEY_ENTER
                    || event.key() == GLFW.GLFW_KEY_KP_ENTER) {
                teamNameFocused = false;
                return true;
            }
            if (event.key() == GLFW.GLFW_KEY_BACKSPACE && !teamName.isEmpty()) {
                teamName = teamName.substring(0, teamName.length() - 1);
                return true;
            }
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(@NotNull CharacterEvent event) {
        if (teamNameFocused) {
            String typed = TextInputHelper.getTypedText(event);
            if (typed != null && typed.length() == 1 && !Character.isISOControl(typed.charAt(0)) && teamName.length() < 64) {
                teamName += typed;
            }
            return true;
        }
        return super.charTyped(event);
    }

    @Override
    public void onClose() {
        SeqClient.mc.setScreen(parent);
    }

    private void beginTeamEdit(Team team) {
        closeTeamEditor();
        teamEditorOpen = true;
        flashMessage = null;
        editingTeamId = team == null ? null : team.id();
        teamName = team == null ? "New team" : team.name();
        if (team == null) {
            RosterMember caller = manager.snapshot() == null ? null : manager.snapshot().caller();
            RosterMember initialLeader = caller != null && caller.teamId() == null
                    ? caller
                    : manager.snapshot() == null ? null : manager.snapshot().roster().stream()
                            .filter(member -> member.teamId() == null)
                            .findFirst()
                            .orElse(null);
            if (initialLeader != null) {
                teamMembers.add(new TeamMemberDraft(initialLeader.playerUuid(), WarTeamRole.WAR_LEADER));
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
                    teamMembers.add(new TeamMemberDraft(member.playerUuid(), member.role()));
                } else {
                    staleMembers++;
                }
            }
            if (staleMembers > 0) {
                flashMessage = staleMembers + " former member" + (staleMembers == 1 ? " was" : "s were")
                        + " removed from this draft. Save to apply.";
            }
        }
        teamNameFocused = true;
    }

    private void closeTeamEditor() {
        teamEditorOpen = false;
        editingTeamId = null;
        teamName = "";
        teamMembers.clear();
        teamNameFocused = false;
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
            TeamDraft draft = new TeamDraft(teamName, version, teamMembers);
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
            teamMembers.add(new TeamMemberDraft(member.playerUuid(), WarTeamRole.WARRER));
            return;
        }
        teamMembers.remove(current);
        switch (current.role()) {
            case WARRER -> teamMembers.add(new TeamMemberDraft(member.playerUuid(), WarTeamRole.ECOER));
            case ECOER -> {
                teamMembers.removeIf(draft -> draft.role() == WarTeamRole.WAR_LEADER);
                teamMembers.add(new TeamMemberDraft(member.playerUuid(), WarTeamRole.WAR_LEADER));
            }
            case WAR_LEADER -> {
                // Cycling the leader removes them; validation explains that a new leader is required.
            }
        }
    }

    private TeamMemberDraft teamMember(String playerUuid) {
        return teamMembers.stream()
                .filter(member -> member.playerUuid().equalsIgnoreCase(playerUuid))
                .findFirst()
                .orElse(null);
    }

    private List<RosterMember> editableRoster(WarPlannerSnapshot snapshot) {
        return snapshot.roster().stream()
                .filter(member -> member.teamId() == null
                        || (editingTeamId != null && member.teamId().longValue() == editingTeamId))
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
            case READY -> manager.isMutating() ? "Saving…" : "Live";
            case FORBIDDEN -> "Unavailable";
            case OFFLINE -> "Offline · cached";
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

    private static List<WarCompositionRole> teamCompositionRoles(WarPlannerSnapshot snapshot, Team team) {
        List<WarCompositionRole> roles = new ArrayList<>();
        for (TeamMember member : team.members()) {
            snapshot.roster().stream()
                    .filter(candidate -> samePlayer(candidate.playerUuid(), member.playerUuid()))
                    .findFirst()
                    .ifPresent(candidate -> roles.addAll(candidate.compositionRoles()));
        }
        return WarCompositionRole.ordered(roles);
    }

    private static boolean samePlayer(String left, String right) {
        return left != null && right != null && left.equalsIgnoreCase(right);
    }

    private static int availableCharacters(float left, float right, float fontSize, int maximum) {
        float approximateCharacterWidth = fontSize * .55f;
        return Math.max(1, Math.min(maximum, (int) ((right - left) / approximateCharacterWidth)));
    }

    private static String teamSummary(Team team) {
        return team.members().stream()
                .sorted(Comparator.comparingInt(TeamMember::position))
                .map(member -> (member.minecraftUsername() == null ? member.playerUuid() : member.minecraftUsername())
                        + " (" + roleLabel(member.role()) + ")")
                .reduce((left, right) -> left + " · " + right)
                .orElse("No members");
    }

    private static String teamName(WarPlannerSnapshot snapshot, Long id) {
        Team team = snapshot.team(id);
        return team == null ? "Team #" + id : team.name();
    }

    private static String roleLabel(WarTeamRole role) {
        if (role == null) return "Unknown";
        return switch (role) {
            case WAR_LEADER -> "Leader";
            case WARRER -> "Warrer";
            case ECOER -> "Ecoer";
        };
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
