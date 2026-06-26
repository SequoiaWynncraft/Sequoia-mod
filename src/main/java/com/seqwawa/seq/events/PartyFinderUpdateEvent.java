package com.seqwawa.seq.events;

import com.seqwawa.seq.model.Listing;

/**
 * Fired when a party_finder_update WebSocket message is received.
 */
public record PartyFinderUpdateEvent(String action, Listing listing) {}
