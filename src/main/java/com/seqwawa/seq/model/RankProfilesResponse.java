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
            String category,
            String tier,
            @SerializedName("asset_key") String assetKey) {}

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
            MinecraftIdentity minecraft,
            @SerializedName("role_keys") List<String> roleKeys,
            @SerializedName("award_keys") List<String> awardKeys) {}

    public record MinecraftIdentity(String uuid, String username) {}
}
