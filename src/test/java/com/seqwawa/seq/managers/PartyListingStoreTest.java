package com.seqwawa.seq.managers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.seqwawa.seq.model.Listing;
import com.seqwawa.seq.model.PartyMode;
import com.seqwawa.seq.model.PartyRegion;
import com.seqwawa.seq.model.PartyStatus;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class PartyListingStoreTest {
    @Test
    void replaceAllDeduplicatesByFirstOccurrenceAndPreservesOrder() {
        PartyListingStore store = new PartyListingStore();
        Listing first = listing(1, "first");
        Listing duplicate = listing(1, "duplicate");
        Listing second = listing(2, "second");

        List<Listing> replaced = store.replaceAll(List.of(first, duplicate, second));

        assertEquals(List.of(first, second), replaced);
        assertEquals(List.of(first, second), store.snapshot());
        assertEquals(1, store.version());
    }

    @Test
    void upsertReplacesFirstMatchRemovesDuplicatesAndCanMoveToTop() {
        PartyListingStore store = new PartyListingStore();
        Listing first = listing(1, "first");
        Listing second = listing(2, "second");
        store.listings().addAll(List.of(first, second, listing(2, "duplicate")));
        Listing updated = listing(2, "updated");

        store.upsert(updated, true);

        assertEquals(List.of(updated, first), store.snapshot());
        assertSame(updated, store.find(2));
        assertEquals(1, store.version());
    }

    @Test
    void appendAndRemoveAdvanceVersionExactlyOncePerTransition() {
        PartyListingStore store = new PartyListingStore();
        Listing listing = listing(7, "created");

        store.upsert(listing, false);
        store.remove(7);
        store.remove(999);

        assertEquals(List.of(), store.snapshot());
        assertEquals(3, store.version());
    }

    private static Listing listing(long id, String note) {
        return new Listing(
                id,
                List.of(),
                null,
                "leader-" + id,
                PartyMode.CHILL,
                false,
                PartyRegion.NA,
                PartyStatus.OPEN,
                null,
                note,
                List.of(),
                List.of(),
                Instant.EPOCH);
    }
}
