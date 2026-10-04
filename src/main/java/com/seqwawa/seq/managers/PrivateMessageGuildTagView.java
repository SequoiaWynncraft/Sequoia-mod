package com.seqwawa.seq.managers;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import net.minecraft.client.GuiMessage;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/** Updates just the retained display lines of changed PMs, without replaying chat history. */
public final class PrivateMessageGuildTagView {
    private static final int MESSAGES_PER_TICK = 4;
    private final Cache<GuiMessage, Entry> entries = CacheBuilder.newBuilder().weakKeys().maximumSize(512).build();
    private final Set<Entry> dirty = new LinkedHashSet<>();
    private final Runnable requestRefresh;

    public PrivateMessageGuildTagView(Runnable requestRefresh) {
        this.requestRefresh = requestRefresh;
    }

    public List<FormattedCharSequence> wrap(
            GuiMessage message,
            BiFunction<Component, Runnable, Component> decorate,
            Function<Component, List<FormattedCharSequence>> split) {
        Entry entry = entries.getIfPresent(message);
        if (entry == null) {
            entry = new Entry(message);
            entries.put(message, entry);
        }
        entry.decorate = decorate;
        entry.split = split;
        entry.component = decorate.apply(message.content(), entry.changed);
        entry.lines = split.apply(entry.component);
        dirty.remove(entry); // A normal resize/rebuild already applied the latest tag.
        return entry.lines;
    }

    /** Returns the adjusted scroll anchor. Unchanged lines keep their identity and metadata. */
    public int refresh(
            List<GuiMessage.Line> displayed, int scroll, int linesPerPage, int historyLimit,
            BiConsumer<GuiMessage, GuiMessage.Line> addedLine) {
        if (dirty.isEmpty()) return scroll;
        List<GuiMessage.Line> updated = new ArrayList<>(displayed);
        boolean changed = false;
        int remaining = MESSAGES_PER_TICK;
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(1);
        while (remaining-- > 0 && !dirty.isEmpty()) {
            Entry entry = dirty.iterator().next();
            dirty.remove(entry);
            int start = findBottomLine(updated, entry.lines);
            if (start < 0) continue; // Cleared, deleted or no longer retained: never resurrect it.
            int oldCount = Math.min(entry.lines.size(), updated.size() - start);
            boolean matches = true;
            for (int i = 0; i < oldCount; i++) {
                if (updated.get(start + i).content() != entry.lines.get(entry.lines.size() - 1 - i)) {
                    matches = false;
                    break;
                }
            }
            if (!matches) continue;
            Component component = entry.decorate.apply(entry.message.content(), entry.changed);
            if (component == entry.component) continue;
            List<FormattedCharSequence> lines = entry.split.apply(component);
            List<GuiMessage.Line> replacement = new ArrayList<>(lines.size());
            for (int i = lines.size() - 1; i >= 0; i--) {
                GuiMessage.Line line = new GuiMessage.Line(
                        entry.message.addedTime(), lines.get(i), entry.message.tag(), i == lines.size() - 1);
                addedLine.accept(entry.message, line);
                replacement.add(line);
            }
            updated.subList(start, start + oldCount).clear();
            updated.addAll(start, replacement);
            if (scroll > 0) {
                if (scroll >= start + oldCount) scroll += replacement.size() - oldCount;
                else if (scroll >= start) scroll = start + Math.min(scroll - start, Math.max(0, replacement.size() - 1));
            }
            entry.component = component;
            entry.lines = lines;
            changed = true;
            if (System.nanoTime() >= deadline) break;
        }
        if (changed) {
            if (updated.size() > historyLimit) updated.subList(historyLimit, updated.size()).clear();
            // Wynntils uses CopyOnWriteArrayList: commit once per batch, not once per line.
            displayed.clear();
            displayed.addAll(updated);
            scroll = Math.max(0, Math.min(scroll, displayed.size() - linesPerPage));
        }
        if (!dirty.isEmpty()) requestRefresh.run();
        return scroll;
    }

    private static int findBottomLine(List<GuiMessage.Line> displayed, List<FormattedCharSequence> lines) {
        if (lines.isEmpty()) return -1;
        FormattedCharSequence bottom = lines.getLast();
        for (int i = 0; i < displayed.size(); i++) {
            if (displayed.get(i).content() == bottom) return i;
        }
        return -1;
    }

    private final class Entry {
        final GuiMessage message;
        final Runnable changed = () -> {
            dirty.add(this);
            requestRefresh.run();
        };
        BiFunction<Component, Runnable, Component> decorate;
        Function<Component, List<FormattedCharSequence>> split;
        Component component;
        List<FormattedCharSequence> lines = List.of();

        Entry(GuiMessage message) {
            this.message = message;
        }
    }
}
