package com.seqwawa.seq.ui;

import static com.seqwawa.seq.managers.ThemeManager.color;
import static com.seqwawa.seq.ui.theme.UiColor.*;
import static com.seqwawa.seq.ui.WarPlannerDialogUi.*;

import com.seqwawa.seq.managers.WarPlannerManager;

import com.seqwawa.seq.model.war.WarPlannerSnapshot;
import com.seqwawa.seq.model.war.WarPlannerSnapshot.RosterMember;
import com.seqwawa.seq.utils.rendering.UiCanvas;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import org.lwjgl.glfw.GLFW;

/** Owns a war-planner dialog, including its draft and in-flight request session. */
final class WarPingPicker {
    private boolean warPingPickerOpen;
    private boolean warPingSending;
    private int warPingScrollRows;
    private String warPingSearchQuery = "";
    private boolean warPingSearchFocused;

    private final WarPlannerManager manager;
    private final Executor clientExecutor;
    private final Consumer<String> feedback;
    private long generation;

    WarPingPicker(WarPlannerManager manager, Executor clientExecutor, Consumer<String> feedback) {
        this.manager = manager;
        this.clientExecutor = clientExecutor;
        this.feedback = feedback;
    }

    private void setMessage(String message) {
        feedback.accept(message);
    }

    boolean isOpen() { return warPingPickerOpen; }

    void render(UiCanvas canvas, float width, float height, float nvgMouseX, float nvgMouseY) {
        WarPlannerSnapshot snapshot = manager.snapshot();
        if (snapshot == null || !manager.canManage()) return;
        Layout layout = layout(width, height);
        float x = layout.modal().x(), y = layout.modal().y();
        float w = layout.modal().width(), h = layout.modal().height();
        canvas.fillRect(0, 0, width, height, color(BACKGROUND_MODAL_OVERLAY));
        canvas.fillRect(x, y, w, h, plannerBackground(color(BACKGROUND_BODY_OPAQUE)));
        canvas.strokeRect(x, y, w, h, 1, color(CONTROL_BORDER));
        text(canvas, "War ping", x + 14, y + 21, 16, color(ACCENT_PRIMARY), false);
        text(canvas, "Notify an online, Discord-linked member in war chat.",
                x + 14, y + 39, 9, color(TEXT_MUTED), false);
        button(canvas, nvgMouseX, nvgMouseY, layout.close(), "×", true, warPingSending);

        float searchY = layout.search().y();
        boolean searchHovered = layout.search().contains(nvgMouseX, nvgMouseY);
        layout.search().fill(canvas,
                color(warPingSearchFocused || searchHovered ? CONTROL_INPUT_HOVER : CONTROL_INPUT));
        layout.search().stroke(canvas, 1,
                color(warPingSearchFocused ? CONTROL_BORDER : CONTROL_INPUT_SECONDARY));
        String searchText = warPingSearchQuery.isEmpty() ? "Search online members…" : warPingSearchQuery;
        if (warPingSearchFocused && (System.currentTimeMillis() / 500) % 2 == 0) searchText += "|";
        canvas.save();
        canvas.scissor(x + 18, searchY, Math.max(0, SequoiaUiStyle.searchWidth(w - 24) - 12), TEAM_EDITOR_SEARCH_HEIGHT);
        text(canvas, searchText, x + 18, searchY + TEAM_EDITOR_SEARCH_HEIGHT / 2, 10,
                color(warPingSearchQuery.isEmpty() ? TEXT_MUTED : TEXT_PRIMARY), false);
        canvas.restore();

        List<RosterMember> candidates = pingCandidates(snapshot, warPingSearchQuery);
        float listTop = layout.roster().area().y();
        float listBottom = listTop + layout.roster().area().height();
        canvas.scissor(x + 8, listTop, w - 16, Math.max(0, listBottom - listTop));
        int start = warPingScrollStart(warPingScrollRows, candidates.size());
        if (candidates.isEmpty()) {
            text(canvas, warPingSearchQuery.isBlank()
                            ? "No eligible online members."
                            : "No eligible members match this search.",
                    x + 18, listTop + 14, 10, color(TEXT_MUTED), false);
        }
        for (int index = start;
                index < candidates.size() && layout.roster().fullyVisible(index - start);
                index++) {
            UiBounds rowBounds = layout.roster().row(index - start);
            float rowY = rowBounds.y() - 2;
            RosterMember member = candidates.get(index);
            rowBounds.fill(canvas,
                    plannerBackground(color(BACKGROUND_CONTENT)));
            float actionX = x + w - 72;
            int nameCharacters = availableCharacters(x + 18, actionX - 8, 11, 24);
            text(canvas, truncate(member.displayName(), nameCharacters), x + 18, rowY + 14, 11,
                    color(TEXT_PRIMARY), false);
            button(canvas, nvgMouseX, nvgMouseY, layout.ping(index - start), "Ping", false,
                    warPingSending || (manager.isRequestInFlight() || manager.isMutating()));
        }
        canvas.resetScissor();
    }

