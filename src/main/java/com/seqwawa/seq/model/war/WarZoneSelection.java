package com.seqwawa.seq.model.war;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Small immutable selection value used by the map picker and its tests. */
public record WarZoneSelection(Set<String> names) {
    public WarZoneSelection {
        names = names == null ? Set.of() : Set.copyOf(new LinkedHashSet<>(names));
    }

    public static WarZoneSelection of(List<String> names) {
        return new WarZoneSelection(names == null ? Set.of() : new LinkedHashSet<>(names));
    }

    public WarZoneSelection toggle(String name) {
        if (name == null || name.isBlank()) return this;
        LinkedHashSet<String> changed = new LinkedHashSet<>(names);
        if (!changed.remove(name)) changed.add(name);
        return new WarZoneSelection(changed);
    }

    public boolean contains(String name) {
        return names.contains(name);
    }

    public List<String> sortedNames() {
        return names.stream().sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }
}
