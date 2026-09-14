package com.seqwawa.seq.model;

import java.util.Locale;

/**
 * The narrowing applied to the members list: "who here can run TNA", "who has an
 * ascendancy", "who can bring auras", "who is free right now".
 * <p>
 * The raid and build are held as keys, so a filter stays valid across a catalog
 * refresh rather than pointing at an object that no longer exists.
 */
public record MemberFilter(
        String raidKey, String buildKey, boolean aurasOnly, boolean availableOnly, String search) {

    private static final MemberFilter NONE = new MemberFilter(null, null, false, false, null);

    public MemberFilter {
        raidKey = blankToNull(raidKey == null ? null : raidKey.trim().toUpperCase(Locale.ROOT));
        buildKey = blankToNull(RaidBuild.normalizeKey(buildKey));
        search = blankToNull(search == null ? null : search.trim().toLowerCase(Locale.ROOT));
    }

    public static MemberFilter none() {
        return NONE;
    }

    public boolean isActive() {
        return raidKey != null || buildKey != null || aurasOnly || availableOnly || search != null;
    }

    public boolean hasRaid() {
        return raidKey != null;
    }

    public MemberFilter withRaid(RaidType value) {
        return new MemberFilter(value == null ? null : value.key(), buildKey, aurasOnly, availableOnly, search);
    }

    public MemberFilter withBuild(String value) {
        return new MemberFilter(raidKey, value, aurasOnly, availableOnly, search);
    }

    public MemberFilter withAurasOnly(boolean value) {
        return new MemberFilter(raidKey, buildKey, value, availableOnly, search);
    }

    public MemberFilter withAvailableOnly(boolean value) {
        return new MemberFilter(raidKey, buildKey, aurasOnly, value, search);
    }

    public MemberFilter withSearch(String value) {
        return new MemberFilter(raidKey, buildKey, aurasOnly, availableOnly, value);
    }

    /** The raid this filter names, resolved against the current catalog. */
    public RaidType raid(RaidCatalog catalog) {
        return catalog == null || raidKey == null ? null : catalog.raid(raidKey);
    }

    /**
     * Whether a member passes this filter.
     * <p>
     * The raid clause has two ways to be satisfied, because the two sources of
     * truth are uneven. A member who has filled in a profile is matched on what
     * they declared they own. A member who has not is matched on Wynncraft's
     * measured clear count for that raid, which is weaker evidence but real:
     * someone with 3566 TNA clears can run TNA whether or not they ever opened the
     * setup screen. Builds and auras have no such fallback, because Wynncraft does
     * not know them, so those clauses only ever match a declared profile.
     */
    public boolean matches(
            GuildMemberPresence member, RaidTeamProfile profile, boolean busy, RaidCatalog catalog) {
        if (member == null) {
            return false;
        }
        if (availableOnly && busy) {
            return false;
        }
        if (search != null && !member.username().toLowerCase(Locale.ROOT).contains(search)) {
            return false;
        }

        RaidTeamProfile known = profile == null ? RaidTeamProfile.empty() : profile;

        if (buildKey != null && !known.hasBuild(buildKey)) {
            return false;
        }
        if (aurasOnly && !known.canBringAuras()) {
            return false;
        }
        if (raidKey != null) {
            RaidType raid = raid(catalog);
            if (raid == null) {
                // The catalog no longer knows this raid, so the clause cannot be
                // evaluated. Keeping the member visible beats hiding the whole guild.
                return true;
            }
            boolean declaredCover = known.isComplete() && known.coversRaid(raid);
            boolean measuredCover = !known.isComplete() && member.stats().completions(raid) > 0;
            return declaredCover || measuredCover;
        }
        return true;
    }

    /** Why a member matched a raid filter, so the row can say which it was. */
    public enum RaidMatchBasis {
        /** The member ticked a meta build for this raid in their profile. */
        DECLARED,
        /** No profile, but Wynncraft records clears of this raid. */
        MEASURED,
        NONE
    }

    public static RaidMatchBasis raidMatchBasis(
            GuildMemberPresence member, RaidTeamProfile profile, RaidType raid) {
        if (member == null || raid == null) {
            return RaidMatchBasis.NONE;
        }
        RaidTeamProfile known = profile == null ? RaidTeamProfile.empty() : profile;
        if (known.isComplete() && known.coversRaid(raid)) {
            return RaidMatchBasis.DECLARED;
        }
        if (member.stats().completions(raid) > 0) {
            return RaidMatchBasis.MEASURED;
        }
        return RaidMatchBasis.NONE;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
