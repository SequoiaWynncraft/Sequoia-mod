package org.sequoia.seq.network.auth;

import com.google.gson.annotations.SerializedName;

public record WynnOAuthStatusResponse(
        String status,
        String token,
        @SerializedName("minecraft_uuid") String minecraftUuid,
        @SerializedName("minecraft_username") String minecraftUsername,
        @SerializedName("error_code") String errorCode,
        @SerializedName("error_message") String errorMessage) {}
