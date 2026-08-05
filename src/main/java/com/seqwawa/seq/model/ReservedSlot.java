package com.seqwawa.seq.model;

import java.time.Instant;

public record ReservedSlot(String playerUUID, PartyRole role, Instant createdAt) {}
