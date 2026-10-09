package com.seqwawa.seq.ui;

import static org.junit.jupiter.api.Assertions.*;

import com.seqwawa.seq.managers.WarPlannerManager;
import com.seqwawa.seq.model.war.WarCompositionRole;
import com.seqwawa.seq.model.war.WarPlannerAccess;
import com.seqwawa.seq.model.war.WarPlannerDrafts.TeamDraft;
import com.seqwawa.seq.model.war.WarPlannerDrafts.SupportDraft;
import com.seqwawa.seq.model.war.WarPlannerSnapshot;
import com.seqwawa.seq.model.war.WarPlannerSnapshot.RosterMember;
import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

class WarPlannerDialogsTest {
    @Test
    void teamSearchAndMenuOwnFocusAndSavingUsesOnlyClickedVisibleRows() {
        Harness h = new Harness();
        WarTeamEditor editor = new WarTeamEditor(h.manager, h.callbacks::add, h.feedback::add);
        editor.open(null);
        var layout = WarTeamEditor.layout(640, 480);
        click(editor, layout.type());
        assertTrue(editor.keyPressed(GLFW.GLFW_KEY_ESCAPE));
        assertFalse(editor.charTyped("Alpha"));
        click(editor, layout.search());
        assertTrue(editor.charTyped("Alpha"));
        assertTrue(editor.keyPressed(GLFW.GLFW_KEY_ENTER));
        assertFalse(editor.charTyped("discarded"));
        UiBounds firstRow = layout.roster().row(0);
        editor.click(firstRow.x() - 10, firstRow.y() + 10, 640, 480);
        click(editor, layout.save());
        TeamDraft draft = (TeamDraft) h.arguments[0];
        assertEquals(List.of("self"), draft.members().stream().map(m -> m.playerUuid()).toList());
        h.next.complete(h.snapshot);
        h.callbacks.removeFirst().run();
        assertFalse(editor.isOpen());
        assertEquals("War team created.", h.feedback.getLast());
    }

    @Test
    void teamSaveCompletionCannotCloseAReopenedDraft() {
        Harness h = new Harness();
        WarTeamEditor editor = new WarTeamEditor(h.manager, h.callbacks::add, h.feedback::add);
        editor.open(null);
        click(editor, WarTeamEditor.layout(640, 480).save());
        h.next.complete(h.snapshot);
        editor.close();
        editor.open(null);
        int messages = h.feedback.size();
        h.callbacks.removeFirst().run();
        assertTrue(editor.isOpen());
        assertEquals(messages, h.feedback.size());
    }

    @Test
    void supportIgnoresClippedRowsAndCompletionAfterClosure() {
        Harness h = new Harness();
        WarSupportEditor editor = new WarSupportEditor(h.manager, h.callbacks::add, h.feedback::add);
        editor.open(0);
        var shortLayout = WarSupportEditor.layout(640, 160);
        UiBounds clipped = shortLayout.roster().row(0);
        editor.click(clipped.x() + 10, clipped.y() + 3, 640, 160);
        assertNull(h.method);
        var normal = WarSupportEditor.layout(640, 480);
        UiBounds row = normal.roster().row(0);
        editor.click(row.x() - 1, row.y() + 8, 640, 480);
        assertNull(h.method);
        editor.click(row.x() + 8, row.y() + 8, 640, 480);
        assertEquals("updateSupport", h.method);
        assertEquals("LEAD", ((SupportDraft) h.arguments[0]).slots().getFirst().code());
        editor.close();
        h.next.complete(h.snapshot);
        int messages = h.feedback.size();
        h.callbacks.removeFirst().run();
        assertFalse(editor.isOpen());
        assertEquals(messages, h.feedback.size());
    }

    @Test
    void roleSaveRetainsTheSubmittedChoicesAndDoesNotAffectAReopenedEditor() {
        Harness h = new Harness();
        WarRoleEditor editor = new WarRoleEditor(h.manager, h.callbacks::add, h.feedback::add);
        editor.open();
        var layout = WarRoleEditor.layout(640, 480);
        UiBounds option = layout.option(1);
        editor.click(option.x() + 5, option.y() + 5, 640, 480);
        editor.click(layout.save().x() + 5, layout.save().y() + 5, 640, 480);
        assertEquals("updateCompositionRoles", h.method);
        assertTrue(((List<?>) h.arguments[0]).contains(WarCompositionRole.values()[1]));
        h.next.complete(h.snapshot);
        editor.close();
        editor.open();
        int messages = h.feedback.size();
        h.callbacks.removeFirst().run();
        assertTrue(editor.isOpen());
        assertEquals(messages, h.feedback.size());
    }

