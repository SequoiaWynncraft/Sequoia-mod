package org.sequoia.seq.managers;

import org.sequoia.seq.model.Member;
import org.sequoia.seq.model.PartyRole;
import org.sequoia.seq.utils.PlayerNameCache;
import org.sequoia.seq.utils.WynnClassCache;

/**
 * Adapter class wrapping {@link Member} with public fields
 * matching what {@link org.sequoia.seq.ui.PartyFinderScreen} expects.
 *
 * <p>
 * {@code role} is the party role (DPS, Healer, Tank) — what function
 * the player serves in the group.
 *
 * <p>
 * {@code className} is the Wynncraft character class asset key
 * (e.g. "archer", "warrior", "mage", "assassin", "shaman") resolved
 * via Wynntils. It is {@code null} when the class has not yet been
 * resolved (async lookup in progress or player offline).
 */
public class PartyMember {

    public final String name;
    /** Wynncraft class asset key (e.g. "archer"), or null if not yet resolved. */
    public final String className;
    public final boolean isLeader;
    /** Display-friendly party role (e.g. "DPS", "Healer", "Tank"). */
    public final String role;
    public final String playerUUID;

    public PartyMember(Member member, String leaderUUID) {
        this.playerUUID = member.playerUUID();
        this.name = PlayerNameCache.resolve(member.playerUUID());
        this.isLeader = member.playerUUID().equals(leaderUUID);

        // Party role — display-friendly text
        this.role = formatRole(member.role());

        // Wynncraft class — resolved from Wynntils for the local player
        this.className = WynnClassCache.resolve(member.playerUUID());
    }

    public String displayName() {
        return PlayerNameCache.resolve(playerUUID);
    }

    /**
     * Converts a {@link PartyRole} enum value into a display-friendly string.
     * <ul>
     * <li>DPS → "DPS"</li>
     * <li>HEALER → "Healer"</li>
     * <li>TANK → "Tank"</li>
     * </ul>
     */
    private static String formatRole(PartyRole partyRole) {
        if (partyRole == null)
            return "DPS";
        return switch (partyRole) {
            case DPS -> "DPS";
            case HEALER -> "Healer";
            case TANK -> "Tank";
        };
    }
}
