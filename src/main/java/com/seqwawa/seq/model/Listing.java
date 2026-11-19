package com.seqwawa.seq.model;

import com.google.gson.annotations.SerializedName;
import com.seqwawa.seq.utils.PlayerNameCache;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public record Listing(
        long id,
        List<Activity> activities,
        Activity activity,
        String leaderUUID,
        PartyRegion region,
        @SerializedName(
                        value = "world",
                        alternate = {"leaderWorld", "leaderWorldName", "currentWorld", "currentWorldName"})
                String world,
        PartyStatus status,
        PartyCloseReason closeReason,
        String note,
        List<Member> members,
        List<ReservedSlot> reservedSlots,
        Instant createdAt,
        @SerializedName(
                        value = "joinPolicy",
                        alternate = {"join_policy"})
                PartyJoinPolicy joinPolicy) {
    public Listing(
            long id,
            List<Activity> activities,
            Activity activity,
            String leaderUUID,
            PartyRegion region,
            String world,
            PartyStatus status,
            PartyCloseReason closeReason,
            String note,
            List<Member> members,
            List<ReservedSlot> reservedSlots,
            Instant createdAt) {
        this(
                id,
                activities,
                activity,
                leaderUUID,
                region,
                world,
                status,
                closeReason,
                note,
                members,
                reservedSlots,
                createdAt,
                PartyJoinPolicy.OPEN);
    }

    public Listing(
            long id,
            List<Activity> activities,
            Activity activity,
            String leaderUUID,
            PartyRegion region,
            PartyStatus status,
            PartyCloseReason closeReason,
            String note,
            List<Member> members,
            List<ReservedSlot> reservedSlots,
            Instant createdAt) {
        this(
                id,
                activities,
                activity,
                leaderUUID,
                region,
                null,
                status,
                closeReason,
                note,
                members,
                reservedSlots,
                createdAt,
                PartyJoinPolicy.OPEN);
    }

    public Listing(
            long id,
            List<Activity> activities,
            Activity activity,
            String leaderUUID,
            PartyRegion region,
            PartyStatus status,
            String note,
            List<Member> members,
            List<ReservedSlot> reservedSlots,
            Instant createdAt) {
        this(
                id,
                activities,
                activity,
                leaderUUID,
                region,
                null,
                status,
                null,
                note,
                members,
                reservedSlots,
                createdAt,
                PartyJoinPolicy.OPEN);
    }

    public List<Activity> resolvedActivities() {
        if (activities != null && !activities.isEmpty()) {
            return activities;
        }
        return activity != null ? List.of(activity) : List.of();
    }

    public int maxPartySize() {
        return resolvedActivities().stream()
                .mapToInt(Activity::maxPartySize)
                .max()
                .orElse(Math.max(1, occupiedSlotCount()));
    }

    public int occupiedSlotCount() {
        return memberCount() + (reservedSlots != null ? reservedSlots.size() : 0);
    }

    public int memberCount() {
        return members != null ? members.size() : 0;
    }

    /** The slots the leader reserved, observed Wynn members aside; an older backend listed them as "reserved" members. */
    public int reservedSlotCount() {
        if (reservedSlots != null) {
            return (int) reservedSlots.stream()
                    .filter(slot -> slot != null && !slot.isObservedWynnMember())
                    .count();
        }
        return members == null ? 0 : (int) members.stream()
                .filter(member -> member != null && member.playerUUID() != null
                        && "reserved".equals(member.playerUUID().trim().toLowerCase(Locale.ROOT)))
                .count();
    }

    public PartyJoinPolicy resolvedJoinPolicy() {
        return joinPolicy != null ? joinPolicy : PartyJoinPolicy.OPEN;
    }

    public boolean isLedBy(String playerUuid) {
        return samePlayer(playerUuid, leaderUUID);
    }

    /** Whether {@code playerUuid} is one of the members, not only a reserved slot. */
    public boolean hasMember(String playerUuid) {
        return member(playerUuid) != null;
    }

    /** Whether {@code playerUuid} leads or is a member. */
    public boolean includes(String playerUuid) {
        return isLedBy(playerUuid) || hasMember(playerUuid);
    }

    public Member member(String playerUuid) {
        if (members == null || playerKey(playerUuid) == null) {
            return null;
        }
        return members.stream()
                .filter(member -> member != null && samePlayer(playerUuid, member.playerUUID()))
                .findFirst()
                .orElse(null);
    }

    public boolean reservesSlotFor(String playerUuid) {
        return reservedSlots != null && playerKey(playerUuid) != null && reservedSlots.stream()
                .anyMatch(slot -> slot != null && samePlayer(playerUuid, slot.playerUUID()));
    }

    /** Each member's {@link #playerKey}, in order. */
    public Set<String> memberKeys() {
        Set<String> keys = new LinkedHashSet<>();
        if (members != null) {
            for (Member member : members) {
                String key = member == null ? null : playerKey(member.playerUUID());
                if (key != null) {
                    keys.add(key);
                }
            }
        }
        return keys;
    }

    public boolean isFull() {
        int maxPartySize = maxPartySize();
        return status == PartyStatus.FULL || (maxPartySize > 0 && occupiedSlotCount() >= maxPartySize);
    }

    /** Closed by the backend because it filled up; it reopens by itself when a slot frees. */
    public boolean isAutoCapacityClosed() {
        return status == PartyStatus.CLOSED && closeReason == PartyCloseReason.AUTO_CAPACITY;
    }

    /** Whether two player ids name one player, with or without dashes, in any case. */
    public static boolean samePlayer(String left, String right) {
        String leftKey = playerKey(left);
        return leftKey != null && leftKey.equals(playerKey(right));
    }

    /** {@code value} as one spelling of a player id: a UUID's 32 hex digits, else the trimmed text in lower case. */
    public static String playerKey(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String formatted = PlayerNameCache.formatUUID(value);
        return formatted != null
                ? formatted.replace("-", "").toLowerCase(Locale.ROOT)
                : value.trim().toLowerCase(Locale.ROOT);
    }
}
