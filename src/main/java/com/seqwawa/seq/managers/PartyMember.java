package com.seqwawa.seq.managers;

import com.seqwawa.seq.model.Member;
import com.seqwawa.seq.model.PartyRole;
import com.seqwawa.seq.model.ReservedSlot;
import com.seqwawa.seq.ui.PartyFinderScreen;
import com.seqwawa.seq.utils.PlayerNameCache;
import com.seqwawa.seq.utils.WynnClassCache;

/**
 * Adapter class wrapping {@link Member} with public fields
 * matching what {@link PartyFinderScreen} expects.
 *
 * <p>
 * {@code role} is the party role (DPS, Healer, Tank, Other) — what function
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
    public final boolean isObserved;
    /** Display-friendly party role (e.g. "DPS", "Healer", "Tank", "Other"). */
    public final String role;
    public final String playerUUID;

    public PartyMember(Member member, String leaderUUID) {
        this.playerUUID = member.playerUUID();
        this.name = PlayerNameCache.resolve(member.playerUUID());
        this.isLeader = member.playerUUID().equals(leaderUUID);
        this.isReserved = false;
        this.isObserved = false;

        // Party role — display-friendly text
        this.role = formatRole(member.role());

        // Wynncraft class icon key from backend class type (fallback to local
        // resolution)
        String backendClassIcon = WynnClassCache.toAssetKey(member.classType());
        this.className = backendClassIcon != null ? backendClassIcon : WynnClassCache.resolve(member.playerUUID());
    }

    private PartyMember(ReservedSlot reservedSlot) {
        this.playerUUID = reservedSlot != null ? reservedSlot.playerUUID() : null;
        String observedUsername = reservedSlot != null ? reservedSlot.observedUsername() : null;
        this.name = observedUsername != null && !observedUsername.isBlank()
                ? observedUsername
                : (playerUUID != null && !playerUUID.isBlank() ? PlayerNameCache.resolve(playerUUID) : RESERVED_LABEL);
        this.isLeader = false;
        this.isObserved = reservedSlot != null && reservedSlot.isObservedWynnMember();
        this.isReserved = !isObserved;
        this.role = formatRole(reservedSlot != null ? reservedSlot.role() : null);
        this.className = null;
    }

    public static PartyMember reserved(ReservedSlot reservedSlot) {
        return new PartyMember(reservedSlot);
    }

    public String displayName() {
        if (isReserved || isObserved) {
            return name;
        }
        return PlayerNameCache.resolve(playerUUID);
    }

    /**
     * Converts a {@link PartyRole} enum value into a display-friendly string.
     * <ul>
     * <li>DPS → "DPS"</li>
     * <li>HEALER → "Healer"</li>
     * <li>TANK → "Tank"</li>
     * <li>OTHER → "Other"</li>
     * </ul>
     */
    private static String formatRole(PartyRole partyRole) {
        if (partyRole == null)
            return "DPS";
        return switch (partyRole) {
            case DPS -> "DPS";
            case HEALER -> "Healer";
            case TANK -> "Tank";
            case OTHER -> "Other";
        };
    }
}
