package com.seqwawa.seq.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PartyFinderSpotTest {

    @Test
    void labelsTheRaidAndHowFullItIs() {
        assertEquals("PF TNA 2/4", new PartyFinderSpot(1, List.of("TNA"), 2, 4, true, List.of()).label());
        assertEquals("PF TNA+1 3/4", new PartyFinderSpot(1, List.of("TNA", "TCC"), 3, 4, true, List.of()).label());
        assertEquals("PF ? 1/4", new PartyFinderSpot(1, List.of(), 1, 4, true, List.of()).label());
    }

    @Test
    void onlyAnOpenListingWithRoomIsJoinable() {
        assertTrue(new PartyFinderSpot(1, List.of("TNA"), 3, 4, true, List.of()).isJoinable());
        assertFalse(new PartyFinderSpot(1, List.of("TNA"), 4, 4, true, List.of()).isJoinable(), "full");
        assertFalse(new PartyFinderSpot(1, List.of("TNA"), 2, 4, false, List.of()).isJoinable(), "closed or invite only");
    }

    @Test
    void indexesMembersByUuidWhateverTheDashesAndCase() {
        PartyFinderSpot tna = new PartyFinderSpot(
                7, List.of("TNA"), 2, 4, true, List.of("66EFB975-0000-0000-0000-000000000001", "aaaa"));
        PartyFinderSpot notg = new PartyFinderSpot(8, List.of("NOTG"), 1, 4, true, List.of("bbbb"));

        Map<String, PartyFinderSpot> index = PartyFinderSpot.indexByMember(List.of(tna, notg));

        assertSame(tna, index.get(RaidProfilesResponse.normalizeUuid("66efb975-0000-0000-0000-000000000001")));
        assertSame(tna, index.get("aaaa"));
        assertSame(notg, index.get("bbbb"));
        assertNull(index.get("cccc"));
    }

    @Test
    void theFirstListingWinsWhenTwoClaimTheSamePlayer() {
        PartyFinderSpot first = new PartyFinderSpot(1, List.of("TNA"), 2, 4, true, List.of("aaaa"));
        PartyFinderSpot second = new PartyFinderSpot(2, List.of("TCC"), 2, 4, true, List.of("aaaa"));

        assertSame(first, PartyFinderSpot.indexByMember(List.of(first, second)).get("aaaa"));
    }

    @Test
    void noListingsMeansAnEmptyIndex() {
        assertTrue(PartyFinderSpot.indexByMember(null).isEmpty());
        assertTrue(PartyFinderSpot.indexByMember(List.of()).isEmpty());
    }
}
