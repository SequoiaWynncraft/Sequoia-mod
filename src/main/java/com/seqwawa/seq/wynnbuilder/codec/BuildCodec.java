package com.seqwawa.seq.wynnbuilder.codec;

import com.seqwawa.seq.wynnbuilder.data.BuildEquipment;
import com.seqwawa.seq.wynnbuilder.data.CraftedItem;
import com.seqwawa.seq.wynnbuilder.data.EncodingConsts;
import com.seqwawa.seq.wynnbuilder.data.EquipmentSlot;
import com.seqwawa.seq.wynnbuilder.data.Powder;
import com.seqwawa.seq.wynnbuilder.data.WynnBuild;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;

/**
 * Section A of the encoding spec: the V12 binary build format.
 *
 * <p>Field order is header, equipment (with powders inline), tomes, skill points, level, aspects,
 * ability tree. Bit widths come from the {@link EncodingConsts} of the build's own data version,
 * which is why decoding a link may require downloading an older data set.
 */
public final class BuildCodec {
    /** Written into the first six bits to mark a binary vector; legacy versions used 0-11 here. */
    public static final int BINARY_FLAG = 12;
    /** Values at or below this in the leading six bits mean the hash uses a legacy encoding. */
    public static final int MAX_LEGACY_VERSION = 11;

    private static final int HEADER_FLAG_BITS = 6;
    private static final int VERSION_BITS = 10;
    private static final int ASPECT_TIER_BITS = 2;
    private static final int CUSTOM_LENGTH_BITS = 12;

    private BuildCodec() {}

    /** Whether a build hash uses the V12 binary encoding rather than a legacy one. */
    public static boolean isBinary(String hash) {
        if (hash == null || hash.isEmpty()) {
            return false;
        }
        int leading = WynnBase64.value(hash.charAt(0));
        return leading > MAX_LEGACY_VERSION;
    }

    /** Reads only the data version index, so the right data set can be fetched before decoding. */
    public static int peekDataVersionIndex(String hash) {
        BitVector vector = BitVector.fromBase64(hash);
        vector.read(HEADER_FLAG_BITS);
        return vector.readInt(VERSION_BITS);
    }

    public static String encode(WynnBuild build, EncodingConsts consts, IntFunction<String> recipeTypeLookup) {
        BitVector vector = new BitVector();

        // Header
        vector.append(BINARY_FLAG, HEADER_FLAG_BITS);
        vector.append(build.dataVersionIndex(), VERSION_BITS);

        encodeEquipment(vector, build, consts, recipeTypeLookup);
        encodeTomes(vector, build, consts);
        encodeSkillPoints(vector, build, consts);
        encodeLevel(vector, build, consts);
        encodeAspects(vector, build, consts);
        vector.appendVector(build.abilityTreeBits());

        return vector.toBase64();
    }

    public static WynnBuild decode(String hash, EncodingConsts consts, IntFunction<String> recipeTypeLookup) {
        BitVector vector = BitVector.fromBase64(hash);
        vector.read(HEADER_FLAG_BITS);
        int versionIndex = vector.readInt(VERSION_BITS);

        WynnBuild build = new WynnBuild(versionIndex, consts.maxLevel(), consts.tomeCount(), consts.aspectCount());

        decodeEquipment(vector, build, consts, recipeTypeLookup);
        decodeTomes(vector, build, consts);
        decodeSkillPoints(vector, build, consts);
        decodeLevel(vector, build, consts);
        decodeAspects(vector, build, consts);

        // Whatever remains is the ability tree, minus the trailing Base64 padding.
        BitVector abilityTree = new BitVector();
        while (vector.hasRemaining(1)) {
            abilityTree.appendBit(vector.readBit());
        }
        build.setAbilityTreeBits(abilityTree);
        return build;
    }

    // ---------------------------------------------------------------- equipment

