package com.seqwawa.seq.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class PremadePartyTest {

    @Test
    void togglingAMemberAddsThenRemovesThem() {
        PremadeParty party = PremadeParty.named("TNA core").withMemberToggled("Blousy");
        assertTrue(party.contains("blousy"), "membership ignores case");
        assertEquals(List.of("Blousy"), party.members());

        party = party.withMemberToggled("BLOUSY");
        assertFalse(party.contains("Blousy"));
        assertTrue(party.members().isEmpty());
    }

    @Test
    void theSamePersonTypedTwiceIsOneEntry() {
        PremadeParty party = new PremadeParty("core", List.of("Blousy", "blousy", " BLOUSY "), 0L);

        assertEquals(List.of("Blousy"), party.members(), "the first spelling wins");
    }

    @Test
    void aFullPartyRefusesMoreMembers() {
        List<String> ten = IntStream.range(0, PremadeParty.MAX_MEMBERS)
                .mapToObj(index -> "player" + index)
                .toList();
        PremadeParty party = new PremadeParty("big", ten, 0L);

        assertTrue(party.isFull());
        assertEquals(ten, party.withMemberToggled("onemore").members());
    }

    @Test
    void extraMembersBeyondTheCapAreDroppedOnConstruction() {
        List<String> twelve = IntStream.range(0, PremadeParty.MAX_MEMBERS + 2)
                .mapToObj(index -> "player" + index)
                .toList();

        assertEquals(PremadeParty.MAX_MEMBERS, new PremadeParty("big", twelve, 0L).members().size());
    }

    @Test
    void addingSeveralSkipsDuplicatesAndStopsAtTheCap() {
        PremadeParty party = PremadeParty.named("core").withMemberToggled("Blousy");

        party = party.withMembersAdded(Arrays.asList("a3pki", "BLOUSY", "divvy", null, "  "));

        assertEquals(List.of("Blousy", "a3pki", "divvy"), party.members());
    }

    @Test
    void aPartyNeedsANameAndAMemberToBeWorthSaving() {
        assertFalse(PremadeParty.named("core").isUsable(), "a name with nobody in it is not a party");
        assertFalse(PremadeParty.named("").withMemberToggled("Blousy").isUsable());
        assertTrue(PremadeParty.named("core").withMemberToggled("Blousy").isUsable());
    }

    @Test
    void namesAreTrimmedAndCapped() {
        assertEquals("core", PremadeParty.named("  core  ").name());
        assertEquals(
                PremadeParty.MAX_NAME_LENGTH,
                PremadeParty.named("n".repeat(PremadeParty.MAX_NAME_LENGTH + 20)).name().length());
    }

    @Test
    void theKeyIgnoresCaseSoRenamingTheCaseIsNotANewParty() {
        assertEquals(PremadeParty.named("TNA Core").key(), PremadeParty.named("tna core").key());
    }

    @Test
    void summaryListsMembersOrSaysThereAreNone() {
        assertEquals("no members yet", PremadeParty.named("core").summary());
        assertEquals(
                "Blousy, a3pki",
                PremadeParty.named("core").withMemberToggled("Blousy").withMemberToggled("a3pki").summary());
    }
}
