package com.seqwawa.seq.wynnbuilder.data;

import java.util.List;

/**
 * The nine build equipment slots in encoding order.
 *
 * <p>The weapon is always encoded last. Exactly five slots are powderable (four armour pieces plus
 * the weapon), matching {@code POWDERABLE_EQUIPMENT_NUM} in the encoding constants; accessories
 * carry no powder flag at all, so getting this set wrong desynchronises the whole bit stream.
 */
public enum EquipmentSlot {
    HELMET("Helmet", true, "helmet"),
    CHESTPLATE("Chestplate", true, "chestplate"),
    LEGGINGS("Leggings", true, "leggings"),
    BOOTS("Boots", true, "boots"),
    RING1("Ring 1", false, "ring"),
    RING2("Ring 2", false, "ring"),
    BRACELET("Bracelet", false, "bracelet"),
    NECKLACE("Necklace", false, "necklace"),
    WEAPON("Weapon", true, null);

    private static final List<EquipmentSlot> ORDER = List.of(values());

    private final String label;
    private final boolean powderable;
    private final String itemType;

    EquipmentSlot(String label, boolean powderable, String itemType) {
        this.label = label;
        this.powderable = powderable;
        this.itemType = itemType;
    }

    public String label() {
        return label;
    }

    /** Whether a powder flag is present for this slot in the encoded stream. */
    public boolean powderable() {
        return powderable;
    }

    /** The item {@code type} accepted here, or {@code null} for the weapon which accepts any. */
    public String itemType() {
        return itemType;
    }

    public boolean isAccessory() {
        return this == RING1 || this == RING2 || this == BRACELET || this == NECKLACE;
    }

    public boolean isArmour() {
        return this == HELMET || this == CHESTPLATE || this == LEGGINGS || this == BOOTS;
    }

    /** Slots in encoding order. */
    public static List<EquipmentSlot> encodingOrder() {
        return ORDER;
    }
}