    private static void encodeEquipment(
            BitVector vector, WynnBuild build, EncodingConsts consts, IntFunction<String> recipeTypeLookup) {
        for (EquipmentSlot slot : EquipmentSlot.encodingOrder()) {
            BuildEquipment piece = build.equipment(slot);
            switch (piece) {
                case BuildEquipment.Normal normal -> {
                    vector.append(consts.equipmentKindNormal(), consts.equipmentKindBits());
                    // 0 marks an empty slot, so real IDs are stored offset by one.
                    vector.append(normal.itemId() + 1, consts.itemIdBits());
                }
                case BuildEquipment.None ignored -> {
                    vector.append(consts.equipmentKindNormal(), consts.equipmentKindBits());
                    vector.append(0, consts.itemIdBits());
                }
                case BuildEquipment.Crafted crafted -> {
                    vector.append(consts.equipmentKindCrafted(), consts.equipmentKindBits());
                    String recipeType = recipeTypeLookup == null ? null : recipeTypeLookup.apply(crafted.craft().recipeId());
                    CraftedCodec.encode(vector, crafted.craft(), CraftedItem.isWeaponType(recipeType));
                }
                case BuildEquipment.Custom custom -> {
                    vector.append(consts.equipmentKindCustom(), consts.equipmentKindBits());
                    // The length field counts Base64 characters, not bits.
                    vector.append(custom.bits().length() / 6, CUSTOM_LENGTH_BITS);
                    vector.appendVector(custom.bits());
                }
            }
            if (slot.powderable()) {
                encodePowders(vector, build.powders(slot), consts);
            }
        }
    }

    private static void decodeEquipment(
            BitVector vector, WynnBuild build, EncodingConsts consts, IntFunction<String> recipeTypeLookup) {
        List<EquipmentSlot> slots = EquipmentSlot.encodingOrder();
        for (int i = 0; i < consts.equipmentCount() && i < slots.size(); i++) {
            EquipmentSlot slot = slots.get(i);
            int kind = vector.readInt(consts.equipmentKindBits());
            if (kind == consts.equipmentKindNormal()) {
                int id = vector.readInt(consts.itemIdBits());
                build.setEquipment(slot, id == 0 ? BuildEquipment.none() : new BuildEquipment.Normal(id - 1));
            } else if (kind == consts.equipmentKindCrafted()) {
                CraftedItem craft = CraftedCodec.decode(vector, recipeTypeLookup);
                build.setEquipment(slot, craft == null ? BuildEquipment.none() : new BuildEquipment.Crafted(craft));
            } else if (kind == consts.equipmentKindCustom()) {
                int lengthInChars = vector.readInt(CUSTOM_LENGTH_BITS);
                BitVector custom = new BitVector();
                for (int bit = 0; bit < lengthInChars * 6; bit++) {
                    custom.appendBit(vector.readBit());
                }
                build.setEquipment(slot, new BuildEquipment.Custom(custom));
            } else {
                throw new IllegalArgumentException("Unknown equipment kind " + kind);
            }
            if (slot.powderable()) {
                build.setPowders(slot, decodePowders(vector, consts));
            }
        }
    }

    // ---------------------------------------------------------------- powders

    /**
     * Groups powders by element in order of first appearance, preserving tier order within a group.
     *
     * <p>This mirrors how the game itself reorders applied powders, and the encoder relies on it to
     * make the repeat flags pay off.
     */
    static List<List<Powder>> groupPowders(List<Powder> powders) {
        List<List<Powder>> chunks = new ArrayList<>();
        int[] chunkOfElement = new int[Powder.PowderElement.encodingOrder().size()];
        java.util.Arrays.fill(chunkOfElement, -1);
        for (Powder powder : powders) {
            int elementIndex = powder.elementIndex();
            if (chunkOfElement[elementIndex] < 0) {
                chunkOfElement[elementIndex] = chunks.size();
                chunks.add(new ArrayList<>());
            }
            chunks.get(chunkOfElement[elementIndex]).add(powder);
        }
        return chunks;
    }

