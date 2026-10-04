package com.seqwawa.seq.model;

import java.util.Locale;

/**
 * The narrowing applied to the members list: raid, auras, availability, name.
 * <p>
 * The raid is held as a key, so a filter stays valid across a catalog refresh.
 */
public record MemberFilter(String raidKey, boolean aurasOnly, boolean availableOnly, String search) {

    private static final MemberFilter NONE = new MemberFilter(null, false, false, null);

    public MemberFilter {
        raidKey = blankToNull(raidKey == null ? null : raidKey.trim().toUpperCase(Locale.ROOT));
        search = blankToNull(search == null ? null : search.trim().toLowerCase(Locale.ROOT));
    }

    public static MemberFilter none() {
        return NONE;
    }

    public boolean isActive() {
        return raidKey != null || aurasOnly || availableOnly || search != null;
    }

    public boolean hasRaid() {
        return raidKey != null;
    }

    public MemberFilter withRaid(RaidType value) {
        return new MemberFilter(value == null ? null : value.key(), aurasOnly, availableOnly, search);
    }

    public MemberFilter withAurasOnly(boolean value) {
        return new MemberFilter(raidKey, value, availableOnly, search);
    }

    public MemberFilter withAvailableOnly(boolean value) {
        return new MemberFilter(raidKey, aurasOnly, value, search);
    }

    public MemberFilter withSearch(String value) {
        return new MemberFilter(raidKey, aurasOnly, availableOnly, value);
    }

    /** The raid this filter names, resolved against the current catalog. */
    public RaidType raid(RaidCatalog catalog) {
        return catalog == null || raidKey == null ? null : catalog.raid(raidKey);
    }

    /**
     * Whether a member passes this filter.
     * <p>
     * The raid clause is satisfied two ways: a member with a profile is matched on
     * the builds they declared, one without is matched on their clear count for that
     * raid. Auras have no such fallback, since Wynncraft does not know them.
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

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
