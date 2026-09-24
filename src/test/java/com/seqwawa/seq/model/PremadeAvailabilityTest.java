package com.seqwawa.seq.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class PremadeAvailabilityTest {

    private static GuildMemberPresence online(String name) {
        return new GuildMemberPresence(name, null, null, "NA1", false);
    }

    private static final PremadeParty PARTY = new PremadeParty("Tna crew", List.of("Me", "Ann", "Bob", "Cid"), 1L);

    @Test
    void countsWhoIsOnlineAndWhoIsBusy() {
        PremadeAvailability availability = PremadeAvailability.of(
                PARTY, List.of(online("Ann"), online("bob")), name -> name.equalsIgnoreCase("Bob"), "Me");

        assertEquals("3/4 online, 1 busy", availability.summary());
        assertEquals(PremadeAvailability.State.FREE, availability.seats().get(0).state(), "you are never offline");
        assertTrue(availability.seats().get(0).self());
        assertEquals(PremadeAvailability.State.BUSY, availability.seats().get(2).state());
        assertEquals(PremadeAvailability.State.OFFLINE, availability.seats().get(3).state());
        assertFalse(availability.everyoneFree());
    }

    @Test
    void invitesGoOnlyToTheFreeAndNeverToYou() {
        PremadeAvailability availability = PremadeAvailability.of(
                PARTY, List.of(online("Ann"), online("Bob")), name -> name.equals("Bob"), "me");

        assertEquals(List.of("Ann"), availability.freeToInvite());
        assertEquals("1 busy and 1 offline left out", availability.leftOutSummary());
    }

    @Test
    void aGroupThatCanAllComeSaysSo() {
        PremadeAvailability availability = PremadeAvailability.of(
                PARTY, List.of(online("Ann"), online("Bob"), online("Cid")), name -> false, "Me");

        assertEquals("4/4 online", availability.summary());
        assertTrue(availability.everyoneFree());
        assertNull(availability.leftOutSummary());
        assertEquals(List.of("Ann", "Bob", "Cid"), availability.freeToInvite());
    }

    @Test
    void nobodyOnlineReadsPlainly() {
        PremadeParty others = new PremadeParty("Others", List.of("Ann", "Bob"), 1L);
        PremadeAvailability availability = PremadeAvailability.of(others, List.of(), name -> false, "Me");

        assertEquals("nobody online", availability.summary());
        assertEquals("2 offline left out", availability.leftOutSummary());
        assertTrue(availability.freeToInvite().isEmpty());
    }
}