    private static void encodePowders(BitVector vector, List<Powder> powders, EncodingConsts consts) {
        if (powders.isEmpty()) {
            vector.append(EncodingConsts.NO_POWDERS, 1);
            return;
        }
        vector.append(EncodingConsts.HAS_POWDERS, 1);

        int elementCount = consts.powderElements();
        Powder previous = null;
        for (List<Powder> chunk : groupPowders(powders)) {
            int i = 0;
            while (i < chunk.size()) {
                Powder powder = chunk.get(i);
                if (previous == null) {
                    vector.append(powder.encodeId(consts.powderTiers()), consts.powderIdBits());
                } else {
                    vector.append(EncodingConsts.POWDER_NO_REPEAT, 1);
                    if (powder.tier() == previous.tier()) {
                        vector.append(EncodingConsts.POWDER_REPEAT_TIER, 1);
                        int wrap = Math.floorMod(powder.elementIndex() - previous.elementIndex(), elementCount) - 1;
                        vector.append(wrap, consts.powderWrapperBits());
                    } else {
                        vector.append(EncodingConsts.POWDER_CHANGE_POWDER, 1);
                        vector.append(EncodingConsts.POWDER_NEW_POWDER, 1);
                        vector.append(powder.encodeId(consts.powderTiers()), consts.powderIdBits());
                    }
                }
                // Collapse an identical run into repeat flags.
                while (++i < chunk.size() && chunk.get(i).equals(powder)) {
                    vector.append(EncodingConsts.POWDER_REPEAT, 1);
                }
                previous = powder;
            }
        }
        vector.append(EncodingConsts.POWDER_NO_REPEAT, 1);
        vector.append(EncodingConsts.POWDER_CHANGE_POWDER, 1);
        vector.append(EncodingConsts.POWDER_NEW_ITEM, 1);
    }

    private static List<Powder> decodePowders(BitVector vector, EncodingConsts consts) {
        List<Powder> powders = new ArrayList<>();
        if (vector.readInt(1) == EncodingConsts.NO_POWDERS) {
            return powders;
        }
        Powder previous = Powder.decodeId(vector.readInt(consts.powderIdBits()), consts.powderTiers());
        powders.add(previous);

        // Each iteration reads one to three flags, narrowing from "same powder again" down to
        // "end of item". Every branch consumes exactly the flags the encoder wrote for that case.
        boolean reading = true;
        while (reading) {
            if (vector.readInt(1) == EncodingConsts.POWDER_REPEAT) {
                powders.add(previous);
            } else if (vector.readInt(1) == EncodingConsts.POWDER_REPEAT_TIER) {
                // Same tier, different element, stored as a wrap-around offset.
                int wrap = vector.readInt(consts.powderWrapperBits());
                int elementIndex = (previous.elementIndex() + wrap + 1) % consts.powderElements();
                powders.add(new Powder(Powder.PowderElement.byIndex(elementIndex), previous.tier()));
            } else if (vector.readInt(1) == EncodingConsts.POWDER_NEW_POWDER) {
                powders.add(Powder.decodeId(vector.readInt(consts.powderIdBits()), consts.powderTiers()));
            } else {
                reading = false; // NEW_ITEM
            }
            if (reading) {
                previous = powders.get(powders.size() - 1);
            }
        }
        return powders;
    }

    // ---------------------------------------------------------------- tomes

    private static void encodeTomes(BitVector vector, WynnBuild build, EncodingConsts consts) {
        List<Integer> tomes = build.tomeIds();
        boolean any = tomes.stream().anyMatch(java.util.Objects::nonNull);
        if (!any) {
            vector.append(EncodingConsts.NO_TOMES, 1);
            return;
        }
        vector.append(EncodingConsts.HAS_TOMES, 1);
        for (int i = 0; i < consts.tomeCount(); i++) {
            Integer tomeId = i < tomes.size() ? tomes.get(i) : null;
            if (tomeId == null) {
                vector.append(EncodingConsts.SLOT_UNUSED, 1);
            } else {
                vector.append(EncodingConsts.SLOT_USED, 1);
                vector.append(tomeId, consts.tomeIdBits());
            }
        }
    }

    private static void decodeTomes(BitVector vector, WynnBuild build, EncodingConsts consts) {
        if (vector.readInt(1) == EncodingConsts.NO_TOMES) {
            return;
        }
        for (int i = 0; i < consts.tomeCount(); i++) {
            if (vector.readInt(1) == EncodingConsts.SLOT_USED) {
                int tomeId = vector.readInt(consts.tomeIdBits());
                if (i < build.tomeIds().size()) {
                    build.tomeIds().set(i, tomeId);
                }
            }
        }
    }

