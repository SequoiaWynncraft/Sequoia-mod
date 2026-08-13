package com.seqwawa.seq.wynnbuilder.data;

import com.seqwawa.seq.wynnbuilder.codec.BitVector;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * A complete build: equipment and powders, tomes, aspects, skill points, level and ability tree.
 *
 * <p>Mutable, because the builder screen edits it in place. {@link #copy()} produces an independent
 * snapshot for undo or comparison.
 */
public final class WynnBuild {
    /** Skill point order used throughout the encoding: earth, thunder, water, fire, air. */
    public static final List<String> SKILL_POINT_ORDER = List.of("Strength", "Dexterity", "Intelligence", "Defence", "Agility");
    public static final List<String> SKILL_POINT_SHORT = List.of("str", "dex", "int", "def", "agi");
    public static final int SKILL_POINT_TYPES = 5;

    private int dataVersionIndex;
    private final Map<EquipmentSlot, BuildEquipment> equipment = new EnumMap<>(EquipmentSlot.class);
    private final Map<EquipmentSlot, List<Powder>> powders = new EnumMap<>(EquipmentSlot.class);
    private final List<Integer> tomeIds = new ArrayList<>();
    private final List<AspectSelection> aspects = new ArrayList<>();
    /** Manually assigned skill points in etwfa order; {@code null} means "let the builder decide". */
    private final Integer[] assignedSkillPoints = new Integer[SKILL_POINT_TYPES];
    private int level;
    private BitVector abilityTreeBits = new BitVector();

    public WynnBuild(int dataVersionIndex, int level, int tomeCount, int aspectCount) {
        this.dataVersionIndex = dataVersionIndex;
        this.level = level;
        for (EquipmentSlot slot : EquipmentSlot.encodingOrder()) {
            equipment.put(slot, BuildEquipment.none());
            powders.put(slot, new ArrayList<>());
        }
        for (int i = 0; i < tomeCount; i++) {
            tomeIds.add(null);
        }
        for (int i = 0; i < aspectCount; i++) {
            aspects.add(null);
        }
    }

    /** An aspect slot: an aspect ID together with its tier (1-based). */
    public record AspectSelection(int aspectId, int tier) {}

    public int dataVersionIndex() {
        return dataVersionIndex;
    }

    public void setDataVersionIndex(int dataVersionIndex) {
        this.dataVersionIndex = dataVersionIndex;
    }

    public BuildEquipment equipment(EquipmentSlot slot) {
        return equipment.getOrDefault(slot, BuildEquipment.none());
    }

    public void setEquipment(EquipmentSlot slot, BuildEquipment value) {
        equipment.put(slot, value == null ? BuildEquipment.none() : value);
    }

    public Map<EquipmentSlot, BuildEquipment> allEquipment() {
        return equipment;
    }

    /** The mutable powder list for a slot. Non-powderable slots always stay empty. */
    public List<Powder> powders(EquipmentSlot slot) {
        return powders.computeIfAbsent(slot, ignored -> new ArrayList<>());
    }

    public void setPowders(EquipmentSlot slot, List<Powder> value) {
        powders.put(slot, new ArrayList<>(value));
    }

    public List<Integer> tomeIds() {
        return tomeIds;
    }

    public List<AspectSelection> aspects() {
        return aspects;
    }

    public Integer[] assignedSkillPoints() {
        return assignedSkillPoints;
    }

    public Integer assignedSkillPoint(int index) {
        return assignedSkillPoints[index];
    }

    public void setAssignedSkillPoint(int index, Integer value) {
        assignedSkillPoints[index] = value;
    }

    /** Whether any element carries a manual assignment, which selects the encoded SP flag. */
    public boolean hasManualSkillPoints() {
        for (Integer value : assignedSkillPoints) {
            if (value != null) {
                return true;
            }
        }
        return false;
    }

    public void clearManualSkillPoints() {
        java.util.Arrays.fill(assignedSkillPoints, null);
    }

    public int level() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public BitVector abilityTreeBits() {
        return abilityTreeBits;
    }

    public void setAbilityTreeBits(BitVector bits) {
        this.abilityTreeBits = bits == null ? new BitVector() : bits;
    }

    public boolean hasAnyEquipment() {
        return equipment.values().stream().anyMatch(value -> !value.isEmpty());
    }

    public WynnBuild copy() {
        WynnBuild clone = new WynnBuild(dataVersionIndex, level, tomeIds.size(), aspects.size());
        clone.equipment.putAll(equipment);
        for (Map.Entry<EquipmentSlot, List<Powder>> entry : powders.entrySet()) {
            clone.powders.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
        for (int i = 0; i < tomeIds.size(); i++) {
            clone.tomeIds.set(i, tomeIds.get(i));
        }
        for (int i = 0; i < aspects.size(); i++) {
            clone.aspects.set(i, aspects.get(i));
        }
        System.arraycopy(assignedSkillPoints, 0, clone.assignedSkillPoints, 0, SKILL_POINT_TYPES);
        clone.abilityTreeBits = abilityTreeBits;
        return clone;
    }
}
