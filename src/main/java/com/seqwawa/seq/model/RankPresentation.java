package com.seqwawa.seq.model;

import com.seqwawa.seq.utils.ColorRamp;

/**
 * How a member's progression rank should be drawn: the rank itself, plus the
 * colours resolved for that member.
 * <p>
 * The two are paired here rather than folded into {@link DiscordRank} because
 * they come from different places. The rank is a property of the role, shared by
 * everyone who holds it; the colours may be individual to the member, so two
 * people of the same rank can legitimately render in different colours.
 *
 * @param rank   the member's progression rank
 * @param colors the stops to draw it in, empty when neither the member nor their
 *               role publishes a colour
 */
public record RankPresentation(DiscordRank rank, ColorRamp colors) {

    public RankPresentation {
        if (rank == null) {
            throw new IllegalArgumentException("Rank presentation requires a rank");
        }
        colors = colors == null ? ColorRamp.empty() : colors;
    }

    /** Label drawn inside the chat pill. */
    public String pillLabel() {
        return rank.pillLabel();
    }

    /** Human readable rank name, e.g. {@code Sapling}. */
    public String label() {
        return rank.label();
    }
}
