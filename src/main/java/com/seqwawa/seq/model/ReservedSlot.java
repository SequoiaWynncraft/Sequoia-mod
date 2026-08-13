package com.seqwawa.seq.model;

import java.time.Instant;

public record ReservedSlot(
        String playerUUID,
        String observedUsername,
        PartyRole role,
        Instant createdAt,
        PartyReservedSlotSource source) {
    public ReservedSlot(String playerUUID, String observedUsername, PartyRole role, Instant createdAt) {
        this(playerUUID, observedUsername, role, createdAt, PartyReservedSlotSource.MANUAL);
    }

    public ReservedSlot(String playerUUID, PartyRole role, Instant createdAt) {
        this(playerUUID, null, role, createdAt, PartyReservedSlotSource.MANUAL);
    }

    public boolean isObservedWynnMember() {
        return observedUsername != null && !observedUsername.isBlank();
    }
}