    @Test
    void pingSearchAndDismissalDoNotLeakAnOldCompletionIntoAnotherSession() {
        Harness h = new Harness();
        WarPingPicker picker = new WarPingPicker(h.manager, h.callbacks::add, h.feedback::add);
        picker.open();
        assertTrue(picker.charTyped("Alpha"));
        picker.scroll(100);
        var layout = WarPingPicker.layout(640, 480);
        UiBounds action = layout.ping(0);
        picker.click(action.x() + 5, action.y() + 5, 640, 480);
        assertEquals("pingPlayer", h.method);
        assertEquals("alpha", h.arguments[0]);
        assertTrue(picker.keyPressed(GLFW.GLFW_KEY_ESCAPE));
        assertFalse(picker.isOpen());
        h.next.complete(h.snapshot);
        picker.open();
        int messages = h.feedback.size();
        h.callbacks.removeFirst().run();
        assertTrue(picker.isOpen());
        assertEquals(messages, h.feedback.size());
        assertTrue(picker.charTyped("Beta"));
        picker.click(action.x() + 5, action.y() + 5, 640, 480);
        assertEquals("beta", h.arguments[0]);
    }

    @Test
    void rowHitTestingExcludesGapsPartialRowsAndOutOfBoundsClicks() {
        for (float width : new float[] {280, 420, 640, 1920}) {
            for (float height : new float[] {160, 300, 480, 1080}) {
                List<UiRowList> lists = List.of(WarTeamEditor.layout(width, height).roster(),
                        WarSupportEditor.layout(width, height).roster(), WarPingPicker.layout(width, height).roster());
                for (UiRowList rows : lists) {
                    for (int row = 0; row < 15; row++) {
                        UiBounds bounds = rows.row(row);
                        assertEquals(rows.fullyVisible(row) ? row + 2 : -1,
                                rows.indexAt(bounds.x() + 1, bounds.y() + 1, 2, 100));
                        assertEquals(-1, rows.indexAt(bounds.x() - 1, bounds.y() + 1, 2, 100));
                        assertEquals(-1, rows.indexAt(bounds.x() + 1, bounds.y() - 1, 2, 100));
                    }
                }
            }
        }
    }

    private static void click(WarTeamEditor editor, UiBounds bounds) {
        assertTrue(editor.click(bounds.x() + 5, bounds.y() + 5, 640, 480));
    }

    private static final class Harness {
        final ArrayDeque<Runnable> callbacks = new ArrayDeque<>();
        final List<String> feedback = new ArrayList<>();
        final WarPlannerSnapshot snapshot;
        final WarPlannerManager manager;
        CompletableFuture<WarPlannerSnapshot> next;
        String method;
        Object[] arguments;

        Harness() {
            Instant now = Instant.parse("2026-10-09T12:00:00Z");
            snapshot = new WarPlannerSnapshot(3, now, new WarPlannerSnapshot.Self("self", true), true,
                    List.of(member("self", "You"), member("alpha", "Alpha"), member("beta", "Beta")),
                    List.of(), new WarPlannerSnapshot.SupportBoard(1L, List.of()), List.of(), List.of(), List.of());
            WarPlannerManager.Gateway gateway = (WarPlannerManager.Gateway) Proxy.newProxyInstance(
                    WarPlannerManager.Gateway.class.getClassLoader(), new Class<?>[] {WarPlannerManager.Gateway.class},
                    (proxy, called, args) -> {
                        if (called.getName().equals("access")) return CompletableFuture.completedFuture(
                                new WarPlannerAccess(1, now, "self", null));
                        if (called.getName().equals("snapshot")) return CompletableFuture.completedFuture(snapshot);
                        method = called.getName();
                        arguments = args;
                        next = new CompletableFuture<>();
                        return next;
                    });
            manager = new WarPlannerManager(gateway, Clock.fixed(now, java.time.ZoneOffset.UTC));
            manager.tick(true, true);
            manager.refreshNow().join();
            assertTrue(manager.canManage());
        }

        private static RosterMember member(String uuid, String name) {
            return new RosterMember(uuid, name, "discord-" + uuid, name,
                    List.of(), true, true, null, null);
        }
    }
}
