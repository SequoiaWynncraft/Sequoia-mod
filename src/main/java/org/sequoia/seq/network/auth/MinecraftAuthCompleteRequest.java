package org.sequoia.seq.network.auth;

public record MinecraftAuthCompleteRequest(String challenge_id, String username) {

    public String challengeId() {
        return challenge_id;
    }
}
