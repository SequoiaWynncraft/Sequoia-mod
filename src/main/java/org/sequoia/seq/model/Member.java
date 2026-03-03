package org.sequoia.seq.model;

import java.time.Instant;

public record Member(
                String playerUUID,
                PartyRole role,
                WynnClassType classType,
                Instant joinedAt) {
}
