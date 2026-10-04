package com.seqwawa.seq.model;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A party finder listing reduced to what a member row needs: raid, slots, whether
 * it takes joins, and who is in it. Built from the party finder's own listings.
 */
public record PartyFinderSpot(
        long listingId, List<String> raidShortNames, int occupiedSlots, int maxSize, boolean open, List<String> memberUuids) {

    public PartyFinderSpot {
        raidShortNames = raidShortNames == null ? List.of() : List.copyOf(raidShortNames);
        memberUuids = memberUuids == null
                ? List.of()
                : memberUuids.stream()
                        .map(RaidProfilesResponse::normalizeUuid)
                        .filter(uuid -> uuid != null)
                        .toList();
    }

    public boolean isFull() {
        return maxSize > 0 && occupiedSlots >= maxSize;
    }

    /** Whether joining could actually work. */
    public boolean isJoinable() {
        return open && !isFull();
    }

    /** {@code PF TNA 2/4}, or {@code PF TNA+1 2/4} when the listing names several raids. */
    public String label() {
        String raid = raidShortNames.isEmpty()
                ? "?"
                : raidShortNames.size() == 1
                        ? raidShortNames.get(0)
                        : raidShortNames.get(0) + "+" + (raidShortNames.size() - 1);
        return "PF " + raid + " " + occupiedSlots + "/" + maxSize;
    }

    /** Every member uuid mapped to their listing. The first listing claiming a uuid wins. */
    public static Map<String, PartyFinderSpot> indexByMember(List<PartyFinderSpot> spots) {
        if (spots == null || spots.isEmpty()) {
            return Map.of();
        }
        Map<String, PartyFinderSpot> byUuid = new HashMap<>();
        for (PartyFinderSpot spot : spots) {
            if (spot == null) {
                continue;
            }
            for (String uuid : spot.memberUuids()) {
                byUuid.putIfAbsent(uuid, spot);
            }
        }
        return Map.copyOf(byUuid);
    }
}
