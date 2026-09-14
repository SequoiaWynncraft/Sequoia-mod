package com.seqwawa.seq.model;

import java.util.Locale;

/**
 * One build the guild treats as meta, as the backend describes it.
 * <p>
 * This used to be an enum with the eight builds written into the mod. It is a
 * record now because the meta is the guild's to change, not a release's: adding
 * a build should be an edit on the backend, not a new jar for everyone.
 * <p>
 * {@link #key} is the stable identifier stored in profiles; {@link #label} is
 * only ever shown. Renaming the label leaves every saved profile intact.
 */
public record RaidBuild(String key, String label, int position) {

    public RaidBuild {
        key = normalizeKey(key);
        label = label == null || label.isBlank() ? prettify(key) : label.trim();
    }

    /** Convenience for tests and for a catalog entry that carries no ordering. */
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

    /** Build keys are compared uppercase, so casing in a payload never matters. */
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
