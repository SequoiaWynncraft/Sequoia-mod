package com.seqwawa.seq.managers;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.GuiMessageTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import org.junit.jupiter.api.Test;

class PrivateMessageGuildTagViewTest {
    private final AtomicInteger requested = new AtomicInteger();
    private final PrivateMessageGuildTagView view = new PrivateMessageGuildTagView(requested::incrementAndGet);

    private static FormattedCharSequence text(String text) {
        return FormattedCharSequence.forward(text, Style.EMPTY);
    }

    private static List<GuiMessage.Line> display(GuiMessage message, List<FormattedCharSequence> lines) {
        List<GuiMessage.Line> result = new ArrayList<>();
        for (int i = lines.size() - 1; i >= 0; i--) {
            result.add(new GuiMessage.Line(message.addedTime(), lines.get(i), message.tag(), i == lines.size() - 1));
        }
        return result;
    }

    private final class Message {
        final GuiMessage original = new GuiMessage(42, Component.literal("PM"), null, GuiMessageTag.system());
        Component decorated = original.content();
        Runnable changed;
        int wraps;
        Function<Component, List<FormattedCharSequence>> layout = component -> component == original.content()
                ? List.of(text("original")) : List.of(text("tagged top"), text("tagged bottom"));

        List<GuiMessage.Line> add() {
            return display(original, view.wrap(original, (component, callback) -> {
                changed = callback;
                return decorated;
            }, component -> {
                wraps++;
                return layout.apply(component);
            }));
        }

        void complete() {
            decorated = Component.literal("[SEQ] PM");
            changed.run();
        }
    }

    @Test
    void replacesOnlyAffectedLinesPreservingScrollAgeTagsAndOtherMessages() {
        Message pm = new Message();
        List<GuiMessage.Line> displayed = pm.add();
        GuiMessage.Line newer = new GuiMessage.Line(99, text("newer"), null, true);
        GuiMessage.Line older = new GuiMessage.Line(1, text("older"), null, true);
        displayed.addFirst(newer);
        displayed.add(older);
        pm.complete();
        List<GuiMessage.Line> notified = new ArrayList<>();
        int scroll = view.refresh(displayed, 2, 1, 100, (message, line) -> {
            assertSame(pm.original, message, "Wynntils must see the original message timestamp");
            notified.add(line);
        });
        assertEquals(3, scroll);
        assertSame(newer, displayed.getFirst());
        assertSame(older, displayed.getLast());
        assertEquals(2, pm.wraps);
        assertEquals(2, notified.size());
        assertEquals(42, displayed.get(1).addedTime());
        assertSame(pm.original.tag(), displayed.get(1).tag());
        assertTrue(displayed.get(1).endOfEntry());
        assertFalse(displayed.get(2).endOfEntry());
        view.refresh(displayed, scroll, 1, 100, (message, line) -> fail("No pending updates"));
        assertEquals(2, pm.wraps);
    }

    @Test
    void batchesDuplicateUpdatesAndSpreadsLargeBurstsAcrossTicks() {
        List<Message> messages = new ArrayList<>();
        List<GuiMessage.Line> displayed = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            Message message = new Message();
            messages.add(message);
            displayed.addAll(message.add());
            message.complete();
            message.changed.run();
        }
        view.refresh(displayed, 0, 10, 100, (message, line) -> {});
        int wrapped = messages.stream().mapToInt(message -> message.wraps - 1).sum();
        assertTrue(wrapped > 0 && wrapped <= 4);
        for (int i = 0; i < 12; i++) view.refresh(displayed, 0, 10, 100, (message, line) -> {});
        assertEquals(24, displayed.size());
        messages.forEach(message -> assertEquals(2, message.wraps));
    }

    @Test
    void neverRewrapsOrResurrectsClearedAndDeletedMessages() {
        Message removed = new Message();
        List<GuiMessage.Line> displayed = removed.add();
        displayed.clear();
        GuiMessage.Line replacement = new GuiMessage.Line(42, text("deleted"), null, true);
        displayed.add(replacement);
        removed.complete();
        view.refresh(displayed, 0, 10, 100, (message, line) -> fail("Removed message"));
        assertEquals(List.of(replacement), displayed);
        assertEquals(1, removed.wraps);
    }

    @Test
    void respectsHistoryCapForPartiallyRetainedMessagesAndKeepsBottomPinned() {
        Message pm = new Message();
        pm.layout = component -> component == pm.original.content()
                ? List.of(text("a"), text("b"), text("c"))
                : List.of(text("tag"), text("a"), text("b"), text("c"));
        List<GuiMessage.Line> displayed = pm.add();
        displayed.removeLast();
        pm.complete();
        assertEquals(0, view.refresh(displayed, 0, 1, 2, (message, line) -> {}));
        assertEquals(2, displayed.size());
        assertTrue(displayed.getFirst().endOfEntry());
    }

    @Test
    void resizeBeforeCompletionUsesLatestWrappingAndUnchangedResultsDoNotWrap() {
        Message pm = new Message();
        pm.add();
        pm.layout = component -> List.of(text(component.getString()));
        List<GuiMessage.Line> displayed = pm.add();
        pm.changed.run();
        view.refresh(displayed, 0, 10, 100, (message, line) -> fail("Tag did not change"));
        assertEquals(2, pm.wraps);
        pm.complete();
        view.refresh(displayed, 0, 10, 100, (message, line) -> {});
        assertEquals(1, displayed.size());
        assertEquals(3, pm.wraps);
    }

    @Test
    void removingTagShrinksOnlyItsMessageAndAdjustsAnchor() {
        Message pm = new Message();
        pm.decorated = Component.literal("[SEQ] PM");
        List<GuiMessage.Line> displayed = pm.add();
        GuiMessage.Line older = new GuiMessage.Line(1, text("older"), null, true);
        displayed.add(older);
        pm.decorated = pm.original.content();
        pm.changed.run();
        assertEquals(1, view.refresh(displayed, 2, 1, 100, (message, line) -> {}));
        assertEquals(2, displayed.size());
        assertSame(older, displayed.getLast());
    }
}
