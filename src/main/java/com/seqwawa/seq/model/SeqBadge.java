package com.seqwawa.seq.model;

import java.util.Locale;
import net.minecraft.resources.Identifier;

public record SeqBadge(SeqBadgeEvent event, SeqBadgeTier tier) {
    public SeqBadge {
        if (event == null || tier == null) {
            throw new IllegalArgumentException("Badge event and tier are required.");
        }
    }

    public Identifier textureId() {
        return Identifier.fromNamespaceAndPath(
                "seq", "badges/" + event.commandName() + "_" + tier.commandName() + ".png");
    }

    public static SeqBadge parseLegacy(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        for (SeqBadgeEvent event : SeqBadgeEvent.values()) {
            String prefix = event.apiName() + "_";
            if (!value.toUpperCase(Locale.ROOT).startsWith(prefix)) {
                continue;
            }
            SeqBadgeTier tier = SeqBadgeTier.parse(value.substring(prefix.length()));
            return tier == null ? null : new SeqBadge(event, tier);
        }
        return null;
    }
}
