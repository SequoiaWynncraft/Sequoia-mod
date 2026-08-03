package com.seqwawa.seq.model;

import java.util.Locale;

/**
 * A Sequoia Discord progression rank (Leafkin → Yggdrasil) as published by the
 * backend rank-profile catalog.
 * <p>
 * This is the rank's identity only. Colour is deliberately not part of it: a
 * member may be given an individual colour that overrides their role's, so the
 * two are resolved separately and paired in a {@link RankPresentation}.
 *
 * @param key      catalog role key, e.g. {@code rank.sapling}
 * @param label    human readable name, e.g. {@code Sapling}
 * @param position Discord role position; higher means a more senior rank
 */
public record DiscordRank(String key, String label, int position) {

    /** Catalog category that holds the Sequoia progression ranks. */
    public static final String PROGRESSION_CATEGORY = "progression_rank";

    public DiscordRank {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Discord rank key must not be blank");
        }
        if (label == null || label.isBlank()) {
            throw new IllegalArgumentException("Discord rank label must not be blank");
        }
    }

    /** Uppercase form used inside Wynncraft chat pills. */
    public String pillLabel() {
        return label.toUpperCase(Locale.ROOT);
    }
}