    boolean click(float mx, float my, float width, float height) {
        WarPlannerSnapshot snapshot = manager.snapshot();
        if (snapshot == null || !manager.canManage()) {
            close();
            return true;
        }
        if (warPingSending) return true;
        Layout layout = layout(width, height);
        if (layout.close().contains(mx, my)) {
            close();
            return true;
        }
        if (layout.search().contains(mx, my)) {
            warPingSearchFocused = true;
            return true;
        }
        warPingSearchFocused = false;
        List<RosterMember> candidates = pingCandidates(snapshot, warPingSearchQuery);
        int start = warPingScrollStart(warPingScrollRows, candidates.size());
        int row = layout.roster().indexAt(mx, my, start, candidates.size());
        if (row >= 0 && layout.ping(row - start).contains(mx, my)) sendWarPing(candidates.get(row));
        return true;
    }

    void open() {
        if (!manager.canManage() || manager.isMutating()) return;
        close();
        warPingPickerOpen = true;
        warPingSending = false;
        warPingScrollRows = 0;
        warPingSearchQuery = "";
        warPingSearchFocused = true;
        setMessage(null);
    }

    void close() {
        generation++;
        warPingPickerOpen = false;
        warPingSending = false;
        warPingScrollRows = 0;
        warPingSearchQuery = "";
        warPingSearchFocused = false;
    }

    private void sendWarPing(RosterMember member) {
        if (!canPingPlayer(manager.snapshot(), member) || manager.isMutating()) return;
        long requestGeneration = generation;
        warPingSending = true;
        manager.pingPlayer(member.playerUuid()).whenComplete((result, error) -> clientExecutor.execute(() -> {
            if (requestGeneration != generation) return;
            warPingSending = false;
            if (error != null || result == null || !result.success()) {
                setMessage(error != null
                        ? "War ping failed."
                        : result == null ? "No response from the war planner." : result.message());
                return;
            }
            close();
            setMessage(result.message());
        }));
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
                .filter(member -> query.isEmpty() || rosterSearchText(member).contains(query))
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

    boolean keyPressed(int key) {

        if (!warPingPickerOpen) return false;
        if (key == GLFW.GLFW_KEY_ESCAPE) { close(); return true; }
        if (!warPingSearchFocused) return false;
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            warPingSearchFocused = false;
        } else if (key == GLFW.GLFW_KEY_BACKSPACE && !warPingSearchQuery.isEmpty()) {
            warPingSearchQuery = warPingSearchQuery.substring(0, warPingSearchQuery.length() - 1);
            warPingScrollRows = 0;
        }
        return true;
    }

    boolean charTyped(String text) {
        if (!warPingPickerOpen || !warPingSearchFocused) return false;
        if (text != null && warPingSearchQuery.length() + text.length() <= TEAM_EDITOR_SEARCH_MAX_LENGTH) {
            warPingSearchQuery += text;
            warPingScrollRows = 0;
        }
        return true;
    }

    void scroll(int delta) {
        warPingScrollRows = clampRows(warPingScrollRows + delta, pingCandidates(manager.snapshot(), warPingSearchQuery).size());
    }

    static Layout layout(float width, float height) {
        float w = Math.max(1, Math.min(460, width - PADDING * 2));
        float h = Math.max(0, Math.min(390, height - 44));
        float x = (width - w) / 2, y = (height - h) / 2;
        return new Layout(new UiBounds(x, y, w, h),
                new UiBounds(x + w - 34, y + 9, 24, BUTTON_HEIGHT),
                new UiBounds(x + 12, y + 52, SequoiaUiStyle.searchWidth(w - 24), TEAM_EDITOR_SEARCH_HEIGHT),
                new UiRowList(new UiBounds(x + 12, y + 82, w - 24, Math.max(0, h - 94)), 30, 25));
    }

    record Layout(UiBounds modal, UiBounds close, UiBounds search, UiRowList roster) {
        UiBounds ping(int visibleIndex) {
            return new UiBounds(modal.x() + modal.width() - 72, roster.row(visibleIndex).y() + 2, 54, BUTTON_HEIGHT);
        }
    }
}
