package com.seqwawa.seq.model;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import net.minecraft.resources.Identifier;

public record SeqBadge(SeqBadgeType type, SeqBadgeTier tier) {
    public SeqBadge {
        if (type == null || tier == null) {
            throw new IllegalArgumentException("Badge type and tier are required.");
        }
    }

    public Identifier textureId() {
        return Identifier.fromNamespaceAndPath(
                "seq", "badges/" + type.commandName() + "_" + tier.commandName() + ".png");
    }

    public static List<SeqBadge> sortForRender(Collection<SeqBadge> badges) {
        return badges.stream()
                .sorted(Comparator.comparingInt((SeqBadge badge) -> badge.type().renderOrder())
                        .thenComparing(SeqBadge::type))
                .toList();
    }

    public static SeqBadge parseLegacy(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        for (SeqBadgeType type : SeqBadgeType.values()) {
            String prefix = type.apiName() + "_";
            if (!value.toUpperCase(Locale.ROOT).startsWith(prefix)) {
                continue;
            }
            SeqBadgeTier tier = SeqBadgeTier.parse(value.substring(prefix.length()));
            return tier == null ? null : new SeqBadge(type, tier);
        }
        return null;
    }
}