    // ---------------------------------------------------------------- skill points

    private static void encodeSkillPoints(BitVector vector, WynnBuild build, EncodingConsts consts) {
        if (!build.hasManualSkillPoints()) {
            vector.append(EncodingConsts.SP_AUTOMATIC, 1);
            return;
        }
        vector.append(EncodingConsts.SP_ASSIGNED, 1);
        for (int i = 0; i < consts.skillPointTypes(); i++) {
            Integer value = i < WynnBuild.SKILL_POINT_TYPES ? build.assignedSkillPoint(i) : null;
            if (value == null) {
                vector.append(EncodingConsts.SP_ELEMENT_UNASSIGNED, 1);
            } else {
                vector.append(EncodingConsts.SP_ELEMENT_ASSIGNED, 1);
                // Truncated to the field width and read back as two's complement.
                vector.append(value & ((1 << consts.maxSkillPointBits()) - 1), consts.maxSkillPointBits());
            }
        }
    }

    private static void decodeSkillPoints(BitVector vector, WynnBuild build, EncodingConsts consts) {
        if (vector.readInt(1) == EncodingConsts.SP_AUTOMATIC) {
            return;
        }
        for (int i = 0; i < consts.skillPointTypes(); i++) {
            if (vector.readInt(1) == EncodingConsts.SP_ELEMENT_ASSIGNED) {
                int value = vector.readSigned(consts.maxSkillPointBits());
                if (i < WynnBuild.SKILL_POINT_TYPES) {
                    build.setAssignedSkillPoint(i, value);
                }
            }
        }
    }

    // ---------------------------------------------------------------- level and aspects

    private static void encodeLevel(BitVector vector, WynnBuild build, EncodingConsts consts) {
        if (build.level() == consts.maxLevel()) {
            vector.append(EncodingConsts.LEVEL_MAX, 1);
        } else {
            vector.append(EncodingConsts.LEVEL_OTHER, 1);
            vector.append(build.level(), consts.levelBits());
        }
    }

    private static void decodeLevel(BitVector vector, WynnBuild build, EncodingConsts consts) {
        if (vector.readInt(1) == EncodingConsts.LEVEL_MAX) {
            build.setLevel(consts.maxLevel());
        } else {
            build.setLevel(vector.readInt(consts.levelBits()));
        }
    }

    private static void encodeAspects(BitVector vector, WynnBuild build, EncodingConsts consts) {
        List<WynnBuild.AspectSelection> aspects = build.aspects();
        boolean any = aspects.stream().anyMatch(java.util.Objects::nonNull);
        if (!any) {
            vector.append(EncodingConsts.NO_ASPECTS, 1);
            return;
        }
        vector.append(EncodingConsts.HAS_ASPECTS, 1);
        for (int i = 0; i < consts.aspectCount(); i++) {
            WynnBuild.AspectSelection aspect = i < aspects.size() ? aspects.get(i) : null;
            if (aspect == null) {
                vector.append(EncodingConsts.SLOT_UNUSED, 1);
            } else {
                vector.append(EncodingConsts.SLOT_USED, 1);
                vector.append(aspect.aspectId(), consts.aspectIdBits());
                // Undocumented in ENCODING.md but present in the encoder: aspects carry a tier.
                vector.append(aspect.tier() - 1, ASPECT_TIER_BITS);
            }
        }
    }

    private static void decodeAspects(BitVector vector, WynnBuild build, EncodingConsts consts) {
        if (vector.readInt(1) == EncodingConsts.NO_ASPECTS) {
            return;
        }
        for (int i = 0; i < consts.aspectCount(); i++) {
            if (vector.readInt(1) == EncodingConsts.SLOT_USED) {
                int aspectId = vector.readInt(consts.aspectIdBits());
                int tier = vector.readInt(ASPECT_TIER_BITS) + 1;
                if (i < build.aspects().size()) {
                    build.aspects().set(i, new WynnBuild.AspectSelection(aspectId, tier));
                }
            }
        }
    }
}
