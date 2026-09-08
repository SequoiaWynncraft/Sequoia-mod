package com.seqwawa.seq.ui;

import java.util.HashSet;
import java.util.Set;

/** Per-screen expansion choices; search reveals matches without changing those choices. */
final class SettingsSectionExpansion {
    private final Set<Section> expanded = new HashSet<>();

    boolean isCollapsed(String category, String section, boolean searching) {
        return section != null && !searching && !expanded.contains(new Section(category, section));
    }

    void toggle(String category, String section) {
        Section key = new Section(category, section);
        if (!expanded.remove(key)) {
            expanded.add(key);
        }
    }

    private record Section(String category, String name) {}
}
