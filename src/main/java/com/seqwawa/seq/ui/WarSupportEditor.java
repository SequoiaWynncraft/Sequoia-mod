package com.seqwawa.seq.ui;

import static com.seqwawa.seq.managers.ThemeManager.color;
import static com.seqwawa.seq.ui.theme.UiColor.*;
import static com.seqwawa.seq.ui.WarPlannerDialogUi.*;

import com.seqwawa.seq.managers.WarPlannerManager;
import com.seqwawa.seq.model.war.WarPlannerDrafts.SupportDraft;
import com.seqwawa.seq.model.war.WarPlannerDrafts.SupportSlotDraft;
import com.seqwawa.seq.model.war.WarPlannerSnapshot;
import com.seqwawa.seq.model.war.WarPlannerSnapshot.RosterMember;
import com.seqwawa.seq.model.war.WarPlannerSnapshot.SupportSlot;
import com.seqwawa.seq.utils.rendering.UiCanvas;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/** Owns a war-planner dialog, including its draft and in-flight request session. */
final class WarSupportEditor {
    private Integer editingSupportSlot;
    private int supportEditorScrollRows;
    private boolean supportEditorSaving;

    private final WarPlannerManager manager;
    private final Executor clientExecutor;
    private final Consumer<String> feedback;
    private long generation;

    WarSupportEditor(WarPlannerManager manager, Executor clientExecutor, Consumer<String> feedback) {
        this.manager = manager;
        this.clientExecutor = clientExecutor;
        this.feedback = feedback;
    }

    private void setMessage(String message) {
        feedback.accept(message);
    }

    boolean isOpen() { return editingSupportSlot != null; }

    void open(int slot) { close(); editingSupportSlot = slot; }

    void render(UiCanvas canvas, float width, float height, float nvgMouseX, float nvgMouseY) {
        WarPlannerSnapshot snapshot = manager.snapshot();
        if (snapshot == null || editingSupportSlot == null) return;
        String label = editingSupportSlot == 0 ? "Lead" : "Eco " + editingSupportSlot;
        Layout layout = layout(width, height);
        float x = layout.modal().x(), y = layout.modal().y();
        float w = layout.modal().width(), h = layout.modal().height();
        canvas.fillRect(0, 0, width, height, color(BACKGROUND_MODAL_OVERLAY));
        canvas.fillRect(x, y, w, h, plannerBackground(color(BACKGROUND_BODY_OPAQUE)));
        canvas.strokeRect(x, y, w, h, 1, color(CONTROL_BORDER));
        text(canvas, "Assign shared " + label, x + 14, y + 21, 16, color(ACCENT_PRIMARY), false);
        text(canvas, "Support members can also belong to any party.", x + 14, y + 39, 10, color(TEXT_MUTED), false);
        button(canvas, nvgMouseX, nvgMouseY, layout.close(), "×", true, supportEditorSaving);

        List<RosterMember> candidates = supportCandidates(snapshot, editingSupportSlot);
        float listTop = layout.roster().area().y();
        float listBottom = listTop + layout.roster().area().height();
        canvas.scissor(x + 8, listTop, w - 16, listBottom - listTop);
        int start = Math.min(supportEditorScrollRows, Math.max(0, candidates.size() - 1));
        String selectedUuid = supportSlot(snapshot, editingSupportSlot) == null
                ? null : supportSlot(snapshot, editingSupportSlot).playerUuid();
        for (int index = start; index < candidates.size() && layout.roster().fullyVisible(index - start); index++) {
            UiBounds rowBounds = layout.roster().row(index - start);
            float rowY = rowBounds.y() - 2;
            RosterMember candidate = candidates.get(index);
            boolean selected = samePlayer(selectedUuid, candidate.playerUuid());
            rowBounds.fill(canvas,
                    color(selected ? ACCENT_PRIMARY_DARK : BACKGROUND_CONTENT));
            text(canvas, truncate(candidate.displayName(), 28), x + 18, rowY + 14, 11, color(TEXT_PRIMARY), false);
            text(canvas, candidate.online() ? "Online" : "Offline · currently assigned", x + w - 150, rowY + 14,
                    9, color(candidate.online() ? CONTROL_SUCCESS : TEXT_MUTED), false);
        }
        canvas.resetScissor();
        button(canvas, nvgMouseX, nvgMouseY, layout.clear(), "Clear slot", true, supportEditorSaving);
        button(canvas, nvgMouseX, nvgMouseY, layout.cancel(), "Cancel", false, supportEditorSaving);
    }

    boolean click(float mx, float my, float width, float height) {
        WarPlannerSnapshot snapshot = manager.snapshot();
        if (snapshot == null || editingSupportSlot == null || supportEditorSaving) return true;
        Layout layout = layout(width, height);
        if (layout.close().contains(mx, my)
                || layout.cancel().contains(mx, my)) {
            close();
            return true;
        }
        if (layout.clear().contains(mx, my)) {
            saveSupportSlot(editingSupportSlot, null);
            return true;
        }
        List<RosterMember> candidates = supportCandidates(snapshot, editingSupportSlot);
        int row = layout.roster().indexAt(mx, my,
                Math.min(supportEditorScrollRows, Math.max(0, candidates.size() - 1)), candidates.size());
        if (row >= 0) saveSupportSlot(editingSupportSlot, candidates.get(row).playerUuid());
        return true;
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
        long requestGeneration = generation;
        supportEditorSaving = true;
        manager.saveSupport(new SupportDraft(snapshot.support().version(), slots)).whenComplete((result, error) ->
                clientExecutor.execute(() -> {
                    if (requestGeneration != generation) return;
                    supportEditorSaving = false;
                    if (error != null || result == null || !result.success()) {
                        setMessage(error != null ? "War planner request failed." : result == null ? "No response." : result.message());
                        return;
                    }
                    setMessage(result.message());
                    close();
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

    void close() {
        generation++;
        editingSupportSlot = null;
        supportEditorScrollRows = 0;
        supportEditorSaving = false;
    }

    void scroll(int delta) {
        WarPlannerSnapshot snapshot = manager.snapshot();
        if (snapshot != null && editingSupportSlot != null) {
            supportEditorScrollRows = clampRows(supportEditorScrollRows + delta,
                    supportCandidates(snapshot, editingSupportSlot).size());
        }
    }

    static Layout layout(float width, float height) {
        float w = Math.max(1, Math.min(430, width - PADDING * 2));
        float h = Math.max(0, Math.min(390, height - 44));
        float x = (width - w) / 2, y = (height - h) / 2;
        return new Layout(new UiBounds(x, y, w, h),
                new UiBounds(x + w - 34, y + 9, 24, BUTTON_HEIGHT),
                new UiBounds(x + w - 80, y + h - 32, 68, BUTTON_HEIGHT),
                new UiBounds(x + 12, y + h - 32, 72, BUTTON_HEIGHT),
                new UiRowList(new UiBounds(x + 12, y + 54, w - 24, Math.max(0, h - 96)), 30, 25));
    }

    record Layout(UiBounds modal, UiBounds close, UiBounds cancel, UiBounds clear, UiRowList roster) {}
}
