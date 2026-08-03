package com.seqwawa.seq.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public record RankProfilesResponse(
        @SerializedName("schema_version") int schemaVersion,
        Catalog catalog,
        List<Profile> profiles) {

    public record Catalog(
            List<RoleDefinition> roles,
            List<AwardDefinition> awards,
            List<AssetDefinition> assets) {}

    public record RoleDefinition(
            String key,
            String label,
            String category,
            String tier,
            @SerializedName("asset_key") String assetKey,
            int position,
            RoleColors colors) {

        public RoleDefinition(String key, String category, String tier, String assetKey) {
            this(key, null, category, tier, assetKey, 0, null);
        }
    }

    /**
     * Display colours as {@code #RRGGBB}. A role with only {@code primary} is solid;
     * {@code secondary} and {@code tertiary} add the stops of a gradient role.
     */
    public record RoleColors(String primary, String secondary, String tertiary) {}

    public record AwardDefinition(
            String key,
            String category,
            String series,
            String tier,
            @SerializedName("asset_key") String assetKey) {}

    public record AssetDefinition(
            String key,
            String url,
            @SerializedName("content_type") String contentType,
            String sha256) {}

    public record Profile(
            DiscordIdentity discord,
            MinecraftIdentity minecraft,
            @SerializedName("role_keys") List<String> roleKeys,
            @SerializedName("award_keys") List<String> awardKeys,
            Summary summary,
            RoleColors colors) {

        public Profile(MinecraftIdentity minecraft, List<String> roleKeys, List<String> awardKeys) {
            this(null, minecraft, roleKeys, awardKeys, null, null);
        }

        public Profile(
                DiscordIdentity discord,
                MinecraftIdentity minecraft,
                List<String> roleKeys,
                List<String> awardKeys,
                Summary summary) {
            this(discord, minecraft, roleKeys, awardKeys, summary, null);
        }
    }

    public record MinecraftIdentity(String uuid, String username) {}

    /** Discord identity of a member who linked their game account. */
    public record DiscordIdentity(
            String id,
            String username,
            @SerializedName("display_name") String displayName,
            @SerializedName("avatar_url") String avatarUrl) {}

    /** Backend-resolved "best" role per category, so clients do not re-implement precedence. */
    public record Summary(
            @SerializedName("progression_rank") String progressionRank,
            @SerializedName("in_game_rank") String inGameRank,
            String leadership,
            String insignia,
            @SerializedName("war_honour") String warHonour) {}
}
