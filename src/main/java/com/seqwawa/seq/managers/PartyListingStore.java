package com.seqwawa.seq.managers;

import com.seqwawa.seq.model.Listing;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

final class PartyListingStore {
    private final List<Listing> listings = new CopyOnWriteArrayList<>();
    private final Object lock = new Object();
    private volatile int version;

    List<Listing> listings() {
        return listings;
    }

    int version() {
        return version;
    }

    List<Listing> replaceAll(List<Listing> source) {
        List<Listing> deduplicated = deduplicateById(source);
        synchronized (lock) {
            listings.clear();
            listings.addAll(deduplicated);
            version++;
        }
        return deduplicated;
    }

    void upsert(Listing updated, boolean moveToTop) {
        synchronized (lock) {
            int firstMatchIndex = -1;
            for (int index = 0; index < listings.size(); index++) {
                if (listings.get(index).id() != updated.id()) {
                    continue;
                }
                if (firstMatchIndex < 0) {
                    firstMatchIndex = index;
                } else {
                    listings.remove(index);
                    index--;
                }
            }

            if (firstMatchIndex < 0) {
                if (moveToTop) {
                    listings.add(0, updated);
                } else {
                    listings.add(updated);
                }
            } else {
                listings.set(firstMatchIndex, updated);
                if (moveToTop && firstMatchIndex > 0) {
                    listings.remove(firstMatchIndex);
                    listings.add(0, updated);
                }
            }
            version++;
        }
    }

    void remove(long listingId) {
        synchronized (lock) {
            listings.removeIf(listing -> listing.id() == listingId);
            version++;
        }
    }

    Listing find(long listingId) {
        synchronized (lock) {
            return listings.stream()
                    .filter(listing -> listing != null && listing.id() == listingId)
                    .findFirst()
                    .orElse(null);
        }
    }

    List<Listing> snapshot() {
        synchronized (lock) {
            return List.copyOf(listings);
        }
    }

    static List<Listing> deduplicateById(List<Listing> source) {
        LinkedHashMap<Long, Listing> unique = new LinkedHashMap<>();
        for (Listing listing : source) {
            unique.putIfAbsent(listing.id(), listing);
        }
        return new ArrayList<>(unique.values());
    }
}
