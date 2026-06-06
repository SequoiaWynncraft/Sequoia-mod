package org.sequoia.seq.map;

import java.awt.Color;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public enum GatheringProfession {
    WOODCUTTING(new Color(91, 190, 106, 235)),
    MINING(new Color(122, 165, 219, 235)),
    FARMING(new Color(228, 190, 79, 235)),
    FISHING(new Color(82, 190, 211, 235)),
    UNKNOWN(new Color(210, 210, 210, 210));

    private static final Map<GatheringProfession, Set<String>> RESOURCES = Map.of(
            WOODCUTTING, Set.of(
                    "OAK", "BIRCH", "WILLOW", "ACACIA", "SPRUCE", "JUNGLE", "DARK", "LIGHT",
                    "PINE", "AVO", "SKY", "MAPLE", "REDWOOD", "DERNIC_TREE", "DERNIC_WOOD"),
            MINING, Set.of(
                    "COPPER", "GRANITE", "GOLD", "SANDSTONE", "IRON", "SILVER", "COBALT", "KANDERSTONE",
                    "DIAMOND", "VOIDSTONE", "MOLTEN", "TITANIUM", "CINNABAR", "DERNIC_ORE"),
            FARMING, Set.of(
                    "WHEAT", "BARLEY", "OAT", "MALT", "HOPS", "RYE", "MILLET", "DECAY", "DECAY_ROOT",
                    "RICE", "SORGHUM", "HEMP", "JUTE", "HEATHER", "DERNIC_CROPS", "DERNIC_GRAIN"),
            FISHING, Set.of(
                    "GUDGEON", "TROUT", "SALMON", "CARP", "ICEFISH", "PIRANHA", "KOI", "GYLIA",
                    "BASS", "MOLTEN_EEL", "STARFISH", "STURGEON", "MAHSEER", "DERNIC_FISH"));

    private final Color color;

    GatheringProfession(Color color) {
        this.color = color;
    }

    public Color color() {
        return color;
    }

    public static GatheringProfession fromResource(String resource) {
        if (resource == null) {
            return UNKNOWN;
        }
        String normalized = resource.trim().toUpperCase(Locale.ROOT).replace(" ", "_").replace("-", "_");
        for (Map.Entry<GatheringProfession, Set<String>> entry : RESOURCES.entrySet()) {
            if (entry.getValue().contains(normalized)) {
                return entry.getKey();
            }
        }
        return UNKNOWN;
    }
}
