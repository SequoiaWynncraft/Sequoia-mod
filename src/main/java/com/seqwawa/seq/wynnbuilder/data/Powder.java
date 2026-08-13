package com.seqwawa.seq.wynnbuilder.data;

import java.util.List;
import java.util.Locale;

/**
 * A single applied powder, identified by element and tier.
 *
 * <p>Encoded IDs are {@code elementIndex * tierCount + (tier - 1)}, so an ID only means something
 * alongside the tier count of the version that produced it. {@link #decodeId} and {@link #encodeId}
 * carry out the rescaling described in the encoding spec, which lets a build written when only five
 * powder tiers existed still resolve correctly today.
 */
public record Powder(PowderElement element, int tier) {
    public Powder {
        if (tier < 1) {
            throw new IllegalArgumentException("Powder tier must be at least 1");
        }
    }

    public enum PowderElement {
        EARTH('E', "Earth"),
        THUNDER('T', "Thunder"),
        WATER('W', "Water"),
        FIRE('F', "Fire"),
        AIR('A', "Air");

        private static final List<PowderElement> ORDER = List.of(values());

        private final char symbol;
        private final String label;

        PowderElement(char symbol, String label) {
            this.symbol = symbol;
            this.label = label;
        }

        public char symbol() {
            return symbol;
        }

        public String label() {
            return label;
        }

        /** Element order used by the encoding: E, T, W, F, A. */
        public static List<PowderElement> encodingOrder() {
            return ORDER;
        }

        public static PowderElement byIndex(int index) {
            return ORDER.get(Math.floorMod(index, ORDER.size()));
        }

        public static PowderElement bySymbol(char symbol) {
            char upper = Character.toUpperCase(symbol);
            for (PowderElement element : ORDER) {
                if (element.symbol == upper) {
                    return element;
                }
            }
            return null;
        }
    }

    public int elementIndex() {
        return PowderElement.encodingOrder().indexOf(element);
    }

    /** The powder ID as understood by the current tier count. */
    public int id(int tierCount) {
        return elementIndex() * tierCount + (tier - 1);
    }

    /**
     * Rewrites a powder ID read from an older vector into the current tier space.
     *
     * @param encodedId the value straight out of the bit vector
     * @param encodedTierCount the tier count of the version that wrote it
     */
    public static Powder decodeId(int encodedId, int encodedTierCount) {
        int elementIndex = encodedId / encodedTierCount;
        int tier = encodedId % encodedTierCount + 1;
        return new Powder(PowderElement.byIndex(elementIndex), tier);
    }

    /** The inverse of {@link #decodeId}: renders this powder into an older tier space. */
    public int encodeId(int encodedTierCount) {
        return elementIndex() * encodedTierCount + (tier - 1);
    }

    public String displayName() {
        return element.label() + " " + toRoman(tier);
    }

    /** Compact form used in tooltips and by {@link #parse}, e.g. {@code E6}. */
    public String shortName() {
        return String.valueOf(element.symbol()) + tier;
    }

    /** Parses the compact form; returns {@code null} when the text is not a powder. */
    public static Powder parse(String text) {
        if (text == null || text.length() < 2) {
            return null;
        }
        String trimmed = text.trim().toUpperCase(Locale.ROOT);
        PowderElement element = PowderElement.bySymbol(trimmed.charAt(0));
        if (element == null) {
            return null;
        }
        try {
            int tier = Integer.parseInt(trimmed.substring(1));
            return tier >= 1 ? new Powder(element, tier) : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static String toRoman(int value) {
        return switch (value) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            case 6 -> "VI";
            case 7 -> "VII";
            default -> String.valueOf(value);
        };
    }
}
