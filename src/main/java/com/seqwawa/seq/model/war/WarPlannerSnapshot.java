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
        List<Zone> zones,
        List<String> territories) {

    public static final int SUPPORTED_SCHEMA_VERSION = 1;

    public WarPlannerSnapshot {
        roster = roster == null ? List.of() : List.copyOf(roster);
        teams = teams == null ? List.of() : List.copyOf(teams);
        zones = zones == null ? List.of() : List.copyOf(zones);
        territories = territories == null ? List.of() : List.copyOf(territories);
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

    public record Self(
            @SerializedName("player_uuid") String playerUuid,
            @SerializedName("can_manage") boolean canManage) {}

    public record RosterMember(
            @SerializedName("player_uuid") String playerUuid,
            @SerializedName("minecraft_username") String minecraftUsername,
            @SerializedName("discord_id") String discordId,
            @SerializedName("discord_username") String discordUsername,
            @SerializedName("discord_role_keys") List<String> discordRoleKeys,
            boolean available,
            @SerializedName("available_until") Instant availableUntil,
            @SerializedName("team_id") Long teamId,
            @SerializedName("team_role") WarTeamRole teamRole) {
        public RosterMember {
            discordRoleKeys = discordRoleKeys == null ? List.of() : List.copyOf(discordRoleKeys);
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
            WarTeamRole role,
            int position) {}

    public record Zone(
            long id,
            String name,
            String color,
            @SerializedName("assigned_team_id") Long assignedTeamId,
            Long version,
            List<String> territories) {
        public Zone {
            territories = territories == null ? List.of() : List.copyOf(territories);
        }
    }
}
