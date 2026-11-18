package com.seqwawa.seq.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seqwawa.seq.network.OutboundMessageType.Readiness;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class OutboundMessageTypeTest {

    private static Set<String> typesWhere(Predicate<OutboundMessageType> test) {
        return Arrays.stream(OutboundMessageType.values())
                .filter(test)
                .map(OutboundMessageType::wireName)
                .collect(Collectors.toSet());
    }

    @Test
    void everyTypeIsFoundByItsWireName() {
        for (OutboundMessageType type : OutboundMessageType.values()) {
            assertSame(type, OutboundMessageType.of(type.wireName()));
        }
        assertNull(OutboundMessageType.of("discord_chat"), "an inbound type is not one the client sends");
        assertNull(OutboundMessageType.of(null));
    }

    @Test
    void eachTypeNeedsTheSessionItAlwaysNeeded() {
        Map<String, Readiness> readiness = new TreeMap<>();
        for (OutboundMessageType type : OutboundMessageType.values()) {
            readiness.put(type.wireName(), type.readiness());
        }

        Map<String, Readiness> expected = new TreeMap<>(Map.ofEntries(
                Map.entry("get_connected", Readiness.MEMBER),
                Map.entry("bomb_share_request", Readiness.MEMBER),
                Map.entry("bomb_share_submit", Readiness.MEMBER),
                Map.entry("treasury_out", Readiness.TREASURY),
                Map.entry("guild_chat", Readiness.MEMBER),
                Map.entry("guild_membership_event", Readiness.MEMBER),
                Map.entry("guild_rank_event", Readiness.MEMBER),
                Map.entry("guild_alliance_update", Readiness.MEMBER),
                Map.entry("guild_alliance_snapshot", Readiness.MEMBER),
                Map.entry("guild_raid_announcement", Readiness.MEMBER),
                Map.entry("guild_bank_event", Readiness.MEMBER),
                Map.entry("guild_storage_snapshot", Readiness.MEMBER),
                Map.entry("guild_storage_reward", Readiness.MEMBER),
                Map.entry("guild_war_queue", Readiness.MEMBER),
                Map.entry("guild_war_queue_cancel", Readiness.MEMBER),
                Map.entry("guild_war_submission", Readiness.MEMBER),
                Map.entry("war_status", Readiness.MEMBER),
                Map.entry("war_tower_update", Readiness.MEMBER),
                Map.entry("party_class_update", Readiness.SIGNED_IN),
                Map.entry("party_sync_snapshot", Readiness.SIGNED_IN),
                Map.entry("party_sync_member_removed", Readiness.SIGNED_IN),
                Map.entry("treasury_auth_response", Readiness.NONE)));
        assertEquals(expected, readiness);
    }

    @Test
    void partyFinderTrafficNeedsASessionButNotSequoiaMembership() {
        for (String type : Set.of("party_class_update", "party_sync_snapshot", "party_sync_member_removed")) {
            assertTrue(OutboundMessageType.of(type).needsBearerSession(), type);
            assertFalse(OutboundMessageType.of(type).memberOnly(), type);
        }
        assertTrue(OutboundMessageType.of("guild_chat").memberOnly());
        assertTrue(OutboundMessageType.of("get_connected").memberOnly());
        assertTrue(OutboundMessageType.of("war_tower_update").memberOnly());
    }

    @Test
    void treasuryOutUsesTheSessionProofInsteadOfABearerToken() {
        OutboundMessageType treasuryOut = OutboundMessageType.of("treasury_out");

        assertFalse(treasuryOut.needsBearerSession());
        assertFalse(treasuryOut.memberOnly());
        assertTrue(treasuryOut.mainServerOnly());
        assertTrue(treasuryOut.throttled());
    }

    @Test
    void onlyTheTreasurySessionProofMayGoFromOutsideTheMainServer() {
        assertEquals(Set.of("treasury_auth_response"), typesWhere(type -> !type.mainServerOnly()));
    }

    @Test
    void snapshotsAndLiveWarTelemetrySkipThePrivilegedSendThrottle() {
        assertEquals(
                Set.of("guild_alliance_snapshot", "guild_storage_snapshot", "guild_storage_reward",
                        "war_status", "war_tower_update", "treasury_auth_response"),
                typesWhere(type -> !type.throttled()));
    }

    @Test
    void versionWarningsRepeatAtEachFeaturesOwnPace() {
        Map<String, Integer> reminders = new TreeMap<>();
        for (OutboundMessageType type : OutboundMessageType.values()) {
            if (type.versionReminderEvery() > 0) {
                reminders.put(type.wireName(), type.versionReminderEvery());
            }
        }

        assertEquals(new TreeMap<>(Map.ofEntries(
                Map.entry("bomb_share_request", 5),
                Map.entry("bomb_share_submit", 5),
                Map.entry("treasury_out", 1),
                Map.entry("guild_chat", 20),
                Map.entry("guild_membership_event", 5),
                Map.entry("guild_raid_announcement", 5),
                Map.entry("guild_bank_event", 10),
                Map.entry("guild_storage_snapshot", 10),
                Map.entry("guild_storage_reward", 10),
                Map.entry("guild_war_queue", 5),
                Map.entry("guild_war_queue_cancel", 5),
                Map.entry("guild_war_submission", 5),
                Map.entry("war_status", 5),
                Map.entry("war_tower_update", 5))), reminders);
    }

    @Test
    void repeatedVersionWarningsNameTheFeatureWhereOneIsKnown() {
        Map<String, String> names = new TreeMap<>();
        for (OutboundMessageType type : OutboundMessageType.values()) {
            if (type.featureName() != null) {
                names.put(type.wireName(), type.featureName());
            }
        }

        assertEquals(new TreeMap<>(Map.of(
                "bomb_share_request", "bomb share relays",
                "bomb_share_submit", "bomb share relays",
                "guild_chat", "guild chat relays",
                "guild_raid_announcement", "raid completion relays",
                "guild_bank_event", "guild bank relays",
                "guild_war_queue", "guild war tracking",
                "guild_war_queue_cancel", "guild war tracking",
                "guild_war_submission", "guild war tracking")), names);
    }
}
