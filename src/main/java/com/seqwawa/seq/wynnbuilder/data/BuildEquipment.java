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

    static BuildEquipment none() {
        return None.INSTANCE;
    }

    default boolean isEmpty() {
        return this instanceof None;
    }
}
