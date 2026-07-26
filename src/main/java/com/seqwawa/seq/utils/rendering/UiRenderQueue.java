package com.seqwawa.seq.utils.rendering;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/** Thread-safe queue that separates screen-owned, HUD, and resource rendering work. */
public final class UiRenderQueue<C> {
    private final Queue<Entry<C>> entries = new ConcurrentLinkedQueue<>();

    public void submitScreen(Object owner, C command) {
        entries.add(new Entry<>(owner, Scope.SCREEN, Objects.requireNonNull(command)));
    }

    public void submitHud(C command) {
        entries.add(new Entry<>(null, Scope.HUD, Objects.requireNonNull(command)));
    }

    public void submitResource(C command) {
        entries.add(new Entry<>(null, Scope.RESOURCE, Objects.requireNonNull(command)));
    }

    public List<C> drain(Object currentOwner) {
        List<C> commands = new ArrayList<>();
        Entry<C> entry;
        while ((entry = entries.poll()) != null) {
            if (entry.isValidFor(currentOwner)) {
                commands.add(entry.command());
            }
        }
        return List.copyOf(commands);
    }

    public void clear() {
        entries.clear();
    }

    private enum Scope {
        SCREEN,
        HUD,
        RESOURCE
    }

    private record Entry<C>(Object owner, Scope scope, C command) {
        private boolean isValidFor(Object currentOwner) {
            return switch (scope) {
                case SCREEN -> owner == currentOwner;
                case HUD -> currentOwner == null;
                case RESOURCE -> true;
            };
        }
    }
}
