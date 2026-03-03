package org.sequoia.seq.events;

import org.sequoia.seq.model.Listing;

/**
 * Fired when a party_finder_update WebSocket message is received.
 */
public record PartyFinderUpdateEvent(String action, Listing listing) {}
