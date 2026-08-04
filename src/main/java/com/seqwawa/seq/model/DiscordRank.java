package com.seqwawa.seq.model;

import java.util.Locale;

/**
 * A Sequoia Discord progression rank (Leafkin → Yggdrasil) as published by the
 * backend rank-profile catalog.
 *
 * @param key      catalog role key, e.g. {@code rank.sapling}
 * @param label    human readable name, e.g. {@code Sapling}
 * @param position Discord role position; higher means a more senior rank
 * @param color    the Discord role colour as {@code 0xRRGGBB}, or {@code null}
 *                 when the role is uncoloured
 */
public record DiscordRank(String key, String label, int position, Integer color) {

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
