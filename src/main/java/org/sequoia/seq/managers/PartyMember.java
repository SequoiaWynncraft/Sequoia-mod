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

    private static final String RESERVED_LABEL = "<RESERVED>";

    public final String name;
    /** Wynncraft class asset key (e.g. "archer"), or null if not yet resolved. */
    public final String className;
    public final boolean isLeader;
    public final boolean isReserved;
    /** Display-friendly party role (e.g. "DPS", "Healer", "Tank"). */
    public final String role;
    public final String playerUUID;

    public PartyMember(Member member, String leaderUUID) {
        this.playerUUID = member.playerUUID();
        this.name = PlayerNameCache.resolve(member.playerUUID());
        this.isLeader = member.playerUUID().equals(leaderUUID);
        this.isReserved = false;

        // Party role — display-friendly text
        this.role = formatRole(member.role());

        // Wynncraft class icon key from backend class type (fallback to local
        // resolution)
        String backendClassIcon = WynnClassCache.toAssetKey(member.classType());
        this.className = backendClassIcon != null ? backendClassIcon : WynnClassCache.resolve(member.playerUUID());
    }

    private PartyMember(Member reservedSlot) {
        this.playerUUID = null;
        this.name = RESERVED_LABEL;
        this.isLeader = false;
        this.isReserved = true;
        this.role = formatRole(reservedSlot != null ? reservedSlot.role() : null);
        this.className = null;
    }

    public static PartyMember reserved(Member reservedSlot) {
        return new PartyMember(reservedSlot);
    }

    public String displayName() {
        if (isReserved) {
            return RESERVED_LABEL;
        }
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
