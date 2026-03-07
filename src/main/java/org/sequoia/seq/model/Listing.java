package org.sequoia.seq.model;

import java.time.Instant;
import java.util.List;

public record Listing(
        long id,
        List<Activity> activities,
        Activity activity,
        String leaderUUID,
        PartyMode mode,
        boolean strict,
        PartyRegion region,
        PartyStatus status,
        String note,
        List<Member> members,
        List<Member> reservedSlots,
        Instant createdAt) {
    /**
     * Whether this listing's expand state is toggled in the UI (client-only, not
     * from backend).
     */
    private static final java.util.Map<Long, Boolean> expandedState = new java.util.concurrent.ConcurrentHashMap<>();

    public boolean isExpanded() {
        return expandedState.getOrDefault(id, false);
    }

    public void setExpanded(boolean expanded) {
        expandedState.put(id, expanded);
    }

    public static void clearExpandedState() {
        expandedState.clear();
    }

    public List<Activity> resolvedActivities() {
        if (activities != null && !activities.isEmpty()) {
            return activities;
        }
        return activity != null ? List.of(activity) : List.of();
    }

    public Activity primaryActivity() {
        List<Activity> resolved = resolvedActivities();
        return resolved.isEmpty() ? null : resolved.get(0);
    }

    public int maxPartySize() {
        return resolvedActivities().stream()
                .mapToInt(Activity::maxPartySize)
                .max()
                .orElse(Math.max(1, occupiedSlotCount()));
    }

    public int occupiedSlotCount() {
        return memberCount() + reservedSlotCount();
    }

    public int memberCount() {
        return members != null ? members.size() : 0;
    }

    public int reservedSlotCount() {
        return reservedSlots != null ? reservedSlots.size() : 0;
    }

    public Member getLeader() {
        return members.stream()
                .filter(m -> m.playerUUID().equals(leaderUUID))
                .findFirst()
                .orElse(members.isEmpty() ? null : members.get(0));
    }
}
