package org.sequoia.seq.managers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.sequoia.seq.model.Activity;
import org.sequoia.seq.model.Listing;
import org.sequoia.seq.model.Member;
import org.sequoia.seq.model.PartyMode;
import org.sequoia.seq.model.PartyRegion;
import org.sequoia.seq.model.PartyRole;
import org.sequoia.seq.model.PartyStatus;
import org.sequoia.seq.model.WynnClassType;
import org.sequoia.seq.network.auth.StoredAuthSession;

class PartyFinderManagerAnnouncementTest {

    private static final String LOCAL_UUID = "00000000-0000-0000-0000-000000000001";
    private static final Instant BASE_TIME = Instant.parse("2026-03-20T12:00:00Z");

    @Test
    void selectOpenPartyAnnouncementCandidates_keepsOnlyJoinableRemoteListings() {
        Listing eligible = listing(10L, "leader-a", PartyStatus.OPEN, 1, 0, BASE_TIME.minusSeconds(30));
        Listing full = listing(11L, "leader-b", PartyStatus.OPEN, 4, 0, BASE_TIME.minusSeconds(60));
        Listing closed = listing(12L, "leader-c", PartyStatus.CLOSED, 1, 0, BASE_TIME.minusSeconds(90));
        Listing ownListing = listing(13L, LOCAL_UUID, PartyStatus.OPEN, 1, 0, BASE_TIME.minusSeconds(120));
        Listing reservedForLocal = listing(
                14L,
                "leader-d",
                PartyStatus.OPEN,
                1,
                1,
                BASE_TIME.minusSeconds(150),
                List.of(new Member(LOCAL_UUID, PartyRole.HEALER, WynnClassType.MAGE, BASE_TIME.minusSeconds(149))));

        List<Listing> candidates = PartyFinderManager.selectOpenPartyAnnouncementCandidates(
                List.of(eligible, full, closed, ownListing, reservedForLocal),
                LOCAL_UUID);

        assertEquals(1, candidates.size());
        assertEquals(10L, candidates.getFirst().id());
    }

    @Test
    void selectOpenPartyAnnouncementCandidates_sortsNewestFirst() {
        Listing newest = listing(21L, "leader-new", PartyStatus.OPEN, 1, 0, BASE_TIME.minusSeconds(10));
        Listing oldest = listing(20L, "leader-old", PartyStatus.OPEN, 1, 0, BASE_TIME.minusSeconds(100));

        List<Listing> candidates = PartyFinderManager.selectOpenPartyAnnouncementCandidates(
                List.of(oldest, newest),
                LOCAL_UUID);

        assertIterableEquals(List.of(21L, 20L), candidates.stream().map(Listing::id).toList());
    }

    @Test
    void buildOpenPartyAnnouncementSummary_buildsJoinCommandsAndLeaderNames() {
        Listing newer = listing(31L, "leader-1", PartyStatus.OPEN, 2, 0, BASE_TIME.minusSeconds(5));
        Listing older = listing(30L, "leader-2", PartyStatus.OPEN, 1, 0, BASE_TIME.minusSeconds(50));

        PartyFinderManager.OpenPartyAnnouncementSummary summary = PartyFinderManager.buildOpenPartyAnnouncementSummary(
                List.of(newer, older),
                uuid -> "name-" + uuid);

        assertFalse(summary.isEmpty());
        assertEquals(2, summary.entries().size());
        assertEquals("name-leader-1", summary.entries().get(0).leaderName());
        assertEquals("/seq p join 31", summary.entries().get(0).joinCommand());
        assertEquals("NOG", summary.entries().get(0).activitySummary());
        assertEquals("/seq p join 30", summary.entries().get(1).joinCommand());
    }

    @Test
    void buildOpenPartyAnnouncementSummary_returnsEmptyForNoCandidates() {
        PartyFinderManager.OpenPartyAnnouncementSummary summary =
                PartyFinderManager.buildOpenPartyAnnouncementSummary(List.of(), uuid -> "ignored");

        assertTrue(summary.isEmpty());
    }

    @Test
    void resolveIdentityUuid_prefersAuthenticatedSessionUuid() {
        StoredAuthSession session = new StoredAuthSession(
                "token",
                BASE_TIME.plusSeconds(600),
                "00000000-0000-0000-0000-00000000abcd",
                "LinkedPlayer");

        String resolved = PartyFinderManager.resolveIdentityUuid(LOCAL_UUID, session);

        assertEquals("00000000-0000-0000-0000-00000000abcd", resolved);
    }

    @Test
    void resolveIdentityUuid_fallsBackToCurrentPlayerUuidWhenSessionMissingUuid() {
        StoredAuthSession session = new StoredAuthSession("token", BASE_TIME.plusSeconds(600), "", "LinkedPlayer");

        String resolved = PartyFinderManager.resolveIdentityUuid(LOCAL_UUID, session);

        assertEquals(LOCAL_UUID, resolved);
    }

    private static Listing listing(
            long id,
            String leaderUuid,
            PartyStatus status,
            int memberCount,
            int reservedCount,
            Instant createdAt) {
        return listing(id, leaderUuid, status, memberCount, reservedCount, createdAt, members(leaderUuid, memberCount), reserved(reservedCount, createdAt));
    }

    private static Listing listing(
            long id,
            String leaderUuid,
            PartyStatus status,
            int memberCount,
            int reservedCount,
            Instant createdAt,
            List<Member> reservedSlots) {
        return listing(id, leaderUuid, status, memberCount, reservedCount, createdAt, members(leaderUuid, memberCount), reservedSlots);
    }

    private static Listing listing(
            long id,
            String leaderUuid,
            PartyStatus status,
            int memberCount,
            int reservedCount,
            Instant createdAt,
            List<Member> members,
            List<Member> reservedSlots) {
        return new Listing(
                id,
                List.of(new Activity(1L, "Nest of the Grootslangs", 4)),
                null,
                leaderUuid,
                PartyMode.CHILL,
                false,
                PartyRegion.NA,
                status,
                null,
                null,
                members,
                reservedSlots.isEmpty() ? reserved(reservedCount, createdAt) : reservedSlots,
                createdAt);
    }

    private static List<Member> members(String leaderUuid, int memberCount) {
        java.util.ArrayList<Member> members = new java.util.ArrayList<>();
        members.add(new Member(leaderUuid, PartyRole.DPS, WynnClassType.WARRIOR, BASE_TIME));
        for (int i = 1; i < memberCount; i++) {
            members.add(new Member(
                    "member-" + i,
                    PartyRole.DPS,
                    WynnClassType.ARCHER,
                    BASE_TIME.plusSeconds(i)));
        }
        return members;
    }

    private static List<Member> reserved(int reservedCount, Instant createdAt) {
        java.util.ArrayList<Member> reservedSlots = new java.util.ArrayList<>();
        for (int i = 0; i < reservedCount; i++) {
            reservedSlots.add(new Member(
                    "reserved-" + i,
                    PartyRole.HEALER,
                    WynnClassType.MAGE,
                    createdAt.plusSeconds(i + 1)));
        }
        return reservedSlots;
    }
}
