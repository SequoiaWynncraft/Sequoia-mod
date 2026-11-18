package com.seqwawa.seq.network;

import java.util.HashMap;
import java.util.Map;

/**
 * Every message type the client sends the backend, and what each needs before it may go:
 * which session it needs, whether it may only go from the main Wynncraft server, whether it
 * shares the 50 ms send throttle, and how often a version warning about it is repeated.
 * Types missing from here go out unchecked.
 */
enum OutboundMessageType {
    GET_CONNECTED("get_connected", Readiness.MEMBER, true, 0, null),
    BOMB_SHARE_REQUEST("bomb_share_request", Readiness.MEMBER, true, 5, "bomb share relays"),
    BOMB_SHARE_SUBMIT("bomb_share_submit", Readiness.MEMBER, true, 5, "bomb share relays"),
    TREASURY_OUT("treasury_out", Readiness.TREASURY, true, 1, null),
    GUILD_CHAT("guild_chat", Readiness.MEMBER, true, 20, "guild chat relays"),
    GUILD_MEMBERSHIP_EVENT("guild_membership_event", Readiness.MEMBER, true, 5, null),
    GUILD_RANK_EVENT("guild_rank_event", Readiness.MEMBER, true, 0, null),
    GUILD_ALLIANCE_UPDATE("guild_alliance_update", Readiness.MEMBER, true, 0, null),
    GUILD_ALLIANCE_SNAPSHOT("guild_alliance_snapshot", Readiness.MEMBER, false, 0, null),
    GUILD_RAID_ANNOUNCEMENT("guild_raid_announcement", Readiness.MEMBER, true, 5, "raid completion relays"),
    GUILD_BANK_EVENT("guild_bank_event", Readiness.MEMBER, true, 10, "guild bank relays"),
    GUILD_STORAGE_SNAPSHOT("guild_storage_snapshot", Readiness.MEMBER, false, 10, null),
    GUILD_STORAGE_REWARD("guild_storage_reward", Readiness.MEMBER, false, 10, null),
    GUILD_WAR_QUEUE("guild_war_queue", Readiness.MEMBER, true, 5, "guild war tracking"),
    GUILD_WAR_QUEUE_CANCEL("guild_war_queue_cancel", Readiness.MEMBER, true, 5, "guild war tracking"),
    GUILD_WAR_SUBMISSION("guild_war_submission", Readiness.MEMBER, true, 5, "guild war tracking"),
    WAR_STATUS("war_status", Readiness.MEMBER, false, 5, null),
    WAR_TOWER_UPDATE("war_tower_update", Readiness.MEMBER, false, 5, null),
    PARTY_CLASS_UPDATE("party_class_update", Readiness.SIGNED_IN, true, 0, null),
    PARTY_SYNC_SNAPSHOT("party_sync_snapshot", Readiness.SIGNED_IN, true, 0, null),
    PARTY_SYNC_MEMBER_REMOVED("party_sync_member_removed", Readiness.SIGNED_IN, true, 0, null),
    /** The Treasury account's session proof, which is how that socket signs in: it needs nothing first. */
    TREASURY_AUTH_RESPONSE(TreasuryAuthResponse.TYPE, Readiness.NONE, false, 0, null);

    /** The session a message needs before it may go. */
    enum Readiness {
        /** Nothing: it is part of signing in. */
        NONE,
        /** A socket signed in with the account's bearer token, which the backend hasn't refused. */
        SIGNED_IN,
        /** Signed in, and the backend hasn't said the account is outside Sequoia. */
        MEMBER,
        /** The Treasury account's own socket, signed in by its Minecraft session proof. */
        TREASURY
    }

    private static final Map<String, OutboundMessageType> BY_WIRE_NAME = new HashMap<>();

    static {
        for (OutboundMessageType type : values()) {
            BY_WIRE_NAME.put(type.wireName, type);
        }
    }

    private final String wireName;
    private final Readiness readiness;
    private final boolean throttled;
    private final int versionReminderEvery;
    private final String featureName;

    OutboundMessageType(
            String wireName, Readiness readiness, boolean throttled, int versionReminderEvery, String featureName) {
        this.wireName = wireName;
        this.readiness = readiness;
        this.throttled = throttled;
        this.versionReminderEvery = versionReminderEvery;
        this.featureName = featureName;
    }

    /** The type named {@code wireName} on the wire, or null for one this table doesn't know. */
    static OutboundMessageType of(String wireName) {
        return wireName == null ? null : BY_WIRE_NAME.get(wireName);
    }

    String wireName() {
        return wireName;
    }

    Readiness readiness() {
        return readiness;
    }

    /** Whether it needs a bearer-token session, member or not. */
    boolean needsBearerSession() {
        return readiness == Readiness.SIGNED_IN || readiness == Readiness.MEMBER;
    }

    /** Whether it is dropped once the backend says the account is not a Sequoia member. */
    boolean memberOnly() {
        return readiness == Readiness.MEMBER;
    }

    /** Whether it may only go while the player is on the main Wynncraft server. Only signing in may go from anywhere. */
    boolean mainServerOnly() {
        return readiness != Readiness.NONE;
    }

    /** Whether it shares the 50 ms throttle on privileged sends. */
    boolean throttled() {
        return throttled;
    }

    /**
     * How many version rejections of this type pass between warnings after the first, or 0
     * when only the first is shown.
     */
    int versionReminderEvery() {
        return versionReminderEvery;
    }

    /** What a repeated version warning calls this feature, or null for the generic wording. */
    String featureName() {
        return featureName;
    }
}
