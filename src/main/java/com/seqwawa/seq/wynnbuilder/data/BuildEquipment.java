package com.seqwawa.seq.wynnbuilder.data;

import com.seqwawa.seq.wynnbuilder.codec.BitVector;

/** What occupies one of the nine equipment slots. */
public sealed interface BuildEquipment {

    /** An empty slot. Encoded as a {@code NORMAL} item with ID 0. */
    record None() implements BuildEquipment {
        public static final None INSTANCE = new None();
    }

    /** A regular Wynncraft item, referenced by its WynnBuilder item ID. */
    record Normal(int itemId) implements BuildEquipment {}

    /** A crafted item, carrying its own self-contained encoding. */
    record Crafted(CraftedItem craft) implements BuildEquipment {}

    /**
     * A custom item.
     *
     * <p>The mod does not offer a custom-item editor, so the original bits are preserved verbatim
     * and written back unchanged. That keeps a shared link containing a custom item round-trippable
     * instead of silently dropping the item.
     */
    record Custom(BitVector bits) implements BuildEquipment {}

    /**
     * A piece read off the player, carrying the values the game itself prints.
     *
     * <p>Everything about it is already resolved: the identifications are the roll this particular
     * drop got rather than a range, and the damage, health and defences are the powdered numbers
     * from the tooltip. That is what makes the powder tier a non-question — Wynncraft never shows
     * it and Wynntils assumes six, but the tooltip has already applied whatever tier is really
     * socketed, so the build carries no powders and nothing has to be guessed.
     *
     * @param crafted whether the piece is player-made, which the skill point solver needs to know
     *     because a craft's own bonuses cannot pay for its own requirements
     * @param best the same item rolled perfectly, used to price what a bad roll is costing
     */
    record Live(WynnItem item, boolean crafted, WynnItem best) implements BuildEquipment {
        public Live(WynnItem item, boolean crafted) {
            this(item, crafted, item);
        }
    }

    static BuildEquipment none() {
        return None.INSTANCE;
    }

    default boolean isEmpty() {
        return this instanceof None;
    }
}
