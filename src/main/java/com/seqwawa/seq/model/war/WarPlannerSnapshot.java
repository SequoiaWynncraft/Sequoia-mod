package com.seqwawa.seq.model.war;

import com.google.gson.annotations.SerializedName;
import java.time.Instant;
import java.util.List;

/** Immutable, server-authoritative view of the Seq war planner. */
public record WarPlannerSnapshot(
        @SerializedName("schema_version") int schemaVersion,
        @SerializedName("server_time") Instant serverTime,
        Self self,
        @SerializedName("discord_roles_available") boolean discordRolesAvailable,
        List<RosterMember> roster,
        List<Team> teams,
        SupportBoard support,
        List<Zone> zones,
        List<String> territories,
        @SerializedName("territory_details") List<TerritoryDetails> territoryDetails) {

    public static final int SUPPORTED_SCHEMA_VERSION = 3;

    public WarPlannerSnapshot {
        roster = roster == null ? List.of() : List.copyOf(roster);
        teams = teams == null ? List.of() : List.copyOf(teams);
        support = support == null ? new SupportBoard(1L, List.of()) : support;
        zones = zones == null ? List.of() : List.copyOf(zones);
        territories = territories == null ? List.of() : List.copyOf(territories);
        territoryDetails = territoryDetails == null ? List.of() : List.copyOf(territoryDetails);
    }

    public boolean isSupported() {
        return schemaVersion == SUPPORTED_SCHEMA_VERSION;
    }

    public RosterMember caller() {
        if (self == null || self.playerUuid() == null) {
            return null;
        }
        return roster.stream()
                .filter(member -> self.playerUuid().equalsIgnoreCase(member.playerUuid()))
                .findFirst()
                .orElse(null);
    }

    public Team team(Long id) {
        if (id == null) {
            return null;
        }
        return teams.stream().filter(team -> id.longValue() == team.id()).findFirst().orElse(null);
    }

    /** Current online roster used by the overview. Missing legacy status fails closed as offline. */
    public List<RosterMember> onlineRoster() {
        return roster.stream().filter(RosterMember::online).toList();
    }

    /** Online roster plus the authenticated caller, whose cached Wynn presence may lag behind the open client. */
    public List<RosterMember> visibleRoster() {
        String callerUuid = self == null ? null : self.playerUuid();
        return roster.stream()
                .filter(member -> member.online()
                        || callerUuid != null && callerUuid.equalsIgnoreCase(member.playerUuid()))
                .toList();
    }

    /** Managers may move any online member. Current members stay visible while editing even when offline. */
    public List<RosterMember> teamCandidates(Long editingTeamId) {
        return roster.stream()
                .filter(member -> member.online()
                        || (editingTeamId != null
                                && member.teamId() != null
                                && member.teamId().longValue() == editingTeamId.longValue()))
                .toList();
    }

    public record Self(
            @SerializedName("player_uuid") String playerUuid,
            @SerializedName("can_manage") boolean canManage) {}

    public record RosterMember(
            @SerializedName("player_uuid") String playerUuid,
            @SerializedName("minecraft_username") String minecraftUsername,
            @SerializedName("discord_id") String discordId,
            @SerializedName("discord_username") String discordUsername,
            @SerializedName("composition_roles") List<WarCompositionRole> compositionRoles,
            boolean online,
            boolean available,
            @SerializedName("available_until") Instant availableUntil,
            @SerializedName("team_id") Long teamId) {
        public RosterMember {
            compositionRoles = WarCompositionRole.ordered(compositionRoles);
        }

        public String displayName() {
            if (minecraftUsername != null && !minecraftUsername.isBlank()) {
                return minecraftUsername;
            }
            if (discordUsername != null && !discordUsername.isBlank()) {
                return discordUsername;
            }
            return playerUuid == null ? "Unknown member" : playerUuid;
        }
    }

    public record Team(long id, String name, Long version, List<TeamMember> members) {
        public Team {
            members = members == null ? List.of() : List.copyOf(members);
        }
    }

    public record TeamMember(
            @SerializedName("player_uuid") String playerUuid,
            @SerializedName("minecraft_username") String minecraftUsername,
            int position) {}

    public record SupportBoard(Long version, List<SupportSlot> slots) {
        public SupportBoard {
            slots = slots == null ? List.of() : List.copyOf(slots);
        }
    }

    public record SupportSlot(
            String code,
            @SerializedName("player_uuid") String playerUuid,
            @SerializedName("minecraft_username") String minecraftUsername) {}

    public record Zone(
            long id,
            String name,
            String color,
            @SerializedName("assigned_team_ids") List<Long> assignedTeamIds,
            Long version,
            List<String> territories) {
        public Zone {
            assignedTeamIds = assignedTeamIds == null ? List.of() : List.copyOf(assignedTeamIds);
            territories = territories == null ? List.of() : List.copyOf(territories);
        }
    }

    public record TerritoryDetails(String name, List<String> connections, List<String> resources) {
        public TerritoryDetails {
            connections = connections == null ? List.of() : List.copyOf(connections);
            resources = resources == null ? List.of() : List.copyOf(resources);
        }
    }
}
