package com.seqwawa.seq.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.seqwawa.seq.model.BombShareType;
import com.seqwawa.seq.model.ChatItemPreview;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class OutboundPayloadFactoryTest {
    @Test
    void guildChatMatchesNestedProtocolFixture() {
        ChatItemPreview preview = new ChatItemPreview(
                "Weathered",
                "Mythic Dagger",
                0xFF0000,
                List.of("Combat Lv. Min: 96"),
                List.of("Very Fast Attack Speed"),
                List.of(new ChatItemPreview.StatRoll("rawStrength", "strength", "Strength", 12, 80.0f)),
                new ChatItemPreview.ShinyStat("mobsKilled", "Mobs Killed", 12345, 2));

        assertEquals(
                protocolFixture("guild-chat.json"),
                OutboundPayloadFactory.guildChat(
                        "SequoiaUser",
                        "Seq User",
                        "Selling this item",
                        "https://example.test/avatar.png",
                        List.of(preview)));
    }

    @Test
    void raidAnnouncementMatchesProtocolFixture() {
        assertEquals(
                protocolFixture("raid-announcement.json"),
                OutboundPayloadFactory.raidAnnouncement(
                        List.of("SequoiaUser", "CinfrasCitizen", "DetlasMerchant", "RagniGuard"),
                        "TNA",
                        3,
                        4096,
                        1250000.5,
                        18));
    }

    @Test
    void guildMembershipEventUsesStableWireFields() {
        var payload = OutboundPayloadFactory.guildMembershipEvent("invited", "GaztheCat", "NewMember");

        assertEquals("invited", payload.get("action").getAsString());
        assertEquals("GaztheCat", payload.get("actor").getAsString());
        assertEquals("NewMember", payload.get("target").getAsString());
    }

    @Test
    void partySyncSnapshotMatchesProtocolFixtureAndRetainsFiltering() {
        assertEquals(
                protocolFixture("party-sync-snapshot.json"),
                OutboundPayloadFactory.partySyncSnapshot(
                        true,
                        "SequoiaUser",
                        List.of("SequoiaUser", "CinfrasCitizen", "DetlasMerchant", "RagniGuard")));

        var withoutLeader = OutboundPayloadFactory.partySyncSnapshot(false, " ", List.of("SeqUser", "", "  "));
        assertFalse(withoutLeader.has("leader_username"));
        assertEquals(1, withoutLeader.getAsJsonArray("member_usernames").size());
    }

    @Test
    void bombShareFactoriesRetainWireEnumNamesAndWorldTrimming() {
        var request = OutboundPayloadFactory.bombShareRequest(
                "loot-and-combat", java.util.Arrays.asList(BombShareType.LOOT, null, BombShareType.COMBAT_XP));
        var submit = OutboundPayloadFactory.bombShareSubmit(
                "04677645-a0d0-4b5f-bd5d-590b3f7f2f5d", List.of(" WC1 ", "", "WC2"));

        assertEquals("LOOT", request.getAsJsonArray("requested_types").get(0).getAsString());
        assertEquals("COMBAT_XP", request.getAsJsonArray("requested_types").get(1).getAsString());
        assertEquals("WC1", submit.getAsJsonArray("worlds").get(0).getAsString());
        assertEquals("WC2", submit.getAsJsonArray("worlds").get(1).getAsString());
    }

    private static JsonElement protocolFixture(String name) {
        String path = "/protocol/outbound/" + name;
        try (InputStream stream = OutboundPayloadFactoryTest.class.getResourceAsStream(path)) {
            if (stream == null) {
                throw new AssertionError("Missing protocol fixture " + path);
            }
            return JsonParser.parseString(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new AssertionError("Failed to read protocol fixture " + path, exception);
        }
    }
}
