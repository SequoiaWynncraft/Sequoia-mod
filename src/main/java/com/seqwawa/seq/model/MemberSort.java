package com.seqwawa.seq.model;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/**
 * The orders the members list can be put in, one per clickable column.
 * <p>
 * A member Wynncraft says nothing about sinks to the bottom in both directions:
 * flipping the order swaps best and worst, it does not float unknowns to the top.
 */
public enum MemberSort {
    NAME(false),
    WORLD(false),
    /** When the member's session started. */
    ONLINE_SINCE(true),
    GUILD_RAIDS(true),
    WARS(true);

    private final boolean descendingByDefault;

    MemberSort(boolean descendingByDefault) {
        this.descendingByDefault = descendingByDefault;
    }

    /** The direction that reads as "best first" on the first click. */
    public boolean descendingByDefault() {
        return descendingByDefault;
    }

    public static List<GuildMemberPresence> sort(
            List<GuildMemberPresence> members,
            MemberSort sort,
            boolean descending,
            RaidType raid,
            Function<GuildMemberPresence, Instant> lastLogin) {
        if (members == null || members.isEmpty()) {
            return List.of();
        }
        MemberSort key = sort == null ? NAME : sort;
        Comparator<GuildMemberPresence> comparator = key == NAME
                ? byName(descending)
                : Comparator.comparing((GuildMemberPresence member) -> !isKnown(key, member, lastLogin))
                        .thenComparing(direct(key, raid, lastLogin, descending))
                        // Name breaks every tie, so the list never reshuffles between frames.
                        .thenComparing(member -> member.username().toLowerCase(Locale.ROOT));
        return members.stream().sorted(comparator).toList();
    }

    private static Comparator<GuildMemberPresence> byName(boolean descending) {
        Comparator<GuildMemberPresence> byName =
                Comparator.comparing(member -> member.username().toLowerCase(Locale.ROOT));
        return descending ? byName.reversed() : byName;
    }

    private static Comparator<GuildMemberPresence> direct(
            MemberSort key, RaidType raid, Function<GuildMemberPresence, Instant> lastLogin, boolean descending) {
        Comparator<GuildMemberPresence> ascending = switch (key) {
            case WORLD -> Comparator.comparing(MemberSort::worldPrefix).thenComparingInt(MemberSort::worldNumber);
            case ONLINE_SINCE -> Comparator.comparing(
                    member -> loginOrEpoch(member, lastLogin), Comparator.naturalOrder());
            case GUILD_RAIDS -> Comparator.comparingInt(member -> raid == null
                    ? member.stats().totalRaidCompletions()
                    : member.stats().completions(raid));
            case WARS -> Comparator.comparingInt(member -> member.stats().wars());
            case NAME -> Comparator.comparing(member -> member.username().toLowerCase(Locale.ROOT));
        };
        return descending ? ascending.reversed() : ascending;
    }

    private static boolean isKnown(
            MemberSort key, GuildMemberPresence member, Function<GuildMemberPresence, Instant> lastLogin) {
        return switch (key) {
            case WORLD -> member.hasWorld();
            case ONLINE_SINCE -> lastLogin != null && lastLogin.apply(member) != null;
            // Zero is a real answer here, not a missing one.
            case GUILD_RAIDS, WARS, NAME -> true;
        };
    }

    /** The letters of a world name, so NA6 and NA12 group together. */
    private static String worldPrefix(GuildMemberPresence member) {
        String world = member.world();
        if (world == null) {
            return "";
        }
        int index = 0;
        while (index < world.length() && !Character.isDigit(world.charAt(index))) {
            index++;
        }
        return world.substring(0, index);
    }

    /** The digits of a world name, so NA6 comes before NA12. */
    private static int worldNumber(GuildMemberPresence member) {
        String world = member.world();
        if (world == null) {
            return Integer.MAX_VALUE;
        }
        StringBuilder digits = new StringBuilder();
        for (int index = 0; index < world.length(); index++) {
            if (Character.isDigit(world.charAt(index))) {
                digits.append(world.charAt(index));
            }
        }
        if (digits.isEmpty()) {
            return Integer.MAX_VALUE;
        }
        try {
            return Integer.parseInt(digits.toString());
        } catch (NumberFormatException ignored) {
            return Integer.MAX_VALUE;
        }
    }

    private static Instant loginOrEpoch(
            GuildMemberPresence member, Function<GuildMemberPresence, Instant> lastLogin) {
        Instant login = lastLogin == null ? null : lastLogin.apply(member);
        return login == null ? Instant.EPOCH : login;
    }
}
