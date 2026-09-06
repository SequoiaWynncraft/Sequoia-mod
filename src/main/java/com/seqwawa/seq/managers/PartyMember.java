package com.seqwawa.seq.managers;

import com.seqwawa.seq.model.Member;
import com.seqwawa.seq.model.PartyRole;
import com.seqwawa.seq.model.ReservedSlot;
import com.seqwawa.seq.ui.PartyFinderScreen;
import com.seqwawa.seq.utils.PlayerNameCache;
import com.seqwawa.seq.utils.WynnClassCache;
import java.util.function.Function;

/**
 * Adapter class wrapping {@link Member} with public fields
 * matching what {@link PartyFinderScreen} expects.
 *
 * <p>
 * {@code role} is the party role (DPS, Healer, Tank, Other) — what function
 * the player serves in the group.
 *
 * <p>
 * {@link #classIconKey()} exposes the active local class when known, otherwise
 * the class supplied by the listing. Unknown classes have no icon.
 */
public class PartyMember {

    private static final String RESERVED_LABEL = "<RESERVED>";

    public final String name;
    private final String backendClassIcon;
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

        this.backendClassIcon = WynnClassCache.toAssetKey(member.classType());
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
        this.backendClassIcon = null;
    }

    public static PartyMember reserved(ReservedSlot reservedSlot) {
        return new PartyMember(reservedSlot);
    }

    /** Resolve at display time so cached cards pick up newly detected or changed classes. */
    public String classIconKey() {
        return classIconKey(WynnClassCache::resolve);
    }

    String classIconKey(Function<String, String> localClassResolver) {
        if (isReserved) return null;
        String localClass = playerUUID == null ? null : localClassResolver.apply(playerUUID);
        return localClass != null ? localClass : backendClassIcon;
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
