package com.seqwawa.seq.model;

import java.util.Locale;

/**
 * One build the guild treats as meta, as the backend describes it, so adding one
 * is an edit on the backend rather than a new jar.
 * <p>
 * {@link #key} is the stable identifier stored in profiles; {@link #label} is only
 * ever shown, so renaming it leaves saved profiles intact.
 */
public record RaidBuild(String key, String label, int position) {

    public RaidBuild {
        key = normalizeKey(key);
        label = label == null || label.isBlank() ? prettify(key) : label.trim();
    }

    /** For tests and for a catalog entry with no ordering. */
    public static RaidBuild of(String key, String label) {
        return new RaidBuild(key, label, 0);
    }

    public String displayName() {
        return label;
    }

    public boolean isValid() {
        return !key.isEmpty();
    }

    public boolean matches(String otherKey) {
        return otherKey != null && key.equals(normalizeKey(otherKey));
    }

    /** Keys compare uppercase, so casing in a payload does not matter. */
    public static String normalizeKey(String key) {
        return key == null ? "" : key.trim().toUpperCase(Locale.ROOT);
    }

    /** Turns {@code CSPRING} into {@code Cspring} when the backend sent no label. */
    private static String prettify(String key) {
        if (key.isEmpty()) {
            return "";
        }
        return key.charAt(0) + key.substring(1).toLowerCase(Locale.ROOT);
    }
}
