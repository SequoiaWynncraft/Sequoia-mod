package org.sequoia.seq.network.auth;

public record AuthenticatedMinecraftUser(String minecraft_uuid, String minecraft_username) {

    public String minecraftUuid() {
        return minecraft_uuid;
    }

    public String minecraftUsername() {
        return minecraft_username;
    }
}
