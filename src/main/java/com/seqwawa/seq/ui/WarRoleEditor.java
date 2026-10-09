package com.seqwawa.seq.ui;

import static com.seqwawa.seq.managers.ThemeManager.color;
import static com.seqwawa.seq.ui.theme.UiColor.*;
import static com.seqwawa.seq.ui.WarPlannerDialogUi.*;

import com.seqwawa.seq.managers.WarPlannerManager;
import com.seqwawa.seq.model.war.WarCompositionRole;

import com.seqwawa.seq.model.war.WarPlannerSnapshot;
import com.seqwawa.seq.model.war.WarPlannerSnapshot.RosterMember;
import com.seqwawa.seq.utils.rendering.UiCanvas;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/** Owns a war-planner dialog, including its draft and in-flight request session. */
final class WarRoleEditor {
    private boolean roleEditorOpen;
    private boolean roleEditorSaving;
    private final EnumSet<WarCompositionRole> selectedCompositionRoles =
            EnumSet.noneOf(WarCompositionRole.class);

    private final WarPlannerManager manager;
    private final Executor clientExecutor;
    private final Consumer<String> feedback;
    private long generation;

    WarRoleEditor(WarPlannerManager manager, Executor clientExecutor, Consumer<String> feedback) {
        this.manager = manager;
        this.clientExecutor = clientExecutor;
        this.feedback = feedback;
    }

    private void setMessage(String message) {
        feedback.accept(message);
    }

    boolean isOpen() { return roleEditorOpen; }

    void render(UiCanvas canvas, float width, float height, float nvgMouseX, float nvgMouseY) {
        Layout layout = layout(width, height);
        float x = layout.modal().x(), y = layout.modal().y();
        float w = layout.modal().width(), h = layout.modal().height();
        canvas.fillRect(0, 0, width, height, color(BACKGROUND_MODAL_OVERLAY));
        canvas.fillRect(x, y, w, h, plannerBackground(color(BACKGROUND_BODY_OPAQUE)));
        canvas.strokeRect(x, y, w, h, 1, color(CONTROL_BORDER));
        text(canvas, "Your war capabilities", x + 14, y + 22, 16, color(ACCENT_PRIMARY), false);
        text(canvas, "Select every war capability you can play.",
                x + 14, y + 43, 9, color(TEXT_MUTED), false);

        float optionY = y + 62;
        WarCompositionRole[] roles = WarCompositionRole.values();
        for (int index = 0; index < roles.length; index++) {
            WarCompositionRole role = roles[index];
            UiBounds option = layout.option(index);
            float optionX = option.x();
            boolean selected = selectedCompositionRoles.contains(role);
            option.fill(canvas,
                    color(selected ? ACCENT_PRIMARY_DARK : BACKGROUND_CONTENT));
            option.stroke(canvas, 1,
                    color(selected ? ACCENT_PRIMARY : CONTROL_BORDER));
            renderCompositionIcons(canvas, List.of(role), optionX + 10, optionY + 8);
            text(canvas, (selected ? "✓ " : "") + role.label(), optionX + 30, optionY + 15, 11,
                    color(selected ? TEXT_PRIMARY : TEXT_SECONDARY), false);
            text(canvas, selected ? "Selected" : "Not selected", optionX + 10, optionY + 32, 8,
                    color(TEXT_MUTED), false);
        }
        button(canvas, nvgMouseX, nvgMouseY, layout.cancel(), "Cancel", false, roleEditorSaving);
        primaryButton(canvas, nvgMouseX, nvgMouseY, layout.save(),
                roleEditorSaving ? "Saving…" : "Save", roleEditorSaving);
    }

    boolean click(float mx, float my, float width, float height) {
        if (roleEditorSaving) return true;
        Layout layout = layout(width, height);
        if (layout.cancel().contains(mx, my)) {
            close();
            return true;
        }
        if (layout.save().contains(mx, my)) {
            saveCompositionRoles();
            return true;
        }
        WarCompositionRole[] roles = WarCompositionRole.values();
        for (int index = 0; index < roles.length; index++) {
            if (layout.option(index).contains(mx, my)) {
                WarCompositionRole role = roles[index];
                if (!selectedCompositionRoles.remove(role)) selectedCompositionRoles.add(role);
                return true;
            }
        }
        return true;
    }

    void open() {
        WarPlannerSnapshot snapshot = manager.snapshot();
        RosterMember caller = snapshot == null ? null : snapshot.caller();
        if (caller == null || manager.isMutating()) {
            return;
        }
        close();
        selectedCompositionRoles.clear();
        selectedCompositionRoles.addAll(caller.compositionRoles());
        roleEditorOpen = true;
        roleEditorSaving = false;
        setMessage(null);
    }

    void close() {
        generation++;
        roleEditorOpen = false;
        roleEditorSaving = false;
        selectedCompositionRoles.clear();
    }

    private void saveCompositionRoles() {
        long requestGeneration = generation;
        roleEditorSaving = true;
        manager.updateCompositionRoles(WarCompositionRole.ordered(List.copyOf(selectedCompositionRoles)))
                .whenComplete((result, error) -> clientExecutor.execute(() -> {
                    if (requestGeneration != generation) return;
                    roleEditorSaving = false;
                    if (error != null || result == null || !result.success()) {
                        setMessage(error != null
                                ? "War planner request failed."
                                : result == null ? "No response." : result.message());
                        return;
                    }
                    setMessage(result.message());
                    close();
                }));
    }

    static Layout layout(float width, float height) {
        float w = Math.max(1, Math.min(420, width - PADDING * 2)), h = 176;
        float x = (width - w) / 2, y = (height - h) / 2;
        return new Layout(new UiBounds(x, y, w, h),
                new UiBounds(x + w - 154, y + h - 34, 66, BUTTON_HEIGHT),
                new UiBounds(x + w - 80, y + h - 34, 66, BUTTON_HEIGHT));
    }

    record Layout(UiBounds modal, UiBounds cancel, UiBounds save) {
        UiBounds option(int index) {
            float w = (modal.width() - 44) / 3;
            return new UiBounds(modal.x() + 14 + index * (w + 8), modal.y() + 62, w, 42);
        }
    }
}
