package com.seqwawa.seq.managers;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.seqwawa.seq.model.Activity;
import com.seqwawa.seq.model.Listing;
import com.seqwawa.seq.model.PartyMode;
import com.seqwawa.seq.model.PartyRegion;
import com.seqwawa.seq.model.PartyStatus;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class PartyFinderNotificationFormatterTest {
    @Test
    void commandQuotingEscapesBackslashesAndQuotes() {
        assertEquals("\"\"", PartyFinderNotificationFormatter.quoteForCommand(null));
        assertEquals(
                "\"token\\\\segment\\\"quoted\"",
                PartyFinderNotificationFormatter.quoteForCommand("token\\segment\"quoted"));
    }

    @Test
    void inviterFallbacksAndInviteAllGrammarRemainStable() {
        assertEquals("a player", PartyFinderNotificationFormatter.inviterName(null));
        assertEquals("a player", PartyFinderNotificationFormatter.inviterName("Loading..."));
        assertEquals("CinfrasCitizen", PartyFinderNotificationFormatter.inviterName("CinfrasCitizen"));
        assertEquals("no valid party members to invite.", PartyFinderNotificationFormatter.inviteAllMessage(0, 0));
        assertEquals("sent 1 party invite.", PartyFinderNotificationFormatter.inviteAllMessage(1, 0));
        assertEquals(
                "sent 2 party invites. Skipped 1.", PartyFinderNotificationFormatter.inviteAllMessage(2, 1));
    }

    @Test
    void activitySummaryDeduplicatesAndUsesEstablishedRaidAbbreviations() {
        Listing listing = new Listing(
                42,
                List.of(
                        new Activity(1, "The Nameless Anomaly", 4),
                        new Activity(2, "TNA", 4),
                        new Activity(3, "The Wartorn Palace", 4)),
                null,
                "leader",
                PartyMode.CHILL,
                false,
                PartyRegion.NA,
                PartyStatus.OPEN,
                null,
                null,
                List.of(),
                List.of(),
                Instant.EPOCH);

        assertEquals("TNA/TWP", PartyFinderNotificationFormatter.activitySummary(listing));
    }
}
