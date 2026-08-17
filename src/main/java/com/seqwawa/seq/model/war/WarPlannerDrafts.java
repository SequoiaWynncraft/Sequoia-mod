package com.seqwawa.seq.model.war;

import java.util.LinkedHashSet;
import java.util.List;

public final class WarPlannerDrafts {
    private WarPlannerDrafts() {}

    public record TeamMemberDraft(String playerUuid) {
        public TeamMemberDraft {
            if (playerUuid == null || playerUuid.isBlank()) {
                throw new IllegalArgumentException("A team member must have a player UUID.");
            }
        }
    }

    public record TeamDraft(WarTeamType teamType, Long version, List<TeamMemberDraft> members) {
        public TeamDraft {
            if (teamType == null) {
                throw new IllegalArgumentException("A team type is required.");
            }
            if (version != null && version <= 0) {
                throw new IllegalArgumentException("Team version must be positive.");
            }
            members = members == null ? List.of() : List.copyOf(members);
            if (members.isEmpty() || members.size() > 5) {
                throw new IllegalArgumentException("A war team must contain 1 to 5 people.");
            }
            long distinct = members.stream().map(TeamMemberDraft::playerUuid).map(String::toLowerCase).distinct().count();
            if (distinct != members.size()) {
                throw new IllegalArgumentException("A person can only occupy one slot in a team.");
            }
        }
    }

    public record SupportSlotDraft(String code, String playerUuid) {}

    public record SupportDraft(Long version, List<SupportSlotDraft> slots) {
        public SupportDraft {
            if (version == null || version <= 0) throw new IllegalArgumentException("Support version must be positive.");
            slots = slots == null ? List.of() : List.copyOf(slots);
            var codes = slots.stream().map(SupportSlotDraft::code).distinct().count();
            var players = slots.stream().map(SupportSlotDraft::playerUuid).distinct().count();
            if (codes != slots.size() || players != slots.size()) {
                throw new IllegalArgumentException("Support slots and players must be unique.");
            }
        }
    }

    public record ZoneDraft(
            String name,
            String color,
            List<Long> assignedTeamIds,
            Long version,
            List<String> territories) {
        public ZoneDraft {
            name = requireName(name, "Zone");
            if (version != null && version <= 0) {
                throw new IllegalArgumentException("Zone version must be positive.");
            }
            color = normalizeColor(color);
            assignedTeamIds = assignedTeamIds == null ? List.of() : List.copyOf(new LinkedHashSet<>(assignedTeamIds));
            territories = territories == null
                    ? List.of()
                    : List.copyOf(new LinkedHashSet<>(territories.stream()
                            .filter(value -> value != null && !value.isBlank())
                            .toList()));
            if (territories.isEmpty()) {
                throw new IllegalArgumentException("A zone must contain at least one territory.");
            }
        }
    }

    private static String requireName(String value, String kind) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(kind + " name is required.");
        }
        if (normalized.length() > 64) {
            throw new IllegalArgumentException(kind + " name must be at most 64 characters.");
        }
        return normalized;
    }

    public static String normalizeColor(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase();
        if (!normalized.startsWith("#")) {
            normalized = "#" + normalized;
        }
        if (!normalized.matches("#[0-9A-F]{6}")) {
            throw new IllegalArgumentException("Zone color must use #RRGGBB.");
        }
        return normalized;
    }
}
