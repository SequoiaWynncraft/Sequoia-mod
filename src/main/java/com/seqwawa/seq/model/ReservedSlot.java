package com.seqwawa.seq.model;

import java.time.Instant;

public record ReservedSlot(String playerUUID, String observedUsername, PartyRole role, Instant createdAt) {
    public ReservedSlot(String playerUUID, PartyRole role, Instant createdAt) {
        this(playerUUID, null, role, createdAt);
    }
}
