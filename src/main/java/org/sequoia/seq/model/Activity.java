package org.sequoia.seq.model;

public record Activity(
        long id,
        String name,
        int maxPartySize
) {}
