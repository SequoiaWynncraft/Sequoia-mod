package com.seqwawa.seq.model;

import com.seqwawa.seq.utils.ColorRamp;

/**
 * How a member's progression rank should be drawn: the rank itself, its shared
 * role palette, and any palette resolved specifically for that member.
 * <p>
 * The two are paired here rather than folded into {@link DiscordRank} because
 * they come from different places. The rank is a property of the role, shared by
 * everyone who holds it; the colours may be individual to the member, so two
 * people of the same rank can legitimately render in different colours.
 *
 * @param rank             the member's progression rank
 * @param roleColors       the palette shared by every holder of the progression rank
 * @param individualColors the member's own palette, empty when they have no override
 */
public record RankPresentation(DiscordRank rank, ColorRamp roleColors, ColorRamp individualColors) {

    public RankPresentation {
        if (rank == null) {
            throw new IllegalArgumentException("Rank presentation requires a rank");
        }
        roleColors = roleColors == null ? ColorRamp.empty() : roleColors;
        individualColors = individualColors == null ? ColorRamp.empty() : individualColors;
    }

    /** Compatibility constructor for presentations that only have a role palette. */
    public RankPresentation(DiscordRank rank, ColorRamp roleColors) {
        this(rank, roleColors, ColorRamp.empty());
    }

    /** Effective palette while per-user colouring is enabled. */
    public ColorRamp colors() {
        return individualColors.isEmpty() ? roleColors : individualColors;
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
